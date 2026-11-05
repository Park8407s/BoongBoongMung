package com.kumhoht.boongboongmung.drive

import android.util.Log
import com.kumhoht.boongboongmung.data.BoongRepository
import com.kumhoht.boongboongmung.replay.ReplayEvent
import com.kumhoht.boongboongmung.replay.ReplayScenario
import com.kumhoht.boongboongmung.vehicle.NaviSignal
import com.kumhoht.boongboongmung.vehicle.TelemetryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 재생 모드 진행 상황 (시연용 표시) */
data class ReplayProgress(val title: String, val fraction: Float, val distanceKm: Double)

data class EngineState(
    val phase: DrivePhase = DrivePhase.PARKED,
    val replay: ReplayProgress? = null,
    /** 방금 저장된 운행 id (결과 화면으로 이동용) */
    val lastSettledTripId: Long? = null,
)

/**
 * 주행 데이터 집계 엔진.
 * Telemetry Collector → Drive State Machine → Safe Driving Evaluator → (운행 종료) → Point & Reward Engine
 *
 * 주행 중에는 화면·소리·알림을 쓰지 않고 집계만 한다. 운행이 끝나면 한 번에 정산한다.
 */
class DriveEngine(
    private val collector: TelemetryCollector,
    private val repo: BoongRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val lock = Mutex()
    private var machine = DriveStateMachine()
    private var evaluator: SafeDrivingEvaluator? = null
    private var tickJob: Job? = null
    private var replayJob: Job? = null

    fun start() {
        if (tickJob != null) return
        collector.start()
        tickJob = scope.launch {
            while (isActive) {
                if (replayJob == null) step(collector.sample(System.currentTimeMillis()), replay = false)
                delay(TICK_MS)
            }
        }
        scope.launch {
            collector.naviEvents.collect { signal ->
                if (signal == NaviSignal.ARRIVED && replayJob == null) lock.withLock { evaluator?.markArrived() }
            }
        }
    }

    private suspend fun step(sample: VehicleSample, replay: Boolean) {
        var finished: TripSummary? = null
        lock.withLock {
            when (val tr = machine.onSample(sample)) {
                is DriveTransition.Started -> {
                    Log.i(TAG, "운행 시작 (replay=$replay)")
                    // 재생 모드 시나리오는 실차 가감속으로 만들어서 실차 기준, 실주행은 지금은 가상 콘솔 기준.
                    // 실차 배포 시 LIVE_CRITERIA 를 SafeDrivingCriteria.REAL_CAR 로 바꾼다.
                    val criteria = if (replay) SafeDrivingCriteria.REAL_CAR else LIVE_CRITERIA
                    evaluator = SafeDrivingEvaluator(tr.atMs, criteria).also { it.onSample(sample) }
                }
                is DriveTransition.Ended -> {
                    evaluator?.onSample(sample)
                    finished = evaluator?.finish(tr.atMs)
                    evaluator = null
                }
                null -> evaluator?.onSample(sample)
            }
            _state.update { it.copy(phase = machine.phase) }
        }
        finished?.let { trip ->
            Log.i(TAG, "운행 종료 ${"%.2f".format(trip.distanceKm)}km smooth=${trip.smoothKm}/${trip.judgedKm} " +
                "accel=${trip.harshAccelCount} decel=${trip.harshDecelCount} limit=${trip.speedLimitRatio}")
            val id = runCatching { repo.settleTrip(trip, replay) }
                .onFailure { Log.e(TAG, "정산 실패", it) }
                .getOrNull()
            if (id != null) _state.update { it.copy(lastSettledTripId = id) }
        }
    }

    // ------------------------------------------------------------ 주행 로그 재생 모드

    val isReplaying: Boolean get() = replayJob != null

    /**
     * 저장된 주행 로그를 같은 파이프라인으로 재생한다. 주차 중에만 시작할 수 있다.
     * 가상 시각은 '지금 끝나도록' 과거로 맞춘다 (연속 일수·휴식 보너스 계산이 자연스럽게 된다).
     */
    fun startReplay(scenario: ReplayScenario): Boolean {
        if (replayJob != null || _state.value.phase != DrivePhase.PARKED) return false
        val frames = scenario.frames()
        if (frames.isEmpty()) return false
        val duration = frames.last().sample.timeMs
        val base = System.currentTimeMillis() - duration
        val speedUp = maxOf(MIN_SPEED_UP, duration / TARGET_REPLAY_MS.toDouble())

        replayJob = scope.launch {
            try {
                lock.withLock { machine = DriveStateMachine(); evaluator = null }
                var debtMs = 0.0
                var lastT = 0L
                frames.forEachIndexed { i, f ->
                    val t = f.sample.timeMs
                    debtMs += (t - lastT) / speedUp
                    lastT = t
                    if (debtMs >= 16) { delay(debtMs.toLong()); debtMs = 0.0 }
                    step(f.sample.copy(timeMs = base + t), replay = true)
                    if (f.event == ReplayEvent.ARRIVED) lock.withLock { evaluator?.markArrived() }
                    if (i % 20 == 0) {
                        val km = lock.withLock { evaluator?.distanceKm } ?: 0.0
                        _state.update {
                            it.copy(replay = ReplayProgress(scenario.title, (i + 1f) / frames.size, km))
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    lock.withLock { machine = DriveStateMachine(); evaluator = null }
                    _state.update { it.copy(replay = null, phase = DrivePhase.PARKED) }
                    replayJob = null
                }
            }
        }
        return true
    }

    fun stopReplay() {
        replayJob?.cancel()
    }

    fun consumeSettled() = _state.update { it.copy(lastSettledTripId = null) }

    companion object {
        private const val TAG = "DriveEngine"
        const val TICK_MS = 500L
        private const val TARGET_REPLAY_MS = 40_000L
        private const val MIN_SPEED_UP = 20.0

        /** 실주행(차량 신호) 판별 기준. 지금은 가상 콘솔, 실차 배포 시 [SafeDrivingCriteria.REAL_CAR] */
        val LIVE_CRITERIA: SafeDrivingCriteria = SafeDrivingCriteria.VIRTUAL_CONSOLE
    }
}

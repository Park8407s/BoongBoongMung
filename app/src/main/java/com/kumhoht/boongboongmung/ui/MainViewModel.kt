package com.kumhoht.boongboongmung.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kumhoht.boongboongmung.BoongApp
import com.kumhoht.boongboongmung.data.Progress
import com.kumhoht.boongboongmung.data.PurchaseResult
import com.kumhoht.boongboongmung.data.TripEntity
import com.kumhoht.boongboongmung.drive.DrivePhase
import com.kumhoht.boongboongmung.drive.EngineState
import com.kumhoht.boongboongmung.reward.GiftReward
import com.kumhoht.boongboongmung.reward.Item
import com.kumhoht.boongboongmung.reward.Levels
import com.kumhoht.boongboongmung.reward.PointRules
import com.kumhoht.boongboongmung.reward.ProgressState
import com.kumhoht.boongboongmung.reward.Slot
import com.kumhoht.boongboongmung.replay.ReplayScenario
import com.kumhoht.boongboongmung.vehicle.LiveVehicle
import com.kumhoht.boongboongmung.vehicle.SignalStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { HOME, SHOP, RECORDS }

/** 화면에 필요한 저장 데이터 묶음 */
data class Wallet(
    val balance: Int = 0,
    val closedBoxes: Int = 0,
    val progress: Progress = Progress(ProgressState(), 1, 5, 30, 0f, 0f),
    val title: String = Levels.titleFor(1),
)

data class Wardrobe(
    val owned: Set<String> = emptySet(),
    val equipped: Map<Slot, String> = emptyMap(),
)

/** 선물상자 열기 다이얼로그 상태 */
sealed interface GiftUi {
    data object Closed : GiftUi
    data object Ready : GiftUi
    data class Opened(val reward: GiftReward) : GiftUi
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val boong = app as BoongApp
    private val repo = boong.repository
    private val engine = boong.engine
    private val collector = boong.collector

    val engineState: StateFlow<EngineState> = engine.state
    val live: StateFlow<LiveVehicle> = collector.live
    val signals: StateFlow<Map<String, SignalStatus>> = collector.status

    val wallet: StateFlow<Wallet> = combine(
        repo.balance, repo.closedBoxes, repo.progress, repo.achievementTitles,
    ) { bal, boxes, prog, titles ->
        Wallet(bal, boxes, prog, titles.firstOrNull() ?: Levels.titleFor(prog.level))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Wallet())

    val wardrobe: StateFlow<Wardrobe> = combine(repo.owned, repo.equipped) { o, e -> Wardrobe(o, e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Wardrobe())

    val latestTrip: StateFlow<TripEntity?> = repo.latestTrip.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val unseenTrip: StateFlow<TripEntity?> = repo.unseenTrip.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val trips: StateFlow<List<TripEntity>> = repo.trips.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _gift = MutableStateFlow<GiftUi>(GiftUi.Closed)
    val gift: StateFlow<GiftUi> = _gift.asStateFlow()

    /** 화면 아래 잠깐 뜨는 안내 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 장착 반응 트리거 */
    private val _react = MutableStateFlow(0)
    val react: StateFlow<Int> = _react.asStateFlow()

    /** 기록 화면에서 다시 보는 결과 */
    private val _reviewTrip = MutableStateFlow<TripEntity?>(null)
    val reviewTrip: StateFlow<TripEntity?> = _reviewTrip.asStateFlow()

    fun select(tab: Tab) { _tab.value = tab }

    fun confirmResult(trip: TripEntity) {
        viewModelScope.launch { repo.markSeen(trip.id) }
        engine.consumeSettled()
        _reviewTrip.value = null
        _tab.value = Tab.HOME
    }

    fun review(trip: TripEntity?) { _reviewTrip.value = trip }

    // ---- 선물상자
    fun showGift() { if (wallet.value.closedBoxes > 0) _gift.value = GiftUi.Ready }
    fun openGift() {
        viewModelScope.launch {
            val r = repo.openGiftBox(System.currentTimeMillis())
            _gift.value = if (r != null) GiftUi.Opened(r) else GiftUi.Closed
        }
    }
    fun closeGift() { _gift.value = GiftUi.Closed }

    // ---- 상점·꾸미기
    fun buy(item: Item) {
        viewModelScope.launch {
            val msg = when (repo.buy(item, System.currentTimeMillis())) {
                PurchaseResult.Ok -> { _react.value++; "${item.name}을(를) 샀어요! 붕붕냥이 좋아해요" }
                PurchaseResult.NotEnoughBones -> "뼈다귀가 조금 모자라요. 안전운전으로 모아 볼까요?"
                PurchaseResult.LevelLocked -> "Lv.${item.requiredLevel}부터 살 수 있어요"
                PurchaseResult.AlreadyOwned -> "이미 가지고 있어요"
                PurchaseResult.NotForSale -> "칭호 보상으로만 받을 수 있어요"
            }
            say(msg)
        }
    }

    fun equip(item: Item) {
        viewModelScope.launch { repo.equip(item); _react.value++ }
    }

    fun unequip(slot: Slot) {
        viewModelScope.launch { repo.unequip(slot); _react.value++ }
    }

    // ---- 주행 로그 재생 모드
    fun startReplay(sc: ReplayScenario) {
        if (engineState.value.phase != DrivePhase.PARKED) { say("주차 중에만 재생할 수 있어요"); return }
        if (!engine.startReplay(sc)) say("재생을 시작하지 못했어요")
    }

    fun stopReplay() = engine.stopReplay()

    fun markAllSeen() { viewModelScope.launch { repo.markAllSeen() } }

    // ---- 데이터 초기화
    fun resetData() {
        if (engineState.value.phase != DrivePhase.PARKED) { say("주차 중에만 초기화할 수 있어요"); return }
        viewModelScope.launch {
            repo.resetAll(System.currentTimeMillis())
            engine.consumeSettled()
            _gift.value = GiftUi.Closed
            _reviewTrip.value = null
            _tab.value = Tab.HOME
            _react.value++
            say("데이터를 초기화했어요. 붕붕냥과 처음부터 시작해요!")
        }
    }

    /** 휴식이 필요한지 (2시간 이상 연속 운행 후 아직 15분이 안 지남) */
    fun needsRest(now: Long): Boolean {
        val st = wallet.value.progress.state
        return st.continuousDriveMs >= PointRules.REST_AFTER_MS && now - st.lastTripEndMs < PointRules.REST_MIN_MS
    }

    fun say(msg: String) {
        _toast.value = msg
        viewModelScope.launch { delay(2500); if (_toast.value == msg) _toast.value = null }
    }

    fun onPermissionsGranted() = collector.onPermissionsGranted()
}

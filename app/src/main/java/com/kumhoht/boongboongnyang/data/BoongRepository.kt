package com.kumhoht.boongboongnyang.data

import androidx.room.withTransaction
import com.kumhoht.boongboongnyang.drive.TripSummary
import com.kumhoht.boongboongnyang.reward.Catalog
import com.kumhoht.boongboongnyang.reward.GiftBox
import com.kumhoht.boongboongnyang.reward.GiftReward
import com.kumhoht.boongboongnyang.reward.Item
import com.kumhoht.boongboongnyang.reward.Levels
import com.kumhoht.boongboongnyang.reward.PointRules
import com.kumhoht.boongboongnyang.reward.ProgressState
import com.kumhoht.boongboongnyang.reward.RewardCalculator
import com.kumhoht.boongboongnyang.reward.RewardLine
import com.kumhoht.boongboongnyang.reward.Slot
import com.kumhoht.boongboongnyang.reward.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 홈·상점 상단에 보이는 진행 정보 */
data class Progress(
    val state: ProgressState,
    val level: Int,
    val kmToNextLevel: Int,
    val kmToNextBox: Int,
    /** 다음 레벨까지 진행률 0~1 */
    val levelFraction: Float,
    /** 다음 선물상자까지 진행률 0~1 */
    val boxFraction: Float,
)

sealed interface PurchaseResult {
    data object Ok : PurchaseResult
    data object NotEnoughFish : PurchaseResult
    data object LevelLocked : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data object NotForSale : PurchaseResult
}

/**
 * Point & Reward Engine + Shop & Inventory 의 저장소.
 * 운행 기록·포인트·아이템은 모두 차량 로컬(Room)에만 저장한다.
 */
class BoongRepository(private val db: BoongDatabase, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val dao = db.dao()

    // ------------------------------------------------------------ 조회

    val balance: Flow<Int> = dao.observeBalance()
    val closedBoxes: Flow<Int> = dao.observeClosedBoxCount()
    val latestTrip: Flow<TripEntity?> = dao.observeLatestTrip()
    val unseenTrip: Flow<TripEntity?> = dao.observeUnseenTrip()
    val trips: Flow<List<TripEntity>> = dao.observeTrips(200)
    val owned: Flow<Set<String>> = dao.observeInventory().map { list -> list.map { it.itemId }.toSet() }
    val equipped: Flow<Map<Slot, String>> = dao.observeEquipped().map { list ->
        list.mapNotNull { e -> runCatching { Slot.valueOf(e.slot) }.getOrNull()?.let { it to e.itemId } }.toMap()
    }
    val achievementTitles: Flow<List<String>> = dao.observeTitles().map { list -> list.map { it.title } }

    val progress: Flow<Progress> = dao.observeKv().map { list ->
        val st = decodeState(list.associate { it.key to it.value })
        val level = Levels.levelFor(st.totalSafeKm)
        val cur = Levels.currentThreshold(level)
        val next = Levels.nextThreshold(level)
        val box = PointRules.GIFT_BOX_EVERY_SAFE_KM
        Progress(
            state = st,
            level = level,
            kmToNextLevel = RewardCalculator.kmToNextLevel(st.totalSafeKm),
            kmToNextBox = RewardCalculator.kmToNextBox(st.totalSafeKm),
            levelFraction = ((st.totalSafeKm - cur).toFloat() / (next - cur).coerceAtLeast(1)).coerceIn(0f, 1f),
            boxFraction = (st.totalSafeKm % box).toFloat() / box,
        )
    }

    // ------------------------------------------------------------ 첫 실행

    /** 기본 배경 지급 + 장착 */
    suspend fun ensureDefaults(now: Long) {
        if (dao.getKv(KEY_INIT) != null) return
        db.withTransaction {
            dao.insertItem(InventoryEntity(Catalog.DEFAULT_BACKGROUND, now, Source.DEFAULT.name))
            dao.equip(EquippedEntity(Slot.BACKGROUND.name, Catalog.DEFAULT_BACKGROUND))
            dao.putKv(KvEntity(KEY_INIT, "1"))
        }
    }

    // ------------------------------------------------------------ 데이터 초기화

    /**
     * 운행 기록·포인트·선물상자·아이템·칭호·레벨을 모두 지우고 첫 실행 상태로 되돌린다.
     * 되돌릴 수 없다. 주차 중에만 호출한다.
     */
    suspend fun resetAll(now: Long) {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        ensureDefaults(now)
    }

    // ------------------------------------------------------------ 운행 정산

    /**
     * 운행 종료 → 포인트·선물상자·레벨·칭호 정산. 하나의 트랜잭션.
     * @return 저장된 운행 id. 너무 짧은 운행이면 null
     */
    suspend fun settleTrip(trip: TripSummary, replay: Boolean): Long? {
        if (trip.distanceKm < PointRules.MIN_TRIP_KM) return null
        val day = Instant.ofEpochMilli(trip.endMs).atZone(zone).toLocalDate()
        val dayKey = dayKey(day)
        val prevKey = dayKey(day.minusDays(1))

        return db.withTransaction {
            val state = loadState()
            val s = RewardCalculator.settle(trip, state, dayKey, prevKey)
            val id = dao.insertTrip(
                TripEntity(
                    startAt = trip.startMs,
                    endAt = trip.endMs,
                    drivingMs = trip.drivingMs,
                    distanceKm = trip.distanceKm,
                    smoothKm = trip.smoothKm,
                    judgedKm = trip.judgedKm,
                    harshAccel = trip.harshAccelCount,
                    harshDecel = trip.harshDecelCount,
                    speedLimitRatio = trip.speedLimitRatio,
                    arrived = trip.arrived,
                    points = s.totalPoints,
                    giftBoxes = s.giftBoxes,
                    lines = encodeLines(s.lines),
                    levelBefore = s.levelBefore,
                    levelAfter = s.levelAfter,
                    restBonus = s.restBonus,
                    feedback = RewardCalculator.feedback(trip),
                    replay = replay,
                )
            )
            if (s.totalPoints > 0) dao.insertLedger(LedgerEntity(at = trip.endMs, amount = s.totalPoints, reason = "운행 #$id"))
            repeat(s.giftBoxes) { dao.insertGiftBox(GiftBoxEntity(earnedAt = trip.endMs, tripId = id)) }
            saveState(s.newState)
            checkSmoothDriverTitle(trip.endMs)
            id
        }
    }

    /** 최근 7일 부드러운 운전 비율 70% 이상(10km 이상) → '부드러운 드라이버' 칭호 + 전용 목도리 */
    private suspend fun checkSmoothDriverTitle(now: Long) {
        val since = now - 7L * 24 * 60 * 60 * 1000
        val recent = dao.tripsSince(since)
        val judged = recent.sumOf { it.judgedKm }
        val smooth = recent.sumOf { it.smoothKm }
        if (judged >= 10 && smooth * 100 >= judged * 70) {
            val inserted = dao.insertTitle(TitleEntity(Catalog.SMOOTH_TITLE, now))
            if (inserted != -1L) {
                dao.insertItem(InventoryEntity(Catalog.SMOOTH_TITLE_ITEM, now, Source.TITLE.name))
            }
        }
    }

    suspend fun markSeen(tripId: Long) = dao.markSeen(tripId)
    suspend fun markAllSeen() = dao.markAllSeen()
    suspend fun trip(id: Long) = dao.trip(id)

    // ------------------------------------------------------------ 선물상자

    /** 가장 오래된 선물상자 하나를 연다. 없으면 null */
    suspend fun openGiftBox(now: Long): GiftReward? = db.withTransaction {
        val box = dao.firstClosedBox() ?: return@withTransaction null
        val owned = dao.ownedIds().toSet()
        val level = Levels.levelFor(loadState().totalSafeKm)
        val reward = GiftBox.open(owned, level)
        when (reward) {
            is GiftReward.Bones -> {
                dao.insertLedger(LedgerEntity(at = now, amount = reward.amount, reason = "선물상자"))
                dao.openBox(box.id, now, "bones:${reward.amount}")
            }
            is GiftReward.ItemReward -> {
                dao.insertItem(InventoryEntity(reward.itemId, now, Source.GIFT_BOX.name))
                dao.openBox(box.id, now, "item:${reward.itemId}")
            }
        }
        reward
    }

    // ------------------------------------------------------------ 상점·꾸미기

    suspend fun buy(item: Item, now: Long): PurchaseResult = db.withTransaction {
        if (item.source != Source.SHOP && item.source != Source.GIFT_BOX) return@withTransaction PurchaseResult.NotForSale
        if (item.id in dao.ownedIds()) return@withTransaction PurchaseResult.AlreadyOwned
        val level = Levels.levelFor(loadState().totalSafeKm)
        if (level < item.requiredLevel) return@withTransaction PurchaseResult.LevelLocked
        if (dao.balance() < item.price) return@withTransaction PurchaseResult.NotEnoughFish
        dao.insertLedger(LedgerEntity(at = now, amount = -item.price, reason = "구매 ${item.name}"))
        dao.insertItem(InventoryEntity(item.id, now, Source.SHOP.name))
        dao.equip(EquippedEntity(item.slot.name, item.id))
        PurchaseResult.Ok
    }

    suspend fun equip(item: Item) {
        if (item.id in dao.ownedIds()) dao.equip(EquippedEntity(item.slot.name, item.id))
    }

    suspend fun unequip(slot: Slot) {
        if (slot == Slot.BACKGROUND) dao.equip(EquippedEntity(slot.name, Catalog.DEFAULT_BACKGROUND))
        else dao.unequip(slot.name)
    }

    // ------------------------------------------------------------ 진행 상태 (kv)

    suspend fun loadState(): ProgressState {
        val keys = listOf(K_SAFE, K_STREAK, K_LAST_SAFE, K_LAST_BONUS, K_CONT, K_LAST_END)
        return decodeState(keys.associateWith { dao.getKv(it) ?: "" })
    }

    private suspend fun saveState(s: ProgressState) {
        dao.putKv(KvEntity(K_SAFE, s.totalSafeKm.toString()))
        dao.putKv(KvEntity(K_STREAK, s.streakDays.toString()))
        dao.putKv(KvEntity(K_LAST_SAFE, s.lastSafeDay.toString()))
        dao.putKv(KvEntity(K_LAST_BONUS, s.lastStreakBonusDay.toString()))
        dao.putKv(KvEntity(K_CONT, s.continuousDriveMs.toString()))
        dao.putKv(KvEntity(K_LAST_END, s.lastTripEndMs.toString()))
    }

    private fun decodeState(m: Map<String, String>) = ProgressState(
        totalSafeKm = m[K_SAFE]?.toIntOrNull() ?: 0,
        streakDays = m[K_STREAK]?.toIntOrNull() ?: 0,
        lastSafeDay = m[K_LAST_SAFE]?.toIntOrNull() ?: 0,
        lastStreakBonusDay = m[K_LAST_BONUS]?.toIntOrNull() ?: 0,
        continuousDriveMs = m[K_CONT]?.toLongOrNull() ?: 0L,
        lastTripEndMs = m[K_LAST_END]?.toLongOrNull() ?: 0L,
    )

    companion object {
        private const val KEY_INIT = "init"
        private const val K_SAFE = "totalSafeKm"
        private const val K_STREAK = "streakDays"
        private const val K_LAST_SAFE = "lastSafeDay"
        private const val K_LAST_BONUS = "lastStreakBonusDay"
        private const val K_CONT = "continuousDriveMs"
        private const val K_LAST_END = "lastTripEndMs"

        fun dayKey(d: LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth

        fun encodeLines(lines: List<RewardLine>) = lines.joinToString("\n") { "${it.label}\t${it.points}" }

        fun decodeLines(s: String): List<RewardLine> = s.lines().mapNotNull { line ->
            val i = line.lastIndexOf('\t')
            if (i < 0) null else RewardLine(line.substring(0, i), line.substring(i + 1).toIntOrNull() ?: 0)
        }
    }
}

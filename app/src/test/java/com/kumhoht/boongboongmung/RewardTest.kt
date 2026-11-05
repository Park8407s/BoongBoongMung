package com.kumhoht.boongboongmung

import com.kumhoht.boongboongmung.drive.TripSummary
import com.kumhoht.boongboongmung.reward.Catalog
import com.kumhoht.boongboongmung.reward.GiftBox
import com.kumhoht.boongboongmung.reward.GiftReward
import com.kumhoht.boongboongmung.reward.Levels
import com.kumhoht.boongboongmung.reward.PointRules
import com.kumhoht.boongboongmung.reward.ProgressState
import com.kumhoht.boongboongmung.reward.RewardCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RewardTest {

    private fun trip(
        km: Double, smooth: Int, judged: Int = km.toInt(), limitCovered: Double = 0.0,
        limitOk: Double = 0.0, arrived: Boolean = false, start: Long = 0, drivingMs: Long = 30 * 60_000L,
    ) = TripSummary(start, start + drivingMs, drivingMs, km, smooth, judged, 0, 0, limitCovered, limitOk, arrived, 80f)

    @Test
    fun commuteExample_matchesPlan() {
        // 기획서 그림 2: 주행 18km +18, 부드러운 운전 15km +15, 규정 속도 준수 94% +10, 목적지 도착 +10 = +53
        val r = RewardCalculator.settle(
            trip(18.3, 15, 18, limitCovered = 18.0, limitOk = 16.93, arrived = true),
            ProgressState(lastSafeDay = 20261103, streakDays = 2, lastStreakBonusDay = 20261103),
            dayKey = 20261104, prevDayKey = 20261103,
        )
        val labels = r.lines.map { it.label to it.points }
        assertTrue(labels.toString(), ("주행 18km" to 18) in labels)
        assertTrue(labels.toString(), ("부드러운 운전 15km" to 15) in labels)
        assertTrue(labels.toString(), ("규정 속도 준수 94%" to 10) in labels)
        assertTrue(labels.toString(), ("목적지 도착" to 10) in labels)
        // 15/18 = 83% → 안전운전한 날, 3일째 연속 → +15
        assertTrue(labels.toString(), ("연속 안전운전 3일째" to 15) in labels)
        assertEquals(68, r.totalPoints)
    }

    @Test
    fun seatBelt_bonusOnlyWhenBuckled() {
        val st = ProgressState()
        fun labels(belt: Boolean?) = RewardCalculator.settle(trip(5.0, 5).copy(beltBuckled = belt), st, 20261105, 20261104)
            .lines.map { it.label to it.points }
        assertTrue(("안전벨트 착용" to PointRules.SEATBELT_BONUS) in labels(true))
        assertFalse(labels(false).any { it.first == "안전벨트 착용" })
        // 신호가 없던 운행은 표시하지 않는다
        assertFalse(labels(null).any { it.first == "안전벨트 착용" })
    }

    @Test
    fun streak_onlyOncePerDay_andCapped() {
        var st = ProgressState(streakDays = 9, lastSafeDay = 20261103)
        val r1 = RewardCalculator.settle(trip(10.0, 10), st, 20261104, 20261103)
        assertEquals(10, r1.newState.streakDays)
        assertTrue(r1.lines.any { it.points == PointRules.STREAK_MAX })
        st = r1.newState
        val r2 = RewardCalculator.settle(trip(10.0, 10, start = 10_000_000), st, 20261104, 20261103)
        assertFalse(r2.lines.any { it.label.startsWith("연속") })
    }

    @Test
    fun streak_resetsAfterGap() {
        val st = ProgressState(streakDays = 5, lastSafeDay = 20261101)
        val r = RewardCalculator.settle(trip(10.0, 10), st, 20261104, 20261103)
        assertEquals(1, r.newState.streakDays)
    }

    @Test
    fun giftBoxes_every30SafeKm() {
        val r = RewardCalculator.settle(trip(60.0, 60), ProgressState(totalSafeKm = 0), 20261104, 20261103)
        assertEquals(2, r.giftBoxes)
        val r2 = RewardCalculator.settle(trip(10.0, 5), ProgressState(totalSafeKm = 28), 20261104, 20261103)
        assertEquals(1, r2.giftBoxes)
        assertEquals(27, RewardCalculator.kmToNextBox(33))
    }

    @Test
    fun restBonus_after2hDriveAnd15minRest() {
        val twoHours = 2 * 60 * 60_000L + 60_000L
        val first = RewardCalculator.settle(trip(200.0, 200, drivingMs = twoHours), ProgressState(), 20261104, 20261103)
        assertFalse(first.restBonus)
        val end = first.newState.lastTripEndMs
        // 10분만 쉼 → 보너스 없음
        val short = RewardCalculator.settle(trip(5.0, 5, start = end + 10 * 60_000L), first.newState, 20261104, 20261103)
        assertFalse(short.restBonus)
        // 16분 쉼 → 보너스
        val rested = RewardCalculator.settle(trip(5.0, 5, start = end + 16 * 60_000L), first.newState, 20261104, 20261103)
        assertTrue(rested.restBonus)
        assertTrue(rested.lines.any { it.label == "휴식 보너스" && it.points == 20 })
        assertEquals(30 * 60_000L, rested.newState.continuousDriveMs) // 휴식 후 리셋 → 이번 운행 30분만
    }

    @Test
    fun levels() {
        assertEquals(1, Levels.levelFor(0))
        assertEquals(2, Levels.levelFor(5))
        assertEquals(5, Levels.levelFor(50))
        assertEquals(10, Levels.levelFor(300))
        assertEquals(11, Levels.levelFor(400))
        assertEquals(80, Levels.nextThreshold(5))
        assertEquals(12, RewardCalculator.kmToNextLevel(38))
    }

    @Test
    fun giftBox_givesExclusiveItemFirst_andFallsBackToBones() {
        var gotSunglasses = false
        repeat(200) { seed ->
            val r = GiftBox.open(emptySet(), 1, Random(seed))
            if (r is GiftReward.ItemReward) { assertEquals("glasses_sun", r.itemId); gotSunglasses = true }
        }
        assertTrue(gotSunglasses)
        val all = Catalog.items.map { it.id }.toSet()
        repeat(100) { seed ->
            val r = GiftBox.open(all, 99, Random(seed))
            assertTrue(r is GiftReward.Bones)
        }
        assertEquals(100, GiftBox.odds.sumOf { it.percent })
    }
}

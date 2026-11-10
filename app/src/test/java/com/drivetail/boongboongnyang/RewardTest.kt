package com.drivetail.boongboongnyang

import com.drivetail.boongboongnyang.drive.TripSummary
import com.drivetail.boongboongnyang.reward.Catalog
import com.drivetail.boongboongnyang.reward.GiftBox
import com.drivetail.boongboongnyang.reward.GiftReward
import com.drivetail.boongboongnyang.reward.Levels
import com.drivetail.boongboongnyang.reward.PointRules
import com.drivetail.boongboongnyang.reward.ProgressState
import com.drivetail.boongboongnyang.reward.RewardCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RewardTest {

    private fun trip(
        km: Double, smooth: Double, judged: Double = km, limitCovered: Double = 0.0,
        limitOk: Double = 0.0, arrived: Boolean = false, start: Long = 0, drivingMs: Long = 30 * 60_000L,
    ) = TripSummary(start, start + drivingMs, drivingMs, km, smooth, judged, 0, 0, limitCovered, limitOk, arrived, 80f)

    @Test
    fun commuteExample_matchesPlan() {
        // 시연용 기준 (0.2km 마다 +1): 주행 18.3km → 18.2km +91, 부드러운 운전 15km +75, 규정 속도 +10, 도착 +10
        val r = RewardCalculator.settle(
            trip(18.3, 15.0, 18.0, limitCovered = 18.0, limitOk = 16.93, arrived = true),
            ProgressState(lastSafeDay = 20261103, streakDays = 2, lastStreakBonusDay = 20261103),
            dayKey = 20261104, prevDayKey = 20261103,
        )
        val labels = r.lines.map { it.label to it.points }
        assertTrue(labels.toString(), ("주행 18.2km" to 91) in labels)
        assertTrue(labels.toString(), ("부드러운 운전 15km" to 75) in labels)
        assertTrue(labels.toString(), ("규정 속도 준수 94%" to 10) in labels)
        assertTrue(labels.toString(), ("목적지 도착" to 10) in labels)
        // 15/18 = 83% → 안전운전한 날, 3일째 연속 → +15
        assertTrue(labels.toString(), ("연속 안전운전 3일째" to 15) in labels)
        assertEquals(91 + 75 + 10 + 10 + 15, r.totalPoints)
    }

    @Test
    fun seatBelt_bonusOnlyWhenBuckled() {
        val st = ProgressState()
        fun labels(belt: Boolean?) = RewardCalculator.settle(trip(5.0, 5.0).copy(beltBuckled = belt), st, 20261105, 20261104)
            .lines.map { it.label to it.points }
        assertTrue(("안전벨트 착용" to PointRules.SEATBELT_BONUS) in labels(true))
        assertFalse(labels(false).any { it.first == "안전벨트 착용" })
        // 신호가 없던 운행은 표시하지 않는다
        assertFalse(labels(null).any { it.first == "안전벨트 착용" })
    }

    @Test
    fun streak_onlyOncePerDay_andCapped() {
        var st = ProgressState(streakDays = 9, lastSafeDay = 20261103)
        val r1 = RewardCalculator.settle(trip(10.0, 10.0), st, 20261104, 20261103)
        assertEquals(10, r1.newState.streakDays)
        assertTrue(r1.lines.any { it.points == PointRules.STREAK_MAX })
        st = r1.newState
        val r2 = RewardCalculator.settle(trip(10.0, 10.0, start = 10_000_000), st, 20261104, 20261103)
        assertFalse(r2.lines.any { it.label.startsWith("연속") })
    }

    @Test
    fun streak_resetsAfterGap() {
        val st = ProgressState(streakDays = 5, lastSafeDay = 20261101)
        val r = RewardCalculator.settle(trip(10.0, 10.0), st, 20261104, 20261103)
        assertEquals(1, r.newState.streakDays)
    }

    @Test
    fun giftBoxes_every2SafeKm() {
        val r = RewardCalculator.settle(trip(60.0, 60.0), ProgressState(totalSafeKm = 0.0), 20261104, 20261103)
        assertEquals(30, r.giftBoxes)
        // 28 → 33km: 30, 32 를 지나서 2개
        val r2 = RewardCalculator.settle(trip(10.0, 5.0), ProgressState(totalSafeKm = 28.0), 20261104, 20261103)
        assertEquals(2, r2.giftBoxes)
        // 1.8 → 2.0km: 딱 2km 에 닿으면 1개 (부동소수 오차 없이)
        val r3 = RewardCalculator.settle(trip(0.2, 0.2), ProgressState(totalSafeKm = 1.8), 20261104, 20261103)
        assertEquals(1, r3.giftBoxes)
        assertEquals(0.7, RewardCalculator.kmToNextBox(3.3), 1e-9)
    }

    @Test
    fun shortDrive_stillEarnsPoints() {
        // 가상 콘솔로 30초쯤 달린 0.44km → 주행 0.4km +2
        val r = RewardCalculator.settle(trip(0.44, 0.4), ProgressState(), 20261104, 20261103)
        val labels = r.lines.map { it.label to it.points }
        assertTrue(labels.toString(), ("주행 0.4km" to 2) in labels)
        assertTrue(labels.toString(), ("부드러운 운전 0.4km" to 2) in labels)
    }

    @Test
    fun restBonus_after2hDriveAnd15minRest() {
        val twoHours = 2 * 60 * 60_000L + 60_000L
        val first = RewardCalculator.settle(trip(200.0, 200.0, drivingMs = twoHours), ProgressState(), 20261104, 20261103)
        assertFalse(first.restBonus)
        val end = first.newState.lastTripEndMs
        // 10분만 쉼 → 보너스 없음
        val short = RewardCalculator.settle(trip(5.0, 5.0, start = end + 10 * 60_000L), first.newState, 20261104, 20261103)
        assertFalse(short.restBonus)
        // 16분 쉼 → 보너스
        val rested = RewardCalculator.settle(trip(5.0, 5.0, start = end + 16 * 60_000L), first.newState, 20261104, 20261103)
        assertTrue(rested.restBonus)
        assertTrue(rested.lines.any { it.label == "휴식 보너스" && it.points == 20 })
        assertEquals(30 * 60_000L, rested.newState.continuousDriveMs) // 휴식 후 리셋 → 이번 운행 30분만
    }

    @Test
    fun levels() {
        // 시연용 기준: 0 / 1 / 3 / 6 / 10 / 16 / 24 / 34 / 46 / 60km, 이후 20km 마다
        assertEquals(1, Levels.levelFor(0.0))
        assertEquals(2, Levels.levelFor(1.0))
        assertEquals(5, Levels.levelFor(10.0))
        assertEquals(10, Levels.levelFor(60.0))
        assertEquals(11, Levels.levelFor(80.0))
        assertEquals(16.0, Levels.nextThreshold(5), 1e-9)
        assertEquals(2.4, RewardCalculator.kmToNextLevel(7.6), 1e-9)
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

package com.kumhoht.boongboongnyang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumhoht.boongboongnyang.data.BoongRepository
import com.kumhoht.boongboongnyang.data.TripEntity
import com.kumhoht.boongboongnyang.data.smoothPercent
import com.kumhoht.boongboongnyang.reward.PointRules
import com.kumhoht.boongboongnyang.reward.Slot
import com.kumhoht.boongboongnyang.ui.pet.Expression
import com.kumhoht.boongboongnyang.ui.pet.PetView
import com.kumhoht.boongboongnyang.ui.theme.Boong
import com.kumhoht.boongboongnyang.ui.theme.Jua
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 홈 (주차 중) — 디자인 캔버스 v3 '붕붕냥 홈'.
 * 왼쪽: 창밖 풍경 속 붕붕냥 + 말풍선 + 안전벨트 칩 / 오른쪽: 레벨 · 최근 드라이브 · 선물상자 · 안내
 */
@Composable
fun HomeScreen(
    wallet: Wallet,
    equipped: Map<Slot, String>,
    latest: TripEntity?,
    needsRest: Boolean,
    reactKey: Int,
    onOpenGift: () -> Unit,
    /** 운전석 안전벨트 안 맴 여부. null = 신호 없음(표시 안 함) */
    beltUnbuckled: Boolean? = null,
    /** 기어를 D/R 로 넣기 전(P·N)인지. 이때만 안전벨트 경고를 한다 */
    beforeDrive: Boolean = true,
) {
    val beltWarn = beltUnbuckled == true && beforeDrive
    // 화면에 들어오면 잠깐 반가워하고, 아이템을 바꾸면 다시 웃는다
    var greeting by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2500); greeting = false }
    LaunchedEffect(reactKey) {
        if (reactKey > 0) { greeting = true; delay(1500); greeting = false }
    }
    val expression = when {
        beltWarn -> Expression.DEFAULT
        greeting || beltUnbuckled == false -> Expression.HAPPY
        needsRest -> Expression.SLEEPY
        else -> Expression.DEFAULT
    }
    val line = when {
        beltWarn -> "출발 전에 안전벨트 매라 냥!"
        needsRest -> "2시간 넘게 달렸어요. 15분 쉬면 휴식 보너스 +${PointRules.REST_BONUS}!"
        greeting && latest == null -> "안녕하세요! 저는 붕붕냥이에요"
        else -> "오늘도 안전하게 달려요!"
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // 왼쪽: 창밖 풍경 (폭 900, 모서리 44)
        Box(Modifier.width(900.dp).fillMaxHeight().clip(RoundedCornerShape(44.dp))) {
            PetView(equipped, expression, Modifier.fillMaxSize(), reactKey = reactKey.takeIf { it > 0 })
            SpeechBubble(Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 48.dp).widthIn(max = 560.dp)) {
                Text(line, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Boong.Brown, lineHeight = 50.sp)
            }
            if (beltUnbuckled != null) {
                BeltChip(!beltUnbuckled, Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 48.dp))
            }
        }

        // 오른쪽 열
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            LevelCard(wallet)
            RecentCard(latest, Modifier.weight(1f))
            GiftCard(wallet.closedBoxes, onOpenGift)
            DashedBox(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 30.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    BoongIcon(Icon.Steering, Boong.Muted, Modifier.size(40.dp))
                    HSpace(18.dp)
                    Text("기어를 D로 바꾸면 화면이 꺼지고, 붕붕냥이 조용히 주행을 기록해요.", fontSize = 24.sp, color = Boong.BrownSoft, lineHeight = 34.sp)
                }
            }
        }
    }
}

@Composable
private fun BeltChip(buckled: Boolean, modifier: Modifier = Modifier) {
    Pill(modifier, bg = if (buckled) Boong.GoodBg else Color(0xFFFFE7CC), height = 60.dp, padH = 26.dp) {
        BoongIcon(Icon.Belt, if (buckled) Boong.Good else Boong.ChipText, Modifier.size(32.dp), strokeWidth = 2.2f)
        Text(
            if (buckled) "안전벨트 착용 완료" else "안전벨트 미착용",
            fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = if (buckled) Boong.Good else Boong.ChipText,
        )
    }
}

@Composable
private fun LevelCard(wallet: Wallet) {
    val p = wallet.progress
    BoongCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        Column(Modifier.padding(horizontal = 40.dp, vertical = 34.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("드라이브 레벨", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Boong.Muted)
                Box(Modifier.weight(1f))
                Text("누적 안전운전 ${km(p.state.totalSafeKm)}km", fontSize = 24.sp, color = Boong.Muted)
            }
            VSpace(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lv.${p.level}", fontFamily = Jua, fontSize = 72.sp, color = Boong.Brown)
                HSpace(18.dp)
                Pill(height = 50.dp, padH = 22.dp) {
                    Text(wallet.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Boong.ChipText)
                }
            }
            ProgressBar(p.levelFraction, Modifier.padding(vertical = 22.dp))
            Text("Lv.${p.level + 1}까지 ${km(p.kmToNextLevel)}km 남았어요", fontSize = 26.sp, color = Boong.BrownSoft)
        }
    }
}

@Composable
private fun RecentCard(latest: TripEntity?, modifier: Modifier = Modifier) {
    BoongCard(modifier.fillMaxWidth(), padding = 0.dp) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 34.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("최근 드라이브", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Boong.Muted)
                Box(Modifier.weight(1f))
                if (latest != null) Text(whenText(latest.endAt), fontSize = 24.sp, color = Boong.Muted)
            }
            if (latest == null) {
                Text("아직 기록이 없어요.\n기어를 D로 바꾸고 출발해 보세요!", fontFamily = Jua, fontSize = 40.sp, color = Boong.Brown, lineHeight = 52.sp)
                Box(Modifier)
                return@Column
            }
            Text(
                if (latest.arrived) "목적지까지 안전하게 도착" else "${km(latest.distanceKm)}km 안전하게 달렸어요",
                fontFamily = Jua, fontSize = 46.sp, color = Boong.Brown, maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatTile("${km(latest.distanceKm)}km", "주행 거리", Modifier.weight(1f))
                StatTile(latest.smoothPercent()?.let { "$it%" } ?: "-", "부드러운 운전", Modifier.weight(1f))
                StatTile("+${latest.points}", "획득 포인트", Modifier.weight(1f))
            }
            // 보너스 칩: 안전벨트 · 규정 속도 · 목적지 도착
            val lines = remember(latest.id) { BoongRepository.decodeLines(latest.lines) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((key, label) in listOf("안전벨트" to "안전벨트", "규정 속도" to "규정 속도", "목적지" to "목적지 도착")) {
                    val got = lines.firstOrNull { it.label.startsWith(key) }
                    BonusChip(label, got?.points, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BonusChip(label: String, points: Int?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val on = points != null
    Row(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (on) Color.White else Boong.Cream)
            .border(2.dp, Boong.CardLine, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Text(label, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = if (on) Boong.BrownSoft else Boong.Dashed, maxLines = 1)
        Text(if (on) "+$points" else "-", fontFamily = Jua, fontSize = 24.sp, color = if (on) Boong.Fish else Boong.Dashed)
    }
}

@Composable
private fun GiftCard(boxes: Int, onOpenGift: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(Boong.Chip)
            .padding(start = 26.dp, top = 26.dp, bottom = 26.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Box(Modifier.size(92.dp).clip(RoundedCornerShape(30.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            GiftBoxIcon(Modifier.size(52.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("선물상자 ${boxes}개", fontFamily = Jua, fontSize = 42.sp, color = Boong.Brown)
            Text(
                if (boxes > 0) "안전운전으로 받은 선물이에요" else "안전운전 ${km(PointRules.GIFT_BOX_EVERY_SAFE_KM)}km마다 1개",
                fontSize = 24.sp, color = Boong.ChipText,
            )
        }
        BoongButton("열기", onOpenGift, enabled = boxes > 0, height = 84.dp, fontSize = 36, padH = 46.dp)
    }
}

/** 10km 미만은 소수 한 자리 (2.0 → "2"), 10km 이상은 정수 */
internal fun km(v: Double): String {
    if (v >= 10) return String.format(Locale.KOREA, "%.0f", v)
    val r = kotlin.math.round(v * 10) / 10.0
    return if (r == kotlin.math.floor(r)) r.toInt().toString() else String.format(Locale.KOREA, "%.1f", r)
}

/** '오늘 오전 8:42' / '어제 오후 6:10' / '11월 3일 오전 9:00' */
internal fun whenText(ms: Long): String {
    val now = Calendar.getInstance()
    val t = Calendar.getInstance().apply { timeInMillis = ms }
    val time = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(ms))
    val dayDiff = (now.get(Calendar.YEAR) - t.get(Calendar.YEAR)) * 366 + now.get(Calendar.DAY_OF_YEAR) - t.get(Calendar.DAY_OF_YEAR)
    return when (dayDiff) {
        0 -> "오늘 $time"
        1 -> "어제 $time"
        else -> SimpleDateFormat("M월 d일 ", Locale.KOREA).format(Date(ms)) + time
    }
}

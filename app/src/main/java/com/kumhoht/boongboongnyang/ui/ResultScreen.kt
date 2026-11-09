package com.kumhoht.boongboongnyang.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumhoht.boongboongnyang.data.BoongRepository
import com.kumhoht.boongboongnyang.data.TripEntity
import com.kumhoht.boongboongnyang.reward.Levels
import com.kumhoht.boongboongnyang.reward.Slot
import com.kumhoht.boongboongnyang.ui.pet.Expression
import com.kumhoht.boongboongnyang.ui.pet.PetView
import com.kumhoht.boongboongnyang.ui.theme.Boong
import com.kumhoht.boongboongnyang.ui.theme.Jua
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 운행 종료 결과 — 디자인 캔버스 v3 '운행 종료 결과'.
 * 줄이 하나씩 나타나고 합계가 올라간 뒤, 선물상자·레벨업이 뜬다. 감점 표시는 없다.
 */
@Composable
fun ResultScreen(
    trip: TripEntity,
    wallet: Wallet,
    equipped: Map<Slot, String>,
    onConfirm: () -> Unit,
    onOpenGift: () -> Unit,
) {
    val lines = remember(trip.id) { BoongRepository.decodeLines(trip.lines) }
    val shownLines = remember(trip.id) { mutableStateOf(0) }
    val total = remember(trip.id) { Animatable(0f) }
    var showExtras by remember(trip.id) { mutableStateOf(false) }

    LaunchedEffect(trip.id) {
        for (i in lines.indices) { delay(180); shownLines.value = i + 1 }
        total.animateTo(trip.points.toFloat(), tween(1100, easing = FastOutSlowInEasing))
        showExtras = true
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // 왼쪽: 반짝이는 창밖 풍경 + 놀란 붕붕냥 + 한마디
        Box(Modifier.width(820.dp).fillMaxHeight().clip(RoundedCornerShape(44.dp))) {
            PetView(equipped, Expression.SURPRISED, Modifier.fillMaxSize(), reactKey = trip.id, sparkles = true)
            SpeechBubble(Modifier.align(Alignment.TopCenter).padding(start = 48.dp, end = 48.dp, top = 48.dp).fillMaxWidth(), tailCenter = true) {
                Text(
                    catSpeech(trip.feedback), fontSize = 40.sp, fontWeight = FontWeight.Black, color = Boong.Brown,
                    lineHeight = 52.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 오른쪽: 결과 카드
        BoongCard(Modifier.weight(1f).fillMaxHeight(), padding = 0.dp, radius = 44.dp) {
            Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 44.dp, bottom = 40.dp)) {
                // 제목 줄: 제목 + (주행 로그 재생으로 만든 운행이면) 오른쪽 끝에 표시. 시간은 아래 줄
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("오늘의 드라이브", fontFamily = Jua, fontSize = 54.sp, color = Boong.Brown)
                    Box(Modifier.weight(1f))
                    if (trip.replay) {
                        Pill(height = 44.dp, padH = 18.dp) { Text("주행 로그 재생", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Boong.ChipText, softWrap = false) }
                    }
                }
                Text(timeRange(trip), fontSize = 26.sp, color = Boong.Muted)
                Column(Modifier.weight(1f).padding(top = 16.dp, bottom = 24.dp).verticalScroll(rememberScrollState())) {
                    lines.take(shownLines.value).forEach { l -> ResultLine(l.label, l.points) }
                    if (lines.isEmpty()) {
                        Text("짧은 운행이었어요. 다음엔 조금 더 달려 볼까요?", fontSize = 28.sp, color = Boong.BrownSoft, modifier = Modifier.padding(top = 12.dp))
                    }
                    AnimatedVisibility(showExtras && trip.levelAfter > trip.levelBefore, enter = fadeIn() + scaleIn()) {
                        Row(
                            Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp))
                                .background(Boong.GoodBg).padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BoongIcon(Icon.Star, Boong.Good, Modifier.size(40.dp))
                            HSpace(16.dp)
                            Text("레벨 업! Lv.${trip.levelAfter} · ${Levels.titleFor(trip.levelAfter)}", fontFamily = Jua, fontSize = 34.sp, color = Boong.Good)
                        }
                    }
                }
                AnimatedVisibility(showExtras && trip.giftBoxes > 0, enter = fadeIn() + scaleIn()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Boong.Chip)
                            .padding(start = 24.dp, top = 16.dp, bottom = 16.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        GiftBoxIcon(Modifier.size(48.dp))
                        Text("선물상자 ${trip.giftBoxes}개 도착!", fontFamily = Jua, fontSize = 36.sp, color = Boong.Brown, modifier = Modifier.weight(1f))
                        BoongButton("열기", onOpenGift, enabled = wallet.closedBoxes > 0, height = 64.dp, fontSize = 30, padH = 40.dp)
                    }
                }
                // 합계
                Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("합계", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Boong.Brown)
                    Box(Modifier.weight(1f))
                    BoneCount(total.value.toInt(), prefix = "+", big = true)
                }
                ProgressBar(wallet.progress.boxFraction, Modifier.padding(top = 12.dp), height = 18.dp)
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("다음 선물상자까지 ${wallet.progress.kmToNextBox}km", fontSize = 26.sp, color = Boong.BrownSoft)
                        Text("Lv.${wallet.progress.level + 1}까지 ${wallet.progress.kmToNextLevel}km", fontSize = 26.sp, color = Boong.BrownSoft)
                    }
                    BoongButton("확인", onConfirm, height = 76.dp, fontSize = 34, padH = 64.dp)
                }
            }
        }
    }
}

@Composable
private fun ResultLine(label: String, points: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val h = 2.dp.toPx()
                drawRect(Boong.Divider, topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconTile(iconFor(label), 56.dp, 32.dp, Boong.Chip, Boong.ChipText)
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Boong.Brown, modifier = Modifier.weight(1f))
        Text("+$points", fontFamily = Jua, fontSize = 40.sp, color = Boong.Brown)
    }
}

private fun iconFor(label: String): Icon = when {
    label.startsWith("주행") -> Icon.Road
    label.startsWith("부드러운") -> Icon.Wave
    label.startsWith("규정") -> Icon.Gauge
    label.startsWith("목적지") -> Icon.Flag
    label.startsWith("안전벨트") -> Icon.Belt
    else -> Icon.Star
}

private fun timeRange(t: TripEntity): String {
    val f = SimpleDateFormat("a h:mm", Locale.KOREA)
    val end = SimpleDateFormat("h:mm", Locale.KOREA)
    return "${f.format(Date(t.startAt))} – ${end.format(Date(t.endAt))}"
}

/** 붕붕냥으로 바뀌기 전에 저장된 운행의 한마디도 새 이름으로 보여 준다 */
private fun catSpeech(s: String): String = s
    .replace("붕붕이가", "붕붕냥이")
    .replace("붕붕이", "붕붕냥")
    .replace("목적지까지 안전하게 도착했어요!", "목적지까지 안전하게 도착했다 냥!")

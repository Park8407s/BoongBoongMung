package com.kumhoht.boongboongmung.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kumhoht.boongboongmung.data.BoongRepository
import com.kumhoht.boongboongmung.data.TripEntity
import com.kumhoht.boongboongmung.reward.Levels
import com.kumhoht.boongboongmung.reward.Slot
import com.kumhoht.boongboongmung.ui.pet.Expression
import com.kumhoht.boongboongmung.ui.pet.PetView
import com.kumhoht.boongboongmung.ui.theme.Boong
import kotlinx.coroutines.delay

/**
 * 운행 종료 결과 화면: 붕붕이와 만나는 핵심 순간 (1~2초 연출).
 * 포인트 카운트업 → 선물상자 도착 → 레벨업 순으로 보여준다. 감점 표시는 없다.
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
        // 줄이 하나씩 나타나고 합계가 올라간다
        for (i in lines.indices) { delay(180); shownLines.value = i + 1 }
        total.animateTo(trip.points.toFloat(), tween(1100, easing = FastOutSlowInEasing))
        showExtras = true
    }

    // 피그마 '그림 2. 운행 종료 결과 화면' 1682×1210: 캐릭터 카드 : 결과 카드 ≈ 1 : 1.34, 카드 간격 40
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        BoongCard(Modifier.weight(1f).fillMaxHeight(), padding = 20.dp) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                PetView(equipped, Expression.SURPRISED, Modifier.fillMaxSize(), reactKey = trip.id)
            }
            VSpace(20.dp)
            Text(
                trip.feedback,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        BoongCard(Modifier.weight(1.34f).fillMaxHeight(), padding = 40.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘의 드라이브", style = MaterialTheme.typography.headlineSmall)
                if (trip.replay) {
                    HSpace(12.dp)
                    Pill(bg = Boong.Chip) { Text("주행 로그 재생", style = MaterialTheme.typography.labelMedium) }
                }
            }
            VSpace(14.dp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                lines.take(shownLines.value).forEach { l ->
                    LineRow(l.label, "+${l.points}")
                    ThinDivider()
                }
                if (lines.isEmpty()) {
                    Text("짧은 운행이었어요. 다음엔 조금 더 달려 볼까요?", style = MaterialTheme.typography.bodyLarge, color = Boong.BrownSoft)
                }
                AnimatedVisibility(showExtras && trip.giftBoxes > 0, enter = fadeIn() + scaleIn()) {
                    Row(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Boong.Chip)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GiftBoxIcon(Modifier.size(44.dp))
                        HSpace(12.dp)
                        Text("선물상자 ${trip.giftBoxes}개 도착!", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        BoongButton("열기", onOpenGift, enabled = wallet.closedBoxes > 0)
                    }
                }
                AnimatedVisibility(showExtras && trip.levelAfter > trip.levelBefore, enter = fadeIn() + scaleIn()) {
                    Row(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Boong.Bone.copy(alpha = 0.3f))
                            .padding(14.dp),
                    ) {
                        Text(
                            "레벨 업! Lv.${trip.levelAfter} · ${Levels.titleFor(trip.levelAfter)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            VSpace(8.dp)
            // 합계 (피그마: '합계' 26 Medium, 뼈다귀 + Jua 40)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("합계", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Boong.Brown)
                Box(Modifier.weight(1f))
                BoneCount(total.value.toInt(), prefix = "+", big = true)
            }
            VSpace(10.dp)
            ProgressBar(wallet.progress.boxFraction)
            VSpace(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "다음 선물상자까지 ${wallet.progress.kmToNextBox}km · Lv.${wallet.progress.level + 1}까지 ${wallet.progress.kmToNextLevel}km",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                BoongButton("확인", onConfirm)
            }
        }
    }
}

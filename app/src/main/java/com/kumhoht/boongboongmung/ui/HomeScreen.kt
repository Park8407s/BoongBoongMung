package com.kumhoht.boongboongmung.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kumhoht.boongboongmung.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kumhoht.boongboongmung.data.TripEntity
import com.kumhoht.boongboongmung.data.smoothPercent
import com.kumhoht.boongboongmung.reward.PointRules
import com.kumhoht.boongboongmung.reward.Slot
import com.kumhoht.boongboongmung.ui.pet.Expression
import com.kumhoht.boongboongmung.ui.pet.PetView
import com.kumhoht.boongboongmung.ui.theme.Boong
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 홈 (주차 중): 붕붕이가 반겨주고, 최근 드라이브 요약·레벨·포인트·선물상자를 보여준다.
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
    // 출발 전 인사: 화면에 들어오면 잠깐 '반가워요' 후 기본 표정
    var greeting by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2500); greeting = false }
    LaunchedEffect(reactKey) {
        if (reactKey > 0) { greeting = true; delay(1500); greeting = false }
    }
    val expression = when {
        beltWarn -> Expression.DEFAULT
        greeting -> Expression.HAPPY
        needsRest -> Expression.SLEEPY
        else -> Expression.DEFAULT
    }
    val line = when {
        beltWarn -> "출발 전에 안전벨트 매요! 붕붕이도 꼭 맸어요"
        needsRest -> "2시간 넘게 달렸어요. 15분 쉬고 출발하면 휴식 보너스 +${PointRules.REST_BONUS}!"
        greeting && latest == null -> "안녕하세요! 저는 붕붕이에요"
        else -> "오늘도 안전하게 달려요!"
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        BoongCard(Modifier.weight(1.15f).fillMaxHeight(), padding = 14.dp) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                PetView(equipped, expression, Modifier.fillMaxSize(), reactKey = reactKey.takeIf { it > 0 })
            }
            VSpace(10.dp)
            Text(
                "“$line”",
                style = MaterialTheme.typography.titleMedium,
                color = if (beltWarn) Boong.Gift else Color.Unspecified,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BoongCard(Modifier.fillMaxWidth().weight(1f)) {
                Text("최근 드라이브", style = MaterialTheme.typography.titleLarge)
                VSpace(10.dp)
                if (latest == null) {
                    Text("아직 기록이 없어요.\n기어를 D로 바꾸고 출발해 보세요!", style = MaterialTheme.typography.bodyLarge, color = Boong.BrownSoft)
                } else {
                    val pct = latest.smoothPercent()
                    Text(
                        "${String.format(Locale.KOREA, "%.0f", latest.distanceKm)}km" +
                            (pct?.let { " · 부드러운 운전 $it%" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                VSpace(4.dp)
                Text("칭호: ${wallet.title}", style = MaterialTheme.typography.bodyLarge)
                if (wallet.progress.state.streakDays > 0) {
                    VSpace(4.dp)
                    Text("연속 안전운전 ${wallet.progress.state.streakDays}일째", style = MaterialTheme.typography.bodyLarge)
                }
                if (beltUnbuckled != null) {
                    VSpace(4.dp)
                    Text(
                        if (beltUnbuckled) "안전벨트: 안 맴 · 출발 전에 매 주세요" else "안전벨트: 맴 ✓",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (beltUnbuckled) Boong.Gift else Boong.Good,
                    )
                }
                VSpace(14.dp)
                Text(
                    "다음 선물상자까지 ${wallet.progress.kmToNextBox}km · Lv.${wallet.progress.level + 1}까지 ${wallet.progress.kmToNextLevel}km",
                    style = MaterialTheme.typography.labelMedium,
                )
                VSpace(6.dp)
                ProgressBar(wallet.progress.boxFraction)
            }

            BoongCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GiftBoxIcon(Modifier.size(64.dp))
                    HSpace(16.dp)
                    Column(Modifier.weight(1f)) {
                        Text("선물상자 ${wallet.closedBoxes}개", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (wallet.closedBoxes > 0) "주차 중에 열어 보세요" else "안전운전 ${PointRules.GIFT_BOX_EVERY_SAFE_KM}km마다 1개",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    BoongButton("열어보기", onOpenGift, enabled = wallet.closedBoxes > 0)
                }
            }
        }
    }
}

/** 선물상자 아이콘 (피그마 '아이콘/선물상자') */
@Composable
fun GiftBoxIcon(modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.ic_gift), contentDescription = null,
        modifier = modifier, contentScale = ContentScale.Fit,
    )
}

package com.kumhoht.boongboongmung.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kumhoht.boongboongmung.data.TripEntity
import com.kumhoht.boongboongmung.data.smoothPercent
import com.kumhoht.boongboongmung.ui.theme.Boong
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 기록: 날짜별 주행거리, 부드러운 운전 비율, 획득 포인트 */
@Composable
fun RecordsScreen(
    trips: List<TripEntity>,
    wallet: Wallet,
    onOpen: (TripEntity) -> Unit,
    onReset: () -> Unit,
    onShutdown: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val week = remember(trips) { trips.filter { now - it.endAt < 7L * 24 * 60 * 60 * 1000 } }
    val weekKm = week.sumOf { it.distanceKm }
    val judged = week.sumOf { it.judgedKm }
    val smooth = week.sumOf { it.smoothKm }
    val weekPoints = week.sumOf { it.points }
    val fmt = remember { SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREA) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        BoongCard(Modifier.weight(0.8f).fillMaxHeight()) {
            Text("이번 주", style = MaterialTheme.typography.titleLarge)
            VSpace(12.dp)
            LineRow("주행거리", "${String.format(Locale.KOREA, "%.1f", weekKm)}km")
            ThinDivider()
            LineRow("부드러운 운전", if (judged > 0) "${smooth * 100 / judged}%" else "-")
            ThinDivider()
            LineRow("운행", "${week.size}회")
            ThinDivider()
            LineRow("획득 포인트", "+${weekPoints.withComma()}")
            ThinDivider()
            LineRow("연속 안전운전", "${wallet.progress.state.streakDays}일")
            VSpace(16.dp)
            Text("누적 안전운전 ${wallet.progress.state.totalSafeKm}km · Lv.${wallet.progress.level}", style = MaterialTheme.typography.bodyLarge)
            VSpace(8.dp)
            ProgressBar(wallet.progress.levelFraction, color = Boong.Good)
            VSpace(6.dp)
            Text("다음 레벨까지 ${wallet.progress.kmToNextLevel}km", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoongButton("붕붕멍 끄기", onShutdown, color = Boong.BrownSoft)
                BoongButton("데이터 초기화", onReset, color = Boong.BrownSoft)
            }
        }

        BoongCard(Modifier.weight(1.2f).fillMaxHeight()) {
            Text("운행 기록", style = MaterialTheme.typography.titleLarge)
            VSpace(8.dp)
            if (trips.isEmpty()) {
                Text("아직 기록이 없어요.", style = MaterialTheme.typography.bodyLarge, color = Boong.BrownSoft)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(trips, key = { it.id }) { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(t) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.format(Date(t.endAt)) + if (t.replay) "  · 재생" else "", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${String.format(Locale.KOREA, "%.1f", t.distanceKm)}km" +
                                    (t.smoothPercent()?.let { " · 부드러운 운전 $it%" } ?: "") +
                                    (t.speedLimitRatio?.let { " · 규정 속도 ${(it * 100).toInt()}%" } ?: ""),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        BoneCount(t.points, prefix = "+")
                    }
                    ThinDivider()
                }
            }
        }
    }
}

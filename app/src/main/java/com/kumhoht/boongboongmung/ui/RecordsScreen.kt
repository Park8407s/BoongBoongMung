package com.kumhoht.boongboongmung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumhoht.boongboongmung.data.TripEntity
import com.kumhoht.boongboongmung.data.smoothPercent
import com.kumhoht.boongboongmung.ui.pet.Expression
import com.kumhoht.boongboongmung.ui.pet.PetView
import com.kumhoht.boongboongmung.ui.theme.Boong
import com.kumhoht.boongboongmung.ui.theme.Jua
import java.util.Calendar

/**
 * 기록 — 디자인 캔버스 v3 '기록'.
 * 왼쪽: 이번 주(월~일) 요약 + 요일별 막대 / 오른쪽: 최근 기록 목록 + 붕붕냥 끄기·데이터 초기화
 */
@Composable
fun RecordsScreen(
    trips: List<TripEntity>,
    wallet: Wallet,
    onOpen: (TripEntity) -> Unit,
    onReset: () -> Unit,
    onShutdown: () -> Unit,
) {
    // 이번 주 월요일 0시
    val weekStart = remember {
        Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }.timeInMillis
    }
    val day = 24L * 60 * 60 * 1000
    val week = trips.filter { it.endAt >= weekStart && it.endAt < weekStart + 7 * day }
    val weekKm = week.sumOf { it.distanceKm }
    val judged = week.sumOf { it.judgedKm }
    val smooth = week.sumOf { it.smoothKm }
    val weekPoints = week.sumOf { it.points }
    val perDay = DoubleArray(7).also { arr -> week.forEach { t -> arr[((t.endAt - weekStart) / day).toInt().coerceIn(0, 6)] += t.distanceKm } }
    val today = ((System.currentTimeMillis() - weekStart) / day).toInt().coerceIn(0, 6)
    val md = { ms: Long -> Calendar.getInstance().apply { timeInMillis = ms }.let { "${it.get(Calendar.MONTH) + 1}월 ${it.get(Calendar.DAY_OF_MONTH)}일" } }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // 왼쪽: 이번 주
        BoongCard(Modifier.width(860.dp).fillMaxHeight(), padding = 0.dp, radius = 44.dp) {
            Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 40.dp, bottom = 36.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("${md(weekStart)} – ${md(weekStart + 6 * day)}", fontSize = 26.sp, color = Boong.Muted)
                        VSpace(6.dp)
                        Text("이번 주 드라이브", fontFamily = Jua, fontSize = 56.sp, color = Boong.Brown)
                        VSpace(6.dp)
                        Text(
                            if (week.isEmpty()) "이번 주는 아직 달리지 않았어요" else "${week.size}번 달렸고, 연속 안전운전 ${wallet.progress.state.streakDays}일째예요",
                            fontSize = 26.sp, color = Boong.BrownSoft,
                        )
                    }
                    PetView(emptyMap(), Expression.SLEEPY, Modifier.size(200.dp), showBackground = false)
                }
                VSpace(24.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatTile("${km(weekKm)}km", "주행 거리", Modifier.weight(1f), labelFirst = true, valueSize = 60)
                    StatTile(if (judged > 0) "${smooth * 100 / judged}%" else "-", "부드러운 운전", Modifier.weight(1f), labelFirst = true, valueSize = 60)
                    StatTile("+${weekPoints.withComma()}", "획득 포인트", Modifier.weight(1f), labelFirst = true, valueSize = 60)
                }
                VSpace(24.dp)
                WeekChart(perDay, today, Modifier.weight(1f).fillMaxWidth())
            }
        }

        // 오른쪽: 최근 기록 + 설정
        BoongCard(Modifier.weight(1f).fillMaxHeight(), padding = 0.dp, radius = 44.dp) {
            Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 32.dp)) {
                Text("최근 기록", fontFamily = Jua, fontSize = 44.sp, color = Boong.Brown)
                VSpace(10.dp)
                if (trips.isEmpty()) {
                    Text("아직 기록이 없어요.", fontSize = 28.sp, color = Boong.BrownSoft, modifier = Modifier.padding(top = 12.dp))
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(trips, key = { it.id }) { t -> TripRow(t, onOpen) }
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(Boong.Nav))
                VSpace(20.dp)
                SettingRow(Icon.Power, Boong.Nav, Boong.Brown, "붕붕냥 끄기", "기록과 차량 신호 수신을 멈춰요.\n앱을 열어 붕붕냥에게 인사하면 다시 시작돼요.") {
                    OutlineButton("끄기", onShutdown)
                }
                VSpace(16.dp)
                SettingRow(Icon.Reset, Boong.DangerBg, Boong.Danger, "데이터 초기화", "주차 중에만 할 수 있고, 한 번 더 확인해요.") {
                    OutlineButton("초기화", onReset, color = Boong.Danger)
                }
            }
        }
    }
}

@Composable
private fun TripRow(t: TripEntity, onOpen: (TripEntity) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(t) }
            .drawBehind { drawRect(Boong.Divider, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(whenText(t.endAt) + if (t.replay) " · 재생" else "", fontSize = 24.sp, color = Boong.Muted)
            Text(
                "${km(t.distanceKm)}km" + (t.smoothPercent()?.let { " · 부드러운 운전 $it%" } ?: ""),
                fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Boong.Brown,
            )
        }
        BoneIcon(Modifier.size(36.dp))
        HSpace(8.dp)
        Text("+${t.points.withComma()}", fontFamily = Jua, fontSize = 42.sp, color = Boong.Brown)
    }
}

@Composable
private fun SettingRow(icon: Icon, bg: androidx.compose.ui.graphics.Color, tint: androidx.compose.ui.graphics.Color, title: String, desc: String, button: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconTile(icon, 64.dp, 34.dp, bg, tint, radius = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Boong.Brown)
            Text(desc, fontSize = 21.sp, color = Boong.Muted, lineHeight = 29.sp, softWrap = false)
        }
        button()
    }
}

/** 요일별 주행거리 막대 (v3: 폭 64, 오늘은 진한 주황 + '오늘' 알약, 0km 는 얇은 막대, 미래는 점선) */
@Composable
private fun WeekChart(perDay: DoubleArray, today: Int, modifier: Modifier = Modifier) {
    val maxKm = (perDay.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val names = listOf("월", "화", "수", "목", "금", "토", "일")
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier.clip(shape).border(2.dp, Boong.Divider, shape).padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (i in 0 until 7) {
            val v = perDay[i]
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i > today) "" else if (v > 0) km(v) else "0", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (i == today) Boong.Brown else Boong.Muted)
                VSpace(8.dp)
                Box(Modifier.weight(1f).width(64.dp), contentAlignment = Alignment.BottomCenter) {
                    val frac = (v / maxKm).toFloat()
                    when {
                        i > today -> Box(
                            Modifier.fillMaxWidth().fillMaxHeight(0.25f).drawBehind {
                                drawRoundRect(
                                    Boong.Dashed, cornerRadius = CornerRadius(18.dp.toPx()),
                                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                                )
                            }
                        )
                        v <= 0 -> Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Boong.Divider))
                        else -> Box(
                            Modifier.fillMaxWidth().fillMaxHeight(frac.coerceAtLeast(0.06f)).clip(RoundedCornerShape(18.dp))
                                .background(if (i == today) Boong.Accent else androidx.compose.ui.graphics.Color(0xFFF6D3A3))
                        )
                    }
                }
                VSpace(12.dp)
                if (i == today) {
                    Pill(bg = Boong.Brown, height = 40.dp, padH = 16.dp) { Text("오늘", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Boong.Cream) }
                } else {
                    Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) { Text(names[i], fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Boong.Muted) }
                }
            }
        }
    }
}

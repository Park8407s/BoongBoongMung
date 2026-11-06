package com.kumhoht.boongboongmung.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kumhoht.boongboongmung.drive.ReplayProgress
import com.kumhoht.boongboongmung.replay.ReplayScenario
import com.kumhoht.boongboongmung.replay.ReplayScenarios
import com.kumhoht.boongboongmung.reward.Catalog
import com.kumhoht.boongboongmung.reward.GiftBox
import com.kumhoht.boongboongmung.reward.GiftReward
import com.kumhoht.boongboongmung.ui.pet.ItemIcon
import com.kumhoht.boongboongmung.ui.theme.Boong
import com.kumhoht.boongboongmung.vehicle.SignalStatus
import java.util.Locale

/** 화면 전체를 덮는 반투명 배경 + 가운데 카드 */
@Composable
fun Overlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            content()
        }
    }
}

/** 선물상자 열기 (확률 공개) */
@Composable
fun GiftDialog(
    state: GiftUi,
    remaining: Int,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onEquip: (String) -> Unit,
) {
    Overlay(onClose) {
        BoongCard(Modifier.width(560.dp), padding = 28.dp) {
            when (state) {
                GiftUi.Ready, GiftUi.Closed -> {
                    val shake = rememberInfiniteTransition(label = "shake")
                    val angle by shake.animateFloat(
                        -6f, 6f, infiniteRepeatable(tween(160, easing = LinearEasing), RepeatMode.Reverse), label = "angle",
                    )
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GiftBoxIcon(Modifier.size(140.dp).rotate(angle))
                    }
                    VSpace(12.dp)
                    Text("선물상자 ${remaining}개", style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    VSpace(12.dp)
                    Text("확률 공개", style = MaterialTheme.typography.titleMedium)
                    GiftBox.odds.forEach { o -> LineRow(o.label, "${o.percent}%") }
                    Text("선물상자는 안전운전으로만 얻을 수 있고 구매할 수 없어요.", style = MaterialTheme.typography.labelMedium)
                    VSpace(16.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                        BoongButton("닫기", onClose, color = Boong.BrownSoft)
                        BoongButton("열기", onOpen, enabled = remaining > 0)
                    }
                }
                is GiftUi.Opened -> {
                    val pop = remember { Animatable(0.3f) }
                    LaunchedEffect(state) { pop.animateTo(1f, tween(450)) }
                    Box(Modifier.fillMaxWidth().scale(pop.value), contentAlignment = Alignment.Center) {
                        when (val r = state.reward) {
                            is GiftReward.Bones -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BoneIcon(Modifier.size(120.dp))
                                Text("뼈다귀 +${r.amount}", style = MaterialTheme.typography.headlineMedium)
                            }
                            is GiftReward.ItemReward -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val item = Catalog.item(r.itemId)
                                if (item != null) ItemIcon(item, Modifier.size(width = 200.dp, height = 140.dp))
                                Text("${item?.name ?: r.itemId} 획득!", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    VSpace(20.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                        val reward = state.reward
                        if (reward is GiftReward.ItemReward) {
                            BoongButton("바로 장착", { onEquip(reward.itemId) }, color = Boong.Good)
                        }
                        if (remaining > 0) BoongButton("하나 더 열기", onOpen)
                        BoongButton("확인", onClose, color = Boong.BrownSoft)
                    }
                }
            }
        }
    }
}

/** 확인 창 (취소 / 실행) */
@Composable
fun ConfirmDialog(title: String, body: String, confirmText: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Overlay(onCancel) {
        BoongCard(Modifier.width(560.dp), padding = 28.dp) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            VSpace(12.dp)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            VSpace(20.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                BoongButton("취소", onCancel, color = Boong.BrownSoft)
                BoongButton(confirmText, onConfirm, color = Boong.Gift)
            }
        }
    }
}

/** 데이터 초기화 확인 */
@Composable
fun ResetDialog(onConfirm: () -> Unit, onCancel: () -> Unit) = ConfirmDialog(
    title = "데이터를 모두 지울까요?",
    body = "운행 기록, 뼈다귀, 선물상자, 아이템, 칭호, 레벨이 모두 지워지고 처음 상태로 돌아가요.\n지운 데이터는 되돌릴 수 없어요.",
    confirmText = "초기화",
    onConfirm = onConfirm,
    onCancel = onCancel,
)

/** 붕붕멍 끄기 확인 */
@Composable
fun ShutdownDialog(onConfirm: () -> Unit, onCancel: () -> Unit) = ConfirmDialog(
    title = "붕붕멍을 끌까요?",
    body = "꺼져 있는 동안은 운전해도 포인트가 쌓이지 않아요.\n붕붕멍 앱을 다시 열면 다시 켜져요.",
    confirmText = "끄기",
    onConfirm = onConfirm,
    onCancel = onCancel,
)

/** 시연 패널: 주행 로그 재생 모드 + 차량 신호 상태 (제목을 길게 누르면 열린다) */
@Composable
fun DemoPanel(
    signals: Map<String, SignalStatus>,
    onReplay: (ReplayScenario) -> Unit,
    onMarkAllSeen: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Overlay(onClose) {
        BoongCard(Modifier.width(900.dp), padding = 24.dp) {
            Row {
                Column(Modifier.weight(1.2f)) {
                    Text("주행 로그 재생 모드", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "에뮬레이터는 차가 움직이지 않아 내비 제한속도·도착 이벤트가 오지 않아요.\n저장된 주행 데이터를 같은 집계 엔진으로 재생해 시연해요.",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    VSpace(10.dp)
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ReplayScenarios.all.forEach { sc ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Boong.Cream)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("시나리오 ${sc.id}. ${sc.title}", style = MaterialTheme.typography.titleMedium)
                                    Text(sc.description, style = MaterialTheme.typography.labelMedium)
                                }
                                BoongButton("재생", { onReplay(sc) })
                            }
                        }
                    }
                }
                HSpace(24.dp)
                Column(Modifier.weight(0.8f)) {
                    Text("차량 신호", style = MaterialTheme.typography.titleLarge)
                    VSpace(8.dp)
                    signals.forEach { (name, st) ->
                        LineRow(
                            name,
                            when (st) {
                                SignalStatus.OK -> "수신"
                                SignalStatus.WAITING -> "대기"
                                SignalStatus.UNSUPPORTED -> "미지원"
                            },
                        )
                    }
                    VSpace(12.dp)
                    BoongButton("결과 모두 확인 처리", onMarkAllSeen, color = Boong.BrownSoft)
                    VSpace(8.dp)
                    BoongButton("데이터 초기화", onReset, color = Boong.Gift)
                    VSpace(8.dp)
                    BoongButton("닫기", onClose)
                }
            }
        }
    }
}

/**
 * Restriction Guard: 주행 중에는 앱 UI 를 쓰지 않는다.
 * (실차·에뮬레이터에서는 플랫폼 주행 규제가 앱 화면을 먼저 가린다. 이 화면은 그 사이·재생 모드용 최소 화면)
 */
@Composable
fun DrivingGuard(replay: ReplayProgress?, onStopReplay: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Boong.Frame), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("운전에 집중해 주세요", style = MaterialTheme.typography.titleLarge, color = Color(0xFFBFAF9F))
            if (replay != null) {
                VSpace(24.dp)
                Text(
                    "주행 로그 재생 중 · ${replay.title} · ${String.format(Locale.KOREA, "%.1f", replay.distanceKm)}km",
                    style = MaterialTheme.typography.bodyLarge, color = Color(0xFF8F8072),
                )
                VSpace(12.dp)
                Box(Modifier.width(420.dp)) { ProgressBar(replay.fraction) }
                VSpace(20.dp)
                BoongButton("재생 중지", onStopReplay, color = Color(0xFF5A4A3E))
            }
        }
    }
}

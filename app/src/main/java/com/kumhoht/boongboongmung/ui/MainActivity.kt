package com.kumhoht.boongboongmung.ui

import android.Manifest
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.kumhoht.boongboongmung.ui.theme.Jua
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kumhoht.boongboongmung.drive.DrivePhase
import com.kumhoht.boongboongmung.drive.DriveService
import com.kumhoht.boongboongmung.drive.GearState
import com.kumhoht.boongboongmung.reward.Catalog
import com.kumhoht.boongboongmung.ui.theme.Boong
import com.kumhoht.boongboongmung.ui.theme.BoongTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) vm.onPermissionsGranted()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DriveService.start(this)
        requestRuntimePermissions()
        setContent { BoongTheme { BoongRoot(vm) } }
    }

    /** 차속(CAR_SPEED)은 위험 권한이라 사용자 허용이 필요하다. */
    private fun requestRuntimePermissions() {
        val wanted = buildList {
            add(CAR_SPEED)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    companion object {
        private const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoongRoot(vm: MainViewModel) {
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val wardrobe by vm.wardrobe.collectAsStateWithLifecycle()
    val latest by vm.latestTrip.collectAsStateWithLifecycle()
    val unseen by vm.unseenTrip.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val gift by vm.gift.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val react by vm.react.collectAsStateWithLifecycle()
    val review by vm.reviewTrip.collectAsStateWithLifecycle()
    val signals by vm.signals.collectAsStateWithLifecycle()
    var demo by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    var askShutdown by remember { mutableStateOf(false) }
    val activity = LocalContext.current as Activity

    // 주행 상태별 동작: 주행 중(기어 D/R 또는 주행 규제)에는 앱 UI 없음
    val driving = engine.phase == DrivePhase.DRIVING || engine.replay != null || live.restricted
    if (driving) {
        DrivingGuard(engine.replay, vm::stopReplay)
        return
    }

    Box(Modifier.fillMaxSize().background(Boong.Cream)) {
        Column(Modifier.fillMaxSize()) {
            // 상단 바 (v3: 흰 바탕 높이 112, 아래 선 2 #F0E2D3)
            val resultShown = unseen != null || review != null
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(Boong.Card)
                    .drawBehind {
                        drawRect(Boong.CardLine, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
                    }
                    .padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "붕붕멍",
                    fontFamily = Jua, fontSize = 48.sp, color = Boong.Brown,
                    modifier = Modifier.combinedClickable(onClick = { vm.select(Tab.HOME) }, onLongClick = { demo = true }),
                )
                HSpace(36.dp)
                if (!resultShown) {
                    val tabs = listOf(Tab.HOME, Tab.SHOP, Tab.RECORDS)
                    NavTabs(listOf("홈", "상점", "기록"), tabs.indexOf(tab)) { vm.select(tabs[it]) }
                } else {
                    Box(Modifier.width(2.dp).height(40.dp).background(Color(0xFFE6D3BF)))
                    HSpace(28.dp)
                    Text("운행 종료 · 주차 확인됨", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Boong.BrownSoft)
                }
                Box(Modifier.weight(1f))
                Pill(padH = 28.dp) {
                    BoneIcon(Modifier.size(40.dp))
                    Text(wallet.balance.withComma(), fontSize = 32.sp, fontWeight = FontWeight.Black, color = Boong.Brown)
                }
                HSpace(16.dp)
                Pill(bg = Boong.Brown) {
                    Text("Lv.${wallet.progress.level}", fontFamily = Jua, fontSize = 32.sp, color = Boong.Cream)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp, vertical = 36.dp)) {
                // 운행 종료 결과 → 앱을 열면 결과 화면을 먼저 보여준다
                val resultTrip = review ?: unseen
                when {
                    resultTrip != null -> ResultScreen(
                        trip = resultTrip,
                        wallet = wallet,
                        equipped = wardrobe.equipped,
                        onConfirm = { vm.confirmResult(resultTrip) },
                        onOpenGift = vm::showGift,
                    )
                    tab == Tab.SHOP -> ShopScreen(wallet, wardrobe, react, vm::buy, vm::equip, vm::unequip)
                    tab == Tab.RECORDS -> RecordsScreen(trips, wallet, onOpen = vm::review, onReset = { askReset = true }, onShutdown = { askShutdown = true })
                    else -> HomeScreen(
                        wallet = wallet,
                        equipped = wardrobe.equipped,
                        latest = latest,
                        needsRest = vm.needsRest(System.currentTimeMillis()),
                        reactKey = react,
                        onOpenGift = vm::showGift,
                        beltUnbuckled = live.beltUnbuckled,
                        beforeDrive = live.gear != GearState.DRIVE && live.gear != GearState.REVERSE,
                    )
                }
            }
        }

        AnimatedVisibility(
            toast != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Boong.Brown)
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) { Text(toast ?: "", color = Color.White, style = MaterialTheme.typography.bodyLarge) }
        }
    }

    if (gift != GiftUi.Closed) {
        GiftDialog(
            state = gift,
            remaining = wallet.closedBoxes,
            onOpen = vm::openGift,
            onClose = vm::closeGift,
            onEquip = { id ->
                Catalog.item(id)?.let { vm.equip(it) }
                vm.closeGift()
            },
        )
    }
    if (demo) {
        DemoPanel(
            signals = signals,
            onReplay = { sc -> demo = false; vm.startReplay(sc) },
            onMarkAllSeen = { vm.markAllSeen(); demo = false },
            onReset = { demo = false; askReset = true },
            onClose = { demo = false },
        )
    }
    if (askReset) {
        ResetDialog(
            onConfirm = { askReset = false; vm.resetData() },
            onCancel = { askReset = false },
        )
    }
    if (askShutdown) {
        ShutdownDialog(
            onConfirm = { askShutdown = false; DriveService.shutdown(activity) },
            onCancel = { askShutdown = false },
        )
    }
}

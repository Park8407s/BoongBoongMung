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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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

    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(Boong.Frame).padding(14.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Boong.Cream)
                .padding(start = 26.dp, end = 26.dp, top = 20.dp, bottom = 26.dp)
        ) {
            // 상단 바 (피그마: 흰 둥근 막대, 높이 64)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Boong.Card)
                    .padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "붕붕멍",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.combinedClickable(onClick = { vm.select(Tab.HOME) }, onLongClick = { demo = true }),
                )
                HSpace(28.dp)
                val resultShown = unseen != null || review != null
                if (!resultShown) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("홈", tab == Tab.HOME, { vm.select(Tab.HOME) })
                        Chip("꾸미기", tab == Tab.SHOP, { vm.select(Tab.SHOP) })
                        Chip("기록", tab == Tab.RECORDS, { vm.select(Tab.RECORDS) })
                    }
                }
                Box(Modifier.weight(1f))
                BoneCount(wallet.balance)
                HSpace(30.dp)
                Box(
                    Modifier
                        .height(36.dp)
                        .widthIn(min = 100.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Boong.Chip)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Lv.${wallet.progress.level}", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Boong.Brown) }
            }
            VSpace(20.dp)

            Box(Modifier.weight(1f).fillMaxWidth()) {
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
}

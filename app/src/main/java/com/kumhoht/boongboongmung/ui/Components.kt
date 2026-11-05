package com.kumhoht.boongboongmung.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumhoht.boongboongmung.ui.theme.Boong
import com.kumhoht.boongboongmung.ui.theme.Jua
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

fun Int.withComma(): String = NumberFormat.getNumberInstance(Locale.KOREA).format(this)

// ------------------------------------------------------------------ 아이콘 (v3 선 아이콘, 24×24 좌표)

enum class Icon(val paths: List<String>, val circles: List<Triple<Float, Float, Float>> = emptyList(), val rects: List<FloatArray> = emptyList()) {
    Gift(listOf("M3 13h18", "M12 9v12", "M12 9c-1.5-3.5-6-4-6-1.5S9 9 12 9c3 0 6 0 6-1.5S13.5 5.5 12 9"), rects = listOf(floatArrayOf(3f, 9f, 18f, 12f, 2f))),
    Belt(listOf("M8 21v-7a4 4 0 0 1 8 0v7", "M9 10l7 9"), circles = listOf(Triple(12f, 5f, 2.5f))),
    Steering(listOf("M12 3v6.5", "M4.5 16.5l5.4-3", "M19.5 16.5l-5.4-3"), circles = listOf(Triple(12f, 12f, 9f), Triple(12f, 12f, 2.5f))),
    Road(listOf("M5 21L9.5 3", "M19 21L14.5 3", "M12 6v2.5", "M12 11.5v2.5", "M12 17v2.5")),
    Wave(listOf("M2.5 13c2.4-4.5 4.8-4.5 7.2 0s4.8 4.5 7.2 0c1.2-2.2 2.4-3 3.6-3")),
    Gauge(listOf("M4 17a8 8 0 1 1 16 0", "M12 17l4-5"), circles = listOf(Triple(12f, 17f, 1.2f))),
    Flag(listOf("M5 21V4", "M5 4h12l-2.5 4.5L17 13H5")),
    Power(listOf("M12 3v9", "M6.3 6.3a8 8 0 1 0 11.4 0")),
    Reset(listOf("M3 12a9 9 0 1 0 3-6.7", "M3 4v5h5")),
    Lock(listOf("M8 11V8a4 4 0 0 1 8 0v3"), rects = listOf(floatArrayOf(5f, 11f, 14f, 10f, 2f))),
    Star(listOf("M12 3l2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.4 6.7 19.4l1.2-6L3.4 9.3l6-.7z")),
}

private val iconPathCache = HashMap<String, Path>()
internal fun svgPath(d: String): Path = iconPathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

@Composable
fun BoongIcon(icon: Icon, tint: Color, modifier: Modifier = Modifier.size(32.dp), strokeWidth: Float = 2f) {
    Canvas(modifier) {
        val k = min(size.width, size.height) / 24f
        withTransform({
            translate((size.width - 24f * k) / 2f, (size.height - 24f * k) / 2f)
            scale(k, k, Offset.Zero)
        }) {
            val st = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            icon.rects.forEach { r -> drawRoundRect(tint, Offset(r[0], r[1]), Size(r[2], r[3]), CornerRadius(r[4]), style = st) }
            icon.circles.forEach { (x, y, rr) -> drawCircle(tint, rr, Offset(x, y), style = st) }
            icon.paths.forEach { drawPath(svgPath(it), tint, style = st) }
        }
    }
}

/** 뼈다귀 포인트 아이콘 (v3: 32×32, -35° 기울임) */
@Composable
fun BoneIcon(modifier: Modifier = Modifier.size(40.dp)) {
    Canvas(modifier) { drawBone(this) }
}

private fun drawBone(scope: DrawScope) = with(scope) {
    val k = min(size.width, size.height) / 32f
    withTransform({
        translate((size.width - 32f * k) / 2f, (size.height - 32f * k) / 2f)
        scale(k, k, Offset.Zero)
    }) {
        rotate(-35f, pivot = Offset(16f, 16f)) {
            val c = Boong.Bone
            drawRoundRect(c, Offset(8f, 13f), Size(16f, 6f), CornerRadius(2f))
            for ((x, y) in listOf(8f to 12.6f, 8f to 19.4f, 24f to 12.6f, 24f to 19.4f)) drawCircle(c, 3.4f, Offset(x, y))
        }
    }
}

/** 선물상자 아이콘 */
@Composable
fun GiftBoxIcon(modifier: Modifier = Modifier.size(52.dp), tint: Color = Color(0xFFC9741C)) {
    BoongIcon(Icon.Gift, tint, modifier)
}

// ------------------------------------------------------------------ 상단 바 부품

/** 둥근 알약 모양 (포인트·칩) */
@Composable
fun Pill(
    modifier: Modifier = Modifier,
    bg: Color = Boong.Chip,
    height: Dp = 64.dp,
    padH: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = padH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** 뼈다귀 + 숫자. big = 결과 합계 (Jua 80) */
@Composable
fun BoneCount(value: Int, prefix: String = "", big: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 12.dp else 8.dp)) {
        BoneIcon(Modifier.size(if (big) 52.dp else 36.dp))
        Text(
            prefix + value.withComma(),
            fontFamily = if (big) Jua else null,
            fontSize = if (big) 80.sp else 30.sp,
            fontWeight = if (big) FontWeight.Normal else FontWeight.Black,
            color = Boong.Brown,
        )
    }
}

/** 탭 묶음 (v3: 배경 #F6EBDF, 선택 탭 진갈색) */
@Composable
fun NavTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Boong.Nav).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            Box(
                Modifier
                    .height(60.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == selected) Boong.Brown else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (i == selected) Boong.Cream else Boong.BrownSoft)
            }
        }
    }
}

// ------------------------------------------------------------------ 카드·버튼

/** 흰 카드 (v3: 모서리 36, 테두리 2 #F0E2D3) */
@Composable
fun BoongCard(
    modifier: Modifier = Modifier,
    padding: Dp = 36.dp,
    radius: Dp = 36.dp,
    bg: Color = Boong.Card,
    border: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (border) Modifier.border(2.dp, Boong.CardLine, shape) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** 진행 막대 (v3: 높이 22, 배경 #F3E6D8, 채움 #F2A33A) */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Boong.Accent, height: Dp = 22.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Boong.Divider)
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

/** 선택 칩 (상점 분류 탭 등) */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(68.dp)
            .clip(shape)
            .background(if (selected) Boong.Brown else Boong.Card)
            .then(if (selected) Modifier else Modifier.border(2.dp, Boong.CardLine, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Boong.Cream else Boong.BrownSoft, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

/** 진한 갈색 버튼 (v3: 알약, Jua 글자) */
@Composable
fun BoongButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Boong.Brown,
    height: Dp = 72.dp,
    fontSize: Int = 32,
    padH: Dp = 40.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .height(height)
            .widthIn(min = 120.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color else Boong.CardLine)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = padH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        leading?.invoke()
        Text(text, color = if (enabled) Boong.Cream else Boong.Muted, fontFamily = Jua, fontSize = fontSize.sp)
    }
}

/** 테두리만 있는 버튼 (끄기·초기화) */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, color: Color = Boong.Brown, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(72.dp)
            .clip(shape)
            .border(3.dp, color, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontFamily = Jua, fontSize = 30.sp) }
}

/** 라벨 + 값 한 줄 (다이얼로그) */
@Composable
fun LineRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (strong) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ThinDivider() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(Boong.Divider))
}

/** 숫자 타일 (v3: 크림 배경, 큰 숫자 + 작은 라벨) */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, labelFirst: Boolean = false, valueSize: Int = 58) {
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(Boong.Cream).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (labelFirst) Text(label, fontSize = 24.sp, color = Boong.Muted)
        Text(value, fontFamily = Jua, fontSize = valueSize.sp, color = Boong.Brown, maxLines = 1)
        if (!labelFirst) Text(label, fontSize = 24.sp, color = Boong.Muted)
    }
}

/** 붕붕이 말풍선 (v3: 흰 둥근 상자 + 왼쪽 아래 꼬리) */
@Composable
fun SpeechBubble(modifier: Modifier = Modifier, tailCenter: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier) {
        // 꼬리: 32px 정사각형을 45° 돌려 아래로 반쯤 나오게
        Canvas(
            Modifier
                .align(if (tailCenter) Alignment.BottomCenter else Alignment.BottomStart)
                .padding(start = if (tailCenter) 0.dp else 80.dp)
                .size(32.dp)
                .offset(y = 14.dp)
        ) {
            rotate(45f) { drawRect(Color.White) }
        }
        Column(
            Modifier
                .shadow(10.dp, RoundedCornerShape(36.dp), ambientColor = Boong.Outline.copy(alpha = 0.25f), spotColor = Boong.Outline.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(36.dp))
                .background(Color.White)
                .padding(horizontal = 36.dp, vertical = 26.dp),
            content = content,
        )
    }
}

/** 점선 테두리 상자 */
@Composable
fun DashedBox(modifier: Modifier = Modifier, radius: Dp = 28.dp, color: Color = Boong.Dashed, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.drawBehind {
            val r = radius.toPx()
            drawRoundRect(
                color, cornerRadius = CornerRadius(r),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
        },
        content = content,
    )
}

/** 아이콘 타일 (둥근 사각 배경 + 선 아이콘) */
@Composable
fun IconTile(icon: Icon, size: Dp, iconSize: Dp, bg: Color, tint: Color, radius: Dp = 18.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg), contentAlignment = Alignment.Center) {
        BoongIcon(icon, tint, Modifier.size(iconSize))
    }
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

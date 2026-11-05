package com.kumhoht.boongboongmung.ui.pet

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kumhoht.boongboongmung.R
import com.kumhoht.boongboongmung.reward.Catalog
import com.kumhoht.boongboongmung.reward.Item
import com.kumhoht.boongboongmung.reward.Slot
import com.kumhoht.boongboongmung.ui.theme.Boong
import kotlin.math.min
import kotlin.math.roundToInt

/** 붕붕이 표정 4종 (피그마 '붕붕이' 컴포넌트) */
enum class Expression(@DrawableRes val art: Int) {
    DEFAULT(R.drawable.pet_basic),      // 기본
    HAPPY(R.drawable.pet_happy),        // 반가워요
    SLEEPY(R.drawable.pet_sleepy),      // 꾸벅꾸벅
    SURPRISED(R.drawable.pet_gift),     // 선물 받았어요
}

/** 피그마 일러스트가 있는 아이템. 없는 아이템은 Canvas 로 그린다. */
@DrawableRes
private fun itemArt(id: String): Int? = when (id) {
    "hat_straw" -> R.drawable.item_hat_straw
    "glasses_sun" -> R.drawable.item_glasses_sun
    "scarf_blue" -> R.drawable.item_scarf_blue
    else -> null
}

/**
 * 피그마 일러스트(300×260)를 200×200 가상 좌표에 놓는 위치.
 * 일러스트의 머리(208×180, 중심 150,128)를 Canvas 머리(108×92, 중심 100,90)에 맞춰서
 * Canvas 로 그리는 모자·안경도 같은 자리에 겹쳐지게 한다.
 */
private const val ART_SCALE = 1.94f
private const val ART_LEFT = 100f - 150f / ART_SCALE
private const val ART_TOP = 90f - 128f / ART_SCALE
private const val ART_W = 300f / ART_SCALE
private const val ART_H = 260f / ART_SCALE
private const val ART_BOTTOM = ART_TOP + ART_H

/** 일러스트 몸통이 Canvas 몸통보다 아래에 있어서 Canvas 목도리를 내린다 */
private const val SCARF_SHIFT = 16f

/**
 * Pet Renderer: 캐릭터·아이템을 레이어로 합성해 그린다.
 * 레이어 순서: 배경 → 카시트 → 캐릭터 일러스트(표정) → 목도리 → 안경 → 모자
 *
 * 그림은 200×200 가상 좌표로 정의하고 화면 크기에 맞춰 늘린다.
 * 아이템은 피그마 일러스트가 있으면 이미지로, 없으면 Canvas 로 그린다.
 *
 * @param reactKey 값이 바뀔 때마다 통통 튀는 반응 (아이템 장착 등)
 */
@Composable
fun PetView(
    equipped: Map<Slot, String>,
    expression: Expression,
    modifier: Modifier = Modifier,
    reactKey: Any? = null,
    showBackground: Boolean = true,
) {
    val idle = rememberInfiniteTransition(label = "idle")
    val breath by idle.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val bounce = remember { Animatable(0f) }
    LaunchedEffect(reactKey) {
        if (reactKey != null) {
            bounce.snapTo(-14f)
            bounce.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val items = remember(equipped) { equipped.mapValues { Catalog.item(it.value) } }
    val petArt = ImageBitmap.imageResource(expression.art)
    val scarfArt = items[Slot.SCARF]?.let { itemArt(it.id) }?.let { ImageBitmap.imageResource(it) }
    val glassesArt = items[Slot.GLASSES]?.let { itemArt(it.id) }?.let { ImageBitmap.imageResource(it) }
    val hatArt = items[Slot.HAT]?.let { itemArt(it.id) }?.let { ImageBitmap.imageResource(it) }

    Canvas(modifier) {
        val p = PetSpace.fit(size)
        if (showBackground) drawBackground(items[Slot.BACKGROUND], size)

        val dy = bounce.value + (if (expression == Expression.SLEEPY) breath * 3f else breath * 1.5f)
        val pet = p.copy(top = p.top + dy * p.s)
        items[Slot.SEAT]?.let { drawSeat(p, it) }
        drawArt(pet, petArt)
        items[Slot.SCARF]?.let { item ->
            if (scarfArt != null) drawArt(pet, scarfArt)
            else drawScarf(pet.copy(top = pet.top + SCARF_SHIFT * pet.s), item)
        }
        items[Slot.GLASSES]?.let { item -> if (glassesArt != null) drawArt(pet, glassesArt) else drawGlasses(pet, item) }
        items[Slot.HAT]?.let { item -> if (hatArt != null) drawArt(pet, hatArt) else drawHat(pet, item) }
    }
}

/** 상점 아이템 아이콘 (아이템만 따로 그린다) */
@Composable
fun ItemIcon(item: Item, modifier: Modifier = Modifier) {
    val art = itemArt(item.id)?.let { ImageBitmap.imageResource(it) }
    Canvas(modifier.clipToBounds()) {
        if (item.slot == Slot.BACKGROUND) {
            drawBackground(item, size, corner = size.minDimension * 0.15f)
            return@Canvas
        }
        // 아이템 그림이 차지하는 범위(가상 좌표)를 칸 안에 꼭 맞게 넣는다
        val b = iconBounds(item.slot, art != null)
        val s = min(size.width / b.width, size.height / b.height) * 0.85f
        val p = PetSpace(left = size.width / 2f - b.center.x * s, top = size.height / 2f - b.center.y * s, s = s)
        if (art != null) {
            drawArt(p, art)
            return@Canvas
        }
        when (item.slot) {
            Slot.HAT -> drawHat(p, item)
            Slot.GLASSES -> drawGlasses(p, item)
            Slot.SCARF -> drawScarf(p, item)
            Slot.SEAT -> drawSeat(p, item)
            Slot.BACKGROUND -> Unit
        }
    }
}

/** 슬롯별 아이템 그림 범위 (가상 좌표). art = 피그마 일러스트 기준 */
private fun iconBounds(slot: Slot, art: Boolean): Rect = when (slot) {
    Slot.HAT -> if (art) Rect(39f, 23f, 161f, 63f) else Rect(40f, 3f, 160f, 63f)
    Slot.GLASSES -> if (art) Rect(72f, 87f, 128f, 105f) else Rect(68f, 79f, 132f, 105f)
    Slot.SCARF -> if (art) Rect(63f, 133f, 137f, 151f) else Rect(61f, 115f, 139f, 157f)
    Slot.SEAT -> Rect(37f, 33f, 163f, 181f)
    Slot.BACKGROUND -> Rect(0f, 0f, 200f, 200f)
}

/** 200×200 가상 좌표 → 화면 좌표 */
internal data class PetSpace(val left: Float, val top: Float, val s: Float) {
    fun x(v: Float) = left + v * s
    fun y(v: Float) = top + v * s
    fun o(x: Float, y: Float) = Offset(x(x), y(y))
    fun sz(w: Float, h: Float) = Size(w * s, h * s)
    fun r(v: Float) = v * s

    companion object {
        /** 캔버스 아래쪽 가운데에 붕붕이를 앉힌다 (모자 꼭대기 y=4 ~ 일러스트 바닥) */
        fun fit(size: Size): PetSpace {
            val s = min(size.width / 175f, size.height / (ART_BOTTOM - 4f + 6f))
            return PetSpace(left = size.width / 2f - 100f * s, top = size.height - ART_BOTTOM * s, s = s)
        }
    }
}

/** 피그마 일러스트를 캐릭터 자리에 그린다 (아이템도 같은 틀로 만들어져 있어 그대로 겹친다) */
private fun DrawScope.drawArt(p: PetSpace, img: ImageBitmap) {
    drawImage(
        img,
        dstOffset = IntOffset(p.x(ART_LEFT).roundToInt(), p.y(ART_TOP).roundToInt()),
        dstSize = IntSize(p.r(ART_W).roundToInt(), p.r(ART_H).roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

private val outline get() = Boong.Line

private fun DrawScope.stroke(p: PetSpace, w: Float = 2.4f) = Stroke(width = p.r(w), cap = StrokeCap.Round)

// ------------------------------------------------------------------ 배경

private fun DrawScope.drawBackground(item: Item?, size: Size, corner: Float = 0f) {
    val bg = item ?: Catalog.item(Catalog.DEFAULT_BACKGROUND)!!
    val main = Color(bg.color)
    val accent = Color(bg.accent)
    val cr = CornerRadius(corner, corner)
    when (bg.id) {
        "bg_sunset" -> {
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF6D7A8), main, accent)), cornerRadius = cr, size = size)
            drawCircle(Color(0xFFFFE9B0), radius = size.minDimension * 0.12f, center = Offset(size.width * 0.75f, size.height * 0.55f))
            val road = Path().apply {
                moveTo(size.width * 0.45f, size.height * 0.62f)
                lineTo(size.width * 0.55f, size.height * 0.62f)
                lineTo(size.width * 0.85f, size.height)
                lineTo(size.width * 0.15f, size.height)
                close()
            }
            drawRect(Color(0xFFB98B70), topLeft = Offset(0f, size.height * 0.62f), size = Size(size.width, size.height * 0.38f))
            drawPath(road, Color(0xFF8C6A5A))
        }
        "bg_night" -> {
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1E2744), main)), cornerRadius = cr, size = size)
            drawCircle(accent, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.82f, size.height * 0.2f))
            val w = size.width / 7f
            val heights = floatArrayOf(0.35f, 0.5f, 0.3f, 0.6f, 0.42f, 0.55f, 0.32f)
            for (i in 0 until 7) {
                val h = size.height * heights[i]
                val top = size.height * 0.75f - h
                drawRect(Color(0xFF3C4A72), topLeft = Offset(i * w + 2f, top), size = Size(w - 4f, h + size.height * 0.25f))
                var wy = top + 8f
                while (wy < size.height * 0.72f) {
                    if (((i * 7 + wy.toInt() / 9) % 3) != 0) {
                        drawRect(accent.copy(alpha = 0.8f), topLeft = Offset(i * w + w * 0.3f, wy), size = Size(w * 0.15f, w * 0.12f))
                    }
                    wy += w * 0.3f
                }
            }
        }
        else -> { // 맑은 하늘
            drawRoundRect(main, cornerRadius = cr, size = size)
            drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension * 0.06f, center = Offset(size.width * 0.2f, size.height * 0.25f))
            drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension * 0.08f, center = Offset(size.width * 0.27f, size.height * 0.22f))
            val hill = Path().apply {
                moveTo(0f, size.height * 0.7f)
                quadraticTo(size.width * 0.3f, size.height * 0.48f, size.width * 0.62f, size.height * 0.66f)
                quadraticTo(size.width * 0.82f, size.height * 0.55f, size.width, size.height * 0.62f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(hill, accent)
        }
    }
}

// ------------------------------------------------------------------ 카시트

private fun DrawScope.drawSeat(p: PetSpace, item: Item) {
    val c = Color(item.color)
    // 등받이
    drawRoundRect(c, topLeft = p.o(38f, 70f), size = p.sz(124f, 110f), cornerRadius = CornerRadius(p.r(30f)))
    drawRoundRect(outline, topLeft = p.o(38f, 70f), size = p.sz(124f, 110f), cornerRadius = CornerRadius(p.r(30f)), style = stroke(p, 2f))
    // 머리 받침
    drawRoundRect(c, topLeft = p.o(70f, 34f), size = p.sz(60f, 40f), cornerRadius = CornerRadius(p.r(16f)))
    drawRoundRect(outline, topLeft = p.o(70f, 34f), size = p.sz(60f, 40f), cornerRadius = CornerRadius(p.r(16f)), style = stroke(p, 2f))
    // 벨트 줄
    drawLine(Color.White.copy(alpha = 0.7f), p.o(52f, 90f), p.o(52f, 170f), strokeWidth = p.r(6f))
    drawLine(Color.White.copy(alpha = 0.7f), p.o(148f, 90f), p.o(148f, 170f), strokeWidth = p.r(6f))
    if (item.id == "seat_cushion") {
        for (i in 0..2) drawCircle(Color.White.copy(alpha = 0.6f), p.r(5f), p.o(70f + i * 30f, 160f))
    }
}

// ------------------------------------------------------------------ 아이템

private fun DrawScope.drawScarf(p: PetSpace, item: Item) {
    val c = Color(item.color)
    val a = Color(item.accent)
    drawRoundRect(c, topLeft = p.o(62f, 118f), size = p.sz(76f, 16f), cornerRadius = CornerRadius(p.r(8f)))
    drawRoundRect(outline, topLeft = p.o(62f, 118f), size = p.sz(76f, 16f), cornerRadius = CornerRadius(p.r(8f)), style = stroke(p, 2f))
    if (item.id == "scarf_ribbon") {
        // 리본
        val l = Path().apply { moveTo(p.x(100f), p.y(126f)); lineTo(p.x(84f), p.y(116f)); lineTo(p.x(84f), p.y(138f)); close() }
        val r = Path().apply { moveTo(p.x(100f), p.y(126f)); lineTo(p.x(116f), p.y(116f)); lineTo(p.x(116f), p.y(138f)); close() }
        drawPath(l, a); drawPath(r, a)
        drawPath(l, outline, style = stroke(p, 1.6f)); drawPath(r, outline, style = stroke(p, 1.6f))
        drawCircle(c, p.r(5f), p.o(100f, 126f))
        drawCircle(outline, p.r(5f), p.o(100f, 126f), style = stroke(p, 1.6f))
    } else {
        // 늘어진 끝
        drawRoundRect(a, topLeft = p.o(112f, 128f), size = p.sz(14f, 26f), cornerRadius = CornerRadius(p.r(4f)))
        drawRoundRect(outline, topLeft = p.o(112f, 128f), size = p.sz(14f, 26f), cornerRadius = CornerRadius(p.r(4f)), style = stroke(p, 1.6f))
        for (i in 0..2) drawLine(Color.White.copy(alpha = 0.6f), p.o(114f + i * 4f, 150f), p.o(114f + i * 4f, 156f), strokeWidth = p.r(1.5f))
    }
}

private fun DrawScope.drawGlasses(p: PetSpace, item: Item) {
    val c = Color(item.color)
    if (item.id == "glasses_sun") {
        for (x in listOf(82f, 118f)) {
            drawRoundRect(c, topLeft = p.o(x - 13f, 84f), size = p.sz(26f, 17f), cornerRadius = CornerRadius(p.r(7f)))
            drawLine(Color.White.copy(alpha = 0.5f), p.o(x - 7f, 88f), p.o(x - 2f, 88f), strokeWidth = p.r(2f), cap = StrokeCap.Round)
        }
        drawLine(c, p.o(95f, 89f), p.o(105f, 89f), strokeWidth = p.r(3f))
    } else {
        for (x in listOf(82f, 118f)) drawCircle(c, p.r(12f), p.o(x, 92f), style = stroke(p, 2.6f))
        drawLine(c, p.o(94f, 90f), p.o(106f, 90f), strokeWidth = p.r(2.4f))
    }
}

private fun DrawScope.drawHat(p: PetSpace, item: Item) {
    val c = Color(item.color)
    val a = Color(item.accent)
    when (item.id) {
        "hat_straw" -> {
            drawOval(c, topLeft = p.o(40f, 40f), size = p.sz(120f, 22f))
            drawOval(outline, topLeft = p.o(40f, 40f), size = p.sz(120f, 22f), style = stroke(p, 2f))
            drawArc(c, 180f, 180f, true, topLeft = p.o(66f, 14f), size = p.sz(68f, 58f))
            drawArc(outline, 180f, 180f, false, topLeft = p.o(66f, 14f), size = p.sz(68f, 58f), style = stroke(p, 2f))
            drawRect(a, topLeft = p.o(67f, 36f), size = p.sz(66f, 7f))
        }
        "hat_beret" -> {
            rotate(-12f, pivot = p.o(100f, 44f)) {
                drawOval(c, topLeft = p.o(52f, 26f), size = p.sz(96f, 34f))
                drawOval(outline, topLeft = p.o(52f, 26f), size = p.sz(96f, 34f), style = stroke(p, 2f))
                drawLine(outline, p.o(100f, 27f), p.o(102f, 18f), strokeWidth = p.r(3f), cap = StrokeCap.Round)
            }
        }
        "hat_knit" -> {
            drawArc(c, 180f, 180f, true, topLeft = p.o(58f, 14f), size = p.sz(84f, 64f))
            drawArc(outline, 180f, 180f, false, topLeft = p.o(58f, 14f), size = p.sz(84f, 64f), style = stroke(p, 2f))
            drawRoundRect(a, topLeft = p.o(56f, 40f), size = p.sz(88f, 14f), cornerRadius = CornerRadius(p.r(6f)))
            drawRoundRect(outline, topLeft = p.o(56f, 40f), size = p.sz(88f, 14f), cornerRadius = CornerRadius(p.r(6f)), style = stroke(p, 2f))
            drawCircle(a, p.r(9f), p.o(100f, 12f))
            drawCircle(outline, p.r(9f), p.o(100f, 12f), style = stroke(p, 2f))
        }
        "hat_crown" -> {
            val crown = Path().apply {
                moveTo(p.x(70f), p.y(52f)); lineTo(p.x(66f), p.y(20f)); lineTo(p.x(83f), p.y(36f))
                lineTo(p.x(100f), p.y(14f)); lineTo(p.x(117f), p.y(36f)); lineTo(p.x(134f), p.y(20f))
                lineTo(p.x(130f), p.y(52f)); close()
            }
            drawPath(crown, c)
            drawPath(crown, outline, style = stroke(p, 2f))
            for (x in listOf(83f, 100f, 117f)) drawCircle(a, p.r(4f), p.o(x, 44f))
        }
        "hat_party" -> {
            val cone = Path().apply {
                moveTo(p.x(100f), p.y(4f)); lineTo(p.x(124f), p.y(52f)); lineTo(p.x(76f), p.y(52f)); close()
            }
            drawPath(cone, c)
            drawLine(a, p.o(92f, 24f), p.o(110f, 24f), strokeWidth = p.r(4f))
            drawLine(a, p.o(85f, 38f), p.o(116f, 38f), strokeWidth = p.r(4f))
            drawPath(cone, outline, style = stroke(p, 2f))
            drawCircle(a, p.r(6f), p.o(100f, 5f))
        }
        else -> { // 캡모자
            drawArc(c, 180f, 180f, true, topLeft = p.o(60f, 18f), size = p.sz(80f, 60f))
            drawArc(outline, 180f, 180f, false, topLeft = p.o(60f, 18f), size = p.sz(80f, 60f), style = stroke(p, 2f))
            drawOval(c, topLeft = p.o(108f, 42f), size = p.sz(50f, 12f))
            drawOval(outline, topLeft = p.o(108f, 42f), size = p.sz(50f, 12f), style = stroke(p, 2f))
            drawCircle(outline, p.r(3f), p.o(100f, 19f))
        }
    }
}

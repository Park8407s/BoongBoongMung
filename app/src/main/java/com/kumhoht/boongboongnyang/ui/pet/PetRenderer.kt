package com.kumhoht.boongboongnyang.ui.pet

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.kumhoht.boongboongnyang.reward.Catalog
import com.kumhoht.boongboongnyang.reward.Item
import com.kumhoht.boongboongnyang.reward.Slot
import com.kumhoht.boongboongnyang.ui.theme.Boong
import kotlin.math.min

/** 붕붕냥 표정 4종 (basic · happy · sleepy · wow) */
enum class Expression { DEFAULT, HAPPY, SLEEPY, SURPRISED }

/**
 * Pet Renderer — 디자인 캔버스 '붕붕냥' 의 캐릭터(Pup.dc.html, 400×400 SVG)를 그대로 옮겨 그린다.
 * 레이어: 배경(창밖 풍경) → 카시트 → 얼굴·귀·볼터치·수염·눈·입 → 목도리 → 앞발(젤리) → 모자 → 안경
 *
 * 시안에 그림이 있는 아이템(밀짚모자·털모자·동그란 안경·목도리)은 시안 모양으로,
 * 나머지(베레모·왕관·고깔·캡모자·리본·카시트)는 기존 Canvas 그림을 고양이 머리에 맞춰 겹친다. 선글라스는 고양이 눈 위치에 맞춰 새로 그림.
 *
 * @param reactKey 값이 바뀔 때마다 통통 튀는 반응 (아이템 장착 등)
 * @param sparkles 결과 화면의 반짝이 별 3개
 */
@Composable
fun PetView(
    equipped: Map<Slot, String>,
    expression: Expression,
    modifier: Modifier = Modifier,
    reactKey: Any? = null,
    showBackground: Boolean = true,
    sparkles: Boolean = false,
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

    Canvas(modifier) {
        if (showBackground) drawScene(items[Slot.BACKGROUND], size)

        // 붕붕냥은 장면 높이의 약 68% 크기로, 바닥에서 4.5% 띄워 가운데에 앉는다 (앞발 아래 끝 = 대시보드 위)
        val s = if (showBackground) min(size.height * 0.68f, size.width * 0.86f) else min(size.width, size.height)
        val bottomGap = if (showBackground) size.height * 0.045f else 0f
        val k = s / 400f
        val dy = (bounce.value + breath * (if (expression == Expression.SLEEPY) 3f else 1.5f)) * k * 2.5f
        withTransform({
            translate(left = (size.width - s) / 2f, top = size.height - bottomGap - s + dy)
            scale(k, k, Offset.Zero)
        }) { drawPup(expression, items) }
        if (sparkles) drawSparkles((size.width - s) / 2f, size.height - bottomGap - s, s)
    }
}

/** 상점 아이템 썸네일 */
@Composable
fun ItemIcon(item: Item, modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds()) {
        if (item.slot == Slot.BACKGROUND) {
            drawScene(item, size, compact = true)
            return@Canvas
        }
        // v3 썸네일(120×120)이 있는 모자
        thumbnailOf(item)?.let { thumb ->
            val k = min(size.width, size.height) / 120f * 0.92f
            withTransform({
                translate((size.width - 120f * k) / 2f, (size.height - 120f * k) / 2f)
                scale(k, k, Offset.Zero)
            }) { thumb(item) }
            return@Canvas
        }
        // 나머지: 머리 위에 얹는 그림만 잘라 크게 보여 준다 (v3 400 좌표 범위)
        val b = onHeadBounds(item)
        val k = min(size.width / b.width, size.height / b.height) * 0.85f
        withTransform({
            translate(size.width / 2f - b.center.x * k, size.height / 2f - b.center.y * k)
            scale(k, k, Offset.Zero)
        }) { drawItemOnly(item) }
    }
}

// ------------------------------------------------------------------ 붕붕냥 (400×400 좌표, 디자인 캔버스 '붕붕냥' Pup.dc.html)

private val CatLine = Color(0xFF1E1E22)     // 고양이 선
private val Pink = Color(0xFFF7A8AE)        // 귀 안쪽·볼터치
private val Jelly = Color(0xFFF7929A)       // 발바닥 젤리
private val Mouth = Color(0xFFEE8E92)       // 입 안
private val Zz = Color(0xFF5A5A64)
private val ItemStroke = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val pathCache = HashMap<String, Path>()
/** SVG path 문자열 → Compose Path (한 번 만들어 재사용) */
private fun svg(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private fun DrawScope.ovalWithLine(c: Offset, rx: Float, ry: Float, fill: Color, stroke: Stroke = ItemStroke) {
    val tl = Offset(c.x - rx, c.y - ry)
    val sz = Size(rx * 2, ry * 2)
    drawOval(fill, tl, sz)
    drawOval(CatLine, tl, sz, style = stroke)
}

private fun DrawScope.pathWithLine(d: String, fill: Color, stroke: Stroke = ItemStroke) {
    val p = svg(d)
    drawPath(p, fill)
    drawPath(p, CatLine, style = stroke)
}

private fun line(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** 몸체 그룹 좌표계: SVG 의 translate(200 97) scale(0.68) translate(-445 -55) */
private inline fun DrawScope.inBody(block: DrawScope.() -> Unit) = withTransform({
    translate(200f, 97f); scale(0.68f, 0.68f, Offset.Zero); translate(-445f, -55f)
}, block)

/** 소품 그룹 좌표계: SVG 의 translate(0 37) */
private inline fun DrawScope.inProps(block: DrawScope.() -> Unit) = withTransform({ translate(0f, 37f) }, block)

/** 기존 Canvas 아이템(200 가상 좌표)을 붕붕냥에 맞추는 좌표계 */
// 모자: 두 귀 사이 정수리에 작게 (시안의 hatFit=small 크기). 모자 아랫단 y52 → 123
private val HAT_SPACE = PetSpace(left = 200f - 100f * 1.6f, top = 123f - 52f * 1.6f, s = 1.6f)
// 카시트: 몸 뒤에 크게
private val SEAT_SPACE = PetSpace(left = 200f - 100f * 2.9f, top = 40f - 34f * 2.9f, s = 2.9f)

private fun DrawScope.drawPup(e: Expression, items: Map<Slot, Item?>) {
    items[Slot.SEAT]?.let { drawSeat(SEAT_SPACE, it) }

    inBody {
        // 얼굴 (세운 귀 포함 한 덩어리)
        val face = svg("M218 352 C200 300 190 230 198 170 C204 120 222 78 252 64 C280 54 330 70 365 85 C420 74 470 74 522 82 C560 64 600 50 632 58 C660 68 676 110 688 170 C696 230 690 300 670 350 C646 404 560 432 445 432 C330 432 242 404 218 352 Z")
        drawPath(face, Color.White)
        drawPath(face, CatLine, style = line(10f))
        // 귀 안쪽 · 볼터치
        drawPath(svg("M256 128 L268 98 L300 110 M592 110 L626 98 L638 128"), Pink, style = line(16f))
        drawPath(svg("M242 296 L258 272 M274 300 L290 276 M306 292 L322 268 M564 298 L580 274 M596 304 L612 280 M626 314 L642 290"), Pink, style = line(15f))
        // 수염
        drawPath(svg("M172 265 H216 M180 320 L220 316 M672 266 H716 M668 316 L708 320"), CatLine, style = line(8f))
        // 눈
        val eye = line(16f)
        when (e) {
            Expression.DEFAULT -> { drawPath(svg("M280 240 L372 216"), CatLine, style = eye); drawPath(svg("M514 216 L606 242"), CatLine, style = eye) }
            Expression.SLEEPY -> { drawPath(svg("M282 240 H370"), CatLine, style = eye); drawPath(svg("M516 240 H604"), CatLine, style = eye) }
            Expression.HAPPY -> { drawPath(svg("M286 240 q40 -40 80 0"), CatLine, style = eye); drawPath(svg("M520 240 q40 -40 80 0"), CatLine, style = eye) }
            Expression.SURPRISED -> for (x in listOf(326f, 560f)) {
                drawCircle(CatLine, 21f, Offset(x, 228f)); drawCircle(Color.White, 7f, Offset(x - 7f, 220f))
            }
        }
        // 입 (기본·졸림은 입 없음)
        when (e) {
            Expression.HAPPY -> drawPath(svg("M411 274 Q416 294 431 291 Q440 289 443 277 Q446 289 455 291 Q470 294 475 274"), CatLine, style = line(10f))
            Expression.SURPRISED -> {
                drawOval(Mouth, Offset(432f, 288f), Size(22f, 28f))
                drawOval(CatLine, Offset(432f, 288f), Size(22f, 28f), style = line(7f))
            }
            else -> Unit
        }
    }

    // 목도리 (앞발 뒤)
    items[Slot.SCARF]?.let { drawCatScarf(it) }

    inBody {
        // 앞발 + 분홍 젤리
        for (x in listOf(288f, 598f)) {
            drawOval(Color.White, Offset(x - 98f, 336f), Size(196f, 164f))
            drawOval(CatLine, Offset(x - 98f, 336f), Size(196f, 164f), style = line(10f))
        }
        for (cx in listOf(294f, 596f)) {
            drawCircle(Jelly, 16f, Offset(cx - 40f, 388f)); drawCircle(Jelly, 16f, Offset(cx, 376f)); drawCircle(Jelly, 16f, Offset(cx + 40f, 388f))
            drawOval(Jelly, Offset(cx - 48f, 406f), Size(96f, 52f))
        }
    }

    // 졸음 z z (글자 대신 같은 모양의 선)
    if (e == Expression.SLEEPY) inProps {
        drawPath(svg("M354 50 h24 l-24 28 h24"), Zz, style = line(6f))
        drawPath(svg("M386 20 h16 l-16 19 h16"), Zz, style = line(5f))
    }

    items[Slot.HAT]?.let { drawCatHat(it) }
    items[Slot.GLASSES]?.let { drawCatGlasses(it) }
}

private fun DrawScope.drawCatScarf(item: Item) = inBody {
    val main = Color(item.color)
    val st = line(9f)
    val band = "M215 342 C330 392 560 392 672 342 L676 380 C560 430 330 430 210 380 Z"
    if (item.id == "scarf_ribbon") {
        // 리본 목도리: 띠 + 가운데 나비 리본
        pathWithLine(band, main, st)
        val accent = Color(item.accent)
        pathWithLine("M443 392 L388 360 L388 424 Z", accent, st)
        pathWithLine("M443 392 L498 360 L498 424 Z", accent, st)
        drawCircle(main, 17f, Offset(443f, 392f)); drawCircle(CatLine, 17f, Offset(443f, 392f), style = st)
    } else {
        val tail = if (item.accent != item.color) Color(item.accent)
        else Color(red = main.red * 0.86f, green = main.green * 0.86f, blue = main.blue * 0.86f)
        pathWithLine("M500 372 L546 368 L556 440 L508 444 Z", tail, st)
        pathWithLine(band, main, st)
    }
}

private fun DrawScope.drawCatHat(item: Item) {
    when (item.id) {
        "hat_straw" -> inProps {
            ovalWithLine(Offset(200f, 86f), 84f, 16f, Color(0xFFEBC777))
            pathWithLine("M150 86 C146 28 254 28 250 86 Z", Color(0xFFEBC777))
            pathWithLine("M151 66 C180 59 220 59 249 66 L250 80 C220 74 180 74 150 80 Z", Color(0xFFE2725B))
        }
        "hat_knit" -> inProps {
            pathWithLine("M134 96 C130 26 270 26 266 96 Z", Color(0xFF8EC1E3))
            drawRoundRect(Color(0xFF6AA6D1), Offset(126f, 78f), Size(148f, 26f), CornerRadius(13f))
            drawRoundRect(CatLine, Offset(126f, 78f), Size(148f, 26f), CornerRadius(13f), style = ItemStroke)
            ovalWithLine(Offset(200f, 30f), 15f, 15f, Color.White)
        }
        else -> drawHat(HAT_SPACE, item)
    }
}

private fun DrawScope.drawCatGlasses(item: Item) = inProps {
    if (item.id == "glasses_sun") {
        val c = Color(item.color)
        for (x in listOf(119f, 278f)) {
            drawRoundRect(c, Offset(x - 50f, 148f), Size(100f, 64f), CornerRadius(24f))
            drawLine(Color.White.copy(alpha = 0.5f), Offset(x - 30f, 162f), Offset(x - 12f, 162f), strokeWidth = 7f, cap = StrokeCap.Round)
        }
        drawPath(svg("M169 172 q30 -12 60 0"), c, style = line(8f))
    } else {
        drawCircle(CatLine, 40f, Offset(119f, 180f), style = ItemStroke)
        drawCircle(CatLine, 40f, Offset(278f, 180f), style = ItemStroke)
        drawPath(svg("M159 176 q39 -14 79 0"), CatLine, style = ItemStroke)
    }
}

/** 썸네일용: 아이템만 */
private fun DrawScope.drawItemOnly(item: Item) {
    when (item.slot) {
        Slot.HAT -> drawCatHat(item)
        Slot.GLASSES -> drawCatGlasses(item)
        Slot.SCARF -> drawCatScarf(item)
        Slot.SEAT -> drawSeat(SEAT_SPACE, item)
        Slot.BACKGROUND -> Unit
    }
}

/** 썸네일용 아이템 범위 (400 좌표) */
private fun onHeadBounds(item: Item): Rect = when (item.slot) {
    Slot.HAT -> Rect(110f, 40f, 290f, 145f)
    Slot.GLASSES -> Rect(70f, 170f, 330f, 265f)
    Slot.SCARF -> Rect(35f, 285f, 365f, 370f)
    Slot.SEAT -> Rect(10f, 30f, 390f, 470f)
    Slot.BACKGROUND -> Rect(0f, 0f, 400f, 400f)
}

/** v3 상점 썸네일(120×120)이 있는 모자 */
private fun thumbnailOf(item: Item): (DrawScope.(Item) -> Unit)? {
    val s4 = Stroke(width = 4f, join = StrokeJoin.Round)
    return when (item.id) {
        "hat_straw" -> { _ ->
            ovalWithLine(Offset(60f, 80f), 52f, 12f, Color(0xFFEBC777), s4)
            pathWithLine("M32 78 C30 36 90 36 88 78 Z", Color(0xFFEBC777), s4)
            pathWithLine("M33 64 C50 58 70 58 87 64 L88 74 C70 69 50 69 32 74 Z", Color(0xFFE2725B), s4)
        }
        "hat_knit" -> { _ ->
            pathWithLine("M26 86 C22 30 98 30 94 86 Z", Color(0xFF8EC1E3), s4)
            drawRoundRect(Color(0xFF6AA6D1), Offset(22f, 74f), Size(76f, 20f), CornerRadius(10f))
            drawRoundRect(CatLine, Offset(22f, 74f), Size(76f, 20f), CornerRadius(10f), style = s4)
            ovalWithLine(Offset(60f, 28f), 10f, 10f, Color.White, s4)
        }
        "hat_cap" -> { it ->
            val main = Color(it.color)
            val dark = Color(red = main.red * 0.85f, green = main.green * 0.85f, blue = main.blue * 0.85f)
            pathWithLine("M86 80 C100 80 110 84 112 90 L58 90 L58 80 Z", dark, s4)
            pathWithLine("M26 82 C26 40 94 40 94 82 Z", main, s4)
            drawCircle(dark, 4f, Offset(60f, 42f))
        }
        "hat_crown" -> { _ ->
            pathWithLine("M24 88 L30 44 L46 64 L60 36 L74 64 L90 44 L96 88 Z", Color(0xFFF7C948), s4)
            drawCircle(Color(0xFFE2725B), 6f, Offset(60f, 74f))
        }
        else -> null
    }
}

// ------------------------------------------------------------------ v3 창밖 풍경

/** 하늘·구름·언덕 2겹·바닥. 노을/밤 배경은 기존 그림 위에 바닥만 v3 로 */
private fun DrawScope.drawScene(item: Item?, size: Size, compact: Boolean = false) {
    val bg = item ?: Catalog.item(Catalog.DEFAULT_BACKGROUND)!!
    val w = size.width; val h = size.height
    val f = if (compact) min(w, h) / 600f else min(1f, w / 900f)   // 구름 크기 비율
    val groundH = if (compact) h * 0.14f else 92f
    if (bg.id == "bg_sky") {
        drawRect(Boong.Sky, size = size)
        cloud(Offset(w - (70f + 240f) * f, h * 0.166f), f, big = true)
        cloud(Offset(60f * f, h * 0.32f), f, big = false)
        val hillBottom = h - groundH + 22f * (groundH / 92f)
        val hillH = 240f * (if (compact) h / 1026f else 1f)
        withTransform({ translate(0f, hillBottom - hillH); scale(w / 900f, hillH / 240f, Offset.Zero) }) {
            drawPath(svg("M0 120 C150 30 300 40 450 110 C600 180 750 60 900 90 L900 240 L0 240 Z"), Color(0xFFC9E5BE))
            drawPath(svg("M0 175 C200 115 380 150 520 172 C680 196 800 140 900 160 L900 240 L0 240 Z"), Color(0xFFB3D9A5))
        }
    } else {
        drawBackground(bg, size)
    }
    drawRect(Boong.Ground, topLeft = Offset(0f, h - groundH), size = Size(w, groundH))
    drawRect(Boong.GroundTop, topLeft = Offset(0f, h - groundH), size = Size(w, 6f * (groundH / 92f)))
}

private fun DrawScope.cloud(at: Offset, f: Float, big: Boolean) {
    val c = Color.White
    withTransform({ translate(at.x, at.y); scale(f, f, Offset.Zero) }) {
        if (big) {
            drawCircle(c, 40f, Offset(70f, 72f)); drawCircle(c, 52f, Offset(125f, 55f)); drawCircle(c, 34f, Offset(180f, 78f))
            drawRoundRect(c, Offset(40f, 78f), Size(170f, 34f), CornerRadius(17f))
        } else {
            val a = 0.85f
            drawCircle(c, 26f, Offset(45f, 46f), alpha = a); drawCircle(c, 32f, Offset(84f, 34f), alpha = a)
            drawRoundRect(c, Offset(22f, 48f), Size(108f, 24f), CornerRadius(12f), alpha = a)
        }
    }
}

/**
 * 결과 화면 반짝이 별 — 붕붕냥 시안: 캐릭터 기준 배치 (캐릭터 680px 기준)
 * 왼쪽 귀 바깥 위(top 103, left -6, 80) · 오른쪽 귀 위(top 77, right 14, 60) · 오른쪽 볼 옆(top 253, right -40, 44)
 */
private fun DrawScope.drawSparkles(left: Float, top: Float, s: Float) {
    val star = svg("M0 -22 C4 -6 6 -4 22 0 C6 4 4 6 0 22 C-4 6 -6 4 -22 0 C-6 -4 -4 -6 0 -22 Z")
    val st = Stroke(width = 2.5f, join = StrokeJoin.Round)
    val u = s / 680f
    val stars = listOf(
        Triple(left + (-6f + 40f) * u, top + (103f + 40f) * u, 80f * u),
        Triple(left + s - (14f + 30f) * u, top + (77f + 30f) * u, 60f * u),
        Triple(left + s + (40f - 22f) * u, top + (253f + 22f) * u, 44f * u),
    )
    for ((x, y, d) in stars) {
        val k = d / 48f
        withTransform({ translate(x, y); scale(k, k, Offset.Zero) }) {
            drawPath(star, Color(0xFFF7B84B)); drawPath(star, Color(0xFF8A4B12), style = st)
        }
    }
}

// ------------------------------------------------------------------ 기존 Canvas 아이템 좌표계 (200 가상 좌표)

/** 200×200 가상 좌표 → 화면 좌표 */
internal data class PetSpace(val left: Float, val top: Float, val s: Float) {
    fun x(v: Float) = left + v * s
    fun y(v: Float) = top + v * s
    fun o(x: Float, y: Float) = Offset(x(x), y(y))
    fun sz(w: Float, h: Float) = Size(w * s, h * s)
    fun r(v: Float) = v * s
}

private val outline get() = CatLine

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

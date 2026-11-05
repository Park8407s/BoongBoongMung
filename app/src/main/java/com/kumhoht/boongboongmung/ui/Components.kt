package com.kumhoht.boongboongmung.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kumhoht.boongboongmung.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumhoht.boongboongmung.ui.theme.Boong
import java.text.NumberFormat
import java.util.Locale

fun Int.withComma(): String = NumberFormat.getNumberInstance(Locale.KOREA).format(this)

/** 뼈다귀 포인트 아이콘 (피그마 '아이콘/뼈다귀 포인트') */
@Composable
fun BoneIcon(modifier: Modifier = Modifier.size(28.dp)) {
    Image(painterResource(R.drawable.ic_bone), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

/** 둥근 알약 모양 표시 (포인트, 레벨) */
@Composable
fun Pill(modifier: Modifier = Modifier, bg: Color = Boong.Chip, content: @Composable () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** 뼈다귀 + 숫자. big = 결과 합계 (Jua 40) */
@Composable
fun BoneCount(value: Int, prefix: String = "", big: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BoneIcon(Modifier.size(width = 46.dp, height = 24.dp))
        Text(
            prefix + value.withComma(),
            style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.labelLarge,
        )
    }
}

/** 흰 카드 (피그마: 모서리 24, 테두리 없음) */
@Composable
fun BoongCard(
    modifier: Modifier = Modifier,
    padding: Dp = 30.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Boong.Card)
            .padding(padding),
        content = content,
    )
}

/** 진행 막대 (피그마: 높이 14) */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Boong.Bone, height: Dp = 14.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Boong.CardLine)
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

/** 선택 칩 (탭, 카테고리) — 피그마: 높이 40, 모서리 20, 20sp */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(40.dp)
            .widthIn(min = 96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Boong.ChipOn else Boong.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Boong.Brown,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 진한 갈색 버튼 — 피그마: 높이 44, 모서리 22, 20sp */
@Composable
fun BoongButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Boong.Brown,
) {
    Box(
        modifier
            .height(44.dp)
            .widthIn(min = 150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) color else Boong.CardLine)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else Boong.BrownSoft, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

/** 라벨 + 값 한 줄 — 피그마: 라벨 22 연갈색, 값 24 진갈색, 줄 높이 약 52 */
@Composable
fun LineRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (strong) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

/** 구분선 — 피그마: 2px #E8D5BF */
@Composable
fun ThinDivider() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(Boong.CardLine))
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

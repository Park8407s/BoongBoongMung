package com.kumhoht.boongboongmung.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 시안 색상 (따뜻한 크림·갈색 톤) */
object Boong {
    val Frame = Color(0xFF2A211B)        // 바깥 프레임 (차량 화면 테두리)
    val Cream = Color(0xFFFFF6EA)        // 앱 배경
    val Card = Color(0xFFFFFFFF)
    val CardLine = Color(0xFFF1E3D0)
    val Brown = Color(0xFF3B2A20)        // 글자·버튼
    val BrownSoft = Color(0xFF7A6252)    // 보조 글자
    val Line = Color(0xFF5A4030)         // 캐릭터 외곽선
    val Bone = Color(0xFFF2B544)         // 뼈다귀 포인트
    val Chip = Color(0xFFF7E6CC)
    val ChipOn = Color(0xFF3B2A20)
    val Dashboard = Color(0xFFEAD6BF)
    val Cheek = Color(0xFFF7B9B9)
    val Ear = Color(0xFFEFD3AE)
    val Good = Color(0xFF6DAA74)
    val Warn = Color(0xFFE59A54)
    val Gift = Color(0xFFE5737A)
}

private val scheme = lightColorScheme(
    primary = Boong.Brown,
    onPrimary = Color.White,
    secondary = Boong.Bone,
    background = Boong.Cream,
    surface = Boong.Card,
    onBackground = Boong.Brown,
    onSurface = Boong.Brown,
)

/** 차량 화면에서 읽기 쉽게 기본 글자를 크게 */
private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Boong.Brown),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Boong.Brown),
    titleMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Boong.Brown),
    bodyLarge = TextStyle(fontSize = 20.sp, color = Boong.Brown),
    bodyMedium = TextStyle(fontSize = 18.sp, color = Boong.Brown),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 16.sp, color = Boong.BrownSoft),
)

@Composable
fun BoongTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

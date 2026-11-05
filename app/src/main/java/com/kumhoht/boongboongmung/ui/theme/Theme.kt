package com.kumhoht.boongboongmung.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kumhoht.boongboongmung.R

/** 시안 색상 (피그마 '붕붕멍' 1682×1210 화면 기준, 따뜻한 크림·갈색 톤) */
object Boong {
    val Frame = Color(0xFF2A211B)        // 바깥 프레임 (차량 화면 테두리)
    val Cream = Color(0xFFFFF6EC)        // 앱 배경
    val Card = Color(0xFFFFFFFF)
    val CardLine = Color(0xFFE8D5BF)     // 구분선·진행바 배경·아이템 카드 테두리
    val Brown = Color(0xFF3B2A20)        // 글자·버튼
    val BrownSoft = Color(0xFF6B5444)    // 보조 글자
    val Equip = Color(0xFF8A4B16)        // '장착 중' 표시
    val Line = Color(0xFF5A4030)         // 캐릭터 외곽선 (Canvas 아이템)
    val Bone = Color(0xFFF2B544)         // 뼈다귀 포인트·진행바
    val Chip = Color(0xFFFCEBD3)         // 레벨 칩·탭
    val ChipOn = Color(0xFF3B2A20)
    val Sky = Color(0xFFDCEEF7)          // 창밖 풍경
    val Dashboard = Color(0xFFEAD6BF)
    val Cheek = Color(0xFFF7B9B9)
    val Ear = Color(0xFFEFD3AE)
    val Good = Color(0xFF6DAA74)
    val Warn = Color(0xFFE59A54)
    val Gift = Color(0xFFE5737A)
}

/** 제목용 둥근 글꼴 (Jua, SIL OFL 1.1 — docs/licenses/Jua-OFL.txt). 본문은 기기 기본(Noto Sans KR) */
val Jua = FontFamily(Font(R.font.jua_regular))

private val scheme = lightColorScheme(
    primary = Boong.Brown,
    onPrimary = Color.White,
    secondary = Boong.Bone,
    background = Boong.Cream,
    surface = Boong.Card,
    onBackground = Boong.Brown,
    onSurface = Boong.Brown,
)

/** 피그마 글자 크기 그대로 (화면 밀도 160dpi 라 1dp = 1px) */
private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = Jua, fontSize = 40.sp, color = Boong.Brown),   // 결과 합계
    headlineMedium = TextStyle(fontFamily = Jua, fontSize = 28.sp, color = Boong.Brown),  // 앱 제목
    headlineSmall = TextStyle(fontFamily = Jua, fontSize = 30.sp, color = Boong.Brown),   // '오늘의 드라이브'
    titleLarge = TextStyle(fontFamily = Jua, fontSize = 26.sp, color = Boong.Brown),      // '선물상자 2개' 등
    titleMedium = TextStyle(fontFamily = Jua, fontSize = 24.sp, color = Boong.Brown),     // 카드 제목·붕붕이 말
    bodyLarge = TextStyle(fontSize = 22.sp, color = Boong.BrownSoft),                     // 본문 줄
    bodyMedium = TextStyle(fontSize = 20.sp, color = Boong.BrownSoft),
    labelLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Boong.Brown), // 값(+18 등)
    labelMedium = TextStyle(fontSize = 19.sp, color = Boong.BrownSoft),                   // 작은 안내
)

@Composable
fun BoongTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

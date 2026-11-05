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

/** 시안 색상 — 디자인 캔버스 '붕붕멍 화면 시안 v3 (1682×1210)' 기준 */
object Boong {
    val Cream = Color(0xFFFFF7EE)        // 앱 배경
    val Card = Color(0xFFFFFFFF)
    val CardLine = Color(0xFFF0E2D3)     // 카드 테두리·헤더 아래선
    val Divider = Color(0xFFF3E6D8)      // 목록 구분선·진행바 배경
    val Brown = Color(0xFF3E2A1F)        // 글자·주 버튼
    val BrownSoft = Color(0xFF6B4E3B)    // 보조 글자
    val Muted = Color(0xFF7A5C48)        // 작은 라벨
    val Chip = Color(0xFFFFF0DC)         // 포인트 알약·선물상자 카드
    val ChipText = Color(0xFF8A4B12)
    val Nav = Color(0xFFF6EBDF)          // 탭 묶음 배경
    val Accent = Color(0xFFF2A33A)       // 진행바·선택 테두리
    val Bone = Color(0xFFE8922A)          // 뼈다귀 포인트
    val GoodBg = Color(0xFFDDF1EC)
    val Good = Color(0xFF1F6B5C)
    val DangerBg = Color(0xFFFBE4DE)
    val Danger = Color(0xFFB3412B)
    val Dashed = Color(0xFFE3CDB6)
    val Sky = Color(0xFFD8ECFA)          // 창밖 풍경
    val Ground = Color(0xFFE9D5BF)
    val GroundTop = Color(0xFFDCC2A6)
    val Outline = Color(0xFF5A3E2B)      // 캐릭터 외곽선

    // 캐릭터·다이얼로그용 추가 색
    val Frame = Brown
    val Line = Outline
    val Gift = Color(0xFFE2725B)
    val Cheek = Color(0xFFF7B3B5)
    val Ear = Color(0xFFF2D6B8)
}

/** 제목·숫자용 둥근 글꼴 (Jua, SIL OFL 1.1 — docs/licenses/Jua-OFL.txt). 본문은 기기 기본(Noto Sans KR) */
val Jua = FontFamily(Font(R.font.jua_regular))

private val scheme = lightColorScheme(
    primary = Boong.Brown,
    onPrimary = Boong.Cream,
    secondary = Boong.Accent,
    background = Boong.Cream,
    surface = Boong.Card,
    onBackground = Boong.Brown,
    onSurface = Boong.Brown,
)

/** v3 글자 크기 (화면 밀도 160dpi 라 1dp = 1px) */
private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = Jua, fontSize = 56.sp, color = Boong.Brown),
    headlineMedium = TextStyle(fontFamily = Jua, fontSize = 48.sp, color = Boong.Brown),
    headlineSmall = TextStyle(fontFamily = Jua, fontSize = 44.sp, color = Boong.Brown),
    titleLarge = TextStyle(fontFamily = Jua, fontSize = 36.sp, color = Boong.Brown),
    titleMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Boong.Brown),
    bodyLarge = TextStyle(fontSize = 26.sp, color = Boong.BrownSoft),
    bodyMedium = TextStyle(fontSize = 24.sp, color = Boong.BrownSoft),
    labelLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Boong.Brown),
    labelMedium = TextStyle(fontSize = 22.sp, color = Boong.Muted),
)

@Composable
fun BoongTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

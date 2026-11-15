package com.carsciencelab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 색은 정보의 출처를 구분하는 데 쓴다.
 * - 호박색: 방향지시등의 빛, 그리고 지금 누를 버튼
 * - 초록: 차량에서 확인한 사실(계기판 표시등의 색)
 * - 하늘색: 원리 모형과 가상 실험
 * 바탕은 해 질 녘 도로의 남색이다. 주차한 차 안에서 눈부시지 않게 어둡게 둔다.
 */
object LabColors {
    val Dusk = Color(0xFF161D31)
    val Asphalt = Color(0xFF222B45)
    val AsphaltRaised = Color(0xFF2D3858)
    val LaneWhite = Color(0xFFF3F1E7)
    val Muted = Color(0xFFB6BCD0)
    val Outline = Color(0xFF4A557E)
    val SignalAmber = Color(0xFFFFB300)
    val OnSignalAmber = Color(0xFF231800)
    val TelltaleGreen = Color(0xFF4FD08A)
    val OnTelltaleGreen = Color(0xFF05210F)
    val ModelBlue = Color(0xFF8FB7FF)
    val OnModelBlue = Color(0xFF0A1B3D)
    val LampOff = Color(0xFF55607F)
}

private val LabColorScheme = darkColorScheme(
    primary = LabColors.SignalAmber,
    onPrimary = LabColors.OnSignalAmber,
    secondary = LabColors.TelltaleGreen,
    onSecondary = LabColors.OnTelltaleGreen,
    tertiary = LabColors.ModelBlue,
    onTertiary = LabColors.OnModelBlue,
    background = LabColors.Dusk,
    onBackground = LabColors.LaneWhite,
    surface = LabColors.Asphalt,
    onSurface = LabColors.LaneWhite,
    surfaceVariant = LabColors.AsphaltRaised,
    onSurfaceVariant = LabColors.Muted,
    outline = LabColors.Outline,
)

/**
 * 차 안에서 팔 길이만큼 떨어져 읽는 글자 크기. 휴대전화 기본값보다 크게 잡는다.
 * 어린이 화면은 한 번에 한 문장만 보여 주므로 질문과 선택지가 가장 크다.
 */
private val LabTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 58.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 46.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 38.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 38.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 32.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
)

@Composable
fun CarScienceLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LabColorScheme,
        typography = LabTypography,
        content = content,
    )
}

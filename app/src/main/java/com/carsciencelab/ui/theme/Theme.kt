package com.carsciencelab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 시스템 화면과 같은 결의 밝은 바탕을 쓴다. 옅은 회색 바탕, 흰 카드, 검은 글자,
 * 가는 테두리다. 색은 정보의 출처를 구분하는 데만 쓴다.
 * - 먹색: 지금 누를 버튼과 선택된 항목(시스템의 기어 표시 P·R·N·D에서 선택된 칸과 같다)
 * - 주황: 방향지시등의 빛
 * - 초록: 차량에서 확인한 사실(계기판 표시등의 색)
 * - 파랑: 원리 모형과 가상 실험
 */
object LabColors {
    val Canvas = Color(0xFFF3F4F6)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSunken = Color(0xFFECEEF1)
    val Ink = Color(0xFF15171A)
    val OnInk = Color(0xFFFFFFFF)
    val Muted = Color(0xFF5F6672)
    val Outline = Color(0xFFD8DBE1)
    val SignalAmber = Color(0xFFFF9500)
    val OnSignalAmber = Color(0xFF231800)
    val TelltaleGreen = Color(0xFF0B8A47)
    val OnTelltaleGreen = Color(0xFFFFFFFF)
    val ModelBlue = Color(0xFF2A62F0)
    val OnModelBlue = Color(0xFFFFFFFF)
    val LampOff = Color(0xFFC5C9D1)

    /** 주의를 끄는 글자(힌트, 끄기 안내). 흰 바탕에서 읽히도록 주황을 어둡게 한 색이다. */
    val Caution = Color(0xFFA85800)

    // 원리 모형과 그림에 쓰는 차체 색. 흰 카드 위에서 윤곽이 보이도록 테두리를 함께 그린다.
    val CarBody = Color(0xFFE1E4E9)
    val CarRoof = Color(0xFFEEF0F3)
    val CarEdge = Color(0xFFAEB4BF)
    val CarGlass = Color(0xFF5B6472)

    /** 계기판에서 켜진 방향 표시 화살표의 밝은 초록. 글자가 아니라 그림에만 쓴다. */
    val TelltaleLit = Color(0xFF12B24E)
}

/**
 * 화면 요소가 무엇을 말하는지에 따라 고르는 색조.
 * 차량에서 확인한 사실([VEHICLE])과 원리 모형([MODEL])을 같은 색으로 쓰지 않는다.
 * [CAUTION]은 지금 해 줘야 할 일(먼저 끄기, 실험 뒤 끄기, 힌트)을 알릴 때 쓴다.
 */
enum class LabTone { NEUTRAL, ACTION, VEHICLE, MODEL, CAUTION }

val LabTone.accent: Color
    get() = when (this) {
        LabTone.NEUTRAL -> LabColors.Muted
        LabTone.ACTION -> LabColors.Ink
        LabTone.VEHICLE -> LabColors.TelltaleGreen
        LabTone.MODEL -> LabColors.ModelBlue
        LabTone.CAUTION -> LabColors.Caution
    }

val LabTone.onAccent: Color
    get() = when (this) {
        LabTone.NEUTRAL -> LabColors.Surface
        LabTone.ACTION -> LabColors.OnInk
        LabTone.VEHICLE -> LabColors.OnTelltaleGreen
        LabTone.MODEL -> LabColors.OnModelBlue
        LabTone.CAUTION -> LabColors.OnInk
    }

/** [base] 위에 색조를 옅게 깐 면. 글자는 [accent]나 먹색을 쓴다. */
fun LabTone.container(base: Color = LabColors.Surface): Color = when (this) {
    LabTone.NEUTRAL -> LabColors.SurfaceSunken
    // 주의는 글자색(어두운 주황)보다 방향지시등의 주황을 옅게 까는 편이 눈에 잘 띈다.
    LabTone.CAUTION -> LabColors.SignalAmber.copy(alpha = 0.16f).compositeOver(base)
    else -> accent.copy(alpha = 0.10f).compositeOver(base)
}

/** 색조 면의 가는 테두리. */
fun LabTone.outline(base: Color = LabColors.Surface): Color = when (this) {
    LabTone.NEUTRAL -> LabColors.Outline
    LabTone.CAUTION -> LabColors.SignalAmber.copy(alpha = 0.55f).compositeOver(base)
    else -> accent.copy(alpha = 0.35f).compositeOver(base)
}

/** 크기와 간격의 기준. 차 안에서 누르는 요소는 [TouchTarget]보다 작게 만들지 않는다. */
object LabDimens {
    val TouchTarget = 64.dp
    val ControlRadius = 16.dp
    val CardRadius = 20.dp
    val PanelRadius = 20.dp

    /** 화면 가장자리 여백. */
    val PageHorizontal = 28.dp
    val PageVertical = 20.dp

    /**
     * 이 폭부터 설명과 원리 모형을 좌우로 나란히 놓는다.
     * Pleos Connect의 앱 창은 화면 전체가 아니라 주행 정보 영역 옆의 일부여서 기준을 낮게 잡았다.
     */
    val TwoPaneMinWidth = 720.dp

    /** 한 단으로 보여 주는 화면에서 글줄이 너무 길어지지 않게 하는 폭. */
    val ReadingWidth = 880.dp

    /** 아주 넓은 창에서 홈과 기록 화면이 퍼지지 않게 하는 폭. */
    val PageWidth = 1200.dp
}

private val LabColorScheme = lightColorScheme(
    primary = LabColors.Ink,
    onPrimary = LabColors.OnInk,
    secondary = LabColors.TelltaleGreen,
    onSecondary = LabColors.OnTelltaleGreen,
    tertiary = LabColors.ModelBlue,
    onTertiary = LabColors.OnModelBlue,
    background = LabColors.Canvas,
    onBackground = LabColors.Ink,
    surface = LabColors.Surface,
    onSurface = LabColors.Ink,
    surfaceVariant = LabColors.SurfaceSunken,
    onSurfaceVariant = LabColors.Muted,
    outline = LabColors.Outline,
    outlineVariant = LabColors.Outline,
)

// 기기 언어가 한국어가 아니어도 한글을 어절 단위로 줄바꿈하도록 글자의 언어를 밝힌다.
private val Korean = LocaleList("ko-KR")

private fun labStyle(size: Int, lineHeight: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
    localeList = Korean,
)

/**
 * Pleos Connect 앱 창(주행 정보 영역 옆의 일부)에 맞춘 글자 크기. 시스템 화면의 글자보다 한두 단계 크게
 * 두되, 한 화면에 질문과 선택지 두 개가 스크롤 없이 들어오도록 했다. 16sp 아래로 내려가지 않는다.
 * 어린이 화면은 한 번에 한 문장만 보여 주므로 질문과 선택지가 가장 크다.
 */
private val LabTypography = Typography(
    displaySmall = labStyle(34, 44, FontWeight.Bold, tracking = -0.01f),
    headlineMedium = labStyle(28, 38, FontWeight.Bold, tracking = -0.01f),
    headlineSmall = labStyle(24, 34, FontWeight.Bold),
    titleLarge = labStyle(22, 30, FontWeight.Bold),
    titleMedium = labStyle(20, 28, FontWeight.SemiBold),
    bodyLarge = labStyle(20, 30, FontWeight.Medium),
    bodyMedium = labStyle(18, 27, FontWeight.Normal),
    labelLarge = labStyle(20, 26, FontWeight.SemiBold),
    labelMedium = labStyle(16, 22, FontWeight.Medium),
    labelSmall = labStyle(16, 22, FontWeight.Bold),
)

@Composable
fun CarScienceLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LabColorScheme,
        typography = LabTypography,
        content = content,
    )
}

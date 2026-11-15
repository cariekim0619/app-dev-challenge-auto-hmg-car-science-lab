package com.carsciencelab.ui.model

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.ui.theme.LabColors
import com.carsciencelab.ui.theme.LabText
import com.carsciencelab.ui.theme.LabTone
import com.carsciencelab.ui.theme.accent

/**
 * 홈 카드에 넣는 실험 그림. 실험이 무엇에 관한 것인지 글을 읽기 전에 알아보게 한다.
 * 차량 상태가 아니며 움직이지 않는다.
 */
@Composable
fun LessonArt(model: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (model) {
            MODEL_TURN_SIGNAL -> drawTurnSignalScene(
                leftLit = true,
                rightLit = false,
                patternActive = true,
                showPattern = false,
            )

            else -> drawPlaceholderArt()
        }
    }
}

/** 준비 중인 실험의 작은 그림. 아직 할 수 없다는 뜻으로 옅게 그린다. */
@Composable
fun PlannedLessonArt(lessonId: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (lessonId) {
            "wiper" -> drawWiperArt()
            "recirculation" -> drawAirArt()
            else -> drawPlaceholderArt()
        }
    }
}

/**
 * 실험을 마쳤을 때의 도장. 차선 점선을 둥글게 두르고 가운데에 확인 표시를 넣는다.
 * [tone]으로 차량 관찰(초록)과 가상 실험(파랑)을 구분한다.
 */
@Composable
fun CompletionStamp(tone: LabTone, modifier: Modifier = Modifier) {
    val accent = tone.accent
    Canvas(modifier = modifier) {
        val side = size.minDimension
        val middle = center
        val dashWidth = side * 0.055f
        val dashHeight = side * 0.12f
        for (index in 0 until 16) {
            rotate(degrees = index * 22.5f, pivot = middle) {
                drawRoundRect(
                    color = LabColors.SignalAmber,
                    topLeft = Offset(middle.x - dashWidth / 2f, middle.y - side / 2f),
                    size = Size(dashWidth, dashHeight),
                    cornerRadius = CornerRadius(dashWidth / 2f),
                )
            }
        }
        drawCircle(color = accent.copy(alpha = 0.12f), radius = side * 0.32f, center = middle)
        drawCircle(color = accent, radius = side * 0.32f, center = middle, style = Stroke(width = side * 0.028f))
        val check = Path().apply {
            moveTo(middle.x - side * 0.15f, middle.y + side * 0.01f)
            lineTo(middle.x - side * 0.04f, middle.y + side * 0.12f)
            lineTo(middle.x + side * 0.17f, middle.y - side * 0.10f)
        }
        drawPath(
            path = check,
            color = accent,
            style = Stroke(width = side * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * 주차 기어 표시. 시스템 화면의 기어 표시(P R N D)에서 선택된 칸과 같은 모양이다.
 * 학습을 잠갔을 때 무엇을 하면 되는지(주차) 한눈에 알린다.
 */
@Composable
fun ParkBadge(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val description = stringResource(R.string.blocked_badge_description)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(LabColors.Ink)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        LabText(text = "P", style = MaterialTheme.typography.displaySmall, color = LabColors.OnInk)
    }
}

// ---- 그리기 ----

private val ArtLine: Color = LabColors.CarEdge
private val ArtFill: Color = LabColors.CarBody

/** 앞유리와 와이퍼. 와이퍼가 쓸고 지나가는 자리를 점선으로 그린다. */
private fun DrawScope.drawWiperArt() {
    val s = size.minDimension
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    fun x(fraction: Float) = left + s * fraction
    fun y(fraction: Float) = top + s * fraction

    val glass = Path().apply {
        moveTo(x(0.22f), y(0.20f))
        lineTo(x(0.78f), y(0.20f))
        lineTo(x(0.94f), y(0.80f))
        lineTo(x(0.06f), y(0.80f))
        close()
    }
    drawPath(path = glass, color = ArtFill)
    drawPath(path = glass, color = ArtLine, style = Stroke(width = s * 0.035f, join = StrokeJoin.Round))

    val pivot = Offset(x(0.5f), y(0.78f))
    val reach = s * 0.46f
    drawArc(
        color = ArtLine,
        startAngle = 212f,
        sweepAngle = 116f,
        useCenter = false,
        topLeft = Offset(pivot.x - reach, pivot.y - reach),
        size = Size(reach * 2f, reach * 2f),
        style = Stroke(
            width = s * 0.022f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.045f, s * 0.05f)),
        ),
    )
    // 와이퍼 한 개를 왼쪽 위로 기울여 그린다.
    val tip = Offset(pivot.x - reach * 0.52f, pivot.y - reach * 0.855f)
    drawLine(color = LabColors.Muted, start = pivot, end = tip, strokeWidth = s * 0.05f, cap = StrokeCap.Round)
    drawCircle(color = LabColors.Muted, radius = s * 0.045f, center = pivot)
}

/** 바람이 흐르는 모습. 물결선 세 줄과 화살촉으로 그린다. */
private fun DrawScope.drawAirArt() {
    val s = size.minDimension
    val left = (size.width - s) / 2f
    val top = (size.height - s) / 2f
    fun x(fraction: Float) = left + s * fraction
    fun y(fraction: Float) = top + s * fraction

    val stroke = Stroke(width = s * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (index in 0 until 3) {
        val lineY = 0.28f + 0.22f * index
        val wave = Path().apply {
            moveTo(x(0.10f), y(lineY))
            cubicTo(x(0.24f), y(lineY - 0.11f), x(0.38f), y(lineY + 0.11f), x(0.52f), y(lineY))
            cubicTo(x(0.60f), y(lineY - 0.06f), x(0.70f), y(lineY - 0.04f), x(0.84f), y(lineY - 0.04f))
        }
        drawPath(path = wave, color = ArtLine, style = stroke)
        val head = Path().apply {
            moveTo(x(0.76f), y(lineY - 0.11f))
            lineTo(x(0.86f), y(lineY - 0.04f))
            lineTo(x(0.76f), y(lineY + 0.03f))
        }
        drawPath(path = head, color = ArtLine, style = stroke)
    }
}

/** 그림이 정해지지 않은 실험의 자리 표시. */
private fun DrawScope.drawPlaceholderArt() {
    val s = size.minDimension
    drawCircle(
        color = ArtLine,
        radius = s * 0.36f,
        center = center,
        style = Stroke(
            width = s * 0.03f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.07f, s * 0.07f)),
        ),
    )
}

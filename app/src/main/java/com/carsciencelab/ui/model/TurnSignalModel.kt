package com.carsciencelab.ui.model

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.ui.theme.LabColors
import com.carsciencelab.vehicle.TurnSignalCodes

/**
 * 방향지시등의 원리를 보여 주는 모형. 위에서 내려다본 차와 계기판 화살표, 깜빡임 패턴을 그린다.
 *
 * 이 모형은 차량 상태가 아니다. [activeCode]는 모형이 보여 줄 값이고, 깜빡이는 속도는 원리를
 * 설명하기 위한 것이어서 실제 차의 점멸 주기를 측정한 값이 아니다.
 * [onAction]은 모형의 레버를 눌렀을 때 불린다. 차량에는 아무 명령도 보내지 않는다.
 */
@Composable
fun TurnSignalModel(
    activeCode: String,
    interactive: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val leftActive = activeCode == TurnSignalCodes.LEFT_ACTIVE
    val rightActive = activeCode == TurnSignalCodes.RIGHT_ACTIVE
    // 켜져 있을 때만 깜빡임을 돌린다. 꺼져 있으면 움직이는 것이 없다.
    val phase: State<Float>? = if (leftActive || rightActive) rememberBlinkPhase() else null
    val description = stringResource(
        when {
            leftActive -> R.string.model_description_left
            rightActive -> R.string.model_description_right
            else -> R.string.model_description_off
        },
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                .semantics { contentDescription = description },
        ) {
            // 그리기 단계에서만 값을 읽어, 깜빡일 때 화면 전체를 다시 구성하지 않는다.
            val lit = phase == null || phase.value < 0.5f
            drawCar()
            drawLamps(leftOn = leftActive && lit, rightOn = rightActive && lit)
            drawArrow(pointsLeft = true, on = leftActive && lit)
            drawArrow(pointsLeft = false, on = rightActive && lit)
            drawBlinkPattern(active = leftActive || rightActive)
        }
        Text(
            text = stringResource(R.string.model_pattern_caption),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (interactive) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LeverButton(
                    text = stringResource(R.string.lever_left),
                    selected = leftActive,
                    onClick = { onAction(TurnSignalCodes.LEFT_ACTIVE) },
                    modifier = Modifier.weight(1f),
                )
                LeverButton(
                    text = stringResource(R.string.lever_off),
                    selected = !leftActive && !rightActive,
                    onClick = { onAction(TurnSignalCodes.OFF) },
                    modifier = Modifier.weight(1f),
                )
                LeverButton(
                    text = stringResource(R.string.lever_right),
                    selected = rightActive,
                    onClick = { onAction(TurnSignalCodes.RIGHT_ACTIVE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun rememberBlinkPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "blink")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BLINK_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blinkPhase",
    )
}

@Composable
private fun LeverButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

// ---- 그리기 ----

private const val BLINK_PERIOD_MS = 700

/** 차체가 차지하는 영역. 앞이 위쪽이다. */
private fun DrawScope.carBounds(): CarBounds {
    val width = size.width * 0.26f
    val height = size.height * 0.72f
    return CarBounds(
        left = (size.width - width) / 2f,
        top = size.height * 0.04f,
        width = width,
        height = height,
    )
}

private class CarBounds(val left: Float, val top: Float, val width: Float, val height: Float)

private fun DrawScope.drawCar() {
    val car = carBounds()
    drawRoundRect(
        color = LabColors.AsphaltRaised,
        topLeft = Offset(car.left, car.top),
        size = Size(car.width, car.height),
        cornerRadius = CornerRadius(car.width * 0.30f, car.width * 0.30f),
    )
    // 앞유리와 뒷유리
    drawRoundRect(
        color = LabColors.Dusk,
        topLeft = Offset(car.left + car.width * 0.14f, car.top + car.height * 0.20f),
        size = Size(car.width * 0.72f, car.height * 0.17f),
        cornerRadius = CornerRadius(car.width * 0.10f, car.width * 0.10f),
    )
    drawRoundRect(
        color = LabColors.Dusk,
        topLeft = Offset(car.left + car.width * 0.17f, car.top + car.height * 0.70f),
        size = Size(car.width * 0.66f, car.height * 0.12f),
        cornerRadius = CornerRadius(car.width * 0.10f, car.width * 0.10f),
    )
}

/** 네 모서리의 방향지시등. 켜진 쪽만 호박색으로 그린다. */
private fun DrawScope.drawLamps(leftOn: Boolean, rightOn: Boolean) {
    val car = carBounds()
    val lampWidth = car.width * 0.26f
    val lampHeight = car.height * 0.075f
    val leftX = car.left - lampWidth * 0.20f
    val rightX = car.left + car.width - lampWidth * 0.80f
    val frontY = car.top + car.height * 0.035f
    val rearY = car.top + car.height * 0.89f
    drawLamp(leftX, frontY, lampWidth, lampHeight, leftOn)
    drawLamp(leftX, rearY, lampWidth, lampHeight, leftOn)
    drawLamp(rightX, frontY, lampWidth, lampHeight, rightOn)
    drawLamp(rightX, rearY, lampWidth, lampHeight, rightOn)
}

private fun DrawScope.drawLamp(x: Float, y: Float, width: Float, height: Float, on: Boolean) {
    if (on) {
        // 빛이 번지는 느낌
        drawCircle(
            color = LabColors.SignalAmber.copy(alpha = 0.22f),
            radius = width * 0.95f,
            center = Offset(x + width / 2f, y + height / 2f),
        )
    }
    drawRoundRect(
        color = if (on) LabColors.SignalAmber else LabColors.LampOff,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f, height / 2f),
    )
}

/** 계기판의 방향 표시 화살표. 차 양옆에 크게 그린다. */
private fun DrawScope.drawArrow(pointsLeft: Boolean, on: Boolean) {
    val car = carBounds()
    val unit = size.height * 0.13f
    val centerX = if (pointsLeft) size.width * 0.17f else size.width * 0.83f
    val centerY = car.top + car.height * 0.42f
    val direction = if (pointsLeft) -1f else 1f
    val path = Path().apply {
        moveTo(centerX + direction * unit * 1.25f, centerY)
        lineTo(centerX, centerY - unit)
        lineTo(centerX, centerY - unit * 0.42f)
        lineTo(centerX - direction * unit * 1.25f, centerY - unit * 0.42f)
        lineTo(centerX - direction * unit * 1.25f, centerY + unit * 0.42f)
        lineTo(centerX, centerY + unit * 0.42f)
        lineTo(centerX, centerY + unit)
        close()
    }
    drawPath(path = path, color = if (on) LabColors.TelltaleGreen else LabColors.LampOff)
}

/**
 * 켜짐과 꺼짐이 같은 간격으로 반복되는 모습을 차선 점선처럼 그린다.
 * 시간에 따른 신호의 모양을 보여 주는 그림이며 측정값이 아니다.
 */
private fun DrawScope.drawBlinkPattern(active: Boolean) {
    val count = 9
    val stripLeft = size.width * 0.08f
    val stripWidth = size.width * 0.84f
    val slot = stripWidth / count
    val dashHeight = size.height * 0.055f
    val y = size.height * 0.89f
    for (index in 0 until count) {
        val isOnSlot = index % 2 == 0
        val color = when {
            !active -> LabColors.Outline
            isOnSlot -> LabColors.SignalAmber
            else -> LabColors.LampOff
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(stripLeft + slot * index + slot * 0.08f, y),
            size = Size(slot * 0.84f, dashHeight),
            cornerRadius = CornerRadius(dashHeight / 2f, dashHeight / 2f),
        )
    }
}

package com.carsciencelab.ui.model

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.ui.theme.LabColors
import com.carsciencelab.ui.theme.LabDimens
import com.carsciencelab.ui.theme.LabText
import com.carsciencelab.vehicle.TurnSignalCodes
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 콘텐츠의 `model` 값 가운데 방향지시등 모형을 가리키는 이름. */
const val MODEL_TURN_SIGNAL = "turn_signal_pattern"

/**
 * 방향지시등의 원리를 보여 주는 모형. 위에서 내려다본 차와 계기판 화살표, 깜빡임 패턴을 그린다.
 *
 * 이 모형은 차량 상태가 아니다. [activeCode]는 모형이 보여 줄 값이고, 깜빡이는 속도는 원리를
 * 설명하기 위한 것이어서 실제 차의 점멸 주기를 측정한 값이 아니다.
 * [onAction]은 모형의 레버를 눌렀을 때 불린다. 차량에는 아무 명령도 보내지 않는다.
 *
 * [fillHeight]가 참이면 남는 높이에 그림을 맞춘다. 레버가 화면 밖으로 밀려나지 않게 하기 위해서다.
 */
@Composable
fun TurnSignalModel(
    activeCode: String,
    interactive: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    hint: String? = null,
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val sceneModifier = if (fillHeight) {
            Modifier
                .fillMaxWidth()
                .weight(1f)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(TURN_SIGNAL_SCENE_ASPECT)
        }
        Canvas(modifier = sceneModifier.semantics { contentDescription = description }) {
            // 그리기 단계에서만 값을 읽어, 깜빡일 때 화면 전체를 다시 구성하지 않는다.
            val lit = phase == null || phase.value < 0.5f
            drawTurnSignalScene(
                leftLit = leftActive && lit,
                rightLit = rightActive && lit,
                patternActive = leftActive || rightActive,
                showPattern = true,
            )
        }
        LabText(
            text = stringResource(R.string.model_pattern_caption),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (interactive) {
            LeverRow(leftActive = leftActive, rightActive = rightActive, onAction = onAction)
            if (hint != null) {
                LabText(
                    text = hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 모형의 레버. 왼쪽, 끄기, 오른쪽 가운데 하나를 고른다. */
@Composable
private fun LeverRow(leftActive: Boolean, rightActive: Boolean, onAction: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 좁으면 글자만 남긴다.
        val showArrows = maxWidth >= 520.dp
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LeverButton(
                text = stringResource(R.string.lever_left),
                selected = leftActive,
                onClick = { onAction(TurnSignalCodes.LEFT_ACTIVE) },
                modifier = Modifier.weight(1f),
                arrowPointsLeft = if (showArrows) true else null,
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
                arrowPointsLeft = if (showArrows) false else null,
            )
        }
    }
}

@Composable
private fun LeverButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrowPointsLeft: Boolean? = null,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = LabDimens.TouchTarget)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(LabDimens.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = contentColor,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (arrowPointsLeft == true) {
            BlockArrow(pointsLeft = true, color = contentColor, modifier = Modifier.size(width = 30.dp, height = 24.dp))
            Spacer(modifier = Modifier.width(10.dp))
        }
        LabText(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        if (arrowPointsLeft == false) {
            Spacer(modifier = Modifier.width(10.dp))
            BlockArrow(pointsLeft = false, color = contentColor, modifier = Modifier.size(width = 30.dp, height = 24.dp))
        }
    }
}

/**
 * 계기판의 방향 표시와 같은 모양의 화살표. 모형 밖에서도(예측 선택지, 레버) 같은 모양을 써서
 * ‘화살표’가 무엇을 가리키는지 한눈에 이어지게 한다.
 */
@Composable
fun BlockArrow(pointsLeft: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = min(size.height / 2f, size.width / 2.5f) * 0.9f
        drawBlockArrow(center = center, unit = unit, pointsLeft = pointsLeft, color = color)
    }
}

// ---- 그리기 ----

private const val BLINK_PERIOD_MS = 700

/** 높이를 정해 주지 않았을 때 쓰는 장면의 가로세로 비. */
internal const val TURN_SIGNAL_SCENE_ASPECT = 1.7f

// 그릴 자리가 이 범위보다 길쭉하거나 납작하면 장면을 가운데에 맞추고 남는 곳은 비운다.
private const val MIN_SCENE_ASPECT = 1.15f
private const val MAX_SCENE_ASPECT = 1.9f

/** 그림을 그릴 영역. 비율을 지켜 캔버스 가운데에 놓는다. */
private class Scene(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun x(fraction: Float) = left + width * fraction
    fun y(fraction: Float) = top + height * fraction
}

/** 차체가 차지하는 영역. 앞이 위쪽이다. */
private class CarRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

private fun DrawScope.fitScene(): Scene {
    // 칸이 높으면 차를 그만큼 크게 그린다. 모형 칸이 화면 높이를 다 쓰는 넓은 창에서 그림이 작아지지 않는다.
    val aspect = (size.width / size.height).coerceIn(MIN_SCENE_ASPECT, MAX_SCENE_ASPECT)
    val width = min(size.width, size.height * aspect)
    val height = width / aspect
    return Scene(
        left = (size.width - width) / 2f,
        top = (size.height - height) / 2f,
        width = width,
        height = height,
    )
}

/**
 * 위에서 내려다본 차, 네 모서리의 방향지시등, 계기판 화살표를 그린다.
 * [showPattern]이면 아래에 깜빡임 패턴을 함께 그린다. 홈 카드의 그림처럼 움직이지 않는 곳에서도 쓴다.
 */
internal fun DrawScope.drawTurnSignalScene(
    leftLit: Boolean,
    rightLit: Boolean,
    patternActive: Boolean,
    showPattern: Boolean,
) {
    val scene = fitScene()
    val carHeight = scene.height * (if (showPattern) 0.73f else 0.86f)
    val carWidth = carHeight * 0.45f
    val car = CarRect(
        left = scene.x(0.5f) - carWidth / 2f,
        top = scene.y(if (showPattern) 0.06f else 0.07f),
        width = carWidth,
        height = carHeight,
    )
    val arrowY = car.top + car.height * 0.44f
    val arrowUnit = min(scene.height * 0.125f, scene.width * 0.075f)
    drawTelltale(center = Offset(scene.x(0.15f), arrowY), unit = arrowUnit, pointsLeft = true, on = leftLit)
    drawTelltale(center = Offset(scene.x(0.85f), arrowY), unit = arrowUnit, pointsLeft = false, on = rightLit)
    drawCar(car)
    drawLamps(car, leftOn = leftLit, rightOn = rightLit)
    if (showPattern) drawBlinkPattern(scene, active = patternActive)
}

private fun DrawScope.drawCar(car: CarRect) {
    val w = car.width
    val h = car.height

    // 바퀴. 차체 옆으로 조금 나와 보인다.
    val wheelWidth = w * 0.17f
    val wheelHeight = h * 0.17f
    val wheelRadius = CornerRadius(w * 0.05f)
    for (fraction in WHEEL_POSITIONS) {
        val y = car.top + h * fraction
        drawRoundRect(
            color = LabColors.CarGlass,
            topLeft = Offset(car.left - w * 0.055f, y),
            size = Size(wheelWidth, wheelHeight),
            cornerRadius = wheelRadius,
        )
        drawRoundRect(
            color = LabColors.CarGlass,
            topLeft = Offset(car.right - wheelWidth + w * 0.055f, y),
            size = Size(wheelWidth, wheelHeight),
            cornerRadius = wheelRadius,
        )
    }

    // 사이드미러
    val mirrorWidth = w * 0.15f
    val mirrorHeight = h * 0.045f
    val mirrorY = car.top + h * 0.305f
    val mirrorRadius = CornerRadius(mirrorHeight / 2f)
    drawRoundRect(
        color = LabColors.CarBody,
        topLeft = Offset(car.left - mirrorWidth * 0.75f, mirrorY),
        size = Size(mirrorWidth, mirrorHeight),
        cornerRadius = mirrorRadius,
    )
    drawRoundRect(
        color = LabColors.CarBody,
        topLeft = Offset(car.right - mirrorWidth * 0.25f, mirrorY),
        size = Size(mirrorWidth, mirrorHeight),
        cornerRadius = mirrorRadius,
    )

    // 차체
    val bodyRadius = CornerRadius(w * BODY_CORNER, w * BODY_CORNER)
    drawRoundRect(
        color = LabColors.CarBody,
        topLeft = Offset(car.left, car.top),
        size = Size(w, h),
        cornerRadius = bodyRadius,
    )
    drawRoundRect(
        color = LabColors.CarEdge,
        topLeft = Offset(car.left, car.top),
        size = Size(w, h),
        cornerRadius = bodyRadius,
        style = Stroke(width = w * 0.02f),
    )

    // 유리(앞유리, 옆유리, 뒷유리)와 그 안쪽의 지붕. 앞유리를 뒷유리보다 넓게 그려 앞뒤를 구분한다.
    drawRoundRect(
        color = LabColors.CarGlass,
        topLeft = Offset(car.left + w * 0.11f, car.top + h * 0.215f),
        size = Size(w * 0.78f, h * 0.615f),
        cornerRadius = CornerRadius(w * 0.20f),
    )
    drawRoundRect(
        color = LabColors.CarRoof,
        topLeft = Offset(car.left + w * 0.17f, car.top + h * 0.385f),
        size = Size(w * 0.66f, h * 0.36f),
        cornerRadius = CornerRadius(w * 0.11f),
    )
    // 보닛 선
    drawLine(
        color = LabColors.CarEdge,
        start = Offset(car.left + w * 0.24f, car.top + h * 0.145f),
        end = Offset(car.right - w * 0.24f, car.top + h * 0.145f),
        strokeWidth = w * 0.016f,
        cap = StrokeCap.Round,
    )
}

/** 네 모서리의 방향지시등. 모서리를 따라 감싼 모양이고, 켜진 쪽만 호박색으로 그린다. */
private fun DrawScope.drawLamps(car: CarRect, leftOn: Boolean, rightOn: Boolean) {
    val w = car.width
    val corner = w * BODY_CORNER
    val stroke = w * 0.085f
    val radius = corner - stroke / 2f - w * 0.012f
    // 각도는 3시 방향에서 시계 방향으로 잰다.
    drawCornerLamp(Offset(car.left + corner, car.top + corner), radius, stroke, startAngle = 196f, on = leftOn)
    drawCornerLamp(Offset(car.left + corner, car.bottom - corner), radius, stroke, startAngle = 106f, on = leftOn)
    drawCornerLamp(Offset(car.right - corner, car.top + corner), radius, stroke, startAngle = 286f, on = rightOn)
    drawCornerLamp(Offset(car.right - corner, car.bottom - corner), radius, stroke, startAngle = 16f, on = rightOn)

    // 사이드미러의 보조 방향지시등
    val dotRadius = w * 0.035f
    val dotY = car.top + car.height * 0.3275f
    drawCircle(
        color = if (leftOn) LabColors.SignalAmber else LabColors.LampOff,
        radius = dotRadius,
        center = Offset(car.left - w * 0.075f, dotY),
    )
    drawCircle(
        color = if (rightOn) LabColors.SignalAmber else LabColors.LampOff,
        radius = dotRadius,
        center = Offset(car.right + w * 0.075f, dotY),
    )
}

private fun DrawScope.drawCornerLamp(center: Offset, radius: Float, stroke: Float, startAngle: Float, on: Boolean) {
    if (on) {
        // 빛이 번지는 느낌
        val middle = Math.toRadians((startAngle + LAMP_SWEEP / 2f).toDouble())
        val glowCenter = Offset(
            x = center.x + radius * cos(middle).toFloat(),
            y = center.y + radius * sin(middle).toFloat(),
        )
        val glowRadius = radius * 2.1f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(LabColors.SignalAmber.copy(alpha = 0.5f), LabColors.SignalAmber.copy(alpha = 0f)),
                center = glowCenter,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = glowCenter,
        )
    }
    drawArc(
        color = if (on) LabColors.SignalAmber else LabColors.LampOff,
        startAngle = startAngle,
        sweepAngle = LAMP_SWEEP,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/** 계기판의 방향 표시 화살표. 차 양옆에 크게 그리고, 켜지면 초록으로 빛난다. */
private fun DrawScope.drawTelltale(center: Offset, unit: Float, pointsLeft: Boolean, on: Boolean) {
    if (on) {
        val glowRadius = unit * 2.4f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(LabColors.TelltaleLit.copy(alpha = 0.30f), LabColors.TelltaleLit.copy(alpha = 0f)),
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )
    }
    drawBlockArrow(
        center = center,
        unit = unit,
        pointsLeft = pointsLeft,
        color = if (on) LabColors.TelltaleLit else LabColors.LampOff,
    )
}

/** 폭 2.5 × 높이 2 [unit] 크기의 화살표. 모서리를 둥글린다. */
internal fun DrawScope.drawBlockArrow(center: Offset, unit: Float, pointsLeft: Boolean, color: Color) {
    val direction = if (pointsLeft) -1f else 1f
    // 선 굵기만큼 안쪽으로 줄여 그려, 둥글린 뒤의 크기가 unit에 맞게 한다.
    val round = unit * 0.16f
    val u = unit - round / 2f
    val path = Path().apply {
        moveTo(center.x + direction * u * 1.25f, center.y)
        lineTo(center.x, center.y - u)
        lineTo(center.x, center.y - u * 0.42f)
        lineTo(center.x - direction * u * 1.25f, center.y - u * 0.42f)
        lineTo(center.x - direction * u * 1.25f, center.y + u * 0.42f)
        lineTo(center.x, center.y + u * 0.42f)
        lineTo(center.x, center.y + u)
        close()
    }
    drawPath(path = path, color = color)
    drawPath(path = path, color = color, style = Stroke(width = round, join = StrokeJoin.Round))
}

/**
 * 켜짐과 꺼짐이 같은 간격으로 반복되는 모습을 차선 점선처럼 그린다.
 * 시간에 따른 신호의 모양을 보여 주는 그림이며 측정값이 아니다.
 */
private fun DrawScope.drawBlinkPattern(scene: Scene, active: Boolean) {
    val count = 9
    val stripLeft = scene.x(0.08f)
    val slot = scene.width * 0.84f / count
    val dashHeight = scene.height * 0.052f
    val y = scene.y(0.895f)
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

private val WHEEL_POSITIONS = floatArrayOf(0.125f, 0.695f)
private const val BODY_CORNER = 0.30f
private const val LAMP_SWEEP = 58f

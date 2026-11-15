package com.carsciencelab.ui.screen

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LessonStage
import com.carsciencelab.ui.model.BlockArrow
import com.carsciencelab.ui.theme.LabColors
import com.carsciencelab.ui.theme.LabDimens
import com.carsciencelab.ui.theme.LabText
import com.carsciencelab.ui.theme.LabTone
import com.carsciencelab.ui.theme.accent
import com.carsciencelab.ui.theme.container
import com.carsciencelab.ui.theme.outline

// ---- 버튼 ----

/** 지금 누를 버튼. 한 화면에 하나만 두고, 차 안에서 누르기 쉽게 크게 만든다. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = LabDimens.TouchTarget),
        shape = RoundedCornerShape(LabDimens.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = LabColors.SurfaceSunken,
            disabledContentColor = LabColors.Muted,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
    ) {
        ButtonContent(text = text, icon = icon, style = MaterialTheme.typography.labelLarge)
    }
}

/** 두 번째 선택지. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = LabDimens.TouchTarget),
        shape = RoundedCornerShape(LabDimens.ControlRadius),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        ButtonContent(text = text, icon = icon, style = MaterialTheme.typography.labelLarge)
    }
}

/** 덜 중요한 동작. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(LabDimens.ControlRadius),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        ButtonContent(text = text, icon = icon, style = MaterialTheme.typography.labelSmall, iconSize = 22.dp)
    }
}

/** 화면 위쪽에서 앞 화면으로 돌아가는 버튼. 시스템의 닫기 버튼처럼 테두리가 있는 네모 칸이다. */
@Composable
fun BackButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(start = 12.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
    ) {
        ButtonContent(
            text = text,
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            style = MaterialTheme.typography.labelSmall,
            iconSize = 22.dp,
        )
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?, style: TextStyle, iconSize: Dp = 26.dp) {
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize))
        Spacer(modifier = Modifier.width(8.dp))
    }
    LabText(text = text, style = style, textAlign = TextAlign.Center)
}

/** 넓으면 가로로, 좁으면 세로로 놓는 영역. 버튼을 나란히 놓을 때 쓴다. */
@Composable
fun ButtonArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 520.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

// ---- 선택지 ----

enum class ChoiceArrow { NONE, LEFT, RIGHT }

/**
 * 예측과 이해 확인의 선택지 하나.
 * [tried]는 이미 골랐다가 틀린 답이라는 뜻이고, [arrow]는 글자 옆에 붙이는 방향 화살표다.
 */
data class ChoiceUi(
    val id: String,
    val label: String,
    val tried: Boolean = false,
    val arrow: ChoiceArrow = ChoiceArrow.NONE,
)

/** 예측과 이해 확인에 쓰는 큰 선택지. 어린이 화면은 한 번에 두 개만 보여 준다. */
@Composable
fun ChoiceButton(choice: ChoiceUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tried = choice.tried
    val contentColor = if (tried) LabColors.Muted else MaterialTheme.colorScheme.onSurface
    val triedDescription = stringResource(R.string.choice_tried)
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 96.dp)
            .semantics { if (tried) stateDescription = triedDescription },
        shape = RoundedCornerShape(LabDimens.CardRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (tried) LabColors.SurfaceSunken else MaterialTheme.colorScheme.surface,
            contentColor = contentColor,
        ),
        elevation = null,
        border = if (tried) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface)
        },
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
    ) {
        when {
            tried -> {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }

            choice.arrow == ChoiceArrow.LEFT -> {
                BlockArrow(pointsLeft = true, color = contentColor, modifier = Modifier.size(width = 44.dp, height = 36.dp))
                Spacer(modifier = Modifier.width(14.dp))
            }
        }
        LabText(
            text = choice.label,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!tried && choice.arrow == ChoiceArrow.RIGHT) {
            Spacer(modifier = Modifier.width(14.dp))
            BlockArrow(pointsLeft = false, color = contentColor, modifier = Modifier.size(width = 44.dp, height = 36.dp))
        }
    }
}

/** 선택지 두 개를 넓으면 나란히, 좁으면 위아래로 놓는다. */
@Composable
fun ChoicePair(
    first: ChoiceUi,
    second: ChoiceUi,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 480.dp) {
            // 글이 길어 한쪽만 두 줄이 되어도 두 선택지의 높이를 맞춘다.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChoiceButton(
                    choice = first,
                    onClick = { onChoose(first.id) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                ChoiceButton(
                    choice = second,
                    onClick = { onChoose(second.id) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceButton(choice = first, onClick = { onChoose(first.id) }, modifier = Modifier.fillMaxWidth())
                ChoiceButton(choice = second, onClick = { onChoose(second.id) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---- 표시 ----

/** 짧은 표시. [dotColor]가 있으면 앞에 점을 찍는다. */
@Composable
fun Pill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        LabText(text = text, color = contentColor, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * 한 문장짜리 알림 상자. 색조로 무엇에 관한 말인지 구분한다.
 * [base]는 이 상자가 놓이는 바탕색이다.
 */
@Composable
fun Callout(
    text: String,
    tone: LabTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Rounded.Info,
    base: Color = MaterialTheme.colorScheme.background,
) {
    val shape = RoundedCornerShape(LabDimens.ControlRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.container(base))
            .border(1.dp, tone.outline(base), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone.accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(26.dp),
            )
        }
        LabText(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 흰 카드. Pleos Connect 시스템 화면처럼 그림자 없이 가는 테두리로만 바탕과 구분한다. */
@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(LabDimens.CardRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier,
        content = content,
    )
}

/** 구역을 나누는 가는 선. */
@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

/** 탐험(어린이)과 이해(보호자) 모드를 바꾼다. 바꿔도 실험 진행은 그대로다. */
@Composable
fun ModeToggle(mode: LearningMode, onSelect: (LearningMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        ModeOption(
            audience = stringResource(R.string.mode_explore_audience),
            label = stringResource(R.string.mode_explore),
            selected = mode == LearningMode.EXPLORE,
            onClick = { onSelect(LearningMode.EXPLORE) },
        )
        ModeOption(
            audience = stringResource(R.string.mode_understand_audience),
            label = stringResource(R.string.mode_understand),
            selected = mode == LearningMode.UNDERSTAND,
            onClick = { onSelect(LearningMode.UNDERSTAND) },
        )
    }
}

/** 누구를 위한 모드인지([audience])를 이름([label]) 앞에 함께 적는다. */
@Composable
private fun ModeOption(audience: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LabText(
            text = audience,
            color = foreground.copy(alpha = if (selected) 0.72f else 0.85f),
            style = MaterialTheme.typography.labelMedium,
        )
        LabText(text = label, color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

// ---- 진행 표시 ----

private val StepLabels = listOf(
    R.string.step_predict,
    R.string.step_observe,
    R.string.step_explain,
    R.string.step_check,
)

/**
 * 실험이 어디까지 왔는지 이름 붙은 네 단계로 보여 준다. 단계 사이는 차선 점선처럼 잇는다.
 * [compact]이면 지금 단계의 이름만 적는다.
 */
@Composable
fun StageStepper(stage: LessonStage, modifier: Modifier = Modifier, compact: Boolean = false) {
    val current = when (stage) {
        LessonStage.READY -> -1
        LessonStage.PREDICT -> 0
        LessonStage.WAITING, LessonStage.OBSERVED -> 1
        LessonStage.EXPLAIN -> 2
        LessonStage.CHECK -> 3
        LessonStage.COMPLETE -> StepLabels.size
    }
    val labels = StepLabels.map { stringResource(it) }
    val description = when {
        current < 0 -> stringResource(R.string.stage_progress_before, labels.size)
        current >= labels.size -> stringResource(R.string.stage_progress_done, labels.size)
        else -> stringResource(R.string.stage_progress, current + 1, labels.size, labels[current])
    }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = if (compact) 6.dp else 10.dp)
                        .width(if (compact) 14.dp else 20.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index <= current) LabColors.Ink else LabColors.Outline),
                )
            }
            StepMarker(number = index + 1, done = index < current, active = index == current)
            if (!compact || index == current) {
                Spacer(modifier = Modifier.width(8.dp))
                LabText(
                    text = label,
                    style = if (index == current) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    color = if (index <= current) LabColors.Ink else LabColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun StepMarker(number: Int, done: Boolean, active: Boolean) {
    val base = Modifier
        .size(30.dp)
        .clip(CircleShape)
    Box(
        modifier = when {
            active -> base.background(LabColors.Ink)
            done -> base.background(LabColors.SurfaceSunken)
            else -> base.border(1.5.dp, LabColors.Outline, CircleShape)
        },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = LabColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        } else {
            LabText(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (active) LabColors.OnInk else LabColors.Muted,
            )
        }
    }
}

/** 설명 문장 여러 개 가운데 몇 번째인지. 지금 문장만 길게 그린다. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.explain_page, current + 1, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until count) {
            val active = index == current
            Box(
                modifier = Modifier
                    .width(if (active) 32.dp else 10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (active) LabColors.Ink else LabColors.Outline),
            )
        }
    }
}

/**
 * 차량이 알려 주기를 기다리는 동안만 보이는 표시. 점 세 개가 차례로 짙어진다.
 * 차량에서 언제 답이 올지 알 수 없으므로 진행률이 아니라 ‘듣고 있음’만 알린다.
 */
@Composable
fun ListeningDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "listening")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "listeningPhase",
    )
    Canvas(modifier = modifier.size(width = 44.dp, height = 12.dp)) {
        val radius = size.height / 2f
        val gap = (size.width - radius * 6f) / 2f
        for (index in 0 until 3) {
            // 그리기 단계에서만 값을 읽어 화면을 다시 구성하지 않는다.
            val local = ((phase.value - index * 0.2f) % 1f + 1f) % 1f
            val alpha = if (local < 0.4f) 1f - (local / 0.4f) * 0.72f else 0.28f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = Offset(radius + index * (radius * 2f + gap), size.height / 2f),
            )
        }
    }
}

// ---- 배치 ----

/** 단계나 화면이 바뀔 때 쓰는 짧은 겹침 전환. 크기는 움직이지 않는다. */
internal fun quickFade(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 70)),
    initialContentExit = fadeOut(animationSpec = tween(durationMillis = 90)),
    sizeTransform = null,
)

/** 스크롤할 내용이 아래에 더 있으면 아래쪽 가장자리를 흐리게 해 알려 준다. */
fun Modifier.fadingBottomEdge(scroll: ScrollState, length: Dp = 32.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (scroll.canScrollForward) {
            val fade = length.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * 실험 화면의 배치.
 *
 * 넓은 창에서는 왼쪽에 설명, 오른쪽에 원리 모형([side])을 놓고 좁은 창에서는 위아래로 쌓는다.
 * 왼쪽은 읽을 것([top])을 위에, 누르거나 확인할 것([bottom])을 아래에 붙이고 그 밑에 단계의
 * 버튼([actions])을 고정한다. 글이 길어도 버튼이 화면 밖으로 밀리지 않는다.
 *
 * 아래에 붙이는 데에는 이유가 하나 더 있다. Pleos Connect는 방향지시등이 켜지면 앱 창 왼쪽 위에
 * 사각지대 카메라 창을 띄운다. 그 순간 확인해야 하는 차량 상태와 다음 버튼이 가려지지 않아야 한다.
 *
 * [scrollKey]가 바뀌면 스크롤을 맨 위로 되돌린다.
 */
@Composable
fun LessonPanes(
    scrollKey: Any?,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    side: @Composable (fillHeight: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(scrollKey) { scroll.scrollTo(0) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= LabDimens.TwoPaneMinWidth) {
            // 모형 칸이 높이를 다 쓸 수 있으면 레버까지 스크롤 없이 보이도록 그림을 남는 높이에 맞춘다.
            val sideFillsHeight = maxHeight >= 420.dp
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .fadingBottomEdge(scroll)
                            .verticalScroll(scroll),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        top()
                        Box(modifier = Modifier.padding(top = 18.dp)) { bottom() }
                    }
                    actions()
                }
                if (sideFillsHeight) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        side(true)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        side(false)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .fadingBottomEdge(scroll)
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    top()
                    bottom()
                    side(false)
                }
                actions()
            }
        }
    }
}

/** 홈과 기록처럼 위에서 아래로 읽는 화면. 아주 넓은 창에서는 가운데로 모은다. */
@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = LabDimens.PageWidth,
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * 한 가지에만 집중하는 화면(이해 확인, 완료, 멈춤). 글줄이 너무 길어지지 않게 폭을 좁히고
 * 화면 가운데에 놓는다. 내용이 길면 스크롤된다.
 */
@Composable
fun FocusColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = LabDimens.ReadingWidth,
    spacing: Dp = 20.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

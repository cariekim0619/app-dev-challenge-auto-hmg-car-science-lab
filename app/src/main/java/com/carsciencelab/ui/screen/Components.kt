package com.carsciencelab.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LessonStage

/** 지금 누를 버튼. 차 안에서 누르기 쉽게 크게 만든다. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(38.dp),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 16.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** 두 번째 선택지. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(38.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** 덜 중요한 동작. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 예측과 이해 확인에 쓰는 큰 선택지. 어린이 화면은 한 번에 두 개만 보여 준다. */
@Composable
fun ChoiceButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    }
}

/** 선택지 두 개를 넓으면 나란히, 좁으면 위아래로 놓는다. */
@Composable
fun ChoicePair(
    first: String,
    second: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ChoiceButton(text = first, onClick = onFirst, modifier = Modifier.weight(1f))
                ChoiceButton(text = second, onClick = onSecond, modifier = Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ChoiceButton(text = first, onClick = onFirst, modifier = Modifier.fillMaxWidth())
                ChoiceButton(text = second, onClick = onSecond, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

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
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(text = text, color = contentColor, style = MaterialTheme.typography.labelMedium)
    }
}

/** 탐험(어린이)과 이해(보호자) 모드를 바꾼다. 바꿔도 실험 진행은 그대로다. */
@Composable
fun ModeToggle(mode: LearningMode, onSelect: (LearningMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
    ) {
        ModeOption(
            text = stringResource(R.string.mode_explore),
            selected = mode == LearningMode.EXPLORE,
            onClick = { onSelect(LearningMode.EXPLORE) },
        )
        ModeOption(
            text = stringResource(R.string.mode_understand),
            selected = mode == LearningMode.UNDERSTAND,
            onClick = { onSelect(LearningMode.UNDERSTAND) },
        )
    }
}

@Composable
private fun ModeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = foreground, style = MaterialTheme.typography.labelMedium)
    }
}

/** 실험이 어디까지 왔는지 차선 점선 모양으로 보여 준다. 단계가 실제로 순서대로 진행된다. */
@Composable
fun StageDashes(stage: LessonStage, modifier: Modifier = Modifier) {
    val done = when (stage) {
        LessonStage.READY -> 0
        LessonStage.PREDICT -> 1
        LessonStage.WAITING, LessonStage.OBSERVED -> 2
        LessonStage.EXPLAIN -> 3
        LessonStage.CHECK -> 4
        LessonStage.COMPLETE -> 5
    }
    val description = stringResource(R.string.stage_progress, done, STAGE_DASH_COUNT)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (index in 0 until STAGE_DASH_COUNT) {
            val color = if (index < done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
    }
}

private const val STAGE_DASH_COUNT = 5

/**
 * 넓은 창에서는 왼쪽에 설명, 오른쪽에 원리 모형을 놓고 좁은 창에서는 위아래로 쌓는다.
 * 창 크기가 바뀌어도 같은 내용을 보여 준다.
 */
@Composable
fun TwoPane(
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= 900.dp) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    primary()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    secondary()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                primary()
                secondary()
            }
        }
    }
}

package com.carsciencelab.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.content.LessonContent
import com.carsciencelab.content.ModeContent
import com.carsciencelab.content.PredictionOption
import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.learning.LessonStage
import com.carsciencelab.learning.LessonState
import com.carsciencelab.learning.LinkState
import com.carsciencelab.learning.SafetyHold
import com.carsciencelab.learning.VehicleValidity
import com.carsciencelab.learning.WaitingHint
import com.carsciencelab.ui.ActiveLessonUi
import com.carsciencelab.ui.LabController
import com.carsciencelab.ui.model.CompletionStamp
import com.carsciencelab.ui.model.MODEL_TURN_SIGNAL
import com.carsciencelab.ui.model.TurnSignalModel
import com.carsciencelab.ui.theme.LabColors
import com.carsciencelab.ui.theme.LabDimens
import com.carsciencelab.ui.theme.LabText
import com.carsciencelab.ui.theme.LabTone
import com.carsciencelab.ui.theme.accent
import com.carsciencelab.ui.theme.container
import com.carsciencelab.ui.theme.onAccent
import com.carsciencelab.ui.theme.outline
import com.carsciencelab.vehicle.TurnSignalCodes

/** 실험 화면이 지금 어떤 모양인지. 단계가 아니라 화면의 틀이 바뀌는 지점이다. */
private enum class LessonView { HOLD, PANES, CHECK, COMPLETE }

private fun viewOf(state: LessonState): LessonView = when {
    state.hold != SafetyHold.NONE -> LessonView.HOLD
    state.stage == LessonStage.CHECK -> LessonView.CHECK
    state.stage == LessonStage.COMPLETE -> LessonView.COMPLETE
    else -> LessonView.PANES
}

/**
 * 한 시점에 화면에 보여 줄 것의 묶음.
 * 단계가 바뀌어 이전 내용이 사라지는 동안에도 그 내용은 자기 시점의 값을 그대로 쓴다.
 */
private data class StageFrame(val lesson: ActiveLessonUi, val mode: LearningMode, val page: Int)

/** 이 값이 달라질 때만 내용을 바꿔 그린다. 차량 값이 바뀐 것만으로는 전환하지 않는다. */
private fun stageKey(frame: StageFrame): Any {
    val stage = frame.lesson.state.stage
    val page = if (stage == LessonStage.EXPLAIN && frame.mode == LearningMode.EXPLORE) frame.page else 0
    return Triple(stage, frame.mode, page)
}

/**
 * 실험 화면. 한 화면에서 예측 → 조작 대기 → 관찰 → 설명으로 단계가 바뀐다.
 * 이해 확인과 완료는 모형 없이 한 가지에만 집중하도록 따로 그린다.
 * 모드를 바꾸면 문장만 달라지고 단계는 그대로다.
 */
@Composable
fun LessonScreen(lesson: ActiveLessonUi, mode: LearningMode, controller: LabController) {
    val state = lesson.state
    // 탐험 모드에서 몇 번째 설명 문장을 보고 있는지. 모드를 바꿨다 돌아와도 유지한다.
    var explainPage by remember(state.lessonId, state.run) { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LessonTopBar(
            title = lesson.content.title,
            stage = state.stage,
            mode = mode,
            onSelectMode = controller::selectMode,
            onExit = controller::exitLesson,
        )
        AnimatedContent(
            targetState = StageFrame(lesson, mode, explainPage),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            transitionSpec = { quickFade() },
            contentKey = { viewOf(it.lesson.state) to it.lesson.state.run },
            label = "lessonView",
        ) { frame ->
            val shown = frame.lesson
            val text = shown.content.textFor(frame.mode)
            when (viewOf(shown.state)) {
                LessonView.HOLD ->
                    ResumePrompt(onResume = controller::resumeAfterHold, onExit = controller::exitLesson)

                LessonView.CHECK ->
                    CheckBody(content = shown.content, text = text, state = shown.state, onAnswer = controller::answer)

                LessonView.COMPLETE -> CompleteBody(
                    content = shown.content,
                    text = text,
                    state = shown.state,
                    onRestart = controller::restartLesson,
                    onExit = controller::exitLesson,
                )

                LessonView.PANES -> StagePanes(
                    frame = frame,
                    controller = controller,
                    onPage = { explainPage = it },
                )
            }
        }
    }
}

@Composable
private fun LessonTopBar(
    title: String,
    stage: LessonStage,
    mode: LearningMode,
    onSelectMode: (LearningMode) -> Unit,
    onExit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 960.dp) {
                // 넓은 창에서는 한 줄에 놓아 아래 내용이 쓸 높이를 남긴다.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BackButton(text = stringResource(R.string.action_go_home), onClick = onExit)
                    LessonTitle(title = title, modifier = Modifier.weight(1f))
                    StageStepper(stage = stage)
                    Spacer(modifier = Modifier.width(8.dp))
                    ModeToggle(mode = mode, onSelect = onSelectMode)
                }
            } else {
                val compact = maxWidth < 600.dp
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BackButton(text = stringResource(R.string.action_go_home), onClick = onExit)
                        Spacer(modifier = Modifier.weight(1f))
                        ModeToggle(mode = mode, onSelect = onSelectMode)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LessonTitle(title = title, modifier = Modifier.weight(1f))
                        StageStepper(stage = stage, compact = compact)
                    }
                }
            }
        }
        HairLine()
    }
}

@Composable
private fun LessonTitle(title: String, modifier: Modifier = Modifier) {
    LabText(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** 제한이 풀린 뒤에는 자동으로 재생하지 않고, 이어 할지 먼저 묻는다. */
@Composable
private fun ResumePrompt(onResume: () -> Unit, onExit: () -> Unit) {
    FocusColumn {
        LabText(
            text = stringResource(R.string.resume_prompt_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        LabText(text = stringResource(R.string.resume_prompt_body), style = MaterialTheme.typography.bodyLarge)
        ButtonArea {
            PrimaryButton(text = stringResource(R.string.action_resume), onClick = onResume)
            SecondaryButton(text = stringResource(R.string.action_go_home), onClick = onExit)
        }
    }
}

// ---- 준비, 예측, 조작 대기, 관찰, 설명 ----

@Composable
private fun StagePanes(frame: StageFrame, controller: LabController, onPage: (Int) -> Unit) {
    LessonPanes(
        scrollKey = stageKey(frame),
        top = { StageSwitch(frame) { StageTop(it) } },
        bottom = { StageSwitch(frame) { StageBottom(frame = it, onPredict = controller::predict) } },
        actions = { StageSwitch(frame) { StagePinned(frame = it, controller = controller, onPage = onPage) } },
        side = { fillHeight ->
            ModelPanel(lesson = frame.lesson, fillHeight = fillHeight, onModelAction = controller::modelAction)
        },
    )
}

/** 단계가 바뀔 때만 짧게 겹쳐 바꾼다. 원리 모형은 이 밖에 있어 깜빡임이 끊기지 않는다. */
@Composable
private fun StageSwitch(frame: StageFrame, content: @Composable (StageFrame) -> Unit) {
    AnimatedContent(
        targetState = frame,
        transitionSpec = { quickFade() },
        contentKey = { stageKey(it) },
        label = "stage",
    ) { current ->
        content(current)
    }
}

/** 왼쪽 위: 지금 읽을 것. */
@Composable
private fun StageTop(frame: StageFrame) {
    val content = frame.lesson.content
    val state = frame.lesson.state
    val text = content.textFor(frame.mode)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (state.stage) {
            LessonStage.READY -> {
                PathPill(content = content, path = state.path)
                LabText(
                    text = content.question,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                LabText(text = text.intro, style = MaterialTheme.typography.bodyLarge)
            }

            LessonStage.PREDICT -> StageHeadline(text = text.prediction)

            LessonStage.WAITING -> {
                StageHeadline(text = instructionOf(text, state.path))
                WaitingNote(text = text, state = state)
            }

            // 조작은 끝났다. 안내는 마친 일로 흐리게 남기고, 결과는 아래쪽에 보여 준다.
            LessonStage.OBSERVED -> DoneInstruction(text = instructionOf(text, state.path))

            LessonStage.EXPLAIN -> ExplainText(text = text, mode = frame.mode, page = frame.page)

            LessonStage.CHECK, LessonStage.COMPLETE -> Unit
        }
    }
}

/** 왼쪽 아래: 지금 누르거나 확인할 것. 방향지시등을 켜면 뜨는 시스템 카메라 창에 가려지지 않는 자리다. */
@Composable
private fun StageBottom(frame: StageFrame, onPredict: (String) -> Unit) {
    val lesson = frame.lesson
    val content = lesson.content
    val state = lesson.state
    val text = content.textFor(frame.mode)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (state.stage) {
            LessonStage.READY -> GuardianCard(tips = content.guardian.tips)

            LessonStage.PREDICT -> {
                val options = content.prediction.options
                if (options.size >= 2) {
                    ChoicePair(
                        first = predictionChoice(content, options[0]),
                        second = predictionChoice(content, options[1]),
                        onChoose = onPredict,
                    )
                }
            }

            LessonStage.WAITING -> WaitingStatus(content = content, text = text, state = state)

            LessonStage.OBSERVED -> ObservedResult(content = content, text = text, state = state)

            LessonStage.EXPLAIN -> {
                val blocks = text.explanation
                if (frame.mode == LearningMode.EXPLORE && blocks.size > 1) {
                    PageDots(count = blocks.size, current = frame.page.coerceIn(0, blocks.lastIndex))
                }
            }

            LessonStage.CHECK, LessonStage.COMPLETE -> Unit
        }
    }
}

/**
 * 왼쪽 맨 아래에 고정하는 것: 차량 상태 한 줄과 단계의 버튼.
 * 글이 길어 스크롤되더라도 늘 같은 자리에서 보고 누를 수 있게 한다.
 */
@Composable
private fun StagePinned(frame: StageFrame, controller: LabController, onPage: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        VehicleFooter(lesson = frame.lesson)
        StageActions(frame = frame, controller = controller, onPage = onPage)
    }
}

@Composable
private fun StageActions(frame: StageFrame, controller: LabController, onPage: (Int) -> Unit) {
    val state = frame.lesson.state
    val text = frame.lesson.content.textFor(frame.mode)
    when (state.stage) {
        LessonStage.READY -> ActionBar {
            PrimaryButton(text = stringResource(R.string.action_begin), onClick = controller::begin)
        }

        LessonStage.WAITING -> WaitingActions(
            state = state,
            onRecheck = controller::refreshVehicle,
            onSwitchToVirtual = controller::switchToVirtual,
        )

        LessonStage.OBSERVED -> ActionBar {
            PrimaryButton(
                text = stringResource(R.string.action_see_why),
                onClick = controller::acknowledgeObservation,
            )
        }

        LessonStage.EXPLAIN -> {
            val blocks = text.explanation
            if (frame.mode == LearningMode.EXPLORE && blocks.isNotEmpty()) {
                val index = frame.page.coerceIn(0, blocks.lastIndex)
                ActionBar {
                    // ‘다음’을 늘 같은 자리에 두려고 ‘이전’을 그 뒤에 놓는다.
                    if (index < blocks.lastIndex) {
                        PrimaryButton(text = stringResource(R.string.action_next), onClick = { onPage(index + 1) })
                    } else {
                        PrimaryButton(
                            text = stringResource(R.string.action_go_to_check),
                            onClick = controller::finishExplanation,
                        )
                    }
                    if (index > 0) {
                        SecondaryButton(text = stringResource(R.string.action_prev), onClick = { onPage(index - 1) })
                    }
                }
            } else {
                ActionBar {
                    PrimaryButton(
                        text = stringResource(R.string.action_go_to_check),
                        onClick = controller::finishExplanation,
                    )
                }
            }
        }

        LessonStage.PREDICT, LessonStage.CHECK, LessonStage.COMPLETE -> Unit
    }
}

@Composable
private fun ActionBar(content: @Composable () -> Unit) {
    ButtonArea(modifier = Modifier.padding(top = 14.dp), content = content)
}

@Composable
private fun StageHeadline(text: String, modifier: Modifier = Modifier) {
    LabText(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.semantics { heading() },
    )
}

private fun instructionOf(text: ModeContent, path: LearningPath): String =
    if (path == LearningPath.VIRTUAL) text.virtualInstruction else text.instruction

/** 어느 경로의 실험인지. 차량 관찰은 초록 점, 가상 실험은 파란 표지로 구분한다. */
@Composable
private fun PathPill(content: LessonContent, path: LearningPath) {
    if (path == LearningPath.VIRTUAL) {
        Pill(
            text = content.virtualLabel,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else {
        Pill(
            text = stringResource(R.string.path_vehicle),
            containerColor = LabTone.VEHICLE.container(MaterialTheme.colorScheme.background),
            contentColor = LabTone.VEHICLE.accent,
            dotColor = LabTone.VEHICLE.accent,
        )
    }
}

@Composable
private fun GuardianCard(tips: List<String>) {
    LabCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                LabText(
                    text = stringResource(R.string.guardian_heading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (tip in tips) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    LabText(text = tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 방향지시등 실험의 선택지에는 모형과 같은 모양의 화살표를 붙인다.
 * 글자를 다 읽지 못해도 방향으로 고를 수 있다.
 */
private fun predictionChoice(content: LessonContent, option: PredictionOption): ChoiceUi {
    val arrow = if (content.model != MODEL_TURN_SIGNAL) {
        ChoiceArrow.NONE
    } else {
        when (option.value) {
            TurnSignalCodes.LEFT_ACTIVE -> ChoiceArrow.LEFT
            TurnSignalCodes.RIGHT_ACTIVE -> ChoiceArrow.RIGHT
            else -> ChoiceArrow.NONE
        }
    }
    return ChoiceUi(id = option.id, label = option.label, arrow = arrow)
}

private fun predictionLabel(content: LessonContent, state: LessonState): String? {
    val prediction = state.prediction ?: return null
    return content.prediction.options.firstOrNull { it.id == prediction.optionId }?.label
}

/** 관찰을 기다리는 동안에도 아이가 고른 예측을 잊지 않게 보여 준다. */
@Composable
private fun PredictionPill(content: LessonContent, state: LessonState) {
    val label = predictionLabel(content, state) ?: return
    Pill(
        text = stringResource(R.string.prediction_value, label),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 조작 안내 바로 아래에 덧붙이는 말. */
@Composable
private fun WaitingNote(text: ModeContent, state: LessonState) {
    val waiting = state.waiting ?: return
    if (state.path == LearningPath.VIRTUAL) {
        if (waiting.hint == WaitingHint.OTHER_VALUE_OBSERVED) {
            Callout(text = text.virtualOtherValue, tone = LabTone.MODEL)
        }
    } else if (waiting.link == LinkState.OK && waiting.hint == WaitingHint.NONE) {
        LabText(
            text = text.waiting,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 조작을 기다리는 동안 보여 주는 차량 상태와 그에 따른 안내. */
@Composable
private fun WaitingStatus(content: LessonContent, text: ModeContent, state: LessonState) {
    if (state.path == LearningPath.VEHICLE) {
        val waiting = state.waiting
        if (waiting != null && waiting.link == LinkState.OK) {
            when (waiting.hint) {
                WaitingHint.ALREADY_AT_TARGET -> Callout(text = text.alreadyAtTarget, tone = LabTone.CAUTION)
                WaitingHint.OTHER_VALUE_OBSERVED -> Callout(text = text.otherValue, tone = LabTone.CAUTION)
                WaitingHint.NONE, WaitingHint.STATE_UNKNOWN -> Unit
            }
        }
        VehicleStatusCard(content = content, state = state)
    }
    PredictionPill(content = content, state = state)
}

/** 차량 상태를 확인하지 못할 때: 다시 확인하거나, 사유를 알린 뒤 원리 모형으로 배우게 한다. */
@Composable
private fun WaitingActions(state: LessonState, onRecheck: () -> Unit, onSwitchToVirtual: () -> Unit) {
    val waiting = state.waiting
    if (state.path != LearningPath.VEHICLE || waiting == null) return
    when {
        waiting.link == LinkState.AWAITING_RECHECK -> ActionBar {
            PrimaryButton(
                text = stringResource(R.string.action_recheck),
                onClick = onRecheck,
                icon = Icons.Rounded.Refresh,
            )
        }

        waiting.link == LinkState.LOST || waiting.hint == WaitingHint.STATE_UNKNOWN -> ActionBar {
            PrimaryButton(
                text = stringResource(R.string.action_recheck),
                onClick = onRecheck,
                icon = Icons.Rounded.Refresh,
            )
            SecondaryButton(text = stringResource(R.string.action_learn_with_model), onClick = onSwitchToVirtual)
        }

        else -> Unit
    }
}

/**
 * 차량이 알려 준 상태를 크게 보여 주는 칸. 원리 모형과 구분되도록 ‘차량 상태’라고 밝힌다.
 * 값을 확인했을 때만 초록으로 그리고, 확인하지 못한 동안에는 그 사정을 문장으로 적는다.
 */
@Composable
private fun VehicleStatusCard(content: LessonContent, state: LessonState) {
    val waiting = state.waiting
    val view = state.vehicle
    val valueLabel = if (view.validity == VehicleValidity.VALID) content.valueLabel(view.code) else null
    val base = MaterialTheme.colorScheme.background
    val shape = RoundedCornerShape(LabDimens.CardRadius)
    when {
        waiting?.link == LinkState.LOST -> StatusMessage(
            message = stringResource(R.string.waiting_link_lost),
            tone = LabTone.CAUTION,
            warning = true,
        )

        waiting?.link == LinkState.AWAITING_RECHECK -> StatusMessage(
            message = stringResource(R.string.waiting_link_restored),
            tone = LabTone.CAUTION,
            warning = false,
        )

        valueLabel != null -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(LabTone.VEHICLE.container(base))
                .border(1.dp, LabTone.VEHICLE.outline(base), shape)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(LabTone.VEHICLE.accent),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                LabText(
                    text = stringResource(R.string.vehicle_state_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = LabTone.VEHICLE.accent,
                )
                LabText(text = valueLabel, style = MaterialTheme.typography.titleLarge)
            }
            // 값이 바뀌기를 기다리는 중이라는 표시. 차량에서 알림이 오면 단계가 넘어간다.
            ListeningDots(color = LabTone.VEHICLE.accent)
        }

        view.validity == VehicleValidity.NONE -> StatusMessage(
            message = stringResource(R.string.vehicle_state_waiting),
            tone = LabTone.NEUTRAL,
            warning = false,
            listening = true,
        )

        else -> StatusMessage(
            message = stringResource(R.string.waiting_state_unknown),
            tone = LabTone.CAUTION,
            warning = true,
        )
    }
}

/** 차량 상태를 값으로 보여 줄 수 없을 때의 한 문장. */
@Composable
private fun StatusMessage(message: String, tone: LabTone, warning: Boolean, listening: Boolean = false) {
    val base = MaterialTheme.colorScheme.background
    val shape = RoundedCornerShape(LabDimens.CardRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.container(base))
            .border(1.dp, tone.outline(base), shape)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (warning) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = tone.accent,
                modifier = Modifier.size(26.dp),
            )
        }
        LabText(text = message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (listening) ListeningDots(color = LabColors.Muted)
    }
}

/** 마친 조작 안내. 관찰 결과를 보는 동안 무엇을 했는지 떠올리게 한다. */
@Composable
private fun DoneInstruction(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = LabColors.Muted,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp),
        )
        LabText(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = LabColors.Muted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 관찰 결과. 차량에서 확인한 것은 초록, 모형에서 확인한 것은 파랑으로 구분한다. */
@Composable
private fun ObservedResult(content: LessonContent, text: ModeContent, state: LessonState) {
    val observed = state.observed
    val isVehicle = state.path == LearningPath.VEHICLE
    val tone = if (isVehicle) LabTone.VEHICLE else LabTone.MODEL
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tone.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = tone.onAccent,
                modifier = Modifier.size(30.dp),
            )
        }
        StageHeadline(
            text = if (isVehicle) text.observation else text.virtualObservation,
            modifier = Modifier.weight(1f),
        )
    }
    if (observed != null) {
        ResultTiles(
            prediction = predictionLabel(content, state),
            observedTitle = if (isVehicle) stringResource(R.string.vehicle_state_label) else content.modelLabel,
            observedValue = content.valueLabel(observed.code) ?: observed.code,
            tone = tone,
        )
        val matched = observed.predictionMatched
        if (matched != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (matched) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = LabColors.SignalAmber,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(26.dp),
                    )
                }
                LabText(
                    text = if (matched) text.predictionMatched else text.predictionMissed,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 아이의 예측과 확인한 값을 나란히 놓아 견주어 보게 한다. */
@Composable
private fun ResultTiles(prediction: String?, observedTitle: String, observedValue: String, tone: LabTone) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (prediction != null) {
            ResultTile(
                title = stringResource(R.string.prediction_label),
                value = prediction,
                tone = LabTone.NEUTRAL,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        ResultTile(
            title = observedTitle,
            value = observedValue,
            tone = tone,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun ResultTile(title: String, value: String, tone: LabTone, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val shape = RoundedCornerShape(LabDimens.ControlRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (tone == LabTone.NEUTRAL) MaterialTheme.colorScheme.surface else tone.container(base))
            .border(1.dp, tone.outline(base), shape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LabText(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (tone == LabTone.NEUTRAL) LabColors.Muted else tone.accent,
        )
        LabText(text = value, style = MaterialTheme.typography.titleLarge)
    }
}

/** 설명. 탐험 모드는 한 번에 한 문장만, 이해 모드는 소제목과 함께 모두 보여 준다. */
@Composable
private fun ExplainText(text: ModeContent, mode: LearningMode, page: Int) {
    val blocks = text.explanation
    if (blocks.isEmpty()) return
    if (mode == LearningMode.EXPLORE) {
        StageHeadline(text = blocks[page.coerceIn(0, blocks.lastIndex)].text)
    } else {
        for (block in blocks) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (block.heading.isNotEmpty()) {
                    LabText(
                        text = block.heading,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                LabText(text = block.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * 차량 경로에서 왼쪽 아래 끝에 붙는 한 줄. 차량에서 확인한 사실은 원리 모형과 떨어뜨려 따로 표시한다.
 * 관찰을 마친 뒤에도 장치가 켜져 있으면 끄도록 알린다. 켜 둔 동안에는 시스템 카메라 창이 화면을 가린다.
 */
@Composable
private fun VehicleFooter(lesson: ActiveLessonUi) {
    val state = lesson.state
    if (state.path != LearningPath.VEHICLE || lesson.restored) return
    // 이 두 단계는 차량 상태를 직접 크게 보여 준다.
    if (state.stage == LessonStage.WAITING || state.stage == LessonStage.OBSERVED) return
    val stillOn = state.stage == LessonStage.EXPLAIN &&
        state.vehicle.validity == VehicleValidity.VALID &&
        state.vehicle.code != lesson.content.restingValue
    Box(modifier = Modifier.padding(top = 14.dp)) {
        if (stillOn) {
            Callout(text = stringResource(R.string.explain_turn_off_hint), tone = LabTone.CAUTION)
        } else {
            VehicleStateLine(content = lesson.content, state = state)
        }
    }
}

/** 차량이 알려 준 상태를 한 줄로. ‘차량 상태’라고 밝힌다. */
@Composable
private fun VehicleStateLine(content: LessonContent, state: LessonState) {
    val view = state.vehicle
    val label = content.valueLabel(view.code)
    when {
        view.validity == VehicleValidity.VALID && label != null -> Pill(
            text = stringResource(R.string.vehicle_state_value, label),
            containerColor = LabTone.VEHICLE.container(MaterialTheme.colorScheme.background),
            contentColor = LabTone.VEHICLE.accent,
            dotColor = LabTone.VEHICLE.accent,
        )

        view.validity == VehicleValidity.NONE -> Pill(
            text = stringResource(R.string.vehicle_state_waiting),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Pill(
            text = stringResource(R.string.vehicle_state_unknown),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- 원리 모형 ----

/**
 * 원리 모형 칸. 차량 상태가 아니라는 표시를 항상 붙인다.
 * 가상 실험의 조작 대기 단계와, 어느 경로든 설명 단계에서 모형을 직접 움직일 수 있다.
 */
@Composable
private fun ModelPanel(lesson: ActiveLessonUi, fillHeight: Boolean, onModelAction: (String) -> Unit) {
    val content = lesson.content
    val state = lesson.state
    val isVirtual = state.path == LearningPath.VIRTUAL
    val interactive = (isVirtual && state.stage == LessonStage.WAITING) || state.stage == LessonStage.EXPLAIN

    LabCard(modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    text = if (isVirtual) content.virtualLabel else content.modelLabel,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                LabText(
                    text = if (isVirtual) content.virtualNote else content.modelNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (content.model == MODEL_TURN_SIGNAL) {
                TurnSignalModel(
                    activeCode = state.modelCode,
                    interactive = interactive,
                    onAction = onModelAction,
                    modifier = if (fillHeight) Modifier.weight(1f) else Modifier,
                    fillHeight = fillHeight,
                    hint = if (state.stage == LessonStage.EXPLAIN) {
                        stringResource(R.string.explain_model_hint)
                    } else {
                        null
                    },
                )
            } else {
                LabText(text = stringResource(R.string.model_unavailable), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---- 이해 확인과 완료 ----

/** 이해 확인. 방금 확인한 원리를 다른 상황에 적용하는 질문 하나와 선택지 두 개만 보여 준다. */
@Composable
private fun CheckBody(content: LessonContent, text: ModeContent, state: LessonState, onAnswer: (String) -> Unit) {
    val ids = content.check.optionIds
    // 정답이 늘 같은 자리에 오지 않도록 다시 할 때마다 순서를 뒤집는다.
    val ordered = if (state.run % 2 == 0) ids.reversed() else ids
    val wrong = state.check.lastWrongOptionId
    FocusColumn(spacing = 24.dp) {
        LabText(
            text = text.check.question,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        if (ordered.size >= 2) {
            ChoicePair(
                first = ChoiceUi(
                    id = ordered[0],
                    label = text.check.options[ordered[0]].orEmpty(),
                    tried = wrong == ordered[0],
                ),
                second = ChoiceUi(
                    id = ordered[1],
                    label = text.check.options[ordered[1]].orEmpty(),
                    tried = wrong == ordered[1],
                ),
                onChoose = onAnswer,
            )
        }
        if (wrong != null) {
            // 틀렸다고만 하지 않고, 다시 생각할 실마리를 준다.
            Callout(
                text = text.check.hint,
                tone = LabTone.CAUTION,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun CompleteBody(
    content: LessonContent,
    text: ModeContent,
    state: LessonState,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val isVehicle = state.path == LearningPath.VEHICLE
    val tone = if (isVehicle) LabTone.VEHICLE else LabTone.MODEL
    val deviceStillOn = isVehicle &&
        state.vehicle.validity == VehicleValidity.VALID &&
        state.vehicle.code != content.restingValue
    FocusColumn {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CompletionStamp(tone = tone, modifier = Modifier.size(92.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabText(
                    text = stringResource(R.string.complete_title),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { heading() },
                )
                // 차량 관찰과 가상 실험 중 어느 쪽으로 기록했는지 밝힌다.
                if (isVehicle) {
                    Pill(
                        text = stringResource(R.string.complete_recorded_vehicle),
                        containerColor = LabTone.VEHICLE.container(MaterialTheme.colorScheme.background),
                        contentColor = LabTone.VEHICLE.accent,
                        dotColor = LabTone.VEHICLE.accent,
                    )
                } else {
                    Pill(
                        text = stringResource(R.string.complete_recorded_virtual),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
        }
        LabText(text = text.check.explanation, style = MaterialTheme.typography.bodyLarge)
        LabCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LabText(
                    text = stringResource(R.string.complete_takeaway_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabText(text = text.completion, style = MaterialTheme.typography.titleLarge)
            }
        }
        if (deviceStillOn) {
            Callout(
                text = stringResource(R.string.complete_turn_off_reminder),
                tone = LabTone.CAUTION,
                icon = Icons.Rounded.Warning,
            )
        }
        ButtonArea {
            PrimaryButton(text = stringResource(R.string.action_go_home), onClick = onExit)
            SecondaryButton(text = stringResource(R.string.action_restart), onClick = onRestart)
        }
    }
}

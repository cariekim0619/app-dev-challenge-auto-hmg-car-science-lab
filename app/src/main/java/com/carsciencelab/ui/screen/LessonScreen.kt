package com.carsciencelab.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.content.LessonContent
import com.carsciencelab.content.ModeContent
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
import com.carsciencelab.ui.model.TurnSignalModel

private const val MODEL_TURN_SIGNAL = "turn_signal_pattern"

/**
 * 실험 화면. 한 화면에서 예측 → 조작 대기 → 관찰 → 설명으로 단계가 바뀐다.
 * 이해 확인과 완료는 모형 없이 한 가지에만 집중하도록 따로 그린다.
 * 모드를 바꾸면 문장만 달라지고 단계는 그대로다.
 */
@Composable
fun LessonScreen(lesson: ActiveLessonUi, mode: LearningMode, controller: LabController) {
    val content = lesson.content
    val state = lesson.state
    val text = content.textFor(mode)

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        LessonTopBar(
            title = content.title,
            stage = state.stage,
            mode = mode,
            onSelectMode = controller::selectMode,
            onExit = controller::exitLesson,
        )
        when {
            state.hold != SafetyHold.NONE ->
                ResumePrompt(onResume = controller::resumeAfterHold, onExit = controller::exitLesson)

            state.stage == LessonStage.CHECK ->
                CheckBody(content = content, text = text, state = state, onAnswer = controller::answer)

            state.stage == LessonStage.COMPLETE -> CompleteBody(
                content = content,
                text = text,
                state = state,
                onRestart = controller::restartLesson,
                onExit = controller::exitLesson,
            )

            else -> TwoPane(
                primary = { StageBody(lesson = lesson, text = text, mode = mode, controller = controller) },
                secondary = { ModelPanel(lesson = lesson, onModelAction = controller::modelAction) },
            )
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuietButton(text = stringResource(R.string.action_go_home), onClick = onExit)
            Spacer(modifier = Modifier.weight(1f))
            ModeToggle(mode = mode, onSelect = onSelectMode)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StageDashes(stage = stage)
        }
    }
}

/** 제한이 풀린 뒤에는 자동으로 재생하지 않고, 이어 할지 먼저 묻는다. */
@Composable
private fun ResumePrompt(onResume: () -> Unit, onExit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(text = stringResource(R.string.resume_prompt_title), style = MaterialTheme.typography.headlineMedium)
        Text(text = stringResource(R.string.resume_prompt_body), style = MaterialTheme.typography.bodyLarge)
        ButtonArea {
            PrimaryButton(text = stringResource(R.string.action_resume), onClick = onResume)
            SecondaryButton(text = stringResource(R.string.action_go_home), onClick = onExit)
        }
    }
}

// ---- 준비, 예측, 조작 대기, 관찰, 설명 ----

@Composable
private fun StageBody(lesson: ActiveLessonUi, text: ModeContent, mode: LearningMode, controller: LabController) {
    val content = lesson.content
    val state = lesson.state
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        when (state.stage) {
            LessonStage.READY ->
                ReadyBody(content = content, text = text, path = state.path, onBegin = controller::begin)

            LessonStage.PREDICT -> PredictBody(content = content, text = text, onPredict = controller::predict)

            LessonStage.WAITING -> WaitingBody(
                text = text,
                state = state,
                onRecheck = controller::refreshVehicle,
                onSwitchToVirtual = controller::switchToVirtual,
            )

            LessonStage.OBSERVED ->
                ObservedBody(text = text, state = state, onContinue = controller::acknowledgeObservation)

            LessonStage.EXPLAIN ->
                ExplainBody(text = text, mode = mode, run = state.run, onFinish = controller::finishExplanation)

            LessonStage.CHECK, LessonStage.COMPLETE -> Unit
        }
        // 차량에서 확인한 사실은 원리 모형과 떨어뜨려 따로 표시한다.
        if (state.path == LearningPath.VEHICLE && !lesson.restored) {
            VehicleStateLine(content = content, state = state)
        }
    }
}

@Composable
private fun ReadyBody(content: LessonContent, text: ModeContent, path: LearningPath, onBegin: () -> Unit) {
    Text(text = content.question, style = MaterialTheme.typography.displaySmall)
    Text(text = text.intro, style = MaterialTheme.typography.bodyLarge)
    if (path == LearningPath.VIRTUAL) {
        Pill(
            text = content.virtualLabel,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.guardian_heading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (tip in content.guardian.tips) {
            Text(text = tip, style = MaterialTheme.typography.bodyMedium)
        }
    }
    PrimaryButton(text = stringResource(R.string.action_begin), onClick = onBegin)
}

@Composable
private fun PredictBody(content: LessonContent, text: ModeContent, onPredict: (String) -> Unit) {
    val options = content.prediction.options
    Text(text = text.prediction, style = MaterialTheme.typography.headlineMedium)
    if (options.size >= 2) {
        ChoicePair(
            first = options[0].label,
            second = options[1].label,
            onFirst = { onPredict(options[0].id) },
            onSecond = { onPredict(options[1].id) },
        )
    }
}

@Composable
private fun WaitingBody(
    text: ModeContent,
    state: LessonState,
    onRecheck: () -> Unit,
    onSwitchToVirtual: () -> Unit,
) {
    val waiting = state.waiting
    if (state.path == LearningPath.VIRTUAL) {
        Text(text = text.virtualInstruction, style = MaterialTheme.typography.headlineMedium)
        if (waiting != null && waiting.hint == WaitingHint.OTHER_VALUE_OBSERVED) {
            Text(text = text.virtualOtherValue, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Text(text = text.instruction, style = MaterialTheme.typography.headlineMedium)
        when {
            waiting == null -> Unit

            waiting.link == LinkState.LOST -> {
                Text(text = stringResource(R.string.waiting_link_lost), style = MaterialTheme.typography.bodyLarge)
                RecoveryButtons(onRecheck = onRecheck, onSwitchToVirtual = onSwitchToVirtual)
            }

            waiting.link == LinkState.AWAITING_RECHECK -> {
                Text(text = stringResource(R.string.waiting_link_restored), style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(text = stringResource(R.string.action_recheck), onClick = onRecheck)
            }

            waiting.hint == WaitingHint.ALREADY_AT_TARGET ->
                Text(text = text.alreadyAtTarget, style = MaterialTheme.typography.bodyLarge)

            waiting.hint == WaitingHint.OTHER_VALUE_OBSERVED ->
                Text(text = text.otherValue, style = MaterialTheme.typography.bodyLarge)

            waiting.hint == WaitingHint.STATE_UNKNOWN -> {
                Text(text = stringResource(R.string.waiting_state_unknown), style = MaterialTheme.typography.bodyLarge)
                RecoveryButtons(onRecheck = onRecheck, onSwitchToVirtual = onSwitchToVirtual)
            }

            else -> Text(
                text = text.waiting,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 차량 상태를 확인하지 못할 때: 다시 확인하거나, 사유를 알린 뒤 원리 모형으로 배우게 한다. */
@Composable
private fun RecoveryButtons(onRecheck: () -> Unit, onSwitchToVirtual: () -> Unit) {
    ButtonArea {
        PrimaryButton(text = stringResource(R.string.action_recheck), onClick = onRecheck)
        SecondaryButton(text = stringResource(R.string.action_learn_with_model), onClick = onSwitchToVirtual)
    }
}

@Composable
private fun ObservedBody(text: ModeContent, state: LessonState, onContinue: () -> Unit) {
    val observed = state.observed
    val isVehicle = state.path == LearningPath.VEHICLE
    Text(
        text = if (isVehicle) text.observation else text.virtualObservation,
        style = MaterialTheme.typography.headlineMedium,
    )
    val matched = observed?.predictionMatched
    if (matched != null) {
        Text(
            text = if (matched) text.predictionMatched else text.predictionMissed,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    PrimaryButton(text = stringResource(R.string.action_see_why), onClick = onContinue)
}

@Composable
private fun ExplainBody(text: ModeContent, mode: LearningMode, run: Int, onFinish: () -> Unit) {
    val blocks = text.explanation
    // 어린이 모드에서 몇 번째 문장을 보고 있는지. 모드를 바꿨다 돌아와도 유지한다.
    var page by remember(run) { mutableStateOf(0) }

    if (mode == LearningMode.EXPLORE) {
        val index = page.coerceIn(0, blocks.lastIndex)
        Text(text = blocks[index].text, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.explain_page, index + 1, blocks.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (index < blocks.lastIndex) {
            PrimaryButton(text = stringResource(R.string.action_next), onClick = { page = index + 1 })
        } else {
            PrimaryButton(text = stringResource(R.string.action_go_to_check), onClick = onFinish)
        }
    } else {
        for (block in blocks) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (block.heading.isNotEmpty()) {
                    Text(
                        text = block.heading,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(text = block.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        PrimaryButton(text = stringResource(R.string.action_go_to_check), onClick = onFinish)
    }
    Text(
        text = stringResource(R.string.explain_model_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 차량이 알려 준 상태. 원리 모형과 구분되도록 ‘차량 상태’라고 밝힌다. */
@Composable
private fun VehicleStateLine(content: LessonContent, state: LessonState) {
    val view = state.vehicle
    val label = content.valueLabel(view.code)
    when {
        view.validity == VehicleValidity.VALID && label != null -> Pill(
            text = stringResource(R.string.vehicle_state_value, label),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dotColor = MaterialTheme.colorScheme.secondary,
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
private fun ModelPanel(lesson: ActiveLessonUi, onModelAction: (String) -> Unit) {
    val content = lesson.content
    val state = lesson.state
    val isVirtual = state.path == LearningPath.VIRTUAL
    val interactive = (isVirtual && state.stage == LessonStage.WAITING) || state.stage == LessonStage.EXPLAIN

    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    text = if (isVirtual) content.virtualLabel else content.modelLabel,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
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
                )
            } else {
                Text(text = stringResource(R.string.model_unavailable), style = MaterialTheme.typography.bodyMedium)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = text.check.question, style = MaterialTheme.typography.headlineMedium)
        if (ordered.size >= 2) {
            ChoicePair(
                first = text.check.options[ordered[0]].orEmpty(),
                second = text.check.options[ordered[1]].orEmpty(),
                onFirst = { onAnswer(ordered[0]) },
                onSecond = { onAnswer(ordered[1]) },
            )
        }
        if (state.check.lastWrongOptionId != null) {
            Text(
                text = text.check.hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
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
    val deviceStillOn = isVehicle &&
        state.vehicle.validity == VehicleValidity.VALID &&
        state.vehicle.code != content.restingValue
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = stringResource(R.string.complete_title), style = MaterialTheme.typography.displaySmall)
        Text(text = text.check.explanation, style = MaterialTheme.typography.bodyLarge)
        Text(text = text.completion, style = MaterialTheme.typography.bodyLarge)
        // 차량 관찰과 가상 실험 중 어느 쪽으로 기록했는지 밝힌다.
        if (isVehicle) {
            Pill(
                text = stringResource(R.string.complete_recorded_vehicle),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dotColor = MaterialTheme.colorScheme.secondary,
            )
        } else {
            Pill(
                text = stringResource(R.string.complete_recorded_virtual),
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        if (deviceStillOn) {
            Text(
                text = stringResource(R.string.complete_turn_off_reminder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ButtonArea {
            PrimaryButton(text = stringResource(R.string.action_go_home), onClick = onExit)
            SecondaryButton(text = stringResource(R.string.action_restart), onClick = onRestart)
        }
    }
}

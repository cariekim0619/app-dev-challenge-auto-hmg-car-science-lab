package com.carsciencelab.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carsciencelab.R
import com.carsciencelab.platform.PlatformDevPanel
import com.carsciencelab.policy.BlockReason
import com.carsciencelab.policy.LearningPermission
import com.carsciencelab.ui.LabController
import com.carsciencelab.ui.LabScreen
import com.carsciencelab.ui.LabUiState
import com.carsciencelab.ui.LabViewModel
import com.carsciencelab.ui.model.ParkBadge
import com.carsciencelab.ui.theme.LabDimens
import com.carsciencelab.ui.theme.LabText
import kotlinx.coroutines.delay

/**
 * 앱 화면의 뿌리. 앱 창 안에서만 그리며 차량의 주행 정보 영역을 차지하지 않는다.
 * 학습이 허용되지 않으면 실험 화면과 원리 모형, 깜빡임을 모두 내리고 안내만 보여 준다.
 */
@Composable
fun LabApp(viewModel: LabViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controller = viewModel.controller

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            LabContent(
                state = state,
                controller = controller,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            // 개발용 빌드에서만 내용이 있다. 앱 화면을 덮지 않도록 아래에 따로 자리를 준다.
            PlatformDevPanel(
                onSupportChanged = controller::refreshAvailability,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    BackHandler(enabled = state.screen != LabScreen.HOME) { controller.showHome() }
}

/** 지금 보여 줄 화면. 우선순위는 준비 중 → 멈춤 → 콘텐츠 오류 → 실험 → 기록 → 홈이다. */
private enum class Destination { LOADING, BLOCKED, CONTENT_ERROR, LESSON, RECORDS, HOME }

private fun destinationOf(state: LabUiState): Destination = when {
    state.loading -> Destination.LOADING
    state.gate is LearningPermission.Blocked -> Destination.BLOCKED
    state.contentError != null -> Destination.CONTENT_ERROR
    state.screen == LabScreen.LESSON && state.lesson != null -> Destination.LESSON
    state.screen == LabScreen.RECORDS -> Destination.RECORDS
    else -> Destination.HOME
}

@Composable
private fun LabContent(state: LabUiState, controller: LabController, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val horizontal = if (maxWidth < 600.dp) 16.dp else LabDimens.PageHorizontal
        AnimatedContent(
            targetState = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontal, vertical = LabDimens.PageVertical),
            transitionSpec = {
                if (destinationOf(targetState) == Destination.BLOCKED) {
                    // 학습을 잠글 때는 실험 화면과 깜빡임을 곧바로 내린다.
                    ContentTransform(
                        targetContentEnter = EnterTransition.None,
                        initialContentExit = ExitTransition.None,
                        sizeTransform = null,
                    )
                } else {
                    quickFade()
                }
            },
            contentKey = { destinationOf(it) },
            label = "screen",
        ) { shown ->
            // 화면이 바뀌는 동안 사라지는 쪽은 자기 시점의 상태(shown)를 그대로 쓴다.
            val lesson = shown.lesson
            val gate = shown.gate
            when (destinationOf(shown)) {
                Destination.LOADING -> MessageScreen(title = stringResource(R.string.loading_title), body = null)

                Destination.BLOCKED -> BlockedScreen(
                    reason = (gate as? LearningPermission.Blocked)?.reason ?: BlockReason.NOT_FOREGROUND,
                    hasLessonInProgress = lesson != null,
                )

                Destination.CONTENT_ERROR -> MessageScreen(
                    title = stringResource(R.string.content_error_title),
                    body = stringResource(R.string.content_error_body, shown.contentError.orEmpty()),
                )

                Destination.LESSON -> lesson?.let {
                    LessonScreen(lesson = it, mode = shown.mode, controller = controller)
                }

                Destination.RECORDS -> RecordsScreen(state = shown, controller = controller)

                Destination.HOME -> HomeScreen(state = shown, controller = controller)
            }
        }
    }
}

/** 상태를 읽지 못했다고 알리기 전에 기다려 주는 시간. */
private const val UNKNOWN_GRACE_MS = 700L

/** 주차·UX 조건을 통과하지 못했을 때의 화면. 무엇이 문제이고 어떻게 하면 되는지만 알린다. */
@Composable
private fun BlockedScreen(reason: BlockReason, hasLessonInProgress: Boolean) {
    val message = stringResource(
        when (reason) {
            BlockReason.NOT_FOREGROUND -> R.string.blocked_reason_not_foreground
            BlockReason.UX_RESTRICTED -> R.string.blocked_reason_ux_restricted
            BlockReason.NOT_PARKED -> R.string.blocked_reason_not_parked
            BlockReason.PARKING_UNKNOWN -> R.string.blocked_reason_parking_unknown
            BlockReason.UX_UNKNOWN -> R.string.blocked_reason_ux_unknown
        },
    )
    val guide = stringResource(
        if (hasLessonInProgress) R.string.blocked_guide_resume else R.string.blocked_guide,
    )

    // 주차와 제한 상태를 아직 읽는 중일 수 있다. 앱을 열자마자 ‘확인할 수 없어요’가 번쩍였다 사라지지
    // 않도록, 읽지 못한 상태가 잠깐 이어진 뒤에야 그렇게 알린다. 그동안에도 학습은 잠겨 있다.
    val unknown = reason == BlockReason.PARKING_UNKNOWN || reason == BlockReason.UX_UNKNOWN
    var settled by remember(unknown) { mutableStateOf(!unknown) }
    LaunchedEffect(unknown) {
        if (unknown) {
            delay(UNKNOWN_GRACE_MS)
            settled = true
        }
    }
    if (!settled) {
        MessageScreen(title = stringResource(R.string.blocked_checking_title), body = null)
        return
    }

    // 시스템의 주행 중 제한 화면처럼 가운데에 짧게 알린다. 움직이는 것은 두지 않는다.
    FocusColumn(maxWidth = 640.dp, spacing = 12.dp, horizontalAlignment = Alignment.CenterHorizontally) {
        ParkBadge(modifier = Modifier.padding(bottom = 8.dp))
        LabText(
            text = stringResource(R.string.blocked_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        LabText(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        LabText(
            text = guide,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageScreen(title: String, body: String?) {
    FocusColumn(maxWidth = 640.dp, spacing = 12.dp, horizontalAlignment = Alignment.CenterHorizontally) {
        LabText(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            LabText(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

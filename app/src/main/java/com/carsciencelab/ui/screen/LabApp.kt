package com.carsciencelab.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

/**
 * 앱 화면의 뿌리. 앱 창 안에서만 그리며 차량의 주행 정보 영역을 차지하지 않는다.
 * 학습이 허용되지 않으면 실험 화면과 원리 모형, 깜빡임을 모두 내리고 안내만 보여 준다.
 */
@Composable
fun LabApp(viewModel: LabViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controller = viewModel.controller

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            LabContent(state = state, controller = controller)
            PlatformDevPanel(
                onSupportChanged = controller::refreshAvailability,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
    BackHandler(enabled = state.screen != LabScreen.HOME) { controller.showHome() }
}

@Composable
private fun LabContent(state: LabUiState, controller: LabController) {
    val gate = state.gate
    val lesson = state.lesson
    val contentError = state.contentError
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        when {
            state.loading -> MessageScreen(title = stringResource(R.string.loading_title), body = null)

            gate is LearningPermission.Blocked ->
                BlockedScreen(reason = gate.reason, hasLessonInProgress = lesson != null)

            contentError != null -> MessageScreen(
                title = stringResource(R.string.content_error_title),
                body = stringResource(R.string.content_error_body, contentError),
            )

            state.screen == LabScreen.LESSON && lesson != null ->
                LessonScreen(lesson = lesson, mode = state.mode, controller = controller)

            state.screen == LabScreen.RECORDS -> RecordsScreen(state = state, controller = controller)

            else -> HomeScreen(state = state, controller = controller)
        }
    }
}

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
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text = stringResource(R.string.blocked_title), style = MaterialTheme.typography.displaySmall)
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = guide,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageScreen(title: String, body: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text = title, style = MaterialTheme.typography.displaySmall)
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

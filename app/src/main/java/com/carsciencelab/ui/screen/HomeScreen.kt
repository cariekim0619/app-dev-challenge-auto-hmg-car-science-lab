package com.carsciencelab.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.content.PlannedLesson
import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.ui.LabController
import com.carsciencelab.ui.LabUiState
import com.carsciencelab.ui.LessonCardUi
import com.carsciencelab.ui.ResumeUi
import com.carsciencelab.ui.VehicleAvailability

/** 실험실 홈. 실험을 고르고, 이 차에서 관찰할 수 있는지와 그렇지 않은 사유를 구분해 보여 준다. */
@Composable
fun HomeScreen(state: LabUiState, controller: LabController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        HomeHeader(
            mode = state.mode,
            onSelectMode = controller::selectMode,
            onShowRecords = controller::showRecords,
        )
        val resume = state.resume
        if (resume != null) {
            ResumeBanner(resume = resume, onResume = controller::resumeSavedLesson)
        }
        for (card in state.cards) {
            LessonCard(
                card = card,
                onObserve = { controller.startLesson(card.lessonId, LearningPath.VEHICLE) },
                onVirtual = { controller.startLesson(card.lessonId, LearningPath.VIRTUAL) },
                onRecheck = controller::refreshAvailability,
            )
        }
        if (state.planned.isNotEmpty()) {
            PlannedLessons(planned = state.planned)
        }
    }
}

@Composable
private fun HomeHeader(mode: LearningMode, onSelectMode: (LearningMode) -> Unit, onShowRecords: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ModeToggle(mode = mode, onSelect = onSelectMode)
            Spacer(modifier = Modifier.weight(1f))
            QuietButton(text = stringResource(R.string.action_show_records), onClick = onShowRecords)
        }
    }
}

/** 설명이나 이해 확인 단계에서 나간 실험이 있을 때 이어 하도록 권한다. */
@Composable
internal fun ResumeBanner(resume: ResumeUi, onResume: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.resume_banner, resume.title),
                style = MaterialTheme.typography.bodyLarge,
            )
            PrimaryButton(text = stringResource(R.string.action_resume), onClick = onResume)
        }
    }
}

@Composable
private fun LessonCard(
    card: LessonCardUi,
    onObserve: () -> Unit,
    onVirtual: () -> Unit,
    onRecheck: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = card.question, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = card.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AvailabilityLine(availability = card.availability)
            if (card.canObserveInVehicle) {
                PrimaryButton(text = stringResource(R.string.action_observe_in_vehicle), onClick = onObserve)
            } else if (card.offersVirtual) {
                ButtonArea {
                    PrimaryButton(text = stringResource(R.string.action_learn_with_model), onClick = onVirtual)
                    QuietButton(text = stringResource(R.string.action_recheck), onClick = onRecheck)
                }
            }
            CompletionCounts(vehicle = card.vehicleCompletions, virtual = card.virtualCompletions)
        }
    }
}

/** 차량 관찰이 가능한지, 아니라면 왜 아닌지를 문장으로 알린다. */
@Composable
private fun AvailabilityLine(availability: VehicleAvailability) {
    if (availability == VehicleAvailability.AVAILABLE) {
        Pill(
            text = stringResource(R.string.availability_available),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dotColor = MaterialTheme.colorScheme.secondary,
        )
    } else {
        val message = stringResource(
            when (availability) {
                VehicleAvailability.NOT_CHECKED -> R.string.availability_not_checked
                VehicleAvailability.CHECKING -> R.string.availability_checking
                VehicleAvailability.UNSUPPORTED -> R.string.availability_unsupported
                VehicleAvailability.PERMISSION_DENIED -> R.string.availability_permission_denied
                VehicleAvailability.CONNECTION_FAILED -> R.string.availability_connection_failed
                VehicleAvailability.CHECK_FAILED -> R.string.availability_check_failed
                VehicleAvailability.AVAILABLE -> R.string.availability_available
            },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 차량 관찰 완료와 가상 실험 완료를 따로 보여 준다. */
@Composable
internal fun CompletionCounts(vehicle: Int, virtual: Int) {
    if (vehicle > 0 || virtual > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                text = stringResource(R.string.count_vehicle, vehicle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = stringResource(R.string.count_virtual, virtual),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun PlannedLessons(planned: List<PlannedLesson>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = stringResource(R.string.home_planned_title), style = MaterialTheme.typography.titleLarge)
        for (lesson in planned) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lesson.question,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    text = stringResource(R.string.planned_badge),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 넓으면 가로로, 좁으면 세로로 놓는 영역. 글과 버튼을 함께 놓을 때 쓴다. */
@Composable
internal fun ButtonArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
            }
        }
    }
}

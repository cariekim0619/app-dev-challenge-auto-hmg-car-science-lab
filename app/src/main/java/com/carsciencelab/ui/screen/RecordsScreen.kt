package com.carsciencelab.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.platform.PlatformModule
import com.carsciencelab.ui.LabController
import com.carsciencelab.ui.LabUiState
import com.carsciencelab.ui.LessonCardUi
import com.carsciencelab.vehicle.ConnectionState
import java.text.DateFormat
import java.util.Date

/**
 * 실험 기록. 완료한 실험과 이어 하기, 다시 해보기를 보여 준다.
 * 기록은 이 기기에만 있고 로그인 없이 쓴다. 아래쪽에는 검증 기록에 남길 실행 환경을 적는다.
 */
@Composable
fun RecordsScreen(state: LabUiState, controller: LabController) {
    val context = LocalContext.current
    val environment = remember { PlatformModule.environmentInfo(context) }
    var confirmingClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.records_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.weight(1f),
            )
            QuietButton(text = stringResource(R.string.action_go_home), onClick = controller::showHome)
        }

        val resume = state.resume
        if (resume != null) {
            ResumeBanner(resume = resume, onResume = controller::resumeSavedLesson)
        }

        for (card in state.cards) {
            RecordCard(
                card = card,
                onRestart = {
                    val path = if (card.canObserveInVehicle) LearningPath.VEHICLE else LearningPath.VIRTUAL
                    controller.startLesson(card.lessonId, path)
                },
            )
        }

        if (confirmingClear) {
            Text(text = stringResource(R.string.records_clear_confirm), style = MaterialTheme.typography.bodyLarge)
            ButtonArea {
                PrimaryButton(
                    text = stringResource(R.string.action_clear_records_confirm),
                    onClick = {
                        controller.clearRecords()
                        confirmingClear = false
                    },
                )
                SecondaryButton(text = stringResource(R.string.action_cancel), onClick = { confirmingClear = false })
            }
        } else {
            QuietButton(text = stringResource(R.string.action_clear_records), onClick = { confirmingClear = true })
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.env_title), style = MaterialTheme.typography.titleLarge)
            for (entry in environment) {
                EnvironmentRow(label = stringResource(entry.label), value = entry.value)
            }
            EnvironmentRow(
                label = stringResource(R.string.env_connection),
                value = stringResource(
                    when (state.diagnostics.connection) {
                        ConnectionState.IDLE -> R.string.env_connection_idle
                        ConnectionState.CONNECTING -> R.string.env_connection_connecting
                        ConnectionState.CONNECTED -> R.string.env_connection_connected
                        ConnectionState.FAILED -> R.string.env_connection_failed
                    },
                ),
            )
            EnvironmentRow(
                label = stringResource(R.string.env_dropped),
                value = state.diagnostics.droppedObservations.toString(),
            )
        }
    }
}

@Composable
private fun RecordCard(card: LessonCardUi, onRestart: () -> Unit) {
    val completedAt = card.lastCompletedAtEpochMs
    val hasRecord = card.vehicleCompletions > 0 || card.virtualCompletions > 0
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = card.question, style = MaterialTheme.typography.titleLarge)
            if (hasRecord) {
                CompletionCounts(vehicle = card.vehicleCompletions, virtual = card.virtualCompletions)
            } else {
                Text(
                    text = stringResource(R.string.records_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (completedAt != null) {
                val formatted = remember(completedAt) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(completedAt))
                }
                Text(
                    text = stringResource(R.string.records_last_completed, formatted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.canObserveInVehicle || card.offersVirtual) {
                SecondaryButton(
                    text = stringResource(if (hasRecord) R.string.action_restart else R.string.action_begin),
                    onClick = onRestart,
                )
            }
        }
    }
}

@Composable
private fun EnvironmentRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
    }
}

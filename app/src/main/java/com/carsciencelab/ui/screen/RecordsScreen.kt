package com.carsciencelab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carsciencelab.R
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.platform.PlatformModule
import com.carsciencelab.ui.LabController
import com.carsciencelab.ui.LabUiState
import com.carsciencelab.ui.LessonCardUi
import com.carsciencelab.ui.theme.LabDimens
import com.carsciencelab.ui.theme.LabText
import com.carsciencelab.ui.theme.LabTone
import com.carsciencelab.ui.theme.accent
import com.carsciencelab.ui.theme.container
import com.carsciencelab.ui.theme.outline
import com.carsciencelab.vehicle.ConnectionState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * 실험 기록. 완료한 실험과 이어 하기, 다시 해보기를 보여 준다.
 * 기록은 이 기기에만 있고 로그인 없이 쓴다. 아래쪽에는 검증 기록에 남길 실행 환경을 적는다.
 */
@Composable
fun RecordsScreen(state: LabUiState, controller: LabController) {
    val context = LocalContext.current
    val environment = remember { PlatformModule.environmentInfo(context) }
    var confirmingClear by remember { mutableStateOf(false) }
    val hasAnythingToClear = state.resume != null ||
        state.cards.any { it.vehicleCompletions > 0 || it.virtualCompletions > 0 }

    PageColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton(text = stringResource(R.string.action_go_home), onClick = controller::showHome)
            LabText(
                text = stringResource(R.string.records_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (hasAnythingToClear) {
                QuietButton(
                    text = stringResource(R.string.action_clear_records),
                    onClick = { confirmingClear = true },
                    icon = Icons.Rounded.Delete,
                )
            }
        }
        HairLine()

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

        LabText(
            text = stringResource(R.string.env_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        LabCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

    if (confirmingClear) {
        // 되돌릴 수 없는 동작이라 한 번 더 묻는다.
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = {
                LabText(
                    text = stringResource(R.string.action_clear_records),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                LabText(
                    text = stringResource(R.string.records_clear_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.action_clear_records_confirm),
                    onClick = {
                        controller.clearRecords()
                        confirmingClear = false
                    },
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { confirmingClear = false },
                )
            },
            shape = RoundedCornerShape(LabDimens.CardRadius),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
        )
    }
}

@Composable
private fun RecordCard(card: LessonCardUi, onRestart: () -> Unit) {
    val completedAt = card.lastCompletedAtEpochMs
    val hasRecord = card.vehicleCompletions > 0 || card.virtualCompletions > 0
    LabCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LabText(text = card.question, style = MaterialTheme.typography.titleLarge)
            // 차량 관찰과 가상 실험을 따로 센다. 가상 실험을 차량 관찰 실적으로 섞지 않는다.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CountTile(
                    label = stringResource(R.string.record_vehicle_label),
                    count = card.vehicleCompletions,
                    tone = LabTone.VEHICLE,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 260.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                )
                CountTile(
                    label = stringResource(R.string.record_virtual_label),
                    count = card.virtualCompletions,
                    tone = LabTone.MODEL,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 260.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                )
            }
            LabText(
                text = if (completedAt != null) {
                    // 화면의 글이 한국어이므로 기기 언어와 상관없이 한국어 표기로 적는다.
                    val formatted = remember(completedAt) {
                        DateFormat
                            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.KOREA)
                            .format(Date(completedAt))
                    }
                    stringResource(R.string.records_last_completed, formatted)
                } else {
                    stringResource(R.string.records_empty)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (card.canObserveInVehicle || card.offersVirtual) {
                SecondaryButton(
                    text = stringResource(if (hasRecord) R.string.action_restart else R.string.action_begin),
                    onClick = onRestart,
                )
            }
        }
    }
}

/** 완료 횟수 한 칸. 한 번도 하지 않은 쪽은 색을 빼서 조용히 둔다. */
@Composable
private fun CountTile(label: String, count: Int, tone: LabTone, modifier: Modifier = Modifier) {
    val active = count > 0
    val shown = if (active) tone else LabTone.NEUTRAL
    val base = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(LabDimens.ControlRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(shown.container(base))
            .border(1.dp, shown.outline(base), shape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LabText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) tone.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabText(
            text = stringResource(R.string.record_times, count),
            style = MaterialTheme.typography.titleLarge,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnvironmentRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LabText(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        LabText(text = value, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
    }
}

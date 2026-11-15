package com.carsciencelab.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.carsciencelab.ui.model.LessonArt
import com.carsciencelab.ui.model.PlannedLessonArt
import com.carsciencelab.ui.theme.LabColors
import com.carsciencelab.ui.theme.LabDimens
import com.carsciencelab.ui.theme.LabText
import com.carsciencelab.ui.theme.LabTone
import com.carsciencelab.ui.theme.accent
import com.carsciencelab.ui.theme.container
import com.carsciencelab.ui.theme.outline

/** 실험실 홈. 실험을 고르고, 이 차에서 관찰할 수 있는지와 그렇지 않은 사유를 구분해 보여 준다. */
@Composable
fun HomeScreen(state: LabUiState, controller: LabController) {
    PageColumn {
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 760.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeTitle(modifier = Modifier.weight(1f))
                    ModeToggle(mode = mode, onSelect = onSelectMode)
                    RecordsButton(onClick = onShowRecords)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeTitle()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModeToggle(mode = mode, onSelect = onSelectMode)
                        Spacer(modifier = Modifier.weight(1f))
                        RecordsButton(onClick = onShowRecords)
                    }
                }
            }
        }
        HairLine()
    }
}

@Composable
private fun HomeTitle(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LabText(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        LabText(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordsButton(onClick: () -> Unit) {
    SecondaryButton(
        text = stringResource(R.string.action_show_records),
        onClick = onClick,
        icon = Icons.AutoMirrored.Rounded.List,
    )
}

/** 설명이나 이해 확인 단계에서 나간 실험이 있을 때 이어 하도록 권한다. */
@Composable
internal fun ResumeBanner(resume: ResumeUi, onResume: () -> Unit) {
    val base = MaterialTheme.colorScheme.background
    LabCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LabTone.CAUTION.container(base),
        borderColor = LabTone.CAUTION.outline(base),
    ) {
        BoxWithConstraints {
            val message = stringResource(R.string.resume_banner, resume.title)
            if (maxWidth >= 640.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LabText(text = message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    PrimaryButton(text = stringResource(R.string.action_resume), onClick = onResume)
                }
            } else {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabText(text = message, style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton(text = stringResource(R.string.action_resume), onClick = onResume)
                }
            }
        }
    }
}

/** 지금 할 수 있는 실험. 질문을 가장 크게 적고, 무엇에 관한 실험인지 그림으로 함께 보여 준다. */
@Composable
private fun LessonCard(
    card: LessonCardUi,
    onObserve: () -> Unit,
    onVirtual: () -> Unit,
    onRecheck: () -> Unit,
) {
    LabCard(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            if (maxWidth >= 760.dp) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    LessonCardText(
                        card = card,
                        onObserve = onObserve,
                        onVirtual = onVirtual,
                        onRecheck = onRecheck,
                        modifier = Modifier.weight(1.1f),
                    )
                    LessonArtFrame(
                        model = card.model,
                        modifier = Modifier
                            .weight(1f)
                            .height(270.dp),
                    )
                }
            } else {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    LessonArtFrame(
                        model = card.model,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                    )
                    LessonCardText(card = card, onObserve = onObserve, onVirtual = onVirtual, onRecheck = onRecheck)
                }
            }
        }
    }
}

@Composable
private fun LessonCardText(
    card: LessonCardUi,
    onObserve: () -> Unit,
    onVirtual: () -> Unit,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabText(
                text = card.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LabText(
                text = card.question,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        AvailabilityLine(availability = card.availability)
        when {
            card.canObserveInVehicle ->
                PrimaryButton(text = stringResource(R.string.action_observe_in_vehicle), onClick = onObserve)

            card.offersVirtual -> ButtonArea {
                PrimaryButton(text = stringResource(R.string.action_learn_with_model), onClick = onVirtual)
                QuietButton(
                    text = stringResource(R.string.action_recheck),
                    onClick = onRecheck,
                    icon = Icons.Rounded.Refresh,
                )
            }

            // 확인이 끝나기 전에는 누를 수 없는 버튼을 같은 자리에 두어, 확인 뒤에 화면이 밀리지 않게 한다.
            else -> PrimaryButton(
                text = stringResource(R.string.action_observe_in_vehicle),
                onClick = {},
                enabled = false,
            )
        }
        CompletionCounts(vehicle = card.vehicleCompletions, virtual = card.virtualCompletions)
    }
}

/** 실험 그림을 놓는 자리. 원리 모형과 같은 그림이지만 움직이지 않는다. */
@Composable
private fun LessonArtFrame(model: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LabDimens.ControlRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        LessonArt(
            model = model,
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        )
    }
}

/** 차량 관찰이 가능한지, 아니라면 왜 아닌지를 문장으로 알린다. */
@Composable
private fun AvailabilityLine(availability: VehicleAvailability) {
    when (availability) {
        VehicleAvailability.AVAILABLE -> Pill(
            text = stringResource(R.string.availability_available),
            containerColor = LabTone.VEHICLE.container(),
            contentColor = LabTone.VEHICLE.accent,
            dotColor = LabTone.VEHICLE.accent,
        )

        VehicleAvailability.NOT_CHECKED, VehicleAvailability.CHECKING -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 차량의 답을 기다리는 표시는 실험 화면과 같은 점 세 개를 쓴다.
            ListeningDots(color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabText(
                text = stringResource(
                    if (availability == VehicleAvailability.CHECKING) {
                        R.string.availability_checking
                    } else {
                        R.string.availability_not_checked
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 관찰이 안 되는 사유를 구분해 적는다. 고장처럼 보이지 않게 차분한 색을 쓴다.
        else -> Callout(
            text = stringResource(
                when (availability) {
                    VehicleAvailability.UNSUPPORTED -> R.string.availability_unsupported
                    VehicleAvailability.PERMISSION_DENIED -> R.string.availability_permission_denied
                    VehicleAvailability.CONNECTION_FAILED -> R.string.availability_connection_failed
                    else -> R.string.availability_check_failed
                },
            ),
            tone = LabTone.NEUTRAL,
            base = MaterialTheme.colorScheme.surface,
        )
    }
}

/** 차량 관찰 완료와 가상 실험 완료를 따로 보여 준다. */
@Composable
internal fun CompletionCounts(vehicle: Int, virtual: Int) {
    if (vehicle > 0 || virtual > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CountLabel(text = stringResource(R.string.count_vehicle, vehicle), color = LabTone.VEHICLE.accent)
            CountLabel(text = stringResource(R.string.count_virtual, virtual), color = LabTone.MODEL.accent)
        }
    }
}

@Composable
private fun CountLabel(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        LabText(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** 아직 만들지 않은 실험. 어떤 질문이 이어질지만 알리고 누를 수 없게 둔다. */
@Composable
private fun PlannedLessons(planned: List<PlannedLesson>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabText(
            text = stringResource(R.string.home_planned_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 760.dp) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in planned.chunked(columns)) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (lesson in row) {
                            PlannedCard(
                                lesson = lesson,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        // 마지막 줄이 덜 찼을 때 카드가 한 줄 전체로 늘어나지 않게 빈칸을 둔다.
                        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedCard(lesson: PlannedLesson, modifier: Modifier = Modifier) {
    LabCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlannedLessonArt(lessonId = lesson.id, modifier = Modifier.size(64.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabText(
                    text = lesson.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = LabColors.Muted,
                )
                Pill(
                    text = stringResource(R.string.planned_badge),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

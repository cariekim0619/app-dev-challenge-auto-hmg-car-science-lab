package com.carsciencelab.ui

import com.carsciencelab.content.LessonContent
import com.carsciencelab.content.PlannedLesson
import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.learning.LessonStage
import com.carsciencelab.learning.LessonState
import com.carsciencelab.policy.BlockReason
import com.carsciencelab.policy.LearningPermission
import com.carsciencelab.vehicle.ConnectionState

enum class LabScreen { HOME, LESSON, RECORDS }

/** 차량에서 관찰할 수 있는지에 대한 확인 결과. 미지원과 실패 사유를 구분해 보여 준다. */
enum class VehicleAvailability {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    UNSUPPORTED,
    PERMISSION_DENIED,
    CONNECTION_FAILED,
    CHECK_FAILED,
}

data class LessonCardUi(
    val lessonId: String,
    val title: String,
    val question: String,
    val availability: VehicleAvailability,
    val vehicleCompletions: Int,
    val virtualCompletions: Int,
    val lastCompletedAtEpochMs: Long?,
) {
    val canObserveInVehicle: Boolean get() = availability == VehicleAvailability.AVAILABLE

    /** 차량 관찰이 안 되는 사유가 확인됐을 때만 가상 실험을 권한다. */
    val offersVirtual: Boolean
        get() = availability == VehicleAvailability.UNSUPPORTED ||
            availability == VehicleAvailability.PERMISSION_DENIED ||
            availability == VehicleAvailability.CONNECTION_FAILED ||
            availability == VehicleAvailability.CHECK_FAILED
}

data class ResumeUi(
    val lessonId: String,
    val title: String,
    val path: LearningPath,
    val stage: LessonStage,
)

data class ActiveLessonUi(
    val content: LessonContent,
    val state: LessonState,
    /** 저장한 지점에서 이어 한 실험인가. 이 경우 차량 상태를 다시 구독하지 않는다. */
    val restored: Boolean,
)

data class LabDiagnostics(
    val connection: ConnectionState = ConnectionState.IDLE,
    /** 세션이 다르거나 오래돼서 버린 관찰의 수. */
    val droppedObservations: Int = 0,
)

data class LabUiState(
    val loading: Boolean = true,
    val screen: LabScreen = LabScreen.HOME,
    val mode: LearningMode = LearningMode.EXPLORE,
    val gate: LearningPermission = LearningPermission.Blocked(BlockReason.NOT_FOREGROUND),
    val cards: List<LessonCardUi> = emptyList(),
    val planned: List<PlannedLesson> = emptyList(),
    val resume: ResumeUi? = null,
    val lesson: ActiveLessonUi? = null,
    val contentError: String? = null,
    val diagnostics: LabDiagnostics = LabDiagnostics(),
)

package com.carsciencelab.learning

/** 실험 한 번의 진행 단계. */
enum class LessonStage { READY, PREDICT, WAITING, OBSERVED, EXPLAIN, CHECK, COMPLETE }

/** 실제 차량 관찰과 가상 모형 학습을 섞지 않기 위한 구분. */
enum class LearningPath { VEHICLE, VIRTUAL }

/** 어린이 탐험 모드와 보호자·성인 이해 모드. 진행 단계와는 별개다. */
enum class LearningMode { EXPLORE, UNDERSTAND }

enum class SafetyHold {
    NONE,

    /** 주차·UX 조건을 통과하지 못해 멈춘 상태. */
    BLOCKED,

    /** 조건은 다시 통과했지만 사용자가 이어 하기를 누르기 전이다. */
    AWAITING_RESUME,
}

enum class WaitingHint {
    NONE,

    /** 과제 시작 때 이미 목표 상태였다. 먼저 원래 상태로 돌려야 한다. */
    ALREADY_AT_TARGET,

    /** 목표가 아닌 다른 값이 확인됐다. */
    OTHER_VALUE_OBSERVED,

    /** 값을 확인할 수 없다. */
    STATE_UNKNOWN,
}

enum class LinkState {
    OK,

    /** 차량 서비스 연결이 끊겨 관찰 대기를 멈췄다. */
    LOST,

    /** 연결은 돌아왔지만 사용자가 다시 확인을 누르기 전이다. */
    AWAITING_RECHECK,
}

enum class VehicleValidity { NONE, VALID, UNKNOWN, UNAVAILABLE }

/** 화면에 보여 줄 최근 차량 상태. 영구 저장하지 않는다. */
data class VehicleView(val validity: VehicleValidity, val code: String?) {
    companion object {
        val NONE = VehicleView(VehicleValidity.NONE, null)
    }
}

data class Prediction(val optionId: String, val code: String)

data class WaitingState(
    /** 과제를 시작한 시점의 수신 순서 표식. 이보다 앞선 관찰은 과제 진행에 쓰지 않는다. */
    val startMark: Long,
    /** 과제 시작 이후 목표가 아닌 상태를 확인했는가. 확인해야 목표 관찰을 변화로 인정한다. */
    val armed: Boolean,
    val hint: WaitingHint,
    val link: LinkState = LinkState.OK,
    /** [WaitingHint.OTHER_VALUE_OBSERVED]일 때 확인된 값. */
    val otherCode: String? = null,
)

data class ObservedResult(
    val code: String,
    /** 예측과 같았는가. 저장된 지점에서 이어 한 경우 알 수 없으면 null. */
    val predictionMatched: Boolean?,
    val path: LearningPath,
)

data class CheckState(val attempts: Int = 0, val lastWrongOptionId: String? = null)

data class LessonState(
    val lessonId: String,
    val path: LearningPath,
    val stage: LessonStage,
    val hold: SafetyHold = SafetyHold.NONE,
    val prediction: Prediction? = null,
    val waiting: WaitingState? = null,
    val observed: ObservedResult? = null,
    val check: CheckState = CheckState(),
    val vehicle: VehicleView = VehicleView.NONE,
    val linkUp: Boolean = true,
    /** 원리 모형이 보여 주는 값. 차량 상태가 아니다. */
    val modelCode: String,
    /** 다시 해보기를 누를 때마다 1씩 늘어난다. */
    val run: Int = 1,
)

/** 엔진이 콘텐츠에서 필요로 하는 최소 정보. */
data class LessonSpec(
    val lessonId: String,
    val targetCode: String,
    val restingCode: String,
    /** 예측 선택지 id → 그 선택지가 뜻하는 값. */
    val predictionOptions: Map<String, String>,
    val checkOptionIds: Set<String>,
    val correctCheckOptionId: String,
) {
    init {
        require(lessonId.isNotBlank()) { "lessonId가 비어 있습니다." }
        require(targetCode != restingCode) { "목표 값과 기본 값이 같습니다." }
        require(predictionOptions.isNotEmpty()) { "예측 선택지가 없습니다." }
        require(correctCheckOptionId in checkOptionIds) { "정답 선택지가 선택지 목록에 없습니다." }
    }
}

sealed interface LessonEvent {
    // 사용자 입력
    data object Begin : LessonEvent
    data class Predict(val optionId: String) : LessonEvent
    data object AcknowledgeObservation : LessonEvent
    data object FinishExplanation : LessonEvent
    data class Answer(val optionId: String) : LessonEvent
    data object Restart : LessonEvent

    // 차량 경로
    data class VehicleObserved(val observation: SequencedObservation) : LessonEvent
    data class LinkChanged(val connected: Boolean) : LessonEvent

    /** 과제는 그대로 두고 현재값만 다시 읽는다. */
    data object RefreshVehicleState : LessonEvent

    /** 연결 문제 뒤에 과제를 새 기준으로 다시 시작한다. */
    data object RecheckVehicle : LessonEvent

    // 가상 경로와 원리 모형
    data class ModelActed(val code: String) : LessonEvent

    // 학습 허용 조건
    data class PermissionChanged(val allowed: Boolean) : LessonEvent
    data object Resume : LessonEvent
}

sealed interface LessonEffect {
    /** 완료 기록. 한 번의 실험에서 정확히 한 번만 나온다. */
    data class RecordCompletion(val lessonId: String, val path: LearningPath) : LessonEffect

    /** 현재 차량 값을 한 번 읽어 달라는 요청. */
    data object RequestSnapshot : LessonEffect

    /** 구독을 새 세션으로 다시 시작하고 현재값을 읽어 달라는 요청. */
    data object RestartVehicleSession : LessonEffect
}

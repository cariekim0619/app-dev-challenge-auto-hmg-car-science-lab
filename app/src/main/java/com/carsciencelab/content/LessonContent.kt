package com.carsciencelab.content

import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LessonSpec
import kotlinx.serialization.Serializable

/**
 * 실험 한 편의 콘텐츠. `assets/lessons/` 아래 JSON 파일과 1:1로 대응한다.
 * 설명과 질문을 화면 코드에 직접 쓰지 않기 위한 구조다.
 */
@Serializable
data class LessonContent(
    val id: String,
    val contentVersion: Int,
    val title: String,
    val question: String,
    val learningGoal: String,
    val requiredSignals: List<String>,
    val model: String,
    /** 원리 모형 옆에 붙는 표시와 보충 설명. 모형이 차량 상태가 아님을 알린다. */
    val modelLabel: String,
    val modelNote: String,
    /** 가상 경로에서 모형 옆에 붙는 표시와 보충 설명. */
    val virtualLabel: String,
    val virtualNote: String,
    /** 정규화한 값 → 화면에 보여 줄 이름. */
    val values: Map<String, ValueInfo>,
    val restingValue: String,
    val targetTransition: TargetTransition,
    val prediction: PredictionContent,
    val check: CheckContent,
    val modes: ModeContents,
    val guardian: GuardianContent,
    val sourceRefs: List<SourceRef>,
) {
    fun textFor(mode: LearningMode): ModeContent = when (mode) {
        LearningMode.EXPLORE -> modes.explore
        LearningMode.UNDERSTAND -> modes.understand
    }

    fun valueLabel(code: String?): String? = code?.let { values[it]?.label }

    fun toSpec(): LessonSpec = LessonSpec(
        lessonId = id,
        targetCode = targetTransition.to,
        restingCode = restingValue,
        predictionOptions = prediction.options.associate { it.id to it.value },
        checkOptionIds = check.optionIds.toSet(),
        correctCheckOptionId = check.correctOptionId,
    )
}

@Serializable
data class ValueInfo(val label: String)

@Serializable
data class TargetTransition(
    /** 과제를 시작한 뒤의 변화만 인정한다는 뜻. 항상 true여야 한다. */
    val afterTaskStart: Boolean,
    val to: String,
)

@Serializable
data class PredictionContent(val options: List<PredictionOption>)

@Serializable
data class PredictionOption(val id: String, val value: String, val label: String)

@Serializable
data class CheckContent(val optionIds: List<String>, val correctOptionId: String)

@Serializable
data class ModeContents(val explore: ModeContent, val understand: ModeContent)

/** 한 모드에서 쓰는 문장 묶음. 두 모드는 같은 단계와 같은 선택지를 다른 깊이로 설명한다. */
@Serializable
data class ModeContent(
    val intro: String,
    val prediction: String,
    val instruction: String,
    val waiting: String,
    val alreadyAtTarget: String,
    val otherValue: String,
    val observation: String,
    val virtualInstruction: String,
    val virtualOtherValue: String,
    val virtualObservation: String,
    val predictionMatched: String,
    val predictionMissed: String,
    /** 설명. 어린이 모드는 한 번에 한 문장씩 보여 준다. */
    val explanation: List<ExplanationBlock>,
    val check: CheckText,
    val completion: String,
)

@Serializable
data class ExplanationBlock(
    /** 이해 모드에서 쓰는 소제목. 어린이 모드는 비워 둔다. */
    val heading: String = "",
    val text: String,
)

@Serializable
data class CheckText(
    val question: String,
    /** 선택지 id → 문장. */
    val options: Map<String, String>,
    val hint: String,
    val explanation: String,
)

@Serializable
data class GuardianContent(val tips: List<String>)

/** 설명의 근거. [status]가 verified가 아니면 출시 전에 검수해야 한다. */
@Serializable
data class SourceRef(
    val id: String,
    val title: String,
    val supports: String,
    val status: String,
    val url: String = "",
)

/** 아직 만들지 않은 실험. 홈 화면에 준비 중으로만 표시한다. */
@Serializable
data class PlannedLesson(val id: String, val title: String, val question: String)

data class LessonCatalog(val lessons: List<LessonContent>, val planned: List<PlannedLesson>) {
    fun find(lessonId: String): LessonContent? = lessons.firstOrNull { it.id == lessonId }

    companion object {
        val EMPTY = LessonCatalog(emptyList(), emptyList())
    }
}

/** 콘텐츠를 읽어 오는 쪽. 구현체는 앱에 포함한 파일을 읽는다. */
interface LessonRepository {
    suspend fun load(): LessonCatalog
}

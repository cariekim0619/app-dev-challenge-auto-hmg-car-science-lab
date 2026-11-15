package com.carsciencelab.content

import com.carsciencelab.vehicle.SignalKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class LessonContentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 콘텐츠 JSON을 읽고 검증한다.
 *
 * 알 수 없는 키를 허용하지 않는다. 오타가 조용히 무시되면 문장이 빠진 채로
 * 화면에 나갈 수 있기 때문이다.
 */
object LessonContentParser {

    private val json = Json { ignoreUnknownKeys = false }

    @Serializable
    private data class Index(val lessons: List<IndexEntry>)

    @Serializable
    private data class IndexEntry(
        val id: String,
        val status: String,
        val file: String = "",
        val title: String = "",
        val question: String = "",
    )

    fun parseLesson(text: String): LessonContent {
        val content = try {
            json.decodeFromString(LessonContent.serializer(), text)
        } catch (e: SerializationException) {
            throw LessonContentException("콘텐츠 형식이 올바르지 않습니다: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw LessonContentException("콘텐츠 형식이 올바르지 않습니다: ${e.message}", e)
        }
        validate(content)
        return content
    }

    /**
     * 색인 파일과 각 실험 파일을 읽어 목록을 만든다.
     * [readFile]은 색인에 적힌 파일 이름을 받아 내용을 돌려준다.
     */
    fun parseCatalog(indexText: String, readFile: (String) -> String): LessonCatalog {
        val index = try {
            json.decodeFromString(Index.serializer(), indexText)
        } catch (e: SerializationException) {
            throw LessonContentException("색인 형식이 올바르지 않습니다: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw LessonContentException("색인 형식이 올바르지 않습니다: ${e.message}", e)
        }
        val lessons = mutableListOf<LessonContent>()
        val planned = mutableListOf<PlannedLesson>()
        val seen = mutableSetOf<String>()
        for (entry in index.lessons) {
            check(seen.add(entry.id)) { "실험 id가 중복됩니다: ${entry.id}" }
            when (entry.status) {
                "available" -> {
                    check(entry.file.isNotBlank()) { "${entry.id}: 파일 이름이 없습니다." }
                    val lesson = parseLesson(readFile(entry.file))
                    check(lesson.id == entry.id) { "${entry.file}: id가 색인(${entry.id})과 다릅니다." }
                    lessons += lesson
                }

                "planned" -> {
                    check(entry.title.isNotBlank() && entry.question.isNotBlank()) {
                        "${entry.id}: 준비 중인 실험에는 제목과 질문이 필요합니다."
                    }
                    planned += PlannedLesson(entry.id, entry.title, entry.question)
                }

                else -> fail("${entry.id}: 알 수 없는 상태입니다: ${entry.status}")
            }
        }
        return LessonCatalog(lessons, planned)
    }

    private fun validate(content: LessonContent) {
        val where = content.id.ifBlank { "(id 없음)" }
        check(content.id.isNotBlank()) { "id가 비어 있습니다." }
        check(content.contentVersion > 0) { "$where: contentVersion은 1 이상이어야 합니다." }
        check(content.title.isNotBlank() && content.question.isNotBlank()) { "$where: 제목과 질문이 필요합니다." }
        val modelTexts = listOf(content.modelLabel, content.modelNote, content.virtualLabel, content.virtualNote)
        check(modelTexts.all { it.isNotBlank() }) { "$where: 원리 모형과 가상 실험의 표시 문장이 필요합니다." }

        check(content.requiredSignals.isNotEmpty()) { "$where: requiredSignals가 비어 있습니다." }
        for (signal in content.requiredSignals) {
            check(SignalKey.fromId(signal) != null) { "$where: 알 수 없는 신호입니다: $signal" }
        }

        val target = content.targetTransition.to
        check(content.targetTransition.afterTaskStart) {
            "$where: 과제 시작 이후의 변화만 인정해야 합니다(afterTaskStart=true)."
        }
        check(target in content.values) { "$where: 목표 값 $target 이 values에 없습니다." }
        check(content.restingValue in content.values) {
            "$where: 기본 값 ${content.restingValue} 이 values에 없습니다."
        }
        check(target != content.restingValue) { "$where: 목표 값과 기본 값이 같습니다." }
        for ((code, info) in content.values) {
            check(info.label.isNotBlank()) { "$where: 값 $code 의 이름이 비어 있습니다." }
        }

        // 어린이 화면은 큰 선택지 두 개를 보여 준다.
        val options = content.prediction.options
        check(options.size == 2) { "$where: 예측 선택지는 두 개여야 합니다." }
        check(options.map { it.id }.toSet().size == 2) { "$where: 예측 선택지 id가 중복됩니다." }
        for (option in options) {
            check(option.value in content.values) { "$where: 예측 선택지 값 ${option.value} 이 values에 없습니다." }
            check(option.label.isNotBlank()) { "$where: 예측 선택지 ${option.id} 의 문장이 비어 있습니다." }
        }
        check(options.any { it.value == target }) { "$where: 예측 선택지에 목표 값이 없습니다." }

        val checkIds = content.check.optionIds
        check(checkIds.size == 2 && checkIds.toSet().size == 2) { "$where: 이해 확인 선택지는 서로 다른 두 개여야 합니다." }
        check(content.check.correctOptionId in checkIds) { "$where: 이해 확인 정답이 선택지에 없습니다." }

        validateMode(where, "explore", content.modes.explore, checkIds)
        validateMode(where, "understand", content.modes.understand, checkIds)

        check(content.guardian.tips.isNotEmpty()) { "$where: 보호자 안내가 비어 있습니다." }

        check(content.sourceRefs.isNotEmpty()) { "$where: 출처가 비어 있습니다." }
        for (ref in content.sourceRefs) {
            check(ref.id.isNotBlank() && ref.title.isNotBlank() && ref.supports.isNotBlank()) {
                "$where: 출처에 id, title, supports가 필요합니다."
            }
            check(ref.status == "verified" || ref.status == "needs_review") {
                "$where: 출처 ${ref.id} 의 status는 verified 또는 needs_review여야 합니다."
            }
        }
    }

    private fun validateMode(where: String, name: String, mode: ModeContent, checkIds: List<String>) {
        val sentences = listOf(
            mode.intro, mode.prediction, mode.instruction, mode.waiting, mode.alreadyAtTarget,
            mode.otherValue, mode.observation, mode.virtualInstruction, mode.virtualOtherValue,
            mode.virtualObservation, mode.predictionMatched, mode.predictionMissed, mode.completion,
            mode.check.question, mode.check.hint, mode.check.explanation,
        )
        check(sentences.all { it.isNotBlank() }) { "$where/$name: 비어 있는 문장이 있습니다." }
        check(mode.explanation.isNotEmpty()) { "$where/$name: 설명이 비어 있습니다." }
        check(mode.explanation.all { it.text.isNotBlank() }) { "$where/$name: 비어 있는 설명이 있습니다." }
        check(mode.check.options.keys == checkIds.toSet()) {
            "$where/$name: 이해 확인 선택지 문장이 선택지 id와 맞지 않습니다."
        }
        check(mode.check.options.values.all { it.isNotBlank() }) { "$where/$name: 비어 있는 선택지 문장이 있습니다." }
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) fail(message())
    }

    private fun fail(message: String): Nothing = throw LessonContentException(message)
}

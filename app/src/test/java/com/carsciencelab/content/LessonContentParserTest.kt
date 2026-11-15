package com.carsciencelab.content

import com.carsciencelab.learning.LearningMode
import com.carsciencelab.testsupport.TestAssets
import com.carsciencelab.vehicle.TurnSignalCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LessonContentParserTest {

    private val turnSignal = TestAssets.read("turn_signal.json")
    private val index = TestAssets.read("index.json")

    private fun assertRejected(text: String, because: String) {
        try {
            LessonContentParser.parseLesson(text)
            fail("검증을 통과하면 안 된다: $because")
        } catch (expected: LessonContentException) {
            // 기대한 동작
        }
    }

    /** 원본에서 [old]를 [new]로 바꾼 콘텐츠. 바꿀 곳이 정확히 한 곳인지 확인한다. */
    private fun mutated(old: String, new: String): String {
        val count = turnSignal.split(old).size - 1
        assertEquals("치환 대상이 한 곳이어야 한다: $old", 1, count)
        return turnSignal.replace(old, new)
    }

    // ---- 앱에 포함한 콘텐츠 ----

    @Test
    fun `앱에 포함한 콘텐츠가 검증을 통과한다`() {
        val catalog = LessonContentParser.parseCatalog(index, TestAssets::read)

        assertEquals(listOf("turn_signal"), catalog.lessons.map { it.id })
        assertEquals(listOf("wiper", "recirculation"), catalog.planned.map { it.id })
        assertNotNull(catalog.find("turn_signal"))
    }

    @Test
    fun `방향지시등 콘텐츠에서 엔진이 쓸 정보를 만든다`() {
        val spec = LessonContentParser.parseLesson(turnSignal).toSpec()

        assertEquals("turn_signal", spec.lessonId)
        assertEquals(TurnSignalCodes.LEFT_ACTIVE, spec.targetCode)
        assertEquals(TurnSignalCodes.OFF, spec.restingCode)
        assertEquals(
            mapOf("left" to TurnSignalCodes.LEFT_ACTIVE, "right" to TurnSignalCodes.RIGHT_ACTIVE),
            spec.predictionOptions,
        )
        assertEquals(setOf("direction", "hurry"), spec.checkOptionIds)
        assertEquals("direction", spec.correctCheckOptionId)
    }

    @Test
    fun `두 모드는 같은 단계를 다른 문장으로 설명한다`() {
        val lesson = LessonContentParser.parseLesson(turnSignal)
        val explore = lesson.textFor(LearningMode.EXPLORE)
        val understand = lesson.textFor(LearningMode.UNDERSTAND)

        assertEquals(explore.check.options.keys, understand.check.options.keys)
        assertFalse(explore.prediction == understand.prediction)
        // 어린이 모드는 소제목 없이 한 문장씩, 이해 모드는 소제목과 함께 보여 준다.
        assertTrue(explore.explanation.all { it.heading.isEmpty() })
        assertTrue(understand.explanation.all { it.heading.isNotEmpty() })
        assertEquals("왼쪽 켜짐", lesson.valueLabel(TurnSignalCodes.LEFT_ACTIVE))
    }

    @Test
    fun `출처에 예시용 자리표시자가 남아 있지 않다`() {
        val lesson = LessonContentParser.parseLesson(turnSignal)

        assertTrue(lesson.sourceRefs.none { it.id == "hyundai_manual_for_supported_model" })
        assertTrue(lesson.sourceRefs.all { it.supports.isNotBlank() })
    }

    // ---- 잘못된 콘텐츠 ----

    @Test
    fun `알 수 없는 키가 있으면 거부한다`() {
        assertRejected(mutated("\"contentVersion\": 1,", "\"contentVersion\": 1, \"contnetVersion\": 2,"), "오타 키")
    }

    @Test
    fun `알 수 없는 신호를 요구하면 거부한다`() {
        assertRejected(mutated("[\"turn_signal_state\"]", "[\"wiper_state\"]"), "모르는 신호")
    }

    @Test
    fun `목표 값이 값 목록에 없으면 거부한다`() {
        assertRejected(mutated("\"to\": \"LEFT_ACTIVE\"", "\"to\": \"UP_ACTIVE\""), "없는 목표 값")
    }

    @Test
    fun `과제 시작 이전의 상태를 인정하는 콘텐츠는 거부한다`() {
        assertRejected(mutated("\"afterTaskStart\": true", "\"afterTaskStart\": false"), "시작 전 상태 인정")
    }

    @Test
    fun `목표 값과 기본 값이 같으면 거부한다`() {
        assertRejected(mutated("\"restingValue\": \"OFF\"", "\"restingValue\": \"LEFT_ACTIVE\""), "목표와 기본이 같음")
    }

    @Test
    fun `예측 선택지가 두 개가 아니면 거부한다`() {
        val third = "{ \"id\": \"right\", \"value\": \"RIGHT_ACTIVE\", \"label\": \"오른쪽\" }"
        assertRejected(
            mutated(third, "$third, { \"id\": \"none\", \"value\": \"OFF\", \"label\": \"안 켜져요\" }"),
            "선택지 세 개",
        )
    }

    @Test
    fun `예측 선택지에 목표 값이 없으면 거부한다`() {
        assertRejected(
            mutated("{ \"id\": \"left\", \"value\": \"LEFT_ACTIVE\"", "{ \"id\": \"left\", \"value\": \"OFF\""),
            "목표가 선택지에 없음",
        )
    }

    @Test
    fun `이해 확인 정답이 선택지에 없으면 거부한다`() {
        assertRejected(mutated("\"correctOptionId\": \"direction\"", "\"correctOptionId\": \"speed\""), "없는 정답")
    }

    @Test
    fun `모드의 선택지 문장이 선택지 id와 맞지 않으면 거부한다`() {
        assertRejected(mutated("\"hurry\": \"빨리 가고 싶어요\"", "\"fast\": \"빨리 가고 싶어요\""), "선택지 id 불일치")
    }

    @Test
    fun `비어 있는 문장이 있으면 거부한다`() {
        assertRejected(mutated("\"instruction\": \"왼쪽 방향지시등을 켜 주세요.\"", "\"instruction\": \" \""), "빈 문장")
    }

    @Test
    fun `출처 상태가 정해진 값이 아니면 거부한다`() {
        assertRejected(
            mutated(
                "\"supports\": \"1분간 90±30회 점멸하는 구조\",\n      \"status\": \"needs_review\"",
                "\"supports\": \"1분간 90±30회 점멸하는 구조\",\n      \"status\": \"maybe\"",
            ),
            "모르는 출처 상태",
        )
    }

    @Test
    fun `형식이 깨진 글은 거부한다`() {
        assertRejected("{ \"id\": \"turn_signal\"", "깨진 JSON")
        assertRejected("[]", "객체가 아님")
    }

    // ---- 색인 ----

    private fun assertCatalogRejected(indexText: String, because: String) {
        try {
            LessonContentParser.parseCatalog(indexText, TestAssets::read)
            fail("검증을 통과하면 안 된다: $because")
        } catch (expected: LessonContentException) {
            // 기대한 동작
        }
    }

    @Test
    fun `색인의 id가 중복되면 거부한다`() {
        assertCatalogRejected(index.replace("\"id\": \"wiper\"", "\"id\": \"turn_signal\""), "중복 id")
    }

    @Test
    fun `색인의 상태를 알 수 없으면 거부한다`() {
        assertCatalogRejected(index.replace("\"status\": \"planned\"", "\"status\": \"soon\""), "모르는 상태")
    }

    @Test
    fun `색인의 id와 파일의 id가 다르면 거부한다`() {
        assertCatalogRejected(index.replace("\"id\": \"turn_signal\"", "\"id\": \"blinker\""), "id 불일치")
    }

    @Test
    fun `준비 중인 실험에 제목이나 질문이 없으면 거부한다`() {
        assertCatalogRejected(index.replace("\"title\": \"공기는 어디에서 올까\",", ""), "제목 없음")
    }
}

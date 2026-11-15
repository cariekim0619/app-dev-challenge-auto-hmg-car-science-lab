package com.carsciencelab.data

import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.learning.LessonStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabRecordsTest {

    @Test
    fun `차량 관찰 완료와 가상 학습 완료를 따로 센다`() {
        val data = StoredLabData()
            .withCompletion("turn_signal", LearningPath.VEHICLE, atEpochMs = 10)
            .withCompletion("turn_signal", LearningPath.VIRTUAL, atEpochMs = 20)
            .withCompletion("turn_signal", LearningPath.VIRTUAL, atEpochMs = 30)

        val record = data.records.getValue("turn_signal")
        assertEquals(1, record.vehicleCompletions)
        assertEquals(2, record.virtualCompletions)
        assertEquals("VIRTUAL", record.lastPath)
        assertEquals(30L, record.lastCompletedAtEpochMs)
    }

    @Test
    fun `저장했다가 다시 읽어도 내용이 같다`() {
        val data = StoredLabData()
            .withMode(LearningMode.UNDERSTAND)
            .withCompletion("turn_signal", LearningPath.VEHICLE, atEpochMs = 10)
            .withResume(ResumePoint.of("turn_signal", LearningPath.VIRTUAL, LessonStage.CHECK, predictionMatched = false))

        val restored = StoredLabDataCodec.decode(StoredLabDataCodec.encode(data))

        assertEquals(data, restored)
        assertEquals(LearningMode.UNDERSTAND, restored.learningMode())
        assertEquals(LearningPath.VIRTUAL, restored.resume!!.learningPath())
        assertEquals(LessonStage.CHECK, restored.resume!!.lessonStage())
    }

    @Test
    fun `저장된 글이 없거나 손상됐으면 빈 기록으로 시작한다`() {
        assertEquals(StoredLabData(), StoredLabDataCodec.decode(null))
        assertEquals(StoredLabData(), StoredLabDataCodec.decode("  "))
        assertEquals(StoredLabData(), StoredLabDataCodec.decode("{ 깨진 글"))
        assertEquals(StoredLabData(), StoredLabDataCodec.decode("[1, 2, 3]"))
    }

    @Test
    fun `나중 버전이 추가한 항목이 있어도 읽는다`() {
        val text = """{"schema":2,"mode":"UNDERSTAND","records":{},"resume":null,"futureField":true}"""

        val data = StoredLabDataCodec.decode(text)

        assertEquals(LearningMode.UNDERSTAND, data.learningMode())
        assertEquals(2, data.schema)
    }

    @Test
    fun `모르는 모드 이름은 탐험 모드로 읽는다`() {
        assertEquals(LearningMode.EXPLORE, StoredLabData(mode = "EXPERT").learningMode())
    }

    @Test
    fun `기록을 지워도 모드 선택은 남는다`() {
        val data = StoredLabData()
            .withMode(LearningMode.UNDERSTAND)
            .withCompletion("turn_signal", LearningPath.VEHICLE, atEpochMs = 10)
            .withResume(ResumePoint.of("turn_signal", LearningPath.VEHICLE, LessonStage.EXPLAIN, true))

        val cleared = data.cleared()

        assertEquals(StoredLabData(mode = "UNDERSTAND"), cleared)
    }

    @Test
    fun `이어 하기 지점은 설명과 이해 확인 단계만 인정한다`() {
        assertEquals(LessonStage.EXPLAIN, ResumePoint("a", "VEHICLE", "EXPLAIN").lessonStage())
        assertEquals(LessonStage.CHECK, ResumePoint("a", "VEHICLE", "CHECK").lessonStage())
        assertNull(ResumePoint("a", "VEHICLE", "WAITING").lessonStage())
        assertNull(ResumePoint("a", "VEHICLE", "없는 단계").lessonStage())
        assertNull(ResumePoint("a", "하늘", "CHECK").learningPath())
    }
}

package com.carsciencelab.data

import com.carsciencelab.learning.LearningMode
import com.carsciencelab.learning.LearningPath
import com.carsciencelab.learning.LessonStage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 실험 하나의 완료 기록. 차량 관찰 완료와 가상 학습 완료를 따로 센다. */
@Serializable
data class LessonRecord(
    val vehicleCompletions: Int = 0,
    val virtualCompletions: Int = 0,
    val lastPath: String? = null,
    val lastCompletedAtEpochMs: Long? = null,
)

/** 이어 하기 지점. 관찰을 마친 뒤의 단계(설명·이해 확인)만 저장한다. */
@Serializable
data class ResumePoint(
    val lessonId: String,
    val path: String,
    val stage: String,
    val predictionMatched: Boolean? = null,
) {
    fun learningPath(): LearningPath? = LearningPath.entries.firstOrNull { it.name == path }

    fun lessonStage(): LessonStage? = LessonStage.entries
        .firstOrNull { it.name == stage }
        ?.takeIf { it == LessonStage.EXPLAIN || it == LessonStage.CHECK }

    companion object {
        fun of(lessonId: String, path: LearningPath, stage: LessonStage, predictionMatched: Boolean?) =
            ResumePoint(lessonId, path.name, stage.name, predictionMatched)
    }
}

/**
 * 기기에 저장하는 내용 전부. 로그인 없이 로컬에만 둔다.
 * 차량 상태는 저장하지 않는다. 나이나 이름 같은 개인정보도 받지 않는다.
 */
@Serializable
data class StoredLabData(
    val schema: Int = 1,
    val mode: String = LearningMode.EXPLORE.name,
    val records: Map<String, LessonRecord> = emptyMap(),
    val resume: ResumePoint? = null,
) {
    fun learningMode(): LearningMode =
        LearningMode.entries.firstOrNull { it.name == mode } ?: LearningMode.EXPLORE

    fun withMode(mode: LearningMode): StoredLabData = copy(mode = mode.name)

    fun withResume(point: ResumePoint?): StoredLabData = copy(resume = point)

    fun withCompletion(lessonId: String, path: LearningPath, atEpochMs: Long): StoredLabData {
        val previous = records[lessonId] ?: LessonRecord()
        val updated = when (path) {
            LearningPath.VEHICLE -> previous.copy(vehicleCompletions = previous.vehicleCompletions + 1)
            LearningPath.VIRTUAL -> previous.copy(virtualCompletions = previous.virtualCompletions + 1)
        }.copy(lastPath = path.name, lastCompletedAtEpochMs = atEpochMs)
        return copy(records = records + (lessonId to updated))
    }

    /** 기록과 이어 하기 지점을 지운다. 모드 선택은 남긴다. */
    fun cleared(): StoredLabData = StoredLabData(mode = mode)
}

object StoredLabDataCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(data: StoredLabData): String = json.encodeToString(StoredLabData.serializer(), data)

    /** 저장된 글을 읽는다. 없거나 손상됐으면 빈 기록으로 시작한다. */
    fun decode(text: String?): StoredLabData {
        if (text.isNullOrBlank()) return StoredLabData()
        return try {
            json.decodeFromString(StoredLabData.serializer(), text)
        } catch (_: SerializationException) {
            StoredLabData()
        } catch (_: IllegalArgumentException) {
            StoredLabData()
        }
    }
}

/** 기록을 보관하는 쪽. 구현체는 기기 저장소를 감싼다. */
interface LessonRecordStore {
    val data: Flow<StoredLabData>

    suspend fun update(transform: (StoredLabData) -> StoredLabData)
}

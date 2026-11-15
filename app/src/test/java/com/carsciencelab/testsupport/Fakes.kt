package com.carsciencelab.testsupport

import com.carsciencelab.content.LessonCatalog
import com.carsciencelab.content.LessonContentParser
import com.carsciencelab.content.LessonRepository
import com.carsciencelab.data.LessonRecordStore
import com.carsciencelab.data.StoredLabData
import com.carsciencelab.policy.ParkingState
import com.carsciencelab.policy.SafetyMonitor
import com.carsciencelab.policy.SafetySnapshot
import com.carsciencelab.policy.UxRestrictionState
import com.carsciencelab.vehicle.ConnectionState
import com.carsciencelab.vehicle.Observation
import com.carsciencelab.vehicle.ObservationKind
import com.carsciencelab.vehicle.ObservationSource
import com.carsciencelab.vehicle.ObservedValue
import com.carsciencelab.vehicle.SignalKey
import com.carsciencelab.vehicle.SupportResult
import com.carsciencelab.vehicle.TurnSignalCodes
import com.carsciencelab.vehicle.VehicleGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.io.File

/** 앱에 포함한 콘텐츠 파일을 테스트에서 읽는다. */
object TestAssets {
    private val candidates = listOf("src/main/assets/lessons", "app/src/main/assets/lessons")

    fun read(name: String): String {
        val file = candidates.map { File(it, name) }.firstOrNull { it.isFile }
            ?: error("콘텐츠 파일을 찾지 못했습니다: $name (작업 폴더: ${File(".").absolutePath})")
        return file.readText(Charsets.UTF_8)
    }

    fun catalog(): LessonCatalog = LessonContentParser.parseCatalog(read("index.json"), ::read)
}

val PARKED_AND_UNRESTRICTED = SafetySnapshot(ParkingState.PARKED, UxRestrictionState.UNRESTRICTED)

class FakeSafetyMonitor(initial: SafetySnapshot) : SafetyMonitor {
    override val snapshot = MutableStateFlow(initial)
    var started = false
    var stopped = false

    override fun start() {
        started = true
    }

    override fun stop() {
        stopped = true
    }
}

class FakeLessonRepository(private val load: () -> LessonCatalog) : LessonRepository {
    override suspend fun load(): LessonCatalog = load.invoke()
}

class InMemoryRecordStore(initial: StoredLabData = StoredLabData()) : LessonRecordStore {
    private val flow = MutableStateFlow(initial)
    var failUpdates = false
    val value: StoredLabData get() = flow.value

    override val data: Flow<StoredLabData> = flow

    override suspend fun update(transform: (StoredLabData) -> StoredLabData) {
        if (failUpdates) throw IllegalStateException("저장 실패")
        flow.value = transform(flow.value)
    }
}

/** 테스트가 차량 값과 지원 여부, 연결 상태를 직접 정하는 대역. */
class FakeVehicleGateway : VehicleGateway {
    override val connection = MutableStateFlow(ConnectionState.IDLE)

    var support: SupportResult = SupportResult.Supported
    var current: ObservedValue? = ObservedValue.Known(TurnSignalCodes.OFF)
    var capabilityCalls = 0
    var readCalls = 0
    var closed = false

    /** 지금 구독 중인 세션. */
    val subscriptions = mutableListOf<String>()

    /** true면 조회 응답을 보류한다. [releaseRead]로 내보낸다. */
    var holdReads = false
    private val pendingReads = mutableListOf<CompletableDeferred<ObservedValue?>>()
    private val updates = MutableSharedFlow<ObservedValue?>(extraBufferCapacity = 64)

    override suspend fun capability(signal: SignalKey): SupportResult {
        capabilityCalls += 1
        if (support == SupportResult.Supported) connection.value = ConnectionState.CONNECTED
        return support
    }

    override suspend fun read(signal: SignalKey, sessionId: String): Observation {
        readCalls += 1
        val value = if (holdReads) {
            val pending = CompletableDeferred<ObservedValue?>()
            pendingReads += pending
            pending.await()
        } else {
            current
        }
        return observation(value, ObservationKind.SNAPSHOT, sessionId)
    }

    override fun observe(signal: SignalKey, sessionId: String): Flow<Observation> = flow {
        subscriptions += sessionId
        try {
            updates.collect { emit(observation(it, ObservationKind.UPDATE, sessionId)) }
        } finally {
            subscriptions -= sessionId
        }
    }

    override suspend fun close() {
        closed = true
    }

    /** 상태 변화 알림을 보낸다. */
    fun emit(value: ObservedValue?) {
        current = value
        check(updates.tryEmit(value)) { "알림 버퍼가 가득 찼습니다." }
    }

    fun emit(code: String) = emit(ObservedValue.Known(code))

    /** 보류해 둔 가장 오래된 조회에 응답한다. */
    fun releaseRead(value: ObservedValue?) {
        check(pendingReads.isNotEmpty()) { "보류 중인 조회가 없습니다." }
        pendingReads.removeAt(0).complete(value)
    }

    private fun observation(value: ObservedValue?, kind: ObservationKind, sessionId: String) = Observation.of(
        signal = SignalKey.TURN_SIGNAL_STATE,
        value = value,
        receivedAtElapsedMs = 0L,
        source = ObservationSource.SIMULATION,
        kind = kind,
        sessionId = sessionId,
    )
}

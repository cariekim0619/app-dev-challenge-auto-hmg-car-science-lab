package com.carsciencelab.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * 개발용 모의 차량. 화면 개발과 흐름 확인에만 쓴다.
 *
 * simulation 빌드에만 들어 있고 차량 빌드(pleos)에는 포함되지 않는다.
 * 여기서 만든 관찰은 [ObservationSource.SIMULATION]으로 표시되며 SDK 연동 검증으로 세지 않는다.
 */
class DemoVehicleGateway : VehicleGateway {

    private val _connection = MutableStateFlow(ConnectionState.IDLE)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val updates = MutableSharedFlow<ObservedValue?>(extraBufferCapacity = 16)
    private var current: ObservedValue? = ObservedValue.Known(TurnSignalCodes.OFF)
    private var linkUp = true

    /** 지원 확인에 돌려줄 결과. 개발용 패널에서 바꾼다. */
    var support: SupportResult = SupportResult.Supported

    override suspend fun capability(signal: SignalKey): SupportResult {
        val result = if (linkUp) support else SupportResult.CheckFailed(CheckFailure.CONNECTION)
        _connection.value = if (result is SupportResult.CheckFailed) ConnectionState.FAILED else ConnectionState.CONNECTED
        return result
    }

    override suspend fun read(signal: SignalKey, sessionId: String): Observation {
        if (linkUp) _connection.value = ConnectionState.CONNECTED
        return observation(signal, if (linkUp) current else null, ObservationKind.SNAPSHOT, sessionId)
    }

    override fun observe(signal: SignalKey, sessionId: String): Flow<Observation> =
        updates.map { observation(signal, it, ObservationKind.UPDATE, sessionId) }

    override suspend fun close() {
        _connection.value = ConnectionState.IDLE
    }

    // ---- 개발용 조작 ----

    fun setTurnSignal(code: String) {
        current = ObservedValue.Known(code)
        if (linkUp) updates.tryEmit(current)
    }

    fun sendUnknownValue() {
        current = ObservedValue.Unknown("SIMULATED_UNKNOWN")
        if (linkUp) updates.tryEmit(current)
    }

    /** 같은 값을 한 번 더 보낸다. 중복 알림에도 완료가 반복되지 않는지 볼 때 쓴다. */
    fun repeatLastValue() {
        if (linkUp) updates.tryEmit(current)
    }

    fun setLink(up: Boolean) {
        linkUp = up
        _connection.value = if (up) ConnectionState.CONNECTED else ConnectionState.FAILED
    }

    private fun observation(
        signal: SignalKey,
        value: ObservedValue?,
        kind: ObservationKind,
        sessionId: String,
    ): Observation = Observation.of(
        signal = signal,
        value = value,
        receivedAtElapsedMs = System.nanoTime() / 1_000_000,
        source = ObservationSource.SIMULATION,
        kind = kind,
        sessionId = sessionId,
    )
}

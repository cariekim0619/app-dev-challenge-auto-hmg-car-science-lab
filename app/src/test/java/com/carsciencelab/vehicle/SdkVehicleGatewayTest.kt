package com.carsciencelab.vehicle

import com.carsciencelab.testsupport.CodedException
import com.carsciencelab.testsupport.FakeVehicleLink
import com.carsciencelab.testsupport.LinkErrorCode
import com.carsciencelab.testsupport.Reply
import com.carsciencelab.testsupport.SdkValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

private class Collected(val job: Job, val items: List<Observation>)

class SdkVehicleGatewayTest {

    private val signal = SignalKey.TURN_SIGNAL_STATE
    private val link = FakeVehicleLink()
    private val logs = mutableListOf<String>()
    private val gateway = gatewayWith(requestTimeoutMs = 80L)

    /** 테스트가 콜백을 직접 부르는 경우에 쓴다. 느린 환경에서도 시간 제한이 먼저 끝나지 않게 한다. */
    private val patientGateway = gatewayWith(requestTimeoutMs = 30_000L)

    private fun gatewayWith(requestTimeoutMs: Long) = SdkVehicleGateway(
        link = link,
        source = ObservationSource.VEHICLE_SDK,
        elapsedMs = { 1_234L },
        log = { _, message -> logs += message },
        requestTimeoutMs = requestTimeoutMs,
    )

    private fun CoroutineScope.collect(flow: Flow<Observation>): Collected {
        val items = Collections.synchronizedList(mutableListOf<Observation>())
        val job = launch(Dispatchers.Unconfined) { flow.collect { items += it } }
        return Collected(job, items)
    }

    private fun known(code: String) = ObservedValue.Known(code)

    // ---- 지원 확인 ----

    @Test
    fun `읽을 수 있는 접근 유형이면 지원으로 보고 연결은 한 번만 연다`() = runBlocking {
        link.access = Reply.Value("READ")
        assertEquals(SupportResult.Supported, gateway.capability(signal))
        link.access = Reply.Value("READ_WRITE")
        assertEquals(SupportResult.Supported, gateway.capability(signal))

        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
        assertEquals(1, link.opens)
        assertEquals(0, link.closes)
    }

    @Test
    fun `읽을 수 없는 접근 유형은 미지원이고 연결은 살아 있다`() = runBlocking {
        for (access in listOf("WRITE", "NONE", "SOMETHING_NEW")) {
            link.access = Reply.Value(access)
            assertEquals(SupportResult.Unsupported, gateway.capability(signal))
        }
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
    }

    @Test
    fun `권한 오류와 미지원 오류와 원인 모를 오류를 구분한다`() = runBlocking {
        link.access = Reply.Error(CodedException(LinkErrorCode.ERROR_SECURITY_PERMISSION))
        assertEquals(SupportResult.PermissionDenied, gateway.capability(signal))
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)

        link.access = Reply.Error(CodedException(LinkErrorCode.ERROR_NOT_SUPPORTED_PROPERTY))
        assertEquals(SupportResult.Unsupported, gateway.capability(signal))
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)

        link.access = Reply.Error(IllegalArgumentException("what"))
        val unknown = gateway.capability(signal)
        assertTrue(unknown is SupportResult.CheckFailed && unknown.failure == CheckFailure.UNKNOWN)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)
        // 원인 모를 오류로는 연결을 다시 열지 않는다.
        link.access = Reply.Value("READ")
        gateway.capability(signal)
        assertEquals(1, link.opens)
    }

    @Test
    fun `호출이 예외를 던져도 확인 실패로 돌려준다`() = runBlocking {
        link.access = Reply.Throws(IllegalStateException("boom"))
        val result = gateway.capability(signal)
        assertTrue(result is SupportResult.CheckFailed)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)
    }

    @Test
    fun `연결을 열기만 해서는 연결됨으로 표시하지 않는다`() = runBlocking {
        link.access = Reply.Silent
        assertEquals(ConnectionState.IDLE, patientGateway.connection.value)

        val pending = async(Dispatchers.Unconfined) { patientGateway.capability(signal) }
        assertEquals(1, link.opens)
        assertEquals(ConnectionState.CONNECTING, patientGateway.connection.value)

        link.pendingAccess.single().first("READ")
        assertEquals(SupportResult.Supported, pending.await())
        assertEquals(ConnectionState.CONNECTED, patientGateway.connection.value)
    }

    @Test
    fun `지원 확인이 답하지 않으면 확인 실패이고 다음 요청 때 연결을 다시 연다`() = runBlocking {
        link.access = Reply.Silent
        assertEquals(SupportResult.CheckFailed(CheckFailure.TIMEOUT), gateway.capability(signal))
        assertEquals(ConnectionState.FAILED, gateway.connection.value)
        assertEquals(1, link.opens)

        link.access = Reply.Value("READ")
        assertEquals(SupportResult.Supported, gateway.capability(signal))
        assertEquals(2, link.opens)
        assertEquals(1, link.closes)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
    }

    @Test
    fun `연결을 열지 못하면 확인 실패이고 다음 요청 때 다시 연다`() = runBlocking {
        link.openError = IllegalStateException("vehicle service missing")
        val failed = gateway.capability(signal)
        assertTrue(failed is SupportResult.CheckFailed)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)
        assertEquals(0, link.accessCalls)
        assertEquals(0, link.closes)

        link.openError = null
        assertEquals(SupportResult.Supported, gateway.capability(signal))
        assertEquals(2, link.opens)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
    }

    @Test
    fun `연결을 여는 중의 권한 예외는 권한 오류이지만 연결됨으로 표시하지 않는다`() = runBlocking {
        link.openError = SecurityException("no permission")
        assertEquals(SupportResult.PermissionDenied, gateway.capability(signal))
        assertEquals(ConnectionState.FAILED, gateway.connection.value)
    }

    @Test
    fun `연결 오류 코드는 연결 문제로 분류하고 다음 요청 때 다시 연다`() = runBlocking {
        link.access = Reply.Error(CodedException(LinkErrorCode.ERROR_SERVICE_CONNECTION_FAILURE))
        val failed = gateway.capability(signal)
        assertTrue(failed is SupportResult.CheckFailed && failed.failure == CheckFailure.CONNECTION)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)

        link.access = Reply.Value("READ")
        assertEquals(SupportResult.Supported, gateway.capability(signal))
        assertEquals(2, link.opens)
    }

    @Test
    fun `콜백이 여러 번 와도 처음 것만 받고 죽지 않는다`() = runBlocking {
        link.access = Reply.Silent
        val pending = async(Dispatchers.Unconfined) { patientGateway.capability(signal) }
        val (onAccess, onError) = link.pendingAccess.single()

        onAccess("READ")
        onAccess("NONE")
        onError(CodedException(LinkErrorCode.ERROR_SERVICE_CONNECTION_FAILURE))

        assertEquals(SupportResult.Supported, pending.await())
        // 이미 끝난 요청에 뒤늦게 온 오류로 연결 상태를 바꾸지 않는다.
        assertEquals(ConnectionState.CONNECTED, patientGateway.connection.value)
    }

    @Test
    fun `시간이 지난 뒤에 온 응답은 무시한다`() = runBlocking {
        link.access = Reply.Silent
        assertEquals(SupportResult.CheckFailed(CheckFailure.TIMEOUT), gateway.capability(signal))
        val (onAccess, onError) = link.pendingAccess.single()

        onAccess("READ")
        onError(RuntimeException("late"))

        assertEquals(ConnectionState.FAILED, gateway.connection.value)
    }

    // ---- 현재값 조회 ----

    @Test
    fun `조회한 값을 앱 내부 값으로 바꿔 돌려준다`() = runBlocking {
        link.current = Reply.Value(SdkValue.LEFT)
        val observation = gateway.read(signal, "s1")

        assertEquals(known(TurnSignalCodes.LEFT_ACTIVE), observation.value)
        assertEquals(ObservationQuality.VALID, observation.quality)
        assertEquals(ObservationKind.SNAPSHOT, observation.kind)
        assertEquals(ObservationSource.VEHICLE_SDK, observation.source)
        assertEquals("s1", observation.sessionId)
        assertEquals(1_234L, observation.receivedAtElapsedMs)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
    }

    @Test
    fun `값이 없다고 답하면 값 없는 관찰이고 연결은 살아 있다`() = runBlocking {
        link.current = Reply.Value(null)
        val observation = gateway.read(signal, "s1")

        assertEquals(null, observation.value)
        assertEquals(ObservationQuality.UNAVAILABLE, observation.quality)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
        // 지원 확인으로 되묻지 않고, 연결도 다시 열지 않는다.
        assertEquals(0, link.accessCalls)
        gateway.read(signal, "s1")
        assertEquals(1, link.opens)
    }

    @Test
    fun `모르는 값은 꺼짐으로 바꾸지 않는다`() = runBlocking {
        link.current = Reply.Value(SdkValue.HAZARD)
        val observation = gateway.read(signal, "s1")

        assertEquals(ObservedValue.Unknown("HAZARD"), observation.value)
        assertEquals(ObservationQuality.UNKNOWN_VALUE, observation.quality)
    }

    @Test
    fun `조회만 답이 없고 지원 확인은 답하면 연결 실패로 보지 않는다`() = runBlocking {
        link.current = Reply.Silent
        link.access = Reply.Value("READ")
        val observation = gateway.read(signal, "s1")

        assertEquals(ObservationQuality.UNAVAILABLE, observation.quality)
        assertEquals(ObservationKind.SNAPSHOT, observation.kind)
        assertEquals(1, link.accessCalls)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)

        link.current = Reply.Value(SdkValue.OFF)
        assertEquals(known(TurnSignalCodes.OFF), gateway.read(signal, "s1").value)
        assertEquals(1, link.opens)
    }

    @Test
    fun `조회도 지원 확인도 답이 없으면 연결 실패이고 다음 요청 때 다시 연다`() = runBlocking {
        link.current = Reply.Silent
        link.access = Reply.Silent
        val observation = gateway.read(signal, "s1")

        assertEquals(ObservationQuality.UNAVAILABLE, observation.quality)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)

        link.current = Reply.Value(SdkValue.OFF)
        assertEquals(known(TurnSignalCodes.OFF), gateway.read(signal, "s1").value)
        assertEquals(2, link.opens)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
    }

    @Test
    fun `조회가 연결 오류로 실패하면 되묻지 않고 연결 실패로 표시한다`() = runBlocking {
        link.current = Reply.Error(CodedException(LinkErrorCode.ERROR_SERVICE_CONNECTION_FAILURE))
        val observation = gateway.read(signal, "s1")

        assertEquals(ObservationQuality.UNAVAILABLE, observation.quality)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)
        assertEquals(0, link.accessCalls)

        link.current = Reply.Value(SdkValue.OFF)
        gateway.read(signal, "s1")
        assertEquals(2, link.opens)
    }

    @Test
    fun `조회가 권한 오류로 실패하면 연결은 살아 있는 것으로 둔다`() = runBlocking {
        link.current = Reply.Error(CodedException(LinkErrorCode.ERROR_SECURITY_PERMISSION))
        val observation = gateway.read(signal, "s1")

        assertEquals(ObservationQuality.UNAVAILABLE, observation.quality)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
        assertEquals(0, link.accessCalls)
    }

    @Test
    fun `값이 없다는 오류로 끝난 조회는 연결 실패가 아니고 되묻지도 않는다`() = runBlocking {
        link.current = Reply.Error(CodedException(LinkErrorCode.ERROR_EMPTY_RESULT))
        val observation = gateway.read(signal, "s1")

        assertEquals(null, observation.value)
        assertEquals(ObservationQuality.UNAVAILABLE, observation.quality)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)
        assertEquals(0, link.accessCalls)
        gateway.read(signal, "s1")
        assertEquals(1, link.opens)
    }

    @Test
    fun `조회가 원인 모를 오류로 실패하면 지원 확인으로 연결을 가린다`() = runBlocking {
        link.current = Reply.Error(RuntimeException("no idea"))
        link.access = Reply.Value("READ")
        assertEquals(ObservationQuality.UNAVAILABLE, gateway.read(signal, "s1").quality)
        assertEquals(1, link.accessCalls)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)

        link.access = Reply.Silent
        assertEquals(ObservationQuality.UNAVAILABLE, gateway.read(signal, "s1").quality)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)
    }

    @Test
    fun `조회 콜백이 값과 오류를 모두 불러도 처음 것만 받는다`() = runBlocking {
        link.current = Reply.Silent
        val pending = async(Dispatchers.Unconfined) { patientGateway.read(signal, "s1") }
        val (onValue, onError) = link.pendingReads.single()

        onValue(SdkValue.RIGHT)
        onError(RuntimeException("duplicate"))
        onValue(SdkValue.OFF)

        assertEquals(known(TurnSignalCodes.RIGHT_ACTIVE), pending.await().value)
    }

    // ---- 변화 알림 ----

    @Test
    fun `수집하는 동안 알림을 순서대로 내보내고 멈추면 같은 리스너를 해제한다`() = runBlocking {
        val collected = collect(gateway.observe(signal, "s7"))
        assertEquals(1, link.activeListeners)

        link.emit(SdkValue.LEFT)
        link.emit(SdkValue.OFF)
        link.emit(null)

        assertEquals(
            listOf(known(TurnSignalCodes.LEFT_ACTIVE), known(TurnSignalCodes.OFF), null),
            collected.items.map { it.value },
        )
        assertTrue(collected.items.all { it.kind == ObservationKind.UPDATE && it.sessionId == "s7" })

        collected.job.cancelAndJoin()
        assertEquals(0, link.activeListeners)
        assertEquals(1, link.cancelCalls)
    }

    @Test
    fun `리스너 오류는 값 없는 관찰로 내보내고 연결 오류일 때만 연결 실패로 표시한다`() = runBlocking {
        gateway.capability(signal)
        val collected = collect(gateway.observe(signal, "s1"))

        link.emitError(RuntimeException("no idea"))
        assertEquals(ObservationQuality.UNAVAILABLE, collected.items.single().quality)
        assertEquals(ConnectionState.CONNECTED, gateway.connection.value)

        link.emitError(CodedException(LinkErrorCode.ERROR_SERVICE_CONNECTION_FAILURE))
        assertEquals(2, collected.items.size)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)

        collected.job.cancelAndJoin()
    }

    @Test
    fun `연결을 다시 열면 수집 중인 알림 등록을 새 연결로 옮기고 끊겼음을 알린다`() = runBlocking {
        val collected = collect(gateway.observe(signal, "s1"))
        link.emit(SdkValue.OFF)
        assertEquals(1, link.subscribeCalls)

        // 지원 확인이 답하지 않아 연결을 다시 열어야 하는 상태가 된다.
        link.access = Reply.Silent
        gateway.capability(signal)
        link.access = Reply.Value("READ")
        gateway.capability(signal)

        assertEquals(2, link.opens)
        assertEquals(2, link.subscribeCalls)
        assertEquals(1, link.activeListeners)
        assertEquals(
            listOf(ObservationQuality.VALID, ObservationQuality.UNAVAILABLE),
            collected.items.map { it.quality },
        )

        link.emit(SdkValue.LEFT)
        assertEquals(known(TurnSignalCodes.LEFT_ACTIVE), collected.items.last().value)

        collected.job.cancelAndJoin()
        assertEquals(0, link.activeListeners)
        // 닫힌 연결의 등록은 해제하지 않는다. 새 연결의 등록만 한 번 해제한다.
        assertEquals(1, link.cancelCalls)
    }

    @Test
    fun `알림 등록에 실패하면 값 없는 관찰을 내보내고 다시 열 때 등록한다`() = runBlocking {
        gateway.capability(signal)
        link.subscribeError = IllegalStateException("register failed")
        val collected = collect(gateway.observe(signal, "s1"))

        assertEquals(ObservationQuality.UNAVAILABLE, collected.items.single().quality)
        assertEquals(0, link.activeListeners)
        assertEquals(ConnectionState.FAILED, gateway.connection.value)

        link.subscribeError = null
        gateway.capability(signal)
        assertEquals(2, link.opens)
        assertEquals(1, link.activeListeners)

        link.emit(SdkValue.RIGHT)
        assertEquals(known(TurnSignalCodes.RIGHT_ACTIVE), collected.items.last().value)
        collected.job.cancelAndJoin()
    }

    @Test
    fun `연결을 열지 못한 채 시작한 구독도 나중에 연결되면 등록한다`() = runBlocking {
        link.openError = IllegalStateException("vehicle service missing")
        val collected = collect(gateway.observe(signal, "s1"))
        assertEquals(ObservationQuality.UNAVAILABLE, collected.items.single().quality)
        assertEquals(0, link.subscribeCalls)

        link.openError = null
        assertEquals(SupportResult.Supported, gateway.capability(signal))
        assertEquals(1, link.activeListeners)

        link.emit(SdkValue.LEFT)
        assertEquals(known(TurnSignalCodes.LEFT_ACTIVE), collected.items.last().value)
        collected.job.cancelAndJoin()
    }

    @Test
    fun `수집을 멈춘 구독은 연결을 다시 열어도 등록하지 않는다`() = runBlocking {
        val collected = collect(gateway.observe(signal, "s1"))
        collected.job.cancelAndJoin()

        link.access = Reply.Silent
        gateway.capability(signal)
        link.access = Reply.Value("READ")
        gateway.capability(signal)

        assertEquals(2, link.opens)
        assertEquals(1, link.subscribeCalls)
        assertEquals(0, link.activeListeners)
    }

    @Test
    fun `닫으면 알림을 해제하고 연결을 정리한다`() = runBlocking {
        gateway.capability(signal)
        val collected = collect(gateway.observe(signal, "s1"))

        gateway.close()

        assertEquals(1, link.cancelCalls)
        assertEquals(0, link.activeListeners)
        assertEquals(1, link.closes)
        assertEquals(ConnectionState.IDLE, gateway.connection.value)

        // 닫은 뒤에 수집을 멈춰도 같은 등록을 두 번 해제하지 않는다.
        collected.job.cancelAndJoin()
        assertEquals(1, link.cancelCalls)
    }

    @Test
    fun `기록에 지원 확인 결과와 조회 값과 알림을 남긴다`() = runBlocking {
        gateway.capability(signal)
        link.current = Reply.Value(SdkValue.LEFT)
        gateway.read(signal, "s1")
        val collected = collect(gateway.observe(signal, "s1"))
        link.emit(SdkValue.OFF)
        collected.job.cancelAndJoin()

        assertTrue(logs.any { it == "capability turn_signal_state accessType=READ" })
        assertTrue(logs.any { it == "read turn_signal_state raw=LEFT" })
        assertTrue(logs.any { it == "update turn_signal_state raw=OFF session=s1" })
    }
}

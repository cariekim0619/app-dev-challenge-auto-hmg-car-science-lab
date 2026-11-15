package com.carsciencelab.vehicle

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * [VehicleLink]로 차량 상태를 읽는 [VehicleGateway]. 실차와 Pleos 에뮬레이터에서 같은 코드를 쓴다.
 *
 * 지키는 규칙
 * - 연결을 열었다는 것만으로 연결됐다고 표시하지 않는다. 차량 서비스가 요청에 답했을 때
 *   [ConnectionState.CONNECTED]로 바꾼다.
 * - 요청에는 시간 제한이 있다. 지원 확인이 답하지 않으면 연결 문제로 보고 다음 요청 때 다시 연다.
 * - 현재값 조회만 답이 없을 때는 곧바로 연결 실패로 단정하지 않는다. 지원 확인으로 차량 서비스가
 *   살아 있는지 본 뒤에 정한다. 값이 아직 없는 신호와 끊긴 연결을 구분하기 위해서다.
 * - 연결을 다시 열면 수집 중인 알림 등록을 새 연결로 옮기고, 그 사이의 변화는 받지 못했을 수 있으므로
 *   값이 없는 관찰을 한 번 내보낸다.
 * - SDK 값은 이름으로 판별하고([TurnSignalMapper]) 오류는 [VehicleErrorClassifier]로 분류한다.
 *   모르는 값과 없는 값을 꺼짐으로 바꾸지 않는다.
 * - 기록에는 지원 확인 결과, 조회한 값, 받은 알림을 남긴다. 차량 식별 정보는 남기지 않는다.
 */
class SdkVehicleGateway(
    private val link: VehicleLink,
    private val source: ObservationSource,
    private val elapsedMs: () -> Long,
    private val log: VehicleLog = VehicleLog { _, _ -> },
    private val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
) : VehicleGateway {

    private class Subscription(
        val signal: SignalKey,
        val onValue: (Any?) -> Unit,
        val onError: (Throwable) -> Unit,
        val onInterrupted: () -> Unit,
    ) {
        /** 지금 연결에 등록돼 있으면 해제 핸들, 아니면 null. */
        var handle: LinkSubscription? = null
    }

    private sealed interface Outcome<out T> {
        data class Answer<T>(val value: T) : Outcome<T>
        data class Failed(val failure: VehicleFailure) : Outcome<Nothing>
        data object TimedOut : Outcome<Nothing>
    }

    private val lock = Any()
    private var opened = false
    private val subscriptions = LinkedHashSet<Subscription>()

    @Volatile
    private var needsReopen = false

    private val _connection = MutableStateFlow(ConnectionState.IDLE)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    override suspend fun capability(signal: SignalKey): SupportResult {
        val outcome = request<String>("capability") { onAnswer, onError ->
            link.checkAccess(signal, onAnswer, onError)
        }
        val result = when (outcome) {
            is Outcome.Answer -> {
                log.write(false, "capability ${signal.id} accessType=${outcome.value}")
                if (outcome.value in READABLE_ACCESS) SupportResult.Supported else SupportResult.Unsupported
            }

            is Outcome.Failed -> VehicleErrorClassifier.toSupportResult(outcome.failure)

            Outcome.TimedOut -> {
                log.write(true, "capability ${signal.id} timed out")
                needsReopen = true
                SupportResult.CheckFailed(CheckFailure.TIMEOUT)
            }
        }
        _connection.value = if (result is SupportResult.CheckFailed || !isOpen()) {
            ConnectionState.FAILED
        } else {
            ConnectionState.CONNECTED
        }
        return result
    }

    override suspend fun read(signal: SignalKey, sessionId: String): Observation {
        val outcome = request<Any?>("read") { onAnswer, onError ->
            link.readCurrent(signal, onAnswer, onError)
        }
        when (outcome) {
            is Outcome.Answer -> {
                log.write(false, "read ${signal.id} raw=${outcome.value}")
                _connection.value = ConnectionState.CONNECTED
                return observation(signal, outcome.value, ObservationKind.SNAPSHOT, sessionId)
            }

            is Outcome.Failed -> when (outcome.failure.kind) {
                // 차량 서비스가 오류로 답했다. 연결은 살아 있다.
                VehicleFailureKind.PERMISSION, VehicleFailureKind.UNSUPPORTED, VehicleFailureKind.EMPTY ->
                    _connection.value = if (isOpen()) ConnectionState.CONNECTED else ConnectionState.FAILED

                // noteFailure가 이미 연결 실패로 표시했다.
                VehicleFailureKind.CONNECTION -> Unit

                // 원인을 모르면 차량 서비스가 답하는지 지원 확인으로 가린다.
                VehicleFailureKind.UNKNOWN -> capability(signal)
            }

            Outcome.TimedOut -> {
                log.write(true, "read ${signal.id} timed out")
                // 이 조회만 답이 없는지, 차량 서비스가 아예 답하지 않는지 지원 확인으로 가린다.
                capability(signal)
            }
        }
        return unavailable(signal, ObservationKind.SNAPSHOT, sessionId)
    }

    override fun observe(signal: SignalKey, sessionId: String): Flow<Observation> = callbackFlow {
        val subscription = Subscription(
            signal = signal,
            onValue = { raw ->
                log.write(false, "update ${signal.id} raw=$raw session=$sessionId")
                trySend(observation(signal, raw, ObservationKind.UPDATE, sessionId))
            },
            onError = { error ->
                noteFailure("listener", error)
                trySend(unavailable(signal, ObservationKind.UPDATE, sessionId))
            },
            onInterrupted = { trySend(unavailable(signal, ObservationKind.UPDATE, sessionId)) },
        )
        if (!attach(subscription)) trySend(unavailable(signal, ObservationKind.UPDATE, sessionId))
        // 수집이 끝나면 등록할 때 쓴 것과 같은 리스너로 해제한다.
        awaitClose { detach(subscription) }
    }

    override suspend fun close() {
        synchronized(lock) {
            for (subscription in subscriptions) {
                val handle = subscription.handle
                subscription.handle = null
                if (handle != null) cancelQuietly(handle, subscription.signal)
            }
            subscriptions.clear()
            if (opened) closeLink()
            opened = false
            needsReopen = false
        }
        _connection.value = ConnectionState.IDLE
    }

    // ---- 요청 ----

    /**
     * 콜백 한 번으로 끝나는 요청을 보내고 결과를 기다린다.
     * SDK가 콜백을 두 번 부르거나 성공과 실패를 모두 불러도 처음 한 번만 받는다.
     */
    private suspend fun <T> request(
        operation: String,
        send: (onAnswer: (T) -> Unit, onError: (Throwable) -> Unit) -> Unit,
    ): Outcome<T> {
        val outcome = withTimeoutOrNull(requestTimeoutMs) {
            suspendCancellableCoroutine<Outcome<T>> { continuation ->
                val settled = AtomicBoolean(false)
                val onAnswer: (T) -> Unit = { value ->
                    if (settled.compareAndSet(false, true)) continuation.resume(Outcome.Answer(value))
                }
                val onError: (Throwable) -> Unit = { error ->
                    if (settled.compareAndSet(false, true)) {
                        continuation.resume(Outcome.Failed(noteFailure(operation, error)))
                    } else {
                        log.write(true, "$operation late failure ignored type=${error.javaClass.name}")
                    }
                }
                try {
                    ensureOpen()
                    send(onAnswer, onError)
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }
        return outcome ?: Outcome.TimedOut
    }

    // ---- 알림 등록 ----

    private fun attach(subscription: Subscription): Boolean {
        var newlyOpened = false
        val error: Exception? = synchronized(lock) {
            val failure = try {
                newlyOpened = openLocked()
                subscription.handle =
                    link.subscribe(subscription.signal, subscription.onValue, subscription.onError)
                null
            } catch (e: Exception) {
                e
            }
            // 등록에 실패했어도 목록에 남긴다. 연결을 다시 열 때 한 번 더 등록한다.
            subscriptions += subscription
            failure
        }
        if (newlyOpened) _connection.value = ConnectionState.CONNECTING
        if (error != null) {
            noteFailure("register", error)
            // 알림을 등록하지 못한 구독은 변화를 받지 못한다. 원인이 무엇이든 연결 문제로 표시해
            // 사용자가 다시 확인할 때 연결을 새로 열고 등록을 다시 시도하게 한다.
            needsReopen = true
            _connection.value = ConnectionState.FAILED
            return false
        }
        log.write(false, "registered ${subscription.signal.id}")
        return true
    }

    private fun detach(subscription: Subscription) {
        val handle = synchronized(lock) {
            subscriptions -= subscription
            val current = subscription.handle
            subscription.handle = null
            current
        }
        if (handle != null) cancelQuietly(handle, subscription.signal)
    }

    private fun cancelQuietly(handle: LinkSubscription, signal: SignalKey) {
        try {
            handle.cancel()
            log.write(false, "unregistered ${signal.id}")
        } catch (e: Exception) {
            log.write(true, "unregister ${signal.id} failed type=${e.javaClass.name}")
        }
    }

    // ---- 연결 수명 ----

    private fun isOpen(): Boolean = synchronized(lock) { opened }

    private fun ensureOpen() {
        val newlyOpened = synchronized(lock) { openLocked() }
        if (newlyOpened) _connection.value = ConnectionState.CONNECTING
    }

    /**
     * 연결이 없으면 열고, 연결 문제가 있었다면 닫았다가 다시 연다. 새로 열었으면 true.
     * [lock]을 잡고 불러야 한다. 열지 못하면 예외가 그대로 나간다.
     */
    private fun openLocked(): Boolean {
        if (opened && needsReopen) {
            log.write(true, "reopening vehicle link")
            // 닫는 연결의 알림 등록은 함께 사라진다.
            for (subscription in subscriptions) subscription.handle = null
            closeLink()
            opened = false
        }
        if (opened) return false
        link.open()
        opened = true
        needsReopen = false
        log.write(false, "vehicle link opened")
        for (subscription in subscriptions) {
            subscription.handle = try {
                link.subscribe(subscription.signal, subscription.onValue, subscription.onError)
            } catch (e: Exception) {
                log.write(true, "re-register ${subscription.signal.id} failed type=${e.javaClass.name}")
                null
            }
            // 다시 여는 동안의 변화는 받지 못했을 수 있다. 현재값을 모른다고 알린다.
            subscription.onInterrupted()
        }
        return true
    }

    private fun closeLink() {
        try {
            link.close()
        } catch (e: Exception) {
            log.write(true, "close failed type=${e.javaClass.name}")
        }
    }

    private fun noteFailure(operation: String, error: Throwable): VehicleFailure {
        val failure = VehicleErrorClassifier.classify(error)
        log.write(
            true,
            "$operation failed kind=${failure.kind} code=${failure.code} " +
                "type=${failure.exceptionType} message=${error.message}",
        )
        if (failure.kind == VehicleFailureKind.CONNECTION) {
            needsReopen = true
            _connection.value = ConnectionState.FAILED
        }
        return failure
    }

    // ---- 관찰 만들기 ----

    private fun observation(signal: SignalKey, raw: Any?, kind: ObservationKind, sessionId: String): Observation =
        Observation.of(
            signal = signal,
            value = when (signal) {
                SignalKey.TURN_SIGNAL_STATE -> TurnSignalMapper.fromSdk(raw)
            },
            receivedAtElapsedMs = elapsedMs(),
            source = source,
            kind = kind,
            sessionId = sessionId,
        )

    private fun unavailable(signal: SignalKey, kind: ObservationKind, sessionId: String): Observation =
        Observation.of(
            signal = signal,
            value = null,
            receivedAtElapsedMs = elapsedMs(),
            source = source,
            kind = kind,
            sessionId = sessionId,
        )

    companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MS = 5_000L
        private val READABLE_ACCESS = setOf("READ", "READ_WRITE")
    }
}

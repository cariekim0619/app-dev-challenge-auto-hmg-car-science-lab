package com.carsciencelab.testsupport

import com.carsciencelab.vehicle.LinkSubscription
import com.carsciencelab.vehicle.SignalKey
import com.carsciencelab.vehicle.VehicleLink

/** SDK가 주는 방향지시등 값의 대역. HAZARD는 앱이 모르는 이름이다. */
enum class SdkValue { OFF, LEFT, RIGHT, HAZARD }

/** 문서에 나온 SDK ErrorCode 이름 가운데 테스트에 쓰는 것. */
enum class LinkErrorCode {
    ERROR_SECURITY_PERMISSION,
    ERROR_NOT_SUPPORTED_PROPERTY,
    ERROR_SERVICE_CONNECTION_FAILURE,
    ERROR_EMPTY_RESULT,
}

/** SDK 예외처럼 오류 코드를 인자 없는 메서드로만 내놓는다. */
class CodedException(private val code: LinkErrorCode) : Exception("coded failure") {
    @Suppress("unused")
    fun a(): LinkErrorCode = code
}

sealed interface Reply<out T> {
    data class Value<T>(val value: T) : Reply<T>
    data class Error(val error: Throwable) : Reply<Nothing>

    /** 콜백 대신 호출 자체가 예외를 던진다. */
    data class Throws(val error: Exception) : Reply<Nothing>

    /** 콜백을 부르지 않는다. 나중에 pending 목록으로 직접 부를 수 있다. */
    data object Silent : Reply<Nothing>
}

/** 응답을 정해 둘 수 있는 가짜 SDK 접점. 연결을 닫으면 그 연결의 알림 등록도 사라진다. */
class FakeVehicleLink : VehicleLink {
    private class Listener(val onValue: (Any?) -> Unit, val onError: (Throwable) -> Unit)

    var openError: Exception? = null
    var subscribeError: Exception? = null
    var access: Reply<String> = Reply.Value("READ")
    var current: Reply<Any?> = Reply.Value(SdkValue.OFF)

    var opens = 0
    var closes = 0
    var accessCalls = 0
    var readCalls = 0
    var subscribeCalls = 0
    var cancelCalls = 0

    val pendingAccess = mutableListOf<Pair<(String) -> Unit, (Throwable) -> Unit>>()
    val pendingReads = mutableListOf<Pair<(Any?) -> Unit, (Throwable) -> Unit>>()

    private var isOpen = false
    private val listeners = mutableListOf<Listener>()
    val activeListeners: Int get() = listeners.size

    override fun open() {
        opens += 1
        openError?.let { throw it }
        isOpen = true
    }

    override fun close() {
        closes += 1
        isOpen = false
        listeners.clear()
    }

    override fun checkAccess(signal: SignalKey, onAccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        check(isOpen) { "link is closed" }
        accessCalls += 1
        pendingAccess += onAccess to onError
        when (val reply = access) {
            is Reply.Value -> onAccess(reply.value)
            is Reply.Error -> onError(reply.error)
            is Reply.Throws -> throw reply.error
            Reply.Silent -> Unit
        }
    }

    override fun readCurrent(signal: SignalKey, onValue: (Any?) -> Unit, onError: (Throwable) -> Unit) {
        check(isOpen) { "link is closed" }
        readCalls += 1
        pendingReads += onValue to onError
        when (val reply = current) {
            is Reply.Value -> onValue(reply.value)
            is Reply.Error -> onError(reply.error)
            is Reply.Throws -> throw reply.error
            Reply.Silent -> Unit
        }
    }

    override fun subscribe(
        signal: SignalKey,
        onValue: (Any?) -> Unit,
        onError: (Throwable) -> Unit,
    ): LinkSubscription {
        check(isOpen) { "link is closed" }
        subscribeCalls += 1
        subscribeError?.let { throw it }
        val listener = Listener(onValue, onError)
        listeners += listener
        return LinkSubscription {
            cancelCalls += 1
            listeners -= listener
        }
    }

    fun emit(raw: Any?) = listeners.toList().forEach { it.onValue(raw) }

    fun emitError(error: Throwable) = listeners.toList().forEach { it.onError(error) }
}

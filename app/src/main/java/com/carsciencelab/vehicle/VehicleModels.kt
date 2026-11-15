package com.carsciencelab.vehicle

/**
 * 앱이 관찰하는 차량 신호의 내부 키.
 * Pleos SDK의 함수나 enum 이름이 아니라 이 앱이 정한 이름이다.
 */
enum class SignalKey(val id: String) {
    TURN_SIGNAL_STATE("turn_signal_state");

    companion object {
        fun fromId(id: String): SignalKey? = entries.firstOrNull { it.id == id }
    }
}

/**
 * SDK 값을 확인한 뒤 매핑한 앱 내부 값.
 * 알 수 없는 값은 [Unknown]으로 남기며 꺼짐이나 정답으로 바꾸지 않는다.
 */
sealed interface ObservedValue {
    data class Known(val code: String) : ObservedValue
    data class Unknown(val raw: String) : ObservedValue
}

/** 방향지시등 실험에서 쓰는 정규화한 값. SDK enum 이름과 다르다. */
object TurnSignalCodes {
    const val OFF = "OFF"
    const val LEFT_ACTIVE = "LEFT_ACTIVE"
    const val RIGHT_ACTIVE = "RIGHT_ACTIVE"
}

enum class ObservationSource { VEHICLE_SDK, SIMULATION }

enum class ObservationKind {
    /** 조회 요청에 대한 응답. */
    SNAPSHOT,

    /** 상태 변화 알림. */
    UPDATE,
}

enum class ObservationQuality { VALID, UNKNOWN_VALUE, UNAVAILABLE }

/**
 * 차량에서 받은 관찰 한 건.
 * [receivedAtElapsedMs]는 앱이 값을 받은 시각이며 값이 실제로 바뀐 시각이 아니다.
 */
data class Observation(
    val signal: SignalKey,
    val value: ObservedValue?,
    val receivedAtElapsedMs: Long,
    val source: ObservationSource,
    val kind: ObservationKind,
    val sessionId: String,
    val quality: ObservationQuality,
) {
    companion object {
        /** 값의 종류에 맞는 [ObservationQuality]를 붙여 만든다. */
        fun of(
            signal: SignalKey,
            value: ObservedValue?,
            receivedAtElapsedMs: Long,
            source: ObservationSource,
            kind: ObservationKind,
            sessionId: String,
        ): Observation = Observation(
            signal = signal,
            value = value,
            receivedAtElapsedMs = receivedAtElapsedMs,
            source = source,
            kind = kind,
            sessionId = sessionId,
            quality = when (value) {
                is ObservedValue.Known -> ObservationQuality.VALID
                is ObservedValue.Unknown -> ObservationQuality.UNKNOWN_VALUE
                null -> ObservationQuality.UNAVAILABLE
            },
        )
    }
}

enum class CheckFailure { CONNECTION, TIMEOUT, UNKNOWN }

/** 기능 지원 확인 결과. 미지원과 확인 실패, 권한 오류를 구분한다. */
sealed interface SupportResult {
    data object Supported : SupportResult
    data object Unsupported : SupportResult
    data object PermissionDenied : SupportResult
    data class CheckFailed(val failure: CheckFailure, val detail: String? = null) : SupportResult
}

enum class ConnectionState {
    /** 아직 차량 서비스에 요청을 보내지 않았다. */
    IDLE,

    /** 초기화는 호출했지만 서비스의 응답을 받기 전이다. */
    CONNECTING,

    /** 차량 서비스가 요청에 응답했다. */
    CONNECTED,

    /** 초기화나 요청이 연결 문제로 실패했다. */
    FAILED,
}

package com.carsciencelab.learning

import com.carsciencelab.vehicle.Observation
import com.carsciencelab.vehicle.ObservationKind

/** 수신 순서가 붙은 관찰. */
data class SequencedObservation(
    val observation: Observation,
    /** 받아들인 순서. 세션이 바뀌어도 계속 증가한다. */
    val seq: Long,
    /** 조회 응답일 때, 조회를 요청한 시점의 표식. 알림이면 null. */
    val requestMark: Long?,
) {
    /**
     * [mark] 시점 이후의 정보인가.
     * 알림은 그 뒤에 받은 것만, 조회 응답은 그 시점이나 그 뒤에 요청한 것만 해당한다.
     */
    fun isAfter(mark: Long): Boolean = when (observation.kind) {
        ObservationKind.UPDATE -> seq > mark
        ObservationKind.SNAPSHOT -> (requestMark ?: Long.MIN_VALUE) >= mark
    }
}

enum class DropReason {
    NO_ACTIVE_SESSION,
    SESSION_MISMATCH,

    /** 조회를 요청한 뒤에 더 새로운 알림을 이미 받았다. */
    STALE_SNAPSHOT,
    MISSING_REQUEST_MARK,
}

sealed interface Interpretation {
    data class Accepted(val observation: SequencedObservation) : Interpretation
    data class Dropped(val reason: DropReason) : Interpretation
}

/**
 * 차량에서 온 관찰에 순서를 붙이고, 쓰면 안 되는 관찰을 걸러 낸다.
 *
 * - 현재 세션이 아닌 관찰은 버린다(이전 구독의 늦은 알림).
 * - 조회 응답이 도착하기 전에 더 새로운 알림을 받았다면 그 응답을 버린다.
 *   오래된 조회 결과가 최신 알림을 덮어쓰지 않게 하기 위해서다.
 *
 * 시간이 지났다는 이유만으로 값을 오래된 것으로 취급하지 않는다.
 * 변화가 없는 정상 상태일 수 있기 때문이다.
 */
class ObservationInterpreter {
    private var activeSessionId: String? = null
    private var seq: Long = 0
    private var lastUpdateSeq: Long = 0

    val sessionId: String? get() = activeSessionId

    fun startSession(sessionId: String) {
        activeSessionId = sessionId
    }

    fun endSession() {
        activeSessionId = null
    }

    /** 현재 수신 순서 표식. 조회를 요청하기 직전에 받아 두었다가 응답과 함께 넘긴다. */
    fun mark(): Long = seq

    /**
     * 과제 시작을 순서에 새긴다. 돌려준 표식보다 앞선 관찰과, 그 전에 요청한 조회는
     * 과제 시작 이전의 정보로 구분된다.
     */
    fun beginTask(): Long {
        seq += 1
        return seq
    }

    fun accept(observation: Observation, requestMark: Long? = null): Interpretation {
        val active = activeSessionId ?: return Interpretation.Dropped(DropReason.NO_ACTIVE_SESSION)
        if (observation.sessionId != active) {
            return Interpretation.Dropped(DropReason.SESSION_MISMATCH)
        }
        return when (observation.kind) {
            ObservationKind.UPDATE -> {
                seq += 1
                lastUpdateSeq = seq
                Interpretation.Accepted(SequencedObservation(observation, seq, null))
            }

            ObservationKind.SNAPSHOT -> {
                val mark = requestMark
                    ?: return Interpretation.Dropped(DropReason.MISSING_REQUEST_MARK)
                if (lastUpdateSeq > mark) {
                    return Interpretation.Dropped(DropReason.STALE_SNAPSHOT)
                }
                seq += 1
                Interpretation.Accepted(SequencedObservation(observation, seq, mark))
            }
        }
    }
}

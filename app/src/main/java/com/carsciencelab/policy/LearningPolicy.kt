package com.carsciencelab.policy

import kotlinx.coroutines.flow.StateFlow

enum class ParkingState { PARKED, NOT_PARKED, UNKNOWN }

enum class UxRestrictionState { UNRESTRICTED, RESTRICTED, UNKNOWN }

/** 플랫폼에서 읽은 주차·UX 제한 상태. 읽지 못한 항목은 UNKNOWN으로 둔다. */
data class SafetySnapshot(val parking: ParkingState, val ux: UxRestrictionState) {
    companion object {
        val UNKNOWN = SafetySnapshot(ParkingState.UNKNOWN, UxRestrictionState.UNKNOWN)
    }
}

/** 주차·UX 제한 상태를 알려 주는 쪽. 구현체는 플랫폼 API를 감싼다. */
interface SafetyMonitor {
    val snapshot: StateFlow<SafetySnapshot>

    fun start()

    fun stop()
}

enum class BlockReason {
    NOT_FOREGROUND,
    UX_RESTRICTED,
    NOT_PARKED,
    PARKING_UNKNOWN,
    UX_UNKNOWN,
}

sealed interface LearningPermission {
    data object Allowed : LearningPermission
    data class Blocked(val reason: BlockReason) : LearningPermission
}

/**
 * 학습을 진행해도 되는지 판단한다. 차량 관찰과 가상 학습 모두 이 판단을 통과해야 한다.
 *
 * 주차(P)와 시스템 UX 제한 해제를 모두 확인한 경우에만 허용한다.
 * 어느 하나라도 읽지 못하면 잠근다. ‘속도 0’이나 ‘주차 브레이크’ 같은 자체 규칙으로
 * 플랫폼 제한을 대신하지 않는다.
 */
object LearningPolicy {

    fun evaluate(snapshot: SafetySnapshot, foreground: Boolean): LearningPermission {
        val reason = when {
            !foreground -> BlockReason.NOT_FOREGROUND
            snapshot.ux == UxRestrictionState.RESTRICTED -> BlockReason.UX_RESTRICTED
            snapshot.parking == ParkingState.NOT_PARKED -> BlockReason.NOT_PARKED
            snapshot.parking == ParkingState.UNKNOWN -> BlockReason.PARKING_UNKNOWN
            snapshot.ux == UxRestrictionState.UNKNOWN -> BlockReason.UX_UNKNOWN
            else -> null
        }
        return if (reason == null) LearningPermission.Allowed else LearningPermission.Blocked(reason)
    }
}

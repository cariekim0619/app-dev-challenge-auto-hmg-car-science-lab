package com.carsciencelab.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningPolicyTest {

    private fun evaluate(parking: ParkingState, ux: UxRestrictionState, foreground: Boolean = true) =
        LearningPolicy.evaluate(SafetySnapshot(parking, ux), foreground)

    @Test
    fun `주차와 제한 해제를 모두 확인했을 때만 허용한다`() {
        for (parking in ParkingState.entries) {
            for (ux in UxRestrictionState.entries) {
                val expectedAllowed = parking == ParkingState.PARKED && ux == UxRestrictionState.UNRESTRICTED
                val actual = evaluate(parking, ux)
                assertEquals("$parking / $ux", expectedAllowed, actual == LearningPermission.Allowed)
            }
        }
    }

    @Test
    fun `읽지 못한 항목이 있으면 잠근다`() {
        assertEquals(
            LearningPermission.Blocked(BlockReason.PARKING_UNKNOWN),
            evaluate(ParkingState.UNKNOWN, UxRestrictionState.UNRESTRICTED),
        )
        assertEquals(
            LearningPermission.Blocked(BlockReason.UX_UNKNOWN),
            evaluate(ParkingState.PARKED, UxRestrictionState.UNKNOWN),
        )
        assertEquals(
            LearningPermission.Blocked(BlockReason.PARKING_UNKNOWN),
            LearningPolicy.evaluate(SafetySnapshot.UNKNOWN, foreground = true),
        )
    }

    @Test
    fun `주차 중이어도 시스템 제한이 걸려 있으면 잠근다`() {
        assertEquals(
            LearningPermission.Blocked(BlockReason.UX_RESTRICTED),
            evaluate(ParkingState.PARKED, UxRestrictionState.RESTRICTED),
        )
    }

    @Test
    fun `제한이 없어도 주차가 아니면 잠근다`() {
        assertEquals(
            LearningPermission.Blocked(BlockReason.NOT_PARKED),
            evaluate(ParkingState.NOT_PARKED, UxRestrictionState.UNRESTRICTED),
        )
    }

    @Test
    fun `화면이 보이지 않으면 조건이 좋아도 잠근다`() {
        assertEquals(
            LearningPermission.Blocked(BlockReason.NOT_FOREGROUND),
            evaluate(ParkingState.PARKED, UxRestrictionState.UNRESTRICTED, foreground = false),
        )
    }
}

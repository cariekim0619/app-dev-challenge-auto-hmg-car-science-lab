package com.carsciencelab.policy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 개발용 모의 주차·UX 제한 상태. simulation 빌드에만 들어 있다.
 * 처음에는 주차 중이고 제한이 없는 상태로 시작한다.
 */
class DemoSafetyMonitor : SafetyMonitor {

    private val _snapshot = MutableStateFlow(
        SafetySnapshot(ParkingState.PARKED, UxRestrictionState.UNRESTRICTED),
    )
    override val snapshot: StateFlow<SafetySnapshot> = _snapshot.asStateFlow()

    override fun start() = Unit

    override fun stop() = Unit

    fun setParking(state: ParkingState) {
        _snapshot.value = _snapshot.value.copy(parking = state)
    }

    fun setUxRestriction(state: UxRestrictionState) {
        _snapshot.value = _snapshot.value.copy(ux = state)
    }
}

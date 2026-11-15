package com.carsciencelab.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 상태를 읽는 계약. 이 앱이 정의한 인터페이스이며 Pleos SDK의 함수 서명이 아니다.
 *
 * 구현체는 읽기만 한다. 차량을 바꾸는 명령은 이 계약에 없다.
 * [sessionId]는 호출하는 쪽이 정하고, 구현체는 내보내는 [Observation]에 그대로 붙인다.
 * 구독을 다시 시작할 때마다 새 값을 넘기면 이전 구독의 늦은 알림을 가려낼 수 있다.
 */
interface VehicleGateway {
    val connection: StateFlow<ConnectionState>

    /** 신호를 읽을 수 있는지 확인한다. 실패 사유를 미지원으로 뭉개지 않는다. */
    suspend fun capability(signal: SignalKey): SupportResult

    /** 현재값을 한 번 읽는다. 읽지 못하면 값이 없는 [Observation]을 돌려준다. */
    suspend fun read(signal: SignalKey, sessionId: String): Observation

    /** 수집하는 동안만 상태 변화 알림을 구독한다. 수집을 멈추면 같은 리스너로 해제한다. */
    fun observe(signal: SignalKey, sessionId: String): Flow<Observation>

    /** 남은 구독을 정리하고 SDK를 해제한다. */
    suspend fun close()
}

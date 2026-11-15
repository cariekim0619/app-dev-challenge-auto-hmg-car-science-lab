package com.carsciencelab.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.TurnSignal
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context

/**
 * Pleos Vehicle SDK 호출을 [VehicleLink]로 옮긴다. SDK 타입은 이 파일에서만 다룬다.
 *
 * 호출하는 API는 모두 조회용이다(지원 확인, 현재값 조회, 변화 알림 등록과 해제).
 * 판단은 하지 않는다. 값과 예외를 받은 그대로 [SdkVehicleGateway]에 넘긴다.
 *
 * 호출 형태의 근거는 docs/verified-features.md에 적었다.
 */
class PleosVehicleLink(context: Context) : VehicleLink {

    private val appContext = context.applicationContext

    @Volatile
    private var vehicle: Vehicle? = null

    override fun open() {
        val created = Vehicle(appContext)
        created.initialize()
        vehicle = created
    }

    override fun close() {
        val current = vehicle ?: return
        vehicle = null
        current.release()
    }

    override fun checkAccess(signal: SignalKey, onAccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        when (signal) {
            SignalKey.TURN_SIGNAL_STATE -> turnSignal().checkTurnSignalStateCapabilities(
                { config -> onAccess(config.accessType.name) },
                { error -> onError(error) },
            )
        }
    }

    override fun readCurrent(signal: SignalKey, onValue: (Any?) -> Unit, onError: (Throwable) -> Unit) {
        when (signal) {
            SignalKey.TURN_SIGNAL_STATE -> turnSignal().getCurrentTurnSignalState(
                { state -> onValue(state) },
                { error -> onError(error) },
            )
        }
    }

    override fun subscribe(
        signal: SignalKey,
        onValue: (Any?) -> Unit,
        onError: (Throwable) -> Unit,
    ): LinkSubscription = when (signal) {
        SignalKey.TURN_SIGNAL_STATE -> {
            val api = turnSignal()
            val listener = object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                    onValue(state)
                }

                override fun onFailed(e: Exception) {
                    onError(e)
                }
            }
            api.registerTurnSignalState(listener)
            // 등록할 때 쓴 것과 같은 리스너로 해제한다.
            LinkSubscription { api.unregisterTurnSignalState(listener) }
        }
    }

    private fun turnSignal(): TurnSignal =
        checkNotNull(vehicle) { "vehicle link is not open" }.getTurnSignal()
}

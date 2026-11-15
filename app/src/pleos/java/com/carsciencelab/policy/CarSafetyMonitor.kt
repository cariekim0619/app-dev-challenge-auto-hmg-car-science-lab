package com.carsciencelab.policy

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AAOS 공개 API로 주차 여부와 시스템 UX 제한을 읽는다.
 *
 * - 주차: `GEAR_SELECTION` 프로퍼티가 P인지 확인한다(권한 `android.car.permission.CAR_POWERTRAIN`).
 * - UX 제한: `CarUxRestrictionsManager`의 현재값과 변경 알림을 반영한다.
 *
 * 둘 중 하나라도 읽지 못하면 해당 항목을 UNKNOWN으로 두고, [LearningPolicy]가 학습을 잠근다.
 * ‘속도 0’이나 ‘주차 브레이크’ 같은 자체 규칙으로 대신하지 않는다.
 *
 * 이 프로퍼티의 지원 여부와 권한, 에뮬레이터 동작은 직접 확인해야 한다(docs/verified-features.md).
 */
class CarSafetyMonitor(context: Context) : SafetyMonitor {

    private val appContext = context.applicationContext
    private val _snapshot = MutableStateFlow(SafetySnapshot.UNKNOWN)
    override val snapshot: StateFlow<SafetySnapshot> = _snapshot.asStateFlow()

    private var started = false
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null
    private var gearRegistered = false

    // 차량 라이브러리(android.car)가 없는 기기에서 이 클래스를 만들기만 해도 죽지 않도록,
    // 콜백 객체는 차량용 기기임을 확인한 뒤 startGear에서 만든다.
    private var gearCallback: CarPropertyManager.CarPropertyEventCallback? = null

    private fun createGearCallback(): CarPropertyManager.CarPropertyEventCallback =
        object : CarPropertyManager.CarPropertyEventCallback {
            @Suppress("DEPRECATION")
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (value.propertyId == VehiclePropertyIds.GEAR_SELECTION) {
                    // 값을 쓸 수 없는 상태로 온 알림은 기어를 모르는 것으로 본다.
                    val available = value.status == CarPropertyValue.STATUS_AVAILABLE
                    val gear = if (available) value.value as? Int else null
                    Log.i(TAG, "gear changed value=$gear available=$available")
                    updateParking(parkingFrom(gear))
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                if (propertyId == VehiclePropertyIds.GEAR_SELECTION) {
                    Log.w(TAG, "gear property error")
                    updateParking(ParkingState.UNKNOWN)
                }
            }
        }

    override fun start() {
        if (started) return
        started = true
        _snapshot.value = SafetySnapshot.UNKNOWN
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.w(TAG, "automotive feature missing; learning stays locked")
            return
        }
        // android.car 라이브러리가 없는 기기에서는 클래스를 찾지 못할 수 있어 Throwable로 받는다.
        val connected = try {
            Car.createCar(appContext)
        } catch (t: Throwable) {
            Log.w(TAG, "car service unavailable", t)
            null
        }
        if (connected == null) return
        car = connected
        try {
            startUxRestrictions(connected)
        } catch (t: Throwable) {
            Log.w(TAG, "ux restrictions unavailable", t)
            updateUx(UxRestrictionState.UNKNOWN)
        }
        try {
            startGear(connected)
        } catch (t: Throwable) {
            Log.w(TAG, "gear selection unavailable", t)
            updateParking(ParkingState.UNKNOWN)
        }
    }

    override fun stop() {
        if (!started) return
        started = false
        try {
            uxManager?.unregisterListener()
        } catch (t: Throwable) {
            Log.w(TAG, "ux listener unregister failed", t)
        }
        try {
            val callback = gearCallback
            if (gearRegistered && callback != null) propertyManager?.unregisterCallback(callback)
        } catch (t: Throwable) {
            Log.w(TAG, "gear callback unregister failed", t)
        }
        try {
            car?.disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "car disconnect failed", t)
        }
        gearRegistered = false
        gearCallback = null
        uxManager = null
        propertyManager = null
        car = null
        _snapshot.value = SafetySnapshot.UNKNOWN
    }

    private fun startUxRestrictions(car: Car) {
        val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        uxManager = manager
        if (manager == null) {
            updateUx(UxRestrictionState.UNKNOWN)
            return
        }
        manager.registerListener { restrictions -> updateUx(uxFrom(restrictions)) }
        updateUx(uxFrom(manager.currentCarUxRestrictions))
    }

    @Suppress("DEPRECATION")
    private fun startGear(car: Car) {
        val manager = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        propertyManager = manager
        if (manager == null) {
            updateParking(ParkingState.UNKNOWN)
            return
        }
        val callback = createGearCallback()
        gearCallback = callback
        gearRegistered = manager.registerCallback(
            callback,
            VehiclePropertyIds.GEAR_SELECTION,
            CarPropertyManager.SENSOR_RATE_ONCHANGE,
        )
        if (!gearRegistered) Log.w(TAG, "gear callback was not registered")
        // 알림은 바뀔 때만 오므로 현재값을 한 번 읽는다.
        val current: Int? = try {
            manager.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0)
        } catch (t: Throwable) {
            Log.w(TAG, "gear read failed", t)
            null
        }
        Log.i(TAG, "gear initial value=$current")
        updateParking(parkingFrom(current))
    }

    private fun uxFrom(restrictions: CarUxRestrictions?): UxRestrictionState = when {
        restrictions == null -> UxRestrictionState.UNKNOWN
        restrictions.isRequiresDistractionOptimization -> UxRestrictionState.RESTRICTED
        else -> UxRestrictionState.UNRESTRICTED
    }

    private fun parkingFrom(gear: Int?): ParkingState = when (gear) {
        null, VehicleGear.GEAR_UNKNOWN -> ParkingState.UNKNOWN
        VehicleGear.GEAR_PARK -> ParkingState.PARKED
        else -> ParkingState.NOT_PARKED
    }

    private fun updateUx(state: UxRestrictionState) {
        Log.i(TAG, "ux restriction state=$state")
        _snapshot.update { it.copy(ux = state) }
    }

    private fun updateParking(state: ParkingState) {
        _snapshot.update { it.copy(parking = state) }
    }

    private companion object {
        const val TAG = "CarScienceLab.Safety"
    }
}

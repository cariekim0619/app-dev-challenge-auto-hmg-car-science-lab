package com.carsciencelab.platform

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.carsciencelab.BuildConfig
import com.carsciencelab.R
import com.carsciencelab.policy.CarSafetyMonitor
import com.carsciencelab.policy.SafetyMonitor
import com.carsciencelab.vehicle.ObservationSource
import com.carsciencelab.vehicle.PleosVehicleLink
import com.carsciencelab.vehicle.SdkVehicleGateway
import com.carsciencelab.vehicle.VehicleGateway
import com.carsciencelab.vehicle.VehicleLog

/**
 * 차량 빌드(pleos)의 구성. 실제 Pleos Vehicle SDK와 AAOS Car API를 쓴다.
 * 앱 안에 신호를 조작하는 버튼이 없으며, 시연은 공식 Virtual Console로 한다.
 */
object PlatformModule {

    private const val VEHICLE_SERVICE_PACKAGE = "ai.pleos.playground.service.vehicle"

    private const val VEHICLE_LOG_TAG = "CarScienceLab.Vehicle"

    fun createVehicleGateway(context: Context): VehicleGateway = SdkVehicleGateway(
        link = PleosVehicleLink(context),
        source = ObservationSource.VEHICLE_SDK,
        elapsedMs = { SystemClock.elapsedRealtime() },
        log = VehicleLog { warning, message ->
            if (warning) Log.w(VEHICLE_LOG_TAG, message) else Log.i(VEHICLE_LOG_TAG, message)
        },
    )

    fun createSafetyMonitor(context: Context): SafetyMonitor = CarSafetyMonitor(context)

    /** 심사 자료와 검증 기록에 남길 실행 환경 정보. 차량 식별 정보는 포함하지 않는다. */
    fun environmentInfo(context: Context): List<EnvironmentEntry> = listOf(
        EnvironmentEntry(R.string.env_vehicle_source, context.getString(R.string.env_vehicle_source_pleos)),
        EnvironmentEntry(R.string.env_sdk_version, BuildConfig.VEHICLE_SDK_VERSION),
        EnvironmentEntry(R.string.env_service_version, vehicleServiceVersion(context)),
    )

    @Suppress("DEPRECATION")
    private fun vehicleServiceVersion(context: Context): String {
        val version: String? = try {
            context.packageManager.getPackageInfo(VEHICLE_SERVICE_PACKAGE, 0).versionName
        } catch (_: Exception) {
            null
        }
        return version ?: context.getString(R.string.env_value_unknown)
    }
}

/** 차량 빌드에는 개발용 조작 패널이 없다. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PlatformDevPanel(onSupportChanged: () -> Unit, modifier: Modifier = Modifier) {
}

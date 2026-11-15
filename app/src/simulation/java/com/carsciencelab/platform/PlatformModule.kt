package com.carsciencelab.platform

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carsciencelab.BuildConfig
import com.carsciencelab.R
import com.carsciencelab.policy.DemoSafetyMonitor
import com.carsciencelab.policy.ParkingState
import com.carsciencelab.policy.SafetyMonitor
import com.carsciencelab.policy.UxRestrictionState
import com.carsciencelab.vehicle.CheckFailure
import com.carsciencelab.vehicle.DemoVehicleGateway
import com.carsciencelab.vehicle.SupportResult
import com.carsciencelab.vehicle.TurnSignalCodes
import com.carsciencelab.vehicle.VehicleGateway

/**
 * 개발용 빌드(simulation)의 구성. 모의 차량과 모의 주차 상태를 쓴다.
 * 일반 Android 에뮬레이터에서 화면과 흐름을 확인하기 위한 것으로, release 빌드는 만들지 않는다.
 */
object PlatformModule {

    val demoGateway = DemoVehicleGateway()
    val demoSafety = DemoSafetyMonitor()

    @Suppress("UNUSED_PARAMETER")
    fun createVehicleGateway(context: Context): VehicleGateway = demoGateway

    @Suppress("UNUSED_PARAMETER")
    fun createSafetyMonitor(context: Context): SafetyMonitor = demoSafety

    fun environmentInfo(context: Context): List<EnvironmentEntry> = listOf(
        EnvironmentEntry(R.string.env_vehicle_source, context.getString(R.string.env_vehicle_source_simulation)),
    )
}

/**
 * 모의 입력 패널. 차량 신호와 연결, 주차 상태를 직접 바꿔 본다.
 * 여기서 만든 변화는 실제 차량 연동 결과가 아니다.
 *
 * 앱 화면 아래에 따로 놓이는 띠다. 앱 화면을 덮지 않으므로, 버튼을 누르면서 화면이 어떻게
 * 바뀌는지 그대로 볼 수 있다. 줄이 길면 옆으로 민다. 접으면 한 줄만 남는다.
 */
@Composable
fun PlatformDevPanel(onSupportChanged: () -> Unit, modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    val gateway = PlatformModule.demoGateway
    val safety = PlatformModule.demoSafety
    var open by rememberSaveable { mutableStateOf(true) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { open = !open },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (open) "모의 입력 닫기" else "모의 입력",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = "개발용, 실제 차량 연동이 아님",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (open) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    DevGroup("방향지시등") {
                        DevButton("꺼짐") { gateway.setTurnSignal(TurnSignalCodes.OFF) }
                        DevButton("왼쪽") { gateway.setTurnSignal(TurnSignalCodes.LEFT_ACTIVE) }
                        DevButton("오른쪽") { gateway.setTurnSignal(TurnSignalCodes.RIGHT_ACTIVE) }
                        DevButton("알 수 없는 값") { gateway.sendUnknownValue() }
                        DevButton("같은 값 반복") { gateway.repeatLastValue() }
                    }
                    DevGroup("연결") {
                        DevButton("끊기") { gateway.setLink(false) }
                        DevButton("복구") { gateway.setLink(true) }
                    }
                    DevGroup("지원 확인") {
                        DevButton("지원") {
                            gateway.support = SupportResult.Supported
                            onSupportChanged()
                        }
                        DevButton("미지원") {
                            gateway.support = SupportResult.Unsupported
                            onSupportChanged()
                        }
                        DevButton("권한 오류") {
                            gateway.support = SupportResult.PermissionDenied
                            onSupportChanged()
                        }
                        DevButton("확인 실패") {
                            gateway.support = SupportResult.CheckFailed(CheckFailure.UNKNOWN)
                            onSupportChanged()
                        }
                    }
                    DevGroup("기어") {
                        DevButton("P") { safety.setParking(ParkingState.PARKED) }
                        DevButton("D") { safety.setParking(ParkingState.NOT_PARKED) }
                        DevButton("읽기 실패") { safety.setParking(ParkingState.UNKNOWN) }
                    }
                    DevGroup("UX 제한") {
                        DevButton("해제") { safety.setUxRestriction(UxRestrictionState.UNRESTRICTED) }
                        DevButton("활성") { safety.setUxRestriction(UxRestrictionState.RESTRICTED) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevGroup(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun DevButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

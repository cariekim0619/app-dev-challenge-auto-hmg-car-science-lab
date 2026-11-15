package com.carsciencelab.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carsciencelab.content.AssetLessonRepository
import com.carsciencelab.data.DataStoreLessonRecordStore
import com.carsciencelab.platform.PlatformModule
import com.carsciencelab.vehicle.VehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 화면 회전이나 창 크기 변경에도 실험 진행을 유지하도록 [LabController]를 보관한다.
 * 별도 서버나 로그인 없이, 앱에 포함한 콘텐츠와 기기 저장소만 쓴다.
 */
class LabViewModel(application: Application) : AndroidViewModel(application) {

    private val gateway: VehicleGateway = PlatformModule.createVehicleGateway(application)

    val controller = LabController(
        scope = viewModelScope,
        gateway = gateway,
        safety = PlatformModule.createSafetyMonitor(application),
        lessons = AssetLessonRepository(application.assets),
        store = DataStoreLessonRecordStore(application),
        onStorageError = { error -> Log.w(TAG, "record store failed", error) },
    )

    val state: StateFlow<LabUiState> = controller.state

    init {
        controller.start()
    }

    override fun onCleared() {
        // 구독을 먼저 정리한 다음 SDK를 해제한다.
        controller.dispose()
        CoroutineScope(Dispatchers.Main.immediate).launch { gateway.close() }
        super.onCleared()
    }

    private companion object {
        const val TAG = "CarScienceLab"
    }
}

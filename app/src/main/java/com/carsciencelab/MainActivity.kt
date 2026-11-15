package com.carsciencelab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.carsciencelab.ui.LabViewModel
import com.carsciencelab.ui.screen.LabApp
import com.carsciencelab.ui.theme.CarScienceLabTheme

/**
 * 앱의 유일한 화면. 주차 중에 쓰는 앱이어서 주행 중 사용(DO)을 선언하지 않는다.
 * 화면이 보이지 않게 되면 학습을 멈추고 차량 구독을 해제하도록 알린다.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarScienceLabTheme {
                LabApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.controller.onForegroundChanged(true)
    }

    override fun onStop() {
        // 화면 회전이나 창 크기 변경으로 다시 만들어지는 중이라면 학습을 멈추지 않는다.
        if (!isChangingConfigurations) {
            viewModel.controller.onForegroundChanged(false)
        }
        super.onStop()
    }
}

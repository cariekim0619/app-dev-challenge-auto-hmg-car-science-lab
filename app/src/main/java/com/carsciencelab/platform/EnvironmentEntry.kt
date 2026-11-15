package com.carsciencelab.platform

import androidx.annotation.StringRes

/** 실험 기록 화면에 보여 주는 실행 환경 한 줄. */
data class EnvironmentEntry(@StringRes val label: Int, val value: String)

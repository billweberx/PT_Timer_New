package com.billweberx.pt_timer.data

import androidx.annotation.Keep

@Keep
data class SpinnerOption(val value: String)
@Keep
data class AppState(
    val allSetups: List<TimerSetup>,
    val activeSetupName: String?,
    val bandColorOptions: List<SpinnerOption> = emptyList(),
    val weightOptions: List<SpinnerOption> = emptyList()
)

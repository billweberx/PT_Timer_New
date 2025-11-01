package com.billweberx.pt_timer.data

import androidx.annotation.Keep

@Keep
data class AppState(
    val allSetups: List<TimerSetup>,
    val activeSetupName: String?
)

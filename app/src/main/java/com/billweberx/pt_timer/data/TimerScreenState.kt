package com.billweberx.pt_timer.data

// Data class for the main timer screen's reactive state, moved here to be globally accessible
data class TimerScreenState(
    val status: String = "Ready",
    val remainingTime: Int = 0,
    val progressDisplay: String = "",
    val currentSet: Int = 0,
    val currentRep: Int = 0,
    val activePhase: String = "Exercise",
    val isPaused: Boolean = false
)
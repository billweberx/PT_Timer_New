package com.billweberx.pt_timer.data

import androidx.annotation.Keep

@Keep
data class TimerSetup(
    val name: String,
    val config: SetupConfig, // This correctly matches the "config": {} object in your JSON
    val startRepSoundId: Int,
    val startRestSoundId: Int,
    val startSetRestSoundId: Int,
    val completeSoundId: Int
)

@Keep
data class SetupConfig(
    val moveToTime: String = "1",
    val exerciseTime: String = "2",
    val moveFromTime: String = "1",
    val restTime: String = "2",
    val reps: String = "10",
    val sets: String = "2",
    val setRestTime: String = "15",
    val totalTime: String = "0"
)

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
    val moveToTime: String,
    val exerciseTime: String,
    val moveFromTime: String,
    val restTime: String,
    val reps: String,
    val sets: String,
    val setRestTime: String,
    val totalTime: String
)
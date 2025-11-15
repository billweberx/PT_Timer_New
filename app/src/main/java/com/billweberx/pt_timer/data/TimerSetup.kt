package com.billweberx.pt_timer.data

import androidx.annotation.Keep

@Keep
data class TimerSetup(
    val name: String,
    val config: SetupConfig,
    val getReadySound: String = "none",
    val startRepSound: String = "none",
    val startRestSound: String = "none",
    val startSetRestSound: String = "none",
    val completeSound: String = "none"
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
    val totalTime: String = "0",
    val instructions: String = "",
    val imageResName: String = "none",
    val imageResId: Int? = 0,
    val bandColor: String = "N/A",
    val weightLbs: String = "N/A",
    val timesPerDay: String = "1",
    val timesPerWeek: String = "7",
    val getReadyTime: String = "5"
)
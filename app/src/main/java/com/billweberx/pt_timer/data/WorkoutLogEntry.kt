package com.billweberx.pt_timer.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class WorkoutLogEntry(
    @SerializedName("mode") val mode: String, // "Gym" or "PT"
    @SerializedName("date") val date: String,
    @SerializedName("time") val time: String,
    @SerializedName("exerciseName") val exerciseName: String,

    // Fields from SetupConfig
    @SerializedName("moveToTime") val moveToTime: String,
    @SerializedName("exerciseTime") val exerciseTime: String,
    @SerializedName("moveFromTime") val moveFromTime: String,
    @SerializedName("restTime") val restTime: String,
    @SerializedName("reps") val reps: String,
    @SerializedName("sets") val sets: String,
    @SerializedName("setRestTime") val setRestTime: String,
    @SerializedName("totalTime") val totalTime: String,
    @SerializedName("instructions") val instructions: String,
    @SerializedName("imageResName") val imageResName: String,
    @SerializedName("imageResId") val imageResId: Int?, // Nullable as it might be a user image
    @SerializedName("imageDisplayName") val imageDisplayName: String,
    @SerializedName("bandColor") val bandColor: String,
    @SerializedName("weightLbs") val weightLbs: String,
    @SerializedName("timesPerDay") val timesPerDay: String,
    @SerializedName("timesPerWeek") val timesPerWeek: String,
    @SerializedName("getReadyTime") val getReadyTime: String
)

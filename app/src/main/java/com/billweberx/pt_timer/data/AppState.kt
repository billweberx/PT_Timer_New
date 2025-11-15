package com.billweberx.pt_timer.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class SpinnerOption(
    @SerializedName("value") val value: String
)



@Keep
data class AppState(
    @SerializedName("allSetups") val allSetups: List<TimerSetup> = emptyList(),
    @SerializedName("activeSetupName") val activeSetupName: String? = null,
    // Store as simple strings for robust serialization
    @SerializedName("bandColorOptions") val bandColorOptions: List<String> = emptyList(),
    @SerializedName("weightOptions") val weightOptions: List<String> = emptyList()
)


package com.billweberx.pt_timer.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class BundleOption(
    @SerializedName("name") val name: String,         // Display name for the spinner
    @SerializedName("filePath") val filePath: String, // Full path to the JSON file (e.g., "left_shoulder_basic.json")
    @SerializedName("isFactory") val isFactory: Boolean // True if it's from assets, false if user-created
)

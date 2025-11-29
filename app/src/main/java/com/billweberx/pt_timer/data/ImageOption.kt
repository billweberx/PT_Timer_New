package com.billweberx.pt_timer.data


data class ImageOption(
    val displayName: String, // <-- NEW: This is the name shown to the user
    val resourceId: Int, // 0 for user images, R.drawable.id for factory images
    val storageName: String // <-- NEW: This stores the raw drawable name OR unique filename for user images
)

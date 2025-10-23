// In SoundOption.kt

package com.billweberx.pt_timer

/**
 * Represents a single sound option in the dropdown menus.
 *
 * @property displayName The user-friendly name of the sound (e.g., "Short Beep").
 * @property resourceId The integer ID of the sound in the R.raw folder (e.g., R.raw.short_beep).
 *                      A value of -1 represents "None".
 */
data class SoundOption(
    val displayName: String,
    val resourceId: Int
)

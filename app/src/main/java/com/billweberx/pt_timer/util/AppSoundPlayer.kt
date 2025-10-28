package com.billweberx.pt_timer.util

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A robust, singleton sound player that uses a single, reusable MediaPlayer instance
 * to prevent resource exhaustion. It's thread-safe using a Mutex.
 */
object AppSoundPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex() // To prevent race conditions when two sounds are called at once

    fun playSound(context: Context, soundResourceId: Int) {
        if (soundResourceId == -1) return

        scope.launch {
            mutex.withLock { // Ensures only one sound can be prepared and played at a time
                try {
                    val appContext = context.applicationContext

                    // Create the MediaPlayer only if it doesn't exist
                    if (mediaPlayer == null) {
                        mediaPlayer = MediaPlayer()
                    }

                    // Prepare the player for the new sound
                    mediaPlayer?.apply {
                        reset() // Important: Reset the player to an uninitialized state
                        val afd =
                            appContext.resources.openRawResourceFd(soundResourceId) ?: return@launch
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        prepare() // Prepare the sound synchronously
                        start()     // Play it
                    }

                } catch (e: Exception) {
                    Log.e("AppSoundPlayer", "Error playing sound resId $soundResourceId", e)
                    // If something goes wrong, try to clean up
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }
        }
    }
}

package com.billweberx.pt_timer


import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger

class PtTimerApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(coil.decode.GifDecoder.Factory()) // Register the GIF decoder
            }
            .allowHardware(false) // Apply this compatibility setting globally for GIFs
            .logger(DebugLogger()) // Enable detailed Coil logging for diagnostics
            .build()
    }
}
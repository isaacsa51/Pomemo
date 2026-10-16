package com.serranoie.app.pomemo.alarm

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Blinks the camera flash while something rings, for a phone lying face down or across the room.
 * setTorchMode needs no CAMERA permission. Does nothing without a flash or while the camera is
 * busy, and [stop] always leaves the torch off.
 */
class TorchBlinker(context: Context) {
    private val cm = context.getSystemService(CameraManager::class.java)
    private val id: String? = runCatching {
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    private var job: Job? = null

    /** Half a second on, half off: in step with the countdown screen, well under 3 flashes a second. */
    fun start(scope: CoroutineScope, periodMs: Long = 500) {
        if (id == null || job != null) return
        job = scope.launch {
            var on = false
            while (true) {
                on = !on
                set(on)
                delay(periodMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        set(false)
    }

    private fun set(on: Boolean) {
        id?.let { runCatching { cm.setTorchMode(it, on) } }
    }
}

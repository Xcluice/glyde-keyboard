package com.glyde.keyboard

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Feedback(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** kind: 0 normal, 1 space, 2 delete, 3 enter */
    fun tap(prefs: GlydePrefs, kind: Int) {
        val level = prefs.haptic
        val v = vibrator
        if (level > 0 && v != null && v.hasVibrator()) {
            val ms = when (level) {
                1 -> 8L
                2 -> 14L
                else -> 24L
            }
            val amp = when (level) {
                1 -> 70
                2 -> 140
                else -> 255
            }
            try {
                val effect = if (v.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(ms, amp)
                } else {
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                v.vibrate(effect)
            } catch (e: Exception) {
                // ignore vibrator failures
            }
        }
        if (prefs.sound) {
            val fx = when (kind) {
                1 -> AudioManager.FX_KEYPRESS_SPACEBAR
                2 -> AudioManager.FX_KEYPRESS_DELETE
                3 -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, -1f)
        }
    }
}

package com.xcluice.glyde

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    @Volatile
    private var vib: Vibrator? = null

    private fun vibrator(ctx: Context): Vibrator? {
        vib?.let { return it }
        val app = ctx.applicationContext
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vib = v
        return v
    }

    /** level: 0 off, 1 light, 2 medium, 3 strong. heavy bumps one step for function keys. */
    fun tap(ctx: Context, level: Int, heavy: Boolean = false) {
        if (level <= 0) return
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        val lv = if (heavy) minOf(3, level + 1) else level
        try {
            if (v.hasAmplitudeControl()) {
                val (ms, amp) = when (lv) {
                    1 -> 8L to 60
                    2 -> 12L to 120
                    else -> 18L to 210
                }
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else if (Build.VERSION.SDK_INT >= 29) {
                val id = when (lv) {
                    1 -> VibrationEffect.EFFECT_TICK
                    2 -> VibrationEffect.EFFECT_CLICK
                    else -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
                v.vibrate(VibrationEffect.createPredefined(id))
            } else {
                v.vibrate(VibrationEffect.createOneShot(10L * lv, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
            // vibration is best-effort
        }
    }
}

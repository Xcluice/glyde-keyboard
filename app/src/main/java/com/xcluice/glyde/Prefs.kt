package com.xcluice.glyde

import android.content.Context
import android.content.SharedPreferences

/** Single source of truth for keyboard + settings-app state. */
class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("glyde_prefs", Context.MODE_PRIVATE)

    var themeId: String
        get() = sp.getString(K_THEME, "obsidian") ?: "obsidian"
        set(v) = sp.edit().putString(K_THEME, v).apply()

    /** 0 = off, 1 = light, 2 = medium, 3 = strong */
    var hapticLevel: Int
        get() = sp.getInt(K_HAPTIC, 2)
        set(v) = sp.edit().putInt(K_HAPTIC, v).apply()

    /** ripple | pop | glow | none */
    var keyAnim: String
        get() = sp.getString(K_ANIM, "ripple") ?: "ripple"
        set(v) = sp.edit().putString(K_ANIM, v).apply()

    var keyPreview: Boolean
        get() = sp.getBoolean(K_PREVIEW, true)
        set(v) = sp.edit().putBoolean(K_PREVIEW, v).apply()

    var keySound: Boolean
        get() = sp.getBoolean(K_SOUND, false)
        set(v) = sp.edit().putBoolean(K_SOUND, v).apply()

    var numberRow: Boolean
        get() = sp.getBoolean(K_NUMROW, false)
        set(v) = sp.edit().putBoolean(K_NUMROW, v).apply()

    var autoCap: Boolean
        get() = sp.getBoolean(K_AUTOCAP, true)
        set(v) = sp.edit().putBoolean(K_AUTOCAP, v).apply()

    var doubleSpacePeriod: Boolean
        get() = sp.getBoolean(K_DBLSPACE, true)
        set(v) = sp.edit().putBoolean(K_DBLSPACE, v).apply()

    var heightScale: Float
        get() = sp.getFloat(K_HEIGHT, 1f)
        set(v) = sp.edit().putFloat(K_HEIGHT, v).apply()

    /** key corner radius in dp */
    var keyRadius: Float
        get() = sp.getFloat(K_RADIUS, 10f)
        set(v) = sp.edit().putFloat(K_RADIUS, v).apply()

    fun listen(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unlisten(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val K_THEME = "theme"
        const val K_HAPTIC = "haptic"
        const val K_ANIM = "anim"
        const val K_PREVIEW = "preview"
        const val K_SOUND = "sound"
        const val K_NUMROW = "numrow"
        const val K_AUTOCAP = "autocap"
        const val K_DBLSPACE = "dblspace"
        const val K_HEIGHT = "height"
        const val K_RADIUS = "radius"

        @Volatile
        private var inst: Prefs? = null

        fun get(ctx: Context): Prefs =
            inst ?: synchronized(this) { inst ?: Prefs(ctx).also { inst = it } }
    }
}

package com.glyde.keyboard

import android.content.Context

class GlydePrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("glyde_prefs", Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString("theme", "ocean") ?: "ocean"
        set(v) { sp.edit().putString("theme", v).apply() }

    var heightScale: Float
        get() = sp.getFloat("height", 1f)
        set(v) { sp.edit().putFloat("height", v).apply() }

    var glass: Float
        get() = sp.getFloat("glass", 1f)
        set(v) { sp.edit().putFloat("glass", v).apply() }

    /** 0 off, 1 light, 2 medium, 3 strong */
    var haptic: Int
        get() = sp.getInt("haptic", 2)
        set(v) { sp.edit().putInt("haptic", v).apply() }

    var sound: Boolean
        get() = sp.getBoolean("sound", false)
        set(v) { sp.edit().putBoolean("sound", v).apply() }

    var numberRow: Boolean
        get() = sp.getBoolean("number_row", false)
        set(v) { sp.edit().putBoolean("number_row", v).apply() }

    var keyPreview: Boolean
        get() = sp.getBoolean("key_preview", true)
        set(v) { sp.edit().putBoolean("key_preview", v).apply() }

    var liquid: Boolean
        get() = sp.getBoolean("liquid", true)
        set(v) { sp.edit().putBoolean("liquid", v).apply() }

    var ambient: Boolean
        get() = sp.getBoolean("ambient", false)
        set(v) { sp.edit().putBoolean("ambient", v).apply() }

    /** Everything the keyboard view needs to redraw; haptic and sound are read live. */
    fun signature(): String =
        listOf(theme, heightScale, glass, numberRow, keyPreview, liquid, ambient).joinToString("|")
}

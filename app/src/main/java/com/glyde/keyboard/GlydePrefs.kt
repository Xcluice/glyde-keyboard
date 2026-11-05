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

    var suggest: Boolean
        get() = sp.getBoolean("suggest", true)
        set(v) { sp.edit().putBoolean("suggest", v).apply() }

    var autocorrect: Boolean
        get() = sp.getBoolean("autocorrect", true)
        set(v) { sp.edit().putBoolean("autocorrect", v).apply() }

    var swipe: Boolean
        get() = sp.getBoolean("swipe", true)
        set(v) { sp.edit().putBoolean("swipe", v).apply() }

    var incognito: Boolean
        get() = sp.getBoolean("incognito", false)
        set(v) { sp.edit().putBoolean("incognito", v).apply() }

    var clipboardOn: Boolean
        get() = sp.getBoolean("clipboard_on", true)
        set(v) { sp.edit().putBoolean("clipboard_on", v).apply() }

    /** Comma separated language ids the globe key cycles through. */
    var enabledLangs: List<String>
        get() = (sp.getString("langs", "en") ?: "en").split(',').filter { it.isNotBlank() }.ifEmpty { listOf("en") }
        set(v) { sp.edit().putString("langs", v.joinToString(",")).apply() }

    var lang: String
        get() = sp.getString("lang", "en") ?: "en"
        set(v) { sp.edit().putString("lang", v).apply() }

    /** Everything the keyboard view needs to redraw; haptic and sound are read live. */
    fun signature(): String =
        listOf(
            theme, heightScale, glass, numberRow, keyPreview, liquid, ambient,
            suggest, swipe, incognito, lang, enabledLangs.joinToString(",")
        ).joinToString("|")
}

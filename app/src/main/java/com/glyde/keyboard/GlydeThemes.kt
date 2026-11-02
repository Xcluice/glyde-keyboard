package com.glyde.keyboard

data class GlydeTheme(
    val id: String,
    val name: String,
    val bgTop: Int,
    val bgBottom: Int,
    val orbA: Int,
    val orbB: Int,
    val orbC: Int,
    val accent: Int,
    val ink: Int,
    val dark: Boolean
)

object GlydeThemes {
    val all: List<GlydeTheme> = listOf(
        GlydeTheme(
            "ocean", "Ocean",
            0xFF06202B.toInt(), 0xFF0B4F6C.toInt(),
            0xFF00E0C6.toInt(), 0xFF1E90FF.toInt(), 0xFF00B4D8.toInt(),
            0xFF2EE6D6.toInt(), 0xFFFFFFFF.toInt(), true
        ),
        GlydeTheme(
            "sunset", "Sunset",
            0xFF2B0F0A.toInt(), 0xFF7A2E12.toInt(),
            0xFFFF7A18.toInt(), 0xFFFFB347.toInt(), 0xFFFF4E50.toInt(),
            0xFFFF9A3C.toInt(), 0xFFFFFFFF.toInt(), true
        ),
        GlydeTheme(
            "aurora", "Aurora",
            0xFF04140F.toInt(), 0xFF0B3D2E.toInt(),
            0xFF39FF9E.toInt(), 0xFF00D4FF.toInt(), 0xFFA3FF5C.toInt(),
            0xFF4DFFB0.toInt(), 0xFFFFFFFF.toInt(), true
        ),
        GlydeTheme(
            "coral", "Coral",
            0xFF2A0D14.toInt(), 0xFF6B1F2E.toInt(),
            0xFFFF5E78.toInt(), 0xFFFFA07A.toInt(), 0xFFFFD166.toInt(),
            0xFFFF6B81.toInt(), 0xFFFFFFFF.toInt(), true
        ),
        GlydeTheme(
            "mango", "Mango",
            0xFF241503.toInt(), 0xFF6B4300.toInt(),
            0xFFFFC300.toInt(), 0xFFFF8F00.toInt(), 0xFFFFE066.toInt(),
            0xFFFFC53D.toInt(), 0xFFFFFFFF.toInt(), true
        ),
        GlydeTheme(
            "daylight", "Daylight",
            0xFFEAF4FB.toInt(), 0xFFC9E3F5.toInt(),
            0xFF7CC8FF.toInt(), 0xFFB5F0E8.toInt(), 0xFFFFFFFF.toInt(),
            0xFF1E88E5.toInt(), 0xFF0E2A3B.toInt(), false
        ),
        GlydeTheme(
            "midnight", "Midnight",
            0xFF08090D.toInt(), 0xFF171A21.toInt(),
            0xFF3A86FF.toInt(), 0xFF00F5D4.toInt(), 0xFFFFBE0B.toInt(),
            0xFF4C9AFF.toInt(), 0xFFFFFFFF.toInt(), true
        )
    )

    fun byId(id: String): GlydeTheme = all.firstOrNull { it.id == id } ?: all[0]
}

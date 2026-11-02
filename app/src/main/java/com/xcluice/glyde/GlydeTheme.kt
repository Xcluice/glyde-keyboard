package com.xcluice.glyde

data class GlydeTheme(
    val id: String,
    val name: String,
    val glass: Boolean,
    val dark: Boolean,
    val bgTop: Int,
    val bgBottom: Int,
    val blobA: Int,
    val blobB: Int,
    val keyTop: Int,
    val keyBottom: Int,
    val funcTop: Int,
    val funcBottom: Int,
    val borderTop: Int,
    val borderBottom: Int,
    val pressed: Int,
    val accent: Int,
    val accentText: Int,
    val label: Int,
    val labelDim: Int,
    val ripple: Int,
    val bubble: Int,
)

object Themes {
    private fun c(v: Long): Int = v.toInt()

    val all: List<GlydeTheme> = listOf(
        // ---------- Glass ----------
        GlydeTheme(
            id = "obsidian", name = "Obsidian", glass = true, dark = true,
            bgTop = c(0xFF0E1020), bgBottom = c(0xFF1B2140),
            blobA = c(0x66586CFF), blobB = c(0x55B44BFF),
            keyTop = c(0x38FFFFFF), keyBottom = c(0x14FFFFFF),
            funcTop = c(0x22FFFFFF), funcBottom = c(0x0AFFFFFF),
            borderTop = c(0x70FFFFFF), borderBottom = c(0x14FFFFFF),
            pressed = c(0x55FFFFFF), accent = c(0xFF7C8CFF), accentText = c(0xFFFFFFFF),
            label = c(0xFFF2F4FF), labelDim = c(0x99F2F4FF),
            ripple = c(0xFFAAB6FF), bubble = c(0xFF2A3052),
        ),
        GlydeTheme(
            id = "frost", name = "Frost", glass = true, dark = false,
            bgTop = c(0xFFEAF1FC), bgBottom = c(0xFFD3E2F6),
            blobA = c(0x88A9C9FF), blobB = c(0x88FFC9E6),
            keyTop = c(0xEEFFFFFF), keyBottom = c(0xAAFFFFFF),
            funcTop = c(0x88FFFFFF), funcBottom = c(0x55FFFFFF),
            borderTop = c(0xFFFFFFFF), borderBottom = c(0x558FAEDB),
            pressed = c(0x442F6BFF), accent = c(0xFF2F6BFF), accentText = c(0xFFFFFFFF),
            label = c(0xFF1B2540), labelDim = c(0x99283556),
            ripple = c(0xFF2F6BFF), bubble = c(0xFFFFFFFF),
        ),
        GlydeTheme(
            id = "aurora", name = "Aurora", glass = true, dark = true,
            bgTop = c(0xFF08182A), bgBottom = c(0xFF241047),
            blobA = c(0x9900E5C3), blobB = c(0x99B14BFF),
            keyTop = c(0x38FFFFFF), keyBottom = c(0x14FFFFFF),
            funcTop = c(0x22FFFFFF), funcBottom = c(0x0AFFFFFF),
            borderTop = c(0x70FFFFFF), borderBottom = c(0x14FFFFFF),
            pressed = c(0x55FFFFFF), accent = c(0xFF3DF5D0), accentText = c(0xFF04201C),
            label = c(0xFFEFFFFB), labelDim = c(0x99EFFFFB),
            ripple = c(0xFF7CFFE6), bubble = c(0xFF16304A),
        ),
        // ---------- Gradient ----------
        GlydeTheme(
            id = "sunset", name = "Sunset", glass = false, dark = true,
            bgTop = c(0xFFFF8A5B), bgBottom = c(0xFF8E3A9D),
            blobA = c(0x66FFD27F), blobB = c(0x55FF4D8D),
            keyTop = c(0x40FFFFFF), keyBottom = c(0x22FFFFFF),
            funcTop = c(0x26FFFFFF), funcBottom = c(0x12FFFFFF),
            borderTop = c(0x55FFFFFF), borderBottom = c(0x14FFFFFF),
            pressed = c(0x55FFFFFF), accent = c(0xFFFFFFFF), accentText = c(0xFF8E3A9D),
            label = c(0xFFFFFFFF), labelDim = c(0xB3FFFFFF),
            ripple = c(0xFFFFFFFF), bubble = c(0xFFA84C9A),
        ),
        GlydeTheme(
            id = "ocean", name = "Ocean", glass = false, dark = true,
            bgTop = c(0xFF36D1DC), bgBottom = c(0xFF2B4BA6),
            blobA = c(0x55B8F7FF), blobB = c(0x553A6BFF),
            keyTop = c(0x40FFFFFF), keyBottom = c(0x22FFFFFF),
            funcTop = c(0x26FFFFFF), funcBottom = c(0x12FFFFFF),
            borderTop = c(0x55FFFFFF), borderBottom = c(0x14FFFFFF),
            pressed = c(0x55FFFFFF), accent = c(0xFFFFFFFF), accentText = c(0xFF2B4BA6),
            label = c(0xFFFFFFFF), labelDim = c(0xB3FFFFFF),
            ripple = c(0xFFFFFFFF), bubble = c(0xFF2A6CC0),
        ),
        GlydeTheme(
            id = "midnight", name = "Midnight", glass = false, dark = true,
            bgTop = c(0xFF141E30), bgBottom = c(0xFF3B1F6B),
            blobA = c(0x66B388FF), blobB = c(0x5547C1FF),
            keyTop = c(0x30FFFFFF), keyBottom = c(0x16FFFFFF),
            funcTop = c(0x1CFFFFFF), funcBottom = c(0x0CFFFFFF),
            borderTop = c(0x4DFFFFFF), borderBottom = c(0x12FFFFFF),
            pressed = c(0x55FFFFFF), accent = c(0xFFB388FF), accentText = c(0xFF1A0B36),
            label = c(0xFFF3EEFF), labelDim = c(0x99F3EEFF),
            ripple = c(0xFFC9A8FF), bubble = c(0xFF2B2358),
        ),
        GlydeTheme(
            id = "mint", name = "Mint", glass = false, dark = false,
            bgTop = c(0xFFE3F8EE), bgBottom = c(0xFFB5E3CE),
            blobA = c(0x88FFFFFF), blobB = c(0x667EE0B5),
            keyTop = c(0xF2FFFFFF), keyBottom = c(0xBBFFFFFF),
            funcTop = c(0x99FFFFFF), funcBottom = c(0x66FFFFFF),
            borderTop = c(0xFFFFFFFF), borderBottom = c(0x555FA88C),
            pressed = c(0x3314A37F), accent = c(0xFF14A37F), accentText = c(0xFFFFFFFF),
            label = c(0xFF0E3B2E), labelDim = c(0x990E3B2E),
            ripple = c(0xFF14A37F), bubble = c(0xFFFFFFFF),
        ),
    )

    fun byId(id: String): GlydeTheme = all.firstOrNull { it.id == id } ?: all[0]
}

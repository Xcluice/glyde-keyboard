package com.glyde.keyboard

/**
 * One typing mode. Roman modes use the English layout and convert what you type into
 * Devanagari or Urdu script through suggestions; native modes have their own keys.
 */
data class Lang(
    val id: String,
    val name: String,
    val label: String,
    val latin: Boolean,
    val translit: String?,
    val base: List<String>,
    val shift: List<String>,
    val alts: Map<Char, String>,
    val comma: String,
    val period: String
) {
    val rtl: Boolean get() = id == "ur" || id == "ks"
}

object Languages {

    private val qwerty = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    private val hindiBase = listOf("ौैाीूबहगदज", "ोे्िुपरकतच", "ंमनवलसय")
    private val hindiShift = listOf("औऐआईऊभङघधझ", "ओएअइउफऱखथछ", "ँणऩऴळशष")
    private val hindiAlts = mapOf(
        'क' to "टठ", 'प' to "डढ", 'म' to "ञ", 'र' to "ऱऋॠ", 'ं' to "ःँ",
        'ा' to "ॉऑ", 'ु' to "ृॄ", 'े' to "ॆॅ", 'ज' to "़"
    )

    private val urduBase = listOf("قوعرتےءیہپ", "اسدفگحجکل", "زشچطبنم")
    private val urduShift = listOf("ضؤئڑٹۓآۃھ؟", "أصڈۂغںثخ؛", "ذژظ٪٫٬؍")
    private val urduAlts = mapOf(
        'ع' to "َُِّْٰ",
        'ا' to "آأإٰ",
        'و' to "ؤۆ",
        'ی' to "ئ",
        'ہ' to "ۃۂھ",
        'ے' to "ۓ"
    )

    private val kashmiriShift = listOf("ضؤئڑٹێٲۍۄؠ", "أصڈۂغںثخ؛", "ذژظ٪٫٬؍")
    private val kashmiriAlts = mapOf(
        'ع' to "َِْٕٗٔٛٚ",
        'ا' to "ٲآأٳ",
        'و' to "ۄۆؤ",
        'ی' to "ێۍؠئ",
        'ہ' to "ۃۂھ",
        'ے' to "ۓ"
    )

    val all: List<Lang> = listOf(
        Lang("en", "English", "EN", true, null, qwerty, emptyList(), emptyMap(), ",", "."),
        Lang("hi_roman", "Hindi (Roman)", "हि/A", true, "hi", qwerty, emptyList(), emptyMap(), ",", "."),
        Lang("hi", "Hindi (InScript)", "हि", false, null, hindiBase, hindiShift, hindiAlts, ",", "।"),
        Lang("ur_roman", "Urdu (Roman)", "ار/A", true, "ur", qwerty, emptyList(), emptyMap(), ",", "."),
        Lang("ur", "Urdu", "ار", false, null, urduBase, urduShift, urduAlts, "،", "۔"),
        Lang("ks", "Kashmiri", "کٲ", false, null, urduBase, kashmiriShift, kashmiriAlts, "،", "۔")
    )

    fun byId(id: String): Lang = all.firstOrNull { it.id == id } ?: all[0]
}

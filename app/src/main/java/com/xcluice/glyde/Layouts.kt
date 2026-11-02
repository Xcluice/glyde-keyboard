package com.xcluice.glyde

enum class KeyType { CHAR, SHIFT, DELETE, SYMBOLS, SYMBOLS_ALT, SPACE, ENTER, SPACER }

enum class Page { LETTERS, SYMBOLS1, SYMBOLS2 }

data class KeyDef(
    val type: KeyType,
    val label: String = "",
    val weight: Float = 1f,
    val alts: List<String> = emptyList(),
)

object Layouts {
    private val accents: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ä", "ã", "å", "ā"),
        "c" to listOf("ç", "ć", "č"),
        "e" to listOf("è", "é", "ê", "ë", "ē"),
        "i" to listOf("ì", "í", "î", "ï", "ī"),
        "l" to listOf("ł"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ", "ō"),
        "s" to listOf("ß", "ś", "š"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
    )

    private fun chars(s: String, digitAlt: Boolean = false): List<KeyDef> =
        s.mapIndexed { i, ch ->
            val l = ch.toString()
            val alts = ArrayList<String>()
            if (digitAlt) alts.add(((i + 1) % 10).toString())
            accents[l]?.let { alts.addAll(it) }
            KeyDef(KeyType.CHAR, l, 1f, alts)
        }

    private fun plain(s: String): List<KeyDef> = s.map { KeyDef(KeyType.CHAR, it.toString()) }

    private fun spacer(w: Float) = KeyDef(KeyType.SPACER, weight = w)

    private fun fn(type: KeyType, label: String = "", w: Float = 1.5f) = KeyDef(type, label, w)

    private fun bottom(toggleLabel: String): List<KeyDef> = listOf(
        fn(KeyType.SYMBOLS, toggleLabel, 1.5f),
        KeyDef(KeyType.CHAR, ","),
        KeyDef(KeyType.SPACE, "", 5f),
        KeyDef(KeyType.CHAR, ".", 1f, listOf("…", "!", "?", ":", ";", "'", "\"")),
        fn(KeyType.ENTER, "", 1.5f),
    )

    fun rowsFor(page: Page, numberRow: Boolean): List<List<KeyDef>> = when (page) {
        Page.LETTERS -> buildList {
            if (numberRow) add(plain("1234567890"))
            add(chars("qwertyuiop", digitAlt = !numberRow))
            add(listOf(spacer(0.5f)) + chars("asdfghjkl") + listOf(spacer(0.5f)))
            add(listOf(fn(KeyType.SHIFT)) + chars("zxcvbnm") + listOf(fn(KeyType.DELETE)))
            add(bottom("?123"))
        }
        Page.SYMBOLS1 -> listOf(
            plain("1234567890"),
            plain("@#\$%&-+()/"),
            listOf(fn(KeyType.SYMBOLS_ALT, "=\\<")) + plain("*\"':;!?") + listOf(fn(KeyType.DELETE)),
            bottom("ABC"),
        )
        Page.SYMBOLS2 -> listOf(
            plain("~`|•√π÷×¶∆"),
            plain("£¢€¥^°={}\\"),
            listOf(fn(KeyType.SYMBOLS_ALT, "?123")) + plain("©®™[]<>") + listOf(fn(KeyType.DELETE)),
            bottom("ABC"),
        )
    }
}

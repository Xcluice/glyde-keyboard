package com.glyde.keyboard

import android.content.Context

/**
 * An in-memory, alphabetically sorted word list with frequencies.
 * Loads from an asset of "word<TAB>frequency" lines.
 */
class Lexicon {

    @Volatile
    var ready = false
        private set

    private var words: Array<String> = emptyArray()
    private var freqs: IntArray = IntArray(0)

    fun load(context: Context, asset: String) {
        val list = ArrayList<Pair<String, Int>>(45000)
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val f = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                list.add(line.substring(0, tab) to f)
            }
        }
        list.sortBy { it.first }
        words = Array(list.size) { list[it].first }
        freqs = IntArray(list.size) { list[it].second }
        ready = true
    }

    val size: Int get() = words.size

    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun indexOf(word: String): Int {
        if (!ready) return -1
        val i = lowerBound(word)
        return if (i < words.size && words[i] == word) i else -1
    }

    fun contains(word: String): Boolean = indexOf(word) >= 0

    fun freq(word: String): Int {
        val i = indexOf(word)
        return if (i >= 0) freqs[i] else 0
    }

    /** Calls [f] for every word whose first letter is [c]. */
    fun forEachStartingWith(c: Char, f: (String, Int) -> Unit) {
        if (!ready) return
        var i = lowerBound(c.toString())
        while (i < words.size && words[i][0] == c) {
            f(words[i], freqs[i])
            i++
        }
    }

    /** Top completions of [prefix] by frequency (the prefix itself is included if it is a word). */
    fun complete(prefix: String, limit: Int, boost: (String) -> Int = { 0 }): List<String> {
        if (!ready || prefix.isEmpty()) return emptyList()
        val best = ArrayList<Pair<String, Long>>(limit + 1)
        var i = lowerBound(prefix)
        while (i < words.size && words[i].startsWith(prefix)) {
            val w = words[i]
            val score = freqs[i].toLong() + boost(w).toLong()
            if (best.size < limit || score > best.last().second) {
                var pos = best.size
                while (pos > 0 && best[pos - 1].second < score) pos--
                best.add(pos, w to score)
                if (best.size > limit) best.removeAt(best.size - 1)
            }
            i++
        }
        return best.map { it.first }
    }

    /** Best dictionary word within edit distance 1 (substitution, insertion, deletion, swap). */
    fun correct(word: String, boost: (String) -> Int = { 0 }): String? {
        if (!ready || word.length < 3) return null
        var bestWord: String? = null
        var bestScore = -1L
        for (i in words.indices) {
            val w = words[i]
            val dl = w.length - word.length
            if (dl < -1 || dl > 1) continue
            if (!withinOne(word, w)) continue
            val score = freqs[i].toLong() + boost(w).toLong()
            if (score > bestScore) {
                bestScore = score
                bestWord = w
            }
        }
        return bestWord
    }

    /** Suggestions for a word the user typed that is not necessarily in the dictionary. */
    fun nearby(word: String, limit: Int, boost: (String) -> Int = { 0 }): List<String> {
        if (!ready || word.length < 2) return emptyList()
        val found = ArrayList<Pair<String, Long>>()
        for (i in words.indices) {
            val w = words[i]
            val dl = w.length - word.length
            if (dl < -1 || dl > 1) continue
            if (w == word || !withinOne(word, w)) continue
            found.add(w to (freqs[i].toLong() + boost(w).toLong()))
        }
        found.sortByDescending { it.second }
        return found.take(limit).map { it.first }
    }

    companion object {
        /** True when [a] and [b] differ by at most one edit (a swap of neighbours counts as one). */
        fun withinOne(a: String, b: String): Boolean {
            if (a == b) return true
            val la = a.length
            val lb = b.length
            if (la - lb > 1 || lb - la > 1) return false
            var i = 0
            while (i < la && i < lb && a[i] == b[i]) i++
            if (la == lb) {
                if (a.regionMatches(i + 1, b, i + 1, la - i - 1)) return true
                return i + 1 < la &&
                    a[i] == b[i + 1] && a[i + 1] == b[i] &&
                    a.regionMatches(i + 2, b, i + 2, la - i - 2)
            }
            return if (la > lb) a.regionMatches(i + 1, b, i, lb - i)
            else b.regionMatches(i + 1, a, i, la - i)
        }
    }
}

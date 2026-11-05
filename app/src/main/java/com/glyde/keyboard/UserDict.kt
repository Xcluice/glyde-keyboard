package com.glyde.keyboard

import android.content.Context
import java.io.File

/** Words and word pairs the user types, kept on the device only. */
class UserDict(context: Context) {

    private val wordsFile = File(context.filesDir, "user_words.txt")
    private val pairsFile = File(context.filesDir, "user_pairs.txt")
    private val counts = HashMap<String, Int>()
    private val pairs = HashMap<String, HashMap<String, Int>>()
    private var dirty = 0

    init {
        load()
    }

    private fun load() {
        try {
            if (wordsFile.exists()) {
                wordsFile.forEachLine { line ->
                    val t = line.indexOf('\t')
                    if (t > 0) {
                        val n = line.substring(t + 1).toIntOrNull() ?: return@forEachLine
                        counts[line.substring(0, t)] = n
                    }
                }
            }
            if (pairsFile.exists()) {
                pairsFile.forEachLine { line ->
                    val p = line.split('\t')
                    if (p.size == 3) {
                        val n = p[2].toIntOrNull() ?: return@forEachLine
                        pairs.getOrPut(p[0]) { HashMap() }[p[1]] = n
                    }
                }
            }
        } catch (e: Exception) {
            counts.clear()
            pairs.clear()
        }
    }

    fun count(word: String): Int = counts[word] ?: 0

    /** Score added on top of dictionary frequency, so words you use float to the top. */
    fun boost(word: String): Int {
        val c = counts[word] ?: return 0
        return minOf(c, 30) * 3_000_000
    }

    fun learn(word: String, previous: String?) {
        if (word.length < 2 || word.length > 24) return
        counts[word] = (counts[word] ?: 0) + 1
        if (!previous.isNullOrEmpty()) {
            val m = pairs.getOrPut(previous) { HashMap() }
            m[word] = (m[word] ?: 0) + 1
        }
        dirty++
        if (dirty >= 15) save()
    }

    /** Words you typed that start with [prefix], most used first. */
    fun complete(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        return counts.entries
            .filter { it.key.startsWith(prefix) && it.value >= 2 }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    fun next(previous: String, limit: Int): List<String> {
        val m = pairs[previous] ?: return emptyList()
        return m.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    fun clear() {
        counts.clear()
        pairs.clear()
        wordsFile.delete()
        pairsFile.delete()
        dirty = 0
    }

    fun save() {
        dirty = 0
        try {
            val top = counts.entries.sortedByDescending { it.value }.take(6000)
            wordsFile.bufferedWriter().use { w ->
                for (e in top) w.write("${e.key}\t${e.value}\n")
            }
            val flat = ArrayList<Triple<String, String, Int>>()
            for ((a, m) in pairs) for ((b, n) in m) flat.add(Triple(a, b, n))
            flat.sortByDescending { it.third }
            pairsFile.bufferedWriter().use { w ->
                for (t in flat.take(6000)) w.write("${t.first}\t${t.second}\t${t.third}\n")
            }
        } catch (e: Exception) {
            // best effort
        }
    }
}

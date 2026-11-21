// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.glide

import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import java.text.Normalizer
import java.util.WeakHashMap
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Open-source swipe (glide) typing decoder.
 *
 * The finger path is compared with the ideal path of every candidate word (a polyline through the
 * centers of the word's keys). Candidates are narrowed by first and last letter, then ranked by
 * shape distance, key coverage and word frequency.
 */
object GlideDecoder {
    private const val RESAMPLE_POINTS = 32
    private const val MAX_RESULTS = 12
    private const val MAX_WORD_LENGTH = 30

    private class WordIndex(
        val words: Array<String>,
        val probabilities: IntArray,
        val byEnds: HashMap<Long, IntArray>
    )

    private class KeyLayout(val positions: HashMap<Int, FloatArray>, val keyWidth: Float)

    private class Candidate(val word: String, val cost: Float)

    private val indexes = WeakHashMap<Dictionary, WordIndex>()
    private val building = HashSet<Dictionary>()

    private fun endsKey(first: Int, last: Int): Long = (first.toLong() shl 32) or (last.toLong() and 0xffffffffL)

    /** lower-case code point without diacritics, so accented letters map to the key of their base letter */
    private fun baseCode(codePoint: Int): Int {
        val lower = Character.toLowerCase(codePoint)
        if (lower < 0x80) return lower
        val decomposed = Normalizer.normalize(String(Character.toChars(lower)), Normalizer.Form.NFD)
        return if (decomposed.isEmpty()) lower else decomposed.codePointAt(0)
    }

    /** Starts building the word index for [dict] in the background, if not done yet. */
    fun prepare(dict: Dictionary) {
        getIndex(dict)
    }

    private fun getIndex(dict: Dictionary): WordIndex? {
        synchronized(indexes) {
            val existing = indexes[dict]
            if (existing != null) return existing
            if (!building.add(dict)) return null
        }
        val thread = Thread({
            val built = try { buildIndex(dict) } catch (_: Throwable) { null }
            synchronized(indexes) {
                building.remove(dict)
                if (built != null) indexes[dict] = built
            }
        }, "glide-index")
        thread.isDaemon = true
        thread.priority = Thread.MIN_PRIORITY
        thread.start()
        return null
    }

    private fun buildIndex(dict: Dictionary): WordIndex {
        val words = ArrayList<String>()
        val probabilities = ArrayList<Int>()
        dict.forEachWord { word, probability ->
            if (word.length in 2..MAX_WORD_LENGTH && probability >= 0) {
                val first = word.codePointAt(0)
                val last = word.codePointBefore(word.length)
                if (Character.isLetter(first) && Character.isLetter(last)) {
                    words.add(word)
                    probabilities.add(probability)
                }
            }
        }
        val groups = HashMap<Long, ArrayList<Int>>()
        for (i in words.indices) {
            val word = words[i]
            val key = endsKey(baseCode(word.codePointAt(0)), baseCode(word.codePointBefore(word.length)))
            groups.getOrPut(key) { ArrayList() }.add(i)
        }
        val byEnds = HashMap<Long, IntArray>(groups.size * 2)
        for ((key, ids) in groups) byEnds[key] = ids.toIntArray()
        return WordIndex(words.toTypedArray(), probabilities.toIntArray(), byEnds)
    }

    private fun layoutOf(keyboard: Keyboard): KeyLayout? {
        val positions = HashMap<Int, FloatArray>()
        var widthSum = 0f
        for (key in keyboard.sortedKeys) {
            val code = key.code
            if (code <= 0 || !Character.isLetter(code)) continue
            val base = baseCode(code)
            if (positions.containsKey(base)) continue
            positions[base] = floatArrayOf(key.x + key.width / 2f, key.y + key.height / 2f)
            widthSum += key.width
        }
        if (positions.size < 6) return null
        return KeyLayout(positions, widthSum / positions.size)
    }

    private fun pathLength(xs: FloatArray, ys: FloatArray, count: Int): Float {
        var total = 0f
        for (i in 1 until count) total += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        return total
    }

    /** Resamples a polyline to [count] points spaced equally along its length. Returns x0,y0,x1,y1,... or null for a degenerate path. */
    private fun resample(px: FloatArray, py: FloatArray, size: Int, count: Int): FloatArray? {
        val total = pathLength(px, py, size)
        if (total < 1e-3f) return null
        val out = FloatArray(count * 2)
        val step = total / (count - 1)
        out[0] = px[0]
        out[1] = py[0]
        var produced = 1
        var prevX = px[0]
        var prevY = py[0]
        var carried = 0f
        var i = 1
        while (i < size && produced < count - 1) {
            val dx = px[i] - prevX
            val dy = py[i] - prevY
            val d = hypot(dx, dy)
            if (d <= 0f) {
                i++
                continue
            }
            if (carried + d >= step) {
                val t = (step - carried) / d
                prevX += t * dx
                prevY += t * dy
                out[2 * produced] = prevX
                out[2 * produced + 1] = prevY
                produced++
                carried = 0f
            } else {
                carried += d
                prevX = px[i]
                prevY = py[i]
                i++
            }
        }
        for (k in produced until count) {
            out[2 * k] = px[size - 1]
            out[2 * k + 1] = py[size - 1]
        }
        return out
    }

    private fun nearestCodes(x: Float, y: Float, layout: KeyLayout, radius: Float, maxCount: Int): List<Int> {
        val found = ArrayList<Pair<Float, Int>>()
        var bestDistance = Float.MAX_VALUE
        var bestCode = -1
        for ((code, p) in layout.positions) {
            val d = hypot(p[0] - x, p[1] - y)
            if (d < bestDistance) {
                bestDistance = d
                bestCode = code
            }
            if (d <= radius) found.add(d to code)
        }
        if (found.isEmpty()) return if (bestCode >= 0) listOf(bestCode) else emptyList()
        found.sortBy { it.first }
        return found.take(maxCount).map { it.second }
    }

    /** Returns suggestions for the current swipe, or null if the word index is still being built or the input is unusable. */
    fun suggest(dict: Dictionary, composedData: ComposedData, keyboard: Keyboard, weightForLocale: Float): ArrayList<SuggestedWordInfo>? {
        val index = getIndex(dict) ?: return null
        val layout = layoutOf(keyboard) ?: return null
        val pointers = composedData.mInputPointers
        val size = pointers.pointerSize
        if (size < 2) return null
        val xInts = pointers.xCoordinates
        val yInts = pointers.yCoordinates
        val gx = FloatArray(size) { xInts[it].toFloat() }
        val gy = FloatArray(size) { yInts[it].toFloat() }
        val keyWidth = layout.keyWidth
        val gestureLength = pathLength(gx, gy, size)
        if (gestureLength < 0.7f * keyWidth) return null
        val gesture = resample(gx, gy, size, RESAMPLE_POINTS) ?: return null

        val starts = nearestCodes(gx[0], gy[0], layout, 1.6f * keyWidth, 4)
        val ends = nearestCodes(gx[size - 1], gy[size - 1], layout, 1.6f * keyWidth, 4)

        val idealX = FloatArray(MAX_WORD_LENGTH + 2)
        val idealY = FloatArray(MAX_WORD_LENGTH + 2)
        val candidates = ArrayList<Candidate>()
        for (s in starts) for (e in ends) {
            val ids = index.byEnds[endsKey(s, e)] ?: continue
            for (id in ids) {
                val word = index.words[id]
                // ideal path through the key centers, skipping letters without a key and repeated letters
                var count = 0
                var lastCode = -1
                var offset = 0
                while (offset < word.length && count < idealX.size) {
                    val cp = word.codePointAt(offset)
                    offset += Character.charCount(cp)
                    val code = baseCode(cp)
                    if (code == lastCode) continue
                    val p = layout.positions[code] ?: continue
                    idealX[count] = p[0]
                    idealY[count] = p[1]
                    count++
                    lastCode = code
                }
                if (count < 2) continue
                val idealLength = pathLength(idealX, idealY, count)
                val ratio = idealLength / gestureLength
                if (ratio < 0.6f || ratio > 1.8f) continue
                val ideal = resample(idealX, idealY, count, RESAMPLE_POINTS) ?: continue

                var shapeSum = 0f
                for (k in 0 until RESAMPLE_POINTS) {
                    shapeSum += hypot(ideal[2 * k] - gesture[2 * k], ideal[2 * k + 1] - gesture[2 * k + 1])
                }
                val shape = shapeSum / RESAMPLE_POINTS / keyWidth
                if (shape > 1.6f) continue

                // every letter of the word should be close to the swipe path
                var coverageSum = 0f
                for (k in 0 until count) {
                    var best = Float.MAX_VALUE
                    for (j in 0 until RESAMPLE_POINTS) {
                        val dx = idealX[k] - gesture[2 * j]
                        val dy = idealY[k] - gesture[2 * j + 1]
                        val d2 = dx * dx + dy * dy
                        if (d2 < best) best = d2
                    }
                    coverageSum += sqrt(best)
                }
                val coverage = coverageSum / count / keyWidth
                if (coverage > 1.5f) continue

                val cost = shape + 0.8f * coverage - 0.07f * ln(1f + index.probabilities[id])
                candidates.add(Candidate(word, cost))
            }
        }
        if (candidates.isEmpty()) return null
        candidates.sortBy { it.cost }
        val result = ArrayList<SuggestedWordInfo>()
        for (c in candidates.take(MAX_RESULTS)) {
            val score = (1_000_000f * exp(-c.cost) * weightForLocale).toInt()
            result.add(
                SuggestedWordInfo(
                    c.word, "", score, SuggestedWordInfo.KIND_CORRECTION, dict,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE
                )
            )
        }
        return result
    }
}

package com.glyde.keyboard

import kotlin.math.hypot
import kotlin.math.ln

/** Turns a finger path across the letter keys into likely words. */
class SwipeEngine(private val lexicon: Lexicon) {

    private val n = 40

    /**
     * @param xy the raw path, flattened as x0,y0,x1,y1...
     * @param centers key centre for every letter on the keyboard, as [x, y]
     * @param keyW width of one key in pixels
     */
    fun decode(
        xy: FloatArray,
        centers: Map<Char, FloatArray>,
        keyW: Float,
        boost: (String) -> Int
    ): List<String> {
        if (!lexicon.ready || xy.size < 6) return emptyList()
        val gesture = resample(xy, n)
        val gLen = pathLength(xy)
        if (gLen < keyW * 1.5f) return emptyList()

        val sx = xy[0]
        val sy = xy[1]
        val ex = xy[xy.size - 2]
        val ey = xy[xy.size - 1]
        val near = keyW * 1.6f

        val startLetters = centers.filter { hypot(it.value[0] - sx, it.value[1] - sy) < near }.keys
        val endLetters = centers.filter { hypot(it.value[0] - ex, it.value[1] - ey) < near }.keys
        if (startLetters.isEmpty() || endLetters.isEmpty()) return emptyList()

        val top = ArrayList<Pair<String, Float>>()
        val ideal = FloatArray(64 * 2)

        for (c in startLetters) {
            lexicon.forEachStartingWith(c) { w, f ->
                if (w.length < 2 || w.length > 18) return@forEachStartingWith
                if (w[w.length - 1] !in endLetters) return@forEachStartingWith

                // ideal path through the key centres (repeated letters collapse)
                var count = 0
                var prev = '\u0000'
                var valid = true
                for (ch in w) {
                    if (ch == prev) continue
                    val p = centers[ch]
                    if (p == null) {
                        valid = false
                        break
                    }
                    ideal[count * 2] = p[0]
                    ideal[count * 2 + 1] = p[1]
                    count++
                    prev = ch
                }
                if (!valid || count < 2) return@forEachStartingWith

                val path = ideal.copyOf(count * 2)
                val iLen = pathLength(path)
                val ratio = iLen / gLen
                if (ratio < 0.45f || ratio > 2.2f) return@forEachStartingWith

                val shape = shapeDistance(gesture, resample(path, n)) / keyW
                val score = shape - 0.10f * ln((f + boost(w)).toFloat() + 1f)
                if (shape > 2.4f) return@forEachStartingWith
                insert(top, w, score)
            }
        }
        return top.map { it.first }
    }

    private fun insert(top: ArrayList<Pair<String, Float>>, w: String, score: Float) {
        if (top.size >= 5 && score >= top.last().second) return
        var pos = top.size
        while (pos > 0 && top[pos - 1].second > score) pos--
        top.add(pos, w to score)
        if (top.size > 5) top.removeAt(top.size - 1)
    }

    private fun shapeDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in 0 until n) {
            sum += hypot(a[i * 2] - b[i * 2], a[i * 2 + 1] - b[i * 2 + 1])
        }
        return sum / n
    }

    private fun pathLength(p: FloatArray): Float {
        var len = 0f
        var i = 2
        while (i < p.size) {
            len += hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1])
            i += 2
        }
        return len
    }

    /** Re-samples a path to [count] points spaced evenly along its length. */
    private fun resample(p: FloatArray, count: Int): FloatArray {
        val out = FloatArray(count * 2)
        val total = pathLength(p)
        if (total <= 0f) {
            for (i in 0 until count) {
                out[i * 2] = p[0]
                out[i * 2 + 1] = p[1]
            }
            return out
        }
        val step = total / (count - 1)
        var seg = 2
        var segStart = 0f
        var segLen = hypot(p[2] - p[0], p[3] - p[1])
        for (i in 0 until count) {
            val target = i * step
            while (seg < p.size - 2 && segStart + segLen < target) {
                segStart += segLen
                seg += 2
                segLen = hypot(p[seg] - p[seg - 2], p[seg + 1] - p[seg - 1])
            }
            val t = if (segLen > 0f) ((target - segStart) / segLen).coerceIn(0f, 1f) else 0f
            out[i * 2] = p[seg - 2] + (p[seg] - p[seg - 2]) * t
            out[i * 2 + 1] = p[seg - 1] + (p[seg + 1] - p[seg - 1]) * t
        }
        return out
    }
}

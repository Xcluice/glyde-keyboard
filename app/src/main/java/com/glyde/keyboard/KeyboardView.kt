package com.glyde.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun wa(c: Int, a: Float): Int =
    (c and 0x00FFFFFF) or ((a * 255f).toInt().coerceIn(0, 255) shl 24)

private fun alpha255(a: Float): Int = (a * 255f).toInt().coerceIn(0, 255)

private enum class KeyKind { CHAR, SHIFT, DEL, PAGE, SPACE, ENTER, SPACER }

/** A tiny underdamped spring: gives keys their liquid overshoot. */
private class Spring {
    var pos = 0f
    var vel = 0f
    var goal = 0f

    fun step(dt: Float): Boolean {
        var left = dt
        while (left > 0f) {
            val sub = min(left, 0.008f)
            vel += (-520f * (pos - goal) - 20f * vel) * sub
            pos += vel * sub
            left -= sub
        }
        if (abs(pos - goal) < 0.002f && abs(vel) < 0.02f) {
            pos = goal
            vel = 0f
            return false
        }
        return true
    }

    fun reset() {
        pos = 0f
        vel = 0f
        goal = 0f
    }
}

private class KeyDef(
    val label: String,
    val kind: KeyKind,
    val weight: Float = 1f,
    val alts: String = "",
    val target: Int = 0
) {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    var cellX = 0f
    var cellY = 0f
    var cellW = 0f
    var cellH = 0f
    val sp = Spring()
}

private class Ptr {
    var key: KeyDef? = null
    var tool = -1
    var startX = 0f
    var startY = 0f
    var lastX = 0f
    var lastY = 0f
    var altOpen = false
    var altSel = 0
    var altLeft = 0f
    var altTop = 0f
    var altCw = 0f
    var altN = 0
    var altChars: List<String> = emptyList()
    var cursorMode = false
    var cursorAccum = 0f
    var longPress: Runnable? = null
    var repeat: Runnable? = null
}

private class Ripple(val x: Float, val y: Float, val start: Long)

class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onChar(text: String)
        fun onDelete()
        fun onSpace()
        fun onEnter()
        fun onSettings()
        fun onThemeCycle()
        fun onMoveCursor(delta: Int)
        fun onKeyFeedback(kind: Int)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // ---- configuration ----
    private var theme: GlydeTheme = GlydeThemes.byId("ocean")
    private var glassStrength = 1f
    private var heightScale = 1f
    private var numberRow = false
    private var showPreview = true
    private var liquid = true
    private var ambient = false
    private var lastSig = ""
    private var accentInk = Color.WHITE

    // ---- keyboard state ----
    private var page = 0
    private var shift = 0 // 0 off, 1 once, 2 lock
    private var lastShiftTap = 0L
    private var enterLabel: String? = null
    private var pages: List<List<List<KeyDef>>> = emptyList()
    private val ptrs = SparseArray<Ptr>()
    private val ripples = ArrayList<Ripple>()
    private val tools = arrayOf(Spring(), Spring())
    private val toolRects = arrayOf(RectF(), RectF())

    // ---- layout metrics ----
    private val laneH: Float get() = dp(48f)
    private val vGap: Float get() = dp(7f)
    private val hGap: Float get() = dp(5f)
    private val sidePad: Float get() = dp(4f)
    private val topPad: Float get() = dp(2f)
    private val bottomPad: Float get() = dp(10f)

    // ---- animation state ----
    private var lastFrame = 0L
    private var orbT = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var touchAmt = 0f
    private var tickPending = false
    private val tickRunnable = Runnable { tick() }

    // ---- paints, paths, shaders ----
    private val tmp = Matrix()
    private val fm = Paint.FontMetrics()
    private val panelPath = Path()
    private val keyPath = Path()
    private val medFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val boldFace: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val panelRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = medFace
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private lateinit var keyShader: LinearGradient
    private lateinit var modShader: LinearGradient
    private lateinit var accentShader: LinearGradient
    private lateinit var glossShader: LinearGradient
    private lateinit var glowShader: LinearGradient
    private lateinit var rimShader: LinearGradient
    private lateinit var sheenShader: LinearGradient
    private lateinit var rippleShader: RadialGradient
    private var orbShaders: Array<RadialGradient> = emptyArray()
    private var bgShader: LinearGradient? = null
    private var panelRimShader: LinearGradient? = null

    private val shiftPath = Path().apply {
        moveTo(0f, -1f); lineTo(1f, 0f); lineTo(0.45f, 0f); lineTo(0.45f, 0.8f)
        lineTo(-0.45f, 0.8f); lineTo(-0.45f, 0f); lineTo(-1f, 0f); close()
    }
    private val delPath = Path().apply {
        moveTo(-1f, 0f); lineTo(-0.4f, -0.7f); lineTo(1f, -0.7f); lineTo(1f, 0.7f)
        lineTo(-0.4f, 0.7f); close()
        moveTo(0.05f, -0.28f); lineTo(0.6f, 0.28f)
        moveTo(0.6f, -0.28f); lineTo(0.05f, 0.28f)
    }
    private val enterPath = Path().apply {
        moveTo(0.85f, -0.75f); lineTo(0.85f, 0.15f); lineTo(-0.75f, 0.15f)
        moveTo(-0.2f, -0.4f); lineTo(-0.75f, 0.15f); lineTo(-0.2f, 0.7f)
    }
    private val dropPath = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.2f, -0.5f, 0.85f, -0.1f, 0.85f, 0.35f)
        cubicTo(0.85f, 0.78f, 0.45f, 1f, 0f, 1f)
        cubicTo(-0.45f, 1f, -0.85f, 0.78f, -0.85f, 0.35f)
        cubicTo(-0.85f, -0.1f, -0.2f, -0.5f, 0f, -1f)
        close()
    }
    private val gearPath = Path().apply {
        addCircle(0f, 0f, 0.42f, Path.Direction.CW)
        for (i in 0 until 8) {
            val a = i * Math.PI / 4.0
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            moveTo(ca * 0.66f, sa * 0.66f)
            lineTo(ca * 0.98f, sa * 0.98f)
        }
    }

    init {
        buildPages()
        rebuildShaders()
    }

    // ------------------------------------------------------------------
    // public API
    // ------------------------------------------------------------------

    fun applyPrefs(p: GlydePrefs) {
        val sig = p.signature()
        if (sig == lastSig) return
        lastSig = sig
        theme = GlydeThemes.byId(p.theme)
        glassStrength = p.glass
        heightScale = p.heightScale
        numberRow = p.numberRow
        showPreview = p.keyPreview
        liquid = p.liquid
        ambient = p.ambient
        accentInk = if (ColorUtils.calculateLuminance(theme.accent) > 0.5) 0xFF0B1A1F.toInt() else Color.WHITE
        buildPages()
        layoutKeys()
        rebuildShaders()
        requestLayout()
        invalidate()
        if (ambient) requestTick()
    }

    fun setEditorInfo(info: EditorInfo, restarting: Boolean) {
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        var label: String? = when (action) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> null
        }
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) label = null
        enterLabel = label
        if (!restarting) {
            val cls = info.inputType and EditorInfo.TYPE_MASK_CLASS
            val numeric = cls == EditorInfo.TYPE_CLASS_NUMBER ||
                cls == EditorInfo.TYPE_CLASS_PHONE ||
                cls == EditorInfo.TYPE_CLASS_DATETIME
            switchPage(if (numeric) 1 else 0)
            shift = 0
        }
        invalidate()
    }

    fun setAutoShift(on: Boolean) {
        if (shift == 2) return
        val ns = if (on) 1 else 0
        if (ns != shift) {
            shift = ns
            invalidate()
        }
    }

    // ------------------------------------------------------------------
    // layouts
    // ------------------------------------------------------------------

    private fun keyHeight(): Float {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return dp(46f) * heightScale * (if (land) 0.78f else 1f)
    }

    private fun buildPages() {
        fun row(s: String, digitAlts: Boolean = false): List<KeyDef> =
            s.mapIndexed { i, ch ->
                val extra = if (digitAlts) "1234567890"[i].toString() else ""
                KeyDef(ch.toString(), KeyKind.CHAR, 1f, extra + (ALTS[ch] ?: ""))
            }

        fun spacer(w: Float) = KeyDef("", KeyKind.SPACER, w)

        fun bottomRow(pageLabel: String, target: Int): List<KeyDef> = listOf(
            KeyDef(pageLabel, KeyKind.PAGE, 1.5f, "", target),
            KeyDef(",", KeyKind.CHAR, 1f, ALTS[','] ?: ""),
            KeyDef("", KeyKind.SPACE, 5f),
            KeyDef(".", KeyKind.CHAR, 1f, ALTS['.'] ?: ""),
            KeyDef("", KeyKind.ENTER, 1.5f)
        )

        val letters = ArrayList<List<KeyDef>>()
        if (numberRow) letters.add(row("1234567890"))
        letters.add(row("qwertyuiop", !numberRow))
        letters.add(listOf(spacer(0.5f)) + row("asdfghjkl") + spacer(0.5f))
        letters.add(listOf(KeyDef("", KeyKind.SHIFT, 1.5f)) + row("zxcvbnm") + KeyDef("", KeyKind.DEL, 1.5f))
        letters.add(bottomRow("?123", 1))

        val sym1 = listOf(
            row("1234567890"),
            row("@#\$%&-+()/"),
            listOf(KeyDef("=\\<", KeyKind.PAGE, 1.5f, "", 2)) + row("*\"':;!?") + KeyDef("", KeyKind.DEL, 1.5f),
            bottomRow("ABC", 0)
        )

        val sym2 = listOf(
            row("~`|•√π÷×¶∆"),
            row("£¢€¥^°={}\\"),
            listOf(KeyDef("?123", KeyKind.PAGE, 1.5f, "", 1)) + row("©®™[]<>") + KeyDef("", KeyKind.DEL, 1.5f),
            bottomRow("ABC", 0)
        )

        pages = listOf(letters, sym1, sym2)
    }

    private fun layoutKeys() {
        val w = width.toFloat()
        if (w <= 0f || pages.isEmpty()) return
        val maxRows = pages.maxOf { it.size }
        val area = maxRows * (keyHeight() + vGap) - vGap
        val unit = (w - 2f * sidePad) / 10f
        for (pg in pages) {
            val n = pg.size
            val kh = (area + vGap) / n - vGap
            for ((ri, row) in pg.withIndex()) {
                var cum = 0f
                val cy = laneH + topPad + ri * (kh + vGap)
                for (k in row) {
                    val cx = sidePad + cum * unit
                    k.cellX = cx
                    k.cellW = k.weight * unit
                    k.cellY = cy - vGap / 2f
                    k.cellH = kh + vGap
                    k.x = cx + hGap / 2f
                    k.w = k.cellW - hGap
                    k.y = cy
                    k.h = kh
                    cum += k.weight
                }
            }
        }
    }

    private fun hitKey(x: Float, y: Float): KeyDef? {
        val rows = pages[page]
        if (rows.isEmpty()) return null
        var chosen = rows.last()
        for (row in rows) {
            val f = row.first()
            if (y < f.cellY + f.cellH) {
                chosen = row
                break
            }
        }
        for (k in chosen) {
            if (k.kind == KeyKind.SPACER) continue
            if (x < k.cellX + k.cellW) return k
        }
        return chosen.lastOrNull { it.kind != KeyKind.SPACER }
    }

    private fun toolAt(x: Float, y: Float): Int {
        val slop = dp(6f)
        for (i in 0..1) {
            val r = toolRects[i]
            if (x >= r.left - slop && x <= r.right + slop && y >= r.top - slop && y <= r.bottom + slop) return i
        }
        return -1
    }

    // ------------------------------------------------------------------
    // sizing
    // ------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = pages.maxOf { it.size }
        val h = (laneH + topPad + rows * (keyHeight() + vGap) + bottomPad).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
        val r = dp(22f)
        panelPath.reset()
        panelPath.addRoundRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW
        )
        val s = dp(38f)
        val gap = dp(8f)
        val right = w - dp(10f)
        val top = (laneH - s) / 2f
        toolRects[1].set(right - s, top, right, top + s)
        toolRects[0].set(right - 2f * s - gap, top, right - s - gap, top + s)
        rebuildSizeShaders()
    }

    private fun rebuildShaders() {
        val dark = theme.dark
        val g = glassStrength
        val white = Color.WHITE
        val topA = if (dark) 0.27f * g else 0.80f * g
        val botA = if (dark) 0.10f * g else 0.50f * g
        val clamp = Shader.TileMode.CLAMP
        keyShader = LinearGradient(0f, 0f, 0f, 1f, wa(white, topA), wa(white, botA), clamp)
        modShader = LinearGradient(0f, 0f, 0f, 1f, wa(white, topA * 0.55f), wa(white, botA * 0.5f), clamp)
        accentShader = LinearGradient(0f, 0f, 0f, 1f, wa(theme.accent, 0.95f), wa(theme.accent, 0.70f), clamp)
        glossShader = LinearGradient(0f, 0f, 0f, 1f, wa(white, if (dark) 0.34f else 0.55f), wa(white, 0f), clamp)
        glowShader = LinearGradient(0f, 0f, 0f, 1f, wa(white, 0f), wa(white, if (dark) 0.14f else 0.25f), clamp)
        rimShader = LinearGradient(
            0f, 0f, 1f, 1f,
            intArrayOf(wa(white, 0.85f), wa(white, 0.10f), wa(white, 0.08f), wa(white, 0.45f)),
            floatArrayOf(0f, 0.42f, 0.62f, 1f), clamp
        )
        sheenShader = LinearGradient(0f, 0f, 0f, 1f, wa(white, if (dark) 0.16f else 0.35f), wa(white, 0f), clamp)
        rippleShader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(wa(theme.accent, 0.55f), wa(theme.accent, 0f)),
            floatArrayOf(0f, 1f), clamp
        )
        val oa = if (dark) 0.78f else 0.65f
        orbShaders = arrayOf(orb(theme.orbA, oa), orb(theme.orbB, oa * 0.9f), orb(theme.orbC, oa * 0.8f))
        solidPaint.color = wa(theme.bgBottom, 0.94f)
        rebuildSizeShaders()
    }

    private fun orb(c: Int, a: Float): RadialGradient =
        RadialGradient(
            0f, 0f, 1f,
            intArrayOf(wa(c, a), wa(c, 0f)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )

    private fun rebuildSizeShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        bgShader = LinearGradient(0f, 0f, 0f, h, theme.bgTop, theme.bgBottom, Shader.TileMode.CLAMP)
        panelRimShader = LinearGradient(
            0f, 0f, 0f, h * 0.7f,
            wa(Color.WHITE, 0.5f), wa(Color.WHITE, 0f), Shader.TileMode.CLAMP
        )
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        c.save()
        c.clipPath(panelPath)
        bgPaint.shader = bgShader
        c.drawRect(0f, 0f, w, h, bgPaint)
        for (i in 0 until 3) {
            val sp = ORB_SPEED[i]
            var ox = w * (0.5f + 0.42f * sin(orbT * sp + ORB_PHASE[i]))
            var oy = h * (0.5f + 0.40f * cos(orbT * sp * 0.8f + ORB_PHASE[i] * 1.7f))
            ox += (touchX - ox) * 0.22f * touchAmt
            oy += (touchY - oy) * 0.22f * touchAmt
            val r = max(w, h) * (0.55f + 0.10f * i)
            tmp.setScale(r, r)
            tmp.postTranslate(ox, oy)
            orbShaders[i].setLocalMatrix(tmp)
            orbPaint.shader = orbShaders[i]
            c.drawRect(0f, 0f, w, h, orbPaint)
        }
        tmp.setScale(1f, h * 0.5f)
        sheenShader.setLocalMatrix(tmp)
        sheenPaint.shader = sheenShader
        c.drawRect(0f, 0f, w, h * 0.5f, sheenPaint)
        c.restore()

        panelRim.shader = panelRimShader
        c.drawPath(panelPath, panelRim)

        drawToolbar(c)

        val rows = pages[page]
        for (pass in 0..1) {
            for (row in rows) {
                for (k in row) {
                    if (k.kind == KeyKind.SPACER) continue
                    val pressed = k.sp.pos > 0.04f || k.sp.goal > 0f
                    if ((pass == 0) == pressed) continue
                    drawKey(c, k)
                }
            }
        }

        drawRipples(c)
        drawPopups(c)
    }

    private fun drawToolbar(c: Canvas) {
        val cy = laneH / 2f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(16f)
        textPaint.typeface = boldFace
        textPaint.color = wa(theme.ink, 0.92f)
        textPaint.getFontMetrics(fm)
        c.drawText("Glyde", dp(16f), cy - (fm.ascent + fm.descent) / 2f, textPaint)
        val tw = textPaint.measureText("Glyde")
        dotPaint.color = theme.accent
        c.drawCircle(dp(16f) + tw + dp(7f), cy - dp(4f), dp(3f), dotPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = medFace

        for (i in 0..1) {
            val r = toolRects[i]
            val p = tools[i].pos
            val s = 1f + 0.1f * p
            c.save()
            c.scale(s, s, r.centerX(), r.centerY())
            drawGlass(c, r.left, r.top, r.right, r.bottom, dp(13f), p, 1)
            drawIcon(c, if (i == 0) dropPath else gearPath, r.centerX(), r.centerY(), dp(8.5f), theme.ink, false)
            c.restore()
        }
    }

    private fun bulge(k: KeyDef): Float {
        if (!liquid || touchAmt < 0.01f) return 0f
        val cx = k.x + k.w / 2f
        val cy = k.y + k.h / 2f
        val dist = hypot(cx - touchX, cy - touchY)
        val reach = k.h * 1.9f
        if (dist >= reach) return 0f
        val f = 1f - dist / reach
        return 0.055f * f * f * touchAmt
    }

    private fun drawKey(c: Canvas, k: KeyDef) {
        val p = k.sp.pos
        val b = bulge(k)
        val sx = 1f + 0.087f * p + b
        val sy = 1f + 0.067f * p + b
        val cx = k.x + k.w / 2f
        val cy = k.y + k.h / 2f
        c.save()
        c.scale(sx, sy, cx, cy)

        val fill = when {
            k.kind == KeyKind.ENTER -> 2
            k.kind == KeyKind.SHIFT && shift != 0 -> 2
            k.kind == KeyKind.SHIFT || k.kind == KeyKind.DEL || k.kind == KeyKind.PAGE -> 1
            else -> 0
        }
        drawGlass(c, k.x, k.y, k.x + k.w, k.y + k.h, k.h * 0.30f, p, fill)

        val ink = if (fill == 2) accentInk else theme.ink
        when (k.kind) {
            KeyKind.CHAR -> {
                textPaint.textSize = dp(22f)
                textPaint.color = ink
                drawCentered(c, cased(k.label), cx, cy)
                if (page == 0 && k.alts.isNotEmpty() && k.alts[0].isDigit()) {
                    textPaint.textSize = dp(9.5f)
                    textPaint.color = wa(ink, 0.55f)
                    c.drawText(k.alts.substring(0, 1), k.x + k.w - dp(8f), k.y + dp(12f), textPaint)
                }
            }
            KeyKind.SHIFT -> {
                val s = k.h * 0.26f
                drawIcon(c, shiftPath, cx, cy - (if (shift == 2) dp(1.5f) else 0f), s, ink, shift != 0)
                if (shift == 2) {
                    iconPaint.style = Paint.Style.STROKE
                    iconPaint.strokeWidth = dp(2f)
                    iconPaint.color = ink
                    c.drawLine(cx - s * 0.6f, cy + s * 1.15f, cx + s * 0.6f, cy + s * 1.15f, iconPaint)
                }
            }
            KeyKind.DEL -> drawIcon(c, delPath, cx, cy, k.h * 0.27f, ink, false)
            KeyKind.ENTER -> {
                val el = enterLabel
                if (el != null) {
                    textPaint.textSize = dp(13f)
                    textPaint.color = ink
                    drawCentered(c, el, cx, cy)
                } else {
                    drawIcon(c, enterPath, cx, cy, k.h * 0.27f, ink, false)
                }
            }
            KeyKind.PAGE -> {
                textPaint.textSize = dp(15f)
                textPaint.color = ink
                drawCentered(c, k.label, cx, cy)
            }
            KeyKind.SPACE -> {
                textPaint.textSize = dp(12f)
                textPaint.color = wa(ink, 0.55f)
                drawCentered(c, "Glyde", cx, cy)
            }
            KeyKind.SPACER -> {}
        }
        c.restore()
    }

    /** The liquid glass surface: translucent body, specular gloss, bottom glow and a refracting rim. */
    private fun drawGlass(
        c: Canvas, l: Float, t: Float, r: Float, b: Float,
        rad: Float, press: Float, fill: Int
    ) {
        val h = b - t
        val w = r - l
        val pc = press.coerceIn(0f, 1f)

        shadowPaint.alpha = alpha255((if (theme.dark) 0.22f else 0.14f) * (1f - 0.6f * pc))
        c.drawRoundRect(l, t + dp(2f), r, b + dp(2f), rad, rad, shadowPaint)

        tmp.setScale(1f, h)
        tmp.postTranslate(0f, t)
        val sh: LinearGradient = when (fill) {
            2 -> accentShader
            1 -> modShader
            else -> keyShader
        }
        sh.setLocalMatrix(tmp)
        fillPaint.shader = sh
        c.drawRoundRect(l, t, r, b, rad, rad, fillPaint)

        if (pc > 0.01f) {
            pressPaint.alpha = alpha255(0.30f * pc)
            c.drawRoundRect(l, t, r, b, rad, rad, pressPaint)
        }

        keyPath.reset()
        keyPath.addRoundRect(l, t, r, b, rad, rad, Path.Direction.CW)
        c.save()
        c.clipPath(keyPath)
        val gh = h * 0.55f
        tmp.setScale(1f, gh)
        tmp.postTranslate(0f, t)
        glossShader.setLocalMatrix(tmp)
        glossPaint.shader = glossShader
        c.drawRect(l, t, r, t + gh, glossPaint)
        val bh = h * 0.4f
        tmp.setScale(1f, bh)
        tmp.postTranslate(0f, b - bh)
        glowShader.setLocalMatrix(tmp)
        glossPaint.shader = glowShader
        c.drawRect(l, b - bh, r, b, glossPaint)
        c.restore()

        tmp.setScale(w, h)
        tmp.postTranslate(l, t)
        rimShader.setLocalMatrix(tmp)
        rimPaint.shader = rimShader
        val ins = rimPaint.strokeWidth / 2f
        c.drawRoundRect(l + ins, t + ins, r - ins, b - ins, rad, rad, rimPaint)
    }

    private fun drawCentered(c: Canvas, text: String, cx: Float, cy: Float) {
        textPaint.getFontMetrics(fm)
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun drawIcon(c: Canvas, path: Path, cx: Float, cy: Float, s: Float, color: Int, fill: Boolean) {
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        iconPaint.color = color
        iconPaint.style = if (fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        iconPaint.strokeWidth = dp(1.9f) / s
        c.drawPath(path, iconPaint)
        c.restore()
    }

    private fun drawRipples(c: Canvas) {
        if (ripples.isEmpty()) return
        val now = System.nanoTime()
        val base = keyHeight()
        for (r in ripples) {
            val t = ((now - r.start) / RIPPLE_NS.toFloat()).coerceIn(0f, 1f)
            val e = 1f - (1f - t) * (1f - t) * (1f - t)
            val rad = dp(14f) + base * 1.45f * e
            tmp.setScale(rad, rad)
            tmp.postTranslate(r.x, r.y)
            rippleShader.setLocalMatrix(tmp)
            ripplePaint.shader = rippleShader
            ripplePaint.alpha = alpha255(1f - t)
            c.drawCircle(r.x, r.y, rad, ripplePaint)
        }
    }

    private fun drawPopups(c: Canvas) {
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            val k = p.key ?: continue
            if (k.kind != KeyKind.CHAR) continue
            if (p.altOpen) {
                drawAlt(c, p)
            } else if (showPreview && k.sp.pos > 0.2f) {
                drawPreview(c, k)
            }
        }
    }

    private fun drawPreview(c: Canvas, k: KeyDef) {
        val bw = k.w * 1.3f
        val bh = (k.y - dp(5f)).coerceIn(dp(32f), max(k.h * 1.2f, dp(32f)))
        val left = (k.x + k.w / 2f - bw / 2f).coerceIn(dp(2f), max(dp(2f), width - bw - dp(2f)))
        val top = max(0f, k.y - bh - dp(3f))
        val rad = dp(14f)
        c.drawRoundRect(left, top, left + bw, top + bh, rad, rad, solidPaint)
        drawGlass(c, left, top, left + bw, top + bh, rad, 0.5f, 0)
        textPaint.textSize = dp(30f)
        textPaint.color = theme.ink
        drawCentered(c, cased(k.label), left + bw / 2f, top + bh / 2f)
    }

    private fun drawAlt(c: Canvas, p: Ptr) {
        val ph = dp(46f)
        val pw = p.altN * p.altCw + dp(12f)
        val rad = dp(14f)
        c.drawRoundRect(p.altLeft, p.altTop, p.altLeft + pw, p.altTop + ph, rad, rad, solidPaint)
        drawGlass(c, p.altLeft, p.altTop, p.altLeft + pw, p.altTop + ph, rad, 0.4f, 0)
        for (i in p.altChars.indices) {
            val cx = p.altLeft + dp(6f) + (i + 0.5f) * p.altCw
            val cy = p.altTop + ph / 2f
            val sel = i == p.altSel
            if (sel) {
                accentPaint.color = wa(theme.accent, 0.92f)
                c.drawRoundRect(
                    cx - p.altCw / 2f + dp(2f), p.altTop + dp(5f),
                    cx + p.altCw / 2f - dp(2f), p.altTop + ph - dp(5f),
                    dp(10f), dp(10f), accentPaint
                )
            }
            textPaint.textSize = dp(22f)
            textPaint.color = if (sel) accentInk else theme.ink
            drawCentered(c, cased(p.altChars[i]), cx, cy)
        }
    }

    // ------------------------------------------------------------------
    // animation loop
    // ------------------------------------------------------------------

    private fun requestTick() {
        if (tickPending) return
        tickPending = true
        postOnAnimation(tickRunnable)
    }

    private fun tick() {
        tickPending = false
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0.016f
        else ((now - lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrame = now
        var active = false

        for (row in pages[page]) {
            for (k in row) {
                if (k.kind != KeyKind.SPACER && k.sp.step(dt)) active = true
            }
        }
        for (s in tools) {
            if (s.step(dt)) active = true
        }
        if (ripples.isNotEmpty()) {
            ripples.removeAll { now - it.start > RIPPLE_NS }
            if (ripples.isNotEmpty()) active = true
        }
        val target = if (ptrs.size() > 0) 1f else 0f
        touchAmt += (target - touchAmt) * min(1f, dt * 9f)
        if (abs(target - touchAmt) > 0.01f) active = true else touchAmt = target
        orbT += dt * ((if (ambient) 0.22f else 0f) + 1.1f * touchAmt)

        invalidate()

        if (active) {
            tickPending = true
            postOnAnimation(tickRunnable)
        } else if (ambient && isShown) {
            tickPending = true
            postDelayed(tickRunnable, 40L)
        } else {
            lastFrame = 0L
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && ambient) requestTick()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(tickRunnable)
        tickPending = false
        lastFrame = 0L
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            cancelLongPress(p)
            stopRepeat(p)
        }
        ptrs.clear()
    }

    // ------------------------------------------------------------------
    // touch handling
    // ------------------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                onDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    onMove(e.getPointerId(i), e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                onUp(e.getPointerId(i), e.getX(i), e.getY(i), false)
            }
            MotionEvent.ACTION_CANCEL -> {
                val ids = IntArray(ptrs.size()) { ptrs.keyAt(it) }
                for (id in ids) onUp(id, 0f, 0f, true)
            }
        }
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        val p = Ptr()
        p.startX = x
        p.startY = y
        p.lastX = x
        p.lastY = y
        ptrs.put(id, p)
        touchX = x
        touchY = y
        if (y < laneH) {
            p.tool = toolAt(x, y)
            if (p.tool >= 0) {
                tools[p.tool].goal = 1f
                listener?.onKeyFeedback(0)
                if (liquid) addRipple(x, y)
            }
        } else {
            val k = hitKey(x, y)
            p.key = k
            if (k != null) pressKey(p, k, x, y, true)
        }
        requestTick()
    }

    private fun pressKey(p: Ptr, k: KeyDef, x: Float, y: Float, fromDown: Boolean) {
        k.sp.goal = 1f
        listener?.onKeyFeedback(
            when (k.kind) {
                KeyKind.SPACE -> 1
                KeyKind.DEL -> 2
                KeyKind.ENTER -> 3
                else -> 0
            }
        )
        if (liquid) addRipple(x, y)
        if (k.kind == KeyKind.DEL && fromDown) {
            listener?.onDelete()
            startRepeat(p)
        } else if (k.kind == KeyKind.CHAR && k.alts.isNotEmpty()) {
            scheduleLongPress(p)
        }
    }

    private fun onMove(id: Int, x: Float, y: Float) {
        val p = ptrs.get(id) ?: return
        touchX = x
        touchY = y

        if (p.altOpen) {
            p.altSel = ((x - p.altLeft - dp(6f)) / p.altCw).toInt().coerceIn(0, max(0, p.altN - 1))
            p.lastX = x
            p.lastY = y
            invalidate()
            return
        }
        if (p.tool >= 0) {
            p.lastX = x
            p.lastY = y
            return
        }

        val cur = p.key
        if (cur != null && cur.kind == KeyKind.SPACE) {
            if (p.cursorMode || abs(x - p.startX) > dp(22f)) {
                p.cursorMode = true
                p.cursorAccum += x - p.lastX
                val step = dp(14f)
                while (p.cursorAccum > step) {
                    listener?.onMoveCursor(1)
                    p.cursorAccum -= step
                }
                while (p.cursorAccum < -step) {
                    listener?.onMoveCursor(-1)
                    p.cursorAccum += step
                }
                p.lastX = x
                p.lastY = y
                invalidate()
                return
            }
        }

        if (y >= laneH) {
            val nk = hitKey(x, y)
            if (nk !== cur) {
                if (cur != null) cur.sp.goal = 0f
                cancelLongPress(p)
                stopRepeat(p)
                p.key = nk
                if (nk != null) pressKey(p, nk, x, y, false)
            }
        }
        p.lastX = x
        p.lastY = y
        requestTick()
    }

    private fun onUp(id: Int, x: Float, y: Float, cancel: Boolean) {
        val p = ptrs.get(id) ?: return
        ptrs.remove(id)
        cancelLongPress(p)
        stopRepeat(p)
        val k = p.key
        if (k != null) k.sp.goal = 0f
        if (p.tool >= 0) {
            tools[p.tool].goal = 0f
            if (!cancel && toolAt(x, y) == p.tool) {
                if (p.tool == 0) listener?.onThemeCycle() else listener?.onSettings()
            }
        } else if (!cancel && k != null) {
            commit(p, k)
        }
        requestTick()
        invalidate()
    }

    private fun commit(p: Ptr, k: KeyDef) {
        when (k.kind) {
            KeyKind.CHAR -> {
                val text = if (p.altOpen && p.altSel in p.altChars.indices) p.altChars[p.altSel] else k.label
                typeText(cased(text))
            }
            KeyKind.SHIFT -> handleShift()
            KeyKind.DEL -> {}
            KeyKind.PAGE -> switchPage(k.target)
            KeyKind.SPACE -> if (!p.cursorMode) listener?.onSpace()
            KeyKind.ENTER -> listener?.onEnter()
            KeyKind.SPACER -> {}
        }
    }

    private fun typeText(t: String) {
        if (shift == 1) shift = 0
        listener?.onChar(t)
        invalidate()
    }

    private fun handleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == 2 -> 0
            shift == 1 && now - lastShiftTap < 400L -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
    }

    private fun switchPage(target: Int) {
        page = target.coerceIn(0, pages.size - 1)
        for (pg in pages) for (row in pg) for (k in row) k.sp.reset()
        for (i in 0 until ptrs.size()) {
            val q = ptrs.valueAt(i)
            q.key = null
            cancelLongPress(q)
            stopRepeat(q)
        }
        invalidate()
    }

    private fun cased(t: String): String =
        if (page == 0 && shift != 0) t.uppercase() else t

    private fun addRipple(x: Float, y: Float) {
        if (ripples.size > 6) ripples.removeAt(0)
        ripples.add(Ripple(x, y, System.nanoTime()))
    }

    private fun scheduleLongPress(p: Ptr) {
        val r = Runnable {
            val k = p.key ?: return@Runnable
            if (ptrs.indexOfValue(p) < 0 || p.cursorMode) return@Runnable
            val chars = k.alts.take(8).map { it.toString() }
            if (chars.isEmpty()) return@Runnable
            val n = chars.size
            val cw = min(dp(40f), (width - dp(24f)) / n)
            val pw = n * cw + dp(12f)
            p.altChars = chars
            p.altN = n
            p.altCw = cw
            p.altLeft = (k.x + k.w / 2f - pw / 2f).coerceIn(dp(4f), max(dp(4f), width - pw - dp(4f)))
            p.altTop = max(dp(2f), k.y - dp(46f) - dp(8f))
            p.altOpen = true
            p.altSel = ((p.lastX - p.altLeft - dp(6f)) / cw).toInt().coerceIn(0, n - 1)
            listener?.onKeyFeedback(0)
            invalidate()
            requestTick()
        }
        p.longPress = r
        postDelayed(r, LONG_PRESS_MS)
    }

    private fun cancelLongPress(p: Ptr) {
        p.longPress?.let { removeCallbacks(it) }
        p.longPress = null
    }

    private fun startRepeat(p: Ptr) {
        val r = object : Runnable {
            override fun run() {
                if (ptrs.indexOfValue(p) < 0) return
                listener?.onDelete()
                listener?.onKeyFeedback(2)
                postDelayed(this, REPEAT_MS)
            }
        }
        p.repeat = r
        postDelayed(r, 400L)
    }

    private fun stopRepeat(p: Ptr) {
        p.repeat?.let { removeCallbacks(it) }
        p.repeat = null
    }

    private companion object {
        const val RIPPLE_NS = 520_000_000L
        const val LONG_PRESS_MS = 360L
        const val REPEAT_MS = 48L
        val ORB_SPEED = floatArrayOf(0.9f, 1.3f, 0.7f)
        val ORB_PHASE = floatArrayOf(0f, 2.1f, 4.2f)
        val ALTS: Map<Char, String> = mapOf(
            'a' to "àáâäæãåā",
            'e' to "èéêëēėę",
            'i' to "îïíīįì",
            'o' to "ôöòóœøōõ",
            'u' to "ûüùúū",
            'c' to "çćč",
            'n' to "ñń",
            's' to "śšß",
            'y' to "ýÿ",
            'z' to "žźż",
            'l' to "ł",
            'd' to "ð",
            'g' to "ğ",
            '.' to "!?:;…",
            ',' to "…",
            '-' to "–—•",
            '$' to "€£¥¢",
            '"' to "“”„«»",
            '\'' to "‘’‚‹›",
            '?' to "¿",
            '!' to "¡",
            '%' to "‰",
            '=' to "≠≈",
            '0' to "°",
            '<' to "‹«≤",
            '>' to "›»≥"
        )
    }
}

package com.xcluice.glyde

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas-rendered keyboard. Handles glass/gradient themes, key animations,
 * haptics, multi-touch, shift/caps-lock, long-press alternates and key repeat.
 */
class GlydeKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onOpenSettings()
    }

    enum class Shift { OFF, ONCE, LOCK }

    private class Key(val def: KeyDef) {
        val cell = RectF()
        val draw = RectF()
        var pressed = false
        var downAt = 0L
        var upAt = 0L
        var rippleX = 0f
        var rippleY = 0f
        var rippleAt = 0L
        var fillShader: Shader? = null
        var borderShader: Shader? = null
    }

    private class Popup(val alts: List<String>, val rect: RectF, val cellW: Float, val pad: Float) {
        var sel = 0
    }

    private class Ptr(val id: Int) {
        var key: Key? = null
        var toolbar = false
        var popup: Popup? = null
        var x = 0f
        var y = 0f
        var longPress: Runnable? = null
        var repeat: Runnable? = null
    }

    var listener: Listener? = null

    var enterLabel: String? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var spaceLabel: String = "Glyde"
        set(value) {
            field = value
            invalidate()
        }

    var shift = Shift.OFF
        private set

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val prefs = Prefs.get(context)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val uiHandler = Handler(Looper.getMainLooper())

    private var theme: GlydeTheme = Themes.byId(prefs.themeId)
    private var page = Page.LETTERS
    private var autoShifted = false
    private var lastShiftTap = 0L
    private var keys: List<Key> = emptyList()
    private val ptrs = SparseArray<Ptr>()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaintA = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaintB = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = density * 2f
    }
    private val path = Path()
    private val settingsRect = RectF()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        post { applyPrefs() }
    }

    // ------------------------------------------------------------------ public API

    fun showSymbols() = switchPage(Page.SYMBOLS1)

    fun showLetters() = switchPage(Page.LETTERS)

    /** Called by the IME after each edit; turns one-shot shift on at sentence starts. */
    fun autoShift(on: Boolean) {
        if (on && shift == Shift.OFF) {
            shift = Shift.ONCE
            autoShifted = true
            invalidate()
        } else if (!on && shift == Shift.ONCE && autoShifted) {
            shift = Shift.OFF
            autoShifted = false
            invalidate()
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.listen(prefListener)
        applyPrefs()
    }

    override fun onDetachedFromWindow() {
        prefs.unlisten(prefListener)
        cancelAll()
        super.onDetachedFromWindow()
    }

    private fun applyPrefs() {
        theme = Themes.byId(prefs.themeId)
        requestLayout()
        rebuildShaders()
        rebuildKeys()
        invalidate()
    }

    private fun stripH() = dp(44f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = if (prefs.numberRow) 5 else 4
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rowH = dp(52f) * prefs.heightScale * (if (land) 0.78f else 1f)
        val h = (stripH() + dp(4f) + rowH * rows + dp(6f)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders()
        rebuildKeys()
    }

    private fun rebuildShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, theme.bgTop, theme.bgBottom, Shader.TileMode.CLAMP)
        val r = max(w, h) * 0.55f
        blobPaintA.shader = if (theme.blobA != 0) {
            RadialGradient(w * 0.12f, h * 0.15f, r, theme.blobA, theme.blobA and 0x00FFFFFF, Shader.TileMode.CLAMP)
        } else null
        blobPaintB.shader = if (theme.blobB != 0) {
            RadialGradient(w * 0.9f, h * 0.95f, r, theme.blobB, theme.blobB and 0x00FFFFFF, Shader.TileMode.CLAMP)
        } else null
    }

    private fun rebuildKeys() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        settingsRect.set(dp(6f), dp(2f), dp(54f), stripH() - dp(2f))

        val rows = Layouts.rowsFor(page, prefs.numberRow)
        val top = stripH() + dp(4f)
        val bottom = h - dp(6f)
        val rowH = (bottom - top) / rows.size
        val sidePad = dp(3f)
        val gx = dp(2.5f)
        val gy = dp(3.5f)
        val out = ArrayList<Key>()
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (w - 2 * sidePad) / total
            var x = sidePad
            val y0 = top + r * rowH
            for (def in row) {
                val kw = unit * def.weight
                if (def.type != KeyType.SPACER) {
                    val k = Key(def)
                    k.cell.set(x, y0, x + kw, y0 + rowH)
                    k.draw.set(x + gx, y0 + gy, x + kw - gx, y0 + rowH - gy)
                    val isFunc = def.type == KeyType.SHIFT || def.type == KeyType.DELETE ||
                        def.type == KeyType.SYMBOLS || def.type == KeyType.SYMBOLS_ALT
                    val t = if (isFunc) theme.funcTop else theme.keyTop
                    val b = if (isFunc) theme.funcBottom else theme.keyBottom
                    k.fillShader = LinearGradient(0f, k.draw.top, 0f, k.draw.bottom, t, b, Shader.TileMode.CLAMP)
                    k.borderShader = LinearGradient(
                        0f, k.draw.top, 0f, k.draw.bottom,
                        theme.borderTop, theme.borderBottom, Shader.TileMode.CLAMP,
                    )
                    out.add(k)
                }
                x += kw
            }
        }
        keys = out
    }

    private fun switchPage(p: Page) {
        if (page == p) return
        cancelAll()
        page = p
        rebuildKeys()
        invalidate()
    }

    // ------------------------------------------------------------------ drawing

    private fun ease(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun applyShift(s: String): String =
        if (page == Page.LETTERS && shift != Shift.OFF && s.isNotEmpty() && s[0].isLetter()) s.uppercase() else s

    private fun baseline(cy: Float): Float = cy - (textPaint.ascent() + textPaint.descent()) / 2f

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        if (blobPaintA.shader != null) canvas.drawRect(0f, 0f, w, h, blobPaintA)
        if (blobPaintB.shader != null) canvas.drawRect(0f, 0f, w, h, blobPaintB)

        drawStrip(canvas)

        var animating = false
        val anim = prefs.keyAnim
        for (k in keys) {
            if (drawKey(canvas, k, now, anim)) animating = true
        }
        drawOverlays(canvas, now)
        if (animating) postInvalidateOnAnimation()
    }

    private fun drawStrip(c: Canvas) {
        val sh = stripH()
        textPaint.textSize = dp(14f)
        textPaint.letterSpacing = 0.18f
        textPaint.isFakeBoldText = true
        textPaint.color = withAlpha(theme.accent, 170)
        c.drawText("glyde", width / 2f, baseline(sh / 2f), textPaint)
        textPaint.letterSpacing = 0f
        textPaint.isFakeBoldText = false

        // settings (sliders) icon
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = dp(1.6f)
        iconPaint.color = theme.labelDim
        keyPaint.shader = null
        keyPaint.color = theme.labelDim
        val cx = settingsRect.centerX()
        val cy = settingsRect.centerY()
        val half = dp(8f)
        val ys = floatArrayOf(-dp(5.5f), 0f, dp(5.5f))
        val ks = floatArrayOf(-dp(3f), dp(3.5f), -dp(1f))
        for (i in 0..2) {
            c.drawLine(cx - half, cy + ys[i], cx + half, cy + ys[i], iconPaint)
            c.drawCircle(cx + ks[i], cy + ys[i], dp(2.6f), keyPaint)
        }
    }

    /** returns true while this key still needs animation frames */
    private fun drawKey(c: Canvas, k: Key, now: Long, anim: String): Boolean {
        var animating = false
        val r = k.draw
        val type = k.def.type
        val radius = dp(prefs.keyRadius)

        val pr: Float = if (k.pressed) {
            ease(min(1f, (now - k.downAt) / 90f))
        } else {
            1f - ease(min(1f, (now - k.upAt) / 160f))
        }
        if (k.pressed || now - k.upAt < 170) animating = true

        val scale = when (anim) {
            "pop" -> 1f - 0.07f * pr
            "none" -> 1f
            else -> 1f - 0.035f * pr
        }
        c.save()
        c.scale(scale, scale, r.centerX(), r.centerY())

        // glow halo behind the key
        if (anim == "glow" && pr > 0f) {
            val g = dp(4f) * pr
            keyPaint.shader = null
            keyPaint.color = withAlpha(theme.accent, (110 * pr).toInt())
            c.drawRoundRect(r.left - g, r.top - g, r.right + g, r.bottom + g, radius + g, radius + g, keyPaint)
        }

        // body
        if (type == KeyType.ENTER) {
            keyPaint.shader = null
            keyPaint.color = theme.accent
        } else {
            keyPaint.shader = k.fillShader
            keyPaint.color = 0xFFFFFFFF.toInt()
        }
        c.drawRoundRect(r, radius, radius, keyPaint)
        keyPaint.shader = null

        // border
        if (type != KeyType.ENTER) {
            borderPaint.shader = k.borderShader
            c.drawRoundRect(
                r.left + 0.5f * density, r.top + 0.5f * density,
                r.right - 0.5f * density, r.bottom - 0.5f * density,
                radius, radius, borderPaint,
            )
            borderPaint.shader = null
        }

        // pressed tint
        if (pr > 0f && anim != "none") {
            val a = ((theme.pressed ushr 24) * pr).toInt()
            keyPaint.color = withAlpha(theme.pressed, a)
            c.drawRoundRect(r, radius, radius, keyPaint)
        }

        // ripple
        if (anim == "ripple") {
            val t = (now - k.rippleAt) / 380f
            if (t in 0f..1f) {
                animating = true
                c.save()
                path.reset()
                path.addRoundRect(r, radius, radius, Path.Direction.CW)
                c.clipPath(path)
                val maxR = hypot(r.width(), r.height())
                ripplePaint.color = withAlpha(theme.ripple, ((1f - t) * 120).toInt())
                c.drawCircle(k.rippleX, k.rippleY, maxR * ease(t), ripplePaint)
                c.restore()
            }
        }

        drawLabel(c, k, r)
        c.restore()
        return animating
    }

    private fun drawLabel(c: Canvas, k: Key, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        val lc = theme.label
        when (k.def.type) {
            KeyType.CHAR -> {
                val txt = applyShift(k.def.label)
                textPaint.color = lc
                textPaint.textSize = dp(if (txt.length == 1 && txt[0].isLetterOrDigit()) 22f else 20f)
                c.drawText(txt, cx, baseline(cy), textPaint)
                val hint = k.def.alts.firstOrNull()
                if (hint != null && page == Page.LETTERS && hint[0].isDigit()) {
                    textPaint.color = theme.labelDim
                    textPaint.textSize = dp(10f)
                    c.drawText(hint, r.right - dp(9f), r.top + dp(13f), textPaint)
                }
            }
            KeyType.SPACE -> {
                textPaint.color = theme.labelDim
                textPaint.textSize = dp(12f)
                c.drawText(spaceLabel, cx, baseline(cy), textPaint)
            }
            KeyType.SYMBOLS, KeyType.SYMBOLS_ALT -> {
                textPaint.color = lc
                textPaint.textSize = dp(15f)
                c.drawText(k.def.label, cx, baseline(cy), textPaint)
            }
            KeyType.SHIFT -> {
                val active = shift != Shift.OFF
                iconPaint.color = if (active) theme.accent else lc
                iconPaint.strokeWidth = dp(2f)
                iconPaint.style = if (active) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                val s = dp(9f)
                path.reset()
                path.moveTo(cx, cy - s)
                path.lineTo(cx + s, cy)
                path.lineTo(cx + s * 0.45f, cy)
                path.lineTo(cx + s * 0.45f, cy + s * 0.8f)
                path.lineTo(cx - s * 0.45f, cy + s * 0.8f)
                path.lineTo(cx - s * 0.45f, cy)
                path.lineTo(cx - s, cy)
                path.close()
                c.drawPath(path, iconPaint)
                if (shift == Shift.LOCK) {
                    iconPaint.style = Paint.Style.STROKE
                    c.drawLine(cx - s * 0.45f, cy + s * 1.15f, cx + s * 0.45f, cy + s * 1.15f, iconPaint)
                }
                iconPaint.style = Paint.Style.STROKE
            }
            KeyType.DELETE -> {
                iconPaint.color = lc
                iconPaint.style = Paint.Style.STROKE
                iconPaint.strokeWidth = dp(1.8f)
                val w = dp(11f)
                val h = dp(8f)
                path.reset()
                path.moveTo(cx - w, cy)
                path.lineTo(cx - w * 0.4f, cy - h)
                path.lineTo(cx + w, cy - h)
                path.lineTo(cx + w, cy + h)
                path.lineTo(cx - w * 0.4f, cy + h)
                path.close()
                c.drawPath(path, iconPaint)
                val xc = cx + w * 0.25f
                val d = h * 0.45f
                c.drawLine(xc - d, cy - d, xc + d, cy + d, iconPaint)
                c.drawLine(xc - d, cy + d, xc + d, cy - d, iconPaint)
            }
            KeyType.ENTER -> {
                val label = enterLabel
                if (label != null) {
                    textPaint.color = theme.accentText
                    textPaint.textSize = dp(14f)
                    c.drawText(label, cx, baseline(cy), textPaint)
                } else {
                    iconPaint.color = theme.accentText
                    iconPaint.style = Paint.Style.STROKE
                    iconPaint.strokeWidth = dp(2f)
                    val s = dp(8f)
                    path.reset()
                    path.moveTo(cx + s, cy - s * 0.8f)
                    path.lineTo(cx + s, cy + s * 0.2f)
                    path.lineTo(cx - s, cy + s * 0.2f)
                    path.moveTo(cx - s + s * 0.55f, cy + s * 0.2f - s * 0.55f)
                    path.lineTo(cx - s, cy + s * 0.2f)
                    path.lineTo(cx - s + s * 0.55f, cy + s * 0.2f + s * 0.55f)
                    c.drawPath(path, iconPaint)
                }
            }
            KeyType.SPACER -> {}
        }
    }

    private fun drawOverlays(c: Canvas, now: Long) {
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            val k = p.key ?: continue
            val popup = p.popup
            if (popup != null) {
                drawPopup(c, popup)
            } else if (k.pressed && k.def.type == KeyType.CHAR && prefs.keyPreview) {
                drawBubble(c, k, now)
            }
        }
    }

    private fun drawBubble(c: Canvas, k: Key, now: Long) {
        val label = applyShift(k.def.label)
        val bw = max(k.draw.width() * 1.3f, dp(48f))
        val bh = dp(54f)
        var left = k.cell.centerX() - bw / 2f
        left = left.coerceIn(dp(2f), max(dp(2f), width - bw - dp(2f)))
        val bottom = k.draw.top + dp(4f)
        val top = bottom - bh
        val s = 0.85f + 0.15f * ease(min(1f, (now - k.downAt) / 70f))
        c.save()
        c.scale(s, s, left + bw / 2f, bottom)
        val rad = dp(prefs.keyRadius) + dp(4f)
        keyPaint.shader = null
        keyPaint.color = theme.bubble
        c.drawRoundRect(left, top, left + bw, bottom, rad, rad, keyPaint)
        borderPaint.shader = null
        borderPaint.color = withAlpha(theme.borderTop, 120)
        c.drawRoundRect(left, top, left + bw, bottom, rad, rad, borderPaint)
        textPaint.color = theme.label
        textPaint.textSize = dp(30f)
        c.drawText(label, left + bw / 2f, baseline(top + bh / 2f - dp(2f)), textPaint)
        c.restore()
    }

    private fun drawPopup(c: Canvas, p: Popup) {
        val rad = dp(14f)
        keyPaint.shader = null
        keyPaint.color = theme.bubble
        c.drawRoundRect(p.rect, rad, rad, keyPaint)
        borderPaint.shader = null
        borderPaint.color = withAlpha(theme.borderTop, 120)
        c.drawRoundRect(p.rect, rad, rad, borderPaint)
        textPaint.textSize = dp(22f)
        for (i in p.alts.indices) {
            val l = p.rect.left + p.pad + i * p.cellW
            if (i == p.sel) {
                keyPaint.color = theme.accent
                c.drawRoundRect(l, p.rect.top + dp(4f), l + p.cellW, p.rect.bottom - dp(4f), dp(10f), dp(10f), keyPaint)
                textPaint.color = theme.accentText
            } else {
                textPaint.color = theme.label
            }
            c.drawText(p.alts[i], l + p.cellW / 2f, baseline(p.rect.centerY()), textPaint)
        }
    }

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = e.actionIndex
                handleDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = ptrs.get(e.getPointerId(i)) ?: continue
                    handleMove(p, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val p = ptrs.get(e.getPointerId(i))
                if (p != null) handleUp(p, e.getX(i), e.getY(i), false)
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? {
        if (y < stripH()) return null
        var best: Key? = null
        var bd = Float.MAX_VALUE
        for (k in keys) {
            val c = k.cell
            val dx = max(max(c.left - x, 0f), x - c.right)
            val dy = max(max(c.top - y, 0f), y - c.bottom)
            val d = dx * dx + dy * dy
            if (d < bd) {
                bd = d
                best = k
            }
            if (d == 0f) break
        }
        val lim = dp(28f)
        return if (bd <= lim * lim) best else null
    }

    private fun handleDown(id: Int, x: Float, y: Float) {
        ptrs.get(id)?.let { cancelCallbacks(it) }
        val p = Ptr(id)
        p.x = x
        p.y = y
        ptrs.put(id, p)
        if (y < stripH()) {
            p.toolbar = true
            return
        }
        val k = keyAt(x, y) ?: return
        pressKey(p, k, x, y, slide = false)
    }

    private fun pressKey(p: Ptr, k: Key, x: Float, y: Float, slide: Boolean) {
        val now = SystemClock.uptimeMillis()
        p.key = k
        k.pressed = true
        k.downAt = now
        k.rippleX = x
        k.rippleY = y
        k.rippleAt = now
        if (!slide) feedback(k.def.type)
        when (k.def.type) {
            KeyType.DELETE -> if (!slide) {
                listener?.onDelete()
                scheduleRepeat(p)
            }
            KeyType.CHAR -> if (k.def.alts.isNotEmpty()) scheduleLongPress(p, k)
            else -> {}
        }
        invalidate()
    }

    private fun handleMove(p: Ptr, x: Float, y: Float) {
        p.x = x
        p.y = y
        if (p.toolbar) return
        val popup = p.popup
        if (popup != null) {
            val idx = popupIndex(popup, x)
            if (idx != popup.sel) {
                popup.sel = idx
                Haptics.tap(context, prefs.hapticLevel)
                invalidate()
            }
            return
        }
        val cur = p.key
        val nk = keyAt(x, y)
        if (nk != null && nk !== cur) {
            cancelCallbacks(p)
            if (cur != null) {
                cur.pressed = false
                cur.upAt = SystemClock.uptimeMillis()
            }
            pressKey(p, nk, x, y, slide = true)
        }
    }

    private fun handleUp(p: Ptr, x: Float, y: Float, cancel: Boolean) {
        ptrs.remove(p.id)
        cancelCallbacks(p)
        if (p.toolbar) {
            if (!cancel && settingsRect.contains(x, y)) {
                Haptics.tap(context, prefs.hapticLevel)
                listener?.onOpenSettings()
            }
            return
        }
        val k = p.key ?: return
        k.pressed = false
        k.upAt = SystemClock.uptimeMillis()
        invalidate()
        if (cancel) return

        val popup = p.popup
        if (popup != null) {
            commit(popup.alts[popup.sel])
            return
        }
        when (k.def.type) {
            KeyType.CHAR -> commit(applyShift(k.def.label))
            KeyType.SPACE -> listener?.onText(" ")
            KeyType.ENTER -> listener?.onEnter()
            KeyType.SHIFT -> onShiftTap()
            KeyType.SYMBOLS -> switchPage(if (page == Page.LETTERS) Page.SYMBOLS1 else Page.LETTERS)
            KeyType.SYMBOLS_ALT -> switchPage(if (page == Page.SYMBOLS1) Page.SYMBOLS2 else Page.SYMBOLS1)
            KeyType.DELETE, KeyType.SPACER -> {}
        }
    }

    private fun commit(text: String) {
        if (shift == Shift.ONCE) {
            shift = Shift.OFF
            autoShifted = false
            invalidate()
        }
        listener?.onText(text)
    }

    private fun onShiftTap() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == Shift.LOCK -> Shift.OFF
            now - lastShiftTap < 300 -> Shift.LOCK
            shift == Shift.ONCE -> Shift.OFF
            else -> Shift.ONCE
        }
        autoShifted = false
        lastShiftTap = now
        invalidate()
    }

    private fun scheduleLongPress(p: Ptr, k: Key) {
        val r = Runnable {
            if (ptrs.get(p.id) === p && p.key === k && p.popup == null) showPopup(p, k)
        }
        p.longPress = r
        uiHandler.postDelayed(r, 330)
    }

    private fun scheduleRepeat(p: Ptr) {
        val r = object : Runnable {
            override fun run() {
                if (ptrs.get(p.id) !== p || p.key?.def?.type != KeyType.DELETE) return
                listener?.onDelete()
                Haptics.tap(context, min(1, prefs.hapticLevel))
                uiHandler.postDelayed(this, 45)
            }
        }
        p.repeat = r
        uiHandler.postDelayed(r, 380)
    }

    private fun cancelCallbacks(p: Ptr) {
        p.longPress?.let { uiHandler.removeCallbacks(it) }
        p.repeat?.let { uiHandler.removeCallbacks(it) }
        p.longPress = null
        p.repeat = null
    }

    private fun cancelAll() {
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            cancelCallbacks(p)
            p.key?.pressed = false
        }
        ptrs.clear()
        invalidate()
    }

    private fun showPopup(p: Ptr, k: Key) {
        val alts = k.def.alts.map { applyShift(it) }
        val n = alts.size
        val pad = dp(4f)
        val cellW = min(dp(44f), (width - dp(8f) - 2 * pad) / n)
        val total = cellW * n + 2 * pad
        var left = k.cell.centerX() - cellW / 2f - pad
        left = left.coerceIn(dp(2f), max(dp(2f), width - total - dp(2f)))
        val bottom = k.draw.top + dp(2f)
        val rect = RectF(left, bottom - dp(52f), left + total, bottom)
        val popup = Popup(alts, rect, cellW, pad)
        popup.sel = popupIndex(popup, p.x)
        p.popup = popup
        Haptics.tap(context, prefs.hapticLevel)
        invalidate()
    }

    private fun popupIndex(p: Popup, x: Float): Int =
        ((x - (p.rect.left + p.pad)) / p.cellW).toInt().coerceIn(0, p.alts.size - 1)

    private fun feedback(type: KeyType) {
        val heavy = type == KeyType.SHIFT || type == KeyType.DELETE || type == KeyType.ENTER ||
            type == KeyType.SYMBOLS || type == KeyType.SYMBOLS_ALT
        Haptics.tap(context, prefs.hapticLevel, heavy)
        if (prefs.keySound) {
            val fx = when (type) {
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, -1f)
        }
    }
}

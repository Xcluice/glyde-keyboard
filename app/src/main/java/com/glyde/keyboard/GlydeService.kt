package com.glyde.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class GlydeService : InputMethodService(), KeyboardView.Listener {

    private lateinit var prefs: GlydePrefs
    private lateinit var feedback: Feedback
    private var board: KeyboardView? = null
    private var lastSpaceAt = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = GlydePrefs(this)
        feedback = Feedback(this)
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        v.applyPrefs(prefs)
        board = v
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val b = board ?: return
        b.applyPrefs(prefs)
        if (info != null) b.setEditorInfo(info, restarting)
        refreshShift()
    }

    private fun refreshShift() {
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val caps = info.inputType != EditorInfo.TYPE_NULL &&
            ic.getCursorCapsMode(info.inputType) != 0
        board?.setAutoShift(caps)
    }

    override fun onChar(text: String) {
        currentInputConnection?.commitText(text, 1)
        refreshShift()
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected != null && selected.isNotEmpty()) {
            ic.commitText("", 1)
        } else if (!ic.deleteSurroundingTextInCodePoints(1, 0)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        refreshShift()
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastSpaceAt < 350L) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceAt = 0L
                refreshShift()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpaceAt = now
        refreshShift()
    }

    override fun onEnter() {
        val info = currentInputEditorInfo
        val ic = currentInputConnection ?: return
        if (info != null) {
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val noAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        refreshShift()
    }

    override fun onMoveCursor(delta: Int) {
        sendDownUpKeyEvents(if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun onSettings() {
        val i = Intent(this, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    override fun onThemeCycle() {
        val ids = GlydeThemes.all.map { it.id }
        val next = ids[(ids.indexOf(prefs.theme) + 1).mod(ids.size)]
        prefs.theme = next
        board?.applyPrefs(prefs)
    }

    override fun onKeyFeedback(kind: Int) {
        feedback.tap(prefs, kind)
    }
}

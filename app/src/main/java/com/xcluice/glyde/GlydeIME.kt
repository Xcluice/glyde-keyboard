package com.xcluice.glyde

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class GlydeIME : InputMethodService(), GlydeKeyboardView.Listener {

    private var keyboard: GlydeKeyboardView? = null
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
    }

    override fun onCreateInputView(): View {
        val v = GlydeKeyboardView(this)
        v.listener = this
        keyboard = v
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val v = keyboard ?: return
        when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> v.showSymbols()
            else -> v.showLetters()
        }
        v.enterLabel = enterLabelFor(info)
        updateAutoCaps()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps()
    }

    // ------------------------------------------------------------ listener

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        if (text == " " && prefs.doubleSpacePeriod && isPlainText()) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                updateAutoCaps()
                return
            }
        }
        ic.commitText(text, 1)
        updateAutoCaps()
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoCaps()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        if (ei != null) {
            val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
            val noAction = (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        updateAutoCaps()
    }

    override fun onOpenSettings() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    // ------------------------------------------------------------ helpers

    private fun enterLabelFor(info: EditorInfo): String? {
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            else -> null
        }
    }

    private fun isPlainText(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        if ((t and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        return when (t and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
            else -> true
        }
    }

    private fun updateAutoCaps() {
        val v = keyboard ?: return
        if (!prefs.autoCap) {
            v.autoShift(false)
            return
        }
        val ei = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val mode = if ((ei.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            0
        } else {
            ic.getCursorCapsMode(ei.inputType)
        }
        v.autoShift(mode != 0)
    }
}

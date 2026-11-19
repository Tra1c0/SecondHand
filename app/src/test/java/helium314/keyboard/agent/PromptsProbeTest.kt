package helium314.keyboard.agent

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.prefs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class])
class PromptsProbeTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val switcher = KeyboardSwitcher.getInstance()
    @Test fun probe() {
        ShadowInputMethodService.reset()
        latinIME.prefs().edit(commit = true) { clear() }
        val root = switcher.onCreateInputView(latinIME, true)
        latinIME.setInputView(root)
        for (type in listOf(InputType.TYPE_CLASS_TEXT, InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE)) {
            ShadowInputMethodService.currentInputType = type
            val info = EditorInfo().apply { inputType = type }
            latinIME.onStartInput(info, false); latinIME.onStartInputView(info, false)
            switcher.reloadMainKeyboard()
            val kb = switcher.keyboard!!
            println("PROBE type=$type element=${kb.mId.element} comma=" + kb.sortedKeys.filter { it.label == "," || it.code == ','.code }.map { k -> k.popupKeys?.map { it.mCode to (it.mIconName ?: it.mLabel) } })
        }
    }
}

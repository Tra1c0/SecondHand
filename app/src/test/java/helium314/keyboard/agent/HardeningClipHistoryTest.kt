// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.clip.ClipKind
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 65 (S2, S8, S12): one keep rule for the vault and the history, and what does not outlive its use. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class HardeningClipHistoryTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val manager get() = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val keyguard get() = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val plain = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        load(plain)
        ClipboardDao.getInstance(latinIME)?.clear()
        ClipVault.clear()
    }

    @After fun tearDown() {
        shadowOf(keyguard).setIsDeviceLocked(false)
        ClipboardDao.getInstance(latinIME)?.clear()
        ClipVault.clear()
        latinIME.prefs().edit().clear().commit()
        load(plain)
    }

    private fun load(editor: EditorInfo, incognito: Boolean = false) {
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, incognito).commit()
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editor, false, latinIME.packageName))
    }

    private fun copy(text: String) = manager.setPrimaryClip(ClipData.newPlainText("", text))
    private fun historyTexts() = ClipboardDao.getInstance(latinIME)!!.getAll().map { it.text }

    private val text = "Einkaufsliste: Brot und Milch"

    @Test fun theControlCaseIsKept() {
        copy(text)
        assertTrue(historyTexts().contains(text))
    }

    @Test fun alwaysIncognitoKeepsNothing() {
        load(plain, incognito = true)
        copy(text)
        assertFalse(historyTexts().contains(text))
        copy("$text 2")
        assertFalse(historyTexts().contains("$text 2"))
    }

    @Test fun aFieldWithoutLearningKeepsNothing() {
        val noLearning = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        load(noLearning)
        copy(text)
        assertFalse(historyTexts().contains(text))
        assertTrue(ClipVault.secrets().isEmpty())
    }

    @Test fun aPasswordFieldKeepsNothingNotEvenAnUnmarkedClip() {
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        load(password)
        copy(text)
        assertFalse(historyTexts().contains(text))
    }

    @Test fun aLockedDeviceKeepsNothing() {
        shadowOf(keyguard).setIsDeviceLocked(true)
        copy(text)
        shadowOf(keyguard).setIsDeviceLocked(false)
        assertFalse(historyTexts().contains(text))
    }

    @Test fun aClipCopiedWithTheKeyboardHiddenIsJudgedByTheLastField() {
        // "current field" is the last field the keyboard saw: it cannot know where the clip came from
        val noLearning = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        load(noLearning)
        copy(text)
        assertFalse(historyTexts().contains(text))
        load(plain)
        copy("$text 3")
        assertTrue(historyTexts().contains("$text 3"))
    }

    @Test fun theRuleTable() {
        assertTrue(ClipVault.mayKeep(locked = false, alwaysIncognito = false, fieldIncognito = false))
        assertFalse(ClipVault.mayKeep(locked = true, alwaysIncognito = false, fieldIncognito = false))
        assertFalse(ClipVault.mayKeep(locked = false, alwaysIncognito = true, fieldIncognito = false))
        assertFalse(ClipVault.mayKeep(locked = false, alwaysIncognito = false, fieldIncognito = true))
        assertFalse(ClipVault.mayKeep(latinIME, null))
    }

    @Test fun destroyingTheServiceClearsTheVaultAndItsHooks() {
        ClipVault.offer("Einkaufsliste", false, System.currentTimeMillis(), "DE", true)
        ClipVault.onChange = { }
        assertNotNull(ClipVault.currentClip)
        latinIME.clipboardHistoryManager.onDestroy()
        assertNull(ClipVault.currentClip)
        assertNull(ClipVault.onChange)
        assertTrue(ClipVault.codes().isEmpty() && ClipVault.secrets().isEmpty())
        assertNull(ClipVault.freshest(latinIME, ClipKind.TEXT, 60_000))
    }

    @Test fun theFieldReferenceIsDroppedWhenTheFieldEnds() {
        val watcher = FieldCorrectionWatcher()
        val field = watcher.javaClass.getDeclaredField("reference").apply { isAccessible = true }
        val snapshot = field.type.declaredConstructors.first().apply { isAccessible = true }.newInstance(0, "text of the last field", true)
        field.set(watcher, snapshot)
        assertNotNull(field.get(watcher))
        watcher.end()
        assertNull(field.get(watcher))
    }
}

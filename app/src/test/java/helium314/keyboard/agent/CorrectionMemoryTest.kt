// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 20a: the rules that keep words and the memory of undone corrections. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CorrectionMemoryTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    private fun settings(vararg flags: Pair<String, Boolean>): helium314.keyboard.latin.settings.SettingsValues {
        val edit = app.prefs().edit().clear()
        flags.forEach { (key, value) -> edit.putBoolean(key, value) }
        edit.commit()
        Settings.getInstance().loadSettings(app, Locale.GERMANY, InputAttributes(EditorInfo(), false, app.packageName))
        return Settings.getValues()
    }

    @Before fun clear() {
        RejectedCorrections.clear(app)
    }

    @After fun restore() {
        RejectedCorrections.clear(app)
        settings()
    }

    @Test fun umlautPairsAreRecognisedOtherAccentsAreNot() {
        assertTrue(CorrectionGuard.differsOnlyByUmlaut("mochte", "möchte"))
        assertTrue(CorrectionGuard.differsOnlyByUmlaut("schon", "schön"))
        assertTrue(CorrectionGuard.differsOnlyByUmlaut("Wurde", "würde"))
        assertTrue(CorrectionGuard.differsOnlyByUmlaut("zahlen", "zählen"))
        assertFalse(CorrectionGuard.differsOnlyByUmlaut("esta", "está")) // Spanish accent: correction stays allowed
        assertFalse(CorrectionGuard.differsOnlyByUmlaut("ne", "né"))
        assertFalse(CorrectionGuard.differsOnlyByUmlaut("möchte", "möchte"))
        assertFalse(CorrectionGuard.differsOnlyByUmlaut("Möchte", "möchte")) // only case
        assertFalse(CorrectionGuard.differsOnlyByUmlaut("mochte", "machte"))
    }

    @Test fun guardOnlyEverBlocks() {
        val never: (String, String) -> Boolean = { _, _ -> false }
        fun allows(typed: String, correction: String, known: Boolean, keepValid: Boolean = false, keepUmlaut: Boolean = true,
                   rejected: (String, String) -> Boolean = never) =
            CorrectionGuard.allowsAutoCorrection(typed, correction, known, keepValid, keepUmlaut, rejected)
        assertTrue(allows("henlo", "hello", known = false))
        assertTrue(allows("mochte", "möchte", known = false)) // not a word: correcting is right
        assertFalse(allows("mochte", "möchte", known = true))
        assertTrue(allows("mochte", "möchte", known = true, keepUmlaut = false))
        assertTrue(allows("hell", "hello", known = true))
        assertFalse(allows("hell", "hello", known = true, keepValid = true))
        assertFalse(allows("henlo", "hello", known = false, rejected = { t, c -> t == "henlo" && c == "hello" }))
    }

    @Test fun pairsAreStoredOnlyWhenAllowed() {
        RejectedCorrections.remember(app, "henlo", "hello", settings())
        assertFalse(RejectedCorrections.contains(app, "henlo", "hello")) // opt-in

        val on = AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED to true
        RejectedCorrections.remember(app, "henlo", "hello", settings(on, AgentPreferences.LEARNING_PAUSED to true))
        RejectedCorrections.remember(app, "henlo", "hello", settings(on, Settings.PREF_ALWAYS_INCOGNITO_MODE to true))
        assertFalse(RejectedCorrections.contains(app, "henlo", "hello"))

        val allowed = settings(on)
        RejectedCorrections.remember(app, "Berlin", "berlin", allowed) // only case: not stored
        RejectedCorrections.remember(app, " ", "hello", allowed)
        assertEquals(0, RejectedCorrections.all(app).size)
        RejectedCorrections.remember(app, "Henlo", "Hello", allowed)
        assertTrue(RejectedCorrections.contains(app, "henlo", "hello"))
        assertTrue(RejectedCorrections.contains(app, "HENLO", "hello"))
        assertFalse(RejectedCorrections.contains(app, "hello", "henlo")) // direction matters
    }

    @Test fun pairsSurviveARestartAreBoundedAndCanBeForgotten() {
        val allowed = settings(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED to true)
        repeat(RejectedCorrections.MAX_PAIRS + 5) { RejectedCorrections.remember(app, "typo$it", "word$it", allowed) }
        RejectedCorrections.remember(app, "typo7", "word7", allowed) // used again: moves to the end
        RejectedCorrections.resetForTest() // as after a restart of the keyboard
        assertEquals(RejectedCorrections.MAX_PAIRS, RejectedCorrections.all(app).size)
        assertFalse(RejectedCorrections.contains(app, "typo0", "word0")) // oldest dropped
        assertTrue(RejectedCorrections.contains(app, "typo7", "word7"))
        assertTrue(RejectedCorrections.contains(app, "typo504", "word504"))
        assertEquals("typo7" to "word7", RejectedCorrections.all(app).last())

        RejectedCorrections.clear(app)
        RejectedCorrections.resetForTest()
        assertEquals(0, RejectedCorrections.all(app).size)
    }
}

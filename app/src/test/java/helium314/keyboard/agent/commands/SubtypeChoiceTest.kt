// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.utils.SubtypeSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 24g fix (P66): a language command's choice holds only for the same field and subtype. */
@RunWith(RobolectricTestRunner::class)
class SubtypeChoiceTest {
    private lateinit var german: InputMethodSubtype
    private lateinit var spanish: InputMethodSubtype

    private fun field(id: Int = 3, pkg: String? = "com.example", type: Int = InputType.TYPE_CLASS_TEXT) =
        EditorInfo().apply { fieldId = id; packageName = pkg; inputType = type }

    @Before fun setUp() {
        german = SubtypeSettings.getResourceSubtypesForLocale("de".constructLocale()).first()
        spanish = SubtypeSettings.getResourceSubtypesForLocale("es".constructLocale()).first()
        SubtypeChoice.clear()
    }

    @After fun tearDown() = SubtypeChoice.clear()

    @Test fun nothingHoldsWithoutAChoice() {
        assertFalse(SubtypeChoice.holds(field(), german))
    }

    @Test fun theSameFieldRestartingKeepsTheChoice() {
        SubtypeChoice.remember(field(), spanish)
        // the restart's EditorInfo is a new object; another text variation of the same class counts as the same field
        assertTrue(SubtypeChoice.holds(field(type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE), spanish))
        assertTrue(SubtypeChoice.holds(field(), spanish))
    }

    @Test fun anotherFieldEndsTheChoice() {
        SubtypeChoice.remember(field(), spanish)
        assertFalse(SubtypeChoice.holds(field(id = 4), spanish))
        assertFalse(SubtypeChoice.holds(field(), spanish)) // forgotten, also for the first field
    }

    @Test fun anotherAppOrInputClassEndsTheChoice() {
        SubtypeChoice.remember(field(), spanish)
        assertFalse(SubtypeChoice.holds(field(pkg = "org.other"), spanish))
        SubtypeChoice.remember(field(), spanish)
        assertFalse(SubtypeChoice.holds(field(type = InputType.TYPE_CLASS_NUMBER), spanish))
    }

    @Test fun anotherSubtypeInUseEndsTheChoice() {
        // e.g. the language key was used since
        SubtypeChoice.remember(field(), spanish)
        assertFalse(SubtypeChoice.holds(field(), german))
        assertFalse(SubtypeChoice.holds(field(), spanish))
    }

    @Test fun noEditorRemembersNothing() {
        SubtypeChoice.remember(null, spanish)
        assertFalse(SubtypeChoice.holds(field(), spanish))
        SubtypeChoice.remember(field(), spanish)
        assertFalse(SubtypeChoice.holds(null, spanish))
        assertFalse(SubtypeChoice.holds(field(), spanish))
    }
}

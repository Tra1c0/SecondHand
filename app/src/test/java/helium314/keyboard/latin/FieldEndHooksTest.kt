// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.ClipVault
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 65 (S12): the two `LatinIME` lines that end a field drop the field's text and the private-field state. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FieldEndHooksTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)

    @After fun tearDown() { ClipVault.fieldFinished(null) }

    /** Fills the watcher's reference as a checkpoint would (it holds up to about 1300 characters of the field). */
    private fun fill(): () -> Any? {
        val watcher = latinIME.mInputLogic.mFieldCorrections
        val field = watcher.javaClass.getDeclaredField("reference").apply { isAccessible = true }
        val snapshot = field.type.declaredConstructors.first().apply { isAccessible = true }.newInstance(0, "text of the last field", true)
        field.set(watcher, snapshot)
        ClipVault.fieldStarted(true)
        assertNotNull(field.get(watcher))
        assertTrue(ClipVault.isPrivateFieldActive())
        return { field.get(watcher) }
    }

    @Test fun finishingTheInputDropsTheFieldText() {
        val reference = fill()
        latinIME.onFinishInputInternal()
        assertNull(reference())
        assertFalse(ClipVault.isPrivateFieldActive())
    }

    @Test fun hidingTheInputViewDropsTheFieldText() {
        val reference = fill()
        latinIME.onFinishInputViewInternal(false)
        assertNull(reference())
        assertFalse(ClipVault.isPrivateFieldActive())
    }
}

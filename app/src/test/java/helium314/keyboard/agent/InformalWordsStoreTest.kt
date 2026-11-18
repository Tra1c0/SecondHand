// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 71: the built-in informal words are read from the app's real assets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InformalWordsStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before @After fun reset() = InformalWordsStore.resetForTest()

    @Test fun `the list is shipped in the assets`() {
        assertTrue(InformalWordsStore.size(context) in 2000..4000)
    }

    @Test fun `listed and expressive words are protected, ordinary words are not`() {
        assertTrue(InformalWordsStore.protects(context, "gecovert", "de", "de"))
        assertTrue(InformalWordsStore.protects(context, "Lowkey", "en", null))
        assertTrue(InformalWordsStore.protects(context, "neeee", "de", "de"))
        assertFalse(InformalWordsStore.protects(context, "nicht", "de", "de"))
        assertFalse(InformalWordsStore.protects(context, "gecovrt", "de", "de"), "a typo of a listed word is a typo")
    }

    @Test fun `only German and English typing is covered`() {
        assertFalse(InformalWordsStore.protects(context, "gecovert", "es", "es"))
        assertTrue(InformalWordsStore.protects(context, "gecovert", "es", "de"))
        assertFalse(InformalWordsStore.protects(context, "gecovert", null, null))
    }

    @Test fun `no context, no word, no protection`() {
        assertFalse(InformalWordsStore.protects(null, "gecovert", "de"))
        assertFalse(InformalWordsStore.protects(context, "", "de"))
        assertEquals(false, InformalWordsStore.protects(context, "gecovert"))
    }
}

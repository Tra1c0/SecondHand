// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale
import org.junit.Test
import kotlin.test.assertEquals

class LocalTextActionsTest {
    @Test fun casingPreservesEmojiAndHandlesGermanExpansion() {
        assertEquals("GRÜSSE 😀", LocalTextAction.UPPERCASE.transform("Grüße 😀"))
        assertEquals("straße 😀", LocalTextAction.LOWERCASE.transform("STRAẞE 😀"))
    }

    @Test fun casingDoesNotDependOnTheDevicesTurkishLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("I", LocalTextAction.UPPERCASE.transform("i"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun cleanSpacesKeepsParagraphBoundaries() {
        assertEquals("Hallo Welt\nNoch eine Zeile", LocalTextAction.CLEAN_SPACES.transform("  Hallo\t Welt\nNoch  eine Zeile  "))
    }
}

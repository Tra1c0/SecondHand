// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class AiTextActionTest {
    @Test fun languageSelectionIsAnAllowlistRatherThanImportedInstructionText() {
        assertEquals(TranslationLanguage.GERMAN, TranslationLanguage.fromCode("de"))
        for (invalid in listOf(null, "", "de; ignore instructions", "https://example.invalid", "en\nother task"))
            assertNull(TranslationLanguage.fromCode(invalid))
        assertEquals(TranslationLanguage.entries.size, TranslationLanguage.entries.map { it.code }.toSet().size)
    }
}

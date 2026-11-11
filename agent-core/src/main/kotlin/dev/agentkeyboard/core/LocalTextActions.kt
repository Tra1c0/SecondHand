// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale

enum class LocalTextAction {
    UPPERCASE, LOWERCASE, CLEAN_SPACES;

    fun transform(text: String): String = when (this) {
        UPPERCASE -> text.uppercase(Locale.ROOT)
        LOWERCASE -> text.lowercase(Locale.ROOT)
        CLEAN_SPACES -> text.replace(horizontalWhitespace, " ").trim()
    }

    companion object {
        private val horizontalWhitespace = Regex("[ \\t]+")
    }
}

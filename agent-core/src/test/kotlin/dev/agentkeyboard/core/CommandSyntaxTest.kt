// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class CommandSyntaxTest {
    private val names = listOf("math", "contact", "conv", "url")
    private fun parse(text: String, prefix: Char = '@') = CommandSyntax.parse(text, prefix, names)

    @Test fun barePrefixOffersAllNamesAndAPartialNameTheMatchingOnes() {
        assertEquals(CommandNames(0, "@", '@', "", listOf("contact", "conv", "math", "url")), parse("@"))
        assertEquals(CommandNames(6, "@co", '@', "co", listOf("contact", "conv")), parse("Hallo @co"))
        assertEquals(listOf("math"), (parse("@MA") as CommandNames).candidates) // case does not matter
        assertNull(parse("@lewin")) // a mention, no command
        assertNull(parse("@x"))
    }

    @Test fun completeNameWithAndWithoutArgument() {
        assertEquals(CommandCall(0, "@math", '@', "math", ""), parse("@math"))
        assertEquals(CommandCall(4, "@Math ", '@', "math", ""), parse("so: @Math "))
        assertEquals(CommandCall(0, "@contact Anna Be", '@', "contact", "Anna Be"), parse("@contact Anna Be"))
        assertEquals(CommandCall(1, "@url heise.de", '@', "url", "heise.de"), parse("(@url heise.de"))
        assertEquals(CommandCall(0, "@math 3 @lewin", '@', "math", "3 @lewin"), parse("@math 3 @lewin"))
    }

    @Test fun mailAddressesUnknownNamesAndOtherLinesNeverCount() {
        assertNull(parse("lewin@math"))
        assertNull(parse("mail an a@contact"))
        assertNull(parse("@lewin hallo"))
        assertNull(parse("@mathe")) // a longer word is not the command
        assertNull(parse("@math\n3+4"))
        assertNull(parse("@math  ")?.takeIf { (it as CommandCall).argument.isEmpty() })
        assertEquals(CommandCall(6, "@url", '@', "url", ""), parse("Zeile\n@url"))
        assertNull(parse("@math", '#'))
        assertNull(parse("@math", '/')) // a word separator is no allowed prefix
        assertNull(CommandSyntax.parse("@math", '@', emptyList()))
    }

    @Test fun ownPrefix() {
        assertEquals(CommandCall(0, "#math", '#', "math", ""), parse("#math", '#'))
        assertTrue(CommandSyntax.ALLOWED_PREFIXES.all { CommandSyntax.isAllowedPrefix(it) })
        assertEquals('@', CommandSyntax.DEFAULT_PREFIX)
    }

    @Test fun lengthLimits() {
        val long = "x".repeat(CommandSyntax.MAX_ARGUMENT)
        assertEquals(long, (parse("@url $long") as CommandCall).argument)
        assertNull(parse("@url ${long}y"))
        // the longest possible command fills the window exactly and still counts
        val longest = "@contact " + "a".repeat(CommandSyntax.MAX_ARGUMENT)
        assertTrue(longest.length <= CommandSyntax.LOOKBACK)
        assertEquals("contact", (parse(longest) as CommandCall).name)
    }

    @Test fun commandWords() {
        assertTrue(CommandSyntax.isCommandWord("@math", '@', names))
        assertTrue(CommandSyntax.isCommandWord("@URL", '@', names))
        assertFalse(CommandSyntax.isCommandWord("@mathe", '@', names))
        assertFalse(CommandSyntax.isCommandWord("math", '@', names))
        assertFalse(CommandSyntax.isCommandWord("@", '@', names))
        assertFalse(CommandSyntax.isCommandWord("#math", '@', names))
    }
}

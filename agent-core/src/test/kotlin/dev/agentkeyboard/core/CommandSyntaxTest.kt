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

    @Test fun onlyNamedCommandsTakeLongArguments() {
        val long = "x".repeat(CommandSyntax.MAX_LONG_ARGUMENT)
        val longNames = setOf("contact")
        assertEquals(long, (CommandSyntax.parse("@contact $long", '@', names, longNames) as CommandCall).argument)
        assertNull(CommandSyntax.parse("@contact ${long}y", '@', names, longNames))
        // every other command keeps the short limit, also when another one is long
        assertNull(CommandSyntax.parse("@url " + "x".repeat(CommandSyntax.MAX_ARGUMENT + 1), '@', names, longNames))
        assertNull(parse("@contact " + "x".repeat(CommandSyntax.MAX_ARGUMENT + 1)))
        // the longest long command fills the window read by the caller and still counts
        val window = ("Hallo @contact $long").takeLast(CommandSyntax.LOOKBACK + 1)
        assertEquals("contact", (CommandSyntax.parse(window, '@', names, longNames) as CommandCall).name)
    }

    @Test fun commandWords() {
        assertTrue(CommandSyntax.isCommandWord("@math", '@', names))
        assertTrue(CommandSyntax.isCommandWord("@URL", '@', names))
        assertFalse(CommandSyntax.isCommandWord("@mathe", '@', names))
        assertFalse(CommandSyntax.isCommandWord("math", '@', names))
        assertFalse(CommandSyntax.isCommandWord("@", '@', names))
        assertFalse(CommandSyntax.isCommandWord("#math", '@', names))
    }

    // stage 24g
    private val all = listOf("math", "contact", "conv", "url", "translate", "lang", "ki", "termin", "clip")
    private val languages = LanguageMatcher(listOf("de", "en", "es").map { LanguageMatcher.Language(it, LanguageNames.classifiedAliases(it)) }, all)
    private val claim: (String) -> String? = { word -> "lang".takeIf { languages.match(word).isNotEmpty() } }
    private fun extended(text: String) = CommandSyntax.parse(text, '@', all, fuzzyNames = true, claim = claim)

    @Test fun typosOfNamesOnlyOfferTheNameToComplete() {
        assertEquals(CommandNames(0, "@mtah", '@', "mtah", listOf("math"), fuzzy = true), extended("@mtah"))
        assertEquals(listOf("translate"), (extended("x @tranlate") as CommandNames).candidates)
        assertEquals(listOf("contact"), (extended("@contcat") as CommandNames).candidates)
        // never a call, also not with an argument after it
        assertNull(extended("@mtah 3+4"))
        assertNull(extended("@lewin"))
        assertNull(extended("@max"))
        assertNull(extended("@xy")) // two letters: exact only
        // without the option nothing changes
        assertNull(CommandSyntax.parse("@mtah", '@', all))
        assertEquals(listOf("math"), CommandSyntax.closeNames("mtah", all))
        assertTrue(CommandSyntax.closeNames("ma", all).isEmpty())
        assertTrue(CommandSyntax.closeNames("math", listOf("math")).isEmpty()) // the name itself is no typo
        // after review: from four letters, same first letter, up to four letters only a swap
        for (mention in listOf("uri", "urs", "lana", "lange", "matt", "clio", "mathe", "ahtm", "urlx"))
            assertTrue(CommandSyntax.closeNames(mention, all).isEmpty(), mention)
        assertNull(extended("@matt"))
        assertNull(extended("@uri"))
        assertEquals(listOf("clip"), CommandSyntax.closeNames("cilp", all))
    }

    @Test fun aClaimOrTypoInsideTheArgumentOfARealCommandNeverTakesOver() {
        // the reviewer's examples, with a claim that takes every word, so they hold whatever the matcher says
        val greedy: (String) -> String? = { "lang" }
        fun parse(text: String) = CommandSyntax.parse(text, '@', all, longArguments = listOf("translate"), fuzzyNames = true, claim = greedy)
        assertEquals(CommandCall(0, "@ki schreib an @isa", '@', "ki", "schreib an @isa"), parse("@ki schreib an @isa"))
        assertEquals(CommandCall(0, "@termin Treffen mit @gerd", '@', "termin", "Treffen mit @gerd"), parse("@termin Treffen mit @gerd"))
        assertEquals(CommandCall(0, "@translate \"hola\" @es", '@', "translate", "\"hola\" @es"), parse("@translate \"hola\" @es"))
        assertEquals("ki", (parse("@ki frag @matt") as CommandCall).name)
        assertEquals("ki", (parse("@ki frag @mtah") as CommandCall).name) // a typo of a name inside the argument too
        assertEquals("ki", (parse("x @ki frag @sp hola") as CommandCall).name)
        // with the real language matcher as well
        assertEquals("translate", (CommandSyntax.parse("@translate \"hola\" @es", '@', all, listOf("translate"), true, claim) as CommandCall).name)
        assertEquals("termin", (extended("@termin Essen @sp") as CommandCall).name)
        // without an earlier command, or on a new line, the claim and the typo stay
        assertEquals("sp", (parse("Hallo @sp") as CommandCall).claimed)
        assertEquals("sp", (parse("@ki frag\n@sp") as CommandCall).claimed)
        assertTrue((parse("x @mtah") as CommandNames).fuzzy)
        assertEquals("sp", (parse("@sp @ger @sp") as CommandCall).claimed) // claims never take over from each other
        // a command typed by its name later on the line still wins, as before
        assertEquals("math", (parse("@ki frag @math") as CommandCall).name)
        assertEquals(listOf("math"), (parse("@ki frag @ma") as CommandNames).candidates)
        // an earlier command whose argument is too long is no command: the claim stays
        val long = "@ki " + "x".repeat(CommandSyntax.MAX_ARGUMENT) + " @sp"
        assertEquals("sp", (parse(long) as CommandCall).claimed)
    }

    @Test fun aCommandMayClaimUnknownWords() {
        assertEquals(CommandCall(0, "@sp", '@', "lang", "", claimed = "sp"), extended("@sp"))
        assertEquals(CommandCall(6, "@sp ", '@', "lang", "", claimed = "sp"), extended("Hallo @sp "))
        for (typed in listOf("spain", "spanisch", "spnaish", "espanol", "español", "ger", "deutsch", "deustch", "germany",
                "eng", "english", "englisch", "inglés", "usa", "Österreich"))
            assertEquals("lang", (extended("@$typed") as CommandCall).name, typed)
        assertEquals("español", (extended("@Español") as CommandCall).claimed)
        // a claimed word takes no argument: another word makes the line text again
        assertNull(extended("@sp hola"))
        assertNull(extended("@sp  "))
        // the command's own name stays a normal call
        assertEquals(CommandCall(0, "@lang", '@', "lang", ""), extended("@lang"))
        // mail addresses, mentions and other punctuation never count
        assertNull(extended("a@spain"))
        assertNull(extended("@lewin"))
        assertNull(extended("@sp."))
        assertNull(extended("@max hallo"))
        // a claim may only name an enabled command
        assertNull(CommandSyntax.parse("@sp", '@', listOf("math"), fuzzyNames = true, claim = claim))
        // without a claim, accented words stay no command
        assertNull(CommandSyntax.parse("@español", '@', all, fuzzyNames = true))
    }

    @Test fun commandNamesAlwaysWinOverClaims() {
        assertEquals(CommandCall(0, "@math", '@', "math", ""), extended("@math"))
        assertEquals(CommandNames(0, "@u", '@', "u", listOf("url")), extended("@u"))
        assertEquals(CommandCall(0, "@ki", '@', "ki", ""), extended("@ki"))
        // a claim that would take a typo of a command name loses to the name
        val greedy: (String) -> String? = { "lang" }
        assertEquals(listOf("math"), (CommandSyntax.parse("@mtah", '@', all, fuzzyNames = true, claim = greedy) as CommandNames).candidates)
        assertNull(CommandSyntax.parse("@mtah 2", '@', all, fuzzyNames = true, claim = greedy))
        assertEquals(listOf("translate"), (CommandSyntax.parse("@tranlsate", '@', all, fuzzyNames = true, claim = greedy) as CommandNames).candidates)
        assertEquals("lang", (CommandSyntax.parse("@kino", '@', all, fuzzyNames = true, claim = greedy) as CommandCall).name)
    }

    @Test fun claimedWordsAreCommandWords() {
        assertTrue(CommandSyntax.isCommandWord("@sp", '@', all, claim))
        assertTrue(CommandSyntax.isCommandWord("@Español", '@', all, claim))
        assertFalse(CommandSyntax.isCommandWord("@sp", '@', all))
        assertFalse(CommandSyntax.isCommandWord("@lewin", '@', all, claim))
        assertFalse(CommandSyntax.isCommandWord("@sp", '@', listOf("math"), claim))
    }
}

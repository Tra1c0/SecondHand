// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.CorrectionRules.Diff
import dev.agentkeyboard.core.CorrectionRules.Kind
import org.junit.Test
import kotlin.test.*

class CorrectionRulesTest {
    private val qwertz = KeyAdjacency.QWERTZ
    private val qwerty = KeyAdjacency.QWERTY

    /** Old word unknown, new word known: the usual situation of a typo fix. */
    private fun kind(old: String, new: String, realOld: Boolean = false, realNew: Boolean = true, keys: KeyAdjacency = qwertz) =
        CorrectionRules.classify(old, new, realOld, realNew, keys)

    @Test fun typicalTyposAreTypos() {
        assertEquals(Kind.TYPO, kind("Hsus", "Haus")) // s next to a
        assertEquals(Kind.TYPO, kind("teh", "the", keys = qwerty)) // swapped
        assertEquals(Kind.TYPO, kind("fur", "für")) // umlaut
        assertEquals(Kind.TYPO, kind("Hus", "Haus")) // letter missing
        assertEquals(Kind.TYPO, kind("Haaus", "Haus")) // letter doubled
        assertEquals(Kind.TYPO, kind("nciht", "nicht")) // swapped
        assertEquals(Kind.TYPO, kind("Strase", "Straße")) // s and ß
        assertEquals(Kind.TYPO, kind("cafe", "café")) // other diacritic
        assertEquals(Kind.TYPO, kind("Wohnunf", "Wohnung")) // f next to g
        assertEquals(Kind.TYPO, kind("Wohnunf", "Wohnung", keys = qwerty))
        assertEquals(Kind.TYPO, kind("Mpntsg", "Montag")) // two neighbouring keys
        assertEquals(Kind.TYPO, kind("Tschüs", "Tschüss")) // two steps allowed for longer words
        assertEquals(Kind.TYPO, kind("hsus", "Haus")) // the case does not count
        assertEquals(Kind.TYPO, kind("Kidner", "Kinder"))
    }

    @Test fun realWordsAndUnknownTargetsAreRevisions() {
        assertEquals(Kind.REVISION, kind("Montag", "Freitag", realOld = true))
        assertEquals(Kind.REVISION, kind("das", "dass", realOld = true)) // a real word changed into another
        assertEquals(Kind.REVISION, kind("Haus", "Hsus", realOld = true, realNew = false)) // made worse
        assertEquals(Kind.REVISION, kind("Hsus", "Hous", realNew = false)) // the new word is not real either
        assertEquals(Kind.REVISION, kind("Hsus", "hsus")) // case only
        assertEquals(Kind.REVISION, kind("Hsus", "Hsus"))
    }

    @Test fun lowSimilarityIsARevision() {
        assertEquals(Kind.REVISION, kind("Mntg", "Freitag"))
        assertEquals(Kind.REVISION, kind("Hzus", "Haus")) // z is not next to a
        assertEquals(Kind.REVISION, kind("Hxxus", "Haus")) // doubled x, but x is far from a
        assertEquals(Kind.REVISION, kind("Mpntsgg", "Montag")) // three steps
        assertEquals(Kind.REVISION, kind("Hasus", "Haus")) // an extra letter that is not doubled
        assertEquals(Kind.REVISION, kind("Haus2", "Haus")) // digits
        assertEquals(Kind.REVISION, kind("abc", "xyz"))
    }

    @Test fun shortWordsAllowOneStep() {
        assertEquals(Kind.TYPO, kind("ubd", "und"))
        assertEquals(Kind.REVISION, kind("ubs", "und")) // two steps in a short word
        assertEquals(Kind.TYPO, kind("ihc", "ich"))
        assertEquals(Kind.TYPO, kind("ivh", "ich", keys = qwerty)) // v next to c
        assertEquals(Kind.REVISION, kind("hx", "ich"))
        // five letters: two steps are fine
        assertEquals(Kind.TYPO, kind("Hsuse", "Hause"))
    }

    @Test fun adjacencyFollowsTheLayout() {
        assertTrue(qwertz.adjacent('z', 'u')) // German: z between t and u
        assertFalse(qwerty.adjacent('z', 'u'))
        assertTrue(qwerty.adjacent('z', 'x'))
        assertTrue(qwertz.adjacent('a', 'q'))
        assertTrue(qwertz.adjacent('a', 'w'))
        assertFalse(qwertz.adjacent('a', 'e'))
        assertFalse(qwertz.adjacent('a', 'a'))
        // a layout from real key geometry (pixels)
        val keys = listOf(KeyBox('a', 50f, 150f, 100f, 100f), KeyBox('b', 150f, 150f, 100f, 100f), KeyBox('c', 350f, 150f, 100f, 100f),
            KeyBox('d', 100f, 250f, 100f, 100f))
        val grid = KeyAdjacency.fromKeys(keys)
        assertTrue(grid.adjacent('a', 'b'))
        assertFalse(grid.adjacent('a', 'c'))
        assertTrue(grid.adjacent('d', 'a'))
        assertTrue(grid.adjacent('D', 'b'))
        assertFalse(grid.adjacent('a', 'x'))
        assertEquals(Kind.TYPO, kind("bd", "ad", keys = grid))
    }

    @Test fun diffFindsOneChangedWord() {
        val single = CorrectionRules.diff("Ich wohne in einem Hsus mit Garten", "Ich wohne in einem Haus mit Garten")
        assertEquals(Diff.Single(CorrectionRules.Change(19, "Hsus", "Haus")), single)
        // the first word, the last word
        assertEquals(Diff.Single(CorrectionRules.Change(0, "Hsus", "Haus")), CorrectionRules.diff("Hsus ist da", "Haus ist da"))
        assertEquals(Diff.Single(CorrectionRules.Change(4, "Hsus", "Haus")), CorrectionRules.diff("ein Hsus", "ein Haus"))
        // a longer or shorter word
        assertEquals(Diff.Single(CorrectionRules.Change(4, "Hus", "Haus")), CorrectionRules.diff("ein Hus.", "ein Haus."))
    }

    @Test fun diffIgnoresTypingNewText() {
        assertEquals(Diff.None, CorrectionRules.diff("ein ", "ein Haus"))
        assertEquals(Diff.None, CorrectionRules.diff("ein Haus", "ein Haus und"))
        assertEquals(Diff.None, CorrectionRules.diff("ein Haus", "ein Haus"))
        assertEquals(Diff.None, CorrectionRules.diff("ein Haus", "ein Haus."))
        assertEquals(Diff.None, CorrectionRules.diff("", "Haus"))
        assertEquals(Diff.Several, CorrectionRules.diff("ein Haus und", "")) // the field was emptied (sent): never learned
    }

    @Test fun diffSeesSeveralChangedWords() {
        assertEquals(Diff.Several, CorrectionRules.diff("Hsus und Hof", "Haus uns Hof"))
        assertEquals(Diff.Several, CorrectionRules.diff("ein Hsus und", "ein Haus"))  // rest gone, not allowed here
        assertEquals(Diff.Several, CorrectionRules.diff("ein Hausboot", "ein Haus boot"))
        assertEquals(Diff.Several, CorrectionRules.diff("Ich Haus", "Ich bin Haus"))
        assertEquals(Diff.Several, CorrectionRules.diff("Montag oder Dienstag", "Freitag"))
    }

    @Test fun diffAllowsTheDeletedTailOnlyWhenAsked() {
        // backspace over "und dann", "Hsus" fixed, the rest not typed again yet
        assertEquals(Diff.Single(CorrectionRules.Change(4, "Hsus", "Haus")),
            CorrectionRules.diff("ein Hsus und dann", "ein Haus", tailMayBeDeleted = true))
        assertEquals(Diff.Several, CorrectionRules.diff("ein Hsus und dann", "ein Haus"))
        // text after the cursor that stayed: a normal single change
        assertEquals(Diff.Several, CorrectionRules.diff("ein Hsus und dann", "ein Haus und", tailMayBeDeleted = true))
    }

    @Test fun diffWithTextCutOffAtTheEnd() {
        // only the part before the limit is known: what is known must agree
        assertEquals(Diff.Single(CorrectionRules.Change(4, "Hsus", "Haus")),
            CorrectionRules.diff("ein Hsus und da", "ein Haus und d", oldComplete = false, newComplete = false))
        assertEquals(Diff.Several, CorrectionRules.diff("ein Hsus und da", "ein Haus uns d", oldComplete = false, newComplete = false))
        assertEquals(Diff.None, CorrectionRules.diff("ein Haus und da", "ein Haus und", oldComplete = false, newComplete = false))
    }
}

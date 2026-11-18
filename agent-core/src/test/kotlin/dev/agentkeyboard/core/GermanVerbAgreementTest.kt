// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GermanVerbAgreementTest {
    private val context = sequenceOf("nachträglich", "ich", "dass")

    @Test fun preservesAnAlreadyTypedVerbForm() {
        assertEquals("korrigiere", GermanVerbAgreement.prefer("korrigiere", "korrigieren", emptySequence(), context) {
            error("Preserving an explicit form needs no editor read")
        })
        assertEquals("korrigieren", GermanVerbAgreement.prefer("korrigieren", "korrigiere", emptySequence(), context) { null })
    }

    @Test fun recoversATypoUsingTheSubjectAndAnExistingCandidate() {
        assertEquals("korrigiere", GermanVerbAgreement.prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"), context) { "" })
        assertEquals("sortierst", GermanVerbAgreement.prefer("sorteirst", "sortieren", sequenceOf("sortierst"),
            sequenceOf("jetzt", "du")) { "" })
    }

    @Test fun anExistingModalCanRequireTheInfinitive() {
        assertEquals("korrigieren", GermanVerbAgreement.prefer("korrigeiren", "korrigiere", sequenceOf("korrigieren"),
            context) { " muss." })
        assertEquals("korrigieren", GermanVerbAgreement.prefer("korrigeiren", "korrigiere", sequenceOf("korrigieren"),
            sequenceOf("später", "muss", "ich")) { error("The left context already has a modal") })
        assertEquals("korrigiere", GermanVerbAgreement.prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            context) { ". Muss das sein?" })
    }

    @Test fun leavesUnrelatedOrAmbiguousSuggestionsAlone() {
        assertNull(GermanVerbAgreement.prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            sequenceOf("nachträglich")) { error("No clear subject") })
        assertNull(GermanVerbAgreement.prefer("korrigeire", "korrigieren", sequenceOf("verbessere"),
            context) { error("No competing form") })
        assertNull(GermanVerbAgreement.prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            context) { null })
        assertNull(GermanVerbAgreement.prefer("blume", "Blumen", emptySequence(), context) { null })
        assertNull(GermanVerbAgreement.prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            sequenceOf("das", "habe", "ich")) { error("Another verb already occupies the predicate") })
    }
}

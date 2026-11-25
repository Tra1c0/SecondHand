// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Stage 96: the word as it was before the delete key removed its characters. */
class WordBeforeDeleteTest {
    private fun deleted(memory: WordBeforeDelete, start: Int, before: String, after: String) {
        memory.beforeDelete(start, before)
        memory.afterDelete(after)
    }

    @Test fun theFormBeforeDeletingBeganIsKeptOverSeveralDeletes() {
        val memory = WordBeforeDelete()
        deleted(memory, 4, "löschen", "lösche")
        assertEquals("löschen", memory.originalFor(4, "lösche"))
        deleted(memory, 4, "lösche", "lösch")
        assertEquals("löschen", memory.originalFor(4, "lösch"))
    }

    @Test fun nothingBeforeADeleteOrForAnotherWord() {
        val memory = WordBeforeDelete()
        assertNull(memory.originalFor(0, "Haus"))
        deleted(memory, 4, "löschen", "lösche")
        assertNull(memory.originalFor(0, "lösche")) // another place
        assertNull(memory.originalFor(4, "löscht")) // the word changed since the last delete
        assertNull(memory.originalFor(4, "löschen")) // the original again
    }

    @Test fun aWordChangedInBetweenStartsANewRun() {
        val memory = WordBeforeDelete()
        deleted(memory, 0, "löschen", "lösch")
        // a picked suggestion made it "löschte", then deleting goes on at the same place
        deleted(memory, 0, "löschte", "löscht")
        assertEquals("löschte", memory.originalFor(0, "löscht"))
    }

    @Test fun forgottenWhenTheWordIsGoneTheCursorLeavesOrCleared() {
        val memory = WordBeforeDelete()
        deleted(memory, 0, "ab", "a")
        deleted(memory, 0, "a", "")
        assertNull(memory.originalFor(0, "a"))

        deleted(memory, 6, "Hallo", "Hall")
        memory.cursorMovedTo(8) // inside the word: kept
        assertEquals("Hallo", memory.originalFor(6, "Hall"))
        memory.cursorMovedTo(2) // another word
        assertNull(memory.originalFor(6, "Hall"))

        deleted(memory, 6, "Hallo", "Hall")
        memory.clear()
        assertNull(memory.originalFor(6, "Hall"))
    }
}

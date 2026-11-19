// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import helium314.keyboard.agent.security.credentialContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Stage 74a: the prompt store (permanent, own order, limits, locked device, unusable files). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PromptStoreTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var accessible = true
    private val store get() = PromptStore(app) { accessible }
    private val file get() = File(credentialContext(app).noBackupFilesDir, PromptStore.FILE_NAME)

    @Before fun clear() {
        accessible = true
        file.delete(); File(file.path + ".bak").delete()
    }

    private fun titles(s: PromptStore = store) = s.read().map { it.title }

    @Test fun survivesANewStoreInstanceInCredentialProtectedNoBackupStorage() {
        assertTrue(store.read().isEmpty())
        assertTrue(store.add("Mail", "Schreibe eine Mail").ok)
        // a new instance (as after a restart) reads the same
        val read = PromptStore(app) { true }.read()
        assertEquals(1, read.size)
        assertEquals("Mail", read[0].title)
        assertEquals("Schreibe eine Mail", read[0].text)
        assertTrue(file.exists())
        assertTrue(file.path.contains("no_backup"))
        assertFalse(credentialContext(app).isDeviceProtectedStorage)
        assertFalse(read[0].toString().contains("Mail")) // never shows content
    }

    @Test fun addPutsTheNewestFirstAndIdsAreUnique() {
        val a = store.add("a", "1").prompt!!
        val b = store.add("b", "2").prompt!!
        val c = store.add("", "3").prompt!!
        assertEquals(listOf("", "b", "a"), titles())
        assertEquals(3, setOf(a.id, b.id, c.id).size)
        assertEquals(listOf(c.id, b.id, a.id), store.read().map { it.id })
    }

    @Test fun updateKeepsThePositionAndChangesOnlyThatPrompt() {
        val a = store.add("a", "1").prompt!!
        val b = store.add("b", "2").prompt!!
        store.add("c", "3")
        assertTrue(store.update(b.id, "  b2  ", "zwei").ok)
        assertEquals(listOf("c", "b2", "a"), titles())
        assertEquals("zwei", store.read()[1].text)
        assertEquals(b.id, store.read()[1].id)
        assertEquals("1", store.read()[2].text)
        assertEquals(PromptError.NOT_FOUND, store.update("nope", "x", "y").error)
        assertEquals(PromptError.EMPTY_TEXT, store.update(a.id, "a", "  \n ").error)
        assertEquals(listOf("c", "b2", "a"), titles())
    }

    @Test fun deleteRemovesOnlyThatPrompt() {
        val a = store.add("a", "1").prompt!!
        store.add("b", "2")
        assertTrue(store.delete(a.id).ok)
        assertEquals(listOf("b"), titles())
        assertEquals(PromptError.NOT_FOUND, store.delete(a.id).error)
        assertEquals(listOf("b"), titles())
    }

    @Test fun reorderWithAPermutationPersists() {
        val a = store.add("a", "1").prompt!!
        val b = store.add("b", "2").prompt!!
        val c = store.add("c", "3").prompt!!
        assertEquals(listOf("c", "b", "a"), titles())
        assertTrue(store.reorder(listOf(a.id, c.id, b.id)).ok)
        assertEquals(listOf("a", "c", "b"), titles(PromptStore(app) { true }))
        // the order is kept when a prompt is added: the new one goes first, the rest stays
        store.add("d", "4")
        assertEquals(listOf("d", "a", "c", "b"), titles())
    }

    @Test fun reorderThatIsNoPermutationChangesNothing() {
        val a = store.add("a", "1").prompt!!
        val b = store.add("b", "2").prompt!!
        val before = file.readBytes()
        val version = PromptStore.version
        for (bad in listOf(listOf(a.id), listOf(b.id, a.id, "extra"), listOf(b.id, b.id), listOf(b.id, "other"),
            listOf(b.id, a.id, a.id), emptyList())) {
            val result = store.reorder(bad)
            assertFalse(result.ok, "$bad")
            assertEquals(PromptError.INVALID_ORDER, result.error)
        }
        assertEquals(listOf("b", "a"), titles())
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(version, PromptStore.version)
        // a deleted prompt makes an old order invalid (list changed while the dialog was open)
        store.delete(a.id)
        assertFalse(store.reorder(listOf(b.id, a.id)).ok)
        assertEquals(listOf("b"), titles())
    }

    @Test fun versionChangesWithEverySuccessfulWriteOnly() {
        val start = PromptStore.version
        val a = store.add("a", "1").prompt!!
        assertEquals(start + 1, PromptStore.version)
        store.update(a.id, "a", "2")
        store.reorder(listOf(a.id))
        store.delete(a.id)
        assertEquals(start + 4, PromptStore.version)
        store.add("", "")
        store.delete("nope")
        assertEquals(start + 4, PromptStore.version)
    }

    @Test fun textLimit() {
        assertTrue(store.add("", "x".repeat(PromptStore.MAX_TEXT)).ok)
        val tooLong = store.add("", "x".repeat(PromptStore.MAX_TEXT + 1))
        assertEquals(PromptError.TEXT_TOO_LONG, tooLong.error)
        assertEquals(PromptError.EMPTY_TEXT, store.add("t", "").error)
        assertEquals(PromptError.EMPTY_TEXT, store.add("t", " \n\t ").error)
        // UTF-16 characters, never cut inside a surrogate pair: a text ending in an emoji at the limit is rejected, not cut
        val emoji = "😀"
        assertTrue(store.add("", "x".repeat(PromptStore.MAX_TEXT - 2) + emoji).ok)
        assertEquals(PromptError.TEXT_TOO_LONG, store.add("", "x".repeat(PromptStore.MAX_TEXT - 1) + emoji).error)
        assertEquals(2, store.read().size)
        assertTrue(store.read().first().text.endsWith(emoji))
    }

    @Test fun titleLimit() {
        assertTrue(store.add("t".repeat(PromptStore.MAX_TITLE), "x").ok)
        assertEquals(PromptError.TITLE_TOO_LONG, store.add("t".repeat(PromptStore.MAX_TITLE + 1), "x").error)
        // spaces around the title do not count; it is trimmed and kept to one line
        assertTrue(store.add("  " + "t".repeat(PromptStore.MAX_TITLE) + "  ", "x").ok)
        assertEquals("t".repeat(PromptStore.MAX_TITLE), store.read().first().title)
        assertEquals("a b", store.add("a\nb", "x").prompt!!.title)
        assertEquals("", store.add("   ", "x").prompt!!.title)
        assertTrue(store.add("Mail 😀 für Äpfel ☃", "x").ok)
        assertEquals("Mail 😀 für Äpfel ☃", store.read().first().title)
    }

    @Test fun numberLimit() {
        repeat(PromptStore.MAX_PROMPTS) { assertTrue(store.add("p$it", "t").ok, "$it") }
        assertEquals(PromptError.FULL, store.add("one more", "t").error)
        assertEquals(PromptStore.MAX_PROMPTS, store.read().size)
        // a delete makes room again
        assertTrue(store.delete(store.read().last().id).ok)
        assertTrue(store.add("again", "t").ok)
        assertEquals(PromptStore.MAX_PROMPTS, store.read().size)
        assertEquals("again", store.read().first().title)
    }

    @Test fun newlinesEmojiAndSpacesComeBackUnchanged() {
        val text = "  Erste Zeile\r\nzweite \"Zeile\" \\ mit Emoji 😀👍 und \u0007 Tab\t\n\n  letzte Zeile  \n"
        assertTrue(store.add("", text).ok)
        assertEquals(text, PromptStore(app) { true }.read().single().text)
    }

    @Test fun lockedDeviceReadsEmptyAndWritesNothing() {
        val a = store.add("a", "1").prompt!!
        val before = file.readBytes()
        val version = PromptStore.version
        accessible = false
        assertTrue(store.read().isEmpty())
        assertEquals(null, store.load())
        assertEquals(PromptError.LOCKED, store.add("b", "2").error)
        assertEquals(PromptError.LOCKED, store.update(a.id, "x", "y").error)
        assertEquals(PromptError.LOCKED, store.delete(a.id).error)
        assertEquals(PromptError.LOCKED, store.reorder(listOf(a.id)).error)
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(version, PromptStore.version)
        accessible = true
        assertEquals(listOf("a"), titles())
    }

    @Test fun lockedDeviceNeverCreatesTheFile() {
        accessible = false
        assertFalse(store.add("a", "1").ok)
        assertFalse(file.exists())
    }

    @Test fun aCorruptFileReadsEmptyIsNotOverwrittenByReadingAndBlocksWrites() {
        file.parentFile!!.mkdirs()
        val garbage = "{ not json".toByteArray()
        file.writeBytes(garbage)
        assertTrue(store.read().isEmpty())
        assertEquals(null, store.load())
        assertTrue(garbage.contentEquals(file.readBytes()), "a read changed the file")
        // a later write fails and leaves the file as it is (as in the snippet store)
        assertEquals(PromptError.IO, store.add("a", "1").error)
        assertEquals(PromptError.IO, store.reorder(emptyList()).error)
        assertTrue(garbage.contentEquals(file.readBytes()), "a write replaced the unreadable file")
    }

    @Test fun aWellFormedFileWithWrongContentIsUnusableToo() {
        file.parentFile!!.mkdirs()
        for (bad in listOf("""{"storageVersion":2,"prompts":[]}""", """{"storageVersion":1,"prompts":[{"id":"a","title":"","text":" "}]}""",
            """{"storageVersion":1,"prompts":[{"id":"a","title":"","text":"x"},{"id":"a","title":"","text":"y"}]}""",
            """{"storageVersion":1,"prompts":[{"id":"a","title":"","text":"x","url":"u"}]}""")) {
            file.writeText(bad)
            assertTrue(store.read().isEmpty(), bad)
            assertFalse(store.add("a", "1").ok, bad)
            assertEquals(bad, file.readText())
        }
    }

    @Test fun anOversizedFileReadsEmptyAndIsNotOverwritten() {
        file.parentFile!!.mkdirs()
        val content = ByteArray(PromptStore.MAX_STORAGE_BYTES + 10) { ' '.code.toByte() }
        file.writeBytes(content)
        assertTrue(store.read().isEmpty())
        assertEquals(PromptError.IO, store.add("a", "1").error)
        assertEquals(content.size, file.readBytes().size)
        assertNotEquals(0, file.length())
    }

    @Test fun aWriteThatWouldNotFitInTheFileLimitIsRefusedAsFull() {
        // 8000 CJK characters are 24000 bytes: 100 of them are more than 2 MiB
        var failed: PromptResult? = null
        repeat(100) { if (failed == null) store.add("", "中".repeat(PromptStore.MAX_TEXT)).let { if (!it.ok) failed = it } }
        assertEquals(PromptError.FULL, failed?.error)
        assertTrue(file.length() <= PromptStore.MAX_STORAGE_BYTES)
        assertTrue(store.read().isNotEmpty())
    }
}

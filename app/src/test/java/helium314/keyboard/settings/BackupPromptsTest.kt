// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.app.Application
import helium314.keyboard.agent.PromptError
import helium314.keyboard.agent.PromptStore
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.settings.preferences.PROMPTS_ENTRY_NAME
import helium314.keyboard.settings.preferences.isPromptsEntry
import helium314.keyboard.settings.preferences.readPromptsEntry
import helium314.keyboard.settings.preferences.restorePromptsFromBackup
import helium314.keyboard.settings.preferences.writePromptsEntry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 76: the prompt store is part of the keyboard's own backup and restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupPromptsTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var accessible = true
    private val store get() = PromptStore(app) { accessible }
    private val file get() = File(credentialContext(app).noBackupFilesDir, PromptStore.FILE_NAME)

    @Before fun clear() {
        accessible = true
        file.delete(); File(file.path + ".bak").delete()
    }

    /** What the backup writes for the prompts, as a whole zip. */
    private fun backupZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("preferences.json")); zip.write("x".toByteArray()); zip.closeEntry()
            writePromptsEntry(zip, store)
        }
        return out.toByteArray()
    }

    private fun zipWith(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> entries.forEach { (n, b) -> zip.putNextEntry(ZipEntry(n)); zip.write(b); zip.closeEntry() } }
        return out.toByteArray()
    }

    private fun names(zip: ByteArray): List<String> {
        val result = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z -> generateSequence { z.nextEntry }.forEach { result.add(it.name) } }
        return result
    }

    private fun entryBytes(zip: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) { val e = z.nextEntry ?: return null; if (e.name == name) return z.readBytes() }
        }
    }

    /** What the restore does with a zip, as the restore loop does it: exact name match, bounded read, then the store. */
    private fun restore(zip: ByteArray): PromptError? {
        var bytes: ByteArray? = null
        var found = false
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (isPromptsEntry(e.name)) { bytes = readPromptsEntry(z); found = true }
            }
        }
        return restorePromptsFromBackup(store, found, bytes)
    }

    private fun titles() = store.read().map { it.title }

    @Test fun theBackupHasOneEntryWithTheExactBytesOfTheFile() {
        store.add("a", "eins"); store.add("b", "zwei")
        val zip = backupZip()
        assertEquals(1, names(zip).count { it == "agent/agent_prompts.json" })
        assertEquals("agent/agent_prompts.json", PROMPTS_ENTRY_NAME)
        assertContentEquals(file.readBytes(), entryBytes(zip, PROMPTS_ENTRY_NAME))
    }

    @Test fun noPromptsMeansNoEntryAndTheBackupStillWorks() {
        assertEquals(listOf("preferences.json"), names(backupZip()))
    }

    @Test fun anUnusableFileMeansNoEntry() {
        store.add("a", "eins")
        file.writeText("{ not json")
        assertEquals(listOf("preferences.json"), names(backupZip()))
        file.writeBytes(ByteArray(PromptStore.MAX_STORAGE_BYTES + 1) { 'a'.code.toByte() })
        assertEquals(listOf("preferences.json"), names(backupZip()))
    }

    @Test fun aLockedDeviceMeansNoEntry() {
        store.add("a", "eins")
        accessible = false
        assertEquals(listOf("preferences.json"), names(backupZip()))
    }

    @Test fun restoringAValidEntryReplacesTheStoredPromptsInOrder() {
        store.add("c", "3"); store.add("b", "2"); store.add("a", "1")
        val zip = backupZip()
        assertEquals(listOf("a", "b", "c"), titles())
        store.add("neu", "x"); store.delete(store.read().first { it.title == "b" }.id)
        val before = PromptStore.version
        assertNull(restore(zip))
        assertEquals(listOf("a", "b", "c"), titles())
        assertTrue(PromptStore.version > before)
    }

    @Test fun anOldBackupWithoutTheEntryKeepsTheExistingPrompts() {
        store.add("a", "1")
        assertNull(restore(zipWith("preferences.json" to "x".toByteArray())))
        assertEquals(listOf("a"), titles())
    }

    @Test fun aCorruptEntryKeepsTheExistingPromptsAndDoesNotThrow() {
        store.add("a", "1")
        val before = file.readBytes()
        assertEquals(PromptError.INVALID_TEXT, restore(zipWith(PROMPTS_ENTRY_NAME to "{\"storageVersion\":1,\"prompts\":[{\"id\":\"x\"}]}".toByteArray())))
        assertEquals(PromptError.INVALID_TEXT, restore(zipWith(PROMPTS_ENTRY_NAME to byteArrayOf(0xC3.toByte(), 0x28))))
        assertEquals(PromptError.INVALID_TEXT, restore(zipWith(PROMPTS_ENTRY_NAME to "garbage".toByteArray())))
        assertContentEquals(before, file.readBytes())
        assertEquals(listOf("a"), titles())
    }

    @Test fun anEntryThatIsTooLargeKeepsTheExistingPrompts() {
        store.add("a", "1")
        val big = ByteArray(PromptStore.MAX_STORAGE_BYTES + 10) { ' '.code.toByte() }
        assertNull(ZipInputStream(ByteArrayInputStream(zipWith(PROMPTS_ENTRY_NAME to big))).use { it.nextEntry; readPromptsEntry(it) })
        assertEquals(PromptError.INVALID_TEXT, restore(zipWith(PROMPTS_ENTRY_NAME to big)))
        assertEquals(listOf("a"), titles())
    }

    @Test fun aLockedDeviceDoesNotWritePrompts() {
        store.add("keep", "1")
        val zip = backupZip()
        store.add("other", "2")
        val before = file.readBytes()
        accessible = false
        assertEquals(PromptError.LOCKED, restore(zip))
        assertContentEquals(before, file.readBytes())
        accessible = true
        assertEquals(listOf("other", "keep"), titles())
    }

    @Test fun roundTripKeepsMultiLineTextAndEmoji() {
        store.add("Dritter 🎉", "Zeile 1\nZeile 2\n\n  eingerückt \"zitat\" \\ 👨‍👩‍👧 äöü")
        store.add("Zweiter", "Text mit Emoji 😀 und\r\nWindows-Zeilenende")
        store.add("", "Erster ohne Titel")
        val expected = store.read()
        val zip = backupZip()
        expected.forEach { store.delete(it.id) }
        assertTrue(store.read().isEmpty())
        assertNull(restore(zip))
        assertEquals(expected.map { Triple(it.id, it.title, it.text) }, store.read().map { Triple(it.id, it.title, it.text) })
        assertEquals(3, store.read().size)
    }

    @Test fun pathLikeEntryNamesAreIgnored() {
        store.add("a", "1")
        val valid = file.readBytes()
        store.add("b", "2")
        for (name in listOf("../agent_prompts.json", "agent/../agent_prompts.json", "agent_prompts.json", "/agent/agent_prompts.json", "unprotected/agent/agent_prompts.json", "agent/agent_prompts.json/")) {
            assertFalse(isPromptsEntry(name), name)
            assertNull(restore(zipWith(name to valid)), name)
            assertEquals(listOf("b", "a"), titles(), name)
        }
        assertNull(restore(zipWith(PROMPTS_ENTRY_NAME to valid)))
        assertEquals(listOf("a"), titles())
    }
}

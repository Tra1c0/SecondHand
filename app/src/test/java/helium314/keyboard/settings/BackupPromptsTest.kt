// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.app.Application
import helium314.keyboard.agent.PromptStore
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.settings.preferences.PROMPTS_ENTRY_NAME
import helium314.keyboard.settings.preferences.PromptsEntryCollector
import helium314.keyboard.settings.preferences.PromptsRestore
import helium314.keyboard.settings.preferences.isPromptsEntry
import helium314.keyboard.settings.preferences.promptsRestoreMessage
import helium314.keyboard.latin.R
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

    /**
     * What the restore does with a zip: the production [PromptsEntryCollector] and [restorePromptsFromBackup]. Only
     * the walk over the zip is rebuilt here (the real one sits in a composable and needs an Activity and the files
     * of the keyboard); it does nothing but offer every entry name to the collector.
     */
    private fun restore(zip: ByteArray): PromptsRestore {
        val collector = PromptsEntryCollector()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                collector.offer(e.name, z)
            }
        }
        return collector.apply(store)
    }

    /**
     * Two prompt entries in one zip: ZipOutputStream refuses duplicate names, so each entry comes from its own
     * one-entry zip and both are offered to the same production collector, in order (what the loop does for two entries).
     */
    private fun restoreInOrder(first: ByteArray, second: ByteArray): PromptsRestore {
        val collector = PromptsEntryCollector()
        for (bytes in listOf(first, second))
            ZipInputStream(ByteArrayInputStream(zipWith(PROMPTS_ENTRY_NAME to bytes))).use { z -> collector.offer(z.nextEntry.name, z) }
        return collector.apply(store)
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
        assertEquals(PromptsRestore.APPLIED, restore(zip))
        assertEquals(listOf("a", "b", "c"), titles())
        assertTrue(PromptStore.version > before)
    }

    @Test fun anOldBackupWithoutTheEntryKeepsTheExistingPrompts() {
        store.add("a", "1")
        assertEquals(PromptsRestore.NO_ENTRY, restore(zipWith("preferences.json" to "x".toByteArray())))
        assertEquals(listOf("a"), titles())
    }

    @Test fun aCorruptEntryKeepsTheExistingPromptsAndDoesNotThrow() {
        store.add("a", "1")
        val before = file.readBytes()
        assertEquals(PromptsRestore.CORRUPT, restore(zipWith(PROMPTS_ENTRY_NAME to "{\"storageVersion\":1,\"prompts\":[{\"id\":\"x\"}]}".toByteArray())))
        assertEquals(PromptsRestore.CORRUPT, restore(zipWith(PROMPTS_ENTRY_NAME to byteArrayOf(0xC3.toByte(), 0x28))))
        assertEquals(PromptsRestore.CORRUPT, restore(zipWith(PROMPTS_ENTRY_NAME to "garbage".toByteArray())))
        assertContentEquals(before, file.readBytes())
        assertEquals(listOf("a"), titles())
    }

    @Test fun anEntryThatIsTooLargeKeepsTheExistingPrompts() {
        store.add("a", "1")
        val big = ByteArray(PromptStore.MAX_STORAGE_BYTES + 10) { ' '.code.toByte() }
        assertNull(ZipInputStream(ByteArrayInputStream(zipWith(PROMPTS_ENTRY_NAME to big))).use { it.nextEntry; readPromptsEntry(it) })
        assertEquals(PromptsRestore.CORRUPT, restore(zipWith(PROMPTS_ENTRY_NAME to big)))
        assertEquals(listOf("a"), titles())
    }

    @Test fun aLockedDeviceDoesNotWritePrompts() {
        store.add("keep", "1")
        val zip = backupZip()
        store.add("other", "2")
        val before = file.readBytes()
        accessible = false
        assertEquals(PromptsRestore.LOCKED, restore(zip))
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
        assertEquals(PromptsRestore.APPLIED, restore(zip))
        assertEquals(expected.map { Triple(it.id, it.title, it.text) }, store.read().map { Triple(it.id, it.title, it.text) })
        assertEquals(3, store.read().size)
    }

    @Test fun pathLikeEntryNamesAreIgnored() {
        store.add("a", "1")
        val valid = file.readBytes()
        store.add("b", "2")
        for (name in listOf("../agent_prompts.json", "agent/../agent_prompts.json", "agent_prompts.json", "/agent/agent_prompts.json", "unprotected/agent/agent_prompts.json", "agent/agent_prompts.json/")) {
            assertFalse(isPromptsEntry(name), name)
            assertEquals(PromptsRestore.NO_ENTRY, restore(zipWith(name to valid)), name)
            assertEquals(listOf("b", "a"), titles(), name)
        }
        assertEquals(PromptsRestore.APPLIED, restore(zipWith(PROMPTS_ENTRY_NAME to valid)))
        assertEquals(listOf("a"), titles())
    }

    private val emptyFile = "{\"storageVersion\":1,\"prompts\":[]}".toByteArray()

    @Test fun aBackupOfAnEmptyListHasNoEntry() {
        val a = store.add("a", "1").prompt!!
        store.delete(a.id)
        assertEquals("{\"storageVersion\":1,\"prompts\":[]}", file.readText().replace(" ", "").replace("\n", ""))
        assertNull(store.exportBytes())
        assertEquals(listOf("preferences.json"), names(backupZip()))
    }

    @Test fun anEntryWithAnEmptyListKeepsTheExistingPrompts() {
        store.add("a", "1")
        assertEquals(PromptsRestore.NO_ENTRY, restore(zipWith(PROMPTS_ENTRY_NAME to emptyFile)))
        assertEquals(listOf("a"), titles())
    }

    @Test fun theMessageForEachReason() {
        assertEquals(R.string.restore_prompts_corrupt, promptsRestoreMessage(PromptsRestore.CORRUPT))
        assertEquals(R.string.restore_prompts_locked, promptsRestoreMessage(PromptsRestore.LOCKED))
        assertEquals(R.string.restore_prompts_unreadable, promptsRestoreMessage(PromptsRestore.UNREADABLE))
        assertNull(promptsRestoreMessage(PromptsRestore.NO_ENTRY))
        assertNull(promptsRestoreMessage(PromptsRestore.APPLIED))
        for (id in listOf(R.string.restore_prompts_corrupt, R.string.restore_prompts_locked, R.string.restore_prompts_unreadable))
            assertTrue(app.getString(id).isNotBlank())
    }

    @Test fun anUnreadableCurrentFileGivesUnreadableAndKeepsTheFile() {
        store.add("a", "1")
        val zip = backupZip()
        file.writeText("{ not json")
        assertEquals(PromptsRestore.UNREADABLE, restore(zip))
        assertEquals("{ not json", file.readText())
    }

    @Test fun aFailingAccessCheckReadsAsLockedAndNeverThrows() {
        store.add("a", "1"); val valid = file.readBytes()
        val throwing = PromptStore(app) { throw IllegalStateException("x") }
        assertEquals(PromptsRestore.LOCKED, restorePromptsFromBackup(throwing, true, valid))
    }

    @Test fun withTwoEntriesTheLastValidOneWins() {
        store.add("old", "0")
        store.add("a", "1"); val one = file.readBytes()
        store.add("b", "2"); val two = file.readBytes()
        file.delete(); File(file.path + ".bak").delete()
        assertEquals(PromptsRestore.APPLIED, restoreInOrder(one, two))
        assertEquals(listOf("b", "a", "old"), titles())
    }

    @Test fun withTwoEntriesAnInvalidLastOneKeepsTheExistingPrompts() {
        store.add("a", "1"); val valid = file.readBytes()
        store.add("keep", "k")
        assertEquals(PromptsRestore.CORRUPT, restoreInOrder(valid, "garbage".toByteArray()))
        assertEquals(listOf("keep", "a"), titles())
    }

    // ---- stage 77: folders in the backup ----

    @Test fun theBackupCarriesFoldersAndRestoringBringsThemBackWithTheirPrompts() {
        val work = store.addFolder("Arbeit").folder!!
        val home = store.addFolder("Privat").folder!!
        store.add("c", "3", home.id); store.add("b", "2", null); store.add("a", "1", work.id)
        val zip = backupZip()
        assertContentEquals(file.readBytes(), entryBytes(zip, PROMPTS_ENTRY_NAME))
        // the phone changes: another folder, a deleted one, a moved prompt
        store.deleteFolder(work.id); store.addFolder("Anderes")
        assertEquals(PromptsRestore.APPLIED, restore(zip))
        val library = store.loadLibrary()!!
        assertEquals(listOf("Arbeit", "Privat"), library.folders.map { it.name })
        assertEquals(listOf("a", "b", "c"), library.prompts.map { it.title })
        assertEquals(listOf(work.id, null, home.id), library.prompts.map { it.folderId })
    }

    @Test fun aStoreWithOnlyFoldersHasNoBackupEntry() {
        store.addFolder("Leer")
        assertEquals(listOf("preferences.json"), names(backupZip()))
    }

    @Test fun anEntryWithFoldersButNoPromptsNeverReplacesAStoreWithPromptsAndFolders() {
        val keep = store.addFolder("Bleibt").folder!!
        store.add("keep", "k", keep.id)
        val before = file.readBytes()
        val version = PromptStore.version
        val foldersOnly = "{\"storageVersion\":2,\"folders\":[{\"id\":\"f1\",\"name\":\"Fremd\"}],\"prompts\":[]}"
        assertEquals(PromptsRestore.NO_ENTRY, restore(zipWith(PROMPTS_ENTRY_NAME to foldersOnly.toByteArray())))
        assertContentEquals(before, file.readBytes())
        assertEquals(version, PromptStore.version)
        assertEquals(listOf("Bleibt"), store.loadLibrary()!!.folders.map { it.name })
        assertEquals(listOf("keep"), titles())
        // and onto an empty store it changes nothing either (no entry, no file)
        file.delete()
        assertEquals(PromptsRestore.NO_ENTRY, restore(zipWith(PROMPTS_ENTRY_NAME to foldersOnly.toByteArray())))
        assertFalse(file.exists())
    }

    @Test fun anOldBackupWithoutFoldersRestoresAsBefore() {
        // exactly what stage 76 wrote into a backup
        val old = "{\"storageVersion\":1,\"prompts\":[{\"id\":\"p-1\",\"title\":\"alt\",\"text\":\"Text\"}]}".toByteArray()
        store.addFolder("Heute"); store.add("jetzt", "x", store.loadLibrary()!!.folders.single().id)
        assertEquals(PromptsRestore.APPLIED, restore(zipWith(PROMPTS_ENTRY_NAME to old)))
        val library = store.loadLibrary()!!
        assertEquals(listOf("alt"), library.prompts.map { it.title })
        assertEquals(listOf(null), library.prompts.map { it.folderId })
        assertTrue(library.folders.isEmpty(), "the backup had no folders, so the restored state has none")
        assertContentEquals(old, file.readBytes(), "and the file is in the old format again, byte for byte")
    }

    @Test fun aBackupEntryThatPointsToAMissingFolderIsCorruptAndKeepsTheExistingContent() {
        val keep = store.addFolder("Bleibt").folder!!
        store.add("keep", "k", keep.id)
        val before = file.readBytes()
        val bad = "{\"storageVersion\":2,\"folders\":[{\"id\":\"f1\",\"name\":\"A\"}],\"prompts\":[{\"id\":\"p\",\"title\":\"\",\"text\":\"x\",\"folderId\":\"f2\"}]}"
        assertEquals(PromptsRestore.CORRUPT, restore(zipWith(PROMPTS_ENTRY_NAME to bad.toByteArray())))
        val higher = "{\"storageVersion\":3,\"folders\":[],\"prompts\":[]}"
        assertEquals(PromptsRestore.CORRUPT, restore(zipWith(PROMPTS_ENTRY_NAME to higher.toByteArray())))
        assertContentEquals(before, file.readBytes())
        assertEquals(listOf("keep"), titles())
    }
}

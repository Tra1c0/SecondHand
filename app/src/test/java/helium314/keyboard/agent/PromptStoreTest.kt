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

    @Test fun aWriteThatWouldNotFitInTheFileLimitIsRefusedAsFileFull() {
        // 8000 CJK characters are 24000 bytes: 100 of them are more than 2 MiB
        var failed: PromptResult? = null
        repeat(100) { if (failed == null) store.add("", "中".repeat(PromptStore.MAX_TEXT)).let { if (!it.ok) failed = it } }
        assertEquals(PromptError.FILE_FULL, failed?.error)
        assertTrue(file.length() <= PromptStore.MAX_STORAGE_BYTES)
        assertTrue(store.read().isNotEmpty())
    }

    @Test fun anUnpairedSurrogateIsRejectedAndChangesNothing() {
        val a = store.add("a", "1").prompt!!
        val before = file.readBytes()
        val version = PromptStore.version
        for (lone in listOf("\uD83D", "\uDE00", "x\uD83Dy", "\uDE00\uD83D")) {
            assertEquals(PromptError.INVALID_TEXT, store.add("t", "text $lone").error, lone)
            assertEquals(PromptError.INVALID_TEXT, store.add("t$lone", "text").error, lone)
            assertEquals(PromptError.INVALID_TEXT, store.update(a.id, "a", "text $lone").error, lone)
            assertEquals(PromptError.INVALID_TEXT, store.update(a.id, "a$lone", "text").error, lone)
        }
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(version, PromptStore.version)
        // a valid pair still round-trips, in title and text
        val emoji = "\uD83D\uDE00"
        assertTrue(store.update(a.id, emoji, "x$emoji").ok)
        assertEquals(emoji, store.read().single().title)
        assertEquals("x$emoji", store.read().single().text)
    }

    @Test fun aFileWithAnUnpairedSurrogateIsUnusable() {
        file.parentFile!!.mkdirs()
        val bad = """{"storageVersion":1,"prompts":[{"id":"a","title":"","text":"x\ud83d"}]}"""
        file.writeText(bad)
        assertTrue(store.read().isEmpty())
        assertEquals(PromptStoreState.UNUSABLE, store.state())
    }

    @Test fun stateTellsLockedFromUnusableFromReady() {
        assertEquals(PromptStoreState.READY, store.state())
        file.parentFile!!.mkdirs()
        file.writeText("garbage")
        assertEquals(PromptStoreState.UNUSABLE, store.state())
        accessible = false
        assertEquals(PromptStoreState.LOCKED, store.state())
    }

    @Test fun resetOnAnUnusableFileGivesAnEmptyUsableStore() {
        file.parentFile!!.mkdirs()
        file.writeText("garbage")
        File(file.path + ".bak").writeText("old garbage")
        val version = PromptStore.version
        assertTrue(store.reset().ok)
        assertFalse(file.exists())
        assertFalse(File(file.path + ".bak").exists())
        assertEquals(version + 1, PromptStore.version)
        assertEquals(PromptStoreState.READY, store.state())
        assertTrue(store.add("a", "1").ok)
        assertEquals(listOf("a"), titles())
    }

    @Test fun resetOnAHealthyStoreDeletesNothing() {
        assertTrue(store.add("a", "1").ok)
        assertTrue(store.add("b", "2").ok)
        val version = PromptStore.version
        assertEquals(PromptError.READABLE, store.reset().error)
        assertEquals(listOf("b", "a"), titles())
        assertEquals(version, PromptStore.version)
        assertTrue(file.exists())
        // no file at all is healthy too: nothing to delete, no version bump
        file.delete()
        assertEquals(PromptError.READABLE, store.reset().error)
        assertEquals(version, PromptStore.version)
    }

    @Test fun resetWithOnlyAReadableBackupKeepsTheBackupsPrompts() {
        assertTrue(store.add("a", "1").ok)
        // an interrupted write: only the backup is left; reading it restores the main file
        file.renameTo(File(file.path + ".bak"))
        assertFalse(file.exists())
        val version = PromptStore.version
        assertEquals(PromptError.READABLE, store.reset().error)
        assertEquals(listOf("a"), titles())
        assertEquals(version, PromptStore.version)
    }

    @Test fun resetWhileLockedDoesNothing() {
        file.parentFile!!.mkdirs()
        file.writeText("garbage")
        val version = PromptStore.version
        accessible = false
        assertEquals(PromptError.LOCKED, store.reset().error)
        assertEquals("garbage", file.readText())
        assertEquals(version, PromptStore.version)
    }

    @Test fun reorderOfNothingWithNothingStoredIsASuccessfulNoOp() {
        val version = PromptStore.version
        assertTrue(store.reorder(emptyList()).ok)
        assertFalse(file.exists())
        assertEquals(version, PromptStore.version)
        // but a list of unknown ids is still refused
        assertEquals(PromptError.INVALID_ORDER, store.reorder(listOf("x")).error)
    }

    // ---- stage 77: folders ----

    private fun lib(s: PromptStore = store) = s.loadLibrary()!!
    private fun folderNames(s: PromptStore = store) = lib(s).folders.map { it.name }
    private fun folder(name: String) = store.addFolder(name).folder!!
    private fun inFolder(title: String, folderId: String?) = store.add(title, "text of $title", folderId).prompt!!
    private fun fileText() = file.readText()

    /** A file as stage 74 wrote it (format 1, no folders). */
    private val oldFormat = """{"storageVersion":1,"prompts":[{"id":"p-1","title":"Mail","text":"Hallo\nWelt \uD83D\uDE00"},{"id":"p-2","title":"","text":"zweiter"}]}"""

    @Test fun anOldFileWithoutFoldersLoadsUnchangedWithAllPromptsInNoFolder() {
        file.parentFile!!.mkdirs()
        // the exact bytes stage 74 wrote (the emoji is raw UTF-8 in the file)
        val bytes = oldFormat.replace("\\uD83D\\uDE00", "\uD83D\uDE00").toByteArray(Charsets.UTF_8)
        file.writeBytes(bytes)
        assertEquals(PromptStoreState.READY, store.state())
        val library = lib()
        assertTrue(library.folders.isEmpty())
        assertEquals(listOf("p-1", "p-2"), library.prompts.map { it.id })
        assertEquals(listOf(null, null), library.prompts.map { it.folderId })
        assertEquals("Hallo\nWelt \uD83D\uDE00", library.prompts[0].text)
        assertEquals(PromptFilter.All, library.effective(PromptFilter.NoFolder), "no folder is not offered without folders")
        // reading changes nothing, and writing the same content back gives the same bytes (nothing lost, nothing added)
        assertTrue(bytes.contentEquals(file.readBytes()))
        assertEquals(String(bytes, Charsets.UTF_8), PromptStore.encode(library))
        assertTrue(bytes.contentEquals(store.exportBytes()!!), "the backup carries the old file as it is")
    }

    @Test fun aFileWithoutFoldersIsWrittenInTheOldFormatAndOneWithFoldersInTheNewOne() {
        val a = inFolder("a", null)
        assertTrue(fileText().startsWith("""{"storageVersion":1,"prompts":["""), fileText())
        assertFalse(fileText().contains("folders") || fileText().contains("folderId"))
        val f = folder("Arbeit")
        assertTrue(store.update(a.id, "a", "text of a", f.id).ok)
        assertTrue(fileText().startsWith("""{"storageVersion":2,"folders":[{"id":""" ), fileText())
        assertTrue(fileText().contains(""""folderId":"${f.id}""""))
        inFolder("loose", null)
        assertTrue(fileText().contains(""""folderId":null"""))
        // the last folder gone: back to the old format, every prompt kept
        assertTrue(store.deleteFolder(f.id).ok)
        assertTrue(fileText().startsWith("""{"storageVersion":1,"prompts":["""), fileText())
        assertEquals(listOf("loose", "a"), titles())
    }

    @Test fun addingAFolderToAnOldFileKeepsEveryPromptAndItsOrder() {
        file.parentFile!!.mkdirs()
        file.writeText(oldFormat)
        val f = folder("Neu")
        val library = lib()
        assertEquals(listOf(f), library.folders)
        assertEquals(listOf("p-1", "p-2"), library.prompts.map { it.id })
        assertEquals(listOf("Mail", ""), library.prompts.map { it.title })
        assertEquals(listOf(null, null), library.prompts.map { it.folderId })
        assertEquals("zweiter", library.prompts[1].text)
    }

    @Test fun folderCreateRenameAndOrder() {
        val a = folder("  Arbeit  ")
        val b = folder("Privat")
        assertEquals("Arbeit", a.name, "trimmed")
        assertNotEquals(a.id, b.id)
        assertEquals(listOf("Arbeit", "Privat"), folderNames(), "a new folder goes last")
        val p = inFolder("p", a.id)
        val renamed = store.renameFolder(a.id, " Büro ")
        assertTrue(renamed.ok)
        assertEquals(PromptFolder(a.id, "Büro"), renamed.folder)
        assertEquals(listOf("Büro", "Privat"), folderNames(), "a rename keeps the place")
        assertEquals(a.id, lib().prompts.single { it.id == p.id }.folderId, "and the prompts")
        assertEquals(PromptError.FOLDER_NOT_FOUND, store.renameFolder("nope", "x").error)
        assertEquals(PromptError.FOLDER_NOT_FOUND, store.deleteFolder("nope").error)
        assertTrue(store.deleteFolder(b.id).ok)
        assertEquals(listOf("Büro"), folderNames())
        assertEquals(PromptFolder(a.id, "Büro").toString(), "PromptFolder(name 4 chars)", "shows no name")
    }

    @Test fun folderNameRules() {
        val none = emptyList<PromptFolder>()
        assertEquals(PromptError.FOLDER_NAME_EMPTY, store.addFolder("").error)
        assertEquals(PromptError.FOLDER_NAME_EMPTY, store.addFolder("  \t ").error)
        assertTrue(store.addFolder("x".repeat(PromptStore.MAX_FOLDER_NAME)).ok)
        assertEquals(PromptError.FOLDER_NAME_TOO_LONG, store.addFolder("y".repeat(PromptStore.MAX_FOLDER_NAME + 1)).error)
        assertTrue(store.addFolder("  " + "z".repeat(PromptStore.MAX_FOLDER_NAME) + "  ").ok, "spaces around the name do not count")
        // UTF-16 characters: 38 letters and one emoji are 40, one more is 41
        assertTrue(store.addFolder("a".repeat(PromptStore.MAX_FOLDER_NAME - 2) + "\uD83D\uDE00").ok)
        assertEquals(PromptError.FOLDER_NAME_TOO_LONG, store.addFolder("b".repeat(PromptStore.MAX_FOLDER_NAME - 1) + "\uD83D\uDE00").error)
        for (bad in listOf("a\nb", "a\tb", "a\u0000b", "a\u007fb", "a\u0085b", "\uD83D", "\uDE00x", "x\uD83Dy")) {
            assertEquals(PromptError.FOLDER_NAME_INVALID, store.addFolder(bad).error, bad.map { it.code }.toString())
        }
        assertEquals(PromptError.FOLDER_NAME_INVALID, PromptStore.folderNameError("a\nb", none, null))
        assertEquals(3, folderNames().size, "nothing of the refused names was stored")
    }

    @Test fun folderNamesAreUniqueIgnoringCaseAndSpaces() {
        val a = folder("Arbeit")
        assertEquals(PromptError.FOLDER_NAME_TAKEN, store.addFolder("arbeit").error)
        assertEquals(PromptError.FOLDER_NAME_TAKEN, store.addFolder("ARBEIT").error)
        assertEquals(PromptError.FOLDER_NAME_TAKEN, store.addFolder("  Arbeit ").error)
        val b = folder("Ärger")
        assertEquals(PromptError.FOLDER_NAME_TAKEN, store.addFolder("ärger").error)
        // a rename to another folder's name is taken; to the own name in another spelling it is fine
        assertEquals(PromptError.FOLDER_NAME_TAKEN, store.renameFolder(b.id, "aRBEIT").error)
        assertTrue(store.renameFolder(a.id, "ARBEIT").ok)
        assertTrue(store.renameFolder(a.id, "ARBEIT").ok, "even the same name again")
        assertEquals(listOf("ARBEIT", "Ärger"), folderNames())
        // after a delete the name is free again
        assertTrue(store.deleteFolder(a.id).ok)
        assertTrue(store.addFolder("arbeit").ok)
    }

    @Test fun atMostFiftyFoldersAndADeleteMakesRoomAgain() {
        repeat(PromptStore.MAX_FOLDERS) { assertTrue(store.addFolder("f$it").ok, "$it") }
        assertEquals(PromptError.FOLDERS_FULL, store.addFolder("one more").error)
        assertEquals(PromptStore.MAX_FOLDERS, lib().folders.size)
        assertTrue(store.deleteFolder(lib().folders.last().id).ok)
        assertTrue(store.addFolder("again").ok)
        assertEquals("again", lib().folders.last().name)
    }

    @Test fun aRefusedFolderChangeChangesNothing() {
        val a = folder("Arbeit")
        inFolder("p", a.id)
        val before = file.readBytes()
        val version = PromptStore.version
        assertFalse(store.addFolder("").ok)
        assertFalse(store.addFolder("arbeit").ok)
        assertFalse(store.renameFolder(a.id, "x\ny").ok)
        assertFalse(store.renameFolder("nope", "y").ok)
        assertFalse(store.deleteFolder("nope").ok)
        assertFalse(store.reorderFolders(listOf("x")).ok)
        assertFalse(store.reorderInFolder(a.id, listOf("x")).ok)
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(version, PromptStore.version)
    }

    @Test fun deletingAFolderKeepsItsPromptsInNoFolderAndInTheirPlaces() {
        val a = folder("A")
        val b = folder("B")
        val p1 = inFolder("p1", a.id)
        val p2 = inFolder("p2", b.id)
        val p3 = inFolder("p3", a.id)
        val p4 = inFolder("p4", null)
        assertEquals(listOf("p4", "p3", "p2", "p1"), titles())
        val result = store.deleteFolder(a.id)
        assertTrue(result.ok)
        assertEquals(2, result.moved)
        val library = lib()
        assertEquals(listOf("B"), library.folders.map { it.name })
        assertEquals(listOf("p4", "p3", "p2", "p1"), library.prompts.map { it.title }, "nothing deleted, nothing moved in the order")
        assertEquals(listOf(null, null, b.id, null), library.prompts.map { it.folderId })
        assertEquals(listOf(p3.id, p4.id, p1.id).toSet(), library.promptsIn(PromptFilter.NoFolder).map { it.id }.toSet())
        assertEquals(listOf(p2.id), library.promptsIn(PromptFilter.Folder(b.id)).map { it.id })
        assertEquals(1, store.deleteFolder(b.id).moved, "one prompt moved by the second delete")
        assertEquals(listOf(null, null, null, null), lib().prompts.map { it.folderId })
    }

    @Test fun promptsGoIntoAFolderAndMoveBetweenFoldersKeepingTheirPlace() {
        val a = folder("A")
        val b = folder("B")
        val p1 = inFolder("p1", a.id)
        val p2 = inFolder("p2", null)
        val p3 = inFolder("p3", a.id)
        assertEquals(listOf("p3", "p2", "p1"), titles(), "a new prompt is first overall")
        assertEquals(listOf(p3.id, p1.id), lib().promptsIn(PromptFilter.Folder(a.id)).map { it.id }, "and first in its folder")
        // the three-argument update keeps the folder, the four-argument one moves
        assertTrue(store.update(p1.id, "p1x", "new text").ok)
        assertEquals(a.id, lib().prompts.single { it.id == p1.id }.folderId)
        assertTrue(store.update(p1.id, "p1x", "new text", b.id).ok)
        assertEquals(listOf("p3", "p2", "p1x"), titles(), "a move keeps the global place")
        assertEquals(b.id, lib().prompts.single { it.id == p1.id }.folderId)
        assertTrue(store.update(p3.id, "p3", "text of p3", null).ok)
        assertEquals(null, lib().prompts.first { it.id == p3.id }.folderId)
        // a missing folder is refused for add and for a move; the prompt stays where it was
        val before = file.readBytes()
        assertEquals(PromptError.FOLDER_NOT_FOUND, store.add("x", "y", "gone").error)
        assertEquals(PromptError.FOLDER_NOT_FOUND, store.update(p2.id, "p2", "text of p2", "gone").error)
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test fun theExistingLimitsStillHoldForPromptsInFolders() {
        val a = folder("A")
        assertTrue(store.add("t".repeat(PromptStore.MAX_TITLE), "x", a.id).ok)
        assertEquals(PromptError.TITLE_TOO_LONG, store.add("t".repeat(PromptStore.MAX_TITLE + 1), "x", a.id).error)
        assertTrue(store.add("", "x".repeat(PromptStore.MAX_TEXT), a.id).ok)
        assertEquals(PromptError.TEXT_TOO_LONG, store.add("", "x".repeat(PromptStore.MAX_TEXT + 1), a.id).error)
        assertEquals(PromptError.EMPTY_TEXT, store.add("t", " ", a.id).error)
        assertEquals(PromptError.INVALID_TEXT, store.add("t", "x\uD83D", a.id).error)
        // 200 prompts in total, whatever the folder
        val b = folder("B")
        var i = 0
        while (store.read().size < PromptStore.MAX_PROMPTS) { assertTrue(store.add("p${i++}", "t", if (i % 2 == 0) a.id else b.id).ok) }
        assertEquals(PromptError.FULL, store.add("more", "t", a.id).error)
        assertEquals(PromptError.FULL, store.add("more", "t", null).error)
        assertEquals(PromptStore.MAX_PROMPTS, store.read().size)
        // the file limit holds too: many multi-byte prompts in folders end in FILE_FULL, never in a file that cannot be read
        file.delete()
        val c = folder("C")
        var failed: PromptResult? = null
        repeat(100) { if (failed == null) store.add("", "中".repeat(PromptStore.MAX_TEXT), c.id).let { if (!it.ok) failed = it } }
        assertEquals(PromptError.FILE_FULL, failed?.error)
        assertTrue(file.length() <= PromptStore.MAX_STORAGE_BYTES)
        assertEquals(PromptStoreState.READY, store.state())
    }

    @Test fun reorderingInsideAFolderPermutesOnlyThatFoldersPlaces() {
        val a = folder("A")
        val b = folder("B")
        // global order after the adds (newest first): n2 a3 b2 a2 n1 b1 a1
        val a1 = inFolder("a1", a.id); val b1 = inFolder("b1", b.id); val n1 = inFolder("n1", null)
        val a2 = inFolder("a2", a.id); val b2 = inFolder("b2", b.id); val a3 = inFolder("a3", a.id); val n2 = inFolder("n2", null)
        assertEquals(listOf("n2", "a3", "b2", "a2", "n1", "b1", "a1"), titles())
        assertTrue(store.reorderInFolder(a.id, listOf(a1.id, a3.id, a2.id)).ok)
        assertEquals(listOf("n2", "a1", "b2", "a3", "n1", "b1", "a2"), titles(), "only the places of A's prompts changed")
        assertEquals(listOf(a1.id, a3.id, a2.id), lib().promptsIn(PromptFilter.Folder(a.id)).map { it.id })
        assertEquals(listOf(b2.id, b1.id), lib().promptsIn(PromptFilter.Folder(b.id)).map { it.id }, "B keeps its relative order")
        assertEquals(listOf(n2.id, n1.id), lib().promptsIn(PromptFilter.NoFolder).map { it.id })
        assertEquals(listOf(a.id, b.id), lib().folders.map { it.id })
        // "no folder" can be ordered the same way
        assertTrue(store.reorderInFolder(null, listOf(n1.id, n2.id)).ok)
        assertEquals(listOf("n1", "a1", "b2", "a3", "n2", "b1", "a2"), titles())
        assertEquals(listOf(null, a.id, b.id, a.id, null, b.id, a.id), lib().prompts.map { it.folderId })
    }

    @Test fun reorderingInsideAFolderNeedsExactlyThatFoldersIds() {
        val a = folder("A")
        val b = folder("B")
        val a1 = inFolder("a1", a.id); val a2 = inFolder("a2", a.id); val b1 = inFolder("b1", b.id)
        val before = file.readBytes()
        val version = PromptStore.version
        assertEquals(PromptError.INVALID_ORDER, store.reorderInFolder(a.id, listOf(a1.id)).error, "one missing")
        assertEquals(PromptError.INVALID_ORDER, store.reorderInFolder(a.id, listOf(a1.id, a2.id, b1.id)).error, "one of another folder")
        assertEquals(PromptError.INVALID_ORDER, store.reorderInFolder(a.id, listOf(a1.id, b1.id)).error, "wrong one")
        assertEquals(PromptError.INVALID_ORDER, store.reorderInFolder(a.id, listOf(a1.id, a1.id)).error, "duplicate")
        assertEquals(PromptError.INVALID_ORDER, store.reorderInFolder(null, listOf(a1.id)).error, "a prompt that has a folder is not in no folder")
        assertEquals(PromptError.FOLDER_NOT_FOUND, store.reorderInFolder("gone", emptyList()).error)
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(version, PromptStore.version)
        // nothing in the folder and nothing given: a no-op that writes nothing
        val c = folder("C")
        val afterFolder = PromptStore.version
        assertTrue(store.reorderInFolder(c.id, emptyList()).ok)
        assertEquals(afterFolder, PromptStore.version)
        // the whole-list reorder still wants every id
        assertEquals(PromptError.INVALID_ORDER, store.reorder(listOf(a1.id, a2.id)).error)
        assertTrue(store.reorder(listOf(b1.id, a1.id, a2.id)).ok)
        assertEquals(listOf("b1", "a1", "a2"), titles())
        assertEquals(listOf(a.id, b.id, c.id), lib().folders.map { it.id }, "folders are untouched by a prompt reorder")
    }

    @Test fun foldersCanBeReorderedAndNeedExactlyTheStoredIds() {
        val a = folder("A"); val b = folder("B"); val c = folder("C")
        val p = inFolder("p", b.id)
        assertTrue(store.reorderFolders(listOf(c.id, a.id, b.id)).ok)
        assertEquals(listOf("C", "A", "B"), folderNames())
        assertEquals(b.id, lib().prompts.single().folderId)
        assertEquals(PromptError.INVALID_ORDER, store.reorderFolders(listOf(a.id, b.id)).error)
        assertEquals(PromptError.INVALID_ORDER, store.reorderFolders(listOf(a.id, b.id, c.id, "x")).error)
        assertEquals(PromptError.INVALID_ORDER, store.reorderFolders(listOf(a.id, a.id, b.id)).error)
        assertEquals(listOf("C", "A", "B"), folderNames())
        assertEquals(p.id, lib().prompts.single().id)
    }

    @Test fun foldersAndAssignmentsSurviveANewStoreInstanceAndHaveNoContentInToString() {
        val a = folder("Arbeit")
        val b = folder("Privat")
        val p = inFolder("p", b.id)
        val read = PromptStore(app) { true }.loadLibrary()!!
        assertEquals(listOf(a, b), read.folders)
        assertEquals(b.id, read.prompts.single { it.id == p.id }.folderId)
        assertFalse(read.folders[0].toString().contains("Arbeit"))
        assertFalse(PromptFilter.Folder("secret-id").toString().contains("secret"))
        assertEquals(read.prompts.map { it.id }, store.read().map { it.id }, "read() and load() still give all prompts")
    }

    @Test fun theFilterShowsTheRightPromptsAndFallsBackWhenItCannotBeShown() {
        val a = folder("A"); val b = folder("B")
        val p1 = inFolder("p1", a.id); val p2 = inFolder("p2", null)
        val library = lib()
        assertEquals(listOf(p2.id, p1.id), library.promptsIn(PromptFilter.All).map { it.id })
        assertEquals(listOf(p1.id), library.promptsIn(PromptFilter.Folder(a.id)).map { it.id })
        assertEquals(emptyList(), library.promptsIn(PromptFilter.Folder(b.id)))
        assertEquals(listOf(p2.id), library.promptsIn(PromptFilter.NoFolder).map { it.id })
        assertEquals(PromptFilter.NoFolder, library.effective(PromptFilter.NoFolder))
        assertEquals(PromptFilter.All, library.effective(PromptFilter.Folder("gone")))
        assertEquals(PromptFilter.Folder(b.id), library.effective(PromptFilter.Folder(b.id)), "an empty folder can still be shown")
        // "no folder" is offered only while some prompt has none
        store.update(p2.id, "p2", "text of p2", a.id)
        assertEquals(PromptFilter.All, lib().effective(PromptFilter.NoFolder))
        for (filter in listOf(PromptFilter.All, PromptFilter.NoFolder, PromptFilter.Folder("x:y"))) assertEquals(filter, PromptFilter.decode(filter.encode()))
        assertEquals(PromptFilter.All, PromptFilter.decode("garbage"))
        assertEquals(null, PromptFilter.All.folderIdForNew)
        assertEquals(null, PromptFilter.NoFolder.folderIdForNew)
        assertEquals("q", PromptFilter.Folder("q").folderIdForNew)
    }

    /** What the strict reader must refuse, each one as a file on disk: the store reads empty, never writes, and "reset" is the way out. */
    private val unusableFolderFiles = mapOf(
        "a prompt of a folder the file does not have" to """{"storageVersion":2,"folders":[{"id":"f1","name":"A"}],"prompts":[{"id":"p","title":"","text":"x","folderId":"f2"}]}""",
        "a prompt with a folder but no folders at all" to """{"storageVersion":2,"folders":[],"prompts":[{"id":"p","title":"","text":"x","folderId":"f1"}]}""",
        "a higher format version" to """{"storageVersion":3,"folders":[],"prompts":[]}""",
        "a higher format version with the old keys" to """{"storageVersion":3,"prompts":[]}""",
        "format 2 without the folders key" to """{"storageVersion":2,"prompts":[]}""",
        "format 1 with a folders key" to """{"storageVersion":1,"folders":[],"prompts":[]}""",
        "format 2 prompt without folderId" to """{"storageVersion":2,"folders":[],"prompts":[{"id":"p","title":"","text":"x"}]}""",
        "format 1 prompt with a folderId" to """{"storageVersion":1,"prompts":[{"id":"p","title":"","text":"x","folderId":null}]}""",
        "a folderId that is a number" to """{"storageVersion":2,"folders":[{"id":"f1","name":"A"}],"prompts":[{"id":"p","title":"","text":"x","folderId":1}]}""",
        "duplicate folder ids" to """{"storageVersion":2,"folders":[{"id":"f1","name":"A"},{"id":"f1","name":"B"}],"prompts":[]}""",
        "a folder name with spaces around" to """{"storageVersion":2,"folders":[{"id":"f1","name":" A"}],"prompts":[]}""",
        "an empty folder name" to """{"storageVersion":2,"folders":[{"id":"f1","name":""}],"prompts":[]}""",
        "a folder name of 41 characters" to """{"storageVersion":2,"folders":[{"id":"f1","name":"${"n".repeat(41)}"}],"prompts":[]}""",
        "a folder name with a control character" to """{"storageVersion":2,"folders":[{"id":"f1","name":"a\nb"}],"prompts":[]}""",
        "a folder name with a lone surrogate" to """{"storageVersion":2,"folders":[{"id":"f1","name":"a\ud83d"}],"prompts":[]}""",
        "a folder with an extra key" to """{"storageVersion":2,"folders":[{"id":"f1","name":"A","color":"red"}],"prompts":[]}""",
        "a folder with an empty id" to """{"storageVersion":2,"folders":[{"id":"","name":"A"}],"prompts":[]}""",
        "51 folders" to """{"storageVersion":2,"folders":[${(0..50).joinToString(",") { """{"id":"f$it","name":"n$it"}""" }}],"prompts":[]}""",
    )

    @Test fun aFileWithFoldersThatTheReaderDoesNotAcceptIsUnusableAndNeverChanged() {
        file.parentFile!!.mkdirs()
        for ((what, content) in unusableFolderFiles) {
            file.writeText(content)
            assertEquals(PromptStoreState.UNUSABLE, store.state(), what)
            assertTrue(store.read().isEmpty(), what)
            assertEquals(null, store.loadLibrary(), what)
            assertEquals(null, store.exportBytes(), what)
            // every write is refused with IO and leaves the bytes alone; nothing is silently turned into "no folder"
            assertEquals(PromptError.IO, store.add("a", "1").error, what)
            assertEquals(PromptError.IO, store.addFolder("n").error, what)
            assertEquals(PromptError.IO, store.deleteFolder("f1").error, what)
            assertEquals(PromptError.IO, store.reorderFolders(emptyList()).error, what)
            assertEquals(content, file.readText(), what)
        }
    }

    @Test fun aValidFileWithFoldersIsReadAndAnUnusableOneCanBeResetAway() {
        file.parentFile!!.mkdirs()
        file.writeText("""{"storageVersion":2,"folders":[{"id":"f1","name":"A"},{"id":"f2","name":"B"}],"prompts":[{"id":"p1","title":"t","text":"x","folderId":"f2"},{"id":"p2","title":"","text":"y","folderId":null}]}""")
        assertEquals(PromptStoreState.READY, store.state())
        assertEquals(listOf("A", "B"), folderNames())
        assertEquals(listOf("f2", null), lib().prompts.map { it.folderId })
        // a v2 file with no folders is valid too (all prompts without folder)
        file.writeText("""{"storageVersion":2,"folders":[],"prompts":[{"id":"p1","title":"","text":"x","folderId":null}]}""")
        assertEquals(PromptStoreState.READY, store.state())
        // an unusable one: reset is refused while it is readable, and deletes it once it is not
        file.writeText(unusableFolderFiles.getValue("a prompt of a folder the file does not have"))
        assertTrue(store.reset().ok)
        assertFalse(file.exists())
        assertEquals(PromptStoreState.READY, store.state())
        assertTrue(store.addFolder("fresh").ok)
    }

    @Test fun lockedDeviceRefusesEveryFolderChangeAndCreatesNothing() {
        accessible = false
        assertEquals(PromptError.LOCKED, store.addFolder("A").error)
        assertEquals(PromptError.LOCKED, store.renameFolder("x", "A").error)
        assertEquals(PromptError.LOCKED, store.deleteFolder("x").error)
        assertEquals(PromptError.LOCKED, store.reorderFolders(emptyList()).error)
        assertEquals(PromptError.LOCKED, store.reorderInFolder(null, emptyList()).error)
        assertEquals(null, store.loadLibrary())
        assertFalse(file.exists())
        accessible = true
        val a = folder("A")
        val before = file.readBytes()
        accessible = false
        assertEquals(PromptError.LOCKED, store.deleteFolder(a.id).error)
        assertEquals(null, store.exportBytes())
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test fun theBackupCarriesFoldersWithPromptsButAFileWithOnlyFoldersHasNoEntry() {
        assertEquals(null, store.exportBytes(), "no file")
        val a = folder("Nur Ordner")
        assertEquals(null, store.exportBytes(), "folders alone are not backed up: restoring them could never replace a store")
        val p = inFolder("p", a.id)
        val withPrompt = store.exportBytes()!!
        assertTrue(withPrompt.contentEquals(file.readBytes()))
        // restore into a fresh state: folders, assignments and order come back
        file.delete()
        assertTrue(store.replaceAll(withPrompt).ok)
        assertEquals(listOf(a), lib().folders)
        assertEquals(listOf(p), lib().prompts)
        // restoring an old backup (format 1) replaces the folders too: it is "the state of that backup"
        assertTrue(store.replaceAll(oldFormat.toByteArray()).ok)
        assertTrue(lib().folders.isEmpty())
        assertEquals(listOf("p-1", "p-2"), lib().prompts.map { it.id })
        assertTrue(fileText().startsWith("""{"storageVersion":1"""))
        // a backup that points to a missing folder is refused, nothing changes
        val before = file.readBytes()
        val bad = unusableFolderFiles.getValue("a prompt of a folder the file does not have").toByteArray()
        assertEquals(PromptError.INVALID_TEXT, store.replaceAll(bad).error)
        assertEquals(null, PromptStore.decodeBackup(bad))
        assertTrue(before.contentEquals(file.readBytes()))
        // for the backup decision only prompts count: an empty library and a folder-only one both have none
        assertFalse(PromptStore.decodeBackup(oldFormat.replace(Regex("\\[.*\\]"), "[]").toByteArray())!!.hasPrompts())
        assertFalse(PromptStore.decodeBackup("""{"storageVersion":2,"folders":[{"id":"f","name":"A"}],"prompts":[]}""".toByteArray())!!.hasPrompts())
        assertTrue(PromptStore.decodeBackup(oldFormat.toByteArray())!!.hasPrompts())
    }

    @Test fun twoFolderNamesThatDifferOnlyInCaseAreReadAndStayWritable() {
        // the case tables differ between Android versions, so the reader must not refuse such a file
        file.parentFile!!.mkdirs()
        val content = """{"storageVersion":2,"folders":[{"id":"f1","name":"Arbeit"},{"id":"f2","name":"ARBEIT"}],"prompts":[{"id":"p1","title":"t","text":"x","folderId":"f2"}]}"""
        file.writeText(content)
        assertEquals(PromptStoreState.READY, store.state())
        assertEquals(listOf("Arbeit", "ARBEIT"), folderNames())
        // an unrelated change is written without failing, and both folders stay as they are
        assertTrue(store.add("neu", "y", "f1").ok)
        assertTrue(store.update("p1", "t2", "x2").ok)
        assertTrue(store.reorderFolders(listOf("f2", "f1")).ok)
        assertTrue(store.reorder(store.read().map { it.id }.reversed()).ok)
        assertEquals(listOf("ARBEIT", "Arbeit"), folderNames())
        assertEquals(PromptStoreState.READY, store.state())
        assertEquals(setOf("f1", "f2"), lib().prompts.map { it.folderId }.toSet())
        // uniqueness is enforced only for a new or renamed folder, against the other folders
        assertEquals(PromptError.FOLDER_NAME_TAKEN, store.addFolder("arbeit").error)
        // a rename into a collision with another folder is refused (also to the own old name while the twin exists), any other name works
        assertEquals(PromptError.FOLDER_NAME_TAKEN, store.renameFolder("f1", "Arbeit ").error)
        assertTrue(store.renameFolder("f1", "Arbeit 2").ok)
        assertTrue(store.renameFolder("f2", "ARBEIT").ok, "no twin any more: its own spelling is allowed again")
        assertTrue(store.deleteFolder("f1").ok)
        assertEquals(listOf("ARBEIT"), folderNames())
    }

    @Test fun aStoreNearTheFileLimitThatGainsItsFirstFolderIsRefusedAsFileFullAndStaysByteIdentical() {
        // format 2 adds a folderId to every prompt, so a store that just fits in format 1 may not fit with a folder
        val prompts = ArrayList<Prompt>()
        fun size() = PromptStore.encode(prompts).toByteArray(Charsets.UTF_8).size
        while (size() + 24100 < PromptStore.MAX_STORAGE_BYTES) prompts.add(Prompt("p${prompts.size}", "", "中".repeat(PromptStore.MAX_TEXT)))
        while (size() + 4100 < PromptStore.MAX_STORAGE_BYTES) prompts.add(Prompt("p${prompts.size}", "", "x".repeat(4000)))
        // a last prompt that brings the file to 10 bytes below the limit
        prompts.add(Prompt("p${prompts.size}", "", "x"))
        val padding = PromptStore.MAX_STORAGE_BYTES - 10 - size()
        assertTrue(padding in 0 until PromptStore.MAX_TEXT, "$padding")
        prompts[prompts.size - 1] = Prompt(prompts.last().id, "", "x".repeat(1 + padding))
        assertEquals(PromptStore.MAX_STORAGE_BYTES - 10, size())
        assertTrue(prompts.size <= PromptStore.MAX_PROMPTS)
        file.parentFile!!.mkdirs()
        file.writeText(PromptStore.encode(prompts))
        assertEquals(PromptStoreState.READY, store.state())
        val before = file.readBytes()
        val version = PromptStore.version
        assertEquals(PromptError.FILE_FULL, store.addFolder("Neu").error)
        assertTrue(before.contentEquals(file.readBytes()), "the file is untouched")
        assertEquals(version, PromptStore.version)
        assertEquals(PromptStoreState.READY, store.state())
        assertTrue(store.readFolders().isEmpty())
        // a prompt can still be removed, and then the folder fits
        assertTrue(store.delete("p0").ok)
        assertTrue(store.addFolder("Neu").ok)
    }
}

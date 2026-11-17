// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.settings.preferences.RestoreFolders
import helium314.keyboard.settings.preferences.RestoreNotStarted
import helium314.keyboard.settings.preferences.SettingsRestore
import helium314.keyboard.settings.preferences.restoreSettingsFromLines
import helium314.keyboard.settings.preferences.restoreWithRollback
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 65 (S3, S4): a failed restore leaves the data folders and the settings as they were. */
@RunWith(RobolectricTestRunner::class)
class RestoreSafetyTest {
    private val root: File = Files.createTempDirectory("restore").toFile()
    private val files = File(root, "files").apply { mkdirs() }
    private val protectedDir = File(root, "protected").apply { mkdirs() }
    private val restoredDb = File(root, "heliboard.db_restored")
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = ctx.getSharedPreferences("restore_test", Context.MODE_PRIVATE)
    private val protectedPrefs get() = ctx.getSharedPreferences("restore_test_protected", Context.MODE_PRIVATE)

    private fun File.put(name: String, text: String) { File(this, name).apply { parentFile?.mkdirs() }.writeText(text) }
    private fun File.read(name: String) = File(this, name).readText()
    private fun copyOf(folder: File) = File(folder.absolutePath + "2")

    // --- the folders --------------------------------------------------------------------------

    @Test fun aFailedRestoreBringsBothFoldersBack() {
        files.put("dicts/a.txt", "own files")
        protectedDir.put("clipboard/b.txt", "own protected")
        val aside = RestoreFolders(files, protectedDir)
        assertTrue(aside.moveAside())
        assertFalse(files.exists())
        files.put("blacklists/new.txt", "from the backup")
        protectedDir.put("junk.txt", "half a file")
        assertTrue(aside.putBack())
        assertEquals("own files", files.read("dicts/a.txt"))
        assertEquals("own protected", protectedDir.read("clipboard/b.txt"))
        assertFalse(File(files, "blacklists/new.txt").exists())
        assertFalse(File(protectedDir, "junk.txt").exists())
        assertFalse(copyOf(files).exists())
        assertFalse(copyOf(protectedDir).exists())
    }

    @Test fun aSuccessfulRestoreDropsTheCopies() {
        files.put("a.txt", "old")
        protectedDir.put("b.txt", "old")
        val aside = RestoreFolders(files, protectedDir)
        assertTrue(aside.moveAside())
        files.put("a.txt", "new")
        assertTrue(aside.discardCopies())
        assertEquals("new", files.read("a.txt"))
        assertFalse(copyOf(files).exists())
        assertFalse(copyOf(protectedDir).exists())
        assertFalse(File(files.absolutePath + "2_discard").exists())
    }

    @Test fun aLeftoverFromAnInterruptedRestoreIsRolledBackFirstAndTheNextRestoreWorks() {
        // the earlier restore died after moving the folders aside and writing a part of the backup
        files.put("half.txt", "half written")
        copyOf(files).apply { mkdirs() }.let { File(it, "mine.txt").writeText("mine before") }
        protectedDir.put("b.txt", "intact")
        val aside = RestoreFolders(files, protectedDir)
        assertTrue(aside.moveAside())
        // the leftover was put back in place of the half-written folder, then moved aside again
        assertEquals("mine before", File(copyOf(files), "mine.txt").readText())
        assertFalse(File(copyOf(files), "half.txt").exists())
        assertTrue(aside.putBack())
        assertEquals("mine before", files.read("mine.txt"))
        assertFalse(File(files, "half.txt").exists())
        assertEquals("intact", protectedDir.read("b.txt"))
    }

    @Test fun aLeftoverThatCannotBePutBackStopsTheRestoreAndIsNeverDeleted() {
        files.put("half.txt", "half written")
        copyOf(files).apply { mkdirs() }.let { File(it, "mine.txt").writeText("mine before") }
        val aside = RestoreFolders(files, protectedDir, rename = { _, _ -> false })
        assertFalse(aside.moveAside())
        assertFalse(aside.intact)
        assertEquals("mine before", File(copyOf(files), "mine.txt").readText()) // the only copy is still there
    }

    @Test fun aFailingRenameLeavesEverythingAsItWasAndSaysSo() {
        files.put("a.txt", "mine")
        protectedDir.put("b.txt", "mine")
        // the second folder cannot be renamed: the first one is moved back
        val aside = RestoreFolders(files, protectedDir, rename = { from, to -> if (from == protectedDir) false else from.renameTo(to) })
        assertFalse(aside.moveAside())
        assertTrue(aside.intact)
        assertEquals("mine", files.read("a.txt"))
        assertEquals("mine", protectedDir.read("b.txt"))
        assertFalse(copyOf(files).exists())
    }

    @Test fun aFailingPutBackIsReportedAndKeepsTheCopy() {
        files.put("a.txt", "mine")
        protectedDir.put("b.txt", "mine")
        val aside = RestoreFolders(files, protectedDir, rename = { from, to -> if (to == files) false else from.renameTo(to) })
        assertTrue(aside.moveAside())
        files.put("new.txt", "from the backup")
        assertFalse(aside.putBack())
        assertEquals("mine", File(copyOf(files), "a.txt").readText()) // the user's data is still there
        assertEquals("mine", protectedDir.read("b.txt")) // the other folder did come back
    }

    @Test fun aFolderThatDidNotExistDoesNotExistAfterAFailedRestore() {
        protectedDir.deleteRecursively()
        files.put("a.txt", "mine")
        val aside = RestoreFolders(files, protectedDir)
        assertTrue(aside.moveAside())
        protectedDir.put("created.txt", "by the restore")
        assertTrue(aside.putBack())
        assertEquals("mine", files.read("a.txt"))
        assertFalse(protectedDir.exists())
    }

    @Test fun oneFolderForBothIsMovedOnce() {
        files.put("a.txt", "mine")
        val aside = RestoreFolders(files, files)
        assertTrue(aside.moveAside())
        files.put("junk.txt", "x")
        assertTrue(aside.putBack())
        assertEquals("mine", files.read("a.txt"))
        assertFalse(File(files, "junk.txt").exists())
    }

    // --- the whole sequence, with its roll-back ------------------------------------------------

    private fun setUpUserData() {
        files.put("dicts/a.txt", "own files")
        protectedDir.put("clipboard/b.txt", "own protected")
        prefs.edit().clear().putBoolean("keep", true).putLong("big", 5_000_000_000L).putFloat("f", 1.5f).putString("name", "mine").commit()
        protectedPrefs.edit().clear().putInt("p", 7).commit()
    }

    @Test fun aRestoreThatThrowsPutsFoldersAndBothSettingsBack() {
        setUpUserData()
        restoredDb.writeText("stale copy of an earlier restore")
        var staleGone = false
        val result = restoreWithRollback(RestoreFolders(files, protectedDir), restoredDb, prefs, protectedPrefs) {
            staleGone = !restoredDb.exists() // a stale copy is gone before the work starts
            files.put("blacklists/new.txt", "from the backup")
            prefs.edit().clear().putString("from", "backup").commit()
            protectedPrefs.edit().clear().commit()
            restoredDb.writeText("new db")
            throw IllegalStateException("the work failed")
        }
        assertTrue(staleGone)
        assertFalse(result.ok)
        assertTrue(result.rolledBack)
        assertEquals("own files", files.read("dicts/a.txt"))
        assertEquals("own protected", protectedDir.read("clipboard/b.txt"))
        assertFalse(File(files, "blacklists/new.txt").exists())
        assertEquals(setOf("keep", "big", "f", "name"), prefs.all.keys)
        assertEquals(5_000_000_000L, prefs.getLong("big", 0))
        assertEquals(1.5f, prefs.getFloat("f", 0f))
        assertEquals(7, protectedPrefs.getInt("p", 0))
        assertFalse(restoredDb.exists())
    }

    @Test fun aRestoreThatWorksKeepsTheNewDataAndDropsTheCopies() {
        setUpUserData()
        val result = restoreWithRollback(RestoreFolders(files, protectedDir), restoredDb, prefs, protectedPrefs) {
            files.put("dicts/a.txt", "from the backup")
        }
        assertTrue(result.ok)
        assertEquals("from the backup", files.read("dicts/a.txt"))
        assertFalse(copyOf(files).exists())
    }

    @Test fun aRollBackThatFailsIsReportedAsNotRolledBack() {
        setUpUserData()
        val folders = RestoreFolders(files, protectedDir, rename = { from, to -> if (to == files) false else from.renameTo(to) })
        val result = restoreWithRollback(folders, restoredDb, prefs, protectedPrefs) { throw IllegalStateException("fails") }
        assertFalse(result.ok)
        assertFalse(result.rolledBack) // the message must not say that nothing was changed
        assertEquals("own files", File(copyOf(files), "dicts/a.txt").readText())
    }

    @Test fun aRestoreThatCannotStartChangesNothingAndSaysSo() {
        setUpUserData()
        val folders = RestoreFolders(files, protectedDir, rename = { from, to -> if (from == protectedDir) false else from.renameTo(to) })
        var worked = false
        val result = restoreWithRollback(folders, restoredDb, prefs, protectedPrefs) { worked = true }
        assertFalse(worked)
        assertFalse(result.ok)
        assertTrue(result.rolledBack)
        assertTrue(result.error is RestoreNotStarted)
        assertEquals("own files", files.read("dicts/a.txt"))
        assertEquals("own protected", protectedDir.read("clipboard/b.txt"))
    }

    // --- the settings -------------------------------------------------------------------------

    @Test fun aDamagedSettingsFileChangesNothing() {
        prefs.edit().clear().putBoolean("keep", true).putString("name", "mine").commit()
        val damaged = listOf("boolean settings", "{\"keep\":false", "string settings", "{}")
        assertEquals(SettingsRestore.DAMAGED, restoreSettingsFromLines(damaged, prefs))
        assertTrue(prefs.getBoolean("keep", false))
        assertEquals("mine", prefs.getString("name", null))
        for (bad in listOf(listOf("garbage"), listOf("int settings"), listOf("int settings", "{\"n\":\"text\"}"), emptyList())) {
            assertEquals(SettingsRestore.DAMAGED, restoreSettingsFromLines(bad, prefs))
        }
        assertEquals(setOf("keep", "name"), prefs.all.keys)
    }

    @Test fun aGoodFirstSectionAndADamagedLaterOneLeavesTheOldSettingsUntouched() {
        // the case the old code got wrong: it had cleared and half-filled the settings before it failed
        prefs.edit().clear().putBoolean("old", true).putString("name", "mine").commit()
        val lines = listOf("boolean settings", "{\"keep\":false}", "int settings", "{\"n\":3}", "string settings", "{\"s\":")
        assertEquals(SettingsRestore.DAMAGED, restoreSettingsFromLines(lines, prefs))
        assertEquals(setOf("old", "name"), prefs.all.keys)
        assertTrue(prefs.getBoolean("old", false))
    }

    @Test fun aGoodSettingsFileReplacesTheSettingsIncludingLongAndFloat() {
        prefs.edit().clear().putBoolean("old", true).commit()
        val good = listOf("boolean settings", "{\"keep\":false}", "int settings", "{\"n\":3}",
            "long settings", "{\"big\":5000000000}", "float settings", "{\"f\":1.5}", "string settings", "{\"s\":\"x\"}",
            "string set settings", "{\"set\":[\"a\",\"b\"]}")
        assertEquals(SettingsRestore.OK, restoreSettingsFromLines(good, prefs))
        assertEquals(setOf("keep", "n", "big", "f", "s", "set"), prefs.all.keys)
        assertEquals(5_000_000_000L, prefs.getLong("big", 0))
        assertEquals(1.5f, prefs.getFloat("f", 0f))
        assertEquals(setOf("a", "b"), prefs.getStringSet("set", null))
        assertNull(prefs.getString("old", null))
    }
}

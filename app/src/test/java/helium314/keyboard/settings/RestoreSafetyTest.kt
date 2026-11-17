// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.settings.preferences.RestoreFolders
import helium314.keyboard.settings.preferences.restoreSettingsFromLines
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 65 (S3, S4): a failed restore leaves the data folders and the settings as they were. */
@RunWith(RobolectricTestRunner::class)
class RestoreSafetyTest {
    private val root: File = Files.createTempDirectory("restore").toFile()
    private val files = File(root, "files").apply { mkdirs() }
    private val protectedDir = File(root, "protected").apply { mkdirs() }

    private fun File.put(name: String, text: String) { File(this, name).apply { parentFile?.mkdirs() }.writeText(text) }
    private fun File.read(name: String) = File(this, name).readText()

    @Test fun aFailedRestoreBringsBothFoldersBack() {
        files.put("dicts/a.txt", "own files")
        protectedDir.put("clipboard/b.txt", "own protected")
        val aside = RestoreFolders(files, protectedDir)
        assertTrue(aside.moveAside())
        assertFalse(files.exists())
        assertFalse(protectedDir.exists())
        // the restore wrote something and then failed
        files.put("blacklists/new.txt", "from the backup")
        protectedDir.put("junk.txt", "half a file")
        aside.putBack()
        assertEquals("own files", files.read("dicts/a.txt"))
        assertEquals("own protected", protectedDir.read("clipboard/b.txt"))
        assertFalse(File(files, "blacklists/new.txt").exists())
        assertFalse(File(protectedDir, "junk.txt").exists())
        assertFalse(File(files.absolutePath + "2").exists())
        assertFalse(File(protectedDir.absolutePath + "2").exists())
    }

    @Test fun aSuccessfulRestoreDropsTheCopies() {
        files.put("a.txt", "old")
        protectedDir.put("b.txt", "old")
        val aside = RestoreFolders(files, protectedDir)
        assertTrue(aside.moveAside())
        files.put("a.txt", "new")
        aside.discardCopies()
        assertEquals("new", files.read("a.txt"))
        assertFalse(File(files.absolutePath + "2").exists())
        assertFalse(File(protectedDir.absolutePath + "2").exists())
    }

    @Test fun aLeftoverCopyStopsTheRestoreBeforeAnythingIsTouched() {
        files.put("a.txt", "mine")
        protectedDir.put("b.txt", "mine")
        File(protectedDir.absolutePath + "2").apply { mkdirs() }.let { File(it, "x.txt").writeText("earlier copy") }
        val aside = RestoreFolders(files, protectedDir)
        assertFalse(aside.moveAside())
        // the first folder was moved before the second refused: it is back, nothing is lost
        assertEquals("mine", files.read("a.txt"))
        assertEquals("mine", protectedDir.read("b.txt"))
        assertEquals("earlier copy", File(protectedDir.absolutePath + "2", "x.txt").readText())
        assertFalse(File(files.absolutePath + "2").exists())
    }

    @Test fun aFolderThatDidNotExistDoesNotExistAfterAFailedRestore() {
        protectedDir.deleteRecursively()
        files.put("a.txt", "mine")
        val aside = RestoreFolders(files, protectedDir)
        assertTrue(aside.moveAside())
        protectedDir.put("created.txt", "by the restore")
        aside.putBack()
        assertEquals("mine", files.read("a.txt"))
        assertFalse(protectedDir.exists())
    }

    @Test fun oneFolderForBothIsMovedOnce() {
        files.put("a.txt", "mine")
        val aside = RestoreFolders(files, files)
        assertTrue(aside.moveAside())
        files.put("junk.txt", "x")
        aside.putBack()
        assertEquals("mine", files.read("a.txt"))
        assertFalse(File(files, "junk.txt").exists())
    }

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("restore_test", Context.MODE_PRIVATE)

    @Test fun aDamagedSettingsFileChangesNothing() {
        prefs.edit().clear().putBoolean("keep", true).putString("name", "mine").commit()
        val damaged = listOf("boolean settings", "{\"keep\":false", "string settings", "{}")
        assertFalse(restoreSettingsFromLines(damaged, prefs))
        assertTrue(prefs.getBoolean("keep", false))
        assertEquals("mine", prefs.getString("name", null))
        // no known section, a missing line, and a wrong value type are damage too
        assertFalse(restoreSettingsFromLines(listOf("garbage"), prefs))
        assertFalse(restoreSettingsFromLines(listOf("int settings"), prefs))
        assertFalse(restoreSettingsFromLines(listOf("int settings", "{\"n\":\"text\"}"), prefs))
        assertFalse(restoreSettingsFromLines(emptyList(), prefs))
        assertEquals(setOf("keep", "name"), prefs.all.keys)
    }

    @Test fun aGoodSettingsFileReplacesTheSettings() {
        prefs.edit().clear().putBoolean("old", true).commit()
        val good = listOf("boolean settings", "{\"keep\":false}", "int settings", "{\"n\":3}", "string settings", "{\"s\":\"x\"}",
            "string set settings", "{\"set\":[\"a\",\"b\"]}")
        assertTrue(restoreSettingsFromLines(good, prefs))
        assertEquals(setOf("keep", "n", "s", "set"), prefs.all.keys)
        assertEquals(3, prefs.getInt("n", 0))
        assertEquals(setOf("a", "b"), prefs.getStringSet("set", null))
    }
}

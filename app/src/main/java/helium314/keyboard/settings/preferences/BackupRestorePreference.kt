// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.filePicker
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.content.edit
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.transferOldPinnedClips
import helium314.keyboard.latin.utils.IntentUtils

// fork (38h): [row] draws the entry, so the area page "System & Daten" opens the same dialog from its own row
@Composable
fun BackupRestorePreference(setting: Setting, row: @Composable (onClick: () -> Unit) -> Unit = { Preference(name = setting.title, onClick = it) }) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var error: String? by rememberSaveable { mutableStateOf(null) }
    val backupLauncher = backupLauncher { error = it }
    val restoreLauncher = restoreLauncher { error = it }
    row { showDialog = true }
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            content = { Text(stringResource(R.string.backup_restore_message)) },
            confirmButtonText = stringResource(R.string.button_backup),
            neutralButtonText = stringResource(R.string.button_restore),
            onNeutral = {
                showDialog = false
                val intent = IntentUtils.getResolvableTypeIntent(ctx, "application/zip")
                    .setAction(Intent.ACTION_OPEN_DOCUMENT)
                restoreLauncher.launch(intent)
            },
            onConfirmed = {
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                val intent = IntentUtils.getResolvableTypeIntent(ctx, "application/zip")
                    .setAction(Intent.ACTION_CREATE_DOCUMENT)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        ctx.getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_backup_$currentDate.zip"
                    )
                backupLauncher.launch(intent)
            }
        )
    }
    if (error != null) {
        InfoDialog(
            if (error!!.startsWith("b"))
                stringResource(R.string.backup_error, error!!.drop(1))
            else stringResource(R.string.restore_error, error!!.drop(1))
        ) { error = null }
    }
}

@Composable
private fun backupLauncher(onError: (String) -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        // zip all files matching the backup patterns
        // essentially this is the typed words information, and user-added dictionaries
        val filesDir = ctx.filesDir ?: return@filePicker
        val filesPath = filesDir.path + File.separator
        val files = mutableListOf<File>()
        filesDir.walk().forEach { file ->
            val path = file.path.replace(filesPath, "")
            if (file.isFile && backupFilePatterns.any { path.matches(it) })
                files.add(file)
        }
        val protectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)
        val protectedFilesPath = protectedFilesDir.path + File.separator
        val protectedFiles = mutableListOf<File>()
        protectedFilesDir.walk().forEach { file ->
            val path = file.path.replace(protectedFilesPath, "")
            if (file.isFile && backupFilePatterns.any { path.matches(it) })
                protectedFiles.add(file)
        }
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                    // write files to zip
                    val zipStream = ZipOutputStream(os)
                    files.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(filesPath, "")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    protectedFiles.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(protectedFilesDir.path, "unprotected")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    val dbFile = ctx.getDatabasePath(Database.NAME)
                    if (dbFile.exists()) {
                        val fileStream = FileInputStream(dbFile).buffered()
                        zipStream.putNextEntry(ZipEntry(Database.NAME))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    zipStream.putNextEntry(ZipEntry(PREFS_FILE_NAME))
                    settingsToJsonStream(ctx.prefs().all, zipStream)
                    zipStream.closeEntry()
                    zipStream.putNextEntry(ZipEntry(PROTECTED_PREFS_FILE_NAME))
                    settingsToJsonStream(ctx.protectedPrefs().all, zipStream)
                    zipStream.closeEntry()
                    zipStream.close()
                }
            } catch (t: Throwable) {
                onError("b" + t.message)
                Log.w("AdvancedScreen", "error during backup", t)
            } finally {
                wait.countDown()
            }
        }
        wait.await()
    }
}

@Composable
private fun restoreLauncher(onError: (String) -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        val wait = CountDownLatch(1)
        var rollbackFailed = false // the data is not as it was: the follow-up steps must not act on it
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            val restoredDb = ctx.getDatabasePath(Database.NAME + "_restored")
            val folders = RestoreFolders(ctx.filesDir!!, DeviceProtectedUtils.getFilesDir(ctx))
            // stage 65 (S3, S4): moving aside, the work and the roll-back are in restoreWithRollback
            val result = restoreWithRollback(folders, restoredDb, ctx.prefs(), ctx.protectedPrefs()) {
                var anyMatch = false
                val filesDir = ctx.filesDir!!
                val deviceProtectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)
                ctx.getActivity()?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        LayoutUtilsCustom.onLayoutFileChanged()
                        Settings.getInstance().stopListener()
                        while (entry != null) {
                            if (entry.name.startsWith("unprotected${File.separator}")) {
                                val adjustedName = entry.name.substringAfter("unprotected${File.separator}")
                                if (backupFilePatterns.any { adjustedName.matches(it) }) {
                                    if (!restoreEntryToDir(zip, deviceProtectedFilesDir, adjustedName)) {
                                        Log.w("AdvancedScreen", "skipping unsafe backup entry $adjustedName")
                                    }
                                }
                                anyMatch = true
                            } else if (backupFilePatterns.any { entry.name.matches(it) }) {
                                if (!restoreEntryToDir(zip, filesDir, entry.name)) {
                                    Log.w("AdvancedScreen", "skipping unsafe backup entry ${entry.name}")
                                }
                                anyMatch = true
                            } else if (entry.name == Database.NAME) {
                                anyMatch = true
                                FileUtils.copyStreamToNewFile(zip, restoredDb)
                            } else if (entry.name == PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                anyMatch = true
                                checkSettingsRestore(ctx, restoreSettingsFromLines(prefLines, ctx.prefs()))
                            } else if (entry.name == PROTECTED_PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                checkSettingsRestore(ctx, restoreSettingsFromLines(prefLines, ctx.protectedPrefs()))
                                anyMatch = true
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: throw Exception("the backup file could not be opened")
                if (!anyMatch)
                    throw Exception("nothing to restore in the given file")
                Database.copyFromDb(restoredDb, ctx)
            }
            if (result.ok) {
                if (Looper.myLooper() == null)
                    Looper.prepare()
                Toast.makeText(ctx, ctx.getString(R.string.backup_restored), Toast.LENGTH_LONG).show()
            } else {
                // only a restore that put everything back may say that nothing was changed
                val message = when {
                    !result.rolledBack -> ctx.getString(R.string.restore_rollback_failed)
                    result.error is RestoreNotStarted -> ctx.getString(R.string.restore_nothing_changed)
                    else -> result.error?.message ?: result.error?.javaClass?.simpleName ?: ""
                }
                onError("r$message")
                Log.w("AdvancedScreen", "error during restore", result.error)
                if (!result.rolledBack) rollbackFailed = true
            }
            wait.countDown()
        }
        wait.await()
        checkVersionUpgrade(ctx)
        transferOldPinnedClips(ctx)
        Settings.getInstance().startListener()
        SubtypeSettings.reloadEnabledSubtypes(ctx)
        val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
        ctx.getActivity()?.sendBroadcast(newDictBroadcast)
        LayoutUtilsCustom.onLayoutFileChanged()
        // review of stage 65 (N1): removing layout settings of layouts that are "missing" would delete them for good
        if (!rollbackFailed) LayoutUtilsCustom.removeMissingLayouts(ctx)
        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        SupportedEmojis.load(ctx)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}

private fun checkSettingsRestore(ctx: android.content.Context, result: SettingsRestore) {
    when (result) {
        SettingsRestore.OK -> {}
        SettingsRestore.DAMAGED -> throw Exception(ctx.getString(R.string.restore_settings_damaged))
        SettingsRestore.NOT_SAVED -> throw Exception(ctx.getString(R.string.restore_settings_not_saved))
    }
}

@Suppress("UNCHECKED_CAST") // it is checked... but whatever (except string set, because can't check for that))
private fun settingsToJsonStream(settings: Map<String?, Any?>, out: OutputStream) {
    val booleans = settings.filter { it.key is String && it.value is Boolean } as Map<String, Boolean>
    val ints = settings.filter { it.key is String && it.value is Int } as Map<String, Int>
    val longs = settings.filter { it.key is String && it.value is Long } as Map<String, Long>
    val floats = settings.filter { it.key is String && it.value is Float } as Map<String, Float>
    val strings = settings.filter { it.key is String && it.value is String } as Map<String, String>
    val stringSets = settings.filter { it.key is String && it.value is Set<*> } as Map<String, Set<String>>
    // now write
    out.write("boolean settings\n".toByteArray())
    out.write(Json.encodeToString(booleans).toByteArray())
    out.write("\nint settings\n".toByteArray())
    out.write(Json.encodeToString(ints).toByteArray())
    out.write("\nlong settings\n".toByteArray())
    out.write(Json.encodeToString(longs).toByteArray())
    out.write("\nfloat settings\n".toByteArray())
    out.write(Json.encodeToString(floats).toByteArray())
    out.write("\nstring settings\n".toByteArray())
    out.write(Json.encodeToString(strings).toByteArray())
    out.write("\nstring set settings\n".toByteArray())
    out.write(Json.encodeToString(stringSets).toByteArray())
}

internal enum class SettingsRestore { OK, DAMAGED, NOT_SAVED }

/**
 * Stage 65 (S4): replaces the settings in [prefs] by the ones in [list], but only if the whole list
 * can be read (also its later sections). A damaged file returns [SettingsRestore.DAMAGED] and leaves
 * [prefs] as they were; a failed commit returns [SettingsRestore.NOT_SAVED] (a failed restore).
 */
internal fun restoreSettingsFromLines(list: List<String>, prefs: SharedPreferences): SettingsRestore {
    val staged = parseSettingsLines(list) ?: return SettingsRestore.DAMAGED
    return if (writeSettings(prefs, staged)) SettingsRestore.OK else SettingsRestore.NOT_SAVED
}

/** Replaces all of [prefs] by [values]; false if the commit did not succeed. */
internal fun writeSettings(prefs: SharedPreferences, values: Map<String, Any?>): Boolean {
    val e = prefs.edit()
    e.clear()
    values.forEach { (key, value) ->
        when (value) {
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Long -> e.putLong(key, value)
            is Float -> e.putFloat(key, value)
            is String -> e.putString(key, value)
            is Set<*> -> e.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
    return e.commit()
}

private fun parseSettingsLines(list: List<String>): Map<String, Any>? {
    val i = list.iterator()
    val staged = LinkedHashMap<String, Any>()
    var sections = 0
    try {
        while (i.hasNext()) {
            when (i.next()) {
                "boolean settings" -> staged.putAll(Json.decodeFromString<Map<String, Boolean>>(i.next()))
                "int settings" -> staged.putAll(Json.decodeFromString<Map<String, Int>>(i.next()))
                "long settings" -> staged.putAll(Json.decodeFromString<Map<String, Long>>(i.next()))
                "float settings" -> staged.putAll(Json.decodeFromString<Map<String, Float>>(i.next()))
                "string settings" -> staged.putAll(Json.decodeFromString<Map<String, String>>(i.next()))
                "string set settings" -> staged.putAll(Json.decodeFromString<Map<String, Set<String>>>(i.next()))
                else -> continue
            }
            sections++
        }
    } catch (e: Exception) {
        return null
    }
    return if (sections == 0) null else staged
}

/** The restore did not start; [RestoreResult.rolledBack] tells whether everything is as it was. */
internal class RestoreNotStarted : Exception("the data folders could not be prepared")

internal class RestoreResult(val ok: Boolean, val rolledBack: Boolean, val error: Throwable?)

/**
 * Stage 65 (S3, S4): moves the data [folders] aside, runs [work] (which fills the folders, the settings
 * and the database) and drops the copies; if anything throws, puts folders and both settings files back.
 * [RestoreResult.rolledBack] is false when a roll-back step failed: then the data is not as it was.
 */
internal fun restoreWithRollback(
    folders: RestoreFolders, restoredDb: File, prefs: SharedPreferences, protectedPrefs: SharedPreferences, work: () -> Unit,
): RestoreResult {
    val oldPrefs = prefs.all.toMap()
    val oldProtectedPrefs = protectedPrefs.all.toMap()
    restoredDb.delete() // a copy of an earlier restore must never be read as part of this one
    if (!folders.moveAside()) return RestoreResult(false, folders.intact, RestoreNotStarted())
    try {
        work()
    } catch (t: Throwable) {
        var restored = folders.putBack()
        restored = writeSettings(prefs, oldPrefs) && restored
        restored = writeSettings(protectedPrefs, oldProtectedPrefs) && restored
        restoredDb.delete()
        return RestoreResult(false, restored, t)
    }
    folders.discardCopies()
    return RestoreResult(true, true, null)
}

/**
 * Stage 65 (S3): the two data folders of a restore. [moveAside] first rolls back an interrupted earlier
 * restore (a leftover `<name>2` is the user's data from before: it replaces the half-written folder),
 * then renames both folders to `<name>2` as a pair (all or nothing). [putBack] returns the originals,
 * [discardCopies] drops them. Every step is checked; the copy is never deleted unless its folder is
 * back in place. [rename] and [delete] can be replaced in tests.
 */
internal class RestoreFolders(
    private val filesDir: File,
    private val protectedDir: File,
    private val rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
    private val delete: (File) -> Boolean = { it.deleteRecursively() },
) {
    private val moved = ArrayList<Pair<File, File>>() // original to aside copy, of the folders moved
    private val absent = ArrayList<File>() // folders that did not exist before: a failed restore removes what it made

    /** After [moveAside] returned false: whether the folders are as they were before it started. */
    var intact = false
        private set

    private val folders: List<File>
        get() = if (runCatching { filesDir.canonicalPath == protectedDir.canonicalPath }.getOrDefault(false)) listOf(filesDir)
        else listOf(filesDir, protectedDir)

    private fun copyOf(folder: File) = File(folder.absolutePath + "2")
    private fun trashOf(folder: File) = File(folder.absolutePath + "2_discard")

    private fun recoverInterrupted(): Boolean {
        for (folder in folders) {
            trashOf(folder).takeIf { it.exists() }?.let { delete(it) } // best effort, never a copy to restore
            val copy = copyOf(folder)
            if (!copy.exists()) continue
            if (folder.exists() && !delete(folder)) return false
            if (!rename(copy, folder)) return false
        }
        return true
    }

    fun moveAside(): Boolean {
        intact = false
        if (!recoverInterrupted()) return false
        for (folder in folders) {
            if (!folder.exists()) { absent.add(folder); continue } // nothing to keep
            val copy = copyOf(folder)
            if (!rename(folder, copy)) { intact = putBack(); return false }
            moved.add(folder to copy)
        }
        intact = true
        return true
    }

    /** Failed restore: the new content goes, the originals come back. True if every step worked. */
    fun putBack(): Boolean {
        var ok = true
        for ((folder, copy) in moved.reversed()) {
            if (folder.exists() && !delete(folder)) { ok = false; continue } // the copy stays where it is
            if (!rename(copy, folder)) ok = false
        }
        moved.clear()
        for (folder in absent) if (folder.exists() && !delete(folder)) ok = false
        absent.clear()
        return ok
    }

    /** Successful restore: the copies go. The copy is renamed first, so a half-deleted one is never taken for a roll-back. */
    fun discardCopies(): Boolean {
        var ok = true
        for ((_, copy) in moved) {
            val trash = File(copy.absolutePath + "_discard")
            if (rename(copy, trash)) delete(trash) else if (!delete(copy)) ok = false
        }
        moved.clear()
        absent.clear()
        return ok
    }
}

private fun restoreEntryToDir(zip: ZipInputStream, baseDir: File, entryName: String): Boolean {
    val file = File(baseDir, entryName)
    val canonicalBase = baseDir.canonicalFile
    val canonicalTarget = file.canonicalFile
    if (canonicalTarget.path != canonicalBase.path
        && !canonicalTarget.path.startsWith(canonicalBase.path + File.separator)
    ) return false
    FileUtils.copyStreamToNewFile(zip, file)
    return true
}

private const val PREFS_FILE_NAME = "preferences.json"
private const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"

private val backupFilePatterns by lazy { listOf(
    "blacklists${File.separator}.*\\.txt".toRegex(),
    "layouts${File.separator}.*${LayoutUtilsCustom.CUSTOM_LAYOUT_PREFIX}+\\..{0,4}".toRegex(), // can't expect a period at the end, as this would break restoring older backups
    "dicts${File.separator}.*${File.separator}.*user\\.dict".toRegex(),
    "UserHistoryDictionary.*${File.separator}UserHistoryDictionary.*\\.(body|header)".toRegex(),
    "custom_background_image.*".toRegex(),
    "custom_font".toRegex(),
    "custom_emoji_font".toRegex(),
    "clipboard/.*".toRegex(),
) }

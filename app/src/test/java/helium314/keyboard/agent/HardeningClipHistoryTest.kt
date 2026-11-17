// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PersistableBundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.clip.ClipKind
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A provider that knows the size of one image and serves its bytes, for clips that carry a Uri. */
class Stage65ImageProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(10L)) }
    override fun getType(uri: Uri) = "image/png"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = File.createTempFile("stage65", ".png").apply { writeBytes(ByteArray(10) { it.toByte() }); deleteOnExit() }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}

/** Stage 65 (S2, S8): one keep rule for the vault and the history, and what does not outlive the service. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class HardeningClipHistoryTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val manager get() = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val keyguard get() = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val plain = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val noLearning = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
    private val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }

    @Before fun setUp() {
        ClipVault.fieldFinished(null)
        latinIME.prefs().edit().clear().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        load(plain)
        ClipboardDao.getInstance(latinIME)?.clear()
        ClipVault.clear()
    }

    @After fun tearDown() {
        ClipVault.fieldFinished(null)
        shadowOf(keyguard).setIsDeviceLocked(false)
        ClipboardDao.getInstance(latinIME)?.clear()
        ClipVault.clear()
        latinIME.prefs().edit().clear().commit()
        load(plain)
    }

    private fun load(editor: EditorInfo, incognito: Boolean = false) {
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, incognito).commit()
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editor, false, latinIME.packageName))
    }

    /** The input view starts in [editor] through the real hook, as `onStartInputView` does. */
    private fun startField(editor: EditorInfo) {
        load(editor)
        LastField.capture(latinIME, editor, Settings.getValues(), null)
    }

    private fun copy(text: String) = manager.setPrimaryClip(ClipData.newPlainText("", text))
    private fun historyTexts() = ClipboardDao.getInstance(latinIME)!!.getAll().map { it.text }

    private val text = "Einkaufsliste: Brot und Milch"

    @Test fun theControlCaseIsKept() {
        startField(plain)
        copy(text)
        assertTrue(historyTexts().contains(text))
    }

    @Test fun aClipCopiedWhileAFieldWithoutLearningIsActiveIsNotKept() {
        startField(noLearning)
        copy(text)
        assertFalse(historyTexts().contains(text))
        assertTrue(ClipVault.secrets().isEmpty())
    }

    @Test fun aClipCopiedWhileAPasswordFieldIsActiveIsNotKept() {
        startField(password)
        copy(text)
        assertFalse(historyTexts().contains(text))
    }

    @Test fun aClipCopiedByAnotherAppWhileTheKeyboardIsHiddenIsKeptAndANotKeptOneDoesNotComeBack() {
        startField(noLearning)
        copy("$text eins") // active private field: not kept
        // the keyboard hides (the same hook as LatinIME.onFinishInputViewInternal), the user copies in the browser
        ClipVault.fieldFinished(null)
        copy("$text zwei")
        assertTrue(historyTexts().contains("$text zwei"))
        // the earlier clip was not kept and nothing offers it again
        assertFalse(historyTexts().contains("$text eins"))
        assertEquals(listOf("$text zwei"), historyTexts().filter { it?.startsWith(text) == true })
    }

    @Test fun alwaysIncognitoKeepsNothingInEitherState() {
        load(plain, incognito = true)
        copy(text)
        assertFalse(historyTexts().contains(text))
        ClipVault.fieldStarted(true)
        copy("$text 2")
        ClipVault.fieldFinished(null)
        copy("$text 3")
        assertTrue(historyTexts().none { it?.startsWith(text) == true })
    }

    @Test fun aLockedDeviceKeepsNothingInEitherState() {
        shadowOf(keyguard).setIsDeviceLocked(true)
        copy(text)
        ClipVault.fieldStarted(false)
        copy("$text 2")
        shadowOf(keyguard).setIsDeviceLocked(false)
        assertTrue(historyTexts().none { it?.startsWith(text) == true })
    }

    // --- files and images ------------------------------------------------------------------

    private fun imageClip(sensitive: Boolean): ClipData {
        val uri = Uri.parse("content://stage65.images/a${System.nanoTime()}.png")
        return ClipData(ClipDescription("", arrayOf("image/png")).apply {
            if (sensitive) extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }, ClipData.Item(uri))
    }

    private fun imagesOn() {
        Robolectric.setupContentProvider(Stage65ImageProvider::class.java, "stage65.images")
        latinIME.prefs().edit().putBoolean(Settings.PREF_CLIPBOARD_USE_FILES, true).commit()
    }

    @Test fun aSensitiveImageClipIsNotKeptAndANormalOneIs() {
        imagesOn()
        val dao = ClipboardDao.getInstance(latinIME)!!
        manager.setPrimaryClip(imageClip(sensitive = true))
        assertEquals(0, dao.count())
        manager.setPrimaryClip(imageClip(sensitive = false))
        assertEquals(1, dao.count())
    }

    @Test fun anImageClipFollowsTheFieldRuleToo() {
        imagesOn()
        val dao = ClipboardDao.getInstance(latinIME)!!
        startField(noLearning)
        manager.setPrimaryClip(imageClip(sensitive = false))
        assertEquals(0, dao.count())
        ClipVault.fieldFinished(null)
        manager.setPrimaryClip(imageClip(sensitive = false))
        assertEquals(1, dao.count())
    }

    @Test fun theRuleTable() {
        assertTrue(ClipVault.mayKeep(locked = false, alwaysIncognito = false, sensitive = false, privateFieldActive = false))
        assertFalse(ClipVault.mayKeep(locked = true, alwaysIncognito = false, sensitive = false, privateFieldActive = false))
        assertFalse(ClipVault.mayKeep(locked = false, alwaysIncognito = true, sensitive = false, privateFieldActive = false))
        assertFalse(ClipVault.mayKeep(locked = false, alwaysIncognito = false, sensitive = true, privateFieldActive = false))
        assertFalse(ClipVault.mayKeep(locked = false, alwaysIncognito = false, sensitive = false, privateFieldActive = true))
        assertFalse(ClipVault.mayKeep(latinIME, null))
    }

    // --- S8: what the end of the service clears -----------------------------------------------

    @Test fun destroyingTheServiceClearsTheVaultAndEveryReferenceItHolds() {
        val key = "sk" + "-proj-" + "Ab3dE5fG7hJ9kL1mN3pQ5rS7tU9vW1xY3z" + "7f3a"
        val now = System.currentTimeMillis()
        ClipVault.clock = { now }
        try {
            ClipVault.offer(key, false, now, "DE", true)
            ClipVault.offer("482913", false, now, "DE", true)
            assertEquals(1, ClipVault.secrets().size) // so that the checks after the destroy mean something
            assertEquals(1, ClipVault.codes().size)
            ClipVault.onChange = { }
            ScreenshotChip.onChange = { }
            val manager = latinIME.clipboardHistoryManager
            fun set(name: String, value: Any?) = manager.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
            fun get(name: String) = manager.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(manager)
            set("clipboardSuggestionView", View(latinIME))
            set("screenshotView", View(latinIME))
            assertNotNull(ClipVault.currentClip)

            manager.onDestroy()

            assertNull(ClipVault.currentClip)
            assertNull(ClipVault.onChange)
            assertNull(ScreenshotChip.onChange)
            assertNull(get("clipboardSuggestionView"))
            assertNull(get("screenshotView"))
            assertTrue(ClipVault.secrets().isEmpty())
            assertTrue(ClipVault.codes().isEmpty())
            assertNull(ClipVault.freshest(latinIME, ClipKind.OTP, 60_000))
        } finally {
            ClipVault.clock = { System.currentTimeMillis() }
        }
    }
}

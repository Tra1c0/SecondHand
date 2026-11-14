// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import helium314.keyboard.agent.security.AesGcmSecretCipher
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AgentMyDataScreen
import helium314.keyboard.settings.screens.MY_DATA_FIELD_TAG
import helium314.keyboard.settings.screens.MY_DATA_LOCKED_TAG
import helium314.keyboard.settings.screens.MY_DATA_SAVE_TAG
import helium314.keyboard.settings.screens.MY_DATA_SWITCH_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import javax.crypto.KeyGenerator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 45: the page "Meine Daten & Vorschläge". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class MyDataScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmSecretCipher { aesKey }
    private var created = 0
    private fun store(context: Context) = MyDataStore(context, cipher) { true }.also { created++ }
    private val directory get() = File(credentialContext(app).noBackupFilesDir, MyDataStore.DIRECTORY)
    private val keyguard get() = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    @Before fun setUp() {
        directory.listFiles()?.forEach { it.delete() }
        app.prefs().edit().clear().commit()
    }

    @After fun tearDown() {
        shadowOf(keyguard).setIsDeviceLocked(false)
        directory.listFiles()?.forEach { it.delete() }
        app.prefs().edit().clear().commit()
    }

    private fun show() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentMyDataScreen(onClickBack = {}, storeFactory = ::store) } }
        compose.waitForIdle()
    }

    @Test fun startsEmptyAndSavesEncrypted() {
        show()
        // the fields open once the (empty) data is read
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag(MY_DATA_FIELD_TAG + 1).assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag(MY_DATA_FIELD_TAG + 1).performScrollTo().performTextInput("erika@example.org")
        compose.onNodeWithTag(MY_DATA_SAVE_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(5000) { directory.listFiles()?.isNotEmpty() == true }
        assertEquals(listOf("erika@example.org"), store(app).read().emails)
        assertFalse(directory.listFiles()!!.single().readBytes().toString(Charsets.ISO_8859_1).contains("erika"))
    }

    @Test fun lockedShowsOnlyTheHintAndOpensNoStore() {
        shadowOf(keyguard).setIsDeviceLocked(true)
        show()
        compose.onNodeWithTag(MY_DATA_LOCKED_TAG).assertExists()
        compose.onNodeWithTag(MY_DATA_FIELD_TAG + 0).assertDoesNotExist()
        assertEquals(0, created)
        // the switch is no personal data and stays usable
        assertTrue(FieldSuggestionChips.isEnabled(app))
        compose.onNodeWithTag(MY_DATA_SWITCH_TAG).performClick()
        compose.waitForIdle()
        assertFalse(FieldSuggestionChips.isEnabled(app))
    }
}

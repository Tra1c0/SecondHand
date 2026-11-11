// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.database.ClipboardContentProvider
import helium314.keyboard.settings.screens.gesturedata.GestureFileProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AgentStorageContextsTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun clipboardProviderUsesEngineFilesEvenWhenAttachedFromCredentialContext() {
        val authority = BuildConfig.APPLICATION_ID + ".clipprovider"
        val info = app.packageManager.resolveContentProvider(authority, 0)!!
        val provider = ClipboardContentProvider()
        provider.attachInfo(credentialContext(app), info)
        assertTrue(provider.context!!.isDeviceProtectedStorage)
        val file = File(app.filesDir, "clipboard/storage-test.txt").apply { parentFile!!.mkdirs(); writeText("clipboard-test") }
        val uri = FileProvider.getUriForFile(app, authority, file)
        val content = ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri, "r")!!).use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals("clipboard-test", content)
        val secret = File(credentialContext(app).noBackupFilesDir, "agent_credentials/private.sealed")
        assertFailsWith<IllegalArgumentException> { FileProvider.getUriForFile(app, authority, secret) }
    }

    @Test fun gestureProviderUsesEngineContextAndCannotShareCredentialStorage() {
        val authority = BuildConfig.APPLICATION_ID + ".provider"
        val provider = GestureFileProvider()
        provider.attachInfo(credentialContext(app), app.packageManager.resolveContentProvider(authority, 0)!!)
        assertTrue(provider.context!!.isDeviceProtectedStorage)
        assertEquals(app.filesDir, provider.context!!.filesDir)
        val secret = File(credentialContext(app).noBackupFilesDir, "agent_credentials/private.sealed")
        assertFailsWith<IllegalArgumentException> { FileProvider.getUriForFile(app, authority, secret) }
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForkIdentityTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun forkProvidersUseOwnAuthorities() {
        assertTrue(BuildConfig.APPLICATION_ID.startsWith("dev.personal.agentkeyboard"))
        assertTrue(app.packageManager.getApplicationLabel(app.applicationInfo).toString().startsWith("SecondHand"))
        for (suffix in listOf(".clipprovider", ".provider")) {
            val provider = app.packageManager.resolveContentProvider(BuildConfig.APPLICATION_ID + suffix, 0)
            assertNotNull("Own provider must resolve: $suffix", provider)
            assertEquals(BuildConfig.APPLICATION_ID, provider!!.packageName)
            assertFalse(provider.exported)
        }
    }

    @Test fun automaticBackupsAreDisabled() {
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun cloudPermissionIsPresentAndCleartextTransportIsDisabled() {
        val info = app.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
    }

    @Test fun systemVisibleNamesSaySecondHandNotHeliBoard() {
        // the fork strings the manifest and the spell checker XML point to (debug variants override them, still "SecondHand ...")
        for (id in listOf(R.string.english_ime_name, R.string.agent_spell_checker_service_name,
            R.string.agent_ime_settings, R.string.agent_android_spell_checker_settings)) {
            val text = app.getString(id)
            assertTrue(text, text.startsWith("SecondHand"))
            assertFalse(text, text.contains("HeliBoard") || text.contains("Agent Keyboard") || text.contains("Lab"))
        }
        assertEquals("SecondHand", app.getString(R.string.english_ime_name))
        assertEquals("SecondHand", app.packageManager.getApplicationLabel(app.applicationInfo).toString())
        assertTrue(app.getString(R.string.agent_screen_reader_label).startsWith("SecondHand"))
    }

    @Test fun launcherIconResourcesResolve() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round, R.drawable.ic_launcher_foreground,
            R.drawable.ic_launcher_background, R.drawable.ic_launcher_monochrome))
            assertNotNull("icon resource $id", ContextCompat.getDrawable(app, id))
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import helium314.keyboard.latin.BuildConfig
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
        assertTrue(app.packageManager.getApplicationLabel(app.applicationInfo).toString().startsWith("Agent Keyboard"))
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
}

// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.latin.define.DebugFlags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DebugPrivacyTest {
    @Test fun debugInitializationPreservesThePlatformCrashHandler() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        DebugFlags.init(RuntimeEnvironment.getApplication())
        assertSame(original, Thread.getDefaultUncaughtExceptionHandler())
    }
}

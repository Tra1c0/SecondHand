// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.view.Window
import android.view.WindowManager
import java.util.WeakHashMap

/**
 * Reference-counted FLAG_SECURE per window. Protected settings pages can be nested (AI area ->
 * AI provider page) and the nav host animates, so the new page starts before the old one is
 * disposed. With a per-page "wasSecure" snapshot the old page would clear the flag under the new
 * one; here the flag only falls when the last holder releases it, and only if it was not set
 * before the first holder took it.
 */
internal object SecureWindow {
    private class Hold(var count: Int, val wasSecure: Boolean)
    private val holds = WeakHashMap<Window, Hold>()

    fun acquire(window: Window?) {
        window ?: return
        val hold = holds[window]
        if (hold != null) hold.count++
        else holds[window] = Hold(1, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window?) {
        window ?: return
        val hold = holds[window] ?: return
        hold.count--
        if (hold.count > 0) return
        holds.remove(window)
        if (!hold.wasSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

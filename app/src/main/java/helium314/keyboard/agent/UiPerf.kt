// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver

/**
 * Stage 102: measuring points for the clipboard view (before and after the screenshot shelf). A [mark] remembers when
 * something started (the clipboard key, a media change, a tap on a folder); [reportAtNextDraw] writes the milliseconds
 * from that mark to the next frame the view draws, as one line with the tag [TAG]. Only times and counts are written,
 * never a clip, a name or a file; the emulator tests read these lines (tools/devicetest/devicetest.py).
 */
object UiPerf {
    const val TAG = "AgentPerf"
    const val CLIPBOARD_OPEN = "clipboard_open"
    const val SHOT_VISIBLE = "shot_visible"
    const val FOLDER_OPEN = "folder_open"
    /** A mark older than this belongs to nothing that is drawn now (e.g. a media change while the view was closed). */
    private const val MAX_AGE_MILLIS = 10_000L

    private val marks = HashMap<String, Long>()

    /** Main thread. [keepEarliest]: a second mark of the same kind before the report keeps the first (a screenshot is reported several times). */
    @JvmStatic @JvmOverloads
    fun mark(name: String, keepEarliest: Boolean = false, at: Long = SystemClock.uptimeMillis()) {
        val known = marks[name]
        if (keepEarliest && known != null && at - known in 0..MAX_AGE_MILLIS) return
        marks[name] = at
    }

    /** Main thread: writes "<name> ms=<from the mark to the next frame of [view]> <details>" once; nothing without a fresh mark. */
    @JvmStatic
    fun reportAtNextDraw(name: String, view: View, details: () -> String = { "" }) {
        val start = marks.remove(name) ?: return
        if (SystemClock.uptimeMillis() - start !in 0..MAX_AGE_MILLIS) return
        val observer = view.viewTreeObserver
        if (!observer.isAlive) return
        val listener = object : ViewTreeObserver.OnDrawListener {
            var done = false
            override fun onDraw() {
                if (done) return
                done = true
                val millis = SystemClock.uptimeMillis() - start
                // a draw listener must not remove itself while the tree draws
                view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this) }
                runCatching { android.util.Log.i(TAG, "$name ms=$millis ${details()}".trim()) }
            }
        }
        observer.addOnDrawListener(listener)
        view.invalidate()
    }
}

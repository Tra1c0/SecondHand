// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.Executors

/** Reads the visible text of other apps once, when the user asks for it (stage 24e). */
interface ScreenTextReader {
    /** True while the accessibility service is switched on and connected. */
    val isAvailable: Boolean
    /** Reads once; [onResult] runs on the main thread with the texts, or null if nothing could be read. */
    fun read(onResult: (List<CharSequence>?) -> Unit)
}

/**
 * Accessibility service for `@url` → "Read screen" (stage 24e, explicitly approved by the owner).
 *
 * It subscribes to no events and asks for no gestures or key filtering ([R.xml.agent_screen_reader]),
 * so the system never wakes it; it only reads when the keyboard calls [read] after a tap. It runs in
 * the keyboard's process, so the keyboard reaches it through the static [connected] instance
 * (set in [onServiceConnected], cleared in [onUnbind] and [onDestroy]); there is no IPC of its own.
 *
 * A read collects the text of the visible app windows (or the active window when the window list
 * is not available), skips password nodes, nodes marked as sensitive and the keyboard's own
 * windows, and stops after [MAX_NODES] nodes or [MAX_CHARS] characters. The texts are handed to
 * the caller and kept nowhere; nothing is logged.
 */
class ScreenReaderService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (connected === this) connected = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    // subscribed to no event types: never called by the system
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /** The visible texts; runs on a background thread (each node access is a call to the app). */
    internal fun collectTexts(): List<CharSequence> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val windows = runCatching { windows }.getOrNull().orEmpty()
        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            window.root?.let(roots::add)
        }
        if (roots.isEmpty()) runCatching { rootInActiveWindow }.getOrNull()?.let(roots::add)
        return collectTexts(roots, packageName)
    }

    companion object : ScreenTextReader {
        const val MAX_NODES = 3000
        const val MAX_CHARS = 20_000

        @Volatile private var connected: ScreenReaderService? = null
        private val executor by lazy { Executors.newSingleThreadExecutor() }
        private val main by lazy { Handler(Looper.getMainLooper()) }

        override val isAvailable: Boolean get() = connected != null

        override fun read(onResult: (List<CharSequence>?) -> Unit) {
            val service = connected ?: return onResult(null)
            executor.execute {
                val texts = runCatching { service.collectTexts() }.getOrNull()
                main.post { onResult(texts) }
            }
        }

        /**
         * Texts of the node trees under [roots], breadth first, without password or sensitive
         * nodes and without trees of [ownPackage] (the keyboard and its settings).
         */
        internal fun collectTexts(roots: List<AccessibilityNodeInfo>, ownPackage: String): List<CharSequence> {
            val texts = mutableListOf<CharSequence>()
            var nodes = 0
            var chars = 0
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            for (root in roots) if (root.packageName?.toString() != ownPackage) queue.add(root)
            while (queue.isNotEmpty() && nodes < MAX_NODES && chars < MAX_CHARS) {
                val node = queue.removeFirst()
                nodes++
                if (node.isPassword || isSensitive(node)) continue // also skips its children
                node.text?.takeIf { it.isNotBlank() }?.let { text ->
                    val part = if (text.length > MAX_CHARS - chars) text.subSequence(0, MAX_CHARS - chars) else text
                    texts += part.toString() // a copy: the node is not kept
                    chars += part.length
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            }
            return texts
        }

        private fun isSensitive(node: AccessibilityNodeInfo) =
            Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive

        /** Tests only. */
        internal fun setConnectedForTest(service: ScreenReaderService?) { connected = service }
    }
}

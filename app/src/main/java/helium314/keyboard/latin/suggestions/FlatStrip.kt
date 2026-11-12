// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.FlatMenuView
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsActivity

/**
 * Arrangement of the strip above the keys in the Flat style, measured on the reference
 * screenshots (1200 px wide, 3.25 px per dp): buttons of 34 dp, the outer two 6 dp from the
 * edges, the toolbar keys spread evenly between them, everything centred 22 dp below the upper
 * edge. The key at the right end sits in a filled circle. The left key opens [FlatMenuView].
 *
 * Everything is set once when the strip is created. Switching between toolbar and suggestions
 * only toggles visibility, without layout animation, so that it costs a single layout pass.
 */
internal object FlatStrip {
    private const val BUTTON_DP = 34f
    private const val EDGE_DP = 6f
    /** Height of the row the buttons sit in; a higher strip gets the rest as padding above it (4 dp at 40 dp). */
    private const val CONTENT_ROW_DP = 36f

    fun arrange(
        strip: ViewGroup, wrapper: ViewGroup, expandKey: ImageButton, toolbarContainer: View,
        toolbar: ViewGroup, pinnedKeys: ViewGroup,
    ) {
        val density = strip.resources.displayMetrics.density
        val button = Math.round(BUTTON_DP * density)
        val edge = Math.round(EDGE_DP * density)

        // 40 dp in portrait, 36 dp in phone landscape, where no padding is left
        val stripHeight = strip.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        strip.setPadding(0, (stripHeight - Math.round(CONTENT_ROW_DP * density)).coerceAtLeast(0), 0, 0)
        (wrapper as? LinearLayout)?.layoutTransition = null

        // left key: plain icon instead of the tinted circle
        (expandKey.layoutParams as LinearLayout.LayoutParams).apply {
            width = button; height = button; marginStart = edge
        }
        expandKey.scaleType = ImageView.ScaleType.CENTER
        expandKey.background = null
        expandKey.contentDescription = expandKey.context.getString(R.string.agent_menu_open)

        // The toolbar takes the room between the left key and the pinned keys. Spacers with equal
        // weight before, between and after its keys spread them in the same measure pass; with more
        // keys than fit the spacers collapse and the row scrolls as before.
        (toolbarContainer.layoutParams as LinearLayout.LayoutParams).apply { width = 0; weight = 1f }
        (toolbarContainer as? HorizontalScrollView)?.isFillViewport = true
        toolbar.setPadding(0, 0, 0, 0)
        val keys = toolbar.children.toList()
        keys.forEach { setButtonSize(it, button) }
        for (index in keys.size downTo 0)
            toolbar.addView(Space(toolbar.context), index, LinearLayout.LayoutParams(0, 0, 1f))

        pinnedKeys.setPadding(0, 0, 0, 0)
        (pinnedKeys.layoutParams as LinearLayout.LayoutParams).marginEnd = edge
        pinnedKeys.children.forEach { setButtonSize(it, button) }
        (pinnedKeys.children.lastOrNull() as? ImageButton)?.let { last ->
            last.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(FlatStyle.STRIP_CIRCLE)
            }
            last.drawable?.mutate()?.let {
                it.setTintList(null)
                it.setColorFilter(FlatStyle.STRIP_CIRCLE_ICON, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    /** Fixed square buttons: every key gets its own layout params, the shared instance stays untouched. */
    private fun setButtonSize(view: View, size: Int) {
        view.layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        view.background = null
    }

    /** Quick pin copies a toolbar key together with its layout params; give the copy its own fixed size. */
    fun sizePinnedCopy(original: View, copy: View) {
        val size = Math.round(BUTTON_DP * original.resources.displayMetrics.density)
        setButtonSize(copy, size)
        setButtonSize(original, size)
    }

    // ---- recent emojis while a symbol page is shown (stage 23b) ----

    const val EMOJI_SLOTS = 6
    /** Shown when fewer emojis were used so far. */
    val DEFAULT_EMOJIS = listOf("👍", "😂", "❤️", "🙏", "😊", "🔥")

    /** A row of [EMOJI_SLOTS] emoji buttons spread like the toolbar keys; added once, filled on show. */
    fun createEmojiRow(wrapper: ViewGroup, toolbarContainer: View, onPick: (String) -> Unit): LinearLayout {
        val context = wrapper.context
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            id = R.id.agent_strip_emojis
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        repeat(EMOJI_SLOTS) {
            val button = android.widget.TextView(context).apply {
                gravity = android.view.Gravity.CENTER
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 24f)
                isSoundEffectsEnabled = false
                setOnClickListener { v -> (v as android.widget.TextView).text?.toString()?.takeIf { it.isNotEmpty() }?.let(onPick) }
            }
            row.addView(button, LinearLayout.LayoutParams(Math.round(BUTTON_DP * density), LinearLayout.LayoutParams.MATCH_PARENT))
            row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        }
        wrapper.addView(row, wrapper.indexOfChild(toolbarContainer) + 1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        return row
    }

    /** The emojis to show: recently used first, then defaults, without duplicates. */
    fun emojisToShow(recent: List<String>): List<String> =
        (recent + DEFAULT_EMOJIS).distinct().take(EMOJI_SLOTS)

    fun fillEmojiRow(row: ViewGroup, emojis: List<String>) {
        var slot = 0
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i) as? android.widget.TextView ?: continue
            val emoji = emojis.getOrNull(slot++)
            child.text = emoji ?: ""
            child.contentDescription = emoji
            child.isVisible = emoji != null
        }
    }

    // ---- menu behind the left key ----

    /** Opens or closes the menu over the key area. [onCode] receives the key code of a tapped pill. */
    fun toggleMenu(strip: SuggestionStripView, key: ImageButton, toolbarContainer: View, onCode: (Int) -> Unit) {
        val root = strip.rootView
        val menu = root.findViewById<FlatMenuView>(R.id.agent_menu) ?: return
        if (menu.isOpen) {
            menu.close()
            return
        }
        val keys = root.findViewById<View>(R.id.keyboard_view_wrapper)
        menu.onAction = { action ->
            when (action) {
                is FlatMenuView.Action.Code -> onCode(action.code)
                is FlatMenuView.Action.OpenSettings -> openSettings(strip.context, action.destination)
            }
        }
        // While the menu is open the strip shows the tools, also when the toolbar does not appear on
        // its own (auto-show off): otherwise nothing in this style could reach them.
        val toolbarWasVisible = toolbarContainer.visibility == View.VISIBLE
        strip.dismissMoreSuggestionsPanel()
        if (!toolbarWasVisible) strip.setToolbarVisibility(true)
        menu.onClosed = {
            keys?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            showGridIcon(key)
            if (!toolbarWasVisible) strip.setToolbarVisibility(false)
        }
        // the keys under the menu must not be reachable for screen readers while it is open
        keys?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        menu.open(root.findViewById<View>(R.id.strip_container)?.height ?: strip.height)
        showCloseIcon(key)
    }

    fun closeMenu(strip: View) {
        strip.rootView.findViewById<FlatMenuView>(R.id.agent_menu)?.close()
    }

    /** The strip sets the icon of the left key again on every update; keep the close icon while the menu is open. */
    fun keepMenuIcon(strip: View, key: ImageButton) {
        if (strip.rootView.findViewById<FlatMenuView>(R.id.agent_menu)?.isOpen == true) showCloseIcon(key)
    }

    private fun showCloseIcon(key: ImageButton) {
        if (key.getTag(R.id.agent_menu) == null) key.setTag(R.id.agent_menu, key.drawable)
        key.setImageDrawable(ContextCompat.getDrawable(key.context, R.drawable.ic_close))
        key.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Settings.getValues()?.mColors?.get(ColorType.FUNCTIONAL_KEY_BACKGROUND) ?: FlatStyle.FUNCTIONAL_KEY)
        }
        key.contentDescription = key.context.getString(R.string.agent_menu_close)
    }

    private fun showGridIcon(key: ImageButton) {
        (key.getTag(R.id.agent_menu) as? Drawable)?.let(key::setImageDrawable)
        key.setTag(R.id.agent_menu, null)
        key.background = null
        key.contentDescription = key.context.getString(R.string.agent_menu_open)
    }

    private fun openSettings(context: Context, destination: String) {
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                // clear an existing settings task: it would only come to the front and ignore the destination
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION, destination)
        )
    }
}

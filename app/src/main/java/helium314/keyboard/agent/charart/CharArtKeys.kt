// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.charart

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.latin.R

/**
 * The small keyboard inside the character art window (stage 53). While the window covers the
 * keys, the user types its text, a snippet name or own art characters here. The keys only change
 * the window's own text: nothing goes to the app's field, the keyboard engine never sees them, so
 * nothing is corrected or learned and no input connection is involved.
 */
class CharArtKeys(context: Context) : LinearLayout(context) {
    var onText: ((String) -> Unit)? = null
    var onDelete: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null
    /** Only letters (a snippet name): digits, signs, space and new line are off. */
    var lettersOnly = false
        set(value) { field = value; symbols = false; build() }
    /** Without a new line key (own characters). */
    var singleLine = false
        set(value) { field = value; build() }
    var german = true
        set(value) { field = value; build() }
    var keyColor = 0xFFE0E0E0.toInt()
    var functionalColor = 0xFFC8C8C8.toInt()
    var accentColor = 0xFF1A73E8.toInt()
    var textColor = 0xFF000000.toInt()
    var accentTextColor = 0xFFFFFFFF.toInt()

    private var shift = false
    private var symbols = false
    private val dp = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        id = R.id.agent_char_art_keys
        build()
    }

    fun build() {
        removeAllViews()
        val rows = when {
            symbols -> SYMBOLS
            german -> GERMAN
            else -> ENGLISH
        }
        rows.forEachIndexed { index, letters ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
            if (index == rows.lastIndex && !symbols) row.addView(key("⇧", functional = true, description = R.string.agent_char_art_keys_shift) {
                shift = !shift; build()
            }, weight(1.3f))
            for (char in letters) {
                val label = if (shift && !symbols) capital(char) else char.toString()
                row.addView(key(label) { onText?.invoke(label); if (shift) { shift = false; build() } }, weight(1f))
            }
            if (index == rows.lastIndex) row.addView(key("⌫", functional = true, description = R.string.agent_char_art_keys_delete) { onDelete?.invoke() }, weight(1.3f))
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
        bottom.addView(key(if (symbols) "abc" else "123", functional = true, description = R.string.agent_char_art_keys_symbols,
            enabled = !lettersOnly) { symbols = !symbols; build() }, weight(1.5f))
        bottom.addView(key(",", enabled = !lettersOnly) { onText?.invoke(",") }, weight(1f))
        bottom.addView(key(" ", description = R.string.agent_char_art_keys_space, enabled = !lettersOnly) { onText?.invoke(" ") }, weight(4f))
        bottom.addView(key(".", enabled = !lettersOnly) { onText?.invoke(".") }, weight(1f))
        if (!singleLine) bottom.addView(key("↵", functional = true, description = R.string.agent_char_art_keys_enter, enabled = !lettersOnly) {
            onEnter?.invoke()
        }, weight(1.3f))
        bottom.addView(key(context.getString(R.string.agent_char_art_keys_done), accent = true) { onDone?.invoke() }, weight(2f))
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** One key's share of its row; no margins, so the gap between keys still belongs to a key's touch area. */
    private fun weight(weight: Float) = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)

    private fun key(label: String, functional: Boolean = false, accent: Boolean = false, description: Int = 0,
                    enabled: Boolean = true, action: () -> Unit) = TextView(context).apply {
        text = label
        if (description != 0) contentDescription = context.getString(description)
        gravity = android.view.Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (label.length > 2) 13f else 17f)
        setTextColor(if (accent) accentTextColor else textColor)
        // the visible key is inset by 2 dp, the touch area is the whole cell (review of stage 53, M2)
        background = InsetDrawable(GradientDrawable().apply {
            cornerRadius = 6 * dp
            setColor(if (accent) accentColor else if (functional) functionalColor else keyColor)
        }, (2 * dp).toInt())
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.35f
        isClickable = true
        isFocusable = true
        setOnClickListener { if (isEnabled) action() }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    companion object {
        /** The capital of [char]; ß becomes the capital sharp s ẞ, not "SS" (review of stage 53, N13). */
        internal fun capital(char: Char): String = if (char == 'ß') "ẞ" else char.uppercase()

        private val GERMAN = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnmß")
        private val ENGLISH = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        /** Signs, and the shades for own art characters. */
        private val SYMBOLS = listOf("1234567890", "-/:;()&@\"'", "?!*+=#♥░▒▓█")
    }
}

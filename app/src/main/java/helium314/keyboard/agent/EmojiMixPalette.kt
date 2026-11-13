// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import dev.agentkeyboard.core.EmojiMix
import dev.agentkeyboard.core.EmojiMixTable
import helium314.keyboard.agent.commands.EmojiMixer
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import java.io.File
import java.lang.ref.WeakReference

/**
 * The mixer mode of the emoji palette (stage 25a), switched by the "Emoji-Mixer" key of its bottom
 * row. While it is on, a bar above the emoji pages shows the chosen emojis (at most two: Emoji
 * Kitchen only mixes pairs) and a tap on an emoji chooses it instead of typing it. Choosing the
 * second emoji is the explicit request: the mix is loaded once ([EmojiMixer.request]; a cached mix
 * comes from the device) and its sticker slides in; a tap on it inserts it (or shares it). A pair
 * without mix shows "Keine Mischung" and partners of the first emoji that have one (from the table,
 * offline). Leaving the mode or the palette clears the choice. Off in password fields, incognito
 * and on a locked device. Nothing is logged.
 *
 * [palette] is the vertical palette view; the bar goes on top and [pager] gives up as much height
 * as the bar takes ([beforeMeasure]), so the palette keeps its size.
 */
class EmojiMixPalette(private val palette: LinearLayout, private val pager: View) {
    private val context: Context = palette.context
    private val selection = mutableListOf<String>()
    private var editorInfo: EditorInfo? = null
    private var baseHeight = -1
    private var shownFile: File? = null

    var isActive = false
        private set
    val selected: List<String> get() = selection.toList()

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    private val colors = Settings.getValues().mColors
    private val flat = FlatStyle.isActive(colors)
    private val textColor = if (flat) FlatStyle.LABEL else colors.get(ColorType.KEY_TEXT)
    private val chipColor = if (flat) 0xFF191919.toInt() else colors.get(ColorType.KEY_BACKGROUND)
    private val accent = if (flat) FlatStyle.ACCENT else colors.get(ColorType.ACTION_KEY_BACKGROUND)

    private fun chipBackground() = GradientDrawable().apply { cornerRadius = dp(17f).toFloat(); setColor(chipColor) }

    private fun text(size: Float = 15f) = TextView(context).apply {
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        if (flat) typeface = FlatStyle.labelTypeface
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setSingleLine()
    }

    private val slots = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val dots = LoadingDotsView(context, accent).apply { visibility = View.GONE }
    val status: TextView = text(14f).apply { setTextColor(ColorUtils.setAlphaComponent(textColor, 0xCC)) }
    private val partners = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val partnersScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(partners)
        visibility = View.GONE
    }
    val preview: ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        isClickable = true
        isFocusable = true
        visibility = View.GONE
        setOnClickListener { insert() }
    }
    private val previewCaption = text(13f).apply { visibility = View.GONE }

    /** The bar; GONE while the mode is off. */
    val bar: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            addView(slots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36f)))
            addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8f); marginEnd = dp(6f)
            })
            addView(status, LinearLayout.LayoutParams(0, dp(36f), 1f).apply { marginStart = dp(6f) })
        }
        addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(partnersScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40f)))
        val previewRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(preview, LinearLayout.LayoutParams(dp(96f), dp(96f)))
            addView(previewCaption, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f) })
        }
        addView(previewRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    init {
        palette.addView(bar, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        current = WeakReference(this)
    }

    /** The palette opens for [editorInfo]. */
    fun start(editorInfo: EditorInfo?) {
        this.editorInfo = editorInfo
        current = WeakReference(this)
    }

    /** The palette closes: mode off, choice gone. */
    fun stop() = setActive(false)

    private fun field(): EditorInfo? = EmojiMixer.latinIme(context)?.currentInputEditorInfo ?: editorInfo

    fun toggle() {
        if (!isActive && EmojiMixer.blocked(field())) {
            Toast.makeText(context, R.string.agent_emoji_mixer_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        setActive(!isActive)
    }

    private fun setActive(on: Boolean) {
        if (on == isActive) return
        isActive = on
        selection.clear()
        shownFile = null
        if (on) {
            baseHeight = pager.layoutParams?.height ?: -1
            EmojiMixer.listen(LISTENER) { update() }
            EmojiMixer.table(context)
        } else {
            EmojiMixer.listen(LISTENER, null)
            if (baseHeight >= 0) pager.layoutParams = pager.layoutParams.apply { height = baseHeight }
            baseHeight = -1
        }
        bar.visibility = if (on) View.VISIBLE else View.GONE
        update()
        palette.requestLayout()
    }

    /** A tap on an emoji of the palette; true if the mixer took it (then it is not typed). */
    fun onEmoji(emoji: String): Boolean {
        if (!isActive) return false
        if (EmojiMixer.blocked(field())) {
            setActive(false)
            return false
        }
        if (selection.size < 2) selection += emoji else selection[1] = emoji
        update()
        return true
    }

    fun remove(index: Int) {
        if (index !in selection.indices) return
        selection.removeAt(index)
        update()
    }

    /** Before the palette measures itself: the pages give up the bar's height. */
    fun beforeMeasure(widthMeasureSpec: Int) {
        if (!isActive || baseHeight < 0) return
        bar.measure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        pager.layoutParams.height = (baseHeight - bar.measuredHeight).coerceAtLeast(0)
    }

    /** The mix of the two chosen emojis, or null. */
    fun currentMix(): EmojiMix? {
        if (selection.size != 2) return null
        return EmojiMixer.table(context)?.find(selection[0], selection[1])
    }

    private fun update() {
        if (!isActive) return
        slots.removeAllViews()
        selection.forEachIndexed { index, emoji ->
            if (index > 0) slots.addView(text(16f).apply { this.text = "+"; setPadding(dp(6f), 0, dp(6f), 0) })
            slots.addView(text(22f).apply {
                this.text = emoji
                contentDescription = context.getString(R.string.agent_emoji_mixer_remove, emoji)
                background = chipBackground()
                setPadding(dp(10f), 0, dp(10f), 0)
                isClickable = true
                setOnClickListener { remove(index) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36f)))
        }
        dots.visibility = View.GONE
        partnersScroll.visibility = View.GONE
        status.setOnClickListener(null)
        status.isClickable = false
        var ready: EmojiMixer.State.Ready? = null
        when (selection.size) {
            0 -> status.setText(R.string.agent_emoji_mixer_hint)
            1 -> status.setText(R.string.agent_emoji_mixer_hint_second)
            else -> {
                val table = EmojiMixer.table(context)
                val mix = table?.find(selection[0], selection[1])
                when {
                    table == null -> { dots.visibility = View.VISIBLE; status.text = "" }
                    mix == null -> showPartners(table)
                    else -> {
                        // choosing the second emoji is the request: once per pair, a cached mix needs no network
                        var state = EmojiMixer.state(context, mix)
                        if (state == null && EmojiMixer.request(context, mix)) state = EmojiMixer.state(context, mix)
                        when (state) {
                            null, EmojiMixer.State.Loading -> {
                                dots.visibility = View.VISIBLE
                                status.setText(R.string.agent_emoji_mixer_loading)
                            }
                            EmojiMixer.State.Missing -> showPartners(table)
                            is EmojiMixer.State.Failed -> {
                                status.setText(R.string.agent_emoji_mixer_failed)
                                status.isClickable = true
                                status.setOnClickListener { EmojiMixer.request(context, mix) }
                            }
                            is EmojiMixer.State.Ready -> {
                                ready = state
                                status.text = ""
                            }
                        }
                    }
                }
            }
        }
        showPreview(ready)
        palette.requestLayout()
    }

    private fun showPartners(table: EmojiMixTable) {
        status.setText(R.string.agent_emoji_mixer_none)
        partners.removeAllViews()
        val first = selection[0]
        val own = table.codepointsOf(first)
        for (mix in table.partners(first, EmojiMixTable.POPULAR, PARTNERS)) {
            val partner = if (mix.left == own) mix.rightEmoji else mix.leftEmoji
            partners.addView(text(22f).apply {
                this.text = partner
                contentDescription = context.getString(R.string.agent_emoji_mixer_partner, partner)
                setPadding(dp(8f), 0, dp(8f), 0)
                isClickable = true
                setOnClickListener {
                    if (selection.size == 2) selection[1] = partner else selection += partner
                    update()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        partnersScroll.visibility = if (partners.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun showPreview(ready: EmojiMixer.State.Ready?) {
        if (ready == null || ready.thumbnail == null) {
            preview.visibility = View.GONE
            previewCaption.visibility = View.GONE
            preview.setImageDrawable(null)
            shownFile = null
            return
        }
        val inserts = ScreenshotChip.editorAccepts(field(), EmojiMixer.MIME_TYPE)
        preview.setImageBitmap(ready.thumbnail)
        preview.contentDescription = context.getString(if (inserts) R.string.agent_emoji_mixer_insert else R.string.agent_emoji_mixer_share)
        previewCaption.setText(if (inserts) R.string.agent_emoji_mixer_insert else R.string.agent_emoji_mixer_share)
        previewCaption.visibility = View.VISIBLE
        val fresh = shownFile != ready.file
        shownFile = ready.file
        preview.visibility = View.VISIBLE
        if (!fresh) return
        preview.animate().cancel()
        if (FlatStyle.animationsEnabled(context)) {
            // slides up out of the bar and grows to its size
            preview.scaleX = 0.6f; preview.scaleY = 0.6f; preview.alpha = 0f; preview.translationY = dp(24f).toFloat()
            preview.animate().scaleX(1f).scaleY(1f).alpha(1f).translationY(0f).setDuration(ANIMATION_MS).start()
        } else {
            preview.scaleX = 1f; preview.scaleY = 1f; preview.alpha = 1f; preview.translationY = 0f
        }
    }

    /** The tap on the sticker: into the field (or the share sheet); the choice starts again. */
    fun insert(): Boolean {
        val mix = currentMix() ?: return false
        val state = EmojiMixer.state(context, mix) as? EmojiMixer.State.Ready ?: return false
        val target = EmojiMixer.latinIme(context) ?: context
        val done = { selection.clear(); update() }
        return EmojiMixer.deliver(target, field(), state.file, afterInsert = done, afterShare = done)
    }

    companion object {
        const val LISTENER = "palette"
        const val PARTNERS = 8
        const val ANIMATION_MS = 220L

        private var current: WeakReference<EmojiMixPalette>? = null

        /** The "Emoji-Mixer" key of the palette's bottom row (KeyCode.AGENT_EMOJI_MIXER). */
        @JvmStatic fun toggleCurrent() { current?.get()?.toggle() }

        /** The mixer of the palette shown last, for tests. */
        @JvmStatic fun current(): EmojiMixPalette? = current?.get()
    }
}

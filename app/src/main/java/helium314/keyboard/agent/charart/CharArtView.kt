// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.charart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.edit
import dev.agentkeyboard.core.charart.ArtResult
import dev.agentkeyboard.core.charart.ArtStyle
import dev.agentkeyboard.core.charart.ArtWarning
import dev.agentkeyboard.core.charart.Arrangement
import dev.agentkeyboard.core.charart.BigFont
import dev.agentkeyboard.core.charart.CharArt
import dev.agentkeyboard.core.charart.Dither
import dev.agentkeyboard.core.charart.EmojiPixels
import dev.agentkeyboard.core.charart.FrameOptions
import dev.agentkeyboard.core.charart.FrameStyle
import dev.agentkeyboard.core.charart.LayoutOptions
import dev.agentkeyboard.core.charart.Look
import dev.agentkeyboard.core.charart.OutputOptions
import dev.agentkeyboard.core.charart.Photo
import dev.agentkeyboard.core.charart.PhotoOptions
import dev.agentkeyboard.core.charart.PictureElement
import dev.agentkeyboard.core.charart.Sketch
import dev.agentkeyboard.core.charart.SketchRaster
import dev.agentkeyboard.core.charart.TargetApp
import dev.agentkeyboard.core.charart.TextColumns
import dev.agentkeyboard.core.charart.TextElement
import dev.agentkeyboard.core.charart.TextMode
import dev.agentkeyboard.core.charart.UnicodeStyle
import helium314.keyboard.agent.AgentKeyboardFrame
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlin.math.min

/**
 * The character art window of stage 53, shown in place of the keys. On top the text (taken from
 * the field, changed with the window's own keys), in the middle the drawing area (or the keys, the
 * pictures, the width), next to or below it the live preview made of characters; every choice
 * renders the whole art again through [CharArt]. The art goes into the field only through the
 * controller after a tap on "Insert" or "Replace" (each tap once, then "Undo" for a few seconds),
 * or to the clipboard or a snippet. Everything stays on the device; drawing, picture and text are
 * held only while the window is open.
 */
class CharArtView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    /** The art for the field at the cursor. */
    var onInsert: ((String) -> Unit)? = null
    /** The art in place of the field text read when the window opened. */
    var onReplace: ((String) -> Unit)? = null
    var onUndo: (() -> Unit)? = null
    var onCopy: ((String) -> Unit)? = null
    /** Saves [text] as a snippet called [name]. */
    var onSaveSnippet: ((name: String, text: String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    /** Opens what lets the user allow pictures. */
    var onAllowPictures: (() -> Unit)? = null
    /** Replaceable in tests. */
    var images: CharArtImages = CharArtImages.Device(context)
    /** Runs picture reads; replaceable in tests. */
    var background: (Runnable) -> Unit = { Thread(it, "char-art-pictures").start() }

    enum class Tab(val title: Int) {
        DRAW(R.string.agent_char_art_tab_draw), PHOTO(R.string.agent_char_art_tab_photo), STYLE(R.string.agent_char_art_tab_style),
        TEXT(R.string.agent_char_art_tab_text), FRAME(R.string.agent_char_art_tab_frame), LAYOUT(R.string.agent_char_art_tab_layout),
        APP(R.string.agent_char_art_tab_app),
    }
    private enum class Editing { TEXT, CUSTOM, NAME }

    val sketch = Sketch()
    internal var tab = Tab.DRAW
        private set
    private var raster: SketchRaster = sketch.rasterize()
    private var photo: Photo? = null
    private var photoBitmap: Bitmap? = null
    private var photoOptions = PhotoOptions()
    private var look = Look()
    private var pictureSize = 1f
    private var crop = true
    /** The text of the text element: from the field, changed in the window. */
    internal var text = ""
        private set
    private var textMode = TextMode.BIG
    /** False: the text stays in the window but is not part of the art ("No text"). */
    private var textShown = true
    private var unicode = UnicodeStyle(bold = true)
    private var font = BigFont.STANDARD
    private var letterSize = 0
    private var textLook = Look()
    private var frame = FrameOptions()
    private var layoutOptions = LayoutOptions()
    private var output = OutputOptions()
    private var editing: Editing? = null
    private var snippetName = ""
    private var canReplace = false
    private var german = true
    /** What the preview shows and the buttons would insert. */
    var result: ArtResult = CharArt.render(null, null)
        private set
    /** While true, the insert button takes back the last insertion. */
    var offersUndo = false
        private set
    private var pictureGeneration = 0
    private var renderPosted = false

    private val dp = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private var backgroundColor = 0
    private var keyColor = 0
    private var functionalColor = 0
    private var accentColor = 0
    private var textColor = 0
    private var accentTextColor = 0

    private val tabRow = LinearLayout(context)
    private val textRow = TextView(context)
    private val optionsRow = LinearLayout(context)
    private val middle = LinearLayout(context)
    private val canvas = CharArtCanvas(context, sketch)
    private val keys = CharArtKeys(context)
    private val photoStrip = LinearLayout(context)
    private val photoList = LinearLayout(context)
    private val appPanel = LinearLayout(context)
    private val widthLabel = TextView(context)
    private val widthBar = SeekBar(context)
    private val bubble = TextView(context)
    private val previewBox = ScrollView(context)
    private val preview = TextView(context)
    private val status = TextView(context)
    private val copyButton: TextView
    private val snippetButton: TextView
    private val replaceButton: TextView
    private val insertButton: TextView
    private val endUndo = Runnable { offersUndo = false; updateButtons() }

    init {
        orientation = VERTICAL
        isClickable = true
        visibility = View.GONE
        readColors()
        val padding = (4 * dp).toInt()
        setPadding(padding, padding, padding, padding)

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tabRow.orientation = HORIZONTAL
        tabRow.id = R.id.agent_char_art_tabs
        header.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(tabRow) },
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        header.addView(button(context.getString(R.string.agent_panel_close), R.id.agent_char_art_close, functional = true) { onClose?.invoke() },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, (34 * dp).toInt()))

        textRow.apply {
            id = R.id.agent_char_art_text
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            contentDescription = context.getString(R.string.agent_char_art_text_description)
            isClickable = true
            setOnClickListener { startEditing(if (editing == Editing.NAME) Editing.NAME else Editing.TEXT) }
        }
        addView(textRow, LayoutParams(LayoutParams.MATCH_PARENT, (28 * dp).toInt()))

        optionsRow.orientation = HORIZONTAL
        optionsRow.id = R.id.agent_char_art_options
        optionsRow.gravity = Gravity.CENTER_VERTICAL
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(optionsRow) },
            LayoutParams(LayoutParams.MATCH_PARENT, (34 * dp).toInt()))

        // the middle: work area (drawing, keys, pictures, width) and the preview
        val work = LinearLayout(context).apply { orientation = VERTICAL }
        photoStrip.apply {
            id = R.id.agent_char_art_photos
            orientation = VERTICAL
            visibility = View.GONE
        }
        photoList.orientation = HORIZONTAL
        photoStrip.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(photoList) },
            LayoutParams(LayoutParams.MATCH_PARENT, (52 * dp).toInt()))
        work.addView(photoStrip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val stage = FrameLayout(context)
        stage.addView(canvas, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        keys.visibility = View.GONE
        stage.addView(keys, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        buildAppPanel()
        stage.addView(appPanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        work.addView(stage, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        middle.addView(work, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        preview.apply {
            id = R.id.agent_char_art_preview
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9f)
            setHorizontallyScrolling(true)
            includeFontPadding = false
            setLineSpacing(0f, 1f)
            setPadding((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            gravity = Gravity.START or Gravity.TOP // centred lines do not draw in render tests (P40)
        }
        previewBox.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(preview) })
        previewBox.background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(keyColor) }
        middle.addView(previewBox, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = (4 * dp).toInt() })
        addView(middle, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        status.apply {
            id = R.id.agent_char_art_status
            setTextColor(textColor)
            alpha = 0.85f
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        bottom.addView(status, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        copyButton = button(context.getString(R.string.agent_char_art_copy), R.id.agent_char_art_copy, functional = true) {
            if (!result.isEmpty) onCopy?.invoke(result.text)
        }
        snippetButton = button(context.getString(R.string.agent_char_art_snippet), R.id.agent_char_art_snippet, functional = true) {
            if (editing == Editing.NAME) saveSnippet() else if (!result.isEmpty) startEditing(Editing.NAME)
        }
        replaceButton = button(context.getString(R.string.agent_char_art_replace), R.id.agent_char_art_replace, functional = true) {
            if (offersUndo) return@button
            if (!result.isEmpty) onReplace?.invoke(result.text)
        }
        insertButton = button(context.getString(R.string.agent_char_art_insert), R.id.agent_char_art_insert, accent = true) {
            if (offersUndo) { endUndoOffer(); onUndo?.invoke(); return@button }
            if (!result.isEmpty) onInsert?.invoke(result.text)
        }
        for (view in listOf(copyButton, snippetButton, replaceButton, insertButton))
            bottom.addView(view, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { marginStart = (4 * dp).toInt() })
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, (38 * dp).toInt()))

        canvas.paperColor = 0xFFFFFFFF.toInt()
        canvas.inkColor = 0xFF000000.toInt()
        canvas.frameColor = textColor and 0x55FFFFFF
        // sizes are known only after layout: fit the preview and the bubble then
        previewBox.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitPreview() }
        appPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitBubble() }
        canvas.onChanged = { changed, done ->
            raster = changed
            if (done) render() else postRender()
        }
        keys.keyColor = keyColor; keys.functionalColor = functionalColor; keys.accentColor = accentColor
        keys.textColor = textColor; keys.accentTextColor = accentTextColor
        keys.onText = ::typed
        keys.onDelete = ::deleteTyped
        keys.onEnter = { if (editing == Editing.TEXT && text.count { it == '\n' } < CharArt.MAX_TEXT_LINES - 1) typed("\n") }
        keys.onDone = { if (editing == Editing.NAME) saveSnippet() else stopEditing() }
    }

    private fun readColors() {
        val colors = runCatching { Settings.getValues()?.mColors }.getOrNull()
        if (colors == null) {
            val surface = context.obtainStyledAttributes(R.styleable.AgentSurface)
            backgroundColor = surface.getColor(R.styleable.AgentSurface_android_colorBackground, 0xFFFFFFFF.toInt())
            textColor = surface.getColor(R.styleable.AgentSurface_android_textColorPrimary, 0xFF000000.toInt())
            surface.recycle()
            keyColor = blend(backgroundColor, textColor, 0.08f)
            functionalColor = blend(backgroundColor, textColor, 0.16f)
            accentColor = textColor
            accentTextColor = backgroundColor
        } else {
            backgroundColor = colors.get(ColorType.MAIN_BACKGROUND) or 0xFF000000.toInt()
            keyColor = colors.get(ColorType.KEY_BACKGROUND)
            functionalColor = colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)
            accentColor = colors.get(ColorType.ACTION_KEY_BACKGROUND)
            textColor = colors.get(ColorType.KEY_TEXT)
            accentTextColor = colors.get(ColorType.ACTION_KEY_ICON)
        }
        setBackgroundColor(backgroundColor)
    }

    private fun blend(from: Int, to: Int, share: Float): Int {
        fun channel(shift: Int) = ((from shr shift and 0xFF) * (1 - share) + (to shr shift and 0xFF) * share).toInt() and 0xFF
        return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun button(label: String, viewId: Int = View.NO_ID, functional: Boolean = false, accent: Boolean = false,
                       action: () -> Unit) = TextView(context).apply {
        if (viewId != View.NO_ID) id = viewId
        text = label
        gravity = Gravity.START or Gravity.CENTER_VERTICAL // wraps its text; centred single lines do not draw in render tests (P40)
        setSingleLine()
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        setTextColor(if (accent) accentTextColor else textColor)
        setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
        background = GradientDrawable().apply {
            cornerRadius = 8 * dp
            setColor(if (accent) accentColor else if (functional) functionalColor else keyColor)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { if (isEnabled) action() }
    }

    /** An option: highlighted while [selected]. */
    private fun chip(label: String, selected: Boolean, action: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setSingleLine()
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        setTextColor(if (selected) accentTextColor else textColor)
        setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
        background = GradientDrawable().apply { cornerRadius = 15 * dp; setColor(if (selected) accentColor else keyColor) }
        isSelected = selected
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun addChip(row: LinearLayout, label: String, selected: Boolean = false, action: () -> Unit) {
        row.addView(chip(label, selected, action), LayoutParams(LayoutParams.WRAP_CONTENT, (30 * dp).toInt()).apply {
            marginEnd = (4 * dp).toInt()
        })
    }

    private fun buildAppPanel() {
        appPanel.apply {
            id = R.id.agent_char_art_app_panel
            orientation = VERTICAL
            visibility = View.GONE
            setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
        }
        widthLabel.apply {
            id = R.id.agent_char_art_width_label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
        }
        appPanel.addView(widthLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        widthBar.apply {
            id = R.id.agent_char_art_width
            max = CharArt.MAX_WIDTH - CharArt.MIN_WIDTH
            contentDescription = context.getString(R.string.agent_char_art_width_description)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) setWidth(progress + CharArt.MIN_WIDTH)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }
        appPanel.addView(widthBar, LayoutParams(LayoutParams.MATCH_PARENT, (32 * dp).toInt()))
        // a chat bubble as wide as on a phone: lines too long for it wrap here as they would there
        bubble.apply {
            id = R.id.agent_char_art_bubble
            setTextColor(textColor)
            includeFontPadding = false
            setPadding((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 10 * dp; setColor(keyColor) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        appPanel.addView(bubble, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    // ---- open and close ----

    /**
     * Opens the window with [fieldText] as the text. [replaceable]: the whole field before the cursor
     * was read and nothing follows it, so "Replace" can put the art in its place. [app] is the
     * profile of the field's app, if known; otherwise the last chosen one.
     */
    fun show(fieldText: String, replaceable: Boolean, app: TargetApp?, language: String) {
        resetContent()
        german = language == "de"
        keys.german = german
        text = fieldText.take(MAX_TEXT)
        canReplace = replaceable && text.isNotBlank()
        textMode = if (text.length <= 12 && !text.contains('\n')) TextMode.BIG else TextMode.UNICODE
        val prefs = context.prefs()
        val stored = runCatching { ArtStyle.valueOf(prefs.getString(AgentPreferences.CHAR_ART_STYLE, null) ?: "") }.getOrNull()
        look = Look(style = stored ?: ArtStyle.BRAILLE)
        textLook = Look(style = stored ?: ArtStyle.BRAILLE)
        val chosen = app ?: runCatching { TargetApp.valueOf(prefs.getString(AgentPreferences.CHAR_ART_APP, null) ?: "") }.getOrNull()
            ?: TargetApp.WHATSAPP
        useApp(chosen, remember = false)
        tab = Tab.DRAW
        visibility = View.VISIBLE
        update()
        announceForAccessibility(context.getString(R.string.agent_char_art_title))
    }

    /** Closes the window and forgets drawing, picture and text. */
    fun hide() {
        visibility = View.GONE
        resetContent()
    }

    private fun resetContent() {
        handler.removeCallbacks(endUndo)
        handler.removeCallbacksAndMessages(RENDER_TOKEN)
        renderPosted = false
        offersUndo = false
        pictureGeneration++
        sketch.clear()
        canvas.reset()
        raster = canvas.raster
        photo = null
        photoBitmap = null
        canvas.setPhoto(null, null)
        photoList.removeAllViews()
        photoOptions = PhotoOptions()
        text = ""
        textShown = true
        editing = null
        snippetName = ""
        canReplace = false
        pictureSize = 1f
        crop = true
        unicode = UnicodeStyle(bold = true)
        font = BigFont.STANDARD
        letterSize = 0
        frame = FrameOptions()
        layoutOptions = LayoutOptions()
        result = CharArt.render(null, null)
        preview.text = ""
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        (parent as? AgentKeyboardFrame)?.updateOverlayAccessibility()
    }

    // ---- after the controller inserted ----

    /** Whether "Replace" is offered: the field holds the text read at opening, unchanged. */
    fun setReplaceable(value: Boolean) {
        canReplace = value && text.isNotBlank()
        updateButtons()
    }

    /** The art went into the field: the insert button takes it back for a few seconds. */
    fun offerUndo() {
        offersUndo = true
        handler.removeCallbacks(endUndo)
        handler.postDelayed(endUndo, UNDO_MILLIS)
        updateButtons()
    }

    fun endUndoOffer() {
        handler.removeCallbacks(endUndo)
        offersUndo = false
        updateButtons()
    }

    // ---- choices ----

    fun selectTab(choice: Tab) {
        if (editing != null && editing != Editing.TEXT) editing = null
        if (editing == Editing.TEXT && choice != Tab.TEXT) editing = null
        tab = choice
        if (choice == Tab.PHOTO) loadPictures()
        update()
    }

    private fun useApp(app: TargetApp, remember: Boolean) {
        val width = context.prefs().getInt(widthKey(app), app.defaultWidth).coerceIn(CharArt.MIN_WIDTH, CharArt.MAX_WIDTH)
        output = OutputOptions(app, width, app.codeBlockDefault, output.brailleBlanks)
        if (remember) context.prefs().edit { putString(AgentPreferences.CHAR_ART_APP, app.name) }
    }

    private fun widthKey(app: TargetApp) = AgentPreferences.CHAR_ART_WIDTH_PREFIX + app.name.lowercase()

    internal fun setWidth(width: Int) {
        val bounded = width.coerceIn(CharArt.MIN_WIDTH, CharArt.MAX_WIDTH)
        if (bounded == output.width) return
        output = output.copy(width = bounded)
        context.prefs().edit { putInt(widthKey(output.app), bounded) }
        render()
    }

    private fun setStyle(style: ArtStyle) {
        if (look.style == style) {
            // a second tap on emoji or own characters changes them
            if (style == ArtStyle.EMOJI) look = look.copy(emoji = EmojiPixels.entries[(look.emoji.ordinal + 1) % EmojiPixels.entries.size])
            if (style == ArtStyle.CUSTOM) { startEditing(Editing.CUSTOM); return }
        } else look = look.copy(style = style)
        context.prefs().edit { putString(AgentPreferences.CHAR_ART_STYLE, style.name) }
        update()
    }

    private fun startEditing(what: Editing) {
        editing = what
        if (what == Editing.NAME && snippetName.isEmpty()) snippetName = context.getString(R.string.agent_char_art_title)
            .filter { it.isLetter() }.lowercase().take(12).ifEmpty { "kunst" }
        if (what == Editing.TEXT) tab = Tab.TEXT
        update()
    }

    private fun stopEditing() {
        editing = null
        update()
    }

    private fun typed(value: String) {
        when (editing) {
            Editing.TEXT -> if (text.length + value.length <= MAX_TEXT) text += value
            Editing.CUSTOM -> {
                val current = TextColumns.graphemes(look.customChars)
                if (current.size < MAX_CUSTOM) look = look.copy(customChars = look.customChars + value)
            }
            Editing.NAME -> if (value.all { it.isLetter() } && snippetName.length + value.length <= helium314.keyboard.agent.commands.SnippetStore.MAX_NAME)
                snippetName += value
            null -> return
        }
        update()
    }

    private fun deleteTyped() {
        fun dropLast(value: String) = TextColumns.graphemes(value).dropLast(1).joinToString("")
        when (editing) {
            Editing.TEXT -> text = dropLast(text)
            Editing.CUSTOM -> look = look.copy(customChars = dropLast(look.customChars))
            Editing.NAME -> snippetName = dropLast(snippetName)
            null -> return
        }
        update()
    }

    private fun saveSnippet() {
        val name = snippetName
        if (name.isEmpty() || result.isEmpty) return
        editing = null
        update()
        onSaveSnippet?.invoke(name, result.text)
    }

    /** A picture as template (from the strip, or a test): drawn under the drawing and converted with its options. */
    internal fun usePhoto(bitmap: Bitmap) {
        val small = CharArtImages.scaledDown(bitmap, PHOTO_PX)
        photoBitmap = small
        photo = Photo.fitted(CharArtImages.darkness(small))
        photoOptions = photoOptions.copy(use = true)
        canvas.setPhoto(photo, small)
        canvas.photoShownFaint = false
        update()
    }

    private fun removePhoto() {
        photo = null
        photoBitmap = null
        canvas.setPhoto(null, null)
        update()
    }

    private fun loadPictures() {
        photoList.removeAllViews()
        if (!images.allowed()) {
            photoList.addView(TextView(context).apply {
                setText(R.string.agent_char_art_photo_none)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
                maxLines = 3
                maxWidth = (220 * dp).toInt()
                gravity = Gravity.CENTER_VERTICAL
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            photoList.addView(button(context.getString(R.string.agent_char_art_photo_allow), functional = true) { onAllowPictures?.invoke() },
                LayoutParams(LayoutParams.WRAP_CONTENT, (34 * dp).toInt()).apply { gravity = Gravity.CENTER_VERTICAL; marginStart = (6 * dp).toInt() })
            return
        }
        photoList.addView(TextView(context).apply {
            setText(R.string.agent_char_art_photo_loading)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            gravity = Gravity.CENTER_VERTICAL
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        val generation = ++pictureGeneration
        val source = images
        val thumbPx = (48 * dp).toInt()
        background(Runnable {
            val found = runCatching { source.recent(MAX_PICTURES).mapNotNull { uri -> source.load(uri, thumbPx)?.let { uri to it } } }
                .getOrDefault(emptyList())
            handler.post {
                if (generation != pictureGeneration || visibility != View.VISIBLE) return@post
                showPictures(found)
            }
        })
    }

    private fun showPictures(found: List<Pair<android.net.Uri, Bitmap>>) {
        photoList.removeAllViews()
        if (found.isEmpty()) {
            photoList.addView(TextView(context).apply {
                setText(R.string.agent_char_art_photo_none)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
                maxLines = 3
                gravity = Gravity.CENTER_VERTICAL
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            return
        }
        found.forEachIndexed { index, (uri, thumbnail) ->
            photoList.addView(ImageView(context).apply {
                setImageBitmap(thumbnail)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = context.getString(R.string.agent_char_art_photo_item, index + 1)
                isClickable = true
                setOnClickListener { pickPicture(uri) }
            }, LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply { marginEnd = (4 * dp).toInt(); gravity = Gravity.CENTER_VERTICAL })
        }
    }

    private fun pickPicture(uri: android.net.Uri) {
        val generation = ++pictureGeneration
        val source = images
        background(Runnable {
            val bitmap = runCatching { source.load(uri, PHOTO_PX) }.getOrNull()
            handler.post {
                if (generation != pictureGeneration || visibility != View.VISIBLE || bitmap == null) return@post
                usePhoto(bitmap)
            }
        })
    }

    // ---- what is shown ----

    private fun update() {
        buildTabs()
        buildOptions()
        textRow.text = when (editing) {
            Editing.NAME -> context.getString(R.string.agent_char_art_snippet_name, "$snippetName▏")
            Editing.CUSTOM -> context.getString(R.string.agent_char_art_custom_value, look.customChars.replace(' ', '·') + "▏")
            Editing.TEXT -> context.getString(R.string.agent_char_art_text_value, text.replace('\n', '↵') + "▏")
            null -> if (text.isEmpty()) context.getString(R.string.agent_char_art_text_empty)
                else context.getString(R.string.agent_char_art_text_value, text.replace('\n', '↵'))
        }
        val typing = editing != null
        keys.visibility = if (typing) View.VISIBLE else View.GONE
        // only on a change: setting them builds the keys again (back to letters)
        if (keys.lettersOnly != (editing == Editing.NAME)) keys.lettersOnly = editing == Editing.NAME
        if (keys.singleLine != (editing != Editing.TEXT)) keys.singleLine = editing != Editing.TEXT
        appPanel.visibility = if (!typing && tab == Tab.APP) View.VISIBLE else View.GONE
        canvas.visibility = if (!typing && tab != Tab.APP) View.VISIBLE else View.INVISIBLE
        photoStrip.visibility = if (!typing && tab == Tab.PHOTO) View.VISIBLE else View.GONE
        canvas.photoShownFaint = !photoOptions.use
        render()
    }

    private fun buildTabs() {
        tabRow.removeAllViews()
        tabRow.gravity = Gravity.CENTER_VERTICAL
        for (choice in Tab.entries) addChip(tabRow, context.getString(choice.title), choice == tab) { selectTab(choice) }
    }

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun styleName(style: ArtStyle, of: Look) = when (style) {
        ArtStyle.BRAILLE -> string(R.string.agent_char_art_style_braille)
        ArtStyle.BLOCKS -> string(R.string.agent_char_art_style_blocks)
        ArtStyle.HALF_BLOCKS -> string(R.string.agent_char_art_style_half)
        ArtStyle.ASCII -> string(R.string.agent_char_art_style_ascii)
        ArtStyle.EMOJI -> string(R.string.agent_char_art_style_emoji, of.emoji.ink + of.emoji.paper)
        ArtStyle.CUSTOM -> string(R.string.agent_char_art_style_custom, of.customChars.trim().ifEmpty { "·" })
    }

    private fun buildOptions() {
        val row = optionsRow
        row.removeAllViews()
        when (tab) {
            Tab.DRAW -> {
                for ((tool, title) in listOf(CharArtCanvas.Tool.PEN to R.string.agent_char_art_tool_pen,
                    CharArtCanvas.Tool.ERASER to R.string.agent_char_art_tool_eraser, CharArtCanvas.Tool.LINE to R.string.agent_char_art_tool_line,
                    CharArtCanvas.Tool.CIRCLE to R.string.agent_char_art_tool_circle, CharArtCanvas.Tool.FILL to R.string.agent_char_art_tool_fill))
                    addChip(row, string(title), canvas.tool == tool) { canvas.tool = tool; update() }
                val widths = listOf(R.string.agent_char_art_width_thin, R.string.agent_char_art_width_medium, R.string.agent_char_art_width_thick)
                addChip(row, string(widths[canvas.widthIndex])) { canvas.widthIndex = (canvas.widthIndex + 1) % widths.size; update() }
                addChip(row, string(R.string.agent_char_art_undo_step)) { if (sketch.undo()) canvas.refresh() }
                addChip(row, string(R.string.agent_char_art_clear)) { sketch.clear(); canvas.refresh() }
            }
            Tab.PHOTO -> if (photo != null) {
                addChip(row, string(if (photoOptions.use) R.string.agent_char_art_photo_use else R.string.agent_char_art_photo_template), photoOptions.use) {
                    photoOptions = photoOptions.copy(use = !photoOptions.use); update()
                }
                addChip(row, string(R.string.agent_char_art_contrast, CONTRASTS.formatted(photoOptions.contrast))) {
                    photoOptions = photoOptions.copy(contrast = CONTRASTS.next(photoOptions.contrast)); update()
                }
                addChip(row, string(R.string.agent_char_art_threshold, (photoOptions.threshold * 100).toInt())) {
                    photoOptions = photoOptions.copy(threshold = THRESHOLDS.next(photoOptions.threshold)); update()
                }
                val dither = when (photoOptions.dither) {
                    Dither.NONE -> R.string.agent_char_art_dither_none
                    Dither.ORDERED -> R.string.agent_char_art_dither_ordered
                    Dither.FLOYD_STEINBERG -> R.string.agent_char_art_dither_diffusion
                }
                addChip(row, string(dither)) {
                    photoOptions = photoOptions.copy(dither = Dither.entries[(photoOptions.dither.ordinal + 1) % Dither.entries.size]); update()
                }
                addChip(row, string(R.string.agent_char_art_invert), photoOptions.invert) { photoOptions = photoOptions.copy(invert = !photoOptions.invert); update() }
                addChip(row, string(R.string.agent_char_art_photo_remove)) { removePhoto() }
            }
            Tab.STYLE -> {
                for (style in ArtStyle.entries) addChip(row, styleName(style, look), look.style == style) { setStyle(style) }
                addChip(row, string(R.string.agent_char_art_outline), look.outline) { look = look.copy(outline = !look.outline); update() }
                addChip(row, string(R.string.agent_char_art_invert), look.invert) { look = look.copy(invert = !look.invert); update() }
                addChip(row, string(R.string.agent_char_art_crop), crop) { crop = !crop; update() }
                addChip(row, string(R.string.agent_char_art_size_percent, (pictureSize * 100).toInt())) {
                    pictureSize = SIZES.next(pictureSize); update()
                }
            }
            Tab.TEXT -> {
                for ((mode, title) in listOf(TextMode.BIG to R.string.agent_char_art_mode_big, TextMode.UNICODE to R.string.agent_char_art_mode_unicode,
                    TextMode.PLAIN to R.string.agent_char_art_mode_plain))
                    addChip(row, string(title), textShown && textMode == mode) { textMode = mode; textShown = true; update() }
                addChip(row, string(R.string.agent_char_art_text_off), !textShown) { textShown = !textShown; update() }
                when (textMode) {
                    TextMode.BIG -> {
                        for ((choice, title) in listOf(BigFont.MINI to R.string.agent_char_art_font_mini,
                            BigFont.STANDARD to R.string.agent_char_art_font_standard, BigFont.BOLD to R.string.agent_char_art_font_bold))
                            addChip(row, string(title), font == choice) { font = choice; update() }
                        addChip(row, if (letterSize == 0) string(R.string.agent_char_art_size_auto) else string(R.string.agent_char_art_size, letterSize)) {
                            letterSize = (letterSize + 1) % 4; update()
                        }
                        addChip(row, string(R.string.agent_char_art_text_style, styleName(textLook.style, textLook))) {
                            textLook = textLook.copy(style = ArtStyle.entries[(textLook.style.ordinal + 1) % ArtStyle.entries.size],
                                customChars = look.customChars, emoji = look.emoji)
                            update()
                        }
                        addChip(row, string(R.string.agent_char_art_outline), textLook.outline) { textLook = textLook.copy(outline = !textLook.outline); update() }
                        addChip(row, string(R.string.agent_char_art_invert), textLook.invert) { textLook = textLook.copy(invert = !textLook.invert); update() }
                    }
                    TextMode.UNICODE -> {
                        for ((family, title) in FAMILIES) addChip(row, string(title), unicode.family == family) { unicode = unicode.copy(family = family); update() }
                        addChip(row, string(R.string.agent_char_art_bold), unicode.bold) { unicode = unicode.copy(bold = !unicode.bold); update() }
                        addChip(row, string(R.string.agent_char_art_italic), unicode.italic) { unicode = unicode.copy(italic = !unicode.italic); update() }
                        addChip(row, string(R.string.agent_char_art_strike), unicode.strike) { unicode = unicode.copy(strike = !unicode.strike); update() }
                        addChip(row, string(R.string.agent_char_art_underline), unicode.underline) {
                            unicode = unicode.copy(underline = !unicode.underline); update()
                        }
                    }
                    TextMode.PLAIN -> {}
                }
            }
            Tab.FRAME -> {
                for ((style, title) in listOf(FrameStyle.NONE to R.string.agent_char_art_frame_none, FrameStyle.LINE to R.string.agent_char_art_frame_line,
                    FrameStyle.DOUBLE to R.string.agent_char_art_frame_double, FrameStyle.ROUND to R.string.agent_char_art_frame_round))
                    addChip(row, string(title), frame.style == style) { frame = frame.copy(style = style); update() }
                addChip(row, string(R.string.agent_char_art_frame_emoji, frame.emoji), frame.style == FrameStyle.EMOJI) {
                    frame = if (frame.style == FrameStyle.EMOJI) frame.copy(emoji = FRAME_EMOJIS[(FRAME_EMOJIS.indexOf(frame.emoji) + 1) % FRAME_EMOJIS.size])
                        else frame.copy(style = FrameStyle.EMOJI)
                    update()
                }
                addChip(row, string(R.string.agent_char_art_padding, frame.padding)) { frame = frame.copy(padding = (frame.padding + 1) % 3); update() }
            }
            Tab.LAYOUT -> {
                addChip(row, string(R.string.agent_char_art_stack), layoutOptions.arrangement == Arrangement.STACK) {
                    layoutOptions = layoutOptions.copy(arrangement = Arrangement.STACK); update()
                }
                addChip(row, string(R.string.agent_char_art_side), layoutOptions.arrangement == Arrangement.SIDE_BY_SIDE) {
                    layoutOptions = layoutOptions.copy(arrangement = Arrangement.SIDE_BY_SIDE); update()
                }
                addChip(row, string(R.string.agent_char_art_text_first), layoutOptions.textFirst) {
                    layoutOptions = layoutOptions.copy(textFirst = !layoutOptions.textFirst); update()
                }
                addChip(row, string(R.string.agent_char_art_center), layoutOptions.center) { layoutOptions = layoutOptions.copy(center = true); update() }
                addChip(row, string(R.string.agent_char_art_left), !layoutOptions.center) { layoutOptions = layoutOptions.copy(center = false); update() }
                addChip(row, string(R.string.agent_char_art_spacing, layoutOptions.spacing)) {
                    layoutOptions = layoutOptions.copy(spacing = (layoutOptions.spacing + 1) % 3); update()
                }
                addChip(row, string(R.string.agent_char_art_mirror), layoutOptions.mirror) { layoutOptions = layoutOptions.copy(mirror = !layoutOptions.mirror); update() }
            }
            Tab.APP -> {
                for ((app, title) in APPS) addChip(row, string(title), output.app == app) { useApp(app, remember = true); update() }
                if (output.app.codeBlock != null)
                    addChip(row, string(R.string.agent_char_art_code_block), output.codeBlock) { output = output.copy(codeBlock = !output.codeBlock); update() }
                addChip(row, string(R.string.agent_char_art_braille_blanks), output.brailleBlanks) {
                    output = output.copy(brailleBlanks = !output.brailleBlanks); update()
                }
            }
        }
    }

    private fun postRender() {
        if (renderPosted) return
        renderPosted = true
        handler.postAtTime({ renderPosted = false; render() }, RENDER_TOKEN, android.os.SystemClock.uptimeMillis() + LIVE_RENDER_MILLIS)
    }

    /** Renders the whole art again from every element and option. */
    internal fun render() {
        handler.removeCallbacksAndMessages(RENDER_TOKEN)
        renderPosted = false
        val hasDrawing = !sketch.isEmpty || raster.inkBounds() != null
        val picture = if (!hasDrawing && photo == null) null
            else PictureElement(raster, photo, photoOptions, look, pictureSize, crop)
        val textElement = if (text.isBlank() || !textShown) null else TextElement(text, textMode, unicode, font, letterSize, textLook)
        result = CharArt.render(picture, textElement, frame, layoutOptions, output)
        val code = output.codeBlock && output.app.codeBlock != null
        val body = result.lines.joinToString("\n")
        preview.typeface = if (code) Typeface.MONOSPACE else Typeface.DEFAULT
        preview.text = body
        fitPreview()
        preview.contentDescription = string(R.string.agent_char_art_preview_description, result.lines.size, string(R.string.agent_char_art_screen_reader))
        status.text = statusText()
        widthLabel.text = string(R.string.agent_char_art_width_value, output.width) + " · " +
            string(if (result.fitsPhone) R.string.agent_char_art_fits_phone else R.string.agent_char_art_too_wide_phone)
        widthBar.progress = output.width - CharArt.MIN_WIDTH
        bubble.typeface = preview.typeface
        bubble.text = body
        fitBubble()
        updateButtons()
    }

    private fun statusText(): String {
        if (result.isEmpty) return string(R.string.agent_char_art_empty)
        val appName = string(APPS.first { it.first == output.app }.second)
        val parts = result.warnings.map { warning ->
            when (warning) {
                ArtWarning.WIDER_THAN_PHONE -> string(R.string.agent_char_art_warn_wide)
                ArtWarning.TOO_LONG -> string(R.string.agent_char_art_warn_long, appName)
                ArtWarning.ALIGNMENT -> if (output.app.codeBlock != null) string(R.string.agent_char_art_warn_alignment_code)
                    else string(R.string.agent_char_art_warn_alignment, appName)
                ArtWarning.MIXED_WIDTHS -> string(R.string.agent_char_art_warn_mixed)
                ArtWarning.CODE_BLOCK_FONT -> string(R.string.agent_char_art_warn_code_font)
                ArtWarning.SPACES_COLLAPSE -> string(R.string.agent_char_art_warn_spaces)
                ArtWarning.STYLE_APPROXIMATED -> string(R.string.agent_char_art_warn_approximated)
                ArtWarning.EMOJI_LETTERS -> string(R.string.agent_char_art_warn_emoji_letters)
                ArtWarning.UNSUPPORTED_CHARACTERS -> string(R.string.agent_char_art_warn_unsupported)
            }
        }
        return if (parts.isEmpty()) string(R.string.agent_char_art_fits, result.columns) else "⚠ " + parts.joinToString(" · ")
    }

    private fun updateButtons() {
        val usable = !result.isEmpty
        insertButton.text = string(if (offersUndo) R.string.agent_char_art_undo else R.string.agent_char_art_insert)
        insertButton.isEnabled = usable || offersUndo
        insertButton.alpha = if (insertButton.isEnabled) 1f else 0.5f
        replaceButton.visibility = if (canReplace && !offersUndo) View.VISIBLE else View.GONE
        replaceButton.isEnabled = usable
        copyButton.isEnabled = usable
        snippetButton.isEnabled = usable
        snippetButton.text = string(if (editing == Editing.NAME) R.string.agent_char_art_snippet_save else R.string.agent_char_art_snippet)
        for (view in listOf(replaceButton, copyButton, snippetButton)) view.alpha = if (view.isEnabled) 1f else 0.5f
    }

    /** The whole art fits the preview: the font shrinks for wide or tall art (4–14 sp); below that it scrolls. */
    private fun fitPreview() {
        val available = previewBox.width - preview.paddingLeft - preview.paddingRight
        val availableHeight = previewBox.height - preview.paddingTop - preview.paddingBottom
        if (available <= 0 || availableHeight <= 0 || result.isEmpty) return
        val paint = Paint().apply { typeface = preview.typeface; textSize = 10 * resources.displayMetrics.scaledDensity }
        val widest = result.lines.maxOf { paint.measureText(it) }.coerceAtLeast(1f)
        val tallest = (paint.fontSpacing * result.lines.size).coerceAtLeast(1f)
        val size = min(10f * available / widest, 10f * availableHeight / tallest).coerceIn(4f, 14f)
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    }

    /** The bubble is as wide as a chat bubble holding the app's phone width of braille characters. */
    private fun fitBubble() {
        val width = appPanel.width - appPanel.paddingLeft - appPanel.paddingRight
        if (width <= 0) return
        val inner = (width * 0.9f).toInt() - bubble.paddingLeft - bubble.paddingRight
        val paint = Paint().apply { typeface = bubble.typeface; textSize = 10 * resources.displayMetrics.scaledDensity }
        val sample = paint.measureText("⣿".repeat(output.app.phoneWidth)).coerceAtLeast(1f)
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, (10f * inner / sample).coerceIn(3f, 14f))
        bubble.maxWidth = (width * 0.9f).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // tall enough: drawing area above the preview as in the owner's sketch; otherwise side by side
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val vertical = height / dp >= STACK_MIN_DP
        val wanted = if (vertical) VERTICAL else HORIZONTAL
        if (middle.orientation != wanted) {
            middle.orientation = wanted
            for (index in 0 until middle.childCount) {
                val child = middle.getChildAt(index)
                child.layoutParams = if (vertical) LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { if (index > 0) topMargin = (4 * dp).toInt() }
                    else LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { if (index > 0) marginStart = (4 * dp).toInt() }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { fitPreview(); fitBubble() }
    }

    companion object {
        const val MAX_TEXT = 300
        const val MAX_CUSTOM = 10
        const val MAX_PICTURES = 12
        const val PHOTO_PX = 256
        const val UNDO_MILLIS = 8_000L
        const val LIVE_RENDER_MILLIS = 40L
        /** From this height of the window on, the preview goes below the drawing area. */
        const val STACK_MIN_DP = 380
        private val RENDER_TOKEN = Any()
        private val CONTRASTS = floatArrayOf(0.5f, 0.75f, 1f, 1.5f, 2f)
        private val THRESHOLDS = floatArrayOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)
        private val SIZES = floatArrayOf(1f, 0.75f, 0.5f)
        private val FRAME_EMOJIS = listOf("✨", "⭐", "❤️", "🌸", "🔥")
        private val APPS = listOf(TargetApp.WHATSAPP to R.string.agent_char_art_app_whatsapp, TargetApp.TELEGRAM to R.string.agent_char_art_app_telegram,
            TargetApp.DISCORD to R.string.agent_char_art_app_discord, TargetApp.SIGNAL to R.string.agent_char_art_app_signal,
            TargetApp.GENERIC to R.string.agent_char_art_app_generic)
        private val FAMILIES = listOf(UnicodeStyle.Family.SERIF to R.string.agent_char_art_family_serif,
            UnicodeStyle.Family.SANS to R.string.agent_char_art_family_sans, UnicodeStyle.Family.SCRIPT to R.string.agent_char_art_family_script,
            UnicodeStyle.Family.FRAKTUR to R.string.agent_char_art_family_fraktur, UnicodeStyle.Family.DOUBLE_STRUCK to R.string.agent_char_art_family_double,
            UnicodeStyle.Family.MONOSPACE to R.string.agent_char_art_family_mono, UnicodeStyle.Family.CIRCLED to R.string.agent_char_art_family_circled,
            UnicodeStyle.Family.SQUARED to R.string.agent_char_art_family_squared, UnicodeStyle.Family.SMALL_CAPS to R.string.agent_char_art_family_smallcaps,
            UnicodeStyle.Family.UPSIDE_DOWN to R.string.agent_char_art_family_upside)

        private fun FloatArray.next(current: Float): Float {
            val index = indexOfFirst { kotlin.math.abs(it - current) < 1e-3f }
            return this[(index + 1).mod(size)]
        }

        private fun FloatArray.formatted(value: Float): String =
            if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import helium314.keyboard.latin.suggestions.BarDrag
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.StripIconColors
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.getStringResourceOrName
import java.util.Locale

/**
 * Fork, stage 85: the panel of all tools in the key area, in every style. The left key of the fixed bar opens it;
 * the strip above stays visible and usable, touches there pass through this view (as with [FlatMenuView], whose
 * layout it follows: pills 48 dp high in two columns, 24 dp side margins, 8 dp gaps). A tap on a pill closes the
 * panel first and then reports the tool, so the tool runs exactly like its key in the bar. A tool that the field
 * does not allow is dimmed and does not react. Nothing is animated.
 *
 * Stage 86: a button "Anpassen" at the top starts the edit mode of the bar ([onEditRequested]). While [editListener] is
 * set, the panel shows the hint "Zum Personalisieren halten und ziehen" instead, a tap on a pill adds the tool to the
 * bar or takes it out ([EditListener.onTap]), holding a pill starts a drag ([EditListener.onDrag]), and a tool dropped on
 * the panel is reported to [dropListener]. Tools that are in the bar are shown dimmed ([setMarked]).
 */
class ToolGridView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    /** One pill: the tool and whether the current field allows it. */
    class Tool(val key: ToolbarKey, val available: Boolean = true, val reason: helium314.keyboard.latin.utils.BarTools.Reason? = null)

    /** What the panel asks of the edit mode of the bar (stage 86). */
    interface EditListener {
        /** A tap on a pill: the tool goes into the bar or out of it. */
        fun onTap(key: ToolbarKey)
        /** The last change can be taken back ("Rückgängig"). */
        fun canUndo(): Boolean
        fun onUndo()
        /** "Zurücksetzen" was confirmed in the panel: the standard bar comes back. */
        fun onReset()
        /** Actions for screen readers on the pill of [key]: the label and what it does (true when it did something). */
        fun actions(key: ToolbarKey): List<Pair<Int, () -> Boolean>>
    }

    /** A pill of a tool that the field does not allow was tapped: the owner of the panel says why (stage 88). */
    var onUnavailable: ((ToolbarKey, helium314.keyboard.latin.utils.BarTools.Reason?) -> Unit)? = null
    /** Called after the panel has closed itself because a pill was tapped. */
    var onTool: ((ToolbarKey) -> Unit)? = null
    /** The button "Anpassen" was tapped; the owner of the panel starts the edit mode. */
    var onEditRequested: (() -> Unit)? = null
    /** Non-null while the bar is being edited. */
    var editListener: EditListener? = null
        private set
    /**
     * Stage 88: the touch logic of the edit mode (see [BarDrag]); this view is the container that takes the touches of the bar
     * slots and of a drag, and draws the floating copy of the dragged tool above the bar and the panel.
     */
    var dragLogic: BarDrag? = null
    /** The tool of the bar slot under a point of this view (its coordinates), or null. */
    var slotAt: ((Float, Float) -> ToolbarKey?)? = null
    /** The events of the touch logic, after the logic has handled them; the owner updates the bar. */
    var onDragEvents: ((List<BarDrag.Event>) -> Unit)? = null
    /** Where the slot of [key] is in the bar after a drop (centre, in the coordinates of this view), for the floating copy to glide to. */
    var slotCentre: ((ToolbarKey) -> PointF?)? = null
    private var hintText: CharSequence? = null
    private var marked = emptySet<ToolbarKey>()
    private var lastTools = emptyList<Tool>()
    private var pillBackground = 0
    private var gridGround = 0
    private var liftedKey: ToolbarKey? = null
    private var keyColour = 0
    private var keyText = 0
    private var keyIcon = 0
    /** Tests draw the panel in the colours of a design that is not the current one. */
    internal var colorsOverride: helium314.keyboard.latin.common.Colors? = null
    private val reasons = HashMap<ToolbarKey, helium314.keyboard.latin.utils.BarTools.Reason?>()
    /** Called whenever the panel goes away, for whatever reason. */
    var onClosed: (() -> Unit)? = null

    val isOpen: Boolean get() = visibility == VISIBLE

    private val content = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val pills = LinkedHashMap<ToolbarKey, Button>()

    init {
        visibility = GONE
        // one finger at a time: a second finger on another pill must not act (stage 88, B7)
        isMotionEventSplittingEnabled = false
        scroll.isMotionEventSplittingEnabled = false
        content.isMotionEventSplittingEnabled = false
        content.orientation = LinearLayout.VERTICAL
        // swallow touches between the pills so that they do not reach the keys underneath
        content.isClickable = true
        content.isSoundEffectsEnabled = false
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        (parent as? AgentKeyboardFrame)?.updateOverlayAccessibility()
    }

    /** Why the pill of [key] is in the "off" look, or null when the tool is available (stage 88). */
    fun reason(key: ToolbarKey) = reasons[key]

    /** The pill of [key], or null when the panel does not list it. */
    fun pill(key: ToolbarKey): Button? = pills[key]

    /** The tools in the order of the panel, as built by the last [open]. */
    val listed: List<ToolbarKey> get() = pills.keys.toList()

    /** The hint line of the edit mode, null for the standard one. */
    val hint: TextView? get() = content.findViewById(R.id.agent_tool_grid_hint)

    /** Switches the edit mode on (with the listener) or off (null); the pills are built again. */
    fun setEditing(listener: EditListener?) {
        if (listener == null) { cancelTouch(); dragLogic = null; slotAt = null; onDragEvents = null; slotCentre = null }
        editListener = listener
        hintText = null
        if (listener == null) marked = emptySet()
        if (lastTools.isNotEmpty()) build(lastTools)
    }

    /** A line instead of the standard hint, e.g. that the bar is full; null for the standard hint again. */
    fun setHint(text: CharSequence?) {
        hintText = text
        hint?.text = text ?: context.getString(R.string.agent_grid_edit_hint)
    }

    /** The tools that are in the bar are dimmed in the panel while it is edited. */
    fun setMarked(keys: Set<ToolbarKey>) {
        marked = keys
        if (editListener == null) return
        refreshActions()
        pills.forEach { (key, pill) -> applyMark(key, pill) }
    }

    /** Shows [tools] below the strip, which is [stripHeight] pixels high. */
    fun open(stripHeight: Int, tools: List<Tool>) {
        build(tools)
        (scroll.layoutParams as LayoutParams).topMargin = stripHeight
        scroll.scrollTo(0, 0)
        scroll.requestLayout()
        visibility = VISIBLE
    }

    fun close() {
        if (!isOpen) return
        cancelTouch()
        visibility = GONE
        editListener = null // the edit mode ends with the panel; what was dropped is saved already
        onClosed?.invoke()
    }

    /**
     * A child takes the gesture (the scroll view catching a fling with a touch, or scrolling) and asks not to be intercepted: from then on
     * this container sees neither moves nor the end of it. So the touch is over for the edit mode at once, before the hold timer
     * could pick a tool up under a finger that is only stopping the panel (stage 88, review).
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        val logic = dragLogic
        if (disallowIntercept && logic != null && logic.phase == BarDrag.Phase.PRESSED) endTouch(logic)
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onDetachedFromWindow() {
        cancelTouch()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) close() // the keyboard was hidden
    }

    // ---- stage 88: the touches of the edit mode ----

    private var trackedPointer = -1
    private var floating: ImageView? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val holdTimer = Runnable {
        // (no check of the scroll position here: every way a child takes the gesture goes through requestDisallowInterceptTouchEvent, see there)
        val logic = dragLogic ?: return@Runnable
        handle(logic.tick(SystemClock.uptimeMillis()))
    }

    /** Forgets a touch (whatever state it was in); a drag in progress is given back like a cancel. */
    private fun endTouch(logic: BarDrag) {
        handler.removeCallbacks(holdTimer)
        trackedPointer = -1
        val events = logic.cancel()
        if (events.isNotEmpty()) handle(events)
    }

    private fun targetAt(x: Float, y: Float): BarDrag.Target? {
        slotAt?.invoke(x, y)?.let { return BarDrag.Target.Slot(it) }
        if (y < scroll.top) return null // the strip: only the slots count, the left key and the rest pass through
        for ((key, pill) in pills) if (rectOf(pill).contains(x, y)) return BarDrag.Target.Pill(key)
        return null
    }

    /** The bounds of [view] (a pill) in the coordinates of this view, scrolled. */
    private fun rectOf(view: View): RectF {
        var x = 0f
        var y = 0f
        var v: View = view
        while (v !== this) {
            val parent = v.parent as? View ?: break
            x += v.left - parent.scrollX
            y += v.top - parent.scrollY
            v = parent
        }
        return RectF(x, y, x + view.width, y + view.height)
    }

    /** Scrolls the panel so that the pill of [key] is in view (a finger does this by scrolling; the tests need it too). */
    fun revealPill(key: ToolbarKey) {
        val pill = pills[key] ?: return
        val r = rectOf(pill)
        if (r.top >= scroll.top && r.bottom <= height) return
        scroll.scrollBy(0, (r.top - scroll.top - dp(GAP_DP)).toInt())
    }

    /** The centre of the pill of [key] in the coordinates of this view, or null when it is not listed or scrolled out of sight. */
    fun pillCentre(key: ToolbarKey): PointF? {
        val pill = pills[key] ?: return null
        val r = rectOf(pill)
        if (r.bottom < scroll.top || r.top > height) return null
        return PointF(r.left + dp(ICON_START_DP + 12f), r.centerY())
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val logic = dragLogic ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // a new gesture never fails on an old state: the end of the last one may have gone to the scroll view or a child
                endTouch(logic)
                val on = targetAt(ev.x, ev.y) ?: return false
                if (!logic.down(ev.getPointerId(0), ev.x, ev.y, ev.eventTime, on)) return false
                trackedPointer = ev.getPointerId(0)
                handler.postDelayed(holdTimer, (logic.holdDueAt() - ev.eventTime).coerceAtLeast(0L))
                return on is BarDrag.Target.Slot // a pill still works as a button (tap) and as a scroller until the drag starts
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                if (trackedPointer < 0) return false
                val dragging = logic.phase == BarDrag.Phase.DRAGGING
                val events = feed(logic, ev)
                // a short touch on a pill is done here (the pill's own click would need a post that a view without a window never runs, and a cancelled child cannot click twice)
                return dragging || logic.phase == BarDrag.Phase.DRAGGING || events.any { it is BarDrag.Event.Tapped }
            }
        }
        return logic.phase == BarDrag.Phase.DRAGGING
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val logic = dragLogic ?: return super.onTouchEvent(ev)
        if (trackedPointer < 0) return false // nothing of ours: the touch passes to what is behind (the strip, the left key)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> { feed(logic, ev) }
        }
        return true // a second finger and the rest of a gesture that started here are ours (and ignored)
    }

    private fun feed(logic: BarDrag, ev: MotionEvent): List<BarDrag.Event> {
        val index = ev.findPointerIndex(trackedPointer)
        val pointerUp = ev.actionMasked == MotionEvent.ACTION_POINTER_UP
        if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP && ev.getPointerId(ev.actionIndex) != trackedPointer) return emptyList() // another finger lifts: ignored
        if (index < 0) return emptyList()
        val x = ev.getX(index)
        val y = ev.getY(index)
        val events = when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> logic.move(trackedPointer, x, y, ev.eventTime)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> logic.up(trackedPointer, x, y, ev.eventTime)
            else -> logic.cancel()
        }
        if (logic.phase != BarDrag.Phase.PRESSED) handler.removeCallbacks(holdTimer)
        if (logic.phase == BarDrag.Phase.IDLE) trackedPointer = -1
        if (pointerUp && logic.phase != BarDrag.Phase.IDLE) return events
        handle(events)
        return events
    }

    private fun handle(events: List<BarDrag.Event>) {
        val logic = dragLogic ?: return
        if (events.isNotEmpty()) onDragEvents?.invoke(events)
        for (e in events) when (e) {
            is BarDrag.Event.PickedUp -> startFloating(e.key, e.fromBar)
            is BarDrag.Event.Dropped -> endFloating(e.key, e.toBar)
            is BarDrag.Event.Cancelled -> removeFloating()
            else -> Unit
        }
        if (logic.phase == BarDrag.Phase.DRAGGING) moveFloating(logic.x, logic.y)
    }

    private val floatingSize get() = dp(FLOAT_DP)

    private fun startFloating(key: ToolbarKey, fromBar: Boolean) {
        removeFloating()
        val colors = colorsOverride ?: Settings.getValues()?.mColors
        val disc = colors?.get(ColorType.KEY_BACKGROUND) ?: FlatStyle.KEY
        val ring = colors?.get(ColorType.ACTION_KEY_BACKGROUND) ?: FlatStyle.ACCENT
        val icon = StripIconColors.ensureContrast(colors?.get(ColorType.KEY_ICON) ?: FlatStyle.FUNCTIONAL_LABEL, disc)
        val view = ImageView(context)
        view.setImageDrawable(KeyboardIconsSet.instance.getNewDrawable(key.name, context)?.mutate()?.apply { setTint(icon) })
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        val pad = dp(10f)
        view.setPadding(pad, pad, pad, pad)
        view.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(disc); setStroke(dp(2f), ring) }
        view.elevation = dp(10f).toFloat()
        view.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        view.isClickable = false
        view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        view.scaleX = SCALE; view.scaleY = SCALE
        view.contentDescription = null
        addView(view, LayoutParams(floatingSize, floatingSize))
        floating = view
        view.tag = key
        liftedKey = key
        pills[key]?.alpha = LIFTED_ALPHA // the pill of the tool being dragged stays as an empty place
        moveFloating(dragLogic?.x ?: 0f, dragLogic?.y ?: 0f)
    }

    /** The floating copy is centred under the finger (a pill is wide, so an offset from its centre would put the copy far from the finger), kept inside the keyboard window. */
    private fun moveFloating(x: Float, y: Float) {
        val view = floating ?: return
        val half = floatingSize / 2f
        view.x = x.coerceIn(half, (width - half).coerceAtLeast(half)) - half
        view.y = y.coerceIn(half, (height - half).coerceAtLeast(half)) - half
        view.visibility = VISIBLE
    }

    private fun endFloating(key: ToolbarKey, toBar: Boolean) {
        val view = floating ?: return
        floating = null
        liftedKey = null
        pills[key]?.let { applyMark(key, it) } // the pill's look follows the bar again
        val half = floatingSize / 2f
        val target = (if (toBar) slotCentre?.invoke(key) else pillCentre(key))
        if (target == null || !isAttachedToWindow) { removeView(view); return }
        view.animate().cancel()
        view.animate().x(target.x - half).y(target.y - half).scaleX(0.8f).scaleY(0.8f).alpha(0.2f).setDuration(GLIDE_MS)
            .withEndAction { removeView(view) }.start()
    }

    private fun removeFloating() {
        val view = floating ?: return
        floating = null
        view.animate().cancel()
        removeView(view)
        liftedKey = null
        (view.tag as? ToolbarKey)?.let { key -> pills[key]?.let { applyMark(key, it) } }
    }

    /** The floating copy while a drag is going on (tests and the owner look at it). */
    val floatingCopy: View? get() = floating

    /** Stops tracking a touch and removes the floating copy; nothing is saved here (the model drops its drag state). */
    fun cancelTouch() {
        handler.removeCallbacks(holdTimer)
        trackedPointer = -1
        dragLogic?.cancel()
        removeFloating()
    }

    private fun dp(value: Float) = Math.round(value * resources.displayMetrics.density)

    private fun build(tools: List<Tool>) {
        lastTools = tools
        content.removeAllViews()
        pills.clear()
        reasons.clear()
        val colors = colorsOverride ?: Settings.getValues()?.mColors
        val background = colors?.get(ColorType.MAIN_BACKGROUND) ?: FlatStyle.BACKGROUND
        val pill = colors?.get(ColorType.KEY_BACKGROUND) ?: FlatStyle.KEY
        val text = colors?.get(ColorType.KEY_TEXT) ?: FlatStyle.LABEL
        val icon = colors?.get(ColorType.KEY_ICON) ?: FlatStyle.FUNCTIONAL_LABEL
        pillBackground = pill
        // the pills are computed against the surface they really have (the key colour may be translucent), see PillColors
        gridGround = background
        keyColour = pill; keyText = text; keyIcon = icon
        val look = PillColors.normal(pill, background, text, icon)

        scroll.setBackgroundColor(background)
        content.setPadding(dp(SIDE_DP), dp(SIDE_DP), dp(SIDE_DP), dp(SIDE_DP))
        content.addView(header(look.text, look.icon, look.fill), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(GAP_DP) })
        tools.chunked(COLUMNS).forEachIndexed { rowIndex, rowTools ->
            // no baseline alignment: a pill with a two-line name has a lower first baseline and would be pushed down, out of its row (88c)
            val row = LinearLayout(context).apply { isMotionEventSplittingEnabled = false; isBaselineAligned = false }
            rowTools.forEachIndexed { column, tool ->
                val params = LinearLayout.LayoutParams(0, dp(PILL_HEIGHT_DP), 1f)
                if (column > 0) params.marginStart = dp(GAP_DP)
                val button = pillButton(tool, pill, text, icon)
                pills[tool.key] = button
                row.addView(button, params)
            }
            // a last row with one pill keeps the column width of the others
            if (rowTools.size < COLUMNS) repeat(COLUMNS - rowTools.size) {
                row.addView(View(context), LinearLayout.LayoutParams(0, dp(PILL_HEIGHT_DP), 1f).apply { marginStart = dp(GAP_DP) })
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (rowIndex > 0) params.topMargin = dp(GAP_DP)
            content.addView(row, params)
        }
    }

    /** The hint line while editing, else the button that starts the edit mode. */
    private fun header(textColor: Int, iconColor: Int, pillColor: Int): View {
        val listener = editListener
        if (listener != null) {
            val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(context).apply {
                id = R.id.agent_tool_grid_hint
                text = hintText ?: context.getString(R.string.agent_grid_edit_hint)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
                gravity = Gravity.CENTER
                setPadding(0, dp(GAP_DP), 0, dp(GAP_DP))
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            // "Rückgängig" and "Zurücksetzen" (with the question in the panel itself, no system dialog)
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val ask = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; visibility = GONE; id = R.id.agent_tool_grid_reset_ask }
            fun gap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(EDIT_HEIGHT_DP)).apply { marginStart = dp(GAP_DP); marginEnd = dp(GAP_DP) }
            actions.addView(smallButton(R.id.agent_tool_grid_undo, R.string.agent_bar_edit_undo, textColor, pillColor) {
                if (listener.canUndo()) { listener.onUndo(); refreshActions() }
            }, gap())
            actions.addView(smallButton(R.id.agent_tool_grid_reset, R.string.agent_bar_edit_reset, textColor, pillColor) {
                actions.visibility = GONE; ask.visibility = VISIBLE
            }, gap())
            ask.addView(TextView(context).apply {
                setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
                text = context.getString(R.string.agent_bar_reset_ask)
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }, gap())
            ask.addView(smallButton(R.id.agent_tool_grid_reset_yes, R.string.agent_bar_reset_yes, textColor, pillColor) {
                ask.visibility = GONE; actions.visibility = VISIBLE; listener.onReset(); refreshActions()
            }, gap())
            ask.addView(smallButton(R.id.agent_tool_grid_reset_no, R.string.agent_bar_reset_no, textColor, pillColor) {
                ask.visibility = GONE; actions.visibility = VISIBLE
            }, gap())
            box.addView(actions)
            box.addView(ask)
            return box
        }
        return Button(context, null, 0).apply {
            id = R.id.agent_tool_grid_edit
            text = context.getString(R.string.agent_grid_edit)
            isAllCaps = false
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
            typeface = FlatStyle.labelTypeface
            gravity = Gravity.CENTER
            isSoundEffectsEnabled = false
            background = roundedBackground(pillColor, dp(EDIT_HEIGHT_DP) / 2f)
            minimumWidth = 0; minimumHeight = 0
            setPadding(dp(ICON_START_DP), 0, dp(ICON_START_DP), 0)
            val edit = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_edit)?.mutate()?.apply { setTint(iconColor) }
                ?.let { InsetDrawable(it, 0, 0, dp(GAP_DP), 0) }
            setCompoundDrawablesRelativeWithIntrinsicBounds(edit, null, null, null)
            setOnClickListener { onEditRequested?.invoke() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(EDIT_HEIGHT_DP)).apply { gravity = Gravity.END }
        }
    }

    private fun smallButton(buttonId: Int, textRes: Int, textColor: Int, pillColor: Int, action: () -> Unit) = Button(context, null, 0).apply {
        id = buttonId
        text = context.getString(textRes)
        isAllCaps = false
        setTextColor(StripIconColors.ensureContrast(textColor, pillColor))
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
        typeface = FlatStyle.labelTypeface
        gravity = Gravity.CENTER // a button without a style has its text at the top
        isSoundEffectsEnabled = false
        background = roundedBackground(pillColor, dp(EDIT_HEIGHT_DP) / 2f)
        minimumWidth = 0; minimumHeight = 0
        setPadding(dp(ICON_START_DP), 0, dp(ICON_START_DP), 0)
        setOnClickListener { action() }
    }

    /** "Rückgängig" looks off while there is nothing to take back. */
    fun refreshActions() {
        val undo = content.findViewById<View>(R.id.agent_tool_grid_undo) ?: return
        undo.alpha = if (editListener?.canUndo() == true) 1f else 0.4f
    }

    private fun applyMark(key: ToolbarKey, pill: Button) {
        val label = key.name.lowercase(Locale.ROOT).getStringResourceOrName("", context)
        val inBar = key in marked
        // a tool in the bar has a duller pill (not an alpha: the text stays readable)
        stylePill(pill, key, if (inBar) PillColors.inBar(keyColour, gridGround, keyText, keyIcon) else PillColors.normal(keyColour, gridGround, keyText, keyIcon), outlined = inBar)
        pill.alpha = if (key == liftedKey) LIFTED_ALPHA else 1f // the pill of the tool in the hand stays an empty place
        pill.contentDescription = if (inBar) context.getString(R.string.agent_bar_in_bar, label) else label
    }

    /** Surface, text and icon of a pill (the icon is rebuilt: its tint cannot be changed from outside). */
    private fun stylePill(button: Button, key: ToolbarKey, look: PillColors.Look, outlined: Boolean = false) {
        button.setTextColor(look.text)
        button.background = roundedBackground(look.fill, dp(PILL_HEIGHT_DP) / 2f, look.pressed, if (outlined) look.text else 0)
        val start = KeyboardIconsSet.instance.getNewDrawable(key.name, context)?.mutate()?.apply { setTint(look.icon) }
            ?.let { InsetDrawable(it, 0, 0, dp(ICON_GAP_DP), 0) }
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(start, null, null, null)
    }

    private fun pillButton(tool: Tool, pillColor: Int, textColor: Int, iconColor: Int): Button {
        val key = tool.key
        val button = Button(context, null, 0) // no default style: no shadow, no minimum size, no caps
        val label = key.name.lowercase(Locale.ROOT).getStringResourceOrName("", context)
        val off = !tool.available && editListener == null
        button.tag = key
        button.text = label
        button.isAllCaps = false
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
        button.typeface = FlatStyle.labelTypeface
        // a long name ("Diktieren (auf dem Gerät)") is never cut off: it shrinks (down to 10 dp) and may take two lines
        button.maxLines = 2
        button.ellipsize = android.text.TextUtils.TruncateAt.END
        button.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol || (v as Button).textSize == 0f) fitText(v as Button) }
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        button.isSoundEffectsEnabled = false
        button.setPadding(dp(ICON_START_DP), 0, dp(TEXT_END_DP), 0)
        button.compoundDrawablePadding = 0
        stylePill(button, key, PillColors.normal(pillColor, gridGround, textColor, iconColor).let { if (off) PillColors.unavailable(it) else it })
        val editing = editListener
        if (editing != null) {
            // customizing is about the layout of the bar: also a tool the field does not allow can be moved
            button.setOnClickListener { editing.onTap(key) }
            editing.actions(key).forEach { (labelRes, action) ->
                androidx.core.view.ViewCompat.addAccessibilityAction(button, context.getString(labelRes)) { _, _ -> action() }
            }
            applyMark(key, button)
            return button
        }
        reasons[key] = if (tool.available) null else (tool.reason ?: helium314.keyboard.latin.utils.BarTools.Reason.PRIVATE_FIELD)
        // a tool the field does not allow stays tappable for the touch system: the tap says why (stage 88)
        button.contentDescription = if (tool.available) label else context.getString(R.string.agent_bar_tool_unavailable, label)
        button.setOnClickListener { act(key) }
        return button
    }

    /**
     * The name always fits: the largest size from [TEXT_DP] down to [MIN_TEXT_DP] at which it takes one or two lines in the room that
     * is left beside the icon; only below that it is shortened with "…". (The framework's auto size does not do this reliably for a text
     * beside a compound drawable, and a view without a window never runs it.)
     */
    private fun fitText(button: Button) {
        val room = button.width - button.paddingLeft - button.paddingRight - (button.compoundDrawablesRelative[0]?.intrinsicWidth ?: 0)
        if (room <= 0) return
        val paint = android.text.TextPaint(button.paint)
        var dpSize = TEXT_DP
        var fitting = MIN_TEXT_DP
        while (dpSize >= MIN_TEXT_DP) {
            paint.textSize = dpSize * resources.displayMetrics.density
            // the constructor, not StaticLayout.Builder (API 23; the minimum is 21)
            @Suppress("DEPRECATION")
            val layout = android.text.StaticLayout(button.text, paint, room, android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            if (layout.lineCount <= 2 && (0 until layout.lineCount).all { layout.getLineWidth(it) <= room }) { fitting = dpSize; break }
            dpSize -= 0.5f
        }
        val px = fitting * resources.displayMetrics.density
        if (button.textSize != px) button.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
    }

    private fun act(key: ToolbarKey) {
        if (key in reasons && reasons[key] != null) { onUnavailable?.invoke(key, reasons[key]); return } // "off": say why, stay open
        close()
        onTool?.invoke(key)
    }

    private fun roundedBackground(color: Int, radius: Float, pressedColor: Int = ColorUtils.blendARGB(color, Color.WHITE, 0.15f), outline: Int = 0) = GradientDrawable().apply {
        cornerRadius = radius
        // a tool that is in the bar has a thin outline in the colour of its text (a second cue besides the duller surface)
        if (outline != 0) setStroke(dp(1.5f), ColorUtils.setAlphaComponent(outline, 0x99))
        val pressed = pressedColor
        setColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(pressed, color)))
    }

    companion object {
        const val COLUMNS = 2
        private const val SIDE_DP = 24f
        private const val GAP_DP = 8f
        private const val PILL_HEIGHT_DP = 48f
        private const val TEXT_DP = 16f
        private const val MIN_TEXT_DP = 10f
        private const val ICON_START_DP = 14f
        private const val TEXT_END_DP = 10f
        private const val ICON_GAP_DP = 10f
        private const val EDIT_HEIGHT_DP = 40f
        private const val FLOAT_DP = 52f
        private const val SCALE = 1.15f
        private const val LIFTED_ALPHA = 0.3f
        private const val GLIDE_MS = 140L
    }
}

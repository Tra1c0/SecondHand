// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SettingsDestination
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Stage 38a: the component kit of the new settings (mockup docs/referenzen/einstellungen-mockup).
 * One CSS px of the mockup is one dp (one sp for text). Headings use the system font in bold
 * instead of Space Grotesk, body text the system font instead of Inter (no bundled fonts).
 */

/** Colours, radii and sizes of the mockup. */
object AgentSettingsTokens {
    val Background = Color(0xFF0B0B0F)
    val Card = Color(0xFF18181D)
    val Border = Color(0xFF2A2A31)
    val Text = Color(0xFFF2F2F5)
    val Muted = Color(0xFFA3A3AD)
    /** Values in rows, footer pills and chips that are off. */
    val Soft = Color(0xFFC9C9D1)
    val Chevron = Color(0xFF8E8E99)
    /** Round buttons and footer pills. */
    val Button = Color(0xFF1E1E24)
    val ChipBorder = Color(0xFF33333B)
    val SwitchOff = Color(0xFF3A3A42)
    /** Insets inside a card (gesture buttons, design tiles). */
    val Inset = Color(0xFF0F0F13)
    /** Text on gradients and on the light button. */
    val OnGradient = Color(0xFF0B0B0F)

    val CardRadius = 28.dp
    val HeroRadius = 30.dp
    val TileRadius = 26.dp
    val ChoiceRadius = 22.dp
    val RowRadius = 18.dp
    val MinTouch = 44.dp
    val PagePadding = 16.dp

    val SwitchWidth = 52.dp
    val SwitchHeight = 30.dp
    val SwitchKnob = 22.dp
}

/** Text styles of the mockup; headings stand in for Space Grotesk, the rest for Inter. */
object AgentSettingsType {
    private val heading = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
    private val body = TextStyle(fontFamily = FontFamily.Default)
    val PageTitle = heading.copy(fontSize = 32.sp, letterSpacing = (-0.5).sp)
    val HeroTitle = heading.copy(fontSize = 34.sp, letterSpacing = (-0.5).sp)
    val Number = heading.copy(fontSize = 13.sp)
    val SectionTitle = heading.copy(fontSize = 20.sp)
    val SpacedTitle = heading.copy(fontSize = 20.sp, letterSpacing = 1.5.sp)
    val TileTitle = heading.copy(fontSize = 19.sp, lineHeight = 1.15.em)
    val ChoiceLabel = heading.copy(fontSize = 19.sp, letterSpacing = 3.sp)
    val TileSub = body.copy(fontSize = 12.5.sp, lineHeight = 1.35.em, fontWeight = FontWeight.Medium)
    val HeroSub = body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val RowTitle = body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val Small = body.copy(fontSize = 12.5.sp)
    val Label = body.copy(fontSize = 13.sp)
    val Button = body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val LookName = body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * The seven areas of the new settings with number, texts and colours. [route] is the navigation
 * route of the area page; the start page lays the tiles out as 01 wide, 02/03, 04/05, 06 wide,
 * 07 wide ([wide]).
 */
enum class SettingsArea(
    val route: String,
    val number: String,
    @StringRes val title: Int,
    @StringRes val tileSubtitle: Int,
    @StringRes val subtitle: Int,
    val from: Color,
    val to: Color,
    /** Colour of the dots in front of the rows. */
    val dot: Color,
    val wide: Boolean,
) {
    LOOK(SettingsDestination.AreaLook, "01", R.string.agent_area_look, R.string.agent_area_look_tile,
        R.string.agent_area_look_sub, Color(0xFFFF4D8D), Color(0xFFFFB547), Color(0xFFFF6B8A), true),
    TYPING(SettingsDestination.AreaTyping, "02", R.string.agent_area_typing, R.string.agent_area_typing_tile,
        R.string.agent_area_typing_sub, Color(0xFF00C2A8), Color(0xFF7CF0C8), Color(0xFF2BD4B4), false),
    KEYS(SettingsDestination.AreaKeys, "03", R.string.agent_area_keys, R.string.agent_area_keys_tile,
        R.string.agent_area_keys_sub, Color(0xFF5B8CFF), Color(0xFFA9C7FF), Color(0xFF6E9BFF), false),
    GESTURES(SettingsDestination.AreaGestures, "04", R.string.agent_area_gestures, R.string.agent_area_gestures_tile,
        R.string.agent_area_gestures_sub, Color(0xFFA78BFA), Color(0xFFE9D5FF), Color(0xFFB794F6), false),
    BAR(SettingsDestination.AreaBar, "05", R.string.agent_area_bar, R.string.agent_area_bar_tile,
        R.string.agent_area_bar_sub, Color(0xFFFFC94D), Color(0xFFFFE7A3), Color(0xFFFFC94D), false),
    AI(SettingsDestination.AreaAi, "06", R.string.agent_area_ai, R.string.agent_area_ai_tile,
        R.string.agent_area_ai_sub, Color(0xFF22D3EE), Color(0xFFA5B4FC), Color(0xFF38D5EE), true),
    SYSTEM(SettingsDestination.AreaSystem, "07", R.string.agent_area_system, R.string.agent_area_system_tile,
        R.string.agent_area_system_sub, Color(0xFF94A3B8), Color(0xFFE2E8F0), Color(0xFFB6C2D1), true);

    /** The area gradient, 135° as on tiles and heroes. */
    fun brush(angle: Float = 135f): Brush = cssGradient(angle, from, to)

    companion object {
        fun ofRoute(route: String?): SettingsArea? = entries.firstOrNull { it.route == route }
    }
}

/**
 * A CSS `linear-gradient(<angle>deg, ...)`: 0° points up, 90° right, and the gradient line is as
 * long as CSS makes it, so the first and last colour sit exactly in the corners.
 */
fun cssGradient(angle: Float, from: Color, to: Color): Brush = cssGradient(angle, listOf(0f to from, 1f to to))

fun cssGradient(angle: Float, stops: List<Pair<Float, Color>>): Brush = CssGradient(angle, stops)

private class CssGradient(private val angle: Float, private val stops: List<Pair<Float, Color>>) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = Math.toRadians(angle.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val delta = Offset(dx * half, dy * half)
        return LinearGradientShader(center - delta, center + delta, stops.map { it.second }, stops.map { it.first })
    }
    override fun equals(other: Any?) = other is CssGradient && other.angle == angle && other.stops == stops
    override fun hashCode() = angle.hashCode() * 31 + stops.hashCode()
}

/** Dark scheme of the mockup for the new pages and for the upstream search results shown on them. */
@Composable
fun AgentSettingsTheme(content: @Composable () -> Unit) {
    val t = AgentSettingsTokens
    val scheme = darkColorScheme(
        primary = t.Text, onPrimary = t.OnGradient,
        background = t.Background, onBackground = t.Text,
        surface = t.Background, onSurface = t.Text, onSurfaceVariant = t.Muted,
        surfaceContainer = t.Card, surfaceContainerHigh = t.Card, surfaceContainerHighest = t.Button,
        surfaceVariant = t.Card, outline = t.ChipBorder, outlineVariant = t.Border,
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides t.Text, content = content)
    }
}

/** Line icons of the mockup (24 × 24 viewport, stroke 2, round caps). */
enum class LineIcon { SEARCH, BACK, CHEVRON }

@Composable
fun AgentLineIcon(icon: LineIcon, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            LineIcon.SEARCH -> {
                drawCircle(color, radius = 7f * s, center = Offset(11f * s, 11f * s), style = stroke)
                drawLine(color, Offset(20f * s, 20f * s), Offset(16.5f * s, 16.5f * s), 2f * s, StrokeCap.Round)
            }
            LineIcon.BACK -> drawPath(Path().apply { moveTo(15f * s, 5f * s); lineTo(8f * s, 12f * s); lineTo(15f * s, 19f * s) }, color, style = stroke)
            LineIcon.CHEVRON -> drawPath(Path().apply { moveTo(9f * s, 6f * s); lineTo(15f * s, 12f * s); lineTo(9f * s, 18f * s) }, color, style = stroke)
        }
    }
}

/** Round 44 dp button with a line icon (search on the start page, back on area pages). */
@Composable
fun AgentRoundButton(icon: LineIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(AgentSettingsTokens.MinTouch).clip(CircleShape).background(AgentSettingsTokens.Button)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AgentLineIcon(icon, AgentSettingsTokens.Text, 20.dp) }
}

/** Back button with the name of the page above ("Einstellungen"). */
@Composable
fun BackHeader(onBack: () -> Unit, label: String = stringResource(R.string.agent_settings_title), modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AgentRoundButton(LineIcon.BACK, stringResource(R.string.agent_settings_back), onBack, Modifier.testTag(BACK_TAG))
        Text(label, style = AgentSettingsType.Label, color = AgentSettingsTokens.Muted)
    }
}

/** Gradient card at the top of an area page: number, title, subtitle. */
@Composable
fun AreaHero(area: SettingsArea, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(AgentSettingsTokens.HeroRadius)).background(area.brush())
            .padding(horizontal = 20.dp, vertical = 22.dp)
            // one stop for TalkBack: number, title (heading) and subtitle
            .semantics(mergeDescendants = true) { heading() }.testTag(HERO_TAG),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val c = AgentSettingsTokens.OnGradient
        Text(area.number, style = AgentSettingsType.Number, color = c, modifier = Modifier.alpha(0.7f))
        Text(stringResource(area.title), style = AgentSettingsType.HeroTitle, color = c)
        Text(stringResource(area.subtitle), style = AgentSettingsType.HeroSub, color = c)
    }
}

/** Dark card with a title and rows ([SettingRow], [SettingSwitchRow]): 8 dp inside, title 20 sp. */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(cardModifier(modifier).padding(8.dp)) {
        if (title != null)
            Text(title, style = AgentSettingsType.SectionTitle, color = AgentSettingsTokens.Text,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp).semantics { heading() })
        content()
    }
}

/**
 * Dark card for free content (choice cards, chips, design tiles): 18 dp inside, the title spaced
 * and in capitals when [spaced], an optional [hint] on the right of the title.
 */
@Composable
fun FeatureCard(
    title: String?,
    modifier: Modifier = Modifier,
    spaced: Boolean = false,
    hint: String? = null,
    gap: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(cardModifier(modifier).padding(18.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (title != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (spaced) title.uppercase(Locale.getDefault()) else title,
                style = if (spaced) AgentSettingsType.SpacedTitle else AgentSettingsType.SectionTitle,
                color = AgentSettingsTokens.Text, modifier = Modifier.weight(1f, fill = false).semantics { heading() })
            if (hint != null) Text(hint, style = AgentSettingsType.Label, color = AgentSettingsTokens.Muted)
        }
        content()
    }
}

private fun cardModifier(modifier: Modifier) = modifier.fillMaxWidth()
    .clip(RoundedCornerShape(AgentSettingsTokens.CardRadius)).background(AgentSettingsTokens.Card)
    .border(1.dp, AgentSettingsTokens.Border, RoundedCornerShape(AgentSettingsTokens.CardRadius))

@Composable
private fun RowText(title: String, summary: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = AgentSettingsType.RowTitle, color = AgentSettingsTokens.Text)
        if (summary != null) Text(summary, style = AgentSettingsType.Small, color = AgentSettingsTokens.Muted)
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(12.dp).clip(CircleShape).background(color))

/**
 * A row that opens something: dot, title, summary, then [value] and a chevron. The whole row is
 * one button for TalkBack ("<title> öffnen").
 */
@Composable
fun SettingRow(
    title: String,
    summary: String?,
    dot: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    val label = stringResource(R.string.agent_settings_open, title)
    Row(
        modifier.fillMaxWidth().heightIn(min = AgentSettingsTokens.MinTouch).clip(RoundedCornerShape(AgentSettingsTokens.RowRadius))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Dot(dot)
        RowText(title, summary, Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!value.isNullOrEmpty()) Text(value, style = AgentSettingsType.Label, color = AgentSettingsTokens.Soft)
            AgentLineIcon(LineIcon.CHEVRON, AgentSettingsTokens.Chevron, 16.dp)
        }
    }
}

/** A row with a [GradientSwitch]; the whole row is the switch (Role.Switch, on/off for TalkBack). */
@Composable
fun SettingSwitchRow(
    title: String,
    summary: String?,
    dot: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    from: Color,
    to: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = AgentSettingsTokens.MinTouch).clip(RoundedCornerShape(AgentSettingsTokens.RowRadius))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Dot(dot)
        RowText(title, summary, Modifier.weight(1f))
        GradientSwitch(checked, null, from, to)
    }
}

/**
 * The switch of the mockup: 52 × 30 dp, knob 22 dp, the area gradient when on, grey when off. With
 * [onCheckedChange] it is its own switch (Role.Switch, at least 44 dp to touch, [description] for
 * TalkBack); without, it only shows the state of the row around it.
 */
@Composable
fun GradientSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    from: Color,
    to: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val t = AgentSettingsTokens
    val position by animateFloatAsState(if (checked) 1f else 0f, label = "switch")
    val touch = if (onCheckedChange == null) Modifier else Modifier
        .heightIn(min = t.MinTouch)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
    Box(modifier.then(touch), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(t.SwitchWidth, t.SwitchHeight).clip(RoundedCornerShape(t.SwitchHeight / 2))
                .background(if (checked) cssGradient(90f, from, to) else Brush.linearGradient(listOf(t.SwitchOff, t.SwitchOff)))
                .padding(4.dp)
        ) {
            Box(Modifier.offset(x = (t.SwitchWidth - 8.dp - t.SwitchKnob) * position).size(t.SwitchKnob).clip(CircleShape).background(t.Text))
        }
    }
}

/** One big card of [ChoiceCards]; a card that is not [enabled] cannot be chosen (38e: gesture typing without its library). */
class Choice(val label: String, val hint: String, val from: Color, val to: Color, val tag: String = label, val enabled: Boolean = true)

/**
 * A choice as big gradient cards (owner's sketch of the frame rate, ROADMAP 37): spaced capital
 * label, hint, a glowing pill; the chosen card at full opacity with a white outline, the others at
 * 55 %. TalkBack: radio buttons in one group.
 */
@Composable
fun ChoiceCards(
    title: String,
    choices: List<Choice>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    FeatureCard(title, modifier, spaced = true) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.forEachIndexed { i, choice -> ChoiceCard(choice, i, i == selected) { onSelect(i) } }
        }
        below()
    }
}

@Composable
private fun ChoiceCard(choice: Choice, index: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AgentSettingsTokens.ChoiceRadius)
    val outline = AgentSettingsTokens.Text
    Box(
        Modifier.fillMaxWidth()
            // outline 3 dp, 2 dp outside the card (CSS outline-offset), drawn before the clip
            .drawWithContent {
                drawContent()
                if (selected) {
                    val w = 3.dp.toPx(); val off = 2.dp.toPx() + w / 2
                    drawRoundRect(outline, topLeft = Offset(-off, -off), size = Size(size.width + 2 * off, size.height + 2 * off),
                        cornerRadius = CornerRadius(AgentSettingsTokens.ChoiceRadius.toPx() + off), style = Stroke(w))
                }
            }
            .alpha(if (selected) 1f else if (choice.enabled) 0.55f else 0.35f)
            .clip(shape).background(cssGradient(120f, choice.from, choice.to))
            .selectable(selected = selected, enabled = choice.enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 92.dp).testTag(CHOICE_TAG + choice.tag),
    ) {
        Glow(index)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(choice.label.uppercase(Locale.getDefault()), style = AgentSettingsType.ChoiceLabel, color = AgentSettingsTokens.OnGradient)
            Text(choice.hint, style = AgentSettingsType.Small.copy(fontWeight = FontWeight.Medium), color = AgentSettingsTokens.OnGradient,
                textAlign = TextAlign.Start)
        }
    }
}

/** The glowing pill: 42 × 22 dp, 14 dp from the top, 18 + 40 dp × index from the right. */
@Composable
private fun BoxScope.Glow(index: Int) {
    Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 18.dp + 40.dp * index).size(42.dp, 22.dp)
        .clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.75f)))
}

/** One chip of [ChipToggles]. */
class ChipItem(val label: String, val checked: Boolean, val onToggle: (Boolean) -> Unit, val tag: String = label)

/**
 * Toggle chips that wrap. [area] null: the light chips of the start page (on = light, off = dark
 * with a border); with an area: on = area gradient, off = outlined (command chips of the bar
 * page). Each chip is a switch for TalkBack and at least 44 dp high.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipToggles(items: List<ChipItem>, modifier: Modifier = Modifier, area: SettingsArea? = null) {
    val t = AgentSettingsTokens
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val shape = RoundedCornerShape(if (area == null) 22.dp else 20.dp)
            val (background, border, text) = when {
                area == null && item.checked -> Triple(Brush.linearGradient(listOf(t.Text, t.Text)), Color.Transparent, t.OnGradient)
                area == null -> Triple(Brush.linearGradient(listOf(t.Card, t.Card)), t.ChipBorder, t.Soft)
                item.checked -> Triple(cssGradient(120f, area.from, area.to), Color.Transparent, t.OnGradient)
                else -> Triple(Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), t.SwitchOff, t.Chevron)
            }
            Box(
                Modifier.heightIn(min = t.MinTouch).widthIn(min = t.MinTouch).clip(shape).background(background).border(1.dp, border, shape)
                    .toggleable(value = item.checked, role = Role.Switch, onValueChange = item.onToggle)
                    .padding(horizontal = if (area == null) 15.dp else 14.dp, vertical = if (area == null) 11.dp else 8.dp)
                    .testTag(CHIP_TAG + item.tag),
                contentAlignment = Alignment.Center,
            ) { Text(item.label, style = AgentSettingsType.Button, color = text) }
        }
    }
}

/** Gradient tile of an area on the start page: number, title, subtitle at the bottom. */
@Composable
fun AreaTile(area: SettingsArea, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AgentSettingsTokens.OnGradient
    Column(
        modifier.heightIn(min = if (area.wide) 110.dp else 150.dp).clip(RoundedCornerShape(AgentSettingsTokens.TileRadius))
            .background(area.brush()).clickable(role = Role.Button, onClick = onClick).padding(16.dp)
            .testTag(TILE_TAG + area.route),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        Text(area.number, style = AgentSettingsType.Number, color = c, modifier = Modifier.alpha(0.7f))
        Text(stringResource(area.title), style = AgentSettingsType.TileTitle, color = c)
        Text(stringResource(area.tileSubtitle), style = AgentSettingsType.TileSub, color = c)
    }
}

/** Small dark pill (footer of the start page), 44 dp high to be easy to hit. */
@Composable
fun FooterPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = AgentSettingsTokens.MinTouch).widthIn(min = AgentSettingsTokens.MinTouch).clip(RoundedCornerShape(18.dp)).background(AgentSettingsTokens.Button)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AgentSettingsType.Label, color = AgentSettingsTokens.Soft) }
}

const val BACK_TAG = "agent_settings_back"
const val HERO_TAG = "agent_area_hero"
const val TILE_TAG = "agent_area_tile_"
const val CHIP_TAG = "agent_chip_"
const val CHOICE_TAG = "agent_choice_"

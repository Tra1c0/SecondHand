// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import helium314.keyboard.latin.utils.prefs

/**
 * Key corner radius and label font of a user design (stage 23f2). Both are optional preferences;
 * without them every style looks as before. [load] runs once per keyboard view, when its key
 * backgrounds have been loaded, so a change takes effect with the next theme reload.
 */
object DesignLook {
    /** Corner radius of the key backgrounds in dp, as float; absent = the style's own radius. */
    const val PREF_CORNER_RADIUS = "agent_key_corner_radius"
    /** Label font: [FONT_REGULAR], [FONT_MEDIUM] or [FONT_BOLD]; absent = the style's own font. */
    const val PREF_FONT = "agent_key_font"
    const val FONT_REGULAR = "regular"
    const val FONT_MEDIUM = "medium"
    const val FONT_BOLD = "bold"
    val FONTS = listOf(FONT_REGULAR, FONT_MEDIUM, FONT_BOLD)

    /** Shapes with a larger radius are pills (Flat action and layout keys) and stay pills. */
    private const val PILL_RADIUS_DP = 100f

    @Volatile private var font: String? = null

    private val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    /** Reads both preferences and applies the corner radius to the given key backgrounds. */
    @JvmStatic
    fun load(context: Context, vararg backgrounds: Drawable) {
        val prefs = context.prefs()
        font = prefs.getString(PREF_FONT, null)?.takeIf { it in FONTS }
        val radius = prefs.getFloat(PREF_CORNER_RADIUS, -1f)
        if (radius < 0f || !radius.isFinite()) return
        val density = context.resources.displayMetrics.density
        backgrounds.forEach { setCornerRadius(it, radius * density, PILL_RADIUS_DP * density) }
    }

    /** The chosen label font, or null to keep the style's font. */
    @JvmStatic
    fun typeface(): Typeface? = typeface(font)

    fun typeface(name: String?): Typeface? = when (name) {
        FONT_REGULAR -> Typeface.DEFAULT
        FONT_MEDIUM -> medium
        FONT_BOLD -> Typeface.DEFAULT_BOLD
        else -> null
    }

    /**
     * Sets [radius] on every shape inside [drawable] (selectors and layer lists included). The
     * drawables are already mutated by Colors.selectAndColorDrawable, so shared resources stay as
     * they are. Before Android 7 a shape's radius cannot be read, so pills cannot be told apart and
     * nothing changes there.
     */
    fun setCornerRadius(drawable: Drawable, radius: Float, pillRadius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        when (drawable) {
            is GradientDrawable -> if (drawable.cornerRadius < pillRadius) drawable.cornerRadius = radius
            is LayerDrawable -> for (i in 0 until drawable.numberOfLayers) setCornerRadius(drawable.getDrawable(i), radius, pillRadius)
            is DrawableContainer -> (drawable.constantState as? DrawableContainer.DrawableContainerState)
                ?.children?.filterNotNull()?.forEach { setCornerRadius(it, radius, pillRadius) }
        }
    }
}

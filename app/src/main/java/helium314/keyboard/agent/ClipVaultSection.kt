// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.agentkeyboard.core.clip.ClipCategory
import dev.agentkeyboard.core.clip.ClipGuard
import dev.agentkeyboard.core.clip.ClipKind
import helium314.keyboard.latin.R

/**
 * The vault rows at the top of the clipboard history view (stage 44): "Vault" with the masked
 * secrets, "One-time codes" with the codes, each a row of chips; nothing when empty or blocked.
 * Chip text and TalkBack description show only [ClipVault.Item.shown]. Also the labels of personal
 * clips in the normal list ([historyText]).
 */
object ClipVaultSection {
    const val TAG_SECTION = "agent_clip_vault"
    const val TAG_SECRET_ROW = "agent_clip_vault_secrets"
    const val TAG_CODE_ROW = "agent_clip_vault_codes"
    /** Stage 45: the chip that opens "Meine Daten & Vorschläge". */
    const val TAG_MY_DATA = "agent_clip_vault_my_data"
    private const val ROW_DP = 40f
    private const val CHIP_DP = 32f
    private const val LABEL_START_DP = 11f
    private const val GAP_DP = 6f

    private fun Context.dp(value: Float) = Math.round(value * resources.displayMetrics.density)

    /**
     * Rebuilds the section as first child of [parent]; [blocked] or an empty vault hide it.
     * Returns the height it takes, which the caller subtracts from the list.
     */
    fun update(
        parent: LinearLayout, blocked: Boolean, textColor: Int, chipColor: Int, typeface: Typeface?,
        onPick: (ClipVault.Item) -> Unit,
    ): Int {
        val context = parent.context
        val secrets = if (blocked) emptyList() else ClipVault.secrets()
        val codes = if (blocked) emptyList() else ClipVault.codes()
        val existing = parent.findViewWithTag<LinearLayout>(TAG_SECTION)
        if (secrets.isEmpty() && codes.isEmpty()) {
            // created only when needed, so the view keeps its upstream children otherwise
            existing?.removeAllViews()
            existing?.visibility = View.GONE
            return 0
        }
        val section = existing ?: LinearLayout(context).apply {
            tag = TAG_SECTION
            orientation = LinearLayout.VERTICAL
            parent.addView(this, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        section.removeAllViews()
        section.visibility = View.VISIBLE
        var rows = 0
        if (secrets.isNotEmpty()) {
            section.addView(row(context, TAG_SECRET_ROW, context.getString(R.string.agent_vault_title), secrets, textColor, chipColor, typeface, onPick))
            rows++
        }
        if (codes.isNotEmpty()) {
            section.addView(row(context, TAG_CODE_ROW, context.getString(R.string.agent_vault_codes), codes, textColor, chipColor, typeface, onPick))
            rows++
        }
        // stage 45: "Meine Daten" at the end of the last row opens its settings page
        ((section.getChildAt(section.childCount - 1) as? HorizontalScrollView)?.getChildAt(0) as? LinearLayout)
            ?.addView(chipView(context, context.getString(R.string.agent_vault_my_data), textColor, chipColor, typeface) {
                helium314.keyboard.agent.commands.SnipCommand.openSettings(context, AgentPreferences.MY_DATA_DESTINATION)
            }.apply { tag = TAG_MY_DATA; alpha = 0.7f },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(CHIP_DP)).apply { marginEnd = context.dp(GAP_DP) })
        return rows * context.dp(ROW_DP)
    }

    private fun row(
        context: Context, rowTag: String, title: String, items: List<ClipVault.Item>, textColor: Int, chipColor: Int,
        typeface: Typeface?, onPick: (ClipVault.Item) -> Unit,
    ): View {
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(LABEL_START_DP), 0, context.dp(LABEL_START_DP), 0)
        }
        line.addView(TextView(context).apply {
            text = title
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            this.typeface = typeface
            isSingleLine = true
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(GAP_DP * 2) })
        items.forEach { item ->
            val shown = item.shown
            line.addView(chipView(context, shown, textColor, chipColor, typeface) { onPick(item) }.apply {
                tag = item.id
                contentDescription = context.getString(
                    if (item.kind == ClipKind.SECRET) R.string.agent_vault_secret_description else R.string.agent_vault_code_description, shown)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(CHIP_DP)).apply { marginEnd = context.dp(GAP_DP) })
        }
        return HorizontalScrollView(context).apply {
            tag = rowTag
            isHorizontalScrollBarEnabled = false
            addView(line)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(ROW_DP))
        }
    }

    private fun chipView(context: Context, label: String, textColor: Int, chipColor: Int, typeface: Typeface?, onTap: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            this.typeface = typeface
            isSingleLine = true
            gravity = Gravity.CENTER
            setPadding(context.dp(12f), 0, context.dp(12f), 0)
            background = GradientDrawable().apply { cornerRadius = context.dp(CHIP_DP) / 2f; setColor(chipColor) }
            isClickable = true
            setOnClickListener { onTap() }
        }

    /** Label of a personal clip ("IBAN", "Telefon", "E-Mail", "Adresse"), or null. */
    fun label(context: Context, kind: ClipKind): String? = when (kind) {
        ClipKind.IBAN -> context.getString(R.string.agent_vault_label_iban)
        ClipKind.PHONE -> context.getString(R.string.agent_vault_label_phone)
        ClipKind.EMAIL -> context.getString(R.string.agent_vault_label_email)
        ClipKind.ADDRESS -> context.getString(R.string.agent_vault_label_address)
        else -> null
    }

    /**
     * Card text of a history entry: personal clips get their label in a small first line; a secret
     * still in the history (pinned before stage 44) is masked. Returns text and TalkBack description
     * (null: the text itself).
     */
    fun historyText(context: Context, text: String, kind: ClipKind): Pair<CharSequence, String?> {
        if (kind.category == ClipCategory.SECRET) {
            val masked = context.getString(R.string.agent_vault_hidden_entry, ClipGuard.mask(text))
            return masked to masked
        }
        val label = label(context, kind) ?: return text to null
        val builder = SpannableStringBuilder(label).append('\n').append(text)
        builder.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(RelativeSizeSpan(0.75f), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return builder to context.getString(R.string.agent_vault_labelled_description, label, text)
    }
}

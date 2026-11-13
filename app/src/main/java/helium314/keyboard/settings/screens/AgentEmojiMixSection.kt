// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.agent.commands.EmojiMixCommand
import helium314.keyboard.agent.commands.EmojiMixer
import helium314.keyboard.latin.R

/**
 * Stage 25a, on the commands page: the strip chip after two typed emojis (default on), what a tap
 * sends to Google, and the stored images. The `@mix` switch is in the list of commands above.
 */
@Composable
fun AgentEmojiMixSection() {
    val context = LocalContext.current
    var strip by remember { mutableStateOf(EmojiMixCommand.stripEnabled(context)) }
    var stored by remember { mutableStateOf(EmojiMixer.cache(context).size()) }
    Column {
        Text(stringResource(R.string.agent_emoji_mix_title), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.agent_emoji_mix_strip), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.agent_emoji_mix_strip_description), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = strip, onCheckedChange = {
                strip = it
                EmojiMixCommand.setStripEnabled(context, it)
            })
        }
        Text(stringResource(R.string.agent_emoji_mix_privacy), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.agent_emoji_mix_cache, Formatter.formatShortFileSize(context, stored)),
            style = MaterialTheme.typography.bodyMedium)
        if (stored > 0) OutlinedButton(onClick = {
            EmojiMixer.clearCache(context)
            stored = 0
        }) { Text(stringResource(R.string.agent_emoji_mix_clear)) }
    }
}

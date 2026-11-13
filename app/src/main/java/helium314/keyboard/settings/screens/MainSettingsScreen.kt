// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.initPreview

/**
 * fork (stage 38a): the main settings page is the new start page [AgentStartScreen] for every
 * keyboard style. The upstream list of entries is gone; each entry is on one of the seven area
 * pages ([SettingsAreas]), reached through [onNavigate] with its route.
 */
@Composable
fun MainSettingsScreen(
    onNavigate: (String) -> Unit,
    onClickBack: () -> Unit,
) {
    AgentStartScreen(onNavigate = onNavigate, onClickBack = onClickBack)
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {})
        }
    }
}

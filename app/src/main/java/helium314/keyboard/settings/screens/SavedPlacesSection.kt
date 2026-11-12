// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.Places
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.SavedPlaces
import helium314.keyboard.latin.R

/** "Gespeicherte Orte" on the commands page (stage 24f): name and address for `@ort zuhause`. */
@Composable
fun SavedPlacesSection() {
    val context = LocalContext.current
    val available = remember { SavedPlaces.available(context) }
    var places by remember { mutableStateOf(SavedPlaces.load(context)) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.agent_places_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.agent_places_description, KeyboardCommands.prefix(context).toString()))
        if (!available) {
            Text(stringResource(R.string.agent_places_unavailable))
            return@Column
        }
        for (place in places) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleSmall)
                    Text(place.address, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = {
                    SavedPlaces.delete(context, place.name)
                    places = SavedPlaces.load(context)
                }) { Text(stringResource(R.string.agent_places_delete, place.name)) }
            }
        }
        OutlinedTextField(name, { name = it.take(Places.MAX_NAME) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.agent_places_name)) }, singleLine = true)
        OutlinedTextField(address, { address = it.take(Places.MAX_ADDRESS) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.agent_places_address)) })
        TextButton(onClick = {
            message = when (SavedPlaces.save(context, name, address)) {
                SavedPlaces.SaveResult.SAVED -> { name = ""; address = ""; null }
                SavedPlaces.SaveResult.INVALID -> context.getString(R.string.agent_places_invalid)
                SavedPlaces.SaveResult.FULL -> context.getString(R.string.agent_places_full, Places.MAX_PLACES)
                SavedPlaces.SaveResult.UNAVAILABLE -> context.getString(R.string.agent_places_unavailable)
            }
            places = SavedPlaces.load(context)
        }) { Text(stringResource(R.string.agent_places_add)) }
        message?.let { Text(it) }
    }
}

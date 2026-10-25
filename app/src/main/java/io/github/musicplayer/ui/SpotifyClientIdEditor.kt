package io.github.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.musicplayer.spotify.SpotifyCatalogState
import io.github.musicplayer.spotify.SpotifyPkce

/** An OAuth Client ID is public app configuration, never a password or a client secret. */
@Composable
internal fun SpotifyClientIdEditor(
    catalog: SpotifyCatalogState,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(catalog.clientId) { mutableStateOf(catalog.clientId) }
    val expanded = editing || !catalog.configured
    val valid = SpotifyPkce.validClientId(draft.trim())

    Column(Modifier.fillMaxWidth().testTag("spotify_client_id_editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (expanded) {
            Text("SPOTIFY DEVELOPER ID", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(64) },
                label = { Text("Public Client ID") },
                placeholder = { Text("32 hexadecimal characters") },
                supportingText = { Text("Find it at developer.spotify.com/dashboard") },
                singleLine = true,
                isError = draft.isNotBlank() && !valid,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                shape = CyberSmall,
                modifier = Modifier.fillMaxWidth().testTag("spotify_client_id_field"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSave(draft.trim()); editing = false },
                    enabled = valid,
                    shape = CyberSmall,
                    modifier = Modifier.weight(1f).testTag("spotify_client_id_save"),
                ) { Text("Save Client ID") }
                if (catalog.configured) {
                    TextButton(onClick = { draft = catalog.clientId; editing = false }, modifier = Modifier.testTag("spotify_client_id_cancel")) {
                        Text("Cancel")
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CLIENT ID  ••••${catalog.clientId.takeLast(4)}", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { editing = true }, modifier = Modifier.testTag("spotify_client_id_edit")) {
                    Text("Change ID")
                }
            }
        }
        if (catalog.clientIdOverridden) {
            TextButton(onClick = { onReset(); editing = false }, modifier = Modifier.testTag("spotify_client_id_reset")) {
                Text("Remove saved ID / use build default")
            }
        }
        Text(
            "Register http://127.0.0.1/callback in your Spotify app's redirect settings. " +
                "Only paste its public Client ID — never a secret. Spotify still handles account authorization.",
            color = Muted, fontSize = 11.sp,
        )
    }
}

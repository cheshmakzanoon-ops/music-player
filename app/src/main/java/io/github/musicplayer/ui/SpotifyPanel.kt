package io.github.musicplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.musicplayer.spotify.SpotifyContentUri
import io.github.musicplayer.spotify.SpotifyCatalogState
import io.github.musicplayer.spotify.SpotifyRemoteState

/** A separate source: Spotify's official client owns its audio, account and availability. */
@Composable
fun SpotifyPanel(
    state: SpotifyRemoteState,
    catalog: SpotifyCatalogState,
    onClose: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAuthorizeCatalog: () -> Unit,
    onDisconnectCatalog: () -> Unit,
    onSetCatalogClientId: (String) -> Unit,
    onResetCatalogClientId: () -> Unit,
    onOpenSpotify: () -> Unit,
    onPlayContent: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    var link by rememberSaveable { mutableStateOf("") }
    val contentUri = remember(link) { SpotifyContentUri.normalize(link) }
    PlayerTheme {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize().testTag("spotify_panel"), color = Ink) {
                Column(Modifier.safeDrawingPadding().fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("EXTERNAL SOURCE / 01", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp)
                            Text("Spotify connection", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = onClose, modifier = Modifier.testTag("close_spotify")) {
                            Icon(Icons.Rounded.Close, "Close Spotify connection", tint = Milk)
                        }
                    }
                    HorizontalDivider(color = Hairline)
                    Column(
                        Modifier.widthIn(max = 600.dp).weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (state.connected) Cyan else Muted, CyberSmall))
                            Text(
                                when {
                                    state.connecting -> "CONNECTING TO SPOTIFY"
                                    state.connected -> "CONNECTED TO SPOTIFY"
                                    else -> "READY WHEN YOU ARE"
                                },
                                color = if (state.connected) Cyan else Muted,
                                fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                modifier = Modifier.padding(start = 9.dp).testTag("spotify_connection_state"),
                            )
                        }
                        if (state.hasTrack && state.connected) {
                            Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f).align(Alignment.CenterHorizontally).background(Panel), contentAlignment = Alignment.Center) {
                                state.artwork?.let {
                                    Image(it.asImageBitmap(), "Cover art for ${state.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } ?: Icon(Icons.Rounded.Headphones, null, tint = Cyan, modifier = Modifier.size(72.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(state.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("spotify_track_title"))
                                Text(state.artist, color = Cyan, style = MaterialTheme.typography.bodyLarge)
                                if (state.album.isNotBlank()) Text(state.album, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Column {
                                val duration = state.durationMs.coerceAtLeast(1L)
                                var seeking by remember(state.title, state.durationMs) { mutableStateOf<Float?>(null) }
                                Slider(
                                    value = seeking ?: state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                                    onValueChange = { seeking = it },
                                    onValueChangeFinished = { seeking?.let { onSeek(it.toLong()) }; seeking = null },
                                    valueRange = 0f..duration.toFloat(),
                                    enabled = state.connected && state.canSeek,
                                    modifier = Modifier.fillMaxWidth().testTag("spotify_seek").semantics { contentDescription = "Spotify playback position" },
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(spotifyTime(seeking?.toLong() ?: state.positionMs), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    Text(spotifyTime(state.durationMs), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onShuffle, enabled = state.connected && state.canShuffle, modifier = Modifier.testTag("spotify_shuffle")) {
                                    Icon(Icons.Rounded.Shuffle, if (state.shuffle) "Disable Spotify shuffle" else "Enable Spotify shuffle", tint = if (state.shuffle) Cyan else LocalContentColor.current)
                                }
                                IconButton(onClick = onPrevious, enabled = state.connected && state.canSkipPrevious, modifier = Modifier.testTag("spotify_previous")) {
                                    Icon(Icons.Rounded.SkipPrevious, "Previous Spotify track", modifier = Modifier.size(30.dp))
                                }
                                FilledIconButton(onClick = onTogglePlay, enabled = state.connected, modifier = Modifier.size(68.dp).testTag("spotify_play_pause"), shape = CyberPanel) {
                                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause Spotify" else "Play Spotify", modifier = Modifier.size(32.dp))
                                }
                                IconButton(onClick = onNext, enabled = state.connected && state.canSkipNext, modifier = Modifier.testTag("spotify_next")) {
                                    Icon(Icons.Rounded.SkipNext, "Next Spotify track", modifier = Modifier.size(30.dp))
                                }
                                IconButton(onClick = onRepeat, enabled = state.connected && state.canRepeat, modifier = Modifier.testTag("spotify_repeat")) {
                                    Icon(Icons.Rounded.Repeat, if (state.repeat) "Disable Spotify repeat" else "Enable Spotify repeat", tint = if (state.repeat) Cyan else LocalContentColor.current)
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().heightIn(min = 190.dp).clip(CyberPanel).border(1.dp, Hairline, CyberPanel)) {
                                CyberCity(Modifier.matchParentSize())
                                Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("YOUR SOUND.\nSAME SERVICE.", color = Milk, fontSize = 26.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black)
                                    Text("Spotify, through your own control room.", color = Cyan, fontSize = 12.sp)
                                }
                            }
                        }
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().testTag("spotify_error"))
                        }
                        Column(
                            Modifier.fillMaxWidth().clip(CyberPanel).background(Panel)
                                .border(1.dp, Cyan.copy(.4f), CyberPanel).padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("CATALOGUE / SPOTIFY WEB API", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            Text("Explore Spotify tracks, albums and artists inside Search.", color = Milk, style = MaterialTheme.typography.bodyMedium)
                            Text("Independent of player controls. Spotify authorization is optional, and your local collection never requires it.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            when {
                                !catalog.configured -> Text("Enter your Spotify developer Client ID below. You won't need another APK.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("spotify_catalog_panel_unconfigured"))
                                !catalog.authorized -> Button(
                                    onClick = onAuthorizeCatalog, enabled = !catalog.authorizing, shape = CyberSmall,
                                    modifier = Modifier.fillMaxWidth().testTag("spotify_catalog_panel_connect"),
                                ) { Text(if (catalog.authorizing) "Waiting for authorization…" else "Connect catalogue") }
                                else -> {
                                    Text("CATALOGUE CONNECTED — search Spotify from NIGHT SHIFT.", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.testTag("spotify_catalog_connected"))
                                    OutlinedButton(onClick = onDisconnectCatalog, shape = CyberSmall, modifier = Modifier.fillMaxWidth().testTag("spotify_catalog_disconnect")) { Text("Disconnect catalogue") }
                                }
                            }
                            SpotifyClientIdEditor(catalog, onSetCatalogClientId, onResetCatalogClientId)
                            catalog.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (!state.configReady) {
                            Text("Spotify connection isn't configured in this build. Your local library and audio links are ready to use.", color = Milk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("spotify_not_configured"))
                        } else if (!state.installed) {
                            Text("Install the official Spotify app to connect. Your local library works on its own.", color = Milk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("spotify_not_installed"))
                        }
                        if (!state.connected) {
                            Button(onClick = onConnect, enabled = state.configReady && state.installed && !state.connecting, shape = CyberSmall, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("spotify_connect")) {
                                if (state.connecting) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                                Text(if (state.connecting) "Connecting…" else "Connect with Spotify")
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().clip(CyberPanel).background(Panel).border(1.dp, Hairline, CyberPanel).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Pick your next track", style = MaterialTheme.typography.titleMedium)
                                Text("Paste a Spotify track, album, artist or playlist link.", color = Muted, style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = link, onValueChange = { link = it },
                                    label = { Text("Spotify link") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    isError = link.isNotBlank() && contentUri == null,
                                    modifier = Modifier.fillMaxWidth().testTag("spotify_content_link"),
                                )
                                Button(onClick = { onPlayContent(link) }, enabled = contentUri != null, shape = CyberSmall, modifier = Modifier.fillMaxWidth().testTag("spotify_play_content")) {
                                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                                    Text("Play on Spotify", modifier = Modifier.padding(start = 7.dp))
                                }
                                if (state.canPlayOnDemand == false) Text("This account cannot request individual tracks on demand. Spotify determines the available playback options.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = onDisconnect, shape = CyberSmall, modifier = Modifier.fillMaxWidth().testTag("spotify_disconnect")) { Text("Disconnect controls") }
                        }
                        TextButton(onClick = onOpenSpotify, modifier = Modifier.fillMaxWidth().testTag("spotify_open_official")) {
                            Text("Open Spotify", modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                        }
                        HorizontalDivider(color = Hairline)
                        Text("Spotify handles account authorization, audio playback, offline listening and catalogue availability. NIGHT SHIFT does not create another account or ask you to choose a country.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text("PLAYBACK PROVIDED BY SPOTIFY", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

private fun spotifyTime(value: Long): String {
    val seconds = value.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

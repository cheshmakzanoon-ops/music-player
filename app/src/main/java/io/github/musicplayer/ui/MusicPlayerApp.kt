package io.github.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.musicplayer.data.AudioUrlPolicy
import io.github.musicplayer.model.DownloadState
import io.github.musicplayer.model.Track
import io.github.musicplayer.spotify.SpotifyCatalogItem
import kotlin.math.PI
import kotlin.math.sin

private enum class Destination { HOME, SEARCH, LIBRARY }

@Composable
fun MusicPlayerApp(state: PlayerUiState, actions: PlayerActions) {
    PlayerTheme {
        var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
        var query by rememberSaveable { mutableStateOf("") }
        var expanded by rememberSaveable { mutableStateOf(false) }
        var settings by rememberSaveable { mutableStateOf(false) }
        var addSource by rememberSaveable { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it, withDismissAction = true)
                actions.onDismissMessage()
            }
        }
        BackHandler(expanded || settings || addSource || destination != Destination.HOME) {
            when {
                addSource -> addSource = false
                settings -> settings = false
                expanded -> expanded = false
                else -> destination = Destination.HOME
            }
        }
        Scaffold(
            containerColor = Ink,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column(Modifier.background(Ink).navigationBarsPadding()) {
                    state.playback.track?.let {
                        MiniPlayer(state, actions, onExpand = { expanded = true })
                    }
                    Navigation(destination) { destination = it }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).testTag("app_ready"), contentAlignment = Alignment.TopCenter) {
                Crossfade(destination, modifier = Modifier.widthIn(max = 800.dp).fillMaxSize(), animationSpec = tween(if (state.reduceMotion) 0 else 220), label = "navigation") { page ->
                    when (page) {
                        Destination.HOME -> HomeScreen(state, actions, { settings = true }) { expanded = true }
                        Destination.SEARCH -> SearchScreen(state, actions, query, { query = it }, { settings = true })
                        Destination.LIBRARY -> LibraryScreen(state, actions, { addSource = true }, { settings = true })
                    }
                }
            }
        }
        if (expanded && state.playback.track != null) {
            NowPlaying(state, actions) { expanded = false }
        }
        if (settings) SettingsDialog(state, actions) { settings = false }
        if (addSource) AddSourceDialog(actions) { addSource = false }
    }
}

@Composable
private fun Navigation(selected: Destination, onSelect: (Destination) -> Unit) {
    Column {
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(Destination.HOME, "Discover", Icons.Rounded.Explore),
                Triple(Destination.SEARCH, "Search", Icons.Rounded.Search),
                Triple(Destination.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
            ).forEach { (destination, label, icon) ->
                val active = selected == destination
                Column(
                    Modifier.weight(1f).clip(CyberSmall)
                        .background(if (active) Lime.copy(.10f) else Color.Transparent)
                        .clickable { onSelect(destination) }.testTag("nav_${destination.name.lowercase()}")
                        .semantics { this.selected = active; role = Role.Tab }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = if (active) Lime else Muted, modifier = Modifier.size(22.dp))
                    Text(label, color = if (active) Lime else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.width(18.dp).height(2.dp).background(if (active) Lime else Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun AppHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            Box(Modifier.size(35.dp).clip(CyberSmall).background(Lime), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
            }
            Column {
                Text("NIGHT SHIFT", fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, fontSize = 17.sp)
                Text("MUSIC PLAYER  /  AFTER HOURS", fontFamily = FontFamily.Monospace, color = Cyan, fontSize = 8.sp, letterSpacing = .8.sp)
            }
        }
        RoundIcon(Icons.Rounded.Tune, "Settings", onSettings, Modifier.testTag("settings"), filled = true)
    }
}

@Composable
private fun HomeScreen(state: PlayerUiState, actions: PlayerActions, onSettings: () -> Unit, onExpand: () -> Unit) {
    val studioTracks = remember(state.tracks) { state.tracks.filter { it.source == "Original studio sample" && it.license == "CC0-1.0" } }
    LazyColumn(Modifier.fillMaxSize().testTag("home_screen"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AppHeader(onSettings) }
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(22.dp).height(2.dp).background(Cyan))
                    Eyebrow("YOUR WORLD. YOUR FREQUENCY.", color = Cyan)
                }
                Spacer(Modifier.height(12.dp))
                Text("AFTER HOURS.", style = MaterialTheme.typography.displayLarge, modifier = Modifier.semantics { heading() })
                Text("ON REPEAT.", style = MaterialTheme.typography.displayLarge, color = Lime)
                Text("A soundtrack for the city in your head.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 11.dp))
            }
        }
        item {
            val featured = studioTracks.firstOrNull()
            if (featured != null) {
                HeroCard(featured, !state.reduceMotion) {
                    actions.onPlay(featured, studioTracks)
                    onExpand()
                }
            } else if (state.catalogLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lime) }
            } else {
                EmptyState(Icons.Rounded.LibraryMusic, "Your next favourite awaits", "Import audio from your phone to start listening.", "Import audio", actions.onImportAudio)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 17.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headphones, null, tint = Cyan, modifier = Modifier.size(15.dp))
                Text("GUEST MODE", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = .9.sp, modifier = Modifier.padding(start = 7.dp))
                Spacer(Modifier.weight(1f))
                Text("NO SIGNUP · JUST PLAY", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Hairline)
        }
        item {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 22.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("01 / ORIGINALS", color = Cyan)
                    Text("Studio transmissions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
                }
                Text(studioTracks.size.toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, color = Lime, fontSize = 23.sp)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(studioTracks.take(6), key = { it.id }) { track ->
                    Column(Modifier.width(152.dp).clip(CyberSmall).clickable { actions.onPlay(track, studioTracks) }.testTag("album_${track.id}")) {
                        Box {
                            AlbumArt(track, Modifier.fillMaxWidth().aspectRatio(1f).clip(CyberPanel).border(1.dp, cyberAccent(track).copy(.35f), CyberPanel))
                            Box(Modifier.padding(10.dp).align(Alignment.BottomEnd).size(32.dp).background(Lime, CyberSmall), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Ink, modifier = Modifier.size(20.dp))
                            }
                        }
                        Text(track.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 11.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${timeLabel(track.durationMs)} / ORIGINAL AUDIO", color = cyberAccent(track), fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp, bottom = 7.dp))
                    }
                }
            }
        }
        item { SectionHeading("In rotation", "02 / YOUR AVAILABLE TRACKS", Modifier.padding(top = 18.dp)) }
        items(state.tracks, key = { "list_${it.id}" }) { track -> TrackRow(track, state, actions, state.tracks) }
        item {
            Column(Modifier.padding(24.dp).fillMaxWidth().clip(CyberPanel).background(Panel).border(1.dp, Cyan.copy(.3f), CyberPanel).padding(20.dp)) {
                Eyebrow("03 / CONNECTED LISTENING", color = Cyan)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Cyan, modifier = Modifier.size(22.dp))
                    Text("Your Spotify connection", style = MaterialTheme.typography.titleMedium)
                }
                Text("Keep your music close. Connect through Spotify with your existing account.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                TextButton(onClick = { actions.onOpenSpotify("") }, contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("open_spotify")) {
                    Text("Spotify connection", color = Lime)
                    Icon(Icons.Rounded.ArrowOutward, null, modifier = Modifier.padding(start = 8.dp).size(16.dp), tint = Lime)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(track: Track, animate: Boolean, onPlay: () -> Unit) {
    Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().heightIn(min = 245.dp).clip(CyberPanel)
        .background(Panel).border(1.dp, Cyan.copy(.4f), CyberPanel).clickable(onClick = onPlay).testTag("hero_play")) {
        CyberCity(Modifier.matchParentSize(), motionPhase(animate, 24000))
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Ink.copy(.83f), Ink.copy(.40f), Color.Transparent))))
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(5.dp).background(Cyan, CircleShape))
                Eyebrow("FEATURED TRANSMISSION", color = Cyan)
            }
            Spacer(Modifier.height(24.dp))
            Text("LIGHTS LOW.\nVOLUME UP.", fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, letterSpacing = (-.7).sp)
            Text("Original sounds. Neon nights.", color = Milk.copy(.75f), fontSize = 11.sp, modifier = Modifier.padding(top = 9.dp))
            Spacer(Modifier.height(27.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Milk)
                    Text("START THE SESSION / ${timeLabel(track.durationMs)}", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Cyan, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.size(49.dp).background(Lime, CyberSmall), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, "Play ${track.title}", tint = Ink, modifier = Modifier.size(29.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(state: PlayerUiState, actions: PlayerActions, query: String, onQuery: (String) -> Unit, onSettings: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val results = remember(state.tracks, query) { state.tracks.filter { "${it.title} ${it.artist} ${it.album} ${it.source}".contains(query.trim(), ignoreCase = true) } }
    val spotify = state.spotifyCatalog
    LaunchedEffect(query, spotify.authorized) { actions.onSearchSpotifyCatalog(query) }
    LazyColumn(Modifier.fillMaxSize().testTag("search_screen"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AppHeader(onSettings) }
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Eyebrow("FIND YOUR NEXT TRANSMISSION", color = Cyan)
                Text(if (query.isBlank()) "TUNE INTO\nSOMETHING." else "FIND YOUR SIGNAL.", style = if (query.isBlank()) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                OutlinedTextField(
                    value = query, onValueChange = onQuery,
                    placeholder = { Text("Search tracks, artists, albums…", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Lime) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
                    singleLine = true, shape = CyberSmall,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Panel, unfocusedContainerColor = Panel, unfocusedBorderColor = Hairline),
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("search_input"),
                )
                Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Light", "Night", "Focus").forEach { suggestion ->
                        FilterPill(suggestion, query.equals(suggestion, true)) { onQuery(suggestion) }
                    }
                }
            }
        }
        item { SectionHeading("Spotify catalogue", "LIVE / SPOTIFY WEB API") }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
                    .clip(CyberPanel).background(Panel).border(1.dp, Cyan.copy(.3f), CyberPanel)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).background(if (spotify.authorized) Cyan else Muted, CircleShape))
                    Text(if (spotify.authorized) "SPOTIFY CONNECTED" else "SPOTIFY CATALOGUE ACCESS", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                when {
                    !spotify.configured -> {
                        Text("Enter your public Spotify developer Client ID to activate catalogue search on this device. Guest listening always works.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("spotify_catalog_unconfigured"))
                        SpotifyClientIdEditor(spotify, actions.onSetSpotifyCatalogClientId, actions.onResetSpotifyCatalogClientId)
                        TextButton(onClick = { actions.onOpenSpotify(query.ifBlank { "" }) }, modifier = Modifier.testTag("spotify_catalog_open_external")) {
                            Text(if (query.isBlank()) "Spotify connection" else "Search in Spotify")
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.padding(start = 8.dp).size(16.dp))
                        }
                    }
                    !spotify.authorized -> {
                        Text("Browse real Spotify tracks, albums and artists here. Spotify authorization is optional and separate from guest listening.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = actions.onAuthorizeSpotifyCatalog, enabled = !spotify.authorizing,
                            modifier = Modifier.fillMaxWidth().testTag("spotify_catalog_connect"), shape = CyberSmall) {
                            if (spotify.authorizing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(if (spotify.authorizing) "Waiting for Spotify…" else "Connect Spotify catalogue")
                        }
                    }
                    query.isBlank() -> Text("Search above to explore Spotify's catalogue. Playback and availability remain with Spotify.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    spotify.loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Cyan)
                            Text("Searching Spotify…", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    spotify.error == null && spotify.results.isEmpty() && !spotify.hasMore -> Text("No Spotify results for this search.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    else -> Text("${spotify.results.size} catalogue results · track / album / artist", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (spotify.configured) {
                    SpotifyClientIdEditor(spotify, actions.onSetSpotifyCatalogClientId, actions.onResetSpotifyCatalogClientId)
                }
                spotify.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("spotify_catalog_error")) }
            }
        }
        if (spotify.authorized && query.isNotBlank()) {
            items(spotify.results, key = { "spotify_${it.kind.apiName}_${it.id}" }) { item ->
                SpotifyCatalogRow(item, state, actions)
            }
            if (spotify.hasMore || spotify.loadingMore) {
                item {
                    OutlinedButton(
                        onClick = actions.onLoadMoreSpotifyCatalog,
                        enabled = !spotify.loadingMore,
                        shape = CyberSmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 13.dp)
                            .testTag("spotify_catalog_load_more"),
                    ) {
                        if (spotify.loadingMore) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Cyan)
                        Text(if (spotify.loadingMore) " Loading more…" else "Load more Spotify results")
                    }
                }
            }
        }
        item { SectionHeading(if (query.isBlank()) "Your sound collection" else "On this device", "${results.size} LOCAL TRACK${if (results.size == 1) "" else "S"}") }
        if (results.isEmpty()) {
            item { EmptyState(Icons.Rounded.SearchOff, "Nothing on this frequency", "Try another title or artist, or import something you love.", "Import audio", actions.onImportAudio) }
        } else {
            items(results, key = { it.id }) { TrackRow(it, state, actions, results) }
        }
        if (query.isNotBlank()) {
            item {
                TextButton(onClick = { actions.onOpenSpotify(query) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).testTag("spotify_search")) {
                    Text("Search “$query” in Spotify", modifier = Modifier.weight(1f), maxLines = 2)
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SpotifyCatalogRow(item: SpotifyCatalogItem, state: PlayerUiState, actions: PlayerActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp)
            .clip(CyberSmall).background(Panel).border(1.dp, Hairline, CyberSmall)
            .clickable { actions.onSelectSpotifyResult(item) }
            .testTag("spotify_result_${item.kind.apiName}_${item.id}")
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        SpotifyCover(item.artworkUrl, item.title, Modifier.size(66.dp).clip(CyberSmall))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("SPOTIFY · ${item.kind.apiName.uppercase()} · ${if (state.spotifyCatalog.authorized) "OPEN / PLAY" else "OPEN"}", color = Cyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Icon(if (item.kind.apiName == "track") Icons.Rounded.PlayArrow else Icons.Rounded.ArrowOutward,
            "Open ${item.title} on Spotify", tint = Lime, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun LibraryScreen(state: PlayerUiState, actions: PlayerActions, onAddSource: () -> Unit, onSettings: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val tracks = remember(state.tracks, state.favorites, filter) {
        when (filter) {
            "Favourites" -> state.tracks.filter { it.id in state.favorites }
            "Offline" -> state.tracks.filter { it.isOffline }
            "Local" -> state.tracks.filter { it.streamUri.startsWith("content:") }
            else -> state.tracks
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("library_screen"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AppHeader(onSettings) }
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Eyebrow("YOUR PERSONAL ARCHIVE", color = Cyan)
                Text("KEEP IT\nON REPEAT.", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                Row(Modifier.padding(top = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryAction("Import audio", Icons.Rounded.Add, actions.onImportAudio, Modifier.weight(1f).testTag("import_audio"), true)
                    LibraryAction("Add stream", Icons.Rounded.Link, onAddSource, Modifier.weight(1f).testTag("add_stream"), false)
                }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp).clip(CyberSmall).background(Panel).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(state.tracks.count { it.isOffline }.toString().padStart(2, '0'), color = Cyan, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
                        Eyebrow("OFFLINE READY", color = Muted)
                    }
                    Box(Modifier.width(1.dp).height(43.dp).background(Hairline))
                    Column(Modifier.weight(1f)) {
                        Text(state.tracks.count { it.id in state.favorites }.toString().padStart(2, '0'), color = Magenta, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
                        Eyebrow("FAVOURITES", color = Muted)
                    }
                }
                Row(Modifier.padding(top = 22.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Favourites", "Offline", "Local").forEach { label ->
                        FilterPill(label, filter == label, Modifier.testTag("filter_${label.lowercase()}")) { filter = label }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.onRefreshCatalog, enabled = !state.catalogLoading, modifier = Modifier.testTag("refresh_catalog")) {
                    if (state.catalogLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(17.dp))
                    Text("Refresh", modifier = Modifier.padding(start = 6.dp), fontSize = 12.sp)
                }
            }
        }
        if (tracks.isEmpty()) {
            item { EmptyState(if (filter == "Favourites") Icons.Rounded.FavoriteBorder else Icons.Rounded.LibraryMusic, "Make room for your favourites", if (filter == "Offline") "Download a studio track or import audio to listen without a connection." else "Keep the tracks you love here. Tap a heart or bring your own audio.", "Import audio", actions.onImportAudio) }
        } else {
            items(tracks, key = { it.id }) { TrackRow(it, state, actions, tracks, allowRemoval = filter == "Offline") }
        }
        item {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Headphones, null, tint = Muted, modifier = Modifier.size(17.dp))
                Text("No account needed. Your imports, favourites and saved audio stay on this device.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LibraryAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, primary: Boolean) {
    Row(modifier.clip(CyberSmall).background(if (primary) Lime else PanelRaised).border(1.dp, if (primary) Lime else Hairline, CyberSmall).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (primary) Ink else Milk, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (primary) Ink else Milk, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun TrackRow(track: Track, state: PlayerUiState, actions: PlayerActions, queue: List<Track>, allowRemoval: Boolean = false) {
    val selected = state.playback.track?.id == track.id
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(CyberSmall)
            .background(if (selected) Lime.copy(.075f) else Panel.copy(.4f))
            .border(1.dp, if (selected) Lime.copy(.28f) else Color.Transparent, CyberSmall)
            .clickable { actions.onPlay(track, queue) }.testTag("track_${track.id}").padding(start = 8.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AlbumArt(track, Modifier.size(52.dp).clip(CyberSmall))
            if (selected && state.playback.isPlaying) {
                Box(Modifier.size(52.dp).clip(CyberSmall).background(Ink.copy(.42f)), contentAlignment = Alignment.Center) {
                    Equalizer(!state.reduceMotion, Modifier.size(19.dp), Lime)
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(track.title, color = if (selected) Lime else Milk, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (track.isOffline) Icon(Icons.Rounded.CheckCircle, "Available offline", tint = Muted, modifier = Modifier.size(12.dp).padding(end = 2.dp))
                Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (track.durationMs > 0L) Text(" · ${timeLabel(track.durationMs)}", color = Muted, fontSize = 11.sp)
            }
        }
        IconButton(onClick = { actions.onToggleFavorite(track) }, modifier = Modifier.size(48.dp).testTag("favorite_${track.id}")) {
            Icon(if (track.id in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (track.id in state.favorites) "Remove ${track.title} from favourites" else "Favourite ${track.title}", tint = if (track.id in state.favorites) Magenta else Muted, modifier = Modifier.size(20.dp))
        }
        if (allowRemoval && track.offlinePath != null) {
            RoundIcon(Icons.Rounded.DeleteOutline, "Remove download of ${track.title}", { actions.onRemoveDownload(track) }, Modifier.testTag("remove_download_${track.id}"))
        } else if (track.downloadable) {
            DownloadButton(track, state.downloads[track.id], actions.onDownload)
        }
    }
}

@Composable
private fun DownloadButton(track: Track, download: DownloadState?, onDownload: (Track) -> Unit, modifier: Modifier = Modifier) {
    val active = download?.isActive == true
    IconButton(onClick = { onDownload(track) }, enabled = !active && track.offlinePath == null, modifier = modifier.size(48.dp).testTag("download_${track.id}")) {
        when {
            active -> Box(Modifier.size(21.dp).semantics { contentDescription = "Downloading ${track.title}" }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { download?.progress?.coerceIn(0f, 1f) ?: 0f }, strokeWidth = 2.dp, modifier = Modifier.fillMaxSize(), color = Lime)
            }
            track.offlinePath != null -> Icon(Icons.Rounded.DownloadDone, "${track.title} downloaded", tint = Lime, modifier = Modifier.size(21.dp))
            download?.error != null -> Icon(Icons.Rounded.Refresh, "Retry download of ${track.title}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(21.dp))
            else -> Icon(Icons.Rounded.ArrowDownward, "Download ${track.title}", tint = Muted, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun MiniPlayer(state: PlayerUiState, actions: PlayerActions, onExpand: () -> Unit) {
    val track = state.playback.track ?: return
    Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp).fillMaxWidth().clip(CyberSmall).background(PanelRaised).border(1.dp, Cyan.copy(.4f), CyberSmall).testTag("mini_player")) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(CyberSmall).clickable(onClick = onExpand).testTag("expand_player"), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(track, Modifier.size(44.dp).clip(CyberSmall))
                Column(Modifier.padding(start = 11.dp)) {
                    Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.playback.isBuffering) "Finding the signal…" else track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RoundIcon(if (state.playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playback.isPlaying) "Pause" else "Play", actions.onTogglePlay, Modifier.testTag("mini_play_pause"), tint = Lime)
            RoundIcon(Icons.Rounded.SkipNext, "Next track", actions.onNext, Modifier.testTag("mini_next"))
        }
        val progress = if (state.playback.durationMs > 0L) state.playback.positionMs.toFloat() / state.playback.durationMs else 0f
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Lime, trackColor = Hairline)
    }
}

@Composable
private fun NowPlaying(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    val playingTrack = state.playback.track ?: return
    val track = state.tracks.firstOrNull { it.id == playingTrack.id } ?: playingTrack
    val localSource = track.streamUri.startsWith("content:")
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val accent = cyberAccent(track)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("now_playing"), color = Ink) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxWidth().height(500.dp)) {
                    drawRect(Brush.radialGradient(listOf(accent.copy(.13f), Color.Transparent), Offset(size.width * .5f, size.height * .35f), size.width * .9f))
                }
                Column(Modifier.safeDrawingPadding().fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundIcon(Icons.Rounded.KeyboardArrowDown, "Close player", onDismiss, Modifier.testTag("close_player"))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Eyebrow(if (showQueue) "UP NEXT / QUEUE" else "NOW PLAYING", color = Cyan)
                            Text(if (showQueue) "${state.playback.queue.size} tracks in your queue" else track.album.ifBlank { track.source }, fontSize = 11.sp, color = Milk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                        RoundIcon(Icons.AutoMirrored.Rounded.QueueMusic, "${if (showQueue) "Hide" else "Show"} queue", { showQueue = !showQueue }, Modifier.testTag("toggle_queue"), tint = if (showQueue) Lime else Milk)
                    }
                    if (showQueue) {
                        LazyColumn(Modifier.weight(1f).testTag("queue_list"), contentPadding = PaddingValues(vertical = 16.dp)) {
                            items(state.playback.queue, key = { it.id }) { TrackRow(it, state, actions, state.playback.queue) }
                        }
                    } else {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            // Measure the bounded viewport before scrolling; reserve room for transport.
                            val artworkSize = minOf(
                                (maxWidth - 48.dp).coerceAtLeast(112.dp),
                                maxHeight * .46f,
                                (maxHeight - 280.dp).coerceAtLeast(112.dp),
                                440.dp,
                            )
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.size(artworkSize), contentAlignment = Alignment.Center) {
                                val orbit = motionPhase(state.playback.isPlaying && !state.reduceMotion, 45000)
                                AlbumArt(track, Modifier.fillMaxSize().clip(CyberPanel).border(1.dp, accent.copy(.55f), CyberPanel), phase = orbit * 360f)
                                Box(Modifier.align(Alignment.TopStart).padding(18.dp).clip(CyberSmall).background(Ink.copy(.82f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(when { track.isOffline -> "OFFLINE READY"; localSource -> "LOCAL SOURCE"; else -> "ONLINE AUDIO" }, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp, color = Milk)
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("current_track_title"))
                                    Text(track.artist, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                                }
                                RoundIcon(if (track.id in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "${if (track.id in state.favorites) "Remove from" else "Add to"} favourites", { actions.onToggleFavorite(track) }, Modifier.testTag("player_favorite"), tint = if (track.id in state.favorites) Magenta else Muted)
                            }
                            val duration = (state.playback.durationMs.takeIf { it > 0L } ?: track.durationMs).coerceAtLeast(1L)
                            var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
                            Slider(
                                value = seeking ?: state.playback.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                                onValueChange = { seeking = it },
                                onValueChangeFinished = { seeking?.let { actions.onSeek(it.toLong()) }; seeking = null },
                                valueRange = 0f..duration.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Hairline),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("seek_slider").semantics { contentDescription = "Playback position" },
                            )
                            Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(timeLabel(seeking?.toLong() ?: state.playback.positionMs), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
                                Text(timeLabel(duration), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                RoundIcon(Icons.Rounded.Shuffle, if (state.playback.shuffle) "Disable shuffle" else "Enable shuffle", actions.onToggleShuffle, Modifier.testTag("shuffle"), tint = if (state.playback.shuffle) Lime else Muted)
                                RoundIcon(Icons.Rounded.SkipPrevious, "Previous track", actions.onPrevious, Modifier.testTag("previous"), iconSize = 32)
                                Box(Modifier.size(72.dp).clip(CyberPanel).background(Lime).clickable(onClick = actions.onTogglePlay).testTag("play_pause").semantics { contentDescription = if (state.playback.isPlaying) "Pause" else "Play" }, contentAlignment = Alignment.Center) {
                                    if (state.playback.isBuffering) CircularProgressIndicator(color = Ink, strokeWidth = 2.dp, modifier = Modifier.size(32.dp)) else Icon(if (state.playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Ink, modifier = Modifier.size(35.dp))
                                }
                                RoundIcon(Icons.Rounded.SkipNext, "Next track", actions.onNext, Modifier.testTag("next"), iconSize = 32)
                                RoundIcon(Icons.Rounded.Repeat, if (state.playback.repeat) "Disable repeat" else "Enable repeat", actions.onToggleRepeat, Modifier.testTag("repeat"), tint = if (state.playback.repeat) Lime else Muted)
                            }
                            state.playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("playback_error")) }
                            Row(Modifier.fillMaxWidth().clip(CyberSmall).background(Panel).border(1.dp, Hairline, CyberSmall).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (track.isOffline) Icons.Rounded.OfflinePin else Icons.Rounded.GraphicEq, null, tint = Lime, modifier = Modifier.size(22.dp))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(when { track.isOffline -> "Take it anywhere"; localSource -> "Your own sound"; else -> "Keep the feeling" }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(when { track.isOffline -> "Available without a connection"; localSource -> "Played through your file provider"; track.downloadable -> "Save this track for offline listening"; else -> "Stream from your audio source" }, color = Muted, fontSize = 11.sp)
                                }
                                if (track.downloadable) DownloadButton(track, state.downloads[track.id], actions.onDownload)
                            }
                            state.downloads[track.id]?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
                            Text(track.source + if (track.license.isNotBlank()) " · ${track.license}" else "", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 18.dp, bottom = 24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Eyebrow("NIGHT SHIFT / PREFERENCES", color = Cyan); Text("Control room", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Set the pace of your listening space.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reduce motion", color = Milk, fontWeight = FontWeight.Medium)
                        Text("Pause decorative animations", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Switch(checked = state.reduceMotion, onCheckedChange = actions.onReduceMotion, modifier = Modifier.testTag("reduce_motion"))
                }
                HorizontalDivider(color = Hairline)
                Text("Your collection", color = Milk, fontWeight = FontWeight.SemiBold)
                Text("Studio tracks are original CC0 samples. Imported files and permitted HTTPS audio can play without an account. Downloads stay on this device.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text("Spotify listening uses its existing account and service availability.", style = MaterialTheme.typography.bodySmall, color = Muted)
                TextButton(onClick = { onDismiss(); actions.onOpenSpotify("") }, modifier = Modifier.testTag("settings_spotify")) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Text("Spotify connection", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_settings")) { Text("Done") } },
        containerColor = Panel,
    )
}

@Composable
private fun AddSourceDialog(actions: PlayerActions, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var permission by rememberSaveable { mutableStateOf(false) }
    val validUrl = remember(url) { runCatching { AudioUrlPolicy.validateHttps(url) }.isSuccess }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Eyebrow("LIBRARY / NEW SOURCE", color = Cyan); Text("Bring your sound", modifier = Modifier.padding(top = 8.dp)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Add a direct HTTPS audio link from a source you’re allowed to use.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, label = { Text("Track name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("stream_title"), shape = CyberSmall)
                OutlinedTextField(url, { url = it }, label = { Text("HTTPS audio URL") }, placeholder = { Text("https://example.com/audio.mp3") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = url.isNotBlank() && !validUrl, modifier = Modifier.fillMaxWidth().testTag("stream_url"), shape = CyberSmall)
                Row(Modifier.clip(CyberSmall).clickable { permission = !permission }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(permission, { permission = it }, modifier = Modifier.testTag("stream_permission"))
                    Text("I have permission to stream and download this audio.", style = MaterialTheme.typography.bodySmall, color = Milk)
                }
                Text("Spotify links and web pages are not direct audio links.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onAddStream(title.trim(), url.trim()); onDismiss() }, enabled = title.isNotBlank() && validUrl && permission, modifier = Modifier.testTag("confirm_add_stream")) { Text("Add to library") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Panel,
    )
}

@Composable
internal fun AlbumArt(track: Track, modifier: Modifier = Modifier, phase: Float = 0f) {
    CyberCover(track, modifier, phase)
}

@Composable
private fun motionPhase(enabled: Boolean, duration: Int): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "ambient motion")
    val value by transition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart), label = "orbit phase")
    return value
}

@Composable
private fun Equalizer(animate: Boolean, modifier: Modifier, color: Color) {
    val phase = motionPhase(animate, 1200)
    Canvas(modifier) {
        repeat(4) { i ->
            val height = size.height * (.3f + .65f * ((sin((phase * 2 * PI) + i * 1.4).toFloat() + 1f) / 2f))
            val x = size.width * (.125f + .25f * i)
            drawLine(color, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), size.width * .12f, StrokeCap.Round)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(subtitle, color = Cyan)
            Box(Modifier.padding(start = 12.dp).weight(1f).height(1.dp).background(Hairline))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 7.dp).semantics { heading() })
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Lime) {
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = modifier)
}

@Composable
private fun FilterPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(CyberSmall).background(if (selected) Lime else PanelRaised).border(1.dp, if (selected) Lime else Hairline, CyberSmall).clickable(onClick = onClick).semantics { this.selected = selected; role = Role.Tab }.padding(horizontal = 18.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, color = if (selected) Ink else Milk, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, tint: Color = Milk, iconSize: Int = 23) {
    IconButton(onClick, modifier.size(48.dp).then(if (filled) Modifier.background(Panel, CyberSmall).border(1.dp, Hairline, CyberSmall) else Modifier)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(PanelRaised, CyberPanel).border(1.dp, Cyan.copy(.4f), CyberPanel), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Lime, modifier = Modifier.size(30.dp)) }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp), color = Milk)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onAction, modifier = Modifier.padding(top = 10.dp)) { Text(action); Icon(Icons.Rounded.ArrowOutward, null, modifier = Modifier.padding(start = 8.dp).size(16.dp)) }
    }
}

internal fun timeLabel(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

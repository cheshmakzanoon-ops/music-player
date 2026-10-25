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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import io.github.musicplayer.model.DownloadState
import io.github.musicplayer.model.Track
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(
            Triple(Destination.HOME, "Discover", Icons.Rounded.Explore),
            Triple(Destination.SEARCH, "Search", Icons.Rounded.Search),
            Triple(Destination.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
        ).forEach { (destination, label, icon) ->
            val active = selected == destination
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(destination) }.testTag("nav_${destination.name.lowercase()}").semantics { this.selected = active; role = Role.Tab }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(icon, contentDescription = null, tint = if (active) Lime else Muted, modifier = Modifier.size(23.dp))
                Text(label, color = if (active) Milk else Muted, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun AppHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Lime), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Ink, modifier = Modifier.size(19.dp))
            }
            Text("music player", fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontSize = 19.sp)
            Box(Modifier.padding(start = 2.dp).size(5.dp).background(Lime, CircleShape))
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
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 26.dp)) {
                Eyebrow("GOOD MUSIC. NO DISTRACTIONS.")
                Spacer(Modifier.height(12.dp))
                Text("Find your\nfrequency.", style = MaterialTheme.typography.displayLarge, modifier = Modifier.semantics { heading() })
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
            Row(Modifier.padding(horizontal = 24.dp, vertical = 28.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("The studio sessions", style = MaterialTheme.typography.titleLarge)
                    Text("Original sounds. Yours to explore.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Text(studioTracks.size.toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, color = Lime, fontSize = 14.sp)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(studioTracks.take(6), key = { it.id }) { track ->
                    Column(Modifier.width(146.dp).clip(RoundedCornerShape(18.dp)).clickable { actions.onPlay(track, studioTracks) }.testTag("album_${track.id}")) {
                        Box {
                            AlbumArt(track, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
                            Box(Modifier.padding(10.dp).align(Alignment.BottomEnd).size(32.dp).background(Ink.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Milk, modifier = Modifier.size(20.dp))
                            }
                        }
                        Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 11.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { SectionHeading("Press play", "EVERY DETAIL, EVERY NOTE", Modifier.padding(top = 30.dp)) }
        items(state.tracks, key = { "list_${it.id}" }) { track ->
            TrackRow(track, state, actions, state.tracks)
        }
        item {
            Column(Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Lilac, modifier = Modifier.size(22.dp))
                    Text("Your Spotify, a tap away", style = MaterialTheme.typography.titleMedium)
                }
                Text("Open the official app for its catalogue and your account.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                TextButton(onClick = { actions.onOpenSpotify("") }, contentPadding = PaddingValues(0.dp), modifier = Modifier.testTag("open_spotify")) {
                    Text("Open Spotify", color = Milk)
                    Icon(Icons.Rounded.ArrowOutward, null, modifier = Modifier.padding(start = 8.dp).size(16.dp), tint = Lime)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(track: Track, animate: Boolean, onPlay: () -> Unit) {
    Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().heightIn(min = 238.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF273125)).clickable(onClick = onPlay).testTag("hero_play")) {
        val phase = motionPhase(animate, 24000)
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF35482C), Color(0xFF172219))))
            val center = Offset(size.width * .88f, size.height * .42f)
            val radius = size.width * .39f
            drawCircle(Brush.radialGradient(listOf(Lime.copy(.25f), Color.Transparent), center, radius * 1.5f), radius * 1.5f, center)
            rotate(phase * 20f, center) {
                repeat(18) { index ->
                    val r = radius * (0.18f + index * .047f)
                    drawOval(Lime.copy(alpha = .11f + index * .012f), Offset(center.x - r, center.y - r * .6f), Size(r * 2, r * 1.2f), style = Stroke(1.6f))
                }
                drawCircle(Lime, 4f, Offset(center.x + radius * .65f, center.y - radius * .4f))
            }
        }
        Column(Modifier.fillMaxWidth().padding(23.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(5.dp).background(Lime, CircleShape))
                Text("THE LISTENING ROOM", fontFamily = FontFamily.Monospace, color = Lime, fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(15.dp))
            Text("Less noise.\nMore feeling.", fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Start with ${track.title}", fontSize = 12.sp, color = Milk)
                    Text("Original studio samples", fontSize = 11.sp, color = Milk.copy(.6f), modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(48.dp).background(Lime, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, "Play ${track.title}", tint = Ink, modifier = Modifier.size(28.dp))
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
    LazyColumn(Modifier.fillMaxSize().testTag("search_screen"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AppHeader(onSettings) }
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Eyebrow("FOLLOW YOUR CURIOSITY")
                Text(if (query.isBlank()) "On your\nwavelength." else "Find your next.", style = if (query.isBlank()) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                OutlinedTextField(
                    value = query, onValueChange = onQuery,
                    placeholder = { Text("Tracks, artists, a feeling…", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Lime) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
                    singleLine = true, shape = RoundedCornerShape(18.dp),
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
        item { SectionHeading(if (query.isBlank()) "Your sound collection" else "Search results", "${results.size} TRACK${if (results.size == 1) "" else "S"}") }
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
                Eyebrow("COLLECT WHAT MOVES YOU")
                Text("Your music.\nYour space.", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                Row(Modifier.padding(top = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryAction("Import audio", Icons.Rounded.Add, actions.onImportAudio, Modifier.weight(1f).testTag("import_audio"), true)
                    LibraryAction("Add stream", Icons.Rounded.Link, onAddSource, Modifier.weight(1f).testTag("add_stream"), false)
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
                Text("${tracks.size} tracks", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(if (primary) Lime else PanelRaised).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (primary) Ink else Milk, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (primary) Ink else Milk, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun TrackRow(track: Track, state: PlayerUiState, actions: PlayerActions, queue: List<Track>, allowRemoval: Boolean = false) {
    val selected = state.playback.track?.id == track.id
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) Lime.copy(.065f) else Color.Transparent)
            .clickable { actions.onPlay(track, queue) }.testTag("track_${track.id}").padding(start = 8.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AlbumArt(track, Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)))
            if (selected && state.playback.isPlaying) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(Ink.copy(.42f)), contentAlignment = Alignment.Center) {
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
            Icon(if (track.id in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (track.id in state.favorites) "Remove ${track.title} from favourites" else "Favourite ${track.title}", tint = if (track.id in state.favorites) Lime else Muted, modifier = Modifier.size(20.dp))
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
    Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PanelRaised).border(1.dp, Hairline, RoundedCornerShape(18.dp)).testTag("mini_player")) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onExpand).testTag("expand_player"), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(track, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
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
    val accent = Color(track.accent)
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
                            Eyebrow(if (showQueue) "UP NEXT" else "NOW PLAYING")
                            Text(if (showQueue) "${state.playback.queue.size} tracks in your queue" else track.album.ifBlank { track.source }, fontSize = 11.sp, color = Milk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                        RoundIcon(Icons.AutoMirrored.Rounded.QueueMusic, "${if (showQueue) "Hide" else "Show"} queue", { showQueue = !showQueue }, Modifier.testTag("toggle_queue"), tint = if (showQueue) Lime else Milk)
                    }
                    if (showQueue) {
                        LazyColumn(Modifier.weight(1f).testTag("queue_list"), contentPadding = PaddingValues(vertical = 16.dp)) {
                            items(state.playback.queue, key = { it.id }) { TrackRow(it, state, actions, state.playback.queue) }
                        }
                    } else {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(24.dp))
                            Box(Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                                val orbit = motionPhase(state.playback.isPlaying && !state.reduceMotion, 45000)
                                AlbumArt(track, Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp)), phase = orbit * 360f)
                                Box(Modifier.align(Alignment.TopStart).padding(18.dp).clip(RoundedCornerShape(20.dp)).background(Ink.copy(.65f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(when { track.isOffline -> "OFFLINE READY"; localSource -> "LOCAL SOURCE"; else -> "STEREO / ONLINE" }, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp, color = Milk)
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("current_track_title"))
                                    Text(track.artist, color = Muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 7.dp))
                                }
                                RoundIcon(if (track.id in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "${if (track.id in state.favorites) "Remove from" else "Add to"} favourites", { actions.onToggleFavorite(track) }, Modifier.testTag("player_favorite"), tint = if (track.id in state.favorites) Lime else Muted)
                            }
                            val duration = (state.playback.durationMs.takeIf { it > 0L } ?: track.durationMs).coerceAtLeast(1L)
                            var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
                            Slider(
                                value = seeking ?: state.playback.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                                onValueChange = { seeking = it },
                                onValueChangeFinished = { seeking?.let { actions.onSeek(it.toLong()) }; seeking = null },
                                valueRange = 0f..duration.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Hairline),
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).testTag("seek_slider").semantics { contentDescription = "Playback position" },
                            )
                            Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(timeLabel(seeking?.toLong() ?: state.playback.positionMs), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                Text(timeLabel(duration), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                RoundIcon(Icons.Rounded.Shuffle, if (state.playback.shuffle) "Disable shuffle" else "Enable shuffle", actions.onToggleShuffle, Modifier.testTag("shuffle"), tint = if (state.playback.shuffle) Lime else Muted)
                                RoundIcon(Icons.Rounded.SkipPrevious, "Previous track", actions.onPrevious, Modifier.testTag("previous"), iconSize = 32)
                                Box(Modifier.size(76.dp).clip(CircleShape).background(Lime).clickable(onClick = actions.onTogglePlay).testTag("play_pause").semantics { contentDescription = if (state.playback.isPlaying) "Pause" else "Play" }, contentAlignment = Alignment.Center) {
                                    if (state.playback.isBuffering) CircularProgressIndicator(color = Ink, strokeWidth = 2.dp, modifier = Modifier.size(32.dp)) else Icon(if (state.playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Ink, modifier = Modifier.size(35.dp))
                                }
                                RoundIcon(Icons.Rounded.SkipNext, "Next track", actions.onNext, Modifier.testTag("next"), iconSize = 32)
                                RoundIcon(Icons.Rounded.Repeat, if (state.playback.repeat) "Disable repeat" else "Enable repeat", actions.onToggleRepeat, Modifier.testTag("repeat"), tint = if (state.playback.repeat) Lime else Muted)
                            }
                            state.playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("playback_error")) }
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun SettingsDialog(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make yourself at home", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("A quieter interface, if that’s your thing.", style = MaterialTheme.typography.bodyMedium, color = Muted)
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
                Text("Spotify opens separately and follows its account, subscription and regional availability requirements.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        containerColor = Panel,
    )
}

@Composable
private fun AddSourceDialog(actions: PlayerActions, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var permission by rememberSaveable { mutableStateOf(false) }
    val validUrl = runCatching { java.net.URI(url.trim()).let { it.scheme.equals("https", true) && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bring your own sound") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Add a direct HTTPS audio link from a source you’re allowed to use.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, label = { Text("Track name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("stream_title"), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(url, { url = it }, label = { Text("HTTPS audio URL") }, placeholder = { Text("https://example.com/audio.mp3") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = url.isNotBlank() && !validUrl, modifier = Modifier.fillMaxWidth().testTag("stream_url"), shape = RoundedCornerShape(12.dp))
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { permission = !permission }, verticalAlignment = Alignment.CenterVertically) {
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
    val accent = Color(track.accent)
    val seed = track.artworkSeed
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.linearGradient(listOf(accent.copy(alpha = .8f), accent.copy(alpha = .2f), Color(0xFF11150F)), start = Offset(w, 0f), end = Offset(0f, h)))
        val center = Offset(w * .52f, h * .5f)
        when ((seed % 3 + 3) % 3) {
            0 -> {
                drawCircle(Brush.radialGradient(listOf(accent.copy(.7f), Color.Transparent), center, w * .5f), w * .5f, center)
                rotate(-28f + phase * .12f, center) {
                    repeat(26) { i ->
                        val r = w * (.13f + i * .012f)
                        drawOval(Color(0xFF182017).copy(alpha = .38f), Offset(center.x - r, center.y - r * .65f), Size(r * 2, r * 1.3f), style = Stroke(w * .0035f))
                    }
                    drawOval(Milk.copy(.7f), Offset(w * .22f, h * .31f), Size(w * .6f, h * .39f), style = Stroke(w * .006f))
                }
            }
            1 -> {
                repeat(22) { i ->
                    val path = Path()
                    val y = h * .22f + i * h * .026f
                    path.moveTo(-w * .1f, y)
                    path.cubicTo(w * .24f, y - h * .38f, w * .54f, y + h * .42f, w * 1.1f, y - h * .04f)
                    drawPath(path, if (i % 4 == 0) Milk.copy(.75f) else Ink.copy(.3f), style = Stroke(w * .007f, cap = StrokeCap.Round))
                }
            }
            else -> {
                rotate(35f + phase * .06f, center) {
                    repeat(9) { i ->
                        val length = w * (.18f + i * .071f)
                        drawRoundRect(if (i % 2 == 0) Ink.copy(.55f) else Milk.copy(.32f), Offset(center.x - length / 2, center.y - length / 2), Size(length, length), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f), style = Stroke(w * .016f))
                    }
                }
            }
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(.25f))))
        drawLine(Milk.copy(.4f), Offset(w * .08f, h * .9f), Offset(w * .22f, h * .9f), w * .003f)
        drawCircle(Milk.copy(.7f), w * .009f, Offset(w * .88f, h * .11f))
    }
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
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Eyebrow(subtitle, Modifier.padding(top = 5.dp), Muted)
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Lime) {
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = modifier)
}

@Composable
private fun FilterPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(if (selected) Lime else PanelRaised).clickable(onClick = onClick).semantics { this.selected = selected; role = Role.Tab }.padding(horizontal = 18.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, color = if (selected) Ink else Milk, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, tint: Color = Milk, iconSize: Int = 23) {
    IconButton(onClick, modifier.size(48.dp).then(if (filled) Modifier.background(Panel, CircleShape) else Modifier)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(PanelRaised, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Lime, modifier = Modifier.size(30.dp)) }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp), color = Milk)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onAction, modifier = Modifier.padding(top = 10.dp)) { Text(action); Icon(Icons.Rounded.ArrowOutward, null, modifier = Modifier.padding(start = 8.dp).size(16.dp)) }
    }
}

internal fun timeLabel(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

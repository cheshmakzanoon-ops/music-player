package io.github.musicplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.musicplayer.ui.MusicPlayerApp
import io.github.musicplayer.ui.PlayerActions
import io.github.musicplayer.ui.PlayerUiState
import io.github.musicplayer.ui.SpotifyPanel

class MainActivity : ComponentActivity() {
    private val model: MusicViewModel by lazy { ViewModelProvider(this)[MusicViewModel::class.java] }

    override fun onStart() {
        super.onStart()
        model.spotify.onForeground(this)
    }

    override fun onStop() {
        model.spotify.onBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 12, 19)),
        )
        setContent {
            var spotifyPanel by rememberSaveable { mutableStateOf(false) }
            val tracks by model.repository.tracks.collectAsStateWithLifecycle()
            val favorites by model.repository.favorites.collectAsStateWithLifecycle()
            val downloads by model.repository.downloads.collectAsStateWithLifecycle()
            val loading by model.repository.refreshing.collectAsStateWithLifecycle()
            val libraryReady by model.repository.ready.collectAsStateWithLifecycle()
            val repositoryError by model.repository.error.collectAsStateWithLifecycle()
            val playback by model.player.state.collectAsStateWithLifecycle()
            val reduceMotion by model.reduceMotion.collectAsStateWithLifecycle()
            val message by model.message.collectAsStateWithLifecycle()
            val spotify by model.spotify.state.collectAsStateWithLifecycle()
            val spotifyCatalog by model.spotifyCatalog.state.collectAsStateWithLifecycle()
            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                uris.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) {
                        // The picker still grants temporary access. Imports are copied to private storage.
                    }
                }
                model.importFiles(uris)
            }

            MusicPlayerApp(
                state = PlayerUiState(
                    tracks = tracks,
                    playback = playback,
                    favorites = favorites,
                    downloads = downloads,
                    catalogLoading = loading || !libraryReady,
                    message = message ?: repositoryError ?: playback.error,
                    reduceMotion = reduceMotion,
                    spotifyCatalog = spotifyCatalog,
                ),
                actions = PlayerActions(
                    onPlay = { track, queue ->
                        model.spotify.pause()
                        model.player.play(track, queue)
                    },
                    onTogglePlay = {
                        if (!playback.isPlaying) model.spotify.pause()
                        model.player.togglePlay()
                    },
                    onNext = model.player::next,
                    onPrevious = model.player::previous,
                    onSeek = model.player::seekTo,
                    onToggleFavorite = { model.repository.toggleFavorite(it.id) },
                    onDownload = model.repository::download,
                    onRemoveDownload = model.repository::removeDownload,
                    onToggleShuffle = model.player::toggleShuffle,
                    onToggleRepeat = model.player::toggleRepeat,
                    onImportAudio = { filePicker.launch(arrayOf("audio/*")) },
                    onAddStream = { title, url -> model.repository.addStream(title, url, downloadAllowed = true) },
                    onRefreshCatalog = model.repository::refreshCatalog,
                    onOpenSpotify = { query ->
                        if (query.isBlank()) spotifyPanel = true else openSpotify(query, model)
                    },
                    onAuthorizeSpotifyCatalog = { authorizeSpotifyCatalog(model) },
                    onDisconnectSpotifyCatalog = model.spotifyCatalog::disconnect,
                    onSetSpotifyCatalogClientId = { model.spotifyCatalog.setClientId(it) },
                    onResetSpotifyCatalogClientId = { model.spotifyCatalog.resetClientId() },
                    onSearchSpotifyCatalog = model.spotifyCatalog::search,
                    onLoadMoreSpotifyCatalog = model.spotifyCatalog::loadMore,
                    onSelectSpotifyResult = { item ->
                        if (spotify.connected) {
                            model.player.pause()
                            model.spotify.play(item.uri)
                        } else {
                            openSpotifyResult(item.spotifyUrl, model)
                        }
                    },
                    onReduceMotion = model::setReduceMotion,
                    onDismissMessage = model::dismissMessage,
                ),
            )
            if (spotifyPanel) {
                SpotifyPanel(
                    state = spotify,
                    catalog = spotifyCatalog,
                    onClose = { spotifyPanel = false },
                    onConnect = {
                        model.player.pause()
                        model.spotify.connect(this)
                    },
                    onDisconnect = model.spotify::disconnect,
                    onAuthorizeCatalog = { authorizeSpotifyCatalog(model) },
                    onDisconnectCatalog = model.spotifyCatalog::disconnect,
                    onSetCatalogClientId = { model.spotifyCatalog.setClientId(it) },
                    onResetCatalogClientId = { model.spotifyCatalog.resetClientId() },
                    onOpenSpotify = { openSpotify("", model) },
                    onPlayContent = { input ->
                        model.player.pause()
                        model.spotify.play(input)
                    },
                    onTogglePlay = {
                        if (!spotify.isPlaying) model.player.pause()
                        model.spotify.togglePlay()
                    },
                    onNext = model.spotify::next,
                    onPrevious = model.spotify::previous,
                    onSeek = model.spotify::seekTo,
                    onShuffle = model.spotify::toggleShuffle,
                    onRepeat = model.spotify::toggleRepeat,
                )
            }
        }
    }

    /** Public content linking: Spotify owns login, catalogue access, playback and downloads. */
    private fun openSpotify(query: String, model: MusicViewModel) {
        val url = if (query.isBlank()) "https://open.spotify.com/" else "https://open.spotify.com/search/${Uri.encode(query)}"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            model.showMessage("Install Spotify or a browser to open Spotify links.")
        }
    }

    private fun openSpotifyResult(url: String, model: MusicViewModel) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            model.showMessage("Install Spotify or a browser to open this Spotify result.")
        }
    }

    private fun authorizeSpotifyCatalog(model: MusicViewModel) {
        model.spotifyCatalog.authorize { url ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}

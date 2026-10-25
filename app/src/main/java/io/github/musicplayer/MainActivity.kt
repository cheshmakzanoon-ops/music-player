package io.github.musicplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.musicplayer.ui.MusicPlayerApp
import io.github.musicplayer.ui.PlayerActions
import io.github.musicplayer.ui.PlayerUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: MusicViewModel = viewModel()
            val tracks by model.repository.tracks.collectAsStateWithLifecycle()
            val favorites by model.repository.favorites.collectAsStateWithLifecycle()
            val downloads by model.repository.downloads.collectAsStateWithLifecycle()
            val loading by model.repository.refreshing.collectAsStateWithLifecycle()
            val repositoryError by model.repository.error.collectAsStateWithLifecycle()
            val playback by model.player.state.collectAsStateWithLifecycle()
            val reduceMotion by model.reduceMotion.collectAsStateWithLifecycle()
            val message by model.message.collectAsStateWithLifecycle()
            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                val readable = uris.filter { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        true
                    } catch (_: SecurityException) {
                        model.showMessage("That provider could not grant lasting access. Choose an audio file saved on your device.")
                        false
                    }
                }
                model.importFiles(readable)
            }

            MusicPlayerApp(
                state = PlayerUiState(
                    tracks = tracks,
                    playback = playback,
                    favorites = favorites,
                    downloads = downloads,
                    catalogLoading = loading,
                    message = message ?: repositoryError ?: playback.error,
                    reduceMotion = reduceMotion,
                ),
                actions = PlayerActions(
                    onPlay = model.player::play,
                    onTogglePlay = model.player::togglePlay,
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
                    onOpenSpotify = { query -> openSpotify(query, model) },
                    onReduceMotion = model::setReduceMotion,
                    onDismissMessage = model::dismissMessage,
                ),
            )
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
}

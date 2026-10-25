package io.github.musicplayer.ui

import io.github.musicplayer.model.DownloadState
import io.github.musicplayer.model.PlaybackState
import io.github.musicplayer.model.Track
import io.github.musicplayer.spotify.SpotifyCatalogItem
import io.github.musicplayer.spotify.SpotifyCatalogState

data class PlayerUiState(
    val tracks: List<Track> = emptyList(),
    val playback: PlaybackState = PlaybackState(),
    val favorites: Set<String> = emptySet(),
    val downloads: Map<String, DownloadState> = emptyMap(),
    val catalogLoading: Boolean = false,
    val message: String? = null,
    val reduceMotion: Boolean = false,
    val spotifyCatalog: SpotifyCatalogState = SpotifyCatalogState(),
)

data class PlayerActions(
    val onPlay: (Track, List<Track>) -> Unit = { _, _ -> },
    val onTogglePlay: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onToggleFavorite: (Track) -> Unit = {},
    val onDownload: (Track) -> Unit = {},
    val onRemoveDownload: (Track) -> Unit = {},
    val onToggleShuffle: () -> Unit = {},
    val onToggleRepeat: () -> Unit = {},
    val onImportAudio: () -> Unit = {},
    val onAddStream: (String, String) -> Unit = { _, _ -> },
    val onRefreshCatalog: () -> Unit = {},
    val onOpenSpotify: (String) -> Unit = {},
    val onAuthorizeSpotifyCatalog: () -> Unit = {},
    val onDisconnectSpotifyCatalog: () -> Unit = {},
    val onSetSpotifyCatalogClientId: (String) -> Unit = {},
    val onResetSpotifyCatalogClientId: () -> Unit = {},
    val onSearchSpotifyCatalog: (String) -> Unit = {},
    val onSelectSpotifyResult: (SpotifyCatalogItem) -> Unit = {},
    val onReduceMotion: (Boolean) -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

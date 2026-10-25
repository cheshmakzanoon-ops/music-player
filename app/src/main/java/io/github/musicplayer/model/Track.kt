package io.github.musicplayer.model

/** Original, local, or explicitly permitted audio. Spotify media is never represented as a raw URL. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val streamUri: String,
    val accent: Long = 0xFFB6FF64,
    val artworkSeed: Int = 0,
    val downloadable: Boolean = false,
    val source: String = "Local file",
    val license: String = "",
    val offlinePath: String? = null,
) {
    val isOffline: Boolean get() = offlinePath != null || streamUri.startsWith("content:") || streamUri.startsWith("asset:")
    val playbackUri: String get() = offlinePath?.let { java.io.File(it).toURI().toString() } ?: streamUri
}

data class PlaybackState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val error: String? = null,
)

data class DownloadState(
    val trackId: String,
    val progress: Float = 0f,
    val isActive: Boolean = true,
    val error: String? = null,
)

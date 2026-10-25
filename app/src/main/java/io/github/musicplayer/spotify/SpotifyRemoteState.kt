package io.github.musicplayer.spotify

import android.graphics.Bitmap

data class SpotifyRemoteState(
    val configReady: Boolean = false,
    val installed: Boolean = false,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val canSeek: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canShuffle: Boolean = false,
    val canRepeat: Boolean = false,
    val canPlayOnDemand: Boolean? = null,
    val artwork: Bitmap? = null,
    val error: String? = null,
    val hasTrack: Boolean = false,
)

package io.github.musicplayer.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.musicplayer.data.TrackJson
import io.github.musicplayer.model.Track
import org.json.JSONObject

internal fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(playbackUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsPlayable(true)
            .setExtras(Bundle().apply { putString("original-track", TrackJson.encode(this@toMediaItem).toString()) })
            .build(),
    )
    .build()

internal fun MediaItem.toTrack(): Track = mediaMetadata.extras?.getString("original-track")?.let { value ->
    runCatching { TrackJson.decode(JSONObject(value)) }.getOrNull()
} ?: Track(
    id = mediaId,
    title = mediaMetadata.title?.toString() ?: "Audio",
    artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
    album = mediaMetadata.albumTitle?.toString().orEmpty(),
    streamUri = localConfiguration?.uri?.toString().orEmpty(),
)

package io.github.musicplayer.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import io.github.musicplayer.data.TrackJson
import io.github.musicplayer.model.Track
import org.json.JSONObject

@OptIn(UnstableApi::class)
internal fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(streamUri)
    .setCustomCacheKey(id)
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

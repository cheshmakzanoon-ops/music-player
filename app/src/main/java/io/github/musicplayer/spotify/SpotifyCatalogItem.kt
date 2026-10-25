package io.github.musicplayer.spotify

/** Public Spotify metadata, never a locally downloadable audio source. */
data class SpotifyCatalogItem(
    val id: String,
    val kind: SpotifyCatalogKind,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val durationMs: Long?,
) {
    val uri: String get() = "spotify:${kind.apiName}:$id"
    val spotifyUrl: String get() = "https://open.spotify.com/${kind.apiName}/$id"
}

enum class SpotifyCatalogKind(val apiName: String) {
    TRACK("track"), ALBUM("album"), ARTIST("artist");
}

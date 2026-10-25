package io.github.musicplayer.spotify

import java.net.URI

/** Normalizes public Spotify content links; never resolves arbitrary hosts or private endpoints. */
object SpotifyContentUri {
    private val content = Regex("(track|album|artist|playlist|episode):([A-Za-z0-9]{22})")
    private val locale = Regex("intl-[a-zA-Z]{2}(?:-[a-zA-Z]{2})?")

    fun normalize(value: String): String? {
        val input = value.trim()
        if (input.length !in 1..4096) return null
        if (input.startsWith("spotify:")) {
            return input.takeIf { content.matches(it.removePrefix("spotify:")) }
        }
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) || !uri.host.equals("open.spotify.com", true) ||
            uri.userInfo != null || uri.port != -1 || uri.fragment != null
        ) return null
        val path = uri.rawPath.orEmpty()
        // Reject encoded separators, extra empty segments and relative-path normalization tricks.
        if ('%' in path || !path.startsWith('/')) return null
        val parts = path.removePrefix("/").removeSuffix("/").split('/')
        val identifiers = if (parts.firstOrNull()?.matches(locale) == true) parts.drop(1) else parts
        if (identifiers.size != 2) return null
        return "${identifiers[0]}:${identifiers[1]}".takeIf(content::matches)?.let { "spotify:$it" }
    }
}

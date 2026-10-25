package io.github.musicplayer.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyContentUriTest {
    private val id = "4uLU6hMCjMI75M1A2tKUQC"

    @Test fun acceptsPublicContentIdentifiersAndShareLinks() {
        for (type in listOf("track", "album", "artist", "playlist", "episode")) {
            val expected = "spotify:$type:$id"
            assertEquals(expected, SpotifyContentUri.normalize(expected))
            assertEquals(expected, SpotifyContentUri.normalize("https://open.spotify.com/$type/$id?si=share-code"))
            assertEquals(expected, SpotifyContentUri.normalize(" https://open.spotify.com/intl-en/$type/$id/ "))
        }
    }

    @Test fun rejectsForeignHostsCredentialsSchemesAndMalformedPaths() {
        listOf(
            "http://open.spotify.com/track/$id", "https://open.spotify.com.evil.test/track/$id",
            "https://evil.test/track/$id", "https://user@open.spotify.com/track/$id",
            "https://open.spotify.com:443/track/$id", "https://open.spotify.com/track/$id#fragment",
            "https://open.spotify.com//track/$id", "https://open.spotify.com/track%2F$id",
            "https://open.spotify.com/intl-en/../track/$id", "https://open.spotify.com/track/$id/extra",
            "spotify:local:artist:album:track", "spotify:user:user:playlist:$id", "spotify:show:$id",
            "spotify:track:short", "spotify:track:${id.dropLast(1)}!", "javascript:alert(1)", "",
        ).forEach { assertNull(it, SpotifyContentUri.normalize(it)) }
    }
}

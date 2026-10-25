package io.github.musicplayer.spotify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyConfigurationTest {
    private val id = "0123456789abcdef0123456789abcdef"

    @Test fun acceptsPublicClientIdAndHttpsRedirect() {
        val configuration = SpotifyConfiguration(id, "https://music.example/spotify/callback")
        assertTrue(configuration.isConfigured)
        assertNull(configuration.configurationIssue)
    }

    @Test fun missingOrMalformedConfigurationRemainsDisabled() {
        for (configuration in listOf(
            SpotifyConfiguration("", ""), SpotifyConfiguration("placeholder", "https://music.example/callback"),
            SpotifyConfiguration(id, "http://music.example/callback"), SpotifyConfiguration(id, "musicplayer://callback"),
            SpotifyConfiguration(id, "https://user:secret@music.example/callback"),
            SpotifyConfiguration(id, "https://music.example/callback#fragment"),
            SpotifyConfiguration(id, "https://music.example:8443/callback"),
        )) {
            assertFalse(configuration.isConfigured)
            assertNotNull(configuration.configurationIssue)
        }
    }
}

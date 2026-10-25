package io.github.musicplayer.spotify

import java.net.URI

/** Public developer application settings. A client secret must never be shipped in the APK. */
data class SpotifyConfiguration(val clientId: String, val redirectUri: String) {
    val configurationIssue: String?
        get() {
            if (!clientId.trim().matches(Regex("[0-9a-fA-F]{32}"))) {
                return "Spotify connection has not been configured for this build."
            }
            val uri = runCatching { URI(redirectUri.trim()) }.getOrNull()
            if (uri == null || !uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
                uri.userInfo != null || uri.fragment != null || uri.port !in listOf(-1, 443)
            ) {
                return "Spotify connection needs a registered HTTPS redirect address."
            }
            return null
        }

    val isConfigured: Boolean get() = configurationIssue == null
}

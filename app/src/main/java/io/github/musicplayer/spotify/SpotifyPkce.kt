package io.github.musicplayer.spotify

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class SpotifyPkceSession(val verifier: String, val challenge: String, val state: String)

class SpotifyAuthorizationException(message: String) : Exception(message)

/** OAuth authorization-code + PKCE. No client secret and no Web API tokens in deep links. */
object SpotifyPkce {
    private val random = SecureRandom()

    fun validClientId(clientId: String): Boolean = clientId.matches(Regex("[0-9a-fA-F]{32}"))

    fun newSession(): SpotifyPkceSession {
        val verifier = randomBytes(64)
        val challenge = urlBase64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
        return SpotifyPkceSession(verifier, challenge, randomBytes(32))
    }

    fun authorizationUrl(clientId: String, redirectUri: String, session: SpotifyPkceSession): String {
        require(validClientId(clientId)) { "Configure a valid Spotify application client ID." }
        require(redirectUri.toHttpUrlOrNull()?.run { scheme == "http" && host == "127.0.0.1" && encodedPath == "/callback" } == true)
        return "https://accounts.spotify.com/authorize".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("code_challenge", session.challenge)
            .addQueryParameter("state", session.state)
            // Public catalogue metadata needs no private user-data scopes.
            .build().toString()
    }

    fun codeFromCallback(url: String, redirectUri: String, expectedState: String): String {
        val callback = url.toHttpUrlOrNull() ?: throw SpotifyAuthorizationException("Invalid Spotify response.")
        val redirect = redirectUri.toHttpUrlOrNull() ?: throw SpotifyAuthorizationException("Invalid redirect setup.")
        if (callback.scheme != redirect.scheme || callback.host != redirect.host ||
            callback.port != redirect.port || callback.encodedPath != redirect.encodedPath ||
            callback.fragment != null
        ) throw SpotifyAuthorizationException("Unexpected Spotify callback address.")
        val returnedState = callback.queryParameter("state") ?: ""
        if (!MessageDigest.isEqual(returnedState.toByteArray(StandardCharsets.UTF_8), expectedState.toByteArray(StandardCharsets.UTF_8)))
            throw SpotifyAuthorizationException("Spotify authorization state mismatch.")
        callback.queryParameter("error")?.let {
            throw SpotifyAuthorizationException(if (it == "access_denied") "Spotify authorization was declined." else "Spotify authorization failed.")
        }
        return callback.queryParameter("code")?.takeIf { it.isNotBlank() && it.length <= 2_048 }
            ?: throw SpotifyAuthorizationException("Spotify did not return an authorization code.")
    }

    private fun randomBytes(count: Int): String = ByteArray(count).also(random::nextBytes).let(::urlBase64)
    private fun urlBase64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

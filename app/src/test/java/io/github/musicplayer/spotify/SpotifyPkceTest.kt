package io.github.musicplayer.spotify

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class SpotifyPkceTest {
    private val clientId = "0123456789abcdef0123456789abcdef"
    private val redirect = "http://127.0.0.1:38191/callback"

    @Test fun authorizationCodeUsesStrongPkceStateAndExactLoopbackRedirect() {
        val session = SpotifyPkce.newSession()
        val again = SpotifyPkce.newSession()
        assertNotEquals(session.verifier, again.verifier)
        assertNotEquals(session.state, again.state)
        assertTrue(session.verifier.length in 43..128)
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(session.verifier.toByteArray(StandardCharsets.US_ASCII)),
        ), session.challenge)

        val authorize = SpotifyPkce.authorizationUrl(clientId, redirect, session).toHttpUrl()
        assertEquals("accounts.spotify.com", authorize.host)
        assertEquals("/authorize", authorize.encodedPath)
        assertEquals("code", authorize.queryParameter("response_type"))
        assertEquals(clientId, authorize.queryParameter("client_id"))
        assertEquals(redirect, authorize.queryParameter("redirect_uri"))
        assertEquals("S256", authorize.queryParameter("code_challenge_method"))
        assertEquals(session.challenge, authorize.queryParameter("code_challenge"))
        assertEquals(session.state, authorize.queryParameter("state"))
        assertNull(authorize.queryParameter("client_secret"))
        assertNull(authorize.queryParameter("scope"))
    }

    @Test fun callbackRequiresExactOriginPathStateAndOneCode() {
        val session = SpotifyPkce.newSession()
        val callback = "$redirect?state=${session.state}&code=good-code"
        assertEquals("good-code", SpotifyPkce.codeFromCallback(callback, redirect, session.state))
        for (bad in listOf(
            "$redirect?state=wrong&code=good-code",
            "http://127.0.0.1:38190/callback?state=${session.state}&code=good-code",
            "http://example.com/callback?state=${session.state}&code=good-code",
            "http://127.0.0.1:38191/other?state=${session.state}&code=good-code",
            "$redirect?state=${session.state}",
            "$redirect?state=${session.state}&error=access_denied",
        )) assertThrows(SpotifyAuthorizationException::class.java) {
            SpotifyPkce.codeFromCallback(bad, redirect, session.state)
        }
    }

    @Test fun unauthorizedRedirectSchemesAndInvalidClientIdsAreRejected() {
        val session = SpotifyPkce.newSession()
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyPkce.authorizationUrl("invalid", redirect, session)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyPkce.authorizationUrl(clientId, "https://example.com/callback", session)
        }
    }
}

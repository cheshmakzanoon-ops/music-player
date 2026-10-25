package io.github.musicplayer.spotify

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpotifyCatalogApiTest {
    private lateinit var apiServer: MockWebServer
    private lateinit var accountServer: MockWebServer
    private lateinit var api: SpotifyCatalogApi

    @Before fun setup() {
        apiServer = MockWebServer().also { it.start() }
        accountServer = MockWebServer().also { it.start() }
        api = SpotifyCatalogApi(apiBase = apiServer.url("/"), accountsBase = accountServer.url("/"), clock = { 10_000L })
    }

    @After fun teardown() {
        apiServer.shutdown()
        accountServer.shutdown()
    }

    @Test fun searchUsesRealV1EndpointBearerAndParses2026Metadata() = runBlocking {
        val trackId = "1A23456789012345678901"
        val albumId = "2A23456789012345678901"
        val artistId = "3A23456789012345678901"
        val response = """
            {
              "tracks":{"items":[{"id":"$trackId","name":"Neon Streets","artists":[{"name":"Arc Waves"}],"duration_ms":211000,
                "album":{"name":"After Dark","images":[{"url":"https://i.scdn.co/image/testimage"}]}}]},
              "albums":{"items":[{"id":"$albumId","name":"Night City","artists":[{"name":"Arc Waves"}],
                "images":[{"url":"https://evil.example/cover.png"}]}]},
              "artists":{"items":[{"id":"$artistId","name":"Arc Waves"}]}
            }
        """.trimIndent()
        apiServer.enqueue(MockResponse().setResponseCode(200).setBody(response))
        val items = api.search("access-token", "  jazz & funk  ")
        assertEquals(3, items.size)
        assertEquals(SpotifyCatalogKind.TRACK, items[0].kind)
        assertEquals("Neon Streets", items[0].title)
        assertEquals("Arc Waves", items[0].subtitle)
        assertEquals(211_000L, items[0].durationMs)
        assertEquals("https://i.scdn.co/image/testimage", items[0].artworkUrl)
        assertEquals("spotify:track:$trackId", items[0].uri)
        assertEquals("https://open.spotify.com/track/$trackId", items[0].spotifyUrl)
        assertEquals(SpotifyCatalogKind.ALBUM, items[1].kind)
        assertNull(items[1].artworkUrl)
        assertEquals(SpotifyCatalogKind.ARTIST, items[2].kind)
        val request = apiServer.takeRequest()
        assertEquals("/v1/search", request.requestUrl?.encodedPath)
        assertEquals("jazz & funk", request.requestUrl?.queryParameter("q"))
        assertEquals("track,album,artist", request.requestUrl?.queryParameter("type"))
        assertEquals("10", request.requestUrl?.queryParameter("limit"))
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertNull(request.requestUrl?.queryParameter("market"))
    }

    @Test fun tokenExchangeAndRefreshNeverRequireOrSendClientSecret() = runBlocking {
        accountServer.enqueue(MockResponse().setBody("""{"access_token":"token-1","token_type":"Bearer","expires_in":3600,"refresh_token":"refresh-1"}"""))
        accountServer.enqueue(MockResponse().setBody("""{"access_token":"token-2","token_type":"Bearer","expires_in":7200}"""))
        val clientId = "0123456789abcdef0123456789abcdef"
        val first = api.exchangeCode(clientId, "auth-code", "http://127.0.0.1:54321/callback", "verifier")
        assertEquals("token-1", first.accessToken)
        assertEquals("refresh-1", first.refreshToken)
        assertEquals(3_610_000L, first.expiresAtMs)
        val exchangeRequest = accountServer.takeRequest()
        assertEquals("/api/token", exchangeRequest.requestUrl?.encodedPath)
        assertTrue(exchangeRequest.body.readUtf8().contains("code_verifier=verifier"))
        assertEquals(null, exchangeRequest.getHeader("Authorization"))
        val refreshed = api.refresh(clientId, first.refreshToken!!)
        assertEquals("token-2", refreshed.accessToken)
        assertNull(refreshed.refreshToken)
        val refreshRequest = accountServer.takeRequest()
        assertTrue(refreshRequest.body.readUtf8().contains("grant_type=refresh_token"))
    }

    @Test fun searchPaginatesUsingSupportedOffsetsAndNeverFollowsUntrustedNextUrl() = runBlocking {
        val firstId = "1234567890123456789012"
        val secondId = "2234567890123456789012"
        apiServer.enqueue(MockResponse().setBody("""
            {"tracks":{"items":[{"id":"$firstId","name":"First"}],"next":"https://attacker.invalid/trick"},
             "albums":{"items":[],"next":null},"artists":{"items":[],"next":null}}
        """.trimIndent()))
        apiServer.enqueue(MockResponse().setBody("""
            {"tracks":{"items":[{"id":"$secondId","name":"Second"}],"next":null},
             "albums":{"items":[],"next":null},"artists":{"items":[],"next":null}}
        """.trimIndent()))

        val first = api.searchPage("test-access", "ambient", 0)
        assertEquals(listOf(firstId), first.items.map { it.id })
        assertTrue(first.hasMore)
        val second = api.searchPage("test-access", "ambient", 10)
        assertEquals(listOf(secondId), second.items.map { it.id })
        assertFalse(second.hasMore)
        val firstRequest = apiServer.takeRequest().requestUrl!!
        val secondRequest = apiServer.takeRequest().requestUrl!!
        assertNull(firstRequest.queryParameter("offset"))
        assertEquals("10", secondRequest.queryParameter("offset"))
        assertEquals("/v1/search", secondRequest.encodedPath)
        assertEquals("ambient", secondRequest.queryParameter("q"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { api.searchPage("test-access", "ambient", -10) }
        }
    }

    @Test fun forbiddenAndRateLimitedResponsesRemainVisibleErrors() = runBlocking {
        apiServer.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        val forbidden = assertThrows(SpotifyCatalogHttpException::class.java) {
            runBlocking { api.search("test", "night") }
        }
        assertEquals(403, forbidden.status)
        apiServer.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "12").setBody("{}"))
        val limited = assertThrows(SpotifyCatalogHttpException::class.java) {
            runBlocking { api.search("test", "night") }
        }
        assertEquals(429, limited.status)
        assertEquals(12, limited.retryAfterSeconds)
    }

    @Test fun missingAndInvalidCatalogueIdsNeverBecomeClickableLinks() = runBlocking {
        apiServer.enqueue(MockResponse().setBody("""{"tracks":{"items":[{"id":"../fake","name":"Invalid"},{"id":"1234567890123456789012","name":"Valid"}]}}"""))
        val items = api.search("test", "night")
        assertEquals(1, items.size)
        assertEquals("Valid", items.single().title)
    }
}

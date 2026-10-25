package io.github.musicplayer.spotify

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SpotifyLoopbackRedirectTest {
    @Test fun onlyValidStateCallbackCompletesBoundLoopbackListener() {
        SpotifyLoopbackRedirect().use { listener ->
            val session = SpotifyPkce.newSession()
            val worker = Executors.newSingleThreadExecutor()
            try {
                val future = worker.submit<String> { listener.awaitCode(session.state) }
                val invalid = URL("${listener.redirectUri}?state=wrong&code=forged").openConnection() as HttpURLConnection
                invalid.connectTimeout = 2_000
                invalid.readTimeout = 2_000
                assertEquals(400, invalid.responseCode)
                invalid.disconnect()
                val valid = URL("${listener.redirectUri}?state=${session.state}&code=real-code").openConnection() as HttpURLConnection
                valid.connectTimeout = 2_000
                valid.readTimeout = 2_000
                assertEquals(200, valid.responseCode)
                assertEquals("no-store", valid.getHeaderField("Cache-Control"))
                assertEquals("real-code", future.get(5, TimeUnit.SECONDS))
                valid.disconnect()
            } finally {
                worker.shutdownNow()
            }
        }
    }
}

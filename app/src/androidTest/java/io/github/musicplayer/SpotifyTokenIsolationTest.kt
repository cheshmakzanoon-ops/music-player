package io.github.musicplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.musicplayer.spotify.SpotifyCatalogToken
import io.github.musicplayer.spotify.SpotifyTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A developer-ID change must never attach one Spotify application's token to another. */
@RunWith(AndroidJUnit4::class)
class SpotifyTokenIsolationTest {
    @Test fun encryptedCredentialsAreBoundToTheirIssuingClientId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SpotifyTokenStore(context)
        val a = "0123456789abcdef0123456789abcdef"
        val b = "fedcba9876543210fedcba9876543210"
        store.clear()
        try {
            val token = SpotifyCatalogToken("fake-token-only-for-local-test", "fake-refresh", Long.MAX_VALUE)
            assertTrue("Android Keystore must encrypt credentials", store.save(token, a))
            assertEquals(token, store.load(a))
            assertNull("Another OAuth client must never inherit the session", store.load(b))
            assertNull("Mismatched credentials must be discarded", store.load(a))
            assertFalse(store.save(token, "not-a-valid-id"))
        } finally {
            store.clear()
        }
    }
}

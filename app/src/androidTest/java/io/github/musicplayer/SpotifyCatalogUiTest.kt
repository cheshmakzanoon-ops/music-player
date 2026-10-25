package io.github.musicplayer

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import io.github.musicplayer.spotify.SpotifyCatalogItem
import io.github.musicplayer.spotify.SpotifyCatalogKind
import io.github.musicplayer.spotify.SpotifyCatalogState
import io.github.musicplayer.ui.MusicPlayerApp
import io.github.musicplayer.ui.PlayerActions
import io.github.musicplayer.ui.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Render actual backend-shaped metadata without claiming a live authorized Spotify user. */
@RunWith(AndroidJUnit4::class)
class SpotifyCatalogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun spotifyTrackAlbumArtistAppearInsideSearchAndOpenTheirExactUris() {
        val ids = listOf("1234567890123456789012", "2234567890123456789012", "3234567890123456789012")
        val results = listOf(
            SpotifyCatalogItem(ids[0], SpotifyCatalogKind.TRACK, "Neon Drive", "Night Artist", null, 185_000),
            SpotifyCatalogItem(ids[1], SpotifyCatalogKind.ALBUM, "Neon Album", "Night Artist", null, null),
            SpotifyCatalogItem(ids[2], SpotifyCatalogKind.ARTIST, "Night Artist", "Artist", null, null),
        )
        var selected: String? = null
        compose.setContent {
            MusicPlayerApp(
                state = PlayerUiState(spotifyCatalog = SpotifyCatalogState(
                    configured = true, authorized = true, query = "neon", results = results,
                )),
                actions = PlayerActions(onSelectSpotifyResult = { selected = it.uri }),
            )
        }
        compose.onNodeWithTag("nav_search").performClick()
        compose.onNodeWithTag("search_input").performTextInput("neon")
        for (item in results) {
            val tag = "spotify_result_${item.kind.apiName}_${item.id}"
            compose.onNodeWithTag("search_screen").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertExists().performClick()
            assertEquals(item.uri, selected)
        }
    }
}

package io.github.musicplayer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    @Test fun clientIdCanBeConfiguredInSearchWithoutRebuildingOrSigningUp() {
        val expectedId = "0123456789abcdef0123456789abcdef"
        var savedId: String? = null
        var catalog by mutableStateOf(SpotifyCatalogState())
        compose.setContent {
            MusicPlayerApp(
                state = PlayerUiState(spotifyCatalog = catalog),
                actions = PlayerActions(onSetSpotifyCatalogClientId = { input ->
                    savedId = input
                    catalog = SpotifyCatalogState(configured = true, clientId = input, clientIdOverridden = true)
                }),
            )
        }
        compose.onNodeWithTag("nav_search").performClick()
        compose.onNodeWithTag("search_screen").performScrollToNode(hasTestTag("spotify_client_id_field"))
        compose.onNodeWithTag("spotify_client_id_save").assertIsNotEnabled()
        compose.onNodeWithTag("spotify_client_id_field").performTextInput("invalid")
        compose.onNodeWithTag("spotify_client_id_save").assertIsNotEnabled()
        compose.onNodeWithTag("spotify_client_id_field").performTextInput(expectedId)
        // The invalid prefix cannot be accepted as an OAuth application identifier.
        compose.onNodeWithTag("spotify_client_id_save").assertIsNotEnabled()
        compose.onNodeWithTag("spotify_client_id_field").performTextReplacement(expectedId)
        compose.onNodeWithTag("spotify_client_id_save").assertIsEnabled().performClick()
        assertEquals(expectedId, savedId)
        compose.onNodeWithTag("search_screen").performScrollToNode(hasTestTag("spotify_catalog_connect"))
        compose.onNodeWithTag("spotify_catalog_connect").assertExists().assertIsEnabled()
        compose.onNodeWithTag("spotify_client_id_edit").assertExists()
    }

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

    @Test fun catalogueNextPageIsExplicitAndCannotBeDoubleTappedWhileLoading() {
        val id = "1234567890123456789012"
        var requests = 0
        var catalog by mutableStateOf(SpotifyCatalogState(
            configured = true, authorized = true, query = "night", hasMore = true,
            results = listOf(SpotifyCatalogItem(id, SpotifyCatalogKind.TRACK, "Night Signal", "Arc Waves", null, 100_000)),
        ))
        compose.setContent {
            MusicPlayerApp(
                state = PlayerUiState(spotifyCatalog = catalog),
                actions = PlayerActions(onLoadMoreSpotifyCatalog = {
                    requests++
                    catalog = catalog.copy(loadingMore = true)
                }),
            )
        }
        compose.onNodeWithTag("nav_search").performClick()
        compose.onNodeWithTag("search_input").performTextInput("night")
        compose.onNodeWithTag("search_screen").performScrollToNode(hasTestTag("spotify_catalog_load_more"))
        compose.onNodeWithTag("spotify_catalog_load_more").assertIsEnabled().performClick()
        assertEquals(1, requests)
        compose.onNodeWithTag("spotify_catalog_load_more").assertIsNotEnabled()
    }
}

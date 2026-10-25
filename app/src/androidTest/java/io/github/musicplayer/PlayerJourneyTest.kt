package io.github.musicplayer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.musicplayer.playback.PlayerService
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** Exercises the shipped UI and real MediaSessionService, with original local audio. */
@RunWith(AndroidJUnit4::class)
class PlayerJourneyTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule(order = 0)
    val stateReset = object : ExternalResource() {
        override fun before() {
            context.stopService(Intent(context, PlayerService::class.java))
            context.getSharedPreferences("appearance", 0).edit().clear().putBoolean("reduce_motion", true).commit()
            context.getSharedPreferences("music-library", 0).edit().clear().commit()
            context.getSharedPreferences("player-session", 0).edit().clear().commit()
        }
        override fun after() { context.stopService(Intent(context, PlayerService::class.java)) }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: MusicViewModel

    @Before
    fun awaitLibrary() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[MusicViewModel::class.java] }
        compose.waitUntil(15_000) { model.repository.ready.value && model.repository.tracks.value.size == 6 }
        compose.onNodeWithTag("app_ready").assertExists()
    }

    @Test
    fun discoverSearchAndOfflineLibraryNeedNoAccount() {
        compose.onNodeWithTag("hero_play").assertIsDisplayed()
        screenshot("01-discover")
        compose.onNodeWithTag("nav_search").performClick()
        compose.onNodeWithTag("search_input").performTextInput("night")
        compose.onNodeWithTag("track_night-drive").assertExists()
        compose.onNodeWithTag("track_first-light").assertDoesNotExist()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        screenshot("02-search")
        compose.onNodeWithTag("search_input").performTextReplacement("zzzz no match")
        compose.onNodeWithText("Nothing on this frequency").assertExists()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithTag("import_audio").assertIsDisplayed()
        compose.onNodeWithTag("add_stream").assertIsDisplayed()
        compose.onNodeWithTag("filter_offline").performClick()
        compose.onNodeWithTag("track_first-light").assertExists()
        compose.onNodeWithTag("track_night-drive").assertDoesNotExist()
        screenshot("03-library")
    }

    @Test
    fun realPlaybackSeekQueueAndFavouriteSurviveActivityRecreation() {
        compose.onNodeWithTag("hero_play").performClick()
        compose.waitUntil(15_000) { model.player.state.value.isPlaying && model.player.state.value.positionMs > 300 }
        compose.onNodeWithTag("now_playing").assertExists()
        compose.onNodeWithTag("current_track_title").assertTextEquals("First light")
        screenshot("04-now-playing")
        compose.onNodeWithTag("play_pause").performScrollTo().performClick()
        compose.waitUntil(5_000) { !model.player.state.value.isPlaying }
        compose.onNodeWithTag("seek_slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(8_000f) }
        compose.waitUntil(5_000) { model.player.state.value.positionMs in 7_500L..9_000L }
        compose.onNodeWithTag("player_favorite").performScrollTo().performClick()
        compose.waitUntil(5_000) { "first-light" in model.repository.favorites.value }
        compose.onNodeWithTag("shuffle").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.player.state.value.shuffle }
        compose.onNodeWithTag("repeat").performClick()
        compose.waitUntil(5_000) { model.player.state.value.repeat }
        compose.onNodeWithTag("toggle_queue").performClick()
        compose.onNodeWithTag("queue_list").assertExists()
        assertEquals(6, model.player.state.value.queue.size)
        screenshot("05-queue")
        compose.onNodeWithTag("close_player").performClick()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[MusicViewModel::class.java] }
        compose.waitUntil(10_000) { model.player.state.value.track?.id == "first-light" }
        assertTrue("first-light" in model.repository.favorites.value)
        assertTrue(model.player.state.value.shuffle)
        assertTrue(model.player.state.value.repeat)
        compose.onNodeWithTag("mini_player").assertExists()
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithTag("filter_favourites").performClick()
        compose.onNodeWithTag("track_first-light").assertExists()
    }

    @Test
    fun backgroundPlaybackContinuesAndControlsResume() {
        compose.onNodeWithTag("hero_play").performClick()
        compose.waitUntil(15_000) { model.player.state.value.isPlaying }
        val before = model.player.state.value.positionMs
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        compose.waitUntil(8_000) { model.player.state.value.positionMs > before + 700 && model.player.state.value.isPlaying }
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(8_000) { model.player.state.value.isPlaying }
        compose.onNodeWithTag("play_pause").performScrollTo().performClick()
        compose.waitUntil(5_000) { !model.player.state.value.isPlaying }
        assertNull(model.player.state.value.error)
    }

    @Test
    fun addSourceValidatesAddressAndPermissionBeforeSaving() {
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithTag("add_stream").performClick()
        compose.onNodeWithTag("confirm_add_stream").assertIsNotEnabled()
        compose.onNodeWithTag("stream_title").performTextInput("My recording")
        compose.onNodeWithTag("stream_url").performTextInput("http://example.com/audio.wav")
        compose.onNodeWithTag("stream_permission").performClick()
        compose.onNodeWithTag("confirm_add_stream").assertIsNotEnabled()
        compose.onNodeWithTag("stream_url").performTextReplacement("https://example.com/audio.wav")
        compose.onNodeWithTag("confirm_add_stream").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { model.repository.tracks.value.any { it.title == "My recording" } }
        val imported = model.repository.tracks.value.first { it.title == "My recording" }
        assertTrue(imported.downloadable)
        assertFalse(imported.isOffline)
        assertEquals("Your HTTPS stream", imported.source)
        assertNull(model.repository.error.value)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File(context.getExternalFilesDir(null), "screenshots/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(bitmap) { "Emulator screenshot unavailable" }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

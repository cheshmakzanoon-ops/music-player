package io.github.musicplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.app.NotificationManager
import android.graphics.Bitmap
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.graphics.HardwareRendererCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import io.github.musicplayer.playback.PlayerService
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/** Exercises the shipped UI and real MediaSessionService, with original local audio. */
@RunWith(AndroidJUnit4::class)
class PlayerJourneyTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var device: UiDevice

    @get:Rule(order = 0)
    val stateReset = object : ExternalResource() {
        override fun before() {
            // Connect accessibility before creating the next playback session; initializing it
            // during playback can refresh SystemUI's stale media notification from a prior test.
            device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            stopPlayerAndAwaitTeardown()
            context.getSharedPreferences("appearance", 0).edit().clear().putBoolean("reduce_motion", true).commit()
            context.getSharedPreferences("music-library", 0).edit().clear().commit()
            context.getSharedPreferences("player-session", 0).edit().clear().commit()
        }
        override fun after() { stopPlayerAndAwaitTeardown() }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val failureEvidence = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            // Do not wait for Compose idleness: a stalled UI is exactly what this evidence must capture.
            runCatching { captureScreenshot("failure-${description.methodName}") }
        }
    }

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
        screenshot("01-discover", "hero_play")
        compose.onNodeWithTag("nav_search").performClick()
        compose.onNodeWithTag("search_input").performTextInput("night")
        compose.onNodeWithTag("search_input").performImeAction()
        compose.onNodeWithTag("search_screen").performScrollToNode(hasTestTag("track_night-drive"))
        compose.onNodeWithTag("track_night-drive").assertExists()
        compose.onNodeWithTag("track_first-light").assertDoesNotExist()
        screenshot("02-search", "track_night-drive")
        compose.onNodeWithTag("search_screen").performScrollToNode(hasTestTag("search_input"))
        compose.onNodeWithTag("search_input").performTextReplacement("zzzz no match")
        compose.onNodeWithTag("search_input").performImeAction()
        compose.onNodeWithTag("search_screen").performScrollToNode(hasText("Nothing on this frequency"))
        compose.onNodeWithText("Nothing on this frequency").assertExists()
        screenshot("06-search-empty", "search_screen")
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithTag("import_audio").assertIsDisplayed()
        compose.onNodeWithTag("add_stream").assertIsDisplayed()
        compose.onNodeWithTag("filter_offline").performClick()
        compose.onNodeWithTag("library_screen").performScrollToNode(hasTestTag("track_first-light"))
        compose.onNodeWithTag("track_first-light").assertExists()
        compose.onNodeWithTag("track_night-drive").assertDoesNotExist()
        screenshot("03-library", "track_first-light")
    }

    @Test
    fun realPlaybackSeekQueueAndFavouriteSurviveActivityRecreation() {
        compose.onNodeWithTag("hero_play").performClick()
        compose.waitUntil(15_000) { model.player.state.value.isPlaying && model.player.state.value.positionMs > 300 }
        compose.onNodeWithTag("now_playing").assertExists()
        compose.onNodeWithTag("current_track_title").assertTextEquals("First light")
        screenshot("04-now-playing", "now_playing")
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
        screenshot("05-queue", "queue_list")
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
        compose.onNodeWithTag("library_screen").performScrollToNode(hasTestTag("track_first-light"))
        compose.onNodeWithTag("track_first-light").assertExists()
    }

    @Test
    fun backgroundPlaybackContinuesAndControlsResume() {
        compose.onNodeWithTag("hero_play").performClick()
        compose.waitUntil(15_000) { model.player.state.value.isPlaying }
        val before = model.player.state.value.positionMs
        device.pressHome()
        compose.waitUntil(8_000) { model.player.state.value.positionMs > before + 700 && model.player.state.value.isPlaying }
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        // Playback is already true in the background; it cannot signal that the resumed
        // Activity has reattached its Compose hierarchy (especially on Android 8).
        compose.waitUntil(8_000) {
            var resumed = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { it is MainActivity }
            }
            resumed && model.player.state.value.isPlaying &&
                compose.onAllNodesWithTag("app_ready")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("app_ready").assertIsDisplayed()
        if (compose.onAllNodesWithTag("now_playing").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("expand_player").performClick()
        }
        compose.onNodeWithTag("play_pause").performScrollTo().performClick()
        compose.waitUntil(5_000) { !model.player.state.value.isPlaying }
        assertNull(model.player.state.value.error)
    }

    @Test
    fun addSourceValidatesAddressAndPermissionBeforeSaving() {
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithTag("add_stream").performClick()
        compose.onNodeWithTag("confirm_add_stream").assertIsNotEnabled()
        screenshot("07-add-source", "stream_title")
        compose.onNodeWithTag("stream_title").performTextInput("My recording")
        compose.onNodeWithTag("stream_url").performTextInput("http://example.com/audio.wav")
        compose.onNodeWithTag("stream_permission").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_add_stream").assertIsNotEnabled()
        compose.onNodeWithTag("stream_url").performScrollTo().performTextReplacement("https://example.com/audio.wav#fragment")
        compose.onNodeWithTag("confirm_add_stream").assertIsNotEnabled()
        compose.onNodeWithTag("stream_url").performScrollTo().performTextReplacement("https://example.com/audio.wav")
        compose.onNodeWithTag("confirm_add_stream").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { model.repository.tracks.value.any { it.title == "My recording" } }
        val imported = model.repository.tracks.value.first { it.title == "My recording" }
        assertTrue(imported.downloadable)
        assertFalse(imported.isOffline)
        assertEquals("Your HTTPS stream", imported.source)
        assertNull(model.repository.error.value)
    }

    @Test
    fun largeTextKeepsSettingsAndSourceControlsReachable() {
        val originalScale = context.resources.configuration.fontScale
        try {
            setFontScale(1.5f)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("settings").performClick()
            compose.onNodeWithTag("reduce_motion").performScrollTo().assertIsOn()
            screenshot("08-settings-large-text", "reduce_motion")
            compose.onNodeWithTag("close_settings").assertIsDisplayed().performClick()
            compose.onNodeWithTag("nav_library").assertIsDisplayed().performClick()
            compose.onNodeWithTag("library_screen").performScrollToNode(hasTestTag("add_stream"))
            compose.onNodeWithTag("add_stream").assertIsDisplayed().performClick()
            compose.onNodeWithTag("stream_title").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("stream_url").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("stream_permission").performScrollTo().assertIsDisplayed()
            screenshot("09-source-large-text", "stream_permission")
            compose.onNodeWithTag("confirm_add_stream").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
            compose.onNodeWithTag("nav_home").assertIsDisplayed().performClick()
        } finally {
            setFontScale(originalScale)
        }
    }

    @Test
    fun optionalSpotifyConnectionDoesNotBlockTheLocalLibrary() {
        compose.onNodeWithTag("nav_library").performClick()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings_spotify").performScrollTo().performClick()
        compose.onNodeWithTag("spotify_panel").assertIsDisplayed()
        val spotify = model.spotify.state.value
        assertFalse(spotify.connected)
        compose.onNodeWithTag("spotify_connect").performScrollTo().assertIsDisplayed()
        if (!spotify.configReady || !spotify.installed) {
            compose.onNodeWithTag("spotify_connect").assertIsNotEnabled()
        } else {
            compose.onNodeWithTag("spotify_connect").assertIsEnabled()
        }
        if (!spotify.configReady) {
            compose.onNodeWithTag("spotify_not_configured").assertExists()
        }
        compose.onNodeWithTag("spotify_connection_state").performScrollTo()
        screenshot("10-spotify-connection", "spotify_connection_state")
        compose.onNodeWithTag("close_spotify").assertIsDisplayed().performClick()
        compose.onNodeWithTag("spotify_panel").assertDoesNotExist()
        compose.onNodeWithTag("library_screen").assertExists()
        compose.onNodeWithTag("library_screen").performScrollToNode(hasTestTag("track_first-light"))
        compose.onNodeWithTag("track_first-light").assertIsDisplayed()
    }

    private fun setFontScale(scale: Float) {
        val command = "settings put system font_scale $scale"
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        compose.waitUntil(10_000) { kotlin.math.abs(context.resources.configuration.fontScale - scale) < 0.01f }
    }

    private fun stopPlayerAndAwaitTeardown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        instrumentation.runOnMainSync { context.stopService(Intent(context, PlayerService::class.java)) }
        @Suppress("DEPRECATION")
        fun fullyStopped(): Boolean =
            PlayerService.activeInstanceCount == 0 &&
                activityManager.getRunningServices(Int.MAX_VALUE).none { it.service.className == PlayerService::class.java.name } &&
                notifications.activeNotifications.isEmpty()

        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (!fullyStopped() && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
        assertTrue("Previous playback service or media notification did not finish teardown", fullyStopped())
        // The active-instance counter reaches zero only after onDestroy's final save/release.
        // Thus the next prefs.clear().commit() cannot be overwritten by the previous session.
        instrumentation.waitForIdleSync()
        if (Build.VERSION.SDK_INT >= 29) {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL)
            try {
                val sessions = context.getSystemService(MediaSessionManager::class.java)
                val sessionDeadline = SystemClock.elapsedRealtime() + 10_000L
                fun hasAppSession() = sessions.getActiveSessions(null).any { it.packageName == context.packageName }
                while (hasAppSession() && SystemClock.elapsedRealtime() < sessionDeadline) {
                    SystemClock.sleep(20)
                }
                assertFalse("Previous media session is still registered", hasAppSession())
            } finally {
                instrumentation.uiAutomation.dropShellPermissionIdentity()
            }
        }
        // SystemUI removes its old media entry asynchronously. Let its accessibility
        // events settle before a new session reuses the same notification id.
        device.waitForIdle(5_000)
        instrumentation.waitForIdleSync()
    }

    private fun screenshot(name: String, visibleTag: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithTag(visibleTag).assertIsDisplayed().fetchSemanticsNode()
        val root = requireNotNull((node.root as? ViewRootForTest)?.view) {
            "No Android view hosts the visible screenshot anchor $visibleTag"
        }
        // Compose idleness settles semantics, not necessarily the native render buffer. Like
        // AndroidX WindowCapture, enable drawing and force the exact semantics node's window
        // to submit a frame. Resolving via ViewRootForTest also finds Dialog windows on API 26,
        // without guessing which Espresso root currently owns native focus.
        // https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable)
        val wasDrawingEnabled = HardwareRendererCompat.isDrawingEnabled()
        try {
            HardwareRendererCompat.setDrawingEnabled(true)
            awaitRenderedWindow(root)
            captureScreenshot(name)
        } finally {
            HardwareRendererCompat.setDrawingEnabled(wasDrawingEnabled)
        }
    }

    private fun awaitRenderedWindow(root: View) {
        val drawn = CountDownLatch(1)
        var drawListener: ViewTreeObserver.OnDrawListener? = null
        var commitCallback: Runnable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = root
            if (Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
                val callback = Runnable { drawn.countDown() }
                commitCallback = callback
                view.viewTreeObserver.registerFrameCommitCallback(callback)
            } else {
                // API 26 has no frame-commit callback; await an actual draw pass, then let
                // the render thread/compositor advance using the VSYNC callbacks below.
                val listener = object : ViewTreeObserver.OnDrawListener {
                    var handled = false
                    override fun onDraw() {
                        if (handled) return
                        handled = true
                        view.post {
                            if (view.viewTreeObserver.isAlive) {
                                view.viewTreeObserver.removeOnDrawListener(this)
                            }
                            drawn.countDown()
                        }
                    }
                }
                drawListener = listener
                view.viewTreeObserver.addOnDrawListener(listener)
            }
            view.invalidate()
        }
        try {
            assertTrue("The selected screenshot window did not render within 5 seconds", drawn.await(5, TimeUnit.SECONDS))
            // A committed buffer need not be visible yet. Two real display frames allow the
            // compositor to present it; this waits on VSYNC rather than an arbitrary sleep.
            val presented = CountDownLatch(1)
            root.postOnAnimation { root.postOnAnimation { presented.countDown() } }
            assertTrue("The display did not present the rendered frame", presented.await(2, TimeUnit.SECONDS))
        } finally {
            root.post {
                val observer = root.viewTreeObserver
                if (observer.isAlive) {
                    drawListener?.let(observer::removeOnDrawListener)
                    if (Build.VERSION.SDK_INT >= 29) {
                        commitCallback?.let { observer.unregisterFrameCommitCallback(it) }
                    }
                }
            }
        }
    }

    private fun captureScreenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), "screenshots/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(bitmap) { "Emulator screenshot unavailable" }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

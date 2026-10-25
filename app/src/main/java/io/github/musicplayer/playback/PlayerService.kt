package io.github.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.musicplayer.data.TrackJson
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns playback independently of the Activity and supplies Android's media notification. */
@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playbackPrefs by lazy { getSharedPreferences("player-session", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        activeInstances.incrementAndGet()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(OfflinePlaybackDataSourceFactory(this)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        restoreSession()
        val builder = MediaSession.Builder(this, player)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        session = builder.build()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                    )
                ) saveSession()
            }
        })
        scope.launch {
            while (isActive) {
                delay(5000)
                if (player.isPlaying) saveSession()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.uid == applicationInfo.uid || controllerInfo.isTrusted) session else null

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        try {
            saveSession()
            scope.cancel()
            session?.release()
            session = null
            player.release()
            super.onDestroy()
        } finally {
            activeInstances.decrementAndGet()
        }
    }

    private fun saveSession() {
        val tracks = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() }
        playbackPrefs.edit()
            .putString("queue", TrackJson.encodeList(tracks))
            .putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("position", player.currentPosition.coerceAtLeast(0))
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .putInt("repeat", player.repeatMode)
            .apply()
    }

    private fun restoreSession() {
        runCatching {
            val tracks = TrackJson.decodeList(playbackPrefs.getString("queue", "[]")!!)
                .map { track ->
                    // If a saved download was removed, restore its original stream instead.
                    if (track.offlinePath != null && !java.io.File(track.offlinePath).isFile) track.copy(offlinePath = null) else track
                }
            if (tracks.isNotEmpty()) {
                player.setMediaItems(
                    tracks.map { it.toMediaItem() },
                    playbackPrefs.getInt("index", 0).coerceIn(tracks.indices),
                    playbackPrefs.getLong("position", 0).coerceAtLeast(0),
                )
                player.shuffleModeEnabled = playbackPrefs.getBoolean("shuffle", false)
                player.repeatMode = playbackPrefs.getInt("repeat", Player.REPEAT_MODE_OFF)
                // Restoring a session must never start sound or network traffic by itself.
                player.playWhenReady = false
            }
        }
    }

    internal companion object {
        private val activeInstances = AtomicInteger(0)

        /** Tests can await completed destruction before resetting the persisted session. */
        val activeInstanceCount: Int get() = activeInstances.get()
    }
}

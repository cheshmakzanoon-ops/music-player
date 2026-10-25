package io.github.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.musicplayer.model.PlaybackState
import io.github.musicplayer.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Main-thread bridge to the persistent media service; UI state comes from the real player. */
@OptIn(UnstableApi::class)
class PlayerController(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var pendingPlay: Pair<Track, List<Track>>? = null
    private var closed = false
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlayerService::class.java)))
        .setListener(object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                if (!closed) _state.update { it.copy(isPlaying = false, isBuffering = false, error = "The player disconnected. Reopen the app to reconnect.") }
            }
        })
        .buildAsync()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) {
            val message = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Can't reach this audio source. Check your connection or play a saved track."
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "This audio file is no longer available. Import it again or reconnect to its source."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The audio source could not serve this track. Try again later."
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "This address did not provide a supported audio file."
                else -> "Couldn't play this track. Try another file or source."
            }
            _state.update { it.copy(isPlaying = false, isBuffering = false, error = message) }
        }
    }

    init {
        future.addListener({
            if (!closed) {
                try {
                    controller = future.get().also { it.addListener(listener) }
                    publish()
                    pendingPlay?.let { (track, queue) -> play(track, queue) }
                    pendingPlay = null
                } catch (error: Exception) {
                    _state.update { it.copy(error = "The audio engine couldn't start. Reopen the app to try again.") }
                }
            }
        }, ContextCompat.getMainExecutor(app))
        scope.launch {
            while (isActive) {
                delay(250)
                publish()
            }
        }
    }

    fun play(track: Track, queue: List<Track>) {
        if (closed) return
        val player = controller
        if (player == null) {
            pendingPlay = track to queue
            return
        }
        val playableQueue = queue.ifEmpty { listOf(track) }.let {
            if (it.any { item -> item.id == track.id }) it else listOf(track) + it
        }.distinctBy(Track::id)
        val index = playableQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        _state.update { it.copy(error = null) }
        player.setMediaItems(playableQueue.map { it.toMediaItem() }, index, 0)
        player.prepare()
        player.play()
        publish()
    }

    fun togglePlay() {
        val player = controller ?: return
        _state.update { it.copy(error = null) }
        if (player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
            player.pause()
        } else if (player.mediaItemCount > 0) {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
        publish()
    }

    /** Pause for a different playback provider without toggling a stopped player on. */
    fun pause() {
        pendingPlay = null
        controller?.pause()
        publish()
    }

    fun seekTo(positionMs: Long) {
        controller?.let { player ->
            val max = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
            player.seekTo(positionMs.coerceIn(0L, max))
            publish()
        }
    }

    fun next() {
        controller?.let { player ->
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            else if (player.mediaItemCount > 0) player.seekToDefaultPosition(0)
            publish()
        }
    }

    fun previous() {
        controller?.let { player ->
            if (player.currentPosition > 3000L || !player.hasPreviousMediaItem()) player.seekTo(0)
            else player.seekToPreviousMediaItem()
            publish()
        }
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        publish()
    }

    fun toggleRepeat() {
        controller?.let { it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF }
        publish()
    }

    fun dismissError() { _state.update { it.copy(error = null) } }

    override fun close() {
        closed = true
        scope.cancel()
        controller?.removeListener(listener)
        controller = null
        MediaController.releaseFuture(future)
    }

    private fun publish() {
        val player = controller ?: return
        val track = player.currentMediaItem?.toTrack()
        val actualDuration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: track?.durationMs ?: 0L
        _state.value = PlaybackState(
            track = track,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = actualDuration,
            queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toTrack() },
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode != Player.REPEAT_MODE_OFF,
            error = _state.value.error,
        )
    }
}

package io.github.musicplayer.spotify

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.annotation.MainThread
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.UnsupportedFeatureVersionException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Capabilities
import com.spotify.protocol.types.Empty
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Repeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Supported App Remote control only. Spotify owns authentication, audio and catalogue entitlements. */
@MainThread
class SpotifyRemoteController(context: Context, private val config: SpotifyConfiguration) : AutoCloseable {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var remote: SpotifyAppRemote? = null
    private var playerSubscription: Subscription<PlayerState>? = null
    private var capabilitiesSubscription: Subscription<Capabilities>? = null
    private var artworkRequest: CallResult<Bitmap>? = null
    private var connectionTimeout: Job? = null
    private var generation = 0L
    private var foreground = false
    private var wantedConnection = false
    private var closed = false
    private var artworkUri: String? = null
    private var anchorPosition = 0L
    private var anchorTime = 0L
    private var playbackSpeed = 1f
    private val _state = MutableStateFlow(SpotifyRemoteState(configReady = config.isConfigured, installed = isInstalled()))
    val state: StateFlow<SpotifyRemoteState> = _state.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(500)
                val current = _state.value
                if (foreground && current.connected && current.isPlaying) {
                    val estimate = anchorPosition + ((SystemClock.elapsedRealtime() - anchorTime) * playbackSpeed).toLong()
                    _state.update { it.copy(positionMs = estimate.coerceIn(0, it.durationMs.coerceAtLeast(0))) }
                }
            }
        }
    }

    fun connect(context: Context) {
        if (closed) return
        foreground = true
        wantedConnection = true
        openConnection(context, showAuthView = true)
    }

    fun onForeground(context: Context) {
        if (closed) return
        foreground = true
        _state.update { it.copy(installed = isInstalled()) }
        if (wantedConnection && remote == null && !_state.value.connecting) openConnection(context, showAuthView = false)
    }

    fun onBackground() {
        foreground = false
        // Spotify's authorization Activity can temporarily stop ours. Keep the pending
        // callback alive, then release any connection delivered while still backgrounded.
        if (!_state.value.connecting) releaseConnection()
    }

    fun disconnect() {
        wantedConnection = false
        releaseConnection()
        _state.update { it.copy(error = null) }
    }

    fun play(input: String) {
        val uri = SpotifyContentUri.normalize(input)
        if (uri == null) {
            fail("Paste a Spotify track, album, artist, playlist or episode link.")
            return
        }
        if (uri.startsWith("spotify:track:") && _state.value.canPlayOnDemand != true) {
            fail(if (_state.value.canPlayOnDemand == null) "Wait for Spotify to confirm playback availability, then try again."
                else "This Spotify account cannot play individual tracks on demand. Open an album, artist or playlist instead.")
            return
        }
        command("start playback") { it.playerApi.play(uri) }
    }

    fun togglePlay() {
        if (!_state.value.hasTrack) { fail("Choose music in Spotify or paste a Spotify link first."); return }
        if (_state.value.isPlaying) pause() else command("resume playback") { it.playerApi.resume() }
    }

    /** Deterministic handoff to local audio; never accidentally resumes Spotify. */
    fun pause() { if (remote?.isConnected == true) command("pause playback") { it.playerApi.pause() } }
    fun next() { if (allowed(_state.value.canSkipNext, "skip this track")) command("skip forward") { it.playerApi.skipNext() } }
    fun previous() { if (allowed(_state.value.canSkipPrevious, "return to the previous track")) command("skip back") { it.playerApi.skipPrevious() } }
    fun seekTo(positionMs: Long) {
        if (allowed(_state.value.canSeek, "seek in this track")) {
            command("seek") { it.playerApi.seekTo(positionMs.coerceIn(0, _state.value.durationMs.coerceAtLeast(0))) }
        }
    }
    fun toggleShuffle() {
        if (allowed(_state.value.canShuffle, "change shuffle")) command("change shuffle") { it.playerApi.setShuffle(!_state.value.shuffle) }
    }
    fun toggleRepeat() {
        if (allowed(_state.value.canRepeat, "change repeat")) command("change repeat") { it.playerApi.setRepeat(if (_state.value.repeat) Repeat.OFF else Repeat.ALL) }
    }
    fun dismissError() { _state.update { it.copy(error = null) } }

    override fun close() {
        closed = true
        wantedConnection = false
        releaseConnection()
        scope.cancel()
    }

    private fun openConnection(context: Context, showAuthView: Boolean) {
        if (_state.value.connecting || remote?.isConnected == true) return
        val installed = isInstalled()
        _state.update { it.copy(installed = installed, configReady = config.isConfigured) }
        config.configurationIssue?.let { fail(it); return }
        if (!installed) { fail("Install the official Spotify app to connect."); return }
        releaseConnection()
        val attempt = generation
        _state.update { it.copy(connecting = true, error = null) }
        connectionTimeout = scope.launch {
            delay(60_000)
            if (attempt == generation && _state.value.connecting) {
                releaseConnection()
                wantedConnection = false
                fail("Spotify did not finish connecting. Return to this screen and try Connect again.")
            }
        }
        try {
            val parameters = ConnectionParams.Builder(config.clientId.trim())
                .setRedirectUri(config.redirectUri.trim()).showAuthView(showAuthView).build()
            SpotifyAppRemote.connect(context, parameters, object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    if (closed) { SpotifyAppRemote.disconnect(appRemote); return }
                    scope.launch {
                        if (attempt != generation || !foreground) {
                            SpotifyAppRemote.disconnect(appRemote)
                            if (attempt == generation) {
                                connectionTimeout?.cancel()
                                _state.update { it.copy(connecting = false) }
                            }
                            return@launch
                        }
                        connectionTimeout?.cancel()
                        remote = appRemote
                        _state.update { it.copy(connected = true, connecting = false, error = null) }
                        try {
                            subscribe(appRemote, attempt)
                        } catch (_: Exception) {
                            connectionLost()
                        }
                    }
                }

                override fun onFailure(error: Throwable) {
                    if (closed) return
                    scope.launch {
                        if (attempt == generation) {
                            releaseConnection()
                            wantedConnection = false
                            fail(connectionError(error))
                        }
                    }
                }
            })
        } catch (error: Exception) {
            releaseConnection()
            wantedConnection = false
            fail(connectionError(error))
        }
    }

    private fun subscribe(connection: SpotifyAppRemote, attempt: Long) {
        playerSubscription = connection.playerApi.subscribeToPlayerState().also { subscription ->
            subscription.setEventCallback { sample -> scope.launch { if (current(connection, attempt)) receivePlayer(sample, connection, attempt) } }
            subscription.setErrorCallback { scope.launch { if (current(connection, attempt)) connectionLost() } }
        }
        capabilitiesSubscription = connection.userApi.subscribeToCapabilities().also { subscription ->
            subscription.setEventCallback { capabilities -> scope.launch {
                if (current(connection, attempt)) _state.update { it.copy(canPlayOnDemand = capabilities.canPlayOnDemand) }
            } }
            subscription.setErrorCallback { scope.launch {
                if (current(connection, attempt)) {
                    _state.update { it.copy(canPlayOnDemand = null) }
                    fail("Spotify could not confirm account playback capabilities. Individual track playback is unavailable until you reconnect.")
                }
            } }
        }
    }

    private fun receivePlayer(sample: PlayerState, connection: SpotifyAppRemote, attempt: Long) {
        val track = sample.track
        val restrictions = sample.playbackRestrictions
        anchorPosition = sample.playbackPosition.coerceAtLeast(0)
        anchorTime = SystemClock.elapsedRealtime()
        playbackSpeed = sample.playbackSpeed.takeIf { it.isFinite() && it >= 0 } ?: 1f
        _state.update { it.copy(
            title = track?.name.orEmpty(), artist = track?.artist?.name.orEmpty(), album = track?.album?.name.orEmpty(),
            durationMs = track?.duration?.coerceAtLeast(0) ?: 0, positionMs = anchorPosition,
            isPlaying = track != null && !sample.isPaused, hasTrack = track != null,
            shuffle = sample.playbackOptions?.isShuffling == true,
            repeat = sample.playbackOptions?.repeatMode?.let { mode -> mode != Repeat.OFF } ?: false,
            canSeek = track != null && track.duration > 0 && restrictions?.canSeek == true,
            canSkipNext = track != null && restrictions?.canSkipNext == true,
            canSkipPrevious = track != null && restrictions?.canSkipPrev == true,
            canShuffle = track != null && restrictions?.canToggleShuffle == true,
            canRepeat = track != null && restrictions?.canRepeatContext == true,
        ) }
        val image = track?.imageUri
        if (image?.raw != artworkUri) {
            artworkUri = image?.raw
            artworkRequest?.cancel()
            artworkRequest = null
            _state.update { it.copy(artwork = null) }
            if (image != null && !image.raw.isNullOrBlank()) {
                val requestedUri = image.raw
                artworkRequest = runCatching {
                    connection.imagesApi.getImage(image, Image.Dimension.LARGE).also { request ->
                        request.setResultCallback { bitmap -> scope.launch {
                            if (current(connection, attempt) && artworkUri == requestedUri) _state.update { it.copy(artwork = bitmap) }
                        } }
                        // Cover-art failure must not interrupt playback or hide its metadata.
                        request.setErrorCallback { }
                    }
                }.getOrNull()
            }
        }
    }

    private fun command(action: String, operation: (SpotifyAppRemote) -> CallResult<Empty>) {
        val connection = remote
        if (closed || !foreground || connection?.isConnected != true) { fail("Connect to Spotify first."); return }
        val attempt = generation
        dismissError()
        try {
            operation(connection).setErrorCallback { scope.launch {
                if (current(connection, attempt)) fail("Spotify could not $action. Check the official app and this account's playback availability.")
            } }
        } catch (_: Exception) {
            if (current(connection, attempt)) fail("Spotify could not $action. Reconnect and try again.")
        }
    }

    private fun allowed(available: Boolean, operation: String): Boolean {
        if (!available) fail("Spotify does not currently allow this account to $operation.")
        return available
    }
    private fun current(connection: SpotifyAppRemote, attempt: Long) = !closed && foreground && generation == attempt && remote === connection
    private fun isInstalled() = runCatching { SpotifyAppRemote.isSpotifyInstalled(app) }.getOrDefault(false)
    private fun fail(message: String) { _state.update { it.copy(error = message) } }
    private fun connectionLost() { releaseConnection(); fail("Spotify disconnected. Tap Connect to try again.") }

    private fun releaseConnection() {
        generation++
        connectionTimeout?.cancel()
        connectionTimeout = null
        playerSubscription?.cancel()
        capabilitiesSubscription?.cancel()
        artworkRequest?.cancel()
        playerSubscription = null
        capabilitiesSubscription = null
        artworkRequest = null
        artworkUri = null
        remote?.let { runCatching { SpotifyAppRemote.disconnect(it) } }
        remote = null
        _state.update { it.copy(
            connected = false, connecting = false, isPlaying = false, artwork = null,
            canPlayOnDemand = null, canSeek = false, canSkipNext = false, canSkipPrevious = false, canShuffle = false, canRepeat = false,
        ) }
    }

    private fun connectionError(error: Throwable): String = when (error) {
        is NotLoggedInException -> "Sign in to the official Spotify app, then return and connect."
        is UserNotAuthorizedException -> "Spotify authorization was not completed. Tap Connect when you are ready to allow access."
        is AuthenticationFailedException -> "Spotify could not authorize this connection. Check the app registration and authorized Spotify account."
        is OfflineModeException -> "Spotify is in offline mode. Open Spotify and reconnect when it is online."
        is UnsupportedFeatureVersionException -> "Update the official Spotify app, then try connecting again."
        else -> "Could not connect to Spotify. Open the official app, check your connection, and try again."
    }
}

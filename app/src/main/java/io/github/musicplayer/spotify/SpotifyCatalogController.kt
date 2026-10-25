package io.github.musicplayer.spotify

import android.content.Context
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpotifyCatalogState(
    val configured: Boolean = false,
    val authorized: Boolean = false,
    val authorizing: Boolean = false,
    val loading: Boolean = false,
    val query: String = "",
    val results: List<SpotifyCatalogItem> = emptyList(),
    val error: String? = null,
)

/** Optional user-authorized catalogue access. Local playback and the App Remote stay separate. */
class SpotifyCatalogController(
    context: Context,
    private val clientId: String,
    private val api: SpotifyCatalogApi = SpotifyCatalogApi(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = SpotifyTokenStore(context.applicationContext)
    private val configured = SpotifyPkce.validClientId(clientId)
    private val _state = MutableStateFlow(SpotifyCatalogState(configured = configured))
    val state: StateFlow<SpotifyCatalogState> = _state.asStateFlow()
    private var credential: SpotifyCatalogToken? = null
    private var authJob: Job? = null
    private var searchJob: Job? = null
    private var listener: SpotifyLoopbackRedirect? = null
    private var generation = 0
    private var closed = false

    init {
        if (configured) scope.launch {
            val beforeLoad = generation
            val restored = withContext(Dispatchers.IO) { store.load() }
            if (!closed && generation == beforeLoad && restored != null) {
                credential = restored
                _state.update { it.copy(authorized = true) }
            }
        }
    }

    /** Browser-based PKCE. Redirect goes to an ephemeral 127.0.0.1 listener, not a website. */
    fun authorize(openBrowser: (String) -> Unit) {
        if (closed || authJob?.isActive == true) return
        if (!configured) {
            _state.update { it.copy(error = "Set SPOTIFY_CLIENT_ID in the Android build to enable Spotify catalogue search.") }
            return
        }
        authJob = scope.launch {
            _state.update { it.copy(authorizing = true, error = null) }
            val epoch = generation
            try {
                val session = SpotifyPkce.newSession()
                val bound = withContext(Dispatchers.IO) { SpotifyLoopbackRedirect() }
                listener = bound
                val redirectUri = bound.redirectUri
                openBrowser(SpotifyPkce.authorizationUrl(clientId, redirectUri, session))
                val code = withContext(Dispatchers.IO) { bound.awaitCode(session.state) }
                val tokens = api.exchangeCode(clientId, code, redirectUri, session.verifier)
                if (epoch == generation && !closed) {
                    credential = tokens
                    withContext(Dispatchers.IO) { store.save(tokens) }
                    _state.update { it.copy(authorized = true, error = null) }
                    val previousQuery = _state.value.query
                    if (previousQuery.isNotBlank()) search(previousQuery)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (epoch == generation && !closed) {
                    _state.update { it.copy(error = failure.message ?: "Could not connect to Spotify.") }
                }
            } finally {
                listener?.close()
                listener = null
                _state.update { it.copy(authorizing = false) }
            }
        }
    }

    /** Debounced metadata requests avoid spending Spotify's 2026 development-mode quota on keystrokes. */
    fun search(text: String) {
        if (closed) return
        val query = text.trim().take(200)
        if (query == _state.value.query && _state.value.results.isNotEmpty()) return
        searchJob?.cancel()
        _state.update { it.copy(query = query, results = emptyList(), loading = it.authorized && query.isNotBlank(), error = null) }
        if (query.isBlank() || !_state.value.authorized) return
        val epoch = generation
        searchJob = scope.launch {
            try {
                delay(450)
                val access = validAccessToken()
                val results = try {
                    api.search(access, query)
                } catch (failure: SpotifyCatalogHttpException) {
                    if (failure.status != 401) throw failure
                    // A server can revoke a token before its advertised expiry.
                    val refreshed = refreshToken(force = true)
                    api.search(refreshed, query)
                }
                if (!closed && generation == epoch) {
                    _state.update { it.copy(results = results, loading = false, error = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!closed && generation == epoch) {
                    if (failure is SpotifyCatalogHttpException && failure.status == 401) {
                        clearCredentials()
                    }
                    _state.update { it.copy(loading = false, error = failure.message ?: "Spotify search failed.") }
                }
            }
        }
    }

    private suspend fun validAccessToken(): String {
        val current = credential ?: throw SpotifyAuthorizationException("Connect Spotify to search its catalogue.")
        if (current.expiresAtMs > System.currentTimeMillis() + 60_000) return current.accessToken
        return refreshToken(force = false)
    }

    private suspend fun refreshToken(force: Boolean): String {
        val existing = credential ?: throw SpotifyAuthorizationException("Connect Spotify to search its catalogue.")
        val refresh = existing.refreshToken ?: throw SpotifyAuthorizationException("Spotify session expired. Connect again.")
        if (!force && existing.expiresAtMs > System.currentTimeMillis() + 60_000) return existing.accessToken
        val updated = api.refresh(clientId, refresh).let { if (it.refreshToken == null) it.copy(refreshToken = refresh) else it }
        credential = updated
        withContext(Dispatchers.IO) { store.save(updated) }
        return updated.accessToken
    }

    fun disconnect() {
        if (closed) return
        generation++
        authJob?.cancel()
        listener?.close()
        listener = null
        searchJob?.cancel()
        clearCredentials()
        _state.value = SpotifyCatalogState(configured = configured)
    }

    private fun clearCredentials() {
        credential = null
        // Synchronous delete prevents a revoked session from being revived after recreation.
        store.clear()
        _state.update { it.copy(authorized = false, results = emptyList()) }
    }

    override fun close() {
        closed = true
        generation++
        listener?.close()
        scope.cancel()
    }
}

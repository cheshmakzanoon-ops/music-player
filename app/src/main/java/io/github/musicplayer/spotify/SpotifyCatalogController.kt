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
    val clientId: String = "",
    val clientIdOverridden: Boolean = false,
    val authorized: Boolean = false,
    val authorizing: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val query: String = "",
    val results: List<SpotifyCatalogItem> = emptyList(),
    val error: String? = null,
)

/** Optional user-authorized catalogue access. Local playback and the App Remote stay separate. */
class SpotifyCatalogController(
    context: Context,
    buildClientId: String,
    private val api: SpotifyCatalogApi = SpotifyCatalogApi(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = SpotifyTokenStore(context.applicationContext)
    private val settings = context.applicationContext.getSharedPreferences("spotify-catalog-settings", Context.MODE_PRIVATE)
    private val defaultClientId = buildClientId.trim().takeIf(SpotifyPkce::validClientId).orEmpty()
    private var overrideClientId = settings.getString("client-id-override", null)?.takeIf(SpotifyPkce::validClientId)
    private var activeClientId = overrideClientId ?: defaultClientId
    private val configured get() = SpotifyPkce.validClientId(activeClientId)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SpotifyCatalogState> = _state.asStateFlow()
    private var credential: SpotifyCatalogToken? = null
    private var authJob: Job? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var nextOffset = 0
    private var listener: SpotifyLoopbackRedirect? = null
    private var generation = 0
    private var closed = false

    private fun initialState() = SpotifyCatalogState(
        configured = configured,
        clientId = activeClientId,
        clientIdOverridden = overrideClientId != null,
    )

    init {
        if (configured) scope.launch {
            val beforeLoad = generation
            val restored = withContext(Dispatchers.IO) { store.load(activeClientId) }
            if (!closed && generation == beforeLoad && restored != null) {
                credential = restored
                _state.update { it.copy(authorized = true) }
            }
        }
    }

    /** Public OAuth application ID can be supplied after installation without rebuilding the APK. */
    fun setClientId(input: String): Boolean {
        if (closed) return false
        val value = input.trim()
        if (!SpotifyPkce.validClientId(value)) {
            _state.update { it.copy(error = "Enter the 32-character public Spotify Client ID, not a client secret.") }
            return false
        }
        if (!settings.edit().putString("client-id-override", value).commit()) {
            _state.update { it.copy(error = "Could not save the Spotify Client ID on this device.") }
            return false
        }
        overrideClientId = value
        switchClientId(value)
        return true
    }

    /** Revert to the optional CI/build configuration, or guest mode if none was embedded. */
    fun resetClientId(): Boolean {
        if (closed) return false
        if (!settings.edit().remove("client-id-override").commit()) {
            _state.update { it.copy(error = "Could not reset the Spotify Client ID.") }
            return false
        }
        overrideClientId = null
        switchClientId(defaultClientId)
        return true
    }

    private fun switchClientId(value: String) {
        if (value != activeClientId) {
            generation++
            authJob?.cancel()
            searchJob?.cancel()
            loadMoreJob?.cancel()
            listener?.close()
            listener = null
            clearCredentials()
            activeClientId = value
            _state.value = initialState()
        } else {
            _state.update { it.copy(clientIdOverridden = overrideClientId != null, error = null) }
        }
    }

    /** Browser-based PKCE. Redirect goes to an ephemeral 127.0.0.1 listener, not a website. */
    fun authorize(openBrowser: (String) -> Unit) {
        if (closed || authJob?.isActive == true) return
        if (!configured) {
            _state.update { it.copy(error = "Enter a public Spotify Client ID to enable catalogue search.") }
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
                openBrowser(SpotifyPkce.authorizationUrl(activeClientId, redirectUri, session))
                val code = withContext(Dispatchers.IO) { bound.awaitCode(session.state) }
                val tokens = api.exchangeCode(activeClientId, code, redirectUri, session.verifier)
                if (epoch == generation && !closed) {
                    credential = tokens
                    withContext(Dispatchers.IO) { store.save(tokens, activeClientId) }
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
        loadMoreJob?.cancel()
        nextOffset = 0
        _state.update { it.copy(query = query, results = emptyList(), loading = it.authorized && query.isNotBlank(),
            loadingMore = false, hasMore = false, error = null) }
        if (query.isBlank() || !_state.value.authorized) return
        val epoch = generation
        searchJob = scope.launch {
            try {
                delay(450)
                val page = searchPageWithAuthorization(query, offset = 0)
                if (!closed && generation == epoch) {
                    nextOffset = 10
                    _state.update { it.copy(results = page.items, hasMore = page.hasMore,
                        loading = false, error = null) }
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

    /** Fetch more metadata only after the user explicitly requests it. */
    fun loadMore() {
        if (closed) return
        val current = _state.value
        if (!current.authorized || current.loading || current.loadingMore || !current.hasMore || current.query.isBlank()) return
        if (nextOffset !in 10..1000) return
        val query = current.query
        val offset = nextOffset
        val epoch = generation
        _state.update { it.copy(loadingMore = true, error = null) }
        loadMoreJob = scope.launch {
            try {
                val page = searchPageWithAuthorization(query, offset)
                if (!closed && generation == epoch && _state.value.query == query && nextOffset == offset) {
                    nextOffset = offset + 10
                    _state.update { state ->
                        state.copy(
                            results = (state.results + page.items).distinctBy { it.kind to it.id },
                            hasMore = page.hasMore && nextOffset <= 1000,
                            loadingMore = false, error = null,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!closed && generation == epoch && _state.value.query == query) {
                    if (failure is SpotifyCatalogHttpException && failure.status == 401) clearCredentials()
                    _state.update { it.copy(loadingMore = false,
                        error = failure.message ?: "Could not load more Spotify results.") }
                }
            }
        }
    }

    private suspend fun searchPageWithAuthorization(query: String, offset: Int): SpotifyCatalogPage {
        val access = validAccessToken()
        return try {
            api.searchPage(access, query, offset)
        } catch (failure: SpotifyCatalogHttpException) {
            if (failure.status != 401) throw failure
            // A server can revoke a token before its advertised expiry.
            val refreshed = refreshToken(force = true)
            api.searchPage(refreshed, query, offset)
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
        val updated = api.refresh(activeClientId, refresh).let { if (it.refreshToken == null) it.copy(refreshToken = refresh) else it }
        credential = updated
        withContext(Dispatchers.IO) { store.save(updated, activeClientId) }
        return updated.accessToken
    }

    fun disconnect() {
        if (closed) return
        generation++
        authJob?.cancel()
        listener?.close()
        listener = null
        searchJob?.cancel()
        loadMoreJob?.cancel()
        clearCredentials()
        _state.value = initialState()
    }

    private fun clearCredentials() {
        credential = null
        // Synchronous delete prevents a revoked session from being revived after recreation.
        store.clear()
        nextOffset = 0
        _state.update { it.copy(authorized = false, results = emptyList(), hasMore = false, loadingMore = false) }
    }

    override fun close() {
        closed = true
        generation++
        listener?.close()
        scope.cancel()
    }
}

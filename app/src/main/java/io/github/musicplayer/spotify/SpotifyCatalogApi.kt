package io.github.musicplayer.spotify

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SpotifyCatalogToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
)

data class SpotifyCatalogPage(
    val items: List<SpotifyCatalogItem>,
    val hasMore: Boolean,
)

class SpotifyCatalogHttpException(val status: Int, val retryAfterSeconds: Int? = null) : IOException(
    when (status) {
        401 -> "Spotify authorization expired. Reconnect your catalogue."
        403 -> "Spotify denied catalogue access. Check your app's authorized-user allowlist and subscription."
        429 -> "Spotify is limiting requests. Try again${retryAfterSeconds?.let { " in $it seconds" } ?: " later"}."
        in 500..599 -> "Spotify is temporarily unavailable (HTTP $status)."
        else -> "Spotify catalogue request failed (HTTP $status)."
    },
)

/** Official Web API metadata only: Spotify audio never passes through this client. */
class SpotifyCatalogApi(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
    private val apiBase: HttpUrl = "https://api.spotify.com/".toHttpUrl(),
    private val accountsBase: HttpUrl = "https://accounts.spotify.com/".toHttpUrl(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun exchangeCode(clientId: String, code: String, redirectUri: String, verifier: String): SpotifyCatalogToken =
        tokenRequest(FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .build())

    suspend fun refresh(clientId: String, refreshToken: String): SpotifyCatalogToken =
        tokenRequest(FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .build())

    private suspend fun tokenRequest(body: FormBody): SpotifyCatalogToken = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(accountsBase.newBuilder().addPathSegments("api/token").build())
            .post(body).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            val json = response.checkedJson()
            val access = json.stringOrNull("access_token")?.takeIf { it.isNotBlank() }
                ?: throw IOException("Spotify did not return an access token.")
            if (json.stringOrNull("token_type")?.equals("Bearer", true) != true)
                throw IOException("Spotify returned an unexpected token type.")
            val lifetime = json.get("expires_in")?.asLong?.coerceIn(1L, 86_400L)
                ?: throw IOException("Spotify did not provide a token lifetime.")
            SpotifyCatalogToken(access, json.stringOrNull("refresh_token"), clock() + lifetime * 1000)
        }
    }

    /** Compatibility entrypoint for first-page callers and Web API fixture tests. */
    suspend fun search(token: String, query: String): List<SpotifyCatalogItem> =
        searchPage(token, query).items

    /** Spotify's 2026 Search limit is 10 per type; explicit offsets reveal later pages. */
    suspend fun searchPage(token: String, query: String, offset: Int = 0): SpotifyCatalogPage = withContext(Dispatchers.IO) {
        require(offset in 0..1000 && offset % 10 == 0) { "Invalid Spotify search page offset." }
        val term = query.trim().take(200)
        if (term.isBlank()) return@withContext SpotifyCatalogPage(emptyList(), false)
        val builder = apiBase.newBuilder().addPathSegment("v1").addPathSegment("search")
            .addQueryParameter("q", term)
            .addQueryParameter("type", "track,album,artist")
            .addQueryParameter("limit", "10") // Spotify's February 2026 maximum per type.
        if (offset > 0) builder.addQueryParameter("offset", offset.toString())
        val url = builder.build()
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            val root = response.checkedJson()
            val items = buildList {
                addAll(root.resultItems("tracks", SpotifyCatalogKind.TRACK))
                addAll(root.resultItems("albums", SpotifyCatalogKind.ALBUM))
                addAll(root.resultItems("artists", SpotifyCatalogKind.ARTIST))
            }
            // Use Spotify's per-type pagination pointers as hints only. Never request
            // an untrusted URL supplied by the response: generate the next offset here.
            SpotifyCatalogPage(items, offset < 1000 && listOf("tracks", "albums", "artists")
                .any { root.hasNext(it) })
        }
    }

    private fun JsonObject.hasNext(collection: String): Boolean = runCatching {
        getAsJsonObject(collection)?.get("next")?.takeUnless { it.isJsonNull }?.asString?.isNotBlank() == true
    }.getOrDefault(false)

    private fun Response.checkedJson(): JsonObject {
        if (!isSuccessful) {
            val retry = header("Retry-After")?.toIntOrNull()?.coerceIn(0, 3_600)
            throw SpotifyCatalogHttpException(code, retry)
        }
        val size = body?.contentLength() ?: -1L
        if (size > 2_000_000) throw IOException("Spotify returned an unexpectedly large response.")
        val raw = body?.byteStream()?.use { input ->
            // Bound even chunked/unknown-length responses, not just Content-Length.
            val bytes = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                val read = input.read(bytes)
                if (read == -1) break
                if (out.size() + read > 2_000_000) throw IOException("Spotify response exceeds size limit.")
                out.write(bytes, 0, read)
            }
            out.toString(Charsets.UTF_8.name())
        } ?: throw IOException("Spotify returned an empty response.")
        return runCatching { JsonParser.parseString(raw).asJsonObject }
            .getOrElse { throw IOException("Spotify returned invalid catalogue data.", it) }
    }

    private fun JsonObject.resultItems(collection: String, kind: SpotifyCatalogKind): List<SpotifyCatalogItem> {
        val result = runCatching { getAsJsonObject(collection)?.getAsJsonArray("items") }.getOrNull()
            ?: return emptyList()
        return result.mapNotNull { element ->
            runCatching {
                val item = element.asJsonObject
                val id = item.stringOrNull("id")?.takeIf { SPOTIFY_ID.matches(it) } ?: return@runCatching null
                val title = item.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: return@runCatching null
                val album = item.objectOrNull("album")
                val artistNames = item.arrayOrNull("artists")?.mapNotNull {
                    runCatching { it.asJsonObject.stringOrNull("name") }.getOrNull()
                }.orEmpty().filter { it.isNotBlank() }
                val subtitle = when (kind) {
                    SpotifyCatalogKind.TRACK -> artistNames.joinToString(", ").ifEmpty { album?.stringOrNull("name") ?: "Track" }
                    SpotifyCatalogKind.ALBUM -> artistNames.joinToString(", ").ifEmpty { "Album" }
                    SpotifyCatalogKind.ARTIST -> "Artist"
                }
                val image = (album ?: item).arrayOrNull("images")?.firstNotNullOfOrNull { entry ->
                    runCatching { entry.asJsonObject.stringOrNull("url")?.takeIf(::spotifyImageUrlAllowed) }.getOrNull()
                }
                val duration = if (kind == SpotifyCatalogKind.TRACK) item.get("duration_ms")?.asLong else null
                SpotifyCatalogItem(id, kind, title, subtitle, image, duration)
            }.getOrNull()
        }
    }

    companion object {
        private val SPOTIFY_ID = Regex("[A-Za-z0-9]{22}")

        internal fun spotifyImageUrlAllowed(url: String): Boolean = runCatching {
            val parsed = url.toHttpUrl()
            parsed.scheme == "https" && parsed.host == "i.scdn.co" && parsed.port == 443 &&
                parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.fragment == null
        }.getOrDefault(false)
    }
}

private fun JsonObject.stringOrNull(name: String): String? =
    runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()

private fun JsonObject.objectOrNull(name: String): JsonObject? =
    runCatching { getAsJsonObject(name) }.getOrNull()

private fun JsonObject.arrayOrNull(name: String) =
    runCatching { getAsJsonArray(name) }.getOrNull()

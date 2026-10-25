package io.github.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import io.github.musicplayer.model.DownloadState
import io.github.musicplayer.model.Track
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** App-private library and downloads for local files and explicitly permitted HTTPS audio. */
class MusicRepository(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
    private val catalogUrl: String = DEFAULT_CATALOG_URL,
) : AutoCloseable {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("music-library", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val startup = CompletableDeferred<Unit>()
    private val audioDirectory = File(app.filesDir, "offline-audio")
    private var catalogTracks = emptyList<Track>()
    private var customTracks = emptyList<Track>()
    private val specifications = ConcurrentHashMap<String, DownloadSpec>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Call>()
    private val downloadedIds = ConcurrentHashMap.newKeySet<String>()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            try {
                val initial = withContext(Dispatchers.IO) {
                    audioDirectory.mkdirs()
                    audioDirectory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach(File::delete)
                    app.assets.open("catalog.json").bufferedReader().use { parseCatalog(it.readText()) }
                }
                lock.withLock {
                    val savedCatalog = runCatching { TrackJson.decodeList(prefs.getString("catalogTracks", "[]")!!) }
                        .getOrDefault(emptyList())
                    catalogTracks = (initial.first + savedCatalog).distinctBy(Track::id)
                    specifications.putAll(initial.second)
                    customTracks = runCatching { TrackJson.decodeList(prefs.getString("customTracks", "[]")!!) }
                        .getOrDefault(emptyList())
                    val savedIds = prefs.getStringSet("downloadedIds", emptySet()).orEmpty()
                    downloadedIds.addAll(savedIds.filter { audioFile(it).let { file -> file.isFile && file.length() > 0 } })
                    publishTracks()
                    saveLibrary()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _error.value = "The starter library could not load: ${error.readableMessage()}"
            } finally {
                _ready.value = true
                startup.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() = startup.await()

    fun clearError() { _error.value = null }

    fun refreshCatalog() {
        if (_refreshing.value) return
        _refreshing.value = true
        scope.launch {
            startup.await()
            try {
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(AudioUrlPolicy.validateHttps(catalogUrl)).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Catalogue returned HTTP ${response.code}.")
                        if (!response.request.url.isHttps) throw IOException("The catalogue redirected to an insecure address.")
                        val body = response.body ?: throw IOException("Catalogue was empty.")
                        if (body.contentLength() > MAX_CATALOG_BYTES) throw IOException("Catalogue is too large.")
                        val bytes = body.byteStream().use { readAtMost(it, MAX_CATALOG_BYTES + 1) }
                        if (bytes.size > MAX_CATALOG_BYTES) throw IOException("Catalogue is too large.")
                        parseCatalog(bytes.toString(Charsets.UTF_8))
                    }
                }
                lock.withLock {
                    // A refresh must not remove the bundled track or orphan an existing offline download.
                    catalogTracks = (catalogTracks.filter { it.streamUri.startsWith("asset:") } + result.first + catalogTracks.filter {
                        it.streamUri.startsWith("asset:") || it.id in downloadedIds
                    }).distinctBy(Track::id)
                    specifications.putAll(result.second)
                    publishTracks()
                    saveLibrary()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _error.value = "Couldn't refresh. Your saved library is still available. ${error.readableMessage()}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun toggleFavorite(id: String) {
        _favorites.update { current -> if (id in current) current - id else current + id }
        prefs.edit().putStringSet("favorites", _favorites.value).apply()
    }

    fun addStream(title: String, url: String, downloadAllowed: Boolean = false) {
        val normalized = try {
            AudioUrlPolicy.validateHttps(url)
        } catch (error: IllegalArgumentException) {
            _error.value = error.message
            return
        }
        scope.launch {
            startup.await()
            val id = "stream-${digest(normalized.toByteArray()).take(24)}"
            val track = Track(
                id = id,
                title = title.trim().take(120).ifBlank { Uri.parse(normalized).lastPathSegment ?: "My stream" },
                artist = "Your audio",
                album = "Added streams",
                streamUri = normalized,
                accent = 0xFF7CE6F2,
                artworkSeed = id.hashCode(),
                downloadable = downloadAllowed,
                source = "Your HTTPS stream",
                license = if (downloadAllowed) "User confirms permission to save this audio" else "Streaming only",
            )
            lock.withLock {
                customTracks = listOf(track) + customTracks.filterNot { it.id == id }
                publishTracks()
                saveLibrary()
            }
        }
    }

    fun importLocalFiles(uris: List<Uri>) {
        scope.launch {
            startup.await()
            val imported = withContext(Dispatchers.IO) {
                uris.distinct().mapNotNull { uri ->
                    try {
                        require(uri.scheme == "content") { "Choose audio with the system file picker." }
                        readLocalTrack(uri)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        _error.value = "Couldn't import a file: ${error.readableMessage()}"
                        null
                    }
                }
            }
            lock.withLock {
                customTracks = (imported + customTracks).distinctBy(Track::id)
                publishTracks()
                saveLibrary()
            }
        }
    }

    fun download(track: Track) {
        if (jobs.containsKey(track.id) || track.id in downloadedIds) return
        if (!track.downloadable || track.license.isBlank()) {
            _error.value = "This source does not allow saving. You can still play it online."
            return
        }
        val url = try {
            AudioUrlPolicy.validateHttps(track.streamUri)
        } catch (error: IllegalArgumentException) {
            _error.value = error.message
            return
        }
        _downloads.update { it + (track.id to DownloadState(track.id)) }
        jobs[track.id] = scope.launch {
            startup.await()
            try {
                val spec = specifications[track.id]
                withContext(Dispatchers.IO) { downloadFile(track, url, spec) }
                lock.withLock {
                    downloadedIds.add(track.id)
                    publishTracks()
                    saveLibrary()
                }
                _downloads.update { it + (track.id to DownloadState(track.id, 1f, false)) }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    _downloads.update { it - track.id }
                    throw error
                }
                val message = "Download failed: ${error.readableMessage()}"
                _downloads.update { it + (track.id to DownloadState(track.id, isActive = false, error = message)) }
                _error.value = message
            } finally {
                calls.remove(track.id)
                jobs.remove(track.id)
            }
        }
    }

    fun cancelDownload(id: String) {
        calls[id]?.cancel()
        jobs[id]?.cancel()
        _downloads.update { it - id }
    }

    fun removeDownload(track: Track) {
        cancelDownload(track.id)
        scope.launch {
            startup.await()
            try {
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val file = audioFile(track.id)
                        if (file.exists() && !file.delete()) throw IOException("Could not remove the saved file.")
                    }
                    downloadedIds.remove(track.id)
                    publishTracks()
                    saveLibrary()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _error.value = error.readableMessage()
            }
        }
    }

    fun removeCustomTrack(track: Track) {
        cancelDownload(track.id)
        scope.launch {
            startup.await()
            lock.withLock {
                withContext(Dispatchers.IO) { audioFile(track.id).delete() }
                downloadedIds.remove(track.id)
                customTracks = customTracks.filterNot { it.id == track.id }
                _favorites.update { it - track.id }
                prefs.edit().putStringSet("favorites", _favorites.value).apply()
                publishTracks()
                saveLibrary()
            }
        }
    }

    override fun close() {
        calls.values.forEach(Call::cancel)
        calls.clear()
        scope.cancel()
    }

    private suspend fun downloadFile(track: Track, url: String, spec: DownloadSpec?) {
        audioDirectory.mkdirs()
        val target = audioFile(track.id)
        val temporary = File(audioDirectory, "${target.name}.part")
        try {
            val call = client.newCall(Request.Builder().url(url).build())
            calls[track.id] = call
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Server returned HTTP ${response.code}.")
                if (!response.request.url.isHttps) throw IOException("Audio redirected to an insecure address.")
                val body = response.body ?: throw IOException("The server returned no audio.")
                val expectedSize = body.contentLength()
                if (expectedSize > AudioUrlPolicy.MAX_DOWNLOAD_BYTES) throw IOException("This file exceeds the 128 MB download limit.")
                var count = 0L
                val checksum = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var lastProgress = -1
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val length = input.read(buffer)
                            if (length < 0) break
                            count += length
                            if (count > AudioUrlPolicy.MAX_DOWNLOAD_BYTES) throw IOException("This file exceeds the 128 MB download limit.")
                            output.write(buffer, 0, length)
                            checksum.update(buffer, 0, length)
                            val percentage = if (expectedSize > 0) ((count * 100) / expectedSize).toInt().coerceIn(0, 99) else 0
                            if (percentage != lastProgress) {
                                lastProgress = percentage
                                _downloads.update { it + (track.id to DownloadState(track.id, percentage / 100f)) }
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (count == 0L || expectedSize > 0 && count != expectedSize) throw IOException("The audio download was incomplete.")
                if (spec?.sizeBytes != null && count != spec.sizeBytes) throw IOException("The audio size differs from the catalogue.")
                val actualHash = checksum.digest().toHex()
                if (spec?.sha256 != null && actualHash != spec.sha256) throw IOException("The audio checksum did not match. Try refreshing the catalogue.")
                val header = temporary.inputStream().use { readAtMost(it, 16) }
                if (!AudioUrlPolicy.looksLikeAudio(header)) throw IOException("This address is not a supported audio file. Use MP3, AAC, M4A, WAV, FLAC, or Ogg.")
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun readLocalTrack(uri: Uri): Track {
        val fallback = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Local audio"
        val id = "local-${digest(uri.toString().toByteArray()).take(24)}"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(app, uri)
            return Track(
                id = id,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
                    ?: fallback.substringBeforeLast('.'),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)
                    ?: "Local audio",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                streamUri = uri.toString(),
                accent = 0xFFA79BFF,
                artworkSeed = id.hashCode(),
                source = "Local file",
                license = "Your file",
            )
        } finally {
            retriever.release()
        }
    }

    private fun parseCatalog(value: String): Pair<List<Track>, Map<String, DownloadSpec>> {
        val trimmed = value.trim()
        val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("tracks")
        require(array.length() <= 1000) { "The catalogue has too many entries." }
        val specs = mutableMapOf<String, DownloadSpec>()
        val parsed = (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val track = TrackJson.decode(json).copy(offlinePath = null)
            require(track.id.isNotBlank() && track.title.isNotBlank()) { "The catalogue contains an unnamed track." }
            require(track.streamUri.startsWith("asset:///") || AudioUrlPolicy.validateHttps(track.streamUri).isNotBlank())
            val sha = json.optString("sha256").lowercase().takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            val size = json.optLong("sizeBytes", -1).takeIf { it > 0 }
            specs[track.id] = DownloadSpec(sha, size)
            track
        }.distinctBy(Track::id)
        return parsed to specs
    }

    private fun publishTracks() {
        _tracks.value = (customTracks + catalogTracks).distinctBy(Track::id).map { track ->
            track.copy(offlinePath = if (track.id in downloadedIds) audioFile(track.id).absolutePath else null)
        }
    }

    private suspend fun saveLibrary() {
        val customJson = TrackJson.encodeList(customTracks)
        val catalogJson = TrackJson.encodeList(catalogTracks)
        val savedDownloads = downloadedIds.toSet()
        withContext(Dispatchers.IO) {
            prefs.edit().putString("customTracks", customJson)
                .putString("catalogTracks", catalogJson)
                .putStringSet("downloadedIds", savedDownloads).commit()
        }
    }

    private fun audioFile(id: String) = File(audioDirectory, "${digest(id.toByteArray())}.audio")
    private data class DownloadSpec(val sha256: String?, val sizeBytes: Long?)

    companion object {
        const val DEFAULT_CATALOG_URL = "https://raw.githubusercontent.com/cheshmakzanoon-ops/music-player/main/catalog/catalog.json"
        private const val MAX_CATALOG_BYTES = 2 * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .followSslRedirects(false)
            .build()

        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
        private fun Exception.readableMessage() = message?.take(200) ?: "Please try again."
        private fun readAtMost(input: InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream(minOf(limit, 32768))
            val buffer = ByteArray(minOf(limit, 32768))
            while (output.size() < limit) {
                val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}

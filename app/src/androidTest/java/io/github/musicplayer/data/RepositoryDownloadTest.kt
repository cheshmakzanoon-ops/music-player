package io.github.musicplayer.data

import android.content.Context
import android.content.ContextWrapper
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import io.github.musicplayer.model.Track
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real TLS, filesystem, persistence, and decoder checks; no internet service or paid account needed. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class RepositoryDownloadTest {
    private lateinit var context: IsolatedContext
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var wav: ByteArray
    private var serverStopped = false
    private val repositories = mutableListOf<MusicRepository>()
    private val players = mutableListOf<ExoPlayer>()

    @Before fun setUp() {
        context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        wav = context.assets.open("audio/first-light.wav").use { it.readBytes() }
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()
    }

    @After fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) {
            players.forEach { it.release() }
            repositories.forEach { it.close() }
        }
        if (!serverStopped) server.shutdown()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        context.cleanup()
    }

    @Test fun httpsDownloadIsVerifiedPersistsAndPlaysWithServerOffline() = runBlocking {
        val repository = repository()
        val track = registerCatalogueFixture(repository)
        server.enqueue(audioResponse(wav))

        repository.download(track)
        withTimeout(15000) {
            repository.downloads.first { it[track.id]?.let { value -> !value.isActive } == true }
        }
        val completed = repository.downloads.value.getValue(track.id)
        assertEquals(null, completed.error)
        assertEquals(1f, completed.progress, 0f)
        assertEquals("/fixture.wav", server.takeRequest(3, TimeUnit.SECONDS)?.path)

        val saved = repository.tracks.value.first { it.id == track.id }
        assertNotNull(saved.offlinePath)
        assertTrue(saved.isOffline)
        val file = File(saved.offlinePath!!)
        assertEquals(wav.size.toLong(), file.length())
        assertEquals(sha256(wav), sha256(file.readBytes()))
        assertNoPartialFiles()

        repository.close()
        val originalCatalogUrl = server.url("/catalog.json").toString()
        server.shutdown()
        serverStopped = true
        val restored = MusicRepository(context, client, originalCatalogUrl).also(repositories::add)
        restored.awaitReady()
        val offlineTrack = restored.tracks.value.first { it.id == track.id }
        assertEquals(file.absolutePath, offlineTrack.offlinePath)
        assertTrue(offlineTrack.playbackUri.startsWith("file:"))
        assertEquals("asset:///audio/first-light.wav", restored.tracks.value.first { it.id == "first-light" }.streamUri)

        val player = createPlayer(offlineTrack.playbackUri)
        assertPlaybackAdvances(player)
        withContext(Dispatchers.Main) {
            assertTrue(player.duration >= 23000)
            assertEquals(Player.STATE_READY, player.playbackState)
            player.pause()
        }
    }

    @Test fun httpsStreamUsesRealNetworkAndDecoder() = runBlocking {
        server.enqueue(audioResponse(wav))
        val player = createPlayer(server.url("/stream.wav").toString(), network = true)
        assertPlaybackAdvances(player)
        assertEquals("/stream.wav", server.takeRequest(3, TimeUnit.SECONDS)?.path)
    }

    @Test fun streamingOnlySourceCannotBeDownloaded() = runBlocking {
        val repository = repository()
        val track = registerCustomStream(repository, "/stream-only.wav", allowed = false)
        repository.download(track)
        assertTrue(repository.error.value.orEmpty().contains("does not allow saving"))
        assertFalse(repository.downloads.value.containsKey(track.id))
        assertFalse(repository.tracks.value.first { it.id == track.id }.isOffline)
        assertEquals(0, server.requestCount)
        assertNoPartialFiles()
    }

    @Test fun htmlAndHttpFailuresDoNotCreateOfflineTracks() = runBlocking {
        val repository = repository()
        val htmlTrack = registerCustomStream(repository, "/html.wav")
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<!DOCTYPE html><html><body>No audio</body></html>"))
        assertDownloadFails(repository, htmlTrack, "supported audio file")

        val missingTrack = registerCustomStream(repository, "/missing.wav")
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))
        assertDownloadFails(repository, missingTrack, "HTTP 404")
        assertEquals(2, server.requestCount)
    }

    @Test fun truncatedOrChangedCatalogueAudioFailsIntegrityCheck() = runBlocking {
        val repository = repository()
        val track = registerCatalogueFixture(repository)
        server.enqueue(audioResponse(wav.copyOf(64)))
        assertDownloadFails(repository, track, "size differs")

        // The file is still a WAV with the correct length, but it no longer matches the published digest.
        val changed = wav.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        server.enqueue(audioResponse(changed))
        assertDownloadFails(repository, track, "checksum did not match")
    }

    @Test fun catalogueChecksumsAndUpdatedEntriesSurviveRestartBeforeDownload() = runBlocking {
        val repository = repository()
        val updated = registerCatalogueFixture(repository, id = "night-drive", title = "Updated night drive")
        repository.close()

        // Recreate before saving the file. Integrity data must survive separately
        // from a completed download, and a bundled ID must retain its newer URL.
        val restored = repository()
        val track = restored.tracks.value.first { it.id == "night-drive" }
        assertEquals("Updated night drive", track.title)
        assertEquals(updated.streamUri, track.streamUri)
        assertEquals(1, restored.tracks.value.count { it.id == "night-drive" })
        assertEquals("asset:///audio/first-light.wav", restored.tracks.value.first { it.id == "first-light" }.streamUri)

        server.enqueue(audioResponse(wav.copyOf(64)))
        assertDownloadFails(restored, track, "size differs")
        val changed = wav.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        server.enqueue(audioResponse(changed))
        assertDownloadFails(restored, track, "checksum did not match")
    }

    @Test fun malformedCatalogueIntegrityDoesNotReplaceTheWorkingLibrary() = runBlocking {
        val repository = repository()
        val original = repository.tracks.value
        for ((field, value, expected) in listOf(
            Triple("sha256", "not-a-sha256-digest", "invalid audio checksum"),
            Triple("sizeBytes", -10, "invalid audio size"),
        )) {
            val fixture = catalogueFixture().put(field, value)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(JSONObject().put("tracks", JSONArray().put(fixture)).toString()))
            repository.clearError()
            repository.refreshCatalog()
            withTimeout(10000) { repository.refreshing.first { !it } }
            assertEquals("/catalog.json", server.takeRequest(3, TimeUnit.SECONDS)?.path)
            assertTrue(repository.error.value.orEmpty(), repository.error.value.orEmpty().contains(expected))
            assertEquals(original, repository.tracks.value)
            assertFalse(repository.tracks.value.any { it.id == "verified-fixture" })
        }
    }

    @Test fun savedTrackCanBeRemovedWithoutLosingItsOnlineSource() = runBlocking {
        val repository = repository()
        val track = registerCatalogueFixture(repository)
        server.enqueue(audioResponse(wav))
        repository.download(track)
        val saved = withTimeout(15000) {
            repository.tracks.first { tracks -> tracks.any { it.id == track.id && it.offlinePath != null } }
                .first { it.id == track.id }
        }
        withTimeout(10000) { repository.downloads.first { it[track.id]?.progress == 1f } }
        val file = File(saved.offlinePath!!)
        repository.removeDownload(saved)
        withTimeout(10000) { repository.tracks.first { tracks -> tracks.first { it.id == track.id }.offlinePath == null } }
        assertFalse(file.exists())
        assertEquals(track.streamUri, repository.tracks.value.first { it.id == track.id }.streamUri)
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun documentImportCopiesAudioBeforeMarkingItOffline() = runBlocking {
        val providerFile = File(context.filesDir, "provider-fixture.wav").apply { writeBytes(wav) }
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "audio/wav"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("Cloud fixture.wav")) }
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
                ParcelFileDescriptor.open(providerFile, ParcelFileDescriptor.MODE_READ_ONLY)
            override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = "test.audio.provider" })
        context.testResolver = ContentResolver.wrap(provider)
        val repository = repository()
        val uri = Uri.parse("content://test.audio.provider/cloud-fixture.wav")
        repository.importLocalFiles(listOf(uri))
        val imported = withTimeout(15000) {
            repository.tracks.first { tracks -> tracks.any { it.streamUri == uri.toString() && it.offlinePath != null } }
                .first { it.streamUri == uri.toString() }
        }
        assertTrue(imported.isOffline)
        assertEquals(sha256(wav), sha256(File(imported.offlinePath!!).readBytes()))
        assertTrue(imported.durationMs >= 23000)
        assertTrue(providerFile.delete())
        assertNoPartialFiles()
        val player = createPlayer(imported.playbackUri)
        assertPlaybackAdvances(player)
        assertEquals(0, server.requestCount)
    }

    private suspend fun repository(): MusicRepository = MusicRepository(
        context,
        client,
        server.url("/catalog.json").toString(),
    ).also {
        repositories.add(it)
        it.awaitReady()
        assertEquals(null, it.error.value)
    }

    private fun catalogueFixture(id: String = "verified-fixture", title: String = "Verified fixture") = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", "Original generated test audio")
        put("durationMs", 24000)
        put("streamUri", server.url("/fixture.wav").toString())
        put("downloadable", true)
        put("license", "CC0-1.0")
        put("source", "Test fixture")
        put("sha256", sha256(wav))
        put("sizeBytes", wav.size)
    }

    private suspend fun registerCatalogueFixture(
        repository: MusicRepository,
        id: String = "verified-fixture",
        title: String = "Verified fixture",
    ): Track {
        val json = JSONObject().put("tracks", JSONArray().put(catalogueFixture(id, title)))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json.toString()))
        repository.refreshCatalog()
        withTimeout(10000) { repository.refreshing.first { !it } }
        assertEquals(null, repository.error.value)
        val track = repository.tracks.value.first { it.id == id }
        assertEquals("/catalog.json", server.takeRequest(3, TimeUnit.SECONDS)?.path)
        assertEquals("asset:///audio/first-light.wav", repository.tracks.value.first { it.id == "first-light" }.streamUri)
        return track
    }

    private suspend fun registerCustomStream(repository: MusicRepository, path: String, allowed: Boolean = true): Track {
        val url = server.url(path).toString()
        repository.addStream("My fixture", url, allowed)
        return withTimeout(10000) {
            repository.tracks.first { list -> list.any { it.streamUri == url } }.first { it.streamUri == url }
        }
    }

    private suspend fun assertDownloadFails(repository: MusicRepository, track: Track, expectedMessage: String) {
        repository.download(track)
        val failed = withTimeout(12000) {
            repository.downloads.first { states -> states[track.id]?.let { !it.isActive && it.error != null } == true }
                .getValue(track.id)
        }
        assertTrue(failed.error, failed.error.orEmpty().contains(expectedMessage))
        assertFalse(repository.tracks.value.first { it.id == track.id }.isOffline)
        assertTrue(File(context.filesDir, "offline-audio").listFiles().orEmpty().isEmpty())
        assertNoPartialFiles()
        // Let the failure handler finish its final job cleanup before retrying the same id.
        withContext(Dispatchers.Main) { }
    }

    private suspend fun createPlayer(uri: String, network: Boolean = false): ExoPlayer = withContext(Dispatchers.Main) {
        val builder = ExoPlayer.Builder(context)
        if (network) builder.setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(OkHttpDataSource.Factory(client)),
        )
        builder.build().also { player ->
            players.add(player)
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        }
    }

    private suspend fun assertPlaybackAdvances(player: ExoPlayer) {
        withTimeout(15000) {
            while (true) {
                val playing = withContext(Dispatchers.Main) {
                    player.playerError?.let { throw AssertionError("Decoder failed: ${it.errorCodeName}", it) }
                    player.isPlaying && player.currentPosition > 350L
                }
                if (playing) return@withTimeout
                delay(50)
            }
        }
    }

    private fun assertNoPartialFiles() {
        assertFalse(File(context.filesDir, "offline-audio").listFiles().orEmpty().any { it.name.endsWith(".part") })
    }

    private fun audioResponse(bytes: ByteArray) = MockResponse()
        .setHeader("Content-Type", "audio/wav")
        .setBody(Buffer().write(bytes))

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val id = UUID.randomUUID().toString()
        private val directory = File(base.cacheDir, "repository-test-$id").apply { mkdirs() }
        private val preferenceNames = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        var testResolver: ContentResolver? = null
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getContentResolver(): ContentResolver = testResolver ?: baseContext.contentResolver
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = "repository-test-$id-$name"
            preferenceNames.add(isolatedName)
            return baseContext.getSharedPreferences(isolatedName, mode)
        }
        fun cleanup() {
            preferenceNames.forEach { baseContext.deleteSharedPreferences(it) }
            directory.deleteRecursively()
        }
    }
}

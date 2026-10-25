package io.github.musicplayer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.musicplayer.spotify.SpotifyCatalogApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Album art remains Spotify-hosted, intact and limited to its published artwork CDN. */
private object SpotifyArtworkLoader {
    private val memory = LruCache<String, Bitmap>(24)
    private val http = OkHttpClient.Builder().followRedirects(false)
        .connectTimeout(7, TimeUnit.SECONDS).readTimeout(7, TimeUnit.SECONDS).build()

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!SpotifyCatalogApi.spotifyImageUrlAllowed(url)) return@withContext null
        synchronized(memory) { memory.get(url) }?.let { return@withContext it }
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful || response.body == null ||
                    response.body!!.contentLength() > 1_500_000) return@use null
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                response.body!!.byteStream().use { input ->
                    while (true) {
                        val bytes = input.read(buffer)
                        if (bytes == -1) break
                        if (output.size() + bytes > 1_500_000) throw java.io.IOException("Oversized Spotify image")
                        output.write(buffer, 0, bytes)
                    }
                }
                BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())?.also {
                    synchronized(memory) { memory.put(url, it) }
                }
            }
        }.getOrNull()
    }
}

@Composable
internal fun SpotifyCover(url: String?, title: String, modifier: Modifier = Modifier) {
    val artwork by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = url?.let { SpotifyArtworkLoader.load(it) }
    }
    Box(modifier.background(Panel), contentAlignment = Alignment.Center) {
        val image = artwork
        if (image != null) {
            Image(image.asImageBitmap(), "Spotify artwork for $title", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            CyberCity(Modifier.matchParentSize())
            Icon(Icons.Rounded.Album, null, tint = Cyan)
        }
    }
}

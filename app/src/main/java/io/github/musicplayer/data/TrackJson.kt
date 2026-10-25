package io.github.musicplayer.data

import io.github.musicplayer.model.Track
import org.json.JSONArray
import org.json.JSONObject

internal object TrackJson {
    fun encode(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("durationMs", track.durationMs)
        put("streamUri", track.streamUri)
        put("accent", track.accent)
        put("artworkSeed", track.artworkSeed)
        put("downloadable", track.downloadable)
        put("source", track.source)
        put("license", track.license)
        track.offlinePath?.let { put("offlinePath", it) }
    }

    fun decode(json: JSONObject): Track = Track(
        id = json.getString("id"),
        title = json.getString("title"),
        artist = json.optString("artist", "Unknown artist"),
        album = json.optString("album"),
        durationMs = json.optLong("durationMs", 0).coerceAtLeast(0),
        streamUri = json.getString("streamUri"),
        accent = parseAccent(json.opt("accent")),
        artworkSeed = json.optInt("artworkSeed"),
        downloadable = json.optBoolean("downloadable", false),
        source = json.optString("source", "Studio samples"),
        license = json.optString("license"),
        offlinePath = json.optString("offlinePath").takeIf { it.isNotBlank() },
    )

    fun decodeList(value: String): List<Track> {
        val array = JSONArray(value)
        return (0 until array.length()).mapNotNull { runCatching { decode(array.getJSONObject(it)) }.getOrNull() }
    }

    fun encodeList(tracks: List<Track>): String = JSONArray().apply {
        tracks.forEach { put(encode(it)) }
    }.toString()

    private fun parseAccent(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is String -> runCatching {
            val color = value.removePrefix("#").removePrefix("0x")
            color.toLong(16).let { if (color.length <= 6) it or 0xFF000000L else it }
        }.getOrDefault(0xFFB6FF64)
        else -> 0xFFB6FF64
    }
}

package io.github.musicplayer.data

import java.net.URI

/** Downloads are direct, permitted audio files. This never resolves protected service tracks. */
object AudioUrlPolicy {
    const val MAX_DOWNLOAD_BYTES = 128L * 1024L * 1024L

    fun validateHttps(value: String): String {
        val trimmed = value.trim()
        require(trimmed.length <= 8192) { "That audio address is too long." }
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid HTTPS audio address.")
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Use a secure HTTPS address for the audio file."
        }
        require(uri.userInfo == null && uri.fragment == null) {
            "Use an audio address without embedded credentials or a fragment."
        }
        return uri.toASCIIString()
    }

    fun looksLikeAudio(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        fun starts(value: String, at: Int = 0) = bytes.size >= at + value.length &&
            value.indices.all { bytes[at + it].toInt() and 0xff == value[it].code }
        return starts("RIFF") && starts("WAVE", 8) || starts("OggS") || starts("fLaC") ||
            starts("ID3") || starts("ftyp", 4) ||
            (bytes[0].toInt() and 0xff == 0xff && bytes[1].toInt() and 0xe0 == 0xe0)
    }
}

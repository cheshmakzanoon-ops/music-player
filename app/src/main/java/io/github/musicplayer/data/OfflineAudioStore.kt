package io.github.musicplayer.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Shared storage identity for repository writes and playback-time source resolution. */
class OfflineAudioStore(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("music-library", Context.MODE_PRIVATE)
    val directory = File(app.filesDir, "offline-audio")

    fun fileFor(id: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(directory, "$hash.audio")
    }

    fun registeredFile(id: String): File? {
        if (id !in preferences.getStringSet("downloadedIds", emptySet()).orEmpty()) return null
        return fileFor(id).takeIf { it.isFile && it.length() > 0L }
    }

    /** Called only after saved library metadata has loaded, so valid downloads are retained. */
    fun reconcile(savedIds: Set<String>): Set<String> {
        directory.mkdirs()
        val surviving = savedIds.filterTo(mutableSetOf()) { fileFor(it).let { file -> file.isFile && file.length() > 0L } }
        val registeredNames = surviving.mapTo(mutableSetOf()) { fileFor(it).name }
        directory.listFiles().orEmpty().forEach { file ->
            val targetName = file.name.substringBefore(".audio") + ".audio"
            val active = File(directory, targetName).absolutePath in activeWrites
            if (!active && (file.name.matches(PART_NAME) || file.name.matches(AUDIO_NAME) && file.name !in registeredNames)) {
                file.delete()
            }
        }
        return surviving
    }

    internal fun beginWrite(id: String) { activeWrites.add(fileFor(id).absolutePath) }
    internal fun finishWrite(id: String) { activeWrites.remove(fileFor(id).absolutePath) }

    private companion object {
        val activeWrites = ConcurrentHashMap.newKeySet<String>()
        val AUDIO_NAME = Regex("[0-9a-f]{64}\\.audio")
        val PART_NAME = Regex("[0-9a-f]{64}\\.audio(?:\\.import)?\\.part")
    }
}

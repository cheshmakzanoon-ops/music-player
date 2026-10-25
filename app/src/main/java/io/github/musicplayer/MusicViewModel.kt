package io.github.musicplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.musicplayer.data.MusicRepository
import io.github.musicplayer.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MusicRepository(application)
    val player = PlayerController(application)
    private val preferences = application.getSharedPreferences("appearance", 0)
    private val _reduceMotion = MutableStateFlow(preferences.getBoolean("reduce_motion", false))
    val reduceMotion = _reduceMotion.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setReduceMotion(reduce: Boolean) {
        _reduceMotion.value = reduce
        preferences.edit().putBoolean("reduce_motion", reduce).apply()
    }

    fun showMessage(message: String) { _message.value = message }

    fun dismissMessage() {
        _message.value = null
        repository.clearError()
        player.dismissError()
    }

    fun importFiles(uris: List<Uri>) { repository.importLocalFiles(uris) }

    override fun onCleared() {
        player.close()
        repository.close()
        super.onCleared()
    }
}

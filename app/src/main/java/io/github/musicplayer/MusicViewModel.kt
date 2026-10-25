package io.github.musicplayer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.musicplayer.data.MusicRepository
import io.github.musicplayer.playback.PlayerController
import io.github.musicplayer.spotify.SpotifyCatalogController
import io.github.musicplayer.spotify.SpotifyConfiguration
import io.github.musicplayer.spotify.SpotifyRemoteController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    val repository = MusicRepository(application)
    val player = PlayerController(application)
    val spotify = SpotifyRemoteController(application, SpotifyConfiguration(
        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
        redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
    ))
    val spotifyCatalog = SpotifyCatalogController(application, BuildConfig.SPOTIFY_CLIENT_ID)
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
        spotify.close()
        spotifyCatalog.close()
        repository.close()
        super.onCleared()
    }
}

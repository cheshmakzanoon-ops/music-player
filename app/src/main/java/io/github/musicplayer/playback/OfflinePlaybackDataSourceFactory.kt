package io.github.musicplayer.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import io.github.musicplayer.data.OfflineAudioStore

/** Resolve saved audio when it is opened, even if the queue predates a download or removal. */
@OptIn(UnstableApi::class)
class OfflinePlaybackDataSourceFactory(
    context: Context,
    upstream: DataSource.Factory = DefaultDataSource.Factory(context),
) : DataSource.Factory {
    private val store = OfflineAudioStore(context)
    private val delegate = ResolvingDataSource.Factory(upstream) { spec ->
        val saved = spec.key?.let(store::registeredFile)
        if (saved == null) spec else spec.withUri(Uri.fromFile(saved))
    }

    override fun createDataSource(): DataSource = delegate.createDataSource()
}

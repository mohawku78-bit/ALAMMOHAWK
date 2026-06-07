package com.example.integratedbpmmeter.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings

enum class MediaTransportCommand {
    Previous,
    PlayPause,
    Next
}

data class NowPlayingTrack(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val packageName: String,
    val playbackState: Int?,
    val artwork: Bitmap? = null,
    val mediaUri: String? = null
) {
    val isPlaying: Boolean
        get() = playbackState == PlaybackState.STATE_PLAYING
}

class MediaSessionReader(private val context: Context) {
    private val appContext = context.applicationContext
    private val listenerComponent = ComponentName(appContext, NowPlayingService::class.java)

    fun hasNotificationListenerAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()

        val flattened = listenerComponent.flattenToString()
        val shortName = listenerComponent.flattenToShortString()
        return enabled.split(':').any { it.equals(flattened, ignoreCase = true) || it.equals(shortName, ignoreCase = true) }
    }

    fun readCurrentTrack(): NowPlayingTrack? {
        return activeControllers()
            .mapNotNull { it.toTrackOrNull() }
            .firstOrNull()
    }

    fun sendTransportCommand(command: MediaTransportCommand): Boolean {
        val controller = activeControllers().firstOrNull() ?: return false
        return runCatching {
            when (command) {
                MediaTransportCommand.Previous -> controller.transportControls.skipToPrevious()
                MediaTransportCommand.PlayPause -> {
                    if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                }
                MediaTransportCommand.Next -> controller.transportControls.skipToNext()
            }
        }.isSuccess
    }

    private fun activeControllers(): List<MediaController> {
        val manager = appContext.getSystemService(MediaSessionManager::class.java) ?: return emptyList()
        val sessions = runCatching { manager.getActiveSessions(listenerComponent) }.getOrNull().orEmpty()
        return sessions.sortedWith(
            compareByDescending<MediaController> { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .thenByDescending { it.metadata != null }
        )
    }

    private fun MediaController.toTrackOrNull(): NowPlayingTrack? {
        return runCatching {
            val metadata = metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ?.takeIf { it > 0L }
            val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val mediaUri = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
                ?: metadata?.description?.mediaUri?.toString()

            if (title == null && artist == null && album == null && playbackState == null) {
                null
            } else {
                NowPlayingTrack(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    packageName = packageName,
                    playbackState = playbackState?.state,
                    artwork = artwork,
                    mediaUri = mediaUri?.takeIf { it.isNotBlank() }
                )
            }
        }.getOrNull()
    }
}

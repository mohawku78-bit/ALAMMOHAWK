package com.example.integratedbpmmeter.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat

class LocalAudioResolver(private val context: Context) {
    private val appContext = context.applicationContext

    fun hasAudioReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, requiredPermission()) == PackageManager.PERMISSION_GRANTED
    }

    fun resolveTrackUri(
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long? = null
    ): String? {
        val titleToken = title.bestLocalAudioSearchToken() ?: return null
        @Suppress("DEPRECATION")
        val dataColumn = MediaStore.Audio.Media.DATA
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.MediaColumns.DISPLAY_NAME,
            dataColumn
        )
        val selection = """
            ${MediaStore.Audio.Media.IS_MUSIC} != 0 AND
            (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?)
        """.trimIndent()
        val args = arrayOf(titleToken.toLikeArg(), titleToken.toLikeArg())

        return queryBestLocalAudio(
            projection = projection,
            selection = selection,
            args = args,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs
        ) ?: queryBestLocalAudio(
            projection = projection,
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            args = null,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs
        )
    }

    fun resolvePickedFileUri(uri: Uri): String {
        return mediaStoreAudioUriFromDocument(uri)?.let { mediaUri ->
            queryDataPath(mediaUri) ?: mediaUri.toString()
        } ?: queryDataPath(uri) ?: uri.toString()
    }

    private fun queryBestLocalAudio(
        projection: Array<String>,
        selection: String,
        args: Array<String>?,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long?
    ): String? {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val dataColumn = MediaStore.Audio.Media.DATA
        return runCatching {
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataIndex = cursor.getColumnIndex(dataColumn)

                var best: LocalAudioMatch? = null
                while (cursor.moveToNext()) {
                    val candidate = LocalAudioCandidate(
                        id = cursor.getLong(idIndex),
                        title = cursor.getStringOrNull(titleIndex),
                        artist = cursor.getStringOrNull(artistIndex),
                        album = cursor.getStringOrNull(albumIndex),
                        durationMs = cursor.getLongOrNull(durationIndex),
                        displayName = cursor.getStringOrNull(displayNameIndex),
                        dataPath = cursor.getStringOrNull(dataIndex)
                    )
                    val score = candidate.scoreAgainst(title, artist, album, durationMs)
                    if (score >= MIN_LOCAL_AUDIO_MATCH_SCORE && (best == null || score > best.score)) {
                        best = LocalAudioMatch(candidate, score)
                    }
                }

                best?.candidate?.let { match ->
                    match.dataPath?.takeIf { it.isNotBlank() }
                        ?: ContentUris.withAppendedId(collection, match.id).toString()
                }
            }
        }.getOrNull()
    }

    private fun mediaStoreAudioUriFromDocument(uri: Uri): Uri? {
        if (!DocumentsContract.isDocumentUri(appContext, uri)) return null
        if (uri.authority != "com.android.providers.media.documents") return null
        val parts = runCatching { DocumentsContract.getDocumentId(uri).split(':') }.getOrNull()
        val type = parts?.getOrNull(0)
        val id = parts?.getOrNull(1)?.toLongOrNull()
        return if (type == "audio" && id != null) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        } else {
            null
        }
    }

    private fun queryDataPath(uri: Uri): String? {
        @Suppress("DEPRECATION")
        val dataColumn = MediaStore.Audio.Media.DATA
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(dataColumn), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(dataColumn)
                if (index >= 0 && cursor.moveToFirst()) cursor.getStringOrNull(index) else null
            }
        }.getOrNull()
    }

    companion object {
        fun requiredPermission(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
    }
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? {
    if (index < 0 || isNull(index)) return null
    return getString(index)?.takeIf { it.isNotBlank() }
}

private fun android.database.Cursor.getLongOrNull(index: Int): Long? {
    if (index < 0 || isNull(index)) return null
    return getLong(index).takeIf { it > 0L }
}

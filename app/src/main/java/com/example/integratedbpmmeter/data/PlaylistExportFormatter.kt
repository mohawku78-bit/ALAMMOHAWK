package com.example.integratedbpmmeter.data

import java.util.Locale

fun List<BpmRecord>.toM3u8Playlist(): String {
    val playlistRecords = this
        .filter { !it.fileUri.isNullOrBlank() }
        .distinctBy { it.fileUri }
    return buildString {
        appendLine("#EXTM3U")
        playlistRecords.forEach { record ->
            val title = listOfNotNull(record.artist, record.title)
                .joinToString(" - ")
                .ifBlank { record.title }
            appendLine("#EXTINF:-1,${title.escapeM3uLine()} (${record.bpm.formatPlaylistBpm()} BPM)")
            appendLine(record.fileUri.orEmpty())
        }
    }
}

private fun String.escapeM3uLine(): String {
    return replace('\n', ' ').replace('\r', ' ').trim()
}

private fun Double.formatPlaylistBpm(): String {
    val rounded = kotlin.math.round(this)
    return if (kotlin.math.abs(this - rounded) < 0.01) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this)
    }
}

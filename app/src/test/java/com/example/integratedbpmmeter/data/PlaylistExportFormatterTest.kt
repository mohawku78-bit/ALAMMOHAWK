package com.example.integratedbpmmeter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistExportFormatterTest {
    @Test
    fun m3u8SkipsDuplicateFileUris() {
        val first = record(title = "Long Green", fileUri = "content://media/external/audio/media/1001")
        val duplicate = first.copy(id = 2L, bpm = 129.0)
        val other = record(
            id = 3L,
            title = "She's Electric",
            artist = "Oasis",
            fileUri = "content://media/external/audio/media/806"
        )

        val text = listOf(first, duplicate, other).toM3u8Playlist()

        assertEquals(2, Regex("^#EXTINF", RegexOption.MULTILINE).findAll(text).count())
        assertEquals(1, Regex("content://media/external/audio/media/1001").findAll(text).count())
        assertTrue(text.contains("Oasis - She's Electric"))
    }

    @Test
    fun m3u8OmitsRecordsWithoutLocalFilesAndEscapesMetadataLines() {
        val text = listOf(
            record(title = "No File", fileUri = null),
            record(title = "Line\nBreak", artist = "Artist\rName", fileUri = "content://track")
        ).toM3u8Playlist()

        assertFalse(text.contains("No File"))
        assertTrue(text.contains("Artist Name - Line Break"))
    }

    private fun record(
        id: Long = 1L,
        title: String,
        artist: String? = "Artist",
        fileUri: String?
    ): BpmRecord {
        return BpmRecord(
            id = id,
            title = title,
            artist = artist,
            bpm = 128.2,
            sourceType = BpmSourceType.NOW_PLAYING,
            sourceAppPackage = "com.sec.android.app.music",
            fileUri = fileUri,
            confidence = 0.82,
            createdAt = 1L
        )
    }
}

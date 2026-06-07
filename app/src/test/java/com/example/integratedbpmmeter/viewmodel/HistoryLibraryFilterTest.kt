package com.example.integratedbpmmeter.viewmodel

import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLibraryFilterTest {
    @Test
    fun runningFilterIncludesDirectAndDoubleTimeWorkoutTempos() {
        val directRunning = record(title = "Direct", bpm = 170.0)
        val doubleTimeRunning = record(title = "Double", bpm = 82.0)
        val generalTempo = record(title = "General", bpm = 128.0)

        val result = listOf(directRunning, doubleTimeRunning, generalTempo)
            .filterBySmartList(HistoryListFilter.RUNNING)

        assertEquals(listOf("Direct", "Double"), result.map { it.title })
    }

    @Test
    fun joggingFilterIncludesLowTempoDoubleTimeMatches() {
        val directJogging = record(title = "Jog", bpm = 145.0)
        val doubleTimeJogging = record(title = "Low", bpm = 72.0)
        val highPace = record(title = "High", bpm = 170.0)

        val result = listOf(directJogging, doubleTimeJogging, highPace)
            .filterBySmartList(HistoryListFilter.JOGGING)

        assertEquals(listOf("Jog", "Low"), result.map { it.title })
    }

    @Test
    fun reviewFilterKeepsOnlyUntrustedEstimates() {
        val verifiedTap = record(
            title = "Tap",
            sourceType = BpmSourceType.TAP,
            manuallyVerified = true
        )
        val publicReference = record(
            title = "Public",
            sourceType = BpmSourceType.PUBLIC_REFERENCE
        )
        val fileEstimate = record(
            title = "File",
            sourceType = BpmSourceType.FILE_ANALYSIS
        )
        val micEstimate = record(
            title = "Mic",
            sourceType = BpmSourceType.MIC_CAPTURE
        )

        val result = listOf(verifiedTap, publicReference, fileEstimate, micEstimate)
            .filterBySmartList(HistoryListFilter.REVIEW)

        assertEquals(listOf("File", "Mic"), result.map { it.title })
    }

    @Test
    fun recentFilterKeepsNewestThirtyRecords() {
        val records = (1L..35L).map { index ->
            record(title = "Track $index", createdAt = index)
        }

        val result = records.filterBySmartList(HistoryListFilter.RECENT)

        assertEquals(30, result.size)
        assertEquals("Track 35", result.first().title)
        assertEquals("Track 6", result.last().title)
    }

    @Test
    fun sourceFiltersSeparateSamsungYouTubeLocalAndOther() {
        val samsung = record(sourceAppPackage = "com.sec.android.app.music")
        val samsungModern = record(sourceAppPackage = "com.samsung.android.app.music")
        val youtubeMusic = record(sourceAppPackage = "com.google.android.apps.youtube.music")
        val localFile = record(fileUri = "content://media/external/audio/media/1")
        val otherApp = record(sourceAppPackage = "com.example.player")

        assertTrue(samsung.matchesSourceFilter(HistorySourceFilter.SAMSUNG_MUSIC))
        assertTrue(samsungModern.matchesSourceFilter(HistorySourceFilter.SAMSUNG_MUSIC))
        assertTrue(youtubeMusic.matchesSourceFilter(HistorySourceFilter.YOUTUBE_MUSIC))
        assertTrue(localFile.matchesSourceFilter(HistorySourceFilter.LOCAL_FILE))
        assertTrue(otherApp.matchesSourceFilter(HistorySourceFilter.OTHER))

        assertFalse(youtubeMusic.matchesSourceFilter(HistorySourceFilter.SAMSUNG_MUSIC))
        assertFalse(localFile.matchesSourceFilter(HistorySourceFilter.OTHER))
        assertFalse(samsung.matchesSourceFilter(HistorySourceFilter.OTHER))
    }

    private fun record(
        title: String = "Track",
        bpm: Double = 128.0,
        sourceType: BpmSourceType = BpmSourceType.NOW_PLAYING,
        sourceAppPackage: String? = null,
        fileUri: String? = null,
        manuallyVerified: Boolean = false,
        createdAt: Long = 0L
    ): BpmRecord {
        return BpmRecord(
            title = title,
            artist = "Artist",
            bpm = bpm,
            sourceType = sourceType,
            sourceAppPackage = sourceAppPackage,
            fileUri = fileUri,
            confidence = 0.8,
            manuallyVerified = manuallyVerified,
            createdAt = createdAt
        )
    }
}

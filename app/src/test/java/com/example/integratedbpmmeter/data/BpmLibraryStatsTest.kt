package com.example.integratedbpmmeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BpmLibraryStatsTest {
    @Test
    fun statsCountReviewAndSourceWork() {
        val records = listOf(
            record(
                sourceType = BpmSourceType.FILE_ANALYSIS,
                fileUri = null,
                sourceAppPackage = null,
                manuallyVerified = false
            ),
            record(
                sourceType = BpmSourceType.NOW_PLAYING,
                fileUri = "content://audio/1",
                sourceAppPackage = "com.sec.android.app.music",
                manuallyVerified = true
            ),
            record(
                sourceType = BpmSourceType.NOW_PLAYING,
                fileUri = null,
                sourceAppPackage = "com.samsung.android.app.music",
                manuallyVerified = true
            ),
            record(
                sourceType = BpmSourceType.PUBLIC_REFERENCE,
                fileUri = null,
                sourceAppPackage = "com.google.android.apps.youtube.music",
                manuallyVerified = false
            )
        )

        val stats = records.toBpmLibraryStats()

        assertEquals(4, stats.totalRecords)
        assertEquals(1, stats.reviewCount)
        assertEquals(2, stats.manuallyVerifiedCount)
        assertEquals(1, stats.localFileCount)
        assertEquals(2, stats.samsungMusicCount)
        assertEquals(1, stats.samsungMissingFileCount)
        assertEquals(1, stats.youtubeMusicCount)
    }

    private fun record(
        sourceType: BpmSourceType,
        fileUri: String?,
        sourceAppPackage: String?,
        manuallyVerified: Boolean
    ): BpmRecord {
        return BpmRecord(
            title = "Track",
            artist = "Artist",
            bpm = 128.0,
            sourceType = sourceType,
            sourceAppPackage = sourceAppPackage,
            fileUri = fileUri,
            confidence = 0.8,
            manuallyVerified = manuallyVerified,
            createdAt = 0L
        )
    }
}

package com.example.integratedbpmmeter.audio

import com.example.integratedbpmmeter.data.BpmSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileEstimateComparatorTest {
    @Test
    fun fileEstimateShowsCloseTapMatch() {
        val labels = fileEstimateComparisonLabels(
            candidates = listOf(BpmCandidate(128.2, 0.82)),
            sources = listOf(BpmSourceType.FILE_ANALYSIS),
            tapBpm = 128.8
        )

        assertEquals("Tap match", labels.first())
    }

    @Test
    fun fileEstimateShowsReferenceDelta() {
        val labels = fileEstimateComparisonLabels(
            candidates = listOf(BpmCandidate(126.0, 0.78)),
            sources = listOf(BpmSourceType.FILE_ANALYSIS),
            tapBpm = null,
            publicReferences = listOf(BpmCandidate(128.4, 0.9))
        )

        assertEquals("Reference +2.4 BPM", labels.first())
    }

    @Test
    fun fileEstimateShowsDoubleTimeFamilyInsteadOfHardMismatch() {
        val labels = fileEstimateComparisonLabels(
            candidates = listOf(BpmCandidate(180.0, 0.82)),
            sources = listOf(BpmSourceType.FILE_ANALYSIS),
            tapBpm = 90.0
        )

        assertEquals("Tap double-time family", labels.first())
    }

    @Test
    fun trustedCandidatesExplainTheirSource() {
        val labels = fileEstimateComparisonLabels(
            candidates = listOf(
                BpmCandidate(128.0, 0.9),
                BpmCandidate(129.0, 0.8)
            ),
            sources = listOf(BpmSourceType.PUBLIC_REFERENCE, BpmSourceType.FILE_ANALYSIS),
            tapBpm = null
        )

        assertEquals("Public reference", labels[0])
        assertEquals("Reference match", labels[1])
    }

    @Test
    fun missingReferenceKeepsTapCheckLanguage() {
        val labels = fileEstimateComparisonLabels(
            candidates = listOf(BpmCandidate(112.0, 0.82)),
            sources = listOf(BpmSourceType.FILE_ANALYSIS),
            tapBpm = null
        )

        assertEquals("Tap-check needed", labels.first())
    }

    @Test
    fun tapSummaryShowsCloseMatch() {
        val summary = fileEstimateTapSummary(
            candidates = listOf(BpmCandidate(128.2, 0.82)),
            sources = listOf(BpmSourceType.FILE_ANALYSIS),
            tapBpm = 128.7
        )

        assertEquals("Auto matches tap", summary?.title)
        assertFalse(summary?.isWarning ?: true)
    }

    @Test
    fun tapSummaryShowsSmallDelta() {
        val summary = fileEstimateTapSummary(
            candidates = listOf(BpmCandidate(126.0, 0.82)),
            sources = listOf(BpmSourceType.FILE_ANALYSIS),
            tapBpm = 128.4
        )

        assertEquals("2.4 BPM from tap", summary?.title)
        assertFalse(summary?.isWarning ?: true)
    }

    @Test
    fun tapSummaryShowsDoubleTimeFamily() {
        val summary = fileEstimateTapSummary(
            candidates = listOf(BpmCandidate(180.0, 0.82)),
            sources = listOf(BpmSourceType.FILE_ANALYSIS),
            tapBpm = 90.0
        )

        assertEquals("Double-time family", summary?.title)
        assertFalse(summary?.isWarning ?: true)
    }

    @Test
    fun tapSummaryWarnsWhenEstimateDiffersFromTap() {
        val summary = fileEstimateTapSummary(
            candidates = listOf(BpmCandidate(160.0, 0.82)),
            sources = listOf(BpmSourceType.FILE_ANALYSIS),
            tapBpm = 128.0
        )

        assertEquals("Estimate differs from tap", summary?.title)
        assertTrue(summary?.isWarning ?: false)
    }

    @Test
    fun tapSummaryIgnoresReferenceOnlyCandidates() {
        val summary = fileEstimateTapSummary(
            candidates = listOf(BpmCandidate(128.0, 0.9)),
            sources = listOf(BpmSourceType.PUBLIC_REFERENCE),
            tapBpm = 128.0
        )

        assertNull(summary)
    }
}

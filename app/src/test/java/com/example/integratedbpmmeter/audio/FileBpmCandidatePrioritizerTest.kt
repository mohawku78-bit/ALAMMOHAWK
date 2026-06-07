package com.example.integratedbpmmeter.audio

import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBpmCandidatePrioritizerTest {
    @Test
    fun manuallyVerifiedSavedBpmBeatsRawFileAnalysis() {
        val result = FileBpmCandidatePrioritizer.prioritize(
            savedReferences = listOf(
                record(
                    bpm = 128.2,
                    sourceType = BpmSourceType.NOW_PLAYING,
                    confidence = 0.62,
                    manuallyVerified = true
                )
            ),
            analysisCandidates = listOf(BpmCandidate(100.0, 0.66))
        )

        assertTrue(result.usedSavedReference)
        assertEquals(128.2, result.candidates[0].bpm, 0.001)
        assertEquals(BpmSourceType.NOW_PLAYING, result.sources[0])
        assertEquals("Verified BPM", result.reasonLabels[0])
        assertTrue(result.candidates[0].confidence >= 0.86)
        assertEquals(100.0, result.candidates[1].bpm, 0.001)
    }

    @Test
    fun previousFileAnalysisRecordsAreNotPromotedAsReferences() {
        val result = FileBpmCandidatePrioritizer.prioritize(
            savedReferences = listOf(
                record(
                    bpm = 99.8,
                    sourceType = BpmSourceType.FILE_ANALYSIS,
                    confidence = 0.95,
                    manuallyVerified = false
                )
            ),
            analysisCandidates = listOf(BpmCandidate(128.0, 0.70))
        )

        assertFalse(result.usedSavedReference)
        assertEquals(listOf(BpmSourceType.FILE_ANALYSIS), result.sources)
        assertEquals(128.0, result.candidates.first().bpm, 0.001)
    }

    @Test
    fun trustedPublicReferenceBeatsRawFileAnalysisWhenNoSavedBpmExists() {
        val result = FileBpmCandidatePrioritizer.prioritize(
            savedReferences = emptyList(),
            publicReferences = listOf(BpmCandidate(126.4, 0.94)),
            analysisCandidates = listOf(BpmCandidate(100.0, 0.66))
        )

        assertFalse(result.usedSavedReference)
        assertTrue(result.usedPublicReference)
        assertEquals(BpmSourceType.PUBLIC_REFERENCE, result.sources[0])
        assertEquals(126.4, result.candidates[0].bpm, 0.001)
        assertEquals(100.0, result.candidates[1].bpm, 0.001)
    }

    @Test
    fun weakPublicReferenceDoesNotBeatRawFileAnalysis() {
        val result = FileBpmCandidatePrioritizer.prioritize(
            savedReferences = emptyList(),
            publicReferences = listOf(BpmCandidate(126.4, 0.42)),
            analysisCandidates = listOf(BpmCandidate(100.0, 0.66))
        )

        assertFalse(result.usedSavedReference)
        assertFalse(result.usedPublicReference)
        assertEquals(BpmSourceType.FILE_ANALYSIS, result.sources[0])
        assertEquals(100.0, result.candidates.first().bpm, 0.001)
    }

    @Test
    fun duplicatePublicReferenceKeepsSavedReferenceSource() {
        val result = FileBpmCandidatePrioritizer.prioritize(
            savedReferences = listOf(
                record(
                    bpm = 128.2,
                    sourceType = BpmSourceType.TAP,
                    manuallyVerified = true
                )
            ),
            publicReferences = listOf(BpmCandidate(128.6, 0.94)),
            analysisCandidates = listOf(BpmCandidate(100.0, 0.66))
        )

        assertTrue(result.usedSavedReference)
        assertFalse(result.usedPublicReference)
        assertEquals(BpmSourceType.TAP, result.sources[0])
        assertEquals(128.2, result.candidates[0].bpm, 0.001)
        assertEquals(BpmSourceType.FILE_ANALYSIS, result.sources[1])
    }

    @Test
    fun duplicateAnalysisTempoIsRemovedBehindSavedReference() {
        val result = FileBpmCandidatePrioritizer.prioritize(
            savedReferences = listOf(
                record(
                    bpm = 128.2,
                    sourceType = BpmSourceType.TAP,
                    manuallyVerified = true
                )
            ),
            analysisCandidates = listOf(
                BpmCandidate(128.6, 0.92),
                BpmCandidate(64.0, 0.55)
            )
        )

        assertEquals(2, result.candidates.size)
        assertEquals(128.2, result.candidates[0].bpm, 0.001)
        assertEquals(64.0, result.candidates[1].bpm, 0.001)
    }

    @Test
    fun rawAnalysisReasonsShowReferenceFamilyMatch() {
        val result = FileBpmCandidatePrioritizer.prioritize(
            savedReferences = emptyList(),
            publicReferences = listOf(BpmCandidate(96.0, 0.92)),
            analysisCandidates = listOf(
                BpmCandidate(192.0, 0.90),
                BpmCandidate(96.0, 0.56)
            ),
            agreementScore = 0.52,
            segmentsAnalyzed = 2
        )

        assertEquals(BpmSourceType.PUBLIC_REFERENCE, result.sources[0])
        assertEquals("Reference match", result.reasonLabels[0])
        assertTrue(result.reasonLabels.drop(1).any { it == "Reference family match" })
    }

    private fun record(
        bpm: Double,
        sourceType: BpmSourceType,
        confidence: Double = 0.8,
        manuallyVerified: Boolean = false
    ): BpmRecord {
        return BpmRecord(
            title = "She's Electric",
            artist = "Oasis",
            bpm = bpm,
            sourceType = sourceType,
            confidence = confidence,
            manuallyVerified = manuallyVerified,
            createdAt = 1L
        )
    }
}

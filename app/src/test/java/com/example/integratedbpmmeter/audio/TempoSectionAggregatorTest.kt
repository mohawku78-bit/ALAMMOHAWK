package com.example.integratedbpmmeter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoSectionAggregatorTest {
    @Test
    fun repeatedFileSectionTempoBeatsOneSectionBurst() {
        val burst = section(0L, 192.0, 0.96)
        val stableA = section(90_000_000L, 96.0, 0.68)
        val stableB = section(150_000_000L, 96.5, 0.66)

        val consensus = TempoSectionAggregator.aggregate(listOf(burst, stableA, stableB))

        assertEquals(96.0, consensus.recommendedBpm ?: 0.0, 2.0)
        assertTrue(consensus.agreementScore >= 0.66)
        assertTrue(
            TempoSectionAggregator.shouldPreferConsensus(
                consensus = consensus,
                currentBest = burst.result,
                sections = listOf(burst, stableA, stableB)
            )
        )
    }

    @Test
    fun singleFileSectionConsensusIsNotPreferred() {
        val only = section(0L, 128.0, 0.88)
        val consensus = TempoSectionAggregator.aggregate(listOf(only))

        assertFalse(
            TempoSectionAggregator.shouldPreferConsensus(
                consensus = consensus,
                currentBest = only.result,
                sections = listOf(only)
            )
        )
        assertTrue(consensus.engineWarnings.any { it.contains("Only one file section") })
    }

    @Test
    fun unrelatedSectionsAreFlaggedAsLowAgreement() {
        val consensus = TempoSectionAggregator.aggregate(
            listOf(
                section(0L, 90.0, 0.70),
                section(60_000_000L, 124.0, 0.72),
                section(120_000_000L, 171.0, 0.73)
            )
        )

        assertTrue(consensus.engineWarnings.any { it.contains("only one file section", ignoreCase = true) })
        assertTrue(consensus.agreementScore < 0.45)
    }

    private fun section(startUs: Long, bpm: Double, confidence: Double): SectionTempoAnalysis {
        return SectionTempoAnalysis(
            startUs = startUs,
            result = TempoAnalysisResult(
                candidates = listOf(BpmCandidate(bpm, confidence)),
                engineName = "test",
                diagnostics = "test",
                segmentsAnalyzed = 3,
                agreementScore = 0.67,
                tempoFamily = tempoFamilyLabel(bpm)
            )
        )
    }
}

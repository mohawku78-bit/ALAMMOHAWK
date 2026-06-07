package com.example.integratedbpmmeter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoEngineTest {
    @Test
    fun normalizeTempoFamiliesRemovesHalfAndDoubleDuplicates() {
        val candidates = normalizeTempoFamilies(
            listOf(
                BpmCandidate(128.0, 0.95),
                BpmCandidate(64.0, 0.90),
                BpmCandidate(256.0, 0.80),
                BpmCandidate(122.0, 0.70)
            )
        )

        assertEquals(2, candidates.size)
        assertEquals(128.0, candidates[0].bpm, 0.01)
        assertTrue(candidates.any { it.bpm == 122.0 })
    }

    @Test
    fun normalizeTempoFamiliesKeeps180AsValidRunningTempo() {
        val candidates = normalizeTempoFamilies(
            listOf(
                BpmCandidate(180.0, 0.98),
                BpmCandidate(90.0, 0.62),
                BpmCandidate(122.0, 0.58)
            )
        )

        assertEquals(180.0, candidates[0].bpm, 1.0)
    }

    @Test
    fun normalizeTempoFamiliesKeepsModeratelyFastTemposWhenNotObviousDoubleTime() {
        val candidates = normalizeTempoFamilies(
            listOf(
                BpmCandidate(160.0, 0.94),
                BpmCandidate(124.0, 0.55)
            )
        )

        assertEquals(160.0, candidates[0].bpm, 1.0)
    }

    @Test
    fun normalizeTempoFamiliesDoesNotAverageHalfDoubleIntoFakeMiddleTempo() {
        val candidates = normalizeTempoFamilies(
            listOf(
                BpmCandidate(160.0, 0.94),
                BpmCandidate(80.0, 0.90),
                BpmCandidate(122.0, 0.50)
            )
        )

        assertTrue(candidates.first().bpm < 90.0 || candidates.first().bpm > 150.0)
    }

    @Test
    fun aggregateSegmentCandidatesPrefersRepeatedTempoOverOneWindowBurst() {
        val candidates = aggregateSegmentCandidates(
            listOf(
                listOf(
                    BpmCandidate(160.0, 1.0),
                    BpmCandidate(80.0, 0.98)
                ),
                listOf(BpmCandidate(100.0, 0.68)),
                listOf(BpmCandidate(100.5, 0.66))
            )
        )

        assertEquals(100.0, candidates.first().bpm, 2.0)
    }

    @Test
    fun tempoAnalysisQualityScorePrefersStableMultiSegmentLock() {
        val oneWindowBurst = TempoAnalysisResult(
            candidates = listOf(BpmCandidate(160.0, 0.92)),
            engineName = "Native tempo",
            diagnostics = "test",
            segmentsAnalyzed = 1,
            agreementScore = 1.0,
            engineWarnings = listOf("Only one segment produced a tempo lock")
        )
        val stableLock = TempoAnalysisResult(
            candidates = listOf(BpmCandidate(100.0, 0.70)),
            engineName = "Native tempo",
            diagnostics = "test",
            segmentsAnalyzed = 3,
            agreementScore = 0.67
        )

        assertTrue(tempoAnalysisQualityScore(stableLock) > tempoAnalysisQualityScore(oneWindowBurst))
    }

    @Test
    fun kotlinTempoEngineReturnsSyntheticTempo() {
        val sampleRate = 44_100
        val samples = pulseTrack(sampleRate, bpm = 128.0)

        val result = KotlinTempoEngine().analyze(samples, sampleRate, 60, 200)

        assertTrue(result.candidates.any { kotlin.math.abs(it.bpm - 128.0) <= 5.0 })
    }

    @Test
    fun hybridTempoEngineKeepsNativeDiagnosticsAndMergesFallbackAlternates() {
        val native = object : TempoEngine {
            override fun analyze(samples: FloatArray, sampleRate: Int, minBpm: Int, maxBpm: Int): TempoAnalysisResult {
                return TempoAnalysisResult(
                    candidates = listOf(BpmCandidate(128.0, 0.91)),
                    engineName = "Native tempo",
                    diagnostics = "native test",
                    segmentsAnalyzed = 3,
                    agreementScore = 0.66,
                    tempoFamily = tempoFamilyLabel(128.0)
                )
            }
        }
        val fallback = object : TempoEngine {
            override fun analyze(samples: FloatArray, sampleRate: Int, minBpm: Int, maxBpm: Int): TempoAnalysisResult {
                return TempoAnalysisResult(
                    candidates = listOf(BpmCandidate(122.0, 0.72)),
                    engineName = "Kotlin fallback",
                    diagnostics = "fallback test"
                )
            }
        }

        val result = HybridTempoEngine(native, fallback).analyze(FloatArray(44_100 * 20), 44_100, 60, 200)

        assertEquals("Native tempo + Kotlin fallback", result.engineName)
        assertEquals(3, result.segmentsAnalyzed)
        assertEquals(0.66, result.agreementScore, 0.001)
        assertTrue(result.candidates.any { kotlin.math.abs(it.bpm - 128.0) <= 1.0 })
        assertTrue(result.candidates.any { kotlin.math.abs(it.bpm - 122.0) <= 1.0 })
    }

    @Test
    fun hybridTempoEngineDoesNotInflateLowNativeAgreementWithCandidateConfidence() {
        val native = object : TempoEngine {
            override fun analyze(samples: FloatArray, sampleRate: Int, minBpm: Int, maxBpm: Int): TempoAnalysisResult {
                return TempoAnalysisResult(
                    candidates = listOf(BpmCandidate(128.0, 0.97)),
                    engineName = "Native tempo",
                    diagnostics = "native test",
                    segmentsAnalyzed = 3,
                    agreementScore = 0.33,
                    tempoFamily = tempoFamilyLabel(128.0),
                    engineWarnings = listOf("Low agreement between analysis segments")
                )
            }
        }
        val fallback = object : TempoEngine {
            override fun analyze(samples: FloatArray, sampleRate: Int, minBpm: Int, maxBpm: Int): TempoAnalysisResult {
                return TempoAnalysisResult(
                    candidates = listOf(BpmCandidate(128.0, 0.85)),
                    engineName = "Kotlin fallback",
                    diagnostics = "fallback test"
                )
            }
        }

        val result = HybridTempoEngine(native, fallback).analyze(FloatArray(44_100 * 20), 44_100, 60, 200)

        assertEquals(0.33, result.agreementScore, 0.001)
        assertTrue(result.engineWarnings.any { it.contains("Low agreement") })
    }

    @Test
    fun hybridTempoEngineLetsFallbackLeadWhenNativeAgreementIsPoor() {
        val native = object : TempoEngine {
            override fun analyze(samples: FloatArray, sampleRate: Int, minBpm: Int, maxBpm: Int): TempoAnalysisResult {
                return TempoAnalysisResult(
                    candidates = listOf(BpmCandidate(132.0, 0.98)),
                    engineName = "Native tempo",
                    diagnostics = "native test",
                    segmentsAnalyzed = 3,
                    agreementScore = 0.33,
                    tempoFamily = tempoFamilyLabel(132.0),
                    engineWarnings = listOf("Low agreement between analysis segments")
                )
            }
        }
        val fallback = object : TempoEngine {
            override fun analyze(samples: FloatArray, sampleRate: Int, minBpm: Int, maxBpm: Int): TempoAnalysisResult {
                return TempoAnalysisResult(
                    candidates = listOf(BpmCandidate(96.0, 0.86)),
                    engineName = "Kotlin fallback",
                    diagnostics = "fallback test"
                )
            }
        }

        val result = HybridTempoEngine(native, fallback).analyze(FloatArray(44_100 * 20), 44_100, 60, 200)

        assertEquals(96.0, result.candidates.first().bpm, 1.0)
    }

    @Test
    fun tempoFamilyLabelShowsHalfBaseAndDouble() {
        assertEquals("64.0 / 128.0 / 256.0", tempoFamilyLabel(128.0))
    }

    private fun pulseTrack(sampleRate: Int, bpm: Double): FloatArray {
        val samples = FloatArray(sampleRate * 24)
        val interval = (sampleRate * 60.0 / bpm).toInt()

        var position = 0
        while (position < samples.size) {
            for (offset in 0 until 220) {
                val index = position + offset
                if (index < samples.size) samples[index] = 1.0f
            }
            position += interval
        }

        return samples
    }
}

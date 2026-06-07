package com.example.integratedbpmmeter.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class BpmEstimatorTest {
    @Test
    fun estimatesSynthetic120BpmPulseTrack() {
        val sampleRate = 44_100
        val samples = pulseTrack(sampleRate, bpm = 120.0)

        val candidates = BpmEstimator().estimate(samples, sampleRate)

        assertFalse(candidates.isEmpty())
        assertTrue(candidates.any { abs(it.bpm - 120.0) <= 5.0 })
    }

    @Test
    fun estimatesCommonDanceTempoCandidates() {
        val sampleRate = 44_100
        listOf(80.0, 90.0, 100.0, 120.0, 128.0, 140.0, 160.0, 174.0).forEach { bpm ->
            val candidates = BpmEstimator().estimate(pulseTrack(sampleRate, bpm), sampleRate)

            assertFalse("Expected candidates for $bpm BPM", candidates.isEmpty())
            assertTrue(
                "Expected $bpm BPM in $candidates",
                candidates.any { abs(it.bpm - bpm) <= 5.0 }
            )
        }
    }

    @Test
    fun prefersBaseTempoOverLoudEighthNoteSubdivision() {
        val sampleRate = 44_100
        val candidates = BpmEstimator().estimate(accentedSubdivisionTrack(sampleRate, bpm = 100.0), sampleRate)

        assertFalse(candidates.isEmpty())
        assertTrue(
            "Expected 100 BPM to lead over fast subdivision candidates: $candidates",
            abs(candidates.first().bpm - 100.0) <= 5.0
        )
    }

    @Test
    fun normalizedKotlinEngineKeepsBaseTempoForSubdivisionTrack() {
        val sampleRate = 44_100
        val result = KotlinTempoEngine().analyze(accentedSubdivisionTrack(sampleRate, bpm = 100.0), sampleRate, 60, 200)

        assertTrue(
            "Expected normalized result near 100 BPM: ${result.candidates}",
            result.candidates.firstOrNull()?.let { abs(it.bpm - 100.0) <= 5.0 } == true
        )
    }

    @Test
    fun multibandVotingPrefersBeatOverFastHighFrequencySubdivision() {
        val sampleRate = 44_100
        val candidates = BpmEstimator().estimate(kickAndHatTrack(sampleRate, bpm = 100.0), sampleRate)

        assertFalse(candidates.isEmpty())
        assertTrue(
            "Expected 100 BPM to lead over high-frequency subdivision candidates: $candidates",
            abs(candidates.first().bpm - 100.0) <= 5.0
        )
    }

    @Test
    fun peakIntervalSupportKeepsTempoWhenSomeBeatsAreMissing() {
        val sampleRate = 44_100
        val candidates = BpmEstimator().estimate(missingBeatTrack(sampleRate, bpm = 128.0), sampleRate)

        assertFalse(candidates.isEmpty())
        assertTrue(
            "Expected 128 BPM candidate despite missing beats: $candidates",
            candidates.any { abs(it.bpm - 128.0) <= 5.0 }
        )
    }

    @Test
    fun lowBandSupportKeepsBaseTempoWhenTrebleSubdivisionIsLouder() {
        val sampleRate = 44_100
        val candidates = BpmEstimator().estimate(
            dominantTrebleSubdivisionTrack(sampleRate, bpm = 96.0),
            sampleRate
        )

        assertFalse(candidates.isEmpty())
        assertTrue(
            "Expected bass pulse near 96 BPM to lead over loud 192 BPM subdivision: $candidates",
            abs(candidates.first().bpm - 96.0) <= 5.0
        )
    }

    private fun pulseTrack(sampleRate: Int, bpm: Double): FloatArray {
        val samples = FloatArray(sampleRate * 24)
        val interval = (sampleRate * 60.0 / bpm).toInt()

        var position = 0
        while (position < samples.size) {
            for (offset in 0 until 180) {
                val index = position + offset
                if (index < samples.size) samples[index] = 1.0f
            }
            position += interval
        }

        return samples
    }

    private fun accentedSubdivisionTrack(sampleRate: Int, bpm: Double): FloatArray {
        val samples = FloatArray(sampleRate * 24)
        val beatInterval = (sampleRate * 60.0 / bpm).toInt()
        val subdivisionInterval = (beatInterval / 2).coerceAtLeast(1)

        var position = 0
        var subdivision = 0
        while (position < samples.size) {
            val amplitude = if (subdivision % 2 == 0) 1.0f else 0.68f
            for (offset in 0 until 160) {
                val index = position + offset
                if (index < samples.size) samples[index] = amplitude
            }
            position += subdivisionInterval
            subdivision++
        }

        return samples
    }

    private fun missingBeatTrack(sampleRate: Int, bpm: Double): FloatArray {
        val samples = FloatArray(sampleRate * 28)
        val beatInterval = (sampleRate * 60.0 / bpm).toInt()

        var position = 0
        var beat = 0
        while (position < samples.size) {
            if (beat % 4 != 3) {
                addSineBurst(samples, sampleRate, position, frequencyHz = 120.0, durationSeconds = 0.07, amplitude = 0.95)
            }
            position += beatInterval
            beat++
        }

        return samples
    }

    private fun kickAndHatTrack(sampleRate: Int, bpm: Double): FloatArray {
        val samples = FloatArray(sampleRate * 28)
        val beatInterval = (sampleRate * 60.0 / bpm).toInt()
        val subdivisionInterval = (beatInterval / 2).coerceAtLeast(1)

        var position = 0
        while (position < samples.size) {
            addSineBurst(samples, sampleRate, position, frequencyHz = 90.0, durationSeconds = 0.09, amplitude = 0.95)
            position += beatInterval
        }

        position = 0
        while (position < samples.size) {
            addSineBurst(samples, sampleRate, position, frequencyHz = 4_500.0, durationSeconds = 0.018, amplitude = 0.48)
            position += subdivisionInterval
        }

        return samples
    }

    private fun dominantTrebleSubdivisionTrack(sampleRate: Int, bpm: Double): FloatArray {
        val samples = FloatArray(sampleRate * 28)
        val beatInterval = (sampleRate * 60.0 / bpm).toInt()
        val subdivisionInterval = (beatInterval / 2).coerceAtLeast(1)

        var position = 0
        while (position < samples.size) {
            addSineBurst(samples, sampleRate, position, frequencyHz = 105.0, durationSeconds = 0.10, amplitude = 0.50)
            position += beatInterval
        }

        position = 0
        while (position < samples.size) {
            addSineBurst(samples, sampleRate, position, frequencyHz = 5_500.0, durationSeconds = 0.015, amplitude = 1.00)
            position += subdivisionInterval
        }

        return samples
    }

    private fun addSineBurst(
        samples: FloatArray,
        sampleRate: Int,
        start: Int,
        frequencyHz: Double,
        durationSeconds: Double,
        amplitude: Double
    ) {
        val length = (sampleRate * durationSeconds).toInt()
        for (offset in 0 until length) {
            val index = start + offset
            if (index >= samples.size) return
            val phase = 2.0 * PI * frequencyHz * offset / sampleRate
            val fade = 1.0 - offset.toDouble() / length.coerceAtLeast(1)
            val value = samples[index] + (sin(phase) * amplitude * fade).toFloat()
            samples[index] = value.coerceIn(-1.0f, 1.0f)
        }
    }
}

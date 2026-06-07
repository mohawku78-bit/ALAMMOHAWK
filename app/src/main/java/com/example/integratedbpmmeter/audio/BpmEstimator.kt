package com.example.integratedbpmmeter.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BpmCandidate(
    val bpm: Double,
    val confidence: Double
)

class BpmEstimator(
    private val minBpm: Int = 60,
    private val maxBpm: Int = 200
) {
    fun estimate(samples: FloatArray, sampleRate: Int): List<BpmCandidate> {
        if (samples.size < sampleRate * 4 || sampleRate <= 0) return emptyList()

        val envelope = combinedEnvelope(samples, sampleRate)
        if (envelope.size < 8) return emptyList()

        val onsetRate = sampleRate.toDouble() / HOP_SIZE
        val scores = mutableListOf<Pair<Double, Double>>()

        for (bpm in minBpm..maxBpm) {
            val lag = (onsetRate * 60.0 / bpm).roundToInt()
            if (lag < 1 || lag >= envelope.size / 2) continue

            val score = autocorrelationScore(envelope, lag) +
                0.45 * autocorrelationScore(envelope, lag * 2) +
                0.25 * autocorrelationScore(envelope, lag * 3)

            if (score.isFinite() && score > 0.0) {
                scores += bpm.toDouble() to score
            }
        }

        if (scores.isEmpty()) return estimateFromPeaks(envelope, onsetRate)

        val maxScore = scores.maxOf { adjustedScore(it) }.coerceAtLeast(0.000001)
        val localMaxima = scores.filterIndexed { index, item ->
            val previous = scores.getOrNull(index - 1)?.second ?: Double.NEGATIVE_INFINITY
            val next = scores.getOrNull(index + 1)?.second ?: Double.NEGATIVE_INFINITY
            item.second >= previous && item.second >= next
        }

        val ranked = localMaxima
            .sortedByDescending { adjustedScore(it) }
            .fold(mutableListOf<BpmCandidate>()) { selected, item ->
                val bpm = item.first
                val alreadyCovered = selected.any { abs(it.bpm - bpm) < 3.0 }
                if (!alreadyCovered) {
                    selected += BpmCandidate(
                        bpm = bpm,
                        confidence = (adjustedScore(item) / maxScore).coerceIn(0.0, 1.0)
                    )
                }
                selected
            }

        val filled = if (ranked.size >= 3) {
            ranked
        } else {
            scores
                .sortedByDescending { adjustedScore(it) }
                .fold(ranked.toMutableList()) { selected, item ->
                    val bpm = item.first
                    val alreadyCovered = selected.any { abs(it.bpm - bpm) < 3.0 }
                    if (!alreadyCovered) {
                        selected += BpmCandidate(
                            bpm = bpm,
                            confidence = (adjustedScore(item) / maxScore * 0.85).coerceIn(0.05, 1.0)
                        )
                    }
                    selected
                }
        }

        return filled.take(3)
    }

    private fun adjustedScore(item: Pair<Double, Double>): Double {
        return item.second * selectionWeight(item.first)
    }

    private fun selectionWeight(bpm: Double): Double {
        return when {
            bpm <= 184.0 -> 1.0
            bpm <= 200.0 -> 0.72
            else -> 0.5
        }
    }

    private fun combinedEnvelope(samples: FloatArray, sampleRate: Int): DoubleArray {
        val onset = onsetEnvelope(samples, sampleRate)
        val energy = energyEnvelope(samples, sampleRate)
        val size = minOf(onset.size, energy.size)
        if (size == 0) return DoubleArray(0)

        return DoubleArray(size) { index ->
            max(onset[index], energy[index] * 0.55)
        }
    }

    private fun onsetEnvelope(samples: FloatArray, sampleRate: Int): DoubleArray {
        val frameCount = ((samples.size - FRAME_SIZE).coerceAtLeast(0) / HOP_SIZE) + 1
        val energies = DoubleArray(frameCount)

        for (frame in 0 until frameCount) {
            val start = frame * HOP_SIZE
            var sum = 0.0
            val end = (start + FRAME_SIZE).coerceAtMost(samples.size)
            for (index in start until end) {
                val sample = samples[index].toDouble()
                sum += sample * sample
            }
            energies[frame] = sqrt(sum / max(1, end - start))
        }

        val flux = DoubleArray(frameCount)
        for (index in 1 until frameCount) {
            flux[index] = max(0.0, energies[index] - energies[index - 1])
        }

        return positiveNormalize(movingAverage(flux, window = max(3, (sampleRate / HOP_SIZE) / 3)))
    }

    private fun energyEnvelope(samples: FloatArray, sampleRate: Int): DoubleArray {
        val frameCount = ((samples.size - FRAME_SIZE).coerceAtLeast(0) / HOP_SIZE) + 1
        val energies = DoubleArray(frameCount)

        for (frame in 0 until frameCount) {
            val start = frame * HOP_SIZE
            var sum = 0.0
            val end = (start + FRAME_SIZE).coerceAtMost(samples.size)
            for (index in start until end) {
                val sample = samples[index].toDouble()
                sum += sample * sample
            }
            energies[frame] = sqrt(sum / max(1, end - start))
        }

        return positiveNormalize(movingAverage(energies, window = max(3, (sampleRate / HOP_SIZE) / 4)))
    }

    private fun positiveNormalize(values: DoubleArray): DoubleArray {
        if (values.isEmpty()) return values
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        if (stdDev < 0.000001) {
            val min = values.minOrNull() ?: 0.0
            val maxValue = values.maxOrNull() ?: 0.0
            val range = maxValue - min
            if (range < 0.000001) return DoubleArray(values.size)
            return DoubleArray(values.size) { index ->
                ((values[index] - min) / range).coerceAtLeast(0.0)
            }
        }

        return DoubleArray(values.size) { index ->
            val normalized = (values[index] - mean) / stdDev
            if (normalized > 0.0) normalized else 0.0
        }
    }

    private fun movingAverage(values: DoubleArray, window: Int): DoubleArray {
        if (values.isEmpty()) return values
        val output = DoubleArray(values.size)
        var sum = 0.0
        for (index in values.indices) {
            sum += values[index]
            if (index >= window) sum -= values[index - window]
            val divisor = (index + 1).coerceAtMost(window)
            output[index] = sum / divisor
        }
        return output
    }

    private fun autocorrelationScore(values: DoubleArray, lag: Int): Double {
        if (lag <= 0 || lag >= values.size) return 0.0
        var score = 0.0
        var normA = 0.0
        var normB = 0.0
        var count = 0
        for (index in lag until values.size) {
            val current = values[index]
            val previous = values[index - lag]
            score += current * previous
            normA += current * current
            normB += previous * previous
            count++
        }
        return if (count == 0 || normA <= 0.0 || normB <= 0.0) {
            0.0
        } else {
            score / sqrt(normA * normB)
        }
    }

    private fun estimateFromPeaks(values: DoubleArray, onsetRate: Double): List<BpmCandidate> {
        if (values.size < 8) return emptyList()
        val threshold = values.average() + sqrt(values.map { it * it }.average()) * 0.35
        val peaks = mutableListOf<Int>()

        for (index in 1 until values.lastIndex) {
            if (
                values[index] >= threshold &&
                values[index] >= values[index - 1] &&
                values[index] >= values[index + 1]
            ) {
                peaks += index
            }
        }

        if (peaks.size < 3) return emptyList()

        val counts = mutableMapOf<Int, Int>()
        for (index in 1 until peaks.size) {
            val interval = peaks[index] - peaks[index - 1]
            if (interval <= 0) continue
            val bpm = (onsetRate * 60.0 / interval).roundToInt()
            val folded = foldIntoRange(bpm)
            if (folded in minBpm..maxBpm) {
                counts[folded] = (counts[folded] ?: 0) + 1
            }
        }

        val maxCount = counts.values.maxOrNull() ?: return emptyList()
        return counts
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map {
                BpmCandidate(
                    bpm = it.key.toDouble(),
                    confidence = (it.value.toDouble() / maxCount * 0.55).coerceIn(0.05, 0.55)
                )
            }
    }

    private fun foldIntoRange(value: Int): Int {
        var bpm = value
        while (bpm < minBpm) bpm *= 2
        while (bpm > maxBpm) bpm /= 2
        return bpm
    }

    companion object {
        private const val FRAME_SIZE = 1024
        private const val HOP_SIZE = 512
    }
}

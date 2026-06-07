package com.example.integratedbpmmeter.audio

import kotlin.math.abs
import kotlin.math.exp
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

        val lowEnvelope = lowBandEnvelope(samples, sampleRate)
        val onsetRate = sampleRate.toDouble() / HOP_SIZE
        val peakSupport = peakTempoSupport(envelope, onsetRate)
        val rows = mutableListOf<TempoScoreRow>()

        for (bpm in minBpm..maxBpm) {
            val lag = (onsetRate * 60.0 / bpm).roundToInt()
            if (lag < 1 || lag >= envelope.size / 2) continue

            val fullScore = rhythmScore(envelope, lag)
            val lowScore = rhythmScore(lowEnvelope, lag, secondWeight = 0.35, thirdWeight = 0.15)
            val peakScore = peakSupport[bpm] ?: 0.0
            val score = fullScore + 0.18 * lowScore + 0.08 * peakScore

            if (score.isFinite() && score > 0.0) {
                rows += TempoScoreRow(
                    bpm = bpm,
                    fullScore = fullScore,
                    lowScore = lowScore,
                    peakScore = peakScore,
                    score = score
                )
            }
        }

        if (rows.isEmpty()) return estimateFromPeaks(envelope, onsetRate)

        val scoreByBpm = rows.associateBy { it.bpm }
        val scoredRows = rows.map { row ->
            row.copy(score = adjustedScore(row, scoreByBpm))
        }
        val maxScore = scoredRows.maxOf { it.score }.coerceAtLeast(0.000001)
        val localMaxima = scoredRows.filterIndexed { index, item ->
            val previous = scoredRows.getOrNull(index - 1)?.score ?: Double.NEGATIVE_INFINITY
            val next = scoredRows.getOrNull(index + 1)?.score ?: Double.NEGATIVE_INFINITY
            item.score >= previous && item.score >= next
        }

        val ranked = localMaxima
            .sortedByDescending { it.score }
            .fold(mutableListOf<BpmCandidate>()) { selected, row ->
                val bpm = row.bpm.toDouble()
                val alreadyCovered = selected.any { abs(it.bpm - bpm) < 3.0 }
                if (!alreadyCovered) {
                    selected += BpmCandidate(
                        bpm = bpm,
                        confidence = (row.score / maxScore).coerceIn(0.0, 1.0)
                    )
                }
                selected
            }

        val filled = if (ranked.size >= 3) {
            ranked
        } else {
            scoredRows
                .sortedByDescending { it.score }
                .fold(ranked.toMutableList()) { selected, row ->
                    val bpm = row.bpm.toDouble()
                    val alreadyCovered = selected.any { abs(it.bpm - bpm) < 3.0 }
                    if (!alreadyCovered) {
                        selected += BpmCandidate(
                            bpm = bpm,
                            confidence = (row.score / maxScore * 0.85).coerceIn(0.05, 1.0)
                        )
                    }
                    selected
                }
        }

        return filled.take(3)
    }

    private fun adjustedScore(row: TempoScoreRow, rowsByBpm: Map<Int, TempoScoreRow>): Double {
        return row.score * selectionWeight(row.bpm.toDouble()) * subdivisionPenalty(row, rowsByBpm)
    }

    private fun rhythmScore(
        values: DoubleArray,
        lag: Int,
        secondWeight: Double = 0.45,
        thirdWeight: Double = 0.25
    ): Double {
        return autocorrelationScore(values, lag) +
            secondWeight * autocorrelationScore(values, lag * 2) +
            thirdWeight * autocorrelationScore(values, lag * 3)
    }

    private fun subdivisionPenalty(row: TempoScoreRow, rowsByBpm: Map<Int, TempoScoreRow>): Double {
        if (row.bpm < 185) return 1.0
        val half = (row.bpm / 2.0).roundToInt()
        val halfRow = rowsByBpm[half] ?: return 1.0
        val halfCombined = halfRow.fullScore + 0.18 * halfRow.lowScore + 0.08 * halfRow.peakScore
        val halfIsCompetitive = halfCombined >= row.score * 0.68
        val halfHasStrongerLowPulse = halfRow.lowScore >= row.lowScore * 1.08
        return when {
            halfIsCompetitive && halfHasStrongerLowPulse -> 0.68
            row.bpm >= 190 && halfIsCompetitive -> 0.82
            else -> 1.0
        }
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

    private fun lowBandEnvelope(samples: FloatArray, sampleRate: Int): DoubleArray {
        val frameCount = ((samples.size - FRAME_SIZE).coerceAtLeast(0) / HOP_SIZE) + 1
        if (frameCount <= 0 || sampleRate <= 0) return DoubleArray(0)

        val cutoffHz = 180.0
        val alpha = exp(-2.0 * Math.PI * cutoffHz / sampleRate)
        val energies = DoubleArray(frameCount)
        var low = 0.0

        for (frame in 0 until frameCount) {
            val start = frame * HOP_SIZE
            val end = (start + FRAME_SIZE).coerceAtMost(samples.size)
            var sum = 0.0
            for (index in start until end) {
                low = (1.0 - alpha) * samples[index] + alpha * low
                sum += low * low
            }
            energies[frame] = sqrt(sum / max(1, end - start))
        }

        return positiveNormalize(movingAverage(energies, window = max(3, (sampleRate / HOP_SIZE) / 4)))
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
        val peaks = detectPeaks(values, onsetRate)
        if (peaks.size < 3) return emptyList()

        val counts = peakTempoCounts(peaks, onsetRate)
        if (counts.isEmpty()) return emptyList()

        val maxCount = counts.values.maxOrNull() ?: return emptyList()
        return counts
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map {
                BpmCandidate(
                    bpm = it.key.toDouble(),
                    confidence = (it.value / maxCount * 0.55).coerceIn(0.05, 0.55)
                )
            }
    }

    private fun peakTempoSupport(values: DoubleArray, onsetRate: Double): Map<Int, Double> {
        val peaks = detectPeaks(values, onsetRate)
        if (peaks.size < 3) return emptyMap()
        val counts = peakTempoCounts(peaks, onsetRate)
        val maxCount = counts.values.maxOrNull()?.coerceAtLeast(0.000001) ?: return emptyMap()
        return counts.mapValues { (_, value) -> (value / maxCount).coerceIn(0.0, 1.0) }
    }

    private fun detectPeaks(values: DoubleArray, onsetRate: Double): List<Int> {
        if (values.size < 8) return emptyList()
        val threshold = values.average() + sqrt(values.map { it * it }.average()) * 0.35
        val minPeakDistance = (onsetRate * 60.0 / maxBpm * 0.45).roundToInt().coerceAtLeast(1)
        val peaks = mutableListOf<Int>()

        for (index in 1 until values.lastIndex) {
            if (
                values[index] >= threshold &&
                values[index] >= values[index - 1] &&
                values[index] >= values[index + 1]
            ) {
                val previous = peaks.lastOrNull()
                if (previous == null || index - previous >= minPeakDistance) {
                    peaks += index
                } else if (values[index] > values[previous]) {
                    peaks[peaks.lastIndex] = index
                }
            }
        }

        return peaks
    }

    private fun peakTempoCounts(peaks: List<Int>, onsetRate: Double): Map<Int, Double> {
        val counts = mutableMapOf<Int, Double>()
        for (startIndex in peaks.indices) {
            val maxEnd = (startIndex + 4).coerceAtMost(peaks.lastIndex)
            for (endIndex in (startIndex + 1)..maxEnd) {
                val interval = peaks[endIndex] - peaks[startIndex]
                if (interval <= 0) continue
                val bpm = (onsetRate * 60.0 / interval).roundToInt()
                val folded = foldIntoRange(bpm)
                if (folded in minBpm..maxBpm) {
                    val distance = endIndex - startIndex
                    val weight = 1.0 / distance.toDouble()
                    counts[folded] = (counts[folded] ?: 0.0) + weight
                }
            }
        }
        return counts
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

    private data class TempoScoreRow(
        val bpm: Int,
        val fullScore: Double,
        val lowScore: Double,
        val peakScore: Double,
        val score: Double
    )
}

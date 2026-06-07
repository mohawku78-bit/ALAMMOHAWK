package com.example.integratedbpmmeter.audio

import kotlin.math.abs

data class TapBpmSnapshot(
    val bpm: Double? = null,
    val tapCount: Int = 0,
    val intervalMs: Double? = null,
    val confidence: Double = 0.0,
    val stabilityLabel: String = "Waiting"
)

class TapBpmCalculator(
    private val maxTapCount: Int = 16,
    private val resetAfterGapNanos: Long = 4_000_000_000L
) {
    private val taps = ArrayDeque<Long>()
    private var multiplier = 1.0

    fun addTap(timestampNanos: Long): TapBpmSnapshot {
        val lastTap = taps.lastOrNull()
        if (lastTap != null && timestampNanos - lastTap > resetAfterGapNanos) {
            taps.clear()
            multiplier = 1.0
        }

        taps.addLast(timestampNanos)
        while (taps.size > maxTapCount) {
            taps.removeFirst()
        }

        return snapshot()
    }

    fun reset(): TapBpmSnapshot {
        taps.clear()
        multiplier = 1.0
        return snapshot()
    }

    fun half(): TapBpmSnapshot {
        multiplier *= 0.5
        return snapshot()
    }

    fun double(): TapBpmSnapshot {
        multiplier *= 2.0
        return snapshot()
    }

    private fun snapshot(): TapBpmSnapshot {
        val intervals = taps.zipWithNext { previous, current -> current - previous }
            .filter { it > 0L }
            .map { it.toDouble() }

        if (intervals.isEmpty()) {
            return TapBpmSnapshot(tapCount = taps.size)
        }

        val robustInterval = robustAverage(intervals)
        val rawBpm = NANOS_PER_MINUTE / robustInterval
        val bpm = rawBpm * multiplier
        val intervalMs = robustInterval / NANOS_PER_MILLISECOND
        val confidence = confidence(intervals, robustInterval)

        return TapBpmSnapshot(
            bpm = bpm,
            tapCount = taps.size,
            intervalMs = intervalMs,
            confidence = confidence,
            stabilityLabel = stabilityLabel(taps.size, confidence)
        )
    }

    private fun robustAverage(values: List<Double>): Double {
        if (values.size < 5) return values.sorted().middleAverage()

        val sorted = values.sorted()
        val trimCount = (values.size * 0.15).toInt().coerceAtLeast(1)
        return sorted.drop(trimCount).dropLast(trimCount).middleAverage()
    }

    private fun List<Double>.middleAverage(): Double {
        if (isEmpty()) return 1.0
        if (size == 1) return first()
        val median = if (size % 2 == 0) {
            (this[size / 2 - 1] + this[size / 2]) / 2.0
        } else {
            this[size / 2]
        }
        val average = average()
        return (median * 0.65) + (average * 0.35)
    }

    private fun confidence(intervals: List<Double>, reference: Double): Double {
        val countScore = (intervals.size / 7.0).coerceIn(0.15, 1.0)
        val meanDeviation = intervals.map { abs(it - reference) }.average()
        val deviationRatio = (meanDeviation / reference).coerceIn(0.0, 1.0)
        val consistencyScore = (1.0 - deviationRatio * 4.0).coerceIn(0.0, 1.0)
        return (countScore * consistencyScore).coerceIn(0.0, 1.0)
    }

    private fun stabilityLabel(tapCount: Int, confidence: Double): String {
        return when {
            tapCount < 4 -> "Warming up"
            confidence >= 0.85 -> "Stable"
            confidence >= 0.6 -> "Good"
            else -> "Unsteady"
        }
    }

    companion object {
        private const val NANOS_PER_MINUTE = 60_000_000_000.0
        private const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}

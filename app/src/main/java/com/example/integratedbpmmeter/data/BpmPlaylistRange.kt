package com.example.integratedbpmmeter.data

import java.util.Locale

data class BpmPlaylistRange(
    val minBpm: Double,
    val maxBpm: Double
) {
    val low: Double
        get() = minOf(minBpm, maxBpm)

    val high: Double
        get() = maxOf(minBpm, maxBpm)

    fun contains(bpm: Double): Boolean {
        return bpm in low..high
    }

    fun label(): String {
        return "${low.formatRangeBpm()}-${high.formatRangeBpm()} BPM"
    }
}

enum class BpmRangePreset(
    val chipLabel: String,
    val range: BpmPlaylistRange
) {
    BPM_160("160", BpmPlaylistRange(160.0, 169.9)),
    BPM_170("170", BpmPlaylistRange(170.0, 179.9)),
    BPM_180("180", BpmPlaylistRange(180.0, 189.9));

    val detailLabel: String
        get() = range.label()
}

fun BpmRecord.matchesPlaylistRange(range: BpmPlaylistRange?): Boolean {
    if (range == null) return true
    return range.contains(bpm) ||
        bpm.doubleTimeCompatibleBpm()?.let { range.contains(it) } == true
}

private fun Double.formatRangeBpm(): String {
    val rounded = kotlin.math.round(this)
    val floored = kotlin.math.floor(this)
    if (kotlin.math.abs(this - floored - 0.9) < 0.01) {
        return floored.toInt().toString()
    }
    return if (kotlin.math.abs(this - rounded) < 0.01) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this)
    }
}

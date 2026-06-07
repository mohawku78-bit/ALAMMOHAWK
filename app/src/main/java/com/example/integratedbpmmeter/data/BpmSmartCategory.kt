package com.example.integratedbpmmeter.data

import java.util.Locale

enum class BpmSmartCategory(
    val label: String,
    val detail: String
) {
    HIGH_PACE("High Pace", "Running cadence / high-intensity pedaling"),
    MID_PACE("Mid Pace", "Jogging / steady pedaling"),
    GROOVE_GENERAL("Groove / General", "General listening / flexible tempo"),
    LOW_DOUBLE_TIME("Low / Double-time", "Hip-hop, groove, double-time running"),
    WARM_UP_COOL_DOWN("Warm-up / Cool-down", "Stretching / warm-up / cool-down");

    companion object {
        fun fromBpm(bpm: Double): BpmSmartCategory {
            return when {
                bpm >= 160.0 -> HIGH_PACE
                bpm >= 140.0 -> MID_PACE
                bpm >= 90.0 -> GROOVE_GENERAL
                bpm >= 70.0 -> LOW_DOUBLE_TIME
                else -> WARM_UP_COOL_DOWN
            }
        }
    }
}

fun BpmRecord.effectiveCategory(): BpmSmartCategory {
    return categoryOverride ?: BpmSmartCategory.fromBpm(bpm)
}

fun Double.doubleTimeCompatibleBpm(): Double? {
    return if (this in 70.0..90.0) this * 2.0 else null
}

fun Double.halfTimeFeelBpm(): Double? {
    return if (this in 160.0..200.0) this / 2.0 else null
}

fun Double.formatBpmOneDecimal(): String = String.format(Locale.US, "%.1f", this)

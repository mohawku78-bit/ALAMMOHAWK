package com.example.integratedbpmmeter.audio

import com.example.integratedbpmmeter.data.BpmSourceType
import kotlin.math.abs

data class TempoReference(
    val bpm: Double,
    val label: String
)

fun fileEstimateComparisonLabels(
    candidates: List<BpmCandidate>,
    sources: List<BpmSourceType>,
    tapBpm: Double?,
    publicReferences: List<BpmCandidate> = emptyList()
): List<String?> {
    val sourceList = sources.ifEmpty { List(candidates.size) { BpmSourceType.FILE_ANALYSIS } }
    val references = buildReferences(candidates, sourceList, tapBpm, publicReferences)

    return candidates.mapIndexed { index, candidate ->
        val source = sourceList.getOrElse(index) { BpmSourceType.FILE_ANALYSIS }
        when (source) {
            BpmSourceType.TAP -> "Verified by tap"
            BpmSourceType.NOW_PLAYING -> "Saved Library"
            BpmSourceType.PUBLIC_REFERENCE -> "Public reference"
            BpmSourceType.FILE_ANALYSIS,
            BpmSourceType.PLAYBACK_CAPTURE,
            BpmSourceType.MIC_CAPTURE -> comparisonLabel(candidate.bpm, references)
        }
    }
}

private fun buildReferences(
    candidates: List<BpmCandidate>,
    sources: List<BpmSourceType>,
    tapBpm: Double?,
    publicReferences: List<BpmCandidate>
): List<TempoReference> {
    val directReferences = candidates.mapIndexedNotNull { index, candidate ->
        val source = sources.getOrElse(index) { BpmSourceType.FILE_ANALYSIS }
        when (source) {
            BpmSourceType.TAP -> TempoReference(candidate.bpm, "Tap")
            BpmSourceType.NOW_PLAYING -> TempoReference(candidate.bpm, "Saved")
            BpmSourceType.PUBLIC_REFERENCE -> TempoReference(candidate.bpm, "Reference")
            else -> null
        }
    }

    val publicOnlyReferences = publicReferences.map { TempoReference(it.bpm, "Reference") }
    val tapReference = tapBpm
        ?.takeIf { it.isFinite() && it in 30.0..400.0 }
        ?.let { TempoReference(it, "Tap") }

    return (listOfNotNull(tapReference) + directReferences + publicOnlyReferences)
        .distinctBy { reference -> "${reference.label}:${reference.bpm.roundedKey()}" }
}

private fun comparisonLabel(candidateBpm: Double, references: List<TempoReference>): String {
    if (references.isEmpty()) return "Tap-check needed"

    val direct = references
        .map { reference -> reference to reference.bpm - candidateBpm }
        .filter { (_, delta) -> abs(delta) <= DIRECT_MATCH_TOLERANCE_BPM }
        .minByOrNull { (_, delta) -> abs(delta) }
    if (direct != null) {
        val (reference, delta) = direct
        return if (abs(delta) <= CLOSE_MATCH_TOLERANCE_BPM) {
            "${reference.label} match"
        } else {
            "${reference.label} ${delta.signedBpm()}"
        }
    }

    val family = references.firstOrNull { reference ->
        candidateBpm.isSameTempoFamilyAs(reference.bpm, FAMILY_MATCH_TOLERANCE_BPM)
    }
    if (family != null) {
        return when {
            abs(candidateBpm * 2.0 - family.bpm) <= FAMILY_MATCH_TOLERANCE_BPM ->
                "${family.label} half-time family"
            abs(candidateBpm - family.bpm * 2.0) <= FAMILY_MATCH_TOLERANCE_BPM ->
                "${family.label} double-time family"
            else -> "${family.label} tempo family"
        }
    }

    val nearest = references.minBy { reference -> abs(reference.bpm - candidateBpm) }
    return "${nearest.label} gap ${abs(nearest.bpm - candidateBpm).formatOne()} BPM"
}

private fun Double.isSameTempoFamilyAs(other: Double, tolerance: Double): Boolean {
    return abs(this - other) <= tolerance ||
        abs(this * 2.0 - other) <= tolerance ||
        abs(this - other * 2.0) <= tolerance
}

private fun Double.signedBpm(): String {
    val sign = if (this >= 0.0) "+" else "-"
    return "$sign${abs(this).formatOne()} BPM"
}

private fun Double.formatOne(): String {
    return String.format(java.util.Locale.US, "%.1f", this)
}

private fun Double.roundedKey(): String {
    return String.format(java.util.Locale.US, "%.1f", this)
}

private const val CLOSE_MATCH_TOLERANCE_BPM = 1.0
private const val DIRECT_MATCH_TOLERANCE_BPM = 4.0
private const val FAMILY_MATCH_TOLERANCE_BPM = 2.0

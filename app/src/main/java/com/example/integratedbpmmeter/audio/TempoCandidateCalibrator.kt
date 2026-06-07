package com.example.integratedbpmmeter.audio

import com.example.integratedbpmmeter.data.BpmSourceType
import kotlin.math.abs

data class CalibratedTempoCandidates(
    val candidates: List<BpmCandidate>,
    val sources: List<BpmSourceType>,
    val reasonLabels: List<String>
)

object TempoCandidateCalibrator {
    fun calibrate(
        analysisCandidates: List<BpmCandidate>,
        analysisSources: List<BpmSourceType> = List(analysisCandidates.size) { BpmSourceType.FILE_ANALYSIS },
        referenceCandidates: List<BpmCandidate> = emptyList(),
        agreementScore: Double = 0.0,
        segmentsAnalyzed: Int = 0,
        engineWarnings: List<String> = emptyList()
    ): CalibratedTempoCandidates {
        if (analysisCandidates.isEmpty()) {
            return CalibratedTempoCandidates(emptyList(), emptyList(), emptyList())
        }

        val sourceList = analysisSources.ifEmpty { List(analysisCandidates.size) { BpmSourceType.FILE_ANALYSIS } }
        val rows = analysisCandidates.mapIndexed { index, candidate ->
            CandidateRow(
                candidate = candidate,
                source = sourceList.getOrElse(index) { BpmSourceType.FILE_ANALYSIS },
                adjustedScore = candidate.adjustedScore(
                    allCandidates = analysisCandidates,
                    referenceCandidates = referenceCandidates,
                    agreementScore = agreementScore,
                    segmentsAnalyzed = segmentsAnalyzed,
                    engineWarnings = engineWarnings
                ),
                reasonLabel = candidate.reasonLabel(
                    allCandidates = analysisCandidates,
                    referenceCandidates = referenceCandidates,
                    agreementScore = agreementScore,
                    segmentsAnalyzed = segmentsAnalyzed,
                    engineWarnings = engineWarnings
                )
            )
        }

        val selected = rows
            .sortedWith(
                compareByDescending<CandidateRow> { it.adjustedScore }
                    .thenBy { abs(it.candidate.bpm - TACTUS_CENTER_BPM) }
            )
            .fold(mutableListOf<CandidateRow>()) { chosen, row ->
                if (chosen.none { abs(it.candidate.bpm - row.candidate.bpm) <= 1.5 }) {
                    chosen += row
                }
                chosen
            }

        return CalibratedTempoCandidates(
            candidates = selected.map { it.candidate },
            sources = selected.map { it.source },
            reasonLabels = selected.map { it.reasonLabel }
        )
    }

    private fun BpmCandidate.adjustedScore(
        allCandidates: List<BpmCandidate>,
        referenceCandidates: List<BpmCandidate>,
        agreementScore: Double,
        segmentsAnalyzed: Int,
        engineWarnings: List<String>
    ): Double {
        var score = confidence.coerceIn(0.0, 1.0) * tempoPrior(bpm)
        val half = allCandidates.firstOrNull { abs(it.bpm * 2.0 - bpm) <= 2.0 }
        val directReferenceMatch = referenceCandidates.any { abs(bpm - it.bpm) <= 2.0 }
        val referenceFamilyMatch = referenceCandidates.any { bpm.isSameTempoFamilyAs(it.bpm, tolerance = 2.0) }

        if (directReferenceMatch) {
            score *= 1.36
        } else if (referenceFamilyMatch) {
            score *= 1.12
        }
        if (segmentsAnalyzed >= 2 && agreementScore >= 0.65) score *= 1.10
        if (engineWarnings.any { it.contains("low agreement", ignoreCase = true) }) score *= 0.84
        if (engineWarnings.any { it.contains("only one segment", ignoreCase = true) }) score *= 0.88

        if (bpm >= 190.0 && half != null && half.confidence >= confidence * 0.62) {
            score *= 0.64
        } else if (bpm >= 185.0 && half != null && half.confidence >= confidence * 0.72) {
            score *= 0.78
        }

        return score
    }

    private fun BpmCandidate.reasonLabel(
        allCandidates: List<BpmCandidate>,
        referenceCandidates: List<BpmCandidate>,
        agreementScore: Double,
        segmentsAnalyzed: Int,
        engineWarnings: List<String>
    ): String {
        if (referenceCandidates.any { bpm.isSameTempoFamilyAs(it.bpm, tolerance = 2.0) }) {
            return "Reference family match"
        }
        val hasHalfCandidate = allCandidates.any { abs(it.bpm * 2.0 - bpm) <= 2.0 }
        if (bpm >= 185.0 && hasHalfCandidate) {
            return "Possible double-time"
        }
        if (segmentsAnalyzed >= 2 && agreementScore >= 0.65 && engineWarnings.isEmpty()) {
            return "Stable segments"
        }
        if (confidence >= 0.84 && engineWarnings.none { it.contains("low agreement", ignoreCase = true) }) {
            return "Low-band pulse"
        }
        return "Needs tap-check"
    }

    private data class CandidateRow(
        val candidate: BpmCandidate,
        val source: BpmSourceType,
        val adjustedScore: Double,
        val reasonLabel: String
    )

    private fun tempoPrior(bpm: Double): Double {
        return when {
            bpm < 68.0 -> 0.70
            bpm <= 150.0 -> 1.0
            bpm <= 182.0 -> 0.97
            bpm <= 190.0 -> 0.80
            bpm <= 200.0 -> 0.62
            else -> 0.45
        }
    }

    private fun Double.isSameTempoFamilyAs(other: Double, tolerance: Double): Boolean {
        return abs(this - other) <= tolerance ||
            abs(this * 2.0 - other) <= tolerance ||
            abs(this - other * 2.0) <= tolerance
    }

    private const val TACTUS_CENTER_BPM = 118.0
}

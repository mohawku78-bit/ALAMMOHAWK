package com.example.integratedbpmmeter.audio

import kotlin.math.abs

internal data class SectionTempoAnalysis(
    val startUs: Long,
    val result: TempoAnalysisResult
)

internal object TempoSectionAggregator {
    fun aggregate(sections: List<SectionTempoAnalysis>): TempoAnalysisResult {
        val validSections = sections.filter { it.result.candidates.isNotEmpty() }
        if (validSections.isEmpty()) {
            return TempoAnalysisResult(
                candidates = emptyList(),
                engineName = "Cross-section tempo",
                diagnostics = "cross-section drum-focused consensus found no tempo lock",
                engineWarnings = listOf("No file section produced a tempo lock")
            )
        }

        val candidates = aggregateSegmentCandidates(validSections.map { it.result.candidates })
        val recommendedBpm = candidates.firstOrNull()?.bpm
        val agreementScore = familySectionAgreement(validSections, recommendedBpm)
        val directSupport = directSectionSupport(validSections, recommendedBpm)
        val warnings = buildList {
            if (validSections.size == 1) add("Only one file section produced a tempo lock")
            if (validSections.size >= 2 && directSupport < 2) add("Top tempo appeared in only one file section")
            if (validSections.size >= 2 && agreementScore < 0.45) add("Low agreement between file sections")
        }

        return TempoAnalysisResult(
            candidates = candidates,
            engineName = "Cross-section tempo",
            diagnostics = "cross-section drum-focused consensus",
            segmentsAnalyzed = validSections.sumOf { it.result.segmentsAnalyzed.coerceAtLeast(1) },
            agreementScore = agreementScore,
            tempoFamily = recommendedBpm?.let { tempoFamilyLabel(it) },
            engineWarnings = warnings
        )
    }

    fun shouldPreferConsensus(
        consensus: TempoAnalysisResult,
        currentBest: TempoAnalysisResult,
        sections: List<SectionTempoAnalysis>
    ): Boolean {
        if (consensus.candidates.isEmpty() || sections.size < 2) return false

        val validSections = sections.filter { it.result.candidates.isNotEmpty() }
        val consensusDirectSupport = directSectionSupport(validSections, consensus.recommendedBpm)
        if (consensusDirectSupport < 2) return false

        val currentBestDirectSupport = directSectionSupport(validSections, currentBest.recommendedBpm)
        if (consensusDirectSupport > currentBestDirectSupport) return true

        val consensusScore = tempoAnalysisQualityScore(consensus)
        val currentBestScore = tempoAnalysisQualityScore(currentBest)
        val currentBestLooksFragile = currentBest.engineWarnings.any { warning ->
            warning.contains("only one", ignoreCase = true) ||
                warning.contains("low agreement", ignoreCase = true)
        }

        return currentBestLooksFragile || consensusScore + SECTION_CONSENSUS_MARGIN >= currentBestScore
    }

    private fun directSectionSupport(sections: List<SectionTempoAnalysis>, bpm: Double?): Int {
        if (bpm == null) return 0
        return sections.count { section ->
            section.result.candidates.any { candidate ->
                abs(candidate.bpm - bpm) <= DIRECT_SECTION_TOLERANCE_BPM
            }
        }
    }

    private fun familySectionAgreement(sections: List<SectionTempoAnalysis>, bpm: Double?): Double {
        if (bpm == null || sections.isEmpty()) return 0.0
        val matching = sections.count { section ->
            section.result.candidates.any { candidate ->
                sameTempoFamily(candidate.bpm, bpm, tolerance = FAMILY_SECTION_TOLERANCE_BPM)
            }
        }
        return matching.toDouble() / sections.size
    }

    private fun sameTempoFamily(a: Double, b: Double, tolerance: Double): Boolean {
        return abs(a - b) <= tolerance ||
            abs(a * 2.0 - b) <= tolerance ||
            abs(a - b * 2.0) <= tolerance
    }
}

private const val DIRECT_SECTION_TOLERANCE_BPM = 2.0
private const val FAMILY_SECTION_TOLERANCE_BPM = 2.0
private const val SECTION_CONSENSUS_MARGIN = 0.08

package com.example.integratedbpmmeter.audio

import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmSourceType

data class PrioritizedBpmCandidates(
    val candidates: List<BpmCandidate>,
    val sources: List<BpmSourceType>,
    val usedSavedReference: Boolean,
    val usedPublicReference: Boolean = false
)

object FileBpmCandidatePrioritizer {
    fun prioritize(
        savedReferences: List<BpmRecord>,
        publicReferences: List<BpmCandidate> = emptyList(),
        analysisCandidates: List<BpmCandidate>,
        analysisSources: List<BpmSourceType> = List(analysisCandidates.size) { BpmSourceType.FILE_ANALYSIS }
    ): PrioritizedBpmCandidates {
        val trustedReferences = savedReferences
            .filter { it.isTrustedFileReference() }
            .sortedWith(savedReferenceComparator)
            .distinctByBpm()
            .take(2)

        val trustedPublicReferences = publicReferences
            .filter { it.confidence >= MIN_PUBLIC_REFERENCE_CONFIDENCE }
            .distinctCandidatesByBpm()
            .take(2)

        if (trustedReferences.isEmpty() && trustedPublicReferences.isEmpty()) {
            return PrioritizedBpmCandidates(
                candidates = analysisCandidates,
                sources = analysisSources,
                usedSavedReference = false
            )
        }

        val referencePairs = (
            trustedReferences.map { record ->
                BpmCandidate(
                    bpm = record.bpm,
                    confidence = record.referenceConfidence()
                ) to record.sourceType
            } + trustedPublicReferences.map { candidate ->
                candidate to BpmSourceType.PUBLIC_REFERENCE
            }
        ).distinctCandidatePairsByBpm()
        val referenceCandidates = referencePairs.map { it.first }
        val retainedAnalysis = analysisCandidates
            .zip(analysisSources.ifEmpty { List(analysisCandidates.size) { BpmSourceType.FILE_ANALYSIS } })
            .filter { (candidate, _) ->
                referenceCandidates.none { reference -> candidate.bpm.isSameBpmAs(reference.bpm) }
            }

        return PrioritizedBpmCandidates(
            candidates = referenceCandidates + retainedAnalysis.map { it.first },
            sources = referencePairs.map { it.second } + retainedAnalysis.map { it.second },
            usedSavedReference = trustedReferences.isNotEmpty(),
            usedPublicReference = trustedReferences.isEmpty() && trustedPublicReferences.isNotEmpty()
        )
    }

    private val savedReferenceComparator = compareByDescending<BpmRecord> { it.manuallyVerified }
        .thenByDescending { it.sourceType.referencePriority() }
        .thenByDescending { it.confidence }
        .thenByDescending { it.createdAt }

    private fun BpmRecord.isTrustedFileReference(): Boolean {
        return manuallyVerified ||
            sourceType == BpmSourceType.TAP ||
            sourceType == BpmSourceType.NOW_PLAYING ||
            sourceType == BpmSourceType.PUBLIC_REFERENCE
    }

    private fun BpmSourceType.referencePriority(): Int {
        return when (this) {
            BpmSourceType.TAP -> 5
            BpmSourceType.NOW_PLAYING -> 4
            BpmSourceType.PUBLIC_REFERENCE -> 3
            BpmSourceType.MIC_CAPTURE -> 2
            BpmSourceType.PLAYBACK_CAPTURE -> 1
            BpmSourceType.FILE_ANALYSIS -> 0
        }
    }

    private fun BpmRecord.referenceConfidence(): Double {
        val floor = when {
            manuallyVerified -> 0.86
            sourceType == BpmSourceType.PUBLIC_REFERENCE -> 0.80
            else -> 0.75
        }
        return confidence.coerceIn(0.1, 1.0).coerceAtLeast(floor)
    }

    private fun List<BpmRecord>.distinctByBpm(): List<BpmRecord> {
        val selected = mutableListOf<BpmRecord>()
        forEach { record ->
            if (selected.none { it.bpm.isSameBpmAs(record.bpm) }) {
                selected += record
            }
        }
        return selected
    }

    private fun List<BpmCandidate>.distinctCandidatesByBpm(): List<BpmCandidate> {
        val selected = mutableListOf<BpmCandidate>()
        forEach { candidate ->
            if (selected.none { it.bpm.isSameBpmAs(candidate.bpm) }) {
                selected += candidate
            }
        }
        return selected
    }

    private fun List<Pair<BpmCandidate, BpmSourceType>>.distinctCandidatePairsByBpm(): List<Pair<BpmCandidate, BpmSourceType>> {
        val selected = mutableListOf<Pair<BpmCandidate, BpmSourceType>>()
        forEach { pair ->
            if (selected.none { it.first.bpm.isSameBpmAs(pair.first.bpm) }) {
                selected += pair
            }
        }
        return selected
    }

    private fun Double.isSameBpmAs(other: Double): Boolean {
        return kotlin.math.abs(this - other) < 1.0
    }

    private const val MIN_PUBLIC_REFERENCE_CONFIDENCE = 0.80
}

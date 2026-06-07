package com.example.integratedbpmmeter.audio

import kotlin.math.abs
import kotlin.math.max

data class TempoAnalysisResult(
    val candidates: List<BpmCandidate>,
    val engineName: String,
    val diagnostics: String,
    val segmentsAnalyzed: Int = 1,
    val agreementScore: Double = 0.0,
    val tempoFamily: String? = null,
    val engineWarnings: List<String> = emptyList()
) {
    val recommendedBpm: Double?
        get() = candidates.firstOrNull()?.bpm
}

interface TempoEngine {
    fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        minBpm: Int,
        maxBpm: Int
    ): TempoAnalysisResult
}

class KotlinTempoEngine : TempoEngine {
    override fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        minBpm: Int,
        maxBpm: Int
    ): TempoAnalysisResult {
        val candidates = BpmEstimator(minBpm, maxBpm).estimate(samples, sampleRate)
        val normalized = normalizeTempoFamilies(candidates)
        return TempoAnalysisResult(
            candidates = normalized,
            engineName = "Kotlin fallback",
            diagnostics = "energy/onset autocorrelation",
            segmentsAnalyzed = 1,
            agreementScore = normalized.firstOrNull()?.confidence ?: 0.0,
            tempoFamily = normalized.firstOrNull()?.let { tempoFamilyLabel(it.bpm) }
        )
    }
}

class NativeTempoEngine : TempoEngine {
    override fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        minBpm: Int,
        maxBpm: Int
    ): TempoAnalysisResult {
        if (!isAvailable) {
            return TempoAnalysisResult(emptyList(), "Native tempo", "native library unavailable")
        }

        return runCatching {
            val segmentResults = segmentWindows(samples, sampleRate)
                .map { window -> parseNativeCandidates(analyzeNative(window, sampleRate, minBpm, maxBpm)) }
                .filter { it.isNotEmpty() }
            val candidates = aggregateSegmentCandidates(segmentResults)
            val agreementScore = agreementScore(segmentResults, candidates.firstOrNull()?.bpm)
            TempoAnalysisResult(
                candidates = candidates,
                engineName = "Native tempo",
                diagnostics = "native multi-window onset/beat detector",
                segmentsAnalyzed = segmentResults.size,
                agreementScore = agreementScore,
                tempoFamily = candidates.firstOrNull()?.let { tempoFamilyLabel(it.bpm) },
                engineWarnings = when {
                    segmentResults.isEmpty() -> listOf("No native tempo lock")
                    segmentResults.size == 1 -> listOf("Only one segment produced a tempo lock")
                    agreementScore < 0.45 -> listOf("Low agreement between analysis segments")
                    else -> emptyList()
                }
            )
        }.getOrElse { error ->
            TempoAnalysisResult(
                candidates = emptyList(),
                engineName = "Native tempo",
                diagnostics = error.message ?: "native analysis failed"
            )
        }
    }

    private external fun analyzeNative(
        samples: FloatArray,
        sampleRate: Int,
        minBpm: Int,
        maxBpm: Int
    ): DoubleArray

    companion object {
        private val isAvailable: Boolean = runCatching {
            System.loadLibrary("integrated_bpm_native")
        }.isSuccess
    }
}

class HybridTempoEngine(
    private val nativeEngine: TempoEngine = NativeTempoEngine(),
    private val fallbackEngine: TempoEngine = KotlinTempoEngine()
) : TempoEngine {
    override fun analyze(
        samples: FloatArray,
        sampleRate: Int,
        minBpm: Int,
        maxBpm: Int
    ): TempoAnalysisResult {
        val native = nativeEngine.analyze(samples, sampleRate, minBpm, maxBpm)
        val fallback = fallbackEngine.analyze(samples, sampleRate, minBpm, maxBpm)
        val nativeReliability = native.reliabilityMultiplier()
        val fallbackReliability = if (nativeReliability < 0.82) 0.96 else 0.84
        val merged = normalizeTempoFamilies(
            native.candidates.map { it.copy(confidence = it.confidence * nativeReliability) } +
                fallback.candidates.map { it.copy(confidence = it.confidence * fallbackReliability) }
        )

        return when {
            native.candidates.isNotEmpty() -> TempoAnalysisResult(
                candidates = merged,
                engineName = "Native tempo + Kotlin fallback",
                diagnostics = native.diagnostics,
                segmentsAnalyzed = native.segmentsAnalyzed,
                agreementScore = native.agreementScore,
                tempoFamily = merged.firstOrNull()?.let { tempoFamilyLabel(it.bpm) },
                engineWarnings = native.engineWarnings + fallback.engineWarnings
            )
            fallback.candidates.isNotEmpty() -> fallback.copy(
                diagnostics = "${fallback.diagnostics}; ${native.diagnostics}"
            )
            else -> TempoAnalysisResult(
                candidates = emptyList(),
                engineName = "No tempo lock",
                diagnostics = "${native.diagnostics}; ${fallback.diagnostics}"
            )
        }
    }
}

private fun TempoAnalysisResult.reliabilityMultiplier(): Double {
    if (candidates.isEmpty()) return 0.0
    val agreementMultiplier = when {
        segmentsAnalyzed <= 1 -> 0.72
        agreementScore < 0.45 -> 0.58
        agreementScore < 0.60 -> 0.76
        agreementScore < 0.72 -> 0.90
        else -> 1.0
    }
    val warningPenalty = engineWarnings.sumOf { warning ->
        when {
            warning.contains("low agreement", ignoreCase = true) -> 0.16
            warning.contains("only one segment", ignoreCase = true) -> 0.12
            warning.contains("no native tempo lock", ignoreCase = true) -> 0.08
            else -> 0.03
        }
    }
    return (agreementMultiplier - warningPenalty).coerceIn(0.35, 1.0)
}

fun normalizeTempoFamilies(candidates: List<BpmCandidate>): List<BpmCandidate> {
    val cleaned = candidates
        .filter { it.bpm.isFinite() && it.confidence.isFinite() }
        .map { candidate ->
            candidate.copy(
                bpm = musicalTempoFor(candidate.bpm),
                confidence = (candidate.confidence * tempoPrior(candidate.bpm)).coerceIn(0.0, 1.0)
            )
        }
        .sortedByDescending { it.confidence }

    val buckets = mutableListOf<MutableList<BpmCandidate>>()
    cleaned.forEach { candidate ->
        val bucket = buckets.firstOrNull { existing ->
            existing.any { sameTempoFamily(it.bpm, candidate.bpm, tolerance = 1.5) }
        }
        if (bucket == null) {
            buckets += mutableListOf(candidate)
        } else {
            bucket += candidate
        }
    }

    return buckets
        .map { bucket -> familyRepresentative(bucket, directTolerance = 1.5, supportBoostPerCandidate = 0.04) }
        .sortedWith(
            compareByDescending<BpmCandidate> { it.confidence * tempoPrior(it.bpm) }
                .thenBy { abs(it.bpm - TACTUS_CENTER_BPM) }
        )
        .take(5)
}

fun normalizeRealtimeTempoCandidates(candidates: List<BpmCandidate>): List<BpmCandidate> {
    return normalizeTempoFamilies(candidates).take(3)
}

private fun musicalTempoFor(bpm: Double): Double {
    var value = bpm
    while (value > 210.0 && value / 2.0 >= 60.0) {
        value /= 2.0
    }
    if (value > 188.0 && value / 2.0 in 70.0..120.0) {
        value /= 2.0
    } else if (
        value > 182.0 &&
        value / 2.0 in 78.0..110.0 &&
        tempoPrior(value / 2.0) > tempoPrior(value) + 0.25
    ) {
        value /= 2.0
    }
    while (value < 68.0 && value * 2.0 <= 200.0) {
        value *= 2.0
    }
    return value
}

private fun tempoPrior(bpm: Double): Double {
    return when {
        bpm < 55.0 -> 0.35
        bpm < 68.0 -> 0.65
        bpm <= 150.0 -> 1.0
        bpm <= 159.0 -> 0.92
        bpm <= 182.0 -> 0.96
        bpm <= 200.0 -> 0.62
        else -> 0.35
    }
}

private const val TACTUS_CENTER_BPM = 118.0

fun tempoFamilyLabel(bpm: Double): String {
    val half = bpm / 2.0
    val double = bpm * 2.0
    return listOf(half, bpm, double)
        .filter { it in 30.0..300.0 }
        .joinToString(" / ") { String.format(java.util.Locale.US, "%.1f", it) }
}

private fun NativeTempoEngine.parseNativeCandidates(values: DoubleArray): List<BpmCandidate> {
    return values
        .asSequence()
        .chunked(2)
        .mapNotNull { pair ->
            val bpm = pair.getOrNull(0) ?: return@mapNotNull null
            val confidence = pair.getOrNull(1) ?: return@mapNotNull null
            BpmCandidate(
                bpm = bpm.coerceIn(30.0, 400.0),
                confidence = confidence.coerceIn(0.0, 1.0)
            )
        }
        .toList()
}

private fun segmentWindows(samples: FloatArray, sampleRate: Int): List<FloatArray> {
    if (sampleRate <= 0 || samples.size <= sampleRate * 35) return listOf(samples)

    val windowSize = (sampleRate * 30).coerceAtMost(samples.size)
    val starts = listOf(
        0,
        ((samples.size - windowSize) / 2).coerceAtLeast(0),
        (samples.size - windowSize).coerceAtLeast(0)
    ).distinct()

    return starts.map { start ->
        samples.copyOfRange(start, start + windowSize)
    }
}

internal fun aggregateSegmentCandidates(segmentResults: List<List<BpmCandidate>>): List<BpmCandidate> {
    if (segmentResults.isEmpty()) return emptyList()
    val indexedCandidates = segmentResults.flatMapIndexed { segmentIndex, candidates ->
        candidates.map { candidate ->
            IndexedTempoCandidate(
                segmentIndex = segmentIndex,
                candidate = candidate.copy(
                    bpm = musicalTempoFor(candidate.bpm),
                    confidence = (candidate.confidence * tempoPrior(candidate.bpm)).coerceIn(0.0, 1.0)
                )
            )
        }
    }
    val buckets = mutableListOf<MutableList<IndexedTempoCandidate>>()
    indexedCandidates.sortedByDescending { it.candidate.confidence }.forEach { indexed ->
        val bucket = buckets.firstOrNull { existing ->
            existing.any { sameTempoFamily(it.candidate.bpm, indexed.candidate.bpm, tolerance = 2.0) }
        }
        if (bucket == null) {
            buckets += mutableListOf(indexed)
        } else {
            bucket += indexed
        }
    }

    val segmentCount = segmentResults.size.coerceAtLeast(1)
    val aggregated = buckets.map { bucket ->
        val representative = familyRepresentative(
            bucket.map { it.candidate },
            directTolerance = 2.0,
            supportBoostPerCandidate = 0.0
        )
        val supportingSegments = bucket.map { it.segmentIndex }.distinct().size
        val support = supportingSegments.toDouble() / segmentCount
        val confidence = (representative.confidence * 0.52 + support * 0.48).coerceIn(0.0, 1.0)
        representative.copy(confidence = confidence)
    }

    return normalizeTempoFamilies(aggregated)
}

private data class IndexedTempoCandidate(
    val segmentIndex: Int,
    val candidate: BpmCandidate
)

private fun familyRepresentative(
    bucket: List<BpmCandidate>,
    directTolerance: Double,
    supportBoostPerCandidate: Double
): BpmCandidate {
    val directGroups = mutableListOf<MutableList<BpmCandidate>>()
    bucket.sortedByDescending { it.confidence }.forEach { candidate ->
        val group = directGroups.firstOrNull { existing ->
            existing.any { abs(it.bpm - candidate.bpm) <= directTolerance }
        }
        if (group == null) {
            directGroups += mutableListOf(candidate)
        } else {
            group += candidate
        }
    }

    val winner = directGroups
        .sortedWith(
            compareByDescending<MutableList<BpmCandidate>> { it.size }
                .thenByDescending { group -> group.maxOf { it.confidence * tempoPrior(it.bpm) } }
                .thenBy { group -> abs(weightedBpm(group) - TACTUS_CENTER_BPM) }
        )
        .first()
    val weightedBpm = weightedBpm(winner)
    val directBoost = winner.size.coerceAtMost(3) * supportBoostPerCandidate
    val familyBoost = (bucket.size - winner.size).coerceAtMost(2) * supportBoostPerCandidate * 0.35

    return BpmCandidate(
        bpm = weightedBpm,
        confidence = (winner.maxOf { it.confidence } + directBoost + familyBoost).coerceIn(0.0, 1.0)
    )
}

private fun weightedBpm(candidates: List<BpmCandidate>): Double {
    return candidates.sumOf { it.bpm * it.confidence } /
        candidates.sumOf { it.confidence }.coerceAtLeast(0.000001)
}

internal fun tempoAnalysisQualityScore(result: TempoAnalysisResult): Double {
    val confidence = result.candidates.firstOrNull()?.confidence ?: 0.0
    val segmentCoverage = when {
        result.segmentsAnalyzed >= 3 -> 1.0
        result.segmentsAnalyzed == 2 -> 0.78
        result.segmentsAnalyzed == 1 -> 0.45
        else -> 0.0
    }
    val warningPenalty = result.engineWarnings.sumOf { warning ->
        when {
            warning.contains("low agreement", ignoreCase = true) -> 0.18
            warning.contains("only one segment", ignoreCase = true) -> 0.14
            warning.contains("no native tempo lock", ignoreCase = true) -> 0.10
            else -> 0.04
        }
    }
    return (
        confidence * 0.50 +
            result.agreementScore.coerceIn(0.0, 1.0) * 0.30 +
            segmentCoverage * 0.20 -
            warningPenalty
        ).coerceIn(0.0, 1.0)
}

private fun agreementScore(segmentResults: List<List<BpmCandidate>>, recommendedBpm: Double?): Double {
    if (recommendedBpm == null || segmentResults.isEmpty()) return 0.0
    val matchingSegments = segmentResults.count { candidates ->
        candidates.any { sameTempoFamily(it.bpm, recommendedBpm, tolerance = 2.0) }
    }
    return matchingSegments.toDouble() / segmentResults.size
}

private fun sameTempoFamily(a: Double, b: Double, tolerance: Double): Boolean {
    return abs(a - b) <= tolerance ||
        abs(a * 2.0 - b) <= tolerance ||
        abs(a - b * 2.0) <= tolerance
}

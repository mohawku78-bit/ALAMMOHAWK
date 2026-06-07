package com.example.integratedbpmmeter.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlin.math.max
import kotlin.math.min

data class AudioFileMetadata(
    val displayName: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?
)

data class AudioAnalysisResult(
    val metadata: AudioFileMetadata,
    val candidates: List<BpmCandidate>,
    val analyzedSeconds: Double,
    val engineName: String,
    val diagnostics: String,
    val segmentsAnalyzed: Int,
    val agreementScore: Double,
    val tempoFamily: String?,
    val engineWarnings: List<String>
)

class AudioFileAnalyzer(
    private val context: Context,
    private val decoder: PcmDecoder = PcmDecoder(context),
    private val tempoEngine: TempoEngine = HybridTempoEngine()
) {
    fun readMetadata(uri: Uri): AudioFileMetadata {
        val displayName = queryDisplayName(uri)
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }

            AudioFileMetadata(
                displayName = displayName,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = durationMs
            )
        } finally {
            retriever.release()
        }
    }

    fun analyze(
        uri: Uri,
        maxAnalysisSeconds: Int = 90,
        minBpm: Int = 60,
        maxBpm: Int = 200
    ): AudioAnalysisResult {
        val metadata = readMetadata(uri)
        val durationUs = metadata.durationMs?.times(1_000L)
        val maxAnalysisUs = maxAnalysisSeconds.coerceIn(30, 120) * 1_000_000L
        val attempts = candidateStartPositions(durationUs, maxAnalysisUs)
        var bestPcm: PcmAudio? = null
        var bestResult = TempoAnalysisResult(emptyList(), "No tempo lock", "not analyzed")
        val sectionResults = mutableListOf<SectionTempoAnalysis>()
        var lastError: Throwable? = null

        for (startUs in attempts) {
            val availableUs = durationUs?.let { (it - startUs).coerceAtLeast(1_000_000L) } ?: maxAnalysisUs
            val decodeUs = min(maxAnalysisUs, availableUs)
            val result = runCatching {
                val pcm = decoder.decodeMono(uri, startUs, decodeUs)
                pcm to tempoEngine.analyze(pcm.samples, pcm.sampleRate, minBpm, maxBpm)
            }

            result
                .onSuccess { (pcm, analysis) ->
                    if (analysis.candidates.isNotEmpty()) {
                        sectionResults += SectionTempoAnalysis(startUs, analysis)
                    }
                    if (bestPcm == null || analysis.candidates.size > bestResult.candidates.size) {
                        bestPcm = pcm
                        bestResult = analysis
                    }
                    if (
                        analysis.candidates.isNotEmpty() &&
                        tempoAnalysisQualityScore(analysis) >= tempoAnalysisQualityScore(bestResult)
                    ) {
                        bestPcm = pcm
                        bestResult = analysis
                    }
                }
                .onFailure { lastError = it }
        }

        val consensusResult = TempoSectionAggregator.aggregate(sectionResults)
        if (TempoSectionAggregator.shouldPreferConsensus(consensusResult, bestResult, sectionResults)) {
            bestResult = consensusResult
        }

        val pcm = bestPcm ?: throw (lastError ?: IllegalStateException("Audio decode produced no samples"))
        val analyzedSeconds = secondsFor(pcm)

        return AudioAnalysisResult(
            metadata = metadata,
            candidates = bestResult.candidates,
            analyzedSeconds = analyzedSeconds,
            engineName = bestResult.engineName,
            diagnostics = bestResult.diagnostics,
            segmentsAnalyzed = bestResult.segmentsAnalyzed,
            agreementScore = bestResult.agreementScore,
            tempoFamily = bestResult.tempoFamily,
            engineWarnings = bestResult.engineWarnings
        )
    }

    private fun candidateStartPositions(durationUs: Long?, maxAnalysisUs: Long): List<Long> {
        val preferred = chooseStartUs(durationUs, maxAnalysisUs)
        val positions = mutableListOf(preferred, 0L, 15_000_000L)
        if (durationUs != null && durationUs > maxAnalysisUs) {
            positions += ((durationUs - maxAnalysisUs) / 2L).coerceAtLeast(0L)
        }
        return positions
            .map { it.coerceAtLeast(0L) }
            .distinct()
            .filter { durationUs == null || it < durationUs }
    }

    private fun chooseStartUs(durationUs: Long?, maxAnalysisUs: Long): Long {
        val skipIntroUs = 15_000_000L
        if (durationUs == null || durationUs <= 0L) return 0L
        if (durationUs <= skipIntroUs + 10_000_000L) return 0L
        if (durationUs <= skipIntroUs + maxAnalysisUs) return skipIntroUs
        return max(skipIntroUs, (durationUs - maxAnalysisUs) / 2L)
    }

    private fun secondsFor(pcm: PcmAudio): Double {
        return if (pcm.sampleRate > 0) {
            pcm.samples.size.toDouble() / pcm.sampleRate
        } else {
            0.0
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }
}

package com.example.integratedbpmmeter.audio

import com.example.integratedbpmmeter.data.BpmSourceType

enum class FileCandidateTrust {
    TRUSTED_REFERENCE,
    AUTO_ESTIMATE,
    NEEDS_VERIFICATION
}

fun fileCandidateTrust(
    sourceType: BpmSourceType?,
    candidate: BpmCandidate?,
    agreementScore: Double,
    segmentsAnalyzed: Int,
    engineWarnings: List<String>
): FileCandidateTrust {
    return when (sourceType) {
        BpmSourceType.TAP,
        BpmSourceType.NOW_PLAYING,
        BpmSourceType.PUBLIC_REFERENCE -> FileCandidateTrust.TRUSTED_REFERENCE

        BpmSourceType.PLAYBACK_CAPTURE,
        BpmSourceType.MIC_CAPTURE -> FileCandidateTrust.NEEDS_VERIFICATION

        BpmSourceType.FILE_ANALYSIS,
        null -> {
            val confidence = candidate?.confidence ?: 0.0
            val hasLowAgreementWarning = engineWarnings.any { warning ->
                warning.contains("low agreement", ignoreCase = true) ||
                    warning.contains("only one segment", ignoreCase = true)
            }
            val lowAgreement = segmentsAnalyzed >= 2 && agreementScore in 0.0..0.64
            val insufficientCoverage = segmentsAnalyzed in 0..1
            if (confidence < 0.82 || lowAgreement || insufficientCoverage || hasLowAgreementWarning) {
                FileCandidateTrust.NEEDS_VERIFICATION
            } else {
                FileCandidateTrust.AUTO_ESTIMATE
            }
        }
    }
}

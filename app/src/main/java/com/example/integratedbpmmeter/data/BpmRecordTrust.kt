package com.example.integratedbpmmeter.data

import kotlin.math.roundToInt

enum class BpmRecordTrust {
    TRUSTED,
    ESTIMATE,
    NEEDS_VERIFICATION
}

fun BpmRecord.trustLevel(): BpmRecordTrust {
    return when {
        manuallyVerified -> BpmRecordTrust.TRUSTED
        sourceType == BpmSourceType.TAP ||
            sourceType == BpmSourceType.NOW_PLAYING ||
            sourceType == BpmSourceType.PUBLIC_REFERENCE -> BpmRecordTrust.TRUSTED
        sourceType == BpmSourceType.FILE_ANALYSIS -> BpmRecordTrust.ESTIMATE
        else -> BpmRecordTrust.NEEDS_VERIFICATION
    }
}

fun BpmRecord.confidenceBadgeLabel(): String {
    return when (trustLevel()) {
        BpmRecordTrust.TRUSTED -> "${(confidence.coerceIn(0.0, 1.0) * 100).roundToInt()}%"
        BpmRecordTrust.ESTIMATE -> "Estimate"
        BpmRecordTrust.NEEDS_VERIFICATION -> "Check"
    }
}

fun BpmRecord.verificationHintLabel(): String? {
    return when {
        manuallyVerified -> "Verified"
        sourceType == BpmSourceType.FILE_ANALYSIS -> "Needs tap-check"
        sourceType == BpmSourceType.PLAYBACK_CAPTURE ||
            sourceType == BpmSourceType.MIC_CAPTURE -> "Experimental"
        else -> null
    }
}

fun BpmRecord.needsBpmReview(): Boolean {
    return trustLevel() != BpmRecordTrust.TRUSTED
}

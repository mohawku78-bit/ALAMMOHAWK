package com.example.integratedbpmmeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BpmRecordTrustTest {
    @Test
    fun trustedManualRecordsKeepConfidencePercent() {
        val record = record(
            sourceType = BpmSourceType.TAP,
            confidence = 0.864,
            manuallyVerified = true
        )

        assertEquals(BpmRecordTrust.TRUSTED, record.trustLevel())
        assertEquals("86%", record.confidenceBadgeLabel())
        assertEquals("Verified", record.verificationHintLabel())
    }

    @Test
    fun rawFileAnalysisRecordsAreShownAsEstimates() {
        val record = record(
            sourceType = BpmSourceType.FILE_ANALYSIS,
            confidence = 0.97,
            manuallyVerified = false
        )

        assertEquals(BpmRecordTrust.ESTIMATE, record.trustLevel())
        assertEquals("Estimate", record.confidenceBadgeLabel())
        assertEquals("Needs tap-check", record.verificationHintLabel())
        assertEquals(true, record.needsBpmReview())
    }

    @Test
    fun manuallyVerifiedFileEstimateLeavesNeedsReview() {
        val record = record(
            sourceType = BpmSourceType.FILE_ANALYSIS,
            confidence = 0.75,
            manuallyVerified = true
        )

        assertEquals(BpmRecordTrust.TRUSTED, record.trustLevel())
        assertEquals("75%", record.confidenceBadgeLabel())
        assertEquals("Verified", record.verificationHintLabel())
        assertEquals(false, record.needsBpmReview())
    }

    @Test
    fun experimentalCaptureRecordsNeedVerification() {
        val record = record(
            sourceType = BpmSourceType.MIC_CAPTURE,
            confidence = 0.91,
            manuallyVerified = false
        )

        assertEquals(BpmRecordTrust.NEEDS_VERIFICATION, record.trustLevel())
        assertEquals("Check", record.confidenceBadgeLabel())
        assertEquals("Experimental", record.verificationHintLabel())
        assertEquals(true, record.needsBpmReview())
    }

    @Test
    fun publicReferencesRemainTrustedReferences() {
        val record = record(
            sourceType = BpmSourceType.PUBLIC_REFERENCE,
            confidence = 0.8,
            manuallyVerified = false
        )

        assertEquals(BpmRecordTrust.TRUSTED, record.trustLevel())
        assertEquals("80%", record.confidenceBadgeLabel())
        assertEquals(null, record.verificationHintLabel())
        assertEquals(false, record.needsBpmReview())
    }

    private fun record(
        sourceType: BpmSourceType,
        confidence: Double,
        manuallyVerified: Boolean
    ): BpmRecord {
        return BpmRecord(
            title = "Track",
            artist = "Artist",
            bpm = 128.0,
            sourceType = sourceType,
            confidence = confidence,
            manuallyVerified = manuallyVerified,
            createdAt = 0L
        )
    }
}

package com.example.integratedbpmmeter.audio

import com.example.integratedbpmmeter.data.BpmSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class FileCandidateTrustTest {
    @Test
    fun tapAndPublicCandidatesAreTrustedReferences() {
        assertEquals(
            FileCandidateTrust.TRUSTED_REFERENCE,
            fileCandidateTrust(
                sourceType = BpmSourceType.TAP,
                candidate = BpmCandidate(128.0, 0.55),
                agreementScore = 0.0,
                segmentsAnalyzed = 0,
                engineWarnings = emptyList()
            )
        )
        assertEquals(
            FileCandidateTrust.TRUSTED_REFERENCE,
            fileCandidateTrust(
                sourceType = BpmSourceType.PUBLIC_REFERENCE,
                candidate = BpmCandidate(128.0, 0.82),
                agreementScore = 0.0,
                segmentsAnalyzed = 0,
                engineWarnings = emptyList()
            )
        )
    }

    @Test
    fun confidentFileAnalysisIsStillLabeledAsAnEstimate() {
        assertEquals(
            FileCandidateTrust.AUTO_ESTIMATE,
            fileCandidateTrust(
                sourceType = BpmSourceType.FILE_ANALYSIS,
                candidate = BpmCandidate(128.0, 0.86),
                agreementScore = 0.72,
                segmentsAnalyzed = 3,
                engineWarnings = emptyList()
            )
        )
    }

    @Test
    fun lowAgreementFileAnalysisNeedsVerification() {
        assertEquals(
            FileCandidateTrust.NEEDS_VERIFICATION,
            fileCandidateTrust(
                sourceType = BpmSourceType.FILE_ANALYSIS,
                candidate = BpmCandidate(128.0, 0.86),
                agreementScore = 0.34,
                segmentsAnalyzed = 3,
                engineWarnings = emptyList()
            )
        )
    }

    @Test
    fun oneSegmentWarningNeedsVerification() {
        assertEquals(
            FileCandidateTrust.NEEDS_VERIFICATION,
            fileCandidateTrust(
                sourceType = BpmSourceType.FILE_ANALYSIS,
                candidate = BpmCandidate(128.0, 0.90),
                agreementScore = 0.90,
                segmentsAnalyzed = 1,
                engineWarnings = listOf("Only one segment produced a tempo lock")
            )
        )
    }
}

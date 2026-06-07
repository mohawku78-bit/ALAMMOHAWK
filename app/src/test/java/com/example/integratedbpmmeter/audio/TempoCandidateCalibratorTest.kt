package com.example.integratedbpmmeter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoCandidateCalibratorTest {
    @Test
    fun fastSubdivisionLosesWhenHalfTimeCandidateIsCompetitive() {
        val result = TempoCandidateCalibrator.calibrate(
            analysisCandidates = listOf(
                BpmCandidate(192.0, 0.92),
                BpmCandidate(96.0, 0.64),
                BpmCandidate(128.0, 0.58)
            ),
            agreementScore = 0.54,
            segmentsAnalyzed = 3
        )

        assertEquals(96.0, result.candidates.first().bpm, 1.0)
        assertTrue(result.reasonLabels.first() in listOf("Low-band pulse", "Stable segments", "Needs tap-check"))
    }

    @Test
    fun strong180TempoStaysAsRunningHighPace() {
        val result = TempoCandidateCalibrator.calibrate(
            analysisCandidates = listOf(
                BpmCandidate(180.0, 0.96),
                BpmCandidate(90.0, 0.55)
            ),
            agreementScore = 0.76,
            segmentsAnalyzed = 3
        )

        assertEquals(180.0, result.candidates.first().bpm, 1.0)
    }

    @Test
    fun referenceFamilyMatchLabelsCandidate() {
        val result = TempoCandidateCalibrator.calibrate(
            analysisCandidates = listOf(
                BpmCandidate(192.0, 0.90),
                BpmCandidate(96.0, 0.50)
            ),
            referenceCandidates = listOf(BpmCandidate(96.0, 0.90)),
            agreementScore = 0.50,
            segmentsAnalyzed = 2
        )

        assertEquals(96.0, result.candidates.first().bpm, 1.0)
        assertEquals("Reference family match", result.reasonLabels.first())
    }
}

package com.example.integratedbpmmeter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisRangeRiskTest {
    @Test
    fun defaultRangeIsNotFlagged() {
        val settings = AppSettings(minBpm = 60, maxBpm = 200)

        assertNull(settings.analysisRangeRisk())
    }

    @Test
    fun fastOnlyRangeIsFlagged() {
        val settings = AppSettings(minBpm = 200, maxBpm = 240)

        assertEquals(AnalysisRangeRisk.FAST_ONLY, settings.analysisRangeRisk())
    }

    @Test
    fun narrowRangeIsFlagged() {
        val settings = AppSettings(minBpm = 90, maxBpm = 120)

        assertEquals(AnalysisRangeRisk.TOO_NARROW, settings.analysisRangeRisk())
    }
}

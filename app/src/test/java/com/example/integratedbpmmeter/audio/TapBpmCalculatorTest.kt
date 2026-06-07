package com.example.integratedbpmmeter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TapBpmCalculatorTest {
    @Test
    fun steadyHalfSecondTapsProduce120Bpm() {
        val calculator = TapBpmCalculator()

        val snapshot = listOf(0L, 500_000_000L, 1_000_000_000L, 1_500_000_000L)
            .fold(TapBpmSnapshot()) { _, timestamp -> calculator.addTap(timestamp) }

        assertNotNull(snapshot.bpm)
        assertEquals(120.0, snapshot.bpm ?: 0.0, 0.01)
        assertEquals(4, snapshot.tapCount)
    }

    @Test
    fun halfAndDoubleAdjustCurrentTempo() {
        val calculator = TapBpmCalculator()
        listOf(0L, 500_000_000L, 1_000_000_000L, 1_500_000_000L)
            .forEach { calculator.addTap(it) }

        val half = calculator.half()
        assertEquals(60.0, half.bpm ?: 0.0, 0.01)

        val doubled = calculator.double()
        assertEquals(120.0, doubled.bpm ?: 0.0, 0.01)
    }

    @Test
    fun longGapStartsFreshMeasurement() {
        val calculator = TapBpmCalculator(resetAfterGapNanos = 2_000_000_000L)
        listOf(0L, 500_000_000L, 1_000_000_000L).forEach { calculator.addTap(it) }

        val snapshot = calculator.addTap(4_000_000_000L)

        assertEquals(1, snapshot.tapCount)
        assertEquals(null, snapshot.bpm)
    }
}

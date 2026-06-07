package com.example.integratedbpmmeter.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BpmPlaylistRangeTest {
    @Test
    fun presetRangeMatchesDirectBpm() {
        val record = record(bpm = 164.0)

        assertTrue(record.matchesPlaylistRange(BpmRangePreset.BPM_160.range))
        assertFalse(record.matchesPlaylistRange(BpmRangePreset.BPM_170.range))
    }

    @Test
    fun presetRangeMatchesDoubleTimeCompatibleBpm() {
        val record = record(bpm = 82.0)

        assertTrue(record.matchesPlaylistRange(BpmRangePreset.BPM_160.range))
        assertFalse(record.matchesPlaylistRange(BpmRangePreset.BPM_170.range))
    }

    @Test
    fun customRangeNormalizesMinAndMax() {
        val range = BpmPlaylistRange(minBpm = 180.0, maxBpm = 160.0)

        assertTrue(range.contains(170.0))
        assertFalse(range.contains(181.0))
    }

    private fun record(bpm: Double): BpmRecord {
        return BpmRecord(
            title = "Track",
            bpm = bpm,
            sourceType = BpmSourceType.TAP,
            confidence = 1.0,
            createdAt = 1L
        )
    }
}

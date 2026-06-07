package com.example.integratedbpmmeter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BpmSmartCategoryTest {
    @Test
    fun classifiesWorkoutTempoRanges() {
        assertEquals(BpmSmartCategory.HIGH_PACE, BpmSmartCategory.fromBpm(170.0))
        assertEquals(BpmSmartCategory.MID_PACE, BpmSmartCategory.fromBpm(145.0))
        assertEquals(BpmSmartCategory.GROOVE_GENERAL, BpmSmartCategory.fromBpm(128.0))
        assertEquals(BpmSmartCategory.LOW_DOUBLE_TIME, BpmSmartCategory.fromBpm(82.0))
        assertEquals(BpmSmartCategory.WARM_UP_COOL_DOWN, BpmSmartCategory.fromBpm(60.0))
    }

    @Test
    fun doubleTimeCompatibilityOnlyAppliesToSeventyToNinetyBpm() {
        assertEquals(164.0, 82.0.doubleTimeCompatibleBpm() ?: 0.0, 0.001)
        assertEquals(180.0, 90.0.doubleTimeCompatibleBpm() ?: 0.0, 0.001)
        assertNull(69.9.doubleTimeCompatibleBpm())
        assertNull(90.1.doubleTimeCompatibleBpm())
    }

    @Test
    fun halfTimeFeelAppliesToHighRunningTempo() {
        assertEquals(90.0, 180.0.halfTimeFeelBpm() ?: 0.0, 0.001)
        assertEquals(80.0, 160.0.halfTimeFeelBpm() ?: 0.0, 0.001)
        assertNull(159.9.halfTimeFeelBpm())
        assertNull(200.1.halfTimeFeelBpm())
    }
}

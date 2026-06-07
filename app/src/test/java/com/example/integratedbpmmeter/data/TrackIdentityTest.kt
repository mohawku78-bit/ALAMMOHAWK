package com.example.integratedbpmmeter.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackIdentityTest {
    @Test
    fun likelySameTrackIgnoresRemasterNoise() {
        val saved = BpmRecord(
            title = "EMI",
            artist = "Sex Pistols",
            bpm = 132.0,
            sourceType = BpmSourceType.TAP,
            confidence = 0.9,
            createdAt = 1L
        )

        assertTrue(
            TrackIdentity.isLikelySameTrack(
                record = saved,
                title = "EMI (Remastered Ver.)",
                artist = "Sex Pistols"
            )
        )
    }

    @Test
    fun likelySameTrackRejectsDifferentTitle() {
        val saved = BpmRecord(
            title = "Something",
            artist = "The Beatles",
            bpm = 66.0,
            sourceType = BpmSourceType.NOW_PLAYING,
            confidence = 0.8,
            createdAt = 1L
        )

        assertFalse(
            TrackIdentity.isLikelySameTrack(
                record = saved,
                title = "Come Together",
                artist = "The Beatles"
            )
        )
    }
}

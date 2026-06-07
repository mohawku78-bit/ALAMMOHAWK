package com.example.integratedbpmmeter.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebBpmTextParserTest {
    @Test
    fun parsesEnglishBpmAnswer() {
        val result = WebBpmTextParser.parse("The song is commonly listed at 128 BPM.")

        assertNotNull(result)
        assertEquals(128.0, result!!.bpm, 0.01)
    }

    @Test
    fun parsesKoreanAiStyleAnswer() {
        val result = WebBpmTextParser.parse("이 곡의 BPM은 약 92입니다.")

        assertNotNull(result)
        assertEquals(92.0, result!!.bpm, 0.01)
    }

    @Test
    fun parsesRangeAsAverage() {
        val result = WebBpmTextParser.parse("AI answer says tempo is about 90-92 BPM.")

        assertNotNull(result)
        assertEquals(91.0, result!!.bpm, 0.01)
    }

    @Test
    fun ignoresTextWithoutBpm() {
        assertNull(WebBpmTextParser.parse("Released in 2024 by an artist."))
    }
}

package com.example.integratedbpmmeter.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioMatcherTest {
    @Test
    fun displayNameWithTrackNumberAndArtistPrefixStillMatches() {
        val candidate = candidate(
            title = null,
            artist = "Backbeat Band",
            displayName = "01 - Backbeat Band - Long Tall Sally.mp3"
        )

        val score = candidate.scoreAgainst(
            wantedTitle = "Long Tall Sally",
            wantedArtist = "Backbeat Band",
            wantedAlbum = null,
            wantedDurationMs = null
        )

        assertTrue(score >= MIN_LOCAL_AUDIO_MATCH_SCORE)
    }

    @Test
    fun unknownArtistStillUsesArtistInDisplayName() {
        val candidate = candidate(
            title = "Long Tall Sally",
            artist = "<unknown>",
            displayName = "Backbeat Band - Long Tall Sally.mp3"
        )

        val score = candidate.scoreAgainst(
            wantedTitle = "Long Tall Sally",
            wantedArtist = "Backbeat Band",
            wantedAlbum = null,
            wantedDurationMs = null
        )

        assertTrue(score >= MIN_LOCAL_AUDIO_MATCH_SCORE)
    }

    @Test
    fun matchingArtistWinsWhenTwoFilesShareTheSameTitle() {
        val wantedTitle = "Long Tall Sally"
        val wantedArtist = "Backbeat Band"
        val matchingArtist = candidate(title = wantedTitle, artist = wantedArtist)
        val wrongArtist = candidate(title = wantedTitle, artist = "Little Richard")

        val matchingScore = matchingArtist.scoreAgainst(wantedTitle, wantedArtist, null, null)
        val wrongScore = wrongArtist.scoreAgainst(wantedTitle, wantedArtist, null, null)

        assertTrue(matchingScore > wrongScore)
        assertTrue(matchingScore >= MIN_LOCAL_AUDIO_MATCH_SCORE)
    }

    @Test
    fun oneCharacterHangulTitleCanBeUsedAsSearchToken() {
        assertEquals("봄", "봄".bestLocalAudioSearchToken())
    }

    @Test
    fun apostropheAndRemasterNoiseStillMatch() {
        val candidate = candidate(
            title = "She's Electric - Remastered 2009",
            artist = "Oasis"
        )

        val score = candidate.scoreAgainst(
            wantedTitle = "She's Electric",
            wantedArtist = "Oasis",
            wantedAlbum = null,
            wantedDurationMs = null
        )

        assertTrue(score >= MIN_LOCAL_AUDIO_MATCH_SCORE)
    }

    @Test
    fun looseTitleOverlapWithDifferentArtistDoesNotMatch() {
        val candidate = candidate(
            title = "Sound of Music",
            artist = "Original Cast"
        )

        val score = candidate.scoreAgainst(
            wantedTitle = "The Sound",
            wantedArtist = "The 1975",
            wantedAlbum = null,
            wantedDurationMs = null
        )

        assertTrue(score < MIN_LOCAL_AUDIO_MATCH_SCORE)
    }

    private fun candidate(
        title: String?,
        artist: String?,
        album: String? = null,
        durationMs: Long? = null,
        displayName: String? = null
    ): LocalAudioCandidate {
        return LocalAudioCandidate(
            id = 1L,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            displayName = displayName,
            dataPath = null
        )
    }
}

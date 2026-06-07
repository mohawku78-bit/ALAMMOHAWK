package com.example.integratedbpmmeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BpmRepositoryTest {
    @Test
    fun findLocalReferencesFallsBackWhenPunctuationBreaksSqlLikeToken() = runTest {
        val dao = FakeBpmDao(
            records = listOf(
                BpmRecord(
                    id = 1L,
                    title = "She's Electric",
                    artist = "Oasis",
                    bpm = 128.2,
                    sourceType = BpmSourceType.NOW_PLAYING,
                    confidence = 0.82,
                    manuallyVerified = true,
                    createdAt = 10L
                ),
                BpmRecord(
                    id = 2L,
                    title = "She Bangs",
                    artist = "Oasis",
                    bpm = 120.0,
                    sourceType = BpmSourceType.TAP,
                    confidence = 0.9,
                    manuallyVerified = true,
                    createdAt = 11L
                )
            )
        )

        val matches = BpmRepository(dao).findLocalReferences("She's Electric", "Oasis")

        assertEquals(1, matches.size)
        assertEquals("She's Electric", matches.first().title)
        assertEquals(128.2, matches.first().bpm, 0.001)
    }

    private class FakeBpmDao(
        private val records: List<BpmRecord>
    ) : BpmDao {
        override fun observeAll(): Flow<List<BpmRecord>> = flowOf(records)

        override fun search(query: String): Flow<List<BpmRecord>> = flowOf(records)

        override suspend fun findLocalReferences(titleToken: String, artistToken: String?): List<BpmRecord> {
            return records.filter { record ->
                record.title.contains(titleToken, ignoreCase = true) &&
                    (
                        artistToken.isNullOrBlank() ||
                            record.artist.orEmpty().contains(artistToken, ignoreCase = true)
                    )
            }
        }

        override suspend fun insert(record: BpmRecord): Long = record.id

        override suspend fun update(record: BpmRecord) = Unit

        override suspend fun delete(record: BpmRecord) = Unit
    }
}

package com.example.integratedbpmmeter.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class BpmRepository(private val dao: BpmDao) {
    fun observeRecords(query: String): Flow<List<BpmRecord>> {
        val normalized = query.trim()
        return if (normalized.isBlank()) dao.observeAll() else dao.search(normalized)
    }

    suspend fun insert(record: BpmRecord): Long = dao.insert(record)

    suspend fun update(record: BpmRecord) = dao.update(record)

    suspend fun delete(record: BpmRecord) = dao.delete(record)

    suspend fun findLocalReferences(title: String, artist: String?): List<BpmRecord> {
        val titleToken = TrackIdentity.normalizedSearchToken(title)
        if (titleToken.isBlank()) return emptyList()
        val artistToken = artist?.let(TrackIdentity::normalizedSearchToken).orEmpty()
        val broadTitleToken = titleToken.split(Regex("""\s+"""))
            .firstOrNull()
            .orEmpty()
        val records = buildList {
            addAll(dao.findLocalReferences(titleToken, artistToken))
            if (broadTitleToken.isNotBlank() && broadTitleToken != titleToken) {
                addAll(dao.findLocalReferences(broadTitleToken, artistToken))
            }
        }
        return records
            .filter { TrackIdentity.isLikelySameTrack(it, title, artist) }
            .distinctBy { "${it.title.lowercase(Locale.US)}|${it.artist?.lowercase(Locale.US)}|${it.bpm}" }
            .take(3)
    }

    companion object {
        fun from(context: Context): BpmRepository {
            return BpmRepository(AppDatabase.getInstance(context).bpmDao())
        }
    }
}

object TrackIdentity {
    private val bracketContent = Regex("""[\[(（【].*?[\])）】]""")
    private val separators = Regex("""\s+[-–—]\s+.*$""")
    private val noiseWords = Regex(
        """\b(remaster(ed)?|mono|stereo|live|explicit|clean|radio edit|edit|version|soundtrack|ost|feat\.?|featuring|with)\b""",
        RegexOption.IGNORE_CASE
    )
    private val nonWord = Regex("""[^a-z0-9가-힣]+""")

    fun normalizedSearchToken(value: String): String {
        return value
            .lowercase(Locale.US)
            .replace(bracketContent, " ")
            .replace(separators, " ")
            .replace(noiseWords, " ")
            .replace(nonWord, " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.length > 1 }
            .take(4)
            .joinToString(" ")
    }

    fun isLikelySameTrack(record: BpmRecord, title: String, artist: String?): Boolean {
        val wantedTitle = normalizedSearchToken(title)
        val recordTitle = normalizedSearchToken(record.title)
        if (wantedTitle.isBlank() || recordTitle.isBlank()) return false
        val titleMatches = recordTitle == wantedTitle ||
            recordTitle.contains(wantedTitle) ||
            wantedTitle.contains(recordTitle)
        if (!titleMatches) return false

        val wantedArtist = artist?.let(::normalizedSearchToken).orEmpty()
        val recordArtist = record.artist?.let(::normalizedSearchToken).orEmpty()
        return wantedArtist.isBlank() ||
            recordArtist.isBlank() ||
            recordArtist.contains(wantedArtist) ||
            wantedArtist.contains(recordArtist)
    }
}

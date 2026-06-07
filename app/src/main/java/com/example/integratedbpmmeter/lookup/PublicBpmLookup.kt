package com.example.integratedbpmmeter.lookup

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class PublicBpmCandidate(
    val title: String,
    val artist: String?,
    val bpm: Double,
    val source: String,
    val matchScore: Double,
    val sourceUrl: String
)

private data class MusicBrainzRecording(
    val id: String,
    val title: String,
    val artist: String?,
    val score: Int
)

class PublicBpmLookup {
    fun lookup(title: String, artist: String?): List<PublicBpmCandidate> {
        val recordings = searchMusicBrainz(title, artist).take(MAX_RECORDINGS)
        if (recordings.isEmpty()) return emptyList()

        val bpmByMbid = lookupAcousticBrainz(recordings.map { it.id })
        return recordings
            .mapNotNull { recording ->
                val bpm = bpmByMbid[recording.id.lowercase(Locale.US)] ?: return@mapNotNull null
                PublicBpmCandidate(
                    title = recording.title,
                    artist = recording.artist,
                    bpm = bpm,
                    source = "MusicBrainz + AcousticBrainz",
                    matchScore = (recording.score / 100.0).coerceIn(0.0, 1.0),
                    sourceUrl = "https://musicbrainz.org/recording/${recording.id}"
                )
            }
            .distinctBy { "${it.title.lowercase(Locale.US)}|${it.artist.orEmpty().lowercase(Locale.US)}|${it.bpm.toInt()}" }
            .sortedWith(compareByDescending<PublicBpmCandidate> { it.matchScore }.thenBy { it.title })
            .take(3)
    }

    private fun searchMusicBrainz(title: String, artist: String?): List<MusicBrainzRecording> {
        return musicBrainzQueries(title, artist)
            .flatMap { query -> searchMusicBrainzQuery(query, title) }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<MusicBrainzRecording> { it.score }.thenBy { it.title })
    }

    private fun musicBrainzQueries(title: String, artist: String?): List<String> {
        val cleanTitle = title.cleanSearchTerm()
        val cleanArtist = artist?.cleanSearchTerm().orEmpty()
        val query = buildString {
            append("recording:\"")
            append(cleanTitle.escapeMusicBrainzQuery())
            append("\"")
            if (cleanArtist.isNotBlank()) {
                append(" AND artist:\"")
                append(cleanArtist.escapeMusicBrainzQuery())
                append("\"")
            }
        }
        val broadQuery = listOf(cleanTitle, cleanArtist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return listOf(query, broadQuery)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun searchMusicBrainzQuery(query: String, fallbackTitle: String): List<MusicBrainzRecording> {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://musicbrainz.org/ws/2/recording?query=$encodedQuery&fmt=json&limit=$MAX_RECORDINGS"
        val json = getJson(url)
        val recordings = json.optJSONArray("recordings") ?: return emptyList()
        val results = mutableListOf<MusicBrainzRecording>()

        for (index in 0 until recordings.length()) {
            val item = recordings.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val recordingTitle = item.optString("title").takeIf { it.isNotBlank() } ?: fallbackTitle
            val recordingArtist = item.optJSONArray("artist-credit")
                ?.let { credits ->
                    buildList {
                        for (creditIndex in 0 until credits.length()) {
                            credits.optJSONObject(creditIndex)
                                ?.optString("name")
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::add)
                        }
                    }.joinToString(", ").ifBlank { null }
                }

            results += MusicBrainzRecording(
                id = id,
                title = recordingTitle,
                artist = recordingArtist,
                score = item.optInt("score", 0)
            )
        }

        return results
    }

    private fun lookupAcousticBrainz(mbids: List<String>): Map<String, Double> {
        if (mbids.isEmpty()) return emptyMap()
        val bulkValues = runCatching { lookupAcousticBrainzBulk(mbids) }.getOrDefault(emptyMap())
        if (bulkValues.isNotEmpty()) return bulkValues

        return mbids
            .mapNotNull { mbid ->
                val bpm = runCatching { lookupAcousticBrainzSingle(mbid) }.getOrNull()
                    ?: return@mapNotNull null
                mbid.lowercase(Locale.US) to bpm
            }
            .toMap()
    }

    private fun lookupAcousticBrainzBulk(mbids: List<String>): Map<String, Double> {
        val ids = mbids.joinToString(";")
        val url = "https://acousticbrainz.org/api/v1/low-level?recording_ids=$ids&features=rhythm.bpm"
        val json = getJson(url)
        val values = mutableMapOf<String, Double>()

        mbids.forEach { mbid ->
            val key = mbid.lowercase(Locale.US)
            val mbidJson = json.optJSONObject(key) ?: return@forEach
            val firstOffsetKey = mbidJson.keys().asSequence().firstOrNull() ?: return@forEach
            val bpm = mbidJson
                .optJSONObject(firstOffsetKey)
                ?.optJSONObject("rhythm")
                ?.optDouble("bpm")
                ?.takeIf { it.isFinite() && it in MIN_PUBLIC_BPM..MAX_PUBLIC_BPM }
                ?: return@forEach

            values[key] = bpm
        }

        return values
    }

    private fun lookupAcousticBrainzSingle(mbid: String): Double? {
        val url = "https://acousticbrainz.org/api/v1/$mbid/low-level"
        val json = getJson(url)
        return json
            .optJSONObject("rhythm")
            ?.optDouble("bpm")
            ?.takeIf { it.isFinite() && it in MIN_PUBLIC_BPM..MAX_PUBLIC_BPM }
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return connection.use {
            val responseCode = responseCode
            val stream = if (responseCode in 200..299) inputStream else errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (responseCode !in 200..299) {
                error("Public BPM lookup failed with HTTP $responseCode")
            }
            JSONObject(body)
        }
    }

    private fun String.escapeMusicBrainzQuery(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"").trim()
    }

    private fun String.cleanSearchTerm(): String {
        return substringBeforeLast('.')
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*(official|lyrics?|audio|video|mv|remaster|remastered)[^)]*\\)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private inline fun <T> HttpURLConnection.use(block: HttpURLConnection.() -> T): T {
        return try {
            block()
        } finally {
            disconnect()
        }
    }

    companion object {
        private const val MAX_RECORDINGS = 8
        private const val TIMEOUT_MS = 10_000
        private const val MIN_PUBLIC_BPM = 30.0
        private const val MAX_PUBLIC_BPM = 260.0
        private const val USER_AGENT = "IntegratedBpmMeter/1.0 (android-app://com.example.integratedbpmmeter)"
    }
}

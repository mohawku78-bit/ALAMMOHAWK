package com.example.integratedbpmmeter.media

import java.util.Locale

internal const val MIN_LOCAL_AUDIO_MATCH_SCORE = 0.62

internal data class LocalAudioCandidate(
    val id: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val displayName: String?,
    val dataPath: String?
) {
    fun scoreAgainst(
        wantedTitle: String?,
        wantedArtist: String?,
        wantedAlbum: String?,
        wantedDurationMs: Long?
    ): Double {
        val titleScore = maxOf(
            similarity(wantedTitle, title),
            similarity(wantedTitle, displayName?.substringBeforeLast('.'))
        )
        if (titleScore < 0.45) return 0.0

        val displayStem = displayName?.substringBeforeLast('.')
        val artistScore = maxOf(
            optionalSimilarity(wantedArtist, artist.takeUnless { it.isUnknownAudioMetadata() }),
            optionalSimilarity(wantedArtist, displayStem)
        )
        val albumScore = optionalSimilarity(wantedAlbum, album)
        val durationScore = durationSimilarity(wantedDurationMs, durationMs)
        val rawScore = titleScore * 0.68 + artistScore * 0.18 + albumScore * 0.07 + durationScore * 0.07
        val artistPenalty = if (wantedArtist.hasUsefulText() && artist.hasUsefulText() && artistScore < 0.25) {
            if (titleScore >= 0.96) 0.04 else 0.12
        } else {
            0.0
        }
        val strongArtistBonus = if (artistScore >= 0.86 && titleScore >= 0.70) 0.03 else 0.0
        return (rawScore - artistPenalty + strongArtistBonus).coerceIn(0.0, 1.0)
    }
}

internal data class LocalAudioMatch(
    val candidate: LocalAudioCandidate,
    val score: Double
)

internal fun String?.bestLocalAudioSearchToken(): String? {
    val tokens = normalizedTokens()
    return tokens
        .filterNot { it in weakSearchTokens }
        .maxByOrNull { token -> token.length + if (token.hasNonAscii()) 2 else 0 }
        ?: tokens.maxByOrNull { token -> token.length + if (token.hasNonAscii()) 2 else 0 }
        ?: this?.trim()?.takeIf { it.length >= 2 || it.hasNonAscii() }?.take(24)
}

internal fun String.toLikeArg(): String {
    return "%${replace("%", "").replace("_", "")}%"
}

private fun similarity(left: String?, right: String?): Double {
    val leftText = left.normalizedText()
    val rightText = right.normalizedText()
    if (leftText.isBlank() || rightText.isBlank()) return 0.0
    if (leftText == rightText) return 1.0
    if (leftText.contains(rightText) || rightText.contains(leftText)) return 0.86

    val leftTokens = left.normalizedTokens().toSet()
    val rightTokens = right.normalizedTokens().toSet()
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
    val overlap = leftTokens.intersect(rightTokens).size.toDouble()
    val balancedCoverage = overlap / maxOf(leftTokens.size, rightTokens.size).toDouble()
    val wantedCoverage = overlap / leftTokens.size.toDouble()
    val candidateCoverage = overlap / rightTokens.size.toDouble()
    return maxOf(
        balancedCoverage,
        minOf(wantedCoverage, candidateCoverage) * 0.92
    )
}

private fun optionalSimilarity(left: String?, right: String?): Double {
    val leftText = left.normalizedText()
    if (leftText.isBlank()) return 0.65
    return similarity(left, right)
}

private fun durationSimilarity(left: Long?, right: Long?): Double {
    if (left == null || right == null) return 0.5
    val delta = kotlin.math.abs(left - right)
    return when {
        delta <= 1_500L -> 1.0
        delta <= 5_000L -> 0.8
        delta <= 12_000L -> 0.45
        else -> 0.0
    }
}

private fun String?.normalizedText(): String {
    return normalizedTokens().joinToString(" ")
}

private fun String?.normalizedTokens(): List<String> {
    if (this == null) return emptyList()
    return lowercase(Locale.US)
        .replace(Regex("""[\[(].*?[\])]"""), " ")
        .replace(
            Regex(
                """\b(remaster(ed)?|mono|stereo|live|explicit|clean|radio edit|edit|version|soundtrack|ost|official|audio|lyrics?|lyric video|music video|feat\.?|featuring|with)\b""",
                RegexOption.IGNORE_CASE
            ),
            " "
        )
        .split(Regex("""[^\p{L}\p{N}]+"""))
        .map { it.trim() }
        .filter { it.length >= 2 || it.hasNonAscii() }
}

private fun String?.hasUsefulText(): Boolean {
    return normalizedText().isNotBlank() && !isUnknownAudioMetadata()
}

private fun String.hasNonAscii(): Boolean {
    return any { it.code > 127 }
}

private fun String?.isUnknownAudioMetadata(): Boolean {
    val text = normalizedText()
    return text == "unknown" || text == "unknown artist"
}

private val weakSearchTokens = setOf(
    "the",
    "and",
    "you",
    "your",
    "with",
    "from",
    "song",
    "track"
)

package com.example.integratedbpmmeter.lookup

data class ParsedWebBpm(
    val bpm: Double,
    val confidence: Double,
    val label: String
)

object WebBpmTextParser {
    fun parse(text: String): ParsedWebBpm? {
        val normalized = text
            .replace(',', '.')
            .replace('–', '-')
            .replace('—', '-')
            .trim()
        if (normalized.isBlank()) return null

        parseRange(normalized)?.let { return it }
        parseKeywordNearNumber(normalized)?.let { return it }
        parseNumberBeforeBpm(normalized)?.let { return it }
        return null
    }

    private fun parseRange(text: String): ParsedWebBpm? {
        val match = RANGE_REGEX.find(text) ?: return null
        val first = match.groupValues[1].toBpmOrNull() ?: return null
        val second = match.groupValues[2].toBpmOrNull() ?: return null
        if (first !in MIN_BPM..MAX_BPM || second !in MIN_BPM..MAX_BPM) return null
        val average = (first + second) / 2.0
        return ParsedWebBpm(
            bpm = average,
            confidence = 0.74,
            label = "${formatBpm(first)}-${formatBpm(second)} BPM"
        )
    }

    private fun parseKeywordNearNumber(text: String): ParsedWebBpm? {
        val match = KEYWORD_NUMBER_REGEX.find(text) ?: return null
        val bpm = match.groupValues[1].toBpmOrNull() ?: return null
        if (bpm !in MIN_BPM..MAX_BPM) return null
        return ParsedWebBpm(
            bpm = bpm,
            confidence = 0.78,
            label = "${formatBpm(bpm)} BPM"
        )
    }

    private fun parseNumberBeforeBpm(text: String): ParsedWebBpm? {
        val match = NUMBER_BPM_REGEX.find(text) ?: return null
        val bpm = match.groupValues[1].toBpmOrNull() ?: return null
        if (bpm !in MIN_BPM..MAX_BPM) return null
        return ParsedWebBpm(
            bpm = bpm,
            confidence = 0.82,
            label = "${formatBpm(bpm)} BPM"
        )
    }

    private fun String.toBpmOrNull(): Double? {
        return replace(',', '.').toDoubleOrNull()
    }

    private fun formatBpm(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
    }

    private val RANGE_REGEX = Regex(
        pattern = """(?i)\b(\d{2,3}(?:[.,]\d+)?)\s*(?:-|~|to|에서)\s*(\d{2,3}(?:[.,]\d+)?)\s*(?:bpm|beats\s+per\s+minute|템포|박자)?"""
    )
    private val KEYWORD_NUMBER_REGEX = Regex(
        pattern = """(?i)(?:bpm|beats\s+per\s+minute|tempo|템포|박자|비피엠)[^\d]{0,24}(\d{2,3}(?:[.,]\d+)?)"""
    )
    private val NUMBER_BPM_REGEX = Regex(
        pattern = """(?i)\b(\d{2,3}(?:[.,]\d+)?)\s*(?:bpm|beats\s+per\s+minute|비피엠)\b"""
    )
    private const val MIN_BPM = 40.0
    private const val MAX_BPM = 260.0
}

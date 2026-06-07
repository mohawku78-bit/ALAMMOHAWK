package com.example.integratedbpmmeter.data

import android.content.Context

data class AppSettings(
    val experimentalCaptureEnabled: Boolean = false,
    val tapSoundEnabled: Boolean = true,
    val minBpm: Int = 60,
    val maxBpm: Int = 200,
    val analysisSeconds: Int = 90
)

enum class AnalysisRangeRisk {
    FAST_ONLY,
    TOO_NARROW
}

fun AppSettings.analysisRangeRisk(): AnalysisRangeRisk? {
    val width = maxBpm - minBpm
    return when {
        minBpm >= 140 -> AnalysisRangeRisk.FAST_ONLY
        width < 60 -> AnalysisRangeRisk.TOO_NARROW
        else -> null
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val minBpm = prefs.getInt(KEY_MIN_BPM, 60)
        val maxBpm = prefs.getInt(KEY_MAX_BPM, 200)
        return AppSettings(
            experimentalCaptureEnabled = prefs.getBoolean(KEY_EXPERIMENTAL_CAPTURE, false),
            tapSoundEnabled = prefs.getBoolean(KEY_TAP_SOUND, true),
            minBpm = minBpm.coerceIn(40, 240),
            maxBpm = maxBpm.coerceIn(minBpm.coerceIn(40, 240), 240),
            analysisSeconds = prefs.getInt(KEY_ANALYSIS_SECONDS, 90).coerceIn(30, 120)
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_EXPERIMENTAL_CAPTURE, settings.experimentalCaptureEnabled)
            .putBoolean(KEY_TAP_SOUND, settings.tapSoundEnabled)
            .putInt(KEY_MIN_BPM, settings.minBpm.coerceIn(40, 240))
            .putInt(KEY_MAX_BPM, settings.maxBpm.coerceIn(settings.minBpm, 240))
            .putInt(KEY_ANALYSIS_SECONDS, settings.analysisSeconds.coerceIn(30, 120))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "integrated_bpm_settings"
        private const val KEY_EXPERIMENTAL_CAPTURE = "experimental_capture"
        private const val KEY_TAP_SOUND = "tap_sound"
        private const val KEY_MIN_BPM = "min_bpm"
        private const val KEY_MAX_BPM = "max_bpm"
        private const val KEY_ANALYSIS_SECONDS = "analysis_seconds"
    }
}

package com.example.integratedbpmmeter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.integratedbpmmeter.data.AppSettings
import com.example.integratedbpmmeter.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)

    private val _uiState = MutableStateFlow(store.load())
    val uiState: StateFlow<AppSettings> = _uiState.asStateFlow()

    fun setExperimentalCaptureEnabled(enabled: Boolean) {
        update { it.copy(experimentalCaptureEnabled = enabled) }
    }

    fun setTapSoundEnabled(enabled: Boolean) {
        update { it.copy(tapSoundEnabled = enabled) }
    }

    fun setBpmRange(minBpm: Int, maxBpm: Int) {
        val normalizedMin = minBpm.coerceIn(40, 240)
        val normalizedMax = maxBpm.coerceIn(normalizedMin, 240)
        update { it.copy(minBpm = normalizedMin, maxBpm = normalizedMax) }
    }

    fun setAnalysisSeconds(seconds: Int) {
        update { it.copy(analysisSeconds = seconds.coerceIn(30, 120)) }
    }

    fun resetAnalysisDefaults() {
        update {
            it.copy(
                minBpm = 60,
                maxBpm = 200,
                analysisSeconds = 90
            )
        }
    }

    private fun update(block: (AppSettings) -> AppSettings) {
        _uiState.update { current ->
            block(current).also { store.save(it) }
        }
    }
}

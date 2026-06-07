package com.example.integratedbpmmeter.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.integratedbpmmeter.audio.TapBpmCalculator
import com.example.integratedbpmmeter.audio.TapBpmSnapshot
import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmSmartCategory
import com.example.integratedbpmmeter.data.BpmRepository
import com.example.integratedbpmmeter.data.BpmSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TapBpmUiState(
    val snapshot: TapBpmSnapshot = TapBpmSnapshot(),
    val titleInput: String = "",
    val isSaving: Boolean = false,
    val statusMessage: String? = null
)

class TapBpmViewModel(application: Application) : AndroidViewModel(application) {
    private val calculator = TapBpmCalculator()
    private val repository = BpmRepository.from(application)

    private val _uiState = MutableStateFlow(TapBpmUiState())
    val uiState: StateFlow<TapBpmUiState> = _uiState.asStateFlow()

    fun onTap(timestampNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        val snapshot = calculator.addTap(timestampNanos)
        _uiState.update { it.copy(snapshot = snapshot, statusMessage = null) }
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(titleInput = value, statusMessage = null) }
    }

    fun reset() {
        _uiState.update {
            it.copy(snapshot = calculator.reset(), statusMessage = null)
        }
    }

    fun half() {
        _uiState.update {
            it.copy(snapshot = calculator.half(), statusMessage = null)
        }
    }

    fun double() {
        _uiState.update {
            it.copy(snapshot = calculator.double(), statusMessage = null)
        }
    }

    fun saveCurrent() {
        val current = _uiState.value
        val bpm = current.snapshot.bpm ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            val result = runCatching {
                repository.insert(
                    BpmRecord(
                        title = current.titleInput.trim().ifBlank { "Tap BPM" },
                        bpm = bpm,
                        sourceType = BpmSourceType.TAP,
                        confidence = current.snapshot.confidence,
                        manuallyVerified = true,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) {
                        "Saved / ${BpmSmartCategory.fromBpm(bpm).label}"
                    } else {
                        "Save failed"
                    }
                )
            }
        }
    }
}

package com.example.integratedbpmmeter.viewmodel

import android.app.Application
import android.media.AudioRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.integratedbpmmeter.audio.BpmCandidate
import com.example.integratedbpmmeter.audio.MicCaptureManager
import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmRepository
import com.example.integratedbpmmeter.data.BpmSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MicCaptureUiState(
    val isListening: Boolean = false,
    val candidates: List<BpmCandidate> = emptyList(),
    val selectedCandidateIndex: Int = 0,
    val capturedSeconds: Double = 0.0,
    val isSaving: Boolean = false,
    val statusMessage: String = "Idle",
    val linkedTitle: String? = null,
    val linkedArtist: String? = null,
    val linkedAlbum: String? = null,
    val linkedPackageName: String? = null
) {
    val selectedCandidate: BpmCandidate?
        get() = candidates.getOrNull(selectedCandidateIndex)
}

class MicCaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val captureManager = MicCaptureManager()
    private val repository = BpmRepository.from(application)

    private val _uiState = MutableStateFlow(MicCaptureUiState())
    val uiState: StateFlow<MicCaptureUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null

    fun startListening(
        linkedTitle: String? = null,
        linkedArtist: String? = null,
        linkedAlbum: String? = null,
        linkedPackageName: String? = null
    ) {
        if (_uiState.value.isListening) return

        listenJob = viewModelScope.launch(Dispatchers.Default) {
            var record: AudioRecord? = null
            val rolling = RollingSampleBuffer(maxSamples = MicCaptureManager.SAMPLE_RATE * 24)
            var totalSamplesRead = 0L
            var lastEstimateAt = 0L

            runCatching {
                record = captureManager.createAudioRecord()
                require(record?.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not start" }
                record?.startRecording()
                require(record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Microphone did not enter recording state"
                }

                _uiState.update {
                    it.copy(
                        isListening = true,
                        candidates = emptyList(),
                        selectedCandidateIndex = 0,
                        capturedSeconds = 0.0,
                        statusMessage = "Listening through microphone",
                        linkedTitle = linkedTitle,
                        linkedArtist = linkedArtist,
                        linkedAlbum = linkedAlbum,
                        linkedPackageName = linkedPackageName
                    )
                }

                val readBuffer = ShortArray(4_096)
                while (isActive) {
                    val read = record?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (read < 0) {
                        error("Microphone read error $read")
                    } else if (read > 0) {
                        for (index in 0 until read) {
                            rolling.add(readBuffer[index] / Short.MAX_VALUE.toFloat())
                        }
                        totalSamplesRead += read

                        val capturedSeconds = rolling.size.toDouble() / MicCaptureManager.SAMPLE_RATE
                        if (
                            rolling.size >= MicCaptureManager.SAMPLE_RATE * 8 &&
                            totalSamplesRead - lastEstimateAt >= MicCaptureManager.SAMPLE_RATE
                        ) {
                            lastEstimateAt = totalSamplesRead
                            val candidates = captureManager.estimate(rolling.toFloatArray())
                            _uiState.update {
                                it.copy(
                                    candidates = candidates,
                                    selectedCandidateIndex = 0,
                                    capturedSeconds = capturedSeconds,
                                    statusMessage = if (candidates.isEmpty()) {
                                        "Keep the speaker volume clear and steady"
                                    } else {
                                        "Mic BPM candidates updated"
                                    }
                                )
                            }
                        } else {
                            _uiState.update { it.copy(capturedSeconds = capturedSeconds) }
                        }
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isListening = false,
                        statusMessage = friendlyMicError(error)
                    )
                }
            }

            runCatching { record?.stop() }
            runCatching { record?.release() }
            _uiState.update { it.copy(isListening = false) }
        }
    }

    fun stopListening() {
        viewModelScope.launch {
            listenJob?.cancelAndJoin()
            listenJob = null
            _uiState.update { it.copy(isListening = false, statusMessage = "Stopped") }
        }
    }

    fun selectCandidate(index: Int) {
        _uiState.update {
            it.copy(selectedCandidateIndex = index.coerceIn(0, (it.candidates.size - 1).coerceAtLeast(0)))
        }
    }

    fun saveSelectedCandidate() {
        val current = _uiState.value
        val candidate = current.selectedCandidate ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                repository.insert(
                    BpmRecord(
                        title = current.linkedTitle ?: "Mic listen BPM",
                        artist = current.linkedArtist,
                        album = current.linkedAlbum,
                        bpm = candidate.bpm,
                        sourceType = BpmSourceType.MIC_CAPTURE,
                        sourceAppPackage = current.linkedPackageName,
                        confidence = candidate.confidence,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) "Saved mic BPM" else "Save failed"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
    }

    private fun friendlyMicError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.isNotBlank() ->
                "Mic listen failed: $message. Check microphone permission and play through the speaker."
            else ->
                "Mic listen failed. Check microphone permission and play through the speaker."
        }
    }

    private class RollingSampleBuffer(private val maxSamples: Int) {
        private var values = FloatArray(maxSamples)
        private var nextIndex = 0
        var size: Int = 0
            private set

        fun add(value: Float) {
            if (size < maxSamples) {
                values[size] = value
                size++
            } else {
                values[nextIndex] = value
                nextIndex = (nextIndex + 1) % maxSamples
            }
        }

        fun toFloatArray(): FloatArray {
            if (size < maxSamples) return values.copyOf(size)
            val ordered = FloatArray(size)
            values.copyInto(
                destination = ordered,
                destinationOffset = 0,
                startIndex = nextIndex,
                endIndex = maxSamples
            )
            values.copyInto(
                destination = ordered,
                destinationOffset = maxSamples - nextIndex,
                startIndex = 0,
                endIndex = nextIndex
            )
            return ordered
        }
    }
}

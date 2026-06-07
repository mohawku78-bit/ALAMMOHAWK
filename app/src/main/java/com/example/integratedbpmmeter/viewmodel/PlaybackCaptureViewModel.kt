package com.example.integratedbpmmeter.viewmodel

import android.app.Application
import android.content.Intent
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.integratedbpmmeter.audio.BpmCandidate
import com.example.integratedbpmmeter.audio.PlaybackCaptureManager
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

data class PlaybackCaptureUiState(
    val isCapturing: Boolean = false,
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

class PlaybackCaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val projectionManager = application.getSystemService(MediaProjectionManager::class.java)
    private val captureManager = PlaybackCaptureManager()
    private val repository = BpmRepository.from(application)

    private val _uiState = MutableStateFlow(PlaybackCaptureUiState())
    val uiState: StateFlow<PlaybackCaptureUiState> = _uiState.asStateFlow()

    private var captureJob: Job? = null
    private var activeProjection: MediaProjection? = null

    fun createCaptureIntent(): Intent = projectionManager.createScreenCaptureIntent()

    fun startCapture(
        resultCode: Int,
        data: Intent,
        linkedTitle: String? = null,
        linkedArtist: String? = null,
        linkedAlbum: String? = null,
        linkedPackageName: String? = null
    ) {
        if (_uiState.value.isCapturing) return

        val projection = runCatching {
            projectionManager.getMediaProjection(resultCode, data)
        }.getOrNull()

        if (projection == null) {
            _uiState.update { it.copy(statusMessage = "MediaProjection permission failed") }
            return
        }

        activeProjection = projection
        captureJob = viewModelScope.launch(Dispatchers.Default) {
            var record: AudioRecord? = null
            val rolling = RollingSampleBuffer(maxSamples = PlaybackCaptureManager.SAMPLE_RATE * 24)
            var totalSamplesRead = 0L
            var lastEstimateAt = 0L

            runCatching {
                record = captureManager.createAudioRecord(projection)
                require(record?.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord did not initialize" }
                record?.startRecording()
                require(record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "AudioRecord could not start recording"
                }

                _uiState.update {
                    it.copy(
                        isCapturing = true,
                        candidates = emptyList(),
                        selectedCandidateIndex = 0,
                        capturedSeconds = 0.0,
                        statusMessage = "Capturing media playback",
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
                        error("AudioRecord read error $read")
                    } else if (read > 0) {
                        for (index in 0 until read) {
                            rolling.add(readBuffer[index] / Short.MAX_VALUE.toFloat())
                        }
                        totalSamplesRead += read

                        val capturedSeconds = rolling.size.toDouble() / PlaybackCaptureManager.SAMPLE_RATE
                        if (
                            rolling.size >= PlaybackCaptureManager.SAMPLE_RATE * 8 &&
                            totalSamplesRead - lastEstimateAt >= PlaybackCaptureManager.SAMPLE_RATE
                        ) {
                            lastEstimateAt = totalSamplesRead
                            val candidates = captureManager.estimate(rolling.toFloatArray())
                            _uiState.update {
                                it.copy(
                                    candidates = candidates,
                                    selectedCandidateIndex = 0,
                                    capturedSeconds = capturedSeconds,
                                    statusMessage = if (candidates.isEmpty()) {
                                        "Listening for a stronger beat"
                                    } else {
                                        "Live candidates updated"
                                    }
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(capturedSeconds = capturedSeconds)
                            }
                        }
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        statusMessage = friendlyPlaybackCaptureError(error)
                    )
                }
            }

            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { projection.stop() }
            activeProjection = null
            _uiState.update { it.copy(isCapturing = false) }
        }
    }

    private fun friendlyPlaybackCaptureError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("AudioRecord", ignoreCase = true) ->
                "Internal audio capture failed. This music app or device may block playback capture. Try Mic Listen instead."
            message.isNotBlank() ->
                "Internal audio capture failed: $message. Try Mic Listen or analyze the audio file instead."
            else ->
                "Internal audio capture failed. Try Mic Listen or analyze the audio file instead."
        }
    }

    fun stopCapture() {
        viewModelScope.launch {
            captureJob?.cancelAndJoin()
            captureJob = null
            runCatching { activeProjection?.stop() }
            activeProjection = null
            _uiState.update { it.copy(isCapturing = false, statusMessage = "Stopped") }
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
                        title = current.linkedTitle ?: "Playback capture",
                        artist = current.linkedArtist,
                        album = current.linkedAlbum,
                        bpm = candidate.bpm,
                        sourceType = BpmSourceType.PLAYBACK_CAPTURE,
                        sourceAppPackage = current.linkedPackageName,
                        confidence = candidate.confidence,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) "Saved capture BPM" else "Save failed"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { activeProjection?.stop() }
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

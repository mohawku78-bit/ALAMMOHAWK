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
import com.example.integratedbpmmeter.lookup.PublicBpmCandidate
import com.example.integratedbpmmeter.lookup.PublicBpmLookup
import com.example.integratedbpmmeter.lookup.WebBpmTextParser
import com.example.integratedbpmmeter.media.LocalAudioResolver
import com.example.integratedbpmmeter.media.MediaSessionReader
import com.example.integratedbpmmeter.media.MediaTransportCommand
import com.example.integratedbpmmeter.media.NowPlayingTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NowPlayingUiState(
    val track: NowPlayingTrack? = null,
    val hasNotificationAccess: Boolean = false,
    val tapSnapshot: TapBpmSnapshot = TapBpmSnapshot(),
    val fallbackTitle: String = "",
    val isLookingUpPublicBpm: Boolean = false,
    val publicBpmCandidates: List<PublicBpmCandidate> = emptyList(),
    val publicBpmStatusMessage: String? = null,
    val localBpmMatches: List<BpmRecord> = emptyList(),
    val localBpmStatusMessage: String? = null,
    val webReferenceText: String = "",
    val parsedWebBpm: Double? = null,
    val webReferenceConfidence: Double = 0.0,
    val webReferenceStatusMessage: String? = null,
    val isSaving: Boolean = false,
    val statusMessage: String? = null
)

class NowPlayingViewModel(application: Application) : AndroidViewModel(application) {
    private val reader = MediaSessionReader(application)
    private val calculator = TapBpmCalculator()
    private val repository = BpmRepository.from(application)
    private val publicBpmLookup = PublicBpmLookup()
    private val localAudioResolver = LocalAudioResolver(application)
    private var lastAutoLookupKey: String? = null
    private var lastLocalLookupKey: String? = null

    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(2_000)
            }
        }
    }

    fun refresh() {
        val hasAccess = reader.hasNotificationListenerAccess()
        val track = reader.readCurrentTrack()
        val previousTrackKey = publicLookupKey(_uiState.value.track?.title, _uiState.value.track?.artist)
        val lookupKey = publicLookupKey(track?.title, track?.artist)
        val shouldClearPublicLookup = previousTrackKey != null && previousTrackKey != lookupKey
        _uiState.update {
            it.copy(
                hasNotificationAccess = hasAccess,
                track = track,
                publicBpmCandidates = if (shouldClearPublicLookup) emptyList() else it.publicBpmCandidates,
                publicBpmStatusMessage = if (shouldClearPublicLookup) null else it.publicBpmStatusMessage,
                localBpmMatches = if (shouldClearPublicLookup) emptyList() else it.localBpmMatches,
                localBpmStatusMessage = if (shouldClearPublicLookup) null else it.localBpmStatusMessage
            )
        }

        if (lookupKey != null && lookupKey != lastLocalLookupKey) {
            lastLocalLookupKey = lookupKey
            startLocalBpmLookup(track?.title.orEmpty(), track?.artist, lookupKey)
        }

        if (lookupKey != null && lookupKey != lastAutoLookupKey && !_uiState.value.isLookingUpPublicBpm) {
            lastAutoLookupKey = lookupKey
            startPublicBpmLookup(autoTriggered = true)
        }
    }

    fun onTap(timestampNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        val snapshot = calculator.addTap(timestampNanos)
        _uiState.update { it.copy(tapSnapshot = snapshot, statusMessage = null) }
    }

    fun resetTap() {
        _uiState.update { it.copy(tapSnapshot = calculator.reset(), statusMessage = null) }
    }

    fun previousTrack() {
        sendMediaCommand(MediaTransportCommand.Previous)
    }

    fun playPauseTrack() {
        sendMediaCommand(MediaTransportCommand.PlayPause)
    }

    fun nextTrack() {
        sendMediaCommand(MediaTransportCommand.Next)
    }

    private fun sendMediaCommand(command: MediaTransportCommand) {
        val success = reader.sendTransportCommand(command)
        _uiState.update {
            it.copy(statusMessage = if (success) null else "Media control unavailable")
        }
        if (success) {
            viewModelScope.launch {
                delay(250)
                refresh()
            }
        }
    }

    fun half() {
        _uiState.update { it.copy(tapSnapshot = calculator.half(), statusMessage = null) }
    }

    fun double() {
        _uiState.update { it.copy(tapSnapshot = calculator.double(), statusMessage = null) }
    }

    fun onFallbackTitleChange(value: String) {
        _uiState.update {
            it.copy(
                fallbackTitle = value,
                publicBpmCandidates = emptyList(),
                publicBpmStatusMessage = null,
                localBpmMatches = emptyList(),
                localBpmStatusMessage = null,
                statusMessage = null
            )
        }
        val title = value.trim()
        val lookupKey = publicLookupKey(title, _uiState.value.track?.artist)
        if (title.isNotBlank() && lookupKey != null && lookupKey != lastLocalLookupKey) {
            lastLocalLookupKey = lookupKey
            startLocalBpmLookup(title, _uiState.value.track?.artist, lookupKey)
        }
    }

    fun lookupPublicBpm() {
        startPublicBpmLookup(autoTriggered = false)
    }

    private fun startPublicBpmLookup(autoTriggered: Boolean) {
        val current = _uiState.value
        if (current.isLookingUpPublicBpm) return
        val title = current.track?.title ?: current.fallbackTitle.trim()
        val artist = current.track?.artist
        if (title.isBlank()) {
            setPublicBpmStatus("Enter or play a track first")
            return
        }
        val lookupKey = publicLookupKey(title, artist) ?: return
        if (!autoTriggered) {
            lastAutoLookupKey = lookupKey
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLookingUpPublicBpm = true,
                    publicBpmCandidates = emptyList(),
                    publicBpmStatusMessage = if (autoTriggered) "Checking public BPM" else "Searching public BPM"
                )
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    publicBpmLookup.lookup(title.trim(), artist?.trim()?.takeIf { it.isNotBlank() })
                }
            }

            val latest = _uiState.value
            if (publicLookupKey(latest.track?.title ?: latest.fallbackTitle, latest.track?.artist) != lookupKey) {
                _uiState.update {
                    it.copy(
                        isLookingUpPublicBpm = false,
                        publicBpmCandidates = emptyList(),
                        publicBpmStatusMessage = null
                    )
                }
                return@launch
            }

            _uiState.update {
                val candidates = result.getOrNull().orEmpty()
                it.copy(
                    isLookingUpPublicBpm = false,
                    publicBpmCandidates = candidates,
                    publicBpmStatusMessage = when {
                        result.isFailure -> "Public BPM lookup failed: ${friendlyLookupError(result.exceptionOrNull())}"
                        candidates.isEmpty() && it.localBpmMatches.isNotEmpty() -> "No public BPM. Showing saved BPM"
                        candidates.isEmpty() -> "No BPM in the free public database"
                        else -> "Public BPM found"
                    }
                )
            }
        }
    }

    private fun startLocalBpmLookup(title: String, artist: String?, lookupKey: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val matches = runCatching {
                withContext(Dispatchers.IO) {
                    repository.findLocalReferences(title.trim(), artist?.trim())
                }
            }.getOrDefault(emptyList())

            val latest = _uiState.value
            if (publicLookupKey(latest.track?.title ?: latest.fallbackTitle, latest.track?.artist) != lookupKey) {
                return@launch
            }

            _uiState.update {
                it.copy(
                    localBpmMatches = matches,
                    localBpmStatusMessage = when {
                        matches.isNotEmpty() -> "Saved BPM found"
                        else -> "No saved BPM for this track"
                    }
                )
            }
        }
    }

    fun onWebReferenceTextChange(value: String) {
        val parsed = WebBpmTextParser.parse(value)
        _uiState.update {
            it.copy(
                webReferenceText = value,
                parsedWebBpm = parsed?.bpm,
                webReferenceConfidence = parsed?.confidence ?: 0.0,
                webReferenceStatusMessage = when {
                    value.isBlank() -> null
                    parsed != null -> "Parsed ${parsed.label}"
                    else -> "No BPM found in pasted text"
                }
            )
        }
    }

    fun saveCurrent() {
        val current = _uiState.value
        val bpm = current.tapSnapshot.bpm ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            val track = current.track
            val result = runCatching {
                val linkedFileUri = resolveLocalMediaUri(track)
                repository.insert(
                    BpmRecord(
                        title = track?.title
                            ?: current.fallbackTitle.trim().ifBlank { "Now Playing BPM" },
                        artist = track?.artist,
                        album = track?.album,
                        bpm = bpm,
                        sourceType = BpmSourceType.NOW_PLAYING,
                        sourceAppPackage = track?.packageName,
                        fileUri = linkedFileUri,
                        confidence = current.tapSnapshot.confidence,
                        manuallyVerified = true,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) {
                        if (track?.packageName.isSamsungMusicPackage() && track?.mediaUri.isNullOrBlank() && !localAudioResolver.hasAudioReadPermission()) {
                            "Saved / Grant music permission in Library for M3U8"
                        } else {
                            "Saved / ${BpmSmartCategory.fromBpm(bpm).label}"
                        }
                    } else {
                        "Save failed"
                    }
                )
            }
            if (result.isSuccess) {
                val title = track?.title ?: current.fallbackTitle.trim()
                val lookupKey = publicLookupKey(title, track?.artist)
                if (!title.isNullOrBlank() && lookupKey != null) {
                    lastLocalLookupKey = lookupKey
                    startLocalBpmLookup(title, track?.artist, lookupKey)
                }
            }
        }
    }

    fun savePublicBpmCandidate(index: Int) {
        val current = _uiState.value
        val candidate = current.publicBpmCandidates.getOrNull(index) ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            val track = current.track
            val result = runCatching {
                val linkedFileUri = resolveLocalMediaUri(track)
                repository.insert(
                    BpmRecord(
                        title = track?.title
                            ?: current.fallbackTitle.trim().ifBlank { candidate.title },
                        artist = track?.artist ?: candidate.artist,
                        album = track?.album,
                        bpm = candidate.bpm,
                        sourceType = BpmSourceType.PUBLIC_REFERENCE,
                        sourceAppPackage = track?.packageName,
                        fileUri = linkedFileUri,
                        confidence = candidate.matchScore.coerceIn(0.1, 1.0),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) "Saved public BPM with media metadata" else "Save failed"
                )
            }
        }
    }

    fun saveWebReference() {
        val current = _uiState.value
        val bpm = current.parsedWebBpm ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            val track = current.track
            val result = runCatching {
                val linkedFileUri = resolveLocalMediaUri(track)
                repository.insert(
                    BpmRecord(
                        title = track?.title
                            ?: current.fallbackTitle.trim().ifBlank { "Now Playing web BPM" },
                        artist = track?.artist,
                        album = track?.album,
                        bpm = bpm,
                        sourceType = BpmSourceType.PUBLIC_REFERENCE,
                        sourceAppPackage = track?.packageName,
                        fileUri = linkedFileUri,
                        confidence = current.webReferenceConfidence.coerceIn(0.1, 1.0),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) "Saved web BPM with media metadata" else "Save failed"
                )
            }
        }
    }

    private fun setPublicBpmStatus(message: String) {
        _uiState.update { it.copy(publicBpmStatusMessage = message) }
    }

    private fun publicLookupKey(title: String?, artist: String?): String? {
        val cleanTitle = title?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val cleanArtist = artist?.trim()?.lowercase().orEmpty()
        return "$cleanTitle|$cleanArtist"
    }

    private fun friendlyLookupError(error: Throwable?): String {
        val message = error?.message.orEmpty()
        return when {
            message.contains("timeout", ignoreCase = true) -> "network timeout"
            message.contains("HTTP 503", ignoreCase = true) -> "public database is busy"
            message.isNotBlank() -> message
            else -> "network unavailable"
        }
    }

    private suspend fun resolveLocalMediaUri(track: NowPlayingTrack?): String? {
        val mediaUri = track?.mediaUri?.takeIf { it.isNotBlank() }
        if (mediaUri != null) return mediaUri
        if (track == null || !track.packageName.isSamsungMusicPackage() || !localAudioResolver.hasAudioReadPermission()) return null
        return withContext(Dispatchers.IO) {
            localAudioResolver.resolveTrackUri(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs
            )
        }
    }
}

private fun String?.isSamsungMusicPackage(): Boolean {
    return this == "com.sec.android.app.music" || this == "com.samsung.android.app.music"
}

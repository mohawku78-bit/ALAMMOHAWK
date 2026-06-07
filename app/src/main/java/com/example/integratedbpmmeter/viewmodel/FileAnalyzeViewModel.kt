package com.example.integratedbpmmeter.viewmodel

import android.app.Application
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.integratedbpmmeter.audio.AudioFileAnalyzer
import com.example.integratedbpmmeter.audio.AudioFileMetadata
import com.example.integratedbpmmeter.audio.BpmCandidate
import com.example.integratedbpmmeter.audio.FileBpmCandidatePrioritizer
import com.example.integratedbpmmeter.audio.TapBpmCalculator
import com.example.integratedbpmmeter.audio.TapBpmSnapshot
import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmRepository
import com.example.integratedbpmmeter.data.BpmSourceType
import com.example.integratedbpmmeter.data.SettingsStore
import com.example.integratedbpmmeter.lookup.PublicBpmCandidate
import com.example.integratedbpmmeter.lookup.PublicBpmLookup
import com.example.integratedbpmmeter.lookup.WebBpmTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileAnalyzeUiState(
    val selectedUri: Uri? = null,
    val metadata: AudioFileMetadata? = null,
    val candidates: List<BpmCandidate> = emptyList(),
    val candidateSources: List<BpmSourceType> = emptyList(),
    val candidateReasonLabels: List<String> = emptyList(),
    val selectedCandidateIndex: Int = 0,
    val analyzedSeconds: Double = 0.0,
    val engineName: String? = null,
    val diagnostics: String? = null,
    val segmentsAnalyzed: Int = 0,
    val agreementScore: Double = 0.0,
    val tempoFamily: String? = null,
    val engineWarnings: List<String> = emptyList(),
    val isReadingMetadata: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isLookingUpPublicBpm: Boolean = false,
    val isPreviewPreparing: Boolean = false,
    val isPreviewPlaying: Boolean = false,
    val previewPositionMs: Int = 0,
    val previewDurationMs: Int = 0,
    val previewStatusMessage: String? = null,
    val tapSnapshot: TapBpmSnapshot = TapBpmSnapshot(),
    val isSaving: Boolean = false,
    val publicBpmCandidates: List<PublicBpmCandidate> = emptyList(),
    val publicBpmStatusMessage: String? = null,
    val webReferenceText: String = "",
    val parsedWebBpm: BpmCandidate? = null,
    val webReferenceStatusMessage: String? = null,
    val statusMessage: String? = null
) {
    val selectedCandidate: BpmCandidate?
        get() = candidates.getOrNull(selectedCandidateIndex)
}

class FileAnalyzeViewModel(application: Application) : AndroidViewModel(application) {
    private val metadataReader = AudioFileAnalyzer(application)
    private val publicBpmLookup = PublicBpmLookup()
    private val repository = BpmRepository.from(application)
    private val settingsStore = SettingsStore(application)
    private val tapCalculator = TapBpmCalculator()
    private var previewPlayer: MediaPlayer? = null
    private var previewUri: Uri? = null
    private var previewProgressJob: Job? = null
    private var lastAutoPublicLookupKey: String? = null

    private val _uiState = MutableStateFlow(FileAnalyzeUiState())
    val uiState: StateFlow<FileAnalyzeUiState> = _uiState.asStateFlow()

    fun selectFile(uri: Uri, autoAnalyze: Boolean = false) {
        viewModelScope.launch {
            releasePreviewPlayer(resetState = false)
            _uiState.update {
                FileAnalyzeUiState(
                    selectedUri = uri,
                    isReadingMetadata = true,
                    tapSnapshot = tapCalculator.reset(),
                    statusMessage = "Reading metadata"
                )
            }

            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val result = runCatching {
                withContext(Dispatchers.IO) { metadataReader.readMetadata(uri) }
            }

            val metadata = result.getOrNull()
            val savedReferences = metadata?.let { findSavedReferences(it) }.orEmpty()
            val savedReferenceCandidates = FileBpmCandidatePrioritizer.prioritize(
                savedReferences = savedReferences,
                analysisCandidates = emptyList()
            )

            _uiState.update {
                it.copy(
                    metadata = metadata,
                    candidates = savedReferenceCandidates.candidates,
                    candidateSources = savedReferenceCandidates.sources,
                    candidateReasonLabels = savedReferenceCandidates.reasonLabels,
                    selectedCandidateIndex = 0,
                    isReadingMetadata = false,
                    statusMessage = when {
                        result.isFailure -> "Metadata read failed"
                        savedReferenceCandidates.usedSavedReference -> "Saved BPM found / tap to confirm"
                        else -> "Ready: play and tap BPM"
                    }
                )
            }

            if (result.isSuccess) {
                preparePreviewPlayer(uri)
            }

            if (metadata != null) {
                startPublicBpmLookup(metadata, autoTriggered = true)
            }

            if (result.isSuccess && autoAnalyze) {
                analyzeSelectedFile()
            }
        }
    }

    fun analyzeSelectedFile() {
        val uri = _uiState.value.selectedUri ?: return
        if (_uiState.value.isAnalyzing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    statusMessage = "Creating auto estimate"
                )
            }

            val result = runCatching {
                val settings = settingsStore.load()
                val analyzer = AudioFileAnalyzer(context = getApplication())
                withContext(Dispatchers.IO) {
                    analyzer.analyze(
                        uri = uri,
                        maxAnalysisSeconds = settings.analysisSeconds,
                        minBpm = settings.minBpm,
                        maxBpm = settings.maxBpm
                    )
                }
            }

            val analysis = result.getOrNull()
            val metadataForReferences = analysis?.metadata ?: _uiState.value.metadata
            val savedReferences = metadataForReferences?.let { findSavedReferences(it) }.orEmpty()
            val analysisCandidates = analysis?.candidates.orEmpty()
            val prioritizedCandidates = FileBpmCandidatePrioritizer.prioritize(
                savedReferences = savedReferences,
                publicReferences = _uiState.value.publicBpmCandidates.toBpmCandidates(),
                analysisCandidates = analysisCandidates,
                agreementScore = analysis?.agreementScore ?: 0.0,
                segmentsAnalyzed = analysis?.segmentsAnalyzed ?: 0,
                engineWarnings = analysis?.engineWarnings.orEmpty()
            )

            _uiState.update { previous ->
                previous.copy(
                    metadata = analysis?.metadata ?: previous.metadata,
                    candidates = prioritizedCandidates.candidates,
                    candidateSources = prioritizedCandidates.sources,
                    candidateReasonLabels = prioritizedCandidates.reasonLabels,
                    selectedCandidateIndex = 0,
                    analyzedSeconds = analysis?.analyzedSeconds ?: 0.0,
                    engineName = analysis?.engineName,
                    diagnostics = analysis?.diagnostics,
                    segmentsAnalyzed = analysis?.segmentsAnalyzed ?: 0,
                    agreementScore = analysis?.agreementScore ?: 0.0,
                    tempoFamily = analysis?.tempoFamily,
                    engineWarnings = analysis?.engineWarnings.orEmpty(),
                    isAnalyzing = false,
                    statusMessage = when {
                        prioritizedCandidates.usedSavedReference && result.isFailure ->
                            "Analysis failed / using saved BPM"
                        prioritizedCandidates.usedPublicReference && result.isFailure ->
                            "Analysis failed / using public BPM"
                        prioritizedCandidates.usedSavedReference ->
                            "Auto estimate ready / saved BPM preferred"
                        prioritizedCandidates.usedPublicReference ->
                            "Auto estimate ready / reference BPM preferred"
                        result.isFailure -> "Analysis failed: ${friendlyAnalysisError(result.exceptionOrNull())}"
                        analysisCandidates.isEmpty() -> "No usable auto estimate"
                        else -> "Auto estimate ready"
                    }
                )
            }
        }
    }

    fun selectCandidate(index: Int) {
        _uiState.update {
            it.copy(selectedCandidateIndex = index.coerceIn(0, (it.candidates.size - 1).coerceAtLeast(0)))
        }
    }

    fun halfSelectedCandidate() {
        adjustSelectedCandidate(0.5)
    }

    fun doubleSelectedCandidate() {
        adjustSelectedCandidate(2.0)
    }

    fun saveSelectedCandidate() {
        val current = _uiState.value
        val uri = current.selectedUri ?: return
        val candidate = current.selectedCandidate ?: return
        val sourceType = current.candidateSources.getOrNull(current.selectedCandidateIndex)
            ?: BpmSourceType.FILE_ANALYSIS
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            val metadata = current.metadata
            val result = runCatching {
                repository.insert(
                    BpmRecord(
                        title = metadata?.title
                            ?: metadata?.displayName
                            ?: "Analyzed audio",
                        artist = metadata?.artist,
                        album = metadata?.album,
                        bpm = candidate.bpm,
                        sourceType = sourceType,
                        fileUri = uri.toString(),
                        confidence = candidate.confidence,
                        manuallyVerified = sourceType == BpmSourceType.TAP,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) sourceType.savedStatusLabel() else "Save failed"
                )
            }
        }
    }

    fun lookupPublicBpm() {
        val metadata = _uiState.value.metadata ?: return setPublicBpmStatus("Select a file with a title first")
        startPublicBpmLookup(metadata, autoTriggered = false)
    }

    private fun startPublicBpmLookup(metadata: AudioFileMetadata, autoTriggered: Boolean) {
        val current = _uiState.value
        val title = metadata.title
            ?: metadata.displayName?.substringBeforeLast('.')
            ?: return setPublicBpmStatus("Select a file with a title first")
        val artist = metadata.artist
        if (title.isBlank()) {
            setPublicBpmStatus("Select a file with a title first")
            return
        }
        if (current.isLookingUpPublicBpm) return
        val lookupKey = publicLookupKey(title, artist) ?: return
        if (autoTriggered && lookupKey == lastAutoPublicLookupKey) return
        lastAutoPublicLookupKey = lookupKey

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
            if (publicLookupKey(latest.metadata?.title ?: latest.metadata?.displayName?.substringBeforeLast('.'), latest.metadata?.artist) != lookupKey) {
                _uiState.update {
                    it.copy(
                        isLookingUpPublicBpm = false,
                        publicBpmCandidates = emptyList(),
                        publicBpmStatusMessage = null
                    )
                }
                return@launch
            }

            val candidates = result.getOrNull().orEmpty()
            val savedReferences = latest.metadata?.let { findSavedReferences(it) }.orEmpty()
            val analysisOnlyCandidates = latest.candidates.zip(
                latest.candidateSources.ifEmpty { List(latest.candidates.size) { BpmSourceType.FILE_ANALYSIS } }
            ).filter { (_, source) -> source == BpmSourceType.FILE_ANALYSIS }
            val prioritizedCandidates = FileBpmCandidatePrioritizer.prioritize(
                savedReferences = savedReferences,
                publicReferences = candidates.toBpmCandidates(),
                analysisCandidates = analysisOnlyCandidates.map { it.first },
                analysisSources = analysisOnlyCandidates.map { it.second },
                agreementScore = latest.agreementScore,
                segmentsAnalyzed = latest.segmentsAnalyzed,
                engineWarnings = latest.engineWarnings
            )

            _uiState.update {
                it.copy(
                    isLookingUpPublicBpm = false,
                    publicBpmCandidates = candidates,
                    candidates = if (candidates.isNotEmpty()) prioritizedCandidates.candidates else it.candidates,
                    candidateSources = if (candidates.isNotEmpty()) prioritizedCandidates.sources else it.candidateSources,
                    candidateReasonLabels = if (candidates.isNotEmpty()) prioritizedCandidates.reasonLabels else it.candidateReasonLabels,
                    selectedCandidateIndex = if (candidates.isNotEmpty()) 0 else it.selectedCandidateIndex,
                    publicBpmStatusMessage = when {
                        result.isFailure -> "Public BPM lookup failed: ${friendlyLookupError(result.exceptionOrNull())}"
                        candidates.isEmpty() -> "No BPM in the free public database. Try Web Search."
                        prioritizedCandidates.usedSavedReference -> "Public BPM found / saved BPM still preferred"
                        prioritizedCandidates.usedPublicReference -> "Public BPM found / selected"
                        else -> "Public BPM lookup complete"
                    }
                )
            }
        }
    }

    fun usePublicBpm(index: Int) {
        _uiState.update { state ->
            val publicCandidate = state.publicBpmCandidates.getOrNull(index) ?: return@update state
            val candidate = BpmCandidate(
                bpm = publicCandidate.bpm,
                confidence = publicCandidate.matchScore.coerceIn(0.1, 1.0)
            )
            val retained = state.candidates.zip(
                state.candidateSources.ifEmpty { List(state.candidates.size) { BpmSourceType.FILE_ANALYSIS } }
            ).zip(
                state.candidateReasonLabels.ifEmpty { List(state.candidates.size) { "Needs tap-check" } }
            ).filter { (candidatePair, _) -> kotlin.math.abs(candidatePair.first.bpm - candidate.bpm) >= 1.0 }
            state.copy(
                candidates = listOf(candidate) + retained.map { it.first.first },
                candidateSources = listOf(BpmSourceType.PUBLIC_REFERENCE) + retained.map { it.first.second },
                candidateReasonLabels = listOf("Reference match") + retained.map { it.second },
                selectedCandidateIndex = 0,
                statusMessage = "Using public BPM as selected candidate"
            )
        }
    }

    fun onWebReferenceTextChange(value: String) {
        val parsed = WebBpmTextParser.parse(value)
        _uiState.update {
            it.copy(
                webReferenceText = value,
                parsedWebBpm = parsed?.let { result ->
                    BpmCandidate(
                        bpm = result.bpm,
                        confidence = result.confidence
                    )
                },
                webReferenceStatusMessage = when {
                    value.isBlank() -> null
                    parsed != null -> "Parsed ${parsed.label}"
                    else -> "No BPM found in pasted text"
                }
            )
        }
    }

    fun useWebReferenceBpm() {
        val candidate = _uiState.value.parsedWebBpm ?: return
        _uiState.update { state ->
            val retained = state.candidates.zip(
                state.candidateSources.ifEmpty { List(state.candidates.size) { BpmSourceType.FILE_ANALYSIS } }
            ).zip(
                state.candidateReasonLabels.ifEmpty { List(state.candidates.size) { "Needs tap-check" } }
            ).filter { (candidatePair, _) -> kotlin.math.abs(candidatePair.first.bpm - candidate.bpm) >= 1.0 }
            state.copy(
                candidates = listOf(candidate) + retained.map { it.first.first },
                candidateSources = listOf(BpmSourceType.PUBLIC_REFERENCE) + retained.map { it.first.second },
                candidateReasonLabels = listOf("Reference match") + retained.map { it.second },
                selectedCandidateIndex = 0,
                statusMessage = "Using pasted web BPM as selected candidate"
            )
        }
    }

    fun saveWebReferenceBpm() {
        val current = _uiState.value
        val uri = current.selectedUri ?: return
        val candidate = current.parsedWebBpm ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            val metadata = current.metadata
            val result = runCatching {
                repository.insert(
                    BpmRecord(
                        title = metadata?.title
                            ?: metadata?.displayName
                            ?: "Web BPM reference",
                        artist = metadata?.artist,
                        album = metadata?.album,
                        bpm = candidate.bpm,
                        sourceType = BpmSourceType.PUBLIC_REFERENCE,
                        fileUri = uri.toString(),
                        confidence = candidate.confidence,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) "Saved web BPM reference" else "Save failed"
                )
            }
        }
    }

    fun togglePreviewPlayback() {
        val uri = _uiState.value.selectedUri ?: return
        val player = previewPlayer
        if (player != null && previewUri == uri) {
            if (player.isPlaying) {
                player.pause()
                previewProgressJob?.cancel()
                _uiState.update {
                    it.copy(
                        isPreviewPlaying = false,
                        previewPositionMs = player.currentPosition,
                        previewStatusMessage = "Paused"
                    )
                }
            } else {
                startPreviewPlayer(player)
            }
            return
        }

        preparePreviewPlayer(uri)
    }

    fun restartPreviewPlayback() {
        val player = previewPlayer
        if (player == null || previewUri != _uiState.value.selectedUri) {
            _uiState.value.selectedUri?.let(::preparePreviewPlayer)
            return
        }
        runCatching {
            player.seekTo(0)
            startPreviewPlayer(player)
        }.onFailure { error ->
            _uiState.update { it.copy(previewStatusMessage = "Preview failed: ${error.message ?: "cannot restart"}") }
        }
    }

    fun pausePreviewPlayback() {
        previewPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.pause()
                previewProgressJob?.cancel()
                _uiState.update {
                    it.copy(
                        isPreviewPlaying = false,
                        previewPositionMs = player.currentPosition,
                        previewStatusMessage = "Paused"
                    )
                }
            }
        }
    }

    fun onPreviewTap(timestampNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        val snapshot = tapCalculator.addTap(timestampNanos)
        _uiState.update { it.copy(tapSnapshot = snapshot, statusMessage = null) }
    }

    fun resetPreviewTap() {
        _uiState.update {
            it.copy(tapSnapshot = tapCalculator.reset(), statusMessage = null)
        }
    }

    fun halfPreviewTap() {
        _uiState.update {
            it.copy(tapSnapshot = tapCalculator.half(), statusMessage = null)
        }
    }

    fun doublePreviewTap() {
        _uiState.update {
            it.copy(tapSnapshot = tapCalculator.double(), statusMessage = null)
        }
    }

    fun usePreviewTapCandidate() {
        val snapshot = _uiState.value.tapSnapshot
        val bpm = snapshot.bpm ?: return
        val candidate = BpmCandidate(
            bpm = bpm.coerceIn(30.0, 400.0),
            confidence = snapshot.confidence.coerceIn(0.1, 1.0)
        )

        _uiState.update { state ->
            val retained = state.candidates.zip(
                state.candidateSources.ifEmpty { List(state.candidates.size) { BpmSourceType.FILE_ANALYSIS } }
            ).zip(
                state.candidateReasonLabels.ifEmpty { List(state.candidates.size) { "Needs tap-check" } }
            ).filter { (candidatePair, _) -> kotlin.math.abs(candidatePair.first.bpm - candidate.bpm) >= 1.0 }
            state.copy(
                candidates = listOf(candidate) + retained.map { it.first.first },
                candidateSources = listOf(BpmSourceType.TAP) + retained.map { it.first.second },
                candidateReasonLabels = listOf("Tapped BPM") + retained.map { it.second },
                selectedCandidateIndex = 0,
                statusMessage = "Tapped BPM selected"
            )
        }
    }

    fun savePreviewTapCandidate() {
        val current = _uiState.value
        val uri = current.selectedUri ?: return
        val bpm = current.tapSnapshot.bpm ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            val metadata = current.metadata
            val result = runCatching {
                repository.insert(
                    BpmRecord(
                        title = metadata?.title
                            ?: metadata?.displayName
                            ?: "Tapped audio",
                        artist = metadata?.artist,
                        album = metadata?.album,
                        bpm = bpm.coerceIn(30.0, 400.0),
                        sourceType = BpmSourceType.TAP,
                        fileUri = uri.toString(),
                        confidence = current.tapSnapshot.confidence.coerceIn(0.1, 1.0),
                        manuallyVerified = true,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = if (result.isSuccess) "Saved verified tap BPM" else "Save failed"
                )
            }
        }
    }

    private fun adjustSelectedCandidate(multiplier: Double) {
        _uiState.update { state ->
            val selected = state.selectedCandidate ?: return@update state
            val updated = state.candidates.toMutableList()
            updated[state.selectedCandidateIndex] = selected.copy(
                bpm = (selected.bpm * multiplier).coerceIn(30.0, 400.0)
            )
            val reasons = state.candidateReasonLabels.toMutableList()
            if (state.selectedCandidateIndex in reasons.indices) {
                reasons[state.selectedCandidateIndex] = "Adjusted manually"
            }
            state.copy(candidates = updated, candidateReasonLabels = reasons)
        }
    }

    private fun preparePreviewPlayer(uri: Uri) {
        if (_uiState.value.isPreviewPreparing) return
        viewModelScope.launch {
            releasePreviewPlayer(resetState = false)
            _uiState.update {
                it.copy(
                    isPreviewPreparing = true,
                    isPreviewPlaying = false,
                    previewPositionMs = 0,
                    previewDurationMs = 0,
                    previewStatusMessage = "Preparing preview"
                )
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(getApplication<Application>(), uri)
                        prepare()
                    }
                }
            }

            result
                .onSuccess { player ->
                    previewPlayer = player
                    previewUri = uri
                    player.setOnCompletionListener {
                        previewProgressJob?.cancel()
                        _uiState.update { state ->
                            state.copy(
                                isPreviewPlaying = false,
                                previewPositionMs = state.previewDurationMs,
                                previewStatusMessage = "Preview complete"
                            )
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isPreviewPreparing = false,
                            previewDurationMs = player.duration.coerceAtLeast(0),
                            previewStatusMessage = "Ready to play"
                        )
                    }
                    startPreviewPlayer(player)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isPreviewPreparing = false,
                            isPreviewPlaying = false,
                            previewStatusMessage = "Preview failed: ${error.message ?: "cannot open file"}"
                        )
                    }
                }
        }
    }

    private fun startPreviewPlayer(player: MediaPlayer) {
        runCatching {
            player.start()
            _uiState.update {
                it.copy(
                    isPreviewPlaying = true,
                    previewDurationMs = player.duration.coerceAtLeast(0),
                    previewStatusMessage = "Playing"
                )
            }
            startPreviewProgressLoop(player)
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isPreviewPlaying = false,
                    previewStatusMessage = "Preview failed: ${error.message ?: "cannot play"}"
                )
            }
        }
    }

    private fun startPreviewProgressLoop(player: MediaPlayer) {
        previewProgressJob?.cancel()
        previewProgressJob = viewModelScope.launch {
            while (runCatching { player.isPlaying }.getOrDefault(false)) {
                _uiState.update {
                    it.copy(
                        previewPositionMs = runCatching { player.currentPosition }.getOrDefault(it.previewPositionMs),
                        previewDurationMs = runCatching { player.duration.coerceAtLeast(0) }.getOrDefault(it.previewDurationMs)
                    )
                }
                delay(250)
            }
        }
    }

    private fun releasePreviewPlayer(resetState: Boolean = true) {
        previewProgressJob?.cancel()
        previewProgressJob = null
        runCatching { previewPlayer?.release() }
        previewPlayer = null
        previewUri = null
        if (resetState) {
            _uiState.update {
                it.copy(
                    isPreviewPreparing = false,
                    isPreviewPlaying = false,
                    previewPositionMs = 0,
                    previewDurationMs = 0,
                    previewStatusMessage = null
                )
            }
        }
    }

    override fun onCleared() {
        releasePreviewPlayer(resetState = false)
        super.onCleared()
    }

    private fun friendlyAnalysisError(error: Throwable?): String {
        val message = error?.message.orEmpty()
        return when {
            message.contains("No audio track", ignoreCase = true) -> "no audio track found"
            message.contains("decode", ignoreCase = true) -> "audio decoder failed"
            message.isNotBlank() -> message
            else -> "could not decode this audio file"
        }
    }

    private fun BpmSourceType.savedStatusLabel(): String {
        return when (this) {
            BpmSourceType.FILE_ANALYSIS -> "Saved estimate"
            BpmSourceType.PUBLIC_REFERENCE -> "Saved public BPM"
            BpmSourceType.TAP -> "Saved verified tap BPM"
            BpmSourceType.NOW_PLAYING -> "Saved media BPM"
            BpmSourceType.PLAYBACK_CAPTURE -> "Saved capture BPM"
            BpmSourceType.MIC_CAPTURE -> "Saved mic BPM"
        }
    }

    private fun setPublicBpmStatus(message: String) {
        _uiState.update { it.copy(publicBpmStatusMessage = message) }
    }

    private suspend fun findSavedReferences(metadata: AudioFileMetadata): List<BpmRecord> {
        val title = metadata.title
            ?: metadata.displayName?.substringBeforeLast('.')
            ?: return emptyList()
        if (title.isBlank()) return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                repository.findLocalReferences(title.trim(), metadata.artist?.trim())
            }
        }.getOrDefault(emptyList())
    }

    private fun List<PublicBpmCandidate>.toBpmCandidates(): List<BpmCandidate> {
        return map {
            BpmCandidate(
                bpm = it.bpm,
                confidence = it.matchScore.coerceIn(0.1, 1.0)
            )
        }
    }

    private fun publicLookupKey(title: String?, artist: String?): String? {
        val normalizedTitle = title?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedArtist = artist?.trim()?.lowercase().orEmpty()
        return "$normalizedTitle|$normalizedArtist"
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
}

package com.example.integratedbpmmeter.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.integratedbpmmeter.data.BpmPlaylistRange
import com.example.integratedbpmmeter.data.BpmLibraryStats
import com.example.integratedbpmmeter.data.BpmRangePreset
import com.example.integratedbpmmeter.data.BpmSmartCategory
import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmRepository
import com.example.integratedbpmmeter.data.doubleTimeCompatibleBpm
import com.example.integratedbpmmeter.data.effectiveCategory
import com.example.integratedbpmmeter.data.matchesPlaylistRange
import com.example.integratedbpmmeter.data.needsBpmReview
import com.example.integratedbpmmeter.data.toM3u8Playlist
import com.example.integratedbpmmeter.data.toBpmLibraryStats
import com.example.integratedbpmmeter.media.LocalAudioResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistorySortMode(val label: String) {
    NEWEST("Newest"),
    BPM_LOW("BPM low"),
    BPM_HIGH("BPM high"),
    TITLE("Title")
}

enum class HistoryListFilter(val label: String) {
    ALL("All"),
    RUNNING("Running 160-180"),
    JOGGING("Jogging 140-159"),
    CYCLING("Cycling 80-100"),
    DOUBLE_TIME("Double-time 70-90"),
    WARM_UP("Warm-up"),
    RECENT("Recently Saved"),
    VERIFIED("Manually Verified"),
    REVIEW("Needs Review")
}

enum class HistorySourceFilter(val label: String) {
    ALL("All sources"),
    SAMSUNG_MUSIC("Samsung Music"),
    YOUTUBE_MUSIC("YouTube Music"),
    LOCAL_FILE("Local files"),
    OTHER("Other apps")
}

data class HistoryBpmRangeState(
    val minText: String = "",
    val maxText: String = "",
    val preset: BpmRangePreset? = null
) {
    val activeRange: BpmPlaylistRange?
        get() {
            val min = minText.toBpmOrNull()
            val max = maxText.toBpmOrNull()
            return if (min != null && max != null) {
                BpmPlaylistRange(min.coerceIn(1.0, 400.0), max.coerceIn(1.0, 400.0))
            } else {
                null
            }
        }
}

data class LibraryPlayerState(
    val currentRecord: BpmRecord? = null,
    val currentIndex: Int = -1,
    val queueSize: Int = 0,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val statusMessage: String? = null
) {
    val hasQueue: Boolean
        get() = queueSize > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BpmRepository.from(application)
    private val localAudioResolver = LocalAudioResolver(application)
    private var libraryPlayer: MediaPlayer? = null
    private var playerProgressJob: Job? = null
    private var playerQueue: List<BpmRecord> = emptyList()
    private var playerIndex: Int = -1
    private var libraryAudioFocusRequest: AudioFocusRequest? = null
    private val libraryAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val libraryAudioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> runCatching {
                libraryPlayer?.setVolume(1f, 1f)
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseLibraryPlayer(
                statusMessage = "Paused by audio focus",
                abandonFocus = focusChange == AudioManager.AUDIOFOCUS_LOSS
            )
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching {
                libraryPlayer?.setVolume(0.25f, 0.25f)
            }
        }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sortMode = MutableStateFlow(HistorySortMode.NEWEST)
    val sortMode: StateFlow<HistorySortMode> = _sortMode.asStateFlow()

    private val _listFilter = MutableStateFlow(HistoryListFilter.ALL)
    val listFilter: StateFlow<HistoryListFilter> = _listFilter.asStateFlow()

    private val _sourceFilter = MutableStateFlow(HistorySourceFilter.ALL)
    val sourceFilter: StateFlow<HistorySourceFilter> = _sourceFilter.asStateFlow()

    private val _bpmRange = MutableStateFlow(HistoryBpmRangeState())
    val bpmRange: StateFlow<HistoryBpmRangeState> = _bpmRange.asStateFlow()

    private val _localFileMatchStatus = MutableStateFlow<String?>(null)
    val localFileMatchStatus: StateFlow<String?> = _localFileMatchStatus.asStateFlow()

    private val _playerState = MutableStateFlow(LibraryPlayerState())
    val playerState: StateFlow<LibraryPlayerState> = _playerState.asStateFlow()

    val libraryStats: StateFlow<BpmLibraryStats> = repository.observeRecords("")
        .map { it.toBpmLibraryStats() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BpmLibraryStats())

    val records: StateFlow<List<BpmRecord>> = combine(
        _query.flatMapLatest { repository.observeRecords(it) },
        _listFilter,
        _sourceFilter,
        _bpmRange,
        _sortMode
    ) { records, listFilter, sourceFilter, bpmRange, sortMode ->
        val filtered = records
            .filterBySmartList(listFilter)
            .filter { it.matchesSourceFilter(sourceFilter) }
            .filter { it.matchesPlaylistRange(bpmRange.activeRange) }
        when (sortMode) {
            HistorySortMode.NEWEST -> filtered.sortedByDescending { it.createdAt }
            HistorySortMode.BPM_LOW -> filtered.sortedWith(compareBy<BpmRecord> { it.bpm }.thenByDescending { it.createdAt })
            HistorySortMode.BPM_HIGH -> filtered.sortedWith(compareByDescending<BpmRecord> { it.bpm }.thenByDescending { it.createdAt })
            HistorySortMode.TITLE -> filtered.sortedWith(compareBy<BpmRecord> { it.title.lowercase() }.thenBy { it.bpm })
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            records.collect { visibleRecords ->
                syncPlayerQueueWithVisibleRecords(visibleRecords)
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSortMode(sortMode: HistorySortMode) {
        _sortMode.value = sortMode
    }

    fun setListFilter(filter: HistoryListFilter) {
        _listFilter.value = filter
    }

    fun setSourceFilter(filter: HistorySourceFilter) {
        _sourceFilter.value = filter
    }

    fun setBpmPreset(preset: BpmRangePreset) {
        _bpmRange.value = HistoryBpmRangeState(
            minText = preset.range.low.formatInputBpm(),
            maxText = preset.range.high.formatInputBpm(),
            preset = preset
        )
        _sortMode.value = HistorySortMode.BPM_LOW
    }

    fun setMinBpm(value: String) {
        _bpmRange.value = _bpmRange.value.copy(minText = value.onlyBpmInput(), preset = null)
    }

    fun setMaxBpm(value: String) {
        _bpmRange.value = _bpmRange.value.copy(maxText = value.onlyBpmInput(), preset = null)
    }

    fun clearBpmRange() {
        _bpmRange.value = HistoryBpmRangeState()
    }

    fun clearLibraryFilters() {
        _query.value = ""
        _listFilter.value = HistoryListFilter.ALL
        _sourceFilter.value = HistorySourceFilter.ALL
        _bpmRange.value = HistoryBpmRangeState()
    }

    fun shareVisiblePlaylist() {
        val visibleRecords = records.value
        if (visibleRecords.isEmpty()) return
        val range = _bpmRange.value.activeRange
        val playlistName = playlistName(range, _sourceFilter.value, "Playlist")
        val text = buildString {
            appendLine(playlistName)
            appendLine("Tracks: ${visibleRecords.size}")
            appendLine()
            visibleRecords.forEachIndexed { index, record ->
                val artist = record.artist?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                val doubleTime = record.bpm.doubleTimeCompatibleBpm()
                val tempo = if (doubleTime != null && range?.contains(doubleTime) == true) {
                    "${record.bpm.formatInputBpm()} BPM / double-time ${doubleTime.formatInputBpm()} BPM"
                } else {
                    "${record.bpm.formatInputBpm()} BPM"
                }
                appendLine("${index + 1}. ${record.title}$artist | $tempo")
                appendLine("   ${record.youtubeMusicSearchUrl()}")
            }
        }
        val sendIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, playlistName)
            .putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(sendIntent, "Share BPM playlist")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(chooser)
    }

    fun shareVisibleM3u8Playlist() {
        val visibleRecords = records.value
            .filter { !it.fileUri.isNullOrBlank() }
            .distinctBy { it.fileUri }
        if (visibleRecords.isEmpty()) return
        val context = getApplication<Application>()
        val range = _bpmRange.value.activeRange
        val playlistName = playlistName(range, _sourceFilter.value, "M3U8")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.saveM3u8ToDownloads(
                        fileName = "${playlistName.safeFileName()}.m3u8",
                        text = visibleRecords.toM3u8Playlist()
                    )
                }
            }
            result.onSuccess {
                _localFileMatchStatus.value = "M3U8 saved to Downloads/Bpm Now. Samsung Music does not import shared M3U; use Samsung instead."
            }.onFailure {
                _localFileMatchStatus.value = "M3U8 save failed"
            }
        }
    }

    fun playVisibleLocalPlaylist() {
        val visibleRecords = records.value.filter { !it.fileUri.isNullOrBlank() }
        if (visibleRecords.isEmpty()) {
            _playerState.value = LibraryPlayerState(statusMessage = "No local tracks to play")
            return
        }
        playerQueue = visibleRecords
        playQueueIndex(0)
    }

    fun toggleLibraryPlayback() {
        val player = libraryPlayer
        if (player == null) {
            if (playerQueue.isEmpty()) {
                playVisibleLocalPlaylist()
            } else {
                playQueueIndex(playerIndex.coerceAtLeast(0))
            }
            return
        }

        runCatching {
            if (player.isPlaying) {
                pauseLibraryPlayer(statusMessage = "Paused", abandonFocus = true)
            } else {
                startLibraryPlayer(player)
            }
        }.onFailure {
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                statusMessage = "Playback failed"
            )
        }
    }

    fun playNextLibraryTrack() {
        if (playerQueue.isEmpty()) return
        val nextIndex = if (playerIndex + 1 < playerQueue.size) playerIndex + 1 else 0
        playQueueIndex(nextIndex)
    }

    fun playPreviousLibraryTrack() {
        if (playerQueue.isEmpty()) return
        val previousIndex = if (playerIndex - 1 >= 0) playerIndex - 1 else playerQueue.lastIndex
        playQueueIndex(previousIndex)
    }

    fun stopLibraryPlayback() {
        releaseLibraryPlayer(resetState = true)
    }

    fun createVisibleAndroidMusicPlaylist() {
        val visibleRecords = records.value
        if (visibleRecords.isEmpty()) return
        if (!localAudioResolver.hasAudioReadPermission()) {
            _localFileMatchStatus.value = "Music permission is needed for Samsung playlist export"
            return
        }

        val range = _bpmRange.value.activeRange
        val playlistName = playlistName(range, _sourceFilter.value, "Playlist")
        val context = getApplication<Application>()
        viewModelScope.launch {
            val missingSamsungRecords = visibleRecords.filter {
                it.fileUri.isNullOrBlank() && it.sourceAppPackage.isSamsungMusicPackage()
            }
            _localFileMatchStatus.value = if (missingSamsungRecords.isNotEmpty()) {
                "Finding local files for Samsung playlist..."
            } else {
                "Creating Android music playlist..."
            }
            val matchedRecords = resolveMissingSamsungLocalFiles(missingSamsungRecords)
            val matchedById = matchedRecords.associateBy { it.id }
            val exportRecords = visibleRecords
                .map { record -> matchedById[record.id] ?: record }
                .filter { !it.fileUri.isNullOrBlank() }
            if (exportRecords.isEmpty()) {
                _localFileMatchStatus.value = "No local files found for Samsung playlist"
                return@launch
            }

            _localFileMatchStatus.value = "Creating Android music playlist..."
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.createAndroidMusicPlaylist(playlistName, exportRecords)
                }
            }
            result.onSuccess { export ->
                val status = if (export.addedTracks > 0) {
                    val matchedPrefix = if (matchedRecords.isNotEmpty()) {
                        "Matched ${matchedRecords.size}; "
                    } else {
                        ""
                    }
                    when {
                        export.resolvedRecords < export.requestedRecords -> {
                            "${matchedPrefix}Created ${export.addedTracks} tracks; ${export.requestedRecords - export.resolvedRecords} need file links; opening Playlists"
                        }
                        export.addedTracks < export.resolvedRecords -> {
                            "${matchedPrefix}Created ${export.addedTracks} unique tracks; duplicates skipped; opening Playlists"
                        }
                        else -> {
                            "${matchedPrefix}Created ${export.addedTracks} tracks in ${export.playlistName}; opening Playlists"
                        }
                    }
                } else {
                    "No MediaStore audio IDs found for these tracks"
                }
                _localFileMatchStatus.value = status
                if (export.addedTracks > 0) {
                    delay(250)
                    openSamsungMusic()
                }
            }.onFailure {
                _localFileMatchStatus.value = "Music playlist create failed"
            }
        }
    }

    fun reportAudioPermissionDenied() {
        _localFileMatchStatus.value = "Music permission was not granted"
    }

    fun resolveVisibleLocalFiles() {
        if (!localAudioResolver.hasAudioReadPermission()) {
            _localFileMatchStatus.value = "Music permission is needed to find Samsung Music files"
            return
        }

        val candidates = records.value.filter {
            it.fileUri.isNullOrBlank() && it.sourceAppPackage.isSamsungMusicPackage()
        }
        if (candidates.isEmpty()) {
            _localFileMatchStatus.value = "No Samsung Music records need file matching"
            return
        }

        viewModelScope.launch {
            _localFileMatchStatus.value = "Finding local files..."
            val matches = resolveMissingSamsungLocalFiles(candidates)
            _localFileMatchStatus.value = if (matches.isEmpty()) {
                "No local file matches found"
            } else {
                "Matched ${matches.size} of ${candidates.size} Samsung Music files"
            }
        }
    }

    private suspend fun resolveMissingSamsungLocalFiles(candidates: List<BpmRecord>): List<BpmRecord> {
        if (candidates.isEmpty()) return emptyList()
        val matches = withContext(Dispatchers.IO) {
            candidates.mapNotNull { record ->
                val uri = localAudioResolver.resolveTrackUri(
                    title = record.title,
                    artist = record.artist,
                    album = record.album
                )
                uri?.let { record.copy(fileUri = it) }
            }
        }
        matches.forEach { repository.update(it) }
        return matches
    }

    fun linkRecordToPickedFile(record: BpmRecord, uri: Uri) {
        viewModelScope.launch {
            val linkedUri = withContext(Dispatchers.IO) {
                localAudioResolver.resolvePickedFileUri(uri)
            }
            repository.update(record.copy(fileUri = linkedUri))
            _localFileMatchStatus.value = "Linked local file for ${record.title}"
        }
    }

    fun openSamsungMusic() {
        val context = getApplication<Application>()
        val playlistIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SAMSUNG_MUSIC_PLAYLISTS_URI))
            .setComponent(
                ComponentName(
                    "com.sec.android.app.music",
                    "com.samsung.android.app.music.ActivityLauncher"
                )
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val packages = listOf("com.sec.android.app.music", "com.samsung.android.app.music")
        val launchIntent = packages.firstNotNullOfOrNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)
        } ?: Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(
                ComponentName(
                    "com.sec.android.app.music",
                    "com.sec.android.app.music.common.activity.MusicMainActivity"
                )
            )

        val openedPlaylists = runCatching {
            context.startActivity(playlistIntent)
        }.isSuccess
        if (!openedPlaylists) {
            runCatching {
                context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                _localFileMatchStatus.value = "Could not open Samsung Music"
            }
        }
    }

    fun openInYouTubeMusic(record: BpmRecord) {
        val uri = Uri.parse(record.youtubeMusicSearchUrl())
        val context = getApplication<Application>()
        val musicIntent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(YOUTUBE_MUSIC_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val intent = if (musicIntent.resolveActivity(context.packageManager) != null) {
            musicIntent
        } else {
            fallbackIntent
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(fallbackIntent)
        }
    }

    fun delete(record: BpmRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }

    fun markManuallyVerified(record: BpmRecord) {
        viewModelScope.launch {
            repository.update(
                record.copy(
                    manuallyVerified = true,
                    confidence = record.confidence.coerceAtLeast(0.75)
                )
            )
            _localFileMatchStatus.value = "Marked ${record.title} as verified"
        }
    }

    private fun playQueueIndex(index: Int, skipFailures: Int = 0) {
        if (playerQueue.isEmpty()) return
        val targetIndex = index.coerceIn(0, playerQueue.lastIndex)
        val record = playerQueue[targetIndex]
        val fileUri = record.fileUri
        if (fileUri.isNullOrBlank()) {
            playNextAfterFailure(targetIndex, skipFailures)
            return
        }

        viewModelScope.launch {
            releaseLibraryPlayer(resetState = false)
            playerIndex = targetIndex
            _playerState.value = LibraryPlayerState(
                currentRecord = record,
                currentIndex = targetIndex,
                queueSize = playerQueue.size,
                isPreparing = true,
                statusMessage = "Preparing ${record.title}"
            )

            val result = runCatching {
                val uri = fileUri.toPlayableUri()
                withContext(Dispatchers.IO) {
                    MediaPlayer().apply {
                        setAudioAttributes(libraryAudioAttributes)
                        setDataSource(getApplication<Application>(), uri)
                        prepare()
                    }
                }
            }

            result
                .onSuccess { player ->
                    libraryPlayer = player
                    player.setOnCompletionListener {
                        playerProgressJob?.cancel()
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            positionMs = _playerState.value.durationMs,
                            statusMessage = "Track complete"
                        )
                        playNextLibraryTrack()
                    }
                    _playerState.value = _playerState.value.copy(
                        isPreparing = false,
                        durationMs = player.duration.coerceAtLeast(0)
                    )
                    startLibraryPlayer(player)
                }
                .onFailure {
                    playNextAfterFailure(targetIndex, skipFailures)
                }
        }
    }

    private fun playNextAfterFailure(failedIndex: Int, skipFailures: Int) {
        if (skipFailures + 1 >= playerQueue.size) {
            _playerState.value = LibraryPlayerState(
                queueSize = playerQueue.size,
                statusMessage = "No playable local tracks found"
            )
            return
        }
        val nextIndex = if (failedIndex + 1 < playerQueue.size) failedIndex + 1 else 0
        _playerState.value = _playerState.value.copy(statusMessage = "Skipping unplayable track")
        playQueueIndex(nextIndex, skipFailures + 1)
    }

    private fun syncPlayerQueueWithVisibleRecords(visibleRecords: List<BpmRecord>) {
        if (playerQueue.isEmpty()) return
        val visibleLocalRecords = visibleRecords.filter { !it.fileUri.isNullOrBlank() }
        val currentRecord = _playerState.value.currentRecord
        if (currentRecord == null) {
            playerQueue = visibleLocalRecords
            if (playerQueue.isEmpty()) {
                playerIndex = -1
                _playerState.value = LibraryPlayerState()
            } else {
                _playerState.value = _playerState.value.copy(queueSize = playerQueue.size)
            }
            return
        }

        val currentIndex = visibleLocalRecords.indexOfFirst { it.id == currentRecord.id }
        if (currentIndex >= 0) {
            playerQueue = visibleLocalRecords
            playerIndex = currentIndex
            _playerState.value = _playerState.value.copy(
                currentRecord = visibleLocalRecords[currentIndex],
                currentIndex = currentIndex,
                queueSize = visibleLocalRecords.size
            )
        } else {
            releaseLibraryPlayer(resetState = false)
            playerQueue = visibleLocalRecords
            playerIndex = -1
            _playerState.value = LibraryPlayerState(
                queueSize = visibleLocalRecords.size,
                statusMessage = if (visibleLocalRecords.isEmpty()) {
                    "No local tracks in this view"
                } else {
                    "Queue changed; tap Play"
                }
            )
        }
    }

    private fun startLibraryPlayer(player: MediaPlayer) {
        runCatching {
            if (!requestLibraryAudioFocus()) {
                _playerState.value = _playerState.value.copy(
                    isPreparing = false,
                    isPlaying = false,
                    statusMessage = "Audio focus denied"
                )
                return
            }
            player.start()
            _playerState.value = _playerState.value.copy(
                isPreparing = false,
                isPlaying = true,
                durationMs = player.duration.coerceAtLeast(0),
                statusMessage = "Playing"
            )
            startPlayerProgressLoop(player)
        }.onFailure {
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                statusMessage = "Playback failed"
            )
        }
    }

    private fun requestLibraryAudioFocus(): Boolean {
        val context = getApplication<Application>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = libraryAudioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(libraryAudioAttributes)
            .setOnAudioFocusChangeListener(libraryAudioFocusListener)
            .build()
            .also { libraryAudioFocusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonLibraryAudioFocus() {
        val request = libraryAudioFocusRequest ?: return
        val context = getApplication<Application>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { audioManager.abandonAudioFocusRequest(request) }
    }

    private fun pauseLibraryPlayer(statusMessage: String, abandonFocus: Boolean) {
        val player = libraryPlayer ?: return
        runCatching {
            if (player.isPlaying) {
                player.pause()
            }
            playerProgressJob?.cancel()
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                positionMs = player.currentPosition,
                statusMessage = statusMessage
            )
        }
        if (abandonFocus) {
            abandonLibraryAudioFocus()
        }
    }

    private fun startPlayerProgressLoop(player: MediaPlayer) {
        playerProgressJob?.cancel()
        playerProgressJob = viewModelScope.launch {
            while (runCatching { player.isPlaying }.getOrDefault(false)) {
                _playerState.value = _playerState.value.copy(
                    positionMs = runCatching { player.currentPosition }.getOrDefault(_playerState.value.positionMs),
                    durationMs = runCatching { player.duration.coerceAtLeast(0) }.getOrDefault(_playerState.value.durationMs)
                )
                delay(500)
            }
        }
    }

    private fun releaseLibraryPlayer(resetState: Boolean) {
        playerProgressJob?.cancel()
        playerProgressJob = null
        runCatching { libraryPlayer?.release() }
        libraryPlayer = null
        abandonLibraryAudioFocus()
        if (resetState) {
            playerQueue = emptyList()
            playerIndex = -1
            _playerState.value = LibraryPlayerState()
        }
    }

    override fun onCleared() {
        releaseLibraryPlayer(resetState = false)
        super.onCleared()
    }

    fun updateRecord(
        record: BpmRecord,
        title: String,
        artist: String,
        album: String,
        bpmText: String,
        categoryOverride: BpmSmartCategory?,
        manuallyVerified: Boolean
    ) {
        val bpm = bpmText.toDoubleOrNull() ?: return
        viewModelScope.launch {
            repository.update(
                record.copy(
                    title = title.trim().ifBlank { record.title },
                    artist = artist.trim().ifBlank { null },
                    album = album.trim().ifBlank { null },
                    bpm = bpm.coerceIn(1.0, 400.0),
                    categoryOverride = categoryOverride,
                    manuallyVerified = manuallyVerified
                )
            )
        }
    }

    private fun List<BpmRecord>.filterBySmartList(filter: HistoryListFilter): List<BpmRecord> {
        return when (filter) {
            HistoryListFilter.ALL -> this
            HistoryListFilter.RUNNING -> filter {
                it.bpm in 160.0..180.0 || it.bpm.doubleTimeCompatibleBpm()?.let { value -> value in 160.0..180.0 } == true
            }
            HistoryListFilter.JOGGING -> filter {
                it.bpm in 140.0..159.0 || it.bpm.doubleTimeCompatibleBpm()?.let { value -> value in 140.0..159.0 } == true
            }
            HistoryListFilter.CYCLING -> filter { it.bpm in 80.0..100.0 }
            HistoryListFilter.DOUBLE_TIME -> filter { it.effectiveCategory() == BpmSmartCategory.LOW_DOUBLE_TIME }
            HistoryListFilter.WARM_UP -> filter { it.effectiveCategory() == BpmSmartCategory.WARM_UP_COOL_DOWN }
            HistoryListFilter.RECENT -> sortedByDescending { it.createdAt }.take(30)
            HistoryListFilter.VERIFIED -> filter { it.manuallyVerified }
            HistoryListFilter.REVIEW -> filter { it.needsBpmReview() }
        }
    }
}

private fun BpmRecord.matchesSourceFilter(filter: HistorySourceFilter): Boolean {
    return when (filter) {
        HistorySourceFilter.ALL -> true
        HistorySourceFilter.SAMSUNG_MUSIC -> sourceAppPackage.isSamsungMusicPackage()
        HistorySourceFilter.YOUTUBE_MUSIC -> sourceAppPackage == YOUTUBE_MUSIC_PACKAGE
        HistorySourceFilter.LOCAL_FILE -> !fileUri.isNullOrBlank()
        HistorySourceFilter.OTHER -> !sourceAppPackage.isSamsungMusicPackage() &&
            sourceAppPackage != YOUTUBE_MUSIC_PACKAGE &&
            fileUri.isNullOrBlank()
    }
}

private fun playlistName(
    range: BpmPlaylistRange?,
    sourceFilter: HistorySourceFilter,
    suffix: String
): String {
    val sourceName = sourceFilter.takeUnless { it == HistorySourceFilter.ALL }?.label
    return listOfNotNull("Bpm Now", sourceName, range?.label(), suffix)
        .joinToString(" ")
}

private const val M3U8_FILE_MIME_TYPE = "audio/x-mpegurl"

private fun Application.saveM3u8ToDownloads(fileName: String, text: String): Uri {
    val resolver = contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, M3U8_FILE_MIME_TYPE)
        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Bpm Now")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("Cannot create M3U8 download")
    resolver.openOutputStream(uri, "w")?.use { output ->
        output.write(text.toByteArray(Charsets.UTF_8))
    } ?: error("Cannot write M3U8 download")
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return uri
}

private data class MusicPlaylistExportResult(
    val requestedRecords: Int,
    val resolvedRecords: Int,
    val addedTracks: Int,
    val playlistName: String
)

private fun Application.createAndroidMusicPlaylist(
    playlistName: String,
    records: List<BpmRecord>
): MusicPlaylistExportResult {
    @Suppress("DEPRECATION")
    val playlistsUri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
    @Suppress("DEPRECATION")
    val membersUriFor: (Long) -> Uri = { playlistId ->
        MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
    }
    val resolver = contentResolver
    val resolvedAudioIds = records
        .mapNotNull { it.fileUri?.let(::audioIdFromFileUri) }
    val audioIds = resolvedAudioIds.distinct()
    if (audioIds.isEmpty()) {
        return MusicPlaylistExportResult(
            requestedRecords = records.size,
            resolvedRecords = 0,
            addedTracks = 0,
            playlistName = playlistName
        )
    }

    val existingPlaylistId = findPlaylistId(playlistName)
    val deletedExisting = existingPlaylistId?.let { playlistId ->
        resolver.delete(ContentUris.withAppendedId(playlistsUri, playlistId), null, null)
    } ?: 0
    val targetPlaylistName = if (existingPlaylistId != null && deletedExisting == 0) {
        "${playlistName} ${playlistExportTimestamp()}"
    } else {
        playlistName
    }
    val playlistId = createMusicPlaylistRow(playlistsUri, targetPlaylistName)

    val membersUri = membersUriFor(playlistId)
    clearPlaylistMembers(membersUri)
    audioIds.forEachIndexed { index, audioId ->
        val values = ContentValues().apply {
            @Suppress("DEPRECATION")
            put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId)
            @Suppress("DEPRECATION")
            put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, index + 1)
        }
        resolver.insert(membersUri, values)
    }
    return MusicPlaylistExportResult(
        requestedRecords = records.size,
        resolvedRecords = resolvedAudioIds.size,
        addedTracks = audioIds.size,
        playlistName = targetPlaylistName
    )
}

private fun Application.createMusicPlaylistRow(playlistsUri: Uri, playlistName: String): Long {
    val values = ContentValues().apply {
        @Suppress("DEPRECATION")
        put(MediaStore.Audio.Playlists.NAME, playlistName)
        @Suppress("DEPRECATION")
        put(MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis() / 1000)
        @Suppress("DEPRECATION")
        put(MediaStore.Audio.Playlists.DATE_MODIFIED, System.currentTimeMillis() / 1000)
    }
    val inserted = contentResolver.insert(playlistsUri, values) ?: error("Cannot create playlist")
    return ContentUris.parseId(inserted)
}

private fun playlistExportTimestamp(): String {
    return SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())
}

private fun Application.clearPlaylistMembers(membersUri: Uri) {
    @Suppress("DEPRECATION")
    val projection = arrayOf(MediaStore.Audio.Playlists.Members._ID)
    @Suppress("DEPRECATION")
    val idColumn = MediaStore.Audio.Playlists.Members._ID
    val memberIds = contentResolver.query(membersUri, projection, null, null, null)?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getLong(0))
            }
        }
    }.orEmpty()

    if (memberIds.isNotEmpty()) {
        val placeholders = memberIds.joinToString(",") { "?" }
        val args = memberIds.map { it.toString() }.toTypedArray()
        val deleted = contentResolver.delete(membersUri, "$idColumn IN ($placeholders)", args)
        if (deleted == 0) {
            memberIds.forEach { memberId ->
                contentResolver.delete(membersUri, "$idColumn = ?", arrayOf(memberId.toString()))
            }
        }
    }
    contentResolver.delete(membersUri, null, null)
}

private fun Application.findPlaylistId(playlistName: String): Long? {
    @Suppress("DEPRECATION")
    val playlistsUri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
    val resolver = contentResolver
    @Suppress("DEPRECATION")
    val projection = arrayOf(MediaStore.Audio.Playlists._ID)
    @Suppress("DEPRECATION")
    val selection = "${MediaStore.Audio.Playlists.NAME} = ?"
    return resolver.query(playlistsUri, projection, selection, arrayOf(playlistName), null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }
}

private fun Application.audioIdFromFileUri(fileUri: String): Long? {
    val uri = runCatching { Uri.parse(fileUri) }.getOrNull()
    val contentId = uri?.let(::audioIdFromContentUri)
    if (contentId != null) return contentId

    @Suppress("DEPRECATION")
    val dataColumn = MediaStore.Audio.Media.DATA
    val resolver = contentResolver
    val projection = arrayOf(MediaStore.Audio.Media._ID)
    val selection = "$dataColumn = ?"
    return resolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        arrayOf(fileUri),
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }
}

private fun Application.audioIdFromContentUri(uri: Uri): Long? {
    if (uri.scheme != "content") return null
    if (uri.authority == "media" || uri.authority?.startsWith("media") == true) {
        uri.lastPathSegment?.toLongOrNull()?.let { return it }
    }
    if (DocumentsContract.isDocumentUri(this, uri) && uri.authority == "com.android.providers.media.documents") {
        val parts = runCatching { DocumentsContract.getDocumentId(uri).split(':') }.getOrNull()
        if (parts?.getOrNull(0) == "audio") return parts.getOrNull(1)?.toLongOrNull()
    }

    return contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }
}

private fun String.toBpmOrNull(): Double? {
    return trim().replace(',', '.').toDoubleOrNull()
}

private fun String.onlyBpmInput(): String {
    return filter { it.isDigit() || it == '.' || it == ',' }.take(6)
}

private fun Double.formatInputBpm(): String {
    val rounded = kotlin.math.round(this)
    return if (kotlin.math.abs(this - rounded) < 0.01) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this)
    }
}

private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
private const val SAMSUNG_MUSIC_PLAYLISTS_URI = "samu_bixby://playlists"
private val SAMSUNG_MUSIC_PACKAGES = setOf(
    "com.sec.android.app.music",
    "com.samsung.android.app.music"
)

private fun String?.isSamsungMusicPackage(): Boolean {
    return this in SAMSUNG_MUSIC_PACKAGES
}

private fun BpmRecord.youtubeMusicSearchUrl(): String {
    val query = listOfNotNull(title, artist)
        .joinToString(" ")
        .ifBlank { title }
    val encoded = URLEncoder.encode(query, "UTF-8")
    return "https://music.youtube.com/search?q=$encoded"
}

private fun String.safeFileName(): String {
    return replace(Regex("""[^a-zA-Z0-9._ -]"""), "_")
        .trim()
        .ifBlank { "BpmNow" }
}

private fun String.toPlayableUri(): Uri {
    return runCatching { Uri.parse(this) }.getOrNull()
        ?.takeIf { !it.scheme.isNullOrBlank() }
        ?: Uri.fromFile(java.io.File(this))
}

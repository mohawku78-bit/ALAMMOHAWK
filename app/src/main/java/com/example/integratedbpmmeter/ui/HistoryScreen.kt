package com.example.integratedbpmmeter.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.integratedbpmmeter.data.BpmSmartCategory
import com.example.integratedbpmmeter.data.BpmLibraryStats
import com.example.integratedbpmmeter.data.BpmRangePreset
import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmSourceType
import com.example.integratedbpmmeter.data.confidenceBadgeLabel
import com.example.integratedbpmmeter.data.doubleTimeCompatibleBpm
import com.example.integratedbpmmeter.data.effectiveCategory
import com.example.integratedbpmmeter.data.halfTimeFeelBpm
import com.example.integratedbpmmeter.data.isSamsungMusicSource
import com.example.integratedbpmmeter.data.needsBpmReview
import com.example.integratedbpmmeter.data.verificationHintLabel
import com.example.integratedbpmmeter.media.LocalAudioResolver
import com.example.integratedbpmmeter.viewmodel.HistoryBpmRangeState
import com.example.integratedbpmmeter.viewmodel.HistorySortMode
import com.example.integratedbpmmeter.viewmodel.HistoryListFilter
import com.example.integratedbpmmeter.viewmodel.HistorySourceFilter
import com.example.integratedbpmmeter.viewmodel.HistoryViewModel
import com.example.integratedbpmmeter.viewmodel.LibraryPlayerState
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val listFilter by viewModel.listFilter.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()
    val bpmRange by viewModel.bpmRange.collectAsStateWithLifecycle()
    val localFileMatchStatus by viewModel.localFileMatchStatus.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val libraryStats by viewModel.libraryStats.collectAsStateWithLifecycle()
    val hasActiveLibraryFilter = query.isNotBlank() ||
        listFilter != HistoryListFilter.ALL ||
        sourceFilter != HistorySourceFilter.ALL ||
        bpmRange.activeRange != null
    val visibleReviewCount = records.count { it.needsBpmReview() }
    var editingRecord by remember { mutableStateOf<BpmRecord?>(null) }
    var fileLinkRecord by remember { mutableStateOf<BpmRecord?>(null) }
    var verifyingRecord by remember { mutableStateOf<BpmRecord?>(null) }
    var pendingAudioPermissionAction by remember { mutableStateOf<AudioPermissionAction?>(null) }
    var showAdvancedLibraryTools by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val showPlayer = playerState.currentRecord != null || !playerState.statusMessage.isNullOrBlank()
    KeepScreenOn(enabled = playerState.isPlaying || playerState.isPreparing)
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingAudioPermissionAction
        pendingAudioPermissionAction = null
        if (granted && action != null) {
            when (action) {
                AudioPermissionAction.ResolveLocalFiles -> viewModel.resolveVisibleLocalFiles()
                AudioPermissionAction.CreateMusicPlaylist -> viewModel.createVisibleAndroidMusicPlaylist()
            }
        } else if (!granted) {
            viewModel.reportAudioPermissionDenied()
        }
    }
    val fileLinkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val record = fileLinkRecord
        if (uri != null && record != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.linkRecordToPickedFile(record, uri)
        }
        fileLinkRecord = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        item {
            LibraryHeader(
                recordCount = records.size,
                totalRecordCount = libraryStats.totalRecords,
                sortLabel = sortMode.label,
                filtered = hasActiveLibraryFilter,
                reviewCount = libraryStats.reviewCount,
                onShowReview = { viewModel.setListFilter(HistoryListFilter.REVIEW) }
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search library") },
                singleLine = true
            )
        }

        if (showAdvancedLibraryTools || sourceFilter != HistorySourceFilter.ALL) {
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(HistorySourceFilter.values().toList()) { filter ->
                    FilterChip(
                        selected = sourceFilter == filter,
                        onClick = { viewModel.setSourceFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }
        }
        }

        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(HistoryListFilter.values().toList()) { filter ->
                    FilterChip(
                        selected = listFilter == filter,
                        onClick = { viewModel.setListFilter(filter) },
                        label = { Text(filter.label(libraryStats)) }
                    )
                }
            }
        }

        if (records.isEmpty()) {
            item {
                EmptyLibraryCard(
                    filtered = hasActiveLibraryFilter,
                    onClearFilters = viewModel::clearLibraryFilters
                )
            }
        }

        if (showPlayer) {
            item {
                LibraryPlaylistPlayer(
                    state = playerState,
                    onPrevious = viewModel::playPreviousLibraryTrack,
                    onPlayPause = viewModel::toggleLibraryPlayback,
                    onNext = viewModel::playNextLibraryTrack,
                    onStop = viewModel::stopLibraryPlayback
                )
            }
        }

        item {
            BpmPlaylistRangePanel(
                bpmRange = bpmRange,
                recordCount = records.size,
                localFileCount = records
                    .mapNotNull { it.fileUri?.takeIf { fileUri -> fileUri.isNotBlank() } }
                    .distinct()
                    .size,
                missingSamsungFileCount = records.count {
                    it.isSamsungMusicSource() && it.fileUri.isNullOrBlank()
                },
                reviewCount = visibleReviewCount,
                showAdvancedTools = showAdvancedLibraryTools,
                onToggleAdvancedTools = { showAdvancedLibraryTools = !showAdvancedLibraryTools },
                onShowReview = { viewModel.setListFilter(HistoryListFilter.REVIEW) },
                onPreset = viewModel::setBpmPreset,
                onMinChange = viewModel::setMinBpm,
                onMaxChange = viewModel::setMaxBpm,
                onClear = viewModel::clearBpmRange,
                onShare = viewModel::shareVisiblePlaylist,
                onPlayPlaylist = viewModel::playVisibleLocalPlaylist,
                onShareM3u8 = viewModel::shareVisibleM3u8Playlist,
                onCreateMusicPlaylist = {
                    val permission = LocalAudioResolver.requiredPermission()
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.createVisibleAndroidMusicPlaylist()
                    } else {
                        pendingAudioPermissionAction = AudioPermissionAction.CreateMusicPlaylist
                        audioPermissionLauncher.launch(permission)
                    }
                },
                onOpenSamsungMusic = viewModel::openSamsungMusic,
                onResolveLocalFiles = {
                    val permission = LocalAudioResolver.requiredPermission()
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.resolveVisibleLocalFiles()
                    } else {
                        pendingAudioPermissionAction = AudioPermissionAction.ResolveLocalFiles
                        audioPermissionLauncher.launch(permission)
                    }
                },
                localFileMatchStatus = localFileMatchStatus
            )
        }

        if (records.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(HistorySortMode.values().toList()) { mode ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { viewModel.setSortMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
            items(records, key = { it.id }) { record ->
                HistoryRecordItem(
                    record = record,
                    onOpenYouTubeMusic = { viewModel.openInYouTubeMusic(record) },
                    onLinkFile = {
                        fileLinkRecord = record
                        fileLinkLauncher.launch(arrayOf("audio/*"))
                    },
                    onMarkVerified = { verifyingRecord = record },
                    onEdit = { editingRecord = record },
                    onDelete = { viewModel.delete(record) }
                )
            }
        }

        }
    }

    editingRecord?.let { record ->
        EditRecordDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { title, artist, album, bpm, categoryOverride, manuallyVerified ->
                viewModel.updateRecord(record, title, artist, album, bpm, categoryOverride, manuallyVerified)
                editingRecord = null
            }
        )
    }

    verifyingRecord?.let { record ->
        VerifyRecordDialog(
            record = record,
            onDismiss = { verifyingRecord = null },
            onConfirm = {
                viewModel.markManuallyVerified(record)
                verifyingRecord = null
            }
        )
    }
}

private enum class AudioPermissionAction {
    ResolveLocalFiles,
    CreateMusicPlaylist
}

@Composable
private fun BpmPlaylistRangePanel(
    bpmRange: HistoryBpmRangeState,
    recordCount: Int,
    localFileCount: Int,
    missingSamsungFileCount: Int,
    reviewCount: Int,
    showAdvancedTools: Boolean,
    onToggleAdvancedTools: () -> Unit,
    onShowReview: () -> Unit,
    onPreset: (BpmRangePreset) -> Unit,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onShareM3u8: () -> Unit,
    onCreateMusicPlaylist: () -> Unit,
    onOpenSamsungMusic: () -> Unit,
    onResolveLocalFiles: () -> Unit,
    localFileMatchStatus: String?
) {
    val activeRange = bpmRange.activeRange
    val localStatusText = when {
        localFileCount > 0 && missingSamsungFileCount > 0 -> {
            "$localFileCount playable / $missingSamsungFileCount to link"
        }
        localFileCount > 0 -> "$localFileCount playable"
        else -> "No linked files"
    }
    val rangeText = activeRange?.label() ?: "Choose a BPM range"
    val summaryText = listOfNotNull(
        "$recordCount tracks",
        localStatusText,
        if (reviewCount > 0) "$reviewCount need tap-check" else null
    ).joinToString(" / ")
    val rangeHelperText = if (activeRange != null) {
        "Double-time matches are included when a 70-90 BPM track fits the selected running range."
    } else {
        "Use 160, 170, 180, or a custom min / max to build a workout list."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Workout BPM list",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$rangeText / $summaryText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilterChip(
                    selected = showAdvancedTools,
                    onClick = onToggleAdvancedTools,
                    label = { Text("More tools") }
                )
            }

            Text(
                text = rangeHelperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BpmRangePreset.values().toList()) { preset ->
                    FilterChip(
                        selected = bpmRange.preset == preset,
                        onClick = { onPreset(preset) },
                        label = { Text(preset.chipLabel) }
                    )
                }
                if (activeRange != null) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onClear,
                            label = { Text("Clear") }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaylistIconAction(
                    enabled = localFileCount > 0,
                    onClick = onPlayPlaylist,
                    icon = Icons.Filled.PlayArrow,
                    label = "Play linked files",
                    contentDescription = "Play visible local playlist",
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
                PlaylistIconAction(
                    enabled = recordCount > 0,
                    onClick = onShare,
                    icon = Icons.Filled.Share,
                    label = "Share links",
                    contentDescription = "Share text playlist with search links",
                    modifier = Modifier.weight(1f)
                )
            }

            if (reviewCount > 0) {
                Surface(
                    onClick = onShowReview,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$reviewCount tracks need tap-check",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Review",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (showAdvancedTools) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaylistIconAction(
                        enabled = missingSamsungFileCount > 0,
                        onClick = onResolveLocalFiles,
                        icon = Icons.Filled.Search,
                        label = if (missingSamsungFileCount > 0) "Find files" else "Linked",
                        contentDescription = if (missingSamsungFileCount > 0) {
                            "Find music files"
                        } else {
                            "Music files already linked"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PlaylistIconAction(
                        enabled = recordCount > 0,
                        onClick = onCreateMusicPlaylist,
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        label = "Try Samsung",
                        contentDescription = "Try creating a Samsung Music playlist",
                        modifier = Modifier.weight(1f)
                    )
                    PlaylistIconAction(
                        enabled = true,
                        onClick = onOpenSamsungMusic,
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = "Open app",
                        contentDescription = "Open Samsung Music playlists",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaylistIconAction(
                        enabled = localFileCount > 0,
                        onClick = onShareM3u8,
                        icon = Icons.Filled.FileDownload,
                        label = "M3U file",
                        contentDescription = "Save playlist file for compatible players",
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Samsung playlists, M3U files, local linking, and source filters are optional. Player support may vary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = bpmRange.minText,
                    onValueChange = onMinChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Min BPM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = bpmRange.maxText,
                    onValueChange = onMaxChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Max BPM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            if (!localFileMatchStatus.isNullOrBlank()) {
                Text(
                    text = localFileMatchStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    filtered: Boolean,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (filtered) "No tracks match this view" else "No saved BPM records",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (filtered) {
                    "Clear filters or choose another BPM range."
                } else {
                    "Tap, public, file, and media BPM saves will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (filtered) {
                TextButton(onClick = onClearFilters) {
                    Text("Clear filters")
                }
            }
        }
    }
}

@Composable
private fun LibraryPlaylistPlayer(
    state: LibraryPlayerState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val record = state.currentRecord
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record?.title ?: "Playlist player",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            record?.artist,
                            record?.bpm?.let { "${String.format(Locale.US, "%.1f", it)} BPM" },
                            if (state.queueSize > 0 && state.currentIndex >= 0) "${state.currentIndex + 1}/${state.queueSize}" else null,
                            state.statusMessage
                        ).joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPrevious, enabled = state.hasQueue) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous local track")
                }
                IconButton(onClick = onPlayPause, enabled = state.hasQueue || record != null) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause local playlist" else "Play local playlist"
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasQueue) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next local track")
                }
                IconButton(
                    onClick = onStop,
                    enabled = record != null || state.hasQueue || !state.statusMessage.isNullOrBlank()
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop local playlist")
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatPlayerTime(state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatPlayerTime(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaylistIconAction(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = when {
        !enabled -> colors.surfaceContainer
        primary -> colors.primary
        else -> colors.surface
    }
    val contentColor = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
        primary -> colors.onPrimary
        else -> colors.primary
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (primary) null else BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    recordCount: Int,
    totalRecordCount: Int,
    sortLabel: String,
    filtered: Boolean,
    reviewCount: Int,
    onShowReview: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Saved tempos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (filtered) {
                    "$recordCount visible / $totalRecordCount records"
                } else {
                    "$totalRecordCount records"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = sortLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (reviewCount > 0) {
                Surface(
                    onClick = onShowReview,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = "$reviewCount tap-check",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordItem(
    record: BpmRecord,
    onOpenYouTubeMusic: () -> Unit,
    onLinkFile: () -> Unit,
    onMarkVerified: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(record.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val category = record.effectiveCategory()
            val doubleTime = record.bpm.doubleTimeCompatibleBpm()
            val halfTime = record.bpm.halfTimeFeelBpm()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.width(78.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", record.bpm),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = record.confidenceBadgeLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val artistAlbum = listOfNotNull(record.artist, record.album).joinToString(" - ")
                    if (artistAlbum.isNotBlank()) {
                        Text(
                            text = artistAlbum,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = listOfNotNull(
                            category.label,
                            doubleTime?.let { "Double-time ${String.format(Locale.US, "%.1f", it)} BPM" },
                            halfTime?.let { "Half-time ${String.format(Locale.US, "%.1f", it)} BPM" },
                            record.confirmationLabel()
                        ).joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Find on YouTube Music") },
                            leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenYouTubeMusic()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Link local file") },
                            leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onLinkFile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            if (record.needsBpmReview()) {
                ReviewRecordActionStrip(onMarkVerified = onMarkVerified)
            }
        }
    }
}

@Composable
private fun ReviewRecordActionStrip(
    onMarkVerified: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap-check before trusting this BPM",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onMarkVerified) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Verified")
            }
        }
    }
}

@Composable
private fun VerifyRecordDialog(
    record: BpmRecord,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as verified?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", record.bpm)} BPM",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Only confirm after you tapped along with the track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Mark verified")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditRecordDialog(
    record: BpmRecord,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, BpmSmartCategory?, Boolean) -> Unit
) {
    var title by remember(record.id) { mutableStateOf(record.title) }
    var artist by remember(record.id) { mutableStateOf(record.artist.orEmpty()) }
    var album by remember(record.id) { mutableStateOf(record.album.orEmpty()) }
    var bpm by remember(record.id) { mutableStateOf(String.format(Locale.US, "%.1f", record.bpm)) }
    var categoryOverride by remember(record.id) { mutableStateOf(record.categoryOverride) }
    var manuallyVerified by remember(record.id) { mutableStateOf(record.manuallyVerified) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit BPM record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                OutlinedTextField(
                    value = bpm,
                    onValueChange = { bpm = it },
                    label = { Text("BPM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    text = "Smart category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = categoryOverride == null,
                            onClick = { categoryOverride = null },
                            label = { Text("Auto") }
                        )
                    }
                    items(BpmSmartCategory.values().toList()) { category ->
                        FilterChip(
                            selected = categoryOverride == category,
                            onClick = { categoryOverride = category },
                            label = { Text(category.label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manually verified",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Mark tracks measured or checked by you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = manuallyVerified,
                        onCheckedChange = { manuallyVerified = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, artist, album, bpm, categoryOverride, manuallyVerified) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun Long.formatDate(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
}

private fun formatPlayerTime(valueMs: Int): String {
    val totalSeconds = (valueMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun BpmSourceType.label(): String {
    return when (this) {
        BpmSourceType.TAP -> "Tap"
        BpmSourceType.NOW_PLAYING -> "Media"
        BpmSourceType.FILE_ANALYSIS -> "File estimate"
        BpmSourceType.PUBLIC_REFERENCE -> "Public"
        BpmSourceType.PLAYBACK_CAPTURE -> "Capture exp"
        BpmSourceType.MIC_CAPTURE -> "Mic estimate"
    }
}

private fun BpmRecord.sourceAppLabel(): String? {
    return when (sourceAppPackage) {
        "com.google.android.apps.youtube.music" -> "YouTube Music"
        "com.sec.android.app.music",
        "com.samsung.android.app.music" -> "Samsung Music"
        null -> null
        else -> sourceAppPackage.substringAfterLast('.')
    }
}

private fun BpmRecord.confirmationLabel(): String {
    return when {
        manuallyVerified || sourceType == BpmSourceType.TAP || sourceType == BpmSourceType.NOW_PLAYING -> "Verified"
        sourceType == BpmSourceType.PUBLIC_REFERENCE -> "Reference"
        sourceType == BpmSourceType.FILE_ANALYSIS -> "Draft"
        needsBpmReview() -> "Tap check needed"
        else -> "Saved"
    }
}

private fun HistoryListFilter.label(stats: BpmLibraryStats): String {
    return if (this == HistoryListFilter.REVIEW && stats.reviewCount > 0) {
        "${label} ${stats.reviewCount}"
    } else {
        label
    }
}

package com.example.integratedbpmmeter.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.integratedbpmmeter.audio.BpmCandidate
import com.example.integratedbpmmeter.audio.AudioFileMetadata
import com.example.integratedbpmmeter.audio.TapBpmSnapshot
import com.example.integratedbpmmeter.data.BpmRecord
import com.example.integratedbpmmeter.data.BpmSmartCategory
import com.example.integratedbpmmeter.data.BpmSourceType
import com.example.integratedbpmmeter.data.doubleTimeCompatibleBpm
import com.example.integratedbpmmeter.data.halfTimeFeelBpm
import com.example.integratedbpmmeter.data.analysisRangeRisk
import com.example.integratedbpmmeter.data.AnalysisRangeRisk
import com.example.integratedbpmmeter.audio.FileCandidateTrust
import com.example.integratedbpmmeter.audio.fileCandidateTrust
import com.example.integratedbpmmeter.lookup.PublicBpmCandidate
import com.example.integratedbpmmeter.media.MediaSessionReader
import com.example.integratedbpmmeter.media.NowPlayingTrack
import com.example.integratedbpmmeter.viewmodel.FileAnalyzeViewModel
import com.example.integratedbpmmeter.viewmodel.NowPlayingViewModel
import com.example.integratedbpmmeter.viewmodel.PlaybackCaptureViewModel
import com.example.integratedbpmmeter.viewmodel.SettingsViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    onFileAnalyze: () -> Unit,
    viewModel: NowPlayingViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    KeepScreenOn()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val track = uiState.track
    val snapshot = uiState.tapSnapshot
    val smartHint = snapshot.bpm?.smartTempoHint()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = AppUi.ScreenPadding,
                top = 28.dp,
                end = AppUi.ScreenPadding,
                bottom = AppUi.ScreenPadding
            ),
        verticalArrangement = Arrangement.spacedBy(AppUi.SectionGap)
    ) {
        if (!uiState.hasNotificationAccess) {
            PermissionStatusCard(
                title = "Current media access",
                granted = false,
                status = "Needs access",
                body = "Enable notification listener access to show the song from Samsung Music, YouTube Music, or another player.",
                actionLabel = "Open Access",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
        }

        NowPlayingMediaCard(
            track = track,
            fallbackTitle = uiState.fallbackTitle,
            hasNotificationAccess = uiState.hasNotificationAccess,
            onPrevious = viewModel::previousTrack,
            onPlayPause = viewModel::playPauseTrack,
            onNext = viewModel::nextTrack
        )

        if (track?.title == null) {
            OutlinedTextField(
                value = uiState.fallbackTitle,
                onValueChange = viewModel::onFallbackTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Manual title for save") },
                singleLine = true
            )
        }

        LowLatencyTapPad(
            bpmText = snapshot.bpm?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
            label = "TAP BPM",
            tapSoundEnabled = settingsState.tapSoundEnabled,
            minHeight = 300.dp,
            artwork = track?.artwork,
            statsText = "Taps ${snapshot.tapCount}  |  ${snapshot.stabilityLabel}  |  ${String.format(Locale.US, "%.0f", snapshot.confidence * 100)}%",
            confidence = snapshot.confidence,
            smartHint = smartHint,
            onTapDown = viewModel::onTap
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconActionButton(
                label = "Reset",
                onClick = viewModel::resetTap,
                icon = Icons.Filled.Refresh,
                modifier = Modifier
                    .weight(1f)
            )
            PrimaryActionButton(
                label = if (uiState.isSaving) "Saving" else "Save BPM",
                onClick = viewModel::saveCurrent,
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier
                    .weight(1f),
                enabled = snapshot.bpm != null && !uiState.isSaving
            )
        }

        NowPlayingReferenceResultCard(
            isLookingUpPublicBpm = uiState.isLookingUpPublicBpm,
            publicStatusMessage = uiState.publicBpmStatusMessage,
            publicCandidates = uiState.publicBpmCandidates,
            localStatusMessage = uiState.localBpmStatusMessage,
            localMatches = uiState.localBpmMatches
        )

        IconActionButton(
            label = "File Tap",
            onClick = onFileAnalyze,
            icon = Icons.Filled.FolderOpen,
            modifier = Modifier.fillMaxWidth()
        )

        uiState.statusMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NowPlayingMediaCard(
    track: NowPlayingTrack?,
    fallbackTitle: String,
    hasNotificationAccess: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val artworkImage = remember(track?.artwork) { track?.artwork?.asImageBitmap() }
    val title = track?.title
        ?: fallbackTitle.ifBlank {
            if (hasNotificationAccess) "No active media" else "Notification access needed"
        }
    val subtitle = track?.artist
        ?: if (hasNotificationAccess) "Play music in another app" else "Enable access to read current media"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10191B)),
        border = BorderStroke(1.dp, Color(0x334DEDE3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E292B)
            ) {
                artworkImage?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF7FFFC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8EA09C),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            NowPlayingTransportControls(
                isPlaying = track?.isPlaying == true,
                enabled = track != null,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext
            )
        }
    }
}

@Composable
private fun NowPlayingTransportControls(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = enabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = if (enabled) Color(0xFF87F5EA) else Color(0x668EA09C)
            )
        }
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(50),
            color = if (enabled) Color(0x3312F5E6) else Color(0x22222222),
            border = BorderStroke(1.dp, Color(0x334DEDE3))
        ) {
            IconButton(
                onClick = onPlayPause,
                enabled = enabled,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (enabled) Color(0xFF87F5EA) else Color(0x668EA09C)
                )
            }
        }
        IconButton(
            onClick = onNext,
            enabled = enabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = if (enabled) Color(0xFF87F5EA) else Color(0x668EA09C)
            )
        }
    }
}

@Composable
private fun NowPlayingReferenceResultCard(
    isLookingUpPublicBpm: Boolean,
    publicStatusMessage: String?,
    publicCandidates: List<PublicBpmCandidate>,
    localStatusMessage: String?,
    localMatches: List<BpmRecord>
) {
    val publicCandidate = publicCandidates.firstOrNull()
    val localCandidate = localMatches.firstOrNull()
    val bpmText = publicCandidate?.let { String.format(Locale.US, "%.1f", it.bpm) }
        ?: localCandidate?.let { String.format(Locale.US, "%.1f", it.bpm) }
        ?: "--"
    val sourceText = publicCandidate?.source ?: localCandidate?.let { "Saved Library" }
    val detailText = publicCandidate?.let { "${String.format(Locale.US, "%.0f", it.matchScore * 100)}% match" }
        ?: localCandidate?.let {
            "${sourceTypeShortLabel(it.sourceType)} / ${String.format(Locale.US, "%.0f", it.confidence * 100)}%"
        }
    val sourceLine = sourceText ?: publicStatusMessage ?: localStatusMessage ?: "Searching references"
    val detailLine = detailText
    val sourceColor = if (publicCandidate != null) Color(0xFF007A6E) else Color(0xFF4E6D68)

    SectionCard(
        contentPadding = 12.dp
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reference BPM",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    detailLine?.let {
                        Text(
                            text = listOf(sourceLine, it).joinToString(" / "),
                            style = MaterialTheme.typography.bodySmall,
                            color = sourceColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } ?: Text(
                        text = sourceLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = sourceColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = bpmText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bpmText != "--") {
                        Text(
                            text = "BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLookingUpPublicBpm) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
    }
}
@Composable
fun FileAnalyzeScreen(
    onTapCorrect: () -> Unit,
    incomingAudioUri: Uri? = null,
    onIncomingAudioConsumed: () -> Unit = {},
    viewModel: FileAnalyzeViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    KeepScreenOn()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var pendingEstimateSave by remember { mutableStateOf<PendingEstimateSave?>(null) }
    val metadata = uiState.metadata
    val engineName = uiState.engineName
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectFile(uri, autoAnalyze = false)
        }
    }

    LaunchedEffect(incomingAudioUri) {
        if (incomingAudioUri != null) {
            viewModel.selectFile(incomingAudioUri, autoAnalyze = false)
            onIncomingAudioConsumed()
        }
    }
    val selectedCandidate = uiState.selectedCandidate ?: uiState.candidates.firstOrNull()
    val selectedSource = uiState.candidateSources.getOrNull(uiState.selectedCandidateIndex)
    val selectedReasonLabel = uiState.candidateReasonLabels.getOrNull(uiState.selectedCandidateIndex)
    val analysisRangeRisk = settingsState.analysisRangeRisk()
    DisposableEffect(Unit) {
        onDispose { viewModel.pausePreviewPlayback() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MeasureWorkbenchCard(
            metadata = metadata,
            hasFile = uiState.selectedUri != null,
            isReadingMetadata = uiState.isReadingMetadata,
            isAnalyzing = uiState.isAnalyzing,
            onPickFile = { pickerLauncher.launch(arrayOf("audio/*")) },
            onAnalyze = viewModel::analyzeSelectedFile
        )

        uiState.statusMessage?.let { message ->
            StatusLine(
                text = when {
                    uiState.analyzedSeconds > 0.0 && message == "Auto estimate ready" ->
                        "$message / ${String.format(Locale.US, "%.0f", uiState.analyzedSeconds)} sec"
                    else -> message
                }
            )
        }

        if (uiState.selectedUri != null) {
            PreviewTapBpmCard(
                snapshot = uiState.tapSnapshot,
                isPreparing = uiState.isPreviewPreparing,
                isPlaying = uiState.isPreviewPlaying,
                positionMs = uiState.previewPositionMs,
                durationMs = uiState.previewDurationMs,
                statusMessage = uiState.previewStatusMessage,
                tapSoundEnabled = settingsState.tapSoundEnabled,
                isSaving = uiState.isSaving,
                onPlayPause = viewModel::togglePreviewPlayback,
                onRestart = viewModel::restartPreviewPlayback,
                onTap = viewModel::onPreviewTap,
                onReset = viewModel::resetPreviewTap,
                onHalf = viewModel::halfPreviewTap,
                onDouble = viewModel::doublePreviewTap,
                onUse = viewModel::usePreviewTapCandidate,
                onSave = viewModel::savePreviewTapCandidate
            )
        }

        if (uiState.selectedUri != null) {
            SectionLabel("Estimates")
        }

        selectedCandidate?.let { candidate ->
            val candidateTrust = fileCandidateTrust(
                sourceType = selectedSource,
                candidate = candidate,
                agreementScore = uiState.agreementScore,
                segmentsAnalyzed = uiState.segmentsAnalyzed,
                engineWarnings = uiState.engineWarnings
            )
            RecommendedBpmPanel(
                title = selectedSource.recommendedPanelTitle(
                    selected = uiState.selectedCandidateIndex != 0,
                    trust = candidateTrust
                ),
                candidate = candidate,
                sourceLabel = selectedSource.measureSourceLabel(engineName),
                reasonLabel = selectedReasonLabel,
                detailText = selectedSource.measureDetailText(
                    engineName = engineName,
                    tempoFamily = uiState.tempoFamily,
                    agreementScore = uiState.agreementScore,
                    segmentsAnalyzed = uiState.segmentsAnalyzed,
                    analyzedSeconds = uiState.analyzedSeconds
                ),
                warningText = uiState.engineWarnings.takeIf { it.isNotEmpty() }?.joinToString("; "),
                advisoryText = selectedSource.measureAdvisoryText(candidateTrust),
                advisoryIsWarning = candidateTrust == FileCandidateTrust.NEEDS_VERIFICATION,
                confidenceLabel = selectedSource.confidenceDisplayLabel(candidateTrust, candidate),
                saveLabel = selectedSource.saveButtonLabel(),
                onSave = {
                    if (candidateTrust == FileCandidateTrust.TRUSTED_REFERENCE) {
                        viewModel.saveSelectedCandidate()
                    } else {
                        pendingEstimateSave = PendingEstimateSave(
                            candidate = candidate,
                            selectedIndex = uiState.selectedCandidateIndex
                        )
                    }
                },
                onHalf = viewModel::halfSelectedCandidate,
                onDouble = viewModel::doubleSelectedCandidate,
                onTapCorrect = onTapCorrect,
                isSaving = uiState.isSaving
            )
        }

        uiState.candidates.drop(1).takeIf { it.isNotEmpty() }?.let { alternates ->
            SectionLabel("Other BPM options")
            alternates.forEachIndexed { offset, candidate ->
                val index = offset + 1
                val sourceType = uiState.candidateSources.getOrNull(index)
                val candidateTrust = fileCandidateTrust(
                    sourceType = sourceType,
                    candidate = candidate,
                    agreementScore = uiState.agreementScore,
                    segmentsAnalyzed = uiState.segmentsAnalyzed,
                    engineWarnings = uiState.engineWarnings
                )
                CompactCandidateRow(
                    candidate = candidate,
                    selected = index == uiState.selectedCandidateIndex,
                    label = sourceType.measureSourceLabel(engineName),
                    reasonLabel = uiState.candidateReasonLabels.getOrNull(index),
                    confidenceLabel = sourceType.confidenceDisplayLabel(candidateTrust, candidate),
                    onSelect = { viewModel.selectCandidate(index) }
                )
            }
        }

        if (uiState.selectedUri != null) {
            PublicBpmLookupCard(
                metadata = metadata,
                isLookingUp = uiState.isLookingUpPublicBpm,
                statusMessage = uiState.publicBpmStatusMessage,
                candidates = uiState.publicBpmCandidates,
                onLookup = viewModel::lookupPublicBpm,
                onUse = viewModel::usePublicBpm,
                onOpenWebSearch = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(publicBpmSearchUrl(metadata))
                        )
                    )
                },
                onOpenSource = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            )

            WebBpmAnswerCard(
                text = uiState.webReferenceText,
                parsedCandidate = uiState.parsedWebBpm,
                statusMessage = uiState.webReferenceStatusMessage,
                isSaving = uiState.isSaving,
                hasFile = uiState.selectedUri != null,
                onTextChange = viewModel::onWebReferenceTextChange,
                onUse = viewModel::useWebReferenceBpm,
                onSave = viewModel::saveWebReferenceBpm
            )
        }

        if (
            uiState.selectedUri != null &&
            !uiState.isAnalyzing &&
            uiState.statusMessage == "No usable auto estimate"
        ) {
            AnalysisIssueCard(
                title = "Auto estimate unavailable",
                body = "The file rhythm is not clear enough for automatic BPM. Play it here and save a tapped BPM instead."
            )
        }

        analysisRangeRisk?.let { risk ->
            AnalysisRangeWarningCard(
                minBpm = settingsState.minBpm,
                maxBpm = settingsState.maxBpm,
                risk = risk,
                onReset = settingsViewModel::resetAnalysisDefaults
            )
        }
    }

    pendingEstimateSave?.let { pending ->
        SaveEstimateConfirmationDialog(
            candidate = pending.candidate,
            onDismiss = { pendingEstimateSave = null },
            onConfirm = {
                viewModel.selectCandidate(pending.selectedIndex)
                viewModel.saveSelectedCandidate()
                pendingEstimateSave = null
            }
        )
    }
}

private data class PendingEstimateSave(
    val candidate: BpmCandidate,
    val selectedIndex: Int
)

@Composable
private fun SaveEstimateConfirmationDialog(
    candidate: BpmCandidate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save draft?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = String.format(Locale.US, "%.1f BPM", candidate.bpm),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "This is an automatic BPM candidate. It will be saved as a draft until you tap-check it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "For the most trusted value, use Play + Tap BPM before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Save draft")
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
private fun AnalysisRangeWarningCard(
    minBpm: Int,
    maxBpm: Int,
    risk: AnalysisRangeRisk,
    onReset: () -> Unit
) {
    val body = when (risk) {
        AnalysisRangeRisk.FAST_ONLY ->
            "Current range is $minBpm - $maxBpm BPM, so file analysis can be forced toward very fast results."
        AnalysisRangeRisk.TOO_NARROW ->
            "Current range is only $minBpm - $maxBpm BPM, so the real tempo may be filtered out."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Analysis range may be biased",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "$body Reset to 60 - 200 BPM if results feel too fast or unreliable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "Reset Estimate Range",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun MeasureWorkbenchCard(
    metadata: AudioFileMetadata?,
    hasFile: Boolean,
    isReadingMetadata: Boolean,
    isAnalyzing: Boolean,
    onPickFile: () -> Unit,
    onAnalyze: () -> Unit
) {
    SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(text = "FILE TAP", selected = hasFile)
                StatusChip(
                    text = when {
                        isAnalyzing -> "Analyzing"
                        isReadingMetadata -> "Reading"
                        hasFile -> "Ready to tap"
                        else -> "No file"
                    }
                )
            }

            Text(
                text = metadata?.title ?: metadata?.displayName ?: "Choose a file and tap BPM",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metadata?.artist ?: metadata?.album ?: "Listen here, tap the beat, then save a verified BPM.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isAnalyzing || isReadingMetadata) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }

            if (hasFile) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconActionButton(
                        label = "Change file",
                        onClick = onPickFile,
                        icon = Icons.Filled.FolderOpen,
                        modifier = Modifier.weight(1f)
                    )
                    IconActionButton(
                        label = if (isAnalyzing) "Estimating" else "Estimate BPM",
                        onClick = onAnalyze,
                        icon = Icons.Filled.GraphicEq,
                        modifier = Modifier.weight(1f),
                        enabled = !isAnalyzing && !isReadingMetadata
                    )
                }
            } else {
                PrimaryActionButton(
                    label = "Choose audio file",
                    onClick = onPickFile,
                    icon = Icons.Filled.FolderOpen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
    }
}

@Composable
private fun PreviewTapBpmCard(
    snapshot: TapBpmSnapshot,
    isPreparing: Boolean,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    statusMessage: String?,
    tapSoundEnabled: Boolean,
    isSaving: Boolean,
    onPlayPause: () -> Unit,
    onRestart: () -> Unit,
    onTap: (Long) -> Unit,
    onReset: () -> Unit,
    onHalf: () -> Unit,
    onDouble: () -> Unit,
    onUse: () -> Unit,
    onSave: () -> Unit
) {
    SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Play + Tap BPM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${formatPreviewTime(positionMs)} / ${formatPreviewTime(durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = statusMessage ?: "Ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPreparing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryActionButton(
                    label = if (isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing
                )
                IconActionButton(
                    label = "Restart",
                    onClick = onRestart,
                    icon = Icons.Filled.Refresh,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing
                )
            }

            LowLatencyTapPad(
                bpmText = snapshot.bpm?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                label = "TAP BPM",
                tapSoundEnabled = tapSoundEnabled,
                minHeight = 228.dp,
                onTapDown = onTap
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactMetric(
                    label = "Taps",
                    value = snapshot.tapCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                CompactMetric(
                    label = "Stability",
                    value = snapshot.stabilityLabel,
                    modifier = Modifier.weight(1f)
                )
                CompactMetric(
                    label = "Confidence",
                    value = "${String.format(Locale.US, "%.0f", snapshot.confidence * 100)}%",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconActionButton(
                    label = "Reset",
                    onClick = onReset,
                    icon = Icons.Filled.Refresh,
                    modifier = Modifier.weight(1f)
                )
                PrimaryActionButton(
                    label = if (isSaving) "Saving" else "Save verified",
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = snapshot.bpm != null && !isSaving
                )
            }
    }
}

@Composable
private fun CompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AnalysisIssueCard(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendedBpmPanel(
    title: String,
    candidate: BpmCandidate,
    sourceLabel: String,
    reasonLabel: String?,
    detailText: String,
    warningText: String?,
    advisoryText: String?,
    advisoryIsWarning: Boolean,
    confidenceLabel: String,
    saveLabel: String,
    onSave: () -> Unit,
    onHalf: () -> Unit,
    onDouble: () -> Unit,
    onTapCorrect: () -> Unit,
    isSaving: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = listOfNotNull(sourceLabel, reasonLabel).joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = confidenceLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = String.format(Locale.US, "%.1f BPM", candidate.bpm),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 38.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            advisoryText?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (advisoryIsWarning) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    }
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (advisoryIsWarning) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Saving" else saveLabel)
            }

            Text(
                text = detailText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            warningText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CompactCandidateRow(
    candidate: BpmCandidate,
    selected: Boolean,
    label: String,
    reasonLabel: String?,
    confidenceLabel: String,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format(Locale.US, "%.1f BPM", candidate.bpm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = listOfNotNull(label, reasonLabel).joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = confidenceLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium
    )
}

private fun BpmSourceType?.recommendedPanelTitle(
    selected: Boolean,
    trust: FileCandidateTrust
): String {
    if (selected) {
        return if (this == BpmSourceType.FILE_ANALYSIS || this == null) {
            "Selected estimate"
        } else {
            "Selected BPM"
        }
    }
    return when (this) {
        BpmSourceType.FILE_ANALYSIS, null -> when (trust) {
            FileCandidateTrust.NEEDS_VERIFICATION -> "Tap to confirm"
            FileCandidateTrust.AUTO_ESTIMATE -> "Auto estimate"
            FileCandidateTrust.TRUSTED_REFERENCE -> "Suggested BPM"
        }
        BpmSourceType.PUBLIC_REFERENCE -> "Reference BPM"
        BpmSourceType.TAP -> "Tapped BPM"
        BpmSourceType.NOW_PLAYING -> "Saved BPM"
        BpmSourceType.PLAYBACK_CAPTURE,
        BpmSourceType.MIC_CAPTURE -> "Live estimate"
    }
}

private fun BpmSourceType?.measureSourceLabel(engineName: String?): String {
    return when (this) {
        BpmSourceType.FILE_ANALYSIS -> "Auto estimate"
        BpmSourceType.PUBLIC_REFERENCE -> "Reference BPM"
        BpmSourceType.TAP -> "Tapped BPM"
        BpmSourceType.NOW_PLAYING -> "Saved media BPM"
        BpmSourceType.PLAYBACK_CAPTURE -> "Experimental capture"
        BpmSourceType.MIC_CAPTURE -> "Mic listen"
        null -> "Auto estimate"
    }
}

private fun BpmSourceType?.measureAdvisoryText(trust: FileCandidateTrust): String? {
    return when (trust) {
        FileCandidateTrust.TRUSTED_REFERENCE -> null
        FileCandidateTrust.AUTO_ESTIMATE -> when (this) {
            BpmSourceType.FILE_ANALYSIS, null ->
                "Likely file tempo. Play + Tap BPM is still the safest save."
            else -> null
        }
        FileCandidateTrust.NEEDS_VERIFICATION -> when (this) {
            BpmSourceType.PLAYBACK_CAPTURE,
            BpmSourceType.MIC_CAPTURE ->
                "Experimental estimate. Tap-check before saving."
            else ->
                "File rhythm is unclear. Use Play + Tap BPM or a reference BPM."
        }
    }
}

private fun BpmSourceType?.saveButtonLabel(): String {
    return when (this) {
        BpmSourceType.FILE_ANALYSIS, null -> "Save Draft"
        BpmSourceType.PUBLIC_REFERENCE -> "Save Reference"
        BpmSourceType.TAP -> "Save Tap"
        else -> "Save BPM"
    }
}

private fun BpmSourceType?.confidenceDisplayLabel(
    trust: FileCandidateTrust,
    candidate: BpmCandidate
): String {
    return when (this) {
        BpmSourceType.FILE_ANALYSIS, null -> when (trust) {
            FileCandidateTrust.NEEDS_VERIFICATION -> "Check"
            FileCandidateTrust.AUTO_ESTIMATE,
            FileCandidateTrust.TRUSTED_REFERENCE -> "Estimate"
        }
        else -> "${String.format(Locale.US, "%.0f", candidate.confidence * 100)}%"
    }
}

private fun sourceTypeShortLabel(sourceType: BpmSourceType): String {
    return when (sourceType) {
        BpmSourceType.TAP -> "Tap"
        BpmSourceType.NOW_PLAYING -> "Media"
        BpmSourceType.FILE_ANALYSIS -> "File"
        BpmSourceType.PUBLIC_REFERENCE -> "Public"
        BpmSourceType.PLAYBACK_CAPTURE -> "Capture"
        BpmSourceType.MIC_CAPTURE -> "Mic"
    }
}

private fun Double.smartTempoHint(): String {
    val category = BpmSmartCategory.fromBpm(this)
    val doubleTime = doubleTimeCompatibleBpm()
    val halfTime = halfTimeFeelBpm()
    return listOfNotNull(
        category.label,
        doubleTime?.let { "Double-time ${String.format(Locale.US, "%.1f", it)} BPM" },
        halfTime?.let { "Half-time feel ${String.format(Locale.US, "%.1f", it)} BPM" }
    ).joinToString(" / ")
}

private fun BpmSourceType?.measureDetailText(
    engineName: String?,
    tempoFamily: String?,
    agreementScore: Double,
    segmentsAnalyzed: Int,
    analyzedSeconds: Double
): String {
    if (this == BpmSourceType.PUBLIC_REFERENCE) {
        return "Reference BPM selected. Save it here, or use your tapped BPM if the groove feels different."
    }
    if (this == BpmSourceType.TAP) {
        return "Tapped BPM selected. Save it as a verified value."
    }
    if (this == BpmSourceType.NOW_PLAYING) {
        return "Saved Library BPM matched by title and artist."
    }
    val parts = listOfNotNull(
        engineName?.shortEngineName(),
        tempoFamily?.let { "Family ${it.replace(" / ", "/")}" },
        agreementScore.takeIf { it > 0.0 }?.let {
            "Agreement ${String.format(Locale.US, "%.0f", it * 100)}%"
        },
        segmentsAnalyzed.takeIf { it > 0 }?.let { "$it windows" },
        analyzedSeconds.takeIf { it > 0.0 }?.let {
            "${String.format(Locale.US, "%.0f", it)} sec"
        }
    )
    return parts.ifEmpty { listOf(engineName ?: "Ready") }.joinToString(" / ")
}

private fun String.shortEngineName(): String {
    return when (this) {
        "Native tempo + Kotlin fallback" -> "Native + Kotlin"
        "Kotlin fallback" -> "Kotlin"
        "Native tempo" -> "Native"
        else -> this
    }
}

@Composable
private fun CandidateRow(
    candidate: BpmCandidate,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format(Locale.US, "%.1f BPM", candidate.bpm),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Confidence ${String.format(Locale.US, "%.0f", candidate.confidence * 100)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PublicBpmLookupCard(
    metadata: AudioFileMetadata?,
    isLookingUp: Boolean,
    statusMessage: String?,
    candidates: List<PublicBpmCandidate>,
    onLookup: () -> Unit,
    onUse: (Int) -> Unit,
    onOpenWebSearch: () -> Unit,
    onOpenSource: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reference BPM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Optional title/artist match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLookup,
                    modifier = Modifier.weight(1f),
                    enabled = metadata != null && !isLookingUp
                ) {
                    Text(if (isLookingUp) "Searching" else "Check")
                }
                OutlinedButton(
                    onClick = onOpenWebSearch,
                    modifier = Modifier.weight(1f),
                    enabled = metadata != null
                ) {
                    Text("Search Web")
                }
            }

            candidates.forEachIndexed { index, candidate ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", candidate.bpm),
                        modifier = Modifier.width(64.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = listOfNotNull(candidate.title, candidate.artist).joinToString(" - "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${candidate.source} / ${String.format(Locale.US, "%.0f", candidate.matchScore * 100)}% match",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalButton(onClick = { onUse(index) }) {
                        Text("Use")
                    }
                    OutlinedButton(onClick = { onOpenSource(candidate.sourceUrl) }) {
                        Text("Source")
                    }
                }
            }
        }
    }
}

@Composable
private fun WebBpmAnswerCard(
    text: String,
    parsedCandidate: BpmCandidate?,
    statusMessage: String?,
    isSaving: Boolean,
    hasFile: Boolean,
    onTextChange: (String) -> Unit,
    onUse: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Paste BPM text",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Paste a web answer, search snippet, or BPM page text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("BPM answer text") },
                minLines = 2,
                maxLines = 4
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parsedCandidate?.let {
                            String.format(Locale.US, "%.1f BPM", it.bpm)
                        } ?: "-- BPM",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statusMessage ?: "Waiting for pasted text",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                parsedCandidate?.let {
                    Text(
                        text = "${String.format(Locale.US, "%.0f", it.confidence * 100)}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onUse,
                    modifier = Modifier.weight(1f),
                    enabled = parsedCandidate != null
                ) {
                    Text("Use Web")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = hasFile && parsedCandidate != null && !isSaving
                ) {
                    Text(if (isSaving) "Saving" else "Save Web")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    captureViewModel: PlaybackCaptureViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captureState by captureViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var hasNotificationAccess by remember {
        mutableStateOf(MediaSessionReader(context).hasNotificationListenerAccess())
    }
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            captureViewModel.startCapture(result.resultCode, data)
        }
    }
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordAudioPermission = granted
        if (granted) {
            projectionLauncher.launch(captureViewModel.createCaptureIntent())
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordAudioPermission = granted
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = MediaSessionReader(context).hasNotificationListenerAccess()
                hasRecordAudioPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun startExperimentalCapture() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            projectionLauncher.launch(captureViewModel.createCaptureIntent())
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FirstRunGuideCard()

        SettingsSectionTitle("Permissions")
        PermissionStatusCard(
            title = "Current media access",
            granted = hasNotificationAccess,
            status = if (hasNotificationAccess) "Ready" else "Needs access",
            body = if (hasNotificationAccess) {
                "Measure can show the current song and save Tap or Reference BPM to that title."
            } else {
                "Enable notification listener access to read title, artist, artwork, and player controls from Samsung Music or other media apps."
            },
            actionLabel = if (hasNotificationAccess) null else "Open Access",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        PermissionStatusCard(
            title = "Microphone listen",
            granted = hasRecordAudioPermission,
            status = if (hasRecordAudioPermission) "Ready" else "Optional",
            body = if (hasRecordAudioPermission) {
                "Mic Listen can be used as an experimental fallback with speaker playback."
            } else {
                "Optional fallback. It cannot hear headphone-only playback and is not required for Tap BPM."
            },
            actionLabel = if (hasRecordAudioPermission) null else "Grant Mic",
            onAction = {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        SettingsSectionTitle("Tap & Measure")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tap feedback sound",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Short click tone on each tap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settingsState.tapSoundEnabled,
                    onCheckedChange = settingsViewModel::setTapSoundEnabled
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Auto estimate BPM range",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${settingsState.minBpm} - ${settingsState.maxBpm} BPM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RangeSlider(
                    value = settingsState.minBpm.toFloat()..settingsState.maxBpm.toFloat(),
                    onValueChange = {
                        settingsViewModel.setBpmRange(
                            minBpm = it.start.roundToInt(),
                            maxBpm = it.endInclusive.roundToInt()
                        )
                    },
                    valueRange = 40f..240f
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Auto estimate segment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${settingsState.analysisSeconds} seconds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settingsState.analysisSeconds.toFloat(),
                    onValueChange = { settingsViewModel.setAnalysisSeconds(it.roundToInt()) },
                    valueRange = 30f..120f,
                    steps = 5
                )
            }
        }

        OutlinedButton(
            onClick = settingsViewModel::resetAnalysisDefaults,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text("Reset Estimates")
        }

        SettingsSectionTitle("Advanced")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Experimental capture",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Optional. Many music apps block this; Tap BPM remains the main flow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settingsState.experimentalCaptureEnabled,
                    onCheckedChange = {
                        settingsViewModel.setExperimentalCaptureEnabled(it)
                        if (!it && captureState.isCapturing) {
                            captureViewModel.stopCapture()
                        }
                    }
                )
            }
        }

        if (settingsState.experimentalCaptureEnabled || captureState.isCapturing || captureState.candidates.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Capture control",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = captureState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (captureState.capturedSeconds > 0.0) {
                        Text(
                            text = "${String.format(Locale.US, "%.0f", captureState.capturedSeconds)} seconds captured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            if (captureState.isCapturing) {
                                captureViewModel.stopCapture()
                            } else {
                                startExperimentalCapture()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = settingsState.experimentalCaptureEnabled
                    ) {
                        Text(if (captureState.isCapturing) "Stop Capture" else "Start Capture")
                    }
                    captureState.candidates.forEachIndexed { index, candidate ->
                        CandidateRow(
                            candidate = candidate,
                            selected = index == captureState.selectedCandidateIndex,
                            onSelect = { captureViewModel.selectCandidate(index) }
                        )
                    }
                    Button(
                        onClick = captureViewModel::saveSelectedCandidate,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = captureState.selectedCandidate != null && !captureState.isSaving
                    ) {
                        Text(if (captureState.isSaving) "Saving" else "Save Capture BPM")
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstRunGuideCard() {
    SectionCard {
            Text(
                text = "Recommended flow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            CompactInfoRow(label = "1", value = "Play music")
            CompactInfoRow(label = "2", value = "Tap BPM")
            CompactInfoRow(label = "3", value = "Save")
            CompactInfoRow(label = "4", value = "Sort by workout range")
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    granted: Boolean,
    status: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            1.dp,
            if (granted) colors.primary.copy(alpha = 0.36f) else colors.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (granted) colors.primaryContainer else colors.surfaceVariant,
                    contentColor = if (granted) colors.onPrimaryContainer else colors.onSurfaceVariant
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactMetadataCard(
    title: String,
    subtitle: String,
    rows: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(0.28f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = value,
                        modifier = Modifier.weight(0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatPreviewTime(valueMs: Int): String {
    val totalSeconds = (valueMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun nowPlayingBpmSearchUrl(track: NowPlayingTrack?, fallbackTitle: String): String {
    val query = listOfNotNull(
        track?.artist,
        track?.title ?: fallbackTitle.ifBlank { null },
        "BPM"
    )
        .joinToString(" ")
        .ifBlank { "song BPM" }
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    return "https://www.google.com/search?q=$encoded"
}

private fun publicBpmSearchUrl(metadata: AudioFileMetadata?): String {
    val query = listOfNotNull(
        metadata?.artist,
        metadata?.title ?: metadata?.displayName?.substringBeforeLast('.'),
        "BPM"
    )
        .joinToString(" ")
        .ifBlank { "song BPM" }
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    return "https://www.google.com/search?q=$encoded"
}

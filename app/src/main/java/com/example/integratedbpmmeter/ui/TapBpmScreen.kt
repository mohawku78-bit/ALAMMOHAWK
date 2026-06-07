package com.example.integratedbpmmeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.integratedbpmmeter.viewmodel.SettingsViewModel
import com.example.integratedbpmmeter.viewmodel.TapBpmViewModel
import java.util.Locale

@Composable
fun TapBpmScreen(
    viewModel: TapBpmViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    KeepScreenOn()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = uiState.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LowLatencyTapPad(
            bpmText = snapshot.bpm?.formatBpm() ?: "--",
            label = "TAP",
            tapSoundEnabled = settingsState.tapSoundEnabled,
            onTapDown = viewModel::onTap
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = "Taps",
                value = snapshot.tapCount.toString(),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Interval",
                value = snapshot.intervalMs?.let { "${it.formatOne()} ms" } ?: "--",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                label = "Stability",
                value = snapshot.stabilityLabel,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Confidence",
                value = "${(snapshot.confidence * 100).formatOne()}%",
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = uiState.titleInput,
            onValueChange = viewModel::onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Track or note") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::reset,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset")
            }
            FilledTonalButton(
                onClick = viewModel::half,
                modifier = Modifier.weight(1f),
                enabled = snapshot.bpm != null
            ) {
                Text("Half")
            }
            FilledTonalButton(
                onClick = viewModel::double,
                modifier = Modifier.weight(1f),
                enabled = snapshot.bpm != null
            ) {
                Text("Double")
            }
        }

        Button(
            onClick = viewModel::saveCurrent,
            modifier = Modifier.fillMaxWidth(),
            enabled = snapshot.bpm != null && !uiState.isSaving
        ) {
            Text(if (uiState.isSaving) "Saving" else "Save BPM")
        }

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
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun Double.formatBpm(): String = String.format(Locale.US, "%.1f", this)

private fun Double.formatOne(): String = String.format(Locale.US, "%.1f", this)

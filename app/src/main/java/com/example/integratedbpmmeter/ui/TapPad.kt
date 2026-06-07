package com.example.integratedbpmmeter.ui

import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun LowLatencyTapPad(
    bpmText: String,
    label: String,
    tapSoundEnabled: Boolean,
    modifier: Modifier = Modifier,
    minHeight: Dp = 286.dp,
    headerTitle: String? = null,
    headerSubtitle: String? = null,
    headerTrailingContent: (@Composable () -> Unit)? = null,
    artwork: Bitmap? = null,
    statsText: String? = null,
    confidence: Double = 0.0,
    smartHint: String? = null,
    onTapDown: (Long) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val soundPlayer = rememberTapSoundPlayer(tapSoundEnabled)
    val latestOnTapDown = rememberUpdatedState(onTapDown)

    val hasHeader = !headerTitle.isNullOrBlank() || !headerSubtitle.isNullOrBlank()
    val shape = RoundedCornerShape(8.dp)
    val artworkImage = remember(artwork) { artwork?.asImageBitmap() }
    val hasArtwork = artworkImage != null
    val foregroundShape = RoundedCornerShape(8.dp)
    val textShadow = remember {
        Shadow(
            color = Color(0x99000000),
            offset = Offset(0f, 2f),
            blurRadius = 8f
        )
    }

    val tapTargetModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val timestamp = SystemClock.elapsedRealtimeNanos()
            latestOnTapDown.value(timestamp)
            soundPlayer()
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(minHeight)
            .then(if (headerTrailingContent == null) tapTargetModifier else Modifier),
        shape = shape,
        color = Color(0xFF101C19),
        border = BorderStroke(1.dp, Color(0xFF2F4E47)),
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .clip(shape)
        ) {
            artworkImage?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.04f
                            scaleY = 1.04f
                        }
                        .blur(6.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (hasArtwork) Color(0x99000000) else Color(0xDD071512),
                                if (hasArtwork) Color(0x4D000000) else Color(0xE80A1815),
                                if (hasArtwork) Color(0xB8000000) else Color(0xF40D1C18)
                            )
                        )
                    )
            )
            if (hasArtwork && !hasHeader) {
                ArtworkGaugeTapPadContent(
                    bpmText = bpmText,
                    label = label,
                    statsText = statsText,
                    smartHint = smartHint,
                    confidence = confidence,
                    textShadow = textShadow,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (hasHeader) Arrangement.SpaceBetween else Arrangement.Center
            ) {
            if (hasHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(foregroundShape)
                        .background(if (hasArtwork) Color(0x88071612) else Color.Transparent)
                        .padding(if (hasArtwork) 12.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (headerTrailingContent != null) tapTargetModifier else Modifier),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        headerTitle?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleLarge.copy(shadow = textShadow),
                                color = Color(0xFFF6FFFB),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        headerSubtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                                color = Color(0xFFA8BDB7),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    headerTrailingContent?.invoke()
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(foregroundShape)
                    .background(if (hasArtwork && hasHeader) Color(0x86071310) else Color.Transparent)
                    .padding(
                        horizontal = if (hasArtwork && hasHeader) 14.dp else 0.dp,
                        vertical = if (hasArtwork && hasHeader) 26.dp else 0.dp
                    )
                    .then(if (headerTrailingContent != null) tapTargetModifier else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = bpmText,
                    style = MaterialTheme.typography.displayLarge.copy(shadow = textShadow),
                    fontSize = 62.sp,
                    lineHeight = 66.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF6FFFB),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.labelLarge.copy(shadow = textShadow),
                    color = Color(0xFF88E5D6)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = label,
                    fontSize = 23.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE8F8F3),
                    style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow),
                    textAlign = TextAlign.Center
                )
            }
            }
            }
        }
    }
}

@Composable
private fun ArtworkGaugeTapPadContent(
    bpmText: String,
    label: String,
    statsText: String?,
    smartHint: String?,
    confidence: Double,
    textShadow: Shadow,
    modifier: Modifier = Modifier
) {
    val progress = confidence.coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        TempoGauge(
            progress = progress,
            modifier = Modifier
                .align(Alignment.Center)
                .size(222.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "DETECTED BPM",
                    style = MaterialTheme.typography.labelLarge.copy(shadow = textShadow),
                    color = Color(0xFF76F4E8),
                    letterSpacing = 0.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                BpmDisplayText(
                    bpmText = bpmText,
                    textShadow = textShadow
                )
                Text(
                    text = "BPM",
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8AF5EA),
                    style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow)
                )
                smartHint?.let {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                        color = Color(0xFFE8F8F3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0x52152426),
                    border = BorderStroke(1.dp, Color(0xFF79F4EA))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                            color = Color(0xFF8AF5EA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Filled.TouchApp,
                            contentDescription = null,
                            tint = Color(0xFF8AF5EA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        statsText?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = it,
                    modifier = Modifier.weight(0.44f),
                    style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                    color = Color(0xFFDBECE8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .weight(0.56f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x3FFFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceAtLeast(0.02f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF62F5EA))
                    )
                }
            }
        }
    }
}

@Composable
private fun TempoGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickCount = 78
            val radius = size.minDimension / 2f
            val longTick = 17.dp.toPx()
            val shortTick = 11.dp.toPx()
            val stroke = 2.dp.toPx()
            val startAngle = 138.0
            val sweepAngle = 264.0
            val activeStart = (tickCount * 0.58f).roundToInt()
            val activeEnd = activeStart + (tickCount * 0.28f * progress).roundToInt()

            repeat(tickCount) { index ->
                val angle = Math.toRadians(startAngle + sweepAngle * index / (tickCount - 1))
                val tickLength = if (index % 5 == 0) longTick else shortTick
                val outer = Offset(
                    x = center.x + cos(angle).toFloat() * radius,
                    y = center.y + sin(angle).toFloat() * radius
                )
                val inner = Offset(
                    x = center.x + cos(angle).toFloat() * (radius - tickLength),
                    y = center.y + sin(angle).toFloat() * (radius - tickLength)
                )
                val active = index in activeStart..activeEnd
                drawLine(
                    color = if (active) Color(0xFF66F5EA) else Color(0x66FFFFFF),
                    start = inner,
                    end = outer,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
        content()
    }
}

@Composable
private fun BpmDisplayText(
    bpmText: String,
    textShadow: Shadow
) {
    if (bpmText == "--") {
        Text(
            text = "--",
                    fontSize = 66.sp,
                    lineHeight = 70.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF7FFFC),
            style = MaterialTheme.typography.displayLarge.copy(shadow = textShadow),
            textAlign = TextAlign.Center
        )
        return
    }

    val intPart = bpmText.substringBefore('.')
    val decimal = bpmText.substringAfter('.', missingDelimiterValue = "")
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = intPart,
            fontSize = 66.sp,
            lineHeight = 70.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF7FFFC),
            style = MaterialTheme.typography.displayLarge.copy(shadow = textShadow)
        )
        if (decimal.isNotBlank()) {
            Text(
                text = ".$decimal",
                fontSize = 34.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF7FFFC),
                style = MaterialTheme.typography.displayLarge.copy(shadow = textShadow),
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
    }
}

@Composable
private fun rememberTapSoundPlayer(enabled: Boolean): () -> Unit {
    val latestEnabled = rememberUpdatedState(enabled)
    val player = remember { TapSoundPlayer() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return remember {
        {
            if (latestEnabled.value) {
                player.play()
            }
        }
    }
}

private class TapSoundPlayer {
    private val toneGenerator = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    }.getOrNull()

    fun play() {
        runCatching {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 35)
        }
    }

    fun release() {
        runCatching {
            toneGenerator?.release()
        }
    }
}

package com.example.integratedbpmmeter.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection

class PlaybackCaptureManager(
    private val estimator: BpmEstimator = BpmEstimator()
) {
    @SuppressLint("MissingPermission")
    fun createAudioRecord(mediaProjection: MediaProjection): AudioRecord {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE / 2)

        return AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferBytes * 4)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()
    }

    fun estimate(samples: FloatArray): List<BpmCandidate> {
        return normalizeRealtimeTempoCandidates(estimator.estimate(samples, SAMPLE_RATE))
    }

    companion object {
        const val SAMPLE_RATE = 44_100
    }
}

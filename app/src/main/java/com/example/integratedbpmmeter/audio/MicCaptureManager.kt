package com.example.integratedbpmmeter.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class MicCaptureManager(
    private val estimator: BpmEstimator = BpmEstimator()
) {
    fun createAudioRecord(): AudioRecord {
        val sources = listOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        sources.forEach { source ->
            val record = runCatching { buildRecord(source) }.getOrNull()
            if (record?.state == AudioRecord.STATE_INITIALIZED) {
                return record
            }
            record?.release()
        }

        return buildRecord(MediaRecorder.AudioSource.MIC)
    }

    @SuppressLint("MissingPermission")
    private fun buildRecord(audioSource: Int): AudioRecord {
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
            .setAudioSource(audioSource)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferBytes * 4)
            .build()
    }

    fun estimate(samples: FloatArray): List<BpmCandidate> {
        return normalizeRealtimeTempoCandidates(estimator.estimate(samples, SAMPLE_RATE))
    }

    companion object {
        const val SAMPLE_RATE = 44_100
    }
}

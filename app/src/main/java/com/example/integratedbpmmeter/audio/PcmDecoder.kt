package com.example.integratedbpmmeter.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

data class PcmAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val durationUs: Long?
)

class PcmDecoder(private val context: Context) {
    fun decodeMono(
        uri: Uri,
        startUs: Long,
        maxDurationUs: Long
    ): PcmAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = findAudioTrack(extractor)
            require(trackIndex >= 0) { "No audio track found" }

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Missing audio mime type")
            val sourceDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }
            } else {
                null
            }

            extractor.selectTrack(trackIndex)
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val output = FloatArrayBuilder()
            val bufferInfo = MediaCodec.BufferInfo()
            var outputFormat = codec.outputFormat
            var sawInputEnd = false
            var sawOutputEnd = false
            val endUs = startUs + maxDurationUs

            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: error("Missing codec input buffer")
                        inputBuffer.clear()

                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || sampleTimeUs < 0 || sampleTimeUs > endUs) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEnd = true
                        }

                        if (bufferInfo.size > 0 && bufferInfo.presentationTimeUs <= endUs) {
                            val buffer = codec.getOutputBuffer(outputIndex)
                                ?: error("Missing codec output buffer")
                            buffer.position(bufferInfo.offset)
                            buffer.limit(bufferInfo.offset + bufferInfo.size)
                            appendMonoSamples(buffer.slice(), outputFormat, output)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val sampleRate = if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }

            return PcmAudio(
                samples = output.toFloatArray(),
                sampleRate = sampleRate,
                durationUs = sourceDurationUs
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return -1
    }

    private fun appendMonoSamples(
        buffer: ByteBuffer,
        format: MediaFormat,
        output: FloatArrayBuilder
    ) {
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        } else {
            1
        }
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }

        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_32BIT -> 4
            else -> 2
        }
        val bytesPerFrame = bytesPerSample * channels

        while (buffer.remaining() >= bytesPerFrame) {
            var mono = 0.0f
            repeat(channels) {
                mono += when (encoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> buffer.getFloat()
                    AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xFF) - 128) / 128f
                    AudioFormat.ENCODING_PCM_32BIT -> buffer.getInt() / Int.MAX_VALUE.toFloat()
                    else -> buffer.getShort() / Short.MAX_VALUE.toFloat()
                }
            }
            output.add((mono / channels).coerceIn(-1.0f, 1.0f))
        }
    }

    private class FloatArrayBuilder {
        private var values = FloatArray(16_384)
        private var size = 0

        fun add(value: Float) {
            if (size == values.size) {
                values = values.copyOf(max(values.size * 2, values.size + 1))
            }
            values[size] = value
            size++
        }

        fun toFloatArray(): FloatArray = values.copyOf(size)
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
    }
}

package com.echox.app.domain

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecordingAssets(
        val audioFile: File,
        val videoFile: File,
        val durationMs: Long,
        val amplitudesFile: File
)

class RecordingPipeline(private val context: Context) {

        private val videoComposer = VideoCompositionManager(context)
        private val frameRenderer = ProfileFrameRenderer(context)

        suspend fun preparePreview(
                rawPcmFile: File,
                durationMs: Long,
                avatarUrl: String?,
                amplitudes: List<Float>
        ): RecordingAssets {
                val wavFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
                val videoFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.mp4")
                val frameFile = File(context.cacheDir, "frame_${System.currentTimeMillis()}.png")
                val amplitudesFile =
                        File(context.cacheDir, "amplitudes_${System.currentTimeMillis()}.txt")
                frameFile.parentFile?.mkdirs()

                val durationLabel = formatDuration(durationMs)

                val audioResult =
                        AudioFileUtils.convertPcmToWav(
                                pcmFile = rawPcmFile,
                                wavFile = wavFile,
                                sampleRate = AudioRecorderManager.SAMPLE_RATE,
                                channels = AudioRecorderManager.CHANNELS,
                                bitsPerSample = AudioRecorderManager.BITS_PER_SAMPLE
                        )

                frameRenderer.renderFrame(
                        avatarUrl = avatarUrl,
                        durationLabel = durationLabel,
                        chunkLabel = "1/1",
                        amplitudes = amplitudes,
                        outputFile = frameFile
                )

                amplitudesFile.writeText(amplitudes.joinToString(","))

                videoComposer.generateVideo(
                        imageUri = frameFile.toUri(),
                        audioUri = audioResult.toUri(),
                        outputFile = videoFile,
                        durationMs = durationMs,
                        chunkLabel = "1/1"
                )

                return RecordingAssets(
                        audioFile = audioResult,
                        videoFile = videoFile,
                        durationMs = durationMs,
                        amplitudesFile = amplitudesFile
                )
        }

        suspend fun renderSegment(
                wavFile: File,
                avatarUrl: String?,
                startMs: Long,
                segmentDurationMs: Long,
                chunkLabel: String,
                amplitudes: List<Float>,
                totalDurationMs: Long
        ): File {
                val frameFile = File(context.cacheDir, "frame_${System.currentTimeMillis()}.png")
                val segmentAudio =
                        File(context.cacheDir, "segment_${System.currentTimeMillis()}.wav")
                val videoFile = File(context.cacheDir, "segment_${System.currentTimeMillis()}.mp4")
                val durationLabel = formatDuration(segmentDurationMs)

                AudioFileUtils.extractSegment(
                        sourceWav = wavFile,
                        outputWav = segmentAudio,
                        startMs = startMs,
                        durationMs = segmentDurationMs,
                        sampleRate = AudioRecorderManager.SAMPLE_RATE,
                        channels = AudioRecorderManager.CHANNELS,
                        bitsPerSample = AudioRecorderManager.BITS_PER_SAMPLE
                )

                // Calculate amplitude slice for this segment
                val startIndex =
                        ((startMs.toDouble() / totalDurationMs) * amplitudes.size)
                                .toInt()
                                .coerceIn(0, amplitudes.size)
                val endIndex =
                        (((startMs + segmentDurationMs).toDouble() / totalDurationMs) *
                                        amplitudes.size)
                                .toInt()
                                .coerceIn(startIndex, amplitudes.size)
                val segmentAmplitudes = amplitudes.subList(startIndex, endIndex)

                frameRenderer.renderFrame(
                        avatarUrl = avatarUrl,
                        durationLabel = durationLabel,
                        chunkLabel = chunkLabel,
                        amplitudes = segmentAmplitudes,
                        outputFile = frameFile
                )

                frameFile.parentFile?.mkdirs()
                videoComposer.generateVideo(
                        imageUri = frameFile.toUri(),
                        audioUri = segmentAudio.toUri(),
                        outputFile = videoFile,
                        durationMs = segmentDurationMs
                )

                segmentAudio.delete()
                return videoFile
        }

        private suspend fun formatDuration(durationMs: Long): String =
                withContext(Dispatchers.Default) {
                        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
                        val minutes = totalSeconds / 60
                        val seconds = totalSeconds % 60
                        "%d:%02d".format(minutes, seconds)
                }
}

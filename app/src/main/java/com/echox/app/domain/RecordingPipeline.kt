package com.echox.app.domain

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecordingAssets(val audioFile: File, val videoFile: File, val durationMs: Long)

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

        videoComposer.generateVideo(
                imageUri = frameFile.toUri(),
                audioUri = audioResult.toUri(),
                outputFile = videoFile,
                durationMs = durationMs
        )

        return RecordingAssets(
                audioFile = audioResult,
                videoFile = videoFile,
                durationMs = durationMs
        )
    }

    suspend fun renderSegment(
            wavFile: File,
            avatarUrl: String?,
            startMs: Long,
            segmentDurationMs: Long,
            chunkLabel: String
    ): File {
        val frameFile = File(context.cacheDir, "frame_${System.currentTimeMillis()}.png")
        val segmentAudio = File(context.cacheDir, "segment_${System.currentTimeMillis()}.wav")
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

        frameRenderer.renderFrame(
                avatarUrl = avatarUrl,
                durationLabel = durationLabel,
                chunkLabel = chunkLabel,
                amplitudes = emptyList(), // TODO: Pass amplitudes for segments if needed
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

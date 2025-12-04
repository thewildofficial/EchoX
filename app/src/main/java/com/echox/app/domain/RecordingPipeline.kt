package com.echox.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
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

        /**
         * Saves recording assets to permanent storage and returns paths
         */
        suspend fun saveRecordingToPermanentStorage(
                assets: RecordingAssets
        ): SavedRecordingPaths = withContext(Dispatchers.IO) {
                val recordingsDir = File(context.filesDir, "recordings")
                recordingsDir.mkdirs()
                
                val timestamp = System.currentTimeMillis()
                val recordingId = "recording_$timestamp"
                
                // Copy files to permanent storage
                val permanentAudio = File(recordingsDir, "$recordingId.wav")
                val permanentVideo = File(recordingsDir, "$recordingId.mp4")
                val permanentAmplitudes = File(recordingsDir, "${recordingId}_amplitudes.txt")
                
                assets.audioFile.copyTo(permanentAudio, overwrite = true)
                assets.videoFile.copyTo(permanentVideo, overwrite = true)
                assets.amplitudesFile.copyTo(permanentAmplitudes, overwrite = true)
                
                // Generate thumbnail from video
                val thumbnailFile = File(recordingsDir, "${recordingId}_thumb.png")
                generateThumbnail(permanentVideo, thumbnailFile)
                
                SavedRecordingPaths(
                        audioPath = permanentAudio.absolutePath,
                        videoPath = permanentVideo.absolutePath,
                        amplitudesPath = permanentAmplitudes.absolutePath,
                        thumbnailPath = thumbnailFile.absolutePath
                )
        }
        
        private suspend fun generateThumbnail(videoFile: File, outputFile: File) = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                        retriever.setDataSource(videoFile.absolutePath)
                        val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        bitmap?.let {
                                FileOutputStream(outputFile).use { out ->
                                        it.compress(Bitmap.CompressFormat.PNG, 90, out)
                                }
                        }
                } catch (e: Exception) {
                        android.util.Log.e("RecordingPipeline", "Failed to generate thumbnail", e)
                } finally {
                        retriever.release()
                }
        }

        private suspend fun formatDuration(durationMs: Long): String =
                withContext(Dispatchers.Default) {
                        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
                        val minutes = totalSeconds / 60
                        val seconds = totalSeconds % 60
                        "%d:%02d".format(minutes, seconds)
                }
}

data class SavedRecordingPaths(
        val audioPath: String,
        val videoPath: String,
        val amplitudesPath: String,
        val thumbnailPath: String
)

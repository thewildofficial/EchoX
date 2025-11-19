package com.echox.app.domain

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class VideoCompositionManager(private val context: Context) {

    suspend fun generateVideo(
        imageUri: Uri,
        audioUri: Uri,
        outputUri: Uri,
        durationMs: Long
    ): Uri = suspendCancellableCoroutine { continuation ->
        try {
            // 1. Prepare Image MediaItem (Video Track)
            val imageItem = MediaItem.Builder()
                .setUri(imageUri)
                .setMimeType(MimeTypes.IMAGE_JPEG)
                .build()

            val imageEditedMediaItem = EditedMediaItem.Builder(imageItem)
                .setDurationUs(durationMs * MICROS_PER_MILLISECOND)
                .setFrameRate(DEFAULT_FRAME_RATE_FPS)
                .build()

            // 2. Prepare Audio MediaItem (Audio Track)
            val audioItem = MediaItem.fromUri(audioUri)
            val audioEditedMediaItem = EditedMediaItem.Builder(audioItem).build()

            // 3. Create Composition
            val videoSequence = EditedMediaItemSequence(listOf(imageEditedMediaItem))
            val audioSequence = EditedMediaItemSequence(listOf(audioEditedMediaItem))

            val composition = Composition.Builder(listOf(videoSequence, audioSequence))
                .build()

            // 4. Configure Transformer
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(outputUri)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        continuation.resumeWithException(exportException)
                    }
                })
                .build()

            // 5. Start Export
            transformer.start(composition, outputUri.toString())

        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    companion object {
        private const val DEFAULT_FRAME_RATE_FPS = 30
        private const val MICROS_PER_MILLISECOND = 1_000L
    }
}

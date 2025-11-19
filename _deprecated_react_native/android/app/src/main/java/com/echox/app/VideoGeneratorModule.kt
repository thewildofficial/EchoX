package com.echox.app

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.EditedMediaItemSequence
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File

class VideoGeneratorModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "VideoGeneratorModule"
    }

    @ReactMethod
    fun generateVideo(imageUri: String, audioUri: String, outputUri: String, durationMs: Double, promise: Promise) {
        val context = reactApplicationContext
        val durationLong = durationMs.toLong()

        try {
            // 1. Prepare Image MediaItem (Video Track)
            // We treat the image as a video of 'durationMs' length
            val imageItem = MediaItem.Builder()
                .setUri(Uri.parse(imageUri))
                .setMimeType(MimeTypes.IMAGE_JPEG) // Or detect from URI
                .build()

            val imageEditedMediaItem = EditedMediaItem.Builder(imageItem)
                .setDurationUs(durationLong * MICROS_PER_MILLISECOND)
                .setFrameRate(DEFAULT_FRAME_RATE_FPS)
                .build()

            // 2. Prepare Audio MediaItem (Audio Track)
            val audioItem = MediaItem.fromUri(Uri.parse(audioUri))
            val audioEditedMediaItem = EditedMediaItem.Builder(audioItem).build()

            // 3. Create Composition
            // We want to mix them. Transformer 'Composition' allows sequences.
            // For mixing (overlaying), we might need a more complex setup or just rely on Transformer's default behavior if we add inputs.
            // However, Media3 Transformer primarily concatenates sequences.
            // To MIX audio and video, we usually create a Composition with two sequences: one video, one audio.
            
            val videoSequence = EditedMediaItemSequence(listOf(imageEditedMediaItem))
            val audioSequence = EditedMediaItemSequence(listOf(audioEditedMediaItem))

            val composition = Composition.Builder(listOf(videoSequence, audioSequence))
                .build()

            // 4. Configure Transformer
            val outputFile = File(outputUri)
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.createNewFile()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        promise.resolve(outputFile.absolutePath)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        promise.reject("EXPORT_ERROR", exportException)
                    }
                })
                .build()

            // 5. Start Export
            transformer.start(composition, outputFile.absolutePath)

        } catch (e: Exception) {
            promise.reject("SETUP_ERROR", e)
        }
    }
    companion object {
        private const val DEFAULT_FRAME_RATE_FPS = 30
        private const val MICROS_PER_MILLISECOND = 1_000L
    }
}

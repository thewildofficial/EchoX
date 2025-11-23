package com.echox.app.domain

import android.content.Context
import com.echox.app.data.api.MediaIds
import com.echox.app.data.api.ReplyData
import com.echox.app.data.api.TweetRequest
import com.echox.app.data.api.UserData
import com.echox.app.data.repository.XRepository
import java.io.File
import java.io.FileInputStream
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class SharePipeline(
    private val context: Context,
    private val repository: XRepository
) {

    private val recordingPipeline = RecordingPipeline(context)

    suspend fun shareRecording(
        user: UserData?,
        audioFile: File,
        previewVideoFile: File,
        durationMs: Long,
        avatarUrl: String?,
        onStatus: (String) -> Unit
    ) {
        val isPremiumUser = user?.verified_type in setOf("blue", "business")
        val maxDurationMs =
            if (isPremiumUser) PREMIUM_DURATION_LIMIT_SEC * 1000L
            else STANDARD_DURATION_LIMIT_SEC * 1000L

        val videos =
            if (durationMs <= maxDurationMs) {
                listOf(previewVideoFile)
            } else {
                buildSegments(
                    audioFile = audioFile,
                    durationMs = durationMs,
                    avatarUrl = avatarUrl,
                    onStatus = onStatus
                )
            }

        val mediaIds = mutableListOf<String>()
        videos.forEachIndexed { index, videoFile ->
            onStatus("Uploading part ${index + 1}/${videos.size}…")
            val mediaId = uploadVideo(videoFile)
            mediaIds.add(mediaId)
        }
        videos.filter { it != previewVideoFile }.forEach { it.delete() }

        postThread(mediaIds, onStatus)
    }

    private suspend fun buildSegments(
        audioFile: File,
        durationMs: Long,
        avatarUrl: String?,
        onStatus: (String) -> Unit
    ): List<File> {
        val totalParts = ceil(durationMs / STANDARD_SEGMENT_MS.toDouble()).toInt().coerceAtLeast(1)
        val segments = mutableListOf<File>()
        for (index in 0 until totalParts) {
            val start = index * STANDARD_SEGMENT_MS
            val remaining = durationMs - start
            if (remaining <= 0) break
            val clipDuration = min(STANDARD_SEGMENT_MS, remaining)
            onStatus("Rendering part ${index + 1}/$totalParts…")
            val file =
                recordingPipeline.renderSegment(
                    wavFile = audioFile,
                    avatarUrl = avatarUrl,
                    startMs = start,
                    segmentDurationMs = clipDuration,
                    chunkLabel = "${index + 1}/$totalParts"
                )
            segments.add(file)
        }
        return segments
    }

    private suspend fun uploadVideo(videoFile: File): String = withContext(Dispatchers.IO) {
        val textPlain = "text/plain".toMediaType()
        val mediaType = "video/mp4"

        val initResponse =
            repository.api.initUpload(
                "INIT".toRequestBody(textPlain),
                videoFile.length().toString().toRequestBody(textPlain),
                mediaType.toRequestBody(textPlain),
                "tweet_video".toRequestBody(textPlain)
            )

        FileInputStream(videoFile).use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            var segmentIndex = 0
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break
                val chunk = buffer.copyOf(bytesRead)
                val mediaBody =
                    chunk.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                val mediaPart = MultipartBody.Part.createFormData("media", "chunk", mediaBody)
                repository.api.appendUpload(
                    "APPEND".toRequestBody(textPlain),
                    initResponse.media_id_string.toRequestBody(textPlain),
                    segmentIndex.toString().toRequestBody(textPlain),
                    mediaPart
                )
                segmentIndex++
            }
        }

        val finalizeResponse =
            repository.api.finalizeUpload(
                "FINALIZE".toRequestBody(textPlain),
                initResponse.media_id_string.toRequestBody(textPlain)
            )

        waitForProcessing(
            mediaId = initResponse.media_id_string,
            response = finalizeResponse,
            textMediaType = textPlain
        )
        initResponse.media_id_string
    }

    private suspend fun waitForProcessing(
        mediaId: String,
        response: com.echox.app.data.api.MediaFinalizeResponse,
        textMediaType: MediaType
    ) {
        var processingInfo = response.processing_info
        while (processingInfo != null && processingInfo.state != "succeeded") {
            if (processingInfo.state == "failed") {
                throw IllegalStateException("Media processing failed")
            }
            val delaySeconds = processingInfo.check_after_secs ?: 2
            delay(delaySeconds * 1000L)
            processingInfo =
                repository.api.checkUploadStatus(
                        "STATUS".toRequestBody(textMediaType),
                        mediaId.toRequestBody(textMediaType)
                    )
                    .processing_info
        }
    }

    private suspend fun postThread(mediaIds: List<String>, onStatus: (String) -> Unit) {
        onStatus("Posting to X…")
        var replyToId: String? = null
        mediaIds.forEachIndexed { index, mediaId ->
            val text =
                if (mediaIds.size > 1) "Voice Note Part ${index + 1}/${mediaIds.size}"
                else "Voice Note via EchoX"
            val request =
                TweetRequest(
                    text = text,
                    media = MediaIds(listOf(mediaId)),
                    reply = replyToId?.let { ReplyData(it) }
                )
            val response = repository.api.postTweet(request)
            replyToId = response.data.id
        }
        onStatus("Shared successfully!")
    }

    companion object {
        private const val PREMIUM_DURATION_LIMIT_SEC = 7200
        private const val STANDARD_DURATION_LIMIT_SEC = 140
        private const val CHUNK_SIZE = 4 * 1024 * 1024
        private val STANDARD_SEGMENT_MS = STANDARD_DURATION_LIMIT_SEC * 1000L
    }
}


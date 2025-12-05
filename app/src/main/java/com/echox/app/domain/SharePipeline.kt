package com.echox.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.echox.app.data.api.UserData
import com.echox.app.data.api.XApiService
import com.echox.app.data.repository.XRepository
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

class SharePipeline(private val context: Context, private val repository: XRepository) {

    private val recordingPipeline = RecordingPipeline(context)
    private val xApiService = XApiService()

    /** Share recording as X thread (default) or via native share sheet (fallback) */
    suspend fun shareRecording(
            user: UserData?,
            audioFile: File,
            previewVideoFile: File,
            durationMs: Long,
            avatarUrl: String?,
            amplitudes: List<Float>,
            onStatus: (String) -> Unit,
            preferXThread: Boolean = true,
            customText: String? = null
    ) {
        val maxDurationMs = STANDARD_DURATION_LIMIT_SEC * 1000L

        val videos =
                if (durationMs <= maxDurationMs) {
                    listOf(previewVideoFile)
                } else {
                    buildSegments(
                            audioFile = audioFile,
                            durationMs = durationMs,
                            avatarUrl = avatarUrl,
                            amplitudes = amplitudes,
                            onStatus = onStatus
                    )
                }

        // DEBUG: Check why X thread might be skipped
        val hasToken = !repository.getAccessToken().isNullOrBlank()
        onStatus(
                "Debug: PreferX=$preferXThread, HasToken=$hasToken, User=${if (user!=null) "OK" else "NULL"}, Videos=${videos.size}"
        )

        // Try X API thread posting first if preferred and we have a token
        // NOTE: We don't require user profile - it's just for UI display. Token is what matters for
        // API calls.
        if (preferXThread && hasToken && videos.isNotEmpty()) {
            // Get user's OAuth access token
            val accessToken = repository.getAccessToken()!!
            onStatus("Posting thread to X...")
            val baseText = customText?.takeIf { it.isNotBlank() } ?: "Check out my audio recording!"
            val success =
                    xApiService.postThread(
                            videos = videos,
                            baseText = baseText,
                            accessToken = accessToken,
                            onProgress = onStatus
                    )

            if (success) {
                onStatus("Thread posted successfully!")
                return
            } else {
                val msg = "X thread failed (postThread=false). Check previous status msgs."
                android.util.Log.e("EchoX_ERROR", msg)
                onStatus(msg)
                // Do NOT return, let it fall back to share sheet so user can still share
            }
        } else {
            if (!hasToken) onStatus("Skipped X: No token")
            if (videos.isEmpty()) onStatus("Skipped X: No videos")
            if (preferXThread) onStatus("X thread skipped (Token=$hasToken, Videos=${videos.size})")
        }

        // Fallback to native share sheet
        val debugInfo =
                "Debug: PreferX=$preferXThread, User=${if (user!=null) "OK" else "NULL"}, Videos=${videos.size}"
        onStatus("Opening share sheet... ($debugInfo)")
        shareFiles(videos)
        onStatus("Shared!")
    }

    private suspend fun buildSegments(
            audioFile: File,
            durationMs: Long,
            avatarUrl: String?,
            amplitudes: List<Float>,
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
                            chunkLabel = "${index + 1}/$totalParts",
                            amplitudes = amplitudes,
                            totalDurationMs = durationMs
                    )
            segments.add(file)
        }
        return segments
    }

    private fun shareFiles(files: List<File>) {
        val uris = ArrayList<Uri>()
        files.forEach { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            uris.add(uri)
        }

        val intent =
                Intent().apply {
                    if (uris.size == 1) {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uris[0])
                        type = "video/mp4"
                    } else {
                        action = Intent.ACTION_SEND_MULTIPLE
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        type = "video/mp4"
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Try to target X app directly for better UX
                    setPackage("com.twitter.android")
                }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to chooser if X app is not installed
            intent.setPackage(null)
            val chooser = Intent.createChooser(intent, "Share Video")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    companion object {
        private const val STANDARD_DURATION_LIMIT_SEC = 140
        private val STANDARD_SEGMENT_MS = STANDARD_DURATION_LIMIT_SEC * 1000L
    }
}

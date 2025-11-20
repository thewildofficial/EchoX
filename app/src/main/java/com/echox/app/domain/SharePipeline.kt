package com.echox.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.echox.app.data.api.UserData
import com.echox.app.data.repository.XRepository
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

class SharePipeline(
        private val context: Context,
        private val repository: XRepository // Kept for compatibility but unused for sharing now
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
        // For simple sharing, we might just share the preview video if it's short enough.
        // But if it's long, we should split it.
        // However, standard intents handle multiple files well.

        val maxDurationMs = STANDARD_DURATION_LIMIT_SEC * 1000L

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

        onStatus("Opening share sheet...")
        shareFiles(videos)
        onStatus("Shared!")
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
                }

        val chooser = Intent.createChooser(intent, "Share Video")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    companion object {
        private const val STANDARD_DURATION_LIMIT_SEC = 140
        private val STANDARD_SEGMENT_MS = STANDARD_DURATION_LIMIT_SEC * 1000L
    }
}

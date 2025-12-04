package com.echox.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.echox.app.data.database.Recording
import com.echox.app.data.repository.RecordingRepository
import com.echox.app.data.repository.XRepository
import com.echox.app.domain.SharePipeline
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecordingsLibraryScreen(
        navController: NavController,
        repository: XRepository,
        recordingRepository: RecordingRepository
) {
        val context = LocalContext.current
        val recordings by recordingRepository.getAllRecordings().collectAsState(initial = emptyList())
        val scope = rememberCoroutineScope()
        val sharePipeline = remember { SharePipeline(context, repository) }
        val user by repository.userProfile.collectAsState()
        val avatarUrl = user?.profile_image_url?.replace("_normal", "")

        var selectedRecording by remember { mutableStateOf<Recording?>(null) }
        var isPlaying by remember { mutableStateOf(false) }
        var player by remember { mutableStateOf<ExoPlayer?>(null) }

        DisposableEffect(selectedRecording) {
                val currentPlayer = player
                onDispose {
                        currentPlayer?.release()
                        player = null
                }
        }
        
        // Create player when recording is selected
        LaunchedEffect(selectedRecording) {
                player?.release()
                player = null
                
                selectedRecording?.let { recording ->
                        val newPlayer = ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(recording.videoPath))))
                                prepare()
                        }
                        player = newPlayer
                        if (isPlaying) {
                                newPlayer.playWhenReady = true
                        }
                }
        }

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color(0xFF15202b)) // XDark
                                .padding(16.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = "My Recordings",
                                style =
                                        androidx.compose.material3.MaterialTheme.typography
                                                .headlineMedium,
                                color = Color.White
                        )
                        
                        androidx.compose.material3.TextButton(onClick = { navController.popBackStack() }) {
                                Text(
                                        text = "Close",
                                        color = Color(0xFF1d9bf0)
                                )
                        }
                }

                if (recordings.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        Text(
                                                text = "No recordings yet",
                                                color = Color.White.copy(alpha = 0.6f),
                                                style =
                                                        androidx.compose.material3.MaterialTheme
                                                                .typography
                                                                .bodyLarge
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                                text = "Record something to see it here",
                                                color = Color.White.copy(alpha = 0.4f),
                                                style =
                                                        androidx.compose.material3.MaterialTheme
                                                                .typography
                                                                .bodyMedium
                                        )
                                }
                        }
                } else {
                        LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                items(recordings) { recording ->
                                        RecordingItem(
                                                recording = recording,
                                                isSelected = selectedRecording?.id == recording.id,
                                                isPlaying = isPlaying && selectedRecording?.id == recording.id,
                                                onPlayClick = {
                                                        if (selectedRecording?.id == recording.id && isPlaying) {
                                                                player?.pause()
                                                                isPlaying = false
                                                        } else {
                                                                selectedRecording = recording
                                                                isPlaying = true
                                                                player?.playWhenReady = true
                                                        }
                                                },
                                                onShareClick = {
                                                        scope.launch {
                                                                shareRecording(
                                                                        recording = recording,
                                                                        sharePipeline = sharePipeline,
                                                                        user = user,
                                                                        avatarUrl = avatarUrl,
                                                                        context = context
                                                                )
                                                        }
                                                },
                                                onDeleteClick = {
                                                        scope.launch {
                                                                val audioFile = File(recording.audioPath)
                                                                val videoFile = File(recording.videoPath)
                                                                val amplitudesFile = File(recording.amplitudesPath)
                                                                val thumbnailFile = recording.thumbnailPath?.let { File(it) }
                                                                
                                                                // Delete files and track results
                                                                val deleteResults = mutableListOf<Pair<String, Boolean>>()
                                                                deleteResults.add("audio" to audioFile.delete())
                                                                deleteResults.add("video" to videoFile.delete())
                                                                deleteResults.add("amplitudes" to amplitudesFile.delete())
                                                                thumbnailFile?.let { deleteResults.add("thumbnail" to it.delete()) }
                                                                
                                                                val failedDeletes = deleteResults.filter { !it.second }.map { it.first }
                                                                
                                                                if (failedDeletes.isNotEmpty()) {
                                                                        // Some files failed to delete
                                                                        val failedTypes = failedDeletes.joinToString(", ")
                                                                        android.util.Log.w(
                                                                                "RecordingsLibrary",
                                                                                "Failed to delete files: $failedTypes for recording ${recording.id}"
                                                                        )
                                                                        Toast.makeText(
                                                                                context,
                                                                                "Warning: Some files could not be deleted ($failedTypes)",
                                                                                Toast.LENGTH_LONG
                                                                        ).show()
                                                                        // Still delete from database to avoid showing broken entries
                                                                        // but log the issue for debugging
                                                                }
                                                                
                                                                // Delete from database
                                                                runCatching {
                                                                        recordingRepository.deleteRecording(recording)
                                                                        
                                                                        if (failedDeletes.isEmpty()) {
                                                                                Toast.makeText(
                                                                                        context,
                                                                                        "Recording deleted",
                                                                                        Toast.LENGTH_SHORT
                                                                                ).show()
                                                                        }
                                                                        
                                                                        if (selectedRecording?.id == recording.id) {
                                                                                selectedRecording = null
                                                                                isPlaying = false
                                                                        }
                                                                }
                                                                        .onFailure { error ->
                                                                                Toast.makeText(
                                                                                        context,
                                                                                        "Failed to delete from database: ${error.message}",
                                                                                        Toast.LENGTH_SHORT
                                                                                ).show()
                                                                        }
                                                        }
                                                }
                                        )
                                }
                        }
                }
        }

        // Video player overlay
        selectedRecording?.let { recording ->
                if (isPlaying && player != null) {
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.9f))
                                                .clickable {
                                                        player?.pause()
                                                        isPlaying = false
                                                },
                                contentAlignment = Alignment.Center
                        ) {
                                Column(
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        AndroidView(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .aspectRatio(16f / 9f),
                                                factory = {
                                                        PlayerView(it).apply {
                                                                useController = true
                                                                player = player
                                                        }
                                                }
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                                text = "Tap to close",
                                                color = Color.White.copy(alpha = 0.7f),
                                                style =
                                                        androidx.compose.material3.MaterialTheme
                                                                .typography
                                                                .bodyMedium
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun RecordingItem(
        recording: Recording,
        isSelected: Boolean,
        isPlaying: Boolean,
        onPlayClick: () -> Unit,
        onShareClick: () -> Unit,
        onDeleteClick: () -> Unit
) {
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(
                                containerColor = Color(0xFF1e2732) // Slightly lighter than background
                        ),
                shape = RoundedCornerShape(12.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Thumbnail
                        Box(
                                modifier =
                                        Modifier.size(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black)
                        ) {
                                if (recording.thumbnailPath != null && File(recording.thumbnailPath).exists()) {
                                        AsyncImage(
                                                model = File(recording.thumbnailPath),
                                                contentDescription = "Recording thumbnail",
                                                modifier = Modifier.fillMaxSize()
                                        )
                                } else {
                                        Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(32.dp)
                                                )
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Recording info
                        Column(
                                modifier = Modifier.weight(1f)
                        ) {
                                Text(
                                        text = recording.getFormattedDate(),
                                        color = Color.White,
                                        style =
                                                androidx.compose.material3.MaterialTheme.typography
                                                        .titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = recording.getFormattedDuration(),
                                        color = Color.White.copy(alpha = 0.6f),
                                        style =
                                                androidx.compose.material3.MaterialTheme.typography
                                                        .bodyMedium
                                )
                        }

                        // Action buttons
                        IconButton(onClick = onPlayClick) {
                                Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color(0xFF1d9bf0)
                                )
                        }
                        IconButton(onClick = onShareClick) {
                                Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color(0xFF1d9bf0)
                                )
                        }
                        IconButton(onClick = onDeleteClick) {
                                Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF3B30)
                                )
                        }
                }
        }
}

private suspend fun shareRecording(
        recording: Recording,
        sharePipeline: SharePipeline,
        user: com.echox.app.data.api.UserData?,
        avatarUrl: String?,
        context: android.content.Context
) {
        // Perform file IO operations on IO dispatcher
        val (audioFile, videoFile, amplitudes) = withContext(Dispatchers.IO) {
                val audio = File(recording.audioPath)
                val video = File(recording.videoPath)
                val amps =
                        runCatching {
                                        File(recording.amplitudesPath)
                                                .readText()
                                                .split(",")
                                                .mapNotNull { it.toFloatOrNull() }
                                }
                                .getOrNull() ?: emptyList()
                
                Triple(audio, video, amps)
        }

        // Check file existence on IO dispatcher
        val filesExist = withContext(Dispatchers.IO) {
                audioFile.exists() && videoFile.exists()
        }

        if (!filesExist) {
                // Toast must be called on main dispatcher
                withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                                        context,
                                        "Recording files not found",
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                }
                return
        }

        // Share operation (likely involves network/IO) on IO dispatcher
        // Note: onStatus callback may be called from IO thread, so we need to ensure
        // Toast operations happen on main thread
        withContext(Dispatchers.IO) {
                sharePipeline.shareRecording(
                        user = user,
                        audioFile = audioFile,
                        previewVideoFile = videoFile,
                        durationMs = recording.durationMs,
                        avatarUrl = avatarUrl,
                        amplitudes = amplitudes,
                        onStatus = { status ->
                                // Toast must be called on main dispatcher
                                // Use Handler to post to main thread from IO context
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        android.widget.Toast.makeText(
                                                context,
                                                status,
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                }
                        }
                )
        }
}

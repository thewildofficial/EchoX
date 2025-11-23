package com.echox.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.echox.app.data.repository.XRepository
import androidx.compose.ui.viewinterop.AndroidView
import com.echox.app.domain.SharePipeline
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun PreviewScreen(
    navController: NavController,
    repository: XRepository,
    audioUri: String?,
    videoUri: String?,
    durationMs: Long
) {
    val context = LocalContext.current
    val user by repository.userProfile.collectAsState()

    val parsedVideoUri = remember(videoUri) { videoUri?.let { Uri.parse(it) } }
    val parsedAudioUri = remember(audioUri) { audioUri?.let { Uri.parse(it) } }
    val audioFile = remember(parsedAudioUri) { parsedAudioUri?.path?.let { File(it) } }
    val videoFile = remember(parsedVideoUri) { parsedVideoUri?.path?.let { File(it) } }
    val sharePipeline = remember { SharePipeline(context, repository) }
    val scope = rememberCoroutineScope()

    val exoPlayer =
        remember(parsedVideoUri) {
            parsedVideoUri?.let {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(it))
                    prepare()
                    playWhenReady = true
                }
            }
        }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    var statusMessage by remember { mutableStateOf("") }
    var isSharing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Preview",
                color = Color.White,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (exoPlayer != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Color.DarkGray, RoundedCornerShape(24.dp)),
                    factory = {
                        PlayerView(it).apply {
                            useController = true
                            player = exoPlayer
                        }
                    }
                )
            } else {
                Text(text = "Preview unavailable", color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = formatDuration(durationMs),
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        if (statusMessage.isNotBlank()) {
            Text(text = statusMessage, color = Color.White.copy(alpha = 0.8f))
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (audioFile == null || videoFile == null) {
                        Toast.makeText(context, "Missing recording", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isSharing) return@Button
                    isSharing = true
                    statusMessage = "Preparing upload…"
                    val avatarUrl = user?.profile_image_url?.replace("_normal", "")
                    scope.launch {
                        runCatching {
                                sharePipeline.shareRecording(
                                    user = user,
                                    audioFile = audioFile,
                                    previewVideoFile = videoFile,
                                    durationMs = durationMs,
                                    avatarUrl = avatarUrl,
                                    onStatus = { status -> statusMessage = status }
                                )
                            }
                            .onSuccess {
                                isSharing = false
                                Toast.makeText(context, "Shared on X!", Toast.LENGTH_SHORT).show()
                                navController.navigate("record") {
                                    popUpTo("record") { inclusive = true }
                                }
                            }
                            .onFailure { error ->
                                isSharing = false
                                statusMessage = "Failed: ${error.message ?: "Unknown error"}"
                            }
                    }
                },
                enabled = !isSharing && parsedAudioUri != null && parsedVideoUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = if (isSharing) "Sharing..." else "Share to X",
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    navController.popBackStack("record", inclusive = false)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                )
            ) {
                Text(text = "Record Again")
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


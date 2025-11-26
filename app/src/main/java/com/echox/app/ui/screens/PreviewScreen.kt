package com.echox.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.echox.app.data.repository.XRepository
import com.echox.app.domain.SharePipeline
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun PreviewScreen(
        navController: NavController,
        repository: XRepository,
        audioUri: String?,
        videoUri: String?,
        durationMs: Long,
        amplitudesPath: String?
) {
    val context = LocalContext.current
    val user by repository.userProfile.collectAsState()

    val parsedVideoUri = remember(videoUri) { videoUri?.let { Uri.parse(it) } }
    val parsedAudioUri = remember(audioUri) { audioUri?.let { Uri.parse(it) } }
    val audioFile = remember(parsedAudioUri) { parsedAudioUri?.path?.let { File(it) } }
    val videoFile = remember(parsedVideoUri) { parsedVideoUri?.path?.let { File(it) } }
    val amplitudes =
            remember(amplitudesPath) {
                amplitudesPath?.let { path ->
                    runCatching {
                                File(path).readText().split(",").mapNotNull { it.toFloatOrNull() }
                            }
                            .getOrNull()
                }
                        ?: emptyList()
            }
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

    DisposableEffect(exoPlayer) { onDispose { exoPlayer?.release() } }

    var statusMessage by remember { mutableStateOf("") }
    var isSharing by remember { mutableStateOf(false) }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(Color(0xFF15202b)) // XDark
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
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (exoPlayer != null) {
                // 16:9 Aspect Ratio Container
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(Color.Black, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            // ratio modifier
                            factory = {
                                PlayerView(it).apply {
                                    useController = true
                                    player = exoPlayer
                                }
                            }
                    )
                }
            } else {
                Text(text = "Preview unavailable", color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                    text = formatDuration(durationMs),
                    color = Color.White.copy(alpha = 0.6f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }

        if (statusMessage.isNotBlank()) {
            Text(text = statusMessage, color = Color.White.copy(alpha = 0.9f))
        }

        // DEBUG INFO
        val debugToken = repository.getAccessToken()
        Text(
                text =
                        "Debug: User=${if (user != null) "OK" else "NULL"}, Token=${if (!debugToken.isNullOrBlank()) "OK" else "MISSING"}",
                color = Color.Yellow,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                    onClick = {
                        Toast.makeText(context, "[DEBUG] Share button clicked!", Toast.LENGTH_LONG)
                                .show()

                        if (audioFile == null || videoFile == null) {
                            Toast.makeText(context, "Missing recording", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (isSharing) return@Button
                        isSharing = true
                        statusMessage = "Preparing upload…"

                        Toast.makeText(
                                        context,
                                        "[DEBUG] User=${if(user!=null)"OK" else "NULL"}, Token=${if(repository.getAccessToken()!=null)"OK" else "NULL"}",
                                        Toast.LENGTH_LONG
                                )
                                .show()

                        val avatarUrl = user?.profile_image_url?.replace("_normal", "")
                        scope.launch {
                            runCatching {
                                sharePipeline.shareRecording(
                                        user = user,
                                        audioFile = audioFile,
                                        previewVideoFile = videoFile,
                                        durationMs = durationMs,
                                        avatarUrl = avatarUrl,
                                        amplitudes = amplitudes,
                                        onStatus = { status ->
                                            statusMessage = status
                                            Toast.makeText(
                                                            context,
                                                            "[STATUS] $status",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                )
                            }
                                    .onSuccess {
                                        isSharing = false
                                        Toast.makeText(context, "Shared on X!", Toast.LENGTH_SHORT)
                                                .show()
                                        navController.navigate("record") {
                                            popUpTo("record") { inclusive = true }
                                        }
                                    }
                                    .onFailure { error ->
                                        isSharing = false
                                        Toast.makeText(
                                                        context,
                                                        "[ERROR] ${error.message}",
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                        statusMessage =
                                                "Failed: ${error.message ?: "Unknown error"}"
                                    }
                        }
                    },
                    enabled = !isSharing && parsedAudioUri != null && parsedVideoUri != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors =
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF1d9bf0)) // XBlue
            ) {
                Text(
                        text = if (isSharing) "Sharing..." else "Share to X",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                    onClick = { navController.popBackStack("record", inclusive = false) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White.copy(alpha = 0.7f)
                            )
            ) {
                Text(
                        text = "Discard",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
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

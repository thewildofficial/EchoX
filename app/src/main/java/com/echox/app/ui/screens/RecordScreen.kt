package com.echox.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.echox.app.data.repository.XRepository
import com.echox.app.domain.AudioRecorderManager
import com.echox.app.domain.RecordingPipeline
import com.echox.app.ui.components.Waveform
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun RecordScreen(navController: NavController, repository: XRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioRecorderManager = remember { AudioRecorderManager() }
    val recordingPipeline = remember { RecordingPipeline(context) }
    val user by repository.userProfile.collectAsState()
    val avatarUrl = user?.profile_image_url?.replace("_normal", "")

    var isRecording by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    val amplitudes = remember { mutableStateListOf<Float>() }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartTime by remember { mutableStateOf<Long?>(null) }

    // Permission Launcher
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.pcm")
                            currentRecordingFile = file
                            recordingStartTime = System.currentTimeMillis()
                            audioRecorderManager.startRecording(file, scope)
                            isRecording = true
                            statusMessage = "Recording..."
                            amplitudes.clear()
                        } else {
                            // Handle permission denied
                        }
                    }
            )

    // Observe amplitude updates
    LaunchedEffect(isRecording) {
        if (isRecording) {
            audioRecorderManager.amplitude.collect { amp -> amplitudes.add(amp) }
        }
    }

    Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
    ) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize().padding(vertical = 48.dp)
        ) {
            // Top Bar / Header
            Text(
                    text = user?.name ?: "EchoX",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 16.dp)
            )

            // Central Visuals (Avatar + Waveform)
            Box(contentAlignment = Alignment.Center) {
                // Glowing Waveform (Behind Avatar)
                Waveform(
                        amplitudes = amplitudes,
                        modifier = Modifier.size(300.dp).padding(16.dp),
                        // TODO: Add glow effect to Waveform component
                        )

                // Avatar Placeholder (Steve Jobs style - simple, elegant)
                if (avatarUrl != null) {
                    AsyncImage(
                            model =
                                    ImageRequest.Builder(context)
                                            .data(avatarUrl)
                                            .crossfade(true)
                                            .build(),
                            contentDescription = "Profile photo",
                            modifier =
                                    Modifier.size(120.dp)
                                            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                            .background(Color.Black, CircleShape)
                                            .clip(CircleShape)
                    )
                } else {
                    Box(
                            modifier =
                                    Modifier.size(120.dp)
                                            .background(Color.DarkGray, CircleShape)
                                            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User Avatar",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            // Bottom Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                        text =
                                when {
                                    isGenerating -> statusMessage.ifBlank { "Generating video..." }
                                    isRecording -> statusMessage.ifBlank { "Recording..." }
                                    statusMessage.isNotBlank() -> statusMessage
                                    else -> "Tap to Record"
                                },
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                        onClick = {
                            if (isRecording) {
                                val rawFile = currentRecordingFile
                                if (rawFile != null) {
                                    audioRecorderManager.stopRecording()
                                    isRecording = false
                                    isGenerating = true
                                    statusMessage = "Finalizing audio..."
                                    val duration =
                                            (System.currentTimeMillis() -
                                                    (recordingStartTime ?: System.currentTimeMillis()))
                                                    .coerceAtLeast(500L)
                                    scope.launch {
                                        runCatching {
                                                recordingPipeline.preparePreview(
                                                        rawPcmFile = rawFile,
                                                        durationMs = duration,
                                                        avatarUrl = avatarUrl
                                                )
                                            }
                                            .onSuccess { assets ->
                                                statusMessage = "Preview ready"
                                                val audioParam = Uri.encode(assets.audioFile.toUri().toString())
                                                val videoParam = Uri.encode(assets.videoFile.toUri().toString())
                                                isGenerating = false
                                                navController.navigate(
                                                        "preview?audio=$audioParam&video=$videoParam&duration=${assets.durationMs}"
                                                )
                                            }
                                            .onFailure { error ->
                                                error.printStackTrace()
                                                statusMessage = "Unable to process recording"
                                                isGenerating = false
                                            }
                                    }
                                }
                            } else {
                                val permissionCheck =
                                        ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                        )
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.pcm")
                                    currentRecordingFile = file
                                    recordingStartTime = System.currentTimeMillis()
                                    audioRecorderManager.startRecording(file, scope)
                                    isRecording = true
                                    statusMessage = "Recording..."
                                    amplitudes.clear()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        shape = CircleShape,
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor =
                                                if (isRecording) Color(0xFFFF3B30) // iOS Red
                                                else Color(0xFF0A84FF) // iOS Blue
                                ),
                        modifier = Modifier.size(72.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                        enabled = !isGenerating
                ) {
                    Icon(
                            imageVector =
                                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

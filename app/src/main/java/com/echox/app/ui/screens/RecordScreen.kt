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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
<<<<<<< HEAD
import androidx.compose.material.icons.filled.PlayArrow
=======
import androidx.compose.material.icons.filled.Settings
>>>>>>> 78e9a65 (feat: Add Settings screen with account info, preferences, and logout)
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.echox.app.data.repository.RecordingRepository
import com.echox.app.data.repository.XRepository
import com.echox.app.domain.AudioRecorderManager
import com.echox.app.domain.RecordingPipeline
import com.echox.app.domain.RecordingState
import com.echox.app.ui.components.Waveform
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun RecordScreen(
        navController: NavController,
        repository: XRepository,
        recordingRepository: RecordingRepository
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val audioRecorderManager = remember { AudioRecorderManager() }
        val recordingPipeline = remember { RecordingPipeline(context) }
        val user by repository.userProfile.collectAsState()
        val avatarUrl = user?.profile_image_url?.replace("_normal", "")
        val recordingState by audioRecorderManager.recordingState.collectAsState()

        var isGenerating by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("") }
        val amplitudes = remember { mutableStateListOf<Float>() }
        var currentRecordingFile by remember { mutableStateOf<File?>(null) }
        var recordingStartTime by remember { mutableStateOf<Long?>(null) }
        var pausedTime by remember { mutableStateOf<Long?>(null) }
        var totalPausedDuration by remember { mutableStateOf(0L) }
        var elapsedTime by remember { mutableStateOf(0L) }

        // Permission Launcher
        val startRecordingSession = {
                val file =
                        File(
                                context.cacheDir,
                                "recording_${System.currentTimeMillis()}.pcm"
                        )
                currentRecordingFile = file
                recordingStartTime = System.currentTimeMillis()
                totalPausedDuration = 0L
                pausedTime = null
                audioRecorderManager.startRecording(file, scope)
                statusMessage = "Listening..."
                amplitudes.clear()
        }

        val permissionLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { isGranted ->
                                if (isGranted) {
                                        startRecordingSession()
                                } else {
                                        // Handle permission denied
                                }
                        }
                )

        // Observe amplitude updates
        LaunchedEffect(recordingState) {
                if (recordingState != RecordingState.Idle) {
                        audioRecorderManager.amplitude.collect { amp ->
                                if (recordingState == RecordingState.Recording) {
                                        amplitudes.add(amp)
                                }
                        }
                } else {
                        amplitudes.clear()
                }
        }

        // Handle pause/resume time tracking
        LaunchedEffect(recordingState) {
                when (recordingState) {
                        RecordingState.Paused -> {
                                if (pausedTime == null) {
                                        pausedTime = System.currentTimeMillis()
                                }
                        }
                        RecordingState.Recording -> {
                                if (pausedTime != null) {
                                        totalPausedDuration += System.currentTimeMillis() - pausedTime!!
                                        pausedTime = null
                                }
                        }
                        RecordingState.Idle -> {
                                pausedTime = null
                                totalPausedDuration = 0L
                        }
                }
        }

        // Update elapsed time every 100ms while recording or paused
        LaunchedEffect(recordingState, recordingStartTime, pausedTime, totalPausedDuration) {
                val initialState = recordingState
                if ((initialState == RecordingState.Recording || initialState == RecordingState.Paused) && recordingStartTime != null) {
                        while (true) {
                                val currentState = audioRecorderManager.recordingState.value
                                if (currentState == RecordingState.Idle) break

                                val currentPausedTime =
                                        if (currentState == RecordingState.Paused && pausedTime != null) {
                                                System.currentTimeMillis() - pausedTime!!
                                        } else {
                                                0L
                                        }
                                elapsedTime =
                                        (System.currentTimeMillis() - recordingStartTime!! - totalPausedDuration - currentPausedTime)
                                                .coerceAtLeast(0L)
                                kotlinx.coroutines.delay(100) // Update 10x per second for smooth display
                        }
                } else {
                        elapsedTime = 0L
                }
        }

        Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF15202b)), // XDark
                contentAlignment = Alignment.Center
        ) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize().padding(vertical = 48.dp)
                ) {
                        // Top Bar / Header
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)
                        ) {
                                Text(
                                        text = user?.name ?: "EchoX",
                                        style =
                                                androidx.compose.material3.MaterialTheme.typography
                                                        .headlineMedium,
                                        color = Color.White.copy(alpha = 0.9f)
                                )
                                
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        // Library button
                                        androidx.compose.material3.IconButton(
                                                onClick = { navController.navigate("library") }
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.History,
                                                        contentDescription = "Recordings Library",
                                                        tint = Color(0xFF1d9bf0)
                                                )
                                        }
                                        
                                        // Settings button
                                        if (user != null) {
                                                androidx.compose.material3.IconButton(
                                                        onClick = { navController.navigate("settings") }
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Settings,
                                                                contentDescription = "Settings",
                                                                tint = Color(0xFF1d9bf0)
                                                        )
                                                }
                                        }
                                }
                        }

                        // Central Visuals (Avatar + Waveform)
                        Box(contentAlignment = Alignment.Center) {
                                // Glowing Waveform (Behind Avatar)
                                Waveform(
                                        amplitudes = amplitudes,
                                        modifier = Modifier.size(340.dp).padding(16.dp),
                                        color = Color(0xFF1d9bf0) // XBlue
                                )

                                // Avatar Placeholder (Steve Jobs style - simple, elegant)
                                Box(
                                        modifier =
                                                Modifier.size(140.dp)
                                                        .background(Color.Black, CircleShape)
                                                        .border(
                                                                2.dp,
                                                                Color(0xFF1d9bf0)
                                                                        .copy(alpha = 0.5f),
                                                                CircleShape
                                                        ) // Subtle blue border
                                                        .clip(CircleShape),
                                        contentAlignment = Alignment.Center
                                ) {
                                        if (avatarUrl != null) {
                                                AsyncImage(
                                                        model =
                                                                ImageRequest.Builder(context)
                                                                        .data(avatarUrl)
                                                                        .crossfade(true)
                                                                        .build(),
                                                        contentDescription = "Profile photo",
                                                        modifier = Modifier.fillMaxSize()
                                                )
                                        } else {
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
                                // Recording Timer
                                if (recordingState != RecordingState.Idle) {
                                        val minutes = (elapsedTime / 1000) / 60
                                        val seconds = (elapsedTime / 1000) % 60
                                        Text(
                                                text = String.format("%d:%02d", minutes, seconds),
                                                color = if (recordingState == RecordingState.Paused) 
                                                        Color(0xFFFFA500) // Orange when paused
                                                else Color(0xFF1DA1F2), // XBlue when recording
                                                style =
                                                        androidx.compose.material3.MaterialTheme
                                                                .typography
                                                                .displaySmall,
                                                fontWeight =
                                                        androidx.compose.ui.text.font.FontWeight
                                                                .Bold,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                }

                                Text(
                                        text =
                                                when {
                                                        isGenerating ->
                                                                statusMessage.ifBlank {
                                                                        "Crafting your video..."
                                                                }
                                                        recordingState == RecordingState.Paused ->
                                                                statusMessage.ifBlank {
                                                                        "Paused"
                                                                }
                                                        recordingState == RecordingState.Recording ->
                                                                statusMessage.ifBlank {
                                                                        "Recording..."
                                                                }
                                                        statusMessage.isNotBlank() -> statusMessage
                                                        else -> "Tap to Record"
                                                },
                                        color = Color.White.copy(alpha = 0.6f),
                                        style =
                                                androidx.compose.material3.MaterialTheme.typography
                                                        .bodyLarge,
                                        modifier = Modifier.padding(bottom = 32.dp)
                                )

                                // Control buttons row
                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Pause/Resume button (only shown when recording)
                                        if (recordingState == RecordingState.Recording || recordingState == RecordingState.Paused) {
                                                Button(
                                                        onClick = {
                                                                if (recordingState == RecordingState.Recording) {
                                                                        audioRecorderManager.pauseRecording()
                                                                } else if (recordingState == RecordingState.Paused) {
                                                                        audioRecorderManager.resumeRecording()
                                                                }
                                                        },
                                                        shape = CircleShape,
                                                        colors =
                                                                ButtonDefaults.buttonColors(
                                                                        containerColor = Color(0xFFFFA500) // Orange
                                                                ),
                                                        modifier = Modifier.size(64.dp),
                                                        elevation =
                                                                ButtonDefaults.buttonElevation(
                                                                        defaultElevation = 12.dp
                                                                ),
                                                        enabled = !isGenerating
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        if (recordingState == RecordingState.Recording) Icons.Default.Pause
                                                                        else Icons.Default.PlayArrow,
                                                                contentDescription = if (recordingState == RecordingState.Recording) "Pause" else "Resume",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(28.dp)
                                                        )
                                                }
                                        }

                                        // Stop/Start button
                                        Button(
                                                onClick = {
                                                        if (recordingState != RecordingState.Idle) {
                                                                val rawFile = currentRecordingFile
                                                                if (rawFile != null) {
                                                                        audioRecorderManager.stopRecording()
                                                                        isGenerating = true
                                                                        statusMessage =
                                                                                "Finalizing audio..."
                                                                        val duration =
                                                                                elapsedTime.coerceAtLeast(500L)
                                                                        scope.launch {
                                                                                runCatching {
                                                                                        recordingPipeline
                                                                                                .preparePreview(
                                                                                                        rawPcmFile =
                                                                                                                rawFile,
                                                                                                        durationMs =
                                                                                                                duration,
                                                                                                        avatarUrl =
                                                                                                                avatarUrl,
                                                                                                        amplitudes =
                                                                                                                amplitudes
                                                                                                                        .toList() // Pass copy of amplitudes
                                                                                                )
                                                                                }
                                                                                        .onSuccess { assets
                                                                                                ->
                                                                                                statusMessage =
                                                                                                        "Preview ready"
                                                                                                val audioParam =
                                                                                                        Uri.encode(
                                                                                                                assets.audioFile
                                                                                                                        .toUri()
                                                                                                                        .toString()
                                                                                                        )
                                                                                                val videoParam =
                                                                                                        Uri.encode(
                                                                                                                assets.videoFile
                                                                                                                        .toUri()
                                                                                                                        .toString()
                                                                                                        )
                                                                                                isGenerating =
                                                                                                        false
                                                                                                val amplitudesParam =
                                                                                                        Uri.encode(
                                                                                                                assets.amplitudesFile
                                                                                                                        .absolutePath
                                                                                                        )
                                                                                                navController
                                                                                                        .navigate(
                                                                                                                "preview?audio=$audioParam&video=$videoParam&duration=${assets.durationMs}&amplitudes=$amplitudesParam"
                                                                                                        )
                                                                                        }
                                                                                        .onFailure { error
                                                                                                ->
                                                                                                android.util
                                                                                                        .Log
                                                                                                        .e(
                                                                                                                "EchoX_Error",
                                                                                                                "Video generation failed",
                                                                                                                error
                                                                                                        )
                                                                                                error.printStackTrace()
                                                                                                statusMessage =
                                                                                                        "Unable to process recording: ${error.message}"
                                                                                                isGenerating =
                                                                                                        false
                                                                                        }
                                                                                }
                                                                        }
                                                                }
                                                        } else {
                                                                val permissionCheck =
                                                                        ContextCompat.checkSelfPermission(
                                                                                context,
                                                                                Manifest.permission
                                                                                        .RECORD_AUDIO
                                                                        )
                                                                if (permissionCheck ==
                                                                                PackageManager
                                                                                        .PERMISSION_GRANTED
                                                                ) {
                                                                        startRecordingSession()
                                                                } else {
                                                                        permissionLauncher.launch(
                                                                                Manifest.permission
                                                                                        .RECORD_AUDIO
                                                                        )
                                                                }
                                                        }
                                                },
                                                shape = CircleShape,
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        if (recordingState != RecordingState.Idle)
                                                                                Color(0xFFFF3B30) // iOS Red
                                                                        else Color(0xFF1d9bf0) // XBlue
                                                        ),
                                                modifier = Modifier.size(80.dp),
                                                elevation =
                                                        ButtonDefaults.buttonElevation(
                                                                defaultElevation = 12.dp
                                                        ),
                                                enabled = !isGenerating
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                if (recordingState != RecordingState.Idle) Icons.Default.Stop
                                                                else Icons.Default.Mic,
                                                        contentDescription = if (recordingState != RecordingState.Idle) "Stop" else "Record",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(36.dp)
                                                )
                                        }
                                }
                        }
                }
        }
}

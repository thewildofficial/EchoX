package com.echox.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.echox.app.domain.AudioRecorderManager
import com.echox.app.ui.components.Waveform
import java.io.File

@Composable
fun RecordScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioRecorderManager = remember { AudioRecorderManager() }

    var isRecording by remember { mutableStateOf(false) }
    val amplitudes = remember { mutableStateListOf<Float>() }

    // Permission Launcher
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        if (isGranted) {
                            val file = File(context.cacheDir, "recording.pcm")
                            audioRecorderManager.startRecording(file, scope)
                            isRecording = true
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
                    text = "EchoX",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.7f),
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

            // Bottom Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                        text = if (isRecording) "Recording..." else "Tap to Record",
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                        onClick = {
                            if (isRecording) {
                                audioRecorderManager.stopRecording()
                                isRecording = false
                                // Navigate to Preview (TODO)
                            } else {
                                val permissionCheck =
                                        ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                        )
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    val file = File(context.cacheDir, "recording.pcm")
                                    audioRecorderManager.startRecording(file, scope)
                                    isRecording = true
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
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
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

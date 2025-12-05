package com.echox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.echox.app.data.repository.XRepository
import com.echox.app.domain.AppPreferences
import com.echox.app.domain.VideoQuality
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavController, repository: XRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by repository.userProfile.collectAsState()
    val preferences = remember { AppPreferences(context) }

    var autoPostEnabled by remember { mutableStateOf(preferences.autoPostEnabled) }
    var videoQuality by remember { mutableStateOf(preferences.videoQuality) }

    val avatarUrl = user?.profile_image_url?.replace("_normal", "")

    Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF15202b)) // XDark
    ) {
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with back button
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                    )
                }
                Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Account Info Card
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e2732)), // XDark
                    shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                            text = "Account",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                    )

                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        // Avatar
                        Box(
                                modifier =
                                        Modifier.size(64.dp)
                                                .background(Color.Black, CircleShape)
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
                                        modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // User Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = user?.name ?: "Unknown",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                            )
                            Text(
                                    text = "@${user?.username ?: "unknown"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // App Preferences Card
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e2732)), // XDark
                    shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                            text = "Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                    )

                    // Auto-post toggle
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = "Auto-post",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                            )
                            Text(
                                    text = "Automatically share recordings after creation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                                checked = autoPostEnabled,
                                onCheckedChange = {
                                    autoPostEnabled = it
                                    preferences.autoPostEnabled = it
                                },
                                colors =
                                        androidx.compose.material3.SwitchDefaults.colors(
                                                checkedThumbColor = Color(0xFF1d9bf0),
                                                checkedTrackColor = Color(0xFF1d9bf0).copy(alpha = 0.5f)
                                        )
                        )
                    }

                    // Video quality selector
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                                text = "Video Quality",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                    onClick = {
                                        videoQuality = VideoQuality.P720
                                        preferences.videoQuality = VideoQuality.P720
                                    },
                                    colors =
                                            ButtonDefaults.textButtonColors(
                                                    contentColor =
                                                            if (videoQuality == VideoQuality.P720)
                                                                    Color(0xFF1d9bf0)
                                                            else Color.White.copy(alpha = 0.6f)
                                            ),
                                    modifier =
                                            Modifier.weight(1f)
                                                    .height(40.dp)
                                                    .background(
                                                            if (videoQuality == VideoQuality.P720)
                                                                    Color(0xFF1d9bf0).copy(alpha = 0.2f)
                                                            else Color.Transparent,
                                                            RoundedCornerShape(8.dp)
                                                    )
                            ) {
                                Text("720p", fontWeight = FontWeight.Medium)
                            }
                            TextButton(
                                    onClick = {
                                        videoQuality = VideoQuality.P1080
                                        preferences.videoQuality = VideoQuality.P1080
                                    },
                                    colors =
                                            ButtonDefaults.textButtonColors(
                                                    contentColor =
                                                            if (videoQuality == VideoQuality.P1080)
                                                                    Color(0xFF1d9bf0)
                                                            else Color.White.copy(alpha = 0.6f)
                                            ),
                                    modifier =
                                            Modifier.weight(1f)
                                                    .height(40.dp)
                                                    .background(
                                                            if (videoQuality == VideoQuality.P1080)
                                                                    Color(0xFF1d9bf0).copy(alpha = 0.2f)
                                                            else Color.Transparent,
                                                            RoundedCornerShape(8.dp)
                                                    )
                            ) {
                                Text("1080p", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Logout Button
            Button(
                    onClick = {
                        scope.launch {
                            repository.logout()
                            // Navigation will auto-redirect to Login due to LaunchedEffect in
                            // Navigation.kt
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF3B30) // Red for logout
                            ),
                    shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                        text = "Logout",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

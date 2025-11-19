
package com.echox.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.echox.app.data.repository.XRepository
import com.echox.app.domain.AuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController, repository: XRepository) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val isConfigured = authManager.isConfigured
    val configurationError = authManager.configurationError
    var isAuthenticating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { authManager.dispose() }
    }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data == null) {
            Toast.makeText(context, "Authentication canceled.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        isAuthenticating = true
        authManager.handleAuthResponse(
            intent = data,
            repository = repository,
            onSuccess = {
                scope.launch {
                    repository.refreshUserProfile()
                    isAuthenticating = false
                    navController.navigate("record") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            },
            onError = { message ->
                isAuthenticating = false
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // X Logo / EchoX Branding
            Text(
                text = "𝕏",
                fontSize = 64.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "Log in to EchoX",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Create and share audio notes seamlessly.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                enabled = isConfigured && !isAuthenticating,
                onClick = {
                    val authIntent = authManager.getAuthIntent()
                    if (authIntent != null) {
                        authLauncher.launch(authIntent)
                    } else {
                        configurationError?.let {
                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConfigured) Color.White else Color.White.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .width(280.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = if (isAuthenticating) "Connecting…" else "Sign in with X",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            configurationError?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = it,
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp
                )
            }
        }
    }
}

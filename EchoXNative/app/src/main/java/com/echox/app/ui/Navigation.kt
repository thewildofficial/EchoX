package com.echox.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.echox.app.ui.screens.RecordScreen

@Composable
fun Navigation() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "record") {
        composable("record") {
            RecordScreen(navController)
        }
        // Add other screens here
    }
}

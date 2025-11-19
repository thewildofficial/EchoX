package com.echox.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echox.app.data.repository.XRepository
import com.echox.app.ui.screens.LoginScreen
import com.echox.app.ui.screens.PreviewScreen
import com.echox.app.ui.screens.RecordScreen

@Composable
fun Navigation() {
    val context = LocalContext.current
    val repository = remember { XRepository(context) }
    val isAuthenticated by repository.isAuthenticated.collectAsState()

    val navController = rememberNavController()
    LaunchedEffect(isAuthenticated) {
        val target = if (isAuthenticated) "record" else "login"
        val current = navController.currentDestination?.route
        if (current != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
        if (isAuthenticated) {
            repository.refreshUserProfile()
        }
    }

    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(navController, repository) }
        composable("record") { RecordScreen(navController, repository) }
        composable(
            route = "preview?audio={audio}&video={video}&duration={duration}",
            arguments =
                listOf(
                    navArgument("audio") { type = NavType.StringType; defaultValue = "" },
                    navArgument("video") { type = NavType.StringType; defaultValue = "" },
                    navArgument("duration") { type = NavType.LongType; defaultValue = 0L }
                )
        ) { entry ->
            val audio = entry.arguments?.getString("audio")
            val video = entry.arguments?.getString("video")
            val duration = entry.arguments?.getLong("duration") ?: 0L
            PreviewScreen(navController, repository, audio, video, duration)
        }
    }
}

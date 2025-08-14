package com.astralstream.player.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.astralstream.player.ui.screens.HomeScreen
import com.astralstream.player.ui.screens.LibraryScreen
import com.astralstream.player.ui.screens.FolderBrowserScreen
import com.astralstream.player.ui.screens.SimplifiedFileExplorerScreen
import com.astralstream.player.ui.screens.SearchScreen
import com.astralstream.player.ui.screens.SettingsScreen
import com.astralstream.player.ui.screens.settings.VideoSettingsScreen
import com.astralstream.player.ui.screens.settings.AudioSettingsScreen
import com.astralstream.player.ui.screens.settings.PrivacySettingsScreen
import com.astralstream.player.ui.screens.settings.AIFeaturesScreen
import com.astralstream.player.ui.screens.settings.AboutScreen

/**
 * Navigation host for AstralStream app
 */
@Composable
fun AstralStreamNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = "home",
    onPermissionRequest: (List<String>) -> Unit = {},
    onNavigateToPlayer: (Uri) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToLibrary = { navController.navigate("library") },
                onNavigateToFolders = { navController.navigate("folders") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToAIFeatures = { navController.navigate("ai_features") },
                onPlayVideo = onNavigateToPlayer,
                onPermissionRequest = onPermissionRequest
            )
        }
        
        composable("library") {
            LibraryScreen(
                onBackPressed = { navController.popBackStack() },
                onPlayVideo = onNavigateToPlayer
            )
        }
        
        composable("folders") {
            SimplifiedFileExplorerScreen(
                onVideoClick = { videoPath ->
                    onNavigateToPlayer(videoPath.toUri())
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                }
            )
        }
        
        composable("search") {
            SearchScreen(
                onBackPressed = { navController.popBackStack() },
                onVideoClick = { videoPath ->
                    onNavigateToPlayer(videoPath.toUri())
                }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onBackPressed = { navController.popBackStack() },
                onNavigateToVideoSettings = { navController.navigate("video_settings") },
                onNavigateToAudioSettings = { navController.navigate("audio_settings") },
                onNavigateToPrivacySettings = { navController.navigate("privacy_settings") },
                onNavigateToAIFeatures = { navController.navigate("ai_features") },
                onNavigateToAbout = { navController.navigate("about") }
            )
        }
        
        composable("video_settings") {
            VideoSettingsScreen(
                onBackPressed = { navController.popBackStack() }
            )
        }
        
        composable("audio_settings") {
            AudioSettingsScreen(
                onBackPressed = { navController.popBackStack() }
            )
        }
        
        composable("privacy_settings") {
            PrivacySettingsScreen(
                onBackPressed = { navController.popBackStack() }
            )
        }
        
        composable("ai_features") {
            AIFeaturesScreen(
                onBackPressed = { navController.popBackStack() }
            )
        }
        
        composable("about") {
            AboutScreen(
                onBackPressed = { navController.popBackStack() }
            )
        }
    }
}
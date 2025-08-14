package com.astralstream.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings screen for app configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit,
    onNavigateToVideoSettings: () -> Unit = {},
    onNavigateToAudioSettings: () -> Unit = {},
    onNavigateToPrivacySettings: () -> Unit = {},
    onNavigateToAIFeatures: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            SettingsItem(
                title = "Video Settings",
                subtitle = "Configure video playback options",
                onClick = onNavigateToVideoSettings
            )
            
            SettingsItem(
                title = "Audio Settings", 
                subtitle = "Configure audio playback options",
                onClick = onNavigateToAudioSettings
            )
            
            SettingsItem(
                title = "Privacy & Security",
                subtitle = "Manage private folders and security",
                onClick = onNavigateToPrivacySettings
            )
            
            SettingsItem(
                title = "AI Features",
                subtitle = "Configure AI-powered enhancements",
                onClick = onNavigateToAIFeatures
            )
            
            SettingsItem(
                title = "About",
                subtitle = "App information and credits",
                onClick = onNavigateToAbout
            )
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
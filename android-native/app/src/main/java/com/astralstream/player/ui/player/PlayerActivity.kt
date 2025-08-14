package com.astralstream.player.ui.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.astralstream.player.ui.theme.AstralStreamTheme
import com.astralstream.player.player.viewmodel.PlayerViewModel
import com.astralstream.player.player.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Dedicated activity for video playback with full-screen support and PiP
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window for video playback
        configureWindow()

        setContent {
            AstralStreamTheme {
                var isFullscreen by remember { mutableStateOf(false) }
                
                LaunchedEffect(isFullscreen) {
                    toggleFullscreen(isFullscreen)
                }

                BackHandler {
                    if (isFullscreen) {
                        isFullscreen = false
                    } else {
                        handleBackPressed()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onFullscreenToggle = { isFullscreen = it },
                        onBackPressed = ::handleBackPressed,
                        onPictureInPicture = { startPictureInPictureMode() }
                    )
                }
            }
        }

        // Handle intent data
        handleIntent(intent)

        // Observe player state for PiP
        observePlayerState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Enter PiP mode if video is playing
        if (viewModel.shouldEnterPipMode()) {
            startPictureInPictureMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setPictureInPictureMode(isInPictureInPictureMode)
        
        if (!isInPictureInPictureMode) {
            // User has closed PiP window, stop playback and close activity
            viewModel.pausePlayback()
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        // Handle background behavior
        if (isInPictureInPictureMode) {
            // Keep playing in PiP mode
        } else {
            // Pause if not in PiP
            viewModel.pausePlayback()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.releasePlayer()
    }

    private fun configureWindow() {
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Handle hardware acceleration
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    }

    private fun toggleFullscreen(isFullscreen: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        if (isFullscreen) {
            // Hide system UI
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            // Lock orientation based on video aspect ratio
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            // Show system UI
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            viewModel.setMedia(uri.toString())
        }
    }

    private fun observePlayerState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collect { state ->
                    // Update PiP params based on video aspect ratio
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        updatePictureInPictureParams(viewModel.videoSize)
                    }
                }
            }
        }
    }

    private fun startPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params: PictureInPictureParams = PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            super.enterPictureInPictureMode(params)
        }
    }

    private fun updatePictureInPictureParams(videoSize: Pair<Int, Int>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params: PictureInPictureParams = PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(videoSize.first, videoSize.second))
                .build()
            setPictureInPictureParams(params)
        }
    }

    private fun handleBackPressed() {
        if (viewModel.playerState.value.isPlaying) {
            // Minimize to background or PiP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode()
            } else {
                moveTaskToBack(true)
            }
        } else {
            finish()
        }
    }
}
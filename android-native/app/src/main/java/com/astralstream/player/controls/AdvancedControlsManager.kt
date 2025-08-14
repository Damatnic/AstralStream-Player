package com.astralstream.player.controls

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced controls manager with playback speed control, sleep timer,
 * kids lock mode, and network streaming support
 */
@Singleton
class AdvancedControlsManager @Inject constructor(
    private val context: Context
) {
    
    private val _controlsState = MutableStateFlow(AdvancedControlsState())
    val controlsState: StateFlow<AdvancedControlsState> = _controlsState.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var sleepTimerJob: Job? = null
    private var networkMonitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Biometric authentication
    private var biometricManager: BiometricManager? = null
    
    companion object {
        private const val TAG = "AdvancedControlsManager"
        
        // Playback speed presets
        val PLAYBACK_SPEEDS = floatArrayOf(
            0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f
        )
        
        // Sleep timer presets (in minutes)
        val SLEEP_TIMER_PRESETS = intArrayOf(
            5, 10, 15, 30, 45, 60, 90, 120
        )
        
        // Network quality thresholds (in Mbps)
        private const val POOR_CONNECTION_THRESHOLD = 1.0
        private const val GOOD_CONNECTION_THRESHOLD = 5.0
        private const val EXCELLENT_CONNECTION_THRESHOLD = 25.0
    }
    
    init {
        initializeBiometricManager()
        startNetworkMonitoring()
        initializeWakeLock()
    }
    
    private fun initializeBiometricManager() {
        biometricManager = BiometricManager.from(context)
        
        val biometricCapability = when (biometricManager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NO_BIOMETRICS_ENROLLED
            else -> BiometricCapability.UNKNOWN
        }
        
        _controlsState.value = _controlsState.value.copy(
            biometricCapability = biometricCapability
        )
        
        Log.d(TAG, "Biometric capability: $biometricCapability")
    }
    
    private fun initializeWakeLock() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AstralStream::PlayerWakeLock"
        )
    }
    
    // Playback speed control
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
        _controlsState.value = _controlsState.value.copy(
            currentPlaybackSpeed = clampedSpeed
        )
        
        Log.d(TAG, "Playback speed set to: ${clampedSpeed}x")
    }
    
    fun getPlaybackSpeedPresets(): FloatArray = PLAYBACK_SPEEDS
    
    fun cyclePlaybackSpeed(forward: Boolean = true) {
        val currentSpeed = _controlsState.value.currentPlaybackSpeed
        val currentIndex = PLAYBACK_SPEEDS.indexOfFirst { it == currentSpeed }.takeIf { it >= 0 } ?: 3 // Default to 1.0x
        
        val newIndex = if (forward) {
            if (currentIndex < PLAYBACK_SPEEDS.size - 1) currentIndex + 1 else 0
        } else {
            if (currentIndex > 0) currentIndex - 1 else PLAYBACK_SPEEDS.size - 1
        }
        
        setPlaybackSpeed(PLAYBACK_SPEEDS[newIndex])
    }
    
    fun resetPlaybackSpeed() {
        setPlaybackSpeed(1.0f)
    }
    
    // Sleep timer implementation
    fun setSleepTimer(minutes: Int) {
        // Cancel existing timer
        sleepTimerJob?.cancel()
        
        if (minutes > 0) {
            val endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())
            
            sleepTimerJob = coroutineScope.launch {
                _controlsState.value = _controlsState.value.copy(
                    isSleepTimerActive = true,
                    sleepTimerMinutes = minutes,
                    sleepTimerEndTime = endTime
                )
                
                // Update countdown every second
                while (System.currentTimeMillis() < endTime) {
                    val remainingMs = endTime - System.currentTimeMillis()
                    val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs).toInt()
                    val remainingSeconds = (TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60).toInt()
                    
                    _controlsState.value = _controlsState.value.copy(
                        sleepTimerRemainingMinutes = remainingMinutes,
                        sleepTimerRemainingSeconds = remainingSeconds
                    )
                    
                    delay(1000)
                }
                
                // Timer expired - trigger sleep action
                onSleepTimerExpired()
            }
            
            Log.d(TAG, "Sleep timer set for $minutes minutes")
        } else {
            _controlsState.value = _controlsState.value.copy(
                isSleepTimerActive = false,
                sleepTimerMinutes = 0,
                sleepTimerEndTime = 0L,
                sleepTimerRemainingMinutes = 0,
                sleepTimerRemainingSeconds = 0
            )
            
            Log.d(TAG, "Sleep timer cancelled")
        }
    }
    
    private fun onSleepTimerExpired() {
        _controlsState.value = _controlsState.value.copy(
            isSleepTimerActive = false,
            sleepTimerExpired = true
        )
        
        // Release wake lock when sleep timer expires
        releaseWakeLock()
        
        Log.d(TAG, "Sleep timer expired")
    }
    
    fun acknowledgeTimerExpiration() {
        _controlsState.value = _controlsState.value.copy(
            sleepTimerExpired = false
        )
    }
    
    fun getSleepTimerPresets(): IntArray = SLEEP_TIMER_PRESETS
    
    fun extendSleepTimer(additionalMinutes: Int) {
        if (_controlsState.value.isSleepTimerActive) {
            val currentMinutes = _controlsState.value.sleepTimerRemainingMinutes
            setSleepTimer(currentMinutes + additionalMinutes)
        }
    }
    
    // Kids lock mode
    fun enableKidsLock(fragment: Fragment, callback: (Boolean) -> Unit) {
        if (_controlsState.value.biometricCapability == BiometricCapability.AVAILABLE) {
            showBiometricPrompt(fragment, "Enable Kids Lock", "Authenticate to enable parental controls") { success ->
                if (success) {
                    _controlsState.value = _controlsState.value.copy(
                        isKidsLockEnabled = true,
                        kidsLockAuthTime = System.currentTimeMillis()
                    )
                    acquireWakeLock() // Prevent screen from turning off
                    Log.d(TAG, "Kids lock enabled")
                }
                callback(success)
            }
        } else {
            // Fallback to simple confirmation
            _controlsState.value = _controlsState.value.copy(
                isKidsLockEnabled = true,
                kidsLockAuthTime = System.currentTimeMillis()
            )
            acquireWakeLock()
            callback(true)
            Log.d(TAG, "Kids lock enabled (no biometric)")
        }
    }
    
    fun disableKidsLock(fragment: Fragment, callback: (Boolean) -> Unit) {
        if (_controlsState.value.biometricCapability == BiometricCapability.AVAILABLE) {
            showBiometricPrompt(fragment, "Disable Kids Lock", "Authenticate to disable parental controls") { success ->
                if (success) {
                    _controlsState.value = _controlsState.value.copy(
                        isKidsLockEnabled = false,
                        kidsLockAuthTime = 0L
                    )
                    releaseWakeLock()
                    Log.d(TAG, "Kids lock disabled")
                }
                callback(success)
            }
        } else {
            _controlsState.value = _controlsState.value.copy(
                isKidsLockEnabled = false,
                kidsLockAuthTime = 0L
            )
            releaseWakeLock()
            callback(true)
            Log.d(TAG, "Kids lock disabled (no biometric)")
        }
    }
    
    private fun showBiometricPrompt(
        fragment: Fragment,
        title: String,
        subtitle: String,
        callback: (Boolean) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(fragment, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback(true)
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback(false)
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback(false)
            }
        })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    fun isKidsLockActive(): Boolean {
        val state = _controlsState.value
        return state.isKidsLockEnabled && 
               (System.currentTimeMillis() - state.kidsLockAuthTime) < TimeUnit.HOURS.toMillis(1) // 1 hour timeout
    }
    
    // Network streaming support
    private fun startNetworkMonitoring() {
        networkMonitorJob = coroutineScope.launch {
            while (true) {
                updateNetworkStatus()
                delay(5000) // Check every 5 seconds
            }
        }
    }
    
    private fun updateNetworkStatus() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        
        val networkInfo = when {
            networkCapabilities == null -> {
                NetworkInfo(
                    isConnected = false,
                    connectionType = ConnectionType.NONE,
                    quality = NetworkQuality.NO_CONNECTION,
                    estimatedBandwidth = 0.0
                )
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val bandwidth = estimateBandwidth(networkCapabilities)
                NetworkInfo(
                    isConnected = true,
                    connectionType = ConnectionType.WIFI,
                    quality = getNetworkQuality(bandwidth),
                    estimatedBandwidth = bandwidth
                )
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val bandwidth = estimateBandwidth(networkCapabilities)
                NetworkInfo(
                    isConnected = true,
                    connectionType = ConnectionType.CELLULAR,
                    quality = getNetworkQuality(bandwidth),
                    estimatedBandwidth = bandwidth
                )
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                val bandwidth = estimateBandwidth(networkCapabilities)
                NetworkInfo(
                    isConnected = true,
                    connectionType = ConnectionType.ETHERNET,
                    quality = getNetworkQuality(bandwidth),
                    estimatedBandwidth = bandwidth
                )
            }
            else -> {
                NetworkInfo(
                    isConnected = true,
                    connectionType = ConnectionType.OTHER,
                    quality = NetworkQuality.UNKNOWN,
                    estimatedBandwidth = 0.0
                )
            }
        }
        
        _controlsState.value = _controlsState.value.copy(
            networkInfo = networkInfo
        )
    }
    
    private fun estimateBandwidth(capabilities: NetworkCapabilities): Double {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Use actual bandwidth if available
            val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
            if (downstreamKbps > 0) {
                downstreamKbps / 1000.0 // Convert to Mbps
            } else {
                // Fallback estimates based on transport type
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 50.0
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 100.0
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 10.0
                    else -> 1.0
                }
            }
        } else {
            // Fallback for older Android versions
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 25.0
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 50.0
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 5.0
                else -> 1.0
            }
        }
    }
    
    private fun getNetworkQuality(bandwidthMbps: Double): NetworkQuality {
        return when {
            bandwidthMbps >= EXCELLENT_CONNECTION_THRESHOLD -> NetworkQuality.EXCELLENT
            bandwidthMbps >= GOOD_CONNECTION_THRESHOLD -> NetworkQuality.GOOD
            bandwidthMbps >= POOR_CONNECTION_THRESHOLD -> NetworkQuality.POOR
            else -> NetworkQuality.VERY_POOR
        }
    }
    
    fun isStreamingRecommended(): Boolean {
        val networkInfo = _controlsState.value.networkInfo
        return networkInfo.isConnected && networkInfo.quality != NetworkQuality.VERY_POOR
    }
    
    fun getRecommendedStreamingQuality(): StreamingQuality {
        val networkInfo = _controlsState.value.networkInfo
        return when (networkInfo.quality) {
            NetworkQuality.EXCELLENT -> StreamingQuality.UHD_4K
            NetworkQuality.GOOD -> StreamingQuality.FULL_HD
            NetworkQuality.POOR -> StreamingQuality.HD
            NetworkQuality.VERY_POOR -> StreamingQuality.SD
            NetworkQuality.NO_CONNECTION -> StreamingQuality.OFFLINE
            NetworkQuality.UNKNOWN -> StreamingQuality.AUTO
        }
    }
    
    // Wake lock management
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(TimeUnit.HOURS.toMillis(2)) // 2 hour timeout
                Log.d(TAG, "Wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
    
    // Picture-in-Picture support
    fun setPictureInPictureEnabled(enabled: Boolean) {
        _controlsState.value = _controlsState.value.copy(
            isPictureInPictureEnabled = enabled
        )
    }
    
    fun shouldEnterPictureInPicture(): Boolean {
        val state = _controlsState.value
        return state.isPictureInPictureEnabled && 
               !state.isKidsLockEnabled && 
               state.networkInfo.isConnected
    }
    
    // Background playback
    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        _controlsState.value = _controlsState.value.copy(
            isBackgroundPlaybackEnabled = enabled
        )
        
        if (enabled) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }
    
    // Auto-pause on phone calls
    fun setAutoPauseOnCallEnabled(enabled: Boolean) {
        _controlsState.value = _controlsState.value.copy(
            isAutoPauseOnCallEnabled = enabled
        )
    }
    
    fun release() {
        sleepTimerJob?.cancel()
        networkMonitorJob?.cancel()
        releaseWakeLock()
        wakeLock = null
        coroutineScope.cancel()
        
        _controlsState.value = AdvancedControlsState()
    }
}

// Data classes and enums
data class AdvancedControlsState(
    // Playback speed
    val currentPlaybackSpeed: Float = 1.0f,
    
    // Sleep timer
    val isSleepTimerActive: Boolean = false,
    val sleepTimerMinutes: Int = 0,
    val sleepTimerEndTime: Long = 0L,
    val sleepTimerRemainingMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0,
    val sleepTimerExpired: Boolean = false,
    
    // Kids lock
    val isKidsLockEnabled: Boolean = false,
    val kidsLockAuthTime: Long = 0L,
    val biometricCapability: BiometricCapability = BiometricCapability.UNKNOWN,
    
    // Network streaming
    val networkInfo: NetworkInfo = NetworkInfo(),
    
    // Advanced features
    val isPictureInPictureEnabled: Boolean = true,
    val isBackgroundPlaybackEnabled: Boolean = false,
    val isAutoPauseOnCallEnabled: Boolean = true
)

enum class BiometricCapability {
    AVAILABLE, NO_HARDWARE, HARDWARE_UNAVAILABLE, NO_BIOMETRICS_ENROLLED, UNKNOWN
}

data class NetworkInfo(
    val isConnected: Boolean = false,
    val connectionType: ConnectionType = ConnectionType.NONE,
    val quality: NetworkQuality = NetworkQuality.NO_CONNECTION,
    val estimatedBandwidth: Double = 0.0 // Mbps
)

enum class ConnectionType {
    NONE, WIFI, CELLULAR, ETHERNET, OTHER
}

enum class NetworkQuality {
    NO_CONNECTION, VERY_POOR, POOR, GOOD, EXCELLENT, UNKNOWN
}

enum class StreamingQuality {
    OFFLINE, SD, HD, FULL_HD, UHD_4K, AUTO
}
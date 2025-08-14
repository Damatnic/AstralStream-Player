package com.astralstream.player

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.astralstream.player.analytics.AnalyticsManager
import com.astralstream.player.data.database.AstralStreamDatabase
import com.astralstream.player.sync.CloudSyncManager
import com.astralstream.player.theme.ThemeEngine
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for AstralStream video player
 * Handles dependency injection and global configuration
 */
@HiltAndroidApp
class AstralStreamApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var database: AstralStreamDatabase
    
    @Inject
    lateinit var analyticsManager: AnalyticsManager
    
    @Inject
    lateinit var cloudSyncManager: CloudSyncManager
    
    @Inject
    lateinit var themeEngine: ThemeEngine

    override fun onCreate() {
        super.onCreate()
        
        // Setup strict mode in debug builds
        if (BuildConfig.DEBUG) {
            setupStrictMode()
        }
        
        // Initialize global components
        setupCrashReporting()
        setupAnalytics()
        initializeThemeEngine()
        initializeCloudSync()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    private fun setupCrashReporting() {
        if (BuildConfig.CRASH_REPORTING_ENABLED) {
            FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
                setUserId(analyticsManager.getAnonymousUserId())
            }
        }
    }

    private fun setupAnalytics() {
        if (BuildConfig.ANALYTICS_ENABLED) {
            analyticsManager.initialize()
            analyticsManager.trackAppLaunch()
        }
    }
    
    private fun initializeThemeEngine() {
        themeEngine.initialize()
    }
    
    private fun initializeCloudSync() {
        cloudSyncManager.initialize()
    }
    
    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Handle memory pressure
        when (level) {
            TRIM_MEMORY_UI_HIDDEN,
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                // Clear caches and free memory
                themeEngine.clearImageCache()
                System.gc()
            }
        }
    }
}
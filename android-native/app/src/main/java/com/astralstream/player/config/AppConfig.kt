package com.astralstream.player.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized configuration management for API keys and app settings
 */
@Singleton
class AppConfig @Inject constructor(
    private val context: Context
) {
    // Use regular SharedPreferences with basic obfuscation for API keys
    // In production, use Android Keystore or proper encryption
    private val securePrefs: SharedPreferences = context.getSharedPreferences(
        "astralstream_secure_prefs",
        Context.MODE_PRIVATE
    )
    
    private val regularPrefs: SharedPreferences = context.getSharedPreferences(
        "astralstream_prefs",
        Context.MODE_PRIVATE
    )
    
    // Basic obfuscation for API keys (not secure, but better than plaintext)
    private fun obfuscate(value: String): String {
        return Base64.encodeToString(value.toByteArray(), Base64.DEFAULT)
    }
    
    private fun deobfuscate(value: String): String {
        return String(Base64.decode(value, Base64.DEFAULT))
    }
    
    companion object {
        // API Key storage keys
        private const val KEY_TMDB_API = "tmdb_api_key"
        private const val KEY_OMDB_API = "omdb_api_key"
        private const val KEY_OPENSUBTITLES_API = "opensubtitles_api_key"
        private const val KEY_YOUTUBE_API = "youtube_api_key"
        
        // Feature flags
        private const val KEY_AI_FEATURES_ENABLED = "ai_features_enabled"
        private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        
        // Network settings
        private const val KEY_DOWNLOAD_OVER_WIFI_ONLY = "download_wifi_only"
        private const val KEY_STREAMING_QUALITY = "streaming_quality"
        private const val KEY_CACHE_SIZE_MB = "cache_size_mb"
        
        // Default values
        const val DEFAULT_CACHE_SIZE_MB = 500
        const val DEFAULT_STREAMING_QUALITY = "auto"
    }
    
    // API Key Management with basic obfuscation
    fun getTmdbApiKey(): String? {
        val obfuscated = securePrefs.getString(KEY_TMDB_API, null)
        return obfuscated?.let { deobfuscate(it) }
    }
    
    fun setTmdbApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_TMDB_API, obfuscate(apiKey)).apply()
    }
    
    fun getOmdbApiKey(): String? {
        val obfuscated = securePrefs.getString(KEY_OMDB_API, null)
        return obfuscated?.let { deobfuscate(it) }
    }
    
    fun setOmdbApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_OMDB_API, obfuscate(apiKey)).apply()
    }
    
    fun getOpenSubtitlesApiKey(): String? {
        val obfuscated = securePrefs.getString(KEY_OPENSUBTITLES_API, null)
        return obfuscated?.let { deobfuscate(it) }
    }
    
    fun setOpenSubtitlesApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_OPENSUBTITLES_API, obfuscate(apiKey)).apply()
    }
    
    fun getYouTubeApiKey(): String? {
        val obfuscated = securePrefs.getString(KEY_YOUTUBE_API, null)
        return obfuscated?.let { deobfuscate(it) }
    }
    
    fun setYouTubeApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_YOUTUBE_API, obfuscate(apiKey)).apply()
    }
    
    // Feature Flags
    fun isAiFeaturesEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_AI_FEATURES_ENABLED, false)
    }
    
    fun setAiFeaturesEnabled(enabled: Boolean) {
        regularPrefs.edit().putBoolean(KEY_AI_FEATURES_ENABLED, enabled).apply()
    }
    
    fun isCloudSyncEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false)
    }
    
    fun setCloudSyncEnabled(enabled: Boolean) {
        regularPrefs.edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, enabled).apply()
    }
    
    fun isAnalyticsEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_ANALYTICS_ENABLED, false)
    }
    
    fun setAnalyticsEnabled(enabled: Boolean) {
        regularPrefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, enabled).apply()
    }
    
    // Network Settings
    fun isDownloadWifiOnly(): Boolean {
        return regularPrefs.getBoolean(KEY_DOWNLOAD_OVER_WIFI_ONLY, true)
    }
    
    fun setDownloadWifiOnly(wifiOnly: Boolean) {
        regularPrefs.edit().putBoolean(KEY_DOWNLOAD_OVER_WIFI_ONLY, wifiOnly).apply()
    }
    
    fun getStreamingQuality(): String {
        return regularPrefs.getString(KEY_STREAMING_QUALITY, DEFAULT_STREAMING_QUALITY) 
            ?: DEFAULT_STREAMING_QUALITY
    }
    
    fun setStreamingQuality(quality: String) {
        regularPrefs.edit().putString(KEY_STREAMING_QUALITY, quality).apply()
    }
    
    fun getCacheSizeMB(): Int {
        return regularPrefs.getInt(KEY_CACHE_SIZE_MB, DEFAULT_CACHE_SIZE_MB)
    }
    
    fun setCacheSizeMB(sizeMB: Int) {
        regularPrefs.edit().putInt(KEY_CACHE_SIZE_MB, sizeMB).apply()
    }
    
    // Validation
    fun hasRequiredApiKeys(): Boolean {
        // At minimum, we need either TMDB or OMDB for metadata
        return getTmdbApiKey() != null || getOmdbApiKey() != null
    }
    
    fun clearAllApiKeys() {
        securePrefs.edit()
            .remove(KEY_TMDB_API)
            .remove(KEY_OMDB_API)
            .remove(KEY_OPENSUBTITLES_API)
            .remove(KEY_YOUTUBE_API)
            .apply()
    }
    
    fun clearAllSettings() {
        regularPrefs.edit().clear().apply()
        securePrefs.edit().clear().apply()
    }
    
    // First run setup
    fun isFirstRun(): Boolean {
        val isFirst = regularPrefs.getBoolean("first_run", true)
        if (isFirst) {
            regularPrefs.edit().putBoolean("first_run", false).apply()
        }
        return isFirst
    }
    
    // Export/Import settings (for backup, excluding API keys)
    fun exportSettings(): Map<String, Any> {
        val settings = mutableMapOf<String, Any>()
        regularPrefs.all.forEach { (key, value) ->
            if (value != null && key != "first_run") {
                settings[key] = value
            }
        }
        return settings
    }
    
    fun importSettings(settings: Map<String, Any>) {
        val editor = regularPrefs.edit()
        settings.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }
}
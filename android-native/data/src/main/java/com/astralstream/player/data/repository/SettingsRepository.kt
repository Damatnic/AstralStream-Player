package com.astralstream.player.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    // Playback settings
    suspend fun setPlaybackSpeed(speed: Float)
    fun getPlaybackSpeed(): Flow<Float>
    
    suspend fun setAutoPlay(enabled: Boolean)
    fun getAutoPlay(): Flow<Boolean>
    
    suspend fun setRememberPosition(enabled: Boolean)
    fun getRememberPosition(): Flow<Boolean>
    
    // Display settings
    suspend fun setThemeMode(mode: ThemeMode)
    fun getThemeMode(): Flow<ThemeMode>
    
    suspend fun setBrightnessLevel(level: Float)
    fun getBrightnessLevel(): Flow<Float>
    
    // Audio settings
    suspend fun setDefaultAudioTrack(language: String?)
    fun getDefaultAudioTrack(): Flow<String?>
    
    // Subtitle settings
    suspend fun setDefaultSubtitleLanguage(language: String?)
    fun getDefaultSubtitleLanguage(): Flow<String?>
    
    suspend fun setSubtitleSize(size: Float)
    fun getSubtitleSize(): Flow<Float>
    
    // Storage settings
    suspend fun setDefaultStoragePath(path: String)
    fun getDefaultStoragePath(): Flow<String?>
    
    // Privacy settings
    suspend fun setPrivateModeEnabled(enabled: Boolean)
    fun getPrivateModeEnabled(): Flow<Boolean>
    
    suspend fun setBiometricEnabled(enabled: Boolean)
    fun getBiometricEnabled(): Flow<Boolean>
    
    // Clear all settings
    suspend fun clearAllSettings()
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}
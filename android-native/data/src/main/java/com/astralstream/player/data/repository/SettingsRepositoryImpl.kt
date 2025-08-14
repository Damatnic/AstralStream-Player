package com.astralstream.player.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val AUTO_PLAY = booleanPreferencesKey("auto_play")
        private val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val BRIGHTNESS_LEVEL = floatPreferencesKey("brightness_level")
        private val DEFAULT_AUDIO_TRACK = stringPreferencesKey("default_audio_track")
        private val DEFAULT_SUBTITLE_LANGUAGE = stringPreferencesKey("default_subtitle_language")
        private val SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")
        private val DEFAULT_STORAGE_PATH = stringPreferencesKey("default_storage_path")
        private val PRIVATE_MODE_ENABLED = booleanPreferencesKey("private_mode_enabled")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[PLAYBACK_SPEED] = speed
        }
    }

    override fun getPlaybackSpeed(): Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PLAYBACK_SPEED] ?: 1.0f
        }

    override suspend fun setAutoPlay(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_PLAY] = enabled
        }
    }

    override fun getAutoPlay(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[AUTO_PLAY] ?: true
        }

    override suspend fun setRememberPosition(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[REMEMBER_POSITION] = enabled
        }
    }

    override fun getRememberPosition(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[REMEMBER_POSITION] ?: true
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    override fun getThemeMode(): Flow<ThemeMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val modeName = preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(modeName)
        }

    override suspend fun setBrightnessLevel(level: Float) {
        dataStore.edit { preferences ->
            preferences[BRIGHTNESS_LEVEL] = level
        }
    }

    override fun getBrightnessLevel(): Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[BRIGHTNESS_LEVEL] ?: 0.5f
        }

    override suspend fun setDefaultAudioTrack(language: String?) {
        dataStore.edit { preferences ->
            if (language != null) {
                preferences[DEFAULT_AUDIO_TRACK] = language
            } else {
                preferences.remove(DEFAULT_AUDIO_TRACK)
            }
        }
    }

    override fun getDefaultAudioTrack(): Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[DEFAULT_AUDIO_TRACK]
        }

    override suspend fun setDefaultSubtitleLanguage(language: String?) {
        dataStore.edit { preferences ->
            if (language != null) {
                preferences[DEFAULT_SUBTITLE_LANGUAGE] = language
            } else {
                preferences.remove(DEFAULT_SUBTITLE_LANGUAGE)
            }
        }
    }

    override fun getDefaultSubtitleLanguage(): Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[DEFAULT_SUBTITLE_LANGUAGE]
        }

    override suspend fun setSubtitleSize(size: Float) {
        dataStore.edit { preferences ->
            preferences[SUBTITLE_SIZE] = size
        }
    }

    override fun getSubtitleSize(): Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SUBTITLE_SIZE] ?: 1.0f
        }

    override suspend fun setDefaultStoragePath(path: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_STORAGE_PATH] = path
        }
    }

    override fun getDefaultStoragePath(): Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[DEFAULT_STORAGE_PATH]
        }

    override suspend fun setPrivateModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PRIVATE_MODE_ENABLED] = enabled
        }
    }

    override fun getPrivateModeEnabled(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PRIVATE_MODE_ENABLED] ?: false
        }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED] = enabled
        }
    }

    override fun getBiometricEnabled(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[BIOMETRIC_ENABLED] ?: false
        }

    override suspend fun clearAllSettings() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
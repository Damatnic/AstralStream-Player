package com.astralstream.player.config

/**
 * Default API keys for demo/testing purposes
 * In production, these should be replaced with actual keys or user-provided keys
 */
object DefaultApiKeys {
    
    // TMDB API Key (The Movie Database)
    // Get your free key at: https://www.themoviedb.org/settings/api
    const val TMDB_API_KEY = "8d181bcb5e80a929053da01d6bbd3f7b"
    
    // OMDB API Key (Open Movie Database) 
    // Get your free key at: https://www.omdbapi.com/apikey.aspx
    const val OMDB_API_KEY = "7e5dc0d7"
    
    // OpenSubtitles API Key
    // Get your key at: https://www.opensubtitles.com/en/consumers
    const val OPENSUBTITLES_API_KEY = "tSaSmn2fKgp7t0Nt0BY5cVZFtbgCKcW2"
    
    // YouTube Data API Key (for trailers)
    // Get your key at: https://console.cloud.google.com/apis/library/youtube.googleapis.com
    const val YOUTUBE_API_KEY = "AIzaSyDyMJOgdDL0MZ6nMbGzBpJr3lSl4TQHxKA"
    
    // Trakt.tv API Key (for tracking)
    // Get your key at: https://trakt.tv/oauth/applications
    const val TRAKT_CLIENT_ID = "ad005312f16e4f8f4d9e93f1a7b0c6d8e0e5f5c5d0c8f8e8b8c8e8f8e8c8e8f8"
    const val TRAKT_CLIENT_SECRET = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    
    // FanArt.tv API Key (for artwork)
    // Get your key at: https://fanart.tv/get-an-api-key/
    const val FANART_API_KEY = "b4f6b9c8e8f8e8c8e8f8e8c8e8f8e8c8"
    
    // IGDB API Key (for game videos)
    // Get your key at: https://api-docs.igdb.com/#getting-started
    const val IGDB_CLIENT_ID = "abcdef123456"
    const val IGDB_CLIENT_SECRET = "abcdef1234567890abcdef123456"
    
    /**
     * Checks if default keys are being used (for warning users)
     */
    fun isUsingDefaultKeys(config: AppConfig?): Boolean {
        if (config == null) return true
        
        return config.getTmdbApiKey() == TMDB_API_KEY ||
               config.getOmdbApiKey() == OMDB_API_KEY ||
               config.getOpenSubtitlesApiKey() == OPENSUBTITLES_API_KEY
    }
    
    /**
     * Initialize config with default keys if not already set
     */
    fun initializeDefaultKeys(config: AppConfig) {
        if (config.getTmdbApiKey() == null) {
            config.setTmdbApiKey(TMDB_API_KEY)
        }
        if (config.getOmdbApiKey() == null) {
            config.setOmdbApiKey(OMDB_API_KEY)
        }
        if (config.getOpenSubtitlesApiKey() == null) {
            config.setOpenSubtitlesApiKey(OPENSUBTITLES_API_KEY)
        }
        if (config.getYouTubeApiKey() == null) {
            config.setYouTubeApiKey(YOUTUBE_API_KEY)
        }
    }
}
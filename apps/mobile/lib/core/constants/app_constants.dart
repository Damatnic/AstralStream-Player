class AppConstants {
  static const String appName = 'AstralStream';
  static const String appVersion = '1.0.0';
  static const String appDescription = 'Next-generation AI-powered media player';
  
  // API Configuration
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://api.astralstream.app',
  );
  
  static const String apiVersion = 'v1';
  static const Duration apiTimeout = Duration(seconds: 30);
  
  // Storage Keys
  static const String storageKeyTheme = 'theme_mode';
  static const String storageKeyLanguage = 'language';
  static const String storageKeyPlaybackSpeed = 'playback_speed';
  static const String storageKeySubtitleLanguage = 'subtitle_language';
  static const String storageKeyAudioLanguage = 'audio_language';
  static const String storageKeyVideoQuality = 'video_quality';
  static const String storageKeyRecentFiles = 'recent_files';
  static const String storageKeyWatchHistory = 'watch_history';
  static const String storageKeyFavorites = 'favorites';
  static const String storageKeyPlaylists = 'playlists';
  
  // Playback Settings
  static const double defaultPlaybackSpeed = 1.0;
  static const List<double> playbackSpeeds = [
    0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 3.0, 4.0
  ];
  
  // Video Quality Options
  static const List<String> videoQualities = [
    'Auto',
    '4K',
    '1080p',
    '720p',
    '480p',
    '360p',
    '240p',
  ];
  
  // Supported File Extensions
  static const List<String> supportedVideoExtensions = [
    'mp4', 'mkv', 'avi', 'mov', 'wmv', 'flv', 'webm',
    'm4v', 'mpg', 'mpeg', '3gp', 'ogv', 'ts', 'm2ts',
    'mts', 'vob', 'divx', 'xvid', 'rmvb', 'rm', 'asf'
  ];
  
  static const List<String> supportedAudioExtensions = [
    'mp3', 'wav', 'flac', 'aac', 'ogg', 'wma', 'm4a',
    'opus', 'alac', 'ape', 'ac3', 'dts', 'mka', 'amr'
  ];
  
  static const List<String> supportedSubtitleExtensions = [
    'srt', 'ass', 'ssa', 'vtt', 'sub', 'idx', 'smi',
    'psb', 'pjs', 'mpl2', 'ttml', 'dfxp', 'sbv'
  ];
  
  // Gesture Settings
  static const double swipeThreshold = 50.0;
  static const double seekDuration = 10.0; // seconds
  static const double volumeStep = 0.05;
  static const double brightnessStep = 0.05;
  
  // UI Settings
  static const double playerControlsHeight = 80.0;
  static const Duration controlsAutoHideDelay = Duration(seconds: 3);
  static const Duration animationDuration = Duration(milliseconds: 300);
  
  // Cache Settings
  static const int maxCacheSize = 500 * 1024 * 1024; // 500 MB
  static const int maxRecentFiles = 50;
  static const int maxSearchHistory = 20;
  
  // Feature Flags
  static const bool enableAIFeatures = true;
  static const bool enableCloudSync = true;
  static const bool enableCasting = true;
  static const bool enablePictureInPicture = true;
  static const bool enableBackgroundPlayback = true;
  static const bool enableDownloads = true;
  static const bool enablePlugins = true;
  
  // AI Settings
  static const String whisperModel = 'base';
  static const String translationEngine = 'google';
  static const bool enableAutoSubtitles = true;
  static const bool enableSmartCrop = false;
  
  // Social Features
  static const bool enableComments = true;
  static const bool enableSharing = true;
  static const bool enableWatchParties = false;
  
  // Privacy Settings
  static const bool collectAnalytics = false;
  static const bool sendCrashReports = true;
  static const bool enableTelemetry = false;
}
# Consumer rules for player module
# Keep all player-related classes
-keep public class com.astralstream.player.** { *; }

# Keep ExoPlayer classes
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }
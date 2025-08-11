# Flutter ProGuard rules
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# MediaKit / ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-keep class com.google.android.exoplayer2.ext.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

# FFmpeg
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.mobileffmpeg.** { *; }

# Hive
-keep class * extends hive.HiveObject { *; }
-keep class * implements hive.TypeAdapter { *; }

# TensorFlow Lite
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }

# Local Auth
-keep class androidx.biometric.** { *; }
-keep class com.android.internal.** { *; }

# Permissions
-keep class com.baseflow.permissionhandler.** { *; }

# File picker
-keep class com.mr.flutter.plugin.filepicker.** { *; }

# Path Provider
-keep class io.flutter.plugins.pathprovider.** { *; }

# Share Plus
-keep class dev.fluttercommunity.plus.share.** { *; }

# Connectivity
-keep class dev.fluttercommunity.plus.connectivity.** { *; }

# Device Info
-keep class dev.fluttercommunity.plus.device_info.** { *; }

# Package Info
-keep class dev.fluttercommunity.plus.packageinfo.** { *; }

# Video Player
-keep class io.flutter.plugins.videoplayer.** { *; }

# Media Kit
-keep class com.alexmercerind.media_kit.** { *; }

# Keep Parcelables
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Gson (if used)
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Prevent stripping of debug information
-keepattributes SourceFile,LineNumberTable

# Keep custom application class
-keep public class * extends android.app.Application

# Keep custom activities
-keep public class * extends android.app.Activity

# Keep custom services
-keep public class * extends android.app.Service

# Keep broadcast receivers
-keep public class * extends android.content.BroadcastReceiver

# Keep content providers
-keep public class * extends android.content.ContentProvider

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Optimize
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
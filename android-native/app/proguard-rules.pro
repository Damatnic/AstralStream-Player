# AstralStream ProGuard Rules

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ========================
# Android and AndroidX
# ========================
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# ========================
# Jetpack Compose
# ========================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Compose lambda functions
-keep class * extends kotlin.jvm.internal.Lambda
-keepclassmembers class * extends kotlin.jvm.internal.Lambda {
    *;
}

# ========================
# Media3/ExoPlayer
# ========================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ExoPlayer specific
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Media3 database
-keep class androidx.media3.database.** { *; }

# Media3 decoders
-keep class androidx.media3.decoder.** { *; }

# Media3 extractors
-keep class androidx.media3.extractor.** { *; }

# ========================
# Room Database
# ========================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep class * extends androidx.room.TypeConverter

# ========================
# Hilt/Dagger
# ========================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <init>(...);
}

# ========================
# Kotlin Coroutines
# ========================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ========================
# Retrofit & OkHttp
# ========================
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ========================
# Glide
# ========================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# ========================
# ML Kit & TensorFlow Lite
# ========================
-keep class com.google.mlkit.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn org.tensorflow.lite.**

# ========================
# Gson
# ========================
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ========================
# Data classes and models
# ========================
-keep @kotlinx.parcelize.Parcelize class * { *; }
-keep class com.astralstream.player.data.model.** { *; }
-keep class com.astralstream.player.data.entities.** { *; }

# ========================
# Native methods
# ========================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========================
# Enums
# ========================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========================
# Parcelable
# ========================
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# ========================
# Serializable
# ========================
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========================
# WorkManager
# ========================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger
-keep class androidx.work.** { *; }

# ========================
# SLF4J Logging Framework
# ========================
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# ========================
# Custom rules for AstralStream
# ========================
-keep class com.astralstream.player.** { *; }

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Keep reflection usage
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# R8 compatibility
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
# Consumer rules for AI module
# Keep all AI-related classes
-keep public class com.astralstream.ai.** { *; }

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }

# Keep TensorFlow Lite classes  
-keep class org.tensorflow.** { *; }
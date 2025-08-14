# AstralStream Android Build Instructions

## Prerequisites

1. **Android Studio** (Latest stable version - Arctic Fox or newer)
2. **JDK 11 or 17** (Recommended for Android Gradle Plugin 8.5.2)
3. **Android SDK** with the following components:
   - SDK Platform API 34 (Android 14)
   - Build-Tools 34.0.0
   - Android SDK Platform-Tools
   - Android SDK Command-line Tools

## Setup Instructions

### 1. Clone or Download the Project
```bash
cd android-native
```

### 2. Open in Android Studio
- Open Android Studio
- Select "Open an existing Android Studio project"
- Navigate to the `android-native` folder
- Click OK

### 3. Sync Project
Android Studio will automatically:
- Download Gradle 8.7
- Download all dependencies
- Index the project

### 4. Configure SDK Location
The `local.properties` file has been pre-configured with:
```
sdk.dir=C:\\Users\\damat\\AppData\\Local\\Android\\Sdk
```
If your Android SDK is in a different location, update this path.

## Build Instructions

### Debug Build
1. In Android Studio: **Build > Make Project** (Ctrl+F9)
2. Or via terminal:
```bash
./gradlew assembleDebug
```

### Release Build
1. Configure signing in `keystore/keystore.properties`:
```properties
storeFile=../keystore/release.keystore
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

2. Build release APK:
```bash
./gradlew assembleRelease
```

### Build Variants
The project includes multiple build variants:

**Product Flavors:**
- `free` - Free version with limited features
- `pro` - Pro version with all features
- `beta` - Beta version with experimental features

**Distribution Channels:**
- `playstore` - For Google Play Store
- `fdroid` - For F-Droid (no analytics/crash reporting)
- `github` - For GitHub releases

Example commands:
```bash
# Build free version for Play Store
./gradlew assembleFreePlaystoreDebug

# Build pro version for GitHub
./gradlew assembleProGithubRelease
```

## Running the App

### On Emulator
1. Create an AVD in Android Studio (Tools > AVD Manager)
2. Run the app: **Run > Run 'app'** (Shift+F10)

### On Physical Device
1. Enable Developer Options and USB Debugging on your device
2. Connect device via USB
3. Run the app: **Run > Run 'app'** (Shift+F10)

## Project Structure

```
android-native/
├── app/           # Main application module
├── core/          # Core utilities and base classes
├── player/        # Video player module
├── data/          # Data layer (database, repositories)
├── features/      # Feature modules
├── ai/            # AI/ML features module
└── keystore/      # Signing keys (not in version control)
```

## Troubleshooting

### Gradle Sync Issues
- **File > Invalidate Caches and Restart**
- Delete `.gradle` folder and sync again

### Build Errors
- Ensure JDK 11 or 17 is configured: **File > Project Structure > SDK Location**
- Check that all SDK components are installed: **Tools > SDK Manager**

### Out of Memory Errors
Increase heap size in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m
```

## Dependencies

Key libraries used:
- **Media3 (ExoPlayer)** - Video playback
- **Jetpack Compose** - UI framework
- **Hilt** - Dependency injection
- **Room** - Database
- **Coroutines** - Async programming
- **ML Kit** - Machine learning features
- **TensorFlow Lite** - Custom ML models

## Testing

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

## Performance

The app is optimized with:
- R8 code shrinking and optimization
- APK splits for different architectures
- Proguard rules for release builds
- Incremental compilation
- Build cache enabled

## Support

For issues or questions, please check:
- The project's issue tracker
- Android Studio logs: **View > Tool Windows > Build**
- Gradle logs: Run with `--info` or `--debug` flag
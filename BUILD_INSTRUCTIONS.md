# AstralStream Build Instructions

## Overview
AstralStream is a feature-rich Android video player that surpasses MX Player with advanced capabilities including hardware acceleration, multi-audio tracks, HDR support, cloud sync, and AI-powered features.

## Prerequisites
- Android Studio Arctic Fox or later
- JDK 11 or higher
- Android SDK 34
- Kotlin 1.9.25

## Build Setup

### 1. Clone the Repository
```bash
git clone [repository-url]
cd AstralStream
```

### 2. Configure Firebase (Optional - for cloud sync and analytics)
- Place your `google-services.json` in `android-native/app/`
- If not using Firebase, the F-Droid flavor will build without it

### 3. Build Commands

#### Debug Build
```bash
cd android-native
./gradlew assembleDebug
```

#### Release Build
```bash
./gradlew assembleRelease
```

#### Build Specific Flavors
```bash
# Free version for Play Store
./gradlew assembleFreePlaystoreRelease

# Pro version for Play Store
./gradlew assembleProPlaystoreRelease

# F-Droid version (no proprietary dependencies)
./gradlew assembleFreeFdroidRelease

# GitHub version with update checker
./gradlew assembleFreeGithubRelease
```

## Features Implemented

### Phase 1-2: Foundation & Network
- Material Design 3 with Jetpack Compose
- ExoPlayer/Media3 integration
- DLNA/Chromecast support
- SMB/Network streaming
- File browser with sorting/filtering

### Phase 3: Advanced Player
- Hardware acceleration with codec detection
- Multi-audio track support
- HDR/Dolby Vision support
- GPU-accelerated video filters
- Frame-by-frame navigation
- Zoom and pan controls

### Phase 4: Content Enhancement
- Metadata fetching (TMDb/OMDb)
- Subtitle downloading (OpenSubtitles)
- Thumbnail generation
- Smart playlists
- Bookmarks and watch history
- Visual seek preview

### Phase 5: Premium Features
- Encrypted cloud sync
- 8 built-in themes
- Comprehensive analytics
- Gesture customization
- Dual audio support

## Architecture

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Database**: Room
- **Player**: ExoPlayer/Media3
- **Network**: Retrofit + OkHttp
- **Cloud**: Firebase (Auth, Firestore, Storage)

### Module Structure
```
android-native/
├── app/              # Main application module
├── core/             # Core utilities and base classes
├── player/           # Video player module
├── data/             # Data layer (repositories, database)
├── features/         # Feature modules
└── ai/               # AI/ML features (optional)
```

## Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumentation Tests
```bash
./gradlew connectedAndroidTest
```

### Run Integration Tests
```bash
./gradlew connectedDebugAndroidTest
```

## Common Issues

### Out of Memory During Build
Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g
```

### Firebase Not Configured
Use the F-Droid flavor which doesn't require Firebase:
```bash
./gradlew assembleFreeFdroidDebug
```

## Performance Optimizations
- Hardware acceleration enabled by default
- Multi-core decoding support
- Adaptive streaming optimization
- Efficient thumbnail caching
- Background media scanning
- Memory-aware cache management

## Privacy & Security
- No analytics in F-Droid builds
- Encrypted cloud sync
- Anonymous authentication
- Local-first architecture
- No parental controls (by design)

## Release Checklist
1. Update version in `build.gradle`
2. Run all tests
3. Generate signed APK
4. Test on multiple devices
5. Update release notes
6. Tag release in git

## Support
For issues or feature requests, please open an issue on GitHub.
# AstralStream - Native Android Video Player

AstralStream is a comprehensive native Android video player built with modern Android development practices, featuring advanced video playback capabilities, AI-powered enhancements, and a beautiful Material Design 3 interface.

## Features

### Core Features
- **Advanced Video Playback**: Powered by Media3/ExoPlayer with support for all major video formats
- **Modern UI**: Built with Jetpack Compose and Material Design 3
- **Picture-in-Picture**: Seamless PiP support for background playback
- **Gesture Controls**: Intuitive touch controls for seeking, volume, and brightness
- **Subtitle Support**: Advanced subtitle rendering with customization options
- **Audio Enhancement**: Built-in equalizer and audio effects

### AI-Powered Features
- **Smart Subtitles**: AI-generated subtitles and translation
- **Scene Detection**: Automatic chapter detection and scene analysis
- **Content Enhancement**: AI-driven video quality improvement
- **Smart Organization**: Automatic media library organization

### Privacy & Security
- **Private Folder**: Secure storage with biometric/PIN protection
- **Encrypted Storage**: Protected media files
- **Privacy Controls**: Granular privacy settings

### Advanced Features
- **Multi-format Support**: MP4, MKV, AVI, MOV, WebM, and more
- **Network Streaming**: HTTP, HTTPS, RTSP, RTMP support
- **Playlist Management**: Create and manage custom playlists
- **Background Playback**: Continue audio playback in background
- **Sleep Timer**: Auto-stop playback after specified time

## Architecture

AstralStream follows modern Android architecture patterns:

### Multi-Module Structure
- **app**: Main application module with UI and navigation
- **core**: Shared utilities and base classes
- **player**: Media playback engine and player UI
- **data**: Database, repositories, and data sources
- **features**: Feature-specific implementations
- **ai**: AI/ML functionality and models

### Technology Stack
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Repository pattern
- **Dependency Injection**: Hilt/Dagger
- **Database**: Room with SQLite
- **Media Playback**: Media3/ExoPlayer
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Glide
- **Concurrency**: Kotlin Coroutines + Flow
- **AI/ML**: TensorFlow Lite + ML Kit

## Setup and Installation

### Prerequisites
- Android Studio Hedgehog or later
- JDK 8 or later
- Android SDK 21+ (Android 5.0+)
- Gradle 8.2+

### Build Instructions

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd AstralStream/android-native
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Open the `android-native` folder
   - Wait for Gradle sync to complete

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Run on device/emulator**
   ```bash
   ./gradlew installDebug
   ```

### Configuration

The app comes pre-configured with sensible defaults, but you can customize:

- **Media3 Configuration**: Modify `BuildConfig` values in app-level `build.gradle`
- **Database Schema**: Update entities in the `data` module
- **AI Models**: Place TensorFlow Lite models in `assets/models/`
- **Themes**: Customize colors and themes in `ui/theme/`

## Project Structure

```
android-native/
├── app/                          # Main application module
│   ├── src/main/java/com/astralstream/player/
│   │   ├── ui/                   # User interface components
│   │   ├── services/             # Background services
│   │   ├── utils/                # Utility classes
│   │   └── AstralStreamApplication.kt
│   ├── src/main/res/             # Resources (layouts, strings, etc.)
│   └── build.gradle              # App-level dependencies
├── core/                         # Core utilities and base classes
├── player/                       # Media playback engine
├── data/                         # Database and repositories
├── features/                     # Feature implementations
├── ai/                          # AI/ML functionality
├── build.gradle                 # Project-level configuration
├── settings.gradle              # Module configuration
└── gradle.properties           # Gradle settings
```

## Key Components

### MainActivity
- Entry point of the application
- Handles permissions and navigation
- Manages system UI and edge-to-edge display

### PlayerActivity
- Dedicated video playback activity
- Full-screen video playback
- Picture-in-Picture support
- Hardware acceleration

### Media Services
- `MediaPlaybackService`: Background audio/video playback
- `MediaScannerService`: Background media scanning
- `MediaScanWorker`: WorkManager-based media indexing

### Database Schema
- **MediaFileEntity**: Video file metadata and properties
- **PlaylistEntity**: User-created playlists
- **PlaybackHistoryEntity**: Playback position and history
- **PlaylistMediaEntity**: Many-to-many playlist relationships

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Maintain consistent formatting

### Architecture Patterns
- Use MVVM pattern for UI logic
- Implement Repository pattern for data access
- Apply Dependency Injection with Hilt
- Follow single responsibility principle

### Performance Considerations
- Optimize database queries with proper indexing
- Use efficient image loading with Glide
- Implement proper memory management
- Cache frequently accessed data

## Testing

### Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Instrumentation Tests
```bash
./gradlew connectedAndroidTest
```

### UI Tests
```bash
./gradlew connectedCheck
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- **Media3/ExoPlayer**: Google's media playback library
- **Jetpack Compose**: Modern Android UI toolkit
- **Material Design 3**: Google's design system
- **TensorFlow Lite**: On-device machine learning
- **Room**: Local database solution

## Support

For support, please create an issue in the repository or contact the development team.

## Roadmap

### Version 1.0
- [ ] Complete core video playback functionality
- [ ] Basic media library and file browser
- [ ] Essential player controls and settings
- [ ] Picture-in-Picture support

### Version 1.1
- [ ] AI-powered subtitle generation
- [ ] Advanced video filters and effects
- [ ] Network streaming capabilities
- [ ] Private folder with biometric security

### Version 1.2
- [ ] Smart content analysis and tagging
- [ ] Advanced playlist features
- [ ] TV/Android TV support
- [ ] Cloud synchronization

---

Built with ❤️ for the Android community
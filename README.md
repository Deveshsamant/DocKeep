# DocKeep

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

DocKeep is a modern Android application for storing and organizing personal documents locally on your device. Built with Kotlin and XML, it provides a clean, user-friendly interface with Material Design components and smooth animations.

<a href="https://f-droid.org/packages/com.dockeep.app/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid" height="80">
</a>

## Features

- **Local Storage**: All documents are stored locally on your device
- **Document Organization**: Create and organize documents in a clean card-based interface
- **Image Management**: Add multiple images to each document from camera or gallery
- **Sharing**: Share individual images or entire documents with other apps
- **Export**: Export documents as ZIP files
- **Dark/Light Theme**: Toggle between light and dark themes
- **Search**: Quickly find documents by name
- **Responsive Design**: Works on both phones and tablets

## File Organization

The app organizes files in the following structure:
```
Pictures/Dockeep/
├── DocumentName1/
│   ├── image001.jpg
│   ├── image002.jpg
│   └── thumbnail.jpg
├── DocumentName2/
│   ├── image001.jpg
│   └── thumbnail.jpg
└── Exports/
    ├── DocumentName1_20231030_1200.zip
    └── DocumentName2_20231030_1205.zip
```

## Dependencies

The project uses the following libraries:

- AndroidX AppCompat
- Material Components
- Room Database
- Glide (for image loading)
- Lottie (for animations)
- Apache Commons Compress (for ZIP functionality)

## Building and Running

### Prerequisites

- Android Studio Flamingo or later
- Android SDK API 34 (Android 14)
- Kotlin 1.9.0 or later

### Gradle Commands

To build and install the app from the terminal:

1. Connect your Android device or start an emulator
2. Navigate to the project root directory
3. Run the following commands:

```bash
# Build debug APK
./gradlew assembleDebug

# Install debug APK to connected device
./gradlew installDebug

# Or build and install in one command
./gradlew installDebug
```

Alternatively, you can build the APK and install it manually:

```bash
# Build debug APK
./gradlew assembleDebug

# Install APK via ADB (make sure device is connected)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Android Studio Import

1. Open Android Studio
2. Select "Open an existing Android Studio project"
3. Navigate to the project directory and select it
4. Wait for Gradle sync to complete
5. Run the app by clicking the "Run" button or pressing Shift+F10

## Permissions

The app requires the following permissions:

- **CAMERA**: To capture new images
- **READ_EXTERNAL_STORAGE**: To access existing images in gallery
- **WRITE_EXTERNAL_STORAGE**: To save documents (Android 10 and below)
- **READ_MEDIA_IMAGES**: To access images (Android 13+)
- **MANAGE_EXTERNAL_STORAGE**: For broader storage access (Android 11+)

## Architecture

The app follows a modern Android architecture pattern:

- **View Layer**: Activities and XML layouts
- **ViewModel**: Manages UI-related data
- **Repository**: Handles data operations
- **Room Database**: Local data persistence
- **Utils**: Helper classes for file operations and utilities

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Icons from Material Design
- Animations from LottieFiles
- Image loading by Glide
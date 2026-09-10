# DocKeep

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

DocKeep is a modern Android application for storing and organizing personal documents locally on your device. Built with Kotlin and XML, it provides a clean, user-friendly interface with Material Design components and smooth animations.


## Features

- **Local storage** — every document stays on the device. There is no account, no sync and no server.
- **Document scanning** — edge detection and perspective correction, so a photo of a page comes out looking like a scan.
- **Scan editor** — rotate, crop, brightness, contrast, filters, and a black-box redaction tool for covering numbers before sharing.
- **Text blocks** — write notes alongside the scans in the same document.
- **OCR** — read the text off any scan, copy it, keep it as a note, or let it suggest the document's name.
- **Search** — by document name, by person, by tag, and through the text OCR has read inside the scans.
- **Tags** — labels that cut across people, so "expiring 2026" can span the whole vault.
- **People** — group documents by family member or friend.
- **PDF export** — build a PDF from a whole document or a single page, choosing exactly which blocks go in.
- **Save and share** — scans to the gallery, PDFs to Documents, or straight out to another app.
- **Bulk actions** — select several documents to share, delete, or move to a person at once.
- **App lock** — fingerprint, face or device PIN in front of the vault.
- **Backup** — export the whole vault as a ZIP and import it back.
- **Light and dark** — both themes, following the system by default.

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

- AndroidX AppCompat and Material Components
- Room (local database)
- Glide (image loading)
- Lottie (animations)
- Apache Commons Compress (ZIP backup)
- ML Kit Document Scanner (edge detection and perspective correction)
- ML Kit Text Recognition (on-device OCR)
- android-image-cropper (crop and straighten)
- AndroidX Biometric (app lock)

PDFs are written with the platform's own `android.graphics.pdf.PdfDocument` —
no third-party PDF library is involved.

## Building and Running

### Prerequisites

- JDK 17
- Android SDK API 34 (Android 14)
- Android Studio is optional — the Gradle wrapper builds the app on its own

Minimum supported device: Android 5.0 (API 21).

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

- **CAMERA** — capturing and scanning pages
- **USE_BIOMETRIC** — the app lock
- **VIBRATE** — the haptic tick when a document is dragged into a new position
- **INTERNET** — downloading the document-scanner module from Play Services on first use
- **READ_EXTERNAL_STORAGE** (Android 12 and below) and **WRITE_EXTERNAL_STORAGE** (Android 10 and below) — saving scans to the gallery on older releases

No broad storage permission is requested. Images arrive through the system
photo picker, which grants access to only the file you choose, and saved scans
and PDFs go out through MediaStore.

## Architecture

The app follows a modern Android architecture pattern:

- **View Layer**: Activities and XML layouts
- **ViewModel**: Manages UI-related data
- **Repository**: Handles data operations
- **Room Database**: Local data persistence
- **Utils**: Helper classes for file operations and utilities

## Issues and feedback

Found a bug, or want to suggest a feature? Open an issue:

**https://github.com/Deveshsamant/DocKeep/issues**

The same link is in the app, under **Settings → Report an issue**, next to the
version number — please include that version in the report.

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

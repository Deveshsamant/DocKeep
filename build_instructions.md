# Build Instructions

## Prerequisites

1. Android Studio installed
2. Android SDK with API 34 (Android 14)
3. Java Development Kit (JDK) 11 or higher

## Setting up the project

1. Open Android Studio
2. Select "Open an existing Android Studio project"
3. Navigate to this project directory and select it
4. Wait for Gradle sync to complete (this may take a few minutes)

## Building the project

### From Android Studio:

1. Select "Build" → "Make Project" or press Ctrl+F9 (Cmd+F9 on Mac)
2. The APK will be generated in `app/build/outputs/apk/debug/app-debug.apk`

### From Terminal/Command Line:

1. Navigate to the project root directory
2. Run one of the following commands:

```bash
# On Windows
gradlew assembleDebug

# On macOS/Linux
./gradlew assembleDebug
```

3. The APK will be generated in `app/build/outputs/apk/debug/app-debug.apk`

## Installing the app

### From Android Studio:

1. Connect your Android device or start an emulator
2. Select "Run" → "Run 'app'" or press Shift+F10

### From Terminal/Command Line:

```bash
# On Windows
gradlew installDebug

# On macOS/Linux
./gradlew installDebug
```

Or manually install the APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

If you encounter issues:

1. Make sure all required SDK components are installed
2. Check that your JAVA_HOME environment variable is set correctly
3. Try "File" → "Sync Project with Gradle Files" in Android Studio
4. Clean and rebuild the project: "Build" → "Clean Project" then "Build" → "Rebuild Project"
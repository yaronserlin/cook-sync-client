# CookSync Client

CookSync is an application designed to synchronize and manage recipes.

## How to download the app

You can download the latest version of the app from the [GitHub Releases](https://github.com/yaronserlin/cook-sync-client/releases) page.

1. Go to the [Releases](https://github.com/yaronserlin/cook-sync-client/releases) page.
2. Find the latest release.
3. Under **Assets**, download the `app-debug.apk` file.
4. Open the APK file on your Android device to install it.
    - *Note: You may need to enable "Install from unknown sources" in your device settings.*

## Development

### Prerequisites
- Android Studio
- JDK 17

### Building from source
To build the APK locally, run:
```bash
./gradlew assembleDebug
```
The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

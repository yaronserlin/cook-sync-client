# CookSync Client

CookSync is an application designed to synchronize and manage recipes.

## How to download the app

You can download the latest version of the app from the [GitHub Releases](https://github.com/yaronserlin/cook-sync-client/releases) page.

1. Go to the [Releases](https://github.com/yaronserlin/cook-sync-client/releases) page.
2. Find the latest release.
3. Under **Assets**, download the `app-prod-release.apk` file.
4. Open the APK file on your Android device to install it.
    - *Note: You may need to enable "Install from unknown sources" in your device settings.*

## Development

### Prerequisites
- Android Studio
- JDK 17

### Building from source
The app has two product flavors, switching which backend it talks to
(`BASE_URL`): `dev` (a local LAN server, for development) and `prod` (the
deployed Render server). In Android Studio, pick one from the **Build
Variants** panel; from the CLI, build either directly:
```bash
./gradlew assembleDevDebug    # app/build/outputs/apk/dev/debug/app-dev-debug.apk
./gradlew assembleProdDebug   # app/build/outputs/apk/prod/debug/app-prod-debug.apk
```
Tagged releases (see [Releases](https://github.com/yaronserlin/cook-sync-client/releases)) are always built from the `prod` flavor.

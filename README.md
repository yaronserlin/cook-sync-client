# CookSync Client

CookSync is an application designed to synchronize and manage recipes.

## Architecture

The app follows **MVVM**, layered as:

- **UI** (`ui/<feature>/`) — Activities, Fragments, and XML layouts. Render
  state and forward user actions to a ViewModel; never call the network or
  a repository directly.
- **ViewModel** (`ui/<feature>/*ViewModel.java`) — owns UI state as
  `LiveData` and all presentation logic (formatting, filtering, pagination
  bookkeeping, optimistic-update/undo scheduling). Built via
  `ui/base/ViewModelFactory`, which wires each ViewModel to the concrete
  `*RepositoryImpl` it needs (Dependency Inversion — ViewModels declare a
  `Repository` interface constructor parameter, never a concrete impl).
- **Repository** (`data/repository/`) — one interface + `impl/` per
  domain (recipes, auth, admin, media, tags, units), wrapping the Retrofit
  API surface (`data/datasource/remote/ApiService`) behind a uniform
  `ApiResult<T>` (Loading/Success/Error) callback shape.
- **RecyclerView adapters** (`ui/*/*Adapter.java`) extend
  `ui/base/BaseAdapter<T, VH>`, which owns the backing list and
  set/insert/remove/`getItemCount()` boilerplate; subclasses only implement
  `onCreateViewHolder`/`onBindViewHolder`.

Cross-cutting pieces:

- **Auth**: JWT access/refresh tokens are stored in
  `data/datasource/local/TokenStore` (backed by `EncryptedSharedPreferences`
  / Android Keystore) and attached/refreshed transparently by Retrofit's
  `AuthInterceptor` + `TokenAuthenticator`.
- **Media**: images upload directly from the client to Cloudinary using a
  short-lived signature issued by the backend — never routed through the
  app server.
- **Shared DTOs**: request/response payload shapes live in the sibling
  `cooksync-DTOs` module and are consumed identically by this client and by
  `cook-sync-server`, so the two can't drift out of sync.
- **Resources**: layouts/drawables/strings/colors/dimens are split by
  feature under `app/src/main/res-features/<feature>/`, each registered as
  an extra Gradle `sourceSet` (see `app/build.gradle.kts`) and merged with
  the default `res/` at build time.

## SDK requirements

| | Version |
|---|---|
| `compileSdk` | 36 |
| `minSdk` | 24 (Android 7.0) |
| `targetSdk` | 36 |
| Java toolchain | 17 |

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

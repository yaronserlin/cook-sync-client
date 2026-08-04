---
name: run-cook-sync-client
description: Build, install, launch, and drive the CookSync Android client on an emulator. Use when asked to run, start, build, or screenshot the CookSync Android app, or to confirm a client change works by tapping through the real UI (not just unit tests).
---

CookSync's Android client (Java, Gradle, min SDK 24 / target 36) is a native
GUI app — it's driven headlessly on a boot-headless Android emulator via
`.claude/skills/run-cook-sync-client/driver.sh`, a thin wrapper around `adb`.
All paths below are relative to `cook-sync-client/` (this skill's grandparent
directory).

## Prerequisites

- macOS with Android SDK at `~/Library/Android/sdk` (or set `$ANDROID_HOME`).
- SDK must already contain: `platform-tools`, `emulator`, and the system
  image the AVD below uses (`system-images/android-37.1/google_apis_playstore_ps16k/x86_64`).
- An AVD named `Pixel_10_Pro` must exist (`emulator -list-avds`). This
  environment already had one — it wasn't created as part of this skill.
  If missing, create one via Android Studio's Device Manager (untested here;
  `cmdline-tools`/`avdmanager` were not present in this environment to script
  it) — any x86_64 emulator AVD will do, then set `AVD_NAME` when invoking
  the driver.
- Java 17 toolchain (used by the Gradle build; resolved automatically via
  the Gradle toolchain if not on `PATH`).

No `apt-get`/Linux setup applies — this app was verified on macOS only, and
the client itself has no server-side prerequisites for build+launch (it
talks to a backend at runtime, not at build time).

## Build

```bash
cd cook-sync-client
.claude/skills/run-cook-sync-client/driver.sh build
# → runs ./gradlew assembleDebug, produces app/build/outputs/apk/debug/app-debug.apk
```

The client depends on `com.cooksync:dtos:1.0.0-SNAPSHOT`, resolved from
`mavenLocal()` (see `settings.gradle.kts`) — confirm
`~/.m2/repository/com/cooksync/dtos` is populated (built from the sibling
`cooksync-DTOs` project) before building here.

## Run (agent path)

```bash
cd cook-sync-client
D=.claude/skills/run-cook-sync-client/driver.sh
"$D" boot        # boots Pixel_10_Pro headless (-no-window), waits for sys.boot_completed
"$D" install      # adb install -r of the debug APK (build first if missing)
"$D" launch       # wakes the screen, unlocks, starts LoginActivity
"$D" tap 640 928  # tap the email field
"$D" text "you@example.com"
"$D" tap 640 1136 # tap the password field
"$D" text "somepassword"
"$D" screenshot login_filled   # → /tmp/cooksync-client-shots/login_filled.png
"$D" tap 640 1289 # tap Sign in
sleep 2           # network round-trip before the result/error renders
"$D" screenshot login_result
"$D" dump         # uiautomator XML dump → /tmp/cooksync-client-shots/dump.xml (bounds for tap targets)
"$D" stop         # kill the emulator
```

Screenshots land in `/tmp/cooksync-client-shots/` (override with `$SHOT_DIR`).

| command | what it does |
|---|---|
| `boot` | Cold/snapshot-boots the `Pixel_10_Pro` AVD fully headless, waits up to 5 min for boot |
| `build` | `./gradlew assembleDebug` |
| `install` | Installs the built debug APK via `adb install -r` |
| `wake` | Wakes + swipe-unlocks the screen (the emulator screen sleeps/locks between commands) |
| `launch [activity]` | `wake`, then `am start`; defaults to `com.cooksync.app/.ui.auth.LoginActivity` |
| `tap X Y` | `input tap` at device pixel coordinates (get them from `dump`) |
| `text STRING` | `input text` (spaces auto-escaped to `%s`) |
| `key KEYCODE` | `input keyevent` |
| `screenshot [name]` | `screencap -p` → `$SHOT_DIR/<name>.png` |
| `dump` | `uiautomator dump` pulled to `$SHOT_DIR/dump.xml` — use to find `bounds="[x1,y1][x2,y2]"` for `tap` |
| `logcat` | Dumps error-level logcat lines mentioning cooksync |
| `uninstall` / `stop` | Uninstalls the app / kills the emulator |

Device is addressed as `emulator-5554` (`$SERIAL`); AVD name is `$AVD_NAME`
(default `Pixel_10_Pro`) — both overridable as env vars.

## Run (human path)

Open Android Studio, select `cook-sync-client`, run the `app` configuration
on any device/emulator. Not used for agent verification.

## Test

```bash
cd cook-sync-client
./gradlew test
```

(Unit tests only — `androidTest` requires a connected device/emulator and
was not exercised as part of authoring this skill.)

---

## Gotchas

- **The emulator screen sleeps/locks between adb commands.** A `launch`
  right after `boot` can land on a black screenshot even though the app is
  the foreground activity — the screen itself is off. `driver.sh launch`
  always calls `wake` (send `KEYCODE_WAKEUP` + swipe-unlock) first; do the
  same before any `tap`/`screenshot` if you see an all-black capture.
- **`am start` on an already-running activity prints a warning, not an
  error**: `Warning: Activity not started, intent has been delivered to
  currently running top-most instance.` — harmless, the activity is still
  frontmost.
- **`-no-window` still needs a signed gRPC token / JWKS setup** the emulator
  generates itself on launch — no manual auth setup required, just don't
  pass `-grpc` unless you also want an open unprotected port.
- **Running two emulator instances against the same AVD fails** with
  `Running multiple emulators with the same AVD is an experimental feature`
  unless the previous one was fully killed. Always `driver.sh stop` (or
  `pkill -f qemu-system`) and confirm `adb devices` is empty before
  re-booting.
- **`BASE_URL` is hardcoded** in `app/build.gradle.kts`
  (`buildConfigField "String" "BASE_URL"`) to a specific LAN IP — it is
  *not* set per-environment automatically. In this session the hardcoded IP
  happened to be reachable and a live backend answered "Invalid
  credentials" on sign-in (proving the network call round-trips); on a
  different network the same tap will instead show a network-error state.
  Either is a legitimate, working UI state — don't assume a network error
  means the harness is broken. Use `run_project.sh` (repo root) to rewrite
  `BASE_URL` to the current host's LAN IP if you need the client to reach a
  locally-running `cook-sync-server`.
- **Cold boot vs snapshot boot**: if `~/.android/avd/Pixel_10_Pro.avd`
  has no `default_boot` snapshot (e.g. after `-no-window` runs, which by
  default don't save one on exit), boot falls back to a full cold boot —
  still well under the 5-minute timeout in `driver.sh boot`, but slower
  than the ~10s snapshot resume.

## Troubleshooting

- **`adb: device offline` right after `emulator ... &`**: normal —
  `adb wait-for-device` (used inside `driver.sh boot`) blocks until it
  clears; don't run `adb shell` commands before that returns.
- **Screenshot is a single solid orange square, nothing else**: caught
  mid-layout-inflate, ~1s after `am start`. `driver.sh launch` already
  sleeps 3s after starting the activity; if you see this, wait longer
  before capturing.
- **`daemon not running; starting now at tcp:5037`** on some `adb`
  invocations: harmless — the first `adb` call in a fresh shell/session
  starts the adb server; the command still completes.

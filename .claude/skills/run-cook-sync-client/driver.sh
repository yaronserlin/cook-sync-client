#!/bin/bash
# Driver for the CookSync Android client — boots the emulator, installs/launches
# the app, and drives it via adb (tap/type/screenshot/dump). Requires macOS with
# the Android SDK at ~/Library/Android/sdk (or $ANDROID_HOME set) and the
# "Pixel_10_Pro" AVD (see SKILL.md to create one if missing).
#
# Usage: driver.sh <command> [args...]
set -euo pipefail

: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
export ANDROID_HOME
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

AVD_NAME="${AVD_NAME:-Pixel_10_Pro}"
SERIAL="${SERIAL:-emulator-5554}"
PKG="com.cooksync.app"
LAUNCH_ACTIVITY="$PKG/.ui.auth.LoginActivity"
CLIENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SHOT_DIR="${SHOT_DIR:-/tmp/cooksync-client-shots}"
mkdir -p "$SHOT_DIR"

cmd="${1:-}"; shift || true

case "$cmd" in
  boot)
    # Cold or snapshot boot, fully headless — no GUI window, no host audio.
    nohup emulator -avd "$AVD_NAME" -no-window -no-boot-anim -gpu swiftshader_indirect -no-audio \
      > /tmp/cooksync-emulator.log 2>&1 &
    echo "emulator pid $!"
    echo "waiting for boot..."
    adb wait-for-device
    for i in $(seq 1 30); do
      boot=$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null || true)
      [ "$boot" = "1" ] && { echo "booted"; exit 0; }
      sleep 10
    done
    echo "timed out waiting for boot; see /tmp/cooksync-emulator.log" >&2
    exit 1
    ;;

  build)
    cd "$CLIENT_DIR"
    ./gradlew assembleDebug --console=plain
    ;;

  install)
    cd "$CLIENT_DIR"
    apk=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
    [ -n "$apk" ] || { echo "no debug APK found — run 'driver.sh build' first" >&2; exit 1; }
    adb -s "$SERIAL" install -r "$apk"
    ;;

  wake)
    # Screen goes to sleep/locks between commands; wake + swipe-unlock.
    adb -s "$SERIAL" shell input keyevent 224   # KEYCODE_WAKEUP
    sleep 1
    adb -s "$SERIAL" shell input swipe 640 2000 640 500
    ;;

  launch)
    activity="${1:-$LAUNCH_ACTIVITY}"
    "$0" wake
    adb -s "$SERIAL" shell am start -n "$activity"
    sleep 3
    ;;

  tap)
    x="$1"; y="$2"
    adb -s "$SERIAL" shell input tap "$x" "$y"
    ;;

  text)
    # adb `input text` needs %s for literal spaces
    adb -s "$SERIAL" shell input text "${1// /%s}"
    ;;

  key)
    adb -s "$SERIAL" shell input keyevent "$1"
    ;;

  screenshot)
    name="${1:-shot}"
    out="$SHOT_DIR/$name.png"
    adb -s "$SERIAL" exec-out screencap -p > "$out"
    echo "$out"
    ;;

  dump)
    adb -s "$SERIAL" shell uiautomator dump /sdcard/dump.xml >/dev/null
    adb -s "$SERIAL" pull /sdcard/dump.xml "$SHOT_DIR/dump.xml" >/dev/null
    echo "$SHOT_DIR/dump.xml"
    ;;

  logcat)
    adb -s "$SERIAL" logcat -d "*:E" | grep -i cooksync || true
    ;;

  uninstall)
    adb -s "$SERIAL" uninstall "$PKG" || true
    ;;

  stop)
    adb -s "$SERIAL" emu kill || true
    ;;

  *)
    echo "usage: driver.sh {boot|build|install|wake|launch [activity]|tap X Y|text STRING|key KEYCODE|screenshot [name]|dump|logcat|uninstall|stop}" >&2
    exit 1
    ;;
esac

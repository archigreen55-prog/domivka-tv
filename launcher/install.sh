#!/usr/bin/env bash
# One-command install of the launcher on an Android TV over Wi-Fi ADB.
# Usage: bash install.sh <TV_IP> [path/to/launcher.apk]
set -u
IP="${1:?usage: bash install.sh <TV_IP> [apk]}"
APK="${2:-app/build/outputs/apk/release/app-release.apk}"
T="adb -s $IP:5555"
[ -f "$APK" ] || { echo "APK not found: $APK (build it: ./gradlew assembleRelease)"; exit 1; }
adb connect "$IP:5555" | grep -q "connected" || { echo "Cannot connect to $IP"; exit 1; }
# Push first: streamed installs fail on some vendor builds ("digest did not verify").
$T push "$APK" /data/local/tmp/domivka.apk >/dev/null || exit 1
$T shell pm install -r /data/local/tmp/domivka.apk
$T shell cmd package set-home-activity com.domivka.launcher/.MainActivity
$T shell rm /data/local/tmp/domivka.apk
$T shell am start -n com.domivka.launcher/.MainActivity >/dev/null 2>&1
echo "Done."

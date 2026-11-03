#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
sdk_dir="$(sed -n 's/^sdk.dir=//p' "$repo_root/android/local.properties")"
aapt="$sdk_dir/build-tools/35.0.0/aapt"
apk="$repo_root/android/app/build/outputs/apk/debug/app-debug.apk"

cd "$repo_root/android"
./gradlew :app:assembleDebug >/dev/null

badging="$($aapt dump badging "$apk")"
grep -Fq "application-label:'DaySpark'" <<<"$badging"
grep -Fq "application-icon-640:'res/mipmap-anydpi-v26/ic_launcher.xml'" <<<"$badging"

echo "Branding test passed: DaySpark label and adaptive launcher icon are packaged."

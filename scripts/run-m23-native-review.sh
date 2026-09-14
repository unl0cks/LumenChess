#!/usr/bin/env bash
# Public-only, opt-in native UI evidence. The second invocation is a new app process.
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# This script runs only on the disposable CI emulator. Reset once, not between
# the two phases: the second process must restore the first phase's durable data.
adb shell pm clear dev.lumenchess | grep -q '^Success'
mkdir -p m23-library-native
# Preserve partial native diagnostics if an assertion fails; never label them final evidence.
trap 'adb pull /sdcard/Android/data/dev.lumenchess/files/m23-library-native/. m23-library-native/ >/dev/null 2>&1 || true' EXIT
for method in captureLibraryAndPrepareRestore restoreLibraryAfterProcessDeath; do
  adb shell am force-stop dev.lumenchess
  adb shell am instrument -w -r -e m23LibraryQa true \
    -e class "dev.lumenchess.games.GameLibraryReviewQaTest#$method" \
    dev.lumenchess.test/androidx.test.runner.AndroidJUnitRunner \
    | tee "m23-library-native/$method-result.txt"
  grep -Eq '^OK \([1-9][0-9]* tests?\)' "m23-library-native/$method-result.txt"
done
adb pull /sdcard/Android/data/dev.lumenchess/files/m23-library-native/. m23-library-native/
test -s m23-library-native/board-bounds.json
test -s m23-library-native/restoration.json

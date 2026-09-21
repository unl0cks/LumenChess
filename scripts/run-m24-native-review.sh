#!/usr/bin/env bash
set -euo pipefail
mkdir -p m24-native
trap 'adb pull /sdcard/Android/data/dev.lumenchess/files/m24-native/. m24-native/ >/dev/null 2>&1 || true' EXIT
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.lumenchess.visual.M24NativeReviewQaTest -Pandroid.testInstrumentationRunnerArguments.m24NativeQa=true
adb pull /sdcard/Android/data/dev.lumenchess/files/m24-native/. m24-native/
test -s m24-native/04-custom-fen-live.png

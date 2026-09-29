#!/usr/bin/env bash
# Runs the scripted completion-pass session and the Live/structure suites on the connected emulator.
set -uo pipefail
mkdir -p completion-device
status=0

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.lumenchess.visual.CompletionPassDeviceQaTest \
  -Pandroid.testInstrumentationRunnerArguments.completionQa=true || status=1
adb pull /sdcard/Android/data/dev.lumenchess/files/completion-qa/. completion-device/ >/dev/null 2>&1 || true

echo "=== MEASUREMENTS ==="
cat completion-device/measurements.txt 2>/dev/null || echo "(no measurements were written)"
echo "=== END MEASUREMENTS ==="

# Live contract, structure and library suites (these are the ones whose assertions changed).
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.lumenchess.play.PlayUiIntegrationTest,dev.lumenchess.design.P5ReferenceStructureTest,dev.lumenchess.games.GameLibraryUiTest \
  || status=1

emit() {
  local name="$1" file="completion-device/$1.png"
  if [ ! -f "$file" ]; then echo "IMG-MISSING $name"; return; fi
  convert "$file" -resize 22% -strip -quality 60 "/tmp/$name.jpg"
  echo "IMG-BEGIN $name $(stat -c%s "/tmp/$name.jpg")"
  base64 -w 400 "/tmp/$name.jpg" | sed 's/^/B64 /'
  echo "IMG-END $name"
}
for name in ${EMIT_IMAGES:-03-live-start}; do emit "$name"; done
exit $status

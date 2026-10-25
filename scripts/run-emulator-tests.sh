#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-artifacts
trap 'adb logcat -d > ci-artifacts/logcat.txt 2>&1 || true; adb pull /sdcard/Android/data/io.github.musicplayer/files/screenshots ci-artifacts/screenshots >/dev/null 2>&1 || true' EXIT

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent 82
adb install -r -t ci-apks/debug/app-debug.apk
adb install -r -t ci-apks/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w -r io.github.musicplayer.test/androidx.test.runner.AndroidJUnitRunner | tee ci-artifacts/instrumentation.txt

python3 - <<'PY'
from pathlib import Path
import re
result = Path('ci-artifacts/instrumentation.txt').read_text()
if not re.search(r'OK \(\d+ tests?\)', result) or 'FAILURES!!!' in result or 'INSTRUMENTATION_FAILED' in result:
    raise SystemExit('Android instrumented tests did not pass; see instrumentation.txt and logcat.txt')
print('Android instrumentation passed.')
PY

if [[ "${1:-}" == "35" ]]; then
  bash scripts/frida-smoke.sh
fi

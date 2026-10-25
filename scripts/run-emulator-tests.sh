#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-artifacts
collect_evidence() {
  local status=$?
  trap - EXIT
  adb logcat -d > ci-artifacts/logcat.txt 2>&1 || true
  adb pull /sdcard/Android/data/io.github.musicplayer/files/screenshots ci-artifacts/screenshots >/dev/null 2>&1 || true
  if [[ "$status" -ne 0 ]]; then
    adb exec-out screencap -p > ci-artifacts/failure-screen.png 2>/dev/null || true
  fi
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  python3 - "$status" <<'PY'
from pathlib import Path
import os
import re
import sys

root = Path('ci-artifacts')
report = root / 'instrumentation.txt'
text = report.read_text(errors='replace') if report.exists() else ''
match = re.search(r'OK \((\d+) tests?\)', text)
count = match.group(1) if match else 'No successful completion'
screenshots = len(list((root / 'screenshots').glob('*.png')))
status = 'Passed' if sys.argv[1] == '0' else 'Failed'
summary = f'### Android emulator validation: {status}\n\n'
summary += f'- Instrumentation: {count} tests\n- Captured UI screenshots: {screenshots}\n'
summary += '- Evidence: instrumentation output, logcat, build/device metadata and screenshots.\n'
(root / 'summary.md').write_text(summary)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
        output.write(summary)
PY
  exit "$status"
}
trap collect_evidence EXIT

adb wait-for-device
{
  git rev-parse HEAD
  adb shell getprop ro.build.version.sdk
  adb shell getprop ro.build.version.release
  adb shell getprop ro.product.cpu.abi
  adb shell wm size
  adb shell wm density
} > ci-artifacts/device-and-revision.txt
if [[ -n "${1:-}" ]]; then
  actual_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
  [[ "$actual_api" == "$1" ]] || { echo "Expected Android API $1 but emulator reports $actual_api" >&2; exit 1; }
fi
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put system font_scale 1.0
adb shell input keyevent 82
adb install -r -t ci-apks/debug/app-debug.apk
adb install -r -t ci-apks/androidTest/debug/app-debug-androidTest.apk
# Some Android 8 images refuse to clear the main buffer; this must not prevent
# instrumentation from running. The exit trap still captures available logs.
if ! adb logcat -c; then
  echo 'Warning: emulator log buffer could not be cleared; continuing with tests.' >&2
fi
timeout --signal=TERM 8m adb shell am instrument -w -r io.github.musicplayer.test/androidx.test.runner.AndroidJUnitRunner | tee ci-artifacts/instrumentation.txt

python3 - <<'PY'
from pathlib import Path
import re
result = Path('ci-artifacts/instrumentation.txt').read_text(errors='replace')
completed = re.search(r'OK \(([1-9]\d*) tests?\)', result)
if not completed or 'FAILURES!!!' in result or 'INSTRUMENTATION_FAILED' in result:
    raise SystemExit('Android instrumented tests did not pass; see instrumentation.txt and logcat.txt')
for required_suite in ('io.github.musicplayer.PlayerJourneyTest', 'io.github.musicplayer.data.RepositoryDownloadTest'):
    if f'class={required_suite}' not in result:
        raise SystemExit(f'Required test suite did not run: {required_suite}')
print(f'Android instrumentation passed: {completed.group(1)} tests.')
PY

if [[ "${1:-}" == "35" ]]; then
  bash scripts/frida-smoke.sh
fi

#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-artifacts
collect_evidence() {
  local status=$?
  trap - EXIT
  timeout --kill-after=5s 20s adb logcat -d > ci-artifacts/logcat.txt 2>&1 || true
  timeout --kill-after=5s 30s adb pull /sdcard/Android/data/io.github.musicplayer/files/screenshots ci-artifacts/screenshots >/dev/null 2>&1 || true
  if [[ "$status" -ne 0 ]]; then
    timeout --kill-after=5s 10s adb exec-out screencap -p > ci-artifacts/failure-screen.png 2>/dev/null || true
  fi
  timeout --kill-after=5s 5s adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  python3 - "$status" <<'PY'
from pathlib import Path
import json
import os
import re
import sys

root = Path('ci-artifacts')
report = root / 'instrumentation.txt'
text = report.read_text(errors='replace') if report.exists() else ''
match = re.search(r'OK \((\d+) tests?\)', text)
failed = re.search(r'Tests run:\s*(\d+),\s*Failures:\s*(\d+)', text)
instrumentation = f'Passed ({match.group(1)} tests)' if match else (
    f'Failed ({failed.group(1)} tests, {failed.group(2)} failures)' if failed else 'No successful completion'
)
results_file = root / 'validation-results.json'
results = json.loads(results_file.read_text()) if results_file.exists() else None
frida = 'Not run (setup or validation interrupted)'
if results:
    execution = results['instrumentation']
    if any(code != 0 for code in execution.values()):
        instrumentation += f'; exit codes: {execution}'
    if not results['frida']['required']:
        frida = f"Not required on API {results['api']}"
    else:
        frida = 'Passed' if results['frida']['exit_code'] == 0 else f"Failed (exit {results['frida']['exit_code']})"
screenshots = len(list((root / 'screenshots').glob('*.png')))
status = 'Passed' if sys.argv[1] == '0' else 'Failed'
summary = f'### Android emulator validation: {status}\n\n'
summary += f'- Instrumentation: {instrumentation}\n- Frida runtime smoke: {frida}\n- Captured UI screenshots: {screenshots}\n'
summary += '- Evidence: instrumentation output, independent validation exit codes, Frida logs when required, logcat, build/device metadata and screenshots.\n'
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
actual_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [[ -n "${1:-}" ]]; then
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
if ! timeout --kill-after=5s 10s adb logcat -c; then
  echo 'Warning: emulator log buffer could not be cleared; continuing with tests.' >&2
fi
# Run both independent checks before deciding the job result. Preserve transport,
# timeout, transcript-write and assertion failures; none become an implicit pass.
set +e
timeout --signal=TERM --kill-after=10s 8m adb shell am instrument -w -r io.github.musicplayer.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee ci-artifacts/instrumentation.txt
instrumentation_pipeline_status=("${PIPESTATUS[@]}")
set -e
instrumentation_process_status=${instrumentation_pipeline_status[0]}
instrumentation_log_status=${instrumentation_pipeline_status[1]}

if python3 - <<'PY'
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
then
  instrumentation_parser_status=0
else
  instrumentation_parser_status=$?
fi

frida_status=0
frida_required=false
if [[ "$actual_api" == "35" ]]; then
  frida_required=true
  if timeout --signal=TERM --kill-after=10s 6m bash scripts/frida-smoke.sh; then
    frida_status=0
  else
    frida_status=$?
  fi
fi

python3 - "$actual_api" "$instrumentation_process_status" "$instrumentation_log_status" "$instrumentation_parser_status" "$frida_required" "$frida_status" <<'PY'
import json
from pathlib import Path
import sys

api, process, transcript, assertions, required, frida = sys.argv[1:]
Path('ci-artifacts/validation-results.json').write_text(json.dumps({
    'api': int(api),
    'instrumentation': {'process_exit': int(process), 'transcript_exit': int(transcript), 'assertions_exit': int(assertions)},
    'frida': {'required': required == 'true', 'exit_code': int(frida) if required == 'true' else None},
}, indent=2) + '\n')
PY

if (( instrumentation_process_status != 0 || instrumentation_log_status != 0 || instrumentation_parser_status != 0 || frida_status != 0 )); then
  echo 'Android validation failed; see validation-results.json for independent check results.' >&2
  exit 1
fi

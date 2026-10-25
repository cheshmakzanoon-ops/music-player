#!/usr/bin/env bash
# Runtime observation of the rebuilt app on the existing API 35 CI emulator.
# Never installs, starts or instruments the reference APK or any other app.
set -euo pipefail

readonly FRIDA_VERSION=17.24.3
readonly FRIDA_TOOLS_VERSION=14.11.0
readonly FRIDA_SERVER_SHA256=44aead87103ab9738771e5eff659640fb6bf566fe83b8629444c1be99f87ce68
readonly APP_ID=io.github.musicplayer
readonly FRIDA_WORK=analysis/frida-ci
mkdir -p "$FRIDA_WORK" ci-artifacts

serial="${ANDROID_SERIAL:-$(adb get-serialno)}"
serial="${serial//$'\r'/}"
if [[ "$serial" != emulator-* ]]; then
    echo 'Frida smoke requires the disposable Android emulator, not a physical device.' >&2
    exit 1
fi
device_adb=(adb -s "$serial")
api="$("${device_adb[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
abi="$("${device_adb[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "$api" != 35 || "$abi" != x86_64 ]]; then
    echo "Expected API 35 x86_64 emulator; got API $api $abi." >&2
    exit 1
fi
"${device_adb[@]}" shell pm path "$APP_ID" | grep -q '^package:'

python3 -m venv "$FRIDA_WORK/venv"
"$FRIDA_WORK/venv/bin/python" -m pip install --disable-pip-version-check --timeout 30 --retries 2 \
    "frida==$FRIDA_VERSION" "frida-tools==$FRIDA_TOOLS_VERSION"
"$FRIDA_WORK/venv/bin/frida" --version

asset_name="frida-server-$FRIDA_VERSION-android-x86_64.xz"
curl --fail --silent --show-error --location --connect-timeout 20 --max-time 90 --retry 2 \
    "https://api.github.com/repos/frida/frida/releases/tags/$FRIDA_VERSION" \
    --output "$FRIDA_WORK/release.json"
python3 - "$FRIDA_WORK/release.json" "$asset_name" "$FRIDA_SERVER_SHA256" <<'PY'
import json, pathlib, sys
release = json.loads(pathlib.Path(sys.argv[1]).read_text())
asset = next(a for a in release['assets'] if a['name'] == sys.argv[2])
url = asset['browser_download_url']
if not url.startswith('https://github.com/frida/frida/releases/download/17.24.3/'):
    raise SystemExit('Unexpected Frida server asset origin')
if asset.get('digest') and asset['digest'] != 'sha256:' + sys.argv[3]:
    raise SystemExit('Published Frida asset digest differs from pinned digest')
pathlib.Path('analysis/frida-ci/asset-url.txt').write_text(url)
pathlib.Path('ci-artifacts/frida-asset.json').write_text(json.dumps({
    k: asset.get(k) for k in ('name', 'size', 'digest', 'browser_download_url')
}, indent=2) + '\n')
PY
curl --fail --silent --show-error --location --connect-timeout 20 --max-time 180 --retry 2 \
    "$(cat "$FRIDA_WORK/asset-url.txt")" --output "$FRIDA_WORK/server.xz"
printf '%s  %s\n' "$FRIDA_SERVER_SHA256" "$FRIDA_WORK/server.xz" | sha256sum --check --strict
xz --decompress --stdout "$FRIDA_WORK/server.xz" > "$FRIDA_WORK/frida-server"

remote_server="/data/local/tmp/musicplayer-frida-$$"
remote_log="/data/local/tmp/musicplayer-frida-$$.log"
remote_pid=''
host_port=''
cleanup() {
    status=$?
    trap - EXIT
    if [[ "$remote_pid" =~ ^[0-9]+$ ]]; then
        timeout 5 "${device_adb[@]}" shell kill "$remote_pid" >/dev/null 2>&1 || true
    fi
    timeout 5 "${device_adb[@]}" pull "$remote_log" ci-artifacts/frida-server.txt >/dev/null 2>&1 || true
    timeout 5 "${device_adb[@]}" shell rm -f "$remote_server" "$remote_log" >/dev/null 2>&1 || true
    if [[ "$host_port" =~ ^[0-9]+$ ]]; then
        timeout 5 "${device_adb[@]}" forward --remove "tcp:$host_port" >/dev/null 2>&1 || true
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

timeout 15 "${device_adb[@]}" root
timeout 30 "${device_adb[@]}" wait-for-device
[[ "$("${device_adb[@]}" shell id -u | tr -d '\r')" == 0 ]]
"${device_adb[@]}" push "$FRIDA_WORK/frida-server" "$remote_server"
"${device_adb[@]}" shell chmod 755 "$remote_server"
remote_pid="$("${device_adb[@]}" shell "$remote_server -l 127.0.0.1:27043 </dev/null >$remote_log 2>&1 & echo \$!" | tr -d '\r')"
[[ "$remote_pid" =~ ^[0-9]+$ ]]
sleep 1
"${device_adb[@]}" shell kill -0 "$remote_pid"
host_port="$("${device_adb[@]}" forward tcp:0 tcp:27043 | tr -d '\r')"
[[ "$host_port" =~ ^[0-9]+$ ]]
"${device_adb[@]}" shell am force-stop "$APP_ID"

# -q uses the CLI's bundled Java bridge; this is not a bare frida-core script.
# The CLI timeout bounds observation; the outer timeout also bounds connection.
timeout --kill-after=5s 35s "$FRIDA_WORK/venv/bin/frida" \
    -H "127.0.0.1:$host_port" -f "$APP_ID" -l scripts/frida-observe.js \
    --quiet --timeout 15 --exit-on-error --kill-on-exit --no-auto-reload \
    2>&1 | tee ci-artifacts/frida-smoke.txt
grep -qx 'MUSIC_PLAYER_FRIDA_MAIN_ACTIVITY_RESUMED' ci-artifacts/frida-smoke.txt
echo 'Frida smoke passed: rebuilt MainActivity resumed under observation.'

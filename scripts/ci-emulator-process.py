#!/usr/bin/env python3
"""Stop only this job's recorded emulator after device evidence is collected."""

import json
import os
from pathlib import Path
import re
import signal
import sys
import time


RECORD = Path("ci-artifacts/emulator-process.json")


def configuration():
    avd = os.environ.get("MUSIC_PLAYER_CI_AVD", "")
    if (
        os.environ.get("GITHUB_ACTIONS") != "true"
        or os.environ.get("RUNNER_OS") != "Linux"
        or not re.fullmatch(r"music-player-api-(26|35)-[0-9]+", avd)
        or os.environ.get("ANDROID_SERIAL") != "emulator-5554"
        or os.environ.get("EMULATOR_PORT") != "5554"
    ):
        raise SystemExit("Refusing process cleanup outside the named CI emulator job")
    if int(Path("/proc/self/stat").read_text().split("(", 1)[0]) != os.getpid():
        raise SystemExit("Refusing cleanup when procfs and process PID namespaces differ")
    sdk = Path(os.environ["ANDROID_HOME"]).resolve()
    executables = {
        sdk / "emulator/qemu/linux-x86_64" / name
        for name in ("qemu-system-x86_64", "qemu-system-x86_64-headless")
    }
    return avd, executables


def identity(pid):
    try:
        proc = Path("/proc") / str(pid)
        # The parenthesized process name can itself contain spaces or ')'.
        stat = (proc / "stat").read_text().rsplit(")", 1)[1].split()
        if stat[0] == "Z":
            return None
        return {
            "pid": int(pid),
            "start_time": stat[19],
            "executable": str((proc / "exe").resolve(strict=True)),
            "argv": (proc / "cmdline").read_bytes().decode(errors="surrogateescape").split("\0")[:-1],
        }
    except (FileNotFoundError, ProcessLookupError, PermissionError):
        return None


def matches(process, avd, executables):
    if not process or Path(process["executable"]) not in executables:
        return False
    args = process["argv"]
    return any(args[i:i + 2] == ["-avd", avd] for i in range(len(args) - 1)) and any(
        args[i:i + 2] == ["-port", "5554"] for i in range(len(args) - 1)
    )


def main():
    avd, executables = configuration()
    if sys.argv[1:] == ["record"]:
        found = [
            p for entry in Path("/proc").iterdir() if entry.name.isdigit()
            if matches(p := identity(entry.name), avd, executables)
        ]
        if len(found) != 1:
            raise SystemExit(f"Expected one exact CI emulator process, found {len(found)}")
        RECORD.write_text(json.dumps(found[0], indent=2) + "\n")
        print(f"Recorded CI emulator PID {found[0]['pid']} for {avd}")
        return
    if sys.argv[1:] != ["stop"]:
        raise SystemExit("Usage: ci-emulator-process.py record|stop")
    if not RECORD.exists():
        print("No recorded emulator process; no process was signalled")
        return
    recorded = json.loads(RECORD.read_text())
    try:
        # A pidfd keeps the signal bound to this process even if the PID is reused.
        process_fd = os.pidfd_open(recorded["pid"])
    except ProcessLookupError:
        print("Recorded emulator has exited; cleanup complete")
        return
    try:
        for sig in (signal.SIGTERM, signal.SIGKILL):
            current = identity(recorded["pid"])
            if current is None or current["start_time"] != recorded["start_time"]:
                print("Recorded emulator has exited; cleanup complete")
                return
            if not matches(current, avd, executables) or current != recorded:
                raise SystemExit("Emulator identity changed; refusing to signal any process")
            print(f"Sending {sig.name} to recorded CI emulator PID {recorded['pid']}", flush=True)
            try:
                signal.pidfd_send_signal(process_fd, sig)
            except ProcessLookupError:
                return
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                current = identity(recorded["pid"])
                if current is None or current["start_time"] != recorded["start_time"]:
                    print("Recorded emulator exited; cleanup complete")
                    return
                time.sleep(0.1)
        raise SystemExit("Recorded emulator did not exit after bounded TERM/KILL cleanup")
    finally:
        os.close(process_fd)


if __name__ == "__main__":
    main()

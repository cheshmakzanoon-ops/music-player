#!/usr/bin/env python3
"""Bounded, static inventory of the reference APK. Never executes APK code.

Generated binaries, manifests and tool logs stay in analysis/ (git-ignored).
The report contains hashes/counts/class names/URL hosts, not tokens or source.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile
import zlib

MAX_ENTRY = 256 * 1024 * 1024
MAX_TOTAL = 1024 * 1024 * 1024
ANDROID = "{http://schemas.android.com/apk/res/android}"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def inventory(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        if any(e.file_size > MAX_ENTRY for e in entries):
            raise ValueError("Entry exceeds static analysis size bound")
        if sum(e.file_size for e in entries) > MAX_TOTAL:
            raise ValueError("Total entry sizes exceed static analysis bound")
        native = [e for e in entries if e.filename.startswith("lib/") and e.filename.endswith(".so")]
        return {
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
            "zip_entries": len(entries),
            "declared_uncompressed_bytes": sum(e.file_size for e in entries),
            "dex_files": [{"name": e.filename, "bytes": e.file_size} for e in entries if re.fullmatch(r"classes\d*\.dex", e.filename)],
            "native_library_files": len(native),
            "native_abis": sorted({e.filename.split("/")[1] for e in native}),
            "native_library_names": sorted({Path(e.filename).name for e in native}),
            "resource_entries": sum(e.filename.startswith("res/") for e in entries),
            "asset_entries": sum(e.filename.startswith("assets/") for e in entries),
            "lspatch_entries": [e.filename for e in entries if e.filename.startswith("assets/lspatch/")],
        }


def extract_stored_member(archive_path: Path, name: str, destination: Path) -> None:
    # LSPatch's wrapper aliases ZIP entries into a stored embedded original APK.
    # Standard ZipFile.open rejects this overlap. Read only the declared bounded
    # stored span, check its local header, filename, full length, and CRC first.
    with zipfile.ZipFile(archive_path) as archive:
        entry = archive.getinfo(name)
    if entry.compress_type != zipfile.ZIP_STORED or entry.flag_bits & 1:
        raise ValueError("Only unencrypted, stored embedded originals are supported")
    if entry.file_size != entry.compress_size or not 0 < entry.file_size <= MAX_ENTRY:
        raise ValueError("Embedded original exceeds bounds or is not stored")
    with archive_path.open("rb") as source:
        source.seek(entry.header_offset)
        header = source.read(30)
        if len(header) != 30 or header[:4] != b"PK\x03\x04":
            raise ValueError("Invalid local ZIP header")
        name_len, extra_len = struct.unpack_from("<HH", header, 26)
        local_name = source.read(name_len).decode("utf-8")
        if local_name != name:
            raise ValueError("Central and local filenames differ")
        offset = entry.header_offset + 30 + name_len + extra_len
        if offset + entry.file_size > archive_path.stat().st_size:
            raise ValueError("Embedded file span lies outside archive")
        source.seek(offset)
        data = source.read(entry.file_size)
    if len(data) != entry.file_size or zlib.crc32(data) & 0xFFFFFFFF != entry.CRC:
        raise ValueError("Embedded original length/CRC mismatch")
    destination.write_bytes(data)


def dex_summary(data: bytes) -> tuple[list[str], list[str]]:
    if len(data) < 112 or not data.startswith(b"dex\n"):
        raise ValueError("Invalid DEX header")
    string_count, string_offset = struct.unpack_from("<II", data, 56)
    type_count, type_offset = struct.unpack_from("<II", data, 64)
    class_count, class_offset = struct.unpack_from("<II", data, 96)
    for count, offset, width in ((string_count, string_offset, 4), (type_count, type_offset, 4), (class_count, class_offset, 32)):
        if count > 1_000_000 or offset + count * width > len(data):
            raise ValueError("DEX table exceeds bounds")
    strings = []
    for index in range(string_count):
        offset = struct.unpack_from("<I", data, string_offset + index * 4)[0]
        for _ in range(5):  # skip bounded ULEB128 UTF-16 length
            if offset >= len(data):
                raise ValueError("Invalid DEX string offset")
            value = data[offset]
            offset += 1
            if value < 128:
                break
        end = data.find(b"\0", offset)
        if end < offset:
            raise ValueError("Unterminated DEX string")
        strings.append(data[offset:end].decode("utf-8", errors="replace"))
    types = [struct.unpack_from("<I", data, type_offset + i * 4)[0] for i in range(type_count)]
    classes = []
    for index in range(class_count):
        type_index = struct.unpack_from("<I", data, class_offset + index * 32)[0]
        descriptor = strings[types[type_index]]
        classes.append((descriptor[1:-1] if descriptor.startswith("L") and descriptor.endswith(";") else descriptor).replace("/", "."))
    return classes, strings


def command_log(arguments: list[str], destination: Path) -> str:
    result = subprocess.run(arguments, capture_output=True, text=True, check=True)
    destination.write_text(result.stdout + result.stderr)
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="?", type=Path, default=Path("reference/music-player-original.apk"))
    parser.add_argument("--work-dir", type=Path, default=Path("analysis"))
    parser.add_argument("--output", type=Path, default=Path("docs/analysis-metadata.json"))
    args = parser.parse_args()
    args.work_dir.mkdir(parents=True, exist_ok=True)
    report = {"analysis_date": "2026-10-25", "method": "static; no APK execution or service requests", "outer_apk": inventory(args.apk)}
    original = args.work_dir / "embedded-original.apk"
    extract_stored_member(args.apk, "assets/lspatch/origin.apk", original)
    report["embedded_original"] = inventory(original)
    for key, path in (("outer_apk", args.apk), ("embedded_original", original)):
        output = command_log(["apksigner", "verify", "--verbose", "--print-certs", str(path)], args.work_dir / f"{key}-signature.txt")
        report[key]["signature"] = {
            "verification_succeeded": True,
            "v2": "Verified using v2 scheme (APK Signature Scheme v2): true" in output,
            "v3": "Verified using v3 scheme (APK Signature Scheme v3): true" in output,
            "source_stamp": "Verified for SourceStamp: true" in output,
            "signer_dn": re.search(r"Signer: certificate DN: (.+)", output).group(1),
            "signer_sha256": re.search(r"Signer: certificate SHA-256 digest: (.+)", output).group(1),
        }
    manifest_text = command_log(["apkanalyzer", "manifest", "print", str(original)], args.work_dir / "AndroidManifest.xml")
    manifest = ET.fromstring(manifest_text)
    app = manifest.find("application")
    report["manifest"] = {
        "package": manifest.attrib["package"],
        "version_name": manifest.attrib.get(ANDROID + "versionName"),
        "version_code": manifest.attrib.get(ANDROID + "versionCode"),
        "sdk": {k.replace(ANDROID, ""): v for k, v in manifest.find("uses-sdk").attrib.items()},
        "application_class": app.attrib.get(ANDROID + "name"),
        "component_counts": dict(Counter(e.tag for e in app if e.tag in {"activity", "activity-alias", "service", "receiver", "provider"})),
        "permission_count": len(manifest.findall("uses-permission")),
        "notable_components": [e.attrib.get(ANDROID + "name") for e in app if e.tag in {"activity", "service"} and any(term in e.attrib.get(ANDROID + "name", "") for term in ("SpotifyMainActivity", "LoginActivity", "CallingCodePicker", "OffliningService", "SpotifyMediaLibraryService", "AppProtocolRemote", "OfflineDeviceLimit"))],
    }
    classes: list[str] = []
    hosts: set[str] = set()
    with zipfile.ZipFile(original) as archive:
        for entry in report["embedded_original"]["dex_files"]:
            names, strings = dex_summary(archive.read(entry["name"]))
            classes.extend(names)
            entry["class_count"] = len(names)
            for value in strings:
                for host in re.findall(r"https?://([a-zA-Z0-9.-]+)", value):
                    host = host.lower().rstrip(".")
                    if any(host == domain or host.endswith("." + domain) for domain in ("spotify.com", "spotify.net", "scdn.co", "spotifycdn.com")):
                        hosts.add(host)
    prefixes = ("com.spotify.", "androidx.compose.", "androidx.media3.", "com.google.android.exoplayer2.", "com.airbnb.epoxy.", "com.airbnb.lottie.", "com.facebook.", "okhttp3.", "retrofit2.", "io.reactivex.", "p.")
    report["dex"] = {"defined_classes": len(classes), "class_prefix_counts": {p: sum(n.startswith(p) for n in classes) for p in prefixes}, "native_bridge_classes": [name for name in classes if name in {"com.spotify.cosmos.cosmosimpl.NativeRouter", "com.spotify.cosmos.cosmosimpl.NativeLifetime", "com.spotify.playbacknative.AudioDriver", "com.spotify.playbacknative.AudioRenderer", "com.spotify.playbacknative.AudioTrackAdapter", "com.spotify.playbacknative.MediaCodecFileDecompressor", "com.spotify.playbacknative.OboeRenderer"}], "url_hosts_observed_in_strings": sorted(hosts), "url_host_note": "Static strings only: not evidence that hosts are active, accessible, supported public APIs, or contacted during a given flow."}
    native_dir = args.work_dir / "native"
    native_dir.mkdir(exist_ok=True)
    native_path = native_dir / "liborbit-jni-spotify.so"
    with zipfile.ZipFile(original) as archive:
        native_path.write_bytes(archive.read("lib/x86_64/liborbit-jni-spotify.so"))
    header = command_log(["readelf", "-h", str(native_path)], args.work_dir / "orbit-elf-header.txt")
    dynamic = command_log(["readelf", "-d", str(native_path)], args.work_dir / "orbit-elf-dynamic.txt")
    symbols = command_log(["readelf", "--dyn-syms", "--wide", str(native_path)], args.work_dir / "orbit-elf-symbols.txt")
    report["native_orbit_bridge"] = {
        "abi": "x86_64",
        "bytes": native_path.stat().st_size,
        "sha256": sha256(native_path),
        "elf_class": re.search(r"Class:\s+(.+)", header).group(1),
        "machine": re.search(r"Machine:\s+(.+)", header).group(1),
        "dependencies": re.findall(r"Shared library: \[([^\]]+)\]", dynamic),
        "exports_jni_onload": bool(re.search(r"\sJNI_OnLoad(?:\s|$)", symbols)),
        "scope": "ELF header, dynamic dependencies and symbol inventory only; not a reconstruction of native playback or a server implementation.",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Wrote {args.output}; {len(classes)} defined classes; no APK code executed.")


if __name__ == "__main__":
    main()

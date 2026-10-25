#!/usr/bin/env python3
"""Recreate the project's original 24-second audio fixtures (Python + FFmpeg).

These are procedurally composed studio samples, not tracks from Spotify or another
commercial catalogue. No sample libraries or copyrighted recordings are used.
"""
from __future__ import annotations

import hashlib
import json
import math
import random
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RATE = 22050
DURATION = 24
SAMPLES = [
    ("first-light", "First light", "Ambient / 86 BPM", 86, [45, 48, 52, 55], 0xFFB6FF64),
    ("night-drive", "Night drive", "Electronic / 112 BPM", 112, [42, 45, 49, 52], 0xFFBFA1FF),
    ("soft-focus", "Soft focus", "Downtempo / 74 BPM", 74, [48, 52, 55, 59], 0xFFFFBFA0),
    ("blue-hour", "Blue hour", "Ambient / 92 BPM", 92, [43, 46, 50, 53], 0xFF78DDE8),
    ("afterimage", "Afterimage", "Electronic / 124 BPM", 124, [40, 43, 47, 50], 0xFFFF81BB),
    ("low-tide", "Low tide", "Downtempo / 80 BPM", 80, [41, 45, 48, 52], 0xFFFFD479),
]


def render(index: int, bpm: int, chord: list[int], destination: Path) -> None:
    rng = random.Random(1703 + index)
    beat = 60 / bpm
    count = RATE * DURATION
    pcm = bytearray(count * 2)
    freq = lambda note: 440 * 2 ** ((note - 69) / 12)
    tones = [freq(n) for n in chord]
    for sample in range(count):
        t = sample / RATE
        phase = t % beat
        step = int(t / (beat / 2))
        onset = t % (beat / 2)
        fade = min(t / 1.2, 1.0, (DURATION - t) / 2.0)
        pad = sum(math.sin(2 * math.pi * f * t + 0.15 * math.sin(t * 0.3)) for f in tones) * 0.07
        arp = math.sin(2 * math.pi * tones[(step + index) % 4] * 2 * t) * math.exp(-onset * 8) * 0.16
        bass = math.sin(2 * math.pi * tones[0] / 2 * t) * (0.09 + 0.02 * math.sin(t * 0.4))
        kick = math.sin(2 * math.pi * (46 * phase + 3.5 * (1 - math.exp(-phase * 30)))) * math.exp(-phase * 20) * 0.18
        hat = (rng.random() * 2 - 1) * math.exp(-onset * 95) * 0.035
        value = math.tanh((pad + arp + bass + kick + hat) * fade) * 0.68
        struct.pack_into("<h", pcm, sample * 2, round(value * 32767))
    with wave.open(str(destination), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(pcm)


def main() -> None:
    assets = ROOT / "app/src/main/assets"
    (assets / "audio").mkdir(parents=True, exist_ok=True)
    audio = ROOT / "catalog/audio"
    audio.mkdir(parents=True, exist_ok=True)
    tracks = []
    for index, (slug, title, mood, bpm, chord, accent) in enumerate(SAMPLES):
        with tempfile.TemporaryDirectory() as directory:
            wav = Path(directory) / "track.wav"
            render(index, bpm, chord, wav)
            target = audio / f"{slug}.ogg"
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", str(target)], check=True)
            if index == 0:
                (assets / "audio/first-light.wav").write_bytes(wav.read_bytes())
        data = target.read_bytes()
        tracks.append({
            "id": slug, "title": title, "artist": "Music Player Studio",
            "album": mood, "durationMs": DURATION * 1000,
            "streamUri": f"https://raw.githubusercontent.com/cheshmakzanoon-ops/music-player/main/catalog/audio/{slug}.ogg",
            "accent": accent, "artworkSeed": index, "downloadable": True,
            "source": "Original studio sample", "license": "CC0-1.0",
            "sha256": hashlib.sha256(data).hexdigest(), "sizeBytes": len(data),
        })
        print(slug, len(data), "bytes")
    catalog = {"version": 1, "description": "Six original procedural studio samples; not the Spotify catalogue.", "tracks": tracks}
    (ROOT / "catalog/catalog.json").write_text(json.dumps(catalog, indent=2) + "\n")
    bundled = json.loads(json.dumps(catalog))
    first = bundled["tracks"][0]
    first.update(streamUri="asset:///audio/first-light.wav", downloadable=False)
    first.pop("sha256")
    first.pop("sizeBytes")
    (assets / "catalog.json").write_text(json.dumps(bundled, indent=2) + "\n")


if __name__ == "__main__":
    main()

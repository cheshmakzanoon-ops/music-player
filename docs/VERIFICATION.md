# Verified Android build — 2026-10-25

**The complete [GitHub Actions run 40184075722](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722) passed.**
Its tested source revision is [`b7b2d3f53eb85f0106d79ae0ad79427144634eb8`](https://github.com/cheshmakzanoon-ops/music-player/commit/b7b2d3f53eb85f0106d79ae0ad79427144634eb8).
The documentation and screenshot commit after it does not change the application,
test code, dependencies or workflow. The structured record is [verification.json](verification.json).

## Download and identify the tested app

[Download the validation APK bundle](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12595024417).
The installable application inside it is `debug/app-debug.apk`.

| Property | Value |
| --- | --- |
| Application | Music Player / NIGHT SHIFT |
| Package | `io.github.musicplayer` |
| Version | `0.2.0` / code `2` |
| Build | Debug, signed for testing |
| Minimum / target Android API | 26 / 35 |
| APK size | 22,373,853 bytes (about 21.3 MiB) |
| APK signature | v2 verified |
| ZIP alignment | 16 KiB alignment check passed |
| APK SHA-256 | `b40f1d2e7d596e593dbde5aff85c20435a899028d9b668bcffd402de6ae9e334` |
| Signing certificate SHA-1 | `b2e833053881d1c10e8ac0eb58aa0b37631b6d1a` |

This fingerprint belongs to this exact CI APK. Future debug builds can have a new
debug signing identity. Production distribution needs a persistent signing identity
kept outside the repository. See [Spotify configuration](SPOTIFY_CONNECTION.md) if
registering the exact APK with Spotify.

## Completed checks

| Check | Result | Evidence |
| --- | --- | --- |
| Build | Debug application and instrumentation APKs built | [Build job](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/job/121141308241) |
| JVM tests | 8 passed, 0 failed | [Build reports](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12595775280) |
| Android lint | 0 errors; 20 warnings | Same build reports |
| API 26 / Android 8.0 | 17 passed, 0 failed; 43.593 seconds | [API 26 evidence](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12594784964) |
| API 35 / Android 15 | 18 passed, 0 failed; 86.408 seconds | [API 35 evidence](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12595950587) |
| Frida on API 35 | Lifecycle marker observed; process exit 0 | `frida-smoke.txt` in API 35 evidence |
| Emulator shutdown | Both recorded emulator processes exited successfully | `emulator-cleanup.txt` in each artifact |

Both emulators used x86-64, a 1080 × 1920 display and density 420. All emulator work
ran in GitHub Actions; no local emulator was installed. The suites overlap between
versions. API 26 runs one fewer test because the content-provider fixture requires
API 29 or later; API 35 covers that fixture.

The 20 lint warnings comprise 11 dependency-version notices, six Kotlin-extension
suggestions, one target-API notice, one exported-service notice and one backup-rule
notice. They are not described as fixed. The exported media service admits same-UID
or trusted controllers in its session callback.

### What the app tests establish

- Account-free launch, navigation, search/empty results, source validation and
  availability of the local library when Spotify is not configured.
- Real playback, seeking, favorites, queue state, Activity recreation and continuing
  playback after Home followed by working controls on return.
- Actual HTTPS WAV/Ogg transfer and decoding, including Ogg byte-range requests and
  seeking. The test server serves real audio rather than a fake player state.
- Downloaded audio survives repository restart and plays after the HTTPS server is
  shut down. A previously queued track also resolves to its newly saved local file.
- Removing a download from a queued item falls back to its original HTTPS source.
- Truncated transfers, size/checksum mismatches, HTML bodies and HTTP errors leave
  no false completed download. Startup reconciles orphaned files.
- A local import remains playable from its private copy after the provider's
  original file is deleted; this fixture is covered on API 35.
- Large-text Settings/source controls remain reachable through tested interactions.

The Frida transcript contains the actual messages:

```text
MUSIC_PLAYER_FRIDA_OBSERVER_READY
MUSIC_PLAYER_FRIDA_MAIN_ACTIVITY_RESUMED
```

This observes the rebuilt application's lifecycle. The supplied modified Spotify
APK was not launched or dynamically instrumented.

## Visual evidence

The README gallery preserves the original API 26 Discover and API 35 Now Playing,
Search and Queue PNGs from this run. Their source filenames and SHA-256 hashes are recorded in
[verification.json](verification.json). No image generation or visual retouching
was used for these screenshots.

The preserved [large-text Settings screenshot](screenshots/settings-large-text-api26.png)
comes from API 26. **API 35 `08-settings-large-text.png` is a stale Discover frame**
after font-scale recreation and is not valid Settings evidence. It is excluded from
the gallery. The API 35 Settings assertions passed, and its later enlarged source
dialog capture is correct. This capture limitation remains open; a green test run
does not imply every captured image is correct.

## Limits of this verification

The included catalogue contains six original 24-second samples. Local imports and
permitted direct HTTPS audio are supported. This run does not establish access to
Spotify's catalogue, anonymous Spotify playback, unrestricted downloads, regional
bypasses or reconstructed Spotify servers.

The optional official Spotify App Remote connection is implemented but has not been
verified with registered client settings, a matching signing registration and an
authorized account. Tests cover its unconfigured state and confirm it does not block
the local player. See [SPOTIFY_CONNECTION.md](SPOTIFY_CONNECTION.md).

These results do not establish compatibility with every physical phone, every host
or every country, or uninterrupted availability. Downloads are app-process managed;
persistent background scheduling and resumable transfers are not implemented.

Workflow artifacts have limited retention (30 days for APK/emulator artifacts and
14 days for build reports). Source, the structured verification record and selected
screenshots are retained in the repository.

# Development record

## 2026-10-25 — investigation and original player implementation

The supplied reference is `com.spotify.music`, version `9.1.78.2215` (145505425).
Its archive contains an LSPatch loader, an embedded original APK, and a patch module.
The reference is retained unchanged. Extracted code, executable patch modules, keys,
and reverse-engineering working files are not part of the new application's source.

The new application is original Kotlin/Compose code under `io.github.musicplayer`.
It opens directly without an app account or a country setup wizard. Its NIGHT SHIFT
interface uses original animated Canvas artwork, blue-black surfaces, cyan and
magenta accents, acid-yellow playback controls, and an optional reduced-motion mode.

### Implemented increments

| Area | Delivered behavior |
| --- | --- |
| Reference analysis | Bounded extraction, hashes and signatures, decoded manifest/resources, DEX inventory, selected JADX classes, native ELF inventory and scoped Ghidra analysis. The detailed scope and unresolved questions are in [APK_ANALYSIS.md](APK_ANALYSIS.md). |
| Android foundation | Pinned Gradle wrapper, JDK 17 baseline, Kotlin/Compose, Media3, API 26 minimum and API 35 compile/target. |
| Playback | Actual Media3 decoding, foreground media service, queue, seek, shuffle, repeat, audio focus, headset-disconnect handling and persisted session state. |
| Library | Device-local favorites, file-picker imports, direct HTTPS sources, search and offline/local filters. |
| Offline audio | Private copies, bounded transfers, published size/SHA-256 validation, atomic writes, cancellation cleanup and startup reconciliation. A queued item resolves its saved file when playback opens the source. |
| Original catalogue | Six generated 24-second CC0 samples; First light is bundled and the other samples stream over HTTPS. All six published audio files were fetched and matched their catalogue checksums. |
| Supported Spotify connection | Published App Remote SDK 0.8.0, explicit connection/error states, current metadata/artwork and supported controls. Registered application settings and user authorization are still required. |
| Automation | GitHub Actions builds, JVM tests, Android lint, API 26/API 35 emulator journeys, screenshots/logs and a bounded Frida observation of the rebuilt app. |

Commits were pushed incrementally to `main` throughout development. Concurrent
updates already on the remote branch were inspected and preserved.

Spotify's server software and catalogue are not contained in its Android APK.
Its supported Android integration requires authorization and delegates playback and
caching to Spotify. A redesigned client cannot promise anonymous access to Spotify's
whole catalogue, unrestricted regional availability, or unrestricted downloads.
The optional connection uses that supported integration. Its configuration and
verification limits are documented in [SPOTIFY_CONNECTION.md](SPOTIFY_CONNECTION.md).

## Verification record

[Run 40184075722](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722)
completed successfully at `b7b2d3f53eb85f0106d79ae0ad79427144634eb8`.

| Check | Final observed result |
| --- | --- |
| Debug app and instrumentation APK builds | Passed |
| JVM tests | 8 passed, 0 failures |
| Android lint | 0 errors, 20 warnings |
| Android 8 / API 26 emulator | 17 tests passed; job passed |
| Android 15 / API 35 emulator | 18 tests passed; job passed |
| Frida observation on API 35 | Actual rebuilt MainActivity resume marker observed; exit 0 |
| Emulator shutdown | Both exact recorded QEMU processes terminated; evidence uploaded |
| Downloaded debug APK | APK v2 signature and 16 KiB ZIP alignment verified |

The [verification report](VERIFICATION.md) records the exact APK checksum, artifacts,
test scope and screenshot caveat. A machine-readable record is in
[verification.json](verification.json). Documentation and selected screenshots were
added after this run; the tested application source was not changed.

Earlier runs exposed Android 8 log-clear failures, a mock HTTPS server that did not
serve Ogg byte-range seeks, media-session teardown and Activity-resume test races,
and an emulator process that kept the runner alive after tests finished. These were
fixed with bounded setup/cleanup, a real byte-range fixture, complete session teardown,
and explicit resumed-window synchronization. Playback assertions still use actual
decoding and state advancement; no failed assertions were disabled.

Frida 17.24.3 also has a reported `-H` WebSocket message-size regression when loading
its Java bridge ([upstream issue 3806](https://github.com/frida/frida/issues/3806)).
The final check uses the exact Android device through `-D`, and the actual lifecycle
marker passed. No SELinux enforcement setting was changed.

Visual review verified the primary player screens and the Android 8 large-text
Settings dialog. Android 15's `08-settings-large-text.png` remains a stale Discover
frame despite passing Settings assertions; it is excluded from the published gallery.
The Android 15 large-text source dialog renders correctly.

An earlier optimized release build at `8517e7c` passed R8 shrinking. The delivered
artifact above is the CI-tested debug build; no production signing key was created
or published. The pinned Spotify AAR also matched its documented SHA-256 checksum.

### Verification boundaries

The HTTPS tests use a real local TLS server and actual WAV/Ogg decoding. Offline
tests stop the server before playing the saved file and include items queued before
a download or its removal. Import tests verify a private copy survives deletion of
the provider's original file. The local content-provider fixture requires API 29+;
the API 35 job covers that case.

The Frida check observes the rebuilt MainActivity lifecycle only. It does not launch
the supplied modified APK or instrument Spotify. Authenticated Spotify playback is
not verified: no registered client settings, matching signing registration or
authorized user session were supplied. The emulator suite checks the optional
connection screen's unavailable state and confirms it does not block local listening.

These are emulator and build results, not a claim of compatibility with every phone,
all countries, every audio host or uninterrupted service availability. Background
download scheduling/resume, production signing and authenticated Spotify integration
validation remain outside the completed verification.

## Sources

- [Spotify Android SDK](https://developer.spotify.com/documentation/android)
- [Spotify Web API](https://developer.spotify.com/documentation/web-api)

The original APK has not been launched or trusted as executable application code.

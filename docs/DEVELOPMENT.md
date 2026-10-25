# Development record

## Verified delivery — 2026-10-25

Source revision: [`b7b2d3f53eb85f0106d79ae0ad79427144634eb8`](https://github.com/cheshmakzanoon-ops/music-player/commit/b7b2d3f53eb85f0106d79ae0ad79427144634eb8).
[GitHub Actions run 40184075722](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722)
completed successfully for the build and both emulator jobs.

| Check | Verified result |
| --- | --- |
| Debug application and instrumentation APKs | Built successfully; application version 0.2.0, package `io.github.musicplayer`. |
| JVM tests | 8 passed; zero failures, errors or skips. |
| Android lint | Zero errors, 20 warnings: 11 dependency suggestions, 6 UseKtx suggestions, and one each for OldTargetApi, ExportedService and DataExtractionRules. |
| Android 8 / API 26 emulator | 17 of 17 instrumentation tests passed in 43.593 seconds. |
| Android 15 / API 35 emulator | 18 of 18 instrumentation tests passed in 86.408 seconds. |
| Frida observation on API 35 | Exit code zero; both observer-ready and actual MainActivity-resumed markers recorded. |
| Workflow integrity | Instrumentation transport, transcript and assertion-parser exit codes were zero; each named emulator was stopped after evidence collection. |
| Delivered APK | 22,373,853 bytes; APK v2 signature verified; 16 KiB ZIP alignment check passed. |

The additional API 35 case is the document-provider import fixture, which requires
API 29+. Both device/revision records identify the source revision above. The
Frida check observes the rebuilt application's lifecycle; it does not test an
authenticated Spotify session.

Download the [application/test APK artifact](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12595024417)
and install `debug/app-debug.apk`. The debug APK's SHA-256 is:

```text
b40f1d2e7d596e593dbde5aff85c20435a899028d9b668bcffd402de6ae9e334
```

Other evidence: [build reports](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12595775280),
[API 26 logs/screenshots](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12594784964),
[API 35 logs/screenshots](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12595950587).
The [curated screenshots](screenshots/README.md) are copied unchanged from those
artifacts. One raw API 35 Settings capture retained a stale home frame and is
excluded; the verified API 26 large-text Settings capture is used instead.

The passing run includes the actual HTTPS Ogg range/seek regression, completed
service/session teardown between UI tests, and synchronization with the resumed
Activity before exercising its controls. Frida uses the exact emulator's ADB
transport to avoid the pinned release's documented
[remote-host WebSocket issue](https://github.com/frida/frida/issues/3806).
No app playback assertion was disabled to obtain these results.

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

- Local debug app and instrumentation APK builds, eight JVM tests and Android lint
  passed after integrating the supported Spotify connection. Lint reported zero
  errors; remaining warnings concerned dependency update/style notices, backup rules
  and the exported media service. The service admits same-UID or trusted controllers.
- The optimized release build, including R8 shrinking, also passed. Its APK is
  unsigned; no production signing key has been created or published.
- The debug APK's v2 signature and 16 KiB ZIP alignment were verified. The pinned
  Spotify AAR matched its documented SHA-256 checksum.
- In [run 40180546153](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40180546153)
  at `3e907ab`, API 35 instrumentation passed all 14 tests. That run remained red:
  API 26 stopped before instrumentation because log clearing failed, and the API 35
  Frida command used an unsupported flag. Both harness failures were fixed in
  subsequent commits; the app assertions were not weakened.
- Screenshot review found dark status icons and evidence captures that could lag
  behind the tested UI. The status-bar contrast and screenshot synchronization were
  corrected, along with adaptive player sizing and consistent audio-URL validation.

The verified source revision and run above are the authority for this delivery.
Documentation and screenshot-only commits do not trigger the workflow. For future
application changes, consult their [Actions run](https://github.com/cheshmakzanoon-ops/music-player/actions/workflows/android.yml);
a successful earlier run does not establish that changed application code passed.

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

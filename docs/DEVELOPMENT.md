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

- Local debug app and instrumentation APK builds, eight JVM tests and Android lint
  passed after integrating the supported Spotify connection. Lint reported zero
  errors and 19 warnings, primarily dependency update/style notices, backup rules
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

The current commit's [Actions run](https://github.com/cheshmakzanoon-ops/music-player/actions/workflows/android.yml)
is the authority for its emulator result. A successful earlier run does not prove
the latest code passed, and local builds do not substitute for emulator tests.

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

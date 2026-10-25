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

## Spotify catalogue extension — 2026-10-25

After the earlier local-player verification, a separate in-app Spotify Web API
catalogue integration was implemented on `main`. Its first complete successful
run was [40187682490](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40187682490),
source revision [`0968861`](https://github.com/cheshmakzanoon-ops/music-player/commit/0968861d62cc5969ece14ffd555fd51282af58e6).

| Check | Result | Evidence |
| --- | --- | --- |
| Compile, unit tests, lint | Passed; **16 JVM tests**, no failures; **0 lint errors, 25 warnings** | [Build reports](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40187682490/artifacts/12597495552) |
| Android 8 / API 26 | **18/18 instrumentation tests** passed in 47.768 seconds | [Emulator evidence](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40187682490/artifacts/12597406197) |
| Android 15 / API 35 | **19/19 instrumentation tests** passed in 99.245 seconds | [Emulator evidence](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40187682490/artifacts/12597471131) |
| Frida / API 35 | Lifecycle smoke succeeded | Same API 35 artifact |
| Installable APK | Built and uploaded | [APK bundle](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40187682490/artifacts/12597240925) |

The new JVM tests exercise Spotify's OAuth PKCE challenge/state validation, a
real bounded loopback callback, exact redirect matching, code exchange and
refresh requests against a mock server, actual `/v1/search` request shape,
Spotify catalogue JSON parsing and visible 403/429 errors. The additional
emulator test checks that the Spotify catalogue section is optional and that
local search continues to work without starting Spotify authorization.
The new [search and connection screenshots](screenshots/README.md#spotify-catalogue-extension-screenshots)
were reviewed as unmodified emulator captures.

These tests provide evidence for implementation correctness, **not** proof
of a live account connection. The CI workflow was not given a Spotify Client ID,
allowlisted user, matching App Remote signing fingerprint or Spotify user session.
Live Spotify access and playback therefore remain **unverified**, and Spotify
continues to enforce account, market, playback and offline-access restrictions.

Later changes add a deterministic populated-result UI emulator test and advance
the catalogue-capable app to version 0.3.0. Those changes were successfully
verified in the final workflow recorded below.

### Final version 0.3.0 verification

**[Run 40188827097](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40188827097)**
completed successfully for tested source revision
[`cf11d0c1cf1852602a479f46ff07e3328a3376ba`](https://github.com/cheshmakzanoon-ops/music-player/commit/cf11d0c1cf1852602a479f46ff07e3328a3376ba).
All three workflow jobs succeeded.

| Check | Verified result | Artifact |
| --- | --- | --- |
| Kotlin compile, JVM tests, lint, APKs | **16/16 JVM tests**, 0 lint errors, 25 warnings | [Build reports](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40188827097/artifacts/12596968516) |
| Android 8 / API 26 x86-64 | **19/19 emulator tests** passed in 44.126 seconds | [API 26 evidence](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40188827097/artifacts/12597202311) |
| Android 15 / API 35 x86-64 | **20/20 emulator tests** passed in 97.881 seconds | [API 35 evidence](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40188827097/artifacts/12597398385) |
| Frida on Android 15 | Both observer/lifecycle markers observed, process exit 0 | API 35 evidence |
| Installable Android application | `debug/app-debug.apk` in archive | [Validation APKs](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40188827097/artifacts/12597327900) |

The latest emulator suites include the fixture-based test that renders Spotify
track, album and artist search rows and checks the exact `spotify:` URI emitted
on selection. No real Spotify account or Spotify-hosted audio stream was simulated.

The actual 0.3.0 APK was independently downloaded, inspected and checked:

| Property | Verified value |
| --- | --- |
| Package | `io.github.musicplayer` |
| Version | `0.3.0`, version code `3` |
| Minimum / target Android API | 26 / 35 |
| APK size | 22,439,389 bytes |
| APK SHA-256 | `de7b78cbdc9704c4b0955643bfda51793927d982a1b244d103c66660181d1d43` |
| APK signing | Debug signature v2 valid |
| APK certificate SHA-1 | `d56154cf64efb7d64a8b15948ad996cd71a2b03d` |
| 16 KiB ZIP alignment | `zipalign -c -P 16 -v 4` passed |
| ZIP archive integrity | `unzip -t` passed |

These exact numbers apply to the tested source revision above. A documentation
commit after it does not modify app files or invalidate the test evidence.
The debug certificate changes across fresh Actions builds; register the
fingerprint of the **actual APK installed** when setting up App Remote.
Live Spotify access still requires the registered public Client ID and a
Spotify-authorized user; it is not included in this test APK.

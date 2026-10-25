# Reference APK: observed architecture and rebuild limits

Analysis date: 2026-10-25. This is a **static analysis of the Android client**, not a reconstruction of Spotify's servers or a complete decompilation of the product. The supplied modified APK was not executed. No credentials were recovered, and no private service requests were made.

The machine-readable evidence is in [analysis-metadata.json](analysis-metadata.json). Reproduce its hashes, signatures, manifest, DEX counts and native ELF inventory with [inspect_reference_apk.py](../scripts/inspect_reference_apk.py). Generated APKs, decoded resources, Java output and raw logs remain in the ignored `analysis/` directory.

## 1. Identity and packaging

The supplied file identifies as **Spotify**, package `com.spotify.music`, version `9.1.78.2215` / version code `145505425`. It is wrapped with LSPatch: the outer file contains a small replacement `classes.dex`, an embedded original APK, a loader DEX, four ABI-specific LSPatch libraries, and a separate patch module. That module was inventoried by filename only; its behavior has not been established.

| Property | Uploaded wrapper | Embedded `assets/lspatch/origin.apk` |
| --- | --- | --- |
| Bytes | 153,583,820 | 148,402,442 |
| DEX files | 1 replacement root DEX, plus loader in assets | 13 |
| Root DEX size | 11,024 bytes | 9,512,776 bytes for `classes.dex` |
| APK v2 signature | Not present/verified | Verified |
| APK v3 signature | Verified | Verified |
| SourceStamp | Not present/verified | Verified, signer DN identifies Google |
| APK signer DN | `CN=IN` | `CN=Spotify, OU=Android, O=Spotify, L=Stockholm, ST=Stockholm, C=SE` |
| APK signer certificate SHA-256 | `1da5e08fffa51acf0ae1cab33b0878b3a386557a2de4d698cb65d9c722ea6d55` | `6505b181933344f93893d586e399b94616183f04349cb572a9e81a3335e28ffd` |

The outer file SHA-256 is `53ed2e463c712db03a23bc4a06ba08d5d9b033e4b9ea0928032f08413016fd0e`. The embedded original SHA-256 is `e8d8c7316619a63bd8a314d51f389feac9db70d62234b0da87a1b75b08ae85d2`.

Signature verification establishes integrity against the certificates contained in these artifacts. An independent official-download comparison has not been performed, and the different outer signer must not be treated as Spotify's signature.

### Why ordinary ZIP extraction failed

The wrapper aliases ZIP entries into the embedded original's stored byte span. Both `unzip` and Python's normal `ZipFile.open()` reject the overlapping entries. This is a concrete packaging observation; it alone does not establish malicious behavior or prove a zip bomb.

The inventory script only extracts the specifically named embedded original. It requires an unencrypted, uncompressed member, checks the local header and filename, enforces a 256 MiB per-entry bound and 1 GiB total declared-size bound, verifies the span lies inside the uploaded file, and validates the extracted length and CRC. It does not perform an unrestricted archive extraction. The resulting embedded APK then passes `apksigner verify`.

## 2. Frontend and application structure

Direct parsing of the embedded DEX class tables counted **95,351 defined classes**. Of these, 9,114 retain `com.spotify.*` names and 80,906 use the obfuscated `p.*` namespace. These counts include dependencies; they are not counts of independently implemented application features. Obfuscation and compiler-generated code make decompiled Java a partial representation of the original source.

The embedded manifest declares:

- `com.spotify.music.SpotifyApplication` as its application class.
- 86 activities, 4 activity aliases, 39 services, 36 receivers and 9 providers.
- 55 requested permissions.
- Minimum SDK 24 and target SDK 37. The outer wrapper raises the reported minimum to SDK 28. These are different artifacts; the wrapper's minimum must not be attributed to the embedded original.
- Native libraries for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`.

Apktool decoded 4,742 original `res/` entries. Its decoded output contains **1,985 layout XML files**, including configuration variants. Selected layout structures show a mixed Android view architecture:

| Observed element | Concrete evidence | Interpretation |
| --- | --- | --- |
| Main navigation/activity | `SpotifyMainActivity`, `empty_main.xml`, fragment lifecycle hooks and `ViewModelProvider` usage in selected JADX output | A coordinating activity hosts feature-specific UI and state; replacing a single layout is insufficient. |
| Home | `funkis_home.xml` uses `CoordinatorLayout`, `AppBarLayout`, `CollapsingToolbarLayout` and container views | Home has a collapsing app bar with dynamically populated content. |
| Search | `page_search.xml` uses `ConstraintLayout` and `RecyclerView`; separate result layouts exist for tracks, artists, albums, playlists and podcasts | Search is a collection of content types and states, not a static list. |
| Library | `library_row_layout.xml` uses Spotify `ArtworkView`, accessory views and text rows | Artwork, status and row actions are composed from reusable components. |
| Signup/account flow | `LoginActivity`, `QuickLoginActivity`, `DelegatingLoginActivity`, `CallingCodePickerActivity` | Login and country calling-code selection are explicit frontend flows. Removing their views does not remove remote authorization requirements. |
| Offline management | `OffliningService`, `OfflineDeviceLimitReachedActivity`, offline-ready and auto-download resources | The original has managed offline state and device-limit UI. Their presence does not grant permission or capability to export catalogue audio. |

DEX names also show AndroidX Compose, Media3 and RxJava components, plus numerous obfuscated dependencies. Class-prefix counts are conservative evidence of their presence, not a reliable measure of how much UI uses each framework.

JADX recovered the main activity's structure, including activity lifecycle, incoming-intent handling, view-model lookup and fragment hooks. That run exited with one decompilation error in an obfuscated dependent method and 39 warnings. It is therefore **partial evidence**, not a clean rebuildable copy of Spotify's code. No recovered source or Spotify resources are used as the new app's source code or artwork.

## 3. Playback and the native bridge

The original includes 48 native library files: 12 distinct names across four ABIs. The substantial core is `liborbit-jni-spotify.so`. Its x86-64 variant is **27,373,800 bytes**, ELF64, SHA-256 `36ed3584b42137ffcb22d90754c77eeaa1cd8d52db46f44b6fe7337d245e0c65`.

`readelf` found imports from `libandroid.so`, `liblog.so`, `libOpenSLES.so`, `libmediandk.so`, `libm.so`, `libdl.so` and `libc.so`, and an exported `JNI_OnLoad` entry. These are evidence of an Android native bridge and multimedia/system dependencies. They are not evidence of a public streaming interface.

Managed bridge classes present in the DEX include:

- `com.spotify.playbacknative.AudioDriver`, `AudioRenderer`, `AudioTrackAdapter` and `OboeRenderer`.
- `com.spotify.playbacknative.MediaCodecFileDecompressor`.
- `com.spotify.cosmos.cosmosimpl.NativeRouter` and `NativeLifetime`.

A separate, successful JADX extraction of `AudioDriver` shows an Android `AudioTrack` output path, playback-position callbacks, audio-route handling and native callbacks for underrun, destroy and flush events. This supports the architecture interpretation that managed Android audio lifecycle code and a native playback core cooperate. It does **not** recover the native core's full implementation, network protocol or content authorization logic.

JADX also successfully inspected `NativeRouter`: its scheduling, lifetime and request-resolution operations cross native method boundaries. This is a concrete client-side message-routing interface, not an embedded server. Request bodies and authentication machinery were not extracted or exercised.

Ghidra 12.2 successfully imported and analyzed the small ARM64 `libandroidx.graphics.path.so` (9,952 bytes), whose dynamic symbol inventory contains a JNI entry point. This was a scoped native frontend-library analysis. The much larger Orbit core received ELF/dependency/symbol inventory only; this report does not claim a complete Ghidra reverse engineering of native playback.

## 4. Backend boundary

The APK contains **client-side callers, models and native libraries**. It does not contain Spotify's production server source, databases, catalogue audio, licensing system or deployment environment. A frontend recreation cannot reconstruct those systems from the APK alone.

Observed URL hosts in DEX strings include account, application-service, public-link, support and image/CDN domains, such as `accounts.spotify.com`, `spclient.wg.spotify.com`, `open.spotify.com` and `i.scdn.co`. The full host-only inventory is in the JSON report. No URL paths, tokens or credential values are published. A static string does not establish that a host is active, public, currently called, or usable without authorization.

The supported integration boundary is described by Spotify's own documentation:

- [Spotify Android SDK](https://developer.spotify.com/documentation/android): App Remote controls the Spotify app after authorization; playback, caching and network handling remain with the Spotify app.
- [Android authorization](https://developer.spotify.com/documentation/android/tutorials/authorization): user permission is part of the supported connection flow.
- [Spotify Web API](https://developer.spotify.com/documentation/web-api): exposes metadata and playback control with API authorization; it is not an anonymous full-catalogue audio-file endpoint.

### Verified integration surface

A repeat inventory of the unchanged reference produced an identical
`analysis-metadata.json`: both APK hashes, signature results, the 95,351-class
count, manifest counts, host inventory and native-library evidence match the
recorded report. This confirms reproducibility of the static findings; it does
not add runtime or server-side evidence.

The decoded original manifest declares
`com.spotify.interapp.service.service.AppProtocolRemoteService` as an exported
service, with the `START_APP_PROTOCOL_SERVICE` and `BIND_PROTOCOL_SERVICE` actions
in Spotify's app-protocol namespace. This is consistent with the supported App
Remote integration documented above. An exported service is an Android entry
point, **not** evidence that callers can skip authorization. Its remote commands,
session state and catalogue access remain subject to the official client's
checks.

The same manifest marks `OffliningService` and the primary `LoginActivity` as
non-exported. The calling-code picker is part of a phone signup flow; its existence
does not establish where regional playback decisions are made. Replacing those
screens can change the frontend experience, but cannot change the service's
account, availability or offline-entitlement rules.

For a new frontend that retains the supported Spotify playback path, the concrete
boundary is the official App Remote SDK: the new app renders its own controls and
metadata while the official Spotify app handles its backend connection and audio.
The original player's local/HTTPS library remains a separate source and opens
without a Google account, Spotify account, or country selection. A registered
developer client ID and Spotify user authorization are still required for the
optional Spotify connection. No server implementation or production credentials
can be reconstructed from this manifest evidence.

Consequently, the requested combination of **the same Spotify backend + no account + every country + unlimited catalogue downloads** is not delivered by this analysis and is not an available property of a new UI. The existing service's authorization, supported devices, availability, content rights and offline behavior still apply. No method for bypassing those restrictions was investigated or added.

An original player can start without its own signup and use local audio or network sources that explicitly permit playback and downloads. A Spotify-facing option can use its supported authorized integration or open content in Spotify. Those are distinct capabilities and should be labeled distinctly in the product and tests.

## 5. Reproduction and tool outcomes

From the repository root, with the installed Android SDK tools and Python:

```sh
python scripts/inspect_reference_apk.py

apktool d -f --no-src --frame-path analysis/apktool-framework \
  --output analysis/apktool-original analysis/embedded-original.apk

JAVA_OPTS='-Xmx1800m' jadx -j 2 --no-res \
  --single-class com.spotify.music.SpotifyMainActivity \
  --single-class-output analysis/SpotifyMainActivity.java \
  analysis/embedded-original.apk

JAVA_OPTS='-Xmx1800m' jadx -j 1 --no-res --no-inline-anonymous --no-inline-methods \
  --single-class com.spotify.playbacknative.AudioDriver \
  --single-class-output analysis/AudioDriver.java \
  analysis/embedded-original.apk

JAVA_OPTS='-Xmx1800m' jadx -j 1 --no-res --no-inline-anonymous --no-inline-methods \
  --single-class com.spotify.cosmos.cosmosimpl.NativeRouter \
  --single-class-output analysis/NativeRouter.java \
  analysis/embedded-original.apk
```

JADX may need permission to write its user configuration/cache. Apktool's framework cache is deliberately redirected into the ignored analysis directory. These commands parse data; they do not install or launch the reference application.

| Tool | Observed outcome |
| --- | --- |
| `apksigner` | Verified uploaded wrapper and embedded original separately; recorded distinct certificate identities and signature schemes. |
| `aapt2` / `apkanalyzer` | Identified package/version/SDK declarations and decoded the manifest. |
| Python static inventory | Reproducible hashes, bounded original extraction, CRC check, archive/DEX/component/host/native inventories; successful rerun. |
| Apktool 3.0.3 | Original manifest and resources decoded successfully with `--no-src`; a full smali source extraction was not requested. |
| JADX 1.5.6 | AudioDriver and NativeRouter selected-class extractions succeeded; main activity extraction is partial because of the reported dependent-method error. |
| `readelf` | Orbit ELF architecture, dynamic imports and JNI entry point inventoried. |
| Ghidra 12.2 | Graphics path native library import and automatic analysis succeeded; native playback core not fully decompiled. |
| Frida / ADB | Dynamic reference-client instrumentation not performed; no local emulator was installed. Emulator testing of the original or rebuilt app must be reported separately with actual run evidence. |
| Uber APK Signer / Android signing tools | Original evidence was not modified or re-signed. Signing a new build does not grant access to the original service. |

## Remaining unknowns

The patch module's effects, full private-service request schemas, actual original-client network behavior, complete native playback implementation, region-by-region content availability and original-client runtime health have not been verified. Static resources can identify UI states, but do not establish the appearance or behavior of every screen at runtime. GitHub Actions tests of the rebuild can validate its own playback and UI; they cannot establish unrestricted Spotify catalogue access.

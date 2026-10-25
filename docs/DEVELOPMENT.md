# Development record

## 2026-10-25 — investigation and rebuild started

The supplied reference is `com.spotify.music`, version `9.1.78.2215` (145505425).
Its archive contains an LSPatch loader, an embedded original APK, and a patch module.
The reference is retained unchanged. Extracted code, executable patch modules, keys,
and reverse-engineering working files are not part of the new application's source.

The rebuild will be original Kotlin/Compose code, with a distinct package name and
artwork. The first working release will provide an animated, accessible interface,
real audio playback, search, a queue, local imports, and offline storage for permitted
audio. It will open directly without account creation or a country setup wizard.

Spotify's server software and catalogue are not contained in its Android APK.
Its supported Android integration requires authorization and delegates playback and
caching to Spotify. A redesigned client cannot promise anonymous access to Spotify's
whole catalogue, unrestricted regional availability, or unrestricted downloads.
The app will represent those capabilities honestly and use supported integrations.

## Verification plan

- Inspect the supplied archive using the installed Android and reverse-engineering tools.
- Record reproducible evidence and the client/server boundary.
- Build the original player with pinned dependencies and a Gradle wrapper.
- Test playback, downloads, search, navigation, and persistence on GitHub Actions Android emulators.
- Publish debug APKs, test results, and screenshots as workflow artifacts.
- Commit and push each complete development increment directly to `main`.

## Sources

- [Spotify Android SDK](https://developer.spotify.com/documentation/android)
- [Spotify Web API](https://developer.spotify.com/documentation/web-api)

The original APK has not been launched or trusted as executable application code.

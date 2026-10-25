# Spotify connection in the redesigned player

The application opens immediately without signup, a Google account, or country
selection. Its original samples, imported files and permitted HTTPS audio work
without Spotify. An optional **Spotify connection** screen uses Spotify's
published App Remote SDK to control the official Spotify app and display its
actual current track, artwork, playback position and player state.

## What this preserves

The official Spotify app still connects to Spotify's existing backend. It owns
the account session, audio transport, content availability, audio focus,
notifications and offline storage. The new frontend sends supported App Remote
commands over the documented application interface. It does not replace or
reconstruct Spotify's production servers.

Implemented commands are play a supported Spotify content link, play/pause,
previous/next, seek, shuffle and repeat. Controls follow capabilities and
restrictions reported by Spotify. A single-track request is rejected until
Spotify reports that the user can play on demand. A missing app, missing build
configuration, denied authorization or connection failure produces an explicit
state; there is no fabricated successful connection or sample-track fallback.

Spotify catalogue search continues to open the official service. The local
search screen searches the collection on this device. The App Remote panel does
not claim to expose the full Spotify queue, catalogue, recommendations or an
independent Spotify download manager.

## Configure your registered Spotify application

1. Register an Android application in the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Register package **`io.github.musicplayer`** and an HTTPS redirect URI you
   control. Use the exact same redirect value below.
3. Add repository [**Actions variables**](https://github.com/cheshmakzanoon-ops/music-player/settings/variables/actions) named `SPOTIFY_CLIENT_ID` and
   `SPOTIFY_REDIRECT_URI`. These are public application identifiers, not secrets.
   The workflow already passes them to Gradle. **Do not provide or embed a client
   secret.**
4. Run [Android validation](https://github.com/cheshmakzanoon-ops/music-player/actions/workflows/android.yml)
   with those variables, then download its `validation-apks` artifact. Extract
   `debug/app-debug.apk`, inspect that APK's SHA-1 with the command below, and
   register that exact fingerprint in the Spotify dashboard.
5. Install **that same APK** alongside the official Spotify app, open **Settings →
   Spotify connection**, and select **Connect with Spotify**. Spotify presents its
   own authorization flow. An existing Spotify session may be reused; the player
   does not implement a Google signup flow.

Debug and release signing identities differ. A new CI run can generate a different
debug identity, so rebuilding after fingerprint registration can require registering
the new APK's fingerprint. For repeatable distribution, use your own persistent
signing identity and register its certificate; keep its private key outside the
repository.

For local builds, the equivalent Gradle properties are:

```sh
./gradlew assembleDebug \
  -PspotifyClientId=YOUR_PUBLIC_CLIENT_ID \
  -PspotifyRedirectUri=https://YOUR_DOMAIN/spotify/callback
```

Inspect the certificate fingerprint of the actual APK before registering it:

```sh
apksigner verify --print-certs path/to/the/actual/app-debug.apk
```

The build intentionally stays usable if these identifiers are absent. The
connection screen explains that Spotify is not configured; local listening is
unaffected. No production identifiers were supplied for this implementation.

## Lifecycle and data handling

- The first connection starts only after an explicit user action. Opening the app or
  connection screen does not start authorization.
- The SDK binding and subscriptions are released when the activity moves to the
  background. Spotify remains responsible for any continuing Spotify audio.
- Foreground reconnection uses the already authorized session without
  automatically showing a new consent prompt. Explicit disconnection disables
  automatic reconnection.
- An in-progress native authorization handoff is allowed to finish, with stale
  callbacks discarded and any background connection immediately released.
- While connected, local playback pauses Spotify through the supported API; explicit Spotify
  playback pauses the local player. No private service endpoints are called.
- Spotify track art comes from the supported Images API and is displayed intact.
  It is not bundled as original artwork or used in the local audio catalogue.
- The app does not store Spotify passwords, OAuth tokens, or a client secret.
- Content input accepts recognized Spotify URI forms and HTTPS links on exactly
  `open.spotify.com`; unrelated URLs, userinfo and alternate ports are rejected.

## Verification scope

JVM tests cover application configuration and content-link validation. Android
emulator tests cover account-free launch, the optional connection screen, its
unavailable-service state, returning to the local library, and the local player's
real audio/download journeys. Those tests do **not** prove an authenticated
Spotify session or catalogue playback.

Authenticated end-to-end verification requires your registered app identifiers,
the matching signing fingerprint, the official Spotify app and a user who
authorizes the connection. It must be checked on that real integration before
claiming Spotify playback is verified. Region and subscription availability
continue to be determined by Spotify.

## Primary references

- [Spotify Android SDK overview](https://developer.spotify.com/documentation/android)
- [Getting started: app registration and authorization](https://developer.spotify.com/documentation/android/tutorials/getting-started)
- [Application lifecycle](https://developer.spotify.com/documentation/android/tutorials/application-lifecycle)
- [App Remote source repository and supported capabilities](https://github.com/spotify/android-sdk/tree/master/app-remote-lib)
- [Player API reference](https://spotify.github.io/android-sdk/app-remote-lib/docs/com/spotify/android/appremote/api/PlayerApi.html)
- [Redirect URI requirements](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)

The pinned SDK binary and its upstream license/notice are documented in
[`app/libs/README.md`](../app/libs/README.md).

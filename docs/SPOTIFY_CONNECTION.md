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

The Search screen has an optional **actual Spotify catalogue** section. After a
user authorizes Spotify's Web API, searches return track, album and artist
metadata from `GET /v1/search`, with Spotify-hosted artwork and direct Spotify
links. A result plays through App Remote when that is connected; otherwise it
opens in Spotify. Local searching and guest playback remain independent.
The player does not fetch Spotify audio, bypass licensing, or implement an
independent Spotify download manager.

## Configure Web API catalogue search

1. Create an app at the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
   Development Mode requires the developer to have active Spotify Premium and
   allows **at most five allowlisted users**. See the [current quota rules](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).
2. Add **`http://127.0.0.1/callback`** (no port) to the Spotify app's Redirect
   URIs. Spotify explicitly permits changing the port on a loopback IP literal
   for each authorization. NIGHT SHIFT starts a temporary listener on a randomly
   assigned local port and supplies that complete URL to Spotify.
3. Install NIGHT SHIFT v0.4.0+ and open **Search → Spotify catalogue**. Enter
   your app's **public 32-character Client ID** and choose **Save Client ID**.
   The value is stored in this device's private app preferences. You can change
   or remove it later, without compiling another APK. **Never paste a client
   secret.** The public `SPOTIFY_CLIENT_ID` [Actions variable](https://github.com/cheshmakzanoon-ops/music-player/settings/variables/actions)
   remains an optional build-time default for managed distributions.
4. Choose **Connect Spotify catalogue**, approve Spotify's browser-based
   authorization, and return to NIGHT SHIFT. Search tracks, albums and artists.
   Catalogue search needs neither an external callback server nor the Spotify
   Android app. App Remote playback controls have separate requirements below.
5. Access and refresh tokens are encrypted with an Android Keystore AES-GCM key
   in private app storage. **Disconnect catalogue** deletes those credentials.
   No app-specific account, Google signup, client secret or Spotify password is
   required by NIGHT SHIFT.

Changing the public Client ID immediately cancels any pending authorization or
search, disconnects the previous catalogue session and deletes its encrypted
tokens. Stored tokens are cryptographically bound to the exact Client ID of
their issuing app; an existing v0.3.0 session requires **one fresh Spotify
authorization after upgrading**. Changing a catalogue Client ID does **not**
reconfigure App Remote: that integration still needs its own build-time Android
SDK redirect URI and registered signing fingerprint.

The browser flow uses Authorization Code + PKCE (S256), a fresh random verifier
and state, exact redirect/state validation, a two-minute loopback deadline and
one-time code exchange. Searches are debounced and use Spotify's 2026 maximum
of 10 items per type, with explicit 401/403/429 handling. The loopback listener
receives only the OAuth redirect; metadata comes directly from Spotify's
`https://api.spotify.com` over HTTPS with the user's authorized token.

## Configure App Remote playback controls

1. Use the registered application in the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Register package **`io.github.musicplayer`** and an HTTPS redirect URI you
   control. Use the exact same redirect value below.
3. Add repository [**Actions variables**](https://github.com/cheshmakzanoon-ops/music-player/settings/variables/actions) named `SPOTIFY_CLIENT_ID` and
   `SPOTIFY_REDIRECT_URI`. These are public application identifiers, not secrets;
   this separate HTTPS redirect configures the App Remote SDK.
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
- The app does not store Spotify passwords or a client secret. It **does** store
  catalogue OAuth access/refresh tokens, encrypted with Android Keystore, when
  the user authorizes catalogue access. App Remote continues to delegate its
  account/session to the official Spotify app.
- Content input accepts recognized Spotify URI forms and HTTPS links on exactly
  `open.spotify.com`; unrelated URLs, userinfo and alternate ports are rejected.

## Verification scope

JVM tests cover PKCE state/redirects, an actual loopback listener, mocked
Spotify Web API search/token exchange, HTTP errors, configuration and link
validation. Android emulator tests additionally cover runtime Client ID
entry, persistence, removal, and encrypted-token isolation between Client IDs,
as well as catalogue-result rendering and exact Spotify URI selection,
account-free launch, the optional connection screen, its
unavailable-service state, returning to the local library, and the local player's
real audio/download journeys. Those tests do **not** prove an authenticated
live Spotify session, live catalogue search or catalogue playback.

Authenticated end-to-end verification requires your registered app identifiers,
the matching signing fingerprint, the official Spotify app and a user who
authorizes the connection. It must be checked on that real integration before
claiming Spotify playback is verified. Region and subscription availability
continue to be determined by Spotify.

## Primary references

- [Web API search](https://developer.spotify.com/documentation/web-api/reference/search)
- [Authorization Code with PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow)
- [Development-mode quota limits](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)
- [Spotify Android SDK overview](https://developer.spotify.com/documentation/android)
- [Getting started: app registration and authorization](https://developer.spotify.com/documentation/android/tutorials/getting-started)
- [Application lifecycle](https://developer.spotify.com/documentation/android/tutorials/application-lifecycle)
- [App Remote source repository and supported capabilities](https://github.com/spotify/android-sdk/tree/master/app-remote-lib)
- [Player API reference](https://spotify.github.io/android-sdk/app-remote-lib/docs/com/spotify/android/appremote/api/PlayerApi.html)
- [Redirect URI requirements](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)

The pinned SDK binary and its upstream license/notice are documented in
[`app/libs/README.md`](../app/libs/README.md).

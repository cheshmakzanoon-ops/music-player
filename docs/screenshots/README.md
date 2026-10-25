# Verified emulator captures

These PNG files are unchanged copies from [successful GitHub Actions run
40184075722](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722),
source revision `b7b2d3f53eb85f0106d79ae0ad79427144634eb8`. They show the application
running on the workflow's Pixel 2 x86-64 Android emulators.

| Capture | Device | Original artifact file |
| --- | --- | --- |
| [Discover, full header](discover-api26.png) | Android 8 / API 26 | `01-discover.png` |
| [Search](search-api35.png) | Android 15 / API 35 | `02-search.png` |
| [Queue](queue-api35.png) | Android 15 / API 35 | `05-queue.png` |
| [Discover, scrolled](discover-api35.png) | Android 15 / API 35 | `01-discover.png` |
| [Now Playing](now-playing-api35.png) | Android 15 / API 35 | `04-now-playing.png` |
| [Spotify connection](spotify-connection-api35.png) | Android 15 / API 35 | `10-spotify-connection.png` |
| [Source dialog at 1.5× font scale](source-large-text-api35.png) | Android 15 / API 35 | `09-source-large-text.png` |
| [Settings at 1.5× font scale](settings-large-text-api26.png) | Android 8 / API 26 | `08-settings-large-text.png` |

All eight selected images were visually reviewed for the intended screen, readable
text and reachable controls. The Spotify image shows the real unconfigured state
of this build; it is not evidence of an authenticated connection.

## Capture limitation

The raw API 35 `08-settings-large-text.png` retained the earlier home-screen frame
after Activity recreation. It is excluded from this gallery. The settings test's
semantic assertions passed, and the API 26 capture correctly shows the large-text
Settings dialog. The other selected API 35 images show their intended screens.

The complete raw evidence remains in the run's
[API 26 artifact](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12594784964)
and [API 35 artifact](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40184075722/artifacts/12595950587).

## Spotify catalogue extension screenshots

These two additional unmodified PNGs were copied from the successful
[Android 15 emulator artifact of run 40187682490](https://github.com/cheshmakzanoon-ops/music-player/actions/runs/40187682490/artifacts/12597471131),
application revision `0968861d62cc5969ece14ffd555fd51282af58e6`:

| File | Raw artifact source | SHA-256 |
| --- | --- | --- |
| [Spotify catalogue Search](spotify-catalogue-search-api35.png) | `02-search.png` | `fcb18561baf010e8ad0e70ff3e804fdab4bcec75be7ced72e26b3e50fe67a33f` |
| [Spotify catalogue connection](spotify-catalogue-connection-api35.png) | `10-spotify-connection.png` | `190d79a7636de289d0aff3c8ee4c50147105af4afbd7189d96b315f0b6b9cd52` |

Both were visually checked for correct, readable controls. Neither screenshot
claims live authentication: the corresponding APK intentionally has no public
Spotify developer client ID configured. The populated result-row rendering is
covered separately by a deterministic emulator test with fixed fixture data.

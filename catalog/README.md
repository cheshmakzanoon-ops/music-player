# Studio samples

This catalogue contains six original, procedurally composed 24-second music samples.
They make the new player's streaming and offline features usable immediately without
an account. They are **not Spotify recordings or a mirror of Spotify's catalogue**.

`scripts/generate_studio_samples.py` produces the recordings from synthesized waveforms
and deterministic noise; it uses no external recordings. The generated recordings
are dedicated to the public domain under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
The project does not claim rights to any material in the separate reference APK.

`catalog.json` supplies HTTPS playback URLs, download permissions, SHA-256 checksums,
and byte counts. The app includes First light locally so that first launch works
without a network connection. The other samples stream from this repository and
can be explicitly saved for offline use. Hosting availability is not guaranteed.

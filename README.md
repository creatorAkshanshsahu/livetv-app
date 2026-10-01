
# Live TV Box

A lightweight Android 9+ TV app for the LiveTgTV V1 public API.

Features:
- Loads the channel catalogue once; no need to search every channel.
- Optional channel search.
- D-pad/remote-friendly grid.
- Full-screen native Media3/ExoPlayer playback.
- V1 MPEG-DASH playback.
- Universal ABI filters: arm64-v8a, armeabi-v7a, x86, x86_64.
- No WebView and no LiveTgTV website dependency.

## Build without Android Studio

Push this project to GitHub. The included GitHub Actions workflow builds a debug APK.

GitHub:
1. Create a new public repository.
2. Upload all files/folders from this project.
3. Commit to `main`.
4. Open the **Actions** tab.
5. Select **Build Live TV Box APK**.
6. Run workflow if it did not run automatically.
7. Open the completed workflow run.
8. Download the artifact `LiveTVBox-universal-debug`.
9. Extract it and install `app-debug.apk` on the Android box.

## API note

The app assumes V1 accepts:
GET /api/public/channels
GET /api/public/channels/{id}

It parses common catalogue field names (`channels`, `results`, `data`, `items`) and common V1 stream fields, including `manifest`/`mpd`.

If the live API uses a different catalogue response shape, the parser can be adjusted without changing the TV UI.

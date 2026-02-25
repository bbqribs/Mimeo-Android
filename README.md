# Mimeo-Android

Mimeo-Android is a minimal Android MVP for read-it-later playback: queue -> segmented play -> progress -> done.

## Endpoints used
- `GET /debug/version` (settings connectivity check)
- `GET /playback/queue` (playlist candidates)
- `GET /items/{id}/text` (TTS text payload)
- `POST /items/{id}/progress` (progress and done sync)
- Progress sync is segment-based (`segment_index / total_segments`) and posted on segment changes.

## Open in Android Studio
1. Open this repo folder in Android Studio.
2. Let Gradle sync.
3. Build/run the `app` module on emulator or device.

## Base URL notes
- Android emulator -> host machine backend: `http://10.0.2.2:8000`
- Physical device -> use host LAN IP (for example `http://192.168.x.y:8000`)
- `127.0.0.1` on Android points to the device itself, not your host backend.

## MVP smoke steps
1. Start backend (`Mimeo` repo) and ensure `/debug/version` is reachable.
2. In app Settings, set base URL + API token.
3. Tap **Test connection** and confirm git SHA appears.
4. Open Queue and refresh.
5. Tap an item -> Player -> verify `Segment X / Y` appears.
6. Use Play, Prev Seg, Next Seg.
7. Tap Next Item to advance queue playback.
8. Tap Done to send progress 100.

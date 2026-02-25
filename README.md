# Mimeo-Android

Mimeo-Android is a minimal Android MVP for read-it-later playback: queue -> segmented play -> progress -> done.
v0.2 adds offline resilience: local text cache + queued progress retry.
v0.2.1 adds WorkManager auto-flush, so queued progress sync runs automatically when network returns.
v0.3 adds a persisted "Now Playing" queue snapshot so playback order stays stable mid-session and resume works cleanly.

## Endpoints used
- `GET /debug/version` (settings connectivity check)
- `GET /playback/queue` (playlist candidates)
- `GET /items/{id}/text` (TTS text payload)
- `POST /items/{id}/progress` (progress and done sync)
- Progress sync is segment-based (`segment_index / total_segments`) and posted on segment changes.
- Queue prefetch caches text for the next items (default first 5).
- If `/items/{id}/text` fails, player falls back to cached text when the active content version matches.
- Offline/timeout progress posts are queued locally and retried on app start, queue open, or manual sync.
- WorkManager schedules unique network-constrained `progress-sync` work on app start and when progress is queued.
- Tapping a queue item creates a persisted "Now Playing" session snapshot (fixed item order + current index).
- Next/Prev Item navigation uses the session snapshot instead of a freshly refetched queue.
- Queue shows a "Resume Now Playing" action after restart when a session exists.

## Open in Android Studio
1. Open this repo folder in Android Studio.
2. Let Gradle sync.
3. Build/run the `app` module on emulator or device.

## Base URL notes
- Android emulator -> host machine backend: `http://10.0.2.2:8000`
- Physical device -> use host LAN IP (for example `http://192.168.x.y:8000`)
- `127.0.0.1` on Android points to the device itself, not your host backend.
- If your PC IP is `192.168.68.124`, set app base URL to `http://192.168.68.124:8000`.

## Device connectivity quick fix (Windows + physical phone)
1. Start backend on LAN bind (not localhost-only):
   ```powershell
   cd C:\Users\brend\documents\Coding\Mimeo\backend
   .\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```
2. Allow inbound TCP 8000 on Private profile (run PowerShell as Administrator):
   ```powershell
   New-NetFirewallRule -DisplayName "Mimeo API 8000 (Private)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8000 -Profile Private
   ```
3. In Android app Settings:
   - Base URL: `http://<your-pc-ip>:8000`
   - API token: your `API_TOKEN` (or a per-device token)
4. Tap **Test connection**.

## Why cleartext setting was needed
- Android blocks plain HTTP by default on many builds.
- This app currently talks to local dev HTTP endpoints, so manifest now sets:
  - `android:usesCleartextTraffic="true"`
- Long term, prefer HTTPS for non-local hosting.

## MVP smoke steps
1. Start backend (`Mimeo` repo) and ensure `/debug/version` is reachable.
2. In app Settings, set base URL + API token.
3. Tap **Test connection** and confirm git SHA appears.
4. Open Queue and refresh.
5. Tap an item -> Player -> verify `Segment X / Y` appears.
6. Use Play, Prev Seg, Next Seg.
7. Tap Next Item / Prev Item to navigate session order.
8. Tap Done to send progress 100.
9. Offline smoke:
   - disable network, open a previously cached item, confirm "Using cached text".
   - interact with playback and confirm pending sync count increases.
   - re-enable network; pending sync should drain automatically (tap **Sync** only as optional manual trigger).

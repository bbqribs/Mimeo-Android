# Operations (Android)

## Backend prerequisite
Use the main Mimeo backend repo and start services first:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ensure-mimeo.ps1
```

## Run Android MVP
1. Open `Mimeo-Android` in Android Studio.
2. Run `app` on emulator/device.
3. In Settings:
   - Base URL: `http://10.0.2.2:8000` (emulator)
   - API token: value from `Mimeo/backend/.env` (`API_TOKEN`)
4. Tap **Test connection** (expects git SHA).
5. Open Queue, select item, Play, Done.
6. Queue screen shows `Pending sync: X`; tap **Sync** to flush queued progress updates.

## Troubleshooting
- `Unauthorized-check token`: token invalid/expired.
- Empty queue: backend has no ready unarchived not-done items.
- Network failure on emulator: verify base URL is `10.0.2.2`, not `127.0.0.1`.
- Progress sync is best-effort; failures are queued locally and retried when network returns.
- Player may show `Using cached text` if `/items/{id}/text` is unavailable and cached content is valid.

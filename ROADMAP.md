# Roadmap (Android)

## Done
- [x] MVP v0 app scaffold with Compose + DataStore + OkHttp.
- [x] Settings screen with base URL/token and `/debug/version` test.
- [x] Queue screen using `/playback/queue`.
- [x] Player screen using `/items/{id}/text` with TTS play/pause/next.
- [x] Progress sync via `/items/{id}/progress` (periodic while speaking + done=100).

## Next
1. [ ] Improve progress estimation using richer TTS callbacks across engines.
2. [ ] Add queue filters/sorting controls from playback API query params.
3. [ ] Add robust lifecycle state restore for active playback item.

## Later
- [ ] Offline cache for text payloads.
- [ ] Named playlists and queue management.
- [ ] Audio focus/media session polish.

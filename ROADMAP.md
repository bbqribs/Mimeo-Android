# Roadmap (Android)

## Done
- [x] MVP v0 app scaffold with Compose + DataStore + OkHttp.
- [x] Settings screen with base URL/token and `/debug/version` test.
- [x] Queue screen using `/playback/queue`.
- [x] Player screen using `/items/{id}/text` with TTS play/pause/next.
- [x] Progress sync via `/items/{id}/progress` (periodic while speaking + done=100).
- [x] Segment-based playback using `paragraphs[]` (with text fallback), prev/next segment controls, and segment-index progress sync.
- [x] Offline caching + retry queue: cached text/paragraphs (Room), offline player fallback, queued progress sync with retry.
- [x] WorkManager auto-flush for queued progress when network returns (no manual sync required).
- [x] v0.3 now-playing session snapshot: persisted queue order + current index, queue resume action, and next/prev item navigation tied to session state.

## Next
1. [ ] Persist last segment index per item in DataStore for cross-process resume.
2. [ ] Add explicit session actions (restart from top, clear session, optional repeat mode).
3. [ ] Add queue filters/sorting controls from playback API query params.

## Later
- [ ] Named playlists and queue management.
- [ ] Audio focus/media session polish.
- [ ] Better conflict handling for stale cached versions during long offline sessions.

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
- [x] In-app connectivity diagnostics screen: `/health`, `/debug/version`, `/debug/python` checks with device-aware hints and timestamps.
- [x] Progress model v1: canonical chunk/char-offset percent wiring, monotonic server updates, and near-end auto-done alignment.
- [x] Android MVP playback end-to-end polish: Now Playing status header, offline/cache badges, retry+diagnostics recovery actions, and configurable completion auto-advance.
- [x] Text segmentation/chunking improvements: backend now emits more natural paragraph/sentence chunk boundaries with deterministic sizing, and Android percent math remains monotonic across varied chunk lengths.
- [x] Start listening here + highlight + optional auto-scroll: player `View text` supports long-press start-from-chunk, current chunk highlighting, and auto-scroll toggle with temporary manual-scroll suppression.
- [x] Now Playing/session UX refinements + playlists entry point stub: queue session banner with resume/restart/clear controls, player session header/back-to-queue action, and a top-level playlists placeholder screen.

## Next
1. [ ] Named playlists v1 integration (select playlist and load playlist-scoped queue).
2. [ ] Hosting story v2 UX: HTTPS-first guidance, per-device token setup polish, safer LAN-mode flow.
3. [ ] Persist last segment index per item in DataStore for cross-process resume.
4. [ ] Add queue filters/sorting controls from playback API query params.

## Later
- [ ] Named playlists and queue management.
- [ ] Audio focus/media session polish.
- [ ] Better conflict handling for stale cached versions during long offline sessions.
- [ ] Replace dev cleartext dependence with HTTPS-friendly transport story for hosted/mobile use.

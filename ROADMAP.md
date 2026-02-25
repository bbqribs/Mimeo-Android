# Roadmap (Android)

## Done
- [x] MVP v0 app scaffold with Compose + DataStore + OkHttp.
- [x] Settings screen with base URL/token and `/debug/version` test.
- [x] Queue screen using `/playback/queue`.
- [x] Player screen using `/items/{id}/text` with TTS play/pause/next.
- [x] Progress sync via `/items/{id}/progress` (periodic while speaking + done=100).
- [x] Segment-based playback using `paragraphs[]` (with text fallback), prev/next segment controls, and segment-index progress sync.

## Next
1. [ ] Persist last segment index per item in DataStore for cross-process resume.
2. [ ] Add queue filters/sorting controls from playback API query params.
3. [ ] Improve progress estimation across non-standard TTS engines.

## Later
- [ ] Offline cache for text payloads.
- [ ] Named playlists and queue management.
- [ ] Audio focus/media session polish.

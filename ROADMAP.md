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
- [x] Named playlists v1 integration: playlist CRUD + selection UI, Smart queue fallback, and playlist-scoped queue loading.
- [x] Playlist item membership UX: Queue row/player overflow now expose `Add to playlist…` with per-playlist add/remove toggles.
- [x] UI compression pass: compact nav, dense queue/player/playlists/settings layouts, and collapsible status banner for offline/LAN issues.
- [x] UX follow-ups: root snackbar visibility, stable player status/banner placement, and denser player control bar without reducing tap targets.

## Android Redesign v1.1
1. [~] Foundation: 4-tab nav shell (Up Next / Locus / Collections / Settings) plus black/purple theme foundations.
2. [x] Mini control panel (collapsed Locus peek) for active playback/session continuity.
3. [x] Up Next skeleton: playlist dropdown, search affordance, filter chips, and grouped queue sections.
4. [x] Typography preferences pass: reading fonts, line height, and display density controls.
5. [x] Playback speed control in Locus with persisted preference.
6. [~] Collections baseline: special collections + named playlist browser under the redesign shell.
7. [ ] Phase 6.2 follow-up: nested folders + moving items between folders.

## Later
- [ ] Hosting story v2 UX: HTTPS-first guidance, per-device token setup polish, safer LAN-mode flow.
- [ ] Persist last segment index per item in DataStore for cross-process resume.
- [ ] Add queue filters/sorting controls from playback API query params.
- [ ] Named playlists and queue management.
- [ ] Audio focus/media session polish.
- [ ] Better conflict handling for stale cached versions during long offline sessions.
- [ ] Replace dev cleartext dependence with HTTPS-friendly transport story for hosted/mobile use.

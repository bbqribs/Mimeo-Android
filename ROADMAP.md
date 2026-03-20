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
- [x] Player chrome reliability follow-up: non-Locus player controls render as a bottom overlay without tab hit-test conflicts; settings toggles persist correctly; playback remains alive across tab switches even when persistent controls are hidden.
- [x] Up Next controls cleanup: single Refresh action remains and covers queue reload plus queued-progress sync path; standalone Sync button removed.
- [x] Reader chrome/fullscreen interaction pass: tapping reader text toggles chrome visibility with directional slide transitions while preserving visible text position.
- [x] Player controls minimization (D-lite): Full/Minimal/Nub modes with persisted mode+side, stable chevron placement, and mode-specific progress lanes (full slider, minimal thin top line, nub thin line at nav boundary).
- [x] Refresh affordance polish: unified Material refresh action states (idle/spin/success/failure + sync-problem warning) across Queue, Playlists, and Locus with reduced error clutter.
- [x] Locus speed control polish: compact icon-led trigger + preset capsules + custom slider + stepper row, with tuned icon/typography sizing and consistent narrow-screen layout.
- [x] Shared chrome polish: persistent title strip typography/link behavior finalized (bold title + italic tappable domain), compact top action bar density, and centered title-strip divider treatment for clearer separation.

## Priority 0
- [x] Android share-sheet saving before redesign: `ACTION_SEND` URL capture via invisible share receiver, `POST /items` with idempotency key, default-save playlist routing, Collections discovery guidance, and share-result notifications without foregrounding the app.

### Share-sheet saving (P0)
- [x] Share target implemented via `ACTION_SEND` and `ShareReceiverActivity`.
- [x] System notifications for success/error with Settings action for token issues.
- [x] Optional persistent notification mode in Settings; default behavior auto-dismisses after about 4 seconds.
- [x] Default save playlist routing.
- [x] Collections discovery link and instructions dialog.
- [x] Queue verification controls restored (search + filters).
- [x] Queue search robustness fix (raw substring + normalized fallback).
- [x] Queue debug instrumentation is hidden behind an explicit overflow toggle (debug builds).
- [x] Share-save can auto-download newly saved items for offline readiness via Settings toggle.
- [x] Active Up Next context refresh now carries focus metadata so newly shared items can appear immediately in the current playlist view.
- [x] Share-save success-message semantics: destination-aware `Saved to Smart Queue ✅` / `Saved to <Playlist> ✅` without duplicate-specific success wording.
- [x] Up Next top action bar supports manual URL save entry via lightweight `+` dialog, routed through existing save semantics.
- [x] Up Next `+` flow now supports manual text submission v1 (`Save URL` / `Paste Text`) via existing `/items/manual-text` backend path.
- [ ] Backlog: allow sharing plain text and saving it as a readable item, not just URLs.

### User sign-in (Phase 3)
- [ ] Username/password sign-in flow — spec ready: `docs/ANDROID_AUTH_PHASE3_SPEC.md`

### Next tickets
- [ ] Share-save destination trust polish: small post-save confirmation surface cleanup if any ambiguity remains after semantics cleanup.
- [ ] Manual-save failed-attempt recovery queue: keep failed manual saves in a clearable retry queue, preserve user-entered work across offline/interrupted conditions, and auto-retry when connectivity returns.
- [ ] Up Next pull-to-refresh affordance: add pull-down gesture parity with the refresh button, including visible content pull-from-top and spinner/refresh indicator while loading.
- [ ] Queue debug cleanup finalization: decide whether to fully remove the debug-only fetch overlay after investigation is complete.
- [ ] Share plain text capture: convert non-URL shared text into a readable item flow or explicitly reject it with a clearer product decision.

## Android Redesign v1.1
1. [~] Foundation: 4-tab nav shell (Up Next / Locus / Collections / Settings) plus black/purple theme foundations.
2. [x] Mini control panel (collapsed Locus peek) for active playback/session continuity.
3. [x] Up Next skeleton: playlist dropdown, search affordance, filter chips, and grouped queue sections.
4. [x] Typography preferences pass: reading fonts, line height, and display density controls.
5. [x] Playback speed control in Locus with persisted preference.
6. [x] Playback speed location decision: keep speed in expanded Locus header, not the pinned PlayerBar.
7. [cancelled] Move playback speed into pinned PlayerBar. Superseded by docs/decision-playback-speed-location.md; keep speed local to the expanded Locus header and avoid expanding shared PlayerBar scope.
8. [x] Collections baseline: special collections + named playlist browser under the redesign shell.
9. [x] Phase 6.2: local playlist folders (create/rename/delete + assign playlists within Collections).
10. [~] Phase 6.3: folder detail view + counts + remove-from-folder inside Collections.
11. [ ] Next: folder badges in playlist list and optional nested folders.
12. [x] Locus expand/collapse on `main`: explicit buttons only, collapsed tab entry, expanded resume/direct entry, title ellipsis fix, and TESTING.md invariants checklist.
13. [ ] Player completion icon follow-up: evaluate open-book/closed-book completion iconography for Locus as a separate ticket after the current player banding work settles.
14. [ ] Shared pinned PlayerBar on `main` remains a separate architecture ticket, not part of playback-speed follow-up.
15. [~] Player screen banding foundation: TopAppBar + reader body + pinned controls above bottom nav are shipped; ongoing polish is focused on chrome density/behavior only.
16. [x] Reader highlight progression: sentence-level highlighting with range-level (TTS `onRangeStart`) support and sentence fallback.
17. [x] Player chrome compression slice: persistent title strip + 3-state controls with chevron mode transitions and persisted settings.
18. [x] Header action polish: refresh visual states unified and speed control updated to compact panel styling with preserved speed semantics.

## Testing debt
- [ ] NoActiveContentStore Worker→ViewModel integration test: verify that IDs written by the worker during a download run are correctly read back and merged into the ViewModel's `noActiveContentItemIds` on the next queue load. Needs ViewModel testing infrastructure (e.g. `TestCoroutineDispatcher` + fake repository) not yet established in this project.

## Later
- [ ] Hosting story v2 UX: HTTPS-first guidance, per-device token setup polish, safer LAN-mode flow.
- [ ] Persist last segment index per item in DataStore for cross-process resume.
- [ ] Add queue filters/sorting controls from playback API query params.
- [ ] Named playlists and queue management.
- [ ] Audio focus/media session polish.
- [ ] Better conflict handling for stale cached versions during long offline sessions.
- [ ] Replace dev cleartext dependence with HTTPS-friendly transport story for hosted/mobile use.



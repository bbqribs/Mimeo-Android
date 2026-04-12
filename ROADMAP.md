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
- [x] Auth/session clarity in Settings: explicit signed-in/session-source messaging, sign-out consequences, and stale-auth guidance copy.
- [x] Phase 3 auth flow complete: sign-in startup gate, username/password sign-in, stale-token recovery, and explicit sign-out path.
- [x] Token storage hardening: auth token moved off plain settings storage to secure-at-rest path with migration and fallback handling.
- [x] Endpoint/scheme guardrails: mode-aware URL validation (Local/LAN/Remote), blocking invalid base URLs and warning on common mismatches while keeping HTTP-over-Tailscale supported.
- [x] TTS voice selection + preview: user-selectable on-device voice/accent with preview phrase and engine-default fallback handling.
- [x] Title-before-body playback option: optional title intro before article text, duplicate-opening suppression, and autoplay sequencing support.
- [x] End-of-article completion cue: optional audible completion signal that does not alter queue/progress semantics.
- [x] Queue metadata/affordance polish: clearer row state indicators and developer-gated capture metadata visibility.
- [x] Playback/open observability + developer toggle: diagnostic state surface for manual-open/autoplay handoff and start-position decisions.
- [x] Player/reader handoff polish: reduced stale-content flashes and smoother queue-tap -> Locus transition.
- [x] Autodownload consistency + durability: newly surfaced item targeting, explicit-refresh re-attempt behavior, workerization with backoff, and diagnostics surface.
- [x] Up Next transition clarity follow-up: explicit failed-processing/no-active-content row states and clearer pending -> saved -> cached visibility after retry/autodownload.
- [x] Mimeo Control Phase 2 Slice 1: app-scoped `PlaybackEngine` extraction shipped; live playback ownership (TTS lifecycle, chunk callbacks, continuation decisions, progress sync triggers) no longer depends on `PlayerScreen` lifecycle.
- [x] Mimeo Control Phase 3 Slice 1 shipped: foreground playback service + media session + media notification foundation, including headset/media-button stabilization follow-up.
- [x] Mimeo Control Phase 3 Slice 2 shipped: bounded audio-focus/interruption policy pass (pause on interruption, no ducking, transient-gain auto-resume where safe) plus headset disconnect pause alignment.
- [x] Continuous play reliability fix shipped: service-owned continuation now remains reliable across screen-off, lock, and background scenarios.
- [x] Background playback observability shipped: continuation/focus/media-button audit logs for screen-off/lock/background troubleshooting.
- [x] Pending outcome simulator (dev-only) shipped: reproducible cached / unavailable-offline / processing-failed outcomes for UI verification.
- [x] Pending/offline outcome polish shipped: clearer non-cached failure presentation, reduced stale-row confusion, and tighter pending/offline state copy.
- [x] Offline/no-active-content copy + behavior cleanup shipped: unavailable-offline/no-active-content messaging and row-state behavior tightened for daily use.
- [x] Plain-text share behavior shipped: dedicated text-share save path, mixed text+URL handling, and original-source footer flow.
- [x] Structured source metadata emission shipped for Android capture flows (`source_type`, `source_label`, `source_url`, `capture_kind`, optional `source_app_package`) with backward-compatible behavior when server ignores fields.
- [x] Provenance/origin/content separation shipped for Android share handling: content links are preserved as content, provenance is inferred only for trusted browser-marked selection patterns, and URL-only vs excerpt capture routing is deterministic.
- [x] Source/title rendering shipped in Up Next + Locus + now-playing strip using metadata-first precedence (`provenance -> origin -> Android selection`) with excerpt title normalization.
- [x] Up Next orientation pass shipped: active-item indicator for current/resumable session item, normal-flow list-position restore, stale-position guardrails, and Up Next tab tap cycle (active item <-> top).
- [x] Locus next-article handoff polish shipped: auto-continue now prefers cached/local content for faster continuation and offline/unreachable fallback without waiting on network-first timeouts.
- [x] Offline action queueing (slice 1) shipped for favourite/archive flows on Locus and list surfaces: local optimistic apply while offline with bounded deferred sync on reconnect (favorite/unfavorite + archive/unarchive).
- [x] PR #170 shipped: offline queued favourite/archive sync, Locus action-target correctness while previewing, playback-owner notification title correctness, and saved-item extraction reconciliation hardening.
- [x] Locus full-text/default-open behavior shipped per `docs/ANDROID_LOCUS_FULLTEXT_DEFAULT_OPEN_SPEC.md`: default manual-open uses Full-text, explicit mode choice is remembered, autoplay preserves mode, and preview-vs-playing ownership rules remain intact.
- [x] Locus source/publication cue shipped: when title-intro is active and a meaningful source label is available, TTS now speaks a brief “From {source}.” cue between title and body; skipped for missing/unhelpful provenance and unchanged for resume/replay rules.
- [x] Archive-while-playing continuity shipped: archiving the currently playing item now marks it archived immediately for UI/list state while preserving active playback; cleanup/removal from playback context is deferred to natural playback/session boundaries.
- [x] Locus FF/RW text navigation shipped: tap rewind/forward now moves by sentence; long-press rewind/forward moves by paragraph, with predictable edge behavior.
- [x] Playback scroll lock-in (spec+tests) shipped: explicit trigger policy extraction, focused unit coverage for detach/reattach/recenter invariants, and bounded instrumentation smoke scaffold + contract doc.
- [x] Android performance quick-wins shipped (bounded): share/save refresh coalescing to reduce duplicate queue churn, memoized QueueScreen list transforms, PlayerScreen keyed queue/archive lookups, and debug-only queue-hash gating so release paths avoid unnecessary hash work.
- [x] Share-save refresh burst dedupe follow-up shipped (bounded): keyed coalescing layered on top of debounce to collapse same-item refresh bursts, plus debug-only refresh counters (source/seen/skipped/executed) for validation without release-path impact.
- [x] Reader rendering performance follow-up shipped (bounded): ReaderBody now caches base annotated full-text/link/search markup by stable keys and applies playback/focus highlight overlays on top, reducing repeated long-article annotated-string rebuild work while preserving current behavior.
- [x] Cached-item invalidation narrowing shipped (bounded): replaced full cached-id table emission observer with scoped latest-write + count-change reconciliation so cache writes update only impacted visible items while still handling delete/eviction shrink paths safely.
- [x] App-shell recomposition pressure follow-up shipped (bounded): removed root collection of high-churn playback engine state in `MimeoApp`, shifted playback-in-progress checks to local usage points, and preserved existing navigation/playback behavior without broad state architecture refactors.
- [x] Playback-owner state correctness pass shipped: Locus title ownership now follows true playback owner (engine/session) with bounded preview override rules, and Up Next row markers now distinguish actively playing vs ready-to-resume owner states.
- [x] In-article link preservation (Android v2 slice) shipped: reader/Locus now render preserved HTTP/HTTPS in-article links as clickable spans using backend `content_blocks.links` metadata with safe fallback behavior when metadata is absent/invalid, and taps open externally via Android `Intent.ACTION_VIEW`.
- [x] Up Next infinite scroll shipped: `/playback/queue` offset pagination with scroll-trigger appending, pull-to-refresh reset, and stable scroll position across Locus navigation.
- [x] Settings collapsible row descriptions shipped: toggle help text is collapsible with inline info icon, Material icons for copy/share/use actions, and connection test results persist in panel with share/copy affordances.
- [x] Connection defaults v1 stage 2 shipped: Local/LAN/Remote mode defaults and guidance updated.
- [x] Item actions v1 shipped: Share URL + Open in browser in Up Next and Locus overflows; long-press on Up Next rows opens bottom-sheet action menu; canonical overflow order per `docs/ANDROID_ITEM_ACTIONS_SPEC.md`.
- [x] Reader text actions shipped: custom floating selection toolbar (Copy + Share selected text); Copy article text + Share article text in Locus overflow with conditional citation block (title/source/URL); `buildArticleShareText` unit tests.
- [x] Reader scrollbar shipped: non-draggable visual scroll indicator on reader right edge via `drawWithContent`; thumb height and position track scroll fraction.
- [x] Reader selection edge-scroll shipped: after releasing a selection handle near top/bottom edge (screen-fraction threshold), reader auto-scrolls ~870 px over 1 s; driven by `edgeScrollSpeed` polled at ~60 fps in PlayerScreen.

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
- [x] Share-sheet plain-text capture shipped: plain text is saved as readable content, URL-only shares remain URL capture, and mixed text+link shares use text capture behavior.

### User sign-in (Phase 3)
- [x] Username/password sign-in flow — shipped per `docs/ANDROID_AUTH_PHASE3_SPEC.md` slice plan (gate, recovery, sign-out, settings coexistence).

### Next tickets
1. [~] Problem reports v2 attachment contract (CONTRACT CHANGE): Android opt-in UI/payload path is now implemented (default-OFF in-dialog attachment checkboxes for title/text + compact privacy hint + bounded attachment payload fields); backend persistence/export contract work remains pending per `docs/PROBLEM_REPORT_ATTACHMENT_V2_CONTRACT_SPEC.md`.
   - Current backend limitation until contract lands: v1 persisted/exported report shape remains the authoritative operator record.
2. [ ] Cross-repo source metadata unification: align backend + extension/web on the same provenance/origin contract and rendering precedence shipped on Android (no body-link source inference).
3. [ ] Source metadata backfill/legacy normalization: define how older items without metadata should render and whether any safe migration/backfill is warranted.
4. [ ] Offline action queueing follow-up: expand deferred-sync coverage beyond favourite/archive to remaining item lifecycle actions where local-first behavior is valuable.
5. [ ] Audio-focus/ownership long-session watch: continue targeted stabilization for rare media-button ownership drift in very long sessions, using existing observability hooks.
6. [x] Keep-screen-on/session UX follow-up: shipped — screen stays on during active playback or manual reader mode sessions (PR #167).

## Reader/Player UX fidelity + state persistence backlog

### Near-term implementation candidates (ranked)
1. [x] Search within Locus: in-article text search with next/previous result navigation, visible match count, and preservation of active playback highlight behavior (PR #162).
2. [x] Scroll-level persistence across tabs (Locus rules): per-item reader scroll offsets now persist/restore; Locus-tab return from preview now reattaches to now-playing item using a Settings toggle for either last reader position (default) or live playback pointer.
3. [x] Reader/Locus paragraph formatting fidelity: paragraph spacing and clickable links shipped.
4. [x] Reader/Locus clickable links: render links as tappable in-body spans with safe external-open behavior, while preserving current selection and playback UX.
5. [x] Locus bottom-gap transition issue: Scaffold content insets removed to eliminate bottom vacated gap.
6. [x] Progress/player scroll jerk during drag: player slider drag stabilized against live progress updates to prevent jump/jitter in reader scroll position.
7. [x] Collapsible pending-item section in Up Next: pending saves moved to a dedicated filter chip (rather than a collapsible section), achieving the same queue-noise reduction goal.

### Spec/design-first items (clarify behavior before coding)
1. [x] Auto-archive toggle at end of article: shipped — configurable toggle archives item on playback completion (PR #164).
2. [x] Undo last article archive/delete: shipped — undo snackbar for last archive/bin action with backend unarchive call (PR #165).
3. [x] Start-in-full-screen toggle for Locus: spec approved — no toggle; explicit item opens always expand, autoplay preserves state. See `docs/ANDROID_LOCUS_START_FULLSCREEN_SPEC.md`. Implementation is a one-line fix in `openRequestSignal` LaunchedEffect.
4. [x] Full-text/default-open behavior spec finalized: see `docs/ANDROID_LOCUS_FULLTEXT_DEFAULT_OPEN_SPEC.md` (decision-only; implementation remains a follow-up ticket).

### Later / exploratory
1. [ ] Time-based FF/RW follow-up (optional): evaluate whether a separate time-skip control model is still useful now that text-native sentence/paragraph FF/RW is shipped.

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
10. [x] Phase 6.3: folder detail view with playlist counts and remove-from-folder shipped.
11. [ ] Next: folder badges in playlist list and optional nested folders.
12. [x] Locus expand/collapse on `main`: explicit buttons only, collapsed tab entry, expanded resume/direct entry, title ellipsis fix, and TESTING.md invariants checklist.
13. [x] Player completion icon follow-up: completion iconography shipped (PR #58).
14. [ ] Shared pinned PlayerBar on `main` remains a separate architecture ticket, not part of playback-speed follow-up.
15. [x] Player screen banding foundation: TopAppBar + reader body + pinned controls above bottom nav shipped (PR #56).
16. [x] Reader highlight progression: sentence-level highlighting with range-level (TTS `onRangeStart`) support and sentence fallback.
17. [x] Player chrome compression slice: persistent title strip + 3-state controls with chevron mode transitions and persisted settings.
18. [x] Header action polish: refresh visual states unified and speed control updated to compact panel styling with preserved speed semantics.

## Testing debt
- [ ] NoActiveContentStore Worker→ViewModel integration test: verify that IDs written by the worker during a download run are correctly read back and merged into the ViewModel's `noActiveContentItemIds` on the next queue load. Needs ViewModel testing infrastructure (e.g. `TestCoroutineDispatcher` + fake repository) not yet established in this project.

## Later
- [ ] Hosting story v2 UX: HTTPS-first guidance, per-device token setup polish, safer LAN-mode flow.
- [ ] Persist last segment index per item in DataStore for cross-process resume.
- [ ] Add queue filters/sorting controls from playback API query params.
- [ ] Audio focus/media session polish.
- [ ] Better conflict handling for stale cached versions during long offline sessions.
- [ ] Replace dev cleartext dependence with HTTPS-friendly transport story for hosted/mobile use.
- [ ] Scrollbars for Up Next and Settings: non-draggable `drawWithContent` indicator for Settings; draggable scrollbar for Up Next LazyColumn (touch overlay maps drag Y → `lazyListState.scrollToItem`; useful for long queues).
- [ ] Compose BOM migration to 1.10.x: bump `compose-bom` from `2024.06.00`, fix any Material3/API deprecations; no architectural changes expected; do as a standalone session. (Also unblocks `onSelectAllRequested` in reader selection toolbar.)



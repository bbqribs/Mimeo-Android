# Roadmap (Android)

Source of truth for redesign scope: `docs/REDESIGN_V2_PLAN.md`.
Drift guard: `docs/REDESIGN_V2_DECISION_SNAPSHOT.md`.
Most recent audit: `docs/REDESIGN_V2_AUDIT_2026-04-21.md`.
Redesign completion plan (post-shipped-redesign forward sequence): `docs/REDESIGN_COMPLETION_PLAN.md`.
Mini-player control spec (v1 shipped; time-based skip deferred): `docs/ANDROID_MINIPLAYER_CONTROL_SPEC.md`.
Up Next layout spec for history / active / upcoming / snap-to-active: `docs/ANDROID_UP_NEXT_LAYOUT_SPEC.md`.
Post-redesign product model planning (canonical in Mimeo): `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\PRODUCT_MODEL_POST_REDESIGN.md` (Android pointer: `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md`).
Workflow + transition guidance: `docs/planning/AGENT_WORKFLOW.md` and `docs/planning/PROJECT_HANDOFF.md`.
Planning-doc ownership decision: `docs/planning/DOCS_OWNERSHIP_DECISION.md`.

## Open — priority order

### P0 — redesign closeout (shipped)

1. [x] **Up Next screen rearchitecture.** Removed the legacy library-filter
   chips (`All / Favorites / Archive / Bin / Unread / In progress / Done`)
   from `QueueScreen`; Up Next is now session-queue-first with retained
   manual-save `+`, seed-source + explicit re-seed confirmation, and
   session pointer semantics.
2. [x] **Persistent mini-player + real Locus route (Phase 3 structural).**
   `MiniPlayer` is pinned at the bottom of the shell outside Locus routes,
   and `ROUTE_LOCUS` / `ROUTE_LOCUS_ITEM` now render the real Locus player
   route (no placeholder `Box` route).
3. [x] **Up Next drag-to-reorder + TalkBack move-up/move-down + playlist
   orphan cleanup.** Closes the Phase 6 reorder UX gap.
   Handles per plan §8 (grip icon, always-visible in ordered lists) with
   accessible non-drag alternatives per §14. Legacy standalone playlists
   screen wiring is removed; `ui/playlists/PlaylistsScreen.kt` is now
   dialog-only picker UI used by active surfaces.

### P0 — next Android implementation (post-redesign)

4. [ ] **Hosting story v2 UX.** HTTPS-first guidance, per-device token
   setup polish, safer LAN-mode flow.

### P1 — follow-ups implied by shipped state

5. [x] **Phase 0 follow-on decomposition of `MainActivity.kt`.** Shell and
   player/nav wiring are extracted into dedicated holders/composables
   (`MainActivityShell`, `PlayerShellState`) and `MainActivity.kt` is now
   the lighter host/composition entry point.
6. [ ] **Problem reports v2 attachment contract (CONTRACT CHANGE, backend).**
   Android opt-in UI/payload path is implemented (default-OFF attachment
   checkboxes + privacy hint + bounded payload). Remaining work is backend
   persistence/export per `docs/PROBLEM_REPORT_ATTACHMENT_V2_CONTRACT_SPEC.md`.
   Tracked in the Mimeo (backend) repo; this line is a pointer only.

### P2 — exploratory / deferred

7. [ ] **Time-based FF/RW follow-up (optional).** Evaluate whether a
   separate time-skip control model is still useful now that sentence /
   paragraph FF/RW is shipped.
8. [x] **Persist last segment index per item in DataStore** for
   cross-process resume.
9. [x] **Audio focus / media session polish** beyond the bounded drift
   fixes already shipped.
10. [x] **Conflict handling for stale cached versions** during long
    offline sessions.
11. [x] **Cleartext → HTTPS-friendly transport** for hosted/mobile use.
12. [ ] **Scrollbars** for Up Next (draggable, long-queue ergonomics) and
    Settings (non-draggable `drawWithContent` indicator).
13. [ ] **Compose BOM migration to 1.10.x.** Bump from `2024.06.00`, fix
    any Material3/API deprecations. Standalone session. Unblocks
    `onSelectAllRequested` in the reader selection toolbar.

### Testing debt

- [x] `NoActiveContentStore` Worker→ViewModel integration test: verify IDs
  written by the worker during a download run are read back and merged
  into the ViewModel's `noActiveContentItemIds` on the next queue load.

## Redesign v2 execution track (reference)

Settled rules and phase map live in `docs/REDESIGN_V2_PLAN.md`. Quick
pointers:

- **Phase 0**: `MainActivity` / root state extraction: shipped, including
  follow-on shell/player-state decomposition.
- **Phase 1 (backend contracts)**: shipped (library `view=` query, batch
  endpoints).
- **Phase 2 (drawer + library views + playlist visibility)**: shipped.
- **Phase 3 (mini-player + Locus restructure)**: shipped.
- **Phase 4 (multi-select + batch actions)**: shipped.
- **Phase 5 (playlist management + reorder)**: shipped.
- **Phase 6 (Up Next finalization)**: shipped for local session behavior,
  including reorder + TalkBack move actions. Cross-device sync deferred to
  v2+ (requires backend CONTRACT CHANGE).

Non-goals still in force:
- No playlist folders (cut in v0.2 of the plan).
- No auto re-seed on pull-to-refresh (plan §3.2, Risk 10).
- No cross-device Up Next sync in v1 (snapshot rule 14).

---

## Shipped log

History of shipped work, kept for reference. Newest at the top of each
block. Not a forward-looking list.

- [x] **Android smart playlist create/edit/delete UI shipped**: Android can create smart playlists from the drawer, edit smart playlist name/filters/sort from smart detail, and delete smart playlists with confirmation. The UI uses the dedicated `/smart-playlists` CRUD endpoints and refreshes smart lists/details without changing manual playlist APIs or selected/default manual-playlist ID guards.
- [x] **Smart playlists Android read/display + pin UI + Up Next seed shipped**: Android now uses dedicated `/smart-playlists` endpoints, displays smart playlists separately from manual playlists, and splits smart detail into Pinned and Live sections using backend `pin_count` (Pinned first, then Live). Live rows can be pinned; Pinned rows can be unpinned; Pinned rows can be reordered with up/down controls inside the Pinned section only; Live rows remain non-reorderable. Pin/unpin/reorder refreshes smart playlist content. Row tap-to-Locus and smart row Play Now / Play Next / Play Last are preserved. Batch Add Selected to Up Next uses the visible smart-playlist order (Pinned first, then Live). "Use as Up Next" is shipped: smart seeding uses the currently displayed rows in displayed order, requires confirmation before replacing an existing non-empty Up Next, labels the source as `Smart view: {name}`, and is snapshot-only with confirmation copy that says no live sync. No backend/API change, cross-device continuity, live sync, or manual playlist reseed behavior change was introduced.
- [x] **Android smart playlist freeze-as-manual shipped**: Freeze now creates a manual-playlist snapshot from the smart playlist. Blank name input uses the backend default. On success, manual playlists refresh and snackbar `Open` navigates directly to the created manual playlist. Freeze does not mutate the source smart playlist. Existing pin/unpin/reorder, `Use as Up Next`, and row queue actions remain preserved.
- [x] **Android smart playlist end-to-end verification passed on attached device** (2026-04-28): verified smart playlist drawer/detail visibility, pin/unpin/reorder, Use as Up Next, Freeze as manual, snackbar Open -> created manual playlist, row tap to Locus, Play Now/Play Next/Play Last, batch Add selected to Up Next, manual playlist reorder, and selected/default manual-playlist guards. Build/test gates passed: `assembleDebug` and `testDebugUnitTest` (434 tests).

- Up Next layout slices 1–3 (2026-04-27): active/upcoming region
  scaffolding; active anchor (not draggable); history hidden (deferred);
  snap-to-active pill; Clear upcoming near Upcoming header; Clear all
  session in Up Next overflow/contextual destructive area; Save queue as
  playlist from Up Next overflow (saves active item + upcoming items in
  session order; hidden pre-active/history rows excluded). No backend or
  API contract changes.
- Sectioned Library Slice 1 (2026-04-27): Inbox / Favorites / Archive
  show static date section headers only for Newest sort with blank
  search. Buckets are Today, Yesterday, This Week, This Month, Last
  Month, Older. Inbox pending stays separate above date sections. Bin,
  active search, and non-Newest sorts remain flat. Existing row tap,
  long-press selection, overflow queue actions, batch actions, refresh,
  search, and sort behavior preserved.
- Mini-player v1 (2026-04-27): two-row/decompressed controls with
  title/source separated from playback controls; always-visible speed
  chip; consolidated play/pause preserved; sentence-level ff/rw and
  long-press paragraph jumps preserved. Time-based skip remains deferred.
- Playlist/library queue-action polish (2026-04-27): playlist row tap
  opens Locus only; library batch Add Selected to Up Next shipped;
  library Play Now shipped as non-destructive insert-and-play; playlist
  batch queue placement chooser shipped with Play Next and Play Last /
  Add to bottom, preserving selected items in visible playlist order.
- Locus/player spike (2026-04-27): resolved that Locus is the full
  player. No separate full-player route or Player Queue surface is
  planned. Optional small Locus bridge chip remains deferred.
- Phase 6A: device-local session queue substrate — remove-from-session
  per row, clear-session button, duplicate-move semantics for Play
  Next / Play Last, `From: [playlist]` seed-source label, snackbar
  feedback, session-preserve guard, session persists across navigation
  / restart / library-view browsing.
- Phase 5C: Play Next / Play Last from `QueueScreen` overflow,
  `PlaylistDetailScreen` per-row overflow, Locus player overflow;
  collapsible session-queue panel in `QueueScreen` with current-item
  indicator.
- Phase 4: multi-select and batch actions across list surfaces with
  partial-failure and narrow undo.
- Phase 2B: Inbox / Favorites / Archive / Bin drawer routes load real
  library data from `GET /items?view=...` with shared list rendering.
- Deferred player-chrome ticket: persistent top now-playing title bar
  removed; compact scrolling (marquee) title on player controls;
  article-level title in reader overlay top bar.
- Locus: full-text default-open behavior
  (`docs/ANDROID_LOCUS_FULLTEXT_DEFAULT_OPEN_SPEC.md`), source /
  publication TTS cue, archive-while-playing continuity, FF/RW
  sentence / paragraph text navigation.
- Playback scroll lock-in (spec + tests), app-shell recomposition
  reductions, cached-item invalidation narrowing, share-save refresh
  coalescing, reader rendering memoization.
- Playback-owner state correctness pass (Locus title ownership,
  Up Next row markers).
- In-article link preservation (Android v2 slice) — render
  `content_blocks.links` as tappable spans with safe fallback.
- Up Next infinite scroll via `/playback/queue` offset pagination with
  scroll-trigger appending, pull-to-refresh reset, stable scroll
  position across Locus navigation.
- Settings collapsible row descriptions; Connection defaults v1 stage
  2 (Local / LAN / Remote mode defaults).
- Item actions v1 (`docs/ANDROID_ITEM_ACTIONS_SPEC.md`): Share URL +
  Open in browser in Up Next and Locus overflows; long-press bottom
  sheet; canonical overflow order.
- Reader text actions: custom floating selection toolbar, Copy /
  Share article text in Locus overflow with citation block;
  `buildArticleShareText` unit tests.
- Reader scrollbar (non-draggable `drawWithContent`).
- Reader selection edge-scroll after releasing a handle near screen
  edges.
- Search within Locus, per-item reader scroll offsets + Locus-tab
  return preference, paragraph formatting fidelity, clickable
  in-body links, Scaffold bottom-gap fix, player slider drag
  stabilized, pending-items filter chip (in place of the previously
  proposed collapsible section).
- Auto-archive at article end (PR #164), undo last archive / bin
  (PR #165), start-in-full-screen / Locus collapse removal
  (`docs/ANDROID_LOCUS_START_FULLSCREEN_SPEC.md`).

### Pre-redesign playback / share-sheet / auth / offline work

- Share-sheet saving (P0): `ACTION_SEND` URL capture via
  `ShareReceiverActivity`, `POST /items` with idempotency key,
  default-save playlist routing, Collections discovery guidance,
  success/error notifications, persistent-notification toggle,
  share-save auto-download, destination-aware success messages,
  manual URL / paste-text `+` dialog, plain-text capture.
- Auth Phase 3: sign-in startup gate, username/password flow,
  stale-token recovery, explicit sign-out, token storage hardening
  (secure-at-rest with migration), endpoint/scheme guardrails.
- TTS voice selection + preview, title-before-body option,
  end-of-article completion cue, queue metadata polish, observability
  + developer toggles, player/reader handoff polish, autodownload
  consistency + durability, Up Next transition clarity follow-up.
- Mimeo Control Phase 2 Slice 1 (`PlaybackEngine` extraction),
  Phase 3 Slice 1 (foreground service + media session + notification),
  Phase 3 Slice 2 (audio-focus / interruption policy). Continuous
  play reliability fix + background playback observability.
- Pending outcome simulator (dev-only), offline / no-active-content
  copy + behavior cleanup, plain-text share behavior.
- Structured source metadata emission, provenance / origin / content
  separation, source/title rendering, Up Next orientation pass
  (active-item indicator, scroll restore, stale guardrails, tab-tap
  cycle), Locus next-article handoff, offline action queueing
  (favourite / archive / bin lifecycle), cross-repo source metadata
  unification (`docs/ANDROID_SOURCE_METADATA_UNIFICATION_SPEC.md`),
  legacy source metadata normalization
  (`docs/ANDROID_LEGACY_SOURCE_METADATA_SPEC.md`),
  audio-focus/ownership long-session watch fixes, keep-screen-on
  during playback / manual reader (PR #167).
- Reader / player fidelity: sentence-level highlight with range
  support, player chrome compression (Full / Minimal / Nub), header
  action polish, shared chrome polish, auth/session clarity, Locus
  speed control polish, refresh affordance polish, reader
  chrome/fullscreen interaction, Up Next controls cleanup (single
  Refresh action), player chrome reliability follow-up, UX
  compression pass and follow-ups.

### v1.1 → redesign bridge (historical)

- 4-tab nav shell + black/purple theme foundations.
- Mini control panel (collapsed Locus peek) — later removed; Locus
  is always expanded.
- Up Next skeleton (playlist dropdown, search, filter chips, grouped
  sections). The filter chips from this skeleton are what ticket 1
  now removes.
- Typography preferences, playback speed in Locus (decision:
  `docs/decision-playback-speed-location.md`). Superseded ticket to
  move speed into the pinned `PlayerBar` was cancelled.
- Collections baseline + playlist browser — later superseded by the
  drawer playlists section.
- Playlist folders (Phase 6.2 / 6.3) — shipped then **cut** in
  Redesign v2 (`REDESIGN_V2_PLAN.md` §4 decision #10). Entities,
  DAO, repository, Collections/FolderDetail screens, and folder
  ViewModel methods removed. DB bumped to v6 with `MIGRATION_5_6`
  dropping orphaned tables.
- Locus expand/collapse explicit buttons + TESTING.md invariants,
  player completion iconography (PR #58), player screen banding
  foundation (PR #56).

### MVP → v0.3 (historical)

- App scaffold (Compose + DataStore + OkHttp), Settings screen,
  queue / player / progress sync, segment-based playback, offline
  caching + retry queue, WorkManager auto-flush, now-playing session
  snapshot (v0.3), in-app connectivity diagnostics, progress model
  v1, MVP playback end-to-end polish, chunking improvements,
  start-listening-here + highlight + auto-scroll, Now Playing UX
  refinements, named playlists v1, playlist item membership UX.


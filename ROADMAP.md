# Roadmap (Android)

Cross-repository authority for remaining project work:
[`PROJECT_COMPLETION_AND_GAPS_PLAN_2026_07.md`](https://github.com/bbqribs/Mimeo/blob/56fbc9bc391bd55ee5290abc92c367859a3eb075/docs/planning/PROJECT_COMPLETION_AND_GAPS_PLAN_2026_07.md),
approved in Mimeo PR #822. Its five lanes, entry gates, dependencies, and
exclusions govern when an older Android planning document differs. This
roadmap mirrors only the Android participation and does not duplicate the plan.

Source of truth for redesign scope: `docs/REDESIGN_V2_PLAN.md`.
Drift guard: `docs/REDESIGN_V2_DECISION_SNAPSHOT.md`.
Most recent audit: `docs/REDESIGN_V2_AUDIT_2026-04-21.md`.
Visual v1 final QA audit (2026-05): `docs/ANDROID_VISUAL_V1_FINAL_QA_2026_05.md`.
Redesign completion plan (post-shipped-redesign forward sequence): `docs/REDESIGN_COMPLETION_PLAN.md`.
Mini-player control spec (v1 shipped; time-based skip deferred): `docs/ANDROID_MINIPLAYER_CONTROL_SPEC.md`.
Up Next layout spec for history / active / upcoming / snap-to-active: `docs/ANDROID_UP_NEXT_LAYOUT_SPEC.md`.
Playback Actions v2 (row [Play] [⋮], tap-row-play, Play All, Play from Here, overflow ordering; Smart Queue as a playlist-like source): `docs/ANDROID_PLAYBACK_ACTIONS_V2_SPEC.md`.
Up Next History / Earlier in queue spec: `docs/ANDROID_UP_NEXT_HISTORY_EARLIER_QUEUE_SPEC.md`.
Server-authoritative Up Next continuity: `docs/ANDROID_UP_NEXT_CONTINUITY.md`.
Post-redesign product model planning (canonical in Mimeo): `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\PRODUCT_MODEL_POST_REDESIGN.md` (Android pointer: `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md`).
Workflow + transition guidance: `docs/planning/AGENT_WORKFLOW.md` and `docs/planning/PROJECT_HANDOFF.md`.
Planning-doc ownership decision: `docs/planning/DOCS_OWNERSHIP_DECISION.md`.

## Historical productization state memo

This memo records the household-launch boundary at the time it was written. It
is not the current execution queue; the five-lane programme below is.

- [x] **Authenticated account and endpoint identity surfaced**
  (T-AND-ACCOUNT-IDENTITY-VISIBILITY-1; pending ticket closeout): Drawer and
  Settings → Account & Connection now show the active session's submitted
  account identifier, canonical endpoint origin, and sign-in state. The
  identity is persisted with the actual device session, never comes from the
  editable endpoint field, and clears when that token is cleared or replaced.

- [x] **Server-authoritative Up Next continuity implemented** (2026-07-17;
  pending ticket PR merge): Android
  now adopts and synchronizes the per-user `/up-next/session` projection with
  ratified first-adoption, offline/reconnect and stale-conflict semantics.
  The existing Room-backed owner and offline playback remain intact; continuity
  metadata is isolated by account + canonical endpoint, lifecycle removals are
  compacted safely, Smart Queue and `/playback/state` remain independent, and
  playback history remains deferred. See `docs/ANDROID_UP_NEXT_CONTINUITY.md`.

- [x] **Android P-1 host externalization is closed.** Release defaults no
  longer expose personal hosts or IPs; developer presets remain in the debug
  source set only. The release path is manual-first, and the release variant
  gate passed.
- **Productization pause:** backend portability remains the active track. Do
  not start Play Console, signing, privacy-policy, network-security, CI, icon,
  or onboarding work unless the operator explicitly allocates spare Android
  capacity. Android P-2/P-4 remain sensible later follow-ups, not active work
  now.
- **Household distribution is shipped (2026-07-15 status).** The 2026-07-08
  carve-out (**T-AND-DIST-MIN-1** — release signing + versioned signed APK +
  sideload install/update runbook; plan:
  `docs/ANDROID_HOUSEHOLD_DISTRIBUTION_MINIMUM_2026_07.md`) is complete:
  - Signed household release line shipped (PR #451; currently `0.4.3` /
    versionCode 9 via PR #457) with versioned artifacts and
    `RELEASE_NOTES.md` discipline. **No member path requires Android
    Studio.**
  - Distribution/installation now also runs through the Mimeo repo's
    operator-authorised path: authenticated same-origin **Account → Android
    app** APK download and Mimeo Control publish workflow (Mimeo PR #759),
    plus a guarded signer-aware device installer (Mimeo PR #760).
  - Debug and production apps have isolated package identities (PR #458):
    distinct debug package ID, debug version suffix, "Mimeo Debug" label.
  - Household GO was recorded 2026-07-11 and the first trusted member is
    onboarded (Mimeo repo, `HOUSEHOLD_READY_REVIEW_2026_07.md` §4; PRs
    #757–#758). The household-readiness lane is closed; the active
    cross-repo queue is Mimeo `ROADMAP.md` → "Current focus — household
    operation and product parity".
  - No Play Store/app-store readiness claim is made. The rest of the
    productization pause (Play Console, CI, store packaging, onboarding
    polish beyond the crowned username/onboarding UX polish item) stays in
    force.

## Active project-completion programme

The supported posture remains private-household distribution against the
Tailscale-only Mimeo service. Store/public-service work is not implied.

### Shipped boundary

- Startup polish is shipped: restored sessions do not flash sign-in, startup
  has a neutral loading state, and the drawer opens closed.
- Smart Queue source actions, Play All/Play from Here, persisted reorder,
  revision preconditions, and conflict refresh are shipped.
- Compose migration foundations, including Compose BOM `2026.03.00`, built-in
  Kotlin, and resolver work, are shipped. They are not an open numbered ticket.
- SettingsStore/Robolectric isolation is fixed and the full JVM suite is not
  quarantined.
- Playback-promotion cleanup is complete; playback commitment has one pointer
  owner per route.
- The `ApiClient` request-builder cleanup is complete. Any future `Accept`
  normalization is evidence-triggered maintenance, not unfinished cleanup.
- The backend summary contract and Android summary entry point are already
  shipped. Household AI summaries remain disabled under the approved privacy
  stance; there is no missing-contract implementation ticket.

### Lane 1 — Audit

**Owner:** Mimeo. Android has no implementation ticket in this lane. It may
provide read-only client-impact input where a converted event family crosses a
mobile contract. Entry and internal prerequisites remain governed by `AUD1`
and `AUD2` in the cross-repo authority.

### Lane 2 — Queue and parity

**Owners:** Mimeo for policy/backend/web; Mimeo-Android for dependent client
adoption and assurance. **Entry gate for server parity:** operator decision
`B1` plus ratified Now Playing/Earlier move rules.

Preserve this order: crowned state/conflict policy → backend pointer/History →
backend semantic reorder → web parity → Android server parity → cross-device
assurance. Android can execute the local/CI slice of
`T-AND-INSTRUMENTED-ASSURANCE-2` now: add a required emulator lane and narrow
local Up Next/History UI coverage while keeping real-headset checks manual. The
archive/History semantics audit is also independently executable if it does not
invent server truth. Cross-device History, reorder, offline-conflict, and
two-device scenarios remain blocked on merged backend contracts and Android
server-parity adoption.

### Lane 3 — Migration

**Owner:** Mimeo/operator infrastructure. Android participates only after a
genuine Linux-target rehearsal passes, in each of the two production-class
dress rehearsals, by temporarily retargeting a client over tailnet HTTPS and
proving rollback. Android work does not make the service migration-ready.
Readiness remains unclaimed until the separate-target Linux rehearsal, both
dress rehearsals, and current G1–G9 evidence exist; real migration has a
separate operator go/no-go.

### Lane 4 — Maintenance

**Owners:** each repository for its surface, with one Android companion record.
**Entry gate:** operator decision `C1`. After approval, batch evidence-backed
reviews quarterly; only backup freshness and consolidated security-advisory
review are monthly across the programme. Review sooner only for a support
deadline, breakage, or relevant advisory.

Current Android watch areas are Media3 migration, replacement of
`security-crypto`, SDK 36, and older AndroidX dependencies. A watch item is not
an automatically scheduled major upgrade: each review must record support and
security evidence, compatibility risk, deferral, and rollback before opening a
bounded ticket. Completed Compose and architecture/refactor foundations stay
closed.

### Lane 5 — Triggered (dormant)

Android participates only after the named cross-repo trigger and operator
decision. Store distribution, public ingress consequences, OAuth/OIDC/MFA,
real-user deletion/export UX, per-user AI/BYOAI, key re-encryption support, and
mobile operator elevation are dormant. The signed household sideload/download
path remains the supported distribution route.

### Parallelism and gates

- Android local emulator-CI/Up Next assurance and the local archive/History
  audit may run in parallel with Mimeo audit conversion, queue-policy work,
  migration target/tooling work, and the maintenance baseline.
- Android server History/reorder/conflict adoption cannot start against an
  unmerged backend contract. Cross-device assurance follows that adoption.
- Android's migration check follows Linux rehearsal evidence and is serialized
  inside each dress rehearsal; it is not a substitute for either rehearsal.
- Lane decisions remain with the operator: `B1`/movement rules for queue work,
  `DEC-A1`–`DEC-A4` for migration, `C1` for maintenance, and each dormant
  capability's explicit trigger. Android must not install a new policy choice.

## Superseded priority snapshot (historical; not an active queue)

The former numbered “Open — priority order” list below is retained as a dated
record. Unchecked boxes are not executable tickets or an unconditional next
queue; re-entry must come through the applicable lane and gate above. Shipped
items stay closed even where this snapshot once described them as open.

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

Active guarded-default-enable PR: visual v1 defaults on for fresh installs
and cleared app data while stored user opt-outs remain honored; merge is
gated on the final QA matrix in
`docs/ANDROID_VISUAL_V1_FINAL_QA_2026_05.md`.

4. [x] **Playback / queue UX cluster.** Smart Queue as playlist-like source
   and Up Next History / Earlier in queue implementation. History is bounded
   session-local state; default Save queue-as-playlist remains Active + Up Next
   only.
   **Partly shipped (2026-07-26):** Up Next History / Earlier in queue is
   implemented (T-AND-UPNEXT-HISTORY-1, PR #468), and its pointer semantics were
   then corrected and cleaned up by PRs #475/#476 — the session pointer now
   follows the engine's commitment to play, with one owner per route. Android
   History remains **bounded session-local state**; cross-device History depends
   on the backend projection `T-UPNEXT-HISTORY-CONTRACT-1` in the Mimeo repo and
   must not be assumed before it merges. Smart Queue-as-playlist-source is the
   part of this cluster still open.
   Smart Queue reorder decision: Smart Queue should become a persisted,
   reorderable inbox view, distinct from chronological Inbox and local Up
   Next. New items default to the top; user-adjusted relative order persists
   below/around new arrivals. Android reorder UI is blocked until the Mimeo
   backend owns a Smart Queue ordinal/rank contract and reorder endpoint
   (CONTRACT CHANGE). Android now adopts the server revision precondition and
   conflict recovery for persisted Smart Queue order; V1 search remains
   supported, but every reorder path is disabled while search/filter is active.
   Smart Queue source idiom (accent left rule on header, reorder status,
   per-row source-list rule) shipped in M-V2-03; drag reorder preserved. The
   remaining cross-device slice is represented only in Lane 2 above.
5. [x] **Startup polish cluster.** No sign-in flash for restored sessions,
   neutral loading/splash state, and drawer closed on launch are shipped.
6. [ ] **Instrumented assurance split.** The local/emulator-CI slice is
   independently executable through Lane 2; cross-device History/reorder
   coverage waits on merged backend contracts and Android parity adoption.
7. [ ] **Privacy-first telemetry cluster.** Default telemetry stays
   anonymised/aggregate-only with no titles, URLs, article text, domains,
   playlist contents, or reading-choice payloads; problem reports remain
   explicit opt-in exception.
8. [ ] **Progress / playback duration model.** Pointer/progress audit plus
   listening-time estimates at 1.0x and current-speed-adjusted playback.
9. [x] **Android maintenance foundations.** Compose BOM/deprecation audit,
   scrollbar coverage where appropriate, and limited refactor survey.
   **The refactor-survey half is done:** `docs/planning/ANDROID_ARCH_PERF_PLAN_2026_07.md`
   is complete — all five tickets T-A…T-E shipped (PRs #445–#448, #477). That plan
   should no longer generate tickets. Its explicitly deferred items (May survey R2
   `LibraryStateHolder`, R12 `QueueLoadCoordinator`, R10 `AppRoute` sealed routes,
   F7 unmeasured hotspots, F8 grab-bag splits) remain valid backlog. Compose BOM
   foundations are shipped; future dependency work follows Lane 4 rather than
   this numbered snapshot.

### P1 — follow-ups implied by shipped state

10. [x] **Phase 0 follow-on decomposition of `MainActivity.kt`.** Shell and
   player/nav wiring are extracted into dedicated holders/composables
   (`MainActivityShell`, `PlayerShellState`) and `MainActivity.kt` is now
   the lighter host/composition entry point.
11. [ ] **Problem reports v2 attachment contract (CONTRACT CHANGE, backend).**
   Android opt-in UI/payload path is implemented (default-OFF attachment
   checkboxes + privacy hint + bounded payload). Remaining work is backend
   persistence/export per `docs/PROBLEM_REPORT_ATTACHMENT_V2_CONTRACT_SPEC.md`.
   Tracked in the Mimeo (backend) repo; this line is a pointer only.
12. [x] **Roadmap hygiene pass** — identify duplicate/stale shipped entries;
   correct cross-repo shipping-state discrepancies. Coordinated with Mimeo
   repo. Done 2026-07-26 (T-ROADMAP-RECONCILIATION-2026-07), paired with the
   equivalent pass in the Mimeo repo.
13. [ ] **Reader context menu expansion.** Four additions to the floating
   selection toolbar and link long-press sheet:
   - Share selected text (plain-text share of highlighted passage, no
     citation block).
   - Web search on selected text (fire implicit `ACTION_WEB_SEARCH`
     intent with selected string).
   - Long-press a link → share the link address (URL only, not article
     share flow).
   - Long-press a link → copy the link address to clipboard.
   The old numbered Compose BOM dependency was stale; the shipped BOM foundation
   already exposes the required selection-toolbar API.
14. [x] **Playback / archive semantics aligned**
   (`T-AND-PLAYBACK-ARCHIVE-SEMANTICS-1`, 2026-07-27). Archive is now an
   organizational flag: it preserves the current playback owner, progress,
   completion and bounded process-local History. Archived session members are
   skipped by future continuation, completion records exactly one History row,
   and unarchive neither resets progress/completion nor starts playback. History
   and Earlier in queue display entries chronologically, with the oldest at the
   top and newest at the bottom, without changing Previous traversal order.
   Archived non-current rows are absent from the future Up Next presentation even
   while continuity data retains them, so the visible list matches autoplay eligibility.

### P2 — exploratory / deferred

13. [ ] **Time-based FF/RW follow-up (optional).** Evaluate whether a
   separate time-skip control model is still useful now that sentence /
   paragraph FF/RW is shipped.
14. [x] **Persist last segment index per item in DataStore** for
   cross-process resume.
15. [x] **Audio focus / media session polish** beyond the bounded drift
   fixes already shipped.
16. [x] **Conflict handling for stale cached versions** during long
    offline sessions.
17. [x] **Cleartext → HTTPS-friendly transport** for hosted/mobile use.
18. [ ] **Scrollbars** for Up Next (draggable, long-queue ergonomics) and
    Settings (non-draggable `drawWithContent` indicator).
19. [x] **Compose BOM foundation.** Shipped as BOM `2026.03.00`; future
    Compose/Material changes are evidence-backed Lane 4 maintenance, not an
    unconditional standalone migration or blocker for reader work.
20. [ ] **Up Next / Now Playing visual hierarchy — now-playing accent.**
    The active now-playing item title in Up Next should be rendered at a
    slightly larger size and/or in an accented color to immediately
    distinguish it from upcoming rows. Coordinate with the Up Next layout
    spec (`docs/ANDROID_UP_NEXT_LAYOUT_SPEC.md`); no structural changes
    to session queue data.
21. [ ] **Player / reader boundary polish.** The player title area needs
    more breathing room and a clearer tap separation from the progress
    bar and the reader text surface below it. Candidate treatments:
    increased vertical padding, a subtle divider or surface elevation
    change, or a shadow. Evaluate in Locus at typical font sizes before
    deciding on approach; defer to a focused visual pass session.
22. [ ] **`Accept: application/json` consistency in `ApiClient` (low priority,
    no known symptom).** Endpoints differ in whether they send an `Accept`
    header. `T-AND-APICLIENT-REQUEST-BUILDER-1` deliberately made `acceptJson()`
    opt-in rather than defaulted, so the consolidation could not change any
    endpoint's request bytes; the current inconsistency is therefore frozen
    intentionally, not accidental. **No evidence it causes any problem** — the
    backend has not been observed to vary behaviour on it. Do not promote this
    without such evidence. If ever normalised, do it as one deliberate pass with
    `ApiClientRequestBuilderTest.kt` updated in the same change.
23. [x] **Article summaries entry point and backend contract.** Both are
    shipped. Summary UI/settings consume the backend contract; household
    generation remains disabled under the approved privacy decision until its
    separate per-user trigger and policy gate are satisfied.

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
- Cross-device Up Next sync was outside redesign v1, but server-authoritative
  continuity has since shipped; only the Lane 2 History/reorder parity slices
  remain open behind their current backend gates.

---

## Shipped log

History of shipped work, kept for reference. Newest at the top of each
block. Not a forward-looking list.

- [x] **Playback pointer correctness + architecture/perf queue completion** (2026-07-24 → 2026-07-26): **T-AND-NOWPLAYING-POINTER-DESYNC-1** (PR #475) re-points the Now Playing session to whatever actually starts playing — the pointer now follows the engine's commitment to play rather than item load — and **T-AND-PLAYBACK-PROMOTION-CLEANUP-1** (PR #476) finished the job: `setNowPlayingCurrentItem`, `promoteReaderItemToNowPlaying` and `shouldMutateUpNextActiveItem` are gone, and `playLocusItem` routes through the single decision point `playReaderItem` so there is exactly one pointer owner per route. Implementation and automated gates are complete; optional S24 re-acceptance is not a blocker. **T-AND-APICLIENT-REQUEST-BUILDER-1** (PR #477) consolidated authenticated request construction in `ApiClient` (`authorizedRequest`, `authorizedBuilder`, `executeAuthorizedJson`, `executeAuthorizedNoBody`) with no change to any endpoint's request bytes, locked by `ApiClientRequestBuilderTest.kt` — this was **T-E**, completing the whole `ANDROID_ARCH_PERF_PLAN_2026_07.md` queue (T-D #445, T-B #446, T-A #447, T-C #448, T-E #477). **SettingsStore DataStore binding fix** (PR #478) resolved a genuine full-suite isolation failure by keying the settings DataStore on its file path rather than the property delegate; verified with 4 consecutive `--rerun-tasks` runs at 1145 tests / 0 failures. Nothing is quarantined or excluded from the suite. Details in `TESTING.md`.
- [x] **Save-reliability and offline series shipped** (2026-07-23 → 2026-07-24): pending-save surfacing (T-AND-PENDING-SAVE-SURFACING-1, PR #471, following the save-disappearance investigation PR #469), distinct debug-variant icon (PR #472), inbox parked-save rows (T-AND-INBOX-PARKED-SAVE-ROWS-1, PR #473), and the offline library cache (T-AND-OFFLINE-LIBRARY-CACHE-1, PR #474).
- [x] **Household distribution + launch-support series shipped** (2026-07-08 → 2026-07-15): signed household release line (T-AND-DIST-MIN-1, PR #451: dedicated release key, versionCode discipline, versioned artifacts, `RELEASE_NOTES.md`, sideload runbook, Settings version display); Android problem-report submission fix (PR #452); reader viewport resume + signed 0.4.2/vc8 (PRs #453–#454); account-scoped local-state ownership so persisted state never crosses account/server boundaries (T-AND-STATE-OWNER-LAND-1, PR #455) with crash-safe sign-out; stale-library response guards (PR #456); signed `0.4.3` / versionCode 9 release provenance (PR #457); isolated debug package identity — distinct debug application ID, debug version suffix, "Mimeo Debug" label (PR #458). Distribution to members now also flows through the Mimeo repo's authenticated Account → Android app download and guarded installer (Mimeo PRs #759–#760). Household GO recorded 2026-07-11; first trusted member onboarded (Mimeo repo).
- [x] **Devices & sessions Android UI shipped** (T-AND-DEVICES-1, 2026-07-05): Settings → Account & Connection gains a "Devices & sessions" screen backed by the existing backend `GET/POST /account/devices*` surface (T-HH-DEVICES-1, no backend/API changes). Lists the signed-in user's active sessions with name, signed-in/last-used/expiry times, and a "Current device" marker; never displays token values. Supports revoking a non-current session and "Sign out everywhere else" (current session always preserved), with plain-language confirmation dialogs and stable loading/empty/error states. Reuses the existing authenticated API client and T-AND-AUTH-EXPIRY-1 stale-token re-auth routing on 401. Verified end-to-end on a physical device against the remote runtime. Unit tests, `assembleDebug`, and `assembleRelease` all passed.
- [x] **Android visual v1 surface streamlining shipped** (2026-05-14): Smart Queue search already uses compact row treatment via `LibraryItemsScreen`. Up Next session panel header simplified to seed-source label only (redundant Re-seed button removed; remains in overflow menu). Bluesky candidate cards redesigned: title clickable→article, post area clickable→post, `SaveActionChip` (FilterChip) replaces saved-state text and bottom-row duplicate buttons. Smart Playlist detail header gains Play All IconButton (replaces "Use as Up Next") and collapsible metadata panel with FlowRow filter chips (collapse/expand arrow with rotation animation). Settings screen: connection help collapsed behind info toggle, Bluesky verbose explanations trimmed, scheduler/source diagnostics collapsed behind show/hide toggle, scanner defaults collapsed by default, "Open candidate browser" and "Open Bluesky smart playlist" navigation buttons removed. `SettingsScreen` signature cleaned of now-dead `onOpenBlueskyBrowse`/`onOpenSmartPlaylist` params. Unit tests and assembleDebug both passed. Follow-ups (not in this ticket): filter chip editing/remove/add in smart playlist header, Bluesky candidate browser accessible from other entry points.
- [x] **Bluesky app-password connect/disconnect UI shipped** (PR #290, 2026-04-30): Settings → Bluesky now shows a connect form (handle + app password) when no account is connected, and a connected panel (handle, DID, mode, last-validation, Disconnect button) when an account is connected. Password field clears on success. All credential material goes to the Mimeo backend only; nothing stored locally. Existing scheduler/source diagnostics preserved. Build and unit tests passed.
- [x] **Hosting story v2 UX shipped**: Connection guidance is now HTTPS-first for Remote mode with explicit `.ts.net` preferred shape and raw Tailscale IP HTTP fallback-only messaging. Local/LAN/Remote copy now calls out emulator `10.0.2.2`, physical-device LAN `http://<LAN-IP>:8000` default, and remote Tailscale HTTPS preference. Endpoint validation now warns on obvious scheme/host mismatches (for example `https://100.x.x.x:8000`, `http://*.ts.net`, and physical-device loopback use), while preserving existing sign-in/manual-token and connection-test behavior.
- [x] **PR #284 reconciliation recorded (closed-unmerged metadata)**: PR #284 was closed in GitHub without merge metadata (`mergeCommit=null`). Its scrollbar/player ergonomics content was reconciled directly onto `main` via additive commit `45cda4c`. Post-reconciliation verification passed: Android build and unit tests succeeded.
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


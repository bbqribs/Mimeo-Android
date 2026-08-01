# Android Progress / Pointer Observability Contract

**Ticket:** `T-AND-PROGRESS-POINTER-OBSERVABILITY-CONTRACT-1`
**Status:** Decision-complete contract. **Docs only — authorizes no runtime implementation.**
**Date:** 2026-08-01
**Base:** `origin/main` at `de96b1d`

## 0. What this document is

This is the decision-complete contract for **one** bounded follow-up Android
implementation ticket: a local, read-only diagnostic surface that answers
"which progress/pointer value is the app actually using for the active item,
and where did it come from?".

It settles semantics, ownership, presentation shape, lifecycle, privacy
classification and acceptance requirements so that the implementation ticket
is execution-only. It does not itself change any behaviour.

### Authority and precedence

| Document | Role here |
|---|---|
| `ROADMAP.md` → cross-repo `PROJECT_COMPLETION_AND_GAPS_PLAN_2026_07.md` | Programme authority. Governs entry, parallelism and exclusions. |
| `docs/REDESIGN_V2_PLAN.md` §7 | Governing progress-model design authority. Not rewritten by this contract; one factual divergence from current code is recorded in §3 and §10 rather than edited into it. |
| `docs/ANDROID_TELEMETRY_PLAN.md` + Mimeo `docs/planning/TELEMETRY_PRIVACY_POLICY.md` | Governing privacy authority. This contract is subordinate to both. |
| `docs/planning/ANDROID_NOWPLAYING_POINTER_DESYNC_2026_07.md` | **Shipped implementation record.** Historical. Its "Cleanup round" pointer-ownership table is current rule and is restated, not reopened, in §4. |
| `docs/ANDROID_PROGRESS_MODEL.md` | Current-state audit. Corrected by this ticket where it is stale (§10). |

### Explicit non-authorizations

This contract does **not** authorize:

- runtime implementation (the follow-up ticket does that, under §11);
- any new telemetry event, ring-buffer entry, upload, or network request;
- any backend API, server parity, or cross-device History work;
- any change to pointer-mutation ownership;
- listening-duration analytics of any kind (§8);
- content-identifying diagnostics (§7).

### Roadmap entry basis

`ROADMAP.md` item 8 ("Progress / playback duration model. Pointer/progress
audit plus listening-time estimates") sits in the **superseded priority
snapshot**, which is explicitly *not* an executable queue. Re-entry must come
through a lane.

This document enters through **Lane 2 parallelism**, which permits Android
"local … audit" work that "does not invent server truth", executable in
parallel with Mimeo-side work. It takes only the *pointer/progress audit* half
of item 8. The *listening-time estimate* half is not taken and stays blocked
(§8). Nothing here depends on an unmerged backend contract, so the Lane 2
server-parity entry gate (`B1` + ratified move rules) is not engaged.

`docs/ANDROID_PROGRESS_MODEL.md` §7 already names this exact follow-up
("add debug-only surface showing queue progress vs session progress vs chunk
cursor for active item") as the audit's own recommended next step.

---

## 1. Evidence base

Every claim below was verified against the working tree at `de96b1d`.

**Code inspected**

- `app/src/main/java/com/mimeo/android/model/Models.kt`
  (`PlaybackQueueItem.progressPercent` / `.furthestPercent`, `ProgressPayload`)
- `app/src/main/java/com/mimeo/android/AppViewModel.kt`
  (`knownProgressForItem`, `knownFurthestForItem`, `postProgress`,
  `applyLocalProgress`, `applyLocalCompletionState`, `applySessionSnapshot`,
  `getPlaybackPosition`, `setPlaybackPosition`, `setReaderScrollOffset`,
  `getReaderScrollOffset`, `playReaderItem`,
  `reconcileSessionPointerToLivePlayback`, `applyAuthoritativeUpNext`,
  `shouldPlacePriorActiveInHistory`)
- `app/src/main/java/com/mimeo/android/MainActivity.kt`
  (`shouldReplayCompletedItem`, `classifyReaderPromoteRoute`,
  `resolveReaderPlaySessionOwner`, `engineCommittedToPlayback`,
  `livePlaybackReconcileKey`, `classifyLivePlaybackSessionSync`,
  `priorActiveShouldGoToHistory`)
- `app/src/main/java/com/mimeo/android/ui/player/PlayerScreen.kt`
  (`PlaybackObservabilityUiState`, `playbackObservabilityLines`,
  `resolveOpenStartSource`, `resolveSeededPlaybackPosition`,
  `resolveReaderInitialOffset`, reader-scroll `LaunchedEffect`,
  near-end forced `postProgress(item, 100)`, diagnostics-strip render site)
- `app/src/main/java/com/mimeo/android/ui/player/PlaybackEngine.kt`
  (`applyLoadedItem`, `syncProgress`, `maybeAutoPlayAfterLoad`)
- `app/src/main/java/com/mimeo/android/repository/PlaybackRepository.kt`
  (`StoredNowPlayingItem`, `postProgress`, `flushPendingProgress`,
  `enqueuePendingProgress`, `setCurrentReaderScrollOffset`,
  `setNowPlayingItemProgress`, `setNowPlayingItemCanonicalProgress`,
  `parseStoredNowPlayingHistory`, `encodeStoredNowPlaying`)
- `app/src/main/java/com/mimeo/android/data/SettingsStore.kt`
  (`readerScrollOffsetByItemJsonKey`, `playbackSegmentIndexByItemJsonKey`,
  `showPlaybackDiagnosticsKey`, clear-on-owner-change paths)
- `app/src/main/java/com/mimeo/android/data/entities/PendingProgressEntity.kt`
- `app/src/main/java/com/mimeo/android/playback/ActivePlaybackTimer.kt`
- `app/src/main/java/com/mimeo/android/ui/settings/SettingsScreen.kt`
  (`SettingsSection.DEVELOPER`, debug-only spoke, playback-diagnostics toggle)

**Tests inspected**

`PlaybackObservabilityTest`, `PlaybackOpenIntentTest`,
`CompletedReplayPolicyTest`, `LivePlaybackSessionSyncTest`,
`ReaderPlaySessionOwnerTest`, `ReaderPromoteRouteTest`,
`PriorActivePlacementPolicyTest`, `ActivePlaybackTimerTest`,
`PlaybackOwnerResolutionTest`, `PlaybackArchiveHistoryTest`,
`NoActiveContentStateTest`.

---

## 2. The six representations

These are distinct values. Conflating any two of them is the class of bug this
surface exists to make visible.

### 2.1 Canonical percent (`progressPercent`)

"Resume here" — where the user is *now*, 0–100.

- **Derived, not stored.** `PlaybackQueueItem.progressPercent` is a computed
  getter: `apiProgressPercent ?: resumeReadPercent ?: lastReadPercent ?: 0`,
  clamped to `>= 0`, then **capped at `furthestPercent`**
  (`Models.kt:397-402`).
- Read via `AppViewModel.knownProgressForItem(itemId)`, which reads
  `queueItems` only (`AppViewModel.kt:6695-6697`).
- Authoritative source is the server projection in `queueItems`; local writes
  are optimistic overlays (`applyLocalProgress`, `applyLocalCompletionState`).

### 2.2 Furthest percent (`furthestPercent`)

"High-water mark" — the monotonic ceiling.

- Also a computed getter:
  `apiFurthestPercent ?: lastReadPercent ?: (apiProgressPercent ?: resumeReadPercent ?: 0)`,
  clamped to `>= 0` (`Models.kt:404-408`).
- Read via `knownFurthestForItem(itemId)`.
- **Sole input to completion/replay classification**:
  `isItemCompletedForPlaybackStart` → `shouldReplayCompletedItem(furthest) = furthest >= 98`
  (`MainActivity.kt:343-345`, `AppViewModel.kt:6699-6705`). Canonical percent
  is *not* consulted for that decision.

### 2.3 Precise playback cursor (`chunkIndex`, `offsetInChunkChars`)

"Exactly where TTS is" within the chunked article.

- Live value: `AppViewModel._playbackPositionByItem`, read by
  `getPlaybackPosition(itemId)` with fallback order
  `in-memory → persisted segment index → PlaybackPosition()`
  (`AppViewModel.kt:6938-6942`).
- Written by `PlaybackEngine` on seek/advance/seed via
  `host.setPlaybackPosition(...)`.
- Persisted to DataStore under `playback_segment_index_by_item_json`, but
  **throttled**: a write only lands when the chunk changes, the offset goes to
  0, or the offset moves `>= 120` chars from the *previous live position*
  (`AppViewModel.kt:6949-6959` — note the baseline is
  `playbackPositionByItem`, i.e. the previous live value, **not** the last
  persisted one). Because `handleChunkProgressEvent` (`PlaybackEngine.kt:282-295`)
  fires per TTS range with small deltas, the `>= 120` branch effectively never
  trips mid-chunk; the persisted offset stays pinned at its last write until
  the chunk index changes. **The real lag is bounded by chunk length, not by
  120 characters.** No numeric lag ceiling may be stated to the user (§6.4).
- Not monotonic. Seeking backward moves it backward.

### 2.4 Reader scroll position (`readerScrollOffset`)

"Where the user is looking."

- The live value is `readerScrollOffsets`, a `rememberSaveable` map **local to
  the `PlayerScreen` composition** (`PlayerScreen.kt:1039`). It is not hoisted
  into the ViewModel and is therefore **not observable from outside Locus**.
- The durable value is `AppViewModel.getReaderScrollOffset(itemId)`
  (`AppViewModel.kt:6999-7006`), which resolves persisted DataStore state
  (`reader_scroll_offset_by_item_json`) and falls back to the session item's
  `readerScrollOffset`. This already-collapsed value is what
  `PlayerScreen.kt:1046-1052` passes as the *persisted* argument to
  `resolveReaderInitialOffset(inMemory, persisted, session)`, so the three-way
  resolution in that function is not fully recoverable after the fact.
- The durable value is written only when the offset moves `>= 24` px from the
  last persisted value (`PlayerScreen.kt:1602-1612`), so it lags the live
  viewport.
- **Does not drive canonical percent** — see §3, divergence D1.

### 2.5 Session-local progress (`NowPlayingSessionItem.lastReadPercent`)

The Up Next session's own cached copy of progress for its members.

- Stored in Room as JSON (`StoredNowPlayingItem`), alongside per-item
  `chunkIndex`, `offsetInChunkChars`, `readerScrollOffset`.
It has **three** writers with **three different merge rules** — this is the
single most confusable part of the model:

| Path | Rule | Code |
|---|---|---|
| Local progress apply (`setNowPlayingItemProgress`) | **Monotonic**: `maxOf(existing, clamped)` | `PlaybackRepository.kt:1356` |
| Explicit completion/reset (`setNowPlayingItemCanonicalProgress`) | **Direct assign**, non-monotonic | `PlaybackRepository.kt:1381` |
| Queue refresh (`reconcileSessionWithQueue`) | **Direct assign of the raw nullable `last_read_percent`**: `lastReadPercent = refreshed.lastReadPercent` | `PlaybackRepository.kt:1435` |

The queue-refresh path is *not* the `furthestPercent`-preferring merge. That
rule (`item.furthestPercent ?: item.lastReadPercent ?: lastReadPercent`,
`PlaybackRepository.kt:1961`) lives in `mergeAuthoritative`, whose only caller
is the **authoritative Up Next server apply** at `PlaybackRepository.kt:1064` —
a different path with a different input type (`UpNextSessionItem`).

Consequences that matter for the diagnostic surface:

- After a queue refresh, R5 holds the server's raw `last_read_percent`, which
  is **not** the same quantity as R1 (`apiProgressPercent ?: resumeReadPercent
  ?: lastReadPercent`, capped at R2). Comparing them naively produces routine
  false divergences — see §6.4.
- R5 can be `null` after reconcile, where R1 floors at `0`.

- **This is a cache, never authoritative.** It can legitimately disagree with
  `queueItems` between a queue refresh and the next session write.

### 2.6 Committed Now Playing pointer (`session.currentIndex` / `currentItem`)

Which item the Up Next session says is active. Ownership is §4.

---

## 3. Source-of-truth matrix

Legend for **Authority**: *Authoritative* = the value other code should
believe; *Derived* = computed from others on read; *Cache* = a copy that may
lag; *Local-only* = never leaves the device in this form.

| # | Representation | Writer(s) | Reader(s) | Persistence | Authority | Update trigger | Stale-state risk | Permitted diagnostic exposure |
|---|---|---|---|---|---|---|---|---|
| R1 | Canonical percent `progressPercent` | Server (`queueItems` load); `applyLocalProgress`; `applyLocalCompletionState` | Queue/library rows, `knownProgressForItem`, seek slider, sort-by-progress | None directly (derived from persisted queue fields) | **Derived** from server fields, capped by R2 | Queue load/refresh; successful or optimistic `postProgress`; done/reset | Optimistic local value survives a failed post; capped silently by R2 so a lower displayed value may not be the raw server value | **Yes** — value, plus which source field won (`progress_percent` / `resume_read_percent` / `last_read_percent` / default), plus whether the R2 cap applied |
| R2 | Furthest percent `furthestPercent` | Same as R1 | `knownFurthestForItem`; `shouldReplayCompletedItem`; done-suffix labels | None directly (derived) | **Derived**; monotonic ceiling | Same as R1 | Stale queue makes an item look not-done and re-open as `ManualOpen` rather than `Replay` | **Yes** — value, source field, and the resulting `>= 98` done verdict |
| R3 | Playback cursor `chunkIndex` / `offsetInChunkChars` (live) | `PlaybackEngine` via `host.setPlaybackPosition` | `PlaybackEngine.play/seek`, seeding, highlight overlay, `postProgress` payload | In-memory `_playbackPositionByItem` | **Authoritative for playback position** while the engine owns the item | Chunk advance, seek, seed on `applyLoadedItem` | None while engine is live; falls back to R4/R5 when the engine has no entry | **Yes** — chunk index and char offset (both are positions, not content) |
| R4 | Playback cursor (persisted segment) | `SettingsStore.savePlaybackSegmentIndex` via `setPlaybackPosition` | `playbackPositionFromPersistedSegment` fallback in `getPlaybackPosition` | DataStore `playback_segment_index_by_item_json` | **Cache** | Throttled against the previous *live* position: chunk change, offset→0, or `>= 120` char move | **Lags the live cursor; the lag is bounded by chunk length, not by 120 chars** (§2.3) | **Yes** — value plus a "persisted; may lag live" marker and the observed live-vs-persisted delta. **No numeric lag ceiling may be stated.** |
| R5 | Session-cached progress `NowPlayingSessionItem.lastReadPercent` | `setNowPlayingItemProgress` (monotonic max, `:1356`); `setNowPlayingItemCanonicalProgress` (direct assign, `:1381`); `reconcileSessionWithQueue` (direct assign of raw `last_read_percent`, `:1435`); `mergeAuthoritative` on server Up Next apply (`:1961`, via `:1064`) | Playlist continuation, `shouldPlacePriorActiveInHistory`, session restore | Room `NowPlayingEntity` JSON | **Cache**; nullable | Local progress apply; done/reset; queue reconcile; authoritative Up Next apply | Holds a *different quantity* to R1 after queue reconcile (raw `last_read_percent` vs R1's capped fallback chain); monotonic local merge can hold a value above the server's after a server-side reset; may be `null` where R1 floors at `0` | **Yes** — value (or `unavailable` when null), and a divergence marker only under the narrowed §6.4 condition. **No "which writer last applied" attribution** — like R8, no such state exists and recording it would mean writing into a settled path |
| R6 | Session-cached cursor / scroll (`chunkIndex`, `offsetInChunkChars`, `readerScrollOffset` on the session item) | `setPlaybackPosition` → session write; `setCurrentReaderScrollOffset` | `applySessionSnapshot` seeds `_playbackPositionByItem`; `getReaderScrollOffset` fallback | Room `NowPlayingEntity` JSON | **Cache** | Same as R3/R7 | Restored on process start *before* the engine attaches, so it briefly *is* the effective value | **Yes** — values plus "from session cache" marker |
| R7 | Reader scroll offset (durable) | Reader `LaunchedEffect` → `setReaderScrollOffset` | `resolveReaderInitialOffset`, `postProgress` payload | DataStore `reader_scroll_offset_by_item_json` + session item | **Authoritative for viewport only** | Scroll settle; persisted only on `>= 24` px delta | Lags the live viewport; **the live value lives in `PlayerScreen`'s `rememberSaveable` state (`PlayerScreen.kt:1039`) and is not observable outside Locus** | **Yes** — the durable value from `getReaderScrollOffset` and whether it came from DataStore or the session item. The live tier renders `unavailable` with a "reader-local" note; it must **not** be obtained by hoisting `PlayerScreen` state. |
| R8 | Committed Now Playing pointer (`session.currentIndex`) | `reconcileSessionPointerToLivePlayback` (engine-commit); `repository.playNowInSession` (explicit adoption); `applyAuthoritativeUpNext` (server) | Up Next ordering, earlier/upcoming split, Locus title, `PlayerScreen.currentItemId` fallback | Room `NowPlayingEntity` | **Authoritative for "what is Now Playing"** | Engine commitment to play (§4); explicit Play Now; server Up Next sync | Server projection can disagree with the engine; `applyAuthoritativeUpNext` resolves by **clearing the engine** (`AppViewModel.kt:7285-7287`) | **Yes** — pointer item id and index, plus the *current* verdicts of the pure classifiers `resolveReaderPlaySessionOwner` and `classifyLivePlaybackSessionSync`. **No "last writer" attribution** — no such state exists and creating it would mean writing into the settled pointer paths (§6.3 A). |
| R9 | Engine current item + commitment | `PlaybackEngine` state machine | `engineCommittedToPlayback`, `livePlaybackReconcileKey`, `engineOwnsLivePlayback` | In-memory only | **Authoritative for "what is audible"** | `openItem`, `play`, `maybeAutoPlayAfterLoad`, `stop` | None; it is the ground truth the others chase | **Yes** — engine item id and the four commitment flags |
| R10 | Pending progress queue | `enqueuePendingProgress` on retryable post failure | `flushPendingProgress`, `pendingProgressCount`, sync badge | Room `pending_progress` (unique index on `itemId`) | **Pending** — not yet server truth | Retryable IO failure on `postProgress`; flush on reconnect | **Stores `itemId` + `percent` only. Chunk/offset/reader-scroll pointers are dropped and never resent** (`PendingProgressEntity.kt`, `PlaybackRepository.kt:850-856`). `PendingProgressDao` exposes **no `Flow`**, so any per-item read is a one-shot suspend snapshot | **Yes** — queued percent and attempt count for the active item, with a mandatory snapshot-time marker (§6.4), plus a fixed "pointer fields not queued" note. **`lastError` is excluded** (§7.2) |
| R11 | Open diagnostics snapshot (`lastOpenDiagnostics`) | `PlaybackEngine.applyLoadedItem` | Existing debug strip | In-memory only | **Derived** record of the last open decision | Every `applyLoadedItem` | Describes the last *open*, not the current position | **Out of scope for this surface.** Already fully exposed by the shipped Locus strip (§5.1) and deliberately not duplicated in §6.3 |
| R12 | Active-playback elapsed ms (`ActivePlaybackTimer`) | `updateActivePlaybackClock` off engine state | `shouldPlacePriorActiveInHistory` only | **Never persisted**, process-local | Internal input to one boolean | Engine play/pause/item change | Resets on active-item change by design | **No — excluded.** See §8. |

---

## 4. Pointer ownership (restated, not reopened)

Shipped and settled by PRs #475/#476. Verified present in current code at
`MainActivity.kt:403-414`, `AppViewModel.kt:6734-6760` and
`AppViewModel.kt:6805-6830`. The diagnostic surface **reports** this; it must
never influence it.

> **Playback commitment — not item preview, not item load — owns the Now
> Playing pointer transition.**

`engineCommittedToPlayback = autoPlayAfterLoad || isSpeaking || isAutoPlaying || hasStartedPlaybackForCurrentItem`
(`MainActivity.kt:443-448`).

| Where the item is | Who moves the pointer | Code |
|---|---|---|
| `session.items` or `session.historyItems` | `EngineCommitReconciler` — the engine-commit reconciler off `playbackEngineState` | `classifyLivePlaybackSessionSync` → `MoveToSessionItem` / `RestoreFromHistory` |
| Neither, but a session exists | `ExplicitAdoption` — `repository.playNowInSession` on `viewModelScope` | `playReaderItem` |
| No session at all | `NoSessionMutation` — nobody | `resolveReaderPlaySessionOwner` |
| Plain open / preview | `NoSessionMutation` — nobody | never calls `playReaderItem` |

**Contract rule:** the observability surface is strictly read-only. It may not
call `playReaderItem`, `playbackOpenItem`, `movePointerToSessionItem`,
`movePointerToHistoryItem`, `setPlaybackPosition`, `setReaderScrollOffset`,
`postProgress`, `toggleCompletion`, or any repository session mutation. It
subscribes to existing `StateFlow`s and computes nothing that is written back.

---

## 5. Diagnostic questions the surface must answer

### 5.1 What already exists

A debug-only **playback observability strip** ships today
(`PlayerScreen.kt:2563-2568`), gated on `BuildConfig.DEBUG &&
settings.showPlaybackDiagnostics`, toggled from the debug-only Settings →
Developer spoke. It emits five lines (`playbackObservabilityLines`):

```
item current=<id> requested=<id|none>
open_intent=<intent> auto_path=<bool>
start_source=<source> known_progress=<int>
seed chunk=<int> offset=<int>
handoff pending=<bool> settled=<bool>
```

It answers *how the last open was seeded*. It does **not** answer *which
representation is currently authoritative and whether any of them disagree*.
That gap is this contract's subject.

### 5.2 The questions the new surface must answer

For the **active item only**:

- **Q1 — Values.** What are R1–R9 right now, each labelled by representation?
- **Q2 — Provenance.** For each derived value, which source field won?
  (R1/R2: `progress_percent` vs `resume_read_percent` vs `last_read_percent`
  vs default. R3: live vs persisted-segment vs session vs default. R7: live vs
  persisted vs session.)
- **Q3 — Authority.** Which value is currently authoritative for
  (a) "what is audible", (b) "what Now Playing claims", (c) "resume position",
  (d) "done or not"?
- **Q4 — Divergence.** Do R1 and R5 disagree? Does R8 disagree with R9? Does
  R3 disagree with R4? Each divergence is reported as a divergence, never
  resolved (§6).
- **Q5 — Freshness.** How long since the last queue load and the last session
  write? *There is no existing per-item "last successful progress post"
  timestamp; `lastSuccessfulSyncAtMs` (`SettingsStore.kt:880-887`) is an
  account-scoped sync time and **must not** be relabelled as one. That row
  renders `unavailable` unless a real timestamp is added, which this contract
  does not authorize.*
- **Q6 — Pending / offline.** Is there a `pending_progress` row for this item?
  What percent and attempt count? Is `queueOffline` set? What is
  `ProgressSyncBadgeState`?
- **Q7 — Completion.** What does `shouldReplayCompletedItem(R2)` return, and
  therefore what `PlaybackOpenIntent` would a manual start produce right now?
- **Q8 — Pointer ownership.** What do the pure classifiers
  `resolveReaderPlaySessionOwner` and `classifyLivePlaybackSessionSync` say
  about the active item *right now*?
  **Not asked:** "which writer last moved the pointer". No such state exists,
  and recording it would require writing into
  `reconcileSessionPointerToLivePlayback`, `repository.playNowInSession` and
  `applyAuthoritativeUpNext` — the exact paths PRs #475/#476 settled, and the
  paths §11.2 forbids touching. The current classifier verdicts answer the
  operationally useful question ("who *would* own a move now") without any
  write. Historical attribution is deliberately given up.
- **Q9 — Restoration.** Were R3/R7 seeded from persisted state this process
  (i.e. has the engine attached yet)?

---

## 6. Presentation shape

Bounded, read-only, single-screen.

### 6.1 Scope

- **Active item only.** One item — the Now Playing pointer's item, or the
  engine's item when they disagree (in which case **both** are shown as a
  divergence, §6.4). No list, no history, no per-item browser. This bound is
  what keeps the surface from becoming a reading log.
- **Fixed field set.** Exactly the fields in §6.3. Adding a field requires
  amending this document (§12).

### 6.2 Placement

A new **Settings → Developer → Progress & pointer state** screen, as a spoke
off the existing debug-only Developer section. Not in Locus — the existing
strip already occupies that slot, and an in-player panel invites the
"resolve the divergence for me" affordance this contract forbids.

The existing Locus strip is **unchanged**. This is a second, separate surface.

### 6.3 Field set

Four sections, in this order:

**A. Identity & ownership**
- Engine item id (R9), engine commitment flags (4 booleans)
- Session pointer item id + index (R8)
- `resolveReaderPlaySessionOwner` verdict for the active item
- `classifyLivePlaybackSessionSync` verdict for the active item

*(No "last pointer writer" row — see Q8.)*

**B. Progress**
- R1 canonical percent + winning source field + whether the R2 cap applied
- R2 furthest percent + winning source field
- R5 session-cached percent (or `unavailable` when null)
- Done verdict: `furthest >= 98` → `true`/`false`, and the resulting manual-start intent

**C. Position**
- R3 live cursor `chunk` / `offset`
- R4 persisted-segment cursor `chunk` / `offset`, with lag marker + observed delta
- R6 session-cached cursor `chunk` / `offset`
- R7 durable reader offset from `getReaderScrollOffset`, marked DataStore or
  session; live tier renders `unavailable` ("reader-local, not observable
  outside Locus")

**D. Freshness & sync**
- Seconds since last queue load; seconds since last session write
- Last successful progress post: `unavailable` (see Q5)
- `queueOffline`, `ProgressSyncBadgeState`
- Pending-progress row for this item: present? percent, attempt count —
  rendered with a snapshot-time marker (§6.4)
- Fixed note: "Queued progress carries percent only; chunk, offset and reader
  scroll are not queued and are not resent."

Plus two pinned bands when non-empty (§6.4): **Divergences**, then
**Expected lag**.

### 6.4 Representing divergence

Divergence is **displayed, never resolved**. The surface must not pick a
winner, must not offer a "fix" or "resync" action, and must not reorder fields
to imply one is correct.

Each detected divergence renders as one row:

```
DIVERGENCE  <name>
  <label A> = <value A>   (<provenance A>)
  <label B> = <value B>   (<provenance B>)
```

**Two bands, not one.** A documented throttle is *lag*, not disagreement.
Rendering ordinary throttle behaviour under a "DIVERGENCE" heading is the same
error in the other direction — it presents expected staleness as a fault and
buries the one detector that matters. So:

**Band 1 — Divergences** (genuine disagreement between values that should agree):

| Name | Condition |
|---|---|
| `pointer-vs-engine` | R8 item id `!=` R9 item id, and R9 item id `> 0` |
| `canonical-vs-session` | R5 is non-null **and** R5 `>` R2 (the session cache claims more progress than the high-water mark). Deliberately *not* `R1 != R5`: after `reconcileSessionWithQueue` those two hold different quantities (§2.5), so plain inequality fires constantly on healthy state. |
| `canonical-vs-furthest` | Recompute the pre-cap value `raw = (apiProgressPercent ?: resumeReadPercent ?: lastReadPercent ?: 0).coerceAtLeast(0)` and fire when `raw > furthestPercent` — i.e. the cap at `Models.kt:401` actually fired. Testing `R1 > R2` is impossible: `minOf` makes R1 `<=` R2 by construction. |

**Band 2 — Expected lag** (documented throttle behaviour, informational):

| Name | Condition |
|---|---|
| `live-vs-persisted-cursor` | R3 `!=` R4. **Continuously true during playback** by §2.3; shown with the observed delta, never as a fault. |
| `reader-lag` | durable R7 `!=` the value it would take at the next persist threshold. Shown only when the `>= 24` px threshold has not yet been crossed. |

`pointer-vs-engine` is the one that reproduces the PR #475 symptom class. Note
it is a **legitimate transient** during a load→play handoff and a **tolerated
steady state** for an item outside the session (`LivePlaybackSessionSync.None`);
the surface labels it — including which of those two cases the classifier says
it is — and does not alarm on it.

### 6.5 Staleness rules (mandatory, all representations)

- Values that cannot be determined render as `unavailable`, **never as `0`**.
  This applies to nullable R5, the non-observable R7 live tier, the absent
  progress-post timestamp, and any absent pending row.
- Every value **not** sourced from a live `StateFlow` renders with a
  `snapshot HH:MM:SS` marker. This covers the pending-progress row, which has
  no `Flow` on `PendingProgressDao` and is necessarily a one-shot suspend read.
- Every value with documented lag (R4, R7) renders with its lag marker.
- **No numeric lag ceiling may be claimed** — §2.3 shows the obvious one is
  wrong. Show the observed delta instead.
- **A stale value must never be presented as current.**

---

## 7. Privacy classification

Subordinate to `docs/ANDROID_TELEMETRY_PLAN.md` and the Mimeo policy.

### 7.1 This surface is not telemetry

The telemetry plan §4.3 already states the governing distinction:

> per-user playback *progress* … is **not** telemetry. It is feature data …
> it must never be mirrored into the telemetry ring buffer.

This surface is a third category: **ephemeral on-device presentation of
in-memory feature state**. It is not telemetry, not an event, not a log, and
not an upload. Accordingly:

- It **must not** emit a telemetry event of any family.
- It **must not** write to the §5.3 ring buffer.
- It **must not** be attachable to a problem report.
- It **must not** appear in `Log.*` at any level, in any build type. (The
  telemetry plan §2 forbids release-only redaction precisely because it is too
  easy to forget.)
- It **must not** be passed to the local logging helpers the implementer will
  be sitting next to: `debugLog` (`PlayerScreen.kt:887`) and `continuationLog`
  (`PlayerScreen.kt:893`, which is an **ungated** `Log.d`). Copying local idiom
  is the realistic accidental-telemetry route here, so both are named
  explicitly. The new file contains **no** logging helper and **no** `Log.*`
  call.

### 7.2 Field exclusions (mandatory)

The surface renders **numbers, ids, enum names and booleans only**. The
following are excluded outright:

| Excluded | Why |
|---|---|
| Item **title** | Telemetry plan §2: title identity |
| Item **URL**, `host`, `sourceUrl`, `canonicalUrl` | URL leak |
| Article **text**, chunk text, paragraph text, highlighted sentence | Content |
| `sourceLabel`, `sourceType`, `captureKind`, `sourceAppPackage` | Source identity |
| Playlist name or Smart Queue seed label | Reading-choice payload |
| `voiceId`, TTS voice name | Not progress state |
| Any **list** of items, or session/History membership beyond the single active item | A list of what the user has been reading is a reading log |
| **Active-playback elapsed ms** (R12) | §8 |
| `PendingProgressEntity.lastError` | Free text truncated from `Throwable.message` (`PlaybackRepository.kt:1867-1871`); telemetry plan §4.4 — may carry URL or response text. Attempt **count** is permitted; the error string is not. |

**Numeric item ids are permitted on-screen.** They are already exposed by the
shipped strip (`item current=<id> requested=<id>`,
`PlayerScreen.kt:363`), they carry no content without server access, and
without them the divergence detectors are unreadable. The telemetry plan
forbids item ids *in telemetry events* — a category this surface is excluded
from by §7.1. This is the single deliberate boundary decision in this
contract; §12 governs changing it.

**Two residual, accepted exposures**, recorded rather than left unremarked:

1. Rendering canonical percent alongside chunk index and character offset lets
   a viewer derive an approximate article **character length**. That is a weak
   length fingerprint, not content. It is inherent to any surface that answers
   Q1, and it is accepted.
2. A **screenshot** of this surface carries the permitted item id. §7.3's
   screenshot row is therefore scoped to "safe apart from the permitted item
   id", not "safe".

### 7.3 Persistence, logging, transmission, retention

| Concern | Rule |
|---|---|
| **Persistence** | The surface persists **nothing**. It holds no state of its own beyond Compose recomposition state. The one permitted persisted item is the existing-pattern boolean toggle (§9), which stores no progress data. |
| **Logging** | No `Log.*` call may take any value rendered by this surface. |
| **Transmission** | No network call. No share/export/copy action — deliberately unlike `ConnectivityDiagnosticsExport`, because a copyable payload is an exfiltration path for item ids and a step toward problem-report attachment. |
| **Retention** | Zero. State is derived on recomposition from live flows and dies with the composition. Sign-out and account/owner change already clear the underlying stores; the surface inherits that with no additional work. |
| **Screenshots** | Out of scope to prevent. The §7.2 exclusions make a screenshot safe **apart from the permitted numeric item id**, which it will contain by design. |

---

## 8. Boundary against listening-duration analytics

**Hard exclusion.** This contract does not define, authorize, estimate,
display or persist listening time.

Verified current state: `ActivePlaybackTimer`
(`app/src/main/java/com/mimeo/android/playback/ActivePlaybackTimer.kt`) is a
process-local, monotonic, **never-persisted** accumulator of active playback
ms for the single tracked item. Its only consumer is
`shouldPlacePriorActiveInHistory` (`AppViewModel.kt:6129-6140`), which feeds
`priorActiveShouldGoToHistory(progressDelta, playedMs)`
(`AppViewModel.kt:7864-7865`):

```kotlin
!(progressDelta < 5 && playedMs < 30_000L)
```

That is the **30-second History classifier**. This contract:

- **does not redefine it** — the `< 5 %` AND `< 30 s` skip rule stands exactly
  as shipped;
- **does not expose `playedMs`** on the diagnostic surface (R12 excluded in
  §3 and §7.2). Rendering a live "seconds played" counter would make the app
  display listening duration in all but name, which is precisely the work that
  remains blocked;
- **does not infer listening time from progress.** Percent deltas are progress,
  not duration, and no field in §6.3 may be labelled or derived as time-listened;
- **does not authorize** the `ROADMAP.md` item 8 "listening-time estimates at
  1.0x and current-speed-adjusted playback" half. That remains **blocked
  pending separate definition**.

If the implementation ticket finds it cannot answer a §5.2 question without
`playedMs`, the correct outcome is to drop that question, not to expose the
timer.

---

## 9. Build and setting gating

**Both.** Debug-build-only **and** developer-setting-gated, matching the
shipped precedent exactly:

```kotlin
if (BuildConfig.DEBUG && settings.showProgressPointerDiagnostics) { ... }
```

- The **Developer** Settings section is already debug-only: it is filtered out
  of the hub (`SettingsScreen.kt:163`) and carries a defence-in-depth
  `BuildConfig.DEBUG` guard at its render site (`SettingsScreen.kt:1300-1302`).
  The entry point lives inside it.
- **The section guard does not transfer to a nav route.** Existing settings
  spokes are registered unconditionally in the shell — e.g.
  `composable(ROUTE_SETTINGS_DIAGNOSTICS) { ConnectivityDiagnosticsScreen(vm) }`
  (`MainActivityShell.kt:833-835`) — so a route registered the same way is
  reachable in a release build by `navigate` or deep link even though no UI
  offers it. The implementation ticket **must** therefore guard at **three**
  places, not one:
  1. the Developer-section entry row (inherits the existing guard);
  2. the `composable(...)` registration in `MainActivityShell.kt`, wrapped in
     `if (BuildConfig.DEBUG)`;
  3. the screen body's own `BuildConfig.DEBUG && settings.showProgressPointerDiagnostics`
     early return.
- The toggle is a new `SettingsStore` boolean, **default `false`**, following
  `showPlaybackDiagnostics` (`SettingsStore.kt:136`, `:260`, `:549`).
  Reusing `showPlaybackDiagnostics` is **not** permitted — the two surfaces
  must be independently switchable so enabling the Locus strip never silently
  enables this screen.
- Release builds must not contain a **registered route** to the screen — not
  merely no UI entry point. The implementation ticket must verify route
  absence, not just section absence (§11.4).

---

## 10. Findings: settled / ambiguous / divergent / product choice / blocked

### 10.1 Settled shipped behaviour (verified at `de96b1d`)

- **S1.** Pointer ownership is exactly the §4 table. `setNowPlayingCurrentItem`,
  `promoteReaderItemToNowPlaying` and `shouldMutateUpNextActiveItem` are absent
  from the tree. Confirmed by grep and by `ReaderPlaySessionOwnerTest` /
  `LivePlaybackSessionSyncTest`.
- **S2.** Completion/replay keys on `furthestPercent >= 98`, not canonical
  percent (`CompletedReplayPolicyTest`).
- **S3.** History is process-local. `parseStoredNowPlayingHistory` returns
  `emptyList()` unconditionally and `encodeStoredNowPlaying` deliberately never
  serialises History (`PlaybackRepository.kt:1883-1897`).
- **S4.** `postProgress` sends `chunk_index`, `offset_in_chunk_chars` and
  `reader_scroll_offset` when available, with a legacy percent-only retry on
  400/422 (`ApiClient.kt:853-880`).
- **S5.** A debug-only playback observability strip already ships (§5.1).
- **S6.** The 30-second History classifier is `priorActiveShouldGoToHistory`
  (§8), covered by `PriorActivePlacementPolicyTest`.

### 10.2 Detected implementation divergence

- **D1 — Silent reading does not update canonical percent on Android.**
  `docs/REDESIGN_V2_PLAN.md` §7 states: "Currently, silent reading (no TTS) in
  Locus does update progress via scroll position. This behavior should be
  preserved". **Current code does not do this.** The reader-scroll
  `LaunchedEffect` (`PlayerScreen.kt:1590-1614`) calls only
  `vm.setReaderScrollOffset(...)`. The only writers of canonical percent are
  `PlaybackEngine.syncProgress` (which requires a non-empty chunk list and
  computes percent from the *playback cursor*), the near-end forced
  `vm.postProgress(currentItemId, 100)` (`PlayerScreen.kt:1841`), and
  `toggleCompletion`. Scrolling an article without ever starting TTS leaves
  canonical percent unchanged.
  **Disposition:** recorded, not fixed. Whether silent reading *should* advance
  percent is a product choice (F1), not a defect this docs ticket may decide.
  `REDESIGN_V2_PLAN.md` is left unedited per the ticket's authority rule; the
  divergence is recorded here and in `ANDROID_PROGRESS_MODEL.md`.

- **D2 — `ANDROID_PROGRESS_MODEL.md` §2 is stale on manual-open precedence.**
  It states manual open seeds from `progressPercent` and that "Manual-open
  start position does not use session `lastReadPercent` or cached local
  playback cursor as an override." Current `resolveSeededPlaybackPosition`
  (`PlayerScreen.kt:383-413`) prefers the **saved playback cursor** whenever
  `hasSavedPlaybackPointer(...)` is true, falling back to
  `positionForPercent(knownProgress)` only when there is no saved pointer. The
  saved pointer comes from `host.getPlaybackPosition(...)`, which reads
  in-memory state seeded from the **session** cursor by `applySessionSnapshot`.
  **Disposition:** corrected in `ANDROID_PROGRESS_MODEL.md` by this ticket.

- **D3 — `ANDROID_PROGRESS_MODEL.md` §3 attributes progress sync to the wrong
  owner.** It names `PlayerScreen.syncProgress(...)`. `syncProgress` is
  private to `PlaybackEngine` (`PlaybackEngine.kt:527`); `PlayerScreen` has no
  such function.
  **Disposition:** corrected in `ANDROID_PROGRESS_MODEL.md` by this ticket.

- **D4 — Queued progress silently drops pointer fields.**
  `enqueuePendingProgress` stores only `itemId` + `percent`, and
  `flushPendingProgress` reposts percent-only. A progress post that fails
  offline therefore permanently loses its `chunk_index`,
  `offset_in_chunk_chars` and `reader_scroll_offset` for that sync.
  **Disposition:** recorded as a *known, intentional-looking* behaviour, not a
  defect ruling. It is not user-visible today. It is a first-class diagnostic
  field (§6.3 D) precisely because it is invisible. Fixing it is out of scope
  and would require schema work; if the operator later wants it, that is its
  own ticket.

### 10.3 Current ambiguity (recorded, not resolved here)

- **A1.** `PlaybackQueueItem.progressPercent` caps itself at `furthestPercent`
  (`Models.kt:401`). When the server sends `resume_read_percent >
  furthest_percent`, the app silently displays the lower value. Whether that
  cap should be visible to the user is undecided; the surface exposes whether
  it fired (§6.3 B).
- **A2.** Session `lastReadPercent` has **four** writers with three different
  merge rules (§2.5): monotonic max on ordinary progress (`:1356`), direct
  assign on completion/reset (`:1381`), direct assign of the raw nullable
  server `last_read_percent` on queue reconcile (`:1435`), and the
  `furthestPercent`-preferring `mergeAuthoritative` on the authoritative Up
  Next apply (`:1961` via `:1064`). The monotonic path can hold the cache
  above the server's value after a server-side reset; the reconcile path can
  push it below canonical or to `null`. No shipped behaviour depends on either
  today, which is why this is recorded as ambiguity rather than a defect.
- **A3.** Reader-offset resolution is stated in two places with different
  shapes: `getReaderScrollOffset` (`AppViewModel.kt:6999-7006`) collapses
  persisted → session internally, while `resolveReaderInitialOffset` takes
  three arguments and prefers live in-memory. The call site
  (`PlayerScreen.kt:1046-1052`) passes the already-collapsed value as the
  *persisted* argument, so the three-way resolution is not recoverable after
  the fact. §6.3 C therefore reports two observable tiers, not three.

### 10.4 Future product choice (not decided here)

- **F1.** Should silent reading advance canonical percent (closing D1 in the
  plan's favour), or should `REDESIGN_V2_PLAN.md` §7 be amended to match
  shipped behaviour? Operator decision. Either way it is a **behaviour**
  ticket, not this one.
- **F2.** Should the R1-capped-by-R2 case surface to the user at all (A1)?
- **F3.** Should any part of this diagnostic ever become non-debug? Default
  answer is no; changing it requires §12.

### 10.5 Blocked / server-dependent

- **B1.** Cross-device progress reconciliation diagnostics — requires server
  parity work gated behind Lane 2's `B1` decision. Out of scope.
- **B2.** Cross-device History — blocked on Mimeo
  `T-UPNEXT-HISTORY-CONTRACT-1`. Out of scope.
- **B3.** Listening-duration modelling (§8) — blocked pending separate
  definition. Out of scope.
- **B4.** Any assertion about what the *server* considers authoritative beyond
  what the existing `/playback/queue` and `/up-next/session` responses already
  carry. This surface reports the client's view only and must label it as such.

---

## 11. Implementation boundary for the follow-up ticket

**Proposed ticket:** `T-AND-PROGRESS-POINTER-OBSERVABILITY-1`

### 11.1 In scope

1. New `SettingsStore` boolean `showProgressPointerDiagnostics`, default
   `false`, wired through the existing combined-settings save path.
2. New debug-only Settings → Developer spoke: **Progress & pointer state**,
   with the toggle and the screen route.
3. New Compose screen rendering §6.3 sections A–D for the active item,
   read-only, from existing `StateFlow`s.
4. New pure, testable functions in a new file (no new module, no new
   dependency):
   - a snapshot builder mapping existing flows → a
     `ProgressPointerDiagnosticsUiState` data class;
   - a renderer producing labelled display rows (mirroring the shape of
     `playbackObservabilityLines`);
   - the three divergence detectors and two lag detectors from §6.4.
5. Three **read-only** accessors, each additive and each with no caller other
   than the new screen:
   - a `PendingProgressDao` `@Query` selecting the row for one `itemId`, plus a
     thin `PlaybackRepository` pass-through (the DAO today exposes only
     `listPending()` / `countPending()`);
   - exposure of the existing private `lastQueueLoadCompletedAtMs`
     (`AppViewModel.kt:469`) as a read-only value for Q5.
   These are reads. They add no writer to any representation in §3.
6. Unit tests per §11.4.

### 11.2 Out of scope (hard boundary)

- Any change to `PlaybackEngine`, `PlaybackRepository` session mutation,
  `AppViewModel` pointer logic, or the existing Locus strip. In particular:
  **no instrumentation inside `reconcileSessionPointerToLivePlayback`,
  `repository.playNowInSession` or `applyAuthoritativeUpNext`** — that is why
  Q8 gives up historical writer attribution.
- Hoisting `PlayerScreen`'s `readerScrollOffsets` state
  (`PlayerScreen.kt:1039`) to make the live reader offset observable. R7's
  live tier stays `unavailable`.
- Any new persisted field beyond the one boolean.
- Any share / copy / export action.
- Any network call, telemetry event, or `Log.*` line.
- Any behaviour fix for D1, D4, A1, A2 or A3.
- Any listening-duration work.

### 11.3 Expected files

| File | Change |
|---|---|
| `app/src/main/java/com/mimeo/android/ui/settings/ProgressPointerDiagnosticsScreen.kt` | **new** — screen + pure state/render/divergence functions |
| `app/src/main/java/com/mimeo/android/ui/settings/SettingsScreen.kt` | add the Developer-spoke entry + toggle row |
| `app/src/main/java/com/mimeo/android/data/SettingsStore.kt` | one new boolean key + accessor + combined-save param + clear paths |
| `app/src/main/java/com/mimeo/android/model/Models.kt` | one new field on the settings model |
| `app/src/main/java/com/mimeo/android/AppViewModel.kt` | settings plumbing (`saveShowProgressPointerDiagnostics` + the five known combined-save call sites) **plus** read-only exposure of `lastQueueLoadCompletedAtMs`; **no progress or pointer logic** |
| `app/src/main/java/com/mimeo/android/MainActivity.kt` | one route **constant** (route constants live here, `MainActivity.kt:230`) |
| `app/src/main/java/com/mimeo/android/MainActivityShell.kt` | the `composable(...)` **registration**, wrapped in `if (BuildConfig.DEBUG)` per §9 |
| `app/src/main/java/com/mimeo/android/data/dao/PendingProgressDao.kt` | one additive read-only `@Query` for a single `itemId` |
| `app/src/main/java/com/mimeo/android/repository/PlaybackRepository.kt` | one read-only pass-through accessor for that query; **no session mutation** |
| `app/src/test/java/com/mimeo/android/ui/settings/ProgressPointerDiagnosticsTest.kt` | **new** — unit tests |
| Existing `SettingsStore*Test.kt` | add the new boolean to the combined-save fixtures (`SettingsStoreAuthSessionTest.kt`, `SettingsStoreLocalStateOwnershipTest.kt`) |

Approximately **11** files, of which 2 are tests. The settings-boolean half of
this estimate was verified against the real call sites
(`SettingsStore.kt:136,260,352,425,549`; `Models.kt:724`;
`AppViewModel.kt:1248,1286,1338,1397,3549`;
`SettingsScreen.kt:287,417,1336`).

**Stop-and-report trigger:** if the implementation needs a twelfth file that is
not a test, or needs *any* edit to `PlaybackEngine.kt`, `PlayerScreen.kt`, or
the pointer-mutation paths named in §11.2, stop and report rather than
proceeding.

### 11.4 Acceptance requirements

**Automated (required, JVM unit tests only — no instrumented test):**

1. Each of the three divergence detectors and two lag detectors (§6.4): fires
   on the stated condition, silent on agreement, and correctly silent on the
   `unavailable` case. Specifically:
   - `canonical-vs-session` must be **silent** on a post-`reconcileSessionWithQueue`
     fixture where R5 holds a raw `last_read_percent` differing from R1 but not
     exceeding R2 — the false-positive case the narrowed condition exists to
     avoid;
   - `canonical-vs-furthest` must fire on a fixture with
     `resume_read_percent > furthest_percent`, proving the recomputed pre-cap
     path works (the post-cap comparison can never fire).
2. Provenance resolution: R1/R2 source-field selection over all four fallback
   orders; R3 over its three-tier fallback; R7 over its **two** observable
   tiers (DataStore, session) with the live tier asserted `unavailable`.
3. `unavailable` rendering: a missing value renders `unavailable`, never `0` —
   covering null R5, the R7 live tier, the absent progress-post timestamp, and
   an absent pending row.
4. Lag and snapshot markers: R4 and R7 always render their lag marker; the
   pending row always renders its snapshot-time marker; **no rendered string
   contains a numeric lag ceiling**.
5. **Exclusion test:** given a fully-populated fixture whose item carries a
   title, URL, host, source label, source app package and article text — and
   whose pending row carries a `lastError` string — assert the rendered row
   list contains none of those strings. This is the privacy regression lock.
6. **No-logging assertion:** a source-level test (or lint rule) asserting the
   new file contains no `Log.`, `println`, `debugLog` or `continuationLog`
   token.
7. Default-off: a fresh `SettingsStore` returns `false` for the new key.
8. Toggle independence: setting `showPlaybackDiagnostics` does not change
   `showProgressPointerDiagnostics`, and vice versa.
9. Read-only: the screen's composable takes no lambda that can mutate session,
   pointer or progress state (enforced by signature review + a test that the
   state builder is a pure function of its inputs).

**Manual (device) verification for the follow-up ticket:**

Classified as **required but low-risk, debug-build only**. Three checks:
(a) enable the toggle in a debug build, play an item, confirm the four sections
populate and no title/URL appears; (b) confirm the release build registers **no
route** to the screen — inspect the guarded `composable(...)` site, since a
release build offering no UI entry point is not evidence of route absence; (c)
confirm the shipped Locus playback-diagnostics strip is unaffected with the new
toggle both on and off. No backend call, no physical-device matrix, no
re-acceptance of pointer behaviour.

### 11.5 Sizing

| Attribute | Value |
|---|---|
| **Size** | Small–Medium. One new screen, one new settings boolean, two additive read-only accessors, ~11 files (2 of them tests), no new dependency, no behaviour change. |
| **Risk** | Low. Additive, debug-gated, read-only. The cross-cutting edits are the settings-plumbing call sites (mechanical, already covered by existing `SettingsStore` tests) and the triple build guard in §9, which is the one place a mistake would be user-visible in release — hence the explicit route-absence check in §11.4. |
| **Model / effort** | Per `AGENTS.md`, model selection follows Mimeo's canonical routing policy, model inventory and the live picker at execution time — **not** this document. This contract records only that the work is a bounded, mechanical, single-surface Android UI ticket with no architectural decisions left open, which is the input that routing needs. |
| **Dependencies** | None. No backend, no unmerged contract, no other Android ticket. Executable immediately after this contract merges. |
| **Parallelism** | Safe to run alongside Mimeo-side Lane 1/2/3 work. Conflicts only with another ticket editing `SettingsScreen.kt` or `SettingsStore.kt`. |

---

## 12. Change control

Changing any of the following requires amending this document in its own PR,
with the same fresh-context privacy review:

- the §3 matrix (adding, removing or re-classifying a representation);
- the §6.3 field set, the §6.4 detector lists (either band), or the §6.5
  staleness rules;
- the §7.2 exclusion list — **especially** the item-id boundary decision;
- the §8 listening-duration exclusion;
- the §9 gating (in particular, any proposal to make the surface non-debug, or
  to drop any of the three build guards);
- the §11.2 out-of-scope boundary — in particular, any proposal to instrument
  the pointer-mutation paths in order to restore "last writer" attribution.

Changes affecting the cross-component privacy stance must additionally update
`docs/ANDROID_TELEMETRY_PLAN.md` and, where the cross-component policy is
touched, Mimeo `docs/planning/TELEMETRY_PRIVACY_POLICY.md`.

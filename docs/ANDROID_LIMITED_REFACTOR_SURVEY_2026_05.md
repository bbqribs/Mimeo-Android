# Android Limited Refactor Survey — May 2026

**Date:** 2026-05-08
**Repo:** Mimeo-Android
**Base SHA:** `6389d5a` (main, in sync with origin/main)
**Working tree:** clean for tracked files (only untracked docs/media present)
**Status:** Survey only. No source changes proposed in this ticket.

## Context

Following the recent rounds of work on playback/session state, Save queue,
Smart Queue, Bluesky candidate browser, startup hydration, and telemetry
planning, several files have grown well beyond a comfortable size:

| File | Lines |
| --- | ---: |
| `app/src/main/java/com/mimeo/android/AppViewModel.kt` | 6197 |
| `app/src/main/java/com/mimeo/android/ui/queue/QueueScreen.kt` | 2452 |
| `app/src/main/java/com/mimeo/android/ui/settings/SettingsScreen.kt` | 2415 |
| `app/src/main/java/com/mimeo/android/ui/playlists/PlaylistDetailScreen.kt` | 1003 |
| `app/src/main/java/com/mimeo/android/MainActivityShell.kt` | 998 |
| `app/src/main/java/com/mimeo/android/ui/playlists/SmartPlaylistDetailScreen.kt` | 922 |
| `app/src/main/java/com/mimeo/android/ui/library/LibraryItemsScreen.kt` | 871 |
| `app/src/main/java/com/mimeo/android/ui/bluesky/BlueskyBrowseScreen.kt` | 729 |

This survey identifies bounded, ticket-sized refactors that preserve current
behaviour. Each candidate is sized to fit a single PR, with no Compose
redesign, no dependency upgrades, no package renames, and no API contract
changes.

---

## Refactor candidates

### R1. Extract `BlueskyStateHolder` from `AppViewModel`

- **Current pain:** ~19 `MutableStateFlow`s for Bluesky connection, browse,
  and candidate-picker state are interleaved with unrelated state in
  `AppViewModel.kt` (~lines 353–410) and mutated by ~11 suspend functions
  scattered across `AppViewModel.kt:1836–2115`. New Bluesky work has to
  navigate ~6k lines of unrelated code.
- **Proposed change:** Move the Bluesky-only state flows into a
  `BlueskyStateHolder` class owned by `AppViewModel`. Keep the public
  `StateFlow`s exposed from `AppViewModel` so screens are unaffected; the
  holder simply replaces the `private val _xxx = MutableStateFlow(...)`
  declarations.
- **Expected files:** `app/src/main/java/com/mimeo/android/state/BlueskyStateHolder.kt` (new),
  `AppViewModel.kt` (delete state vals, delegate to holder).
- **Risk:** Low. Pure state move, no behaviour change.
- **Test strategy:** Existing Bluesky unit/UI tests must continue to pass.
  Add a small unit test that constructs `BlueskyStateHolder` and verifies
  default state matches current `AppViewModel` defaults.
- **Independent?** Yes.

### R2. Extract `LibraryStateHolder` (queue/inbox/favorites/archive/bin)

- **Current pain:** ~15 state flows for the five primary list scopes
  (`AppViewModel.kt:268–342`) along with their per-scope `sort` and
  `searchQuery` flows are mixed with playback state. Adding a new scope
  field touches the middle of the file in five places.
- **Proposed change:** Group per-scope state into a `ScopeListState` data
  class and hold one `MutableStateFlow<ScopeListState>` per scope, owned by
  a `LibraryStateHolder`. Public `StateFlow` exposure on `AppViewModel`
  unchanged.
- **Expected files:** `app/src/main/java/com/mimeo/android/state/LibraryStateHolder.kt` (new),
  `AppViewModel.kt` (state moves only).
- **Risk:** Low–Medium. Care needed to preserve exact emission semantics
  (combining flows must produce equivalent `distinctUntilChanged` behaviour).
- **Test strategy:** Existing list-screen tests; add a unit test that
  exercises sort/search updates per scope.
- **Independent?** Yes (after R1, ideally — but they don't conflict).

### R3. Extract shared item-row Composable + action menu

- **Current pain:** `SessionStaticItemRow` (`QueueScreen.kt`),
  `PlaylistDetailRow` (`PlaylistDetailScreen.kt`), `SmartPlaylistItemRow`
  (`SmartPlaylistDetailScreen.kt`), and `LibraryQueueItemRow`
  (`LibraryItemsScreen.kt`) each implement nearly identical layout for
  thumbnail + title/subtitle + `[Play]` + `[⋮]` overflow. The overflow
  menus repeat the Save/Move/Archive/Delete actions in four places.
- **Proposed change:** Add `ui/common/ItemRow.kt` with `ItemRow(...)` and
  `ItemActionMenu(...)` composables. Each screen passes its own
  `List<ItemAction>` and trailing slot. No visual change; existing screens
  call the shared composable instead of inlining.
- **Expected files:** `ui/common/ItemRow.kt` (new), four screen files
  updated to use the shared row.
- **Risk:** Medium. Visual regressions are easy to introduce; preserve
  paddings/typography exactly. Keep test tags identical.
- **Test strategy:** Snapshot/instrumented checks if available; otherwise
  manual visual diff on each of the four screens (light + dark, RTL).
  Verify existing test tags resolve.
- **Independent?** Yes.

### R4. Extract Bluesky input validation helpers + composable wrappers

- **Current pain:** `normalizeBlueskyHandleInput` and
  `parseBlueskyListIdentifierInput` (in `BlueskyCandidateInput.kt`) are
  re-applied inline in `BlueskyBrowseScreen.kt:356–370` and again in the
  Bluesky block of `SettingsScreen.kt`. Sanitisation rules drift over time.
- **Proposed change:** Promote the two helpers into `util/bluesky/BlueskyInput.kt`
  (pure functions, easily unit-testable) and add thin composable wrappers
  `BlueskyHandleField` / `BlueskyListUriField` in `ui/bluesky/`. Replace
  inline `OutlinedTextField + remember + onValueChange { sanitize(...) }`
  call sites.
- **Expected files:** `util/bluesky/BlueskyInput.kt` (new),
  `ui/bluesky/BlueskyInputFields.kt` (new), `BlueskyBrowseScreen.kt`,
  `SettingsScreen.kt`, `BlueskyCandidateInput.kt` (call sites).
- **Risk:** Low. Pure-helper extraction.
- **Test strategy:** Add unit tests for the pure helpers covering handle
  forms (`@bsky.social`, raw handles, did: URIs) and list URI variants
  (`at://`, full URLs, malformed). Manual verification on the two screens.
- **Independent?** Yes.

### R5. Extract `SessionPlaybackCoordinator`

- **Current pain:** `AppViewModel.kt:5131–5289` contains the cluster
  `startNowPlayingSession`, `replaceNowPlayingSessionFromSnapshot`,
  `playAllFromSnapshot`, `playFromHereSnapshot`, `playNow`,
  `jumpToUpcomingSessionItem`, `jumpToHistorySessionItem`, `playNext`,
  `playLast`, `playLastBatch`, `playNextBatch`. These functions are
  cohesive but coupled to ~20 unrelated members.
- **Proposed change:** Define a `SessionPlaybackCoordinator` that takes the
  required dependencies (player, session repo, snapshot resolver) and
  exposes the eleven methods. `AppViewModel` keeps the public methods as
  thin delegates so call sites are unchanged.
- **Expected files:** `playback/SessionPlaybackCoordinator.kt` (new),
  `AppViewModel.kt` (move + delegate).
- **Risk:** Medium. Easy to drop a side effect (telemetry, state write)
  during the move. Keep one PR; do not also rename methods.
- **Test strategy:** Run existing playback unit tests + the
  instrumented smoke tests added in #310. Add a unit test on the new
  coordinator covering the snapshot variants.
- **Independent?** Yes.

### R6. Extract `PendingActionQueue` (manual save retries / item actions)

- **Current pain:** Pending-save and pending-action retry logic at
  `AppViewModel.kt:1466–1671` and `:2553–2572` (`queueFailedManualSave`,
  `enqueueAcceptedPendingSave`, `markAcceptedPendingSaveResolved`,
  `clearPendingManualSaves`, `removePendingManualSave`,
  `enqueuePendingItemAction`, `flushPendingItemActions`) is structurally
  independent from the rest of the ViewModel but lives inside it.
- **Proposed change:** Extract a `PendingActionQueue` class with the same
  seven methods and a `StateFlow` of pending entries. ViewModel holds an
  instance and exposes the existing public flows.
- **Expected files:** `actions/PendingActionQueue.kt` (new),
  `AppViewModel.kt` (delegation).
- **Risk:** Low–Medium. Watch for `viewModelScope` lifetime assumptions —
  pass the scope in, do not create a new one.
- **Test strategy:** New unit tests for queue/flush/clear semantics; rely
  on existing manual-save tests for end-to-end coverage.
- **Independent?** Yes.

### R7. Extract `BlueskyServiceCoordinator`

- **Current pain:** Eleven Bluesky network calls at
  `AppViewModel.kt:1836–2115` (`loadBlueskyBrowse`,
  `loadMoreBlueskyBrowse`, `setBlueskyBrowseSourceFilter`,
  `setBlueskyBrowseQuery`, `addBlueskyBrowsePin`, `removeBlueskyBrowsePin`,
  `loadBlueskyCandidatePicker`, `scanBlueskyCandidateSource`,
  `saveBlueskyCandidate`, `pinCurrentBlueskyCandidateSource`,
  `unpinBlueskyCandidateSource`) all wrap `ApiClient.bluesky*` calls and
  push results into the state holder from R1.
- **Proposed change:** Pull these into `bluesky/BlueskyServiceCoordinator.kt`
  taking `ApiClient` + `BlueskyStateHolder` as constructor deps.
- **Expected files:** `bluesky/BlueskyServiceCoordinator.kt` (new),
  `AppViewModel.kt`.
- **Risk:** Low if R1 lands first; Medium if attempted standalone.
- **Test strategy:** Existing Bluesky tests; add coordinator-level unit
  tests with a fake `ApiClient`.
- **Independent?** Best done **after R1**.

### R8. Extract `StartupRestorer`

- **Current pain:** The init block at `AppViewModel.kt:527–770` plus
  `runInitialPostSignInHydrationIfNeeded` and
  `continueInitialPostSignInHydration` (`:5745–5987`) implement first-load
  auth/queue/playlist/playback restoration. Failure paths are hard to
  trace because they're intermixed with ongoing-state mutations.
- **Proposed change:** Encapsulate one-shot startup logic in a
  `StartupRestorer` invoked from `init`. Coordinator emits a
  `StateFlow<StartupPhase>` so UI/diagnostics can observe progress
  uniformly.
- **Expected files:** `startup/StartupRestorer.kt` (new),
  `AppViewModel.kt`.
- **Risk:** Medium. Startup ordering is subtle; preserve sequencing
  exactly. Keep this PR focused — no behavioural changes.
- **Test strategy:** Add a unit test exercising the `StartupPhase`
  transitions with fake repos. Manual verification: cold start signed-out,
  cold start signed-in, sign-in flow.
- **Independent?** Yes (does not require R1/R2).

### R9. Extract `SessionSourcePresentation` helpers

- **Current pain:** `resolveSessionSeedSourcePresentation`,
  `shouldConfirmReseedFromCurrentSource`, and `resolveQueueSourceLabel`
  (`QueueScreen.kt:160–210`) duplicate label/branch logic also present in
  `PlaylistDetailScreen.kt` and `SmartPlaylistDetailScreen.kt`.
- **Proposed change:** Move pure helpers to
  `ui/common/SessionSourcePresentation.kt` returning a small `SourceLabel`
  data class. Composable call sites collapse to a single function call.
- **Expected files:** `ui/common/SessionSourcePresentation.kt` (new),
  three screen files.
- **Risk:** Low.
- **Test strategy:** Add unit tests covering each label branch.
- **Independent?** Yes.

### R10. Consolidate routes into a sealed `AppRoute` graph

- **Current pain:** Route string constants live in `MainActivity.kt:211–224`,
  while the `NavHost` body in `MainActivityShell.kt:415–798` is a 383-line
  block with inline imports. Adding/removing a route touches both files
  in three places.
- **Proposed change:** Add `navigation/AppRoute.kt` (sealed class with
  `route: String` and argument helpers) and a `navigation/NavGraph.kt`
  with a single `NavGraphBuilder.appGraph(...)` extension that registers
  every route. `MainActivityShell.kt` just calls `appGraph(...)` inside
  `NavHost`. No behaviour change; no drawer redesign.
- **Expected files:** `navigation/AppRoute.kt` (new),
  `navigation/NavGraph.kt` (new), `MainActivity.kt`,
  `MainActivityShell.kt`, `MimeoDrawerContent.kt` (route refs).
- **Risk:** Medium. Many call sites; pure mechanical rename of strings to
  `AppRoute.X.route`.
- **Test strategy:** App must build and all navigations must work. Manual
  click-through of every drawer entry + deep-link routes.
- **Independent?** Yes.

### R11. Extract `ShareRefreshObserver`

- **Current pain:** Share-refresh telemetry (`AppViewModel.kt:726–745` for
  collection, `:2501–2547` for `recordShareRefreshSignal`,
  `recordShareRefreshSkip`, `recordShareRefreshExecution`) is a small
  self-contained subsystem mixed into the ViewModel.
- **Proposed change:** Pull into `telemetry/ShareRefreshObserver.kt`
  consuming `ShareRefreshBus` and exposing the dedup/snapshot helpers via
  `StateFlow`. Clears space for further telemetry observers in the same
  package.
- **Expected files:** `telemetry/ShareRefreshObserver.kt` (new),
  `AppViewModel.kt`.
- **Risk:** Low.
- **Test strategy:** Unit test for dedup-by-burst-key behaviour.
- **Independent?** Yes.

### R12. Promote queue-load + offline-ready resolution into `QueueLoadCoordinator`

- **Current pain:** `loadQueue`, `loadMoreQueueItems`,
  `applySavedQueueSnapshot`, `resolveOfflineReadyIds`,
  `resolveOfflineReadyIdsForSession`, `resolveOfflineReadyIdsForItemIds`
  (`AppViewModel.kt:2407–2431, 2667–2828, 5696–5745`) form a coherent
  pagination + offline-resolution subsystem.
- **Proposed change:** Extract `QueueLoadCoordinator` taking the queue
  repo, cache repo, and `LibraryStateHolder` (R2). Methods retain their
  signatures.
- **Expected files:** `queue/QueueLoadCoordinator.kt` (new),
  `AppViewModel.kt`.
- **Risk:** Medium. Pagination/race semantics must be preserved exactly.
- **Test strategy:** Existing queue tests; add a coordinator-level test
  with a fake repo simulating partial pages.
- **Independent?** Best done **after R2**.

---

## Tests-related observations

- Pure-helper extractions (R4 Bluesky input, R9 source presentation, R11
  share-refresh dedup) unblock straightforward unit tests that today
  require either a Compose harness or a full `AppViewModel`.
- The instrumented smoke-test foundation added in #310 (`startup`,
  `media-session`, `Up Next`) is the right safety net before R5/R8/R12;
  expand only as those land.
- No fragile tests were observed that need a refactor purely for
  testability today — the wins come from new tests, not unstucking old
  ones. Test-tag coverage on the four duplicated row composables is
  inconsistent; R3 is a good moment to standardise tags.

---

## Recommended first three implementation tickets

1. **T-001 — R4: Extract Bluesky input validation helpers + composable
   wrappers.**
   Smallest, lowest-risk change that delivers visible duplication
   reduction in three call sites and adds genuinely useful unit tests.
   Good warm-up that doesn't conflict with anything else.

2. **T-002 — R1: Extract `BlueskyStateHolder` from `AppViewModel`.**
   Unblocks R7 and removes the most painful single block in
   `AppViewModel.kt`. Mechanical state move, low risk, contained PR.

3. **T-003 — R3: Extract shared item-row Composable + action menu.**
   Highest-leverage UI change: collapses ~four near-identical row
   implementations and standardises test tags. Independent of T-001/T-002.

These three can be merged sequentially with no cross-dependency. Subsequent
tickets (R2 → R12 → R7, then R5/R6/R8/R10/R11 in any order) flow
naturally from there.

---

## Explicit "do not do" list

The following are **out of scope** for this refactor track. Any of them
should be its own dedicated ticket with operator approval, not bundled
into the bounded refactors above.

- **No Compose redesign.** No layout, typography, spacing, or interaction
  changes. Refactors must be pixel-equivalent.
- **No package renames or moves of files for "tidiness".** New files only
  in their new packages; existing files stay where they are.
- **No dependency upgrades** (Kotlin, AGP, Compose, Coroutines, AndroidX,
  Media3, Hilt). The recent W1–W4 build modernization is enough for now.
- **No DI rework.** Do not introduce Hilt modules where none exist; do not
  swap manual wiring for DI in this track.
- **No multi-module split.** The `:app` module stays single. Any module
  extraction needs a separate plan.
- **No backend/API contract changes** of any kind. Survey is Android-only.
- **No reformatting of unrelated files.** Do not run a project-wide
  `ktlint`/`detekt`/`gradle spotlessApply`. PRs must show only the lines
  touched by the refactor.
- **No "while I'm here" cleanups.** Do not delete dead code, fix typos in
  unrelated files, or rename variables outside the moved code.
- **No history rewrites.** Additive commits only, per `CLAUDE.md`.
- **No simultaneous merges across Mimeo + Mimeo-Android.** Per
  multi-agent workflow rules.
- **No new public API surface for the sake of "future flexibility".**
  Coordinators and state holders should be `internal` and stay so.
- **No telemetry/event-name changes** while extracting `ShareRefreshObserver`.
  Schema is frozen for that PR.
- **No introduction of a "ViewModel base class"** to share extracted
  helpers. Compose them into `AppViewModel` directly.

---

## R1.2 closeout — AppViewModel Bluesky boundary (2026-05-08)

**Ticket:** R1.2 — Remove residual Bluesky ViewModel glue and document boundary
**Baseline:** R1 (`BlueskyStateHolder`), R1.1 (`BlueskyServiceCoordinator`) both merged.
**Finding:** No residual glue found. AppViewModel's Bluesky responsibility is already
minimal after R1 + R1.1. No source changes were required.

### What remains in AppViewModel — and why it is intentional

| Element | Lines | Reason kept |
| --- | ---: | --- |
| 9 Bluesky model imports | 104–112 | Required by method signatures and explicit StateFlow return types |
| `BlueskyStateHolder` / `BlueskyServiceCoordinator` imports | 155–156 | Required for instantiation |
| `blueskyState` + `blueskyCoordinator` init | 353–361 | Owner; wires `onCandidateSaved → loadQueue` callback that needs ViewModel scope |
| 28 `StateFlow` delegation properties | 362–389 | Stable public API surface consumed by Compose screens — cannot remove without touching all call sites |
| 14 method delegation stubs | 1705–1718 | Same reason; UI calls `viewModel.loadBlueskyBrowse()` etc. |
| `blueskyCoordinator.resetOnSignOut()` | 595 | Correctly placed in auth lifecycle managed by AppViewModel |
| `blueskyCoordinator.refreshBlueskyStatus()` | 601 | Same |

None of these are shims, dead code, or duplicated derivations. They are the thin
adapter layer that keeps Compose call sites stable while all actual Bluesky logic
lives in `BlueskyServiceCoordinator` + `BlueskyStateHolder`.

### What is NOT in AppViewModel (moved by R1/R1.1)

- All 28 `MutableStateFlow` declarations → `BlueskyStateHolder`
- All Bluesky network calls (connect, disconnect, browse, candidate scan/save/pin) → `BlueskyServiceCoordinator`
- Error-message helpers → file-private in `BlueskyServiceCoordinator.kt`

### Next Bluesky refactor candidate

The StateFlow delegation block (lines 362–389) is the only remaining verbosity.
A future ticket could expose `blueskyState` directly as `internal val` and have
Compose screens read `viewModel.blueskyState.blueskyBrowseItems` — but this would
touch every Bluesky screen and is **not** worth doing until the Compose layer is
being redesigned anyway (Redesign v2). Leave for that track.

### AppViewModel current line count

`AppViewModel.kt` is now **5 753 lines** (down from 6 197 at survey time),
a reduction of ~444 lines from R1 + R1.1.

---

## Gates verified for this survey

- Docs-only deliverable: this file.
- No Kotlin/source changes.
- No unrelated docs/media staged by this ticket.
- `git diff --check` clean (no whitespace errors in this single new doc).
- Local tracked tree was clean for tracked files before work started.
- `main` is in sync with `origin/main` at `6389d5a`.

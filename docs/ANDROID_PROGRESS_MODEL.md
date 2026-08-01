# Android Progress Model (Current-State Audit)

This document describes what the Android app currently does for queue/progress/playback as shipped, and where that behavior matches or diverges from the current Android policy docs.

> **Currency note (2026-08-01, `T-AND-PROGRESS-POINTER-OBSERVABILITY-CONTRACT-1`).**
> Sections 2, 3 and 7 were re-verified against `main` at `de96b1d` and corrected
> where they had gone stale; the corrections are marked inline. The settled
> semantics, ownership and diagnostic contract now live in
> `docs/ANDROID_PROGRESS_POINTER_OBSERVABILITY_CONTRACT.md`, which is the
> authority for the progress/pointer observability follow-up. This file remains
> a current-state audit only.

## Audit Inputs

Code inspected (primary):
- `app/src/main/java/com/mimeo/android/ui/player/PlayerScreen.kt`
- `app/src/main/java/com/mimeo/android/MainActivity.kt`
- `app/src/main/java/com/mimeo/android/ui/queue/QueueScreen.kt`
- `app/src/main/java/com/mimeo/android/repository/PlaybackRepository.kt`

Focused tests inspected:
- `app/src/test/java/com/mimeo/android/ui/player/PlaybackOpenIntentTest.kt`
- `app/src/test/java/com/mimeo/android/CompletedReplayPolicyTest.kt`
- `app/src/test/java/com/mimeo/android/NoActiveContentStateTest.kt`

## 1) Progress Concepts In Use

### Queue-facing percentages
`PlaybackQueueItem` exposes:
- `progressPercent`
- `furthestPercent`

Used by:
- Queue row status display (`QueueScreen.kt`)
- Done/in-progress filtering (`QueueScreen.kt`)
- Completed replay detection path (`MainActivity.kt`)

### Precise player cursor
`PlaybackPosition` stores:
- `chunkIndex`
- `offsetInChunkChars`

Used by:
- Locus playback cursor and seek
- Resume start (depending on open intent)

### Session-side progress cache
`NowPlayingSessionItem` stores:
- `lastReadPercent`
- `chunkIndex`
- `offsetInChunkChars`

Used by:
- Playlist-scoped next-item continuation
- Restoring active item/session between screens/app restarts

### Completion threshold
Current done threshold is `98` (`DONE_PERCENT_THRESHOLD`) in:
- `MainActivity.kt`
- `PlayerScreen.kt`
- `QueueScreen.kt`

Done is threshold-derived for most UI behavior, plus explicit done/reset API actions.

## 2) Source of Truth Mapping

### For playback start position
Effective precedence depends on open intent:
- `ManualOpen`: seed from queue/item `progressPercent` (`knownProgressForItem`) and fall back to beginning when unknown/zero
- `AutoContinue`: force beginning `(0,0)`
- `Replay`: force beginning `(0,0)`

Implemented in `resolveSeededPlaybackPosition(...)` (`PlayerScreen.kt`).

### For `knownProgress`
`knownProgressForItem(itemId)` (`AppViewModel.kt:6695`) currently returns:
- `queueItem.progressPercent` only

**Corrected 2026-08-01.** This section previously stated that "manual-open start
position does not use session `lastReadPercent` or cached local playback cursor
as an override." That is no longer true of shipped code. `resolveSeededPlaybackPosition`
(`PlayerScreen.kt:383-413`) prefers the **saved playback cursor** whenever
`hasSavedPlaybackPointer(...)` holds, and falls back to
`positionForPercent(knownProgress)` only when there is no saved pointer. The saved
pointer is `host.getPlaybackPosition(itemId)` (`AppViewModel.kt:6938`), whose
fallback order is in-memory position → persisted segment index → zero, and whose
in-memory map is seeded from the **session** item's `chunkIndex`/`offsetInChunkChars`
by `applySessionSnapshot`. So the cached cursor — session-derived — does take
precedence over the queue percent on manual open.

`knownProgress` still governs the no-saved-pointer case and the `Replay`
below-threshold check.

### For completed/replay detection
`isItemCompletedForPlaybackStart(itemId)` (`MainActivity.kt`) currently checks:
- `shouldReplayCompletedItem(knownFurthestForItem(itemId))`
- `knownFurthestForItem(itemId) = queueItem.furthestPercent`

So completed/replay detection is based on queue furthest progress.

## 3) Update Flow Summary

### Player movement / seek
`PlayerScreen` updates playback cursor through `vm.setPlaybackPosition(...)`.

Direction:
- local state -> persisted now-playing session

### Progress posting
**Corrected 2026-08-01.** The owner is the engine, not the screen: `syncProgress` is
private to `PlaybackEngine` (`PlaybackEngine.kt:527`); `PlayerScreen` has no such
function. The path is
`PlaybackEngine.syncProgress(...)` -> `host.postProgress(...)` -> `AppViewModel.postProgress(...)`
-> `PlaybackRepository.postProgress(...)` -> `ApiClient.postProgress(...)`.

The other two writers of canonical percent are the near-end forced
`vm.postProgress(currentItemId, 100)` in `PlayerScreen.kt:1841` and
`toggleCompletion`.

Direction:
- app -> backend, with pending retry queue fallback on retryable IO failures.

Payload notes (current):
- Always sends canonical `percent`.
- Also sends pointer metadata when available:
  - `chunk_index`
  - `offset_in_chunk_chars`
  - `reader_scroll_offset`
- If backend rejects pointer fields (400/422), Android retries the same request with legacy percent-only payload for compatibility.
- **The pending-progress fallback does not preserve pointer metadata.**
  `PendingProgressEntity` stores `itemId` + `percent` only, and
  `flushPendingProgress` reposts percent-only (`PlaybackRepository.kt:850-856`).
  A post that fails offline therefore loses its `chunk_index`,
  `offset_in_chunk_chars` and `reader_scroll_offset` for that sync.

### Done/reset actions
`vm.toggleCompletion(...)` -> repository -> backend done/reset endpoint.

Direction:
- app -> backend, then local queue/session projection.

### Queue refresh and session reconcile
Queue reload updates queue items and reconciles session metadata (`reconcileSessionWithQueue(...)`) without replacing chunk/offset cursor state.

**Clarified 2026-08-01.** `reconcileSessionWithQueue` (`PlaybackRepository.kt:1413-1445`)
direct-assigns the raw nullable server `last_read_percent` into the session item
(`lastReadPercent = refreshed.lastReadPercent`, line 1435). It is *not* the
`furthestPercent`-preferring merge — that rule
(`item.furthestPercent ?: item.lastReadPercent ?: lastReadPercent`, line 1961)
lives in `mergeAuthoritative`, whose only caller is the authoritative Up Next
server apply at line 1064. The session cache therefore has four writers with
three different merge rules: monotonic max on local progress (line 1356),
direct assign on completion/reset (line 1381), raw direct assign on queue
reconcile (line 1435), and the furthest-preferring merge on authoritative apply
(line 1961).

Direction:
- backend -> app

## 4) Playback Behavior As Shipped

### Manual open
Manual open starts from queue/item `progressPercent`-derived position, and starts at beginning when queue progress is unknown/zero.

Status vs policy note: matches current policy note.

### Autoplay continuation
When item A ends in playlist-scoped session context, app resolves next item by session order and opens it with intent `AutoContinue`, forcing start at beginning.

Status vs policy note: matches current policy note.

### Replay of completed item
If opened item is considered completed (`>=98`), open intent becomes `Replay`, forcing start at beginning.

Status vs policy note: matches current policy note.

### Non-playlist context
If no playlist-scoped session context exists, continuation does not advance via playlist sequencing; current non-playlist behavior remains unchanged.

Status vs policy note: matches stated scope constraints.

## 5) No-active-content Handling Relevant To Progress

For rows where backend reports `409 No active content` during offline-ready fetch:
- app classifies the state (`isNoActiveContentError`, `isNoActiveContentAttempt`)
- queue row is rendered with distinct unavailable-offline treatment
- those rows are not treated as ordinary offline-ready misses in hydration targeting

Status vs contract: aligns with recent queue/offline behavior tickets.

## 6) Match / Divergence / Ambiguity Matrix

### Matches (implemented and covered)
- Manual open resume precedence implemented and tested (`PlaybackOpenIntentTest`).
- AutoContinue starts next item from beginning implemented and tested.
- Replay starts completed item from beginning implemented and tested.
- Completed threshold behavior (`98`) covered by `CompletedReplayPolicyTest`.
- No-active-content classification/message covered by `NoActiveContentStateTest`.

### Ambiguities to keep explicit
- Done/replay is tied to queue furthest threshold, not a dedicated completed flag in Android playback state.

### Divergence from policy docs (added 2026-08-01)
- **Silent reading does not update canonical percent.** `docs/REDESIGN_V2_PLAN.md` §7
  states that silent (no-TTS) reading in Locus updates progress from scroll position
  and that the behavior should be preserved. Shipped code does not do this: the
  reader-scroll effect (`PlayerScreen.kt:1590-1614`) calls only
  `vm.setReaderScrollOffset(...)`, and every canonical-percent writer is either
  playback-cursor-derived or an explicit completion action. Scrolling an article
  without ever starting TTS leaves canonical percent unchanged. Whether to close
  this in the plan's favour or amend the plan is an open product choice, recorded
  as F1 in `docs/ANDROID_PROGRESS_POINTER_OBSERVABILITY_CONTRACT.md` §10.4.

## 7) Recommended Follow-up Tickets (No Behavior Change In This Audit)

1. `Android progress observability: add debug-only surface showing queue progress vs session progress vs chunk cursor for active item`.
   **Now specified.** This recommendation is settled by
   `docs/ANDROID_PROGRESS_POINTER_OBSERVABILITY_CONTRACT.md`
   (`T-AND-PROGRESS-POINTER-OBSERVABILITY-CONTRACT-1`, 2026-08-01), which defines
   the source-of-truth matrix, presentation shape, privacy classification and the
   bounded implementation ticket `T-AND-PROGRESS-POINTER-OBSERVABILITY-1`. This
   audit no longer generates that ticket on its own.

These are policy/observability follow-ups; this audit intentionally does not change runtime behavior.

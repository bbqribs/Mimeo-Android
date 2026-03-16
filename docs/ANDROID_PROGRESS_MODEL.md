# Android Progress Model (Current-State Audit)

This document describes what the Android app currently does for queue/progress/playback as shipped, and where that behavior matches or diverges from the current Android policy docs.

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
- `ManualOpen`: use saved chunk/offset unless it is zero, then seed from `knownProgress`
- `AutoContinue`: force beginning `(0,0)`
- `Replay`: force beginning `(0,0)`

Implemented in `resolveSeededPlaybackPosition(...)` (`PlayerScreen.kt`).

### For `knownProgress`
`knownProgressForItem(itemId)` (`MainActivity.kt`) currently returns:
- `max(queueItem.progressPercent, sessionItem.lastReadPercent)`

This is a merged source, not strictly "session wins" or "backend wins".

### For completed/replay detection
`isItemCompletedForPlaybackStart(itemId)` (`MainActivity.kt`) currently checks:
- `shouldReplayCompletedItem(knownFurthestForItem(itemId))`
- `knownFurthestForItem(itemId) = max(queueItem.furthestPercent, sessionItem.lastReadPercent)`

So completed detection is based on merged furthest-like signals.

## 3) Update Flow Summary

### Player movement / seek
`PlayerScreen` updates playback cursor through `vm.setPlaybackPosition(...)`.

Direction:
- local state -> persisted now-playing session

### Progress posting
`PlayerScreen.syncProgress(...)` -> `vm.postProgress(...)` -> `PlaybackRepository.postProgress(...)` -> `ApiClient.postProgress(...)`.

Direction:
- app -> backend, with pending retry queue fallback on retryable IO failures.

### Done/reset actions
`vm.toggleCompletion(...)` -> repository -> backend done/reset endpoint.

Direction:
- app -> backend, then local queue/session projection.

### Queue refresh and session reconcile
Queue reload updates queue items and reconciles session metadata (`reconcileSessionWithQueue(...)`) without replacing chunk/offset cursor state.

Direction:
- backend -> app

## 4) Playback Behavior As Shipped

### Manual open
Manual open resumes existing cursor when present; otherwise seeds from known percent.

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

### Divergence or risk candidates
- Policy phrasing in `ANDROID_AUTOPLAY_RESUME_POLICY.md` says session progress should beat backend queue progress for manual open.
- Current shipped `knownProgressForItem(...)` uses `max(session, queue)`.
- In most real cases this is equivalent to "take furthest seen", but it is not the same rule and should be treated as a follow-up decision.

### Ambiguities to keep explicit
- Done/replay is tied to merged percent signals and threshold, not a dedicated completed flag in Android playback state.
- If backend and session values drift, `max(...)` can bias toward older higher progress even when user might expect latest-position semantics.

## 7) Recommended Follow-up Tickets (No Behavior Change In This Audit)

1. `Android progress policy follow-up: decide manual-open precedence when session and queue percent disagree`.
2. `Android progress observability: add debug-only surface showing queue progress vs session progress vs chunk cursor for active item`.

These are policy/observability follow-ups; this audit intentionally does not change runtime behavior.

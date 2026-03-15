# Android Progress Model

This document describes how article progress is currently represented and used in the Android app. It is based on the current code in the Android repo, not intended product behavior.

## Scope

This document covers the progress-related model used by:
- Up Next queue rendering
- Locus playback and resume behavior
- local now-playing session persistence
- progress sync and offline queueing
- done / reset-done behavior

It does not attempt to describe backend internals beyond the fields and endpoints the Android app actually consumes.

## 1. Current-state inventory

The Android app currently uses several different progress-like concepts.

### Queue/API-derived progress fields

`PlaybackQueueItem` in `app/src/main/java/com/mimeo/android/model/Models.kt` carries these backend-facing fields:
- `resumeReadPercent`
- `lastReadPercent`
- `apiProgressPercent`
- `apiFurthestPercent`
- `lastOpenedAt`

It also exposes two computed properties used throughout the UI:
- `progressPercent`
- `furthestPercent`

Current computation in `PlaybackQueueItem`:
- `progressPercent` prefers `apiProgressPercent`, then `resumeReadPercent`, then `lastReadPercent`, then `0`
- `progressPercent` is clamped to never exceed `furthestPercent`
- `furthestPercent` prefers `apiFurthestPercent`, then `lastReadPercent`, then `apiProgressPercent`, then `resumeReadPercent`, then `0`

Practical interpretation today:
- `progressPercent` is the app's "current/resume-ish" percentage for queue display
- `furthestPercent` is the app's "max reached" percentage for queue display and done filtering

### In-session playback position

`PlaybackPosition` in `app/src/main/java/com/mimeo/android/model/PlaybackPosition.kt` stores:
- `chunkIndex`
- `offsetInChunkChars`

This is finer-grained than queue percent values. It is the player's current cursor within chunked article text.

Related helpers in `PlaybackPosition.kt`:
- `calculateCanonicalPercent(...)`
- `absoluteCharOffset(...)`
- `positionFromAbsoluteOffset(...)`

These convert between chunk/offset position and a canonical 0-100 percentage.

### Now Playing session progress

`NowPlayingSessionItem` in `app/src/main/java/com/mimeo/android/repository/PlaybackRepository.kt` stores:
- `lastReadPercent`
- `chunkIndex`
- `offsetInChunkChars`

`NowPlayingSession` stores:
- ordered session item list
- current item index
- source playlist context

This session is persisted locally and is currently the main local source of truth for:
- current item within a playback session
- chunk/offset playback position per item
- per-item session-side last-read percent snapshot

### Done/completed state

The app does not appear to use a separate boolean done flag in Android playback state.

Instead, "done" is inferred from percent thresholds:
- `DONE_PERCENT_THRESHOLD = 98` in multiple UI/player files
- queue filters in `QueueScreen.kt` treat `furthestPercent >= 98` as done
- player UI in `PlayerScreen.kt` treats `effectivePercent >= 98` as completed

Completion can also be explicitly set/reset through:
- `toggleCompletion(...)` in `AppViewModel`
- `markItemDone(...)` / `resetItemDone(...)` in `ApiClient`

### Pending/offline progress sync state

Local queued progress writes are stored in:
- `PendingProgressEntity` in `app/src/main/java/com/mimeo/android/data/entities/PendingProgressEntity.kt`

Fields:
- `itemId`
- `percent`
- `createdAt`
- `attemptCount`
- `lastAttemptAt`
- `lastError`

This is not a playback position model. It is an offline sync queue for progress POSTs.

### Last opened / current item / session context

Other state that affects progress consumption:
- `lastOpenedAt` comes from backend queue payload but is not the main playback-resume source in Android
- `currentItemId` in `PlayerScreen.kt` is the currently displayed/playing item
- `currentNowPlayingItemId()` in `AppViewModel` exposes the current session item
- `sourcePlaylistId` in `NowPlayingSession` preserves the queue/playlist context the session was launched from

## 2. Source-of-truth mapping

### `progressPercent` / `furthestPercent` on queue rows

Source:
- backend queue payload fields, normalized by `PlaybackQueueItem`

Stored locally:
- in-memory `_queueItems` state in `AppViewModel`
- serialized queue snapshot through `SettingsStore.saveQueueSnapshot(...)`

Persistence across restarts:
- yes, indirectly via saved queue snapshot and fresh queue reloads

Local-only or mirrored:
- mirrored from backend, but can be locally updated optimistically by Android via `applyLocalProgress(...)` and `applyLocalCompletionState(...)`

### `PlaybackPosition` (`chunkIndex`, `offsetInChunkChars`)

Source:
- local player state in Android

Stored locally:
- in-memory `_playbackPositionByItem` map in `AppViewModel`
- persisted inside `NowPlayingEntity.queueJson` through `PlaybackRepository.setCurrentPlaybackPosition(...)`

Persistence across restarts:
- yes, as part of persisted `now_playing` Room state

Local-only or mirrored:
- local-only; Android does not send chunk index or character offset to the backend

### `NowPlayingSessionItem.lastReadPercent`

Source:
- initially seeded from queue item `lastReadPercent` in `PlaybackRepository.startSession(...)`
- later updated locally by Android through `setNowPlayingItemProgress(...)` and `setNowPlayingItemCanonicalProgress(...)`
- also refreshed from queue data in `reconcileSessionWithQueue(...)`

Stored locally:
- persisted inside `NowPlayingEntity.queueJson`

Persistence across restarts:
- yes

Local-only or mirrored:
- effectively mirrored-and-local-cache. It starts from backend-derived queue data, then Android mutates it locally between queue refreshes.

### Pending progress queue

Source:
- Android only, when `postProgress(...)` fails with retryable IO failure

Stored locally:
- Room table `pending_progress`

Persistence across restarts:
- yes

Local-only or mirrored:
- local-only queue for future app -> backend sync

### Done/completed state

Source:
- inferred from percentages in most UI paths
- explicitly updated through backend endpoints `/done` and `/reset`

Stored locally:
- reflected into `_queueItems` by `applyLocalCompletionState(...)`
- reflected into now-playing session through `setNowPlayingItemCanonicalProgress(...)`

Persistence across restarts:
- yes, once backend queue state is reloaded; also yes locally in now-playing session state

Local-only or mirrored:
- mirrored from backend but also locally projected immediately after toggles

## 3. Update flow

## Queue load / backend -> app

Primary flow:
- `PlaybackRepository.loadQueueAndPrefetch(...)`
- `AppViewModel.loadQueue...` paths in `MainActivity.kt`

What gets updated:
- `_queueItems`
- cached/offline-ready ids
- queue snapshot
- now-playing session metadata via `repository.reconcileSessionWithQueue(queue.items)`

Direction:
- backend -> app

Notable detail:
- `reconcileSessionWithQueue(...)` refreshes each session item's `title`, `url`, `host`, `status`, `activeContentVersionId`, and `lastReadPercent`
- it does not replace the session's chunk/offset positions

## Manual reading / seeking inside Locus

Primary flow in `PlayerScreen.kt`:
- player maintains `currentPosition` from `_playbackPositionByItem`
- seek and playback callbacks call `vm.setPlaybackPosition(itemId, chunkIndex, offset)`

What gets updated:
- `_playbackPositionByItem` immediately in memory
- persisted now-playing session chunk/offset via `repository.setCurrentPlaybackPosition(...)`

Direction:
- local state only

## Active playback progress posting

Primary flow:
- `syncProgress(...)` in `PlayerScreen.kt`
- calls `vm.postProgress(currentItemId, percent)`
- `AppViewModel.postProgress(...)`
- `PlaybackRepository.postProgress(...)`
- `ApiClient.postProgress(...)` -> `POST /items/{id}/progress`

What gets updated on success:
- backend progress via `/progress`
- local queue row via `applyLocalProgress(...)`
- local now-playing session `lastReadPercent` via `setNowPlayingItemProgress(...)`

Direction:
- app -> backend
- then local app state is updated optimistically/locally

Retry behavior:
- if `PlaybackRepository.postProgress(...)` hits retryable IO failure, it enqueues `PendingProgressEntity`
- `ProgressSyncWorker` and `flushPendingProgress()` later retry those POSTs

## Finishing an item during playback

Primary flow in `PlayerScreen.kt`:
- end-of-chunk done events go through `applyDoneTransition(...)`
- near-end crossing also triggers `vm.postProgress(currentItemId, 100)` through `shouldForceNearEndCommit(...)`

What gets updated:
- progress may be posted as 100%
- local queue/session progress may be updated through the same post-progress path
- autoplay continuation may move to the next session item

Direction:
- app -> backend for percent sync
- local session/item handoff in Android

Important nuance:
- completion is not one single code path. The player has both:
  - end-of-item transition handling
  - near-end forced canonical progress posting

That split makes the model harder to reason about.

## Explicit mark done / undo done

Primary flow:
- `vm.toggleCompletion(...)`
- `repository.toggleCompletion(...)`
- `ApiClient.markItemDone(...)` or `ApiClient.resetItemDone(...)`

What gets updated locally after success:
- queue row through `applyLocalCompletionState(...)`
- session item `lastReadPercent` through `setNowPlayingItemCanonicalProgress(...)`

Direction:
- app -> backend
- then local mirrored state update

Important nuance:
- Android sets:
  - canonical percent to `100` when marking done
  - canonical percent to `97` when undoing done
  - `resumePercent` for undo comes from current player percent
- so "not done but nearly complete" is represented explicitly by local/backend percent values, not a separate state flag

## Opening an item from queue/playlist

Primary flow:
- `QueueScreen.kt` calls `vm.startNowPlayingSession(startItemId, orderedQueueItems = displayedItems)`
- this seeds the session using the current visible queue order
- navigation opens Locus
- `PlayerScreen.kt` load path resolves where to start playback

Direction:
- local/session state only at first; item text fetch is backend -> app

## Background sync / worker behavior

Relevant files:
- `ProgressSyncWorker.kt`
- `WorkScheduler.kt`

Behavior:
- worker loads saved settings and token
- calls `repository.flushPendingProgress(...)`
- retryable failures lead to `Result.retry()`
- `401` is treated as stale-token stop condition and returns success without retry storm

Direction:
- app -> backend, background flush of previously queued progress only

## 4. Playback/resume behavior today

This is the most important practical section.

### What value decides where an item starts?

In `PlayerScreen.kt`, after item text loads:
- `saved = vm.getPlaybackPosition(currentItemId)`
- `knownProgress = vm.knownProgressForItem(currentItemId)`

Then Android computes `seeded` as:
- if `saved.chunkIndex == 0` and `saved.offsetInChunkChars == 0` and `knownProgress > 0` and there are chunks,
  - start from `positionForPercent(knownProgress)`
- else,
  - start from the saved chunk/offset position

So current startup precedence is effectively:
1. local chunk/offset playback position, if non-zero
2. otherwise a percent-derived resume position from `knownProgress`
3. otherwise start at beginning

### What is `knownProgress`?

`knownProgressForItem(...)` in `AppViewModel` takes:
- queue row `progressPercent`
- session item `lastReadPercent`
- returns the max of the two

So the player does not use only backend queue state and does not use only local session state. It uses the maximum of both.

### Manual item open

Current behavior:
- if there is saved chunk/offset position for that item, Android resumes from that exact local position
- otherwise, if queue/session progress is non-zero, Android resumes from the percent-derived location
- otherwise it starts from the beginning

### Autoplay continuation

Current behavior after the just-merged continuation work:
- the next item is selected from the now-playing session order
- Android sets that next item's chunk/offset position to `(0, 0)` via `vm.setPlaybackPosition(nextId, 0, 0)`
- then the next item load path runs the same seed logic described above

Practical result today:
- if the next item has no prior known progress, it starts at the beginning
- if the next item has prior known progress but no non-zero saved chunk/offset, it resumes from that prior percentage

This was confirmed by runtime logs during the Locus continuation work.

### Replaying an item

Current behavior:
- pressing play from an item whose saved position is already at the end can trigger restart-from-start behavior inside `onPlayPause` in `PlayerScreen.kt`
- if the player is at the last chunk and offset is at/after chunk length, it resets to `(0,0)` before playing

### Opening an item with prior progress

Current behavior:
- it will resume from prior progress unless there is a more specific persisted chunk/offset or the item has zero known progress

### Ambiguity note

The implementation is understandable but not especially simple.

Main reasons:
- queue progress fields are normalized from multiple backend fields
- local chunk/offset is a different model than queue percent
- now-playing session also stores `lastReadPercent`
- completion is partly threshold-driven and partly explicit done/reset API-driven

So "where playback should start" is currently a policy assembled from multiple partially overlapping values, not one obvious source of truth.

## 5. Backend interaction actually used by Android

Progress-related backend endpoints consumed by Android today:
- `GET /items/{id}/text` via `ApiClient.getItemText(...)`
- `POST /items/{id}/progress` via `ApiClient.postProgress(...)`
- `POST /items/{id}/done` via `ApiClient.markItemDone(...)`
- `POST /items/{id}/reset` via `ApiClient.resetItemDone(...)`
- queue/list endpoints that return `PlaybackQueueItem` payloads with progress fields

Progress-related backend fields Android consumes from queue/article payloads:
- `resume_read_percent`
- `last_read_percent`
- `progress_percent`
- `furthest_percent`
- `last_opened_at`

Important repo-grounded observation:
- Android currently consumes several backend progress-like fields and normalizes them client-side rather than trusting one canonical server field for all UI/playback decisions.

## 6. Known tensions / decision points

Short list of concrete policy questions created by the current model:
- Should autoplay continuation always start the next item from the beginning, or resume prior progress?
- Should manual open and autoplay continuation use the same resume policy?
- Should "done" items reopen at the beginning, at 97%, or at the exact last local playback position?
- Should queue display progress and player resume progress be driven by one canonical value instead of multiple merged values?
- Should playlist/queue context affect resume policy?

## 7. Recommended next investigation

Smallest next step:
- write a short policy note or bounded ticket specifically for "autoplay continuation start position"

Recommended bounded follow-up ticket:
- `Android — define and implement next-item autoplay resume policy`

Why this is the right next step:
- the continuation bug is fixed
- the remaining behavior question is now policy, not plumbing
- the code already proves that autoplay currently inherits the normal resume model for previously-progressed items

If deeper cleanup is later desired, the next larger investigation would be:
- reduce the number of competing progress sources used by the player (`queue progress`, `session lastReadPercent`, `chunk/offset`)

## Summary

Current Android progress behavior is workable but layered:
- queue/UI progress is percentage-based and normalized from several backend fields
- actual playback cursor is local chunk/offset position
- now-playing session stores both ordered playback context and per-item local progress snapshots
- offline progress sync is a separate pending-write queue
- autoplay continuation now works, but it currently reuses the same resume rules as ordinary item open

That last point is the most likely source of future product decisions.

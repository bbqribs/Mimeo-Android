# Android Autoplay / Resume Policy (Contract + Shipped Audit)

This note records the intended policy and current shipped status for playback start-position behavior.

It builds on `docs/ANDROID_PROGRESS_MODEL.md` and is the contract reference for bounded playback tickets.

## Contract Rules

### Rule A: Manual open (user-initiated)
- Resume from item/queue `progressPercent`.
- Manual open does not use cached local playback cursor or session `lastReadPercent` as the start-position source.

### Rule B: Autoplay continuation (automatic)
- When moving from item A to next item B automatically, B starts from beginning.

### Rule C: Replay of completed item
- Replaying a completed item starts from beginning.

### Rule D: Ordering
- Continuation ordering follows the active launched list/session order (playlist or Smart Queue session ordering), not a separate autoplay order.

## Shipped Status (Audit)

### A. Manual open resume: SHIPPED
Implemented in `PlayerScreen.resolveSeededPlaybackPosition(...)` for `PlaybackOpenIntent.ManualOpen`.

### B. Autoplay start-from-beginning: SHIPPED
Implemented in `resolveSeededPlaybackPosition(...)` for `PlaybackOpenIntent.AutoContinue`.

### C. Replay completed starts-from-beginning: SHIPPED
Implemented in `resolveSeededPlaybackPosition(...)` for `PlaybackOpenIntent.Replay`.

### D. Playlist/session ordering continuation: SHIPPED
Implemented through now-playing session sequencing (`AppViewModel.nextPlaylistScopedSessionItemId(...)`) and consumed by `PlayerScreen` done handling.

## Current Intent Model

`PlaybackOpenIntent` (`PlayerScreen.kt`) is the explicit selector:
- `ManualOpen`
- `AutoContinue`
- `Replay`

This intent is currently the key branch that prevents autoplay/replay from inheriting old resume positions.

## Focused Test Coverage

- `app/src/test/java/com/mimeo/android/ui/player/PlaybackOpenIntentTest.kt`
  - manual open uses queue progress seeding
  - manual open disagreement case (cached/session higher than queue still uses queue progress)
  - AutoContinue starts at beginning
  - Replay starts at beginning

- `app/src/test/java/com/mimeo/android/CompletedReplayPolicyTest.kt`
  - done threshold (`98`) used for completed replay decision

## Current Contract Status

Manual-open precedence is now settled and aligned:
- queue/item `progressPercent` is the manual-open source of truth for start position.
- autoplay/replay intent-based start-from-beginning behavior remains unchanged.

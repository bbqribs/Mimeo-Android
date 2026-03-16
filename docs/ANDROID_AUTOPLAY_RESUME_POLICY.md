# Android Autoplay / Resume Policy (Contract + Shipped Audit)

This note records the intended policy and current shipped status for playback start-position behavior.

It builds on `docs/ANDROID_PROGRESS_MODEL.md` and is the contract reference for bounded playback tickets.

## Contract Rules

### Rule A: Manual open (user-initiated)
- Resume from prior progress using existing manual-open precedence.

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
  - manual open percent seeding
  - manual open exact saved cursor preservation
  - AutoContinue starts at beginning
  - Replay starts at beginning

- `app/src/test/java/com/mimeo/android/CompletedReplayPolicyTest.kt`
  - done threshold (`98`) used for completed replay decision

## Known Policy Tension (Still Open)

Manual-open precedence wording in earlier notes can be read as:
- session progress preferred over backend queue progress.

Current implementation for `knownProgressForItem(...)` is:
- `max(session.lastReadPercent, queue.progressPercent)`.

This is close in practice but not the same as strict source precedence. Keep this as a separate policy decision; do not fold it into autoplay tickets by accident.

## Recommended Next Implementation Slice

Smallest bounded follow-up after this contract audit:

`Android — manual-open precedence refinement when session progress and queue progress disagree`

Scope for that ticket:
- decide strict precedence rule
- adjust `knownProgressForItem(...)` only if policy explicitly approved
- keep autoplay/replay behavior unchanged

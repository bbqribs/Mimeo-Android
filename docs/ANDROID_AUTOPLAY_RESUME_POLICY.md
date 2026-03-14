# Android Autoplay / Resume Policy

This document defines the intended playback start-position rules for Android. It builds on [ANDROID_PROGRESS_MODEL.md](ANDROID_PROGRESS_MODEL.md), which describes current behavior. This document is a product policy note, not a description of what the code does today.

## Scope

This policy covers how the app should decide where playback starts when `PlayerScreen.kt` opens an item for playback, regardless of whether the current code in `AppViewModel.knownProgressForItem(...)`, `PlayerScreen.kt`, or `PlaybackRepository` already behaves that way.

It applies to:
- manual open from Up Next, playlist, or other list context
- autoplay continuation to the next item
- replaying a completed item
- opening an item with prior progress

It does not redefine queue ordering, progress sync transport, or completion thresholds.

## Policy Summary

The app should treat `user-initiated open` and `automatic continuation` as different intents.

- User-initiated open means: the user tapped an item or otherwise explicitly chose it.
- Automatic continuation means: playback reached the end of the current item and the app advanced on its own.

Policy decision:
- User-initiated open should resume from the best available prior progress.
- Automatic continuation should start the next item from the beginning.
- Replaying a completed item should start from the beginning.

This is the smallest policy split that matches current user expectations without redesigning the progress model.

## 1. Desired behavior by context

### Manual open of item with prior progress

Desired behavior:
- Resume from the best available prior position.
- If the app has an exact local playback cursor, use it.
- Otherwise, resume from the best known percentage-derived position.

Reasoning:
- A manual tap is an explicit request to continue that item.
- The user is choosing the item itself, not asking the app to advance a listening session blindly.

Examples:
- If the user stopped mid-article yesterday, tapping that item today should reopen near that point.
- If only backend queue progress exists and no local chunk/offset exists, the app should derive a start position from that percentage.

### Autoplay continuation to next item

Desired behavior:
- Always start the next item from the beginning.
- Do not inherit prior resume progress for the next item.
- Do not reuse old local chunk/offset state for the next item.

Reasoning:
- Continuation represents "play the next article in sequence," not "resume whatever prior state this article happened to have."
- This is especially important in playlist and Smart Queue listening, where continuity is about ordered traversal, not revisiting prior progress.

Examples:
- If item A finishes and item B had previously been opened to 70%, autoplay should start item B at 0%.
- If item B was previously completed, autoplay should still start item B at 0% rather than near the end.

### Replay of completed item

Desired behavior:
- Start from the beginning.
- Completed state should not auto-resume near the end.

Reasoning:
- Replay is semantically different from resume.
- Starting near 98-100% on a replay makes the action feel broken.

### Opening item from playlist context

Desired behavior:
- Playlist context should control item order, not playback start position policy.
- If the user manually opens an item from a playlist, resume that item from the best available prior position.
- If the app auto-continues within that playlist, start the next item from the beginning.

Reasoning:
- Playlist context answers "what comes next."
- It should not, by itself, change whether the chosen item resumes or restarts.

### Opening item from Smart Queue / ad hoc context

Desired behavior:
- Use the same start-position rules as playlist context.
- Smart Queue and ad hoc list contexts may differ in ordering rules, but not in resume policy.

Reasoning:
- The important distinction is user action versus automatic continuation, not playlist versus Smart Queue.

## 2. Progress source precedence by context

Current Android code has several possible inputs, documented in [ANDROID_PROGRESS_MODEL.md](ANDROID_PROGRESS_MODEL.md):
- local chunk/offset from `PlaybackPosition` and `AppViewModel.getPlaybackPosition(...)`
- session progress from `NowPlayingSessionItem.lastReadPercent`
- backend queue progress from `PlaybackQueueItem.progressPercent`
- done/completed state derived from threshold or explicit done/reset actions

The intended precedence should be:

### A. User-initiated open of an in-progress item

Precedence:
1. Local chunk/offset
2. Session progress percent
3. Backend queue progress percent
4. Beginning of item

Notes:
- "Local chunk/offset" means the exact `PlaybackPosition` tracked in Android.
- If local chunk/offset is zero or absent, the app may derive a position from session/backend percent.
- Session progress should beat backend queue progress because it is the more recent app-side playback context.

### B. Automatic continuation to next item

Precedence:
1. Beginning of item
2. Nothing else

Notes:
- Automatic continuation should ignore local chunk/offset, session progress, backend queue progress, and done/completed state for the next item.
- The app may still record/report those older values elsewhere in UI, but they should not determine autoplay start position.

### C. User-initiated replay of a completed item

Precedence:
1. Beginning of item
2. Nothing else

Notes:
- Completed state should be treated as an override against resume.
- If the user explicitly wants a true resume instead of replay, that should be a separate action in a future design, not hidden inside ordinary play.

### D. User-initiated open of a completed item without explicit replay affordance

Desired rule:
- Prefer beginning of item.

Reasoning:
- The app currently has no separate "resume completed item" affordance.
- Treating a simple tap on a done item as replay-from-start is the least surprising behavior.

## 3. Where user-initiated and automatic behavior should differ

This is the core policy split.

### User-initiated open

Should honor prior progress because:
- the user explicitly chose that item
- resuming is usually the expected behavior for manual return to an item

### Automatic continuation

Should ignore prior progress because:
- the app is sequencing through the list automatically
- resuming old prior progress on the next item makes playlist listening feel discontinuous and arbitrary

This difference should be explicit in implementation rather than emerging accidentally from shared code paths in `PlayerScreen.kt`.

## 4. Recommended implementation rule of thumb

When `PlayerScreen.kt` decides where to start an item, it should first know which intent opened that item:
- `ManualOpen`
- `AutoContinue`
- `Replay`

Then apply this policy:
- `ManualOpen` -> resume using normal precedence
- `AutoContinue` -> force start at beginning
- `Replay` -> force start at beginning

That intent flag is the smallest clean input needed to prevent the player from reusing the wrong progress source.

## 5. Consequences for current Android code

Based on the current model documented in [ANDROID_PROGRESS_MODEL.md](ANDROID_PROGRESS_MODEL.md):
- `PlayerScreen.kt` currently seeds from local chunk/offset first, then percent-derived progress from `AppViewModel.knownProgressForItem(...)`.
- `knownProgressForItem(...)` currently merges queue `progressPercent` and session `lastReadPercent`.
- The just-shipped playlist continuation feature sets the next item playback position to `(0, 0)`, but the later percent-seeding step can still pull the next item forward.

Policy implication:
- A future code change should not broadly rewrite the whole progress model.
- It should only bypass percent-based resume seeding when the open intent is `AutoContinue` or `Replay`.

## 6. Biggest unresolved question

The main remaining product question is narrow:
- When a user taps a completed item, should ordinary play mean `replay from start` or `resume near the end`?

This note recommends:
- ordinary play on a completed item should mean `replay from start`

Reason:
- it is more comprehensible than resuming at 98-100%
- it aligns with the same rule proposed for autoplay continuation

## 7. Recommended first implementation slice

Smallest next ticket:
- `Android — force autoplay continuation to start next item from beginning`

Bounded scope for that ticket:
- add an explicit playback-open intent for autoplay continuation
- in `PlayerScreen.kt`, bypass percent-based resume seeding when that intent is `AutoContinue`
- preserve current manual-open resume behavior
- do not redesign backend progress fields, queue progress, or sync behavior

Why this is the right first slice:
- it directly addresses the user-visible autoplay issue that prompted this policy note
- it does not require replacing the current progress model
- it leaves manual resume behavior untouched until separately reviewed

## Summary

Policy decision:
- manual open resumes
- autoplay continuation restarts the next item from the beginning
- replay of completed items starts from the beginning
- playlist versus Smart Queue affects ordering, not start-position policy

This keeps the implementation change small while making playback behavior predictable.

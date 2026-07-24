# T-AND-NOWPLAYING-POINTER-DESYNC-1 — Re-point the Now Playing session to whatever actually starts playing

Base SHA: `9cbbdf3` (main, = merge of #474; operator reported against a build of #473 + #474
on top of `632f4b6`). Branch: `claude/t-nowplaying-pointer-desync-1`.
Android-only. Draft PR to `main`; operator merges.

## Report (operator, 2026-07-24, physical device SM-S926B)

1. Start playing the article at the TOP of Up Next.
2. Skip forward 2–3 articles (the original item moves into "Earlier in queue").
3. Reopen that original article from "Earlier in queue".
4. Long-press the play button to play it again.

**Actual:** playback starts and the original article is audible, but Up Next does not
update — "Now Playing" still shows the 3rd/4th article, the previous three articles stay
in "Earlier in queue", and the only indication of what is really playing is the scrolling
marquee in the player control panel.

**Expected:** starting playback of an item already in the session moves the session's
current pointer to that item, so Now Playing, the Up Next ordering and the
earlier/upcoming split all reflect what is audible.

## Pre-existing, not caused by #471 / #473 / #474 (verified)

`git diff 632f4b6..9cbbdf3` touches only pending-save surfacing, library-listing
snapshot caching, the drawer sync indicator and their tests
(`SettingsStore`, `ShareSaveUtils`, `InboxParkedSaveProjection`, `LibraryItemsScreen`,
`MimeoDrawerContent`, `OfflineSyncPresentation`, plus two `AppViewModel` hunks confined to
`loadQueue`/`loadLibraryItemsView`/`loadArchivedItems`). Nothing in the now-playing
session, playback engine or player-screen paths. Confirmed pre-existing.

The actual regression window is `5dc4f81` ("WIP: implement Up Next History and Earlier",
2026-07-22), which removed the last live caller of `AppViewModel.setNowPlayingCurrentItem`
— `vm.setNowPlayingCurrentItem(currentItemId)` in the PlayerScreen loadItem effect. That
removal was correct on its own terms: syncing the pointer on every *item load* reclassified
the session on mere preview taps (see the comment now at `PlayerScreen.kt:1192-1200`). But
nothing replaced it for the case it was also covering — an item load that goes on to
actually *play*. `setNowPlayingCurrentItem` has had zero live callers since; it survives
only in doc comments.

## Root cause

`PlayerScreen` resolves its `currentItemId` from the session anchor unless the engine
"owns live playback" (`PlayerScreen.kt:930-942`):

```kotlin
val engineOwnsLivePlayback =
    engineState.currentItemId > 0 &&
        (engineState.hasStartedPlaybackForCurrentItem || engineState.isSpeaking || engineState.isAutoPlaying)
val currentItemId = when {
    engineOwnsLivePlayback -> engineState.currentItemId
    sessionCurrentItemId != null && sessionCurrentItemId > 0 -> sessionCurrentItemId
    ...
}
```

Walk the repro with playback **paused** at step 3 (the natural way to browse Up Next — and
the state the operator was in):

- Step 3 navigates to `ROUTE_LOCUS/<original>`. `hasLockedPlaybackOwner` is false (paused)
  and `sessionAnchorDiffersFromEngine` is false (the engine still holds item #4 with
  `hasStartedPlaybackForCurrentItem = true`), so the preview-only skip at
  `PlayerScreen.kt:1204` does **not** apply. The effect falls through to a real
  `vm.playbackOpenItem(original, ManualOpen, autoPlayAfterLoad = false)`.
- `PlaybackEngine.openItem` resets `hasStartedPlaybackForCurrentItem = false`
  (`PlaybackEngine.kt:609,626`). So now `engineOwnsLivePlayback == false`, and
  `currentItemId` resolves back to the **session** anchor (#4) while the reader body and
  the engine's chunk buffer both hold the **original** article. `viewerOverrideItemId` is
  -1 (this is not a preview open), so `locusItemId = previewItemId ?: currentItemId` = #4.
- Step 4 long-presses Play → `playLocusItem()` (`PlayerScreen.kt:1872`). Because
  `locusItemId == currentItemId` (both #4), it skips the promote branch entirely and falls
  to `vm.playbackPlay()`, which speaks the loaded buffer — the original article.

So the engine plays the original article and **nothing ever moves the session pointer**.
`promoteReaderItemToNowPlaying` is never reached. Now Playing, the earlier/upcoming split
and the Up Next order all keep describing #4; only the marquee (fed from the engine/reader
payload) tells the truth.

A second, independent trigger for the same end state exists on the actively-playing
variant of the repro, where `playLocusItem()` *does* take the promote branch: it fires
`actionScope.launch { vm.promoteReaderItemToNowPlaying(...) }` and then synchronously calls
`onOpenItem(locusItemId)`. `actionScope` is a `rememberCoroutineScope()` tied to the
composition, and the launch is dispatched (not immediate), so the promote is vulnerable to
being cancelled by a composition change before it runs.

### Knock-on effect

`applyAuthoritativeUpNext` clears the engine when the authoritative active id differs from
the engine's current item (`AppViewModel.kt:7069-7071`). In the desynced state the server's
active id is #4 while the engine plays the original — so the next Up Next sync round-trip
kills playback outright. This matches the older symptom described in `ff7b435`
("blanked the Reader on the next pause and reverted Now Playing").

## Fix

Put the invariant where the engine state lives, not in a composable: **when the playback
engine actually starts speaking an item, the session pointer follows it.** Gating on
*playback start* rather than on *item load* is what keeps the `5dc4f81` preview fix intact
— previewing an item never starts its playback, so it never re-points the session.

- New pure classifier `classifyLivePlaybackSessionSync(...)` in `MainActivity.kt`, next to
  the existing `classifyReaderPromoteRoute`.
- New `AppViewModel` collector on `playbackEngineState`, keyed on
  `(currentItemId, hasStartedPlaybackForCurrentItem)` and `distinctUntilChanged`, so it
  fires once per "engine began playing item X" transition and never re-fires on position,
  speed or pause updates.
- The two session mutations (`moveCurrentIndex` for a queue member, transient-history
  restore for a history member) are extracted from `promoteReaderItemToNowPlaying` into
  shared private helpers so both entry points keep identical semantics — including the
  `shouldPlacePriorActiveInHistory` decision for the item being displaced.

### Scope boundary: items outside the session

`classifyLivePlaybackSessionSync` returns `None` for an item that is in neither
`session.items` nor `session.historyItems`. Adopting an arbitrary played item into Up Next
is an explicit user action (Play now / reader promote), not something an engine start
should do behind the operator's back. The reported bug is entirely about items *already in
the session*, matching the stated expected behaviour.

### Not changed

- Smart Queue ranking and drag-and-drop (`LibraryItemsScreen`, `SmartQueueReorderPolicy`)
  are untouched.
- `PlayerScreen` is untouched — no change to `locusItemId` resolution, the preview-open
  path, or `playLocusItem`. Once the pointer follows live playback, the composable's
  `currentItemId` converges on its own (the engine wins as soon as playback starts, and
  the session now agrees).
- `setNowPlayingCurrentItem` is left in place (dead but harmless); removing it is a
  separate cleanup.

## Tests

- `LivePlaybackSessionSyncTest` — the classifier: queue member, history member, already
  current, not-yet-started, external item, non-positive id, session-membership precedence.
- `SessionReorderPlanTest` additions — `computeSessionIndexMovePlan` for the *backward*
  move this repro needs (current index 3 → target 0), with and without the displaced
  active item going to History.

## Manual verification

See the PR description.

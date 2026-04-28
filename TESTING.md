# Testing Notes

## Locus UI invariants

Copy this checklist into any PR that changes Locus or player UI:

- Expand/Collapse is present and uses explicit buttons only; no swipe or drag gestures.
- The Locus tab opens in collapsed mode.
- Direct item entry or resume opens in expanded mode.
- The collapsed Locus view stays compact and does not render a large empty card.
- The expanded title truncates with ellipsis and does not overlap the speed or overflow actions.
- Playback speed remains in the expanded Locus header unless a separate decision changes it.
- Run manual sanity on a narrow-width device or emulator before merging.

## Header action polish checklist

Use this for PRs that touch Locus top-bar actions (`done`, `refresh`, `speed`, overflow):

- Top-bar icon sizing remains consistent (24dp for refresh/overflow; done icon follows current ticketed size).
- Refresh action behavior remains: idle, spinning while refreshing, success/failure feedback, and sync-problem warning when connectivity/auth issues persist.
- Speed trigger remains compact and readable on narrow widths (icon + value without overlap).
- Speed panel keeps the expected structure (presets, slider, stepper) and values stay clamped to the supported range.
- Speed changes preserve existing playback semantics (no backend/API behavior changes).

## Shared top chrome checklist

Use this for PRs that touch the persistent now-playing strip or top action bar density:

- Title strip title remains bold and readable for long strings (marquee/truncation behavior remains clean).
- Domain text remains italic, uses highlight color, and opens the source URL only when a valid URL exists.
- The centered title-strip divider remains present and visually aligned (currently about 75% width).
- No divider is rendered beneath the top action bar.
- Top action bar keeps compact vertical density without shrinking actionable touch targets below expected usability.

## Player screen layout sanity

Copy this checklist into any PR that changes `PlayerScreen` or the Locus playback layout:

- Top action bar is present.
- Title marquee row is present when that work is in scope.
- Player controls remain pinned above the bottom nav with no overlap.
- Reader body remains scrollable and separate from the pinned controls.
- Highlight is visible during playback when that work is in scope.

## Reader auto-scroll invariants

Copy this checklist into any PR that changes `ReaderBody`, highlight selection, or player scroll triggers:

- Anchor triggers are explicit only: Locus tab tap and Play action.
- Highlight/viewport visibility math uses root-space alignment.
- Explicit trigger consumption is deferred until scroll bounds are ready.
- Auto-scroll transition trigger fires only when highlight crosses out at the bottom edge.
- Manual-scroll suppression is time-bounded (about 1.2 seconds).
- Manual drag suppression must arm from drag source detection in both directions (drag up and drag down).

### Playback Scroll Guard Suite

Run this suite for any PR touching reader auto-scroll, manual detach/suppression, or reattach triggers:

```bash
.\gradlew.bat testDebugUnitTest --no-daemon --tests "com.mimeo.android.ui.reader.PlaybackScrollContractGuardTest" --tests "com.mimeo.android.ui.reader.ReaderScrollPolicyTest" --tests "com.mimeo.android.ui.reader.ReaderScrollCooldownPolicyTest" --tests "com.mimeo.android.ui.reader.ReaderVisibleBoundsTest"
```

## Share-sheet manual sanity

Run this checklist for PRs that touch share-sheet saving, notifications, or save routing:

- Share a URL from Chrome and confirm Mimeo does not stay foregrounded.
- Share a URL from BlueSky (or another `ACTION_SEND` source) and confirm the same receiver flow.
- Confirm success/error feedback appears as heads-up notifications.
- With `Keep share result notifications` off, confirm results auto-dismiss after about 4 seconds.
- With `Keep share result notifications` on, confirm results remain in the tray.
- With `Auto-download saved articles for offline reading` OFF, confirm share-save succeeds but the new item is not immediately offline-ready.
- With `Auto-download saved articles for offline reading` ON, confirm share-save succeeds and the new item becomes offline-ready without manual download.
- While an Up Next playlist is actively open, share into that same destination and confirm the new item appears without needing sort/search toggles.
- Leave Settings and return; confirm the auto-download toggle still reflects the persisted value.
- Clear the token and confirm `Configure API token in Settings` with a working `Open Settings` action.
- Set an invalid token and confirm `Check your API token` with a working `Open Settings` action.
- Share text without a URL and confirm `No valid URL found`.
- Disable connectivity and confirm `Couldn't reach server`.

## Queue verification checklist

Use this for PRs that touch queue rendering, playlists, or share-save verification:

- Queue top row exposes one explicit refresh action; there is no separate Sync button.
- Refresh must continue to perform both queue reload and queued-progress flush semantics.
- Select the expected playlist or Smart Queue in-app before verifying save results.
- Confirm search is visible and filters the current queue list.
- Confirm filter chips are visible and reset cleanly to `All`.
- Verify a newly shared item appears without using the web app.
- Search by title/domain and, when relevant, by a URL-derived token to confirm normalized fallback still works.

## Smart playlist CRUD manual verification

Use this checklist for docs-only signoff of shipped smart playlist CRUD behavior:

- Create a smart playlist from drawer/library flow and confirm it appears under Smart playlists (not Manual playlists).
- Open smart playlist edit, change name/filter/sort, save, and confirm detail + list refresh.
- Trigger delete from smart playlist detail and confirm the playlist is removed after confirmation.
- Confirm smart detail still preserves shipped baseline actions: pin/unpin/reorder in Pinned section, row Play Now/Next/Last, batch Add Selected to Up Next, Use as Up Next snapshot confirmation, and Freeze as manual snapshot.
- Confirm manual playlist guards remain intact (selected/default manual playlist IDs are unchanged by smart playlist CRUD operations).

## Persistent player cross-tab invariants

Use this for PRs that touch `MainActivity`, `PlayerScreen`, or settings toggles:

- Playback (TTS) continues across tab switches regardless of the `Persistent player across tabs` toggle state.
- `Persistent player across tabs` controls non-Locus controls visibility only.
- With toggle ON: controls are visible and interactive on non-Locus tabs.
- With toggle OFF: controls are hidden on non-Locus tabs, but playback continues and remains controllable when returning to Locus.
- Switching tabs does not create overlapping/invisible full-screen player layers or block tab interaction.

## 3-state player controls invariants

Use this for PRs that touch player bar sizing, mode transitions, or chevron docking:

- Full mode keeps the full progress slider lane; transport button geometry does not shift.
- Minimal mode does not reuse the full upper lane; it renders a thin progress line at the top edge of the minimal panel.
- Nub mode shows only chevron + thin progress line at the nav top boundary.
- Chevron remains circular (no vertical squash) and does not visibly overlap the nub/minimal progress line.
- Tap chevron toggles Full <-> Minimal (and NUB -> last non-nub mode); long-press toggles into/out of NUB.
- Chevron side snap (left/right) persists across tab switches and app relaunch.

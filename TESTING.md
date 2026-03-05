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

## Share-sheet manual sanity

Run this checklist for PRs that touch share-sheet saving, notifications, or save routing:

- Share a URL from Chrome and confirm Mimeo does not stay foregrounded.
- Share a URL from BlueSky (or another `ACTION_SEND` source) and confirm the same receiver flow.
- Confirm success/error feedback appears as heads-up notifications.
- With `Keep share result notifications` off, confirm results auto-dismiss after about 4 seconds.
- With `Keep share result notifications` on, confirm results remain in the tray.
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

## Persistent player cross-tab invariants

Use this for PRs that touch `MainActivity`, `PlayerScreen`, or settings toggles:

- Playback (TTS) continues across tab switches regardless of the `Persistent player across tabs` toggle state.
- `Persistent player across tabs` controls non-Locus controls visibility only.
- With toggle ON: controls are visible and interactive on non-Locus tabs.
- With toggle OFF: controls are hidden on non-Locus tabs, but playback continues and remains controllable when returning to Locus.
- Switching tabs does not create overlapping/invisible full-screen player layers or block tab interaction.

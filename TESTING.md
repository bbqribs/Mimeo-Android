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

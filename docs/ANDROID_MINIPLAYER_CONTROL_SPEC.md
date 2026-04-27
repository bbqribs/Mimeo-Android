# Android Mini-player Control Spec

**Status:** Spec shipped (mini-player v1 merged, 2026-04-27).
**Date:** 2026-04-27
**Scope:** Mini-player layout, controls, semantics, and persistence rules.
Covers the shipped post-redesign two-row layout with always-visible speed pill.
Does not authorize code changes by itself.

**References:**
- `docs/REDESIGN_COMPLETION_PLAN.md` §2 (accepted direction), §4.6
  (preservation contract), §5 (decisions), §6.1 (blockers)
- `docs/DESIGN_BEHAVIOR_RECONCILIATION.md` §1.5 (current behavior)
- `docs/ANDROID_QUEUE_ACTIONS_PATTERN_SPEC.md` (queue action grammar)

---

## Shipped Implementation Summary

Mini-player v1 shipped with:

- Two-row/decompressed layout.
- Title/source row separated from playback controls.
- Always-visible speed chip.
- Single consolidated stateful play/pause preserved.
- Short-press sentence-level rewind/forward preserved.
- Long-press paragraph rewind/forward preserved.
- Chevron drawer behavior and non-Locus persistence preserved.

Time-based skip, icon-set changes, Locus bridge chip, and Up Next history
remain deferred.

## 1. Purpose and status

The mini-player is the persistent playback control surface shown at the
bottom of all non-Locus routes while a session is active. It is not a
replacement for Locus. It coexists with the drawer-based navigation
model and must remain compact but not cramped.

**Design direction confirmed (REDESIGN_COMPLETION_PLAN.md §5):**
- Two-row (or otherwise decompressed) layout.
- Always-visible speed pill.
- Single consolidated stateful play/pause button (already implemented).
- Rewind and fast-forward present.
- Persistent on non-Locus routes.
- Chevron preserved (opens drawer).

**Shipped state (as of 2026-04-27):** the v1 implementation matches the
confirmed direction above. Remaining gaps are deferred design/product
items, not blockers for the shipped mini-player v1.

---

## 2. Layout

### 2.1 Two-row structure

The mini-player dock expands to two rows. Row order top to bottom:

| Row | Contents |
|-----|----------|
| **Title / source row** | Now-playing title (marquee or truncated) · source label |
| **Controls row** | Rewind · Play/Pause · Forward · Speed pill · Chevron |

Title/source row and controls row must not share horizontal space.
No single-row compression is acceptable.

### 2.2 Title / source row

- **Now-playing title:** truncated or marquee per existing
  `continuousNowPlayingMarquee` setting. Tapping anywhere on this row
  opens Locus for the current item (preserves existing `onOpenLocusForItem`
  behavior).
- **Source label:** optional secondary text (same data as the current
  now-playing strip). May be omitted if the source cannot be determined.
- The entire row is a single tap target opening Locus. No separate icon
  for this affordance.

### 2.3 Controls row

Left-to-right order:

| Position | Control | Notes |
|----------|---------|-------|
| 1 | **Rewind** | Sentence-level jump; long-press paragraph (see §3.2) |
| 2 | **Play / Pause** | Stateful single button; long-press opens Locus (existing behavior) |
| 3 | **Forward** | Sentence-level jump; long-press paragraph (see §3.2) |
| 4 | **Speed pill** | Always visible; opens speed control (see §3.3) |
| 5 | **Chevron** | Always present; tap opens drawer; long-press cycles control modes (existing) |

Previous-item and next-item controls (skip prev / skip next) are shown
only in FULL control mode, as today. They are not present in the MINIMAL
or NUB modes. This behavior is unchanged.

### 2.4 Progress strip

Progress strip (thin `PlayerProgressLine`) renders between the two rows
or immediately below the controls row. Placement may be at the very top
of the mini-player dock (above both rows) or between rows, whichever
achieves the least visual crowding. Final placement is an implementation
detail; do not compress either row to fit it.

In FULL control mode, a full seek slider may replace the thin strip per
existing `PlayerControlsMode` logic.

---

## 3. Control semantics

### 3.1 Play / Pause

- Single stateful button: shows Pause icon when playing, Play icon when
  paused.
- Short tap toggles playback (existing behavior, unchanged).
- Long press opens Locus for the current item (existing `playLocusItem`
  behavior).
- No separate competing Play and Pause buttons (operator-rejected,
  completion plan §3).

### 3.2 Rewind and Forward (sentence-level jumps — v1)

**Operator decision (ticket scope):** Keep current sentence-level
semantics. Do not change to time-based (10s / 30s) skip in this
implementation slice.

| Gesture | Action |
|---------|--------|
| Short tap Rewind | Jump to previous sentence boundary |
| Short tap Forward | Jump to next sentence boundary |
| Long press Rewind | Jump to previous paragraph boundary |
| Long press Forward | Jump to next paragraph boundary |

**Confirmed shipped behavior:** Long-press paragraph jump is currently
wired in the shared `renderPlayerControlBar` via `onPreviousSegmentLongPress`
/ `onNextSegmentLongPress` (PlayerScreen.kt lines ~1829–1872). The same
control bar renders in both Locus and mini-player (`compactControlsOnly = true`).
This is not a new behavior; it is already active in the mini-player.

**Time-based skip:** Explicitly deferred. Do not introduce 10s or 30s
skip mechanics in this ticket. If time-based skip is adopted later, it
requires a separate operator decision and a distinct implementation ticket.

### 3.3 Speed pill

- Always visible in the controls row. Not hidden behind long-press or
  auto-hiding chrome.
- Displays the current playback speed (e.g., "1.5×").
- Tap behavior: opens the existing speed control dialog / speed selection
  surface (same surface reachable today from the Locus top bar). The
  exact tap-to-cycle vs tap-to-open-dialog behavior follows whichever
  mechanism the existing `ExpandedPlayerTopBar` speed control uses.
- Speed range and presets are unchanged: 0.5×–4.0×; presets at 1.0,
  1.25, 1.5, 1.75, 2.0.
- Implementation: the speed pill must read its value from the same
  reactive speed state as the Locus top bar. They must stay in sync.

### 3.4 Chevron

- Tap: opens the navigation drawer (existing `onChevronTap` wiring).
  This is load-bearing for drawer access on all non-Locus routes.
  Must not be removed or relocated.
- Long press: cycles through control modes (FULL → MINIMAL → NUB →
  FULL; existing `handleChevronLongPress` behavior).
- Position: right edge of the controls row (unchanged from current layout).
- The chevron is draggable to the left or right edge per
  `chevronSnapEdge` setting (existing behavior, unchanged).

---

## 4. Icon and label guidance

### 4.1 Icon selection

| Control | Icon | Rationale |
|---------|------|-----------|
| Rewind | `msr_fast_rewind_24` (current) | Curved rewind arrow. Acceptable for v1 if paired with clarifying a11y label (§4.2). Do not use a time-label icon (e.g., "15" inside an arrow). |
| Forward | `msr_fast_forward_24` (current) | Curved forward arrow. Same rule as Rewind. |
| Speed pill | Text label (e.g., "1.5×") | Speed chip with text is self-describing. No separate icon needed. |

If the icon set is changed in a future ticket (e.g., moving to sentence-
glyph icons), that requires a separate operator decision. This spec does
not authorize an icon set change.

### 4.2 Accessibility labels

The existing a11y content descriptions are already correct and must be
preserved:

| Control | Content description |
|---------|---------------------|
| Rewind button | `"Previous sentence (long press: previous paragraph)"` |
| Forward button | `"Next sentence (long press: next paragraph)"` |
| Play button (playing state) | Existing "Pause" or equivalent |
| Play button (paused state) | Existing "Play" or equivalent |
| Speed pill | `"Playback speed: [value]×. Tap to change."` (new) |
| Chevron | Existing description (opens drawer) |

Do not use labels implying time-based skips ("Skip back 15 seconds",
"Skip forward 30 seconds"). The a11y label is the contract: if it says
"sentence," the implementation must perform a sentence jump.

---

## 5. Behavior preservation contract

This section is the authoritative reference for the implementation
ticket's preservation checklist (per REDESIGN_COMPLETION_PLAN.md §9).

| Behavior | Rule |
|----------|------|
| Persistence | Mini-player persists on all non-Locus routes while a session is active. The `showMiniPlayer` logic in `MainActivityShell` must not be changed. |
| Not rendered on Locus | Mini-player is hidden while the Locus route is active. Unchanged. |
| Chevron opens drawer | `onChevronTap` callback opens the navigation drawer. Load-bearing. Must survive any layout refactor. |
| Title row opens Locus | Tapping the title/source row calls `onOpenLocusForItem` for the current item. |
| Speed reachable | Speed control must be reachable from the mini-player. This is the primary known gap being closed by this ticket. |
| Session state reactive | Current item title, source, progress, and play state are all reactive to the shared `AppViewModel` state. No stale display. |
| Sentence-jump semantics | Short press = sentence jump. Long press = paragraph jump. No time-based skip. |
| No cross-device sync UI | The mini-player displays only local session state. No sync indicator, no "this session is shared" indicator. |
| No Player Queue surface | The mini-player does not link to or reveal a Player Queue. Up Next is the queue surface. |
| Control mode cycles | FULL / MINIMAL / NUB mode cycling via chevron long-press is preserved. Skip-item controls visible in FULL mode only. |

---

## 6. Unresolved items

| Item | Status | Notes |
|------|--------|-------|
| Speed control UX: cycle-on-tap vs open-dialog | Needs verification at implementation time | Follow whatever the existing Locus top bar speed control does. No new design decision needed. |
| Progress strip placement (above rows vs between rows) | Deferred to implementation | Either placement is acceptable; do not compress rows to fit. |
| NUB mode behavior in two-row layout | Needs manual verification | NUB mode currently shows minimal chrome. Confirm that the two-row expansion does not break NUB mode. |
| Icon set change (sentence glyph vs time arrow) | Explicitly deferred | Requires a separate operator decision. Not in scope for this ticket. |
| Time-based ff/rw | Explicitly deferred | Not in v1. Requires a separate spec and operator decision. |
| Full-player redesign | Explicitly deferred | Gated on Locus/player spike (B3). |
| Locus/player bridge | Explicitly deferred | Not implementation-ready. |
| Up Next history surface | Explicitly deferred | Separate ticket (B2). |

---

## 7. Implementation status

**Shipped bounded ticket:** Mini-player two-row layout +
always-visible speed pill + clarified sentence-jump a11y labels.

**Scope:**
- Two-row mini-player dock.
- Always-visible speed pill in the controls row, reactive to current
  speed state.
- §5 contract behaviors preserved.
- Rewind/Forward a11y labels match §4.2.
- Speed pill a11y label present.
- Chevron opens drawer after layout refactor.
- NUB mode remains in scope for manual verification after future chrome
  changes.

**Explicitly out of scope for this ticket:**
- Time-based ff/rw skip.
- Icon set change.
- Full-player redesign.
- Locus/player bridge.
- Up Next history or snap-to-active affordance.
- Auto-hide of any mini-player chrome.

**Residual risk:** Low. The control logic is unchanged; future work should
avoid changing sentence/paragraph semantics accidentally while revisiting
icons or time-based skip.

**References for implementation ticket:**
- `docs/ANDROID_MINIPLAYER_CONTROL_SPEC.md` (this doc)
- `docs/REDESIGN_COMPLETION_PLAN.md` §2, §4.6, §5
- `app/src/main/java/com/mimeo/android/ui/player/PlayerScreen.kt`
  (renderPlayerControlBar, PlayerControlBar composable)
- `app/src/main/java/com/mimeo/android/ui/player/MiniPlayer.kt`

---

## Manual verification

Docs-only artifact. No code changes. Verification:

```powershell
Get-Content -Raw docs\ANDROID_MINIPLAYER_CONTROL_SPEC.md
git diff -- docs\ANDROID_MINIPLAYER_CONTROL_SPEC.md
```

Confirm:
- §2 layout matches the operator-accepted two-row direction from
  REDESIGN_COMPLETION_PLAN.md §5.
- §3 semantics preserve sentence-jump behavior without introducing
  time-based skip.
- §4 a11y labels do not imply time-based behavior.
- §5 preservation contract matches §4.6 of the completion plan.
- §6 unresolved items carry forward all genuinely open questions without
  resolving them prematurely.
- §7 records shipped scope and does not creep into deferred surfaces.

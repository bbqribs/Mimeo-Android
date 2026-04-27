# Android Locus / Player Integration Spike

**Status:** Design spike — docs/design only. No code changes authorized.
**Date:** 2026-04-27
**Scope:** Decides whether a separate full player is needed, what "Locus/player
bridge" means in plain English, and what (if anything) needs to be built next.
Gates ticket C4 / any full-player work.

**References:**
- `docs/REDESIGN_COMPLETION_PLAN.md` §2, §3, §5, §6.2, §7-B3
- `docs/DESIGN_BEHAVIOR_RECONCILIATION.md` §1.4, §1.5, §2 (Locus/Full player rows)
- `docs/ANDROID_MINIPLAYER_CONTROL_SPEC.md`
- `docs/ANDROID_UP_NEXT_LAYOUT_SPEC.md`
- Code inspected (reference only — no changes):
  - `app/.../ui/player/PlayerScreen.kt` (control modes, LocusContentMode, preview logic)
  - `app/.../ui/player/MiniPlayer.kt` (wrapper)
  - `app/.../MainActivityShell.kt` (showMiniPlayer condition, route detection)
  - `app/.../model/Models.kt` (LocusContentMode enum)

---

## 1. Current Behavior Inventory

### 1.1 How Locus opens

Tapping any item row in any surface (library, playlist detail, Up Next)
calls `onOpenLocusForItem(itemId)`, which navigates to the Locus route
(`ROUTE_LOCUS`). `PlayerScreen.kt` receives the item ID and loads the
reader content. There is one Locus route; it is not parameterized by
"player mode" vs "reader mode" at the route level.

### 1.2 LocusContentMode — the three sub-modes of Locus

Locus has three persisted display modes selectable in Settings:

| Mode | What the user sees |
|---|---|
| `FULL_TEXT` | Reader text only. Top bar and dock are hidden (immersive reading). |
| `FULL_TEXT_WITH_PLAYER` | Reader text + playback dock at the bottom. **Default.** |
| `PLAYBACK_FOCUSED` | Playback-focused layout. Reader text is present but the dock is the dominant surface. |

The user cycles through these via the top-bar toggle. They are modes of
the same `PlayerScreen` composable, not separate routes.

### 1.3 How the mini-player appears and disappears

`MainActivityShell.kt` line 157:

```kotlin
val showMiniPlayer = !requiresSignIn && !isOnLocusRoute && requestedPlayerItemId != null
```

Plain English:
- Mini-player appears on every route **except** Locus, as long as a session
  item is active.
- On the Locus route the mini-player is hidden — Locus itself is the player.
- If there is no active session item, mini-player does not appear anywhere.

There is no additional logic. No animation guard or delay.

### 1.4 What happens on non-Locus routes

Mini-player dock renders at the bottom of the screen. It provides:
- Sentence-level rewind / forward (short press = sentence, long press = paragraph).
- Play / Pause.
- Chevron (opens drawer).
- Progress strip.
- **No speed control** (known gap; addressed by mini-player B1 ticket).

Tapping the now-playing title in the mini-player dock calls
`onOpenLocusForItem(currentItemId)` — navigates to Locus for the active
session item.

### 1.5 What happens on the Locus route

The full `PlayerScreen` renders (reader + dock). No mini-player overlay.

The reader body responds to taps to toggle top-bar visibility (the
`ExpandedPlayerTopBar`). The top bar contains: title, source, progress,
Speed control, Refresh, Favorite, Archive, overflow (Play Next, Share,
Open in browser, etc.).

The dock at the bottom provides FULL / MINIMAL / NUB control modes cycled
by long-pressing the chevron.

### 1.6 Speed, sentence navigation, paragraph long-press, play/pause, chevron, mode cycling

| Control | Behavior |
|---|---|
| Speed | Locus top bar only. Opens speed picker (0.5×–4.0×). Absent from mini-player (known gap). |
| Sentence rewind | Short press: previous sentence boundary. |
| Sentence forward | Short press: next sentence boundary. |
| Paragraph jump | Long press rewind/forward: previous/next paragraph. |
| Play / Pause | Single consolidated stateful button. Short press toggles. |
| Chevron tap | Opens navigation drawer. Load-bearing. |
| Chevron long-press | Cycles FULL → MINIMAL → NUB → FULL control modes. |
| Chevron drag | Snaps chevron to left or right edge (persisted setting). |

### 1.7 How Up Next active item relates to Locus

This is the crux of the bridge question. There are two distinct states:

**State A — Reader item = active session item (normal case)**
The user tapped an upcoming Up Next row or continued from the current
session item. `PlayerScreen` loads that item; the session advances to it.
Reader text, playback audio, and Up Next active anchor all refer to the
same item. No discrepancy.

**State B — Reader item ≠ active session item (preview / detached case)**
The user opened an item from the library or a playlist without starting
a new session, while a different item is actively playing. `PlayerScreen`
loads the reader content for the new item ("preview mode") but playback
continues on the previous session item. The reader and the audio are
out of sync. There is currently **no indicator** in Locus that tells the
user what is actually playing.

The code calls this "preview mode" internally (`previewModeActive`). There
is no UI label for it facing the user.

---

## 2. Concept Options

### Option A — No separate full player; Locus is the full reading/playback surface

**What it is:** The current reality. Locus is simultaneously the reader and
the player. The three `LocusContentMode` values already cover reader-dominant,
balanced, and playback-dominant views. The mini-player handles all non-Locus
routes. No new route.

**Pros:**
- Already implemented. No architectural change.
- Reader-first identity is the product identity. A separate player route
  dilutes it.
- `PLAYBACK_FOCUSED` mode already serves the use case of "I mostly want
  controls, not text."
- One fewer navigation destination means fewer surprise transitions.

**Cons:**
- Speed control is currently only reachable from the top bar (known gap,
  being fixed by B1). Until B1 ships, speed is inaccessible while reading.
- The three `LocusContentMode` modes are a settings-level concept, not an
  in-session toggle. Users who want to flip between reading and listening
  must know about the mode.

**Verdict:** This is the right model. B1 (mini-player speed pill) and the
top-bar speed persistence resolve the known gaps without adding a new surface.

---

### Option B — Full player as an expanded control mode within Locus

**What it is:** `PLAYBACK_FOCUSED` already exists and is essentially this.
The dock expands and the reader takes a back seat. This is Option A with a
label change.

**Pros:** Nothing new to build; the mode exists.

**Cons:** The mode is buried in Settings today. Making it more discoverable
(e.g., a toggle button in the dock) is a UI refinement, not a new surface.

**Verdict:** Acceptable as a UI polish item if the operator wants a quicker
mode toggle. Not a separate architectural decision. Can be handled as a small
addition to an existing ticket, not a new concept.

---

### Option C — Separate full-player route/surface

**What it is:** A new route (`ROUTE_FULL_PLAYER` or similar) that is
primarily a large playback control surface, not a reader. User navigates
there via the mini-player or Up Next.

**Why it might seem appealing:** Some apps (podcasts, audiobooks) have a
"now playing" screen that is not the item reader.

**Why it is wrong for Mimeo:**
- Mimeo's primary differentiator is the reader. A separate player route
  would imply that audio is the primary mode and reading is secondary.
- This is explicitly the "Wireframe 2 Player Queue as distinct surface"
  pattern, which has been rejected.
- It adds a route transition that breaks the "tap is read" mental model:
  tapping the mini-player could open the reader OR the player, creating
  ambiguity about what the user gets.
- Locus's `PLAYBACK_FOCUSED` mode already covers the "mostly controls"
  view within the reader surface.

**Verdict:** Rejected. Matches §3 rejected direction "Player Queue as a
distinct surface."

---

### Option D — Player Queue surface

**What it is:** A dedicated screen that shows the playback queue, controls,
and now-playing info, separate from both Locus and Up Next.

**Why it is wrong for Mimeo:** Explicitly rejected in
`docs/REDESIGN_COMPLETION_PLAN.md` §3. The queue surface is Up Next. The
player surface is Locus. A third "Player Queue" surface would create a
confusing three-way split with overlapping responsibilities.

**Verdict:** Rejected. Do not introduce.

---

## 3. Recommendation

**Locus remains the only full player surface. No new player route.**

The current architecture is correct. Locus is the primary reading and
playback surface. The mini-player handles the non-Locus routes. The three
`LocusContentMode` values are the full range of player presentation without
requiring a separate route.

The operator bias stated in the ticket ("Locus should remain the primary
full reading/playback surface") is correct and confirmed by code inspection.

**What this means concretely:**
- Ticket C4 ("Full player (if still needed) / Player Queue") is not needed
  and can be closed as "resolved — not needed."
- The only remaining player-surface gap is the mini-player B1 ticket
  (speed pill + two-row layout).
- The `PLAYBACK_FOCUSED` mode exists and works; no ticket needed to
  activate it, but a future UI-polish ticket could add a fast toggle
  button to the dock if the operator wants it more discoverable.

---

## 4. Bridge Signal — Plain English

The "bridge" is the answer to this question: **when the user is reading
one item in Locus but a different item is actively playing, what does the
UI communicate?**

### 4.1 When Locus item = active Up Next item (State A)

The normal state. Reader text and audio are in sync. The top bar shows
the title and source of the item. The session's active anchor in Up Next
points to the same item. **No bridge UI is needed** — everything already
refers to the same item.

No affordance is required. The current behavior is correct.

### 4.2 When Locus item ≠ active Up Next item (State B — preview/detached)

The user opened a different item from the library or a playlist while
audio is playing from a session item. The reader shows item X. The audio
is playing item Y. The mini-player is hidden (because Locus is active).
Up Next still shows item Y as the active anchor.

**Current behavior:** No indicator. The user cannot tell, from within Locus,
that a different item is playing. The audio controls (play/pause, rewind,
forward) still affect item Y, not X.

**What the bridge signal is:** A small, persistent "Now playing: [item Y
title]" indicator — a chip or banner — visible in the Locus UI when
State B is active. It tells the user: "You are reading X; audio is still
on Y." A tap on the indicator opens Locus for item Y (the session item),
restoring State A.

**Is a small "Now playing" indicator enough?** Yes. The user needs to know
two things: (1) what is actually playing, and (2) how to get back to it.
A tappable chip satisfies both. No destructive action is needed; no
confirm is needed; no queue mutation happens.

**Does any action need to happen automatically?** No. State B is a valid
user-initiated state (the user deliberately opened a different item). The
system should not automatically switch the session item just because the
user opened a reader. The bridge is informational + a tap shortcut; it is
not a side effect.

### 4.3 Decision table

| State | Locus item | Active session item | Indicator shown? | Tap behavior |
|---|---|---|---|---|
| A — in sync | X | X | No | N/A |
| B — detached | X | Y (Y ≠ X) | Yes: "Now playing: [Y title]" chip | Opens Locus for Y (State A) |
| No session | X | — | No | N/A |

### 4.4 Where to place the indicator

**Recommended:** A small chip or banner row immediately above the playback
dock, visible only in State B. It should not obscure reader text and should
not auto-dismiss.

**Alternative:** In the top bar alongside the item title. Acceptable but
the top bar already auto-hides in immersive reading mode, which could
make the indicator invisible exactly when the user needs it.

**Not recommended:** A modal or dialog. State B is informational, not an
error.

### 4.5 What the bridge is NOT

- It is not a second playback queue.
- It does not start or stop playback.
- It does not mutate the Up Next session.
- It does not open a new route/screen.
- It is not the same as the mini-player (which is hidden while Locus is open).

---

## 5. Implementation Implications

### 5.1 What should be implemented next

**Nothing from this spike requires immediate implementation.** The spike
resolves the design question; implementation follows as a separate bounded
ticket.

If the operator wants to open a ticket:

| Item | Effort | Priority |
|---|---|---|
| Bridge indicator chip (State B "Now playing" chip in Locus) | Small — read session state, compare item IDs, render a chip | Low-medium. Nice to have; not blocking. |
| Make `PLAYBACK_FOCUSED` mode toggleable from the dock | Small — add an icon button in the dock to cycle modes | Low. Discoverability improvement only. |

The mini-player B1 ticket (two-row layout, speed pill) is higher priority
than either of these and is already specified.

### 5.2 What should stay deferred

- Full-player route (rejected above; do not open C4).
- Player Queue surface (rejected; do not open).
- History retention, persisted Up Next history (separate track; C1).
- Smart playlists, Bluesky surface (C2, C3).

### 5.3 Whether existing mini-player polish is enough for now

Yes, for the non-Locus routes. The B1 mini-player ticket closes the main
known gap (speed control absent from mini-player). After B1 ships:
- Speed is reachable from both Locus (top bar) and mini-player (speed pill).
- Play/pause, rewind, forward are reachable from both.
- Chevron opens drawer from both.
- The mini-player identity vs Locus identity is clean (different routes,
  same engine).

The bridge indicator (§4) is a Locus-route improvement, not a mini-player
improvement. It can be deferred until B1 is shipped.

### 5.4 Whether Locus needs a full redesign

**No.** Locus needs only:
1. Speed reachable even with top bar hidden — addressed if the mini-player
   B1 speed pill concept is extended to the Locus dock in a future pass.
   Currently speed is top-bar-only in Locus; this is a known gap in the
   completion plan but does not require a redesign, only adding the speed
   pill to the dock.
2. Bridge indicator chip for State B (§4.2) — small addition, not a redesign.
3. "Save queue as playlist" in Locus overflow — already noted as a gap in
   the completion plan (§2 Locus row); small addition.

None of these require structural changes to Locus.

---

## 6. Risks

### 6.1 Avoid reintroducing Wireframe 2 Player Queue

Any ticket that creates a new full-screen "now playing" route is
reintroducing the Player Queue. The test: if it is a new route, it is
a Player Queue. The bridge indicator (§4) is not a route — it is a chip
rendered within the existing Locus route.

### 6.2 Avoid making Locus a generic queue manager

Locus shows one item at a time. The Up Next session exists alongside it.
Nothing in the bridge should add a queue list to Locus. The indicator chip
shows one item (the active session item) and links to it.

### 6.3 Avoid hiding speed or playback controls

The B1 mini-player spec already addresses speed reachability on non-Locus
routes. For Locus itself, speed is in the top bar. If the top bar
auto-hides, speed must still be reachable — either the auto-hide must be
disabled by default, or a speed pill must be added to the dock. This is a
known gap in the completion plan (§6.1) and must be resolved before
auto-hide is enabled.

### 6.4 Avoid breaking reader-first identity

The bridge indicator must be non-intrusive. A full-width banner, a blocking
modal, or a large chrome element would shift focus from reading to playback
management. A small chip at the edge of the dock area is sufficient.

---

## 7. Recommendation Summary

| Question | Answer |
|---|---|
| Does the app need a separate full-player route? | **No.** Locus is the full player. |
| Is `PLAYBACK_FOCUSED` mode sufficient for playback-dominant use? | **Yes.** No new route needed. |
| What is the Locus/player bridge? | A small "Now playing: [Y]" chip visible only when the Locus reader item ≠ the active session item. Tap opens Locus for the session item. |
| Is the bridge a blocker for any current ticket? | **No.** B1 and the library tickets (A1, A2) do not depend on it. |
| Should C4 be opened? | **No.** Close as resolved — not needed. |
| What is the next implementation step? | Ship mini-player B1 (two-row layout + speed pill). Bridge indicator is a follow-on, lower priority. |

---

## 8. Files Touched

- **Created:** `docs/ANDROID_LOCUS_PLAYER_INTEGRATION_SPIKE.md` (this file)
- **Not touched:** `docs/REDESIGN_COMPLETION_PLAN.md` — §7-B3 already
  describes this ticket correctly; no sync needed.
- **Not touched:** `ROADMAP.md` — no implementation work to record.
- **No Kotlin files.**

---

## 9. Manual Verification

Docs-only artifact. No code changes.

```powershell
Get-Content -Raw docs\ANDROID_LOCUS_PLAYER_INTEGRATION_SPIKE.md
git diff -- docs\
```

Confirm in plain English:
- §3 recommendation matches operator bias ("Locus is the full player").
- §4.3 decision table covers all three session states.
- §5.1 implementation table does not authorize code changes.
- §6 risks do not apply to any already-open ticket.
- No new route, no Player Queue, no full-player redesign is introduced.

# Android redesign completion plan

**Status:** Planning. Authoritative for the next round of redesign tickets.
This doc translates the Claude Design explorations and the behavior
reconciliation into a bounded execution sequence.
**Date:** 2026-04-25
**Scope:** Android-only. Redesign-completion-facing. Does not authorize
code changes by itself.

**Inputs**
- `docs/DESIGN_WIREFRAME_RECONCILIATION.md` (working synthesis)
- `docs/DESIGN_BEHAVIOR_RECONCILIATION.md` (behavior inventory and
  delta matrix)
- `docs/ANDROID_QUEUE_ACTIONS_PATTERN_SPEC.md`
- `docs/ANDROID_ITEM_ACTIONS_SPEC.md`
- Canonical product model:
  `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\PRODUCT_MODEL_POST_REDESIGN.md`
- List layout spec:
  `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\LIST_LAYOUT_HOMOGENIZATION_SPEC.md`
- Claude Design artifacts (reference only):
  `docs/planning/Wireframe/`, `docs/planning/Wireframe 1/`,
  `docs/planning/Mimeo Wireframes 3 - Standalone.html`

**Authoritative use:** Implementation tickets cite this doc plus the
relevant behavior/spec doc. Wireframes are evidence, not source of truth
(see §9 Implementation gate).

---

## 1. Current redesign state

The redesign v2 structural work has shipped: drawer-based navigation,
shared library views (Inbox / Favorites / Archive / Bin), persistent
mini-player, real Locus route, multi-select + batch actions, manual
playlists with reorder, and a device-local Up Next session substrate.
Phases 0–6 of `REDESIGN_V2_PLAN.md` are functionally closed, with
cross-device Up Next sync explicitly deferred.

What is **design-informed but not implemented** is the post-redesign
product model layer on top of that scaffolding: a three-region Up Next
(history / active / upcoming), Sectioned Library, mini-player polish
(consolidated play/pause + speed reachable + ff/rw), Save queue as
playlist, smart playlists, and the Bluesky harvester surface. The
Claude Design wireframes and the operator's reconciliation feedback
have given us a defensible direction for most of these surfaces, with a
small set of unresolved decisions called out below.

What is **still unsettled** is the Locus/player bridge, full-player
necessity, history-row interaction beyond "current/anchored," mini-player
icon/visual treatment details, and history retention/privacy controls.

Why the redesign should continue as **smaller bounded tickets** rather
than one large UI rewrite:

- The shipped behavior contract is non-trivial and easy to lose in a
  re-skin (see §4). A bounded ticket per surface allows the contract to
  be referenced and verified each time.
- Several decisions are genuinely unresolved. Mass implementation would
  force premature decisions or burn rework.
- Claude Design access is rate-limited (the operator has ~1–2 large
  jobs available this week). Implementation tickets must not be blocked
  by design slots that have not yet been spent.
- The redesign v2 audit (`docs/REDESIGN_V2_AUDIT_2026-04-21.md`) and
  the Phase 3 plan note both confirmed that small, behavior-preserving
  tickets are the practical unit of work in this codebase.

---

## 2. Accepted direction by surface

| Surface | Accepted direction | Why | Current implementation gap | Risk if implemented naively |
|---|---|---|---|---|
| **Up Next** | Wireframe 1 Conservative Up Next: muted history above, prominent active anchor in middle (not draggable), upcoming with drag handles below; Clear upcoming near the Upcoming section header; Clear all in overflow/contextual destructive area; floating anchor pill for snap-to-active. | Matches product model three-region structure. Comfortable density preserves long-queue ergonomics. Anchor pill is non-modal and operator-preferred. | **Slices 1–3 shipped (2026-04-27):** Active/upcoming scaffolding, active anchor (not draggable), history hidden, snap-to-active pill, Clear upcoming near Upcoming header, Clear all session in overflow, Save queue as playlist in overflow (saves active + upcoming only; history excluded). Remaining gap: history region display and history persistence (deferred). | History rows might become accidentally selectable, draggable, or refresh-clearable if history display is later introduced without following the spec rules. |
| **Mini-player** | Decompressed two-row (or otherwise multi-row) layout. Title/source on top row separated from playback controls. Always-visible speed pill. Single consolidated stateful play/pause. Rewind and fast-forward remain sentence-level for v1, with long-press paragraph jumps recorded. Persistent on non-Locus routes. Chevron continues to open drawer. | Current single-row dock is too cramped for both readability and reachability. Operator feedback explicitly rejects compressing speed into long-press or hidden affordances. The new mini-player control spec records the current sentence/paragraph behavior without changing playback semantics. | Today's mini-player has no speed control at all; play/pause is consolidated; ff/rw exist as sentence jumps; layout is single-row. | Compressing the new layout to fit the existing dock height would re-create the cramped problem. Icon/visual treatment can be refined during implementation, but must not imply time-based skip semantics. |
| **Locus / player** | Reader remains primary. Mini-player docks below reader (already shipped). Speed must remain reachable even with auto-hiding top bar. Bridge between Locus and Up Next active item is unresolved and **not implementation-ready**. | Operator cannot yet explain the Locus/player bridge in plain English; deferring avoids accidental commitment to a confusing model. | No "Save queue as playlist" in Locus overflow; no explicit bridge affordance when reader item = active session item. | A premature bridge UI could re-introduce the "Player Queue as separate surface" concept that the operator has already rejected. |
| **Library** | Sectioned Library. Shared row anatomy from list layout spec. Date or logical section headers. Visible overflow. "Add Selected to Up Next" added to batch bar. "Play Now" added to row overflow. | Sectioning is the operator-preferred direction; row grammar is shared with playlist/smart-playlist surfaces. "Add Selected to Up Next" parity with playlist detail is explicit in the queue actions spec. | "Add Selected to Up Next" missing from library batch bar; "Play Now" missing from row overflow; sectioning policy not yet decided (date / source / read-state). | Adding a visible play button on the row face would violate the "tap is read; explicit is queue" rule. Pull-to-refresh that auto-re-seeds would violate the no-auto-reseed rule. |
| **Manual playlist** | Conservative Playlist. Manual order visible. Drag handles on every row. Tap → Locus only (must not implicitly start a session, which today's code can do). Row overflow: Play Now / Play Next / Play Last / Move to top / Move to bottom / Remove. Undo on remove is preserved. | Manual order is the playlist's identity. Implicit session-start on tap blurs the "tap is read" rule. | Tap currently can call `vm.startNowPlayingSession(...)`; "Play Now" missing; otherwise close to spec. | Splitting tap from session-start touches `onOpenPlayer` plumbing; care needed to avoid breaking the active-item highlight. |
| **Smart playlist** | Sectioned Smart Playlist. Filter summary header. Pinned section + live-matching section. Sort controls. Pin/unpin per row. Freeze as manual playlist as secondary. Smart playlist may seed Up Next via explicit action; never auto-mutates Up Next. | Aligns with product model Model B (saved filter + optional manual pin/order layer). Avoids smart playlist behaving as live Up Next. | Smart playlists are not yet implemented on Android. | Treating a smart playlist as Up Next continuity. Unclear archive/binned policy for pinned items. |
| **Bluesky rolling surface** | Sectioned Bluesky. Harvester/config header above the list. Article rows use shared row grammar. Play Next / Play Last / Add Selected to Up Next available; Play Now and image-shaped rows out of v1. | Treat Bluesky as a smart-playlist-shaped surface fed by a harvester. Keeps the row grammar consistent and avoids special-casing. | Not implemented. | Auto-promotion to Up Next; thumbnail row treatment; cross-device sync UI — all already rejected. |
| **Drawer / settings** | No structural change beyond what is already shipped. Chevron-opens-drawer is load-bearing. Hamburger button stays. Auto-hide of action bars (queue actions spec §6) requires a "disable auto-hide" setting before any auto-hide ships. | Drawer model is settled. Auto-hide is an accessibility hazard without an opt-out. | Auto-hide for action bars is not implemented; the disable-auto-hide setting is not yet present. | Implementing auto-hide for action bars without the disable-auto-hide setting violates the accessibility rule. |

---

## 3. Rejected directions

These directions are explicitly out of scope. Future design or
implementation work must not reintroduce them without an explicit new
operator decision.

| Rejected direction | Surfaces affected | Reason |
|---|---|---|
| All Dense variants | Up Next, Library, Manual playlist, Smart playlist, Bluesky, Locus, Mini-player, Full player | Operator-rejected across the board. Density loss is not acceptable. |
| Wireframe 2 Player Queue as a distinct surface | Up Next, Locus | Conflicts with Wireframe 1 Up Next and with the canonical Up Next model. |
| Active Up Next item being draggable | Up Next | Active item is the playback anchor; drag introduces motion-vs-anchor ambiguity. |
| Cross-device Up Next sync UI | Up Next, drawer | Out of v1. Requires backend CONTRACT CHANGE. |
| Generic-list treatment of Up Next | Up Next | Erases the history / active / upcoming identity that the product model relies on. |
| Save current queue as playlist outside Up Next or active-player context | Library, Manual playlist, Smart playlist, Bluesky | Creates ambiguity about whose contents are being saved. |
| Visible play button on generic list row faces | Library, Manual playlist, Smart playlist, Bluesky | Tempts default-tap-plays mental model; violates "tap is read" rule. |
| Auto re-seed of Up Next on pull-to-refresh | Up Next | Refresh and re-seed are separate actions per product rule. |
| Smart playlist that auto-mutates Up Next | Smart playlist | Smart playlists may seed Up Next only via explicit action. |
| Compressed single-row mini-player dock as-is | Mini-player | Operator: too cramped; speed not reachable; title/source/controls compete for one row. |
| Speed control hidden behind long-press or auto-hiding chrome only | Mini-player, Locus | Operator: speed must be reachable. |
| Separate competing play and pause buttons | Mini-player, Locus | Operator: one consolidated stateful control. |
| Player Queue as a distinct surface before Locus/player spike | Locus, Up Next | Operator: bridge is unclear; do not commit to a Player Queue surface yet. |
| Image/thumbnail row shape for Bluesky in v1 | Bluesky | Out of v1; row grammar stays consistent with library. |

---

## 4. Behavior preservation contract

Implementation tickets that touch any of the following must explicitly
preserve the listed behaviors. A redesign that drops one of these
silently is a regression. This list is referenced by every Lane 4 / Lane
5 implementation ticket.

### 4.1 List and surface tap behavior

- Default tap on any **library** row (Inbox, Favorites, Archive, Bin)
  opens the item in **Locus**. Tap never mutates Up Next.
- Default tap on any **playlist detail** row opens the item in Locus.
  Tap must not implicitly start a now-playing session. (This is
  currently violated in `PlaylistDetailScreen.kt`; see §7-A1.)
- Default tap on any **Up Next upcoming** row opens the item in Locus
  and resumes / seeds playback per existing semantics.
- Default tap on **Bin** rows is disabled today and remains disabled.

### 4.2 Long-press

- Long-press on library and playlist-detail rows enters multi-select.
- Long-press never enters drag-reorder mode.
- Long-press is never the **sole** entry point to any action.

### 4.3 Up Next reorder and structure

- The active Up Next item is **not draggable** (`isCurrent` guard in
  `QueueScreen.kt`).
- Only **upcoming** Up Next rows are reorderable.
- Drag handles are accompanied by **TalkBack Move up / Move down**
  custom actions on every reorderable row.
- History rows (when introduced) are **not** draggable, **not**
  selectable, and **not** included in multi-select.

### 4.4 Queue actions

- Play Next / Play Last are non-destructive and never confirm.
- Play Now (library rows, shipped A3) is non-destructive: inserts the
  item at the current play position and starts audio, preserving
  existing upcoming items. No confirm required in any queue state.
  A future destructive "Replace queue / Start fresh" action is deferred;
  if introduced, it must be a distinct action with confirm.
- Refresh does not auto-re-seed. Re-seed is an explicit action with its
  own confirmation when the session diverges.
- "Save queue as playlist" exists only in Up Next and Locus contexts.
  Saves active item + upcoming items in session order; hidden
  pre-active/history rows are excluded.

### 4.5 Playlist detail

- Remove from playlist always shows a snackbar with **Undo** action
  (`ACTION_KEY_UNDO_PLAYLIST_REMOVE`). This survives any batch-bar or
  overflow refactor.
- Manual playlist drag reorder persists to server on drop.
- Playlist batch bar is **not** identical to the library batch bar; do
  not merge the two batch-bar components blindly.

### 4.6 Mini-player

- Mini-player persists across all non-Locus routes when a session is
  active.
- Mini-player is **not** rendered on the Locus route itself.
- Chevron in the mini-player **opens the navigation drawer**. This is
  load-bearing for drawer access.
- Tapping the now-playing title strip opens the full Locus view.
- Speed control must remain reachable from the mini-player. (Currently
  it is **not** reachable, which is a known gap.)

### 4.7 Locus / player

- Reader remains the primary identity of Locus.
- Speed must remain reachable even when the top bar auto-hides.
- ff/rw controls perform **sentence-level jumps** today (long-press =
  paragraph). This is the current playback-engine behavior. Any change
  to time-based jumps is a behavior change, not just an icon change.
- Auto-scroll while listening, sentence highlight, and the chevron
  control-mode cycle are unchanged.

### 4.8 Bin and archive

- Bin rows have **no queue actions** (no Play Now / Play Next / Play
  Last / Add to Up Next). Restore first, then queue.
- Archive items have queue actions identical to Inbox/Favorites.

---

## 5. Decisions now made

These were unresolved in the working reconciliation and are now decided
for execution.

| Decision | Resolution | Application |
|---|---|---|
| Snap-to-active affordance | **Floating anchor pill.** Non-modal, on top of the Up Next list, appearing when the active item scrolls out of view. Tap returns to the active item. | Up Next layout ticket carries this as the anchor mechanism. Persistent toolbar pill or menu item are not selected. |
| Mini-player layout | **Two-row (or otherwise decompressed)** layout. Title/source on top row separated from playback controls. | Mini-player control spec ticket designs around the multi-row layout, not the current single-row dock. |
| Speed control on mini-player | **Always-visible speed pill** on the mini-player. | Mini-player control spec ticket places the speed pill in the layout; long-press / hidden treatments are rejected. |
| Play/pause in mini-player and Locus | **Single consolidated stateful button.** | Already true in Locus; mini-player ticket preserves this and rejects competing-buttons treatments. |
| Mini-player ff/rw semantics | **Sentence-level for v1.** Short press remains previous/next sentence; long press remains previous/next paragraph. | Recorded in `docs/ANDROID_MINIPLAYER_CONTROL_SPEC.md`. Time-based skip is deferred and would require a separate operator decision. |
| Clear upcoming placement | **Likely near the Upcoming section header.** Final placement still confirmable in the Up Next layout ticket. | Up Next layout ticket lays out the Upcoming section header with the Clear upcoming control colocated. |
| Clear all session placement | **Overflow / contextual destructive area**, not primary chrome. | Up Next overflow menu carries this; operator-rejected as a header-level button. |
| History-row tap behavior | **Anchored / current solution preferred** (tap on a history row behaves consistent with the active-item anchoring model). Contextual / onboarding hint may be useful initially, then disable / disappear. Final hint copy and lifecycle deferred to the Up Next history ticket. | Up Next history ticket implements the anchored behavior + optional first-run hint. Other candidates (restart playback, add to upcoming) are not selected. |
| Locus / player bridge signal | **Unclear; not implementation-ready.** | No bridge UI ships until the operator can describe it in plain English. Locus/player integration is a design spike, not an implementation ticket. |
| Full player necessity | **Unresolved**, gated by the Locus/player spike. | No full-player redesign ticket is opened until the spike resolves. |

---

## 6. Open blockers

### 6.1 Blocking implementation now

Mini-player ff/rw semantics are no longer a blocker for B1. The
mini-player control spec chooses the current sentence-level behavior for
v1 and records the current long-press paragraph behavior. Icon and
visual treatment may be refined in the implementation ticket, but must
preserve sentence/paragraph labels and must not imply time-based skips.

- **Library sectioning policy (date / source / read-state).** Sectioned
  Library cannot ship without a section grouping rule. **Operator
  decision needed before sectioned-library implementation ticket.**

### 6.2 Needs design exploration

- **Locus / player integration.** Operator cannot describe the bridge
  signal in plain English. A focused design spike (Claude Design or
  in-house) must produce a behavioral description before any UI work.
- **Up Next history persistence and privacy controls.** Retention
  policy (count, time, none), purge / hide / disable controls,
  per-device vs cross-device. Tied to product model §2.3 Q2–Q4.
- **Smart playlist pinned/manual-order behavior.** Policy when pinned
  items leave the filter, are archived, or are binned. Tied to product
  model §4.5.
- **Bluesky representation model (F1 / F2 / F3).** Recommended F3, not
  ratified.
- **"Clear upcoming" exact placement and "Clear all" exact overflow
  copy.** Roughly decided (header vs overflow), final visual treatment
  is part of the Up Next layout spec ticket.
- **Onboarding / contextual hints lifecycle.** Useful early, must be
  dismissible / disable-able. The general hint pattern is unowned.

### 6.3 Can be deferred safely

- Full-player redesign (gated by Locus/player spike).
- Persisted Up Next history backend (depends on product model §2.3
  resolution).
- Cross-device Up Next sync (already deferred; backend CONTRACT
  CHANGE).
- "Clear all session" affordance — only needed if operators consistently
  ask for it; "Clear upcoming" + "Re-seed" likely cover most cases.
- Auto-hide for action bars (blocked on the disable-auto-hide setting
  per queue actions spec §6).

---

## 7. Revised execution sequence

The next 5–8 tickets, grouped. Order within each group is the order in
which they should be picked up.

### A. Low-risk implementation now

These can be opened and shipped without further design work, citing
the existing specs.

1. **A1 — Playlist detail tap behavior correction.**
   Remove the implicit `vm.startNowPlayingSession(...)` call from the
   playlist row tap path. Tap should open Locus only. Active-item
   highlight inside the playlist remains; session start happens via
   row overflow ("Play Now" once shipped) or by tapping the active item
   in Up Next.
   References: §4.1, §4.5, queue actions spec §4.1.

2. **A2 — Library "Add Selected to Up Next" batch action.**
   Add the canonical batch-bar action to Inbox / Favorites / Archive
   batch bars. **Append order: current visible list order under the
   active sort, not tap/selection order.** Selection order is invisible
   and easy to make confusing; visible-list order is the least
   surprising rule and matches what the user sees on screen. Resolves
   queue actions spec §8.3. Bin excluded.
   References: queue actions spec §3, §4.4, §10 Slice B.

3. **A3 — Library "Play Now" row overflow.** *(Shipped 2026-04-26)*
   Play Now added to Inbox / Favorites / Archive row overflow at
   position 1. Implemented as non-destructive insert-and-play: inserts
   the item at the current queue position, preserves upcoming items,
   starts audio via the engine's autoPlayAfterLoad path. No dirty-Up-Next
   confirm (see §4.4 updated). Bin excluded.
   References: queue actions spec §2, §3, §4.2.

### B. Design / spec clarification before implementation

These need at most one design decision or one short spec doc before
becoming implementation tickets. Where a question is still listed,
operator should resolve it before opening the implementation ticket.

4. **B1 — Mini-player control spec.** *(Written 2026-04-27)*
   `docs/ANDROID_MINIPLAYER_CONTROL_SPEC.md` is ready to cite from the
   implementation ticket. It covers the two-row layout, title/source row,
   controls row with consolidated play/pause, sentence-level ff/rw with
   long-press paragraph jumps, always-visible speed pill, chevron drawer
   behavior, persistence rules, and unresolved items that remain out of
   scope.
   References: §2 Mini-player row, §5 mini-player decisions,
   `docs/ANDROID_MINIPLAYER_CONTROL_SPEC.md`.

5. **B2 — Up Next history / Clear upcoming / snap-to-active layout
   spec.** *(Spec written 2026-04-27; slices 1–3 shipped 2026-04-27)*
   Three slices merged:
   - **Slice 1:** Active/upcoming scaffolding; active anchor (not
     draggable); history hidden; snap-to-active pill.
   - **Slice 2:** Clear upcoming near Upcoming header; Clear all session
     in Up Next overflow/contextual destructive area.
   - **Slice 3:** Save queue as playlist in Up Next overflow. Saves
     active item + upcoming items in session order; hidden
     pre-active/history rows excluded. No backend/API contract changes.
   History persistence, retention/privacy controls, and history-row
   queue actions remain deferred (see §6.2 and §6.3).
   References: §2 Up Next row, §4.3, §5 decisions,
   `docs/ANDROID_UP_NEXT_LAYOUT_SPEC.md`.

6. **B3 — Locus/player integration spike.**
   Pure design / writing. Operator drives. Goal: produce a plain-English
   description of when/why a Locus → player bridge appears, and what
   "full player" means relative to Locus + Up Next + mini-player. No
   implementation. Output gates B5/C-tier full-player work.
   References: §6.2, queue actions spec §3 Locus row.

### C. Deferred higher-risk implementation

These require additional design / product input before they can ship.
Listed in approximate priority order; not necessarily the next tickets.

7. **C1 — Persisted Up Next history.**
   Requires product model §2.3 Q2–Q4 resolution (retention, privacy,
   per-device). Backend may be involved (CONTRACT CHANGE flag if so).
   Implementation only after B2 is shipped and product decisions are
   made.

8. **C2 — Smart playlist pinned/live surface.**
   Requires §6.2 decision on pin behavior under archive/bin/filter
   change. Backend contract for smart playlists (Lane 6).

9. **C3 — Bluesky harvester rolling surface.**
   Requires Bluesky representation model ratification. Lane 7 backend.

10. **C4 — Full player (if still needed) / Player Queue.**
    Gated on B3 spike outcome. May be unnecessary.

11. **C5 — Sectioned Library implementation.**
    Gated on operator decision about section grouping rule (§6.1). The
    shared row grammar is already mostly in place; sectioning is the
    remaining work.

---

## 8. Claude Design follow-up brief

The operator has limited Claude Design slots remaining this week. The
next significant design ask should be the **single most leveraged**
prompt available. The strongest candidate remains the **Up Next layout
spec** (B2) — it depends on already-made decisions, the operator
already has a strong direction, and a single design pass producing
concrete pixel-level treatments would unblock the Up Next
implementation ticket.

B1 no longer requires a Claude Design slot before implementation. The
mini-player control spec is written and chooses sentence-level ff/rw for
v1; implementation can proceed from
`docs/ANDROID_MINIPLAYER_CONTROL_SPEC.md` when scheduled.

What follows is a draft prompt skeleton. Fill in the bracketed choices
before sending.

### Draft brief: Up Next layout (B2)

**Goal**

Produce a single concrete Up Next visual layout for an Android phone,
suitable as an implementation reference. Three vertical regions:
muted history above, prominent active anchor in the middle, upcoming
with drag handles below. Include a floating snap-to-active anchor pill
that appears when the active item scrolls out of view. Show the
"Clear upcoming" affordance near the Upcoming section header, and
"Clear all" inside an overflow menu. Show the row overflow `⋮` button.

**What to refine**

- Exact visual treatment of the history region (muted, smaller,
  separator). History is not draggable, not selectable.
- Active-item anchor: visually distinct (border, surface tint, or
  similar), not draggable, no remove icon.
- Upcoming rows: drag handle visible, remove icon, row overflow `⋮`.
- Floating anchor pill: position (e.g., bottom-end above the
  mini-player), copy ("Now playing" or similar), entrance/exit rule
  (appears when active item is off-screen).
- Section headers: "History", "Now Playing" (or none, if the active
  item is its own visual band), "Upcoming". Show a row count where
  appropriate.
- "Clear upcoming" near the Upcoming header — a button or icon, not a
  row.
- Up Next-level overflow menu placement, with "Clear all session" and
  "Save queue as playlist…" inside.
- Mini-player rendered below the list (this surface lives above the
  mini-player on non-Locus routes).
- A first-run inline hint near the active anchor explaining the
  region structure, dismissible.

**What to preserve (do not redesign)**

- Tap on any row opens the item in Locus.
- Active item is not draggable. Drag handles only on upcoming rows.
- TalkBack Move up / Move down on every upcoming row.
- Refresh button next to source label; refresh does not auto-re-seed.
- Re-seed is an explicit action; "Re-seed from [source]" overflow
  entry remains.
- Save queue as playlist lives only in Up Next and Locus.

**What not to introduce**

- Do not add a Player Queue as a distinct surface.
- Do not make history rows draggable, selectable, or include them in
  multi-select.
- Do not propose cross-device sync UI.
- Do not propose a "Save queue as playlist" entry on individual rows.
- Do not show a visible Play button on row faces. The `⋮` overflow
  is the only queue-action entry point on a row.
- Do not propose a Dense variant.

**What needs to be made clearer in plain English (besides the visual)**

- The exact rule for when the floating anchor pill appears and
  disappears. When the active item is partially visible, does it
  show? Threshold?
- The exact entrance for "Clear upcoming" near the Upcoming header
  vs. "Clear all" in overflow — does the user ever see both at once?
- The first-run hint copy and dismissal lifecycle.

---

## 9. Implementation gate

The following gate applies to all implementation tickets derived from
this plan.

- **No implementation ticket is derived directly from raw HTML
  wireframes.** Wireframes are design evidence, not source-of-truth.
- **Implementation tickets reference `docs/REDESIGN_COMPLETION_PLAN.md`
  plus the relevant behavior/spec doc** (e.g.,
  `ANDROID_QUEUE_ACTIONS_PATTERN_SPEC.md`,
  `ANDROID_ITEM_ACTIONS_SPEC.md`, `LIST_LAYOUT_HOMOGENIZATION_SPEC.md`,
  `PRODUCT_MODEL_POST_REDESIGN.md`).
- **Wireframe HTML/JSX artifacts under `docs/planning/Wireframe*` and
  `docs/planning/Mimeo Wireframes 3 - Standalone.html` are not moved
  into implementation scope.** They remain as local design reference
  material until explicitly ratified.
- **Each implementation ticket explicitly cites the §4 preservation
  contract** items it touches, and the manual verification step
  confirms those behaviors still hold post-implementation.

---

## Manual verification

Docs-only artifact. No code changes. Verification:

```powershell
Get-Content -Raw docs\REDESIGN_COMPLETION_PLAN.md
Get-Content -Raw docs\DESIGN_WIREFRAME_RECONCILIATION.md
Get-Content -Raw docs\DESIGN_BEHAVIOR_RECONCILIATION.md
git diff -- docs\REDESIGN_COMPLETION_PLAN.md docs\DESIGN_WIREFRAME_RECONCILIATION.md docs\DESIGN_BEHAVIOR_RECONCILIATION.md ROADMAP.md
```

Confirm:
- §2 accepted directions match operator intent.
- §3 rejected directions match operator intent.
- §4 preservation contract matches the shipped behavior inventory in
  `DESIGN_BEHAVIOR_RECONCILIATION.md`.
- §5 decisions reflect the operator feedback recorded in this session.
- §6 blockers preserve all unresolved questions.
- §7 sequence opens with low-risk Lane 5 work (A1–A3) and gates higher-
  risk work behind a B3 design spike for Locus/player.
- §8 brief is suitable for a Claude Design slot if the operator chooses
  to spend one on B2.

If any section needs adjustment, edit this doc before opening any
implementation ticket; tickets cite this doc, so it must be correct
first.

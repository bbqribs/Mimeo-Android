# Design behavior reconciliation

**Status:** Working draft. Planning-only. Does not authorize code changes.
**Date:** 2026-04-25
**Scope:** Maps current shipped Android behaviors against the Claude Design wireframe direction established in `docs/DESIGN_WIREFRAME_RECONCILIATION.md`.

**Authoritative completion plan:** `docs/REDESIGN_COMPLETION_PLAN.md`
translates this behavior inventory into the next execution sequence and
carries the §4 behavior preservation contract that implementation tickets
must cite. The behavior inventory below is unchanged; the completion plan
is the forward-looking decision layer.

## Source context

- Wireframe reconciliation: `docs/DESIGN_WIREFRAME_RECONCILIATION.md`
- Canonical product model: `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\PRODUCT_MODEL_POST_REDESIGN.md`
- List layout spec: `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\LIST_LAYOUT_HOMOGENIZATION_SPEC.md`
- Queue actions spec: `docs/ANDROID_QUEUE_ACTIONS_PATTERN_SPEC.md`
- Code inspected:
  - `app/.../ui/queue/QueueScreen.kt`
  - `app/.../ui/library/LibraryItemsScreen.kt`
  - `app/.../ui/playlists/PlaylistDetailScreen.kt`
  - `app/.../ui/player/MiniPlayer.kt`
  - `app/.../ui/player/PlayerScreen.kt` (partial; controls and dock sections)
  - `app/.../MainActivityShell.kt`

---

## 1. Current behavior inventory

### 1.1 Up Next (QueueScreen.kt + NowPlayingSessionPanel)

**Header card**
- Shows source label ("Source: Smart queue" or named playlist).
- Refresh button (with animated states: idle / refreshing / success / failure).
- Save button (opens dialog: Save URL or Paste Text).
- Overflow menu: "Pending saves" + "Re-seed from [source]".

**Session panel (NowPlayingSessionPanel)**
- Rendered only when a session exists; otherwise shows "No active session. Open an item to start one."
- Header row: "Session queue · N", "Seeded from: [label]", optionally "Current source: [label]" when different from seeded-from. Re-seed text button + Close (X) icon button.
- Single flat scrollable list of all session items. No visual distinction between history and upcoming regions — only the active item is visually distinct.

**Active item behavior**
- Distinguished by a colored (primary) border, bold title, primary color text.
- No drag handle (replaced with a Spacer).
- No remove button.
- Tap opens item in Locus (same as non-active items).

**Non-active items (upcoming + pre-active)**
- Drag handle on the left for reorder.
- Remove (X) icon button on the right.
- Tap opens item in Locus.
- TalkBack: "Move up" / "Move down" custom accessibility actions on every non-active row.

**Session-level actions**
- Re-seed: replaces the session from the selected source; shows a confirmation dialog if the current session differs from the source.
- Clear session: X button in the panel header. No undo. Clears everything.

**What is absent from current Up Next**
- No history region (rows played before the current item are in the list but not visually grouped or muted).
- No three-way split (history / active / upcoming) — it is a flat list.
- No row overflow menu on individual Up Next rows.
- No long-press multi-select.
- No "Play Next" / "Play Last" actions on rows.
- No "Save queue as playlist" action anywhere in Up Next.
- No snap-to-active affordance.
- No "Clear upcoming" action (only "Clear session" which clears everything).

---

### 1.2 Library (Inbox / Favorites / Archive / Bin)

**Navigation**
- Accessed via the left (or right per setting) navigation drawer.
- Shell shows a "☰" hamburger button + current surface label.

**Tap behavior**
- Default tap opens item in Locus (no Up Next mutation). Correct per product model §3.2.1.

**Long-press behavior**
- Enters multi-select mode; the long-pressed row is pre-selected.
- Multi-select exits on: Back button press, sort option change, or search query change.

**Row overflow**
- All library rows (except Bin) show a MoreVert (`⋮`) button.
- Overflow contains: "Play Next", "Play Last".
- "Play Now" is NOT present yet.
- Bin rows: no overflow menu (no Play Next / Play Last per spec).

**Multi-select batch action bar**
- Replaces search/sort row while active.
- Shows Close (X) + "{N} selected" label + per-surface batch icons.
- Inbox: Archive, Move to Bin, Favorite/Unfavorite toggle.
- Favorites: Archive, Move to Bin, Unfavorite.
- Archive: Unarchive, Move to Bin.
- Bin: Restore only.
- All surfaces (except Bin): optional "Add to Playlist" icon (PlaylistAdd).
- "Add Selected to Up Next" is NOT present in the batch bar on any surface.

**Refresh**
- Refresh button in the search row. Animated states (idle / refreshing / success / failure).
- Pull-to-refresh: not present (button-only).

**Sorting**
- Horizontally scrollable filter chips (sort options vary by surface).
- Inbox: Newest, Oldest, Opened, Progress + server-side sorts.

**Sectioning (Inbox only)**
- A collapsible "Pending (N)" section at the top for items with statuses: extracting, saved, failed, blocked. Collapsed by default.

**Search**
- Text search with a clear button. Triggers on keyboard submit.

**Undo / snackbar**
- Bin with undo exists for batch actions (ACTION_KEY_UNDO_BATCH). Exact undo behavior determined by ViewModel.

---

### 1.3 Manual playlist detail

**Tap behavior**
- Tap conditionally calls `vm.startNowPlayingSession(startItemId)` if: no active session, or the tapped item is already the current session item, or no playback is active. Then opens in Locus.
- This can inadvertently start a session via tap, which deviates from the "tap is read; explicit is queue" rule.

**Long-press behavior**
- Enters multi-select mode; the long-pressed row is pre-selected.

**Multi-select batch action bar**
- Close X + "{N} selected" + "Add to Up Next" (PlaylistAdd icon, uses `playLast`) + "Remove selected from playlist" (Clear icon).
- "Add Selected to Up Next" is present here (already shipped).

**Row overflow**
- MoreVert → "Play Next", "Play Last", "Move to top", "Move to bottom", "Remove from playlist".

**Drag reorder**
- Drag handle on each row; full drag implementation with edge-scroll auto-scroll.
- Reorder is persisted to server via PUT on drop.
- isSaving spinner shows during save.

**Undo / snackbar**
- "Removed from playlist." with "Undo" action (ACTION_KEY_UNDO_PLAYLIST_REMOVE).
- Batch remove: "Removed N from playlist." with "Undo".

**Header**
- Playlist name (primary color, ellipsized). Refresh button. OverflowMenu: Rename, Delete.

**Empty state**
- "No items in this playlist yet." (does not match the spec copy "This playlist is empty. Add items from the library.")

**TalkBack reorder**
- Needs manual verification. Drag-only reorder exists; no confirmed keyboard TalkBack Move up/Move down on playlist rows.

---

### 1.4 Locus (PlayerScreen.kt, compactControlsOnly = false)

**Layout**
- Full-screen reader + player dock at the bottom. No separate navigation bar.
- Reading surface occupies most of the screen.
- Top bar (ExpandedPlayerTopBar) appears on tap / auto-hide toggle; contains: title, source, progress indicator, Speed control, Refresh, Favorite toggle, Archive toggle, overflow (Play Next, Share, Open in browser, Report problem, etc.).

**Tap behavior in reader**
- Tap on reader body: toggles the top bar (reader chrome) visibility.
- Auto-scroll follows playback position when "auto-scroll while listening" is on.

**Playback controls (dock)**
- Three modes: FULL, MINIMAL, NUB.
- In FULL mode: progress slider + previous item (skip prev) + previous sentence (fast_rewind icon, sentence-level, long-press = paragraph) + play/pause + next sentence (fast_forward icon, long-press = paragraph) + next item (skip next).
- In MINIMAL mode: thin progress line + Spacer-for-prev-item + prev sentence + play/pause + next sentence + Spacer-for-next-item.
- NUB mode: minimal; chevron only.
- Chevron: opens the navigation drawer. Long-press cycles through control modes. Draggable to left/right edge.

**Speed control**
- Accessible only from the top bar (ExpandedPlayerTopBar). Not in the dock / control bar.
- Speed range 0.5x–4.0x; preset pills at 1.0, 1.25, 1.5, 1.75, 2.0.

**Navigation from Locus**
- Previous item / Next item controls in FULL mode advance/retreat through the session.
- Tapping the title/now-playing row in the control bar re-scrolls to the playback position.

**Locus overflow actions**
- "Play Next" and "Play Last" exist in the Locus top bar overflow for the active item (carried forward from Phase 5C).
- "Save queue as playlist" is NOT present in the current Locus overflow.

**ff/rw nature**
- The fast_rewind / fast_forward icons perform sentence-level jumps (not time-based rewind). Long-press jumps a paragraph.

---

### 1.5 Mini-player (MiniPlayer.kt → PlayerScreen with compactControlsOnly = true)

**Visibility**
- Shown at the bottom of all non-Locus routes when a player item is active.
- Hidden while on the Locus route (reader hides it).

**Controls rendered**
- Renders `renderPlayerDock()` only — no reader body, no top bar.
- Control bar: previous sentence (fast_rewind icon) + play/pause + next sentence (fast_forward icon).
- Progress: thin progress line (PlayerProgressLine) in minimal mode; full slider in FULL mode.
- Previous item / next item: shown in FULL mode, Spacer in MINIMAL mode.
- Chevron: present; tap opens the drawer.

**Speed control**
- NOT accessible from the mini-player. Speed is only in the full Locus top bar (ExpandedPlayerTopBar), which is not rendered in compact-only mode.

**Tap on title strip**
- Tapping the now-playing title in the control bar opens the full Locus view for the current item.

**Persistence**
- Persists across Inbox, Favorites, Archive, Bin, Up Next, playlist surfaces while an item is active.
- Not shown on the Locus surface itself.

---

### 1.6 Navigation / drawer

**Drawer destinations**
- Inbox, Favorites, Archive, Bin, Up Next (fixed).
- Playlists (user-created, listed below the fixed items).
- "Smart queue" option at the bottom.
- Settings link.
- "New Playlist" action.
- Drawer can be configured to appear on left or right (DrawerPanelSide setting).

**Drawer open/close**
- Hamburger button (☰) in the shell header opens the drawer.
- Chevron in mini-player also opens the drawer on tap.
- Back handler closes the drawer when open.

---

## 2. Design delta matrix

| Surface | Current behavior | Design direction | Preserved behavior | Behavior at risk | New behavior introduced | Open decision | Implementation caution |
|---------|-----------------|------------------|--------------------|-----------------|------------------------|---------------|------------------------|
| Up Next | Flat session list; active item border-only distinction; no history region; no row overflow; no Save queue as playlist; Clear session clears everything | Three-way split: muted history above, prominent active anchor, upcoming with drag handles below; Clear upcoming (preserves history + active); Save queue as playlist | Active item not draggable; upcoming drag reorder; TalkBack Move up/Move down; re-seed with confirmation; item tap → Locus | History rows not yet distinct (accidentally mutable or clickable if naively refactored); "Clear session" semantics will need to bifurcate into "Clear upcoming" vs "Clear all session"; Save queue as playlist entirely absent | Muted history region; "Clear upcoming" action; "Save queue as playlist"; history-row tap behavior (unresolved) | Snap-to-active trigger; history-row tap semantics; history retention policy | Do not remove the active-item drag guard when refactoring the list; do not make history rows reorderable or selectable |
| Library | Play Next / Play Last in row overflow (shipped); long-press → multi-select; no "Add to Up Next" batch action; no "Play Now" | Same row grammar; Play Now in overflow; "Add Selected to Up Next" batch action added | Tap → Locus; Play Next / Play Last; long-press → multi-select; batch archive/favorite/bin | "Add to Up Next" batch action is missing — risk of being forgotten in re-skin pass | "Play Now" overflow action; "Add Selected to Up Next" batch action | Play Now confirm behavior when Up Next is empty | Do not add a visible Play button to the row face; overflow is the entry point |
| Manual playlist detail | Tap starts session conditionally (deviates from "tap is read"); Play Next / Play Last in overflow; "Add to Up Next" in batch bar; Move to top / Move to bottom in overflow; Remove with undo | Tap → open in Locus only (no implicit session start); same overflow grammar; visible drag handles; undo on remove | Drag reorder; "Add to Up Next" batch action (already shipped); Play Next / Play Last; Remove with undo | Tap-to-start-session behavior would be accidentally preserved if not explicitly corrected; Move to top / Move to bottom not in the spec overflow order | Conforming tap behavior (read only); "Play Now" overflow | "Play from here" semantics (re-seed from a position) vs Play Now on a row | The tap-session-start logic is currently intertwined with onOpenPlayer — splitting it requires care |
| Locus | Reader + player in one screen; speed in top bar only; no "Save queue as playlist" in overflow; sentence-jump ff/rw; chevron opens drawer | Same reader-first identity; mini-player docks below reader; speed remains; Locus/player bridge for active item | Speed control; sentence-jump controls; chevron opens drawer; auto-scroll while listening | Speed becoming inaccessible if top bar auto-hides and no alternative is provided | "Save queue as playlist" in overflow; clearer bridge affordance when reader item = active session item | Locus/player integration bridge; full player necessity | Speed must remain reachable even with auto-hiding top bar |
| Mini-player | Sentence rewind + play/pause + sentence forward; progress line; chevron; no speed control; not shown on Locus route | Persistent docked mini-player; play/pause consolidated (already done); speed retained; add ff/rw (currently implemented as sentence-jump, not time-based) | Sentence-jump controls (serving as ff/rw); consolidated play/pause; chevron; progress strip | Speed control is completely absent from the mini-player currently; if design direction "retain speed" is applied, this is a gap | Speed control accessible from mini-player; rewind and fast-forward controls (sentence-jump already present) | Whether ff/rw in the mini-player should be time-based (e.g. 15s) or sentence-based as now | Adding speed to the mini-player dock requires design decision on control density |
| Full player | Is currently Locus (PlayerScreen) — there is no separate full player screen | Unresolved — no selected direction; keep as-is until design spike resolves | Not applicable | N/A | N/A | Full player necessity; Player Queue as distinct surface | Do not implement a full player redesign without resolving the design spike |

---

## 3. Behaviors at risk

Behaviors that could be accidentally lost if the wireframes were implemented naively:

| Risk | Current behavior | Why it is at risk | Mitigation |
|------|-----------------|-------------------|------------|
| Row tap opening Locus | All library, playlist, and Up Next rows open Locus on tap | A design pass that adds a visible play affordance to the row face could make the row feel "playable" and tempt moving the default tap to "play" | Enforce the "overflow is queue, tap is read" rule at every review |
| Long-press entering selection | Library rows and playlist rows all enter multi-select on long-press | If a design pass introduces a visible reorder drag handle on library rows (wrong), long-press could be repurposed | Keep drag handle on ordered surfaces only (playlist, Up Next upcoming) |
| Up Next upcoming-item drag reorder | Non-active items in the session have drag handles and TalkBack Move up/Move down | Redesigning the Up Next layout without preserving drag handles and accessibility actions would lose this | Copy the drag guard logic and TalkBack semantics explicitly to the new layout |
| Active item not being draggable | Active item has a Spacer instead of a drag handle | If a refactor treats all items uniformly, the active item could accidentally get a drag handle | The `isCurrent` guard in QueueScreen.kt must be preserved explicitly |
| Playlist remove / undo | Remove from playlist shows a snackbar with "Undo" | A redesign of the batch bar or overflow could omit the undo action key | The `ACTION_KEY_UNDO_PLAYLIST_REMOVE` snackbar pattern must survive any UI refactor |
| Play Next / Play Last in library overflow | Already shipped in Slice A for Inbox, Favorites, Archive | A re-skin of the overflow menu could silently drop these actions | Treat them as fixed positions in the canonical overflow order |
| "Add to Up Next" in playlist batch bar | Shipped in PlaylistDetailScreen | Easy to drop in a batch-bar refactor that reuses the library batch bar component | The playlist batch bar has different actions than the library batch bar; do not merge them blindly |
| Refresh behavior | Refresh is button-triggered; no pull-to-refresh; does NOT auto-re-seed | A design pass might introduce pull-to-refresh that also re-seeds, violating the "pull-to-refresh does not auto-re-seed" product rule | Verify any pull-to-refresh implementation is refresh-only |
| Mini-player persistence | Mini-player persists while navigating between non-Locus surfaces | A layout refactor that changes the shell structure could drop the mini-player from certain routes | The `showMiniPlayer` logic in MainActivityShell must be preserved explicitly |
| Speed controls | Speed accessible from Locus top bar | If the top bar auto-hides and no alternative is added, speed becomes unreachable | Speed control must remain reachable even with top bar auto-hidden (e.g., add to dock, or keep a tap-to-reveal mechanism) |
| Sentence-jump ff/rw | fast_rewind and fast_forward icons perform sentence-level jumps | A redesign that replaces them with time-based rewind (e.g. 15s) without discussion would change the playback model | Confirm whether ff/rw should remain sentence-based or move to time-based before any change |
| Locus/player bridge behavior | Tapping an Up Next row opens the same PlayerScreen (Locus). No explicit "you are reading vs. you are in player" distinction | A design pass could accidentally split reader and player into separate screens before the design spike resolves this | No full-player-screen implementation until the design spike ticket |
| Drawer/chevron behavior | Chevron in the mini-player opens the drawer; the drawer is also opened by the ☰ button | A redesign that moves the chevron elsewhere would break drawer access from the mini-player | Chevron behavior is load-bearing for navigation; changes require explicit review |

---

## 4. Behaviors gained

Behavior additions implied by the design direction:

| Behavior | Status | Notes |
|----------|--------|-------|
| History region in Up Next | Not implemented | Requires session history persistence (Lane 4); rows must be muted, non-draggable, non-selectable. Interaction semantics (tap) are an open question. |
| "Clear upcoming" action | Not implemented | Currently only "Clear session" exists. "Clear upcoming" must preserve history and active item. |
| "Save queue as playlist" | Not implemented | Belongs in Up Next toolbar and Locus overflow only. Requires a name-entry dialog. |
| Speed control in mini-player | Not implemented | Currently speed is only in the full Locus top bar. Design direction says "speed control retained" for the mini-player. |
| Play Now overflow action in library | Not implemented | Lane 5 addition. Must follow dirty-Up-Next confirm rules when upcoming exists. |
| "Add Selected to Up Next" batch action in library | Not implemented | Lane 5 addition. Already present in playlist detail. |
| Sectioned library layout | Not implemented | Design direction adopts Sectioned Library with date or logical section headers. |
| Smart playlist pinned/live split | Not yet implemented (Lane 6) | Pinned section above live section; per-row pin/unpin. |
| Bluesky rolling smart playlist surface | Not yet implemented (Lane 7) | Harvester-config header; article rows using shared row grammar. |
| "Clear all session" (secondary destructive) | Not implemented; unclear if needed | Product model allows it if clearly differentiated from "Clear upcoming." Not a design direction requirement. |
| Snap-to-active affordance | Not implemented | Desired capability; trigger/placement remain unresolved open questions. |

---

## 5. Open design decisions

Preserved from `DESIGN_WIREFRAME_RECONCILIATION.md` plus newly observed:

1. **Full player necessity.** Is a full player screen needed, or are Locus (reader + dock) and Up Next sufficient? No direction selected; design spike required before code.

2. **Snap-to-active trigger/location.** Persistent pill, mini-player tap, menu item, or gesture? Not designed; unresolved.

3. **Locus/player integration bridge.** When the reader item is the active Up Next item, what bridge affordance appears? Currently there is no explicit bridge — tapping an Up Next item opens Locus with the reader. Unresolved.

4. **History-row tap behavior.** Restart playback, open in reader, add to upcoming, or no tap action? Explicitly deferred in product model §2.3 Q5.

5. **History retention/privacy controls.** Unbounded, time-bounded, or count-bounded? Per-device in v1? Purge/hide/disable controls? Unresolved per product model §2.3 Q2-Q4.

6. **Smart-playlist pinned/manual-order behavior.** Policy when pinned items are archived or binned. Unresolved per product model §4.5.

7. **Bluesky representation model.** F1 / F2 / F3? Recommended F3; not ratified. Unresolved per product model §5.4.

8. **ff/rw semantics.** The current fast_rewind / fast_forward controls are sentence-level jumps, not time-based. The design direction calls for "rewind and fast-forward controls" but does not specify whether they should be time-based (e.g. 15s skip) or remain sentence-based. **Newly observed gap**: if "ff/rw" in the design direction implies time-based, this requires a behavior change to the playback engine.

9. **Speed control in mini-player.** Design direction says "speed control retained." Currently speed is only in the full Locus top bar. Whether speed moves to the mini-player dock, is accessible via a long-press, or via another mechanism is unresolved. **Newly observed gap.**

10. **Playlist row tap behavior.** Currently can implicitly start a session (non-conforming). Correcting this to pure "tap is read" requires a code change and a product decision about whether "Play from here" remains a playlist-level action or a row-level action.

11. **Bin queue actions.** Current Bin rows have no overflow. Product model and queue actions spec confirm this is intentional. If a design pass adds an overflow to Bin rows for visual consistency, Play Next / Play Last must be explicitly omitted.

12. **"Clear all session" vs "Clear upcoming."** Current "Clear session" erases everything. The split into "Clear upcoming" (primary) and "Clear all session" (secondary, destructive) is a product model requirement but the UX for surfacing both actions in the Up Next header is unresolved.

---

## 6. Prompt material for next Claude Design pass

### Selected design direction (summary)

- Up Next: Wireframe 1 Conservative Up Next direction. Three-region layout: muted history above, prominent active anchor in the middle (not draggable), upcoming with drag handles below. Add mini-player direction from pass 2.
- Library: Sectioned Library with shared row anatomy. Date or logical section headers. Overflow for queue actions.
- Manual playlist: Conservative Playlist. Manual order visible. Drag handles. Row overflow with Play Next / Play Last / Move to top / Move to bottom / Remove.
- Mini-player: Pass 2 direction. Persistent docked. Speed retained. Play/pause consolidated. Rewind and fast-forward added. Progress strip.
- Locus: Pass 2 direction. Reader primary. Mini-player docks below reader. Explicit bridge for active-item overlap.
- Full player: Unresolved. Do not introduce a full-player redesign.

### Behaviors that must be preserved

- Default tap on any library or playlist row opens the item in Locus (reader). Never mutates Up Next.
- Long-press enters multi-select mode. Never repurposed as reorder.
- Active Up Next item is not draggable. This is a hard constraint.
- Only upcoming Up Next rows are reorderable.
- History rows (above active item) are not draggable and not selectable.
- Speed control must remain reachable from the mini-player (currently absent; must be added).
- Sentence-jump ff/rw behavior must be explicitly confirmed or replaced with time-based before any implementation change. Do not assume the icon change = behavior change.
- Refresh does not auto-re-seed Up Next. These are separate actions.
- Play Next / Play Last are non-destructive; no confirmation dialog.
- Play Now (on library rows) requires dirty-Up-Next confirmation when upcoming items exist.
- Undo snackbar on playlist remove must survive any redesign of the overflow or batch bar.
- Mini-player persists while navigating between non-Locus surfaces.
- Chevron in mini-player opens the navigation drawer.

### Behaviors to explore in a design pass

- Snap-to-active affordance for Up Next: candidate locations are a persistent floating pill, a toolbar/menu action, or a mini-player tap shortcut.
- Speed control placement in the mini-player: could be a tap-to-expand speed pill adjacent to the controls, or accessible via long-press on the play button.
- History-row interaction semantics: what happens when a user taps a history row? (No answer selected; present options clearly.)
- "Clear upcoming" vs "Clear all session" affordance placement in the Up Next header.
- "Save queue as playlist" entry point in Up Next and Locus: consider a toolbar icon or an overflow item; avoid placing it on individual rows.
- Section header style for Sectioned Library: date-based, source-based, or read-state-based grouping.

### Behaviors not to introduce

- Do not add a visible Play button to the row face on any library or playlist surface.
- Do not make history rows draggable or selectable.
- Do not make the active Up Next item draggable.
- Do not add auto-re-seed behavior to pull-to-refresh.
- Do not expose "Save queue as playlist" outside Up Next and Locus.
- Do not add queue actions to Bin rows.
- Do not design cross-device Up Next sync UI (deferred; requires backend CONTRACT CHANGE).
- Do not introduce a "Player Queue" as a distinct surface before the Locus/player integration design spike resolves whether it is needed.
- Do not use Dense variants on any surface.

---

## Manual verification

This is a docs-only artifact. No code was changed. Verification:

```powershell
Get-Content -Raw docs\DESIGN_BEHAVIOR_RECONCILIATION.md
Get-Content -Raw docs\DESIGN_WIREFRAME_RECONCILIATION.md
```

Review both documents against the product model and queue actions spec. Confirm that all at-risk behaviors identified here are understood before opening any implementation ticket for Lane 4 (Up Next) or Lane 5 (queue actions).

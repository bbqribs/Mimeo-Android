# Android Queue Actions Pattern Spec

**Version:** 1.0
**Status:** Design-only. Authoritative for Lane 5 implementation.
**Date:** 2026-04-25
**Scope:** Play Now / Play Next / Play Last / Add Selected to Up Next / Save current queue as playlist — across all list and item surfaces.
**Canonical authority:** `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\PRODUCT_MODEL_POST_REDESIGN.md` §3 (Android pointer: `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md`)
**Extends:** `docs/ANDROID_ITEM_ACTIONS_SPEC.md` (v1.0)

---

## 1. Purpose and relationship to existing specs

`ANDROID_ITEM_ACTIONS_SPEC.md` v1.0 defines the baseline item-action model
(favorite / archive / bin / share / open-in-browser / report problem) and
was authored before multi-select (Phase 4) and session-queue actions
(Phases 5C, 6A) shipped. This spec adds the queue-action layer on top of
that baseline using the grammar from product model §3.

**This spec does not change shipped behavior.** It defines the forward model
that implementation tickets in Lane 5 will build toward.

**One supersession note:** Item-actions spec v1.0 §6 defines long-press →
open overflow menu on Up Next rows. Product model §3.2 rule 3 supersedes
this: long-press enters multi-select mode on all library and playlist-like
surfaces, including Up Next upcoming rows. The v1.0 note about long-press
was a deferral placeholder. The override is intentional and aligns with
Phase 4 batch-select infrastructure already shipped. See §4 below.

---

## 2. Action definitions

| Action | Canonical label | Semantics |
|--------|-----------------|-----------|
| **Play Now** | "Play Now" | Starts playback of this item immediately. Replaces the active item in Up Next (dirty-Up-Next confirm rules apply; see §5). Explicit overflow action — never the default tap. |
| **Play Next** | "Play Next" | Inserts this item immediately after the current active item in the upcoming queue. Non-destructive; no confirm. |
| **Play Last** | "Play Last" | Appends this item to the end of the upcoming queue. Non-destructive; no confirm. Equivalent to "Add to Up Next" in single-item context. |
| **Add Selected to Up Next** | "Add to Up Next" | Batch action: appends all selected items to the end of the upcoming queue, preserving selection order. Non-destructive; no confirm. Canonical batch-bar label. |
| **Save current queue as playlist** | "Save queue as playlist…" | Creates a new manual playlist from the current Up Next upcoming items. Only available in Up Next and Locus overflow while a session is active. Prompts for playlist name before saving. |

---

## 3. Action availability matrix

Decision rule: **"Yes (overflow)"** means the action appears in the row's `⋮`
overflow menu. **"Yes (batch)"** means the action appears in the multi-select
batch action bar. **"No"** means the action is intentionally absent on that
surface (rationale in the notes column).

| Surface | Play Now | Play Next | Play Last | Add Selected to Up Next (batch) | Save current queue as playlist |
|---------|:--------:|:---------:|:---------:|:-------------------------------:|:------------------------------:|
| Inbox rows | Yes (overflow) | Yes (overflow) | Yes (overflow) | Yes (batch) | No |
| Favorites rows | Yes (overflow) | Yes (overflow) | Yes (overflow) | Yes (batch) | No |
| Archive rows | Yes (overflow) | Yes (overflow) | Yes (overflow) | Yes (batch) | No |
| Bin rows | No | No | No | No | No |
| Manual playlist detail rows | Yes (overflow) | Yes (overflow) | Yes (overflow) | Yes (batch) | No |
| Smart playlist detail rows | Yes (overflow) | Yes (overflow) | Yes (overflow) | Yes (batch) | No |
| Bluesky harvester rows | No (v1) | Yes (overflow) | Yes (overflow) | Yes (batch, if surface supports multi-select in v1) | No |
| Up Next upcoming rows | No (already in queue; tap plays) | No (already in queue) | Move to end (reorder, not add) | No | Yes (overflow + toolbar) |
| Up Next history rows | No | Yes (overflow) | Yes (overflow) | No (history is not selectable in multi-select) | No |
| Locus overflow (active item) | No (is active) | Yes (existing — carries forward) | Yes (existing — carries forward) | No | Yes (session active) |

**Notes:**
- **Bin rows:** No queue actions. Bin items are soft-deleted. Sending a
  binned item to Up Next before restoring it creates confusing session
  state. Restore first, then queue.
- **Play Now on library rows:** Subordinate to the redesign v2 §6 rule
  that default tap does not mutate Up Next. "Play Now" is an explicit
  overflow action, not the default tap. It follows dirty-Up-Next
  confirmation rules (§5.2).
- **Bluesky harvester / Play Now:** Not in v1; harvester does not
  auto-play. Per product model §3.1, explicit Play Next / Play Last are
  the v1 entry points.
- **Up Next upcoming rows / Play Next and Play Last:** The item is already
  in the queue. The correct reorder affordance is drag or TalkBack
  move-up/move-down (already shipped). "Move to end" is a distinct
  reorder action, not "Play Last" (the latter implies adding from outside
  the queue).
- **Save current queue as playlist:** Intentionally single-sourced at Up
  Next and Locus. Offering it elsewhere creates ambiguity about whose
  contents are being saved (product model §3.1 note).

---

## 4. Interaction model by entry point

### 4.1 Default tap

Opens the item in Locus in reader-only mode. Does not mutate Up Next.
This rule is invariant across all library and playlist surfaces
(redesign v2 §6). Queue actions are never triggered by default tap.

### 4.2 Row overflow (`⋮` button)

Every row on every supported surface exposes a `⋮` overflow button.
The button must be:
- Visible without hover or focus (always rendered, not fade-in-on-hover)
- Keyboard-focusable
- TalkBack-reachable with a descriptive content description
  (`"More actions for [item title]"`)

**Canonical overflow order** (to be confirmed at implementation; items
that do not apply to the surface are omitted entirely — not greyed out):

| Position | Action | Condition |
|----------|--------|-----------|
| 1 | Play Now | Supported on surface (see §3) |
| 2 | Play Next | Supported on surface |
| 3 | Play Last | Supported on surface |
| 4 | Add to playlist… | All library + playlist surfaces |
| 5 | Remove from playlist | Playlist detail surfaces only |
| 6 | Remove from Up Next | Up Next upcoming rows only |
| 7 | Share URL | Item has a non-blank URL |
| 8 | Open in browser | Item has a non-blank URL |
| 9 | Report problem | Locus overflow only, when signed in |

This order extends (does not contradict) the canonical order defined in
item-actions spec v1.0 §4. Where v1.0 had "Play this item" in Locus at
position 1, that maps to "Play Now" here. The ordering above supersedes
v1.0's per-surface lists for the queue-action positions.

### 4.3 Long-press

Long-press enters **multi-select mode** on all library and playlist-like
surfaces. This supersedes the v1.0 behavior of long-press → open overflow
on Up Next rows.

**Surface-specific behavior:**

| Surface | Long-press result |
|---------|-------------------|
| Library rows (Inbox / Favorites / Archive / Bin) | Enter multi-select; row is pre-selected |
| Manual / smart playlist detail rows | Enter multi-select; row is pre-selected |
| Bluesky harvester rows | Enter multi-select (if multi-select supported on the surface in v1) |
| Up Next upcoming rows | Enter multi-select; row is pre-selected |
| Up Next history rows | No multi-select. Long-press has no effect in v1 (history actions are limited to single-row overflow). |
| Locus top bar | No change (no list rows here) |

Long-press must never be the **sole** entry point to any action. Every
action that long-press enables (via multi-select) must also be reachable
without long-press via the batch bar or individual overflow. See §6.

### 4.4 Multi-select batch action bar

When multi-select is active, the batch action bar appears and exposes:

| Batch action | Surfaces |
|--------------|---------|
| Add to Up Next | All library + playlist surfaces |
| Archive | Library surfaces (Inbox, Favorites, Bin) |
| Favourite / Unfavourite | Library + playlist surfaces |
| Move to Bin | Library + playlist surfaces (not Bin itself) |

**"Add to Up Next"** is the canonical batch-bar label (product model
§3.1 note: `Add Selected to Up Next` is the canonical name; the batch
bar renders it as "Add to Up Next" for space). It appends all selected
items in selection order to the end of the upcoming queue.

Batch actions are appended to existing batch infrastructure from Phase 4.
No other existing batch actions are removed.

---

## 5. Confirmation and undo rules

### 5.1 Non-destructive additive actions (no confirm)

These actions never prompt for confirmation. A snackbar with the action
result is shown instead.

| Action | Snackbar text (example) |
|--------|------------------------|
| Play Next | "Added to play next" |
| Play Last | "Added to Up Next" |
| Add to Up Next (batch) | "N items added to Up Next" |

No undo is offered for queue-position actions. Undo for queue position
is not practical (the item is now in the queue; the user can reorder or
remove it directly from Up Next).

### 5.2 Destructive or session-replacing actions (confirm required)

These actions require explicit confirmation before executing.

| Action | Trigger | Confirm dialog copy |
|--------|---------|---------------------|
| Play Now (when Up Next has upcoming items) | Tap "Play Now" in overflow | "Replace current queue and play now?" with "Replace" (destructive) / "Cancel". Reuses dirty-Up-Next confirm pattern from redesign v2 §6. |
| Play Now (when Up Next is empty or history-only) | Tap "Play Now" in overflow | No confirm needed. Up Next has no upcoming to replace. |
| Save queue as playlist | Tap "Save queue as playlist…" | Name entry dialog. Not destructive; no separate confirm beyond the dialog itself. |
| Clear upcoming | Existing affordance in Up Next | Existing confirm pattern; not redefined here. |
| Replace queue from playlist | Existing affordance | Existing dirty-Up-Next confirm pattern; not redefined here. |

### 5.3 Bin / state-flag undo

Bin from a list surface retains the existing bin-with-undo snackbar
pattern (redesign v2 §8 v1 scope). This spec does not change that rule.

Snackbar undo scope: restore item state flags only. Does not promise
restoration of Up Next queue position or playlist membership.

---

## 6. Accessibility rules

These rules are non-negotiable and apply to every surface in scope.

| Rule | Requirement |
|------|-------------|
| **No drag-only action** | Every action reachable by drag must also be reachable without drag. Up Next upcoming reorder: drag handles exist alongside TalkBack move-up / move-down (already shipped). |
| **No long-press-only action** | Every action reachable only by long-press (entering multi-select) must also be reachable through the batch bar or individual overflow. Long-press is a shortcut into multi-select, not an exclusive entry point. |
| **Row overflow always focusable** | The `⋮` overflow button on every row must be reachable via TalkBack swipe and D-pad navigation. Content description: `"More actions for [item title]"`. |
| **Batch bar actions are TalkBack-reachable** | Batch action bar items must be individually focusable and announced. "Add to Up Next" button label: "Add selected to Up Next". |
| **No action hidden behind hover** | Actions (including the `⋮` button) must not require hover or pointer proximity to be visible or focusable. |
| **Dialogs and snackbars are announced** | Confirmation dialogs must be TalkBack-announced on appearance. Snackbar messages must be announced (already true for standard Compose `Snackbar`). |
| **Auto-hide surfaces** | If an action bar or toolbar auto-hides (product model §6.3), TalkBack users and users with motor impairments must be able to disable auto-hide via a setting. Do not implement auto-hide for action bars until that setting exists. |

---

## 7. Action labels (canonical strings)

Extends item-actions spec v1.0 §8. Use these exact labels for queue-action
entries.

| Action | Label |
|--------|-------|
| Play Now | "Play Now" |
| Play Next | "Play Next" |
| Play Last | "Play Last" |
| Add to Up Next (batch bar) | "Add to Up Next" |
| Save queue as playlist (overflow) | "Save queue as playlist…" |
| Remove from Up Next (overflow) | "Remove from queue" |
| Move to end (Up Next reorder overflow, if offered) | "Move to end" |

---

## 8. Open questions (preserve; do not answer in this spec)

1. **"Play Now" on library rows with an empty or history-only Up Next:**
   The confirm dialog is skipped (§5.2). Should there be any snackbar
   feedback ("Now playing") to confirm the action? Or does Locus opening
   serve as sufficient feedback? Answer at implementation time.

2. **History-row overflow in Up Next:** §3 lists Play Next / Play Last for
   history rows. The interaction semantics for history rows are an open
   product question (product model §2.3 Q5: tap-to-restart vs
   tap-to-read vs tap-to-add). Until §2.3 Q5 is resolved, do not
   implement queue actions on history rows. Treat this cell as "deferred,
   not absent."

3. **"Add Selected to Up Next" order guarantee:** Does the batch append
   preserve the visual selection order (order items were tapped) or the
   list display order? Recommendation: list display order is more
   predictable; selection order is more intentional. Decide at
   implementation time; document the decision in the commit.

4. **Bluesky harvester multi-select in v1:** Product model §3.1 notes
   this is conditional on whether the harvester surface supports
   multi-select in v1. No decision needed here; captured as a dependency.

5. **"Save queue as playlist" name-entry UX:** Simple text-input dialog
   vs a more opinionated flow (with duplicate-name warning, etc.)? The
   spec requires a prompt; the exact dialog design is deferred to
   implementation.

---

## 9. Out of scope for this spec

- Cross-device Up Next sync (requires backend CONTRACT CHANGE; deferred).
- Smart-playlist backend contracts (Lane 6).
- Bluesky harvester ingestion model (Lane 7).
- History row tap semantics (open product question; §2.3 Q5 in product
  model).
- New list-layout row component (Lane 3 prerequisite; this spec assumes
  each surface has a `⋮` overflow button and supports multi-select via
  Phase 4 infrastructure).
- QueueScreen.kt or list UI code changes (this is a design-only spec).
- Up Next session history display layout (Lane 4).

---

## 10. First implementation slice recommendation

**Slice A: Library rows — Play Next + Play Last in row overflow.**

Surfaces: Inbox, Favorites, Archive rows only (not Bin — see §3 notes).
Actions: Add "Play Next" and "Play Last" to the row `⋮` overflow at
positions 2 and 3 (after "Play Now" if Play Now is not being shipped in
the same slice; or at positions 1–2 if Play Now is deferred to Slice B).

Rationale:
- These are additive, non-destructive actions. No confirm required (§5.1).
- The Phase 4 batch infrastructure and Phase 6A queue substrate are
  already shipped. The wiring is a ViewModel call (`playNext`, `playLast`)
  that already exists from Phase 5C.
- No new UI patterns needed: the existing overflow menu is extended.
- Locus overflow already has Play Next / Play Last from Phase 5C — Slice A
  achieves parity on library rows.

**Slice B: Batch bar — "Add to Up Next".**

Surfaces: All library + playlist surfaces with multi-select.
Action: Add "Add to Up Next" to the existing multi-select batch action bar.

Rationale: Slice A proves the per-row wiring. Slice B reuses the same
ViewModel calls in batch form, plumbing through Phase 4's
`BatchActionHandler`. Natural follow-on from Slice A.

**Slice C: "Save queue as playlist…" in Up Next toolbar and Locus overflow.**

Scope: New action; new name-entry dialog. Most self-contained of the
remaining actions because it touches only Up Next and Locus, not list
surfaces.

Sequencing note: Slices A and B may be implemented concurrently (they
touch different surfaces). Slice C requires agreement on the
name-entry dialog UX (open question 5 above) and is best done after
Slices A + B have been reviewed.

---

## 11. Relationship to ANDROID_ITEM_ACTIONS_SPEC.md v1.0

The table below clarifies which v1.0 rules carry forward unchanged and
which are superseded by this spec.

| v1.0 rule | Status in this spec |
|-----------|---------------------|
| Default tap → open in Locus | **Unchanged.** Reaffirmed in §4.1. |
| `⋮` overflow button on every row | **Unchanged.** Extended with queue-action positions. |
| Canonical overflow order (v1.0 §4.1–4.5) | **Extended.** Queue actions are inserted at positions 1–3; existing actions shift down. See §4.2 for the merged order. |
| Long-press → open overflow (Up Next rows) | **Superseded.** Long-press → enter multi-select (§4.3). This was a deferral in v1.0 §6; multi-select infrastructure is now shipped (Phase 4). |
| URL availability guard for Share / Open in browser | **Unchanged.** Still applies; not affected by queue actions. |
| Canonical label strings (v1.0 §8) | **Extended.** Queue-action labels added in §7 above. Existing labels unchanged. |

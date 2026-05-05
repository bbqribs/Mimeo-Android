# Android Playback Actions v2 Spec

**Status:** Spec checkpoint. No implementation in this ticket.
**Date:** 2026-05-06
**Scope:** Android playback / queue actions across Manual Playlist,
Smart Playlist, Inbox / Favorites / Archive, Smart Queue, and Up Next.
Defines row-action layout, tap-row-play semantics, Play All, Play from
Here, and overflow ordering. Smart Queue is a source/list surface; Up
Next is the target playback session that these actions mutate. No
backend or API contract changes.
**Extends:** `docs/ANDROID_QUEUE_ACTIONS_PATTERN_SPEC.md` v1.0,
`docs/ANDROID_UP_NEXT_LAYOUT_SPEC.md`, `docs/ANDROID_ITEM_ACTIONS_SPEC.md` v1.0.
**Canonical product authority:**
`C:\Users\brend\Documents\Coding\Mimeo\docs\planning\PRODUCT_MODEL_POST_REDESIGN.md`
§3 (Android pointer: `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md`).

---

## 1. Purpose

Queue Actions Pattern Spec v1.0 defined Play Now / Play Next / Play Last /
Add Selected / Save Queue. Slices A–C have either shipped or are in
flight. Operator review surfaced four refinements that this spec encodes
before code changes:

1. **Smart Queue** is presented as a playlist-like surface — a viewable
   source/list users can browse and play from. **Up Next** remains the
   active playback queue/session, not a playlist-like source surface.
2. Play All on a list surface starts at the top of the current visible
   order and queues downward as a snapshot.
3. Row trailing edge uses a visible **[Play] [⋮]** layout instead of
   long-press-only or `⋮`-only.
4. **Tap row Play** and **Play from Here** mutate Up Next according to
   the rules in §5 and §7 — tap-row-Play is an active swap with
   progress preservation; Play from Here replaces upcoming with
   confirmation. Both are invoked from the source surfaces (Manual
   Playlist, Smart Playlist, Inbox / Favorites / Archive, Smart Queue),
   not from Up Next rows.

These refinements are additive to the v1.0 grammar; nothing in v1.0 is
revoked except where explicitly noted in §11.

---

## 2. Surface scope and definitions

| Surface | In-scope | Notes |
|---|:---:|---|
| Manual Playlist detail | Yes (source) | Reorderable, mutable membership. |
| Smart Playlist detail | Yes (source) | Snapshot-only; smart rule preserved (§6). |
| Inbox / Favorites / Archive | Yes (source) | Library list surfaces. Bin excluded for queue actions per v1.0 §3. |
| Smart Queue | Yes (source) | Playlist-like source surface (§3); seeds Up Next via Play All / row Play / Play from Here. Not a manual playlist entity unless explicitly saved. |
| Up Next (Now Playing session) | Yes (target only) | Existing session/queue behavior only — active anchor + upcoming + history per Up Next Layout Spec. Not a playlist-like source. |
| Bin | No | No queue actions; rows must be restored first. |
| Bluesky harvester | No (v1) | Tracked separately; harvester scope unchanged here. |

Terminology used below:

- **Visible order**: the current rendered order under the active filter,
  search query, sort, and pin segmentation, top-to-bottom, at the moment
  the action is invoked.
- **Snapshot**: a frozen copy of the visible order taken at action time.
  Future changes to the source list do not retroactively update Up Next.
- **Active**: the Now Playing anchor (`NowPlayingSession.currentIndex`
  item).
- **Upcoming**: items after the active anchor in the session (per Up Next
  Layout Spec §7).
- **History/progress state**: an item that has left the active session
  but whose `_playbackPositionByItem` entry is preserved so it can resume
  later. **Not** a visible history row in v1 (Up Next layout spec §5
  defers persisted history). See §10 representational note.

---

## 3. Smart Queue as a playlist-like surface

Smart Queue is a viewable source/list surface alongside Manual
Playlist, Smart Playlist, and Inbox / Favorites / Archive. Up Next is
the playback session that Smart Queue (and the other source surfaces)
seed and mutate via the actions in §5–§7.

| Rule | Detail |
|---|---|
| Visual presentation | Smart Queue renders with playlist-style row chrome (cover, title, source, secondary line) and the same row trailing layout (§4) used by Manual / Smart Playlists and library surfaces. It can be opened and browsed like a playlist. |
| Entity status | Smart Queue is **not** automatically a manual playlist entity unless the existing product model says otherwise. Opening, viewing, and playing from Smart Queue does not create a manual playlist record. |
| Save path | If the user wants Smart Queue contents persisted as a manual playlist, the existing **Save queue as playlist…** flow (snapshot at the moment of save) is the only path. No implicit promotion. |
| Seeding Up Next | Play All, row Play, and Play from Here on Smart Queue rows mutate Up Next per §5–§7 with the standard snapshot rule (§9). |
| Drawer / nav | Drawer/navigation entry for Smart Queue is governed by the existing product model and not respecified here. This spec only governs how its rows behave once visible. |

| Rule | Detail (Up Next, target-only) |
|---|---|
| Up Next is a target, not a source | Up Next is **session state**, not a playlist-like source surface. Actions defined in this spec **target** Up Next; they are not invoked **from** Up Next rows except where v1.0 already defined (drag/remove/reorder on upcoming rows; tap-Play on upcoming per §5.1). |
| Persistence | Up Next is not stored as a manual playlist entity, does not appear in the playlist drawer, is not pinnable, and has no playlist ID. |
| Save path | The only path from Up Next to a persisted playlist is the existing **Save queue as playlist…** action (Up Next overflow / Locus overflow). That action snapshots active + upcoming into a new manual playlist. |
| Up Next row chrome | Up Next continues to use the Up Next Layout Spec's region model (history / active / upcoming). Adopting [Play] [⋮] on upcoming rows (§4) is a chrome alignment, not a promotion of Up Next to a source surface. |

---

## 4. Row trailing layout: [Play] [⋮]

Row trailing edge renders **two visible controls**: a Play icon followed
by the existing overflow `⋮`.

| Property | Rule |
|---|---|
| Order | Play icon first (left), `⋮` second (right). |
| Visibility | Both controls always rendered (no hover/focus reveal). |
| Touch target | Each control ≥ 48 dp × 48 dp, with ≥ 8 dp horizontal gap between them. |
| TalkBack labels | Play icon: `"Play [item title]"`. Overflow: `"More actions for [item title]"` (unchanged from v1.0 §6). |
| Long-press (still) | Long-press on the row body continues to enter multi-select mode (queue-actions spec v1.0 §4.3). Long-press on the Play icon or `⋮` is **not** a multi-select trigger. |
| Default tap (still) | Tapping the row body opens the item in Locus reader (v1.0 §4.1). Unchanged. |
| Empty state | If a row has neither tap-to-play nor overflow actions on the surface (e.g. Bin), the trailing controls are omitted, not greyed out (v1.0 §4.2). |

Surfaces that get the [Play] [⋮] trailing layout:

| Surface | Play icon | `⋮` |
|---|:---:|:---:|
| Manual playlist detail rows | Yes | Yes |
| Smart playlist detail rows (Pinned + Live) | Yes | Yes |
| Inbox / Favorites / Archive rows | Yes | Yes |
| Smart Queue rows | Yes | Yes |
| Up Next upcoming rows | Yes (see §5.1) | Yes |
| Up Next active row | No (already active) | Yes (Up Next-level overflow may be reachable here or in toolbar) |
| Up Next history rows (when persisted history ships) | Deferred | Deferred |
| Bin rows | No | Yes (existing non-queue actions only) |

This **supersedes** Queue Actions Pattern Spec v1.0 §4.2 to the extent
that "Play Now" was reached only from the row `⋮` overflow on library
surfaces. With [Play] [⋮], the visible Play icon is the row-level
play entry point; "Play Now" is removed from the row overflow on
surfaces that have the visible Play icon (see §8). The mini-player
appearing with audio starting still serves as feedback; no snackbar is
required (v1.0 §5.1, A3 resolution).

---

## 5. Tap-row-Play semantics

Tapping the visible Play icon on a row is a **non-destructive active
swap** that preserves the previous active item's progress.

### 5.1 Up Next upcoming row

Pre-state: active **X**, upcoming **Y, C, Z**.
User taps Play on row **C**.
Post-state: active **C**, upcoming **X, Y, Z**.

| Rule | Detail |
|---|---|
| New active | Tapped row becomes the active item; playback starts immediately. |
| Prior active | The prior active **X** is reinserted into upcoming at index 0 (immediately after the new active). |
| Other upcoming | Order of items other than the tapped one is preserved; they shift down to make room for the prior-active reinsertion. |
| Progress | `_playbackPositionByItem[X]` is preserved verbatim. When the user later plays X again, it resumes from that offset. |
| History rows | Tap-Play on a history row is **deferred** until persisted history lands (Up Next Layout Spec §5). |
| Confirmation | None. Snackbar not required; mini-player update is feedback. |

### 5.2 Source-surface row (Inbox / Playlist / Smart Queue) — item not in session

Pre-state: session active **X**, upcoming **Y Z**. Tapped row **C** is
**not** in the session.

Post-state: active **C**, upcoming **X, Y, Z**.

| Rule | Detail |
|---|---|
| Insertion | C is inserted as the new active item. |
| Prior active | X is reinserted into upcoming at index 0. |
| Other upcoming | Y, Z preserved in order. |
| Snapshot | The action operates only on the current session; it does **not** queue downward from C's source list (that is what Play All and Play from Here are for). |
| Progress | `_playbackPositionByItem[X]` preserved. |

### 5.3 Source-surface row (Inbox / Playlist / Smart Queue) — item already in session

If C is already present in the session at any position, treat tap-Play as
a **move** of C to active and re-promote prior active X to upcoming index
0. No duplicate insertion. (Mirrors v1.0 §3 dedupe rule for Play Now.)

### 5.4 No active session

If `nowPlayingSession.value == null` when the user taps Play on a row,
start a new session with that row as active and an empty upcoming list.
Equivalent to the existing `startNowPlayingSession` path with a single
queue item.

### 5.5 Active row

The active row in Up Next does **not** render a Play icon (it is already
playing). The existing tap-the-active-row behavior (open Locus) is
unchanged.

---

## 6. Play All on a source surface

A surface-level **Play All** entry (toolbar action, not row-level) starts
the session at the top of the current visible order.

| Rule | Detail |
|---|---|
| Trigger | Toolbar / surface-level action on Manual Playlist detail, Smart Playlist detail, Inbox, Favorites, Archive, and Smart Queue. |
| Snapshot rule | At action time, take a snapshot of the current visible order under active filter / search / sort. Smart playlist Pinned section comes first, then Live section, in the visible order rendered on screen. |
| Active | First item in the snapshot. |
| Upcoming | Remaining snapshot items in snapshot order. |
| Existing Up Next | Replaces the existing Up Next session. Confirmation **required** if Up Next is non-empty; copy must say *"Replace Up Next?"* and identify the new source (e.g., *"From: Inbox"* or *"From: {playlist name}"*). |
| Live sync | None. Subsequent edits/sorts on the source list do not flow into Up Next (preserves the snapshot-only rule from v1.0 and the Smart Playlist `Use as Up Next` shipped behavior). |
| Smart playlist parity | Behaves identically to the shipped *"Use as Up Next"* affordance for smart playlists. Play All on a smart playlist may be implemented as the same code path with a different label, or kept as a distinct entry; either way the snapshot-only rule is identical. |
| Empty list | Action is hidden / disabled when the visible list has zero rows. |
| Bin | Play All is not offered on Bin. |

---

## 7. Play from Here

Play from Here starts the session at the user-selected row and queues
downward to the end of the visible list.

| Rule | Detail |
|---|---|
| Entry point | Row `⋮` overflow only (not a visible row icon, not a toolbar action). Rationale: it has session-replace semantics that demand explicit confirmation; a one-tap visible icon would risk accidental queue replacement. |
| Trigger | Tap **Play from Here** on row C of a source surface (Manual Playlist, Smart Playlist, Inbox / Favorites / Archive, Smart Queue). |
| Confirmation | **Required.** Dialog copy: *"Replace Up Next with items from here down?"* with explicit Replace / Cancel. The previous-active fate (§Prior active below) must be summarized in the dialog body if non-empty: *"Currently playing item will exit Up Next; its progress is kept."* |
| Snapshot scope | At confirm time, take a snapshot of the visible-order rows from C inclusive to the end of the visible list. |
| Active | C. |
| Upcoming | Items below C in the snapshot, in snapshot order. Items **above** C in the visible list are excluded entirely. |
| Prior active | The previous active item moves to **history/progress state** — it leaves the session, is **not** added to upcoming, is **not** archived/binned, and its `_playbackPositionByItem` entry is preserved so it can resume on its next manual open. |
| Other prior upcoming | Items that were in upcoming before this action and that are not in the new snapshot are removed from the session. Their progress entries are preserved (resume-on-open). |
| Live sync | None. Same snapshot-only rule as Play All. |
| Surfaces | Manual Playlist, Smart Playlist (Pinned and Live segments), Inbox, Favorites, Archive, Smart Queue. |
| Bin | Not offered. |
| Up Next rows | Not offered (the rows are already in Up Next; reorder via drag / TalkBack instead). |

---

## 8. Overflow ordering

### 8.1 Playlist row overflow (Manual + Smart)

```
Play Next
Play Last
Play from Here
─────────────
Move to Top of Playlist
Move to Bottom
Remove
```

Notes:
- **Play Now** is **not** in the row overflow on playlist surfaces; the
  visible row Play icon (§4) is the canonical entry. This supersedes
  v1.0 §4.2 position 1 on these surfaces.
- **Move to Top of Playlist** and **Move to Bottom** apply only to
  Manual Playlists (mutable membership). On Smart Playlists, both rows
  are omitted entirely (smart membership is rule-driven and not
  reorderable). The `Remove` entry on smart rows means *unpin* in the
  Pinned segment and is **omitted** in the Live segment.
- **Add to playlist…**, **Share URL**, **Open in browser** from v1.0
  §4.2 are appended below `Remove` in the same order, separated by an
  additional divider. Rationale: keep the queue-state cluster visually
  distinct from item-state actions. Explicit appended order:

  ```
  ─────────────
  Add to playlist…
  Share URL
  Open in browser
  ```

  These are omitted on rows that lack a URL (v1.0 §4.2 condition).

### 8.2 Inbox row overflow (also Favorites, Archive, Smart Queue)

```
Play Next
Play Last
Play from Here
─────────────
Add to Playlist
Favorite
Archive
Bin
```

Notes:
- **Play Now** is **not** in the row overflow; the visible row Play icon
  (§4) is the canonical entry.
- The lower cluster matches existing item-actions semantics (favorite /
  archive / bin remain undo-capable per v1.0 §5.3).
- On Favorites surface, *Favorite* renders as *Unfavorite*. On Archive
  surface, *Archive* renders as *Unarchive*.
- On Smart Queue rows, *Favorite / Archive / Bin* are offered when the
  underlying item supports them (Smart Queue rows are real items, not
  synthetic). If Smart Queue's product model later restricts state-flag
  mutation from this surface, that override is captured in the product
  model, not here.
- **Share URL** and **Open in browser** from v1.0 §4.2 are appended
  below `Bin` in the same order, separated by an additional divider:

  ```
  ─────────────
  Share URL
  Open in browser
  ```

  Omitted on rows that lack a URL.

### 8.3 Up Next upcoming row overflow

Unchanged from v1.0 §3 / Up Next Layout Spec §7: row offers reorder
helpers (Move to end, drag, TalkBack move-up/move-down) and **Remove
from queue**. Play Next / Play Last / Play from Here are **not** offered
because the item is already in the session; tap-Play (§5.1) covers the
"play this now" intent.

---

## 9. Snapshot rule (operates on current visible order/filter/sort)

For every action in this spec that consumes more than one item (Play All,
Play from Here, Add Selected to Up Next, Save queue as playlist), the
input is the **visible order at action time**, including the effects of:

- Active filter (drawer filter, library view).
- Active search query.
- Active sort.
- Section ordering (e.g., Smart Playlist Pinned-then-Live, library date
  buckets).
- Multi-select (where applicable: only the rows the user has selected,
  but in their visible order under the rules above — not selection-tap
  order; v1.0 §8.3 already resolved this for Add Selected).

Once captured, the snapshot is independent of the source. Subsequent
edits, sort changes, filter changes, or smart-rule changes do **not**
retroactively mutate Up Next. This preserves:

- The smart-playlist snapshot-only rule (existing shipped behavior).
- The session-queue substrate guarantees from Phase 6A.
- The "no live sync between source list and Up Next" rule from the
  product model.

---

## 10. Representational note: history/progress state

The Up Next Layout Spec v1 explicitly defers persisted History (§5).
This spec relies on a softer notion of "history/progress state":

- **Session membership**: the item is removed from `NowPlayingSession`
  (no row in active or upcoming).
- **Progress preservation**: `_playbackPositionByItem` retains the
  item's last known offset.
- **No visible history row in v1**: there is currently no UI surface
  rendering history rows in Up Next.

When the user later opens such an item from a library / playlist
surface, the existing resume behavior takes effect; no special UI cue is
required by this spec. If/when persisted History ships per Up Next
Layout Spec §5, the *"previous active goes to history"* phrasing in §7
becomes literally visible. Until then it is a session-exit with progress
retained.

This is the only representational nuance in the spec. The current queue
model can express every action defined here.

---

## 11. Supersession map

| Prior rule | New rule in this spec |
|---|---|
| Queue Actions v1.0 §3: Play Now reachable from row `⋮` on library / playlist rows | Replaced on those surfaces by the visible row Play icon (§4); Play Now is removed from row overflow on surfaces that have the Play icon. |
| Queue Actions v1.0 §4.2: canonical row overflow order | Replaced by §8.1 (playlist) and §8.2 (inbox/library). |
| Up Next Layout Spec v1 §5: history is hidden until persistence ships | Unchanged. This spec does **not** introduce visible history. The "previous active → history" phrasing for Play from Here resolves to session-exit + progress retained until persisted history ships (§10). |
| Smart Playlist *"Use as Up Next"* (shipped) | Unchanged; Play All on a smart playlist may share the same code path. |
| Save queue as playlist… (shipped, Up Next + Locus overflow) | Unchanged. |
| v1.0 long-press → enter multi-select | Unchanged; long-press on row body remains the multi-select entry on all in-scope surfaces. |

---

## 12. Accessibility concerns

This spec calls out two accessibility risks that implementation must
plan for explicitly:

### 12.1 [Play] [⋮] target adjacency

Two trailing-edge controls on the same row introduce mis-tap risk,
especially on dense 3-line rows (title wrap + source + secondary).

| Requirement | Detail |
|---|---|
| Min target | 48 dp × 48 dp per control (Material accessibility). |
| Horizontal gap | ≥ 8 dp between Play and `⋮` touch surfaces. |
| Visual separation | Use distinct icon glyphs and slightly different visual weight (Play is filled / primary, `⋮` is outlined / neutral) to reduce mis-aim. |
| TalkBack | Each control is a discrete focusable node with the labels in §4. The row body itself is also focusable and announces *"Opens [item title] in reader"*. |
| D-pad / keyboard | Tab order: row body → Play → `⋮` → next row. |
| RTL | Mirrored: `⋮` appears on the leading visual edge and Play next to it; tab order remains body → Play → `⋮`. |

### 12.2 Title wrapping and three-line rows

Library and playlist rows can render up to three lines (title wraps to
2, source/secondary on line 3 — see Sectioned Library Slice 1). With
two trailing controls the title column shrinks and is more likely to
wrap.

| Requirement | Detail |
|---|---|
| Title min width | Title column must reserve enough horizontal space that titles ≥ 25 characters render at least one full word per line at standard font scale. |
| Font scale | At 200% font scale the row must remain reachable (controls do not collapse off-screen; they may stack vertically if needed, but Play and `⋮` must remain individually focusable). |
| Truncation | Title may truncate at 2 lines max; ellipsis at end. Source/secondary line truncates to 1 line. |
| Touch target spillover | Trailing controls must not visually overlap the title column at any supported font scale. |

Implementation tickets must verify the row layout at 100% / 130% / 200%
font scale and at the smallest supported device width before marking the
slice ready for review.

---

## 13. Confirmation matrix

| Action | Confirm? | Rationale |
|---|:---:|---|
| Tap row Play (§5) | No | Non-destructive: prior active retained in upcoming with progress. |
| Play Next | No | v1.0 §5.1. |
| Play Last | No | v1.0 §5.1. |
| Play All | Yes when Up Next non-empty | Replaces existing Up Next. |
| Play from Here | Yes always | Replaces upcoming and exits prior active to history/progress. |
| Move to Top / Bottom of Playlist | No | Single-row reorder within source playlist. |
| Remove (playlist) | Yes | Existing destructive pattern (membership change). |
| Remove from queue (Up Next) | No | Existing snackbar/undo pattern (v1.0). |
| Favorite / Archive / Bin | Existing patterns | v1.0 §5.3 (bin keeps undo). |

---

## 14. Stop conditions for implementation tickets

Stop and request operator input if:

- The current `NowPlayingSession` model cannot represent the active swap
  in §5 (active becomes C, prior active reinserted at upcoming index 0
  with progress preserved). Pre-check via existing
  `repository.insertItemAfterCurrent` + `repository.setCurrentIndex`
  semantics; if those primitives do not compose to the §5 result, stop.
- Play from Here cannot be implemented without removing prior-active
  progress (i.e., `_playbackPositionByItem` entries are dropped on
  session exit).
- The visible-order snapshot rule conflicts with how a surface produces
  its rows (e.g., a surface where visible order is non-deterministic).
- The [Play] [⋮] layout cannot meet the §12 accessibility targets at
  supported font scales / device widths without a layout redesign that
  extends beyond this spec.
- Implementation requires a backend or API contract change.
- The spec implies a persisted History UI surface (it does not — see
  §10).

---

## 15. Out of scope for this spec

- Persisted history UI (deferred per Up Next Layout Spec §5).
- Cross-device Up Next sync (CONTRACT CHANGE; deferred).
- Bluesky harvester row queue actions (separate track).
- Bin row queue actions (intentionally absent).
- Undo for queue position (intentionally absent — reorder/remove is the
  recovery path).
- Replace queue / Start fresh as a destructive override action (deferred
  per v1.0 §2 A3).

---

## 16. Manual verification for this spec

Docs-only verification is sufficient:

```powershell
Get-Content -Raw docs\ANDROID_PLAYBACK_ACTIONS_V2_SPEC.md
git diff -- docs\ANDROID_PLAYBACK_ACTIONS_V2_SPEC.md ROADMAP.md
```

Confirm in plain English:

- Smart Queue is presented as playlist-like; Up Next remains the
  active playback queue/session and is not introduced as a
  playlist-like source surface (§3).
- Row trailing edge is [Play] [⋮], with both controls always visible
  and individually focusable (§4).
- Tapping the row Play icon makes that row active and demotes the prior
  active to upcoming index 0, with progress preserved (§5).
- Play All starts at the top of the current visible order and queues
  downward as a snapshot, with confirmation when Up Next is non-empty
  (§6).
- Play from Here always confirms, replaces upcoming with items from the
  selected row downward, and exits the prior active to
  history/progress with progress preserved (§7).
- Playlist and Inbox overflow orderings match §8.1 / §8.2 exactly.
- Smart-playlist snapshot-only rule and "no live sync" rule are
  preserved (§9).
- Accessibility targets for [Play] [⋮] adjacency and three-line rows
  are listed (§12).
- The spec does not assume a persisted history UI (§10).

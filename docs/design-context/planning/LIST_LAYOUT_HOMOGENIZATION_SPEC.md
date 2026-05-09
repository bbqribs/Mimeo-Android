# List layout homogenization — planning spec

**Version:** 0.1 (planning draft)
**Status:** Planning / design-only. Not an implementation spec. No Android or
backend code changes authorized by this doc.
**Date:** 2026-04-24
**Scope:** Android client is the primary surface. Web parity is mentioned only
where it makes the row/surface grammar legible; web work is not authorized
here.
**Source of truth:** `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md` (the
"product model"). This spec elaborates §6.1 of the product model into a
row/surface grammar suitable for the Lane 3 implementation ticket.
**Relation to other docs:**
- Product model: `docs/planning/PRODUCT_MODEL_POST_REDESIGN.md`
- Redesign v2 plan: `docs/REDESIGN_V2_PLAN.md`
- Redesign v2 decision snapshot: `docs/REDESIGN_V2_DECISION_SNAPSHOT.md`
- Item actions v1 (closest extant grammar analogue):
  `docs/ANDROID_ITEM_ACTIONS_SPEC.md`
- Smart playlists planning: `docs/SMART_DYNAMIC_PLAYLISTS_PLANNING_V1.md`

---

## 0. Why this spec exists

The product model (§6.1) commits to the rule that **the list row shape must
not fork across list-shaped surfaces**, with Up Next as the only legitimate
exception. Lane 3 in §7 of the product model is the implementation lane that
will factor that shared shape out before smart playlists, the Bluesky
harvester, or any new list-shaped surface lands.

Before Lane 3 opens an implementation ticket, the row/surface grammar needs
to be specified concretely enough that:

1. The shared row component's API (slots, optional regions, accessibility
   contract) can be designed in one pass instead of evolving per-surface.
2. New list-shaped surfaces (smart playlists, harvester) can be costed
   against a stable scaffold rather than a moving target.
3. Reviewers can detect divergence early — "this surface adds a new region
   not in the grammar" should be a visible signal, not a quiet drift.

This document is that grammar. It is intentionally decision-table heavy and
implementation-ready; ambiguity here becomes per-surface improvisation
later.

---

## 1. Surfaces in scope

The grammar in this doc applies to the following list-shaped surfaces. They
share the base row anatomy (§3) and the surface-level grammar (§4) unless
called out as a per-surface extension (§5).

| Surface | Status | Row source | Notes |
|---------|--------|------------|-------|
| Library — Inbox / Favorites / Archive / Bin | Shipped (`LibraryItemsScreen`) | `Article` projections via library queries | Already unified pre-Lane-3; serves as the seed for the shared row component. |
| Manual playlist detail | Partially shipped | `PlaylistItem` join over `Article` | Adds position-bearing extensions (§5.1). |
| Smart playlist detail | Not yet implemented (Lane 6) | Filter definition over `Article` (+ optional manual pin overlay) | Adds filter-summary header (§5.2). Per product model §4.4 (Model B). |
| Bluesky harvester surface | Not yet implemented (Lane 7) | Smart playlist (per product model §5.3, framing F3) over `capture_kind=bluesky_harvest` items | Adds source-metadata extensions (§5.3). Inherits smart playlist scaffold (§5.2). |
| Up Next — upcoming-only region | Shipped substrate | Session-queue items below the active item | Reuses base row but **lives inside a surface that legitimately diverges**. See §6 for the boundary. |

### 1.1 Out of scope for this grammar

The following surfaces are **not** governed by this doc, even though they are
list-shaped, because their identity is not "a list of saved items":

- Locus reader (single-item playback view).
- The mini-player.
- Operator/debug tables (`/operator`, `/operator/control`,
  `/debug/failure-domains`, etc.).
- Settings/preferences lists.
- Any picker dialog (e.g., "Add to playlist" sheet) — pickers may borrow row
  grammar visually but are not surfaces in the §4 sense.

---

## 2. Design principles

These constrain every decision below.

1. **One row anatomy.** The base row is a single component with optional
   regions. Surfaces compose, they do not subclass.
2. **Extensions are additive, not replacement.** A per-surface extension
   (§5) adds a region or augments an existing one. It does not remove or
   relocate a base region.
3. **Accessibility before density.** Every interactive affordance has a
   non-drag, non-long-press equivalent (redesign v2 §14, product model
   §3.2.5). Density choices come second.
4. **Tap is read; explicit is queue.** Default tap on any list row opens
   Locus reader; queue mutation requires an explicit action (product model
   §3.2.1). The grammar must make this distinction visually obvious — the
   row is not "a play button."
5. **Up Next is the only legitimate divergence.** Other surfaces may add
   extensions (§5) but must not invent a new base anatomy. If a new surface
   wants a base-anatomy change, it reopens this spec.

---

## 3. Base row anatomy

The base row is the contract every list-shaped surface in §1 implements.
Regions are listed in visual reading order (leading → trailing). Each region
has a fixed identity; surfaces choose whether to populate it, not where to
place it.

### 3.1 Region table

| # | Region | Required? | Purpose | Accessibility role |
|---|--------|-----------|---------|---------------------|
| R1 | **Leading area** | Optional, present on most surfaces | Status indicators (read-state pip, source/capture-kind icon, in-multi-select-mode checkbox, drag handle on ordered surfaces). At most one *primary* leading element rendered at a time; multi-select checkbox displaces the others while in selection mode. | Each element has its own focusable role; checkbox announces selected/unselected state. |
| R2 | **Title line** | **Required** | Item title, single line, ellipsized. Truncation rules: head-anchor, tail-ellipsis. No badges in the title line itself. | Treated as the row's accessible name (read first by TalkBack). |
| R3 | **Metadata / source line** | Optional but standard | Source label / publication / domain, plus any per-surface metadata extension (e.g., `From @handle • 3h ago` on the harvester). Single line, ellipsized. | Read after the title; secondary content. |
| R4 | **Progress / state line** | Optional | Read progress bar + percent label, OR state badge (e.g., "Done", "Blocked", "In progress"). One slot, mutually exclusive content (a row is either progress-bearing or state-badged, not both). | Progress: announce as "X percent read". State badge: announce as a status. |
| R5 | **Trailing actions / overflow** | **Required** | Row overflow button (`⋮`) opening the canonical action sheet (product model §3.2.2). Optional: one inline trailing affordance (e.g., favorite ♥/♡ toggle on library rows). The overflow button is *always* present; it is never collapsed into the long-press menu. | Overflow button is keyboard-focusable and TalkBack-labelled per row. |
| R6 | **Selection affordance** | Conditional (any surface that supports multi-select) | Checkbox in R1 while in multi-select mode; long-press enters multi-select; overflow exposes "Select" as an explicit non-long-press equivalent (a11y). | Checkbox state announced; "Selected" / "Not selected". |
| R7 | **Queue action affordance** | Conditional (any surface where queue actions are allowed in the row, per product model §3.1) | Exposed via R5 overflow (Play Now / Play Next / Play Last / Add to Up Next). Not a separate visible button on the base row. The grammar reserves R5 as the single entry point for queue actions on a row. | Inherits R5 a11y. Each action label is unambiguous (e.g., "Play next" not "Next"). |

### 3.2 Region presence by surface

Decision table — `Y` = present, `—` = not used, `Y*` = present with extension
(see §5).

| Region | Library (Inbox/Fav/Archive/Bin) | Manual playlist detail | Smart playlist detail | Bluesky harvester surface | Up Next upcoming |
|--------|-----|-----|-----|-----|-----|
| R1 leading area | Y (status icon, multi-select checkbox) | Y* (drag handle in reorder mode) | Y (status icon, multi-select checkbox; pin marker if pinned) | Y (source icon = Bluesky) | Y* (drag handle) |
| R2 title line | Y | Y | Y | Y | Y |
| R3 metadata / source line | Y | Y | Y | Y* (`From @handle • 3h ago`) | Y |
| R4 progress / state line | Y (progress bar typical) | Y | Y | Y | Y |
| R5 trailing actions / overflow | Y (overflow + optional ♥) | Y (overflow) | Y (overflow; pin/unpin in overflow) | Y (overflow) | Y (overflow; "Remove from upcoming") |
| R6 selection affordance | Y | Y | Y | Y (if harvester supports multi-select in v1) | — (Up Next does not multi-select) |
| R7 queue action affordance | Y (overflow) | Y (overflow) | Y (overflow) | Y (overflow; no Play Now in v1 per product model §3.1) | — (already in queue) |

### 3.3 Disallowed in the base row

To keep the base row stable, the following are **not** part of the base
anatomy and must not be introduced by a surface without reopening this spec:

- A second metadata line under R3 (compress into a single ellipsized line
  with separators).
- Inline body excerpts / snippets (rendered only in detail/reader, never in
  list rows).
- Thumbnails or hero images. (Bluesky images are explicitly excluded from
  v1 per product model §5.1.) When/if image support arrives, it reopens
  this spec because it is a base-anatomy change, not a per-surface
  extension.
- Surface-specific primary buttons on the row face (e.g., a visible "Play"
  button). All play actions live in R5 overflow.

### 3.4 State and density

- One row height across all surfaces in §1 (Up Next upcoming included). No
  per-surface compact/expanded toggles in v1. Density is a future global
  setting, not a per-surface one.
- Selected state styling is identical across surfaces (single token in the
  theme). Smart playlist "pinned" state is a *leading marker*, not a
  background-color change, so it composes with selection.
- Dirty / pending-extraction state is exposed in R4 as a state badge, not a
  separate row variant.

---

## 4. Surface-level grammar

These are the surface chrome regions that wrap the row list. Like the row
regions, they have a fixed identity per surface.

### 4.1 Surface region table

| # | Region | Required? | Purpose |
|---|--------|-----------|---------|
| S1 | **Surface header / title** | Required | Surface name (e.g., "Inbox", playlist name, smart playlist name + filter summary in extension). |
| S2 | **Search / filter / sort area** | Required for library; optional for playlists (manual + smart) and harvester | Search input, filter chips, sort selector. Sort selector is always present where applicable (library, smart playlist live-sort sections). |
| S3 | **Multi-select action bar** | Conditional (surfaces with R6 enabled) | Appears on selection. Hosts batch actions: archive, favorite, bin, **Add Selected to Up Next** (product model §3.2.4), and per-surface extras (e.g., "Remove from playlist" on manual playlist detail). |
| S4 | **List body** | Required | The scrolling list of base rows (§3). |
| S5 | **Empty state** | Required | Surface-specific copy (§4.4) explaining why the list is empty. Never a generic "Nothing here." |
| S6 | **Loading state** | Required | Skeleton rows matching the base anatomy (R2 + R3 + R4 placeholders), not a centered spinner. |
| S7 | **Error state** | Required | Inline error region above the list body, with retry. Errors do not replace the list if cached rows are available. |
| S8 | **Pagination / infinite load** | Conditional | Where the row count is unbounded (library, harvester output). Smart playlist live-sort lists inherit library pagination. Manual playlists are bounded; pagination not required in v1. |
| S9 | **Floating context affordances** | Optional | E.g., "Snap to active" on Up Next (still an open question per product model §2.3). The base grammar reserves the bottom-right area for at most one such affordance; surfaces may choose to use it but not stack. |

### 4.2 Surface chrome composition rules

- S1 is always topmost. S2 sits directly under S1. S3 replaces S2 visually
  while in multi-select mode (does not stack on top of it).
- S5/S6/S7 are *region states* of S4, not separate panes — they render in
  S4's space.
- The order S1 → S2 → S4 (with S3 swapping for S2 in selection) is the same
  on every surface in §1. No surface reorders these.

### 4.3 Search / filter / sort detail (S2)

| Surface | Search input | Filter chips | Sort selector |
|---------|--------------|--------------|---------------|
| Library | Yes (FTS-backed) | Yes (read-state, source, etc., as already shipped) | Yes |
| Manual playlist detail | Optional v1 (defer if budget tight); position-sort only by default | No | No (manual order is the order) |
| Smart playlist detail | Yes | Filter summary chip(s) reflect the saved filter; tappable opens edit (per §5.2) | Yes (per-playlist sort selector, product model §4.4) |
| Bluesky harvester surface | Yes (over harvested items) | Filter chips for window / sub-filters | Yes (recency default) |
| Up Next upcoming | No (a queue is not searched in v1) | No | No (queue order is the order) |

### 4.4 Empty / loading / error state copy contract

Empty state copy is **specific** to the surface — it explains *why* the list
is empty in user-meaningful terms, then offers the next action. Generic
"Nothing here" is disallowed.

| Surface | Empty-state framing |
|---------|---------------------|
| Inbox | "Nothing in your Inbox yet." + primary action: open extension instructions / share-sheet hint. |
| Favorites | "No favorites yet. Tap ♥ on any item to save it here." |
| Archive | "Archive is empty." (No primary action.) |
| Bin | "Bin is empty. Items you delete appear here for 14 days." (Reflects 14-day trash semantics.) |
| Manual playlist (no items) | "This playlist is empty. Add items from the library." |
| Smart playlist (filter returns zero rows) | Filter summary + "No items match yet." (Per product model §4.5.) Not "Empty playlist." |
| Bluesky harvester (window has no items) | "No Bluesky links in the last [window]." Window control adjacent if available. |
| Up Next upcoming (empty) | "Nothing queued. Add items from the library or a playlist." |

Loading state: skeleton rows mirror the row anatomy of the surface (4–6
skeletons by default, never a single spinner). Error state: inline retry,
preserves any cached rows behind the error notice.

### 4.5 Pagination / infinite load (S8)

- **Library and smart playlist live-sort sections:** infinite load via the
  existing library pagination contract.
- **Bluesky harvester surface:** inherits smart playlist pagination.
- **Manual playlist detail:** no pagination in v1 (bounded by user
  curation; if a manual playlist grows beyond a practical threshold, that
  is a separate product question, not a list-layout one).
- **Smart playlist with manual pin overlay:** pinned items render above
  the live-sort section, both within S4. Pinned section is not separately
  paginated; the live-sort section is.
- **Up Next upcoming:** no pagination (the queue is the queue).

---

## 5. Per-surface extensions (allowed)

Each subsection lists the only additions a surface may make on top of the
base anatomy. Anything not listed here is not allowed without reopening
this spec.

### 5.1 Manual playlist detail

- **R1 leading area:** drag handle when the surface is in reorder mode.
  Drag handle is a leading-area element; it does not replace the status
  icon. (Redesign v2 §8 Option A: drag handle owns reorder; long-press
  still selects.)
- **R5 trailing overflow additions:** "Move to top," "Move to bottom,"
  "Remove from playlist." TalkBack "Move up" / "Move down" remain
  available as required by product model §3.2.5.
- **S3 multi-select bar additions:** "Remove selected from playlist."
- **No filter summary** (manual playlists are not filter-defined).

### 5.2 Smart playlist detail

- **S1/S2 surface header extension:** filter-summary line under the title
  ("Source: Bluesky • Last 24h • Unread"). Tapping the filter summary
  opens the filter editor. This is a header extension, not a row
  extension.
- **R1 leading area:** pin marker when an item is pinned (per product
  model §4.4 Model B).
- **R5 trailing overflow additions:** "Pin to top," "Unpin," "Freeze as
  manual playlist" (the latter is a *surface-level* action exposed in S1
  toolbar, but listed here for completeness — it is not a row action).
- **Sort selector (S2):** per-playlist sort, surfaced inline.
- **Empty-state behavior** per §4.4: filter summary visible above empty
  message.

### 5.3 Bluesky harvester surface

- **Inherits smart playlist scaffold** (§5.2). The harvester surface *is*
  a smart playlist per product model §5.3 (framing F3).
- **R3 metadata extension:** the metadata line is augmented with Bluesky
  source info — `From @handle • 3h ago` — in addition to (not replacing)
  the standard source label/domain.
- **R1 leading area:** Bluesky source icon as the status indicator.
- **R5 trailing overflow:** no "Play Now" in v1 (per product model §3.1).
- **Window-adjustment control:** lives in S1/S2 area (header or filter
  chips), not on a row. Window changes are a smart-playlist filter
  parameter; this is a header concern.
- **Image rendering:** explicitly **out of scope** in v1 (product model
  §5.1, §5.5). No thumbnail region on the row.

### 5.4 Library views — read-state / favorite / archive / bin variations

- **R1 leading area:** read-state pip (unread / in-progress / done) as the
  status indicator on the inbox view; for Bin, a distinct trashed marker;
  for Archive, no special leading marker beyond the standard read-state
  pip.
- **R5 trailing inline affordance:** ♥/♡ favorite toggle is permitted as
  the *one* inline trailing affordance (per §3.1 R5). It is the only
  inline-on-row mutation; archive/bin/done remain in overflow.
- **S2 filter chips:** read-state, source/domain, capture-kind, and the
  existing inbox filter set.
- **Bin-specific S3 batch additions:** "Restore selected," "Purge
  selected." Standard "Add Selected to Up Next" is *not* present in Bin
  (binned items should not seed playback).

### 5.5 Queue-adjacent surfaces (non–Up Next)

The general rule: queue-adjacent surfaces that present a *list of items*
(e.g., a "Recently played" list, a "Continue listening" rail rendered as a
list) follow the base anatomy and grammar in §3–§4. They get **no extra
extensions** beyond what library views get, unless a future ticket re-opens
this spec to add one.

If a queue-adjacent surface is not list-shaped (e.g., a single "Now
playing" card, the mini-player), it is out of scope (§1.1).

---

## 6. Up Next divergence boundary

The product model (§6.1) reserves **Up Next** as the one surface where the
base list shape may legitimately diverge, because the *active item +
above/below split* is part of the surface's identity. This section pins
exactly *what* may diverge and what must not.

### 6.1 What Up Next may diverge on

| Aspect | Allowed to diverge | Reason |
|--------|--------------------|--------|
| **Surface-level vertical structure** | Yes — three regions: history (above active), active item, upcoming (below active). | The session anchor is constitutive of the surface. |
| **Active-item presentation** | Yes — the active item is rendered as a distinct anchor, not as a base row. It is visually and behaviorally different (not draggable, not selectable, not tappable as "open in reader" in the same way). Per product model §2.1. | Active item is the playback anchor, not a list entry. |
| **History region row variant** | Yes — history rows may be visually muted and **must not** show queue-action affordances or selection. They are a record, not a queue. The product model defers history-row interaction semantics (§2.3 Q5). | History is "below the waterline" of the active item per product model §2.1. |
| **Floating "Snap to active" affordance** | Yes — uses S9; the trigger is an open question (product model §2.3 Q1) and is not designed in this spec. | Already memorialized in the product model. |
| **No multi-select** | Yes — Up Next omits R6/S3. | Up Next operations (reorder, remove) are per-row, not batch, in v1. |
| **No search/filter/sort (S2)** | Yes — Up Next has no S2. | A queue is not searched/filtered in v1. |

### 6.2 What Up Next must not diverge on

| Aspect | Must conform | Why |
|--------|--------------|-----|
| **Upcoming-region row anatomy** | Same R1–R5 as the base row, with extensions limited to the drag handle (R1) and "Remove from upcoming" (R5 overflow). | "Below the active item" is structurally a list of items; it inherits the row contract so reorder and accessibility behavior match other ordered surfaces (manual playlist). |
| **Tap semantics on upcoming rows** | Same as elsewhere: tap is read; queue actions are explicit. (Existing exception: tap on an Up Next row may also "jump to play" per product model §3.1 — this is the single permitted overload, already shipped.) | Keeps the "tap is read" mental model intact. |
| **Accessibility contract** | Identical: TalkBack move-up / move-down on upcoming rows; non-drag, non-long-press equivalents for every action. | Product model §3.2.5 is global. |
| **Action grammar** (Play Next, Play Last, Add to Up Next, etc.) | Identical to the cross-surface grammar in product model §3.2. | Single grammar is the whole point. |
| **Loading / error states for the upcoming region** | Same skeleton + inline error contract as §4.4. | Surface chrome below the active item is still surface chrome. |

### 6.3 Decision rule for new divergence requests

If a future ticket proposes that Up Next diverge in a way not listed in
§6.1, the default answer is **no**. Reopening §6.1 requires:

1. A product-model update (because §6.1 is grounded in product model §6.1
   and §2).
2. An explicit acknowledgement that the divergence is *constitutive of the
   surface*, not merely *convenient for the surface*.

The same rule applies in reverse for non-Up-Next surfaces seeking to
import an Up Next divergence (e.g., "what if smart playlists had an
'active item' too?"): **no**, because they do not have a session anchor.

---

## 7. Cross-surface decision matrix (summary)

One-page cross-reference for ticket reviewers. `Y` = present/required;
`Y*` = present with surface-specific extension; `—` = absent by design.

| | Library | Manual playlist | Smart playlist | Bluesky harvester | Up Next upcoming | Up Next history | Up Next active |
|---|---|---|---|---|---|---|---|
| Base row anatomy (§3) | Y | Y* | Y* | Y* | Y* | Y* (muted variant) | — (not a row) |
| Drag-to-reorder | — | Y | — (sort-driven) | — | Y | — | — |
| Multi-select (R6 / S3) | Y | Y | Y | Y (if v1 supports) | — | — | — |
| Inline ♥ favorite affordance | Y | — | — | — | — | — | — |
| Search (S2) | Y | Optional | Y | Y | — | — | — |
| Filter chips (S2) | Y | — | Y* | Y* | — | — | — |
| Sort selector (S2) | Y | — | Y | Y | — | — | — |
| Pagination (S8) | Y | — | Y | Y (inherits) | — | — | — |
| Filter-summary header (§5.2) | — | — | Y | Y | — | — | — |
| Pin marker / pin actions | — | — | Y | Y (inherits) | — | — | — |
| Active-item anchor | — | — | — | — | — | — | Y |
| "Snap to active" S9 | — | — | — | — | (surface-level) | (surface-level) | (surface-level) |

---

## 8. Acceptance criteria for the implementation lane

These are the concrete, verifiable conditions the Lane 3 implementation
ticket(s) must satisfy. They are written so a reviewer can check each one
against a PR.

1. **Single shared row component** (call it `LibraryItemRow` or
   equivalent; name TBD at implementation) implements R1–R5 with each
   region exposed as an optional slot. The component is consumed by every
   surface in §1; no surface defines its own row class.
2. **Region presence matches the §3.2 decision table.** A surface that
   uses a region not allowed for it, or omits a required region, is a
   review block.
3. **Tap on a row opens Locus reader on every surface in §1**, with the
   single existing Up Next exception ("jump to play" on an upcoming row).
   Verified by an instrumented test per surface.
4. **R5 overflow is present on every row** of every surface in §1.
   Verified by snapshot/UI test.
5. **Long-press enters multi-select on every selection-bearing surface**;
   long-press is **never** repurposed as reorder (drag handle owns
   reorder per product model §3.2.3). Verified by behavior test.
6. **TalkBack contract:** title is the row's accessible name; overflow has
   a per-row label; "Move up" / "Move down" available where reorder is
   supported; checkbox state announced. Verified by instrumented a11y
   tests (extending existing accessibility coverage in `LibraryItemsScreen`).
7. **Empty / loading / error states match §4.4 per surface.** Generic
   "Nothing here" copy is a review block.
8. **Selected-state styling token is shared.** Surfaces do not redefine
   selection visuals.
9. **No row-level base-anatomy change is introduced** by any surface
   beyond the extensions in §5. Code review checklist line.
10. **Up Next upcoming-region rows use the shared component** (with the
    drag-handle and remove-from-upcoming extensions). Verified by code
    inspection — Up Next must not maintain a parallel row class.

---

## 9. Recommended first implementation slice

Lane 3 should not land as a single mega-PR. The first slice should be the
smallest one that proves the shared-row scaffold without adding new
surfaces.

**Recommended slice: Extract the library row + library surface chrome into
a shared component, then convert manual playlist detail to consume it.**

Concretely:

1. Factor the existing `LibraryItemsScreen` row into a standalone
   composable (`LibraryItemRow`) with R1–R5 as optional slots per §3.1.
2. Factor library surface chrome (S1, S2, S3, S5, S6, S7, S8) into a
   shared scaffold (`ListSurfaceScaffold` or equivalent).
3. Convert manual playlist detail to consume both. Add the §5.1 extensions
   (drag handle, playlist overflow actions, batch "Remove selected from
   playlist"). Existing manual-playlist behavior must not regress.
4. Run the §8 acceptance criteria 1–9 against library + manual playlist;
   §8.10 deferred until the Up Next slice (next bullet).
5. **Do not touch Up Next** in this slice. Up Next conformance (§8.10) is
   a follow-up slice once the shared scaffold has shaken out on two
   surfaces. This avoids destabilizing the shipped session-queue
   substrate while the row component is still being shaped.
6. **Do not introduce smart playlist or harvester surfaces** in this
   slice. Lanes 6 and 7 build on top of the homogenized scaffold; that is
   the whole point of doing Lane 3 first.

Why this slice:
- It exercises every base region (R1–R5) and every base surface region
  (S1–S8) in a setting that already works, so any contract gap shows up
  immediately rather than being smoothed over by a brand-new surface.
- Manual playlist detail is the smallest "second consumer" available
  today; smart playlists and the harvester do not exist yet.
- Up Next is deferred one slice because conforming the upcoming region
  requires touching playback-session UI, which is shipped behavior with
  its own regression risk profile. Ship the scaffold first; conform Up
  Next second.

Slice ordering for the rest of Lane 3 (recommended, not authorized here):

1. Slice A *(recommended above)*: extract scaffold; convert manual
   playlist detail.
2. Slice B: conform Up Next upcoming-region rows to the shared component
   (§8.10), preserving the active-item and history divergences (§6.1).
3. Slice C: revisit empty/error/loading copy across all surfaces (§4.4)
   in one pass once the scaffold is real.

Smart playlist detail (Lane 6) and the Bluesky harvester surface (Lane 7)
implement on top of the finished scaffold; they are not part of Lane 3.

---

## 10. Non-goals (explicit)

This spec does **not**:

- Authorize any Android implementation. Lane 3 opens its own
  implementation ticket.
- Authorize any backend change. There is no backend contract change in
  list layout homogenization.
- Define a smart playlist backend contract. That is product model §4 and
  belongs to Lane 6 / `docs/SMART_DYNAMIC_PLAYLISTS_PLANNING_V1.md`.
- Implement Up Next session history. History persistence, retention,
  privacy controls, and history-row interaction are open questions in
  product model §2.3 and belong to Lane 4.
- Redesign Locus or the player. Locus is out of scope per §1.1.
- Authorize a thumbnail / hero image region. Image support reopens this
  spec because it is a base-anatomy change.
- Authorize a right-hand drawer (product model §6.2 reserves direction
  only).
- Define density tokens or compact-mode toggles. Density is a future
  global concern.
- Change cross-device Up Next continuity (remains a CONTRACT CHANGE
  track, see roadmap).

---

## 11. Open questions deferred (not answered here)

These are recorded so they are not lost, but answering them is **not** in
scope of this planning spec.

1. Per-surface row-action overflow ordering — the canonical order is
   proposed in product model §3.2.2 but ratification at implementation
   time is explicitly noted there.
2. "Snap to active" trigger placement (product model §2.3 Q1).
3. Smart playlist pinned-item bin/archive policy (product model §4.5).
4. Manual playlist search support (§4.3 marks it optional v1).
5. Density / compact-mode (§3.4).
6. Whether queue-adjacent surfaces beyond Up Next should exist at all in
   v1 (this spec governs them *if* they exist).

---

## 12. Reporting / next step

This document is planning-only. The next operational step is git/PR
housekeeping in a fresh low-effort session:

1. Stage `docs/planning/LIST_LAYOUT_HOMOGENIZATION_SPEC.md` (and
   `ROADMAP.md` if a pointer-sync line is added).
2. Commit on a fresh branch off `master` (planning docs do not need a
   feature branch convention beyond branch-first; see product model §8).
3. Open a PR labeled planning/docs-only; no CONTRACT CHANGE label.
4. Manual verification: none required — this is a docs-only artifact.
   Reviewer reads the spec against the product model §6.1 and confirms
   the row/surface grammar matches operator intent.

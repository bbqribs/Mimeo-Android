# Product model — post-redesign planning

**Version:** 0.1 (planning draft)
**Status:** Planning / design-only. Not an implementation spec.
**Date:** 2026-04-24
**Scope:** Android client is the primary surface. Backend and web notes appear
only where necessary to distinguish contracts.
**Relation to other docs:**
- Source of truth for redesign v2 execution rules remains
  `docs/REDESIGN_V2_PLAN.md`.
- Drift guard remains `docs/REDESIGN_V2_DECISION_SNAPSHOT.md`.
- Most recent audit: `docs/REDESIGN_V2_AUDIT_2026-04-21.md`.
- Phase 3 planning note: `docs/REDESIGN_V2_PHASE3_PLAN_NOTE.md`.

This document memorializes product-model decisions made after the redesign v2
execution track closed its primary phases (Phases 0–6A/6 shipped). It
consolidates operator intent for the next product layer — manual playlists,
smart/dynamic playlists, Up Next / session history, queue actions across
surfaces, and a Bluesky harvester — into one planning source so that future
implementation tickets can be scoped against a single reference.

**This is a planning document only.** It does not change shipped behavior,
does not alter the decision snapshot, and does not authorize implementation.
Future tickets draw from it; they do not inherit authority from it.

---

## 1. Product concept boundaries

These are the conceptual surfaces the product model has to distinguish. The
table below is the authoritative distinction map for this doc.

| Concept | What it is | Ordering owner | Persistent? | User-editable? | Can seed Up Next? |
|---------|------------|----------------|-------------|----------------|-------------------|
| **Library/list surfaces** (Inbox / Favorites / Archive / Bin) | Filtered projections of the item table based on flags (`archived_at`, `trashed_at`, `is_favorited`). Views, not collections. | Sort controls (recency, etc.) | N/A (derived) | No reorder. Item state (favorite/archive/bin) is editable. | Yes, via re-seed ("Re-seed from Inbox"). |
| **Manual playlist** | Explicit, user-owned ordered collection. Membership and order are both user-controlled. | User (drag-reorder, add/remove) | Yes (server-persisted) | Yes | Yes, via "Play playlist" / "Play from here" (re-seed with dirty-Up-Next confirm). |
| **Smart / customized / dynamic playlist** | Saved filter definition over the library, rendered as a playlist-shaped surface. May support manual pin/order layered on top of the filter result. Distinct from Up Next continuity. | Filter + sort definition (optionally overridden by a manual layer; see §4) | Yes (the definition is persisted; contents are derived) | Filter definition is editable; item ordering is editable only in the manual-layer model. | Yes, same mechanism as a manual playlist: user action re-seeds Up Next. Smart playlists do not auto-mutate Up Next. |
| **Smart Queue** (retired term) | Legacy pre-redesign backend queue logic. **Retired.** Replaced by the explicit seed-source model in redesign v2 §3.2 and by "smart playlists" in this doc. Kept in this table only to prevent accidental resurrection. | — | — | — | Do not introduce. |
| **Up Next** | The device's single playback session queue. Not a playlist. Device-local in v1 (cross-device sync deferred; requires backend CONTRACT CHANGE). | User (drag-reorder upcoming items only) + auto-advance | Yes (Room-backed, survives restart) | Yes, within session rules (see §2) | N/A (Up Next *is* the queue; it is seeded, not a seed source). |
| **Session history** | The ordered record of items the user has already played in the current/prior session(s), displayed above the active item in Up Next. | Playback chronology (append-only as items are consumed) | Yes (persisted; retention policy is an open question — see §2) | Remove-from-history is permissible; history is not draggable. | No. History is a record, not a seed. |
| **Queue seed sources** | The upstream surfaces that can be used to re-seed Up Next: Inbox (default), a manual playlist, a smart playlist. | Owned by the source | N/A | Via source | Yes, by definition. |
| **Bluesky article harvester** | Source integration that ingests article/video links from Bluesky posts within a rolling time window. Feeds a smart-playlist-shaped surface. | Harvester configuration (window, filters); ordering inherited by the smart-playlist surface | Yes (the config); content is derived and rolls off by window | Config editable; per-item send-to-playlist / send-to-Up-Next is user-driven. | Not automatically. Explicit user action required. |

**Key boundaries to keep sharp:**
- Library views are not collections. Do not expose manual reorder there.
- Manual playlists own position. Smart playlists derive position from rules,
  with an optional manual-layer override (see §4).
- Up Next is a session construct, not a playlist. It consumes seeds; it does
  not *become* the source.
- Session history is below-the-waterline of the active item and is a record,
  not a queue.
- The Bluesky harvester is a *source*; the *surface* it feeds is smart-playlist
  shaped. Keep those two ideas distinct.

---

## 2. Up Next / session model

### 2.1 Settled decisions (memorialized)

- **Up Next is the current playback session surface.** It is not a playlist
  and is not a library view.
- **Session history is persisted but optional.** Persistence is default-on;
  user control over retention and privacy is an open question (see §2.3).
- **Layout anchored on the active item:**
  - Above the active item: session history (already-played items).
  - Active item: the playback anchor. Visually and behaviorally distinct.
  - Below the active item: the upcoming session queue.
- **The active item is not draggable.** Reorder is restricted to upcoming
  items below the active item.
- **Only upcoming items below the active item are reorderable.** History is
  not reorderable.
- **No design work should be invested in dragging the active item downward
  into the upcoming queue at this time.** If this is ever wanted, it reopens
  as a separate product question.
- **Primary clear action: "Clear upcoming."** Removes the below-active
  queue. Preserves history and the active item.
- **Secondary (destructive): "Clear all session"** may exist only if later
  justified. It is explicitly a more aggressive action than "Clear upcoming."
  Must be clearly differentiated if shipped.
- **"Replace queue from playlist / smart playlist"** is permitted with
  explicit confirmation, and reuses the existing dirty-Up-Next confirmation
  pattern (redesign v2 §6 "dirty Up Next" definition applies).
- **"Snap to active" (scroll-to-active-item) is a desired capability.** The
  trigger/control surface is unresolved and must not be designed in this
  planning doc. Captured as an open UX question.

### 2.2 Reordering grammar

| Region | Drag-to-reorder | TalkBack move-up / move-down | Remove |
|--------|-----------------|------------------------------|--------|
| History (above active) | No | No (move semantics don't apply) | Optional; if present, a single "Remove from history" action |
| Active item | No (not draggable) | No | Remove = "Skip current" semantics; handled by playback controls, not drag |
| Upcoming (below active) | Yes | Yes | Yes (per-row remove; existing session-queue substrate) |

### 2.3 Open questions (preserve; do not prematurely answer)

1. **Snap-to-active trigger.** What control / gesture actually invokes it?
   Candidates to consider later: a persistent pill on scroll-off, a
   tap-on-mini-player behavior when Up Next is already open, a menu item, a
   gesture. No preferred answer at this stage.
2. **History retention.** Unbounded? Time-bounded (e.g., last N days)?
   Count-bounded (e.g., last 200 items)? Distinct per-session retention vs
   rolling-global retention?
3. **History scope.** Is history per-device initially, with sync deferred to
   the eventual Up Next cross-device continuity contract (redesign v2
   snapshot rule 14)? Operator expectation is per-device v1, but this should
   be confirmed at the ticket that ships history.
4. **History privacy controls.** Should history be hideable, purgeable, or
   entirely disableable for privacy? Especially relevant if/when sync
   ships.
5. **History interaction semantics.** Tapping a history item — does it
   restart playback of that item (treating history as a time-travel cursor,
   which conflicts with the "active item is the anchor" rule), or does it
   open the item in reader mode only, or does it add to upcoming? Keep
   unresolved; answer when the history surface is designed.

### 2.4 Invariants that remain locked

From `REDESIGN_V2_DECISION_SNAPSHOT.md` — these continue to govern any Up Next
work. The planning above does not change them.

- Up Next is device-local in v1; cross-device sync requires backend CONTRACT
  CHANGE and is not reopened here.
- Up Next edits never mutate source playlists.
- Re-seed is explicit; pull-to-refresh does not auto-re-seed.
- The session current-item pointer advances only on playback start, not on
  navigation/browse.

---

## 3. Queue actions across surfaces

A single action grammar should govern how items enter, leave, and move within
Up Next, regardless of origin surface. Divergence between long-press, row
overflow, and multi-select batch actions is the main risk to guard against.

### 3.1 Per-surface action matrix

| Surface | Play Now | Play Next | Add to end of Up Next | Add Selected to Up Next (batch) | Save current queue as playlist |
|---------|----------|-----------|-----------------------|----------------------------------|-------------------------------|
| Inbox / Favorites / Archive / Bin rows | Yes (default tap → Locus reader; "Play Now" = explicit action that starts playback) | Yes (overflow) | Yes (overflow, "Play Last") | Yes (multi-select → batch) | **No** (Up Next only) |
| Manual playlist detail row | Yes (via "Play from here" — re-seed semantics; see redesign v2 §6) | Yes (overflow) | Yes (overflow) | Yes (multi-select → batch) | **No** |
| Smart playlist detail row | Yes (same re-seed semantics as manual playlist) | Yes | Yes | Yes (multi-select → batch) | **No** |
| Bluesky harvester row | Not in v1 (harvester does not auto-play) | Yes (explicit) | Yes (explicit) | Yes, if the harvester surface supports multi-select in v1 | **No** |
| Up Next row | Jump-to-play (tap plays; already shipped) | N/A (already in Up Next) | Move-to-end (reorder, not add) | N/A | **Yes** |
| Locus player overflow (active item) | N/A (is active) | Yes (existing) | Yes (existing) | N/A | Yes (Up Next context is active) |

**Notes on the matrix:**
- "Play Now" semantics on a library row should remain subordinate to the
  current "tap opens in reader" contract (redesign v2 §6: tap in a library
  view does not mutate Up Next). If shipped, "Play Now" is an *explicit
  action in the row overflow*, not the default tap.
- "Save current queue as playlist" is intentionally single-sourced: only Up
  Next (and, equivalently, the Locus overflow while a session is active).
  Offering it elsewhere creates ambiguity about whose contents are being
  saved.
- `Add Selected to Up Next` is the canonical name for the multi-select batch
  action. It appends (equivalent to "Play Last" applied to the whole
  selection, preserving selection order).

### 3.2 Shared action grammar

To keep divergence bounded, item interaction should follow one grammar across
surfaces:

1. **Default tap** (library / playlist / smart playlist): open item in Locus
   in reader-only mode. No Up Next mutation. (Redesign v2 §6 rule.)
2. **Row overflow** exposes the canonical action set in a canonical order.
   Proposed canonical order — to be ratified at implementation time:
   1. Play Now (if supported on the surface)
   2. Play Next
   3. Play Last / Add to Up Next
   4. Add to playlist…
   5. (Surface-specific: remove from playlist, remove from Up Next, etc.)
   6. Share URL
   7. Open in browser
   8. Report problem (existing item-actions v1 spec)
3. **Long-press** selects (enters multi-select mode) in library and
   playlist-like surfaces. Long-press in ordered surfaces (Up Next upcoming,
   playlist detail) continues to follow redesign v2 §8 Option A (drag handle
   owns reorder; long-press still selects). **Long-press must not be
   repurposed as "reorder" on any surface.**
4. **Multi-select batch bar** exposes `Add Selected to Up Next` as a
   first-class batch action alongside existing batch actions (archive,
   favorite, bin).
5. **Accessibility:** every action reachable by long-press or drag must have
   a non-drag, non-long-press equivalent (redesign v2 §14). Specifically:
   - TalkBack-announceable "Move up" / "Move down" for upcoming Up Next
     rows (shipped).
   - Keyboard-focusable row overflow button on every row.
   - No action may be drag-only or long-press-only.
6. **Confirmation expectations:**
   - **Destructive session actions** (Clear upcoming, Clear all session,
     Replace queue) require explicit confirmation. "Replace queue" follows
     the existing dirty-Up-Next confirmation pattern.
   - **Non-destructive additive actions** (Play Next, Play Last, Add
     Selected to Up Next) do not prompt. Snackbar feedback is sufficient.
   - **Bin from a list surface** retains the existing bin-with-undo
     pattern.
7. **Snackbar undo scope** stays within redesign v2 §8 v1 scope: restore
   state flags only; do not promise restoration of playlist membership or
   Up Next position.

### 3.3 Common implementation pattern

A cross-surface "queue-actions provider" abstraction (naming TBD at
implementation time) should concentrate the Play Now / Play Next / Play Last
/ Add-to-Up-Next wiring so that divergence cannot creep in per-surface. The
`item-actions v1` spec (`docs/ANDROID_ITEM_ACTIONS_SPEC.md`) is the existing
closest analogue; extending that spec rather than creating a parallel one
is the preferred approach.

---

## 4. Smart playlists / smart views

Smart playlists are the main v1-scope addition beyond the redesign v2 model.
They must be clearly distinct from Up Next continuity and from session
history.

### 4.1 Memorialized decisions

- **Smart playlists are distinct from Up Next continuity.** A smart
  playlist is a filter-driven library surface, not a session surface.
- **V1 anchor: saved filter / smart view.** The core data model is a
  persisted filter definition that produces an ordered item list.
- **Manual ordering must be available as an option**, even in v1, for at
  least the hybrid case (see §4.3).
- **Smart playlists may seed Up Next; they do not become Up Next.** Same
  seed/re-seed semantics as manual playlists (redesign v2 §6).
- **Filter dimensions supported in v1:**
  - keyword (title/body, substring or token match — exact match grammar TBD)
  - publication / source label
  - domain
  - capture / save type (share-sheet save, manual URL, paste-text, Bluesky
    harvester, etc.)
  - date saved / age window (e.g., last 24h, last 7d, custom range)
  - archived-inclusion toggle
  - favorited-inclusion toggle
  - done / read-state inclusion (unread / in-progress / done)

### 4.2 Tension: live dynamic ordering vs manual ordering

A smart playlist has two conflicting instincts:
- **Live dynamic:** contents and ordering reflect the filter + sort at read
  time. New saves that match appear; old saves that fall out of scope
  disappear.
- **Manual:** the user has pinned or reordered items and expects the order
  to stick.

Naively picking one loses value: live-only throws away user curation; manual-
only throws away the auto-freshness that makes smart playlists useful in the
first place.

### 4.3 Four candidate v1 models (compared)

| Model | Behavior | Pros | Cons | Fit |
|-------|----------|------|------|-----|
| **A. Live saved filter only** | Filter runs at read time; order is pure sort output. No manual intervention. | Simplest. Cheapest. No membership drift. Matches "smart view" naming. | No user curation. Power users will re-ask for pin/order. No accommodation for the Bluesky harvester case where users want to promote an individual item. | Thin. |
| **B. Saved filter + manual pin/order layer** | Filter produces a base list; a separate manual layer allows pinning items to the top and/or overriding position. Pins survive even if the filter would normally exclude; unpinned items remain live. | Preserves live freshness by default. Adds curation only when the user asks for it. Minimal model surface for v1. | Edge cases when a pinned item leaves the underlying item set (bin/archive): policy needed. Small UI surface for pin/unpin. | **Recommended v1.** |
| **C. Generated snapshot** | "Create playlist from filter" is a one-shot action that materializes the filter output into a plain manual playlist. From that point on it is a manual playlist; the filter is not retained. | Reuses existing manual-playlist machinery entirely. No new runtime model. | Loses auto-freshness (the main value of smart playlists). Belongs as a *secondary* action, not the primary model. | Keep as a secondary export action, not the smart-playlist mental model. |
| **D. Hybrid smart playlist with display-mode options** | The playlist has a configurable display mode: live-sort, manual-order, or pinned-then-live. Single entity, multiple view configurations. | Flexible; user picks the right semantics per-playlist. | Complex. Four-way surface area for any single feature change. Easy to ship a confusing UI. | Overbuilds v1. Defer to v2+ if model B proves insufficient. |

### 4.4 Recommended v1

**Model B: saved filter plus optional manual pin/order layer.** Specifically:

- **Default behavior** for a new smart playlist: live saved filter, sort
  controlled by a sort selector (recency / oldest / custom per playlist).
  No manual layer.
- **Optional manual layer:** user can explicitly "Pin to top" (or "Pin at
  position N") on a row. Pinned items are stored in a per-playlist manual
  overlay.
- **Ordering composition at read time:** pinned items in their pinned
  positions, then filter output filtered-to-exclude-pinned, in live sort
  order.
- **Model C available as a secondary export action** ("Freeze as manual
  playlist") on a smart playlist's toolbar. Creates a brand-new manual
  playlist; the smart playlist is unaffected.
- **Model D deferred.** Revisit once model B is in use and real pain points
  are observed.

### 4.5 Edge cases to resolve at implementation time (not now)

- Pinned item bin/archive policy: does the pin persist (visibly muted),
  auto-unpin, or disappear silently?
- Pinning an item into a position the filter would never place it:
  persistence, renumbering semantics after filter changes.
- Empty-state: smart playlist with a filter that returns zero rows — show
  filter summary + "no items match yet," not a generic empty playlist
  state.
- Duplicate suppression across multiple smart playlists with overlapping
  filters: no v1 handling; duplicates across playlists are acceptable.

### 4.6 Relationship to Up Next

- A smart playlist can be the seed source for Up Next via the same
  "Play playlist" / "Play from here" / "Replace queue from playlist"
  actions that manual playlists support.
- Up Next, once seeded, is independent. Subsequent changes to the smart
  playlist's filter or membership do **not** retroactively mutate Up Next.
  (Same rule as manual playlists.)
- A `seeded_at` timestamp on Up Next reflects the moment of seeding; this
  is useful for smart playlists where the source is live and may drift.

---

## 5. Bluesky harvester model

The Bluesky automatic article harvester is both an **ingestion source** and a
**content surface**. Conflating those two roles is the biggest trap; keep
them distinct.

### 5.1 Memorialized decisions

- **Harvester is a source/integration**, not a playback construct.
- **It feeds a rolling smart-playlist-shaped surface.** The surface is
  where the user sees, curates, and promotes harvested items.
- **Default rolling window: 24 hours.** Harvested items older than the
  window roll out of the surface unless promoted.
- **Window is adjustable later**, either in playlist display options or
  smart playlist settings. Not a v1 launch requirement; v1 can ship with a
  fixed 24h default and adjustment wired in the following lane.
- **Content included in v1:** Bluesky post text and any outbound article /
  video links. **Images are excluded in v1** (no image ingestion or
  thumbnailing).
- **Manual promotion supported:** individual harvested items can be sent to
  a playlist (manual or smart) or to Up Next via the standard queue-actions
  grammar (§3). No auto-promotion to Up Next in v1.
- **No automatic Up Next mutation.** The harvester does not change Up Next
  unless the user explicitly invokes Play Next / Play Last / Add to Up Next.
- **48h is an adjustable alternate window**, not a separate product. Any
  window within reasonable bounds (e.g., 1h–30d) is a future configuration
  choice, not a new surface.

### 5.2 Representation: three candidate framings

| Framing | Definition | Pros | Cons |
|---------|------------|------|------|
| **F1. Special smart-playlist template** | Harvester is modeled as a smart playlist whose filter is the Bluesky source + rolling window + (optional) sub-filters. Single surface type; no new concept. | Reuses §4 smart-playlist model directly. Window becomes a filter parameter. Manual pin/order (§4.4) gives natural curation. | Hides the ingestion story: the "smart playlist" is only populated because a harvester is running. If the harvester is paused, the playlist silently empties. |
| **F2. Source-specific smart playlist** | A distinct playlist *kind* (`smart-playlist-bluesky`) with bespoke UI affordances for Bluesky-specific fields (post text preview, post author, repost/quote markers). | Cleaner UX for Bluesky-specific fields. Can evolve independently of generic smart playlists. | Risks forking smart-playlist UI. Expensive for a first integration. |
| **F3. Separate ingestion automation + generated smart playlist view** | Harvester is a named background automation. It writes items into the library (with a `source=bluesky_harvest` capture type). A smart playlist (user-created or system-seeded) filters on that capture type + window to render the surface. | Clean separation: ingestion vs presentation. Harvester and surface can be configured independently. Reuses capture-type filter (§4.1) and smart-playlist model directly. Matches how Bluesky items already land in the library via the unified item model. | Two concepts (ingestion + view) to teach the user. Slightly more UI surface to explain. |

### 5.3 Recommended v1 framing

**F3 — separate ingestion automation plus a generated smart playlist view.**

Reasoning:
- The ingestion automation has its own lifecycle (enable/disable, credentials,
  window config, error reporting, rate limits). Bundling that into a
  "smart playlist" conflates configuration with content.
- Presenting harvested items as a smart playlist reuses the §4 surface
  directly — no bespoke list UI.
- The harvester can be paused, reconfigured, or disconnected without
  destroying the user's curation (pins survive, filter survives).
- Future harvesters (RSS, Mastodon, Pocket import, etc.) plug into the
  same pattern: "automation writes items in; smart playlist filters them
  out."

This is a *framing recommendation*, not an implementation decision. The
implementation ticket will need backend coordination (new `capture_type` or
`source` values on items, CONTRACT CHANGE if schema changes are required).

### 5.4 Open question retained

**Is the harvester represented as F1, F2, or F3?** Recommended above as F3,
but final ratification happens at the implementation-scoping ticket. If
backend constraints make F1 significantly cheaper, the tradeoff reopens.

### 5.5 Out-of-scope for v1 (explicit)

- Image ingestion / thumbnailing.
- Repost / quote-post resolution beyond capturing the outbound link.
- Author-level subscribe/mute/block. (Smart-playlist filter on author is
  the workaround.)
- Automatic send-to-Up-Next based on harvester triggers.
- Back-dated ingestion beyond the configured window.

---

## 6. Interface / UI follow-up notes

These are design directions, not UI specs. They belong to their respective
implementation lanes (§7) and should not be over-designed here.

### 6.1 List-layout homogenization

- The list row shape (primary title, metadata strip, trailing overflow,
  leading status indicators) should be the same across:
  - Inbox / Favorites / Archive / Bin (already unified via
    `LibraryItemsScreen`)
  - Manual playlist detail
  - Smart playlist detail
  - Bluesky harvester surface (which is a smart playlist per §5.3)
  - Queue-adjacent surfaces where appropriate
- The one place the list shape legitimately diverges is **Up Next**, because
  the session anchor (active item + above/below split) is part of the
  surface identity.
- Row *extensions* (e.g., "From @handle • 3h ago" on the harvester view)
  are permitted, but the base row anatomy must not be forked.

### 6.2 Right-hand drawer alignment

- If a right-hand drawer is introduced (distinct from the existing left
  navigation drawer), its content should be left-aligned internally —
  matching the body-text rhythm, not the drawer edge.
- This is a design-direction note; does not authorize a right-hand drawer
  in any particular ticket.

### 6.3 Focus / action bar auto-hide

- The focus/action bar should slide away after a configurable delay while
  idle, restoring on interaction.
- The auto-hide behavior must be disableable or adjustable via a setting.
- Accessibility concern: TalkBack users and users with motor impairments
  must be able to disable auto-hide entirely.

### 6.4 Snap-to-active (restated from §2)

- Capability is desired; trigger and placement remain an open UX question.
  Do not pre-design it here.

---

## 7. Next implementation lanes

Staged sequence, not individual mega-tickets. Each lane lists: why the order,
main repo affected, model choice for planning vs implementation work,
dependencies, and what stays deferred.

### Lane 1 — Roadmap / workflow / transition cleanup

- **Why first:** The roadmap currently mixes shipped and forward-looking
  items; the "Next tickets" framing from the April audit is partially
  stale. Future lanes need a clean backlog to slot into.
- **Main repo:** Mimeo-Android.
- **Model choice:** low-effort session is sufficient. Mostly editorial.
- **Dependencies:** none.
- **Deferred:** no product-model changes; no new tickets opened beyond
  pointer sync to this planning doc.

### Lane 2 — Product model consolidation

- **Why second:** This doc is the first artifact. Lane 2 is the work to make
  it operationally useful: cross-reference with `REDESIGN_V2_PLAN.md`, flag
  any contradictions, possibly fold the retired "Smart queue" nomenclature
  out of the README.
- **Main repo:** Mimeo-Android (docs only).
- **Model choice:** low-effort session for pointer/fix editing; high-effort
  session only if an audit uncovers a genuine contradiction needing a
  design call.
- **Dependencies:** Lane 1 complete so pointers are stable.
- **Deferred:** no entity/model code changes.

### Lane 3 — List layout homogenization

- **Why third:** Before adding new list-shaped surfaces (smart playlists,
  harvester), the shared row component and surface scaffolding should be
  factored out cleanly so new surfaces are cheap to add.
- **Main repo:** Mimeo-Android.
- **Model choice:** high-effort session (touches shared UI code, a11y,
  tests).
- **Dependencies:** Lanes 1–2 (so the forward roadmap is aligned).
- **Deferred:** no backend work. No new surface added yet.

### Lane 4 — Up Next / session history + upcoming-queue layout

- **Why fourth:** Session history is memorialized but not implemented;
  Clear-upcoming / Clear-all-session / Replace-queue copy and affordances
  are not finalized; snap-to-active is unresolved. Pinning the Up Next
  surface shape before smart-playlist Play-playlist actions ship avoids
  re-work in both directions.
- **Main repo:** Mimeo-Android primarily. No CONTRACT CHANGE (history
  stays device-local in v1).
- **Model choice:** high-effort session for design; splittable into smaller
  implementation tickets once scoped.
- **Dependencies:** Lane 3 (list layout) so history and upcoming share the
  same row grammar.
- **Deferred:** Cross-device Up Next continuity. Snap-to-active trigger
  decision if still unresolved at this point.

### Lane 5 — Common queue-action pattern across list and item surfaces

- **Why fifth:** Once Up Next's shape is pinned (Lane 4) and list layout
  is homogenized (Lane 3), the cross-surface action grammar (§3) can be
  consolidated without chasing a moving target.
- **Main repo:** Mimeo-Android.
- **Model choice:** high-effort session; extends
  `ANDROID_ITEM_ACTIONS_SPEC.md` rather than parallel-creating.
- **Dependencies:** Lanes 3 and 4.
- **Deferred:** no backend change; no new surface.

### Lane 6 — Smart playlists v1

- **Why sixth:** Now the v1 smart-playlist model (§4) lands on a homogenized
  list surface (Lane 3) with a consistent queue-action grammar (Lane 5) and
  a settled Up Next surface (Lane 4). Lane 6 adds the filter/persist/manual-
  layer machinery.
- **Main repo:** Mimeo-Android primarily. Backend likely has opinions
  (filter storage, capture-type enumeration); treat as coordination, not
  blocker, until a specific endpoint change is required. If backend storage
  of the filter definition is required (e.g., for web parity), that is a
  CONTRACT CHANGE and must be labeled as such.
- **Model choice:** high-effort session for model + first surface;
  splittable thereafter.
- **Dependencies:** Lanes 3, 4, 5.
- **Deferred:** Model D (display-mode smart playlists). Cross-surface smart-
  playlist parity in web.

### Lane 7 — Bluesky rolling smart playlist / harvester

- **Why last:** Harvester depends on smart-playlist surface being real
  (Lane 6), on queue actions being consistent (Lane 5), on list layout
  being homogenized (Lane 3), and potentially on a backend ingestion
  component (Mimeo repo).
- **Main repo:** Mimeo (backend) for ingestion and capture-type; Mimeo-
  Android for the surface.
- **Model choice:** high-effort session for the ingestion design and
  Android surface. Expect cross-repo CONTRACT CHANGE. Coordinate per the
  `CLAUDE.md` cross-repo authorization rule.
- **Dependencies:** Lanes 3, 5, 6. Bluesky auth / API access arrangement
  is a prerequisite outside this sequence.
- **Deferred:** images, repost/quote resolution, additional automations,
  auto-promotion to Up Next, retroactive back-fill beyond window.

### What stays paused across all lanes

From `ROADMAP.md` and existing guardrails, these remain deferred and are
*not* reopened by any of the lanes above:

- Cross-device Up Next continuity (backend CONTRACT CHANGE required).
- Host-control track beyond shipped read-only + bounded restart slices.
- Domain reliability work (paused until product/design priorities are
  locked).
- Problem Reports v2 attachment contract backend persistence (tracked in
  Mimeo repo).

HTTPS/Tailscale Remote mode is shipped — this is not a pause, it is a
precondition that the lanes above can rely on.

---

## 8. Model / workflow guidance

Memorializing the agent workflow so it does not drift across tickets:

- **High-effort model** does design, content authoring, code, and test
  work. Planning tickets like this one; implementation tickets with
  non-trivial scope; audits that may uncover contradictions.
- **Low-effort model** handles git / PR housekeeping: staging, commits
  with repository-style messages, branch push, PR open / description,
  post-merge report.
- **Do not switch model mid-session merely for git housekeeping.** Once a
  high-effort session is loaded with context, the cost of thrash on a
  small `git` step is lower than the cost of a cold low-effort session
  rewarming context.
- **For high-effort planning tickets (like this one), stop before git
  operations.** The design work is complete when the artifact is written
  and reported; commit/push/PR belongs to a separate low-effort session.
- **Use a fresh low-effort session for commit / push / PR.** It starts
  cold with only the artifact in diff, which is the right level of
  context for house-keeping.

This applies specifically to this repo's Claude-primary lane. The rule in
`CLAUDE.md` about single-writer-per-PR and serialized merges across Mimeo +
Mimeo-Android continues to apply; nothing in this section changes that.

---

## 9. Explicit non-changes in this doc

This document does not:
- Change any `REDESIGN_V2_DECISION_SNAPSHOT.md` rule.
- Change any `REDESIGN_V2_PLAN.md` phase definition.
- Authorize any implementation ticket.
- Reopen domain reliability work.
- Reopen the Smart Queue terminology (retired; see §1).
- Alter HTTPS/Tailscale Remote mode defaults.
- Alter Up Next device-local v1 rule (no cross-device sync without CONTRACT
  CHANGE).

---

## 10. Open questions (consolidated)

Preserved for the tickets that will resolve them.

1. Snap-to-active: what triggers it, where does the control live? (§2.3)
2. Session history retention: unbounded, time-bounded, or count-bounded?
   (§2.3)
3. Session history scope: strictly per-device in v1, with future sync tied
   to the Up Next continuity contract? (§2.3)
4. Session history privacy controls: hideable, purgeable, disableable?
   (§2.3)
5. History-row interaction: tap-to-restart vs tap-to-read vs tap-to-add?
   (§2.3)
6. Smart-playlist pinned-item policy on bin/archive: persist muted,
   auto-unpin, or disappear? (§4.5)
7. Smart-playlist empty-state treatment. (§4.5)
8. Bluesky harvester representation: F1 / F2 / F3? Recommended F3; ratify
   at implementation scoping. (§5.2, §5.4)
9. Bluesky window adjustability placement: playlist display options vs
   dedicated harvester settings? (§5.1)
10. Right-hand drawer existence and scope — not authorized here; direction
    is "left-aligned content if it exists." (§6.2)

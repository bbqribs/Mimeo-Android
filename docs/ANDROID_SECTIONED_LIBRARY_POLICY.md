# Android Sectioned Library Policy

**Status:** Decision — implementation-facing. Resolves the §6.1 blocker in
`docs/REDESIGN_COMPLETION_PLAN.md`. Authoritative for C5 implementation ticket.
**Date:** 2026-04-27
**Scope:** Inbox, Favorites, Archive, Bin — Android Library surfaces only.
**Non-goals:** Smart playlists, Bluesky, Up Next, any backend/API changes, row
actions, queue-action semantics.

---

## 1. Current behavior summary

All Library surfaces (Inbox, Favorites, Archive, Bin) render flat
`LazyColumn` lists with no visual grouping except the existing Inbox
"Pending (N)" collapsible section.

**Existing behaviors that must be fully preserved:**

| Behavior | Rule |
|----------|------|
| Row tap | Opens item in Locus. Never mutates Up Next. |
| Long-press | Enters multi-select (row pre-selected). Never drag mode. |
| Selection exit | Exits on Back, sort change, or search query change. |
| Row overflow `⋮` | Play Now / Play Next / Play Last (non-Bin); canonical overflow order per queue actions spec §4.2. |
| Batch bar | Per-surface actions plus "Add to Up Next" (non-Bin); visible-list-order append. |
| Pending section (Inbox only) | Collapsible "Pending (N)" at top for status ∈ {extracting, saved, failed, blocked}. Collapsed by default. Unchanged. |
| Bin row tap | Disabled (no queue actions, no Play Now). Unchanged. |
| Archive queue actions | Identical to Inbox / Favorites. Unchanged. |
| Search | Flat results; no grouping while search is active. |
| Sort chips | Client-side: NEWEST, OLDEST, OPENED, PROGRESS. Server-side: ARCHIVED_AT, TRASHED_AT. |
| Refresh | Button-triggered only. Does not auto-re-seed. |

---

## 2. Sectioning options comparison

| Option | User value | Impl. complexity | Interaction with search | Interaction with sort | Interaction with multi-select | Interaction with pending items | Visual density | Risk of confusion |
|--------|-----------|-----------------|------------------------|-----------------------|-------------------------------|--------------------------------|----------------|--------------------|
| **A. Date-based** | High for browse-by-recency. Groups naturally align with how users think about saves ("what did I add this week?"). | Low — `createdAt` is already on `PlaybackQueueItem`; client-side bucketing only. | Hide headers when search active; flat results. | Only meaningful when sort is date-based (NEWEST). Deactivate for OPENED/PROGRESS. | Cross-section selection works unchanged; section headers not selectable. | Pending section stays fixed at top of Inbox; date sections apply to readyItems only. | Moderate. Headers add clear visual anchors without compressing rows. | Low. Date labels are universally understood. |
| **B. Source/domain-based** | Medium in theory. In practice, search and filters already cover "show me items from X source." | High — many items have identical domains; section count is unpredictable; short sections common. | Redundant with search. | Orthogonal to all sort modes; would force a domain sort. | Cross-section selection works, but visual confusion when items from one domain span many sections. | Awkward — pending items are domain-agnostic. | Poor. Many single-item sections likely. | High. Unfamiliar grouping axis; no clear priority among sources. |
| **C. Read-state/progress-based** | Medium for Inbox ("unread / in progress / done"). Low for Favorites/Archive where state is implicit in the view membership. | Moderate — progress data available locally but thresholds arbitrary. | Partially redundant with PROGRESS sort. | Conflicts with PROGRESS sort (sort already does the ranking). | Works but odd: multi-select across "done" and "unread" feels arbitrary. | Status items are already in Pending section. | Moderate. Risk of "why is this item marked done in Favorites?" confusion. | Medium. Fights Favorites / Archive semantics where the view IS the state signal. |
| **D. Capture-type sections** | Low. Most items are web articles; `captureKind` doesn't vary meaningfully across a typical library. | Moderate. Field is present but sparsely populated. | N/A. | Orthogonal. | Works. | N/A. | Low value. | Medium. Users don't think in capture-type terms. |
| **E. No sectioning; conditional on sort/filter** | Baseline (flat list). Sectioning only activates where it adds value. | Lowest. Only sections where warranted; no generic machinery. | No change. | Matches sort context exactly. | No change. | No change. | Best base case. | None. |

**Data availability constraint (stop condition check):**
`PlaybackQueueItem` carries `createdAt: String?` and `lastOpenedAt: String?`
locally. It does **not** carry `archivedAt` or `trashedAt`. This means:

- Date sections using `createdAt` are feasible for NEWEST/OLDEST sorts with
  no data changes.
- Date sections for ARCHIVED_AT or TRASHED_AT sorts require a new field on
  the model. This is an additive model change, not a backend contract change
  (the field likely exists in the API response already), but it is out of v1
  scope.
- Progress-bucket sections using `progressPercent` are technically feasible
  locally but the thresholds are arbitrary.

---

## 3. Recommended v1 policy

**Option A — Date-based, conditional on active sort.**

The operator's starting bias is correct and matches the data constraints.
Specifically:

- Show date-based section headers **only when the active sort is NEWEST**.
- Deactivate headers (flat list) for OLDEST, OPENED, PROGRESS, ARCHIVED_AT,
  and TRASHED_AT sorts.
- Bin excluded from sectioning in v1.

**Why NEWEST only, not OLDEST:**
NEWEST is the default browsing mode ("what did I recently save?"). The
section order for NEWEST is naturally legible top-to-bottom: Today →
Yesterday → This Week → This Month → Last Month → Older. OLDEST sort
reverses this to Older → … → Today, which is logically consistent but
visually unexpected for casual browsing. OLDEST is a management sort, not
a discovery sort; flat list there is fine. Add OLDEST sections only after
NEWEST is validated in production.

**Why not source/domain (Option B):**
Search and filters already cover source-based discovery better than sections
would. The section count would be unpredictable and many short sections
likely.

**Why not read-state (Option C):**
Read state is already signaled by which Library tab the user is in
(Inbox = inbox, Favorites = kept, Archive = done). Sectioning by read state
inside Favorites or Archive fights the view's own semantic identity.

---

## 4. Rules

### 4.1 When section headers appear

| Surface | Sort | Headers shown? | Date field used |
|---------|------|---------------|-----------------|
| Inbox | NEWEST | Yes | `createdAt` |
| Inbox | OLDEST | No (flat) | — |
| Inbox | OPENED | No | — |
| Inbox | PROGRESS | No | — |
| Favorites | NEWEST | Yes | `createdAt` |
| Favorites | OLDEST | No (flat) | — |
| Favorites | OPENED | No | — |
| Archive | NEWEST | Yes | `createdAt` (save date) |
| Archive | OLDEST | No (flat) | — |
| Archive | ARCHIVED_AT | No (flat, v1) | — |
| Bin | Any | No (v1) | — |
| Any surface | Search active | No (flat) | — |

### 4.2 Section labels

When headers are shown, items are bucketed by `createdAt` relative to the
device's local calendar date at render time:

| Bucket | Condition (local date) |
|--------|------------------------|
| **Today** | `createdAt` is today |
| **Yesterday** | `createdAt` is yesterday |
| **This Week** | `createdAt` is in the current Mon–Sun week, before yesterday |
| **This Month** | `createdAt` is in the current calendar month, before this week |
| **Last Month** | `createdAt` is in the previous calendar month |
| **Older** | `createdAt` is before last month, or `createdAt` is null |

Items with `createdAt = null` fall into **Older**.

Bucket boundaries are re-evaluated at each recomposition. No need for a
realtime clock listener; the list refreshes on user interaction anyway.

### 4.3 Empty sections

Hidden. A section label only appears if at least one item falls in that
bucket. No "No items from last month" placeholders.

### 4.4 Section header interactivity

Section headers are **not selectable** and **not focusable** as rows.
They are purely visual separators. TalkBack skips them or announces them
as a static label ("Today", etc.) — they must not be rendered as
interactive elements.

### 4.5 Multi-select across sections

Multi-select **crosses section boundaries freely**. Entering selection on a
row in "Yesterday" and then tapping a row in "This Week" selects both.
Section headers have no effect on selection scope. "Add to Up Next" batch
action appends in current visible list order (which is identical to
`createdAt` descending for NEWEST sort) — section membership does not
change the append order.

### 4.6 Pending items section (Inbox only)

The existing "Pending (N)" section at the top of Inbox is unchanged.
It is not a date section. It is a logical separator controlled by
`status ∈ {extracting, saved, failed, blocked}`. Date sections apply
only to `readyItems` (i.e., items that are not pending). The Pending
section always renders above the first date section header.

If the Pending section is collapsed, the date sections for readyItems are
still shown directly below the collapsed header.

### 4.7 Item ordering within sections

Sectioning is **visual grouping only**. The sort order of items does not
change. Within a "Yesterday" section, items are still ordered by `createdAt`
descending (NEWEST sort). Section headers slot into the already-sorted list
at the boundaries — they never reorder items.

### 4.8 When no items match a sort + surface combination

If all items are in a single bucket (e.g., everything was saved today),
one section header appears above all items. This is intentional — it
confirms to the user the recency of their content without requiring
special-casing.

---

## 5. Implementation slice recommendation

### Slice 1 (v1) — Date headers on Inbox, Favorites, Archive (NEWEST only)

**Surfaces:** Inbox, Favorites, Archive. Bin excluded.

**Sort trigger:** Section headers active only when `sortOption == NEWEST`.
All other sort modes render flat list (existing behavior).

**Data required:** `createdAt` on `PlaybackQueueItem`. Already present.
No backend/API changes. No new fields.

**Implementation approach:**

1. Add a `LibrarySection` sealed type (or simple data structure) wrapping
   either a header label or a list item. Compute the sectioned list as a
   `List<LibraryListEntry>` where entries alternate between `SectionHeader`
   and `Item` nodes.
2. In `LibraryItemsScreen`, replace the `LazyColumn items(readyItems)` block
   with `items(sectionedReadyItems)`, dispatching to either a header row
   composable or the existing `LibraryItemRow`.
3. Section header row: non-interactive `Text` with `labelLarge` or
   `titleSmall` typography; left-aligned; subtle separator or adequate
   vertical padding. Not a `Button` or `Card` — plain layout.
4. When `searchQuery.isNotBlank()`, skip sectionedReadyItems computation
   entirely and render flat `readyItems` as today.
5. Pending section (Inbox) remains above sectionedReadyItems. The pending
   section composable is unchanged.
6. Selection state, batch bar, overflow menu, sort chip exit, back handler
   — all unchanged. Selection state holds `Set<Int>` item IDs; section
   header nodes have no ID.
7. TalkBack: section headers should have `Role.None` or be excluded from
   the focusable traversal. Add `semantics { heading() }` if the header
   warrants structural accessibility (design call; either is compliant).

**What is not in Slice 1:**
- Sticky (pinned) section headers — use static headers for v1. Add
  `stickyHeader {}` in a later polish ticket if warranted.
- OLDEST sort sectioning — deferred; validate NEWEST first.
- ARCHIVED_AT sectioning for Archive — requires `archivedAt` field on
  the model. Deferred; no backend change needed if the field is already
  in the API response, but it needs a model + mapping update.
- Bin — excluded.
- OPENED or PROGRESS sort sectioning — not recommended.

---

## 6. Explicit non-goals

- No smart playlists.
- No Bluesky surface.
- No Up Next changes.
- No new backend fields (in v1).
- No row-face play buttons.
- No change to queue actions or overflow menu order.
- No "Select all in section" gesture.
- No collapse/expand per section in v1 (Pending section collapse is
  pre-existing and isolated to that section only).
- No source/domain-based sections.
- No read-state/progress-based sections.
- No pinned "sticky" section headers in v1.

---

## 7. Unresolved questions

| Question | Impact | Disposition |
|----------|--------|-------------|
| Archive ARCHIVED_AT sort + date sections | If ARCHIVED_AT becomes the default sort for Archive, date headers require `archivedAt` on the model. | Defer to slice 2. Check if API already returns `archived_at` per item; if so, add to model and map. Not a contract change. |
| OLDEST sort header order | Logically valid (Older → Today, top to bottom) but may surprise users. | Validate NEWEST first; add OLDEST sections only after user validation. |
| Sticky vs. static headers | Sticky headers improve orientation in long lists but add implementation complexity. | Static in v1. Open a follow-up polish ticket if users report orientation confusion. |
| Section count threshold | Should headers suppress if there are only 1–2 items total? | No threshold in v1; always show non-empty headers. Revisit if headers feel noisy on small libraries. |
| Null `createdAt` items | Currently placed in Older. Visually fine; check if API can guarantee non-null. | Acceptable fallback for v1. |

---

## Manual verification

Docs-only artifact. No code changes in this ticket.

```powershell
Get-Content -Raw docs\ANDROID_SECTIONED_LIBRARY_POLICY.md
git diff -- docs\ANDROID_SECTIONED_LIBRARY_POLICY.md
```

Confirm:
- §3 recommendation matches operator intent (date-based, NEWEST sort only, Bin excluded).
- §4.1 table covers all surface/sort combinations.
- §4.2 bucket labels are clear and mutually exclusive.
- §5 slice scope excludes code and backend changes.
- §6 non-goals match the ticket's explicit scope restrictions.
- §7 unresolved questions are flagged without being answered here.

Before opening the C5 implementation ticket, verify that
`docs/REDESIGN_COMPLETION_PLAN.md` §6.1 still lists the sectioning policy
as the remaining Library blocker, and update it to reference this doc.

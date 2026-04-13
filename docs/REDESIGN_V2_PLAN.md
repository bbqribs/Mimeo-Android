# Mimeo Redesign v2 — Product + Architecture Plan

**Version:** 0.3 (planning draft)
**Status:** Pre-implementation planning
**Date:** 2026-04-10
**Scope:** Android, backend, and web

**Changes in 0.3:**
- Removed remaining stale "Smart Queue" references (was missed in 0.2 in §3.3 and §9.2).
- Fixed Section 2 entity table and critical-distinction paragraph: Up Next is no longer described as a live mirror of a playlist.
- Settled re-seed trigger: explicit user action only; pull-to-refresh on Up Next does NOT auto-re-seed.
- Added Section 6: Playlist playback context rule (tap / Play playlist / Play from here / dirty-Up-Next definition).
- Fixed Section 5 screen map and "Reaching Up Next": Up Next is a drawer destination on day one (settled in 0.2 but screen map was not updated).
- Clarified Section 6 "Adding to playlist" terminology: "Play Next"/"Play Last" insert into Up Next only; playlist-add actions use "End of playlist"/"Top of playlist".
- Fixed Section 9.2 note: removed "Smart Queue" reference.
- Fixed Section 10.4: removed "manual cursor" language; offset/limit is v1 pagination.
- Fixed risk numbering (was 1–8, 11–12, 9–10 due to insertion order).
- Removed settled "Product decision required" markers from §3.1 and §3.2.

**Changes in 0.2:**
- Up Next is device-local in v1 (was: synced as system playlist). Sync is a v2+ enhancement.
- Dropped "Smart Queue" terminology. Replaced with explicit seed-source model.
- Added "seed source" rule: Up Next edits never mutate any source playlist. No "active playlist" with live link.
- Playlist folders cut entirely (was: deferred).
- Pagination simplified to offset/limit for v1 (was: cursor-based).
- Undo promises tightened: v1 restores archive/favorite flags only, not playlist membership or list position.
- Added playlist visibility from Phase 2 to close the gap between Collections removal and full playlist management in Phase 5.
- Up Next moved into drawer on day one.

---

## 1. Executive Summary

Mimeo Android is transitioning from a playback-shell app to a **library-first app with playback as a first-class mode layered on top**. This is a deliberate reframing: the app's primary identity becomes "your saved-items library" rather than "your TTS queue player." Playback remains deeply integrated and excellent, but it is no longer the sole organizing principle of the UI.

This redesign touches three systems: the Android client (largest scope), the backend API (batch operations, query contracts, playlist ordering), and the web app (eventual nav parity, playlist management). The goal is convergence: Android, web, and API should share a coherent information architecture — Inbox / Favorites / Archive / Bin as library views, playlists as explicit user collections, and a playback layer (Up Next + Locus) that operates across all of them.

The approach is incremental. Hard-won playback, reader, offline, and progress behaviors are preserved. New surfaces are introduced alongside existing ones, with migration paths rather than big-bang rewrites.

**Key shifts:**
- Bottom nav bar → side drawer navigation
- Collections tab → eliminated; playlists move to drawer (visible from Phase 2 onward; full management in Phase 5)
- Up Next remains, but is no longer the implicit home screen for all users
- Up Next is modeled as a device-local persistent ordered list with a "seed source" (v1); cross-device sync deferred to v2+
- Inbox becomes a first-class library view matching web semantics
- Multi-select and batch actions become available across list views
- Locus/player moves from a nav tab to a persistent mini-player + expandable surface

**Key product rules that shape the architecture:**
- Inbox/Favorites/Archive/Bin are **views** (filtered projections), not collections. They are sort-controlled, not manually reorderable.
- Playlists are **persistent ordered collections** owned by the user.
- Up Next is **the device's single playback queue**. It is not a user playlist.
- **Up Next edits never mutate the source playlist.** The source is an informational label, not a live link. Avoid the phrase "active playlist."

---

## 2. Recommended Product Model

### Conceptual layers (bottom to top)

```
┌─────────────────────────────────────────────────┐
│ Settings                                        │ (separate, accessible from drawer)
├─────────────────────────────────────────────────┤
│ Player layer                                    │ (persistent mini-player, expandable to Locus)
│   └── Locus = integrated reader + player        │
│   └── Up Next = active playback queue           │
├─────────────────────────────────────────────────┤
│ Library layer                                   │ (drawer-navigable views)
│   ├── Inbox          (unarchived, non-trashed)  │
│   ├── Favorites      (favorited, non-archived)  │
│   ├── Archive        (archived)                 │
│   ├── Bin            (soft-deleted, time-bound)  │
│   └── Playlists      (explicit user collections)│
└─────────────────────────────────────────────────┘
```

### Entity definitions

| Concept | Definition | Mutable? | Ordered? |
|---------|-----------|----------|----------|
| **Item** | A saved article/text in the library | Yes (status, progress, archive state, favorite state) | N/A |
| **Inbox** | View: all non-archived, non-trashed items | View only (no item "belongs" to inbox) | Sort-controlled |
| **Favorites** | View: favorited, non-archived items | View only (toggle favorite flag) | Sort-controlled |
| **Archive** | View: archived items | View only (toggle archive flag) | Sort-controlled |
| **Bin** | View: soft-deleted items | View only (bin/restore/purge actions) | Recency only |
| **Playlist** | Explicit, persistent, user-managed ordered collection | Yes (CRUD, reorder, add/remove) | Position-ordered |
| **Up Next** | The device's single playback queue; persistent device-local list with a seed source | Yes (reorder, add, remove) | Position-ordered |
| **Locus** | The integrated reading + listening surface for one item | N/A (it is a view, not a collection) | N/A |
| **Player** | The persistent playback state: mini-player bar + expanded Locus | N/A (it is a mode, not a destination) | N/A |

### Critical distinction: views vs collections

Inbox, Favorites, Archive, and Bin are **views** — filtered projections of the item table. An item's visibility in these views is determined by its flags (`archived_at`, `trashed_at`, `is_favorited`). An item does not "belong to" the Inbox in the way it belongs to a playlist.

Playlists are **collections** — explicit, user-managed membership with ordering. An item can be in zero, one, or many playlists simultaneously. Playlist membership is orthogonal to archive/favorite/bin state (with policy rules about what happens to membership on state transitions).

Up Next is a **playback construct** — a single device-local ordered list. It is seeded from a source (Inbox or a user playlist) and then owned independently. Edits to Up Next do not affect the source. See Section 6 for full semantics.

---

## 3. Tensions and Contradictions in the Current Direction

### 3.1 Inbox ordering vs Up Next ordering

**Tension:** The user wants Inbox to be a library view (sorted by recency, status, etc.) but also wants to reorder items and have that persist. A sort-controlled view and a user-ordered list are fundamentally different things.

**Recommendation:** Keep them separate. Inbox is always sort-controlled (the user picks a sort mode; items cannot be manually dragged). Up Next is always position-ordered (the user can drag-reorder). If the user wants a reorderable inbox-like list, they create a playlist. This is the cleanest model and avoids a hybrid that is neither a good view nor a good list.

**Settled:** Inbox is sort-only, not manually reorderable.

### 3.2 Up Next identity: the seed-source model

**Tension:** Up Next needs to be editable, reorderable, and persistent across app restarts — but it is conceptually distinct from a user playlist. If you just call it "a playlist," you blur the distinction. If you call it "ephemeral," you lose user edits on restart.

**Recommendation:** Up Next is **a single, persistent, device-local ordered list**. It is NOT a user playlist. It is not renameable, createable, or deletable by the user. It has exactly one instance per device.

Up Next has an informational `seed_source` field describing where its current contents came from. Values look like:
- `"Inbox (newest first)"` — seeded from an Inbox query
- `"Playlist: Podcasts"` — seeded from the user's "Podcasts" playlist
- `"Custom"` — sufficiently edited that the seed relationship is no longer meaningful

**Edits to Up Next mutate Up Next and only Up Next.** Adding, removing, or reordering items in Up Next never propagates to any other playlist. The seed source is a historical label, not a live link.

**Re-seeding:** The user can explicitly "Re-seed from Inbox" or "Re-seed from [playlist]" via a menu action on the Up Next surface. Re-seeding wipes the current Up Next contents and replaces them with the new source's items. This is a destructive action and confirms if Up Next has unsaved user edits.

**Storage:** Up Next is stored in the Android Room database in v1. It survives app restart and process death. It is NOT synced to the server in v1. Cross-device Up Next sync is a v2+ enhancement (would require a server-side system playlist, which is currently out of scope).

**Why this is cleaner than the "system playlist on server" model:**
- No backend work for v1 (persistence is local, which Room already handles well)
- No sync conflicts to resolve
- The model is transparent: edits obviously only affect Up Next because it is demonstrably a separate object

**Settled decisions:**
- Up Next is device-local in v1 (cross-device sync is v2+).
- Edits in Up Next never mutate the source playlist.
- Re-seed trigger: **explicit user action only** via Up Next overflow menu. Pull-to-refresh on Up Next does NOT auto-re-seed — it may refresh the items' metadata (titles, progress, status) but does not wipe or replace queue contents. This matters: auto-re-seed on refresh would silently destroy user edits.

### 3.3 Default home surface vs default Up Next seed source

These are two independent settings:
- **Default home surface:** Inbox (default) or Up Next (user preference in Settings).
- **Default Up Next seed source:** Inbox newest-first (default). User can re-seed from any user playlist at any time via explicit action.

For v1, these are two simple settings. The seed source does not need to be configurable at install time — Up Next just starts empty and is seeded from Inbox on first open. The user can re-seed from a playlist later.

### 3.4 Duplicate items in playlists

**Tension:** The user wants items to be able to appear twice in the same playlist. This is unusual and creates edge cases: which instance does "remove from playlist" target? What does progress mean for a duplicate? How does the UI disambiguate?

**Recommendation:** Support it at the data layer (playlist entries reference item+position, not unique-on-item), but defer duplicate-in-playlist UX for v2+. For v1, the add-to-playlist action should warn if the item is already present and offer "Add anyway" vs "Cancel." Removal targets the specific entry (by entry ID, not item ID). Progress is per-item, not per-entry — both entries show the same progress.

### 3.5 Long-press: select vs reorder (see Section 8 for full treatment)

**Tension:** Long-press is the natural gesture for both "enter selection mode" and "begin drag reorder." These cannot coexist on the same surface without disambiguation.

### 3.6 Archive removes from playlists — but what about Up Next?

**Note:** Up Next is NOT a user playlist (see 3.2). So "archive removes from playlists" does not automatically apply to Up Next. We make a separate decision for each:

**User playlists:** Archiving an item removes it from all user playlists (deferred v2: per-playlist "include archived" option would override this).

**Up Next:** Archiving an item marks it archived but does not remove it from Up Next until the current playback session ends or the user refreshes/re-seeds. This preserves the "archive as I listen" pattern without disrupting the queue.

The existing deferred-cleanup logic for archive-while-playing already handles this for the currently-playing item. Non-playing archived items in Up Next are harder to justify — they are visible but not re-addable to a user playlist. Tentative v1 behavior: non-playing archived items in Up Next are shown with a muted/archived indicator and can be manually removed by the user but are not auto-removed.

### 3.7 Library-first but Locus is the crown jewel

**Tension:** Calling this "library-first" risks de-emphasizing playback, which is the app's strongest differentiator. The web app is a library; the Android app's value-add is that it is a library that *reads to you*.

**Clarification:** "Library-first" describes the information architecture, not the value proposition. The IA organizes around the library; the UX still treats playback as a first-class experience. The mini-player is always visible during playback. Locus is one tap away from any screen. The library serves the playback — it's where you find, organize, and queue the things you'll listen to.

---

## 4. Decisions: Now vs Later

### Decide now (before implementation starts)

| # | Decision | Recommendation | Type |
|---|----------|---------------|------|
| 1 | Is Inbox sort-only or manually reorderable? | Sort-only | Product |
| 2 | Is Up Next synced cross-device in v1? | No — device-local only. Sync is v2+. | Product |
| 3 | Home screen default? | Inbox (with Up Next as option in settings) | Product |
| 4 | Default Up Next seed source? | Inbox, newest-first. User can re-seed from any user playlist. | Product |
| 5 | Do edits in Up Next mutate the source playlist? | No. Edits affect Up Next only. Source is informational. | Product (critical) |
| 6 | Multi-select gesture? | Long-press to select; drag handles for reorder | Product + UX |
| 7 | Batch API: backend-first or client-side loop? | Backend batch endpoints (new contract) | Engineering |
| 8 | Pagination strategy? | Offset/limit for v1. Cursor-based deferred unless library size pressures it. | Engineering |
| 9 | ViewModel extraction before or during? | Before. Extract ViewModel from MainActivity as Phase 0. | Engineering |
| 10 | Playlist folders: keep or cut? | Cut entirely. Not in v1, not in v2 plan. | Product |
| 11 | Web nav redesign: now or later? | Later. Web keeps top tabs; Android gets drawer. | Product |
| 12 | Is Up Next a drawer destination on day one? | Yes. Visible in drawer from Phase 2 onward. | Product |
| 13 | Is there playlist visibility during Phase 2-4 (before Phase 5 ships full playlist mgmt)? | Yes. Minimal playlist list + existing detail screen wired into drawer from Phase 2. | Product |

### Defer to v2+

- Duplicate-in-playlist UX (support in data layer now, defer UI)
- Playlist-level "include archived items" option
- Global cross-view search (v1 has per-view search only)
- Grouped drag reorder (multi-select + reorder simultaneously)
- Cross-device Up Next sync (server-side system playlist)
- Playlist membership restoration on undo
- Web sidebar/drawer redesign
- Web playlist management (CRUD + reorder)
- Light mode (dark-only for now)

---

## 5. Information Architecture and Navigation

### Drawer structure

```
┌───────────────────────────┐
│ [M] Mimeo                 │  ← app icon, dark/purple treatment
│                           │
│ ▸ Inbox              (42) │  ← item counts
│ ▸ Favorites           (7) │
│ ▸ Archive           (128) │
│ ▸ Bin                 (3) │
│                           │
│ ▸ Up Next             (8) │  ← playback queue, visually distinct
│                           │
│ ─── Playlists ─────────── │
│ ▸ Podcast Queue      (12) │
│ ▸ Weekend Reads       (5) │
│ ▸ Research            (8) │
│ + New Playlist            │
│                           │
│ ─────────────────────────│
│ ⚙ Settings                │
└───────────────────────────┘
```

### Drawer behavior

- Opens via hamburger icon in top-left of every library screen
- Opens via edge swipe from left (standard Android drawer gesture)
- Closes on destination selection, back gesture, or scrim tap
- Current destination is highlighted in drawer
- Drawer is NOT available from Locus (Locus is a separate layer, entered/exited via the player)
- Drawer IS available from Up Next (Up Next is a library-adjacent surface, even though it is playback-oriented)

### Screen map

```
Drawer destinations:
  Inbox ──────────── library list view
  Favorites ──────── library list view (filtered)
  Archive ─────────── library list view (filtered)
  Bin ────────────── library list view (different action set)
  Up Next ─────────── playback queue (visually distinct from library views)
  Playlist [name] ── ordered list view (reorderable; Phase 5+)
  Settings ────────── separate screen tree

Non-drawer surfaces:
  Locus ──────────── reader/player (reached via item tap or mini-player expand)
  Sign In ─────────── auth gate (shown before everything else)
```

### Mini-player and Locus relationship

```
┌──────────────────────────────┐
│ [Library screen content]     │
│                              │
│                              │
│                              │
├──────────────────────────────┤
│ ▶ Article Title    advancement│  ← mini-player bar (persistent when playback active)
└──────────────────────────────┘
         │ tap
         ▼
┌──────────────────────────────┐
│ [Locus: full reader+player]  │  ← expands as full-screen or bottom sheet
│                              │
│  article text with highlight │
│                              │
│  ▶ ◀◀ ▶▶ speed controls     │
└──────────────────────────────┘
```

The mini-player is a persistent bar at the bottom of all library screens (above where the old nav bar was). Tapping it expands to Locus.

Locus is also entered by tapping an item in any list. In library views (Inbox, Favorites, Archive, Bin), tapping opens the item in reader-only mode. In Up Next, tapping starts or resumes playback. See Section 7 for the full entry-point table.

When no playback session is active, the mini-player is hidden. Library screens extend to full height.

### Reaching Up Next

Up Next is a drawer destination. It appears in the drawer between the library views (Inbox / Favorites / Archive / Bin) and the Playlists section. It is also reachable via the queue icon in the mini-player.

### Landing screen

App launch behavior:
1. If not signed in → Sign In screen
2. If signed in → user's configured default home (Inbox or Up Next)
3. If playback was active when app was last closed → restore mini-player state with session

---

## 6. Library / Queue / Playlist Semantics

### Library views: query semantics

Each library view maps to a filtered query on the items table:

| View | Filter | Default sort | Alternate sorts | Shows non-ready? |
|------|--------|-------------|----------------|-----------------|
| **Inbox** | `archived_at IS NULL AND trashed_at IS NULL` | Created (newest first) | Opened, Progress, Status | Yes (with status treatment) |
| **Favorites** | `is_favorited = true AND archived_at IS NULL AND trashed_at IS NULL` | Created (newest first) | Opened, Progress | Yes |
| **Archive** | `archived_at IS NOT NULL AND trashed_at IS NULL` | Archived date (newest first) | Created, Opened | No (archived items are always ready) |
| **Bin** | `trashed_at IS NOT NULL` | Trashed date (newest first) | None | Yes (shows failed items that were binned) |

### Non-ready items in Inbox

Items with status `extracting`, `failed`, or `blocked` appear in Inbox but with distinct treatment:

| Status | Visual treatment | Tap behavior | Available actions |
|--------|-----------------|-------------|-------------------|
| `saved` | Normal row, no status pill | Opens Locus (will show loading/pending) | Archive, Bin, Favorite, Add to Playlist |
| `extracting` | Subtle spinner or "Processing..." pill | Opens status detail (not Locus) | Archive, Bin |
| `ready` | Normal row | Opens Locus | All actions |
| `failed` | Error pill, muted row | Opens status detail with retry option | Retry, Bin, Archive |
| `blocked` | Warning pill | Opens status detail with explanation | Bin, Archive |

**Recommendation:** Group `extracting`/`failed`/`blocked` items into a collapsible "Pending" section at the top of Inbox when present. This keeps the main Inbox list clean while making problems visible. The section shows count and expands to show individual items. When collapsed, show only the count: "3 items processing."

### Playlist semantics

Playlists are:
- Created by the user (via drawer "New Playlist" or from library overflow menu)
- Named, renameable, deletable
- Ordered by `position` (float-based, supporting insertion between items)
- Synced to server (available on web and other devices)
- Viewable as a list with item rows similar to library views, but with reorder capability

Playlist entries store `(playlist_id, article_id, position)`. The `article_id` is not unique within a playlist — duplicates are allowed at the data layer. The `position` field determines display and playback order.

**Adding to playlist:**
- From any library view: overflow menu → "Add to Playlist..." → picker sheet listing all user playlists
- From Locus: overflow → "Add to Playlist..."
- From batch selection: batch action bar → "Add to Playlist..."
- Position within the target playlist: "Top of playlist" or "End of playlist" (default: End)
- Note: "Play Next" and "Play Last" are separate actions that insert into Up Next, not into user playlists. Do not conflate these.

**Removing from playlist:**
- From playlist view: overflow menu → "Remove from Playlist"
- From batch selection in playlist view: batch action bar → "Remove"
- Removal targets the specific entry (by entry ID), important when duplicates exist

### Up Next semantics

Up Next is the active playback queue. It is **a single, persistent, device-local ordered list** — NOT a user playlist. See Section 3.2 for the conceptual model.

**Storage:**
- Stored in the Android Room database
- Survives app restart and process death
- Not synced to the server in v1 (cross-device sync is v2+)

**Contents:**
- An ordered list of item IDs (with positions)
- A `seed_source` metadata field (informational): `"Inbox (newest first)"`, `"Playlist: Podcasts"`, or `"Custom"`
- A `seeded_at` timestamp (for "last updated from source" display)

**Seed behavior:**
- Default seed source: Inbox, sorted newest-first
- Initial seed happens on first app launch or first time the user opens Up Next with an empty queue
- Re-seeding is an explicit user action via an Up Next overflow/menu item: "Re-seed from Inbox" or "Re-seed from [playlist name]"
- Re-seeding wipes the current Up Next contents and replaces them
- If Up Next has user edits (ordering differs from what the source would produce, or items have been added/removed), re-seeding prompts for confirmation

**Edit rules (CRITICAL):**
- **Edits to Up Next never mutate the source playlist or Inbox.** Period.
- Drag-reorder within Up Next: updates Up Next positions only
- Remove from Up Next: removes the entry in Up Next only; item remains in its source playlist and Inbox
- Add to Up Next (via "Play Next" / "Play Last"): inserts into Up Next only; source playlist is unchanged

**Manual insertion actions:**
- **Play Next:** insert at position `current_playing_index + 1`
- **Play Last:** append to end
- Both available from any library item's overflow menu and from Locus overflow

**Playback interaction:**
- Playing an item from Up Next starts playback at that item
- Playing an item from a library list opens Locus in reader-only mode; does not modify Up Next
- Playing an item from a user playlist starts playback using that playlist as the playback context; when the session context requires a queue, Up Next is re-seeded from the playlist (with confirmation if dirty)
- Completing an item in Up Next advances to the next item (auto-advance, if enabled)

**Avoid the term "active playlist."** Use "seed source" or "playing from" when describing the relationship between Up Next and its origin. There is no live link — Up Next is a copy.

### Playlist playback context rule (v1)

This rule defines what happens when the user interacts with items inside a **user playlist detail view**.

#### Tap item (default)

Opens the item in Locus in **reader-only mode**. Does not modify Up Next. Does not start playback. This is identical to tapping an item in any library view: the user is browsing, not committing to a play session.

Rationale: tapping a playlist item to read it should not replace an active Up Next queue. The user may be mid-listen and just checking something.

#### "Play playlist"

Initiates playback starting from the first item in the playlist. Behavior:
1. If Up Next is **dirty** (see definition below): show confirmation dialog "This will replace your current Up Next."
2. Re-seed Up Next from the full playlist in playlist order.
3. Open Locus at the first item and start playback.

#### "Play from here"

Initiates playback starting from a specific item within the playlist, while loading the full playlist into Up Next. Behavior:
1. If Up Next is **dirty**: show confirmation dialog "This will replace your current Up Next."
2. Re-seed Up Next from the full playlist in playlist order.
3. Open Locus at the selected item and start playback from that position.

The full playlist is loaded (not just from the chosen item to the end), so the user can rewind to earlier items without another re-seed.

#### "Play Next" / "Play Last" (from playlist item overflow)

**Do not replace Up Next.** Insert the item into Up Next at position `current+1` or at the end, respectively. This is additive, not a re-seed. No confirmation needed.

#### Definition of "dirty Up Next"

Up Next is **dirty** if the user has made at least one explicit edit since the most recent seed. Explicit edits are:
- Adding an item via "Play Next" or "Play Last"
- Removing an item via swipe-to-dismiss or overflow "Remove from Up Next"
- Reordering an item by drag

Up Next is **clean** immediately after any re-seed, and again after an initial seed (even if the Inbox items have changed since seeding). Changes to the Inbox or source playlist after the last seed do **not** make Up Next dirty — they make the seed stale, but that is displayed via `seeded_at` timestamp, not a dirty flag.

#### Where these actions live

| Action | Surface |
|--------|---------|
| Tap item | Default row tap |
| Play playlist | Toolbar action or overflow menu at the top of the playlist detail screen |
| Play from here | Per-item overflow menu inside the playlist detail screen |
| Play Next / Play Last | Per-item overflow menu (same as from library views) |

#### Implementation phase

These behaviors are part of **Phase 3** (Locus restructure / tap-entry-point differentiation) and **Phase 5** (full playlist management). The Phase 2 playlist detail screen uses the current tap behavior; the new rules take effect when Phase 3 lands.

### Archive/Bin interactions with playlists and Up Next

| Action | Effect on user playlists | Effect on Up Next |
|--------|-------------------------|------------------|
| Archive item | Remove from all user playlists | Item remains in Up Next but shows muted/archived indicator; currently-playing item uses existing deferred cleanup; user can manually remove |
| Bin item | Remove from all user playlists immediately | Remove from Up Next immediately (stop playback if this was the active item) |
| Restore from bin | Does NOT restore user playlist membership | Does NOT restore Up Next position |
| Unarchive item | Does NOT restore user playlist membership | Does NOT restore Up Next position |

**Rationale:** Restoring playlist/queue membership on restore/unarchive is complex (positions may have shifted, playlists may have been reordered) and is deferred to v2+ when richer undo snapshots are implemented.

**Undo scope for v1:** Snackbar undo within the undo window (~8 seconds) reverses only the archive or bin flag change. It does NOT restore playlist membership. It does NOT restore Up Next position. See Section 8 for full undo semantics.

---

## 7. Reader / Player / Locus Integration and Progress Model

### Architectural separation

Despite tight UX integration, reader and player remain architecturally distinct:

| Concern | Owner | State |
|---------|-------|-------|
| Text rendering, scroll position, link handling, text search | **Reader** | `readerScrollOffset`, `visibleRangeStart`, `visibleRangeEnd` |
| TTS playback, chunk sequencing, speed, voice, media session | **Player** | `chunkIndex`, `offsetInChunkChars`, `playbackSpeed`, `voiceId`, `isPlaying` |
| Highlight overlay (current sentence) | **Bridge** | Derived from player's chunk/offset, rendered by reader |
| Canonical progress (percent) | **Shared** | Derived from player position when playing; from reader scroll when reading silently |
| Auto-scroll (reader follows player) | **Bridge** | Reader subscribes to player position; reader can detach (manual scroll) and reattach |

### Progress model — detailed

This is the hardest part of the redesign, because progress must be meaningful across:
- Android reader (scroll position)
- Android player (TTS chunk/offset position)
- Web reader (scroll position, different viewport)
- Cross-device resume

#### Progress pointers

There are three conceptually distinct progress pointers:

1. **Canonical progress (`percent`)**: A 0–100 integer representing how far through the article the user has progressed. This is the cross-surface, cross-device progress indicator. It is what library views display. It is monotonic within a session (never decreases during forward reading/listening, but can be reset by explicit "Reset" action).

2. **Playback cursor (`chunkIndex` + `offsetInChunkChars`)**: The precise TTS position within the chunked article. This is player-specific. It maps to a canonical percent via chunk boundaries and total character count. This is what the player uses for resume and what drives the highlight overlay.

3. **Reader viewport (`readerScrollOffset`, and implicitly `visibleRangeStartChar` + `visibleRangeEndChar`)**: Where the reader's scroll position is. When auto-scroll is active, this follows the playback cursor. When detached, it is independent. This is used to restore "where was I looking" on re-entry.

#### Progress update rules

| Scenario | What updates | Direction |
|----------|-------------|-----------|
| TTS advances to next chunk | Playback cursor advances; canonical percent updates if new percent > previous | Player → Server |
| User scrolls reader (no TTS active) | Reader viewport updates; canonical percent updates if scroll-derived percent > previous | Reader → Server |
| User scrolls reader (TTS active, detached) | Reader viewport updates; canonical percent does NOT update (player cursor is authoritative during playback) | Reader → Local only |
| User seeks via controls (skip forward/back) | Playback cursor jumps; canonical percent updates if forward; does NOT decrease if backward | Player → Server |
| Resume from server state | Playback cursor seeds from server `chunk_index`/`offset_in_chunk_chars`; reader viewport seeds from server `reader_scroll_offset` | Server → App |
| Cross-device resume (web → Android) | Only canonical percent is reliably shared; chunk mapping may differ if content versions differ; best-effort resume to nearest chunk boundary | Server → App |

#### Monotonicity rules (preserving existing behavior)

- **Canonical percent never decreases** during normal use. Re-reading earlier content does not reduce progress. This matches the current `furthestPercent` behavior.
- **Playback cursor can move backward** (user seeks back, rewind). The cursor is a "where am I now" pointer, not a high-water mark.
- **Reader viewport can move freely** (scroll up and down). No monotonicity constraint.
- **Explicit reset** (`toggleCompletion(markDone=false)`) resets canonical percent to 0, allowing re-read.

#### Done threshold

Current: `98%` (`DONE_PERCENT_THRESHOLD`). Preserve this. An item at ≥98% canonical progress is considered "done."

#### Cross-device progress contract

The backend stores per-item:
- `last_read_percent` (canonical high-water mark)
- `resume_read_percent` (current position, may be < last_read_percent after reset)
- `chunk_index`, `offset_in_chunk_chars` (playback cursor, optional)
- `reader_scroll_offset` (viewport position, optional)

Android posts all of these. Web posts `last_read_percent` and `reader_scroll_offset`. Cross-device resume uses `resume_read_percent` to seed position, with chunk-level cursor as a refinement when available and content versions match.

**No change needed for v1.** The existing progress model is sound. The redesign changes which screens display progress and which surfaces trigger progress updates, but the underlying model is preserved.

#### Reader-only progress (new for library-first)

Currently, silent reading (no TTS) in Locus does update progress via scroll position. This behavior should be preserved and made more explicit:
- If the user opens an item from Inbox and reads (scrolls) without starting TTS, progress should still update based on scroll position
- The canonical percent update should use the *bottom* of the visible viewport (not the top) — "I've read up to here"
- Progress sync frequency: same as current (debounced, every few seconds while actively scrolling)

### Locus entry points

| Entry point | Behavior |
|-------------|----------|
| Tap item in any library list | Load item in Locus; if TTS was playing another item, that continues in background; new item is in reader-only mode until user explicitly plays |
| Tap item in Up Next | Load item in Locus and start/resume TTS playback |
| Tap mini-player bar | Expand to Locus showing currently-playing item |
| "Play" action in overflow menu | Load item in Locus and immediately start TTS |
| Resume from app relaunch | If session was active, restore Locus state on mini-player tap |

**Important distinction:** Tapping an item in a library view opens it for reading (Locus in reader mode). Tapping an item in Up Next opens it for playback (Locus in player mode). This is a meaningful product distinction — the library is for browsing and reading; Up Next is for listening.

### Locus modes (preserving existing)

The existing Full/Minimal/Nub chrome modes are preserved. These control player control visibility, not the reader/player distinction. A user in "reader-only" mode (opened from library, no TTS) sees the reader without player controls. Starting TTS reveals controls in the user's preferred mode.

---

## 8. Multi-select / Batch Actions / Reorder

### The gesture problem

Long-press is the canonical Android gesture for both "select this item" (as in Gmail, Files) and "pick up to drag" (as in playlists, todo lists). These cannot coexist on the same touch target without explicit disambiguation.

### Options analyzed

#### Option A: Long-press always selects; drag handles for reorder

- Long-press on any list item → enters selection mode, selects that item
- In ordered lists (Up Next, playlists), a drag handle icon appears on the right side of each row
- Touch-and-drag on the handle → reorder
- Used by: Gmail (select), Spotify (handles), Google Keep (handles)

**Pros:** Consistent gesture meaning across all lists. No mode confusion. Accessible (handle is a distinct target).
**Cons:** Drag handles add visual noise. Handles require wider rows or tighter layouts. Less "magical" feel than long-press-to-drag.

#### Option B: Separate Select and Reorder modes

- Default mode: taps open items; long-press enters Selection mode
- Selection mode: taps toggle selection; top bar shows "N selected" with batch actions; exit via back/X
- Reorder mode: entered via explicit "Reorder" button in toolbar; long-press-and-drag to reorder; exit via "Done"
- Only one mode active at a time

**Pros:** Each mode has clear, unambiguous behavior. No gesture conflicts. Can offer richer mode-specific UI (e.g., Reorder mode could show position numbers).
**Cons:** Extra step to enter Reorder mode. Two distinct modes to explain. User must understand they exist.

#### Option C: Long-press selects in library; long-press drags in ordered lists

- Library views (Inbox, Favorites, Archive, Bin): long-press → selection mode
- Ordered views (Up Next, playlists): long-press → drag reorder
- No multi-select in ordered views (or: multi-select requires explicit "Select" toolbar action)

**Pros:** Matches the primary action per surface type. Feels natural.
**Cons:** Inconsistent behavior per list type. User must learn that long-press does different things in different places. Multi-select in playlists is sacrificed or requires a mode switch.

#### Option D: Long-press selects everywhere; dedicated "Edit order" mode for reorder

Like Option B, but reorder is a heavier-weight mode triggered from the toolbar/overflow. While in Edit Order mode, the entire list shows drag handles and possibly removes other chrome. This is closer to iOS's "Edit" pattern.

**Pros:** Selection is always consistent. Reorder is an intentional, clearly-scoped action.
**Cons:** More ceremony for quick reorders. Might feel heavy for "just move this one item up."

### Recommendation: Option A (long-press selects; drag handles for reorder) with a refinement

Option A is the best default for v1 because:
1. Long-press-to-select is the dominant Android convention (Gmail, Files, Photos, Drive)
2. Drag handles are well-understood and accessible
3. It avoids mode confusion entirely
4. It works the same everywhere — no per-surface learning curve

**Refinement:** In ordered lists (Up Next, playlists), drag handles are always visible (not hidden behind a mode). They appear as a subtle grip icon (⠿ or ≡) on the leading or trailing edge of the row. The handle is touch-targetable but visually recessive — it does not dominate the row.

**Multi-select in ordered lists:** Yes, supported. Long-press selects, as in library views. Selected items can be batch-archived, batch-removed-from-playlist, or batch-added-to-another-playlist. They cannot be batch-reordered in v1 (this requires grouped-drag, which is a v2 feature).

**v2 upgrade path:** Grouped drag (select multiple items, then drag them as a group) is a natural extension once single-drag and multi-select both work independently.

### Selection mode behavior

**Entering selection mode:**
- Long-press any item in a list → that item is selected, selection mode activates
- Top bar transforms to contextual action bar: "N selected" + batch action icons + X (clear)

**While in selection mode:**
- Tap item → toggle selection
- Tap elsewhere (not an item) → no effect (stays in selection mode)
- Back press or X → exit selection mode, clear selection
- Navigate away (drawer, mini-player, etc.) → exit selection mode, clear selection
- Search or filter change → exit selection mode, clear selection
- Pull-to-refresh → preserve selection mode but re-validate selected item IDs against refreshed list (remove any that disappeared)

**Batch actions in contextual bar:**

| Action | Icon | Available in |
|--------|------|-------------|
| Archive | 📥 | Inbox, Favorites, Up Next, Playlists |
| Unarchive | 📤 | Archive |
| Bin | 🗑 | Inbox, Favorites, Archive, Up Next, Playlists |
| Restore | ↩ | Bin |
| Purge | ⊘ (with confirmation) | Bin |
| Favorite | ♥ | Inbox, Archive, Up Next, Playlists |
| Unfavorite | ♡ | Favorites, any view with favorited items selected |
| Add to Playlist | + | Inbox, Favorites, Archive, Up Next |
| Remove from Playlist | − | Playlists |

**Partial failure handling:**
- API calls are issued as a batch request (single HTTP call, see Section 9)
- Response includes per-item success/failure
- Snackbar message: if all succeed, "N items archived" with Undo; if mixed, "N archived, M failed" with Undo for successes
- Failed items remain selected for retry or individual inspection

### Undo for batch actions (v1 scope)

Undo snackbar appears for batch archive, batch unarchive, batch bin, and batch restore-from-bin. Undo for purge is NOT available (purge is permanent; requires confirmation dialog).

**What v1 undo DOES restore:**
- `archived_at` flag (reverses archive → unarchive or unarchive → archive)
- `trashed_at` flag (reverses bin → restore or restore → bin)
- Favorite flag (reverses favorite → unfavorite)

**What v1 undo does NOT restore:**
- Playlist membership (if archiving removed the item from playlists, undo does not re-add it)
- Up Next position (if the item was removed from Up Next, undo does not re-insert it)
- List scroll position or selection state
- Original list position (sort order may have shifted)

**Undo window:** ~8 seconds (snackbar duration). Undo survives in-session navigation but NOT app process kill.

**Partial-failure handling:** If a batch archive succeeds for 8/10 items, the undo reverses only the 8 successes. Failed items remain in their original state.

**Why this scope:** Playlist membership restoration requires capturing entry IDs and positions at action time, then reconciling against the possibly-mutated playlist at undo time. This is a meaningful feature that deserves its own design pass. v2 will expand undo to include playlist/queue restoration.

---

## 9. Backend Implications

### New endpoints needed

#### 9.1 Batch actions endpoint

```
POST /items/batch
{
  "action": "archive" | "unarchive" | "bin" | "restore" | "favorite" | "unfavorite",
  "item_ids": [1, 2, 3, ...]
}

Response:
{
  "results": [
    {"item_id": 1, "status": "ok"},
    {"item_id": 2, "status": "ok"},
    {"item_id": 3, "status": "error", "detail": "Item not found"}
  ]
}
```

This is a CONTRACT CHANGE. Single endpoint, supports all lifecycle actions. Per-item results enable partial failure handling.

**Scope:** `read_write` token required.
**Limit:** Cap at 100 items per request to bound server-side work.

#### 9.2 Library list endpoint (or extended items endpoint)

The Android client needs a way to fetch library views that matches the web's semantics. Options:

**Option A: Extend `GET /items` with view filtering**
```
GET /items?view=inbox&sort=created&dir=desc&limit=25&offset=0
GET /items?view=favorites&sort=created&dir=desc&limit=25&offset=0
GET /items?view=archived&sort=archived&dir=desc&limit=25&offset=0
GET /items?view=trash&limit=25&offset=0
```

**Option B: New `GET /library` endpoint**
```
GET /library?view=inbox&...
```

**Recommendation:** Option A — extend `GET /items` with a `view` query parameter. The items endpoint already exists and supports filtered queries. Adding `view=inbox|archived|favorites|trash` as a convenience filter avoids a new endpoint while providing the semantics the client needs. The response format matches the existing items list format.

**Note:** The existing `/playback/queue` endpoint remains for playback-specific use (initial Up Next seed from Inbox, which applies playback-eligibility filtering). The library list endpoint (`GET /items?view=...`) is separate and returns all items regardless of playback eligibility.

#### 9.3 Playlist reorder (already exists)

`PUT /playlists/{id}/entries/reorder` already exists with the correct semantics. Android just needs to call it.

#### 9.4 Batch add-to-playlist

```
POST /playlists/{playlist_id}/items/batch
{
  "item_ids": [1, 2, 3],
  "position": "end" | "start" | number
}
```

Per-item results (some may already be in playlist, some may not exist).

#### 9.5 Up Next server-side representation (DEFERRED to v2+)

**Not in v1 scope.** Up Next is device-local in v1 (stored in Room). No backend work required for Up Next in v1.

When cross-device Up Next sync is added (v2+), a system playlist on the server will back it. That work is a future contract change and not part of this redesign's backend scope.

#### 9.6 Search endpoint formalization

The web uses inline FTS via the inbox endpoint. Android should use a dedicated search endpoint or the same items endpoint with a `q` parameter:

```
GET /items?view=inbox&q=searchterm&limit=25&offset=0
```

FTS is already implemented in the backend (with ILIKE fallback). No new backend work needed, just Android client support for the query parameter.

### Backend sequencing

1. **Backend phase 1:** `GET /items?view=...` query parameter support (extend existing endpoint). Blocks Android Phase 2.
2. **Backend phase 2:** `POST /items/batch` batch actions endpoint. Blocks Android Phase 4.
3. **Backend phase 3:** `POST /playlists/{id}/items/batch` batch playlist add. Blocks the batch-to-playlist UX in Android Phase 4.
4. **Deferred:** Up Next server-side support (v2+ when cross-device sync is prioritized).

**"Backend-first" scope clarification:** Backend work is a prerequisite only for contract-dependent Android features. Android-internal work (ViewModel extraction, drawer shell, mini-player, Locus restructure) does not block on backend changes and can proceed in parallel.

---

## 10. Android Implications

### Architecture changes

#### 10.1 ViewModel extraction (prerequisite)

Before the UI redesign, extract the ViewModel from `MainActivity.kt`. Target structure:

- `MimeoViewModel` — core business logic, action methods, state flows
- `NavigationViewModel` or navigation state holder — route management, drawer state
- `PlaybackViewModel` or keep `PlaybackEngine` — playback-specific state and controls

The ViewModel extraction does not change behavior. It is a pure refactor that makes subsequent work tractable.

#### 10.2 Navigation overhaul

Replace `NavigationBar` + 4-tab `NavHost` with:
- `ModalNavigationDrawer` (Material 3) wrapping the app shell
- `NavHost` with routes for each library view, Up Next, Settings, Locus
- Drawer state managed in navigation ViewModel
- Current route highlighted in drawer

#### 10.3 Shared list infrastructure

Library views (Inbox, Favorites, Archive, Bin) share the same list UI with different data sources and action sets. Build a shared `LibraryListScreen` composable that accepts:
- Data source (StateFlow of items with pagination)
- Available actions (contextual per view)
- Sort/filter controls (per view)
- Selection state
- Empty state content

#### 10.4 Pagination

Replace the current "fetch everything at once" pattern with simple offset/limit pagination:
- Library views (`GET /items?view=...`) use `limit=25&offset=0` for the initial load; a "Load more" button or scroll-triggered next-page load appends items using `offset=N`
- No Jetpack Paging 3 required for v1 — a manual offset counter per screen is sufficient
- Up Next and playlists load fully (typically small collections; no pagination needed)

#### 10.5 Mini-player

Extract the current player bar concept into a persistent `MiniPlayer` composable that:
- Sits at the bottom of the app shell (above nothing — no more nav bar)
- Shows currently-playing item title, progress, play/pause
- Tapping expands to Locus (full screen or bottom sheet)
- Hidden when no playback session is active
- Does not interfere with drawer

#### 10.6 Selection state management

New `SelectionManager` (or state holder within ViewModel):
- `selectedItemIds: StateFlow<Set<Int>>`
- `isInSelectionMode: StateFlow<Boolean>`
- Methods: `select(id)`, `deselect(id)`, `toggleSelection(id)`, `clearSelection()`, `selectAll()`
- Auto-clears on navigation or filter change

### Data layer changes

- New `LibraryRepository` (or extend existing) for `GET /items?view=...` calls
- New `BatchActionRepository` for `POST /items/batch` calls
- Extend `PlaylistRepository` with `reorderEntries()` and `batchAddItems()`
- Pagination support in repository layer (simple offset counter per view; no Paging 3 required for v1)

### What is preserved exactly as-is

- `PlaybackEngine` — no changes
- `PlaybackService` — no changes
- `TtsController` — no changes
- Media session / audio focus — no changes
- Reader body rendering (`ReaderBody`, `ReaderHighlighting`, `ReaderLinkSupport`) — no changes
- Offline caching / auto-download — no changes
- Progress model / percent math — no changes
- Share receiver (`ShareReceiverActivity`) — no changes
- Auth flow — no changes
- Theme colors — no changes

---

## 11. Web Implications

### v1: Minimal web changes

The web app keeps its current top-tab navigation for v1. Backend changes (batch endpoint, `view` query parameter) benefit the web app but do not require web UI changes.

### v2: Web playlist management

Add playlist management to the web app:
- Playlist list view (accessible from nav or a new tab/sidebar entry)
- Playlist detail view with item list, reorder (drag-and-drop), add/remove
- This uses existing `GET /playlists`, `POST /playlists`, etc. endpoints

### v3: Web nav modernization

If warranted, move the web app from top tabs to a sidebar layout matching Android's drawer. This is a separate project and should not block Android work.

### Cross-surface behavior parity

| Capability | Android v1 | Web current | Web eventual |
|-----------|-----------|------------|-------------|
| Inbox / Favorites / Archive / Bin | ✓ (drawer) | ✓ (top tabs) | ✓ (sidebar) |
| Search within view | ✓ | ✓ | ✓ |
| Sort controls | ✓ | ✓ | ✓ |
| Multi-select / batch | ✓ | ✗ | ✓ (v2+) |
| Playlist CRUD | ✓ | ✗ | ✓ (v2) |
| Playlist reorder | ✓ | ✗ | ✓ (v2) |
| Up Next / playback | ✓ | ✗ | ✗ (Android-specific for now) |
| Locus / TTS | ✓ | ✗ | ✗ (Android-specific) |

---

## 12. Offline / Sync Model

### Tiered sync strategy

| Action type | Offline behavior | Sync mechanism | Conflict resolution |
|------------|-----------------|----------------|-------------------|
| **Single item actions** (archive, unarchive, favorite, unfavorite) | Optimistic local apply + queue | `PendingItemAction` queue, flushed on reconnect | Last-write-wins (server timestamp) |
| **Batch actions** | Optimistic local apply + queue N individual pending actions | Same as single, but N entries | Same |
| **Bin** | Optimistic local remove + queue | Pending action, flushed on reconnect | Server authoritative |
| **Playlist add/remove** | Optimistic local apply + queue | New `PendingPlaylistAction` queue | Server reconcile (add is idempotent; remove is idempotent) |
| **Playlist reorder** | Optimistic local apply + queue | New `PendingPlaylistReorder` — stores full position array | Last-write-wins on full position set |
| **Up Next edits** | Local-only in v1 — no server sync | Stored in Room; no pending-action queue needed | N/A (device is authoritative) |
| **Progress sync** | Debounced local → server | Existing `PendingProgressEntity` + WorkManager | Monotonic (server keeps higher percent) |

### Batch offline detail

When a batch archive of 10 items happens offline:
- All 10 items are optimistically removed from Inbox locally
- 10 `PendingItemAction(ARCHIVE)` entries are created
- The coalescing logic already handles deduplication (if the user archives, undoes, re-archives the same item)
- On reconnect, actions are flushed in order
- If some fail (item was already archived on another device), those failures are silent (idempotent)

### Playlist reorder offline detail

Playlist reorder while offline is trickier because it involves a full position array:
- The optimistic local state reflects the new order immediately
- A `PendingPlaylistReorder` is queued with the full `[(entry_id, new_position)]` array
- On reconnect, the reorder is sent as a single `PUT /playlists/{id}/entries/reorder` call
- If the playlist was modified on another device in the interim, the reorder applies to whatever entries still exist (missing entries are ignored by the server)

### What NOT to sync offline

- Playlist create/rename/delete: require connectivity (rare, intentional actions)
- Purge from bin: requires connectivity (destructive, needs server confirmation)
- Search: requires connectivity (server-side FTS)

---

## 13. Design Language / Design Workflow

### Design tokens (minimal but explicit)

#### Colors

| Token | Value | Usage |
|-------|-------|-------|
| `background` | `#000000` | App background |
| `surface` | `#0A0A0A` | Cards, sheets, elevated surfaces |
| `surfaceVariant` | `#141414` | Drawer background, secondary surfaces |
| `primary` | `#C6A7FF` | Accent, selected state, active indicators |
| `primaryContainer` | `#2C1A45` | Selected row background |
| `onSurface` | `#F4F1F8` | Primary text |
| `onSurfaceVariant` | `#B9B3C2` | Secondary text, metadata |
| `error` | `#CF6679` | Error pills, destructive action confirmation |
| `destructive` | `#CF6679` | Bin action, purge confirmation |

#### List row anatomy

```
┌─────────────────────────────────────────────────────────┐
│ [drag handle]  Title text (truncated to 2 lines)   [⋮] │
│                Source · 3 min · 45%  ▰▰▰▱▱          │
│                [status pill]  [♥]                       │
└─────────────────────────────────────────────────────────┘
```

- **Drag handle:** Only in ordered lists (Up Next, playlists). Leading edge. Grip icon (⠿), `onSurfaceVariant` color.
- **Title:** `bodyLarge`, `onSurface`, max 2 lines, ellipsis.
- **Metadata line:** `bodySmall`, `onSurfaceVariant`. Source domain · estimated time · progress percent.
- **Progress bar:** Thin (2dp), below metadata. `primary` for filled, `surfaceVariant` for track.
- **Status pill:** Small rounded chip. Colors per status (see below).
- **Favorite indicator:** ♥ in `primary` when favorited; absent when not.
- **Overflow:** ⋮ icon, trailing edge. Opens context menu.

#### Row states

| State | Visual treatment |
|-------|-----------------|
| **Default** | Normal rendering |
| **Selected** | `primaryContainer` background; leading check icon replaces thumbnail/handle |
| **Active/playing** | `primary` left border (3dp) or subtle glow; title in `primary` color |
| **Done (≥98%)** | Muted text (`onSurfaceVariant`); progress bar full in reduced opacity |
| **Destructive/binned** | Row shows trashed-at date; available actions are Restore and Purge |
| **Non-ready** | Status pill visible; title may be muted; tap behavior differs (see Section 6) |

#### Status pills

| Status | Pill text | Color |
|--------|-----------|-------|
| `extracting` | Processing | Amber/yellow |
| `failed` | Failed | Error red |
| `blocked` | Blocked | Warning orange |
| `manual_text` | Manual | Neutral gray |
| `done` | Done | Muted green (or none — use progress bar) |

#### Mini-player

```
┌─────────────────────────────────────────────────────────┐
│ ▶  Article Title Here (1 line, ellip…  ▰▰▰▱   [queue] │
└─────────────────────────────────────────────────────────┘
```

- Height: 56dp (Material standard)
- Background: `surface` with subtle top border or elevation
- Play/pause button: leading, `primary` color
- Title: `bodyMedium`, `onSurface`, single line
- Thin progress bar: bottom edge, `primary` fill
- Queue icon: trailing, opens Up Next

#### Expanded Locus

Preserves existing chrome modes (Full/Minimal/Nub). No changes to reader body, highlighting, or player control layout. The entry/exit animation is new:

- **Entry:** Mini-player expands upward to full screen (shared element transition on title text)
- **Exit:** Collapse back to mini-player (reverse animation)
- **Alternative:** Bottom sheet that expands from mini-player position (like Spotify/Apple Music)

#### Drawer

- Width: 300dp or 80% of screen width (whichever is smaller)
- Background: `surfaceVariant`
- Header: app logo (purple M on dark), user info if applicable
- Entries: `bodyLarge`, 48dp height, leading icon, trailing count badge
- Active entry: `primary` text, `primaryContainer` background
- Dividers: 1dp `surfaceVariant` border between sections

#### Sheets and transitions

- Bottom sheets: used for playlist picker, batch action confirmation, item status detail
- Sheet background: `surface` with rounded top corners (16dp radius)
- Sheet scrim: 40% black
- Transition duration: 300ms for sheet open/close; 250ms for navigation; 200ms for selection state

#### Icon rules

- Use Material Symbols (outlined, weight 400) consistently
- Size: 24dp for toolbar actions, 20dp for inline row icons, 18dp for pill icons
- Color: `onSurface` for primary actions, `onSurfaceVariant` for secondary, `primary` for active states

#### Motion rules

- Navigation transitions: horizontal slide (drawer destinations), vertical slide (sheet/Locus expansion)
- Selection mode entry: contextual action bar slides in from top (200ms)
- Row removal (archive/bin): horizontal slide-out (250ms) then collapse (200ms)
- Undo restoration: reverse of removal animation
- Preference: respect `Settings.Global.ANIMATOR_DURATION_SCALE` and `prefers-reduced-motion`

### Design workflow recommendation

For a solo developer not fluent in Figma:

1. **Skip Figma.** Use the design tokens above as your system-of-record.

2. **Design in Compose directly.** Create a `DesignCatalog` screen (dev-only, hidden behind developer toggle) that renders each component state:
   - List row in every state (default, selected, active, done, error)
   - Mini-player in idle and playing states
   - Contextual action bar with various selection counts
   - Drawer in open state with sample data
   - Each status pill variant

3. **Screenshot-driven iteration.** Take screenshots of the catalog on a real device. Annotate them (markup tool, or even just notes). Compare against the web app's current look for alignment.

4. **Reference apps for gesture/motion feel:** Install and study how these handle similar patterns:
   - **Gmail** for multi-select in lists
   - **Spotify** for mini-player ↔ full-screen player transitions and queue
   - **Pocket** or **Instapaper** for read-later library IA
   - **Google Tasks** or **Todoist** for drag-reorder with handles

5. **One screen at a time.** Don't try to nail the design system before building. Build the Inbox screen first with placeholder rows, iterate on the row design until it feels right, then propagate to other screens.

---

## 14. Accessibility

### Requirements to bake in from the start

#### Touch targets
- All interactive elements: minimum 48dp × 48dp touch target
- Drag handles: 48dp wide minimum (even if the icon is 24dp, the touch area extends)
- Overflow menu trigger: 48dp
- Mini-player tap area: full width of the bar

#### Screen reader (TalkBack)

| Element | Content description |
|---------|-------------------|
| List row | "{Title}. {Source}. {Progress} percent. {Status if non-ready}. {Favorited if true}." |
| Selected row | "Selected. {Title}. {Source}. ..." |
| Drag handle | "Drag to reorder. {Title}." |
| Mini-player | "Now playing: {Title}. {Progress} percent. Double-tap to expand." |
| Batch action bar | "{N} items selected. Actions available: Archive, Bin, Favorite, Add to Playlist." |
| Status pill | "{Status}. Tap for details." |

#### Non-drag reorder alternatives

For users who cannot use drag-reorder:
- Context menu (long-press or overflow) → "Move up" / "Move down" actions
- These actions move the item one position in the list
- Available on every item in ordered lists (Up Next, playlists)
- Announced to screen reader after action: "Moved to position {N}"

#### Selected state

- Selected rows use `primaryContainer` background AND a leading checkmark icon
- State is announced to screen reader ("Selected" / "Not selected")
- Batch action bar announces available actions when selection mode activates
- "Select all" and "Clear selection" are both available and announced

#### Contrast

Current color scheme:
- `onSurface` (#F4F1F8) on `background` (#000000): contrast ratio ~19:1 ✓
- `onSurfaceVariant` (#B9B3C2) on `background` (#000000): contrast ratio ~10:1 ✓
- `primary` (#C6A7FF) on `background` (#000000): contrast ratio ~8:1 ✓
- `primary` (#C6A7FF) on `primaryContainer` (#2C1A45): contrast ratio ~5.5:1 ✓

All pass WCAG AA (4.5:1 for normal text, 3:1 for large text).

#### Focus states

- All focusable elements show a visible focus indicator (2dp `primary` outline) when focused via keyboard/d-pad
- Focus order follows visual order (top to bottom, left to right)
- Drawer entries, list items, action buttons, and mini-player are all focusable

#### Motion reduction

- Check `Settings.Global.ANIMATOR_DURATION_SCALE` and `AccessibilityManager.isEnabled`
- When reduced motion is preferred: skip slide animations, use instant transitions, disable auto-scroll in reader
- Drag-reorder still works with reduced motion (it is a direct manipulation, not an animation)

---

## 15. Phased Rollout Plan

### Phase 0: Foundation (no user-visible changes)

**Goal:** Make the codebase ready for the redesign without changing any behavior.

**Scope:**
- Extract `MimeoViewModel` from `MainActivity.kt` into its own class
- Extract navigation state management
- Ensure all existing tests pass against extracted code
- No UI changes, no new screens, no behavior changes

**Estimated complexity:** Medium. The extraction is mechanical but touches everything. Risk of subtle regressions in state flow wiring.

**Gate:** All existing tests pass. Manual smoke test of every current screen.

### Phase 1: Backend contracts

**Goal:** Ship the backend changes that Android will consume.

**Scope:**
1. Extend `GET /items` with `view=inbox|archived|favorites|trash` query parameter
2. Implement `POST /items/batch` batch actions endpoint
3. Implement `POST /playlists/{id}/items/batch` batch playlist add endpoint
4. Ensure pagination works consistently (`limit`/`offset`) on items endpoint
5. All endpoints have tests

**Sequencing:** This can be done in parallel with Phase 0 (different repo).

**Gate:** Backend tests pass. Endpoints manually tested via curl/Postman.

**CONTRACT CHANGE:** Yes. Label all PRs. Android can begin consuming once merged.

### Phase 2: Drawer navigation + library views + basic playlist visibility

**Goal:** Replace bottom nav with drawer. Ship Inbox, Favorites, Archive, Bin as library views. Keep playlists visible and navigable from day one of the drawer.

**Scope:**
1. Implement `ModalNavigationDrawer` shell with all destinations
2. Implement shared `LibraryListScreen` composable
3. Wire Inbox view to `GET /items?view=inbox` (replacing `/playback/queue` as the home data source)
4. Wire Favorites, Archive, Bin views to their respective queries
5. Implement sort controls per view (per-view persistence via DataStore)
6. Implement search per view
7. Remove bottom navigation bar
8. Remove Collections tab and screen
9. **Up Next as drawer destination** (from day one, visible below library views and above playlists section)
10. **Playlists section in drawer** (read-only list: names and counts, tap navigates to existing playlist detail screen)
11. "New Playlist" entry point at the bottom of the playlists section (opens existing create-playlist dialog)
12. Preserve Settings as a drawer destination
13. Handle non-ready items in Inbox (status pills, tap behavior, collapsible Pending section)

**What is NOT in this phase:**
- Multi-select or batch actions (Phase 4)
- Mini-player or Locus restructure (Phase 3)
- Playlist reorder, rename-in-drawer, delete (Phase 5)
- Up Next reorder from within the Up Next drawer destination (Phase 6 — Phase 2 uses the existing Up Next UI behind the new drawer entry)

**Transition strategy:** The old Collections screen is removed in this phase, but the existing playlist detail screen is preserved and wired into the drawer. Users do not lose the ability to view or play from their playlists — only the Collections tab aggregation is removed. Full playlist management (reorder, CRUD from drawer) arrives in Phase 5.

**Gate:** All library views display correct items with correct filtering. Sort and search work. Navigation via drawer works. Playlist detail screen opens from drawer. Up Next still works for playback. No playback regressions.

### Phase 3: Mini-player + Locus restructure

**Goal:** Move Locus from a nav tab to a persistent mini-player + expandable surface.

**Scope:**
1. Implement `MiniPlayer` composable bar at bottom of app shell
2. Mini-player shows when playback session is active; hidden otherwise
3. Tap mini-player → expand to Locus (full-screen, with back navigation)
4. Locus removed from drawer destinations
5. Tapping an item in Up Next opens Locus (as before)
6. Tapping an item in library views opens Locus in reader-only mode (new behavior)
7. Preserve all existing Locus chrome modes (Full/Minimal/Nub)
8. Preserve all existing playback behavior

**The critical subtlety:** Opening an item from a library view should not disrupt active playback of another item. If the user is listening to Item A and taps Item B in Inbox, Item B opens in Locus (reader-only) while Item A continues playing. The mini-player continues showing Item A. If the user then presses play on Item B, playback ownership transfers to Item B.

**Gate:** Mini-player appears/hides correctly. Locus opens from all entry points. Playback survives Locus entry/exit. No playback regressions. Existing Locus tests pass.

### Phase 4: Multi-select + batch actions

**Goal:** Ship multi-select UX and batch operations across all list views.

**Scope:**
1. Implement `SelectionManager` state holder
2. Long-press to select in all list views
3. Contextual action bar with batch actions
4. Wire batch actions to `POST /items/batch` endpoint
5. Implement partial failure handling (mixed success/error display)
6. Implement snackbar undo for batch archive and batch bin
7. Undo restores item state (archive flag, favorite flag)
8. Selection clears on navigation, filter change, search change
9. Implement "Add to Playlist" batch action with playlist picker sheet

**Gate:** Multi-select works in all list views. Batch archive, bin, favorite, unfavorite, add-to-playlist all work. Undo works. Partial failure displays correctly.

### Phase 5: Full playlist management + reorder

**Goal:** Full playlist management including rename, delete, and drag-to-reorder within playlists.

**Scope:**
1. Upgrade drawer playlist entries to show inline rename/delete affordances (via long-press or detail-screen overflow)
2. Drag-to-reorder in playlist detail view (drag handles)
3. Wire reorder to `PUT /playlists/{id}/entries/reorder`
4. Rename playlist (confirmation input)
5. Delete playlist (confirmation dialog; does not delete items, only the playlist container)
6. "Play Next" / "Play Last" actions from library item overflow menus (these insert into Up Next, not into user playlists)
7. Extend offline sync for playlist reorder (new `PendingPlaylistReorder` action type that stores full position array)

**What is NOT in this phase:**
- Up Next reorder UI (that's Phase 6 as part of finalizing Up Next)
- Multi-select within playlists (inherited from Phase 4)

**Gate:** Playlist rename/delete work end-to-end. Reorder persists to server. Offline reorder syncs on reconnect.

### Phase 6: Up Next finalization

**Goal:** Finalize Up Next as a distinct, device-local, reorderable playback queue with seed-source semantics.

**Scope:**
1. Up Next drawer destination gets the refined queue UI (reorderable, drag handles)
2. Up Next stored in Room with ordered entries + `seed_source` + `seeded_at` metadata
3. Initial seed on first use from Inbox newest-first
4. "Re-seed from Inbox" and "Re-seed from [playlist]" actions in Up Next overflow menu
5. Re-seed confirmation dialog when Up Next has user edits
6. "Play Next" / "Play Last" actions (from Phase 5) fully integrated as insertion points
7. Up Next inherits selection + batch actions from Phase 4
8. Move-up / move-down accessibility actions for reorder (non-drag alternative)
9. Visual indicator: "Playing from: [seed source]" label at the top of Up Next (informational only)

**Gate:** Up Next functions as a reorderable playback queue. Edits persist across app restart. Seed/re-seed flows work. Edits to Up Next do not affect source playlists (verified by inspection and test).

### Later phases (v2+)

- **Grouped drag reorder** (select multiple items, drag as group)
- **Playlist folders** (not in current plan; would need fresh product decision)
- **Playlist-level "include archived items"** option
- **Cross-device Up Next sync** (system playlist on server)
- **Global search** across all views (single search bar that searches everywhere)
- **Web playlist management** (CRUD + reorder in web UI)
- **Web nav modernization** (sidebar to match Android drawer)
- **Duplicate-in-playlist UX** (explicit handling when adding duplicate)
- **Undo playlist membership restoration** (richer undo snapshots)
- **Light mode** (theme variant)

---

## 16. Key Risks and Traps to Avoid

### Risk 1: The monolith extraction goes wrong

**Risk:** Extracting the ViewModel from the 5000+ line `MainActivity.kt` introduces subtle state flow regressions.
**Mitigation:** Do Phase 0 as a pure refactor with zero behavior changes. Run the full existing test suite. Manual smoke test every screen. Commit frequently so regressions are bisectable.

### Risk 2: Inbox and Up Next become confusingly similar

**Risk:** Users don't understand why there are two lists of their items. "Why can't I just play from Inbox?"
**Mitigation:** Clear labeling and distinct visual treatment. Inbox is for browsing and organizing (no drag handles, sort controls prominent). Up Next is for playback (drag handles, queue-style, currently-playing indicator). Items tapped in Inbox open in reader mode; items tapped in Up Next start playback. The difference is functional, not just visual.

### Risk 3: Playback disruption during Locus restructure

**Risk:** Moving Locus from a nav tab to an overlay/sheet breaks playback continuity or media session behavior.
**Mitigation:** Phase 3 should change ONLY the entry/exit path for Locus. Internal Locus behavior (reader, player, controls, highlights, progress sync) should be preserved byte-for-byte. The PlaybackEngine and PlaybackService are already decoupled from the Locus composable — they run in the service. The risk is in navigation state, not playback state.

### Risk 4: Batch endpoint races with offline queue

**Risk:** User does a batch archive offline; 10 pending actions are queued. Before sync, the user does more individual actions on some of those items. The pending action queue may have conflicting entries.
**Mitigation:** The existing coalescing logic (`coalescePendingItemActions`) already handles this by keeping only the latest action per (item, action-family) pair. Batch actions simply enqueue N individual pending actions, which coalesce normally.

### Risk 5: Creeping scope in Phase 2

**Risk:** "Just drawer + library views" turns into "also fix the row design, also add pagination, also change the empty state, also..." and Phase 2 becomes a 3-month project.
**Mitigation:** Phase 2 has an explicit NOT-in-scope list: no multi-select, no batch actions, no mini-player, no Locus changes, no playlist management. The existing row design is acceptable for Phase 2. Pagination can use the simple "fetch first page + load more button" pattern rather than full Paging 3 integration.

### Risk 6: Losing the "playback-first" feel

**Risk:** The app feels like a document manager that happens to have a play button. The playback experience becomes second-class.
**Mitigation:** The mini-player is always present during playback. It is the most prominent persistent UI element. Locus opens instantly from any screen. The transition from "browsing library" to "listening to article" should be one tap and feel seamless. Playback features (speed, voice, FF/RW, sentence highlight) are untouched. The goal is "library that reads to you," not "library with a hidden player."

### Risk 7: Android-web divergence grows instead of shrinks

**Risk:** The redesign makes Android more complex while the web stays static, and the two drift further apart.
**Mitigation:** Backend-first sequencing (Phase 1) ensures the API contracts are shared. The library view semantics (Inbox/Favorites/Archive/Bin) are identical between Android and web. Playlist management will eventually come to web (v2). The IA is converging even if the navigation chrome differs.

### Risk 8: Undo complexity explodes

**Risk:** Multi-item undo with playlist membership restoration and cross-surface undo creates a combinatorial explosion of snapshot/restore logic.
**Mitigation:** v1 undo is explicitly scoped: snackbar undo reverses only the archive/favorite/bin flag changes for affected items. It does NOT restore playlist membership, Up Next position, or list scroll state. Cross-navigation undo holds the snapshot in ViewModel state but does not persist it to disk. If the app is killed, undo is lost. This is acceptable. Playlist membership restoration is a v2 feature with its own design pass.

### Risk 9: "Active playlist" ambiguity

**Risk:** The phrase "active playlist" suggests a live mirror between Up Next and a user playlist. Users or implementers could assume that reordering Up Next reorders the source playlist, or that deleting from Up Next removes from the source playlist. This would cause data surprise: "I dragged something in my queue and it disappeared from my Podcasts playlist."
**Mitigation:** The product rule is explicit (Section 6): **Up Next edits never mutate the source playlist.** The source is informational only. Avoid the phrase "active playlist" in UI, docs, and code. Use "seed source" or "playing from" instead. The implementation should make this impossible by construction — Up Next is its own table, its own ordered list, and its mutation path does not touch the playlists table.

### Risk 10: Re-seed data loss

**Risk:** A user has carefully curated their Up Next over time. They accidentally tap "Re-seed from Inbox." Their curation is wiped.
**Mitigation:** Re-seeding is a destructive action. When Up Next has user edits (detected by comparing current contents against what the seed source would produce, or simply by a dirty bit set on any user edit), the re-seed action prompts for confirmation: "This will replace your current Up Next. Continue?" This is similar to the Bin purge confirmation pattern.

### Risk 11: Drag handles feel ugly

**Risk:** Permanent drag handles on every row in ordered lists add visual noise and conflict with the "minimalist" aesthetic.
**Mitigation:** Make handles visually recessive (small, `onSurfaceVariant`, not prominent). They appear only in ordered lists (Up Next, playlists), not in library views. Consider showing handles only when the list has >1 item. If handles prove too noisy, v2 can explore an "Edit" mode that reveals handles on demand — but start with always-visible handles because they are more discoverable and accessible.

### Risk 12: Progress model confusion when opening items from library vs Up Next

**Risk:** Opening an item from Inbox (reader-only mode) vs Up Next (playback mode) creates two different progress update paths for the same item, potentially causing progress to jump or regress.
**Mitigation:** Progress update rules are consistent regardless of entry point (see Section 7). Canonical percent is monotonic. Reader-only scroll updates progress just like playback does. The difference is only in whether TTS starts automatically. The progress model does not change — only the default mode on entry changes.

---

*End of planning document. This document should be reviewed, debated, and revised before implementation begins. Key product decisions (marked in Section 4) require explicit sign-off.*

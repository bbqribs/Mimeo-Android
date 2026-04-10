# Android Item Actions Spec

**Version:** 1.0
**Status:** Design-only; no implementation in this ticket
**Date:** 2026-04-10
**Scope:** Up Next, Locus, and Archive/Bin list views (same overflow model)

---

## 1. Problem statement

Item actions are inconsistent across surfaces:

- **Up Next** rows have a `⋮` overflow menu only; no inline quick-actions.
- **Locus** has inline Favorite + Archive icons plus a `⋮` overflow.
- Neither surface exposes **Share URL** or **Open in browser** actions.
- Long-press on Up Next rows does nothing.
- There is no canonical list of "what actions exist and where."

This spec defines the unified model used by all follow-on tickets.

---

## 2. Surfaces in scope

| Surface | Entry point |
|---------|-------------|
| Up Next (QueueScreen) | Per-item `⋮` button; long-press shortcut |
| Locus expanded (PlayerScreen) | Inline top-bar icons; `⋮` overflow |
| Archive view (Up Next filter) | Per-item `⋮` button |
| Bin view (Up Next filter) | Per-item `⋮` button |

Pending-save rows (failed/processing items) are not in scope; they already have their own Retry/Dismiss model.

---

## 3. Canonical action inventory

These are all lifecycle actions an item can receive. Each has a surface placement decision below.

| Action | Description |
|--------|-------------|
| Open in Locus | Tap to open/play (default tap target — not in overflow) |
| Play this item | Explicitly start playback for the item in Locus |
| Favourite / Unfavourite | Toggle `isFavorited` flag |
| Archive / Unarchive | Move to/from Archive view |
| Move to Bin | Soft-delete; 14-day expiry |
| Restore from Bin | Move Bin item back to Inbox |
| Purge permanently | Hard delete from Bin |
| Download for offline | Cache item text locally (shown only when not cached) |
| Playlists… | Open playlist-picker to add/remove playlist membership |
| Share URL | Send item's source URL via Android share sheet |
| Open in browser | Open item's source URL in default external browser |
| Report problem | (Locus only) Submit feedback about item content |

---

## 4. Action placement per surface

### 4.1 Up Next — normal queue items

**Primary tap:** open in Locus.

**`⋮` overflow menu (ordered):**
1. Download for offline *(only if not yet cached)*
2. Playlists…
3. Favourite / Unfavourite
4. Archive
5. Open in browser *(only if item has a URL)*
6. Share URL *(only if item has a URL)*
7. Move to Bin (14 days)

**Long-press:** opens the same `⋮` overflow menu. This avoids building a separate selection model for now.

No inline quick-action icons on the row itself (rows are already dense; the `⋮` icon is the single action affordance).

### 4.2 Up Next — Archive view items

**Primary tap:** open in Locus.

**`⋮` overflow menu:**
1. Favourite / Unfavourite
2. Unarchive
3. Open in browser *(only if item has a URL)*
4. Share URL *(only if item has a URL)*
5. Move to Bin (14 days)

### 4.3 Up Next — Bin view items

**Primary tap:** disabled (bin-row taps do nothing).

**`⋮` overflow menu:**
1. Restore
2. Open in browser *(only if item has a URL)*
3. Share URL *(only if item has a URL)*
4. Purge permanently

### 4.4 Locus expanded — top-bar icons (inline)

Keep existing inline icons for the two highest-frequency actions:

| Icon | Action | Notes |
|------|--------|-------|
| Search | Search in article | Only in expanded Locus |
| ♥ (heart) | Favourite / Unfavourite | Filled = favourited |
| Archive box | Archive / Unarchive | Filled = archived |
| `⋮` | Overflow | See 4.5 |

No additional inline icons. The bar is already at its comfortable width.

### 4.5 Locus expanded — `⋮` overflow menu (ordered)

1. Play this item *(when playback is not already active on this item)*
2. Playlists…
3. Open in browser *(only if item has a URL)*
4. Share URL *(only if item has a URL)*
5. Report problem *(only when signed in)*
6. Move to Bin (14 days)
7. Collapse player / Expand player

---

## 5. Share actions — exact behavior

### Share URL

```
Android share sheet:
  EXTRA_SUBJECT = item title (or empty if unavailable)
  EXTRA_TEXT    = item source URL
  type          = "text/plain"
  chooser title = "Share"
```

Apps that show both subject and text (e.g. email) will display title + URL. Apps that show only text (e.g. SMS, clipboard) will show the URL alone. No custom format string needed.

Only shown when the item has a non-blank source URL. The field to check is `PlaybackQueueItem.url` (or `displayPayload.url` in Locus).

### Open in browser

```kotlin
Intent(Intent.ACTION_VIEW, Uri.parse(url))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
```

Standard external-open, identical to how in-article link taps already work in `ReaderBody.kt`. Only shown when URL is non-blank.

### No "Share title + URL" as a second option

One share action is sufficient. The Android system chooser handles formatting; adding a second "share title + URL" item would clutter the menu and Android apps already handle EXTRA_SUBJECT.

---

## 6. Long-press behavior

**Decision: long-press opens the overflow menu (same as tapping `⋮`).**

Rationale:
- It is the standard Android list long-press pattern.
- Adding multi-select requires a selection state model and a contextual action bar — deferred to a separate ticket in the redesign v2 scope.
- Long-press to multi-select can be retrofitted later without changing this model; long-press-to-context-menu is a subset of that future behavior.

---

## 7. URL availability rules

Some items may not have a source URL (manual text pastes, excerpt-only captures). The "Open in browser" and "Share URL" actions must be guarded:

```kotlin
val hasUrl = item.url?.isNotBlank() == true
```

Hide both actions entirely (not just disable them) when `hasUrl` is false. Do not show a disabled/greyed row.

---

## 8. Action labels (canonical strings)

Use these exact labels in implementation to keep copy consistent:

| Action | Label |
|--------|-------|
| Favourite | "Favourite" |
| Unfavourite | "Unfavourite" |
| Archive | "Archive" |
| Unarchive | "Unarchive" |
| Move to Bin | "Move to Bin (14 days)" |
| Restore | "Restore" |
| Purge | "Purge permanently" |
| Download | "Download for offline" |
| Retry download | "Retry offline cache" |
| Playlists | "Playlists…" |
| Share | "Share URL" |
| Open in browser | "Open in browser" |
| Report problem | "Report problem" |
| Play | "Play this item" |
| Expand | "Expand player" |
| Collapse | "Collapse player" |

These match existing strings where they already exist.

---

## 9. Out of scope for this ticket

- Multi-select / batch actions (redesign v2 ticket)
- Swipe-to-archive gesture (separate decision ticket)
- Inbox view actions (Inbox does not yet exist on Android; when it does, it uses the same model as Up Next normal queue)
- Pending-save row actions (already have Retry/Dismiss)
- Collections/Folder view item actions (out of scope for now; spec when that surface ships)

---

## 10. Recommended implementation order

1. **Share URL + Open in browser — Up Next overflow** (self-contained; adds two menu items + a `sharePlainText`-style helper, already present in `SettingsScreen.kt`; extract to shared util)
2. **Share URL + Open in browser — Locus overflow** (same helper; add to `LocusOverflowMenuItems`)
3. **Long-press on Up Next rows** (wire `combinedClickable` on `QueueItemCard`; trigger `onExpandMenu`)
4. **Archive view: Share URL + Open in browser** (same guard, two new menu items)
5. **Bin view: Share URL + Open in browser** (same guard)

Steps 1–2 are independent of each other and of step 3. Steps 4–5 are trivial once step 1 is done.

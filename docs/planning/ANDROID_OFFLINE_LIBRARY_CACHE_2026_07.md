# T-AND-OFFLINE-LIBRARY-CACHE-1 — Keep saved articles visible offline, with a discreet sync indicator

Base SHA: `632f4b6` (main). Branch: `claude/t-and-offline-library-cache-1`.
Android-only. Draft PR to `main`; operator merges.

## Problem (verified 2026-07-24 on main @ `632f4b6`)

1. **Inbox has no offline cache.** `AppViewModel.loadInboxItems()` →
   `loadLibraryItemsView(INBOX)` populates `_inboxItems` *only* from a live network
   fetch (`AppViewModel.kt:5119`). On a network error the catch block sets an offline
   flag and returns failure without consulting any cache; `_inboxItems` starts as
   `emptyList()`. So a cold launch while offline always shows "No inbox items."
   The `cached_items` Room table stores only the *text* of opened/downloaded
   articles, not the Inbox listing — it is never consulted by the list load.
2. **Smart Queue snapshot is hidden after 24 h.** `T-AND-PENDING-SAVE-SURFACING-1`
   (#471) added a hard 24 h staleness cutoff to `SettingsStore.loadQueueSnapshot`, so
   the one surface that *did* render offline goes empty after a day offline.
3. Net effect: leaving the app closed/offline for a while makes stored articles
   disappear from display. Offline reading/browsing of already-synced content should
   keep working; only *saving to* / *fetching new from* the runtime requires network.

Out of scope of the disappearance: "No active session. Open an item to start one."
is the Now Playing session empty state (`QueueScreen.kt:733`), restored from Room
(`now_playing` table) independent of network — it is empty only when no playback
session was ever started, not because of connectivity. Not changed here.

## Design decision (operator, 2026-07-24)

Show cached content offline rather than hiding it, marked with a **minimal** indicator:
a discreet "Offline · Last sync: X ago" line (with a cloud-off icon) in the drawer
footer, shown only when offline. Drop the hard 24 h hide-cliff for offline *display* —
the indicator makes clear the data is a saved copy, and any successful refresh replaces
the cache wholesale so trashed/removed items drop out once back online. This resolves
the tension with #471 (which hid stale snapshots precisely so trashed items could not
masquerade as live).

## Scope

### A. Offline cache for library list views
- Persist the last successful listing per view (INBOX, and — same code path, cheap —
  FAVORITES and ARCHIVED) to DataStore with a `savedAt` timestamp.
- `loadLibraryItemsView` / `loadArchivedItems`: on success, save the snapshot and stamp
  a global "last successful sync" time; on a **network** error (not auth), load the
  snapshot into the corresponding state and mark offline. Non-network errors keep
  current behaviour.
- Successful refresh replaces the per-view snapshot wholesale (no row resurrection).

### B. Relax the Smart Queue snapshot age cutoff
- Keep the `savedAt` stamp but stop hiding queue snapshots older than 24 h; offline
  display now relies on the indicator instead of a hard cutoff. Preserve the existing
  behaviour that a successful refresh replaces the snapshot and drops absent rows.

### C. Minimal offline / last-sync indicator
- Expose an `OfflineSyncState` (isOffline, lastSuccessfulSyncAtMs) from the ViewModel.
- Render it discreetly in the drawer footer (cloud-off icon + "Offline · Last sync:
  X ago"), only when offline. No new top-bar chrome; no per-screen banners beyond what
  already exists.

### Cross-cutting constraints
- **Account/endpoint isolation:** per-view snapshots and the last-sync stamp are
  account-scoped and cleared on sign-out alongside the existing queue snapshot
  (`clearAccountScopedDataStoreState`).
- **Privacy:** the indicator shows only a relative time; no URLs/titles/tokens.
- **No behavioural regressions** to Smart Queue ranking, Up Next/Now Playing, reader,
  drag-and-drop, or notifications.

## Tests

JVM unit (Robolectric where DataStore is involved):
- Library-view snapshot round-trips (save → load) per view; wholesale replace on
  re-save; account clear wipes it.
- `loadLibraryItemsView` offline fallback: network error with a saved snapshot renders
  the cached items and marks offline; with no snapshot stays empty.
- Queue snapshot no longer nulls purely on age (update the #471 expiry test).
- `OfflineSyncState` / "last sync X ago" formatting is correct at boundaries.

## Physical-device acceptance (current test device, Mimeo Debug build)
1. Online: open Inbox and Up Next so content loads.
2. Airplane mode, force-close the app, reopen → Inbox still shows the saved articles;
   drawer shows "Offline · Last sync: …". Wait past 24 h (or lower the threshold) and
   confirm content is still shown (no longer hidden).
3. Re-enable network, refresh → indicator clears; items removed server-side disappear.

## Non-goals / stop conditions
- No backend/contract changes; no new not-ready queue query; no offline *writes* beyond
  the existing pending-action queue.
- Do not add offline text for articles never downloaded (that is the existing
  auto-download/cached-text feature, unchanged here).
- Stop and report if correct offline rendering would require a backend change.

## Delivery
Draft PR to `main`: summary, design choices, changed files, unit-test + build results,
device-acceptance result or exact blocker. Do not merge.

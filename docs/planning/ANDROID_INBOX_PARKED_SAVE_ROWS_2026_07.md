# T-AND-INBOX-PARKED-SAVE-ROWS-1 — Show parked (not-yet-uploaded) saves in the Inbox Pending section

Base SHA: `a1bbbf6` (main). Branch: `claude/t-and-inbox-parked-save-rows-1`.
Follow-up to `T-AND-PENDING-SAVE-SURFACING-1` (merged as PR #471), which explicitly
scoped out "no redesign of Pending Saves UI".
Android-only. Draft PR to `main`; operator merges.

## Problem (verified 2026-07-24 on main @ `a1bbbf6`)

1. `LibraryItemsScreen.kt:360` computes
   `pendingItems = sortedItems.filter { it.status in PENDING_STATUSES }`, and
   `sortedItems` derives entirely from **server-fetched** items. A save parked
   client-side (never created server-side) therefore cannot appear in the Inbox
   Pending section at all.
2. Parked saves exist only in `vm.pendingManualSaves`
   (`AppViewModel.kt:417`, persisted via `SettingsStore.pendingManualSavesFlow`),
   rendered only in the Up Next / QueueScreen "Pending saves (N)" hub dialog
   (`QueueScreen.kt:1300`).
3. `LibraryItemsScreen.kt:240` defaults `pendingExpanded = false`, so even
   server-side pending rows are collapsed on arrival.

**Consequence:** after an offline share the user gets the parked notification
(shipped in #471), opens the Inbox, and sees nothing. This reads as data loss and
is the residual half of the save-disappearance report.

## Scope

### A. Project parked saves into the Inbox pending section

New pure helper, `app/src/main/java/com/mimeo/android/ui/library/InboxParkedSaveProjection.kt`:

```kotlin
internal fun projectParkedSavesForInbox(
    parkedSaves: List<PendingManualSaveItem>,
    serverItems: List<PlaybackQueueItem>,
): List<PendingManualSaveItem>
```

Dedupe rules (a parked row is **hidden** when the server already represents it):

- If `parked.resolvedItemId != null` and any `serverItems` entry has that
  `itemId` → hide (the real row is already in the list, pending or ready).
- Else if `parked.urlInput` yields a non-null normalized URL and any
  `serverItems` entry's `url` normalizes equal → hide.
- Otherwise → show.

Normalization mirrors `QueueScreen.normalizePendingComparisonUrl`: `extractFirstHttpUrl`
→ `trim` → `lowercase` → `removeSuffix("/")`; a null/blank result never matches
(so `TEXT`-type parked saves with no URL are never URL-deduped against server rows
that also lack a URL). Extract that normalizer to a shared location rather than
duplicating it — `ShareSaveUtils.kt` is the natural home; leave the QueueScreen
call site delegating to the shared function so both paths cannot drift.

Ordering: newest first by `createdAtMs`. Parked rows render **above** server-side
pending rows inside the same section (a parked save is the newest thing the user did).

### B. Render parked rows, clearly marked as not-yet-uploaded

- New `LibraryItemsScreen` params (all defaulted so the other four library call
  sites are untouched):
  `parkedSaves: List<PendingManualSaveItem> = emptyList()`,
  `onRetryParkedSave: ((PendingManualSaveItem) -> Unit)? = null`,
  `onDismissParkedSave: ((PendingManualSaveItem) -> Unit)? = null`.
  Only the `ROUTE_INBOX` call site in `MainActivityShell.kt:505` passes them
  (wired to `vm.pendingManualSaves`, `vm.retryPendingManualSave`,
  `vm.removePendingManualSave`).
- Parked rows are consumed only when `isInbox` is true; ignore otherwise.
- New private `ParkedSaveRow` composable in `LibraryItemsScreen.kt` — deliberately
  **not** `LibraryQueueItemRow`, which is keyed on a server `itemId` the parked row
  does not have. It shows:
  - title line: `titleInput` → else `urlInput` → else "Pasted text" → else "(no title)"
    (same precedence as `PendingManualRetryCard`);
  - a secondary line marking it as not uploaded, e.g. **"Not uploaded yet — will retry"**
    when `autoRetryEligible`, otherwise **"Not uploaded — tap to retry"** plus
    `lastFailureMessage`;
  - an overflow menu with **Retry** and **Dismiss** only.
  No selection checkbox, no playback actions, no favorite/archive/bin, no drag handle.
- Section header count becomes `parkedRows.size + pendingItems.size`. Header text
  stays "Pending (N)".
- LazyColumn keys: `"parked_${it.id}"` (`id` is a `Long`, disjoint from the existing
  `"p_${itemId}"` server keys).

### C. Expansion default

Add a pure helper alongside the projection:

```kotlin
internal fun shouldAutoExpandPending(parkedCount: Int, previouslyExpanded: Boolean): Boolean
```

Returns `true` when `parkedCount > 0`, else `previouslyExpanded`. Wire it as:
initial `pendingExpanded` state = `parkedRows.isNotEmpty()`, plus a
`LaunchedEffect` on the empty→non-empty edge of `parkedRows` that expands.
Never auto-collapse — a user who manually collapses the section stays collapsed
until new parked rows arrive. `rememberSaveable` retention is preserved.

### Cross-cutting constraints

- **Do not touch:** Smart Queue ranking/order, drag-reorder, Up Next, playback, the
  QueueScreen pending-saves hub, or `PENDING_STATUSES`. See the fragile
  last-row/drag note in repo memory — the drag machinery keys off `readyItems` /
  `readyIndexById`, so parked rows must never enter `readyItems`,
  `readyIndexById`, `itemTopOffsets`, or `itemHeights`.
- **Play All / Play From Here:** `visiblePlaybackItems` (`LibraryItemsScreen.kt:378`)
  must remain server-items-only. A parked save has no `itemId` and must never reach
  a playback snapshot.
- **Selection mode:** parked rows are not selectable and never enter `selectedIds`,
  so batch actions cannot target them.
- **Account isolation:** `pendingManualSaves` is already account-cleared on
  sign-out; no new persistence, no new network calls.
- **Privacy:** row shows title/URL the user already shared — no tokens, no body text
  beyond the existing preview conventions.

## Tests (JVM unit, `app/src/test/java/com/mimeo/android/ui/library/InboxParkedSaveProjectionTest.kt`)

1. Parked row whose `resolvedItemId` matches a server item → hidden.
2. Parked row whose `resolvedItemId` matches nothing → shown.
3. URL dedupe: parked `https://Example.com/a/` vs server `https://example.com/a`
   → hidden (case + trailing slash + scheme-prefixed text handled).
4. Parked row with a URL absent from server items → shown.
5. `TEXT`-type parked save (blank `urlInput`) vs a server item with blank/null URL
   → shown (blank never matches blank).
6. `resolvedItemId` takes precedence: matching ID hides the row even when URLs differ.
7. Ordering: multiple parked rows return newest-`createdAtMs`-first.
8. Empty parked list → empty result; empty server list → all parked rows shown.
9. `shouldAutoExpandPending`: `(0, false) == false`, `(0, true) == true`,
   `(2, false) == true`, `(2, true) == true`.

Existing suites must stay green — in particular
`app/src/test/java/com/mimeo/android/ui/queue/PendingProjectionTransitionTest.kt`,
which covers the QueueScreen projection that shares the extracted URL normalizer.

Compose UI/Robolectric tests are **not** required; the risk here is projection logic
and it is fully covered by pure functions.

## Physical-device acceptance (OnePlus 7T)

1. Airplane mode → share an article → open Inbox: Pending section is **expanded**
   and shows the parked row marked "Not uploaded yet".
2. Restore network → pull-to-refresh Inbox (or let background retry fire) → the
   parked row disappears exactly once the real item appears; **no moment where both
   are visible**.
3. Collapse the Pending section manually with a parked row present → it stays
   collapsed across navigation away and back; a *new* parked save re-expands it.
4. Smart Queue regression: cold-launch → drag the last row, then the first row →
   drag still works, ordering persists (this is the fragile path).
5. Play All from Inbox with a parked row present → playback queue contains only
   real items.

## Non-goals / stop conditions

- No backend/Mimeo changes, no contract changes.
- No changes to notification behavior, WorkManager retry, or snapshot expiry
  (all shipped in `T-AND-PENDING-SAVE-SURFACING-1`).
- No consolidation of the QueueScreen "Pending saves" hub into the Inbox — the hub
  stays as-is; this ticket only adds Inbox visibility.
- Stop and report if surfacing parked rows requires a shared row model between
  `PendingManualSaveItem` and `PlaybackQueueItem` (i.e. if a synthetic
  `PlaybackQueueItem` with a fake `itemId` seems necessary) — that is a larger
  refactor and would put fake IDs into drag/playback paths.

## Delivery

- Branch from current `main` tip; additive commits only, no rebase/force-push.
- Draft PR to `main` with: what changed, `.\gradlew.bat :app:testDebugUnitTest` and
  `.\gradlew.bat :app:assembleDebug` results, and the manual steps above.

## Model/effort recommendation

- Primary: Claude (Sonnet-class), medium effort — localized Compose + pure-function
  work in a file whose fragile regions are already documented.

# T-AND-PENDING-SAVE-SURFACING-1 — Surface parked saves, bounded retry, snapshot expiry

Base SHA: `8788ed9` (main). Branch: `claude/t-and-pending-save-surfacing-1`.
Evidence: `docs/investigations/ANDROID_SAVE_DISAPPEARANCE_INVESTIGATION_2026_07.md`
(§1 Mechanism B, §4 latent defect, §9 regression requirements, §12 follow-up note).
Android-only. Draft PR to `main`; do not merge.

## Problem (from confirmed findings)

1. Retryable share-sheet failures are parked in Pending Saves with the failure
   notification suppressed (`ShareReceiverActivity`, `surfacedResult = null`); retry
   runs only on app-open with connectivity — saves sit invisible for days.
2. `retryPendingManualSave` / `retryAllPendingManualSaves` delete the pending row when a
   retry returns `Saved(itemId = null)` (409 duplicate resolution against the ready-only
   queue) — silent loss (`AppViewModel.kt:1871`, `:1906`).
3. Persisted queue snapshots (`SettingsStore.saveQueueSnapshot`/`loadQueueSnapshot`)
   have no age/invalidations, so trashed/deleted rows render as live indefinitely.

## Scope

### A. User-visible parked-save handling
- When a share result is retryable and parked, post a notification: "Couldn't reach
  Mimeo — save pending. Will retry automatically." (reuse the existing share channel;
  tap opens Pending Saves). Replace, don't stack, per pending row.
- On later successful flush, update/cancel the notification ("Saved to <destination>").

### B. Bounded background retry
- Schedule a WorkManager job when a save parks: `NetworkType.CONNECTED` constraint,
  exponential backoff, max ~5 attempts over ~24 h, then stop (row remains in Pending
  Saves with its failure message — no infinite retries).
- Reuse `retryPendingManualSave` semantics; serialize with the existing
  `pendingManualRetryMutex`; cancel the job if the row resolves via app-open flush.

### C. Preserve Pending state on `Saved(itemId = null)`
- Never remove a pending row for a `Saved` result without a resolved item ID: mark it
  retry-failed (message "Saved earlier — item not yet visible") instead of deleting.
  Applies to both retry paths and any share-receiver equivalent.

### D. Queue-snapshot age/expiry
- Stamp snapshots with `savedAtMs`; on load, treat snapshots older than 24 h as stale:
  render with an explicit offline/stale indicator or skip rendering (pick simplest that
  passes acceptance).
- On any successful refresh, replace the snapshot wholesale (already true) and drop
  in-memory rows whose IDs are absent from the fresh response (no resurrection).

### Cross-cutting constraints
- **Account/endpoint isolation:** retry work and notifications must capture the
  account context at park time (`accountScopedRequestContext`) and no-op if the
  account/base URL changed; snapshots are already account-cleared on sign-out — keep it.
- **Privacy:** notifications show title/destination only — never URLs of sensitive
  pages, tokens, or body text.
- **Battery/notifications:** one WorkManager unique work per pending row (or one
  aggregate job), backoff not polling; respect POST_NOTIFICATIONS permission and
  existing `canPostNotifications` gating; no foreground service.

## Tests

Unit (JVM):
- Retryable share failure ⇒ pending row persists AND notification-policy hook invoked.
- `Saved(itemId = null)` ⇒ pending row NOT removed (both retry paths).
- Snapshot older than threshold ⇒ flagged stale / not rendered as live.
- Refresh response lacking a cached ID ⇒ row dropped from rendered state.
- Retry work no-ops when account context changed.

Integration (Robolectric or instrumented, if harness allows): WorkManager test driver —
park ⇒ job scheduled with network constraint; constraint met ⇒ retry executes once.

## Physical-device acceptance (OnePlus 7T)

1. Airplane mode → share an article → visible "save pending" notification; Pending
   Saves shows the row.
2. Re-enable network, do NOT open the app → item appears server-side within backoff
   window; notification updates to saved.
3. Open app after step 2 → item in Inbox/Smart Queue; pending row gone.
4. Stale-snapshot check: with network off, reopen app after >24 h-old snapshot (or
   lowered debug threshold) → stale indicator shown, binned items not presented as live.

## Non-goals / stop conditions

- No backend/Mimeo changes, no contract changes, no runtime access.
- No redesign of Pending Saves UI, no Up Next/queue/playback behavior changes, no
  Smart Queue reorder changes (see fragile last-row/drag note in repo memory).
- Stop and report if the fix appears to require backend semantics (e.g. a
  not-ready-inclusive queue query) — propose a CONTRACT CHANGE ticket instead.
- Stop if WorkManager scheduling conflicts with existing AutoDownloadWorker patterns
  rather than reusing them.

## Delivery

- Branch `claude/t-and-pending-save-surfacing-1` from `8788ed9` (rebase base to current
  `main` tip at start if it has advanced; additive commits only).
- Draft PR to `main` with: what changed, tests run (`.\gradlew.bat :app:testDebugUnitTest`,
  `:app:assembleDebug`), manual steps above. Do not merge; operator merges.

## Model/effort recommendation

- Primary: Claude (Sonnet-class), medium effort — bounded, well-specified Android work
  reusing existing patterns (WorkManager, notifications, DataStore).
- Alternative: Codex (GPT-5.4, high) if the WorkManager/mutex interaction or
  Robolectric harness proves fiddly; single-writer rule applies either way.

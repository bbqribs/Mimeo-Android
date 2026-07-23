# Android Save Disappearance Investigation — July 2026

Ticket: `T-AND-SAVE-DISAPPEARANCE-INVESTIGATION-1`
Android base: `main` @ `a4cae97fdda2e0696e5c76a0e7b4677cb45f21dd`
Investigation date: 2026-07-23
Method: static trace of the Android save lifecycle + read-only inspection of the Mimeo
backend code and the production runtime (`https://beh-august2015.taildacac5.ts.net/`,
Bearer GETs only — no writes, no deploys, no restarts).

---

## 1. Executive summary

Two distinct, **confirmed** mechanisms produce the reported "save appears to succeed,
then disappears everywhere" symptom. Neither is an extraction failure, and neither is
specific to twz.com or theringer.com.

**Mechanism A — batch trash sweeps deleted successfully saved articles (primary loss).**
On 2026-07-22 two single-request batch bin operations trashed **16 real, `ready`,
successfully extracted articles** minutes-to-hours after they were saved — including all
five reported TWZ articles. The items were removed from every surface (Android Inbox,
web Inbox, Smart Queue) simultaneously. They are still recoverable from the Bin until
~2026-08-05 (14-day retention).

**Mechanism B — silent pending-save parking on Android (perceived loss).**
When a share-sheet save fails with a retryable error (server unreachable, timeout), the
Android app intentionally suppresses the failure notification, parks the save in Pending
Saves, and retries only when the app is next opened with connectivity. Saves can sit
invisible for a day or more: the user saw "Received. Saving..." at share time, nothing
exists server-side, and no failure is ever surfaced. Evidence: a five-item creation burst
at `2026-07-23T11:01:11Z` (including the reported theringer.com article and a TWZ
article) — all created within one second, i.e. a pending-save flush, not manual shares.

Android capture, URL normalization, idempotency, backend extraction, and duplicate
matching were all **exonerated for these incidents** (see §5).

---

## 2. Recovered evidence (read-only production queries)

### 2.1 Batch trash sweep #1 — 2026-07-22T12:46:38Z

All eight items created in the user's 12:30–12:39 save session; all `status=ready`;
trashed in one server-side loop (~25–35 ms apart, newest-first):

| id | created (UTC) | trashed (UTC) | url |
|----|---------------|---------------|-----|
| 2584 | 12:30:24 | 12:46:38.404 | twz.com/air/fury-loyal-wingman-arrives-at-creech-afb-… |
| 2585 | 12:30:35 | 12:46:38.431 | twz.com/air/typhoons-paired-with-cca-fighter-drones-… |
| 2586 | 12:30:52 | 12:46:38.463 | twz.com/news-features/munitions-expenditures-in-iran-war-… |
| 2587 | 12:30:59 | 12:46:38.488 | twz.com/space/could-this-new-northrop-spacecraft-… |
| 2588 | 12:31:09 | 12:46:38.512 | twz.com/air/mv250-hybrid-electric-vtol-combat-cargo-drone-unveiled |
| 2589 | 12:32:12 | 12:46:38.375 | theguardian.com/world/…/british-woman-jailed-hong-kong-… |
| 2590 | 12:32:30 | 12:46:38.350 | theguardian.com/world/…/its-truly-hell-here-airstrikes-… |
| 2591 | 12:39:36 | 12:46:38.317 | propublica.org/article/alice-sebold-anthony-broadwater-… |

### 2.2 Batch trash sweep #2 — 2026-07-22T15:27:09Z

All eight surviving real items created 13:12–13:45; same signature (single request,
newest-first, ~30 ms strides):

| id | created (UTC) | url |
|----|---------------|-----|
| 2592 | 13:12:51 | bbc.co.uk/sport/football/articles/cy8enwn2xq4o |
| 2593 | 13:12:58 | bbc.co.uk/sport/football/articles/cpd7nqnq2w6o |
| 2594 | 13:13:30 | bbc.co.uk/news/articles/c3eygk24318o |
| 2595 | 13:14:12 | bbc.co.uk/news/articles/cgjxqg08q82o |
| 2607 | 13:27:48 | theguardian.com/books/2026/jul/21/kim-phillips-fein-country-of-lords |
| 2611 | 13:34:59 | navalnews.com/…/hd-hyundai-and-sima-unveil-hds-mgp-submarine-… |
| 2613 | 13:37:01 | breakingdefense.com/2026/07/bae-unveils-brontanax-a-uk-designed-cca-drone/ |
| 2614 | 13:45:06 | fortune.com/2026/07/10/us-treasury-borrowed-155-billion-every-month-… |

Interleaved with sweep #2's window, item IDs **2596–2606, 2608–2610, 2612 are
permanently gone** (404 from `/items/{id}` and absent from the Bin) — consistent with
test items created and then binned+purged by automated activity in the same window.
Codex agent test users were being created on the runtime the same afternoon
(e.g. `test-incite`, created 2026-07-22T14:07Z), and prior example.com test items show
the same bin-then-purge cleanup style on 07/17 and 07/19.

### 2.3 Pending-save flush burst — 2026-07-23T11:01:11Z

Five items created within ~1 second, all owned by `user_id=1`, all extracted
successfully on the first worker attempt and now `ready` + queue-eligible:

| id | created (UTC) | url |
|----|---------------|-----|
| 2618 | 11:01:11.308 | theringer.com/2026/07/21/books/colson-whitehead-cool-machine-interview-harlem-trilogy |
| 2619 | 11:01:11 | bit.ly/4wSpH8I |
| 2620 | 11:01:11 | open.substack.com/pub/revolvingdoorproject/p/talking-about-a-world-on-fire… |
| 2621 | 11:01:11 | open.substack.com/pub/ser1897/p/fred-guttenberg-calling-rashida-tlaib… |
| 2622 | 11:01:12.145 | twz.com/air/rq-170-sentinel-stealth-drone-supported-maduro-capture-mission |

One-second spacing is machine-speed, not human share-sheet speed: this is
`AppViewModel.retryAllPendingManualSaves` / auto-retry flushing parked Pending Saves at
app open. The same burst signature appears historically (5 TWZ items within 2 s on
06/18; 3 within 20 s on 06/30) — parking-then-flush has been happening for weeks.

### 2.4 Extraction health for the reported domains

- Every twz.com and theringer.com item on the server is `status=ready`. Zero failed or
  blocked rows exist for either domain (checked all `failed` and `blocked` items).
- `/debug/queue-stats`: ready=1879, extracting=5 (all stale January rows), blocked=178,
  failed=77 — none of the blocked/failed rows are TWZ or Ringer.
- Attempt logs for 2618/2622: single attempt, `succeeded`
  (`readability_http` and `playwright_auth` respectively).

### 2.5 Attribution evidence available/unavailable

- The backend has **no item-level audit events** (`AuditEventType` covers logins,
  device tokens, users, restarts — not item create/trash/purge). The canonical audit
  log on the runtime was rotated today and holds only 2026-07-23 login events.
- No server-side automation writes `trashed_at` — the only writers are
  `DELETE /items/{id}`, `POST /items/batch action=bin`, and the web form POSTs, i.e.
  the sweeps came from an authenticated client request.
- The ~30 ms stride and newest-first order inside each sweep indicate **one
  `POST /items/batch` request whose `item_ids` came from a newest-first listing**
  restricted to "items created in the recent window" — a test-cleanup shape, not a
  plausible manual selection. The Android batch UI sends explicitly selected IDs
  (`selectedIds` keyed by itemId — verified no index-shift bug), and the operator would
  not have binned 16 fresh saves twice.

---

## 3. End-to-end lifecycle trace (Android share → visible item)

1. **Share intent** → `ShareReceiverActivity.consumeShareIntent` → `extractFirstHttpUrl`
   (regex, keeps fragments/query; trims trailing punctuation).
2. **Pending enqueue (pre-flight)** → `SettingsStore.enqueuePendingManualSave`
   (message "Saving...", `autoRetryEligible=false`, `resolvedItemId=null`). Durable
   before any network call — good.
3. **Create** → `ShareSaveCoordinator.saveSharedText`: 8 s online deadline over a 45 s
   OkHttp call; `POST /items` with `Idempotency-Key = android-share-<sha256(normalized url)>`.
   Backend: URL-exact upsert first (re-save of an existing non-archived URL returns the
   existing row and re-queues extraction), then idempotency-key handling.
4. **Success** → pending row marked resolved (`resolvedItemId`, "Processing..."), refresh
   events emitted, status watcher polls `/items/{id}` every 3 s for up to 30 s.
5. **Failure (retryable: network/timeout/5xx/SaveFailed)** → pending row updated with
   failure text, `autoRetryEligible=true` — **and the failure notification is suppressed**
   (`surfacedResult = null` in `ShareReceiverActivity`); the user never learns the save
   did not land.
6. **Retry** → only inside `loadQueueOnce`/connectivity-restored callbacks — i.e. only
   when the app is foregrounded with the backend reachable. No WorkManager job exists
   for pending saves.
7. **Visibility** → Android Smart Queue uses `/playback/queue` which is **ready-only**
   (`include_not_ready` never passed); Android Inbox (`/items?view=inbox`) shows
   non-ready items only inside a **collapsed-by-default "Pending" section**
   (`LibraryItemsScreen.pendingExpanded = false`).
8. **Reconcile** → `reconcilePendingSavesWithQueue` removes the pending row once the
   summary is neither processing nor terminal-failed; terminal failures persist in the
   row with a classified message (`PendingProcessingFailureClassifier`).

## 4. Do Android Pending semantics conceal post-creation failures?

Mostly **no** for genuine extraction failures — a terminal status/failure_reason keeps
the pending row alive with a classified message ("Source blocked access", etc.), and
`syncPendingSaveProcessingFailures` snackbars it. The real gaps are:

- **Pre-creation failures are concealed by design** (§3 step 5) — this is Mechanism B.
- **Latent defect:** on HTTP 409 the duplicate resolver looks for the existing item in
  the **ready-only** queue; if the existing item is not ready (extracting/failed) it
  returns `Saved(itemId = null)`, and `retryPendingManualSave`/`retryAllPendingManualSaves`
  then hit the `!isRetryablePendingSaveResult` branch and **delete the pending row with a
  success result and no created item** (`AppViewModel.kt:1871-1872`, `:1906-1907`).
  Reaching it requires an idempotency-key collision with a payload mismatch (e.g. the
  same URL re-shared with a different `#:~:text=` highlight fragment within the 7-day
  idempotency TTL). Not implicated in this incident's evidence, but it is a true
  silent-loss path and should be fixed.
- **Cosmetic:** the reconcile confirm path only checks the summary, so a pending row can
  be removed while the item is still invisible in the current queue projection (e.g. an
  archived row returned by the idempotency fallback).

## 5. Hypotheses considered and disconfirmed

| Hypothesis | Verdict | Disconfirming evidence |
|---|---|---|
| Backend extraction/worker failure for TWZ/Ringer | **Rejected** | All twz/theringer rows `ready`; zero failed/blocked rows for either domain; single-attempt successes. |
| Android URL extraction/normalization mangling | **Rejected** | Stored URLs match the canonical article URLs exactly. |
| Idempotency/duplicate mis-resolution ate the saves | **Rejected for these items** | Items were created (new IDs) — no 409 path involved. Latent `Saved(itemId=null)` defect noted in §4. |
| Account scoping (user_id NULL rows invisible) | **Rejected** | Burst items all `user_id=1`, `eligible=true` via `/playback/queue/explain`. |
| Inbox/Smart Queue filter hiding items | **Partial, contributing** | `/playback/queue` is ready-only and the Inbox pending section is collapsed by default, so extracting items are effectively invisible — this amplifies the *perception* of disappearance but the swept items were `ready`. |
| Client batch-binned by the operator manually | **Implausible** | Two sweeps, 16 fresh saves, single-request newest-first machine-stride ordering; operator filed this ticket. |
| Automated sweep binned "items created since run start" with the unscoped legacy token | **Best-supported for Mechanism A** | Sweep shape (§2.5); interleaved purged test-ID runs; agent test users created the same afternoon; no server automation exists. Final attribution needs runtime access logs (§8). |

## 6. Does the same cause affect desktop/web saves?

- **Mechanism A: yes.** The sweep trashes items server-side; they vanish from every
  client regardless of which client saved them. Any save (extension, web, Android)
  landing inside a sweep window is lost from all surfaces.
- **Mechanism B: Android-only.** The desktop extension/web save path has no pending-save
  parking layer.

## 7. Recommended fix boundary

**Backend/ops (Mimeo repo — changes required):**
1. **Item lifecycle audit events** — emit `item.created` / `item.trashed` / `item.purged`
   / `item.batch_action` canonical audit events with actor, auth method (legacy vs
   device token), source IP, and item IDs. This incident is unattributable today.
2. **Cleanup guardrails** — any test/agent cleanup must bin/purge only IDs recorded in
   its own creation ledger; never "items created since T" windows; ideally run test
   traffic under dedicated test users so the operator-token sweep cannot see real rows.
   Consider requiring batch bin/purge with the legacy token to reject items owned by
   real users unless explicitly flagged.
3. **Recovery (operator action, needs approval):** restore the 16 binned items from the
   Bin before retention purge (~2026-08-05).

**Android (this repo — changes required):**
4. **Surface parked saves** — when a share-sheet save parks as retryable, post a real
   notification ("Couldn't reach Mimeo — save pending, will retry") instead of silence,
   and/or schedule a WorkManager retry so flushing does not require opening the app.
5. **Fix the `Saved(itemId=null)` retry-removal defect** (§4) — never remove a pending
   row on a `Saved` result without a resolved item ID.

## 8. Follow-up tickets

- **Mimeo:** `T-BACKEND-ITEM-LIFECYCLE-AUDIT-1` — item lifecycle audit events + operator
  cleanup guardrails (items 1–2 above); include one-time restore of the 16 swept items
  and a review of which harness/agent performed the 07/22 sweeps.
- **Mimeo-Android:** `T-AND-PENDING-SAVE-SURFACING-1` — parked-save notification +
  background retry + `Saved(itemId=null)` pending-removal fix (items 4–5 above), with
  unit tests for `retryPendingManualSave` result handling and the notification policy.

## 9. Regression & manual acceptance requirements

For the Android follow-up:
- Unit: retryable share failure ⇒ pending row persists **and** a user-visible
  notification is posted; `Saved(itemId=null)` ⇒ pending row is *not* removed.
- Unit: reconcile does not remove a pending row whose resolved item is archived/absent
  from the active projection.
- Manual: airplane-mode share ⇒ visible "save pending" notification; reconnect ⇒ item
  appears in Inbox/Smart Queue without opening Pending Saves manually.

For the Mimeo follow-up:
- Every bin/purge/batch action produces an audit event with actor attribution
  (verify via `/debug/audit/events` equivalent).
- Cleanup harness dry-run proves it only targets its own ledgered IDs.

## 10. Evidence still needed

1. **Runtime request logs for the two sweeps** — on the remote laptop (read-only):
   `docker logs` (or uvicorn access log) filtered to `2026-07-22T12:46:3*Z` and
   `2026-07-22T15:27:0*Z`, capturing method/path (single `POST /items/batch` vs many
   `DELETE /items/{id}`), source IP (172.18.0.1 = local host process vs Tailscale peer),
   and auth type. This attributes the sweep definitively.
2. **Operator confirmation** that no manual batch bin was performed on 07/22 at those
   times (web Inbox batch panel or Android multi-select).
3. Agent session transcripts/ledgers from 07/22 ~12:40–15:30 UTC (audit-relay and
   Android verification sessions) to identify the cleanup that issued the sweeps.
4. No additional failed TWZ/Ringer URLs are needed — the reported items were located.

## 11. Inspected paths

Android: `ShareReceiverActivity.kt`, `share/ShareSaveCoordinator.kt`,
`share/ShareSaveUtils.kt`, `share/PendingSavePolicy.kt`, `data/SettingsStore.kt`,
`data/ApiClient.kt`, `AppViewModel.kt` (pending saves, reconcile, batch, retry, bin),
`PendingProcessingFailureClassifier.kt`, `ui/library/LibraryItemsScreen.kt`,
`ui/queue/QueueScreen.kt`, `MainActivityShell.kt`.
Mimeo (read-only): `backend/app/api/items.py`, `playback.py`, `inbox.py`, `audit.py`,
`health.py`, `dev.py`, `models/models.py`, `services/smart_queue.py`, `worker.py`,
`core/audit.py`, `scripts/dedup-cleanup.ps1`, `scripts/android_acceptance_harness.py`,
`docs/REMOTE_RUNTIME_VERIFICATION_PROTOCOL.md`.

## 12. Runtime access statement

All runtime interaction was read-only `GET` requests authorized per
`REMOTE_RUNTIME_VERIFICATION_PROTOCOL.md`. No writes, no deploys, no restarts, no test
articles created, no Mimeo repo changes.

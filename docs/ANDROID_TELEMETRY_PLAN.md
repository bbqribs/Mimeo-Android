# Mimeo Android Telemetry Plan — v1

**Version:** 0.1 (planning draft, no code authorized)
**Date:** 2026-05-07
**Status:** Docs only. No Android implementation.
**Authority:** This plan is subordinate to the cross-component policy in
the Mimeo backend repo at `docs/planning/TELEMETRY_PRIVACY_POLICY.md`.
Where the two disagree, the Mimeo policy wins until amended.

---

## 0. Goal

Adopt the Mimeo telemetry privacy policy on Android, with concrete bindings
to Android platform surfaces (Activity/Fragment lifecycle, MediaController/
playback service, Crashlytics-shaped crash capture, Settings screen).

The single rule that drives every decision below:

> **The Android client must not transmit, log, or persist anything that
> reveals what the user is reading or listening to.**

---

## 1. What this plan does and does not authorize

This plan is **docs only**. It does not authorize:

- Adding any new dependency (Crashlytics, Firebase Analytics, Sentry,
  PostHog, Amplitude, Mixpanel, etc.).
- Adding any new network request from the Android client.
- Adding any new persistent table, DataStore key, or shared-preferences key
  for telemetry storage.
- Wiring lifecycle callbacks, playback callbacks, or crash handlers to any
  telemetry sink.

Implementation is staged in §8 and gated on operator approval per ticket.

---

## 2. Forbidden-by-default data on Android

Inherits the Mimeo policy §2 forbidden list verbatim. In addition, the
following Android-platform-specific fields are forbidden by default in any
event, log, or crash report emitted from the app:

| Forbidden field | Why |
|---|---|
| Activity / Fragment / Composable route name *that contains an item or playlist identifier* | Re-identifies what the user is reading |
| `Intent` extras (full bundle dump) | May contain deep-link URL, item_id, playback position |
| `MediaItem.mediaId` / `MediaItem.requestMetadata.mediaUri` | Identifies the item being played |
| `MediaController` queue contents (`getMediaItemAt`, `getCurrentMediaItem`) | Reading/listening log |
| `PlaybackState.position`, `duration`, `bufferedPosition` | Per-item progress |
| Notification text (now-playing title/artist/description) | Title and source identity |
| `androidx.navigation` route arguments | Often contain item_id |
| WorkManager input/output `Data` payloads | Often contain URLs and item_ids |
| OkHttp request URL or body in interceptors / logs | URL leak |
| Logcat lines containing the above | Captured by `bugreport`/Play Console |
| Shared-element transition payloads | May reference item images/URIs |
| Restored process state (`onSaveInstanceState`) dumps | Often the entire reading view state |

The Android client must not feed any of these into:
- `Log.d` / `Log.i` / `Log.w` / `Log.e` (yes, including `BuildConfig.DEBUG`
  builds — release-only redaction is too easy to forget).
- `Crashlytics.log()` / `setCustomKey()` / `recordException()` (none of which
  are authorized in v1; listed defensively).
- Any analytics SDK (none authorized in v1; listed defensively).
- Any backend HTTP request beyond the user-explicit problem-report endpoint
  (§6 of the Mimeo policy).

---

## 3. Allowed-by-default data on Android

Inherits Mimeo policy §3 safelist. Android-specific field bindings:

| Category | Android binding |
|---|---|
| Coarse timings | `SystemClock.elapsedRealtime()` deltas, bucketed before storage |
| Error classes | Symbolic enums in a single `TelemetryErrorClass` Kotlin enum, never `Throwable.message` |
| Surface category | A *fixed enum of screen categories*, never the route string. E.g. `Surface.READER`, not `"reader/{itemId}"` |
| Queue action type | `QueueAction.MARK_READ` etc., never the affected `item_id` |
| HTTP status bucket | OkHttp interceptor that records the bucket only and drops the URL |
| App / build version | `BuildConfig.VERSION_NAME` + git SHA |
| Aggregate counters | In-memory counters flushed to a local diagnostics screen only |

**Surface enum (v1):** `INBOX`, `READER`, `ITEM_DETAIL`, `LOCUS`,
`UP_NEXT`, `PLAYLISTS`, `BLUESKY_BROWSE`, `SETTINGS`, `AUTH`,
`PROBLEM_REPORT`, `OTHER`. Adding a value requires updating this doc.

---

## 4. Event taxonomy (Android view)

The Android client emits the following families from the Mimeo policy §5,
all into the **local-only ring buffer** (§5.3 below) by default. None of
these events are uploaded anywhere unless the user submits a problem
report or explicitly opts into the future backend-tier uploader.

### 4.1 `client.startup`
Bound to `Application.onCreate` → first frame of first Activity.
| Field | Source |
|---|---|
| `client_type` | `android` (constant) |
| `start_kind` | `cold` if `Application.onCreate` ran in this process; `warm` if Activity recreated; `resumed` if the process was already alive |
| `duration_bucket` | `<200ms`/`200-500ms`/`500ms-1s`/`1-3s`/`3-10s`/`>10s` |
| `result_class` | `ok`, `error_init`, `error_auth` (no token / token expired), `error_offline` (initial sync failed) |
| `client_version` | `BuildConfig.VERSION_NAME` |

Forbidden: deep-link URI, restored navigation state, last-opened item_id.

### 4.2 `client.screen_load`
Emitted on `LifecycleOwner.STARTED` for each top-level screen.
| Field | Source |
|---|---|
| `surface` | `Surface` enum (§3) |
| `duration_bucket` | Time from navigation request to first content frame, bucketed |
| `result_class` | `ok`, `error`, `cancelled` (user navigated away mid-load) |
| `client_version` | `BuildConfig.VERSION_NAME` |

Forbidden: route arguments, item_id, playlist_id, scroll position.

### 4.3 `playback.state`
Bound to the Mimeo playback service's state machine. Captures the
*transition*, not the item.
| Field | Source |
|---|---|
| `transition` | One of: `idle→loading`, `loading→playing`, `playing→paused`, `paused→playing`, `playing→ended`, `playing→error`, `loading→error`, `any→stopped` |
| `error_class` | Symbolic enum, only when transition ends in `error` (e.g. `network_failed`, `decode_failed`, `tts_unavailable`, `audio_focus_lost`) |
| `client_version` | `BuildConfig.VERSION_NAME` |

Forbidden: `mediaId`, position, duration, queue contents, voice id, title,
URL, source identity. The transition labels intentionally do **not**
include the item being played.

Note: per-user playback *progress* (e.g. `last_read_percent` posted to the
backend) is **not** telemetry. It is feature data that flows through the
existing `/items/{id}/progress` API and stays in the user's account; it
must never be mirrored into the telemetry ring buffer.

### 4.4 `client.error`
Caught errors at well-defined boundaries (network layer, repository layer,
top-level exception handler).
| Field | Source |
|---|---|
| `surface` | `Surface` enum (§3) |
| `error_class` | `TelemetryErrorClass` enum value |
| `client_version` | `BuildConfig.VERSION_NAME` |

Forbidden: `Throwable.message`, `Throwable.localizedMessage`, full stack
trace text, request URL, response body.

### 4.5 `queue.action` (mirror, local only)
The Android client may record symbolic queue actions in the local ring
buffer for diagnostics. The backend emits its own `queue.action` events
per the Mimeo policy; the Android version is **not** uploaded.
| Field | Source |
|---|---|
| `action` | `QueueAction.MARK_READ`, `ARCHIVE`, `DELETE`, `RESTORE`, `ENQUEUE`, `REORDER`, `CLEAR_UP_NEXT` |
| `result_class` | `ok`, `error_validation`, `error_internal` |

Forbidden: item_id, playlist_id, position index.

---

## 5. Storage tier (Android)

### 5.1 No third-party telemetry SDKs in v1
v1 does not adopt Firebase, Crashlytics, Sentry, Amplitude, or any other
third-party telemetry SDK. The Mimeo policy treats these as out-of-scope
and they would fail the §2 forbidden-list audit by default.

### 5.2 No background telemetry uploads in v1
v1 does not upload telemetry to the Mimeo backend or anywhere else. The
only data that leaves the device is:
1. Existing feature traffic (capture, progress, playback).
2. User-submitted problem reports (§6 of the Mimeo policy + the existing
   Android problem-report attachment v2 contract).

### 5.3 Local ring buffer
Telemetry events live in an in-memory ring buffer of the most recent ~500
events, with no on-disk persistence by default. The buffer is:

- Inspectable from **Settings → Privacy & diagnostics → View local
  diagnostics**.
- Clearable from the same screen.
- Optionally attachable to a problem report — **off by default** — when
  the user submits one. If the user toggles "include diagnostics", the
  contents of the ring buffer are attached *as the safelisted symbolic
  events they already are*. No additional content is added at attachment
  time.
- Cleared on app uninstall (since it is in-memory).
- Cleared on sign-out.

### 5.4 Crash capture
Android crashes are *not* automatically captured to Crashlytics or any
remote endpoint in v1. The default Android crash dialog continues to
work; if the user chooses to file a problem report after a crash, they
may attach the in-memory ring buffer (§5.3). No automatic crash upload.

If a future ticket adds local on-disk crash persistence, it must:
- Strip stack-trace local variable values at capture time.
- Use symbolic `error_class` only for the surface label.
- Honor the same retention as `client.error` events (30 days).

---

## 6. Problem-report flow on Android

This plan does not change the existing problem-report contract
(`PROBLEM_REPORT_ATTACHMENT_V2_CONTRACT_SPEC.md`,
`ANDROID_LOCUS_PROBLEM_REPORT_FLOW_SPEC.md`). It adds these requirements:

1. The submission UI must show every field that will be sent before the
   user submits, with per-field remove controls.
2. **Include item content** must be off by default.
3. **Include local diagnostics** (ring buffer attach) must be off by
   default. Ring-buffer contents must be visible to the user before they
   submit.
4. The user-authored note (≤500 chars) is the only free-text field
   allowed to leave the device through this channel.

---

## 7. User-facing Settings: Privacy & diagnostics screen

A new screen under Settings, present in v1:

- **Header copy (plain language):** "Mimeo does not collect what you read
  or listen to. The app only records small symbolic signals (timings,
  error categories, screen category) on this device, and only sends them
  if you submit a problem report and choose to include them."
- **What we record (on this device):** bullet list summarizing §3.
- **What we never record:** bullet list summarizing §2.
- **View local diagnostics:** opens the ring-buffer contents.
- **Clear local diagnostics:** empties the ring buffer.
- **Retention:** read-only display of ring-buffer cap (~500 events,
  in-memory, cleared on sign-out).
- **Backend uploads:** "Off. Mimeo does not upload diagnostics from your
  device. This will not change without an explicit opt-in toggle on this
  screen."
- **Policy version:** displays the Mimeo telemetry policy version the
  build was compiled against; warns if the document in the repo has a
  newer version.
- **Link to policy:** opens
  `docs/planning/TELEMETRY_PRIVACY_POLICY.md` (rendered or external).

A first-run notice surfaces the same plain-language summary on first sign
in and links into this screen. Dismissible; never shown again unless the
policy version changes.

---

## 8. Implementation tickets (proposed, not authorized here)

In dependency order; none authorized by this plan.

1. **T-TEL-A1: Telemetry plumbing skeleton.**
   Adds a single Kotlin `Telemetry.emit(family, fields)` entry point with
   a sealed-class field schema matching §4. No emitters wired in yet.
   Adds the `TelemetryErrorClass` and `Surface` enums. Unit tests assert
   that forbidden fields fail to compile (using sealed types) or fail at
   runtime with a redactor counter.

2. **T-TEL-A2: In-memory ring buffer + Privacy & diagnostics screen.**
   Implements §5.3 ring buffer and §7 Settings screen, including
   "View local diagnostics" and "Clear local diagnostics". No emitters
   wired in beyond a smoke event. Adds the first-run notice.

3. **T-TEL-A3: Wire `client.startup` and `client.screen_load`.**
   Plumbs `Application.onCreate` and per-screen lifecycle into
   `Telemetry.emit`. Asserts (via static lint or unit test) that no
   route argument string flows into the emitter.

4. **T-TEL-A4: Wire `playback.state`.**
   Plumbs the Mimeo playback service state machine into
   `Telemetry.emit`. Strict review on the diff: no `mediaId`, position,
   or queue contents may appear.

5. **T-TEL-A5: Wire `client.error`.**
   Adds boundary error capture at network/repo/top-level. Replaces any
   current `Log.e(tag, throwable.message, throwable)` calls that would
   leak URLs.

6. **T-TEL-A6: Mirror `queue.action` to local ring buffer.**
   Local diagnostics only. Confirms no upload path exists.

7. **T-TEL-A7: Problem-report integration.**
   Adds the "Include local diagnostics" opt-in to the existing
   problem-report submit UI per §6. Off by default.

8. **T-TEL-A8: Logcat + OkHttp redaction audit.**
   One-time audit of every `Log.*` call site and every OkHttp
   interceptor. Replaces URL/title-quoting log lines with symbolic
   `error_class`. CI lint to prevent regressions.

9. **T-TEL-A9: Policy-version pin + drift warning.**
   Compiles in the policy version string; surfaces a warning in Settings
   when the policy doc in the Mimeo repo has advanced past the compiled
   version.

10. **T-TEL-A10 (deferred, not v1): Optional backend uploader, opt-in.**
    A future ticket may add an opt-in uploader that batches safelist
    events into the backend's aggregate counters. Default off. Not
    authorized in v1.

---

## 9. Out of scope for v1

- Third-party analytics or crash SDKs. Forbidden in v1; listed
  defensively in §2.
- Background telemetry uploads. Forbidden in v1.
- Per-event persisted on-device tables. Ring buffer is in-memory only.
- Cross-device fingerprinting via stable device IDs. Not collected.
- A/B experiment exposure events. Not in scope; revisit if Mimeo ever
  has experiments.

---

## 10. Change control

Any change to:
- the forbidden Android-specific list (§2),
- the Surface enum (§3),
- the event taxonomy (§4),
- the storage tier (§5),
- the problem-report integration (§6), or
- the Settings screen (§7)

must update both this document and (where the change affects the
cross-component policy) `docs/planning/TELEMETRY_PRIVACY_POLICY.md` in
the Mimeo repo.

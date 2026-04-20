# Android Legacy Source Metadata — Normalization & Backfill Boundaries

**Version:** 1.0
**Status:** Final (Android-side spec only)
**Date:** 2026-04-20
**Scope:** Legacy-item rendering boundaries and backfill decision criteria; no code changes in this ticket

---

## Purpose

This document classifies legacy items (those lacking structured source metadata fields) by rendering outcome, establishes which outcomes are acceptable as permanent fallbacks, and defines the threshold criteria for when backend normalization or backfill becomes worth the coordination cost. It is the reference for any future cross-repo backfill coordination ticket.

The source of truth for field definitions and current precedence rules is `docs/ANDROID_SOURCE_METADATA_UNIFICATION_SPEC.md`.

---

## 1. Legacy Item Definition

A **legacy item** is one where all of the following are null or absent: `source_label`, `source_url`, `source_type`, `capture_kind`, `source_app_package`. These items predate structured metadata emission on the Android client.

A **partial-legacy item** has some fields populated but is missing others that would change classification or presentation (e.g., `capture_kind` is set but `source_url` is absent for an excerpt).

---

## 2. Legacy Case Categories

### Category A — Correct as-is (permanent acceptable fallback)

These cases produce correct, user-understandable rendering with no backend action required, ever.

| Case | Fields present | Rendered title | Rendered source | Assessment |
|---|---|---|---|---|
| **A1** Legacy URL article | `title`, `host` | Article title | Domain (host, www-stripped) | Identical to a modern article-like item. No degradation. |
| **A2** Legacy URL article, no host | `title` only | Article title | null (row falls back to URL display) | Minor: no source badge. URL still visible. Acceptable. |
| **A3** Legacy URL article, no title | `host`, `url` | Raw URL as title | Domain (host) | Acceptable degradation; URL is a valid placeholder. |
| **A4** Synthetic-URL excerpt, all null | `url` = `shared-text.mimeo.local/*` | `Excerpt: "…"` | "Android selection" | Excerpt identity is URL-derived; source fallback is correct per §7. |
| **A5** Excerpt with `capture_kind`, no `source_url` | `capture_kind` ∈ excerpt set | `Excerpt: "…"` | "Android selection" | Provenance was never captured; "Android selection" is the correct honest label. |

### Category B — Cosmetically degraded but not confusing (acceptable until scale warrants review)

These cases render without errors but produce a label that a user might find mildly imprecise if they remember where the item came from. No immediate action is needed.

| Case | Fields present | Degradation | Risk |
|---|---|---|---|
| **B1** Old app share, no `source_type="app"` | `url` with HTTP/HTTPS, `host` | Shows domain instead of "App share" | Low: domain is still meaningful context |
| **B2** Old app share, no `source_type`, no `host` | `url` with HTTP/HTTPS only | null source label | Low: URL is shown as fallback |
| **B3** Excerpt with `capture_kind` and `source_label` but no `source_url` | `source_label`, `capture_kind` | Uses `source_label` as origin (not provenance) | Low: origin label is still meaningful, just not a clickable link |

### Category C — Genuinely confusing (may justify backend normalization)

These cases produce misleading or ugly output that a user with no context cannot interpret correctly.

| Case | Fields present | Degraded output | Why it is confusing |
|---|---|---|---|
| **C1** Synthetic-URL excerpt, null title | `url` = `shared-text.mimeo.local/*`, no title | `Excerpt: "https://shared-text.mimeo.local/…"` | The synthetic URL is surfaced as if it were the content preview, which is meaningless to users |
| **C2** Pre-metadata excerpt, real URL | `url`, `host`, no `capture_kind` | Article-like rendering; shows domain as source, full URL as link | Item is functionally an excerpt but renders as a URL article — wrong category, wrong title format |

**Note on C2:** The volume of C2 items depends on when structured Android metadata emission began in the app history. Items captured before `capture_kind` was emitted are permanently unclassifiable as excerpt-like by Android alone. Backend backfill would require identifying these items by other heuristics (e.g., synthetic URL, stored plain-text flag, original capture event metadata).

---

## 3. Android UI Fallback Boundaries (until any backfill lands)

The following rules are exact and binding. No UI composable may introduce special-case branching for legacy items beyond these rules.

1. **`capturePresentation()` is the sole rendering path.** Library rows, Up Next rows, Locus header, and now-playing strip all derive title and source label through `capturePresentation()` (or its surface-specific wrappers). No ad-hoc null checks for "legacy" state in composables.

2. **Null `capture_kind` with a real URL → always article-like.** The `host` field (or null) is the source label. The item URL is the source link. This is correct and final.

3. **Null `capture_kind` with a synthetic shared-text URL → always excerpt-like.** URL-based classification per §2 of the unification spec is the override. "Android selection" is the source fallback. The synthetic URL is never surfaced as a link.

4. **Null title → raw URL is the title.** No special "Untitled" label. The URL serves as a minimal-sufficient identifier.

5. **Null host and null source metadata → null source label.** The row renders without a source badge. No placeholder text ("Unknown source", etc.) is shown for article-like items.

6. **These rules apply equally across all surfaces**: Up Next (via `PlaybackQueueItem`), Locus (via `ItemTextResponse`), now-playing strip (via `NowPlayingSessionItem`), and library views once the `ArticleSummary` CONTRACT CHANGE lands.

---

## 4. When Backend Normalization/Backfill Becomes Worth Doing

Backend normalization is not free — it requires a coordinated schema migration, potential data audit, and ongoing contract negotiation. The following criteria define the threshold for initiating that work.

### 4.1 Trigger for C1 backfill (synthetic-URL, null title)

Initiate when:
- User feedback or support volume indicates users are confused by items titled with a raw `shared-text.mimeo.local` URL, **AND**
- The item count of C1 items in active libraries (not archived/trashed) exceeds a de minimis threshold (suggested: > 5 items per average active account based on server-side query)

Proposed resolution: backend sets a synthetic title (e.g., the first N words of stored plain text) at write time, not as a migration, so the fix applies to future items without touching old rows.

### 4.2 Trigger for C2 backfill (pre-metadata excerpts miscategorized as articles)

Initiate when **all** of the following hold:
- The §8 `ArticleSummary` CONTRACT CHANGE has landed and library views display source metadata
- Server-side query shows a material volume of items with a legacy plain-text/excerpt storage marker but no `capture_kind` field
- The operator has a reliable heuristic for identifying them (e.g., a boolean `is_plain_text` column from the original capture path that predates `capture_kind`)

If no reliable heuristic exists server-side, **do not backfill**. Heuristic-based retroactive `capture_kind` assignment risks misclassifying URL articles as excerpts.

### 4.3 Cases that explicitly do not warrant backfill

- **B1/B2** (app share label precision): source type can be derived from `source_app_package` at display time once the field is populated on new items. No need to touch old rows.
- **A1–A5**: rendering is already correct. No action needed regardless of scale.
- **Category A** items in library views: the rendering gap is caused by the §8 `ArticleSummary` field absence, not by missing legacy data. The fix is the CONTRACT CHANGE, not a data backfill.

---

## 5. Acceptance Criteria for a Future Backfill Ticket

If a backend normalization ticket is initiated under §4.1 or §4.2, the following criteria must be met before Android considers it complete:

### C1 synthetic-title fix

- [ ] Backend sets a meaningful plain-text-derived title at item creation time for all synthetic-URL captures
- [ ] Existing C1 items (null title, synthetic URL) in active accounts are updated with a best-effort title (first 100 chars of stored text, or a safe placeholder if text is unavailable)
- [ ] `capturePresentation()` on Android produces an `Excerpt: "…"` title from the populated title field — no Android code change required if the field is populated
- [ ] `CapturePresentationTest` `synthetic shared text url uses excerpt title and android selection fallback` test continues to pass (the "Android selection" fallback path still works for any synthetic-URL item that still has a null title after migration)

### C2 pre-metadata excerpt reclassification

- [ ] Backend introduces a `capture_kind` backfill migration that targets only items with a reliable existing plain-text/excerpt signal, with per-account opt-in or operator confirmation required before running
- [ ] A/B audit on a sample of affected items confirms false-positive reclassification rate is below acceptable threshold before full rollout
- [ ] After migration: `capturePresentation()` produces `Excerpt: "…"` title and correct source label for reclassified items — no Android code change required
- [ ] `CapturePresentationTest` `excerpt capture ignores legacy raw host when trusted provenance is absent` test continues to pass for the "Android selection" path (items with `capture_kind` but no `source_url`)
- [ ] No regression in Up Next, Locus, now-playing strip rendering

---

## 6. Spec Boundaries

This spec intentionally excludes:

- Any Android code changes to `CapturePresentation.kt` or composables
- Backend implementation of the §8 `ArticleSummary` CONTRACT CHANGE (separate coordination ticket)
- Backend implementation of any normalization described in §4 (separate backfill ticket when triggered)
- Web or extension legacy handling
- Items in `trash`/`archived` state — degraded rendering in those views does not warrant backfill priority
- Time-based expiry or automatic staleness detection for legacy items

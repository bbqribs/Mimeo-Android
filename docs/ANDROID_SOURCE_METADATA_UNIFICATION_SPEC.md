# Android Source Metadata Unification — Client Contract Spec

**Version:** 1.0
**Status:** Final (client-side contract locked)
**Date:** 2026-04-19
**Scope:** Android contract definition; no backend or web changes in this ticket

---

## Purpose

This document locks the Android-side source metadata contract and defines the cross-repo alignment requirements needed for backend, extension, and web to converge on the same provenance/origin/rendering rules. It is the reference point for future coordination tickets. No code changes are made in this ticket.

---

## 1. Source Metadata Fields

The current shipped API contract on Android defines these source metadata fields:

| Field | Type | Description |
|---|---|---|
| `source_type` | `string?` | `"web"` \| `"app"` \| null — broad capture origin classification |
| `source_label` | `string?` | Human-readable source name (domain, app name) |
| `source_url` | `string?` | Trusted provenance URL; must be HTTP/HTTPS |
| `capture_kind` | `string?` | Capture modality; drives excerpt vs article classification |
| `source_app_package` | `string?` | Android package name of the originating app |
| `host` | `string?` | Domain extracted from the item URL; legacy field |

These fields are present on:

- `PlaybackQueueItem` — via `GET /playback/queue`
- `ItemTextResponse` — via `GET /items/{id}/text`
- `NowPlayingSessionItem` — mirrored from the above at playback time

They are **absent** from `ArticleSummary` (returned by `GET /items?view=...` for library views). See §8 for the implications.

---

## 2. Capture Category

All source rendering decisions begin with classifying the item into one of two categories.

### Excerpt-like (text-first)

An item is excerpt-like if **either** condition holds:

1. `capture_kind` (normalized to lowercase, trimmed) is one of: `shared_excerpt`, `manual_text`, `plain_text`, `excerpt`
2. The item URL host is exactly `shared-text.mimeo.local` (synthetic URL assigned to plain-text shares)

### Article-like (URL-first)

All other items: standard URL captures, browser saves, share-sheet URL captures where the URL is the content.

---

## 3. Source Label Precedence

### 3.1 Article-like items

Evaluated in order; first non-null/non-empty value wins:

1. `source_label` (server-provided) — strip leading `www.` if the value contains a dot
2. `host` (from item URL) — strip leading `www.`
3. `source_app_package` — used as-is
4. Literal `"App share"` — only when `source_type == "app"`
5. **null** — no source is displayed

### 3.2 Excerpt-like items

Two sub-classifications apply for excerpts: **provenance** (the original page the text came from) and **origin** (the Android surface that captured it).

Evaluated in order:

1. **Provenance** — host extracted from `source_url` via URI parsing, `www.` stripped. Requires `source_url` to be a valid HTTP/HTTPS URL. This is the highest-trust label.
2. **Origin** — `source_label` (if non-empty), else `source_app_package` (if non-empty)
3. **Fallback** — Literal `"Android selection"` (never null for excerpt-like items)

> **Key rule**: For excerpt-like items, `host` from the item URL is **never** used as a source label. The content URL host is not provenance for an excerpt. Only `source_url` yields a provenance label.

---

## 4. Title Precedence

### 4.1 Article-like items

1. `title` (trimmed, non-empty)
2. Fallback: raw `url`

### 4.2 Excerpt-like items

The title is always formatted as `Excerpt: "..."`:

1. Source text: first non-empty line of `title`, cleaned of a leading `Excerpt:` prefix and surrounding quotes
2. Fallback if title is absent: raw `url`
3. Result is truncated to 96 characters with a trailing ellipsis if needed
4. Final output is always wrapped: `Excerpt: "«text»"`

---

## 5. Source URL Precedence

### 5.1 Article-like items

1. `source_url` — if valid HTTP/HTTPS
2. Fallback: raw `url` — if valid HTTP/HTTPS **and** not a synthetic shared-text URL (`shared-text.mimeo.local`)

### 5.2 Excerpt-like items

1. `source_url` — if valid HTTP/HTTPS
2. **null** — no fallback to raw `url`; the content URL is not the provenance URL for excerpts

---

## 6. TTS Source Cue

When `speakTitleBeforeArticle` is enabled, a spoken `"From {source}."` cue is inserted between the title intro and the article body. The cue derives its source independently of the display label:

1. `source_label` — if **meaningful**: not `"Android selection"`, not a package-like identifier (contains `.` with no spaces)
2. Fallback: `host` from the item URL — only when `source_type == "web"` and `source_label` was present but unhelpful
3. **null** — cue is omitted entirely

The TTS cue uses the raw `source_label` field directly, not the resolved `CapturePresentation.sourceLabel`.

---

## 7. Legacy-Item Fallback Rules

A **legacy item** has all source metadata fields null (`source_label`, `source_url`, `source_type`, `capture_kind`, `source_app_package` are all absent/null). These items predate structured source metadata emission.

**Legacy behavior (current shipped, correct):**

- Capture category: article-like (null `capture_kind` fails the excerpt test)
- Title: `title` if present, else raw URL
- Source label: `host` from the item URL, stripped of `www.`
- Source URL: raw `url` if HTTP/HTTPS

This is a safe, correct fallback. No server-side migration is needed for display correctness. Legacy items will always render as article-like with a host-derived source.

---

## 8. Library Surface Gap

`ArticleSummary` (returned by `GET /items?view=inbox|favorites|archived|trash`) does not carry source metadata fields — only `host`, `title`, and `url`. Library views (Inbox, Favorites, Archive, Bin) therefore bypass `capturePresentation()` logic entirely:

| Surface | Current rendering |
|---|---|
| Library rows (Inbox, Favorites, Archive, Bin) | `title ?? url`; source = `host ?? url` |
| Up Next / Locus / now-playing | Full `capturePresentation()` via source metadata fields |

**Consequences of the gap:**

- Excerpt-like items in library views show their raw title instead of `Excerpt: "..."` format
- Excerpt-like items show `host` (content URL domain) as source rather than the provenance label
- This is cosmetically incorrect but not functionally broken

**Resolution path:** Backend must add source metadata fields to the `ArticleSummary` endpoint response. This is a **CONTRACT CHANGE** and is explicitly out of scope for this ticket. It requires a separate coordination ticket.

---

## 9. Content vs Provenance Invariants

These rules are locked. No cross-repo alignment work may violate them.

1. **Content links are content.** In-article hyperlinks from `content_blocks.links` are rendered as tappable spans only. They are never promoted to `source_url` or treated as item provenance.

2. **Body-link inference is prohibited.** Android does not infer `source_url` from body text. The server must supply `source_url` explicitly.

3. **Synthetic URLs are never surfaced.** Items with `shared-text.mimeo.local` URLs never expose their raw URL as a visible link or source attribution. `source_url` is the only valid link for these items.

4. **Excerpt identity is determined by `capture_kind` or synthetic URL host, not by content shape.** A short URL-captured article is still article-like.

5. **Provenance is never inferred from selection pattern alone.** The shipped Android selection behavior uses a strict list of trusted browser-sharing patterns. Generic heuristic detection is not a substitute for explicit `source_url` emission.

---

## 10. Cross-Repo Alignment Requirements

### Backend

- Continue emitting all source metadata fields on existing endpoints (`/playback/queue`, `/items/{id}/text`); do not remove or rename fields
- Add source metadata fields to `GET /items?view=...` response — this is a **CONTRACT CHANGE** requiring a separate coordination ticket
- `source_url` for excerpts must be the provenance URL (the original page), never a body content URL
- Do not synthesize `source_url` from body links

### Extension / Web

- Adopt the same two-category (excerpt-like / article-like) classification
- Use identical source label precedence: provenance (`source_url` host) → origin (`source_label` / `source_app_package`) → `"Android selection"` for excerpts; `source_label` → `host` → null for articles
- Never display in-article links as item source attribution

### Android — acceptance criteria for follow-on implementation tickets

When backend adds source metadata to `ArticleSummary`:

- [ ] `LibraryItemsScreen` row source label follows article-like precedence (§3.1)
- [ ] Excerpt-like items in library views render with `Excerpt: "..."` title format (§4.2)
- [ ] Excerpt-like items in library views use provenance/origin label precedence (§3.2)
- [ ] No regression in Up Next (`QueueScreen`) or Locus source rendering behavior
- [ ] Existing `CapturePresentationTest` cases pass unchanged
- [ ] New unit tests cover library-view excerpt and legacy-item rendering paths

---

## 11. Spec Boundaries

This spec intentionally excludes:

- Backend implementation of source metadata on new endpoints
- Web client source rendering implementation
- Extension source metadata emission changes
- Backend auto-migration or backfill of legacy items
- The `ArticleSummary` CONTRACT CHANGE (separate coordination ticket)
- Any cross-device or sync semantics

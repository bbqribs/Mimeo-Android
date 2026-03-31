# Problem Reports — Optional Full Title/Text Attachment (v2, CONTRACT CHANGE)

## Scope

This spec defines a bounded cross-repo contract change for problem reports:
optional full title/text attachment, with explicit size/privacy/retention rules.
It is decision-only and does not implement backend or Android changes.

## Goals

- Preserve v1 lightweight reporting flow.
- Add an explicit user opt-in path to attach richer content context.
- Keep storage/export/operator behavior predictable and bounded.

## v1 Baseline (Current)

- Android v1 sends category, note, item/url linkage, and bounded metadata.
- Backend/operator persist and export the existing fields:
  `id`, `reported_at`, `category`, `domain`, `url`, `note`,
  `client_type`, `client_version`, `article_id`,
  `article_status_snapshot`, `article_failure_reason_snapshot`.
- Full article title/text are not guaranteed as first-class persisted/exported fields.

## v2 Contract Shape

### New optional request fields

Add to `POST /feedback/problem-report` payload (all nullable):

- `article_title_attached: string | null`
- `article_text_attached: string | null`
- `include_full_text_attachment: boolean | null` (client intent/audit flag)
- `attachment_truncated: boolean | null` (server-authoritative in stored row/output)

Notes:
- Existing fields remain unchanged.
- `article_title_attached` and `article_text_attached` are user-opt-in only.
- Server may truncate text according to policy below and sets `attachment_truncated=true`.

### Full text policy: bounded, not unbounded

“Full text” in v2 means “attach as much as available up to bounded server limits.”
It is not unbounded storage.

Server limits:
- `article_title_attached`: max 512 chars (hard truncate)
- `article_text_attached`: max 200,000 chars (hard truncate)
- Request body guard: max 1 MB for this endpoint

If truncation occurs:
- Store truncated content.
- Set `attachment_truncated=true`.

## Retention & Privacy Rules

- Attachments are stored only when explicitly opted in.
- Default Android setting is **off** (no attachment).
- Operator visibility follows current operator token access controls.
- Attachments are included in exports by default for operator users.
- Retention: same retention window as problem reports table unless policy later diverges.
- Redaction:
  - no automated PII redaction in v2 (deferred),
  - add explicit user warning in Android before submit.

## Operator/Debug/Export Behavior

`/debug/problem-reports` row/detail output gains nullable fields:
- `article_title_attached`
- `article_text_attached`
- `attachment_truncated`
- `include_full_text_attachment`

CSV export:
- include `article_title_attached`
- include `article_text_attached` as escaped multi-line field
- include `attachment_truncated`

UI display:
- show title inline in row/detail.
- show text attachment in expandable/collapsible detail section.

## Android v2 UX Decision

In Locus report dialog, add explicit toggle:
- Label: `Attach title and full text`
- Default: OFF
- Helper text:
  - `Includes article title and body text with this report.`
  - `For privacy, leave off if the content is sensitive.`
  - `Large content may be truncated by the server.`

Behavior:
- Toggle ON: include title/text fields from current Locus payload when available.
- Toggle OFF: do not send title/text fields.
- Missing local text/title with toggle ON: still submit report; send null attachment fields.

## Backward Compatibility

- v1 clients continue sending existing payload shape; backend accepts unchanged.
- v2 backend keeps all new fields optional.
- v1 report rows remain valid with null attachment columns.
- Operator surfaces must handle null attachment fields without warnings.

## API/Schema Storage Changes (Backend)

Table: `item_failure_reports` add nullable columns:
- `article_title_attached` (TEXT)
- `article_text_attached` (TEXT)
- `attachment_truncated` (BOOLEAN, default false)
- `include_full_text_attachment` (BOOLEAN, nullable)

Pydantic/schema updates:
- request model accepts new optional fields.
- response/debug/export models include new fields.

## Smallest Follow-up Tickets

### Backend (smallest)

`Ticket: Problem reports v2 attachment contract implementation (backend, CONTRACT CHANGE)`

1. Migration for new nullable attachment columns.
2. Extend request schema and endpoint parsing for new optional fields.
3. Enforce title/text/request-size limits + truncation flag behavior.
4. Extend debug/operator JSON + CSV export fields.
5. Add focused tests for:
   - v1 payload compatibility
   - toggle-on payload persistence
   - truncation + flag behavior
   - export field presence/null handling.

### Android (smallest)

`Ticket: Android problem-report v2 attach-title-text opt-in`

1. Add `Attach title and full text` toggle to report dialog (default OFF).
2. Add privacy/size helper copy.
3. Send new optional fields only when toggle is ON.
4. Preserve current v1 submit/error/auth behavior.
5. Add focused UI/state tests for toggle default and payload shaping.

## Explicitly Deferred

- Offline queueing for problem reports
- PII redaction pipeline
- Attachment encryption-at-rest changes beyond existing DB guarantees
- Broader reporting workflow redesign/status lifecycle

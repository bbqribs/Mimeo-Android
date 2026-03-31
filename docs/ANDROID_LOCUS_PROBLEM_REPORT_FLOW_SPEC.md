# Android In-Locus Problem-Report Flow — v1 Spec

## Scope

This spec defines the Android in-Locus problem-report flow only. It does not
implement UI or submission logic.

This flow aligns with backend/operator v1 in:
- `POST /feedback/problem-report`
- categories: `save_failure`, `content_problem`, `app_problem`, `other`
- online submit in v1 (offline queueing deferred)

Reference backend spec:
- `C:\Users\brend\Documents\Coding\Mimeo\docs\OPERATOR_PROBLEM_REPORT_SPEC.md`

## Entry Point (Locus)

- Location: Locus action-bar overflow menu.
- Label: `Report a problem`.
- Availability:
  - Available when Locus is open for any item view (normal or preview).
  - Hidden only if user is not authenticated and no token/session exists.
  - If auth expires mid-flow, submit remains visible but fails with auth guidance.

## Categories and Default Selection

Shown in this order:
1. `content_problem` (label: `Content/display problem`)
2. `app_problem` (label: `App operation issue`)
3. `save_failure` (label: `Save failure`)
4. `other` (label: `Other`)

Default category behavior:
- Item-linked report (`item_id` present): default `content_problem`.
- URL-only report (`item_id` absent, URL available): default `save_failure`.
- Neither item nor URL context: default `app_problem`.

## User Input (Required vs Optional)

Required:
- `category` (single select)
- `user_note` (non-blank, max 500 chars)

Optional:
- User may edit detected URL field when present.
- User may clear URL for app-only issues.

Not included in v1:
- screenshots
- attachments
- multi-step triage questions
- contact-preference capture

## Auto-Attached Context

### Always attached
- `client_type = "android"`
- `client_version` (version name/code)
- `report_time` (ISO-8601 UTC)
- `category`
- `user_note`
- `url` (nullable)
- `item_id` (nullable)

### Item-linked reports (`item_id` available)
- `item_id` = current Locus item id
- `url` = current item url (if known)
- Optional enrichment fields (if readily available in current view model state):
  - `source_label`
  - `source_url`
  - `capture_kind`
  - `source_type`

### URL-only reports (`item_id` unavailable)
- `item_id = null`
- `url` from currently visible/report-target URL context
- No item-derived enrichment required

## Submission and Auth Behavior

- Endpoint: `POST /feedback/problem-report`
- Auth: existing signed-in user token/device token only.
- If no valid auth:
  - block submit
  - show `Sign in to submit problem reports.`
- If auth is stale (`401`):
  - show sign-in recovery guidance
  - preserve form fields until dismissed

## Success and Error Feedback

On submit:
- Disable submit button + show in-flight progress state.

On success (`201`):
- Close sheet/dialog.
- Show confirmation: `Problem report sent. Reference: {report_id}`.

On failure:
- Keep sheet/dialog open with form state intact.
- Show category-specific message:
  - `429`: rate-limit message
  - network failure: retry guidance
  - other API errors: generic submit failed message + retry

## Offline Behavior Decision (v1)

- v1 is **online-only**.
- Offline queueing for problem reports is **explicitly deferred**.
- If offline/unreachable:
  - submit fails immediately with retry guidance
  - no local queue persistence in v1

## Smallest Follow-up Implementation Ticket

`Ticket: In-Locus problem-report flow implementation (Android v1)`

Implementation slice:
1. Add `Report a problem` action to Locus overflow.
2. Add bounded report sheet/dialog with category + note + optional URL edit.
3. Auto-attach required context fields and item/url linkage per this spec.
4. Submit to `POST /feedback/problem-report` using existing auth/session.
5. Implement success/error UX states per this spec.
6. Keep offline queueing explicitly out of scope.


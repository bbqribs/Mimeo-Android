# Claude Design Prompt (Paste-Ready)

You are doing a **v1 implementation-hardening redline pass**, not a redesign.

## Scope and intent
- Preserve the approved **Paper & Ember** direction.
- Only propose changes where a token/component is infeasible in Android Compose or simple server-rendered web.
- Output developer-facing redlines only (no new product plan, no new visual direction).

## Non-negotiables
- Keep product semantics unchanged:
  - Up Next = playback target/session.
  - Smart Queue = playlist-like source.
  - Bluesky scan = live candidate browsing.
  - Bluesky save = normal Mimeo item.
  - Bluesky scan/save never auto-queue.
- No backend/API changes.
- No new queue semantics.
- No new telemetry/privacy assumptions.
- No third-party runtime font/CDN loading.

## Redline task
For each relevant handoff item, classify as:
1. Keep for v1
2. Change before implementation
3. Defer
4. Reject

For each Change/Defer/Reject: give one reason and one concrete replacement/constraint.

## Focus screens only
- Inbox / Library
- Up Next
- Smart Queue
- Reader / Locus
- Bluesky candidate browser
- Settings / Privacy & diagnostics

## Must-cover risk points
- Custom fonts
- Swipe gestures
- Row density
- Serif row titles in dense lists
- Light theme default vs current implementation state
- Dark theme as separate direction
- "Re-seed Up Next" wording/discoverability
- QA checks that reference non-implemented features

## Output format
- Concise redline matrix by screen/component with the 4-way classification.
- Short v1 token/component delta list.
- Explicit "Do not implement in v1" list.
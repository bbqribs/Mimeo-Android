# Claude Design Refinement Prompt (Final Redline Pass)

You are doing a final **implementation redline** pass for Mimeo, not a new visual direction.

## Objective
- Critically review the existing "Paper & Ember" handoff and harden it for implementation.
- Preserve the approved **Paper & Ember** direction unless a token/component is infeasible in Android Compose or simple server-rendered web.
- Produce developer-facing redlines and decisions, not a product strategy or roadmap rewrite.

## Non-negotiable constraints
- Keep current product semantics unchanged (see semantics lock).
- No new backend/API assumptions.
- No new queue semantics.
- No new telemetry/privacy assumptions.
- No third-party runtime font/CDN loading.
- No broad web rewrite or framework migration.

## Required output format
For each relevant handoff item, classify as:
1. Keep for v1
2. Change before implementation
3. Defer
4. Reject

For every "Change", "Defer", or "Reject":
- State exactly why.
- Provide minimal replacement guidance (token/component copy, spacing, typography, interaction wording).
- Keep guidance implementation-oriented and testable.

## Focus surfaces only
- Inbox / Library
- Up Next
- Smart Queue
- Reader / Locus
- Bluesky candidate browser
- Settings / Privacy & diagnostics

## Deliverables
- A concise redline matrix by screen and component.
- A short token/component delta list (only what must change).
- A QA-impact note that removes checks tied to non-implemented features.
- A "do not implement in v1" list aligned with existing constraints.
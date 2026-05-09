# Implementation Redline Request

Classify each current handoff item as:
1. Keep for v1
2. Change before implementation
3. Defer
4. Reject

Use implementation feasibility for Android Compose and simple server-rendered web as the decision standard.

## High-risk items to explicitly evaluate
- Custom font strategy (bundle vs fallback, payload risk, offline behavior).
- Swipe/gesture interactions (discoverability and accidental action risk).
- Row density presets (compact/default/comfortable) across list-heavy screens.
- Serif row titles in high-density contexts.
- Light theme as default in contrast with current dark-first implementation state.
- Dark theme as a separate direction vs same token system variant.
- "Re-seed Up Next" wording and discoverability (icon-only vs labeled action).
- QA checklist items that assume features not currently implemented.

## Redline response requirements
For each non-"Keep" decision:
- Name the exact handoff item.
- Give one concrete reason.
- Provide one concrete replacement or constraint.
- Keep output concise and developer-actionable.
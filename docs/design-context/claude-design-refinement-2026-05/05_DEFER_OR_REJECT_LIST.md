# Defer Or Reject List (v1)

Do not implement the following in v1 unless separately approved.

## Reject for v1
- New hidden gestures that replace existing explicit controls.
- New queue semantics or automatic queue mutation flows.
- Third-party runtime font/CDN loading.
- Backend/API contract changes introduced by design handoff language.
- Telemetry/crash-report behavior that includes reading content details by default.

## Defer unless explicitly approved
- Gesture-only shortcuts that duplicate existing buttons.
- Broad web rewrite to mirror Android structure one-to-one.
- Template/framework migration (CSS-in-JS, design-system runtime, major frontend stack shift).
- QA checks tied to non-implemented features or future-only flows.
- Expanded theme/font settings beyond currently implemented semantics.

## Handoff-specific caution points to redline
- "Re-seed Up Next" discoverability/copy should stay explicit but may need wording simplification.
- Density variants must not destabilize row action layout or accessibility at large font scale.
- Serif-heavy row titles should not reduce scanability in dense queue/library lists.
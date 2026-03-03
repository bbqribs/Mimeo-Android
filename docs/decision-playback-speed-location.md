# Decision: Playback Speed Stays In The Locus Header

**Status:** Accepted  
**Date:** 2026-03-03  
**Scope:** Android Redesign v1.1 / Locus / pinned PlayerBar

## Decision

Playback speed remains in the Locus expanded header and does **not** move into the shared pinned `PlayerBar`.

The pinned `PlayerBar` remains focused on transport and queue navigation:
- Mark done
- Previous item
- Previous segment
- Play/pause
- Next segment
- Next item

Playback speed is treated as a secondary content-level setting for the current listening session, not a primary transport action.

## Alternatives Considered

### Move speed into the pinned PlayerBar

Rejected for v1.1.

Reasons:
- The pinned bar is already dense on narrow screens, and adding speed would either displace an existing control or reduce tap-target clarity.
- `Mark done` still has higher value in the always-visible control bar because it completes the item and affects queue flow.
- Speed is used less frequently than play/pause and skip controls, so keeping it in the expanded header preserves access without adding constant chrome.
- The current Locus expanded layout already provides a stable action row where speed fits cleanly with collapse and overflow actions.

### Keep speed as plain text-only in the header

Rejected.

Reasons:
- Text-only `1.25x` reads like state but not clearly like an affordance.
- The redesign direction calls for a more recognisable media-control treatment.

## Rationale

Keeping speed in the expanded header preserves a clean separation:
- The pinned `PlayerBar` handles transport and completion.
- The expanded Locus header handles item-level actions and listening preferences.

This avoids churn in the shared `PlayerBar` contract, avoids overcrowding on small devices, and keeps collapsed Locus compact. It also keeps behavior consistent with the current explicit expand/collapse pattern: users enter expanded Locus to access secondary controls such as speed and overflow actions.

## UX / Accessibility Spec

- Location: speed appears only in the expanded Locus header action row, beside `Collapse` and overflow.
- Collapsed Locus: no speed control is shown.
- Pinned `PlayerBar`: no speed slot is added in v1.1.
- Primary affordance: use a speedometer or dial-style icon as the main cue, not bare text alone.
- Candidate icon direction: prefer an existing Material symbol if one reads clearly at Android header sizes; otherwise add a dedicated speedometer-style asset rather than overloading generic media icons.
- Label treatment: show icon plus current value in expanded Locus when space allows.
- Tight-space fallback: keep the icon visible and allow the value label to collapse before truncating other higher-priority actions.
- Value format: `1x`, `1.25x`, `1.5x`, `2x`.
- Value rules: preserve the stored playback-speed value; trim trailing zeros for display only.
- Content description: `Playback speed, 1.25x. Double tap to change.`
- Change announcement: when the user selects a new speed, announce `Playback speed set to 1.25x.`
- Consistency: the same speed entry point and wording apply for Locus tab expanded view, resume/deeplink entry into expanded Locus, and any future surface that reuses the expanded Locus header.

## Follow-Up Implementation Ticket

Placeholder: `TBD - replace expanded-header text button with icon-led speed control and preserve current speed dialog behavior`

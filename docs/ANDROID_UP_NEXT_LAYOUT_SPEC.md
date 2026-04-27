# Android Up Next Layout Spec

**Status:** Planning/spec only. Does not authorize code changes.
**Date:** 2026-04-27
**Scope:** Android Up Next layout and interaction contract after
mini-player v1. No backend, storage, or cross-device sync contracts.

## 1. Purpose

This is the implementation source for the Up Next layout ticket after
mini-player v1. It defines the v1 visual model for Up Next as three
regions:

1. History
2. Active / Now Playing
3. Upcoming

The active item is the anchor. It is not draggable. Only upcoming rows
are reorderable. History is a record, not part of queue reorder.

This spec intentionally avoids choosing persisted-history retention,
privacy controls, backend contracts, or cross-device sync.

## 2. Source Context

- `docs/REDESIGN_COMPLETION_PLAN.md` section 7 B2
- `docs/DESIGN_BEHAVIOR_RECONCILIATION.md`
- `docs/DESIGN_WIREFRAME_RECONCILIATION.md`
- `docs/ANDROID_QUEUE_ACTIONS_PATTERN_SPEC.md`
- Current behavior reference:
  - `app/src/main/java/com/mimeo/android/ui/queue/QueueScreen.kt`
  - `app/src/main/java/com/mimeo/android/AppViewModel.kt`

## 3. Current Behavior Summary

Current Up Next is a session-first screen with a flat session list.

| Area | Current behavior |
|---|---|
| Session list | One flat list of all `NowPlayingSession.items`. No History / Active / Upcoming grouping. |
| Active item | Border-only distinction, stronger title treatment, no drag handle, no remove icon. Tap opens Locus. |
| Non-active rows | Drag handle, remove icon, tap opens Locus. Current implementation treats every non-active row as reorderable. |
| Reorder | Drag reorder plus TalkBack custom actions, "Move up" and "Move down". Active item is protected by the `isCurrent` guard. |
| Re-seed | Explicit action. Confirmation appears when the current local session differs from the selected source. |
| Refresh | Refreshes queue/source content and syncs progress. It does not auto-reseed Up Next. |
| Clear session | Header close button clears the whole Now Playing session. No separate Clear upcoming affordance. |
| History | No distinct history grouping. Pre-active rows can exist in the session list but are not visually treated as history. |
| Snap-to-active | Not present. Current panel auto-scrolls to the active item when current item measurements are available. |
| Save queue as playlist | Not present in Up Next. |

## 4. Target Layout Model

Up Next is not a generic list surface. It is a playback-session surface
organized around the active item as the anchor.

### Region Anatomy

| Region | Purpose | Row treatment | Actions |
|---|---|---|---|
| History | Items before the active anchor, when safely available. | Muted, lower emphasis, not selected, not reorderable. | No drag handle. No remove icon in v1. Row overflow deferred until history semantics are resolved. |
| Active / Now Playing | The current playback anchor. | Prominent anchor treatment. Visually stronger than both History and Upcoming. | Tap opens Locus/current reader. No drag handle. No remove icon unless a later ticket explicitly justifies it. |
| Upcoming | Items after the active anchor. | Normal queue rows with visible reorder affordance. | Drag handle, remove icon, TalkBack Move up / Move down, optional row overflow only for queue-row actions already supported. |

### Section Headers

Use explicit section headers so the model is visible:

| Header | Recommendation |
|---|---|
| History | Show only when the History region is present. Include count if useful, for example "History - 3". |
| Now Playing | Recommended above the active anchor. If the active card is visually self-labeling, the header may be reduced but the region must remain obvious to TalkBack. |
| Upcoming | Always show when there is an active session. Include count when non-zero, for example "Upcoming - 8". Place Clear upcoming near this header. |

Header treatment should be compact and operational: label-medium or
equivalent, not large marketing-style headings. Keep spacing tight enough
for long queues, but leave clear separation between regions.

### Spacing and Affordances

- Use slightly more vertical emphasis around the active item than around
  regular rows.
- Use separators or small vertical gaps between regions. Do not wrap the
  whole surface in nested cards.
- Keep row overflow/future overflow on the row trailing edge. Do not place
  visible play buttons on row faces.
- Drag handles appear only in Upcoming.
- Remove icons appear only for Upcoming rows in v1.

## 5. History Region V1

History is a future persisted-history region. The first implementation
slice must not require a persisted history store.

### Initial Implementation Options

| Option | Acceptable for first slice? | Notes |
|---|---:|---|
| Hide History until persistence exists | Yes | Safest first slice. Still build layout internals so the active and upcoming regions can coexist with a future History section. |
| Placeholder/spec-only History section | Yes, only in non-production preview/dev fixtures | Do not show an empty production section that implies history exists when it does not. |
| Derive History from current session items before `currentIndex` | Conditional | Allowed only if existing session data supports it safely and the rows are rendered as non-draggable, non-selectable, non-removable history. |
| Persisted history | No for first slice | Requires retention/privacy decisions outside this ticket. |

### History Behavior Rules

| Behavior | Rule |
|---|---|
| Visual emphasis | Muted compared with active and upcoming rows. Use lower alpha/color emphasis and avoid active-looking borders. |
| Drag | Never draggable. No drag handle. No drag gesture target. |
| Selection | Not selectable. Not part of multi-select. Long-press has no selection effect. |
| Reorder | Not part of queue reorder. TalkBack Move up / Move down must not appear on history rows. |
| Remove | Not shown in v1. Clearing history is a separate retention/privacy problem and is deferred. |
| Tap | Implementation-gated. Preferred product direction is anchored/current model: tap should open the item in Locus without treating history as upcoming reorder material. If implementing that cannot be described clearly from existing navigation/session behavior, stop and ask the operator. |
| Overflow | Defer row overflow for history until tap/action semantics are settled. `ANDROID_QUEUE_ACTIONS_PATTERN_SPEC.md` lists Play Next / Play Last as deferred, not absent. |

Persistence, privacy, retention count/time, purge, hide, and disable
controls are explicitly deferred.

## 6. Active / Now Playing Item

The active item is the playback anchor and the target of snap-to-active.

| Area | Rule |
|---|---|
| Visual treatment | Prominent anchor treatment: stronger surface, border, tint, or typography than nearby rows. It must be visually distinct even when surrounded by long queues. |
| Drag | No drag handle. Active item is never draggable. |
| Reorder actions | No TalkBack Move up / Move down actions on the active item. |
| Remove | No remove icon in v1. Removing the active item would blur clear-upcoming vs clear-session semantics and needs separate justification. |
| Tap | Opens Locus/current reader for the active item. Must not reorder, clear, or reseed the session. |
| Overflow | Up Next-level overflow is allowed near the screen/header. Active row overflow is not required for this slice. |

The active item may show compact metadata already available in session
rows: title, host/source label, and progress/state when available through
existing models. Do not invent new backend fields.

## 7. Upcoming Region

Upcoming preserves the existing reorder behavior for rows after the
active item.

| Behavior | Rule |
|---|---|
| Reorder | Preserve existing drag reorder for upcoming rows. |
| Drag handle | Visible only on upcoming rows. |
| Remove | Preserve remove icon for upcoming rows. |
| TalkBack | Preserve custom actions: "Move up" and "Move down" for every reorderable upcoming row where movement is possible. |
| Tap | Opens Locus for that item using existing Up Next row semantics. |
| Clear upcoming | Place near the Upcoming section header, not in row overflow. |
| Empty upcoming | Show a compact empty state under the Upcoming header, for example "No upcoming items." Keep Re-seed and Up Next overflow available. |

Upcoming rows are the only rows participating in queue reorder. History
and active rows must not be included in the index range passed to reorder
logic.

## 8. Snap-to-Active

Add a floating anchor pill that appears when the active item is off-screen.

| Area | Rule |
|---|---|
| Purpose | Return the user to the active anchor in long queues. |
| Placement | Floating over the Up Next list, preferably bottom-end above the mini-player/docked player area and clear of row remove/overflow targets. |
| Label | Use a concise accessible label such as "Jump to Now Playing". Visible text may be "Now Playing" or "Jump to Now Playing". |
| Trigger | Show when the active item is fully outside the visible list viewport. Recommended threshold: hide while any meaningful portion of the active item is visible; show once less than roughly 24 dp, or less than 25%, of the active row remains visible. |
| Tap | Scrolls the Up Next list to the active item. |
| State | Must not alter playback, current index, queue order, session source, or refresh/reseed state. |
| Dismissal | Disappears after the active item is visible again. |

If the first implementation uses Compose list visibility APIs, prefer a
simple visibility rule over a fragile pixel-perfect threshold. The
contract is that the pill reflects visibility, not playback state.

## 9. Clear and Destructive Actions

Clear upcoming and Clear all session are intentionally different actions.

| Action | Placement | Semantics | Confirmation |
|---|---|---|---|
| Clear upcoming | Near the Upcoming section header. | Removes upcoming rows only. Preserves active item and any history representation. | Confirm if the existing destructive-action pattern requires it. Copy must say upcoming, not session. |
| Clear all session | Up Next overflow/contextual destructive area. | Clears the whole local Now Playing session. | Confirm expected. Copy must say session. |
| Re-seed from current source | Existing explicit re-seed affordance / overflow entry. | Rebuilds session from current source using existing semantics. | Preserve existing dirty-session confirmation. |
| Refresh | Existing refresh button. | Refreshes source/list content and progress. | No auto-reseed. No clear. |

Refresh and re-seed remain separate. Pull-to-refresh, if ever added, must
also be refresh-only and must not auto-reseed.

## 10. Save Queue as Playlist

"Save queue as playlist..." is allowed from Up Next because the session
contents are unambiguous there.

| Area | Rule |
|---|---|
| Placement | Put in Up Next-level overflow and/or a screen-level action area. Prefer overflow if toolbar space is tight. |
| Availability | Available only while a session exists and there is queue content worth saving. |
| Scope | Saves the current Up Next queue/session according to the queue-actions spec. Do not expose from ordinary row overflow. |
| Dialog | Prompt for playlist name before saving. Exact duplicate-name handling is deferred to implementation. |
| Row surfaces | Do not place "Save queue as playlist..." on History, Active, or Upcoming row overflow. |

This spec does not define playlist backend or storage contracts.

## 11. Optional First-Run / Contextual Hint

A short hint may help introduce the History / Now Playing / Upcoming model.

| Area | Rule |
|---|---|
| Placement | Near the active anchor or between History and Now Playing. |
| Timing | First run or contextual only. Must be dismissible. |
| Copy | Plain product-language, for example: "Now Playing stays anchored here. Finished items move above; upcoming items stay below." |
| Behavior | Must not block scrolling, reorder, row tap, or playback controls. |
| Persistence | Hint dismissal persistence can use existing settings infrastructure only if already appropriate. Do not create a history-retention/privacy policy for the hint. |

The hint is optional for the first implementation slice.

## 12. Accessibility Contract

| Element | Requirement |
|---|---|
| History rows | Not selected, not reorderable, no Move up / Move down, no drag handle. If tappable, announce as an item that opens in Locus, not as a queue control. |
| Active row | Announce as Now Playing/current. No reorder actions. Tap opens Locus/current reader. |
| Upcoming rows | Preserve drag handle visibility and TalkBack Move up / Move down custom actions. Remove action remains reachable without drag. |
| Snap pill | Focusable. Accessible label: "Jump to Now Playing". Activating it scrolls only. |
| Clear upcoming | Label must include "upcoming". Confirmation title/body must distinguish it from clearing the whole session. |
| Clear all session | Label must include "session" or "all". Use destructive placement and confirmation. |
| Save queue as playlist | Label: "Save queue as playlist...". Do not hide behind long-press. |
| Long-press | No long-press-only actions. Long-press must not be the only way to select, reorder, clear, or save. |

## 13. Behavior Decision Tables

### Row Behavior

| Row kind | Tap | Drag | TalkBack reorder | Selectable | Remove icon | Row overflow |
|---|---|---:|---:|---:|---:|---|
| History | Opens Locus only if implementation-gated semantics are clear | No | No | No | No | Deferred |
| Active / Now Playing | Opens Locus/current reader | No | No | No | No | Not required |
| Upcoming | Opens Locus using existing semantics | Yes | Yes | No for this layout slice unless separately implemented | Yes | Optional/future queue-row actions |

### Session-Level Actions

| Action | Primary location | Must not do |
|---|---|---|
| Refresh | Header/source area | Must not auto-reseed or clear session. |
| Re-seed | Explicit action / overflow | Must not be folded into refresh. |
| Clear upcoming | Upcoming header area | Must not remove active item or history. |
| Clear all session | Overflow/contextual destructive area | Must not be a primary header button. |
| Save queue as playlist | Up Next overflow/action area | Must not appear on ordinary row overflow. |
| Snap to active | Floating anchor pill | Must not change playback/session state. |

## 14. First Implementation Slice Recommendation

Recommended first bounded slice:

1. Introduce visual region scaffolding around the current session model.
2. Render the active item as the prominent Now Playing anchor.
3. Render only items after the active index as Upcoming for reorder/remove.
4. Keep History hidden until persistence exists, unless current-session
   pre-active rows can be rendered safely as non-draggable history.
5. Add snap-to-active.
6. Add Clear upcoming only if the existing session model can remove all
   upcoming rows without changing active/history semantics. Otherwise keep
   it specified but defer implementation.
7. Keep Clear all session in overflow/contextual destructive placement.
8. Add Save queue as playlist from Up Next overflow/action area only if
   the playlist-save implementation ticket is in scope; otherwise leave the
   placement specified for that ticket.

Do not implement persisted history, retention/privacy controls, backend
contracts, cross-device sync, or history-row queue actions in this first
slice.

## 15. Stop Conditions for Implementation Tickets

Stop and request operator input if:

- The implementation requires choosing persisted-history storage,
  retention, purge, hide, disable, or privacy behavior.
- History-row tap behavior cannot be described as "opens Locus/current
  reader without treating history as upcoming queue material."
- Clear upcoming cannot be implemented without risking removal of the
  active item or hidden history.
- Reorder logic would need to include History or Active rows.
- The design starts turning Up Next into a generic list or separate Player
  Queue surface.

## 16. Manual Verification for This Spec

Docs-only verification is sufficient:

```powershell
Get-Content -Raw docs\ANDROID_UP_NEXT_LAYOUT_SPEC.md
git diff -- docs\ANDROID_UP_NEXT_LAYOUT_SPEC.md docs\REDESIGN_COMPLETION_PLAN.md ROADMAP.md
```

Confirm in plain English:

- The three-region model is clear.
- Active item is not draggable.
- Only upcoming rows are reorderable.
- History is non-draggable, non-selectable, and persistence-deferred.
- Snap-to-active scrolls only and does not mutate playback/session state.
- Clear upcoming and Clear all session are distinct.
- Save queue as playlist is Up Next-level, not row-level.

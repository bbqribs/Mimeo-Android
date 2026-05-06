# Android Up Next History and Earlier Queue Spec

**Status:** Spec checkpoint. No implementation in this ticket.
**Date:** 2026-05-06
**Scope:** Android Up Next display and navigation after jump/play actions.
Extends `docs/ANDROID_PLAYBACK_ACTIONS_V2_SPEC.md` and
`docs/ANDROID_UP_NEXT_LAYOUT_SPEC.md`. No Kotlin changes, backend/API
changes, persistence contract changes, or playlist storage changes.

---

## 1. Purpose

Playback Actions v2 defines visible row Play controls and source-surface
Play All / Play from Here behavior. This spec narrows the Up Next target
model after jump/play actions so the user can see where skipped, active,
upcoming, and completed/left-path items live.

Smart Queue remains a playlist-like source surface. Up Next remains the
active playback session/target.

---

## 2. Visual Sections

Up Next is visually divided into four ordered sections:

1. **History**
2. **Earlier in queue**
3. **Now playing / Active**
4. **Up Next**

| Section | Meaning | Default visibility |
|---|---|---|
| History | Items that were active and have exited the active queue path. | May be collapsed by default if implementation/product review recommends it. |
| Earlier in queue | Items still in the current queue snapshot before the active item. | Visible by default; must not be collapsed by default. |
| Now playing / Active | The current `NowPlayingSession.currentIndex` item. | Always visible when a session exists. |
| Up Next | Items still in the current queue snapshot after the active item. | Visible by default. |

History and Earlier in queue are mutually exclusive for any item. A row
must never appear in both sections at the same time.

---

## 3. Assignment Rules

Assignment is based on the current session snapshot plus the active-item
path:

| Section | Assignment rule |
|---|---|
| History | Items that were active and have exited the active queue path. |
| Earlier in queue | Items still in the current queue snapshot before the active item. |
| Active | The single current active item. |
| Up Next | Items still in the current queue snapshot after the active item. |

Earlier in queue is not "history." It contains items the user skipped
past inside the still-current queue snapshot and can still walk backward
to with Previous.

History is not "earlier in this snapshot." It contains prior active
items that left the active queue path because playback progressed or the
user jumped away after meaningful playback.

---

## 4. Jump to Upcoming Item

Upcoming rows in Up Next render a visible Play icon. Its behavior is
**jump to item**:

| Rule | Requirement |
|---|---|
| Visibility | Up Next upcoming rows show a visible Play icon. |
| Confirmation | No confirmation. |
| Behavior | The tapped row becomes Active immediately. |
| Earlier items | Upcoming items before the tapped row remain in the queue snapshot and move to Earlier in queue. |
| Prior active | Prior-active placement follows the threshold in section 8. |
| Progress | Queue movement preserves item progress. |
| Removal | Queue movement does not archive, bin, remove from playlist, or delete any item. |

Example:

```text
Before:
History: H I J K
Active: A
Up Next: B C D E F G

User taps Play on upcoming D.

After:
History: H I J K A
Earlier in queue: B C
Active: D
Up Next: E F G
```

The jump keeps B and C available in Earlier in queue. It does not treat
them as done, archived, binned, or removed.

---

## 5. Previous / Back Behavior

Previous first walks backward through Earlier in queue.

Example from the post-jump state above:

```text
Before Previous:
History: H I J K A
Earlier in queue: B C
Active: D
Up Next: E F G

After Previous:
History: H I J K A
Earlier in queue: B
Active: C
Up Next: D E F G
```

When moving from D back to C, D becomes the first item in Up Next. The
same rule repeats while Earlier in queue has rows.

After Earlier in queue is exhausted, Previous continues into History,
most recent first. History traversal preserves progress and does not
archive, bin, remove from playlist, or delete any item.

If History is collapsed visually, Previous can still traverse it. The UI
must make this discoverable, for example by announcing that collapsed
History remains reachable through Previous and by updating the collapsed
History count/state when playback enters that section.

---

## 6. Play From Here Distinction

Playlist, Inbox, Archive/Favorites, Smart Playlist, and Smart Queue
**Play from Here** replaces Up Next with a new visible-order snapshot
after confirmation. It starts at the selected source row and queues
downward from that source surface.

Up Next row Play does not replace the session. It jumps within the
existing queue snapshot, requires no confirmation, preserves skipped
earlier items in Earlier in queue, and applies the prior-active placement
threshold in section 8.

---

## 7. Normal Progress and Completion Rules

Queue movement alone does not archive, bin, remove, or mark an item done.
Progress is preserved whether an item lands in History, Earlier in queue,
Active, or Up Next.

Normal near-end/done behavior still applies only through the existing
progress/completion pipeline. This spec does not create a new completion
or auto-archive trigger.

---

## 8. Prior-Active Placement Threshold

When the user jumps away from the active item, decide whether the prior
active goes to History or Earlier in queue:

| Prior active state | Placement |
|---|---|
| Less than 5% progress **and** less than 30 seconds played | Treat as skipped; place in Earlier in queue. |
| 5% progress or more, or 30 seconds played or more | Treat as meaningfully active; place in History/progress state. |

Queue movement preserves progress either way. The threshold is not
user-configurable in v1.

Roadmap note: a future setting may expose "Treat briefly played items as
skipped." Its default should match the v1 threshold above, and later
versions may allow toggling or adjusting the threshold.

---

## 9. Save Queue as Playlist

The current default remains unchanged: **Save queue as playlist** saves
Active + Up Next only.

Earlier in queue and History are excluded from the default save scope,
even when visible. This preserves the existing Up Next Layout Spec rule
and avoids silently saving skipped or historical rows.

Roadmap note: a future expanded save option may allow the operator/user
to include Earlier in queue and/or History. That should be an explicit
option, not a silent change to the current default.

---

## 10. Accessibility and Collapsibility

| Area | Requirement |
|---|---|
| Section labels | Each visible section must have a TalkBack-reachable header or equivalent semantic grouping. |
| History collapsed state | If History is collapsed by default, the collapsed control must announce the section label, item count, and expanded/collapsed state. |
| Earlier in queue | Must be expanded and visible by default when it contains rows. It may have a manual collapse affordance only if the default remains expanded. |
| Mutual exclusivity | Screen reader traversal must not expose the same row under both History and Earlier in queue. |
| Up Next row Play | Visible Play icon is a distinct focusable control with a label such as "Jump to [item title]". |
| Previous behavior | TalkBack-visible player controls should make clear that Previous walks Earlier in queue first, then History most-recent-first. |

Collapsing History is acceptable because History is a record. Collapsing
Earlier in queue by default is not acceptable because it hides the items
the user explicitly skipped past and can still navigate back to.

---

## 11. Representation Note

The current `NowPlayingSession` shape can represent Earlier in queue as
session `items` before `currentIndex`; Active as `currentIndex`; and Up
Next as items after `currentIndex`. This spec does not require a backend
or API change.

Visible History may require implementation decisions already deferred by
`docs/ANDROID_UP_NEXT_LAYOUT_SPEC.md`, but the assignment model here does
not require changing backend contracts.

---

## 12. Out of Scope

- Kotlin implementation.
- Backend/API changes.
- Cross-device Up Next sync.
- Persisted history retention/privacy policy.
- Making Up Next a playlist-like source surface.
- Changing the default Save queue as playlist scope.
- User-configurable briefly-played threshold in v1.

---

## 13. Manual Verification for This Spec

Docs-only verification is sufficient:

```powershell
Get-Content -Raw docs\ANDROID_UP_NEXT_HISTORY_EARLIER_QUEUE_SPEC.md
git diff -- docs\ANDROID_UP_NEXT_HISTORY_EARLIER_QUEUE_SPEC.md ROADMAP.md
```

Confirm in plain English:

- The spec extends Playback Actions v2 and Up Next Layout without
  assuming backend/API changes.
- History and Earlier in queue are mutually exclusive.
- Earlier in queue is visible by default; History may be collapsed.
- Up Next upcoming row Play is a no-confirm jump within the existing
  queue.
- Previous walks backward through Earlier in queue, then continues into
  History most-recent-first.
- Play from Here replaces Up Next after confirmation, while Up Next row
  Play jumps without confirmation.
- Save queue as playlist still defaults to Active + Up Next only.
- Accessibility and collapsibility expectations are explicit.

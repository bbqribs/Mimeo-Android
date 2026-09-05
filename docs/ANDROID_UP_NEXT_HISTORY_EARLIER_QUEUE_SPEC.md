# Android Up Next History and Earlier Queue Spec

**Status:** Initial layout shipped; canonical History/pointer adoption shipped
in PR #492; account-scoped History management shipped in PR #493.
**Date:** 2026-08-29
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
| History | The server's unique account History projection, including ordinary and binned rows. | Visible whenever loaded, including without a session. |
| Earlier in queue | Items still in the current queue snapshot before the active item. | Visible by default; must not be collapsed by default. |
| Now playing / Active | The current `NowPlayingSession.currentIndex` item. | Always visible when a session exists. |
| Up Next | Items still in the current queue snapshot after the active item. | Visible by default. |

History and Earlier in queue use separate row identities. The same article may
appear once in canonical History and once in Earlier because History is a
record while Earlier remains current-session membership. Selection and
History removal must affect only the chosen section's row.

---

## 3. Assignment Rules

Assignment is based on the current session snapshot plus the active-item
path:

| Section | Assignment rule |
|---|---|
| History | The server's unique canonical projection, including ordinary and binned rows, in returned order. |
| Earlier in queue | Items still in the current queue snapshot before the active item. |
| Active | The single current active item. |
| Up Next | Items still in the current queue snapshot after the active item. |

Earlier in queue is not "history." It contains items the user skipped
past inside the still-current queue snapshot and can still walk backward
to with Previous.

History is not "earlier in this snapshot." It is the account-scoped server
projection of retained playback occurrences. Android neither deduplicates nor
reorders it, and it remains visible when no current session exists.

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

After Earlier in queue is exhausted, Previous does not manufacture a replay or
requeue from canonical History. History remains a separately managed record;
ordinary rows can open available article content, while binned rows cannot open
unavailable content and must first be restored. Restore does not requeue or
start playback.

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
| Section identity | If the same article appears in History and Earlier, TalkBack and selection semantics must expose them as distinct section-scoped rows. |
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

Visible History comes only from `GET /up-next/history?include_trashed=true` and
is not persisted as a second durable Android store. Management uses the
settled preferences, occurrence-fenced removal, snapshot-fenced clear, existing
Bin/Restore lifecycle, and atomic session-clear contracts without an Android
contract change.

---

## 12. Out of Scope

- Backend/API changes.
- Android semantic-reorder cutover (shipped in PR #494).
- Cross-device assurance programme (shipped in PR #497; remaining manual
  observations are trigger-gated evidence).
- Changing raw retained-occurrence or retention policy.
- Replay, requeue, Play Next, Play Last, or Play from Here from History.
- Making Up Next a playlist-like source surface.
- Changing the default Save queue as playlist scope.
- User-configurable briefly-played threshold in v1.

---

## 13. Manual Verification for This Spec

For the specification reconciliation itself, inspect:

```powershell
Get-Content -Raw docs\ANDROID_UP_NEXT_HISTORY_EARLIER_QUEUE_SPEC.md
git diff -- docs\ANDROID_UP_NEXT_HISTORY_EARLIER_QUEUE_SPEC.md ROADMAP.md
```

Confirm in plain English:

- The spec extends Playback Actions v2 and Up Next Layout without
  assuming backend/API changes.
- History and Earlier use section-scoped row identity and may contain the same
  article without selection or mutation crossing sections.
- Earlier in queue is visible by default; History may be collapsed.
- Up Next upcoming row Play is a no-confirm jump within the existing
  queue.
- Previous walks backward through Earlier in queue but does not replay or
  requeue canonical History.
- Play from Here replaces Up Next after confirmation, while Up Next row
  Play jumps without confirmation.
- Save queue as playlist still defaults to Active + Up Next only.
- Accessibility and collapsibility expectations are explicit.

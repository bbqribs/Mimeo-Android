# Design wireframe reconciliation

**Status:** Working design reconciliation draft. Implementation-facing, but
does not authorize code changes. Not a final record.
**Date:** 2026-04-25
**Scope:** Android UI direction after reconciling Claude Design wireframes
with the post-redesign product model.

**Companion doc:** `docs/DESIGN_BEHAVIOR_RECONCILIATION.md` maps current
shipped Android behaviors against the directions established here, including
an at-risk behavior inventory and a prompt brief for the next Claude Design
pass.

## Source context

- Canonical product model:
  `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\PRODUCT_MODEL_POST_REDESIGN.md`
- List layout spec:
  `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\LIST_LAYOUT_HOMOGENIZATION_SPEC.md`
- Android queue actions spec:
  `docs/ANDROID_QUEUE_ACTIONS_PATTERN_SPEC.md`
- Claude Design artifacts reviewed:
  - `docs/planning/Wireframe/Mimeo Wireframes.html`
  - `docs/planning/Wireframe/Mimeo Wireframes - Standalone.html`
  - `docs/planning/Wireframe 1/Mimeo Wireframes.html`
  - `docs/planning/Wireframe 1/Mimeo Wireframes - Standalone.html`
  - `docs/planning/Wireframe 1/Mimeo Wireframes 2.html`
  - `docs/planning/Wireframe 1/Mimeo Wireframes 2 - Standalone.html`
  - `docs/planning/Wireframe 1/wf-screens-2.jsx`

The raw HTML/JSX artifacts are present locally and remain design reference
material. This document is the implementation-facing decision layer; the
HTML artifacts should not be moved into commit scope unless explicitly
requested.

## Locked product rules

These rules survive the wireframe reconciliation and override any conflicting
visual treatment:

- Up Next is not a generic list, library view, or playlist.
- Up Next has history, active item, and upcoming regions; this structure may
  diverge from generic list grammar.
- The active item is the playback anchor and is not draggable.
- Only upcoming Up Next rows are reorderable.
- History is a record, not a queue, and is not draggable.
- Smart playlists may seed Up Next through explicit actions, but they are not
  Up Next and do not auto-mutate Up Next.
- Save current queue as playlist belongs only in Up Next or active player
  context.
- Default row tap on library and playlist-shaped surfaces remains read/open
  in Locus; queue mutation is explicit.
- Cross-device Up Next sync remains out of scope and requires a backend
  CONTRACT CHANGE.

## Selected direction by surface

| Surface | Adopted wireframe source/direction | Accepted elements | Rejected elements | Open questions | Implementation notes |
|---|---|---|---|---|---|
| Up Next | Wireframe 1, Direction A - Conservative Up Next, with mini-player added from pass 2 | Comfortable density; explicit History, Now Playing, and Upcoming regions; muted history rows; distinct active item; upcoming drag handles; Clear upcoming preserving history and active item | All Dense variants; Wireframe 2 Player Queue where it conflicts with Wireframe 1 Up Next panel; any active-item draggable treatment; cross-device sync UI | Snap-to-active trigger/location; history-row tap behavior; history retention/privacy controls | Up Next can diverge from generic list grammar. Add mini-player without turning Up Next into the full player. Keep Save queue as playlist in Up Next/active player context only. |
| Library | Sectioned Library | Shared row anatomy; search/filter/sort chrome; date or logical section headers; visible overflow; optional favorite affordance | Dense Library; row-level primary play buttons; generic list rows that hide source/progress/action affordances | Exact section grouping rules beyond current library filters | Use the list layout homogenization grammar. Queue actions live in overflow/batch actions, not default tap. |
| Manual playlist | Conservative Playlist | Manual order; visible reorder handle where applicable; Play all/replace queue with confirmation; row overflow for playlist and queue actions | Dense playlist; active/current-item treatment inside playlist; cross-device queue state UI | Manual playlist search support remains optional v1 | Preserve playlist as persistent ordered collection. Reorder playlist items, not Up Next active state. |
| Smart playlist | Sectioned Smart Playlist | Filter summary; pinned section plus live matching section; sort controls; pin/unpin affordances; Freeze as manual playlist as secondary action | Dense smart playlist; treating smart playlist as Up Next; auto-mutation of Up Next | Pinned/manual-order behavior when items are archived, binned, or fall out of filter; empty-state details | Align with product model Model B: saved filter plus optional manual pin/order layer. Smart playlist can seed Up Next explicitly. |
| Bluesky | Sectioned Bluesky | Harvester/config header; rolling window framing; post/source context; article row using shared row grammar; Play Next/Add to Up Next/Send to playlist actions | Dense Bluesky; image/thumbnail row shape in v1; auto-promotion to Up Next; cross-device sync UI | Bluesky representation model: separate ingestion automation plus generated smart playlist view is recommended but not ratified; window-control placement | Treat as smart-playlist-shaped surface fed by a harvester. Images remain out of v1. |
| Locus | Pass 2 Locus direction, unresolved integration details | Reader remains primary; visible read progress; route to playback for active/current item; mini-player can dock below reader | Dense Locus; embedding generic queue management into reader; Save queue as playlist from non-active/non-player library context | Locus/player integration; when an item is both open for reading and active in Up Next, exact bridge affordance | Locus is a reading surface. Queue actions in Locus should use active item/player context rules from the queue actions spec. |
| Mini-player | Pass 2 mini-player direction, favoring the richer mini-player concept but not Dense | Persistent docked mini-player; progress strip; title/source; consolidated play/pause; speed control retained; upcoming count is acceptable | Dense compact-only mini-player; separate competing play and pause buttons; mini-player without speed access | Exact control density and expanded/collapsed states | Add rewind and fast-forward controls. Keep speed control reachable. Ensure play/pause is one consolidated toggle. |
| Full player | Unresolved; no selected final direction | Conservative and Sectioned pass 2 elements are useful references: playback progress, speed/voice controls, Up Next entry, Save queue as playlist in active player context | Any commitment to current full-player layout; Dense full player; Player Queue replacing Up Next | Full player necessity; whether player queue is a separate surface or only an action/context sheet; Locus/player integration | Do not implement a full-player redesign from this doc. Open a focused design ticket before code. |

## Explicit rejections

- All Dense variants are rejected across Up Next, Library, Manual playlist,
  Smart playlist, Bluesky, Locus, Mini-player, and Full player.
- Wireframe 2 Player Queue is rejected wherever it conflicts with Wireframe
  1's Up Next panel and canonical Up Next model.
- Any treatment that makes the active Up Next item draggable is rejected.
- Any cross-device Up Next sync UI is rejected for v1.
- Any generic-list treatment that erases Up Next's history / active /
  upcoming identity is rejected.
- Any smart-playlist design that behaves as live Up Next continuity is
  rejected.
- Any Save current queue as playlist entry point outside Up Next or active
  player context is rejected.

## Surface summaries

### Up Next

Use Wireframe 1 Direction A as the primary Up Next direction. It best matches
the product model: history above, active anchor in the middle, upcoming below.
Add the mini-player direction from pass 2 so playback remains globally
available without changing Up Next into a full-player screen.

Direction C's sectioning can inform visual hierarchy, but its snap-to-active
pill is not adopted as a decision because the trigger/location remains open.

### Library

Adopt Sectioned Library. It should read as a shared list surface with
sectioning and standard row anatomy, not as a queue. Queue actions are exposed
through row overflow and multi-select batch actions.

### Manual Playlist

Adopt Conservative Playlist. It keeps the manual-order mental model visible
and avoids confusing playlist membership with active playback state.

### Smart Playlist

Adopt Sectioned Smart Playlist. The pinned/live split matches the product
model recommendation of a saved filter plus optional manual pin/order layer.

### Bluesky

Adopt Sectioned Bluesky. The harvester framing should be visible above the
list, while the item rows remain smart-playlist-shaped and share the common
row grammar.

### Locus

Treat pass 2 Locus wireframes as exploratory rather than final. The selected
direction is to preserve Locus as the reader surface, with explicit bridge
affordances when the current reader item is also the active Up Next item.

### Mini-player

The mini-player direction is good and should move forward with changes:
retain speed controls, add rewind and fast-forward, and consolidate play/pause
into one stateful control.

### Full Player

Full player remains unresolved. No implementation ticket should assume the
current full-player variants are accepted. Resolve whether a full player is
needed, and how it relates to Locus and Up Next, before code.

## Unresolved design decisions

Preserve these as open questions:

1. Full player necessity: is a full player a required surface, or are Locus,
   mini-player, and Up Next sufficient?
2. Snap-to-active trigger/location: persistent pill, mini-player tap,
   toolbar/menu action, gesture, or another mechanism.
3. Locus/player integration: exact bridge behavior when the reader item is
   active in Up Next.
4. History-row tap behavior: restart playback, open in reader, add to
   upcoming, or no tap behavior.
5. History retention/privacy controls: retention duration/count, purge,
   hide, or disable controls.
6. Smart-playlist pinned/manual-order behavior: policy when pinned items
   leave the filter, are archived, or are binned.
7. Bluesky representation model: recommended direction remains separate
   ingestion automation plus generated smart playlist view, but final
   ratification belongs to the implementation-scoping ticket.

## Next 3 Android tickets

1. **Up Next visual direction and mini-player controls spec**
   - Lock Wireframe 1 Conservative Up Next plus mini-player.
   - Specify mini-player rewind, play/pause, fast-forward, speed, overflow,
     and Up Next entry behavior.
   - Preserve snap-to-active as unresolved unless the ticket explicitly
     resolves it.

2. **Sectioned shared list surfaces implementation slice**
   - Implement or finish the shared row/scaffold work for Library and Manual
     playlist first.
   - Carry Sectioned Library as the target library treatment.
   - Keep Smart playlist and Bluesky as later consumers, not part of the
     first implementation slice unless separately scoped.

3. **Locus/player integration design spike**
   - Decide whether a full player is needed.
   - Decide whether Player Queue is a distinct surface, an action sheet, or
     replaced by Up Next navigation.
   - Define the active-item bridge between Locus, mini-player, and Up Next.

## Manual verification

Docs-only verification is sufficient for this ticket:

```powershell
Get-Content -Raw docs\DESIGN_WIREFRAME_RECONCILIATION.md
git diff -- docs\DESIGN_WIREFRAME_RECONCILIATION.md ROADMAP.md
```

Review the document against the source product model and wireframe artifacts.
Confirm that selected directions, rejected directions, and unresolved
questions match operator intent before opening git/PR housekeeping.

# Redesign v2 Decision Snapshot (Drift Guard)

This is a compact implementation guard for settled Redesign v2 rules.  
Behavioral source of truth remains `docs/REDESIGN_V2_PLAN.md`.

## Settled rules to keep stable

1. Inbox is a sort-controlled view, not a manually reorderable list.
2. Up Next is device-local in v1 (not cross-device synced).
3. Up Next edits never mutate source playlists.
4. Re-seed of Up Next is explicit user action only.
5. Pull-to-refresh on Up Next must not auto re-seed.
6. Up Next is present in the drawer on day one of drawer rollout.
7. Playlist folders are cut from Redesign v2 scope.
8. Pagination for v1 uses `offset`/`limit`.
9. Undo scope in v1 is narrow:
   - restore item state flags only (archive/favorite/bin as defined by ticket),
   - do not promise playlist membership or list-position restoration unless a ticket explicitly expands scope.
10. Library views (`Inbox/Favorites/Archive/Bin`) are projections, not collections.
11. Playlists are explicit ordered collections; Up Next is a separate playback queue construct.
12. During Phase 0 and Phase 2, playback semantics stay unchanged unless a ticket explicitly authorizes behavior changes.

## Use

- If an implementation PR appears to conflict with these rules, stop and reopen product/architecture clarification before coding around it.
- Keep this file short; do not duplicate full plan detail here.

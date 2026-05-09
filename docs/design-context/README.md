# Mimeo Android — Claude Design context bundle

This folder is a **temporary, curated context bundle** for Claude Design wireframe exploration. It is not the canonical source of truth for anything.

## Purpose

Use this folder to provide Claude Design with focused context for wireframe and product prototype exploration of Mimeo Android screens — specifically the list-layout surfaces (library, playlists, Up Next) and the shared row/scaffold grammar.

**Suggested Claude Design mode: Wireframe**

Attach or link this folder only. Do not attach the full repo.

---

## What is in here

### `planning/`

Copied snapshots of the canonical planning docs from the Mimeo backend repo.

| File | What it is |
|---|---|
| `PRODUCT_MODEL_POST_REDESIGN.md` | Product model: surfaces, session model, queue actions, smart playlists, Bluesky harvester, and next implementation lanes. |
| `LIST_LAYOUT_HOMOGENIZATION_SPEC.md` | Row/surface grammar spec for all list-shaped surfaces. Defines R1–R5 row anatomy, surface regions S1–S9, per-surface extensions, and Up Next divergence boundary. |

**Canonical location:** `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\`

### `android-ui/`

Copied snapshots of the current Android Compose source files.

| File | What it is |
|---|---|
| `ListSurfaceScaffold.kt` | Shared `LibraryItemRow` component (R1–R5 slots) and `ListSurfaceScaffold` wrapper. The reference implementation for the shared row shape. |
| `LibraryItemsScreen.kt` | Inbox / Favorites / Archive / Bin screens. Consumes `LibraryItemRow`. |
| `PlaylistDetailScreen.kt` | Manual playlist detail screen. Consumes `LibraryItemRow` with drag-handle extension. |
| `QueueScreen.kt` | Up Next screen. Session panel with active-item anchor, drag-reorder for upcoming items, action bar. |
| `MiniPlayer.kt` | Mini-player persistent bar. Not a list surface; included for layout context. |

**Canonical location:** `app/src/main/java/com/mimeo/android/ui/`

### `screenshots/`

Drop device screenshots here to give Claude Design visual reference. Empty by default (`.gitkeep` only).

---

## Important rules

- **Do not edit these copies.** They are snapshots. If you need to change source files, edit the originals.
- **Do not treat this folder as a source of truth.** Planning docs may have been updated in the Mimeo repo since this bundle was created.
- **This folder is not committed** unless the operator explicitly decides to track it.
- The list-layout spec marks Up Next as the only legitimate divergence from the shared row grammar. Any wireframe that proposes structural changes to Up Next must justify them against §6.1 of the spec.

# Android Locus Start-in-Full-Screen Spec

Status: Implemented
Scope: Locus `isExpanded` expand/collapse mechanism removed entirely.

---

## 1. Decision

The "Collapse player" option in the Locus overflow dropdown was the only way to set `isExpanded = false`. It served no real use case — the collapsed state is just a mini peek card with no meaningful function distinct from the expanded reader. The feature was eliminated entirely rather than patched.

---

## 2. What was removed

| Item | Location |
|---|---|
| `isExpanded` state (`rememberSaveable`) | `PlayerScreen.kt` |
| Collapsed branch (`!isExpanded` UI: `LocusPeekCard` + `ExpandedPlayerTopBar` peek variant) | `PlayerScreen.kt` |
| `startExpanded: Boolean` dead parameter | `PlayerScreen.kt` |
| `isExpanded` + `onToggleExpanded` parameters | `LocusOverflowMenuItems` |
| "Collapse player" / "Expand player" `DropdownMenuItem` | `LocusOverflowMenuItems` |
| `LocusPeekCard` composable | `PlayerScreen.kt` |
| `startExpanded = ...` call-site argument | `MainActivity.kt` |
| `&& isExpanded` guard on `readerChromeHidden` | `PlayerScreen.kt` |
| `&& isExpanded` guard on `BackHandler` | `PlayerScreen.kt` |

---

## 3. What does NOT change

- `locusContentMode` (Full-text vs Playback-focused) — governed by `docs/ANDROID_LOCUS_FULLTEXT_DEFAULT_OPEN_SPEC.md`.
- Chrome visibility / immersive mode — user-triggered, unchanged.
- `PlayerControlsMode` (FULL / MINIMAL / NUB) — controls the player controls overlay; unchanged.
- Playback ownership rules — unaffected.
- `locusTapSignal` / `openRequestSignal` handling — unaffected (Locus is always expanded, so no forced-expand logic needed).

---

## 4. Prior analysis (for historical context)

The original spec proposed fixing the `rememberSaveable`-without-key bug by adding `isExpanded = true` inside the `openRequestSignal` LaunchedEffect. After reviewing the code, the decision was upgraded: since the only way to collapse Locus was a single overflow option with no practical value, the entire collapse mechanism was removed instead of patched.

`startExpanded: Boolean = false` had been declared as a parameter in `PlayerScreen` but was never referenced in the function body — a dead parameter. It was deleted as part of this cleanup.

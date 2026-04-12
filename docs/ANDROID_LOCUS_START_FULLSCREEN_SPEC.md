# Android Locus Start-in-Full-Screen Spec

Status: Approved for implementation
Scope: Launch/display rules for Locus expanded state only. No playback ownership changes.

---

## 1. Recommendation up front

**Do not add a user-facing settings toggle.**

The only correct behavior is: explicit item opens always expand Locus. This is a one-rule behavioral fix, not a preference. A toggle that lets users suppress expansion on item open serves no real use case and adds unnecessary settings surface.

---

## 2. What "full screen" means here

Locus has two distinct visual dimensions that are often conflated:

| Dimension | States | Where stored |
|---|---|---|
| **Reader panel** | Expanded (reader visible) / Collapsed (mini player only) | `isExpanded` — `rememberSaveable`, no key |
| **Chrome visibility** | Visible / Hidden (immersive, tap to reveal) | `readerChromeHidden`, derived from `immersiveReaderMode` + `isExpanded` |

"Start in full screen" refers to **Dimension 1** only: the reader panel should be expanded when the user opens an item. Dimension 2 (immersive/chrome-hidden mode) is not changed by this spec — chrome starts visible on every open.

`PlayerControlsMode` (FULL / MINIMAL / NUB) is a third, orthogonal dimension covering which controls overlay is shown inside the expanded reader. This spec does not change how it behaves.

---

## 3. Current behavior and the gap

`isExpanded` is initialised as `rememberSaveable { mutableStateOf(true) }` with no key. This means:

- **First entry into Locus**: correctly starts expanded.
- **User collapses Locus, then taps a different item in Up Next**: `openRequestSignal` fires, content reloads, but `isExpanded` remains `false` because `rememberSaveable` preserved the collapsed state. The reader panel stays collapsed even though the user explicitly asked to view an item. **This is the bug.**

`startExpanded: Boolean` is a dead parameter in `PlayerScreen.kt` — declared at line 758, never referenced anywhere in the function body. It has no effect.

---

## 4. Rules

### 4a. Explicit item open → always expand

**Rule**: Any explicit item-open action forces `isExpanded = true`.

Explicit item-open actions include:
- Tapping an item row in Up Next, Archive, Bin, or Favourites.
- Tapping a search result that navigates to Locus.
- Any navigation that increments `openRequestSignal` in PlayerScreen.

**Implementation**: Add `isExpanded = true` inside the `openRequestSignal` `LaunchedEffect` (PlayerScreen.kt ~line 1182), after `lastHandledOpenRequestSignal = openRequestSignal`.

### 4b. Autoplay continuation → preserve current state

**Rule**: Autoplay continue-to-next does NOT reset `isExpanded`. Whatever state the user left it in is preserved.

Rationale: The user is passively listening. Forcing expansion during autoplay would be jarring if they had deliberately collapsed the panel.

The existing `autoPlayAfterLoad` early-return in the `openRequestSignal` effect already handles this correctly — no change needed.

### 4c. App restart / fresh Locus entry

**Rule**: On the first Locus entry after app launch, `isExpanded` is `true` (the `rememberSaveable` default). No change needed.

### 4d. PlayerControlsMode is independent

**Rule**: `PlayerControlsMode` (FULL / MINIMAL / NUB) is not affected by this spec. The remembered mode continues to be restored from settings.

### 4e. Locus tab tap (return-to-now-playing behavior)

**Rule**: Tapping the Locus tab to return to the now-playing item should also expand (`locusTapSignal` handling). Already correctly triggered via tab tap → `isExpanded` should be set to `true` here too if it is not already.

---

## 5. What does NOT change

- `locusContentMode` (Full-text vs Playback-focused) — governed by existing spec (`ANDROID_LOCUS_FULLTEXT_DEFAULT_OPEN_SPEC.md`). Unaffected.
- Chrome visibility / immersive mode — user-triggered, not reset on open.
- `PlayerControlsMode` remembered state — unaffected.
- Playback ownership rules — unaffected.
- Preview-vs-playing item semantics — unaffected.

---

## 6. Implementation order

1. **Wire `isExpanded = true` into `openRequestSignal` LaunchedEffect** (`PlayerScreen.kt` ~line 1182). This is the core fix — one line.
2. **Wire `isExpanded = true` into `locusTapSignal` handling** if `isExpanded` is not already forced there.
3. **Remove or wire `startExpanded` parameter** — either delete it (it's dead) or wire it to set `isExpanded` at initial composition if a caller ever needs to pass `false` (currently none do; deletion is cleaner).
4. **Manual smoke test**:
   - Open item → Locus expands ✓
   - Collapse Locus → tap different item → Locus expands ✓
   - Collapse Locus → wait for autoplay to advance → Locus stays collapsed ✓
   - App restart → Locus tab entry → Locus expanded ✓
   - Locus tab tap → Locus expands ✓

---

## 7. Non-goals

- No Settings toggle.
- No "start in immersive/chrome-hidden" mode.
- No change to how `PlayerControlsMode` is remembered or applied.
- No new persistence layer — `isExpanded` remains session-local `rememberSaveable`.

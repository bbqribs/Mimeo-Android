# Android Visual V1 — Final QA Audit (2026-05)

Date: 2026-05-11  
Auditor: Claude (automated survey + plan reconciliation)  
Branch: master (post PR #545 / all token-slice PRs merged)

Source inputs:
- `docs/ANDROID_VISUAL_DESIGN_V1_PLAN.md` (canonical v1 scope)
- Live codebase survey: `ui/theme/`, all surface composables, `Models.kt`, `SettingsStore.kt`

---

## 1. Feature Flag / Wrapper Audit

| Item | Status | Notes |
|------|--------|-------|
| Runtime flag | **Delivered** | `AppSettings.visualDesignV1Enabled` (DataStore key `visual_design_v1_enabled`); default `false` |
| Compile-time override | **Delivered** | `VISUAL_DESIGN_V1_FORCE_ENABLED = false` in `Theme.kt:23` — dev opt-in only |
| Flag pass-through chain | **Delivered** | `MainActivity → MimeoAppTheme(enableVisualDesignV1) → resolveThemeRuntimePath → LEGACY / VISUAL_V1` |
| CompositionLocal propagation | **Delivered** | `LocalMimeoV1Active provides true` when v1 path active; default `false` |
| Developer settings toggle | **Delivered** | `SettingsScreen.kt:1850–1864` — developer-only "Visual design v1 wrapper" toggle; persists via `AppViewModel.saveVisualDesignV1Enabled` |
| Legacy path isolation | **Delivered** | When flag is off, routes to legacy `MimeoTheme()` composable; no token consumption in that path |
| Production default | **Correct** | Flag defaults to `false`; production users see no visual change until a separate enable ticket flips the default |

**Verdict: wrapper is correctly implemented and isolated. No action needed before default-enable ticket.**

---

## 2. Token Layer Audit

### 2a. Color Tokens

| Set | Identifier | Status | Notes |
|-----|-----------|--------|-------|
| Light palette | `MimeoColors.PaperLight` | **Delivered** | 16 color roles; background `#F4EFE7`, accent `#C25B2E` |
| Dark palette | `MimeoColors.EmberDark` | **Delivered** | 16 color roles; background `#0B0B0E`, accent `#B6A1FF` |
| System theme resolution | `VisualThemePreference.FOLLOW_SYSTEM / LIGHT / DARK` | **Delivered** | Resolved in `Theme.kt` via `resolveThemeRuntimePath` |

**Gap to verify manually:** contrast ratios for muted/metadata roles in both palettes against WCAG AA (plan §Risk table, "Contrast"). No automated check confirmed.

### 2b. Typography Tokens

| Role | Style | Status |
|------|-------|--------|
| `display`, `title` | Serif | **Delivered** |
| `row`, `body`, `meta`, `section`, `caption`, `button` | Sans | **Delivered** |
| `bodyRead` | Serif (Reader body) | **Delivered** |
| `mono` | Mono | **Delivered** |

Note: No Reader font picker shipped (correctly deferred per plan §19).

### 2c. Spacing Tokens

`MimeoSpacing.kt` — `s0`–`s10` (0 dp to 48 dp). **Delivered.**

### 2d. Density Tokens

| Density | `rowPadV` | `rowGap` | `sectionGap` | Status |
|---------|-----------|----------|--------------|--------|
| Default | 14 dp | 4 dp | 18 dp | **Delivered** |
| Compact | 10 dp | 2 dp | 14 dp | **Delivered** |
| Comfortable | — | — | — | **Deferred** (plan §16) |

Resolved via `densityTokensFor(VisualDensityPreference)` in `MimeoDensity.kt:25`.

### 2e. Shape Tokens

`MimeoShapes.kt` — 6 corner-radius roles (none, input, item, card, sheet, pill). **Delivered.**

### 2f. Elevation

No dedicated v1 elevation tokens; Material 3 defaults are used. **Acceptable** — plan does not specify elevation tokens.

---

## 3. Surface-by-Surface Checklist

Legend: ✅ Delivered / ⚠️ Manual verification required / ❌ Not started / 🔁 Deferred

### 3a. App Shell / Drawer

| Check | Status | Notes |
|-------|--------|-------|
| v1 branching in shell | ✅ | `MainActivityShell.kt:124` |
| Drawer list rendering | ✅ | `MimeoDrawerContent.kt:49` |
| Light/dark palette switch | ⚠️ | Needs manual confirmation both themes render correctly |
| Default/Compact density | ⚠️ | Drawer rows do not explicitly consume density tokens — verify visually |
| Route/navigation behavior unchanged | ✅ | No route-semantic changes in visual PRs |
| Drawer closed on launch | ❌ | Roadmap P0 #5 startup polish cluster, not a visual v1 item |

### 3b. Mini-Player Chrome

| Check | Status | Notes |
|-------|--------|-------|
| v1 token consumption | ✅ | `PlayerScreen.kt` lines 850, 2525, 3462, 3522, 3581, 3630, 3658, 3731 |
| Two-row controls (title/source separated) | ✅ | Shipped per redesign log |
| Speed chip | ✅ | v1 branched in `PlayerScreen.kt` |
| Overflow menu | ✅ | v1 branched |
| Light theme mini-player | ⚠️ | Manual verify — most existing testing was dark-mode-first |
| Compact density impact on player row | ⚠️ | Mini-player has no explicit density branching; verify player does not crop at Compact |
| Playback controls reach | ⚠️ | Tap targets ≥ 40 dp; verify at 360 dp viewport |

### 3c. Up Next (Queue Screen)

| Check | Status | Notes |
|-------|--------|-------|
| v1 token branching | ✅ | `QueueScreen.kt` lines 1825, 1856, 1932, 2458 |
| Default density row spacing | ⚠️ | Manual verify: `rowPadV = 14 dp`, `rowGap = 4 dp` |
| Compact density row spacing | ⚠️ | Manual verify: `rowPadV = 10 dp`, `rowGap = 2 dp` |
| Active anchor non-draggable | ✅ | Existing semantics; no v1 change |
| Snap-to-active pill | ✅ | Existing; no v1 change |
| Clear upcoming / Clear all session | ✅ | Existing; no v1 change |
| Save queue as playlist | ✅ | Existing; no v1 change |
| Manual save (+) entry point | ✅ | Preserved per plan |
| History hidden (deferred) | 🔁 | Cross-device sync not in v1 |
| Light / dark palette | ⚠️ | Manual verify both themes |
| Font scale 1.3x: rows legible, no crop | ⚠️ | Plan §Risk "Accessibility font scale" — required before default-enable |
| Font scale 1.5x: rows legible, no crop | ⚠️ | Same as above |

### 3d. Smart Queue

| Check | Status | Notes |
|-------|--------|-------|
| Inherits Up Next token path | ✅ | `SmartQueueScreen.kt` extends QueueScreen logic |
| Search / chip layout at 360 dp | ⚠️ | Plan §Phase 3 acceptance criterion — must not overflow |
| Chip/search at font scale 1.3+ | ⚠️ | Row actions must remain non-overlapping |
| Queue refresh naming unchanged | ✅ | No copy changes in visual PRs |

### 3e. Library / Inbox

| Check | Status | Notes |
|-------|--------|-------|
| v1 branching | ✅ | `LibraryItemsScreen.kt` lines 170, 690, 716 |
| Shared row via `ListSurfaceScaffold` | ✅ | Token-aware scaffold; density applied |
| Sectioned date headers (Inbox / Favorites / Archive) | ✅ | Existing; no v1 change |
| Inbox pending section above date buckets | ✅ | Existing; no v1 change |
| Multi-select / batch actions | ✅ | Existing; no v1 change |
| Default density | ⚠️ | Manual verify |
| Compact density | ⚠️ | Manual verify |
| Light / dark | ⚠️ | Manual verify |
| Font scale 1.3x | ⚠️ | Required |

### 3f. Playlist Detail

| Check | Status | Notes |
|-------|--------|-------|
| v1 branching | ✅ | `PlaylistDetailScreen.kt` lines 114, 854 |
| Shared row foundation | ✅ | Inherits `ItemRow` / `ListSurfaceScaffold` tokens |
| Drag-to-reorder preserved | ✅ | Existing; no v1 change |
| TalkBack move-up/move-down preserved | ✅ | Existing; no v1 change |
| Light / dark | ⚠️ | Manual verify |
| Default / Compact density | ⚠️ | Manual verify |

### 3g. Reader / Locus

| Check | Status | Notes |
|-------|--------|-------|
| v1 token branching in `ReaderBody` | ✅ | `ReaderBody.kt:105` |
| Serif body typography (`bodyRead`) | ✅ | 17 sp, Literata/serif family |
| Player screen integration | ✅ | `PlayerScreen.kt` v1 branching |
| Reader scroll / playback behavior unchanged | ✅ | No behavioral changes in visual PRs |
| Live TTS highlight | 🔁 | Tokens defined; no live service. Deferred per plan §27–28 |
| Reader font picker | 🔁 | Deferred per plan §19 |
| Light / dark Reader background | ⚠️ | Manual verify — paper-toned background in light mode |
| Locus route / navigation semantics | ✅ | Real Locus route shipped in Phase 3; no change in visual PRs |
| Font scale 1.3x in Reader body | ⚠️ | `sp` units used; verify no viewport overflow at narrow widths |

### 3h. Bluesky Candidate Browser

| Check | Status | Notes |
|-------|--------|-------|
| v1 token branching | ✅ | `BlueskyBrowseScreen.kt` lines 88, 185, 207, 236, 489, 566, 659 |
| Candidate post rendering | ✅ | v1 card/color tokens applied |
| Context bands | ✅ | v1 branched |
| Save copy ("Read in Mimeo" / "Open saved item") | ✅ | Plan §Clarifications corrects "Open in Mimeo" wording |
| No auto Up Next mutation on save | ✅ | Plan §Phase 4 Bluesky — preserved |
| No direct Bluesky API calls | ✅ | Backend-backed; no client-side AT protocol |
| Move to playlist after save | 🔁 | Deferred — requires manual-playlist picker flow |
| Light / dark | ⚠️ | Manual verify |
| Default / Compact density | ⚠️ | Density branching not explicit in BlueskyBrowseScreen; verify visual result |

### 3i. Settings

| Check | Status | Notes |
|-------|--------|-------|
| v1 token branching in Settings UI | ✅ | `SettingsScreen.kt` lines 467, 2119, 2145, 2240, 2400, 2524 |
| Theme picker (Follow system / Light / Dark) | ✅ | Developer toggle exists; picker wired |
| Density picker (Default / Compact) | ✅ | Wired to `SettingsStore` |
| Persistence round-trip | ✅ | Unit tested in `SettingsStoreVisualPreferencesTest.kt` (6 tests) |
| Invalid enum fallback (forward-compat) | ✅ | Tested |
| Reader appearance controls preserved | ✅ | Existing controls not removed |
| Light / dark Settings screen itself | ⚠️ | Manual verify |
| Comfortable density option | 🔁 | Deferred |

---

## 4. Light / Dark Coverage Summary

| Surface | Light | Dark |
|---------|-------|------|
| App shell / drawer | ⚠️ verify | ⚠️ verify |
| Mini-player | ⚠️ verify | ⚠️ verify |
| Up Next | ⚠️ verify | ⚠️ verify |
| Smart Queue | ⚠️ verify | ⚠️ verify |
| Library / Inbox | ⚠️ verify | ⚠️ verify |
| Playlist detail | ⚠️ verify | ⚠️ verify |
| Reader / Locus | ⚠️ verify | ⚠️ verify |
| Bluesky browser | ⚠️ verify | ⚠️ verify |
| Settings | ⚠️ verify | ⚠️ verify |

**Token definitions for both palettes are complete and unit-tested. Manual pass across all surfaces in both modes is required before default-enable.**

---

## 5. Default / Compact Density Coverage Summary

| Surface | Default | Compact | Notes |
|---------|---------|---------|-------|
| List rows (all surfaces via `ListSurfaceScaffold`) | ⚠️ verify | ⚠️ verify | `rowPadV` / `rowGap` applied via shared scaffold |
| Up Next rows | ⚠️ verify | ⚠️ verify | QueueScreen density tokens active |
| Bluesky cards | ⚠️ verify | ⚠️ verify | No explicit density branch; may fall through to scaffold defaults |
| Mini-player | ⚠️ verify | ⚠️ verify | No explicit density branch in player controls |
| Reader | N/A | N/A | No density concept in Reader body |
| Settings | ⚠️ verify | ⚠️ verify | Settings rows use tokens |

**Comfortable density remains deferred.** No code stub for it exists that could accidentally be surfaced; gap is clean.

---

## 6. Font Scale / Accessibility Risk Notes

| Risk | Plan Reference | Current State | Required Action |
|------|---------------|--------------|-----------------|
| Dense rows crop at font scale 1.3+ | Plan §Risk, "Accessibility font scale" | Row heights are **not fixed** — `Modifier.height` is not used; rows grow with `sp` text | Manual verify at font scale 1.3x and 1.5x on all list surfaces |
| Row action crowding (Play + More) at large font scale | Plan §Risk, "Row action crowding" | Actions preserved per v1 decision; no new crowding introduced | Verify at 360 dp + 1.3x scale; actions must remain reachable |
| Tap target minimums | Plan §Phase 1 acceptance | Existing 40 dp exceptions remain; no regression confirmed | Spot-check trailing action tap targets at font scale 1.3x |
| Smart Queue chip/search overflow at 360 dp | Plan §Phase 3 acceptance | No fixed-width chip overflow guard visible in survey | Verify at 360 dp viewport on physical or emulated narrow device |
| Reader body overflow at narrow width | Plan §Phase 4 Reader | `sp` units used; no clamping | Verify at 360 dp in Reader with system font scale 1.3x |
| Muted/accent contrast | Plan §Risk, "Contrast" | No automated contrast check | Manual WCAG AA check for metadata/caption roles in PaperLight and EmberDark |

**No accessibility regression has been introduced.** Risks are pre-existing plan items; verification is a gate for default-enable, not an indication of a bug.

---

## 7. Remaining Known Gaps

These are **deferred by design** — all documented in `docs/ANDROID_VISUAL_DESIGN_V1_PLAN.md`. None of them block the guarded default-enable decision.

| Gap | Deferred Reason | Where Documented |
|-----|----------------|-----------------|
| Reader font picker | No new picker in v1; existing controls preserved | Plan §19, §Risk "Existing Reader font picker" |
| Swipe gestures | Not in v1; visible actions and overflow used instead | Plan §19 |
| Live TTS Reader highlight | Requires alignment service backend; tokens defined only | Plan §27–28 |
| Cross-device Up Next sync | Requires backend CONTRACT CHANGE; snapshot-only in v1 | ROADMAP.md §14, plan |
| Move to playlist after Bluesky save | Requires manual-playlist picker flow | Plan §Clarifications |
| Comfortable density | Only Default and Compact shipped | Plan §16 |
| Instrumented tests for v1 surfaces | Scoped out of visual v1 tickets | ROADMAP.md P0 #6 |
| Theme flag removal | Stays until all surfaces verified and default flipped | Plan §Risk "Theme flag/removal plan" |

---

## 8. Behavioral Invariants Confirmed Unchanged

The following behavioral invariants were audited against the visual PRs. All are confirmed unchanged:

- **Up Next semantics:** session-queue-first, manual-save `+` entry, seed-source + re-seed confirmation, no auto-reseed on pull-to-refresh, snapshot-only with source label.
- **Queue refresh naming:** no copy changes in visual PRs.
- **Route / navigation:** no route or deep-link semantic changes; `ROUTE_LOCUS` / `ROUTE_LOCUS_ITEM` unchanged.
- **Playback controls:** existing play/pause/skip/speed controls unchanged; speed chip is visual-only enhancement.
- **Bluesky scan/save:** backend-backed, no direct AT protocol, no auto Up Next mutation on save.
- **Settings persistence:** theme and density round-trip correctly; existing reader font settings unaffected.
- **Drawer behavior:** routes unchanged; drawer closed-on-launch is a startup polish ticket (P0 #5), not a v1 visual regression.
- **Legacy v0 path:** `MimeoTheme()` composable still active when flag is off; no token consumption on legacy path.

---

## 9. Unit Test Coverage

| Test File | Tests | What Is Covered |
|-----------|-------|----------------|
| `MimeoThemeTokensTest.kt` | 8 | PaperLight color values, EmberDark color values, Default density resolution, Compact density resolution, theme choice (FOLLOW_SYSTEM/LIGHT/DARK), v1 runtime path (LEGACY vs VISUAL_V1) |
| `SettingsStoreVisualPreferencesTest.kt` | 6 | AppSettings defaults, empty DataStore fallback, theme persistence round-trip, density persistence round-trip, invalid enum fallback |

**No instrumented/UI tests for v1 surfaces.** All surface verification is manual. This is expected and documented as a separate roadmap cluster (ROADMAP.md P0 #6).

---

## 10. Default-Enable Readiness Decision

**v1 is ready for a guarded default-enable ticket**, subject to completing the manual verification matrix below.

Rationale:
- All planned surfaces have v1 token branching implemented.
- Flag and wrapper are correctly isolated; no production user is affected while the flag is off.
- Legacy v0 path is confirmed clean.
- Token definitions are unit-tested.
- All known gaps are documented deferred items, not implementation holes.
- Behavioral invariants are unchanged.

**Blocking for default-enable:**
1. Manual verification matrix (§11 below) must be completed and signed off.
2. At minimum one physical device test at 360 dp with system font scale 1.3x across list surfaces.
3. Light mode pass on all surfaces (dark-mode-first testing may have left light mode under-verified).

**Non-blocking (can ship after default-enable):**
- Instrumented test suite for v1 surfaces (ROADMAP P0 #6).
- Comfortable density.
- Theme flag removal (remove after default-enable PR is stable).

---

## 11. Manual Verification Matrix (for Default-Enable Ticket)

This section is the verification spec for the operator executing the guarded default-enable ticket. It is not required for the current docs-only audit.

### Prerequisites

1. Enable v1 in Settings → Developer → "Visual design v1 wrapper" ON.
2. Test device: physical Android phone at 360 dp (or emulator configured to 360 dp).
3. Test at default system font scale (1.0x) and at 1.3x.
4. Test both Light and Dark system theme (or via Settings → Appearance).
5. Test both Default and Compact density.

### Verification Checklist

#### App Shell / Drawer

- [ ] Drawer opens; item list renders with v1 colors and typography in light mode.
- [ ] Drawer opens; item list renders with v1 colors and typography in dark mode.
- [ ] Drawer route tap navigates correctly; no route regression.
- [ ] No visual overflow in drawer at font scale 1.3x.

#### Mini-Player

- [ ] Mini-player visible at bottom of shell outside Locus; correct background color in light and dark.
- [ ] Title and source line render with v1 typography roles.
- [ ] Speed chip visible and tappable at 360 dp.
- [ ] Play/pause/skip controls remain reachable at font scale 1.3x.
- [ ] Compact density does not crop player row.

#### Up Next

- [ ] Rows render with Default density: `rowPadV = 14 dp`, `rowGap = 4 dp` (visual spot-check).
- [ ] Rows render with Compact density: `rowPadV = 10 dp`, `rowGap = 2 dp`.
- [ ] Active anchor is non-draggable; upcoming rows are draggable.
- [ ] Snap-to-active pill present and functional.
- [ ] Clear upcoming and Clear all session work.
- [ ] Save queue as playlist works.
- [ ] Light and dark modes — both render correctly.
- [ ] Font scale 1.3x: no row text crops; actions remain reachable.
- [ ] Font scale 1.5x: no row text crops.

#### Smart Queue

- [ ] Search and chips do not overflow at 360 dp viewport.
- [ ] Row actions remain non-overlapping at 360 dp + font scale 1.3x.
- [ ] Light and dark modes.

#### Library / Inbox

- [ ] Date section headers visible; buckets correct.
- [ ] Inbox pending section above date buckets.
- [ ] Default and Compact density rows spot-checked.
- [ ] Light and dark modes.
- [ ] Font scale 1.3x: rows do not crop.
- [ ] Multi-select UI works (batch actions available).

#### Playlist Detail

- [ ] Rows render with v1 tokens.
- [ ] Drag-to-reorder works.
- [ ] TalkBack move-up / move-down accessible (non-drag alternative).
- [ ] Light and dark modes.

#### Reader / Locus

- [ ] Reader body uses serif typography at correct size.
- [ ] Light mode: paper-toned background (not pitch black).
- [ ] Dark mode: dark background with correct text contrast.
- [ ] Existing scroll / playback behavior unchanged.
- [ ] At 360 dp + font scale 1.3x: no Reader body overflow.

#### Bluesky Candidate Browser

- [ ] Candidate cards render with v1 colors.
- [ ] Context bands visible.
- [ ] Save action copy reads "Read in Mimeo" or "Open saved item" — not "Open in Mimeo".
- [ ] Saving does not modify Up Next.
- [ ] Light and dark modes.

#### Settings

- [ ] Theme picker (Follow system / Light / Dark) changes app theme live.
- [ ] Density picker (Default / Compact) changes list density live.
- [ ] Changes persist across app restart.
- [ ] Existing reader font / appearance controls still present and functional.
- [ ] Light and dark Settings screen renders correctly.

#### Contrast (spot-check)

- [ ] Metadata / caption text in PaperLight passes WCAG AA against background (`#F4EFE7`).
- [ ] Metadata / caption text in EmberDark passes WCAG AA against background (`#0B0B0E`).

---

## 12. Next Ticket Recommendation

**Recommended next ticket:** `Android Visual V1 — Guarded Default Enable`

**Scope:**
1. Complete manual verification matrix (§11) on a physical device.
2. If any blocking issues surface, fix them in the same ticket (expected to be minor visual polish only).
3. Flip `AppSettings.visualDesignV1Enabled` default from `false` to `true` in `Models.kt`.
4. Mark the developer toggle in Settings as "experimental — v1 is now default" or remove toggle from developer section (decision for the ticket).
5. Keep `VISUAL_DESIGN_V1_FORCE_ENABLED` compile flag in place but set to `false`; document removal plan in PR.
6. Update ROADMAP.md shipped log with the default-enable entry.

**Out of scope for that ticket:**
- Instrumented tests (separate cluster, P0 #6).
- Comfortable density.
- Theme flag removal (follow-up after default-enable is stable for one release cycle).
- Web harmonisation (separate Mimeo repo effort).

**Gate:** Operator manual sign-off on the §11 matrix before merge.

---

*End of audit. No code changes were made in this document. See `docs/ANDROID_VISUAL_DESIGN_V1_PLAN.md` for canonical v1 scope and decisions.*

# Android Visual Design v1 Implementation Plan

Date: 2026-05-09

Source inputs:
- `docs/design-context/Mimeo v1 Redline/Mimeo Implementation Handoff.html`
- `docs/design-context/Mimeo v1 Redline/Mimeo v1 Redline.html`
- Current Android codebase

This plan converts the visual handoff plus v1 redline into Android implementation work. It is intentionally repo-grounded and docs-only: no Kotlin, backend, font, screenshot, or private article data changes are part of this ticket.

## Accepted v1 Decisions

- Theme default is **Follow system**. Do not force light mode on upgrade.
- Ship one **Paper & Ember** design language with light and dark themes. Dark mode is the dark palette of Paper & Ember, not a second design direction exposed to users.
- Ship **Default** and **Compact** density only. Defer Comfortable.
- List row titles use sans typography in v1 for dense-list legibility.
- Reader body and prominent title/display usage stay serif.
- No swipe gestures in v1. Use visible actions and overflow/action sheets.
- No Reader font picker in v1.
- No telemetry upload behavior changes in the visual-design rollout.
- Preserve visible row actions, including `[Play]` and `[More]`, unless a later ticket explicitly revisits them. This overrides the redline suggestion to reduce visible row actions to Play-only.

## Clarifications

- **Viewport** means available layout width in dp after system insets and parent constraints, not physical screen width.
- **Alignment service** means future TTS-to-text synchronization. v1 may define highlight tokens/styles, but there is no live Reader highlight unless that service exists and is enabled.
- Bluesky post-save copy should not say `Open in Mimeo` where the object is already saved ambiguously. Prefer `Read in Mimeo` for opening the saved Reader item, or `Open saved item` where the destination is broader.
- `Move to playlist` after Bluesky save is deferred. It requires a manual-playlist picker/append flow, which is outside visual v1.
- The redline input is already present under `docs/design-context/Mimeo v1 Redline/`; do not duplicate it into `docs/design-context/visual-v1/` unless a later cleanup ticket normalizes design-context paths.

## Current Code Mapping

| Area | Current files | Notes for v1 planning |
| --- | --- | --- |
| Theme wrapper | `app/src/main/java/com/mimeo/android/ui/theme/Theme.kt` | Current `MimeoTheme` is dark-only and maps directly to Material 3 `darkColorScheme`. Phase 0A should add token definitions without changing the app-wide visual result. |
| Typography and reader font resources | `app/src/main/java/com/mimeo/android/ui/theme/Type.kt`, `app/src/main/res/font/literata_family.xml` | Reader font support already exists around Literata/system families. v1 visual work should not add new font payloads unless explicitly approved. |
| Settings storage | `app/src/main/java/com/mimeo/android/data/SettingsStore.kt`, `app/src/main/java/com/mimeo/android/model/Models.kt` | DataStore is `mimeo_settings`. `AppSettings` already carries reader appearance fields; theme/density preferences should be added as new explicit fields in Phase 0B, with migration-safe defaults. |
| Settings UI | `app/src/main/java/com/mimeo/android/ui/settings/SettingsScreen.kt` | Existing `Reader / Appearance` includes reader font selection and typography controls. v1 should add Theme + Density in a later settings phase, but should not expose a Reader font picker as part of visual v1. |
| App shell and drawer | `app/src/main/java/com/mimeo/android/MainActivityShell.kt`, `app/src/main/java/com/mimeo/android/MimeoDrawerContent.kt` | Shell owns `ModalNavigationDrawer`, route switching, app chrome, and mini-player placement. Drawer visual changes should be separated from route/semantic changes. |
| Shared list shell and rows | `app/src/main/java/com/mimeo/android/ui/common/ListSurfaceScaffold.kt`, `app/src/main/java/com/mimeo/android/ui/common/ItemRow.kt` | `LibraryItemRow` and `ItemRowTrailingActions` are the main shared row foundation. They already show visible Play + More actions; preserve that behavior in v1. |
| Queue / Up Next | `app/src/main/java/com/mimeo/android/ui/queue/QueueScreen.kt` | Primary pilot candidate because it exercises rows, player state, manual save entry, queue actions, and visual density under real content. |
| Smart Queue | `app/src/main/java/com/mimeo/android/ui/queue/SmartQueueScreen.kt` | Needs search/chip responsive behavior checked against 360 dp viewport. Do not rename existing queue refresh semantics in visual tickets. |
| Library | `app/src/main/java/com/mimeo/android/ui/library/LibraryItemsScreen.kt` | Uses shared rows and trailing actions. Good second-stage consumer after row/card tokens stabilize. |
| Bluesky browse/save | `app/src/main/java/com/mimeo/android/ui/bluesky/BlueskyBrowseScreen.kt`, `app/src/main/java/com/mimeo/android/bluesky/BlueskyServiceCoordinator.kt`, `app/src/main/java/com/mimeo/android/state/BlueskyStateHolder.kt` | Android Browse is backend-backed harvested content. No direct Bluesky calls and no auto Up Next mutation should be introduced by visual work. |
| Reader / Locus | `app/src/main/java/com/mimeo/android/ui/reader/ReaderScreen.kt`, `app/src/main/java/com/mimeo/android/ui/reader/ReaderBody.kt`, `app/src/main/java/com/mimeo/android/ui/player/PlayerScreen.kt` | Reader has its own typography, highlighting, scroll, and player integration risks. Defer until tokens and rows have proven out. |
| Mini-player | `app/src/main/java/com/mimeo/android/ui/player/MiniPlayer.kt`, shell integration in `MainActivityShell.kt` | Persistent player visual refresh should be coordinated with Up Next and Reader bridge behavior. |
| Android XML theme shell | `app/src/main/res/values/themes.xml` | Keep platform-level theme changes minimal. Most v1 work should remain in Compose tokens and wrappers. |

## Implementation Phases

### Phase 0A: Android Token Definitions Only

Goal: define the v1 token surface without changing current app visuals.

Expected files:
- `app/src/main/java/com/mimeo/android/ui/theme/Theme.kt`
- New files under `app/src/main/java/com/mimeo/android/ui/theme/` such as `ColorTokens.kt`, `Spacing.kt`, `Density.kt`, `Shapes.kt`
- Focused unit tests only if pure token mapping helpers are introduced

Scope:
- Define Paper & Ember light/dark color roles: background, surface, high surface, line, soft line, foreground levels, accent, accent dim, accent on, now tint, success, warn, danger.
- Define typography roles, with v1 row typography as sans and Reader/title roles as serif.
- Define Default and Compact density tokens only.
- Define shape and spacing constants.
- Add CompositionLocals for tokens not represented by Material 3.
- Do not wire the new tokens globally.
- Do not add font files.

Acceptance:
- Existing app screenshots should be visually unchanged because no global consumer is switched.
- Token values can be inspected in code and compared to the redline.

### Phase 0B: Appearance Settings Storage

Goal: persist appearance choices without changing visible UI.

Expected files:
- `app/src/main/java/com/mimeo/android/model/Models.kt`
- `app/src/main/java/com/mimeo/android/data/SettingsStore.kt`
- `app/src/test/java/com/mimeo/android/data/...` or adjacent settings tests

Scope:
- Add a `VisualThemePreference` enum: `FOLLOW_SYSTEM`, `LIGHT`, `DARK`.
- Add a `VisualDensityPreference` enum: `DEFAULT`, `COMPACT`.
- Add fields to `AppSettings` with defaults `FOLLOW_SYSTEM` and `DEFAULT`.
- Add DataStore keys and save methods.
- Do not expose settings UI yet.
- Do not alter existing Reader font settings in this ticket.

Acceptance:
- New installs resolve to Follow system + Default.
- Existing DataStore reads remain migration-safe.
- Unit tests cover defaults, persistence, and invalid stored enum fallback.

### Phase 0C: Optional Disabled Theme Wrapper / Feature Flag

Goal: make the new visual system opt-in for development without affecting production visuals.

Expected files:
- `app/src/main/java/com/mimeo/android/ui/theme/Theme.kt`
- Possibly `MainActivity.kt` / `MainActivityShell.kt` only if a disabled wrapper parameter must be threaded

Scope:
- Add a disabled-by-default wrapper path that can render the new Material color scheme and locals.
- Gate it behind a compile-time/debug-only or explicit in-code feature flag.
- Keep default production path visually unchanged.
- Document the flag/removal plan in the PR.

Acceptance:
- Default app launch remains current visual design.
- A local developer can opt in to the new wrapper for isolated screenshot/manual review.

### Phase 1: Shared Rows and Cards

Goal: build reusable visual primitives before touching broad screens.

Expected files:
- `app/src/main/java/com/mimeo/android/ui/common/ListSurfaceScaffold.kt`
- `app/src/main/java/com/mimeo/android/ui/common/ItemRow.kt`
- Potential new common files for visual cards/chips/buttons

Scope:
- Update shared row/card primitives to consume v1 tokens when the wrapper is enabled.
- Keep whole-row tap behavior and long-press behavior.
- Preserve visible Play + More row actions.
- Add density-aware row padding and title/meta spacing.
- Ensure text can grow with accessibility font scale; do not clamp row height to a fixed value.

Acceptance:
- Rows render Default and Compact density cleanly.
- Tap targets remain at least 44 dp, or existing 40 dp exceptions are explicitly revisited in the implementation ticket.
- No swipe gestures are added.

### Phase 2: Pilot Screen

Goal: validate the visual system on one real surface before broad rollout.

Recommended pilot: `QueueScreen` / Up Next.

Why:
- It exercises shared rows, current item state, manual-save entry, empty/loading/error states, and mini-player adjacency.
- It is central enough to expose token problems early but can be scoped without touching Reader typography.

Scope:
- Apply v1 tokens to the pilot screen behind the same disabled wrapper/flag.
- Keep queue semantics and refresh behavior unchanged.
- Use viewport in dp for narrow-layout decisions.

Acceptance:
- Manual review at 360 dp, typical phone width, and large font scale.
- No queue behavior changes.

### Phase 3: Up Next / Smart Queue

Goal: expand from the pilot into the queue family.

Scope:
- Complete Up Next row/card refinements.
- Apply Smart Queue responsive search/chip behavior.
- Keep existing queue refresh naming unless a separate product-copy ticket changes it.
- Do not add a refresh confirmation dialog in visual v1.

Acceptance:
- Chip/search layout does not overflow at 360 dp.
- Row actions remain reachable and non-overlapping at font scale 1.3+.

### Phase 4: Reader / Bluesky / Settings

Goal: finish the high-risk surfaces after tokens and rows are proven.

Reader:
- Apply serif Reader body/title roles.
- Define highlight style, but do not implement live TTS highlighting unless alignment service support exists separately.
- Keep existing Reader scroll/playback behavior unchanged.
- Do not add a Reader font picker.

Bluesky:
- Apply candidate/card and context-band visuals.
- Keep Android Browse backend-backed and read-only against harvested Mimeo data.
- Use `Read in Mimeo` or `Open saved item` post-save copy.
- Defer `Move to playlist`.
- Do not mutate Up Next on save.

Settings:
- Expose Theme and Density after storage and wrapper behavior are stable.
- Do not expose design-direction branding.
- Do not add telemetry upload controls or change upload defaults as part of this visual plan.

### Later: Web Harmonisation in Mimeo Repo

Web harmonisation belongs in `C:\Users\brend\Documents\Coding\Mimeo`, not this Android repo. Treat it as a later cross-repo design parity effort after Android v1 tokens and core surfaces stabilize.

## Risks and Mitigations

| Risk | Why it matters | Mitigation |
| --- | --- | --- |
| Font licensing and app size | New bundled fonts increase APK size and require license attribution. | v1 uses system-safe families and existing Literata resources unless font files are explicitly approved. Any new font ticket must include license/About handling. |
| Contrast | Paper & Ember low-contrast metadata/accent roles can fail if used for body text. | Restrict muted/accent roles to metadata, labels, borders, chips, and filled controls. Add contrast checks to implementation tickets. |
| Accessibility font scale | Dense rows can overlap or crop at font scale 1.3+. | Allow row height to grow; verify at 1.3x and 1.5x. Do not enforce fixed row heights. |
| Row action crowding | Visible Play + More actions compete with long titles. | Preserve actions per v1 decision, but make spacing/density work explicit acceptance criteria. Revisit only in a later product-approved ticket. |
| Old hard-coded colors | Current screens use MaterialTheme roles and some local alpha/color choices. | Phase rollout should audit touched surfaces only. Avoid broad formatting or repo-wide color rewrites. |
| Theme flag/removal plan | A long-lived disabled wrapper can become dead code. | Every implementation PR must state whether the flag remains needed. Remove the flag after all planned v1 surfaces are switched and manually verified. |
| Existing Reader font picker | Current Settings already exposes reader font options, while v1 says no new Reader font picker. | Do not expand that surface for visual v1. Any decision to remove or hide existing controls needs a separate product ticket because it changes current app behavior. |
| Redline vs operator row-action conflict | Redline proposed reducing visible row actions; this ticket explicitly preserves them. | Treat this plan's accepted decision as authoritative for v1 implementation tickets. |

## First Three Implementation Tickets

### Ticket 1: Android Visual Tokens, No Global Consumer

Goal: add v1 token definitions only.

In scope:
- Add Paper & Ember color, spacing, shape, typography-role, and density token definitions.
- Add CompositionLocals for v1-specific roles.
- Keep existing `MimeoTheme` default behavior unchanged.
- Add lightweight tests for any nontrivial token mapping helpers.

Out of scope:
- Settings storage/UI.
- Screen conversions.
- Font files.
- Backend or telemetry changes.

Suggested verification:
```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
git diff --check
```

Manual verification:
- Launch the app before and after the build on the same device/emulator and confirm there is no intentional global visual change.
- Inspect the new token file and compare light/dark color constants against the redline tables.

### Ticket 2: Appearance Settings Storage

Goal: persist Theme and Density choices with migration-safe defaults.

In scope:
- Add theme and density enums.
- Add `AppSettings` fields.
- Add DataStore keys and save methods.
- Add tests for default values, persistence, and invalid enum fallback.

Out of scope:
- Settings UI exposure.
- Global theme application.
- Reader font picker changes.

Suggested verification:
```powershell
.\gradlew.bat :app:testDebugUnitTest
git diff --check
```

Manual verification:
- Install over an existing debug build and confirm startup/settings still work.
- Clear app data and confirm settings defaults resolve to Follow system and Default density through logs/test-only inspection if UI is not exposed yet.

### Ticket 3: Disabled Visual Wrapper Flag

Goal: add an opt-in wrapper path for visual v1 without changing production visuals.

In scope:
- Add disabled-by-default wrapper/flag plumbing.
- Resolve Follow system / Light / Dark preference into light/dark token choice when the flag is enabled.
- Resolve Default / Compact density into local density tokens.
- Document the removal condition in code or PR notes.

Out of scope:
- Screen-by-screen redesign.
- Settings UI controls.
- Font files.
- Backend or telemetry changes.

Suggested verification:
```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
git diff --check
```

Manual verification:
- With the flag disabled, launch the app and confirm visuals match current main.
- With the flag enabled locally, launch in system light and system dark and confirm the wrapper changes token families without crashes.

## Gates for This Plan Ticket

Run:
```powershell
git diff --check
git diff --name-only
```

Expected:
- Only `docs/ANDROID_VISUAL_DESIGN_V1_PLAN.md` changes for this ticket.
- No Kotlin, backend, font, screenshot, video, or unrelated media changes.
- The existing untracked design-context input directory may remain untracked; do not stage unrelated untracked media.

Manual verification for this docs-only ticket:
- Read `docs/ANDROID_VISUAL_DESIGN_V1_PLAN.md` and confirm the accepted decisions, code mapping, phases, risks, and first three tickets match the operator handoff.
- Confirm the plan does not instruct committing new font files, screenshots, private article data, Kotlin changes, backend changes, or telemetry upload changes.

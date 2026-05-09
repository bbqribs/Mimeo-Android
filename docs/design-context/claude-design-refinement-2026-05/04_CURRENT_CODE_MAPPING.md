# Current Code Mapping

This mapping anchors design redlines to current implementation files.

## Theme and settings
- `app/src/main/java/com/mimeo/android/ui/theme/Theme.kt` (Material theme wiring)
- `app/src/main/java/com/mimeo/android/ui/theme/Type.kt` (typography tokens)
- `app/src/main/res/values/themes.xml` (Android theme resources)
- `app/src/main/java/com/mimeo/android/ui/settings/SettingsScreen.kt` (settings surface)
- `app/src/main/java/com/mimeo/android/ui/settings/ConnectivityDiagnosticsScreen.kt` (diagnostics UI)

## Row/list composables and scaffolds
- `app/src/main/java/com/mimeo/android/ui/common/ItemRow.kt` (primary row grammar)
- `app/src/main/java/com/mimeo/android/ui/common/ListSurfaceScaffold.kt` (list surface scaffold)
- `app/src/main/java/com/mimeo/android/ui/components/StatusBanner.kt` (inline status messaging)
- `app/src/main/java/com/mimeo/android/ui/components/RefreshActionButton.kt` (refresh action affordance)

## Core screen mappings
- Up Next: `app/src/main/java/com/mimeo/android/ui/queue/QueueScreen.kt`
- Smart Queue: `app/src/main/java/com/mimeo/android/ui/queue/SmartQueueScreen.kt`
- Inbox/Library: `app/src/main/java/com/mimeo/android/ui/library/LibraryItemsScreen.kt`
- Reader/Locus: `app/src/main/java/com/mimeo/android/ui/reader/ReaderScreen.kt`
- Bluesky candidate browser: `app/src/main/java/com/mimeo/android/ui/bluesky/BlueskyBrowseScreen.kt`
- Settings / privacy / diagnostics entry: `app/src/main/java/com/mimeo/android/ui/settings/SettingsScreen.kt`

## Relevant web files (obvious)
- No direct web template/style files are obvious in this Android repo.
- For web semantics reference only (no implementation change requested):
  - `C:/Users/brend/Documents/Coding/Mimeo/docs/planning/BLUESKY_BROWSE_SURFACES_V1_PLAN.md`
  - `C:/Users/brend/Documents/Coding/Mimeo/docs/planning/PRODUCT_MODEL_POST_REDESIGN.md`
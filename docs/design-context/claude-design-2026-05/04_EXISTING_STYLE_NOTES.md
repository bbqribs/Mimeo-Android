# Existing Style Notes (Evidence-Based)

This section summarizes obvious style/system patterns from current implementation. It intentionally avoids speculative inference.

## Theme assumptions
- Android theme is Compose Material 3 with a single explicit dark color scheme in `ui/theme/Theme.kt`.
- Primary accent is purple/lilac-leaning in the current palette.
- Background/surface are near-black/dark grays.
- No explicit light theme definition is present in that theme file.

## Material / Compose patterns
- App shell uses `ModalNavigationDrawer` plus `Scaffold`.
- Drawer uses `NavigationDrawerItem` patterns with selected-state tinting.
- Screens rely heavily on Material typography and `colorScheme` tokens.
- List-heavy surfaces use `LazyColumn` and card/list compositions.

## Navigation structure
- Drawer-level destinations include Inbox, Smart Queue, Up Next, Bluesky, plus Settings.
- Drawer also contains separate sections for manual Playlists and Smart Playlists.
- Settings has dedicated pathways for connection configuration and diagnostics.

## Row actions and interaction style
- Row-level explicit actions are common (icon buttons, overflow menus, batch actions on library surfaces).
- Locus/player include explicit favorite/archive and overflow actions.
- Save/add-to-playlist flows are action-driven rather than implicit.

## Density and layout rhythm
- Current UI trends toward medium-to-dense information rows, especially in queue/library surfaces.
- Section headers/dividers and compact metadata text are widely used.

## Accent usage (obvious only)
- Primary color is used for selected drawer items, highlighted labels, and key action emphasis.
- Error color is used for failure or warning states.
- Surface/on-surface variants carry secondary metadata and separators.

## Cross-platform note
- Web Bluesky surfaces currently exist as server-rendered pages and should harmonise conceptually with Android, without forcing identical component structure.

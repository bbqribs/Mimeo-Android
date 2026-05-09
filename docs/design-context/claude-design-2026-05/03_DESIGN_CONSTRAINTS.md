# Design Constraints

These constraints are mandatory for redesign concepts in this package.

- Do not invent backend features or assume new API capabilities.
- Do not change queue semantics.
- Do not change privacy policy semantics.
- Do not make operator/admin UX the primary visual style for end-user Android flows.
- Preserve explicit save/queue actions.
- Candidate browsing must not auto-save or auto-queue.
- Up Next is the playback target.
- Smart Queue is a playlist-like source.
- Manual playlists and smart playlists remain distinct concepts.
- Designs must be feasible in Jetpack Compose (Android).
- Web designs should remain feasible in simple server-rendered pages.
- Preserve current product semantics even when visual hierarchy changes.

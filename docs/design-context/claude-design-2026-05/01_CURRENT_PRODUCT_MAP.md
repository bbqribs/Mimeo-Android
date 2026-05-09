# Current Product Map (Implemented Surfaces)

This map reflects currently implemented surfaces in the Android app and relevant web Bluesky browse surfaces.

## 1. Inbox / Library (Android)
- Primary list surfaces are implemented as library-style list screens.
- Inbox is a first-class route.
- Adjacent library states include Favorites, Archive, and Bin views.
- Rows support explicit item actions (open, overflow actions, batch operations where enabled).

## 2. Reader / Locus (Android)
- Locus is the reading-plus-playback surface for an item.
- Supports multiple content modes (full text, full text with player, playback-focused).
- Includes explicit actions such as favorite/archive and overflow menu actions.

## 3. Up Next (Android)
- Up Next is the playback target queue surface.
- Session structure includes:
  - History
  - Earlier in queue
  - Now Playing
  - Up Next
- Navigation and item actions are explicit; user actions move or play items intentionally.

## 4. Smart Queue (Android)
- Smart Queue exists as its own destination and source concept.
- It is distinct from Up Next target playback state.

## 5. Manual Playlists (Android)
- Manual playlists are visible in drawer navigation.
- Playlist detail surface exists.
- Membership operations are explicit (add/remove via action flows).

## 6. Smart Playlists (Android)
- Smart playlists are implemented and visually separated from manual playlists.
- Smart playlist detail and smart playlist creation/edit flows are present.
- Smart playlists are treated as dynamic/filter-backed surfaces.

## 7. Bluesky Candidate Browser (Android)
- Dedicated Bluesky browse surface exists.
- Candidate browsing and save actions are explicit.
- Saved-state feedback is represented in-row.

## 8. Settings / Connection (Android)
- Settings includes connection mode handling (Local/LAN/Remote), base URL, token guidance, and connection testing.
- Connectivity diagnostics has a dedicated screen.

## 9. Settings / Bluesky (Android)
- Settings includes Bluesky-related affordances, including browse entry points and smart-playlist creation shortcuts.

## 10. Privacy & diagnostics (Android)
- Connectivity diagnostics is implemented.
- Playback/open diagnostics pathways are present in player/locus flows.
- Additional privacy text may vary by screen/state; preserve existing policy semantics.

## 11. Web Bluesky candidate browser / Saved from Bluesky (Web)
- Web includes Bluesky browsing/saved surfaces, including "Saved from Bluesky".
- Web Bluesky views are secondary to Android daily use, but should remain visually coherent with core Mimeo semantics.

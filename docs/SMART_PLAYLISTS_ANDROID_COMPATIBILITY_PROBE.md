# Smart Playlists Android Compatibility Probe

**Date:** 2026-04-27
**Status:** Investigation complete. Planning only — no production behavior changed.
**Scope:** Can the current Android app tolerate the proposed smart playlist backend
contract without crashes or wrong behavior?
**Source contract:** `C:\Users\brend\Documents\Coding\Mimeo\docs\planning\SMART_PLAYLISTS_V1_CONTRACT_PLAN.md`

---

## Files inspected

| File | Purpose |
|------|---------|
| `app/src/main/java/com/mimeo/android/model/Models.kt` | `PlaylistSummary` and all serializable models |
| `app/src/main/java/com/mimeo/android/data/ApiClient.kt` | `getPlaylists()` and JSON parser config |
| `app/src/main/java/com/mimeo/android/repository/PlaybackRepository.kt` | `listPlaylists()`, session/queue wiring |
| `app/src/main/java/com/mimeo/android/MimeoDrawerContent.kt` | Playlist list rendering in the nav drawer |
| `app/src/main/java/com/mimeo/android/ui/playlists/PlaylistDetailScreen.kt` | Full playlist detail + item rendering |
| `app/src/main/java/com/mimeo/android/MainActivityShell.kt` | Navigation/routing to playlist screens |

---

## 1. Current Android assumptions about playlist objects

`PlaylistSummary` is the only playlist model Android knows about (Models.kt:73–77):

```kotlin
@Serializable
data class PlaylistSummary(
    val id: Int,
    val name: String,
    val entries: List<PlaylistEntrySummary> = emptyList(),
)
```

- No `kind` field. No `filter_definition`. No `sort`. No `pin_count`.
- `entries` is the item membership list: `[{id, article_id, position}]`.
- Android expects ALL playlists to have an `entries` list and renders playlist detail exclusively from that list.
- There is no concept of a "derived" or "live-filter" playlist anywhere in the current Android code.

The `PlaylistDetailScreen` renders items by joining `playlist.entries` with locally-cached item data (`allItemsMap`). If `entries` is empty, the screen shows "No items in this playlist yet." — always.

---

## 2. JSON parser configuration — the key safety finding

Both `ApiClient` and `PlaybackRepository` configure their `Json` instances with:

```kotlin
Json { ignoreUnknownKeys = true }
```

**ApiClient.kt:166** — used for all API responses including `getPlaylists`.
**PlaybackRepository.kt:158** — used for internal session storage.

This means: **any new fields added to the backend `PlaylistOut` response (including `kind`, `filter_definition`, `sort`, `pin_count`) will be silently discarded during deserialization.** The app will not crash. The `PlaylistSummary` Kotlin object will be populated with only `id`, `name`, and `entries`; all other fields are dropped.

---

## 3. Compatibility risk table

| Backend change | Crash risk | Behavior risk | Notes |
|----------------|-----------|--------------|-------|
| Add `kind: "manual"` field to existing manual playlist responses | **None** | **None** | `ignoreUnknownKeys = true` silently drops it. Existing behavior unchanged. |
| Add smart playlists to `GET /playlists` response (with `kind: "smart"`, `entries: []`) | **None** (no crash) | **High — wrong UI** | Smart playlists render as empty manual playlists. See §3.1. |
| Add `filter_definition`, `sort`, `pin_count` to response | **None** | **None** | Silently dropped. Android never sees them. |
| Smart playlist ID stored in `AppSettings.selectedPlaylistId` | **None** | **High — functional failure** | Queue fetch calls `GET /playback/queue?playlist_id=...` which does not support smart playlists. Would return wrong results or an error. |
| Smart playlist ID stored in `AppSettings.defaultSavePlaylistId` | **None** | **High — functional failure** | Save-to-playlist flow calls `POST /playlists/{id}/items`. Smart playlists don't accept manual item entries. API call would fail or be meaningless. |

### 3.1 What "wrong UI" means if smart playlists appear in `GET /playlists`

When a smart playlist is returned in the drawer list:

- **Drawer rendering** (`MimeoDrawerContent.kt:84–116`): The smart playlist appears as a nav item with name and `(0)` count (since `entries` is empty). No badge. Tap navigates to `PlaylistDetailScreen`.

- **Detail screen** (`PlaylistDetailScreen.kt`): `localEntries` is populated from `playlist.entries` (empty). The screen immediately shows "No items in this playlist yet." — the filter output is never fetched because Android has no code to call `/smart-playlists/{id}/items`.

- **Overflow menu on detail screen**: Shows "Rename" and "Delete" — both call existing `/playlists/{id}` endpoints, which will work if the backend accepts those verbs on smart playlist rows (the shared `playlists` table makes this likely). Not harmful.

- **Drag handles**: Rendered on every detail row (there are none, so no handles appear). No harm.

- **"Smart Queue" drawer item** (`MimeoDrawerContent.kt:74–82`): A hardcoded "Smart Queue" item already exists in the drawer, selected when `selectedPlaylistId == null`. This is the legacy pre-redesign queue concept (which the product model marks retired). It is unrelated to smart playlists and is a pre-existing matter — not opened by this probe.

---

## 4. Can `kind` be added harmlessly to existing manual playlist objects?

**Yes — unconditionally safe.** Adding `kind: "manual"` to every existing `PlaylistOut` response changes nothing visible to Android. The field is dropped at deserialization. No code reads it. No crash possible.

---

## 5. Which screens need changes before mixed lists can be safely shown

The following changes must land in Android **before** smart playlists are included in `GET /playlists`:

| Change | Why required |
|--------|-------------|
| Add `kind: String? = null` (default `null`, treat null as `"manual"`) to `PlaylistSummary` | Allows Android to distinguish types after deserialization |
| Filter drawer to show only `kind != "smart"` in the "Playlists" section (or add a separate "Smart Playlists" section) | Prevents smart playlists from appearing as empty manual playlists with drag handles |
| Add a smart playlist detail screen (or a branch in `PlaylistDetailScreen`) that calls `GET /smart-playlists/{id}/items` instead of reading `entries` | Allows item results to actually display |
| Suppress drag-to-reorder and Rename affordances for `kind: "smart"` | Reorder is meaningless for smart playlists; rename may be allowed but should be labeled differently |
| Guard `selectedPlaylistId` and `defaultSavePlaylistId` settings: do not allow a smart playlist ID to be saved in either | Prevents functional failure when those IDs are used as queue/save targets |

None of these are large changes individually, but all must be in place before the backend enables smart playlists in the shared list.

---

## 6. Recommended backend rollout

**Verdict: safe only with phased rollout.**

| Phase | Action | Android prerequisite | Safe now? |
|-------|--------|---------------------|-----------|
| **Phase 1** | Add `kind` field to `PlaylistOut` for existing manual playlists (`kind: "manual"`) | None | **Yes — safe immediately** |
| **Phase 2** | Ship `/smart-playlists/*` backend endpoints (separate from `/playlists`) | None — Android doesn't call these yet | **Yes — safe immediately** |
| **Phase 3** | Android update: add `kind` field to `PlaylistSummary`, add smart playlist detail screen, guard `selectedPlaylistId`/`defaultSavePlaylistId` | **Required before Phase 4** | Needs Android dev work |
| **Phase 4** | Backend enables smart playlists in `GET /playlists` response | Phase 3 Android update shipped | **Only safe after Phase 3** |

**Specific answers to blocking questions B2 and B3 from the contract plan:**

- **B2 (crash on unknown `kind` enum values):** No crash risk. Android does not have a `kind` enum; the field is a plain ignored string from JSON. No `when` exhaustive enum match on `kind` exists anywhere in the current code. Safe.
- **B3 (default-include smart playlists in `GET /playlists`):** Do NOT default-include yet. Keep smart playlists out of the main list until Phase 3 Android update is shipped. Use `GET /smart-playlists` as the separate endpoint until Android can handle the mixed response.

---

## 7. No tests or probes run

All findings are based on static code inspection. The conclusions are deterministic:
- `ignoreUnknownKeys = true` is explicit in the source — no runtime test needed to confirm deserialization behavior.
- The rendering path through `PlaylistDetailScreen` using `playlist.entries` is straightforward — the empty-entries → empty-state branch is directly readable from the code.
- No ambiguous runtime behavior was found that would require a live API test.

---

## Exact next step

The backend contract plan (§4.1, blocking questions B2/B3) can now be resolved:

- **B2:** Resolved. No coordinated release cutover needed for the `kind` field. Backend can add `kind: "manual"` to existing responses at any time.
- **B3:** Resolved. Keep smart playlists **out of** `GET /playlists` default response until Android Phase 3 update ships. Ship `/smart-playlists` as a separate namespace from day 1.

**Android work needed before Phase 4 (mixed-list):**
1. Add `kind: String? = null` to `PlaylistSummary`.
2. Add smart playlist detail screen or `PlaylistDetailScreen` kind-branch.
3. Filter drawer to exclude smart playlists from the manual section.
4. Guard `selectedPlaylistId` / `defaultSavePlaylistId` against smart playlist IDs.

These are the scope inputs for the Android implementation ticket (contract plan §6 stage 6-C).

# Mimeo Android Design Specification

**Version:** 1.5
**Status:** Draft
**Last Updated:** 2026-03-05

**Version history:**
- 1.5: Locked cross-tab playback semantics (persistent-player toggle controls chrome visibility only) and recorded single Refresh action on Up Next (sync is implicit)
- 1.4: Added target player-screen banding notes and restated that Locus planning must remain no-gesture
- 1.3: Reality check on `main` after share-save + queue verification work; Locus expand/collapse is explicit-button only, and shared pinned PlayerBar remains separate architecture work
- 1.2: Recorded playback-speed location decision; speed stays in expanded Locus header, not the pinned PlayerBar
- 1.1: Added purple highlights, pure black dark mode, playback speed control, Locus metadata row
- 1.0: Initial draft

---

## 1. Overview

### 1.0 Reality Check on `main`

These notes reflect what is actually shipped on `main` after the redesign and share-save follow-up work:

- Locus expand/collapse is present on `main` and uses explicit `Expand` / `Collapse` buttons only.
- Locus tab entry is collapsed; direct item or resume entry opens expanded.
- Expanded header title truncation and speed/overflow action spacing are fixed on `main`.
- The TESTING.md Locus invariants checklist is the current merge gate for Locus/player UI changes.
- Cross-tab playback remains active even when leaving Locus; the `Persistent player across tabs` setting controls control-bar visibility on non-Locus tabs, not TTS lifetime.
- Up Next uses a single explicit Refresh action; queued progress sync remains part of that refresh path.
- A shared pinned PlayerBar architecture on `main` is still a separate future ticket, not implied by the playback-speed decision.
- Player-screen planning should assume explicit buttons only; do not reintroduce drag handles, swipe-to-expand, or bottom-sheet gestures in docs.

### 1.1 Vision

Transform Mimeo Android from a functional MVP to a polished, Instapaper-inspired read-later application that seamlessly supports both reading and listening (TTS). The app should feel minimal, typography-focused, and purposeful.

### 1.2 Design Principles

| Principle | Description |
|-----------|-------------|
| **Content first** | Typography and whitespace dominate; chrome recedes |
| **Balanced modes** | Reading and listening are equal, seamlessly switchable |
| **Predictable navigation** | Four tabs with clear purposes; user always knows where they are |
| **Resilient** | Works offline; communicates state clearly; graceful degradation |
| **Efficient** | Power user features (swipe, search, filters) accessible but not overwhelming |

### 1.3 Constraints

- **Tech stack:** Kotlin, Jetpack Compose, Material 3
- **Architecture:** MVVM with single Activity + Compose navigation
- **Existing backend:** REST API with progress tracking, playlists, device tokens
- **Must maintain:** Offline support, progress sync, TTS playback, session persistence

---

## 2. Navigation Structure

### 2.1 Bottom Navigation (4 Tabs)

```
┌─────────────────────────────────────────┐
│  ⏭ Up Next  📍 Locus  📁 Collections  ⚙️  │
└─────────────────────────────────────────┘
```

| Tab | Icon | Route | Purpose | Primary Actions |
|-----|------|-------|---------|-----------------|
| **Up Next** | ⏭ | `upNext` | Queue: items to read/listen | Search, filter, sort, swipe |
| **Locus** | 📍 | `locus/{itemId}` | Item viewer: read or listen | Mode toggle, font controls, fullscreen |
| **Collections** | 📁 | `collections` | Organize and browse | Folders, playlists, archive |
| **Settings** | ⚙️ | `settings` | Configure app | Server, tokens, preferences |

### 2.2 Tab Behaviors

- **Up Next:** Default home. Shows current playlist's items.
- **Locus:** Opens when user taps an item, or via mini panel. Shows current/last item.
- **Collections:** Hierarchical browser for playlists and special collections.
- **Settings:** Static configuration, no dynamic content.

### 2.3 Mini Control Panel (Locus Peek)

A compact collapsed/expanded panel that provides a window into the Locus without leaving the current tab.

**When shown:**
- TTS playback is active
- User has progress > 0% on an item
- User navigates away from Locus while an item is open
- User taps an item in Up Next (briefly, then navigates to Locus)

**When hidden:**
- User taps Collapse/Close
- User navigates to Locus tab (you're now IN the locus)
- Queue is empty

**States:**

```
┌─────────────────────────────────────────┐
│  Queue content...                       │
│                                         │
│  ┌───────────────────────────────────┐ │  ← Collapsed (64dp tall)
│  │ Article Title      ⏸ 45%  ✕      │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│  Collapse                 ⋮            │  ← Explicit action row
│  Article Title                         │ │  ← Expanded view
│  ┌─────────────────────────────┐      │ │
│  │ The quick brown fox...      │      │ │
│  └─────────────────────────────┘      │ │
│  ⏮  ⏸  ⏭                              │ │
│  ━━━━━━━━━━━━━━ 45%                    │ │
└─────────────────────────────────────────┘
```

**Completion behavior:** When article finishes, mini panel shows "Next up: [title]" card. Tap to continue or use the explicit close action.

---

## 3. Screen Specifications

### 3.1 Up Next (Queue)

**Purpose:** Display items in the current playlist/queue with search, filter, and sort capabilities.

**Layout:**

```
┌─────────────────────────────────────────┐
│  ▼ Smart Queue              🔍 ⋮       │  ← Playlist dropdown + search + menu
├─────────────────────────────────────────┤
│  ● All  ○ Unread  ○ Done  ○ Archived   │  ← Filter chips
│  ↕ Sort: Newest first                   │  ← Sort control (can be in menu)
├─────────────────────────────────────────┤
│  💾 Stored  3 items                     │  ← Offline indicator (when applicable)
├─────────────────────────────────────────┤
│  In Progress                            │  ← Section header (collapsible)
│  ┌───────────────────────────────────┐  │
│  │ Article Title                ████│  │  ← Progress bar
│  │ excerpt.text...              45% │  │  ← Metadata
│  │ site.com • 2 days ago   💾      │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ Another Article              ██  │  │
│  │ excerpt.text...              12% │  │
│  │ other.org • 5 days ago          │  │
│  └───────────────────────────────────┘  │
│                                         │
│  Done ▼                                 │  ← Section header (collapsible)
│  ┌───────────────────────────────────┐  │
│  │ Completed Article           ████│  │
│  │ excerpt.text...             100% │  │
│  │ blog.co • 1 week ago             │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

**Components:**
- **Playlist dropdown:** Top-left, shows current playlist. Tap to switch.
- **Search:** Top-right, expands to full-width search bar when tapped.
- **Filter chips:** Single-select (All, Unread, Done, Archived).
- **Sort menu:** Newest first, Oldest first, Progress (asc/desc), Title A-Z.
- **Queue items:** Title, excerpt, progress bar, metadata, offline badge.
- **Section headers:** Collapsible groupings (In Progress, Done).

**Item card specification:**
- Height: Variable, min 88dp
- Padding: 16dp horizontal, 12dp vertical
- Title: 18sp Medium, max 2 lines
- Excerpt: 14sp Regular, max 2 lines, opacity 70%
- Progress bar: 4dp tall, below excerpt, primary color
- Metadata: 12sp Regular, opacity 50%, shows site + date
- Offline badge: Top-right, 12sp chip with icon

**Swipe actions:**
- Swipe left (→): Archive (undo available)
- Swipe right (←): Favorite/unfavorite
- Long press: Multi-select mode

**Empty state:**
- Icon: Stylized article icon
- Text: "Your queue is empty"
- Action: "Add from web" button (opens browser/share flow)

**Loading state:**
- Shimmer rows matching item card shape
- Show 3-5 skeleton items
- Fade in real content when loaded

---

### 3.2 Locus (Item Viewer / Article Detail)

**Purpose:** Read and/or listen to a specific article. This screen merges the item viewer with article detail metadata, echoing the web app's `/items/{id}` view.

**Layout:**

```
┌─────────────────────────────────────────┐
│ ← Back  Article Title        ⋮         │  ← Nav + menu
├─────────────────────────────────────────┤
│  example.com • 45% • 2,341 words        │  ← Metadata row
├─────────────────────────────────────────┤
│  [⭐ Favorite]  [📦 Archive]  [✓ Done] │  ← Action buttons
├─────────────────────────────────────────┤
│                                         │
│  Article content here...                │
│  ┌─────────────────────────────────┐   │  ← Current chunk (TTS mode)
│  │ The quick brown fox jumps over  │   │    Highlighted in purple
│  │ the lazy dog.                   │   │
│  └─────────────────────────────────┘   │
│                                         │
│  The text continues here. More ipsum   │
│  dolor sit amet...                      │
│                                         │
├─────────────────────────────────────────┤
│  ✓  ⏮  ⏸  ⏭  ⏯    1.0×  🗖 Font Aa   │  ← Controls
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━        │  ← Progress scrubber
└─────────────────────────────────────────┘
```

**Metadata row (shows):**
- Domain/site name
- Progress percentage
- Word count
- Created date (optional, can be in menu)

**Action buttons:**
- Favorite/unfavorite (⭐)
- Archive/unarchive (📦)
- Mark done/Mark not done (✓)

**Note:** Does NOT show: strategy, opened, last opened (internal diagnostics)

**Reading mode:**
- Clean, typography-focused view
- Literata serif, 18sp, 1.6 line height
- Progress tracked by scroll position
- Bottom chrome stays visible (no fullscreen for now)

**Listening mode (TTS):**
- Current chunk highlighted with subtle purple background
- Playback controls echo existing app player controls
- Auto-scroll optional (user preference, persists)
- Progress scrubber shows position within text

**Target player screen layout (Instapaper-style bands):**
- **Top action bar:** move the current expanded-header actions into a conventional `TopAppBar`-style layout. Keep today’s actions initially, then rationalize later.
- **Reader body:** the article surface remains the main scrollable region. It should own the current spoken sentence/word highlight behavior as that work is refined beyond the existing chunk highlight.
- **Title marquee row:** add a narrow row just above the playback controls that retains `Back to queue` and shows the current title in a scrolling/marquee treatment when it overflows.
- **Bottom player controls bar:** `PlayerControlBar` is pinned directly above the 4-tab bottom nav and should never sit inside or float over the scrollable reader body.
- **No gesture dependency:** this target layout uses explicit controls only. No drag handle, swipe-to-expand, or nested bottom-sheet gesture behavior should be reintroduced.

**Playback speed location decision (v1.1):**
- Speed stays in the expanded Locus header action row
- The pinned PlayerBar remains reserved for transport and completion controls
- Collapsed Locus does not expose speed
- The previously proposed task to move speed into the pinned PlayerBar is cancelled/superseded by `decision-playback-speed-location.md`
- Any future shared pinned PlayerBar work on `main` must be tracked as a separate architecture ticket, not as playback-speed follow-up work

**Playback controls (echo existing PlayerControlBar):**

| Control | Icon | Action |
|---------|------|--------|
| Mark done | ✓ (check_circle) | Mark item as done |
| Previous item | ⏮ (skip_previous) | Jump to previous item in queue |
| Previous segment | ⏪ (fast_rewind) | Jump to previous TTS chunk |
| Play/Pause | ⏸/▶ (pause/play_arrow) | Toggle playback |
| Next segment | ⏩ (fast_forward) | Jump to next TTS chunk |
| Next item | ⏭ (skip_next) | Jump to next item in queue |

**Playback speed control (NEW):**
- Tap the expanded Locus header speed action to open the speed dialog
- Range: 0.5x to 5.0x
- Increment: 0.05 steps
- Default: 1.0x
- Persists across sessions (store in DataStore)
- Dialog shows current value, slider below
- Preset buttons: 0.75x, 1.0x, 1.25x, 1.5x, 2.0x

```
┌─────────────────────────────────────────┐
│  Playback Speed                  ✕     │
├─────────────────────────────────────────┤
│                                         │
│  1.0×                                   │
│                                         │
│  ━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━   │  ← Slider
│  0.5×                       5.0×       │
│                                         │
│  [0.75×] [1.0×] [1.25×] [1.5×] [2.0×] │  ← Presets
│                                         │
└─────────────────────────────────────────┘
```

**Font controls (tapping "Font Aa"):**
- Font family: Serif (Literata) / Sans (system)
- Font size: 14sp, 16sp, 18sp (default), 20sp, 22sp, 24sp
- Line height: Compact (1.4), Comfortable (1.6, default), Loose (1.8)
- Margin width: Narrow (16dp), Medium (32dp, default), Wide (48dp)

**Chunk highlighting:**
- In TTS mode, current chunk has subtle purple background (#9C27B0 at 12% opacity)
- Smooth scroll to keep current chunk in view when auto-scroll enabled

**Empty state (no content):**
- Icon: Document with question mark
- Text: "Content not available"
- Action: "Retry" button

**Offline state (content cached):**
- No special indication; cached content works normally
- Small "💾 Stored" indicator in menu if desired

**Offline state (content not cached):**
- Overlay: "📴 Content not available offline"
- Action: "Go online" button or "Back" button

---

### 3.3 Collections

**Purpose:** Browse and manage playlists, folders, and special collections.

**Layout:**

```
┌─────────────────────────────────────────┐
│  Collections                   + New    │  ← Header with create button
├─────────────────────────────────────────┤
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │  ← Separator (special vs user)
│  ⭐ Favorites              12 items     │  ← Special collection
│  📦 Archive                156 items    │  ← Special collection
├─────────────────────────────────────────┤
│  📁 To Read                 ▼            │  ← Folder (expanded)
│    • Longform      8 items              │  ← Playlist (tap to select)
│    • News          23 items             │
│    • Research      5 items              │
│  📁 Podcasts               ▶            │  ← Folder (collapsed)
│  📁 Listening                            │
│    • Queue         42 items             │
└─────────────────────────────────────────┘
```

**Behaviors:**
- Tap folder → Expand/collapse to show playlists
- Tap playlist → Switch Up Next to that playlist
- Long-press folder → Rename, delete (with confirmation)
- Long-press playlist → Rename, delete, move to folder
- Tap Favorites/Archive → Switch Up Next to filtered view
- Tap + New → Create new playlist or folder

**Create dialog:**
```
┌─────────────────────────────────────────┐
│  Create new collection                  │
├─────────────────────────────────────────┤
│  Name: [________________]               │
│                                         │
│  Type:                                  │
│  ○ Playlist                             │
│  ○ Folder                               │
│                                         │
│  [Cancel]              [Create]         │
└─────────────────────────────────────────┘
```

**Empty state:**
- Icon: Folder icon
- Text: "No collections yet"
- Action: "Create playlist" button

---

### 3.4 Settings

**Purpose:** Configure app behavior and connection.

**Layout:**

```
┌─────────────────────────────────────────┐
│  Settings                               │
├─────────────────────────────────────────┤
│  Server                                 │
│    Base URL: [https://...]             │
│    Status: ● Connected                  │
│                                         │
│  Authentication                          │
│    Device token: ••••a3f9        [Edit] │
│                                         │
│  Playback                               │
│    Default playback speed: 1.0×    [⏯] │  ← Opens speed slider
│    Auto-advance to next item     [ ]   │
│    Auto-scroll while listening   [✓]   │
│    Voice: [Google English TTS     ▼]   │
│                                         │
│  Reading                                │
│    Font: Literata (serif)         [▼]   │
│    Size: 18sp                     [-]   │
│    Line height: Comfortable       [▼]   │
│                                         │
│  About                                  │
│    Version: 1.2.3                       │
│    [View logs]                          │
└─────────────────────────────────────────┘
```

---

## 4. Design System

### 4.1 Typography

**Font families:**
- **Reading:** Literata (serif) - Regular, Italic, Bold, BoldItalic
- **UI:** System default sans-serif

**Type scale:**

| Use | Size | Weight | Line height | Letter spacing |
|-----|------|--------|-------------|----------------|
| Display | 24sp | Medium | 1.2 | 0 |
| Headline (titles) | 18sp | Medium | 1.3 | 0 |
| Body (UI) | 14sp | Regular | 1.5 | 0 |
| Caption (metadata) | 12sp | Regular | 1.4 | 0.25 |
| Reading (article) | 18sp | Regular | 1.6 | 0 |

**Reading mode variations:**
- Sizes: 14sp, 16sp, 18sp (default), 20sp, 22sp, 24sp
- Line heights: 1.4 (compact), 1.6 (comfortable), 1.8 (loose)

### 4.2 Spacing

8dp baseline grid:

| Token | Value | Usage |
|-------|-------|-------|
| `spacing-xs` | 4dp | Tight spacing between related elements |
| `spacing-sm` | 8dp | Icon padding, small gaps |
| `spacing-md` | 16dp | Default padding, list item spacing |
| `spacing-lg` | 24dp | Section gaps, screen margins |
| `spacing-xl` | 32dp | Major sections |
| `spacing-xxl` | 48dp | Top-level spacing |

**Application:**
- Screen margins: `spacing-lg` (phones), `spacing-xl` (tablets)
- List item padding: `spacing-md` vertical, `spacing-lg` horizontal
- Section gaps: `spacing-xl`
- Icon button padding: `spacing-sm`

### 4.3 Colors

**Brand color (purple):**
- Primary: #9C27B0 (Material Purple 500)
- Primary variant: #7B1FA2 (Purple 700)
- On-primary: #FFFFFF (white)

**Use primary/purple for:**
- Progress bars
- Chunk highlighting (TTS mode) - at 12% opacity
- Selected filter chips
- Active state indicators
- Key action buttons (use sparingly)

**Surface:**
- `surface`: #FFFBFE (light), #000000 (dark - pure black)
- `surfaceVariant`: Slightly tinted for cards
- `onSurface`: #1C1B1F (light), #E6E1E5 (dark)
- `onSurfaceVariant`: #49454F (light), #CAC4D0 (dark)

**Text:**
- Ensure WCAG AA minimum: 4.5:1 for body text
- Title: `onSurface` (high emphasis)
- Body: `onSurface` (high emphasis)
- Metadata: `onSurfaceVariant` (medium emphasis)

**Dark mode (pure black):**
- Background: #000000 (pure black)
- Surface: #0A0A0A (nearly black, subtle elevation)
- Surface variant: #141414
- Text: #E6E1E5

**Swipe action colors:**
- Archive (left swipe): #B00020 (red)
- Favorite (right swipe): #FFD600 (yellow)

### 4.4 Icons

**Material Icons (most common):**
- Navigation: `arrow_back`, `search`, `more_vert`
- Tabs: `play_arrow` (Up Next), `place` (Locus), `folder` (Collections), `settings`
- Actions: `archive`, `favorite`, `favorite_border`, `delete`, `edit`
- Media: `play_arrow`, `pause`, `skip_previous`, `skip_next`
- Offline: `wifi_off`, `storage` (or custom)
- Sections: `expand_more`, `expand_less`

**Custom icons (if needed):**
- "Stored" badge: Simple download/check icon
- Empty states: Can use Material or custom illustrations

---

## 5. Components

### 5.1 Queue Item Card

**Specification:**
- Container: Surface color, 16dp radius corners
- Height: Variable (min 88dp)
- Padding: 16dp horizontal, 12dp vertical
- Elevation: 0dp (flat), 2dp on press

**Content:**
- Title: 18sp Medium, max 2 lines, `onSurface`
- Excerpt: 14sp Regular, max 2 lines, `onSurface` 70% opacity
- Progress bar: 4dp tall, primary color, below excerpt
- Metadata: 12sp Regular, `onSurfaceVariant`
- Offline badge: Top-right, 12sp chip, `surfaceVariant` background

**States:**
- Normal: Default appearance
- Pressed: Elevation 2dp, background slightly darker
- Swipe left: Archive action revealed (red background)
- Swipe right: Favorite action revealed (yellow background)
- Disabled (offline, not cached): 50% opacity

---

### 5.2 Filter Chips

**Specification:**
- Height: 32dp
- Padding: 16dp horizontal, 0dp vertical
- Corner radius: 16dp (fully rounded)
- Border: 1dp on unselected

**States:**
- Unselected: `surfaceVariant` background, `onSurfaceVariant` text
- Selected: Primary background, `onPrimary` text
- Pressed: Slightly darker than selected

---

### 5.3 Mini Control Panel

**Collapsed state:**
- Height: 64dp
- Background: `surface` with top shadow
- Content: Title truncated, progress/status, explicit Expand button, overflow/close actions

**Expanded state:**
- Height: local expanded player surface within the Locus screen
- Top: explicit action row with Collapse plus overflow actions
- Content: Article text with current chunk highlighted (purple)
- Bottom: playback controls remain explicit-button driven; no drag gestures

**Playback controls (echo existing, add speed):**

| Button | Icon | Purpose |
|--------|------|---------|
| Mark done | ✓ check_circle | Mark current item complete |
| Previous item | ⏮ skip_previous | Jump to prior item in queue |
| Previous segment | ⏪ fast_rewind | Jump to prior TTS chunk |
| Play/Pause | ⏸/▶ pause/play_arrow | Toggle playback |
| Next segment | ⏩ fast_forward | Jump to next TTS chunk |
| Next item | ⏭ skip_next | Jump to next item in queue |

**Animations:**
- Collapse/expand: 300ms ease-out, always triggered by explicit buttons
- Fade in/out: 150ms

---

### 5.4 Swipe Actions

**Specification:**
- Swipe threshold: 80dp to reveal action
- Action revealed at 40% swipe
- Background color fills from edge
- Icon appears when action revealed
- Snap back on release if threshold not met

**Actions:**
- Left swipe: Archive (red #B00020)
- Right swipe: Favorite (yellow #FFD600)

**Undo:**
- Snackbar appears after swipe
- "Archived" with "Undo" button
- 5 second timeout

---

### 5.5 Search Bar

**Specification:**
- Height: 48dp
- Background: `surfaceVariant`
- Corner radius: 24dp (fully rounded)
- Leading icon: Search
- Trailing icon: Clear (when text present)

**Behavior:**
- Tap: Expands to full width
- Type: Filters queue in real-time
- Clear: Resets to full queue

---

### 5.6 Skeleton Loaders

**Specification:**
- Base color: `surfaceVariant`
- Shimmer color: `surfaceVariant` with 50% white overlay
- Animation: Horizontal sweep, 1000ms duration, infinite

**Shapes:**
- Queue item: Rectangle with placeholder shapes for title, excerpt, progress
- Reader: Rectangles approximating paragraph blocks
- Collections: Folder/playlist item shapes

---

## 6. States and Behaviors

### 6.1 Loading States

| Screen | Loading behavior |
|--------|------------------|
| Up Next | Skeleton rows (3-5), fade in content |
| Locus | Skeleton blocks, fade in content |
| Collections | Shimmer while fetching playlists |
| Settings | No loading (static content) |

### 6.2 Empty States

| Context | Icon | Message | Action |
|---------|------|---------|--------|
| Queue empty | Article | "Your queue is empty" | "Add from web" |
| No search results | Search | "No articles match '{query}'" | "Clear search" |
| No collections | Folder | "No collections yet" | "Create playlist" |
| No favorites | Favorite border | "No favorites yet" | None |

### 6.3 Error States

| Context | Icon | Message | Action |
|---------|------|---------|--------|
| Network error | Wifi off | "Couldn't connect" | "Retry" |
| Not found | Broken image | "Article not found" | "Go back" |
| Server error | Error | "Something went wrong" | "Retry" |

### 6.4 Offline Behavior

**Cached content:**
- Works normally
- "💾 Stored" badge in queue
- No special indication in Locus

**Uncached content:**
- Queue item: Dimmed with "📴 Offline" badge
- Locus: Overlay with "Content not available offline"
- Tap: Toast or inline message

**Sync behavior:**
- Progress changes queued while offline
- Syncs automatically when connection restored
- WorkManager for background sync

---

## 7. Interactions

### 7.1 Swipe Actions

- **Left swipe:** Archive with undo
- **Right swipe:** Favorite/unfavorite
- **Long press:** Multi-select mode

**Multi-select mode:**
- Checkbox appears on each item
- Bottom bar with actions: Archive, Delete, Move to playlist
- Select all / Deselect all buttons
- Back button exits multi-select

### 7.2 Pull-to-Refresh

- **Up Next:** Pull down triggers sync
- **Collections:** Pull down refreshes playlists
- **Locus:** Not applicable (content is static)

**Visual feedback:**
- Spinner during fetch
- "Synced" or "X updated" toast when complete

### 7.3 Transitions

**Queue → Locus:**
- Shared element: Article title
- Duration: 300ms
- Easing: Ease-out

**Mini panel expand/collapse:**
- Button-triggered transition between collapsed and expanded states
- Duration: 300ms
- Easing: Ease-out

**Tab transitions:**
- Fade: 150ms
- No shared elements

### 7.4 Gestures

| Gesture | Context | Action |
|---------|---------|--------|
| Tap | Item in queue | Open in Locus |
| Long press | Item in queue | Multi-select mode |
| Swipe left | Item in queue | Archive |
| Swipe right | Item in queue | Favorite |
| Pull down | Queue/Collections | Refresh |
| Tap | Speed button (⏯) | Open speed slider dialog |
| Tap | Font Aa button | Open typography controls |

---

## 8. Technical Implementation Notes

### 8.1 Files to Create

**Components:**
- `ui/components/SearchBar.kt`
- `ui/components/FilterChips.kt`
- `ui/components/QueueItemCard.kt`
- `ui/components/SwipeableQueueItem.kt`
- `ui/components/MiniControlPanel.kt`
- `ui/components/PlaybackSpeedDialog.kt` - NEW
- `ui/components/SkeletonLoader.kt`
- `ui/components/PlaylistDropdown.kt`

**Screens:**
- `ui/screens/UpNextScreen.kt` (rename from QueueScreen)
- `ui/screens/LocusScreen.kt` (rename from PlayerScreen)
- `ui/screens/CollectionsScreen.kt` (rename from PlaylistsScreen)

**Theme:**
- `ui/theme/Type.kt` - Type scale with Literata
- `ui/theme/Spacing.kt` - 8dp baseline grid

### 8.2 Files to Modify

**Screens:**
- Rename `QueueScreen.kt` → `UpNextScreen.kt`
- Rename `PlayerScreen.kt` → `LocusScreen.kt`
- Rename `PlaylistsScreen.kt` → `CollectionsScreen.kt`
- Update all imports and navigation references

**ViewModel:**
- `AppViewModel.kt` - Add:
  - Filter state (All/Unread/Done/Archived)
  - Sort state (Newest/Oldest/Progress/Title)
  - Search query state
  - Offline status per item

**Navigation:**
- `navigation/NavRoutes.kt` - Update route names
- `navigation/MimeoNavGraph.kt` - Update graph

**Theme:**
- `theme/Color.kt` - Define purple primary (#9C27B0), pure black dark mode
- `theme/Type.kt` - Add Literata font family
- `theme/Theme.kt` - Apply new typography and colors

**Resources:**
- Add `res/font/literata/` directory with:
  - `Literata-Regular.ttf`
  - `Literata-Italic.ttf`
  - `Literata-Bold.ttf`
  - `Literata-BoldItalic.ttf`

### 8.3 Architecture Considerations

**State management:**
- Keep MVVM pattern
- Use StateFlow for filter/sort/search state
- Offline status derived from `localText != null`

**Data persistence:**
- Playback speed setting: Use DataStore (key-value store)
- Key: `playback_speed` (float, default 1.0)
- Reading preferences: DataStore keys for font family, size, line height

**Data layer:**
- No schema changes required initially
- Future: Consider `isFavorite` flag if not present
- Future: Consider `folder` relationship for playlists

**Performance:**
- LazyColumn for queue (already implemented)
- Pagination for large queues (100+ items)
- Image loading: Coil or similar (if thumbnails added)

**Offline support:**
- Already implemented via Room
- Add `isOfflineAvailable` boolean to item state
- Queue UI dims items where `isOfflineAvailable = false && !isOnline`

**TTS integration:**
- Leverage existing `TtsController`
- Extend with playback speed support
- Speed changes apply to current and future playback

### 8.4 Dependencies

**Add if not present:**
```gradle
// Material Icons Extended
implementation "androidx.compose.material:material-icons-extended"

// Accompanist (for system UI controller)
implementation "com.google.accompanist:accompanist-systemuicontroller:0.32.0"

// Shimmer (if needed)
implementation "com.valentinilk.shimmer:shimmer:1.0.0"
```

---

## 9. Implementation Phases

### Phase 1: Foundation (Week 1-2)
1. Add Literata font resources
2. Create type scale and spacing systems
3. Define color palette with purple primary
4. Update dark mode to pure black
5. Rename tabs (Up Next, Locus, Collections)
6. Update navigation and routes

### Phase 2: Up Next (Week 3-4)
1. Playlist dropdown component
2. Filter chips and sort menu
3. Search bar
4. Swipe actions
5. Offline indicators (Stored/Offline badges)

### Phase 3: Mini Control Panel (Week 5)
1. Collapsed state component
2. Half-sheet expansion
3. Show/hide logic
4. Playback controls (echo existing PlayerControlBar)
5. Completion flow

### Phase 4: Locus Polish (Week 6)
1. Reading/listening mode toggle
2. Typography controls (Font Aa dialog)
3. Chunk highlighting for TTS (purple)
4. Metadata row (domain, progress, words)
5. Action buttons (Favorite, Archive, Done)
6. Offline state handling

### Phase 5: Playback Speed Control (Week 7)
1. Speed slider dialog (0.5x - 5.0x, 0.05 increments)
2. Preset buttons (0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
3. DataStore persistence for speed setting
4. Settings screen integration (default speed)
5. Mini panel speed button

### Phase 6: Collections (Week 8)
1. Hierarchical folder structure
2. Expand/collapse folders
3. Create dialog
4. Special collections (Favorites, Archive)

### Phase 7: Polish (Week 9)
1. Empty states
2. Loading states (skeletons)
3. Error states
4. Transitions and animations
5. Pull-to-refresh

---

## 10. Open Questions

1. **Progress sync:** Start with polling (every 30s) or add WebSocket later?
2. **Folder creation:** Manual only, or auto-generate by source/tag?
3. **Archive behavior:** Permanent, or can items be un-archived?
4. **Thumbnails:** Add article thumbnails to queue items? (increases complexity)
5. **Export:** Should users be able to export playlists/reading state?

---

## Appendix A: Instapaper Reference

**Key Instapaper traits to emulate:**
- Typography-first design
- Generous whitespace
- Minimal chrome
- Fast, reliable sync
- Excellent reading experience
- Simple but powerful organization

**Differences:**
- Instapaper is reading-only; Mimeo has TTS as first-class feature
- Instapaper has no Collections hierarchy; Mimeo has folders/playlists
- Instapaper's "folder" metaphor; Mimeo's "playlist" metaphor

---

## Appendix B: Literata Font

**Download:** https://fonts.google.com/specimen/Literata

**License:** Open Font License (OFL)

**Why Literata:**
- Designed specifically for comfortable reading
- Excellent legibility at small sizes
- Warm, humanist character
- Good italic and bold variants
- Google Fonts integration (easy to add)

**Files needed:**
- Literata-Regular.ttf (variable)
- Literata-Italic.ttf (variable)
- Literata-Bold.ttf (variable)
- Literata-BoldItalic.ttf (variable)

Or use variable font:
- Literata-VariableFont_wght.ttf (covers 200-900 weight)
- Literata-VariableFont_wght_ital.ttf (covers 200-900 weight + italic)

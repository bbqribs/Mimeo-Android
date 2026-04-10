# Android Reader Text Actions Spec

**Version:** 1.0
**Status:** Design-only; no implementation in this ticket
**Date:** 2026-04-10
**Scope:** Locus reader text — selected-text actions and whole-article text actions

---

## 1. Problem statement

Mimeo's Locus reader exposes article text but provides no first-class way to copy or share it:

- Text selection is currently live (via `SelectionContainer` in `ReaderBody.kt`) and produces the native Android floating toolbar with **Copy**, **Share**, and **Select All** — but these share/copy bare text only. No citation, no metadata.
- There is no whole-article copy or share action anywhere in the UI.
- It is not defined when or whether item metadata (title, source, URL) should accompany text that leaves the app.

This spec defines the v1 model for reader text actions on Android.

---

## 2. Two distinct surfaces

Reader text actions split into two cases with different scope and UI placement:

| Case | Trigger | Scope | Placement |
|------|---------|-------|-----------|
| Selected text | Long-press on reader body → native floating toolbar | User-highlighted span | Native Android floating toolbar |
| Whole article | Tap `⋮` in Locus top bar | Full article text | Locus overflow menu |

These are independent and can be implemented in either order.

---

## 3. Selected-text actions

### 3.1 Current state

`ReaderBody.kt` wraps text in `SelectionContainer`. This already enables the native Android text selection UI with:

- **Copy** — copies selection to clipboard (bare text)
- **Share** — sends selection via Android share sheet (bare text, `type = "text/plain"`)
- **Select All** — extends selection to all text in the container

This is the intended v1 behavior. No custom toolbar overrides are needed.

### 3.2 v1 decision: native behavior, no metadata appended

**Copy selected text:** bare text to clipboard. No citation appended.

**Share selected text:** bare text via share sheet. No citation appended.

Rationale:
- The native floating toolbar is provided by the OS; customizing it to inject citation requires replacing `LocalTextToolbar`, which is non-trivial and out of scope here.
- Users selecting a sentence or phrase to share expect just that text, not a citation block. The item-level "Share URL" action already handles attribution for the item as a whole.
- A citation option for selection share is appropriate as a future v2 step after the whole-article share pattern is established.

### 3.3 What is already implemented

Nothing new needs to be added to enable selected-text copy/share — `SelectionContainer` handles it. The existing `selectionClearArmed` / `clearActiveSelection()` machinery in `PlayerScreen.kt` already manages selection cleanup on back/tap.

This section is documentation-only; no follow-on implementation ticket is needed for v1 selected-text behavior.

---

## 4. Whole-article text actions

### 4.1 New Locus overflow items

Add two new items to the Locus `⋮` overflow menu, positioned after the existing URL actions:

**Overflow order (updated):**
1. Play this item *(when playback is not active on this item)*
2. Playlists…
3. Open in browser *(only if item has a URL)*
4. Share URL *(only if item has a URL)*
5. **Copy article text** *(always visible when article text is loaded)*
6. **Share article text** *(always visible when article text is loaded)*
7. Report problem *(only when signed in)*
8. Move to Bin (14 days)
9. Collapse player / Expand player

Both new items are hidden when `fullText` / `chunks` are empty (i.e., article text has not yet loaded). They are not URL-guarded.

### 4.2 Copy article text

**Label:** `Copy article text`

**Behavior:** copy the full article text to the clipboard as a plain string. No metadata appended.

```kotlin
clipboardManager.setText(AnnotatedString(articleText))
```

Where `articleText` is the same `effectiveFullText` computed in `ReaderBody.kt`:
```
chunks.joinToString(separator = "\n\n") { it.text }
```

**Snackbar feedback:** show `"Article text copied"` after the copy action dismisses the overflow.

Clipboard is a scratchpad; recipients expect raw content without annotation. Citation is omitted intentionally.

### 4.3 Share article text

**Label:** `Share article text`

**Behavior:** send full article text via Android share sheet, with a citation block appended.

```
Android share sheet:
  EXTRA_SUBJECT = item title (or empty if unavailable)
  EXTRA_TEXT    = articleText + "\n\n" + citationBlock
  type          = "text/plain"
  chooser title = "Share"
```

#### Citation block format

```
— "{title}"
{sourceLabel}
{url}
```

Rules:
- Include the `— "{title}"` line only when `title` is non-null and non-blank.
- Include the `{sourceLabel}` line only when `sourceLabel` is non-null, non-blank, and not a known generic fallback (same `genericLabels` set already used in `PlayerScreen.kt`).
- Include the `{url}` line only when `url` is non-blank.
- If none of the three lines apply, omit the citation block entirely (share bare article text).

#### Examples

Item with title, source, and URL:
```
The quick brown fox…

— "Lorem Ipsum Study"
The Atlantic
https://theatlantic.com/example
```

Item with title and URL, no meaningful source:
```
The quick brown fox…

— "Lorem Ipsum Study"
https://theatlantic.com/example
```

Item with no metadata (e.g., manual text paste):
```
The quick brown fox…
```

#### Why citation is included in Share but not Copy

Share sends content to another app or person; context is valuable. Copy goes to the clipboard for the user's own use; they expect raw text. This split matches common patterns (iOS Pages "Copy", "Share with attribution").

### 4.4 Metadata field resolution

Use the same field hierarchy already in use across the app:

| Metadata | Field |
|----------|-------|
| Article text | `chunks.joinToString("\n\n") { it.text }` (same as `effectiveFullText` in `ReaderBody.kt`) |
| Title | `displayPayload.title` (or `currentItem.title` from `PlaybackQueueItem`) |
| Source label | `displayPayload.sourceLabel` filtered through `genericLabels` exclusion in `PlayerScreen.kt:492` |
| URL | `displayPayload.url` (non-blank check identical to item-level URL guard) |

No new API fields required.

---

## 5. Visibility guards

| Action | Guard |
|--------|-------|
| Copy article text | Hidden when article text is empty/not yet loaded |
| Share article text | Hidden when article text is empty/not yet loaded |
| Copy selected text | Native behavior; always available when selection exists |
| Share selected text | Native behavior; always available when selection exists |

No URL guard applies to the article text actions (text may exist even when URL does not).

---

## 6. Labels (canonical strings)

| Action | Label |
|--------|-------|
| Copy article text | `"Copy article text"` |
| Share article text | `"Share article text"` |

These extend the canonical label table in `docs/ANDROID_ITEM_ACTIONS_SPEC.md`.

---

## 7. Out of scope for v1

- **Share selected text with citation** — requires `LocalTextToolbar` replacement; deferred to v2.
- **Copy article text with citation** — citation on clipboard adds unwanted noise; omitted by design.
- **User toggle for citation inclusion** — no settings entry needed in v1; citation is always included in Share and always excluded from Copy.
- **Export to file** (`.txt`, Markdown, etc.) — separate export feature, not part of this spec.
- **Clipboard history / secondary clip slots** — OS-level feature, out of scope.
- **Web/desktop reader** — out of scope for this spec; Android only.
- **Pending-save / failed-processing rows** — those items have no loaded text; the article-text actions are naturally hidden.
- **Search selection share** — search result highlighting is read-only; sharing from search mode uses the same native floating toolbar path as regular selection.

---

## 8. Implementation notes

These notes are for the follow-on implementation ticket, not part of the design decision.

- `displayPayload` / `currentItem` are already available in `PlayerScreen.kt`'s composition scope. The citation block can be assembled inline from existing state without a new ViewModel method.
- `clipboardManager` (`LocalClipboardManager.current`) is already used in `SettingsScreen.kt` and `ConnectivityDiagnosticsScreen.kt`; the pattern is established.
- `shareItemUrl` in `ItemUrlActions.kt` already demonstrates the share sheet pattern. A parallel `shareItemText(context, text, title)` helper should be extracted there for whole-article share.
- Article text is already computed as `effectiveFullText` inside `ReaderBody.kt`. The Locus overflow is in `PlayerScreen.kt`, which passes `fullText` and `chunks` down. Pass `effectiveFullText` up (or compute it in the ViewModel) so the overflow can use it without going through `ReaderBody`.

---

## 9. Recommended implementation order

1. **Extract `shareItemText` helper** in `ItemUrlActions.kt` — takes `(context, text, title?, sourceLabel?, url?)`, assembles citation block, fires share sheet.
2. **Expose article text to Locus overflow** — compute `effectiveFullText` in `PlayerViewModel` or pass it up through `PlayerScreen` composable state. Gate on non-empty.
3. **Add `Copy article text` to Locus overflow** — snackbar feedback `"Article text copied"`.
4. **Add `Share article text` to Locus overflow** — call `shareItemText` helper.
5. **No changes to selected-text behavior** — `SelectionContainer` already handles it; document this in the implementation PR.

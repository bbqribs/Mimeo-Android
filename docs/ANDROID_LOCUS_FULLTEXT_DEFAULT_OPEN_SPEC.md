# Android Locus Full-Text / Default-Open Spec

Status: Decision approved (spec-only, no implementation in this ticket)

Scope: Define how Locus should present full text, how Locus opens by default, and how this interacts with playback ownership, preview, and remembered state.

Non-goals:
- No backend/API contract changes.
- No playback architecture redesign.
- No implementation in this document/ticket.

## 1) Core model

Locus has two content presentation modes:

1. Full-text mode
- Continuous article text presentation intended for reading and scanning.
- Playback highlight is still shown when playback is active on the same item.

2. Playback-focused mode
- Existing playback-oriented presentation (current behavior baseline) optimized for active listening control and pointer-following.

These modes affect presentation, not playback ownership.

## 2) Default-open behavior

Decision:
- Locus should open in Full-text mode by default for manual item opens.

Manual item opens include:
- Tapping an item row from Up Next/Archive/Bin/Favourites.
- Entering Locus for a specific requested item.

Autoplay/engine-driven transitions do not count as manual opens.

## 3) Remembered-state behavior

Decision:
- Remember the last explicitly user-selected Locus presentation mode as a global Locus preference.
- If no preference exists (fresh install or reset), default to Full-text mode.

Explicit user action means a direct mode switch by the user in Locus UI. Passive navigation/autoplay must not rewrite the preference.

Precedence:
1. Explicit user mode switch in current session.
2. Remembered Locus mode preference.
3. Default = Full-text.

## 4) Manual-open vs autoplay interaction

Decision:
- Manual open honors the mode-precedence rules above.
- Autoplay continue-to-next should preserve the active Locus mode and must not force a mode flip.
- If user is previewing item B while item A is playing, autoplay progression of A must not replace previewed B content.

Playback ownership remains governed by existing playback/session policy:
- Regular play/pause controls act on playback-owner item.
- Explicit "play this item" affordances transfer playback ownership.

## 5) Playback highlight/current-position rules in Full-text mode

Decision:
- If displayed item == playback-owner item:
  - Show active playback highlight/range in Full-text mode.
  - Do not forcibly auto-jump on every range update.
  - Existing explicit jump-to-playback-pointer behavior remains available.
- If displayed item != playback-owner item (preview mode):
  - No foreign-item playback highlight should be rendered into previewed content.
  - Preview text remains stable unless user explicitly exits preview or switches item.

This keeps reading position stable while preserving quick access to live playback position.

## 6) Preview-vs-playing behavior

Decision:
- While A is playing, opening B in Locus shows B content/title (preview) without stopping A.
- Tapping Locus tab while previewing B returns to playback-owner item A per existing Locus-return policy setting:
  - Follow-now-playing = Off (default): return to A at last reader position for A.
  - Follow-now-playing = On: return to A at current playback pointer/highlight.

This spec does not alter existing Locus-return policy semantics; it aligns full-text behavior with those rules.

## 7) Migration and implementation implications (for later coding ticket)

Implementation ticket should:
- Add a bounded Locus mode state (Full-text vs Playback-focused) and persist explicit user choice.
- Ensure manual opens, autoplay transitions, and preview-state transitions use the precedence/order above.
- Keep playback-owner/item-preview separation strict to avoid title/body mismatch regressions.
- Add tests for:
  - default-open (no preference -> Full-text),
  - remembered-mode restore,
  - autoplay mode preservation,
  - preview stability while another item plays,
  - full-text highlight behavior for owner vs preview item.
- Add manual-smoke checklist for:
  - manual open,
  - autoplay next-item,
  - preview while playing,
  - Locus-tab return policy with both setting values.


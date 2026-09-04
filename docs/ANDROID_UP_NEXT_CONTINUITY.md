# Android Up Next continuity

Status: continuity implemented by `T-AND-UPNEXT-CONTINUITY-1`; canonical
History read/pointer adoption shipped in Android PR #492, and account-scoped
History management is implemented by `T-AND-UPNEXT-HISTORY-MANAGEMENT-1`.
Canonical semantic reorder is implemented by
`T-AND-UPNEXT-SEMANTIC-REORDER-1`.

## Authority and ownership

`AppViewModel` and `PlaybackRepository` remain the single Android Up Next owner.
The Room `now_playing` row remains the offline playback/session cache. The
`up_next_sync_metadata` row stores metadata only: canonical endpoint, hashed
account owner key, endpoint capability, last acknowledged positive version and
dirty state. Structure and pointer versions are retained as separate domains.
The row also holds at most one account-and-endpoint-scoped pending semantic
move plus a bounded sanitized diagnostic; it is not an item database.

The continuity scope is the signed-in account plus normalized server base URL.
Sign-out, account switch and endpoint switch clear the local session and its
continuity metadata before another owner can use them.

## Synchronization semantics

- First supported connection always reads `GET /up-next/session`.
- Server absent + local session publishes a create-only whole snapshot with
  `expected_version: null`.
- Server present wins over either empty or populated pre-adoption local state.
- Both absent records supported/clean state without creating a session.
- Existing non-reorder queue mutations and active-pointer edits remain
  immediately usable offline. Legitimate legacy whole-session mutations retain
  their existing dirty-snapshot path; a local clear uses `DELETE` with the exact
  observed positive structure version.
- Upcoming-row reorder is never represented by that dirty snapshot. Drag and
  TalkBack actions identify the row by item ID, convert the visible destination
  to its zero-based position in the full session, and call
  `POST /up-next/session/move` with the last acknowledged structure version.
  The complete queue is never sent to express a move, and the successful server
  projection supplies the resulting order.
- One offline semantic move may be staged only after a session identity and
  structure version have been observed. Its original precondition, item ID and
  full-session destination survive restart. A second move is disabled until the
  first is resolved. Reconnect keeps the intent queued until an authenticated
  `GET /up-next/session` succeeds against the same normalized endpoint and
  account. That preflight is route-usability evidence only: its projection is
  discarded, local order is not adopted, and the original intent and structure
  version are unchanged. Only then does Android mark the move in flight and
  publish it once against its original structure version.
- An in-flight or otherwise ambiguous move is refresh-only after restart or
  transport uncertainty: Android reads server truth before doing anything else,
  clears the pending intent after reconciliation, and never blindly resubmits.
- HTTP 409 `up_next_version_conflict` is decoded separately from auth and
  transport failures. Android atomically applies `current_session`, discards
  the stale structural edit, and tells the user that the newer session won.
- Versions advance locally only when a successful mutation response or
  authoritative read is atomically applied.
- A structure-domain 409 atomically applies `current_session`, discards the
  move without retry, retains only the sanitized conflict domain/version/
  correlation summary, and asks the user to repeat the move if still wanted.
  Pointer advances use their independent version and do not stale a move; a
  move result cannot regress a newer accepted or pending pointer transition.
- A missing move endpoint (404/405) is remembered separately as unsupported for
  that owner and endpoint, semantic reorder is disabled, and Android refreshes.
  It never falls back to whole-session replacement.
- The Up Next section persistently explains a blocking queued, submitting, or
  uncertain move beside its disabled controls. Queued intent asks for a
  reconnect; submitting waits for acknowledgement; an ambiguous outcome asks
  for refresh and is never resent. Sanitized conflict, rejected, unsupported,
  and legacy-dirty outcomes retain their distinct explanation for that
  account-and-endpoint scope; a later move clears the terminal explanation.
- Before any pre-cutover dirty snapshot is uploaded, Android compares it with
  server truth. Order-only legacy reorders are discarded and refreshed;
  mixed/ambiguous reorder snapshots fail closed. Clearly non-reorder dirty
  mutations retain the existing whole-session path.

Server session items provide membership/order/lifecycle projection. Android
continues using its existing item/session cache and item endpoints for display,
text, TTS cursors and progress. History is never serialized to Room's
`now_playing` payload or supplied by the client: Android reads
`GET /up-next/history?include_trashed=true` without a client limit and displays
the returned unique projection in server order, independently of whether a
session exists. The server-owned `history_display_limit` preference is read and
saved through `/up-next/preferences`; Android does not mirror it in DataStore.
History removal, snapshot-fenced clear, Bin/Restore refresh, and atomic queue
plus History clear are online-only and never enter the offline mutation queue.
Late responses are accepted only for the account/token/backend identity that
started the request.
Accepted pointer transitions are published through `/up-next/session/advance`;
offline transitions retain their original session/from-item/pointer
preconditions and are discarded on semantic conflict rather than replayed with
fresh server truth. `/playback/queue` remains Smart Queue and
`/playback/state` remains the playback-cursor contract.

## Lifecycle reconciliation

An authoritative refresh preserves archived membership, compacts references
that the backend removed, and supports a populated session with no active item.
If the locally playing item is no longer the authoritative active item,
playback is stopped and the media-service snapshot is cleared before exposing
the new pointer; the control bridge remains wired for later playback.
Restore/unarchive does not invent a former server position.

## Device verification

Use `scripts/android-device-verify.ps1` and
`docs/ANDROID_DEVICE_VERIFICATION_RUNBOOK.md` for repeatable sign-in, navigation,
no-active lifecycle assertions and sanitized evidence capture on physical
devices.

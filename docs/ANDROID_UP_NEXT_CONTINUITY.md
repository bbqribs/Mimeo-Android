# Android Up Next continuity

Status: implemented by `T-AND-UPNEXT-CONTINUITY-1` against backend contract
`c39b3ad1b04abdc770781919a84dddea28576667` / migration `c5e7a9b1d3f6`.

## Authority and ownership

`AppViewModel` and `PlaybackRepository` remain the single Android Up Next owner.
The Room `now_playing` row remains the offline playback/session cache. The
`up_next_sync_metadata` row stores metadata only: canonical endpoint, hashed
account owner key, endpoint capability, last acknowledged positive version and
dirty state. It is not an item database.

The continuity scope is the signed-in account plus normalized server base URL.
Sign-out, account switch and endpoint switch clear the local session and its
continuity metadata before another owner can use them.

## Synchronization semantics

- First supported connection always reads `GET /up-next/session`.
- Server absent + local session publishes a create-only whole snapshot with
  `expected_version: null`.
- Server present wins over either empty or populated pre-adoption local state.
- Both absent records supported/clean state without creating a session.
- Offline queue and active-pointer edits remain immediately usable and persist
  dirty with the last observed version.
- Reconnect publishes the complete dirty result with that exact version.
  A local clear uses `DELETE` with the exact observed positive version.
- HTTP 409 `up_next_version_conflict` is decoded separately from auth and
  transport failures. Android atomically applies `current_session`, discards
  the stale structural edit, and tells the user that the newer session won.
- Versions advance locally only when a successful mutation response or
  authoritative read is atomically applied.
- A missing endpoint (404/405) is remembered as unsupported for that owner and
  endpoint; the existing local-only session continues to work.

Server session items provide membership/order/lifecycle projection. Android
continues using its existing item/session cache and item endpoints for display,
text, TTS cursors and progress. History is never serialized to the continuity
API or Room's `now_playing` payload: it is a bounded, ViewModel-local record
for the signed-in account and canonical endpoint, and therefore disappears on
process recreation. Authoritative adoption/refresh/conflict recovery clears it
before publishing the server projection. `/playback/queue` remains Smart Queue
and `/playback/state` remains the playback-cursor contract.

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

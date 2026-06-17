# Android host-change runbook

Safe, tested path for pointing the Mimeo Android app at a different backend host
(backend migration, fresh-host install, or temporary test backend) and switching
back, without losing pending saves or playback state.

Audience: operator. This is manual retargeting only — no QR pairing, no onboarding
flow, no automatic migration. The app never stores AI provider keys or backend
secrets; it stores only a per-device session token (preferred) or a legacy shared
token, plus the base URL/mode.

## Concepts (what the app stores)

- **Connection mode** — `Local`, `LAN`, or `Remote`. Each mode keeps its own saved
  base URL (`local_base_url` / `lan_base_url` / `remote_base_url`), so switching
  modes does not overwrite the others.
- **Active base URL** — the URL currently in use (`base_url`), set from the selected
  mode on Save/Sign In.
- **Device token** — a per-device session token created by Sign In (preferred over
  the legacy shared `API_TOKEN`). Sign-out clears only the token; URL/mode stay.
- **Server identity** — a normalized record of the host you last signed in to. It is
  the guard that protects against silently mixing data from two different servers.

Base URL conventions (see `README.md` → "Base URL notes"):

- Emulator → host machine: `http://10.0.2.2:8000`
- Physical phone on same LAN: `http://<LAN-IP>:8000`
- Off-LAN (Tailscale), preferred: `https://<machine>.<tailnet>.ts.net`
- Raw Tailscale IP over HTTP: fallback-only when TLS is unavailable.

## Two ways to retarget — and how they differ

There are two paths, with intentionally different data behavior:

### A. Sign In to the new host (recommended for a real host change)

`Sign In` screen → enter the new server URL + credentials.

- If the new host's identity differs from the stored one, the app shows the
  **"Different Mimeo server"** dialog before doing anything.
  - **Clear and continue** wipes server-scoped local state (cached items, pending
    progress, **pending manual saves**, pending item actions, queue snapshots,
    playback segment indexes) and then signs in. Use this when the new host is a
    genuinely different library.
  - **Cancel** aborts; nothing changes.
- A fresh per-device token is minted for the new host. The old token is replaced.

Use this path when the new backend is a different dataset and you *want* a clean
slate.

### B. Settings → Save (non-destructive retarget)

`Settings` → **Account & Connection** → pick mode → edit the **Base URL** → **Save**
(or **Test**).

- This retargets the active URL **without** clearing pending saves, pending actions,
  cached items, or playback state. It does **not** show the identity dialog.
- The existing device token is kept. If that token is not valid on the new host,
  requests fail with `Unauthorized` and the app returns you to Sign In — pending
  saves remain queued.

Use this path for a **fresh-host migration rehearsal** where the same data/account is
expected to exist on the new host, or to flip between two URLs for the *same* server
(for example HTTPS `.ts.net` vs LAN IP) without disturbing local state.

> If you are unsure whether the new host shares your data, prefer path A and read the
> dialog. Path B never deletes anything on its own.

## What happens to each kind of state

| State | Settings → Save (B) | Sign In "Clear and continue" (A) |
| --- | --- | --- |
| Active base URL / mode | Updated | Updated |
| Per-mode saved URLs | Only the edited mode | Only the signed-in mode |
| Device token | Kept (may be invalid on new host) | Replaced with new host token |
| Server identity record | Unchanged | Re-stamped to new host |
| Pending manual saves | **Kept** | **Cleared** |
| Pending item actions | **Kept** | **Cleared** |
| Cached items / progress | **Kept** | **Cleared** |
| Queue snapshots / playback position | **Kept** | **Cleared** |

Pending-save retry behavior while a host is unreachable or mid-change:

- Network errors / timeouts / 5xx → save stays queued and **auto-retries** when
  connectivity returns.
- `Unauthorized` / missing token → save stays queued but needs you to fix auth
  (re-sign-in) before it will succeed; it is not auto-retried.

## Recommended host-change procedure

1. **Before changing**, drain or note pending work:
   - Open `Settings` → **Account & Connection** → **Test**. The result line reports
     pending-save count for the active queue.
   - If pending saves matter and the *current* host still works, let them flush first
     (reconnect to the working host until the count reaches zero).
2. **Confirm the new backend is reachable** (from the Mimeo backend repo, not Android):
   - Backend bound to `0.0.0.0:8000`, `GET /health` and `GET /debug/version` reachable.
   - For remote: both phone and host on the same Tailscale tailnet; prefer
     `https://<machine>.<tailnet>.ts.net`.
3. **Retarget**, choosing the path that matches intent:
   - Same data/account expected on the new host → **path B** (Settings → Save).
   - Different library / clean slate → **path A** (Sign In; read the dialog).
4. **Verify after retargeting** (see checklist below).
5. **Switch back** when done (see "Switching back").

## Verify after retargeting

1. `Settings` → **Account & Connection** → **Test** → expect a connected result with a
   `git_sha`. The "Last successful test" row records the host + time per mode.
2. Open **Up Next / Queue** and refresh — the queue should load from the new host.
3. Perform **one harmless read/list action** (open an item, or favorite then unfavorite
   it). Confirm it succeeds against the new host.
4. Confirm the auth state line under **Connection help** shows "signed in".
5. Confirm no unexpected data loss: if you used path B, pending saves should still be
   present; if you used path A and chose "Clear and continue", they are intentionally gone.

## Switching back

- If the previous host's URL is still saved under its mode, just reselect the mode (and
  URL) and **Save**, then **Test**.
- The "Last successful test" list offers a **Use this URL** action to re-apply a
  previously verified host URL quickly.
- If you switched accounts/servers with path A, you will Sign In again on return; the
  identity guard will prompt before clearing if the data sets differ.

## Safety notes / stop conditions

- The app holds **no provider keys and no backend secrets** — only a token and a URL.
  Nothing in this runbook stores credentials beyond the device session token.
- Path B never deletes pending saves or playback state. Path A only deletes them after
  you explicitly choose "Clear and continue".
- Release builds ship neutral host placeholders (`<LAN-IP>`, `<machine>.<tailnet>.ts.net`);
  developer presets exist only in debug builds. Retargeting in release is manual entry.
- This runbook does not change backend contracts. If a host change appears to require a
  backend/API change, stop and treat it as a backend ticket.

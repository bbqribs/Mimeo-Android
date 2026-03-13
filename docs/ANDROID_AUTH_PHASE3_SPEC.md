# Android Auth Phase 3 — User Sign-In Flow Spec

**Status**: Implementation-ready spec
**Date**: 2026-03-13
**Backend prerequisite**: `POST /auth/token` shipped (Mimeo PR #167, merged to master)
**Scope**: Android app — add username/password sign-in screen, store issued token, gate app entry

---

## 1. Current-state inventory

### 1.1 Connection modes

`ConnectionMode` enum (`model/Models.kt:185`): `LOCAL`, `LAN`, `REMOTE`.

Each mode has its own base URL stored in `SettingsStore`:
- `localBaseUrl` — default `http://10.0.2.2:8000` (emulator)
- `lanBaseUrl` — e.g. `http://192.168.x.y:8000`
- `remoteBaseUrl` — e.g. `http://100.x.y.z:8000` (Tailscale)

The active URL is written to `baseUrl` in DataStore when settings are saved.

### 1.2 Token storage

- **Single token** shared across all connection modes: `AppSettings.apiToken` (string, `model/Models.kt:198`).
- Stored in **DataStore Preferences** (`SettingsStore.kt:41`, key `"api_token"`).
- **Not encrypted.** DataStore Preferences uses app-private file storage but no additional encryption layer.
- Token used as `Authorization: Bearer <token>` — added per-request in `ApiClient.kt` (no OkHttp interceptor).

### 1.3 Settings UI — current token field

- `SettingsScreen.kt:283-290`: single `OutlinedTextField` labeled "API Token" with `PasswordVisualTransformation`.
- A "Test" button calls `getDebugVersion()` to validate the token works against the currently selected base URL.
- Below the token field: connection-test success snapshots per mode.

### 1.4 Startup / entry path

- `MainActivity.onCreate()` → `AppViewModel.init{}` → collects `settingsStore.settingsFlow`.
- `MimeoApp` composable → `NavHost` with `startDestination = ROUTE_UP_NEXT`.
- **No sign-in gate today.** If `apiToken` is blank, the app just shows the Up Next screen; API calls fail with 401 and surface error messages via snackbar. The user must navigate to Settings to paste a token.
- When `apiToken` transitions from blank to non-blank (observed in `AppViewModel.init{}`), `loadQueue()` is called automatically.

### 1.5 401 handling today

- `ApiClient.throwApiException()`: HTTP 401 → `ApiException(401, "Unauthorized-check token")`.
- Callers catch this and show snackbar messages. No automatic redirect or token clearing.

---

## 2. Target user flow

### 2.1 First launch (no saved token)

1. App starts. No `apiToken` in DataStore.
2. Instead of showing Up Next, the app shows the **Sign-In screen**.
3. Sign-In screen shows:
   - **Server URL field** — pre-filled with the base URL of the current connection mode (or blank if none configured). The user can edit this.
   - **Username field** — text, empty.
   - **Password field** — password-masked, empty.
   - **Sign In button** — disabled until username and password are non-empty.
   - Small "Advanced settings" text link at the bottom — navigates to the existing Settings screen for manual token/URL configuration.
4. User enters server URL, username, and password. Taps "Sign In".

### 2.2 On submit

1. App sends `POST <serverUrl>/auth/token` with JSON body:
   ```json
   {
     "username": "<entered username>",
     "password": "<entered password>",
     "device_name": "<Android device model, e.g. Pixel 9>"
   }
   ```
2. While waiting: button shows progress indicator, fields disabled.

### 2.3 On success (HTTP 200)

1. Backend returns:
   ```json
   {
     "token": "<raw-device-token>",
     "id": 7,
     "name": "Pixel 9 (auth)",
     "scope": "read_write",
     "created_at": "2026-03-13T01:30:00+00:00",
     "expires_at": null
   }
   ```
2. App stores `token` in DataStore as `apiToken` (same key used today).
3. App stores the server URL as the base URL for the auto-detected or user-selected connection mode (see §3).
4. App navigates to Up Next. `AppViewModel` detects `apiToken` changed from blank to non-blank, triggers `loadQueue()`.
5. **Username and password are never persisted.** Only the returned `token` is stored.

### 2.4 On invalid credentials (HTTP 401)

1. Backend returns `{"detail": "Invalid username or password"}`.
2. App shows an inline error below the form: "Invalid username or password".
3. Fields remain editable. No navigation change.

### 2.5 On rate limit (HTTP 429)

1. Backend returns `{"detail": "Too many login attempts. Please wait 5 minutes before trying again."}`.
2. App shows the detail text as the inline error.

### 2.6 On connectivity failure (network error, DNS, timeout)

1. App shows inline error: "Could not reach server. Check the URL and your network connection."
2. Fields remain editable.

---

## 3. Coexistence with current settings

### 3.1 Sign-In screen and connection modes

The Sign-In screen shows a single "Server URL" field. It does not expose the Local/LAN/Remote mode picker. The URL the user types determines which mode's base URL gets updated:

- If the Sign-In screen is shown because no token exists at all, the URL entered is saved as the base URL for whichever `ConnectionMode` best matches it (heuristic: `10.0.2.2` → LOCAL, `192.168.x.x` or `10.x.x.x` non-CGNAT → LAN, `100.64-127.x.x` → REMOTE, other → LAN). That mode becomes the active `connectionMode`.
- If the Sign-In screen is shown because the user got logged out (token cleared), the URL field pre-fills with the previously active `baseUrl`. The mode stays as-is.

### 3.2 Settings screen — what stays

The existing Settings screen remains fully intact:

- Connection mode chips (Local / LAN / Remote)
- Per-mode base URL fields
- "API Token" field — now labeled **"Device token (advanced)"**
- Save / Test / Diagnostics buttons

This is the "Advanced settings" escape hatch referenced from the sign-in screen. Operators and developers can still paste a raw device token here, bypassing sign-in entirely.

### 3.3 Precedence

- If the user signs in via `/auth/token`, the returned token goes into the same `apiToken` DataStore key. The Settings screen reflects it (masked).
- If the user pastes a raw token in Settings, that works exactly as today. No sign-in screen involved.
- Clearing the token field in Settings and saving → `apiToken` becomes blank → next navigation or app restart shows Sign-In.

---

## 4. Storage and session behavior

### 4.1 What is stored

| Key | Value | Where |
|---|---|---|
| `api_token` | Raw device token string | DataStore Preferences (existing key) |
| `base_url` | Active base URL | DataStore Preferences (existing key) |
| `connection_mode` | Active connection mode | DataStore Preferences (existing key) |
| `{local,lan,remote}_base_url` | Per-mode base URLs | DataStore Preferences (existing keys) |

**Not stored**: username, password, token ID, token name, scope, created_at, expires_at. Only the raw `token` string matters for ongoing API use.

### 4.2 Token is NOT mode-specific

The current architecture uses a single `apiToken` across all connection modes. Phase 3 preserves this. Rationale:
- The same backend instance is reachable via different network paths (LAN IP, Tailscale IP, emulator loopback). The device token is valid regardless of which path is used.
- Per-mode tokens would add complexity for a personal-use tool with no clear benefit.

### 4.3 Mode switching

When the user switches connection modes in Settings (e.g. LAN → Remote), the `apiToken` stays. The app just changes which base URL it uses. This is the existing behavior and does not change.

### 4.4 Future: EncryptedSharedPreferences

The spec deliberately keeps the existing DataStore Preferences storage. Migrating to EncryptedSharedPreferences is a separate hardening ticket and should not block Phase 3 sign-in. The Android app sandbox already protects DataStore files from other apps.

---

## 5. Stale token / 401 behavior

### 5.1 When to detect

Any API call that returns HTTP 401 is a candidate for stale-token handling. This includes:
- `loadQueue()` — Up Next data load
- `getDebugVersion()` — connection test
- All item/progress/playlist/playback calls

### 5.2 Behavior on 401

1. **Clear the token**: Set `apiToken` to `""` in DataStore.
2. **Navigate to Sign-In screen**: Post a navigation event to `ROUTE_SIGN_IN`.
3. **Show message**: Snackbar or inline text on the Sign-In screen: "Session expired. Please sign in again."

### 5.3 Bounded scope for Phase 3

- Only the **first 401 per app session** triggers the clear-and-redirect. Subsequent API calls that race in before navigation completes should not each trigger their own redirect.
- 403 (insufficient scope) is NOT treated as stale-token. It surfaces as a normal error.
- Background workers (`ProgressSyncWorker`) that get 401 should **not** trigger navigation. They should log the failure and stop retrying until the next app foreground.

### 5.4 Re-sign-in after token cleared

The Sign-In screen pre-fills the server URL from the last active `baseUrl`. The user enters credentials again. A new token is issued and stored, replacing the cleared one.

---

## 6. Screen / state / navigation implications

### 6.1 New route

Add `ROUTE_SIGN_IN = "signIn"` to the route constants in `MainActivity.kt` (~line 145).

### 6.2 New composable

`ui/signin/SignInScreen.kt` — new file. Contains:
- Server URL text field
- Username text field
- Password text field (masked)
- Sign In button with loading state
- Error text area
- "Advanced settings" link

### 6.3 New API method

`ApiClient.kt` — add:
```
suspend fun postAuthToken(baseUrl: String, username: String, password: String, deviceName: String): AuthTokenResponse
```

New response model in `Models.kt`:
```
@Serializable
data class AuthTokenResponse(
    val token: String,
    val id: Int,
    val name: String,
    val scope: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)
```

### 6.4 ViewModel additions

`AppViewModel` (in `MainActivity.kt`) — add:
- `signIn(serverUrl, username, password)` function
- `_signInState: MutableStateFlow<SignInState>` (Idle / Loading / Error(message) / Success)
- 401-handler function: `handleAuthFailure()` → clears token, posts ROUTE_SIGN_IN navigation

### 6.5 Navigation changes

In `MimeoApp` composable (`MainActivity.kt:~2059`):
- Add `composable(ROUTE_SIGN_IN) { SignInScreen(...) }` to NavHost.
- **Startup gate**: Before the NavHost, check `settings.value.apiToken`. If blank, set `startDestination = ROUTE_SIGN_IN` instead of `ROUTE_UP_NEXT`.
- On sign-in success: `nav.navigate(ROUTE_UP_NEXT) { popUpTo(ROUTE_SIGN_IN) { inclusive = true } }` — removes sign-in from the back stack.

### 6.6 Files likely to change

| File | Change |
|---|---|
| `ui/signin/SignInScreen.kt` | **New** — sign-in screen composable |
| `model/Models.kt` | Add `AuthTokenResponse` data class |
| `data/ApiClient.kt` | Add `postAuthToken()` method |
| `data/SettingsStore.kt` | Add `saveTokenOnly(token: String)` convenience (or reuse `save()`) |
| `MainActivity.kt` | Add ROUTE_SIGN_IN, sign-in ViewModel state, 401 handler, startup gate |
| `ui/settings/SettingsScreen.kt` | Rename "API Token" label to "Device token (advanced)" |

---

## 7. Open decisions for Codex

1. **Device name string**: Use `android.os.Build.MODEL` (e.g. "Pixel 9") as the `device_name` sent to `POST /auth/token`? Or include `Build.MANUFACTURER` too (e.g. "Google Pixel 9")? Either is fine — pick one and be consistent.

2. **Sign-In screen visual style**: Use the existing Material 3 theme (dark/purple Mimeo palette). No new design tokens needed. But Codex should decide whether the Sign-In screen uses a centered card layout or a simple top-aligned form. Either works — keep it simple.

3. **Connection mode heuristic precision**: The spec suggests inferring connection mode from the URL entered on the sign-in screen (§3.1). If Codex finds this heuristic too fragile, it is acceptable to always save the URL as the LAN base URL and set `connectionMode = LAN` as a safe default. The user can refine in Settings.

---

## 8. Recommended implementation slices

### Slice 1: Sign-In screen + submit + token save (smallest first slice)

- Add `AuthTokenResponse` to `Models.kt`.
- Add `postAuthToken()` to `ApiClient.kt`.
- Create `SignInScreen.kt` with URL / username / password fields + submit button.
- Add `signIn()` to `AppViewModel` — calls `postAuthToken`, on success writes token + base URL to DataStore.
- Add `ROUTE_SIGN_IN` to NavHost.
- **Startup gate**: if `apiToken` is blank, start at `ROUTE_SIGN_IN`.
- On success: navigate to `ROUTE_UP_NEXT`, pop sign-in from back stack.
- Error states: show inline error for 401, 429, and network failures.
- "Advanced settings" link: `nav.navigate(ROUTE_SETTINGS)`.

**Gate**: Fresh install → sign-in screen → enter credentials → token saved → Up Next loads.

### Slice 2: Stale token / 401 → re-sign-in

- Add `handleAuthFailure()` to `AppViewModel`: clears `apiToken`, posts `ROUTE_SIGN_IN`.
- Wire into the primary API call paths (`loadQueue`, `testConnection`, and queue/player data loads).
- Guard against multiple concurrent 401 redirects (use a flag or `compareAndSet`).
- Background workers: log 401 but do not trigger navigation.
- Sign-In screen shows "Session expired" message when reached via 401.

**Gate**: Revoke the token on the backend → next app API call → sign-in screen with "Session expired".

### Slice 3: Settings label cleanup

- Rename "API Token" to "Device token (advanced)" in `SettingsScreen.kt`.
- Update `connectionModeTokenAuthHelp()` hint text to mention that sign-in is the primary path and manual token entry is for operators/debugging.

**Gate**: Settings screen shows updated labels; existing manual-token flow still works.

### Slice 4 (optional, can defer): Connection mode inference from sign-in URL

- Add URL → ConnectionMode heuristic (§3.1) to save the sign-in URL into the correct mode slot.
- If deferred, default to saving as LAN base URL.

---

## 9. Backend endpoint reference

### POST /auth/token

**URL**: `<baseUrl>/auth/token`
**Method**: POST
**Auth**: None (this IS the auth step)
**Content-Type**: `application/json`

**Request body**:
```json
{
  "username": "alice",
  "password": "correct-horse-battery-staple",
  "device_name": "Pixel 9"
}
```
`device_name` is optional (defaults to `"app"` on the backend).

**Success (200)**:
```json
{
  "token": "dGhpcyBpcyBhIHRlc3QgdG9rZW4...",
  "id": 7,
  "name": "Pixel 9 (auth)",
  "scope": "read_write",
  "created_at": "2026-03-13T01:30:00+00:00",
  "expires_at": null
}
```

**Invalid credentials (401)**: `{"detail": "Invalid username or password"}`
**Rate limited (429)**: `{"detail": "Too many login attempts. Please wait 5 minutes before trying again."}`
**Missing field (400)**: `{"detail": "Field 'username' is required"}`

Rate limit: 10 attempts per 5 minutes per IP (shared with browser `/login`).

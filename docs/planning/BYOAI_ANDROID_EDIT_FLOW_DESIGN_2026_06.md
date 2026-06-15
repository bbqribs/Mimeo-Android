# BYOAI-A2 — Android AI Provider Edit Flow Design

**Status:** Design / spec only — no implementation in this ticket.
**Date:** 2026-06
**Repo:** Mimeo-Android
**Author lane:** Claude (Android primary implementer)
**Predecessor:** BYOAI-A1 (read-only AI provider status in the AI Summaries settings spoke)

> This document is a decision and specification record. It does **not** add
> provider-editing code. Implementation is deferred and gated on operator
> approval plus the BACKEND FOLLOW-UP items in §8.

---

## 1. Executive judgement

**Recommendation: keep Android status-only for v1. Do not ship a provider edit
flow yet.**

The decisive reason is not UX — it is the backend contract. The provider
**write** endpoints (`POST/PUT /config/ai-provider`, `POST
/config/ai-provider/test`, `DELETE /config/ai-provider`) are all gated on
`require_legacy_api_token`. They accept only the **legacy shared `API_TOKEN`**
(Bearer header or the browser session cookie holding that same token). A
**per-device token** — the credential Mimeo Android is supposed to prefer (see
`CLAUDE.md` → Auth) — is rejected by these endpoints regardless of its scope.

That produces a hard fork:

- To edit providers from Android today, the app would have to **hold and send
  the legacy shared `API_TOKEN`**. That contradicts the per-device-token posture
  and concentrates an admin-equivalent secret on a mobile device. Not acceptable.
- The clean alternative requires **backend changes** (an operator-capable
  device-token path for the write endpoints, plus a capability flag Android can
  read). Those do not exist yet, so an edit flow cannot be both safe *and*
  buildable in this iteration.

Separately, there is **no operator/admin capability signal** in any
Android-reachable response. Android cannot today distinguish an operator session
from an ordinary read/read-write session without trial-and-error against a
privileged endpoint — which is exactly what we must not do.

Conclusion: Android stays status-only. The edit flow is specified here so it can
be implemented later as a discrete Android PR **after** the backend exposes (a) a
non-legacy operator write path and (b) an operator-capability flag.

Nothing in the recommended posture requires Android to store keys locally, call
providers directly, edit prompts, do OAuth, or touch billing. All of those remain
explicitly out of scope (see §11).

---

## 2. Proposed v1 Android posture

**v1 = status-only, exactly as shipped in BYOAI-A1, with one copy refinement.**

- The AI Summaries settings spoke continues to render the display-safe projection
  from `GET /summary/capabilities` (`SummaryCapabilitiesOut`): enabled state,
  coarse status, provider/model display names, daily limit, modes, disclaimer.
- The existing **"Configure on web"** affordance remains the only call to action
  for `Unconfigured` / `ProviderUnavailable`. It is copy only; it renders no key
  form and no provider editor (see `AiSummariesSettings.kt`).
- Optional, low-risk refinement (not required): when/if the richer safe-status
  endpoint (`GET /config/ai-provider`) is wired into Android, surface read-only
  diagnostic lines (last test result, key-present indicator) **without** any edit
  controls. This is still status-only and can ship independently of the edit flow.

**The edit flow below is the v2 target**, contingent on §8 and §12.

---

## 3. Permission model

### 3.1 What exists today

| Credential | Read safe status (`GET /config/ai-provider`) | Write/test/delete provider |
| --- | --- | --- |
| Per-device token, `read` scope | ✅ allowed (`require_token_scope(read)`) | ❌ rejected |
| Per-device token, `read_write` scope | ✅ allowed | ❌ rejected (writes require legacy token, not scope) |
| Legacy shared `API_TOKEN` | ✅ allowed | ✅ allowed (`require_legacy_api_token`) |

`enforce_local_origin_for_cookie_post` adds a browser-CSRF origin check, but it is
**bypassed when an `Authorization` header is present** (it only protects
cookie-backed browser sessions). So origin enforcement is not the gate for an
API client — the legacy-token requirement is.

### 3.2 What the edit flow requires

The edit UI must be shown **only** to a session that can actually perform writes,
and that determination must come from the backend, not from probing:

1. **Operator/admin-capable session → show edit UI.**
2. **Ordinary read / read-write session → status-only, no edit affordance.**

Because no Android-reachable response currently carries an operator-capability
flag, **requirement (1) is unmet today**. The edit flow MUST gate its entire UI on
a backend-provided boolean (proposed `can_edit` / `operator: true` — see §8,
BACKEND FOLLOW-UP B2). Until that flag exists, Android renders status-only and
never attempts a write.

**Rule:** Android must never infer operator capability by issuing a write and
catching a 401/403. No speculative privileged calls.

---

## 4. Screen flow

**Placement decision: a dedicated "AI Provider" screen, reached from the AI
Summaries settings spoke — not inline in the spoke, and not a general settings
row.**

Rationale:
- The AI Summaries spoke is a *consumer* surface (how summaries behave for this
  user). Provider configuration is an *operator* surface (server-wide
  credentials). Mixing a write-capable key form into the consumer spoke raises the
  blast radius of every future change to that screen.
- A separate screen lets the whole route be gated behind the operator-capability
  flag and lets the key-handling rules (§6) be enforced in one contained
  ViewModel/Composable with no shared state.

Navigation:

```
Settings
└─ AI Summaries (spoke, status-only, always visible)
   └─ [operator only] "Manage AI provider"  ──▶  AI Provider screen (edit flow)
```

- The "Manage AI provider" entry point is rendered **only** when the
  operator-capability flag is true. Ordinary sessions never see it.
- The AI Provider screen is the only place edit controls live. It is reached only
  through that gated entry point; there is no deep link that bypasses the gate.

Edit-screen sections (top to bottom):

1. **Safe status** (read-only): provider, model, base URL, enabled, key present
   (ending `••1234` from `key_last4`), last test status + timestamp, source
   (`database` / `environment` / `none`).
2. **Configuration form**: provider, model, base URL (conditional), API key
   (write-only), enabled toggle.
3. **Actions**: Save, Test, Delete/Clear.
4. **Inline message area** for state copy (§7).

---

## 5. Field-level design

Mirrors the backend `AiProviderConfigIn` / `AiProviderConfigStatusOut` contract so
Android adds no new semantics.

| Field | Control | Source of truth | Notes |
| --- | --- | --- | --- |
| Provider | Dropdown | enum: `anthropic`, `openai`, `deepseek`, `gemini`, `openai_compatible`, `local` | Labels match the backend operator page. |
| Model | Text | `model` (1–128 chars) | Prefill backend `default_model_for_provider` when empty; user-editable. |
| Base URL | Text (conditional) | `base_url` (≤255, must start `http://`/`https://`) | **Shown only for `openai_compatible` and `local`.** Hidden/sent `null` otherwise. Operator decision required on whether to allow at all on Android (§12). |
| API key | Password text, **write-only** | `api_key` (≤4096) | Never prefilled. Placeholder shows `key_last4` ("Stored key ending 1234; paste a new key to rotate"). Empty on save = keep existing key. |
| Enabled | Toggle | `enabled` | Default true on first configure. |
| Test provider | Button | `POST /config/ai-provider/test` | 409 if no config saved yet → disable until first save. Operator decision (§12). |
| Delete / Clear | Button (destructive, confirm) | `DELETE /config/ai-provider` | Confirmation dialog. Operator decision (§12). |

Provider→base-URL coupling and default-model prefill replicate the backend
operator page (`config.py`) so Android and web behave identically.

---

## 6. No-secret handling rules

These are mandatory acceptance criteria for any future implementation.

1. **No local persistence of key material, ever.** The API key lives only in the
   Compose `TextField` state (transient `mutableStateOf` / `rememberSaveable`
   **must not** be used for the key — use plain `remember` so it is not written to
   the saved-instance bundle). No DataStore, no Room, no SharedPreferences, no
   file cache, no in-memory singleton/ViewModel field that outlives the screen.
2. **Write-only field.** The key field is never populated from any response.
   Responses carry only `key_present` + `key_last4`; those drive placeholder copy,
   nothing else.
3. **Clear on every exit path.** Overwrite the key field to empty after a
   successful save, after a test, after delete, on cancel, on navigation away, and
   in `onCleared()`. Treat back-press and process-death the same as cancel.
4. **Submit only when non-blank.** An empty key field on Save sends no `api_key`
   field (keep-existing semantics) — it never sends an empty string.
5. **Never log, snackbar, toast, or analytics the key.** No request/response body
   logging on the provider endpoints. The OkHttp logging interceptor (if any) must
   be disabled or redacted for `/config/ai-provider*`. Crash/ANR breadcrumbs must
   not include the field.
6. **Failures must not echo input.** Error copy is derived from backend
   `last_test_status` / `_bad_config` codes (§7) — never from the raw request body
   or the raw provider error. A test/save failure must not surface the key.
7. **No clipboard auto-copy** of any key-bearing value. `key_last4` may be shown;
   the full key never can.

---

## 7. Error / state copy

Android renders **coarse, pre-mapped copy** keyed off backend fields. It never
shows raw backend status slugs, raw provider errors, ciphertext, env var names,
prompt bodies, or payloads (consistent with BYOAI-A1's `AiSummariesStatus`
mapping).

State derivation inputs from `AiProviderConfigStatusOut`: `configured`,
`enabled`, `key_present`, `last_test_status`, `source`; plus `_bad_config` error
codes from 400 responses.

| State | Trigger | Android copy (display-safe) |
| --- | --- | --- |
| Unconfigured | `configured=false`, `source=none` | "No AI provider is set up on your server yet." |
| Configured, untested | `configured=true`, `last_test_status=untested` | "Provider saved but not yet tested. Run a test to confirm it works." |
| Configured, healthy | `last_test_status=ok` | "Provider is configured and the last test passed." |
| Auth failed | `last_test_status=auth_failed` | "The provider rejected the configured key. Re-enter and test the key." |
| Provider unreachable | `last_test_status=unreachable` | "The provider endpoint couldn't be reached. Check the base URL and network." |
| Generic test error | `last_test_status=error` | "The provider test didn't complete. Re-check the configuration and try again." |
| Missing backend encryption key | 400 `encryption_key_required` / `encryption_key_invalid` | "Your server isn't ready to store provider keys yet. The server operator must configure encryption before saving a key." |
| Invalid local / OpenAI-compatible URL | 400 `base_url_required` / `base_url_must_be_http` | "Enter a base URL starting with http:// or https:// for this provider." |
| Other validation | 400 `unsupported_provider` / `model_required` / `api_key_required` | Field-specific short copy ("Choose a provider." / "Enter a model." / "Enter an API key."). |
| Test before save | 409 | "Save the configuration before testing it." |
| Source = environment | `source=environment` | "This provider is set by a server environment variable. Saving here will override it in the database." (read-only note.) |

All copy is pure/unit-testable (same pattern as `AiSummariesSettings.kt`), so it
can be covered without Compose.

---

## 8. Backend contract dependency list

### Existing endpoints (sufficient for the *mechanics* of an edit flow)

- `GET /config/ai-provider` → `AiProviderConfigStatusOut`
  (`require_token_scope(read)`). Safe status: `provider, model, base_url, enabled,
  configured, key_present, key_last4, last_test_status, last_test_at,
  last_test_detail, source`. **No plaintext key, no ciphertext.**
- `POST` / `PUT /config/ai-provider` → upsert (`require_legacy_api_token`).
- `POST /config/ai-provider/test` → backend-side provider test
  (`require_legacy_api_token`); 409 if unsaved.
- `DELETE /config/ai-provider` → clear DB config (`require_legacy_api_token`).

These cover provider/model/base_url/key/enabled, test, and delete with **no new
fields needed**. The data is already display-safe.

### BACKEND FOLLOW-UP (required before Android edit flow can ship safely)

- **B1 — Non-legacy write authorization.** The write/test/delete endpoints accept
  only the legacy shared `API_TOKEN`. Android needs these to accept an
  **operator-capable per-device token** (e.g. a new `operator`/`admin` device-token
  scope honored by `/config/ai-provider*`). Without this, Android can only write by
  holding the shared admin secret — a **STOP condition** for this design.
- **B2 — Operator-capability flag for clients.** Add a backend-authoritative
  boolean Android can read (e.g. `can_edit` on `AiProviderConfigStatusOut`, or an
  `operator: true` field on an existing session/identity response) so Android shows
  the edit entry point only to capable sessions **without probing**.
- **B3 — (confirm) origin enforcement for header-auth clients.**
  `enforce_local_origin_for_cookie_post` is bypassed when an `Authorization`
  header is present. Confirm that an operator device-token write from Android is an
  intended, non-CSRF path and is rate-limited/audited server-side.

> B1 and B2 are blocking. B3 is a confirmation item. **None of these may be
> implemented under this Android ticket** — they belong to the Mimeo backend repo
> and must be raised as backend tickets. This document does not modify backend code.

---

## 9. Implementation sequence (for the future Android PR, post-approval)

1. **Prereq gate:** B1 + B2 merged in backend and verified on the reference
   backend. Do not start Android edit code before this.
2. Add `AiProviderConfigStatusOut` DTO + `getAiProviderConfig` to `ApiClient`
   (read path; safe to land independently as a status enrichment).
3. Add operator-capability flag plumbing (from B2) and gate the "Manage AI
   provider" entry point.
4. Build the AI Provider screen: safe-status section + pure state/copy mapper
   (unit-tested, no Compose) mirroring §7.
5. Add the configuration form with the §6 no-secret handling enforced and tested.
6. Wire Save / Test / Delete to the write endpoints with the operator token.
7. Telemetry/logging redaction pass for `/config/ai-provider*`.
8. QA against the manual verification plan (§10).

Steps 2–3 (read enrichment + gating) can ship as a smaller PR ahead of the full
edit flow if the operator wants incremental delivery.

---

## 10. Test plan

### Automated (Android)
- Pure state/copy mapper unit tests for every row in §7 (pattern: existing
  `AiSummariesSettingsTest`).
- No-secret handling tests: key field cleared on save/test/delete/cancel/onCleared;
  empty key omits `api_key`; key never placed in saved-instance state.
- Gating test: edit entry point hidden when capability flag false/absent.

### Manual verification (operator)
Plain-English steps to prove Android is safe:

1. **Status-only sessions show no edit UI.** Sign in with an ordinary per-device
   token. Confirm AI Summaries shows status + "Configure on web" only, and that no
   "Manage AI provider" entry appears.
2. **No key is ever displayed.** Open the provider screen (operator session).
   Confirm the key field is empty, the stored key shows only as "ending 1234", and
   no full key, ciphertext, or env var name appears anywhere.
3. **Key does not persist.** Type a key, navigate away without saving, return —
   field is empty. Force-stop and relaunch the app — field is empty. Inspect
   DataStore/Room/SharedPreferences/files — no key material present.
4. **Failures stay clean.** Save a deliberately wrong key, run Test — confirm the
   on-screen message is the mapped copy ("provider rejected the configured key…")
   and that logcat / any export contains no key and no raw provider error.
5. **Missing encryption key path.** Against a backend without
   `AI_PROVIDER_ENCRYPTION_KEY`, attempt save — confirm the friendly
   "server isn't ready to store provider keys" copy, not the raw backend detail.

Command block (logcat key-leak scan during manual run):

```powershell
# Watch for any accidental key/secret material in app logs while exercising the screen
adb logcat | Select-String -Pattern "api_key|sk-|Bearer|AI_PROVIDER_ENCRYPTION_KEY"
```

```bash
# Build + unit tests for the future implementation PR (no code in this ticket)
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

---

## 11. Stop / defer list

**Deferred (specified, not built in v1):**
- The entire AI Provider edit screen and write wiring — until B1 + B2 land and the
  operator approves.
- Read-status enrichment (`GET /config/ai-provider` in Android) — optional, may
  ship earlier as status-only.

**Hard out of scope (never in this feature):**
- Local storage of provider API keys on the device.
- Direct calls from Android to any LLM provider.
- Prompt editing / prompt bodies.
- OAuth / provider sign-in flows.
- Billing, provider resale, or any SaaS/credit handling.
- Showing plaintext keys, ciphertext, env var names, raw provider errors, or raw
  article/provider payloads.
- Holding the legacy shared `API_TOKEN` on the device to satisfy the write
  endpoints (would trip a STOP condition).

---

## 12. Open operator decisions

The operator must decide before any implementation ticket is opened:

1. **Posture:** Confirm Android stays **status-only** for v1 (recommended), or
   approve pursuing the edit flow contingent on backend B1 + B2.
2. **Operator-only edit UI:** Confirm the edit UI must be gated on a
   backend-provided operator-capability flag and never shown to ordinary sessions.
3. **Base URL entry on Android:** Allow entry of `local` / `openai_compatible`
   base URLs from a mobile device, or restrict those providers to the web operator
   page only? (Local endpoints are higher-trust; mobile entry widens exposure.)
4. **Delete / Clear on Android:** Allow destructive clear of the server provider
   config from mobile, or keep delete web-only?
5. **Test provider on Android:** Allow triggering the backend provider test from
   mobile, or keep test web-only?
6. **Backend tickets:** Approve raising B1 (operator write scope) and B2
   (capability flag) as Mimeo backend tickets, since the Android edit flow is
   blocked without them.

---

## Appendix — contract references (read-only inspection, no backend edits made)

- `Mimeo/backend/app/api/config.py` — provider endpoints, auth dependencies,
  operator HTML page.
- `Mimeo/backend/app/schemas.py` — `AiProviderConfigIn`, `AiProviderConfigStatusOut`,
  `AiProviderName`, `AiProviderTestStatus`.
- `Mimeo/backend/app/services/ai_provider_config.py` — test statuses
  (`untested/ok/auth_failed/unreachable/error`), `classify_provider_test_failure`,
  encryption-key assertions.
- `Mimeo/backend/app/core/auth.py` — `require_legacy_api_token`,
  `require_token_scope`, `enforce_local_origin_for_cookie_post`.
- Android predecessor: `app/src/main/java/com/mimeo/android/ui/settings/AiSummariesSettings.kt`,
  `app/src/main/java/com/mimeo/android/model/SummaryCapabilities.kt`.

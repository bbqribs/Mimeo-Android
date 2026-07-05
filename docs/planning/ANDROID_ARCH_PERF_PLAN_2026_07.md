# Android Architecture & Performance Plan — July 2026

**Ticket:** T-AND-ARCH-PERF-PLAN-1
**Date:** 2026-07-05
**Repo:** Mimeo-Android
**Base SHA:** `b1df6c1` (main, clean tree)
**Status:** Docs-only planning. No source changes in this ticket.

## Context and method

This plan follows the recent auth/settings hardening (T-AND-AUTH-EXPIRY-1 stale-token
re-auth) and the Devices & sessions UI (T-AND-DEVICES-1). Reconnaissance was run as
four parallel read-only passes over: (1) `AppViewModel` state structure, (2) the
`data/` + `model/` layer, (3) navigation and Compose screens, (4) tests and build
configuration. All findings below cite concrete files; nothing is speculative.

It also builds on `docs/ANDROID_LIMITED_REFACTOR_SURVEY_2026_05.md`. Verified against
the code (not the doc) at `b1df6c1`:

| May 2026 candidate | Status at b1df6c1 |
| --- | --- |
| R1/R1.1/R1.2 — Bluesky state holder + coordinator | **Shipped** (`state/BlueskyStateHolder.kt`, `bluesky/BlueskyServiceCoordinator.kt`) |
| R3 — shared item row | **Shipped** (`ui/common/ItemRow.kt`, `ui/common/RowChrome.kt`) |
| R4 — Bluesky input helpers | **Shipped** (`util/bluesky/BlueskyInput.kt`) |
| R9 — session source presentation | **Shipped** (`ui/common/SessionSourcePresentation.kt`) |
| R2, R5, R6, R8, R10, R11, R12 | **Not done** — still valid backlog, not re-litigated here |

Despite the Bluesky extraction (~444 lines removed), `AppViewModel.kt` has regrown
from 5,753 to **6,640 lines**, mostly from queue/session work plus the new
auth/devices/password clusters. The growth pattern is the core code-health signal:
extractions work, but new feature state defaults into the god object.

## What is healthy (checked, no action needed)

- **Stale-token re-auth (T-AND-AUTH-EXPIRY-1) is well-designed.**
  `handleAuthFailureIfNeeded()` (`AppViewModel.kt:1654–1669`) is mutex-guarded
  (`authFailureMutex`), idempotent per session (`authFailureHandledThisSession`),
  and consistently invoked from devices, password-change, and queue paths. No rework
  needed.
- **Devices & sessions API layer follows house pattern.** `getAccountDevices` /
  `postRevokeDevice` / `postRevokeOtherDevices` (`ApiClient.kt:383–412`) match the
  existing endpoint template exactly; `DeviceSession` model never exposes raw tokens.
- **Test suite is in good shape.** 123 unit test files (~15k LOC) + 6 instrumented;
  plain JUnit4 + MockWebServer + inline fakes; no `Thread.sleep`, no slow-test
  offenders found. Both recent tickets are covered (`StaleTokenAuthFailureTest.kt`,
  `DevicesApiTest.kt` 188 lines, `DevicesSupportTest.kt`). The 5 Robolectric tests
  are all justified (DataStore/Context). **No test-speed cleanup ticket is warranted.**
- **A repository layer exists and is used** (`repository/PlaybackRepository.kt` with
  stale-cache fallback on 5xx/IOException). ViewModel→ApiClient direct calls exist
  but are the established pattern, not a regression.

## Ranked findings

| # | Sev | Finding | Evidence | Files | User impact | Ticket | Type |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F1 | High | `SettingsScreen.kt` is a 3,656-line monolith: 9 spoke sections rendered as inline `if (activeSection == …)` blocks, 26 `collectAsState()` calls at top level, 30+ local `remember` states. Any state change recomposes the whole screen; every new settings feature edits one giant file. | Spokes at `SettingsScreen.kt:668–2277`; state collection `:224–242`; local state `:261–406` | `ui/settings/SettingsScreen.kt` | Settings screen jank risk on low-end devices; high merge-conflict/regression surface for every settings ticket | **T-A** | Refactor + perf |
| F2 | High | New account-security state (devices list, revoke flows, password change) was added directly into `AppViewModel` rather than a holder, repeating the pre-R1 Bluesky pattern. AppViewModel is the app's only ViewModel: 68 `MutableStateFlow`s, 154 functions, 248-line init block. | Devices `AppViewModel.kt:1522–1586`; password `:1483–1520`; auth failure `:1654–1669`; init `:639–887` | `AppViewModel.kt`, `ui/settings/DevicesSupport.kt`, `ui/settings/PasswordChangeSupport.kt` | None directly today, but each addition raises regression risk in a 6,640-line file with no direct unit tests | **T-B** | Refactor |
| F3 | Medium | Loading/error state handling is re-rolled per screen (19 files) with no shared composable; error copy is hardcoded and inconsistent in tone ("Couldn't…" vs "…is not available…", mixed capitalization, inconsistent "Please"). | `DevicesAndSessionsScreen.kt:75–99`, `AiProviderEditScreen.kt`, `SignInScreen.kt`, `ConnectivityDiagnosticsScreen.kt`; copy in `DevicesSupport.kt:23–111`, `PasswordChangeSupport.kt:24–101`, `BlueskyHealth.kt` | ~19 `ui/**` files | Visible copy inconsistency across settings/account surfaces; error states behave differently per screen (some lack retry) | **T-C** | UX consistency |
| F4 | Medium | Gradle build lacks configuration cache and parallel execution; everything else (KSP not kapt, AGP 9.0.1, JVM 17) is modern. | `gradle.properties` has no `org.gradle.configuration-cache` / `org.gradle.parallel` / `org.gradle.caching` | `gradle.properties` | Developer build time only | **T-D** | Build/test speed |
| F5 | Medium | `ApiClient.kt` repeats the identical `Request.Builder().url().header("Authorization","Bearer $token")` template across ~65 of 69 endpoints; 401 handling is caller-side only (by design post-EXPIRY-1, but the boilerplate invites drift). | `ApiClient.kt` throughout, e.g. `:238–412`; `executeJson`/`throwApiException` `:1434–1485` | `data/ApiClient.kt` | None directly; maintenance drag and drift risk as endpoints grow | **T-E** | Refactor |
| F6 | Low | `DevicesAndSessionsScreen` takes no bottom-clearance parameter, unlike `SettingsScreen` (`jumpPillBottomClearance`); list can sit under the mini-player when playback is active. No error-state Retry button (Refresh button exists but only in header). | `DevicesAndSessionsScreen.kt:48–103` vs `SettingsScreen.kt:658` | `ui/settings/DevicesAndSessionsScreen.kt` | Device rows obscured by mini-player during playback | fold into **T-C** | UX consistency |
| F7 | Low | Plausible (unmeasured) runtime hotspots in AppViewModel: sign-out settings cascade mutates ~15 flows in one collector pass (`:704–752`); cached-item count flow re-runs `resolveOfflineReadyIds` on every cache write with no debounce (`:782–808`); `loadQueueOnce()` is 267 lines on the default (main) dispatcher (`:1990–2257`). | `AppViewModel.kt` lines cited | `AppViewModel.kt` | No reported symptom (no jank/ANR reports); risk is plausible, not observed | **Defer** — no instrumentation ticket; see performance stance | Performance |
| F8 | Low | `Models.kt` (988 lines, ~45 types) and `SettingsStore.kt` (61 preference keys, 30+ near-identical setters) are grab-bags; a few manual `JsonElement` parses are fragile (`BlueskyOperatorStatusResponse.resolvedLastErrorMessage`, `contentSummaryFailureReasonFromApiMessage`). | `Models.kt:135–139, 164–167, 498–506`; `SettingsStore.kt:59–167` | `model/Models.kt`, `data/SettingsStore.kt` | None; navigation/maintenance drag only | **Defer** | Refactor |

## Ticket queue (bounded, sequenced)

Five tickets, each one concern, each a single PR. Ordering minimizes conflicts:
T-D is standalone; T-B before T-A avoids both touching account/devices state at once;
T-C after T-A so the shared components land into the already-split settings files.

### T-D — Enable Gradle configuration cache + parallel builds
- **Scope:** Add `org.gradle.configuration-cache=true`, `org.gradle.parallel=true`,
  `org.gradle.caching=true` to `gradle.properties`; fix any config-cache violations
  that surface (expected few — KSP + AGP 9 are compatible). No source changes.
- **Risk:** Low. If a plugin is incompatible, revert the flag and record why.
- **Verify:** `.\gradlew.bat :app:assembleDebug` twice (second run should report
  configuration cache reused); `.\gradlew.bat :app:testDebugUnitTest`.

### T-B — Extract `AccountSecurityCoordinator` from AppViewModel
- **Scope:** Move devices-list state (`_devicesListState`, `_revokingDeviceIds`,
  `_revokeOthersInProgress`, `loadDevices`, `revokeDevice`, `revokeOtherDevices`,
  `AppViewModel.kt:1522–1586`) and password-change state (`_passwordChangeState`,
  `changePassword`, `clearPasswordChangeState`, `:1483–1520`) into an
  `AccountSecurityCoordinator` + holder under `state/`, exactly following the
  proven `BlueskyStateHolder`/`BlueskyServiceCoordinator` precedent (R1/R1.1).
  `handleAuthFailureIfNeeded` stays in AppViewModel (it owns the auth lifecycle);
  the coordinator receives it as an injected `suspend (Throwable) -> Boolean`.
  AppViewModel keeps thin delegation properties so Compose call sites are unchanged.
- **Why now:** these are the two clusters the recon rated "very high" extraction
  confidence (isolated flows, only coupling is the reusable stale-auth check), and
  they are the newest code — extracting now stops the regrowth pattern at its source.
- **Risk:** Low. Pure state move; existing `DevicesApiTest`, `DevicesSupportTest`,
  password-change tests, and stale-token regressions must pass unchanged.
- **Tests:** Add coordinator-level unit tests with a fake ApiClient covering
  load/revoke/revoke-others and the 401→auth-failure delegation path (closes the
  "device revocation error recovery" gap noted in test recon).

### T-A — Split SettingsScreen spoke sections into per-section files
- **Scope:** Mechanical move of the inline spoke blocks
  (`SettingsScreen.kt:668–2277`) into per-section composables in
  `ui/settings/sections/` (e.g. `AccountSection.kt`, `PlaybackSection.kt`,
  `BlueskySection.kt`, …), each collecting only the state it uses. The hub, the
  `SettingsSection` enum, and `SettingsSpokeBackHeader` stay. Password-change and
  sign-out dialogs move with the Account section. No visual or behavioural change;
  keep test tags and copy byte-identical.
- **Bounding rule:** if a full split proves too large for one PR, land the three
  biggest sections (Bluesky ~380 lines, Playback ~450, Account ~290) first and
  file one follow-up for the remainder — do not let this PR grow review-resistant.
- **Risk:** Medium — large mechanical diff. Mitigate by moving code verbatim,
  section-by-section commits, no renames.
- **Perf note:** this also delivers the only recomposition fix worth doing now:
  per-section composables collecting only their own flows shrink the recomposition
  scope from the whole 3,656-line screen to the active spoke.

### T-C — Shared load-state component + error copy alignment
- **Scope:** Add `ui/common/LoadStatePane.kt` (loading spinner / error text +
  Retry / empty message slots) built around the sealed
  Idle/Loading/Success/Error shape already used by `DevicesListState`
  (`DevicesSupport.kt:11–16`). Adopt it in exactly four screens:
  `DevicesAndSessionsScreen`, `AiProviderEditScreen`,
  `ConnectivityDiagnosticsScreen`, `SignInScreen`. While touching those screens,
  align their error copy to one convention (sentence case, "Couldn't … . Try
  again." pattern, consistent use of "Please") — documented in a short comment
  block in `LoadStatePane.kt`, not a new doc. Include the F6 fix: give
  `DevicesAndSessionsScreen` the bottom-clearance parameter and a Retry action
  in the error state.
- **Not in scope:** the other ~15 screens; migrate them opportunistically when
  next touched. No string-resource migration (see Do-not-do).
- **Risk:** Low–Medium (visual diffs on four screens). Manual check light/dark.

### T-E — Collapse ApiClient request-building boilerplate
- **Scope:** Add private helpers in `ApiClient.kt` (e.g.
  `authorizedRequest(baseUrl, path, token) { get() }`) and mechanically apply to
  the ~65 endpoints. No signature changes, no behaviour changes, no error-model
  changes, no interceptor, no Retrofit. Expected reduction: several hundred lines.
- **Risk:** Low–Medium. Purely mechanical, but 65 call sites; MockWebServer
  contract tests (`DevicesApiTest`, `ApiClientCompletionSemanticsTest`,
  `AiProviderConfigApiTest`) verify method/path/header for a good sample.
- **Sequencing:** last — lowest urgency, and it should not race T-B (both touch
  auth-adjacent API code paths).

## Performance stance

No performance instrumentation ticket is recommended. There is no measured symptom
(no jank/ANR reports, no user-visible slowness attributed to settings, startup, or
the device list), and `docs/ANDROID_TELEMETRY_PLAN.md` already covers the
observability track. The code-observed risks are handled as follows:

- **Settings-screen recomposition scope** — fixed structurally by T-A (the only
  performance-flavoured work justified now, and it is free inside a refactor
  already worth doing).
- **Sign-out 15-flow cascade, offline-ready recompute per cache write,
  `loadQueueOnce` size (F7)** — documented here with line numbers; act only if a
  symptom appears. The `loadQueueOnce` decomposition belongs to the May survey's
  R12 lineage and should be its own future ticket, not smuggled into this queue.
- **Device list performance** — non-issue: single page, `LazyColumn` keyed by
  `device.id` (`DevicesAndSessionsScreen.kt:92`).

## Do not do

- **No Retrofit/Ktor migration.** The hand-rolled OkHttp client is consistent and
  contract-tested; T-E addresses the verbosity at a fraction of the risk.
- **No multi-ViewModel re-architecture / per-screen ViewModels.** The single
  shared `AppViewModel` + holder/coordinator pattern is working (Bluesky proof).
  Keep chipping via extractions.
- **No DI framework, no multi-module split, no package renames** — unchanged from
  the May survey's do-not-do list, which remains in force.
- **No `Models.kt`/`SettingsStore.kt` split (F8).** High-churn mechanical move,
  no behavioural payoff today. Revisit only if a domain split falls out of other
  work naturally.
- **No Compose UI-test infrastructure buildout.** The pure-function test style is
  deliberate and healthy; adding `ComposeTestRule` plumbing is not justified by
  any current gap.
- **No wholesale string-resource migration.** Hardcoded copy is app-wide; T-C
  aligns copy in the screens it touches, nothing more.
- **No performance instrumentation/benchmark ticket** — no measurement gap that
  telemetry planning doesn't already own.
- **No re-auth rework.** T-AND-AUTH-EXPIRY-1's design is sound; do not centralize
  401 handling into ApiClient (it would fight the mutex/idempotence design in
  AppViewModel).
- **No backend/API contract changes.** Everything above is Android-local.

## What first, what deferred

**First: T-D** (configuration cache — trivial, immediate daily payoff), then
**T-B** (AccountSecurityCoordinator — small, proven pattern, freshest code, best
test safety net). **T-A** follows as the highest-impact single change. **T-C**
after T-A; **T-E** last.

**Deferred, in priority order if capacity appears:** remaining SettingsScreen
sections (if T-A is landed partially); May survey R2 (`LibraryStateHolder`) and
R12 (`QueueLoadCoordinator` / `loadQueueOnce` decomposition) as the next
AppViewModel chips; R10 (`AppRoute` sealed routes) — still valid but the
hub-and-spoke settings pattern means route brittleness is lower than the May
survey assumed; F8 grab-bag splits; shared MockWebServer test utility (~50 LOC
saving — nice-to-have only).

## Gates

- Docs-only deliverable: this file.
- `git diff --check` — run at close-out; no whitespace errors.
- No Kotlin/source changes; no build/runtime/manual verification required.

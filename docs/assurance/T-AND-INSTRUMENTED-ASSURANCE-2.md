# T-AND-INSTRUMENTED-ASSURANCE-2 assurance record

**Status: incomplete — do not treat this record as Lane 2 completion.**

This record deliberately distinguishes executed evidence from planned/manual
work. It contains no credentials, private queue contents, raw device UI,
tokens, cookies, or diagnostic payloads.

## Scope and preflight

- Android base: `8cf62491797451aeac34f6f8889137124aea6fba` (`main` before this
  assurance branch).
- Runtime target: the current `ops/runtime-target.json` resolver value. The
  retired `beh-august2015` hostname is not a valid substitute.
- Current Android CI on the base: Android CI, Android Instrumented CI, and
  Android Release Check passed. There were no open Android or backend PRs at
  preflight that changed Up Next, History, synchronization, or verification.
- Backend operator smoke passed before the local/device evidence collection.
- Physical device: SM-S926B, Android 16, unlocked and authorized by ADB.

## Executed evidence

| Scenario | Result | Evidence |
| --- | --- | --- |
| Authenticated route preflight fails while a move is queued | passed | `PendingUpNextMovePublisherTest.reportedOfflineNavigationAndRecreationKeepMoveQueuedWhenPreflightFails` proves one failed route read, retained original intent, and no move POST. |
| Original semantic move is posted once after a read-only preflight | passed | `successfulPreflightIsReadOnlyThenPublishesOriginalIntentExactlyOnce` asserts exact POST body and no second publication after authoritative adoption. |
| POST commits but response is lost | passed | `committedPostWithLostResponseRefreshesAuthoritativeTruthWithoutReplay` uses a stateful MockWebServer: it commits the move, terminates the response, refuses a second POST, then supplies the committed projection to the reconciliation read. |
| Stale queued structure move | passed | `structureConflictAdoptsServerOrderAndDiscardsMoveWithoutRetry` asserts the original structure version, structure-domain 409, authoritative adoption, and no retry. |
| Scope quarantine and late-response isolation | passed | `PendingUpNextMovePublisherTest.accountOrEndpointChangeAfterPreflightCannotStartPostAndQuarantinesIntent`, `UpNextPersistenceTest.accountSwitchEndpointSwitchAndSignOutClearContinuityState`, and `AccountScopedRequestContextTest.lateSemanticMoveResponseCannotAlterNewAccountOrEndpoint`. |
| Pointer/structure isolation and persistence | passed | `UpNextPersistenceTest.semanticMoveResultAtomicallyAdoptsOrderWithoutRegressingPointerProgressOrProvenance`; existing pointer/history API and persistence tests remain part of the full JVM suite. |
| Legacy order-only/mixed/non-reorder classification | passed | `UpNextSynchronizationTest` classification coverage; no whole-session reorder is synthesized from an ambiguous legacy snapshot. |
| API-35 Up Next render semantics | passed on physical device before this change set; must run again in PR CI | Existing `UpNextRenderSmokeTest` covers History vs Earlier identity, accessible move boundaries, disabled status, and compact presentation. |
| Phone-side authoritative HTTPS | passed | `AuthoritativeRuntimeReachabilityInstrumentedTest` ran on the SM-S926B with the dynamically resolved HTTPS origin and received `/health` HTTP 200. The origin is supplied only as an instrumentation argument. |
| Pre-semantic-reorder artifact | passed (build only) | A same-signer debug APK was built from detached `4f3945537c31ae04dfd73b4bc94995a3659787dc`; SHA-256 `5fcdd99ed6715466b7e6dd15e63a9c0237b1949cb0a91890dd445c551ee8aac8`. |

The response-loss fixture is test-only. It uses MockWebServer and the existing
release `ApiClient`; it adds no endpoint, credential capture, release switch,
or retry-policy change.

## Required evidence still not run

| Matrix area | Result | Reason / safe next action |
| --- | --- | --- |
| Android ↔ web ordinary synchronization and History management | not run | Requires a new isolated `test-` account and a clean Android profile. Do not use an unknown existing debug-app session or a household account. |
| Offline success, real stale 409, and real response-loss reconciliation | not run | Execute against the isolated account only after the fresh-profile network boundary is established. Prove route loss with the physical HTTPS probe before each offline case. |
| Endpoint isolation against two real origins | not run | Requires a second safe authoritative test origin; account switching is not a substitute. |
| Real pre-semantic-reorder in-place upgrade | not run | The required old debug artifact is built from `4f3945537c31ae04dfd73b4bc94995a3659787dc`, but has not been installed. Create the legacy snapshot while the route is demonstrably unavailable, then upgrade in place without uninstalling or clearing data. |
| TalkBack, large-text, compact-width observation | not run | Must be observed on hardware; Compose semantics tests are not evidence for TalkBack or visual readability. |
| Real-headset pointer transition and canonical History | not run | Requires real wired/Bluetooth headset hardware and the manual checklist. |
| Post-matrix backend smoke and disposable-account purge | not run | Run the target-resolved operator smoke after the matrix, perform the mandatory account purge dry run, then commit only the recorded `test-` account purge. |

## Safe continuation procedure

1. Start the canonical acceptance harness in an external run directory and
   complete its fresh Android-profile/Tailscale checkpoint. It must record the
   dynamically resolved origin, not a historical hostname.
2. Provision only a `test-` account through the operator path; keep password,
   setup code, device token, cookies, and raw UI out of source control and
   console logs.
3. Run the cross-surface matrix, recording only symbolic outcomes and sanitized
   account IDs. Stop and file a separate corrective ticket on any product,
   backend, contract, or persistence defect.
4. Run the headset checklist and record actual observations.
5. Purge dry-run first, then purge the exact recorded `test-` account; confirm
   it is absent. Run backend smoke again and update this table before claiming
   completion.

The physical HTTPS probe can be repeated without credentials:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.mimeo.android.device.AuthoritativeRuntimeReachabilityInstrumentedTest" `
  "-Pandroid.testInstrumentationRunnerArguments.authoritative_origin=https://<resolved-authoritative-host>" `
  --no-daemon
```

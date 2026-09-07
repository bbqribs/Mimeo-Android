# Android Maintenance Companion Baseline — 2026-09-07

Companion evidence record for `T-MAINTENANCE-BASELINE-1`. The canonical
cross-stack policy and host/backup record are in Mimeo's
`docs/MAINTENANCE_BASELINE_2026_09.md`; this record owns Android configuration,
watch items, and signing/package continuity.

## Cadence, ownership, and boundaries

| Item | Baseline |
| --- | --- |
| Owner | **Mimeo-Android repository maintainer** owns build/dependency evidence and advisory triage; **Android release-signing custodian** owns signer and package continuity. |
| First monthly review | Consolidated advisory review by **2026-10-07** (with Mimeo). Backup freshness is recorded in Mimeo's canonical record. |
| First quarterly window | **2026-12-01 through 2026-12-07**; later quarterly per repository. |
| Emergency review | Event-driven after a support deadline, breakage, or relevant advisory, with an operator-recorded decision and no unattended SLA. |
| Boundary | No upgrade, dependency resolution, signing change, package rename, or architecture decision is authorized here. Future Android and Mimeo batches use separate PRs. |

Observation date for repository facts and external sources: **2026-09-07**.
“Configured” is the Gradle declaration; it is not a resolved dependency,
installed SDK/JDK, or released APK version without separate evidence.

## Configuration evidence

| Surface | Configured evidence | Resolved / installed / deployed status | Horizon / review source |
| --- | --- | --- | --- |
| Gradle / AGP / Kotlin / JDK | [`gradle/wrapper/gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties): Gradle **9.2.1**. [`build.gradle.kts`](../build.gradle.kts): AGP **9.0.1**, Kotlin serialization/Compose **2.3.10**, KSP **2.3.4**. [`app/build.gradle.kts`](../app/build.gradle.kts): toolchain **17**. | Resolved plugin artifacts and installed local JDK/SDK: **unknown**. CI configures Temurin 17 in [android-ci.yml](../.github/workflows/android-ci.yml), not an installed-local-JDK assertion. | [AGP 9.0.1 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes), consulted 2026-09-07: JDK 17 and API 36.1 support. Review AGP/Gradle/Kotlin compatibility together. |
| SDK levels | `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26` in `app/build.gradle.kts`. | Installed SDK platforms and released APK manifest: **unknown**. | [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk), consulted 2026-09-07: from **2026-08-31**, new apps/updates require API 36; existing-app availability to new users requires API 35; an extension may be requested to **2026-11-01**. |
| Compose | Compose BOM **2026.03.00** in `app/build.gradle.kts`. | Resolved Compose library versions: **unknown**. | AndroidX/Compose release notes and advisories at quarterly/monthly review. |
| Major AndroidX | `core-ktx 1.13.1`; lifecycle runtime/viewmodel-compose **2.8.4**; `activity-compose 1.9.1`; navigation-compose **2.7.7**; datastore **1.1.1**; Room **2.8.4**; work-runtime-ktx **2.9.1**. | Exact transitive resolution and installed device versions: **unknown**. | AndroidX release notes/advisories; stale pins remain visible until an evidence-backed decision. |
| Media / crypto | `androidx.media:media:1.7.0`; `androidx.security:security-crypto:1.1.0-alpha06`. | Resolved/device versions: **unknown**. | AndroidX release/deprecation notices monthly and quarterly; neither is a routine bump. |
| Package and signer | Production application ID `com.mimeo.android`, version **0.4.5** / versionCode **11**, and release signing/fingerprint verification are defined in `app/build.gradle.kts`. | Keystore, alias, certificate fingerprint, and signed APK are intentionally **not inspected or recorded**. | [Android household distribution minimum](ANDROID_HOUSEHOLD_DISTRIBUTION_MINIMUM_2026_07.md) requires one operator-held signing identity and forbids committing/printing signing material. |

## Crowned watch items and deferrals

| Watch item | Current configured value | Required future decision / boundary |
| --- | --- | --- |
| Legacy media | `androidx.media:media:1.7.0` | Media3 migration is a separate architectural ticket. Examine playback surfaces first; a routine bump is not that decision. |
| Crypto alpha/deprecation | `security-crypto:1.1.0-alpha06` | Separate alpha/deprecation exit decision; inspect `AuthTokenStorage` use and migration/rollback implications first. |
| SDK deadline | compile/target SDK 35 | API 35 meets existing-app availability, but API 36 has been required for new Play submissions/updates since 2026-08-31; extension date is 2026-11-01. Determine distribution/release intent and behavior changes in a dedicated ticket. |
| Lifecycle | `2.8.4` | Carry in the quarterly matrix; no upgrade decision. |
| Core KTX | `1.13.1` | Carry in the quarterly matrix; no upgrade decision. |
| Navigation Compose | `2.7.7` | Carry in the quarterly matrix; no upgrade decision. |
| Work runtime | `2.9.1` | Carry in the quarterly matrix; no upgrade decision. |
| Signer/package continuity | `com.mimeo.android`; operator-held release identity | Never casually rotate signer, change package identity, or treat a debug/unsigned artifact as a production substitute. Any such work is a separate release/operator ticket. |

## Future review record

For each monthly advisory review and quarterly batch, record owner, date, source
and observation date, configured/resolved/installed/deployed distinctions,
affected usage/reachability, release notes/CVEs, gates actually run, operator
impact, deferral rationale, and next review. Record a separate ticket before an
Android architecture migration, SDK behavior-change work, signing/package change,
data migration, or risky platform major. Do not claim an advisory is unreachable
without checking use.

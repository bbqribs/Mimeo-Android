# Android Dependency & Deprecation Audit — May 2026

**Date:** 2026-05-08  
**Branch:** docs/android-telemetry-plan  
**Build gates:** `assembleDebug` ✅ · `testDebugUnitTest` ✅ (all UP-TO-DATE, no test failures)

---

## 1. Current versions

| Component | Version |
|---|---|
| AGP | 9.0.1 |
| Kotlin | 2.3.10 |
| KSP | 2.3.4 |
| Gradle | 9.2.1 |
| Compose BOM | 2026.03.00 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Java toolchain | 17 (Eclipse Temurin, auto-provisioned) |
| Room | 2.8.4 |
| Lifecycle | 2.8.4 |
| OkHttp | 4.12.0 |
| WorkManager | 2.9.1 |
| Navigation Compose | 2.7.7 |
| kotlinx-serialization-json | 1.6.3 |
| kotlinx-coroutines-android | 1.8.1 |
| security-crypto | 1.1.0-alpha06 |

---

## 2. Deprecation warnings (full list)

All warnings were captured from `--warning-mode all` on both build targets.

### W1 — `android.builtInKotlin=false` is deprecated ⚠️ AGP 10 breaking

```
WARNING: The option setting 'android.builtInKotlin=false' is deprecated.
The current default is 'true'. It will be removed in version 10.0 of the Android Gradle plugin.
```

**Source:** `gradle.properties` line 5.  
**Severity:** Must fix before AGP 10.0.

### W2 — `android.newDsl=false` is deprecated ⚠️ AGP 10 breaking

```
WARNING: The option setting 'android.newDsl=false' is deprecated.
The current default is 'true'. It will be removed in version 10.0 of the Android Gradle plugin.
```

**Source:** `gradle.properties` line 6.  
**Severity:** Must fix before AGP 10.0.

### W3 — `org.jetbrains.kotlin.android` plugin usage is deprecated ⚠️ AGP 10 breaking

```
w: Deprecated 'org.jetbrains.kotlin.android' plugin usage
The 'org.jetbrains.kotlin.android' plugin in project ':app' is no longer required for Kotlin
support since AGP 9.0. Remove both `android.builtInKotlin=true` and `android.newDsl=false`
from `gradle.properties`, then migrate to built-in Kotlin.
```

**Source:** root `build.gradle.kts` (declares `org.jetbrains.kotlin.android`) and `app/build.gradle.kts` (applies it).  
**Severity:** Must fix before AGP 10.0.  
**Note:** W1, W2, and W3 are a single migration unit — they must be resolved together.

### W4 — JDK toolchain auto-provisioned without toolchain repositories ⚠️ Gradle 10 breaking

```
Using toolchain 'Eclipse Temurin JDK 17 (17.0.17+10)' installed via auto-provisioning without
toolchain repositories. This behavior has been deprecated. This will fail with an error in
Gradle 10.
```

**Source:** `app/build.gradle.kts` uses `kotlin { jvmToolchain(17) }` but no toolchain resolver plugin is registered in `settings.gradle.kts`.  
**Severity:** Must fix before Gradle 10.

---

## 3. Which warnings are harmless now

| Warning | Harmless? | Reason |
|---|---|---|
| W1 `builtInKotlin=false` | **No** — actionable | Removed in AGP 10.0 |
| W2 `newDsl=false` | **No** — actionable | Removed in AGP 10.0 |
| W3 `kotlin.android` plugin | **No** — actionable | Same migration as W1/W2 |
| W4 toolchain auto-provision | **No** — actionable | Will error in Gradle 10 |
| Configuration cache suggestion | Yes — informational | Optional performance opt-in, not a deprecation |

No currently emitted warning is harmless to carry forward into an AGP 10.0 or Gradle 10 upgrade.

---

## 4. Compose BOM status

**Current BOM: `2026.03.00`** — March 2026 release, current.

`ROADMAP.md` item 19 reads:

> Compose BOM migration to 1.10.x. Bump from `2024.06.00` ...

This item is **stale**: `app/build.gradle.kts` already uses `2026.03.00`. The migration was completed at some point without the ROADMAP being updated.

**Recommendation:** Mark ROADMAP item 19 as `[x]` in a housekeeping pass; no BOM work is needed now.

---

## 5. KSP version alignment

Kotlin is `2.3.10`, KSP is `2.3.4`. These are on the same minor series (`2.3.x`) but different patch levels. KSP 2.x tracks Kotlin patch versions closely. Verify the KSP changelog for `2.3.4` to confirm it explicitly supports Kotlin `2.3.10`, or bump KSP to `2.3.10` when the next KSP release in that series is available.

This did not produce a build warning, and compilation succeeded — so it is **non-blocking** but worth checking at the next KSP update.

---

## 6. `security-crypto` alpha note

`androidx.security:security-crypto:1.1.0-alpha06` is still in alpha. No stable `1.1.x` release has shipped as of this audit. This is a pre-existing carry; there is no `stable` alternative to pin to right now. No action required — just track for a future maintenance window.

---

## 7. Proposed follow-up tickets (priority order)

### T1 — Built-in Kotlin migration (W1 + W2 + W3) — `maintenance/built-in-kotlin-migration`

**Scope (bounded):**
1. `gradle.properties`: remove `android.builtInKotlin=false` and `android.newDsl=false`.
2. Root `build.gradle.kts`: remove `id("org.jetbrains.kotlin.android") version "..." apply false`.
3. `app/build.gradle.kts`: remove `id("org.jetbrains.kotlin.android")` from the plugins block.
4. Run `assembleDebug` + `testDebugUnitTest`; verify zero warnings.

Risk: low. AGP's built-in Kotlin support has been the default since AGP 8.x. The `kotlin.jvmToolchain(17)` and `compileOptions` blocks are unaffected. `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, and KSP plugins are separate and stay as-is.

**Must fix before any AGP 10.0 upgrade.**

### T2 — Toolchain resolver registration (W4) — `maintenance/toolchain-resolver`

**Scope (bounded):**
1. Add the Foojay toolchain resolver plugin (or equivalent) to `settings.gradle.kts`.
2. Confirm `gradlew --info` no longer emits the auto-provision deprecation.

Standard boilerplate:
```kotlin
// settings.gradle.kts
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
```

Risk: very low. This is a pure settings-level addition with no effect on compilation or runtime behavior.

**Must fix before any Gradle 10 upgrade.**

### T3 — ROADMAP hygiene pass (stale BOM item + other closed items) — `docs/roadmap-hygiene`

**Scope (bounded):**
- Mark ROADMAP item 19 (Compose BOM) as `[x]`.
- Sweep for other stale/shipped items during the same pass (referenced by existing ROADMAP item 12).

Risk: docs-only, zero build impact.

### T4 — KSP version alignment check — include in T1 or T2

When executing T1 or T2, check whether a KSP `2.3.10` release exists and, if so, bump from `2.3.4` to match Kotlin `2.3.10`. If no matching KSP patch is published yet, defer.

---

## 8. What does NOT need fixing now

- Navigation Compose `2.7.7` — behind `2.8.x` but functional; no deprecation warnings emitted.
- WorkManager `2.9.1` — current stable.
- OkHttp `4.12.0` — current stable.
- `security-crypto:1.1.0-alpha06` — no stable replacement; leave as-is.
- Configuration cache — opt-in performance improvement; no urgency.

---

## 9. Dependency upgrade posture

No broad dependency upgrades are recommended in this audit. The existing BOM (`2026.03.00`) is current. Individual library pins (lifecycle, room, okhttp) are all recent. An optional maintenance sweep could align `navigation-compose` and `core-ktx` to their latest stable releases, but this belongs in a dedicated upgrade ticket, not here.

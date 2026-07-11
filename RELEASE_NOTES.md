# Mimeo Android Release Notes

Household release certificate fingerprint:

- SHA-256: `ef3fcdade5e321e023effea81fcbaa909403be41516e26cc6021b70848b8cdd4`

Keep this fingerprint matched to `apksigner verify --print-certs` before any
household APK is distributed. Do not record keystore paths, passwords, or key
material here.

## 0.4.3 (versionCode 9) - 2026-07-11

Artifact: `mimeo-android-v0.4.3-vc9-release.apk`

- Includes account-scoped local-state isolation from PR #455, so persisted
  local state does not cross account or server boundaries.
- Includes stale-library response guards from PR #456, preventing late
  responses from replacing newer library state.
- Advances `versionCode` beyond the prior signed 0.4.2 (versionCode 8) APK so
  household devices can install this release as an update without clearing app
  data.

Backend: no contract dependency.

## 0.4.2 (versionCode 8) - 2026-07-10

Artifact: `mimeo-android-v0.4.2-vc8-release.apk`

- Restores the visible reader viewport after leaving and reopening an article
  and after app restart, including articles outside the local Now Playing
  session.
- Keeps persisted viewport state isolated across account or server changes.
- Advances `versionCode` beyond the signed pilot build line so the APK updates
  an installed versionCode 7 without uninstalling or clearing app data.

Backend: no contract dependency.

## 0.4.0 (versionCode 2) - 2026-07-08

Artifact: `mimeo-android-v0.4.0-vc2-release.apk`

- First signed APK line for trusted household sideloading.
- Adds version discipline for household releases: `versionCode` 2 and
  `versionName` 0.4.0.
- Shows the app version in Settings so an operator can verify installed builds
  without Android Studio.
- Documents the signed APK install/update runbook for household devices.

Backend: no contract dependency.

Known issues:

- No Play Store distribution and no automatic updates; the operator delivers
  APK updates manually.

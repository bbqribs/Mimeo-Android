# Mimeo Android Release Notes

Household release certificate fingerprint:

- SHA-256: `ef3fcdade5e321e023effea81fcbaa909403be41516e26cc6021b70848b8cdd4`

Keep this fingerprint matched to `apksigner verify --print-certs` before any
household APK is distributed. Do not record keystore paths, passwords, or key
material here.

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

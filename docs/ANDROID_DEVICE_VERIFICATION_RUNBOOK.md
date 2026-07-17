# Android device verification runbook

Use `scripts/android-device-verify.ps1` for repeatable physical-device setup,
sign-in, Up Next navigation and evidence capture. The helper favors UI hierarchy
text and bounds over fixed coordinates, so it remains usable across devices and
font/navigation-bar changes.

## Fast path

Connect and manually unlock the device once. The script wakes the display,
attempts USB stay-awake, reports whether the firmware accepted it, attempts only
Android's safe keyguard dismissal, resolves the installed launch activity, and
stops if the secure pattern is still present. It never stores or attempts the
unlock pattern.

```powershell
.\scripts\android-device-verify.ps1 -Action Prepare
.\scripts\android-device-verify.ps1 -Action Status
```

The stock OnePlus 7T (`HD1905`) build used for this ticket rejects shell writes
to the stay-awake setting (`WRITE_SECURE_SETTINGS`) and kills `svc power stayon`.
Its observed screen timeout is 120,000 ms. Enable the Developer Options **Stay
awake** toggle once when running a long device session:

```powershell
.\scripts\android-device-verify.ps1 -Action OpenDeveloperOptions
```

The helper verifies and reports the setting rather than claiming it succeeded.

Use a disposable `test-` account with an alphanumeric password. Keeping the
password alphanumeric avoids `adb shell input text` metacharacter corruption.
Prefer an interactive secure prompt; for unattended local use, set the password
environment variable only for the lifetime of the current PowerShell process.

```powershell
$env:MIMEO_DEVICE_TEST_USERNAME = Read-Host "Existing disposable test-account username"
.\scripts\android-device-verify.ps1 `
  -Action SignInAndOpenUpNext `
  -ServerUrl "https://beh-august2015.taildacac5.ts.net"
```

The helper securely prompts for the password. For unattended local work,
`MIMEO_DEVICE_TEST_PASSWORD` can be injected by an existing secret-aware wrapper
and removed immediately afterward; do not put it in a command, script or shell
history. The helper does not print or write the password and redacts the command
if credential entry fails. Use only a disposable credential because Android's
input process still receives the text transiently.

Before touching a field, the helper runs Android's system `curl` against the
configured server with certificate verification and bounded timeouts. A down
canonical runtime, broken Tailscale route, Wi-Fi problem or certificate failure
therefore stops as `backend unreachable` before credential entry instead of
falling through to the app's broad URL/scheme/certificate error.

It clears each field by observing its current UI length, moving to the end and
sending exactly that many delete keys. The helper requires two stable empty
observations, types, and verifies the exact server/username or masked-password
length before advancing. This prevents surplus delayed deletes from erasing the
first characters of the next value. It detects Android's full `Autofill UI`
window and safely backs out before reading or typing the next field; it also
dismisses the LastPass `Later` prompt when present.

## Up Next lifecycle assertion

For an authoritative session that contains archived `Continuity Zeta`, has the
source label `Corrected conflict authority`, and has no active item:

```powershell
.\scripts\android-device-verify.ps1 `
  -Action VerifyNoActiveUpNext `
  -ExpectedItemTitle "Continuity Zeta" `
  -ExpectedSourceLabel "Corrected conflict authority"
```

The assertion rejects the generic local-empty message, rejects a visible
`Now Playing` section, requires any supplied item/source text, and captures a
screenshot, UI XML, window state, model, dimensions and package version under
`$env:TEMP\MimeoAndroidDeviceVerification`. It deliberately does not export app
storage, settings, tokens or credentials.

## Device-specific evidence

The original verified physical device was a OnePlus 7T (`HD1905`). Its Compose
content hierarchy reported `1080x2287` usable bounds. Recorded fallback centers
are ratios, not unconditional taps:

| Target | X ratio | Y ratio | OnePlus 7T center |
| --- | ---: | ---: | ---: |
| Username | 0.500 | 0.502 | 540, 1148 |
| Password | 0.500 | 0.596 | 540, 1363 |
| Sign In | 0.500 | 0.792 | 540, 1811 |

The current helper uses semantic `EditText` ordering and exact button/drawer
labels first. Add a model/dimension-specific fallback only after capturing UI
XML that demonstrates semantic lookup is unavailable. Run the offline helper
self-test after changing parsing, ratios or input safety:

```powershell
.\scripts\android-device-verify.ps1 -Action SelfTest
```

## Efficient build/device loop

Avoid adding temporary app logging unless UI XML, backend inspection and
existing logcat output cannot isolate the problem. Any source-only diagnostic
change recompiles the large application view model and can cost several minutes.
When source has changed, combine the debug unit/build work in one Gradle task
graph, install once, then prepare the device again because APK installation or a
long build may leave the secure lock screen visible.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
.\scripts\android-device-verify.ps1 -Action Prepare
```

Do not repeatedly alternate task graphs solely to poll progress: Gradle
configuration/KSP work may be repeated. Use the running process output, then run
the release assemble and release lint once after the device fix is stable.

## Lessons retained

- `monkey` is unnecessary for normal launch; resolve the launcher activity and
  use `am start -W`.
- The debug application ID is `com.mimeo.android.debug`, while the activity
  component and Compose hierarchy can still use `com.mimeo.android`; do not use
  the XML package attribute to decide which APK is installed.
- Drawer labels may not expose `clickable=true`; tapping the semantic text bounds
  is reliable.
- Reinstall/build waits can outlast the screen-lock timer. Check keyguard state
  immediately before every visual assertion and stop for manual unlock rather
  than repeatedly dumping the lock screen.
- Verify entered field text and masked-password length before tapping Sign In;
  never infer success from a tap coordinate alone.
- Run the HTTPS preflight from the device, not only the development PC. The two
  network paths are separate evidence, and sign-in should not begin unless the
  device path succeeds.
- A password-manager save sheet can cover the app immediately after successful
  authentication. Treat `Later` as a successful transition marker and dismiss
  it before navigation.
- LastPass can also open a full Android `Autofill UI` between server, username
  and password fields. Detect it from `dumpsys window` and dismiss it with Back
  before dumping hierarchy or sending input.
- Use `wm dismiss-keyguard` only after the operator unlocks or Smart Lock marks
  the device trusted. Never automate a secure pattern.
- Capture evidence outside the repository so verification artifacts cannot dirty
  the ticket branch.

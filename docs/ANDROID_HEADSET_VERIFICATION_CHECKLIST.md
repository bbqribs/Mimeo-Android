# Android real-headset verification checklist (MANUAL)

**Status: MANUAL PROCEDURE — NOT AUTOMATED, NOT RUN BY CI.**

This checklist is executed by a human operator against a physical Android device
and real headset hardware. Nothing in this document is covered by the emulator
lane in `.github/workflows/android-instrumented-ci.yml`, and no CI result may be
presented as evidence that these steps passed.

The execution record in section 6 is the only place a pass/fail claim belongs. It
ships **unrun**. An unrun checklist is recorded as unrun.

## 1. Why this cannot be automated

The instrumented CI lane runs a headless AOSP `x86_64` emulator. That environment
cannot reproduce the behaviour this checklist exists to protect:

- **No real transport hardware.** The emulator exposes no Bluetooth headset, no
  SCO/A2DP route, and no inline-remote button. `KEYCODE_HEADSETHOOK` and the
  media-transport key codes can be injected synthetically, but injection bypasses
  the platform media-router arbitration that the real failures live in.
- **No route-change events from real hardware.** `ACTION_AUDIO_BECOMING_NOISY`
  can be broadcast by hand, but a synthetic broadcast does not exercise an actual
  unplug/disconnect, which is what the safety pause in
  `ANDROID_MC_PLAYBACK_INTERRUPTION_RULES.md` §4 is written against.
- **No competing media-session owners.** The documented ownership failures in §5
  are device-router behaviours: routers that keep headset controls assigned to a
  previously-playing real audio app, and headsets that emit stale explicit
  `PAUSE`/`PLAY` codes for a single physical toggle button. Neither reproduces
  without a second real media app and real headset firmware.
- **No real audio focus contention.** Navigation prompts, alarms and phone calls
  produce genuine `AUDIOFOCUS_*` transitions with device-specific timing. The
  emulator cannot generate them faithfully.

Writing emulator tests that *simulate* these paths would produce synthetic
coverage — green results that do not constitute evidence about headset
behaviour. That is deliberately not done.

## 2. Authority

Expected behaviour is defined by
[`ANDROID_MC_PLAYBACK_INTERRUPTION_RULES.md`](ANDROID_MC_PLAYBACK_INTERRUPTION_RULES.md).
That document reflects the implementation in `PlaybackEngine.kt`,
`PlaybackService.kt`, `AudioInterruptionPolicy.kt` and `MainActivity.kt`.

This checklist verifies that document; it does not extend or reinterpret it. If
an observed behaviour and the rules document disagree, the disagreement is the
finding — do not silently update either one.

## 3. Preconditions

- A physical Android device (`minSdk 26`+). Record model and build.
- A real wired headset with an inline play/pause button, **and** a real Bluetooth
  headset. Several checks below only fail on one of the two.
- A second media app that holds audio focus and publishes a media session
  (any music or podcast player).
- A debug build installed and signed in. Use
  `scripts/android-device-verify.ps1` for repeatable preparation, sign-in and
  Up Next navigation; see
  [`ANDROID_DEVICE_VERIFICATION_RUNBOOK.md`](ANDROID_DEVICE_VERIFICATION_RUNBOOK.md).
- An item with enough text that playback runs for at least a minute.
- `adb logcat` capturing. The relevant tags are `MimeoMediaButton` and
  `MimeoLocusContinue` (rules §9).

## 4. Checklist

Each row cites the rule it verifies. Record the observed result — not the
expected one — in section 6.

### 4.1 Transport routing (rules §2)

| # | Step | Expected |
|---|---|---|
| H1 | Start playback in-app. Press the wired inline button once. | Playback pauses. `handleMediaButtonIntent key=…` logged. |
| H2 | Press the inline button again. | Playback resumes from the same position. |
| H3 | Press and hold / double-press per the headset's mapping. | No crash; unmapped codes are ignored rather than mis-routed. |
| H4 | Repeat H1–H2 on the Bluetooth headset. | Same behaviour as wired. |
| H5 | Use the notification transport controls. | Same engine path; state matches in-app UI. |

Rule under test: all play/pause/toggle inputs converge on the same engine command
path, and notification/headset controls must not bypass engine semantics.

### 4.2 Stale key codes and toggle behaviour (rules §5)

| # | Step | Expected |
|---|---|---|
| H6 | While Mimeo is **playing**, trigger the headset's explicit `PAUSE` code. | Pauses. |
| H7 | While Mimeo is **paused**, trigger the headset's explicit `PAUSE` code again (some headsets emit a stale `PAUSE` for a toggle press). | **Resumes** — state-aware handling, not a no-op. |
| H8 | Mirror H6–H7 with the explicit `PLAY` code. | Symmetric state-aware behaviour. |

This is the documented workaround for headset/router combinations that emit stale
explicit codes for a single physical toggle button. It is the single most
device-specific item here and the least likely to be caught anywhere else.

### 4.3 Media-button ownership (rules §5)

| # | Step | Expected |
|---|---|---|
| H9 | Play in the second media app, pause it, then start Mimeo playback. | Headset buttons target **Mimeo**, not the other app's paused session. |
| H10 | Inspect system media logs during H9. | Routing targets `com.mimeo.android/MimeoPlayback/…`. |
| H11 | Leave Mimeo playing for several minutes without interaction. | Ownership stays with Mimeo; the anchor track keeps it stable. |

The anchor must be an active, effectively silent stream — not a zero-volume
all-zero buffer. Some routers keep headset controls on the previous real audio
app when the new owner publishes only an inaudible zero signal.

### 4.4 Noisy route / disconnect (rules §4)

| # | Step | Expected |
|---|---|---|
| H12 | While playing, physically unplug the wired headset. | Pauses immediately. **No auto-resume.** |
| H13 | Re-plug the headset. | Stays paused until the user explicitly plays. |
| H14 | While playing on Bluetooth, power off / walk out of range. | Pauses. No auto-resume on reconnect. |

Unplug/disconnect is an explicit safety pause. An auto-resume here is a failure,
not a convenience.

### 4.5 Audio focus and interruption (rules §3, §6)

| # | Step | Expected |
|---|---|---|
| H15 | While playing, trigger a navigation prompt (transient, can-duck, `-3`). | Mimeo **pauses** — does not duck. Prompt speaks. Auto-resumes on focus gain. |
| H16 | While playing, fire a timer/alarm (transient, `-2`). | Pauses, then auto-resumes after the interruption. |
| H17 | While **paused**, fire a transient interruption. | Does **not** start playing on focus gain — resume expectation is only set when playback was active at interruption time. |
| H18 | While playing, take a phone call (permanent loss, `-1`). | Pauses and releases focus. **No auto-resume** after the call ends. |
| H19 | Confirm focus codes in logcat during H15–H18. | `onAudioFocusChange focusChange=<code>` matches the case; `autoResumeAfterTransientGain` appears only in H15/H16. |
| H20 | Watch for spurious error toasts across all pause/resume transitions. | No false "TTS error" messaging during expected transitions (rules §8). |

### 4.6 Preview-open ownership while on headset (rules §11)

| # | Step | Expected |
|---|---|---|
| H21 | With item A playing, open item B from Up Next. | B loads as preview only. A keeps playing. |
| H22 | Press the headset button during H21. | Transport still targets **A**, not the previewed B. |
| H23 | Press Play on B explicitly. | Ownership transfers to B. |

### 4.7 Up Next and History over a real session (regression sanity)

| # | Step | Expected |
|---|---|---|
| H24 | Let an item play to completion with the headset connected. | Continuation follows the engine-owned policy; `continueTrigger` / `continueOpenNext` logged. |
| H25 | Open Up Next after completion. | The finished item appears in History; upcoming order is unchanged. |
| H26 | Cold-launch the app with the headset connected and resume. | Up Next and History render as before; no reseed of the active session. |

H24–H26 are **local-session only**. Cross-device History, cross-device reorder
and conflict behaviour are explicitly *not* covered here — they depend on backend
contracts that are not merged, and are the cross-device slice's business.

## 5. Evidence to capture

- Device model, Android build, headset make/model (wired and Bluetooth).
- App version / commit under test.
- `adb logcat` excerpt covering `MimeoMediaButton` and `MimeoLocusContinue` for
  at least H6–H8, H12 and H15–H19.
- A note for every row that failed, including what was observed instead.

## 6. Execution record

Fill this in only when the checklist is actually executed on hardware. Do not
pre-populate it, and do not mark rows from CI or emulator results.

| Field | Value |
|---|---|
| Executed | **NO — never run** |
| Date | — |
| Operator | — |
| Device / build | — |
| Wired headset | — |
| Bluetooth headset | — |
| App commit | — |

| Row | Result | Notes |
|---|---|---|
| H1–H5 | not run | |
| H6–H8 | not run | |
| H9–H11 | not run | |
| H12–H14 | not run | |
| H15–H20 | not run | |
| H21–H23 | not run | |
| H24–H26 | not run | |

**No part of this checklist has been executed.** It is recorded here as an
available procedure, not as completed verification.

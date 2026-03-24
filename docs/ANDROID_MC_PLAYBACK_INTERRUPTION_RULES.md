# Android MC Playback / Interruption Rules

This document defines the expected runtime behavior for Mimeo Control playback when driven by:
- in-app playback controls
- media notification controls
- headset/media buttons
- Android audio-focus changes and interruption events

It reflects the current implementation in:
- `app/src/main/java/com/mimeo/android/ui/player/PlaybackEngine.kt`
- `app/src/main/java/com/mimeo/android/player/PlaybackService.kt`
- `app/src/main/java/com/mimeo/android/player/AudioInterruptionPolicy.kt`
- `app/src/main/java/com/mimeo/android/MainActivity.kt`

## 1) Ownership Model

- `PlaybackEngine` is the single runtime playback owner (item position, speaking state, end-of-item continuation, progress sync triggers).
- `PlaybackService` owns Android media-platform integration (foreground service, `MediaSession`, notification transport controls, headset/media buttons, focus callbacks).
- `PlaybackServiceBridge` connects service transport actions to the same ViewModel/engine commands used by in-app controls.

Rule: all play/pause/toggle inputs must converge on the same engine command path.

## 2) Control Surface Routing

### In-app controls
- Player UI -> `AppViewModel.playbackPlay()/playbackPause()/...` -> `PlaybackEngine`.

### Notification controls
- Notification action sends service intent (`ACTION_PLAY` or `ACTION_PAUSE`).
- `PlaybackService.onStartCommand()` dispatches to `dispatchPlay()` / `dispatchPause()`.
- Service callbacks use `PlaybackServiceBridge` -> same ViewModel playback methods.

### Headset / media buttons
- `MediaSession` receives `ACTION_MEDIA_BUTTON`.
- `PlaybackService.handleMediaButtonIntent()` maps:
  - `KEYCODE_MEDIA_PLAY` -> play
  - `KEYCODE_MEDIA_PAUSE` -> pause
  - `KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_HEADSETHOOK` -> toggle
- Events are handled on `ACTION_DOWN`; `ACTION_UP` is acknowledged and ignored for state transitions.

Rule: notification/headset controls must not bypass engine semantics.

## 3) Audio Focus Policy (TTS-oriented)

No ducking policy: Mimeo pauses speech rather than ducking volume.

Focus change behavior:
- `AUDIOFOCUS_LOSS (-1)`:
  - pause playback
  - release audio focus
  - clear auto-resume expectation
- `AUDIOFOCUS_LOSS_TRANSIENT (-2)`:
  - pause playback
  - keep focus request context
  - set auto-resume expectation only if playback was active at interruption time
- `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK (-3)`:
  - pause playback (no duck)
  - release focus so prompt/navigation speech can run immediately
  - set auto-resume expectation only if playback was active at interruption time
- `AUDIOFOCUS_GAIN (+1)`:
  - reacquire anchor ownership for media buttons
  - auto-resume only if:
    - prior interruption flagged resume expectation
    - an item is still loaded
    - playback is currently paused

Rule: auto-resume happens only for safe transient regain, never for hard loss.

## 4) Noisy-Route (Headset Disconnect) Policy

- On `ACTION_AUDIO_BECOMING_NOISY`:
  - if currently playing: pause + release focus
  - do not auto-resume

Rule: unplug/disconnect is treated as explicit safety pause.

## 5) Service Activation / Session Ownership

- Service playback snapshot is built from engine/session state (`itemId`, `title`, `isPlaying`).
- Service is started when snapshot transitions to an active item (`itemId != null`), and stopped when no active item remains.
- While an item is loaded and playback is active, the service keeps media-button ownership stable with the internal anchor track.
- On transient loss the anchor is stopped; on regain it is restarted.

Rule: media-button ownership should remain with Mimeo during active playback sessions.

## 6) Interruption + Resume Expected Outcomes

### Navigation prompt while listening
1. Focus loss (`-3`) arrives.
2. Mimeo pauses.
3. Prompt speaks.
4. Focus gain (`+1`) arrives.
5. Mimeo auto-resumes if it was playing before interruption.

### Timer/alarm transient interruption
1. Focus loss (`-2`) pauses playback.
2. Focus gain (`+1`) resumes if previously playing.

### Call or permanent takeover
1. Focus loss (`-1`) pauses and releases focus.
2. No automatic resume after call/other app ends.
3. User must manually play to continue.

### Headset disconnect
1. Noisy route broadcast received.
2. Pause immediately.
3. No automatic resume.

## 7) Progress / Continuation Semantics During Interruptions

- Interruption-driven pause uses same pause path as user pause through bridge callbacks.
- End-of-item continuation policy remains engine-owned and unchanged by focus handling.
- Manual-open vs auto-continue semantics are unchanged by this policy.

Rule: interruption handling changes transport behavior only, not queue/progress model decisions.

## 8) Error/UX Guardrails

- Interruption-triggered `tts.stop()` can produce benign engine callbacks; these are suppressed briefly to avoid false "TTS error" user messaging during expected pause/resume transitions.
- Unexpected TTS failures outside suppression window still surface as user-visible errors.

## 9) Debugging Signals

Primary log tag:
- `MimeoMediaButton`
- `MimeoLocusContinue`

Key expected lines:
- `onAudioFocusChange focusChange=<code>`
- `dispatchPause` / `dispatchPlay`
- `autoResumeAfterTransientGain` (only when safe resume rule is satisfied)
- `handleMediaButtonIntent key=<...>`
- `mediaButtonAnchor play/stop`
- `continueTrigger item=<id> ...` (end-of-item continuation decision point)
- `continueOpenNext from=<id> to=<id>` (next-item continuation handoff)
- `bgAutoContinue load start/success/fail ... interactive=<...> locked=<...> background=<...>`
- `audit=... interactive=<...> locked=<...> background=<...>` (service-side state snapshots)

If media buttons route incorrectly, confirm platform routing targets `com.mimeo.android/MimeoPlayback/...` in system media logs.

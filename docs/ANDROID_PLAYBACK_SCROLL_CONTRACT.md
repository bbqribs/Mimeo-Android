# Android Playback Scroll Contract

This contract defines the expected Locus reader scroll behavior during playback and text navigation.

1. Manual scroll must not immediately bounce the reader back to playback progress while the user is actively scrolling.
2. Manual scroll detaches follow mode as soon as the active playback highlight is no longer fully visible (including partial clipping at top/bottom).
3. While detached, normal playback progress must not auto-scroll the reader.
4. Non-reattach external scroll triggers must be consumed while detached and must not reattach follow mode.
5. Tapping Locus in Locus view is an explicit reattach action and must place the active playback highlight at the top anchor so it is immediately visible.
6. Standard playback follow mode scrolls only when the active highlight passes the visible bottom boundary (visible text exhausted), not on every progress tick.
7. FF/RW triggers are distinct from standard playback progress triggers.
8. FF/RW re-centering is allowed only when the target highlight would otherwise be off-screen.
9. If FF/RW target remains visible, no re-centering scroll should occur.
10. FF/RW at the start boundary should clamp to article start (no-op past beginning is not allowed).
11. Search-focus scrolling is independent of playback follow mode and should not silently clear manual detach.

Any intentional change to these rules should be documented in this file and accompanied by test updates.

Regression guard suite (must pass for any reader auto-scroll change):

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.mimeo.android.ui.reader.PlaybackScrollContractGuardTest" --tests "com.mimeo.android.ui.reader.ReaderScrollPolicyTest" --tests "com.mimeo.android.ui.reader.ReaderScrollCooldownPolicyTest" --tests "com.mimeo.android.ui.reader.ReaderVisibleBoundsTest"
```


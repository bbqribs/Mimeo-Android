# Android Playback Scroll Contract

This contract defines the expected Locus reader scroll behavior during playback and text navigation.

1. Manual scroll detaches follow mode.
2. While detached, normal playback progress must not auto-scroll the reader.
3. Non-reattach external scroll triggers must be consumed while detached and must not reattach follow mode.
4. Tapping Locus in Locus view is an explicit reattach action and re-enables follow mode.
5. Standard playback follow mode scrolls only at the transition boundary (when visible text is exhausted), not on every progress tick.
6. FF/RW triggers are distinct from standard playback progress triggers.
7. FF/RW re-centering is allowed only when the target highlight would otherwise be off-screen.
8. If FF/RW target remains visible, no re-centering scroll should occur.
9. FF/RW at the start boundary should clamp to article start (no-op past beginning is not allowed).
10. Search-focus scrolling is independent of playback follow mode and should not silently clear manual detach.

Any intentional change to these rules should be documented in this file and accompanied by test updates.


# Next Goal: Product-Quality BPM Meter v0.5

## Goal

Turn the redesigned native-tempo debug build into a trustworthy daily-use beta by validating BPM accuracy and UI ergonomics against real tracks on a real Android device.

## Execution Plan

1. Delivery and installability
   - Produce a repeatable release folder containing APK, SHA-256 checksum, release notes, and install instructions.
   - Keep filenames stable: `IntegratedBpmMeter-debug.apk`, `SHA256SUMS.txt`, `RELEASE_NOTES.md`.

2. First-run experience
   - Add clear permission status cards for notification listener access and record audio permission.
   - Make Now Playing explain what works after notification access is enabled.
   - Keep internal audio capture visually marked as experimental.
   - Add a short first-run hint that Mic Listen requires speaker playback and will not hear headphones.

3. Smart library validation
   - Save sample 82, 128, 145, and 170 BPM records on device.
   - Confirm automatic categories, double-time hints, Running/Jogging/Cycling filters, Recently Saved, and Manually Verified.
   - Confirm 160/170/180 playlist presets, custom min/max range filtering, and text playlist sharing.
   - Check that category override editing feels quick enough after workout-style saving.

4. BPM accuracy tuning
   - Compare native tempo, Kotlin fallback, and public BPM reference on at least 20 real files using `QA_NOTES.md`.
   - Record failures where half/double normalization chooses the wrong musical feel.
   - Decide whether to vendor full aubio or attempt Essentia as a second native engine.

5. Export planning
   - Add per-track YouTube Music search links as a lightweight v2 path.
   - Keep Google login and YouTube Data API playlist creation for a separate v3 goal.

6. Real-device QA
   - Install debug APK on a real Android 10+ device.
   - Test Tap BPM sound on/off, Library save/edit/delete/sort, file picker metadata, file BPM analysis, Now Playing public lookup, shared audio open, experimental capture, and Settings persistence.
   - Record failures in `QA_NOTES.md`.

7. Design QA
   - Check Measure, Library, and Settings on a small phone and a large phone.
   - Verify long track titles, Korean metadata, public lookup results, and empty states do not crowd or overlap.
   - Decide whether the recommended BPM card should add a waveform/onset confidence visualization after real-device testing.

## Acceptance Criteria

- Fresh clone/build can produce the APK with the commands in `HANDOFF.md`.
- Release folder includes APK, checksum, release notes, and install instructions.
- Unit tests pass.
- Lint passes.
- Debug APK installs on a real Android 10+ device.
- At least three local audio files are analyzed and their candidate behavior is recorded.
- At least one small-phone visual pass confirms the redesigned controls remain reachable.

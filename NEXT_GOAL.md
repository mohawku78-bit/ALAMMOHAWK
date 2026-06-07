# Next Goal: Bpm Now Daily-Use Beta

## Product Direction

Bpm Now is not an automatic BPM oracle.

The core product is:

1. Detect the song the user is currently hearing when Android media access allows it.
2. Give the user a large, fast Tap BPM surface.
3. Save the BPM as a manually verified value.
4. Organize saved tracks into workout-friendly BPM ranges.
5. Treat public BPM and file analysis as references or drafts, not final answers.

This direction should guide every future change. Features that make the app look more automatic but less trustworthy should be deferred unless they clearly support manual confirmation.

## Current Long-Running Work

Polish the daily loop:

1. Measure
   - Keep current media title, artist, artwork, transport controls, Tap BPM, reference BPM, Reset, and Save as the main screen.
   - Keep Tap BPM responsiveness as the highest priority.
   - Keep file analysis secondary and label raw results as estimates.

2. Save
   - Tap and Now Playing saves should be trusted by default.
   - File analysis, mic, and capture results should remain Needs Review until tap-checked.
   - 180 BPM is valid and should stay in High Pace, with half-time feel shown when helpful.

3. Library
   - Make the default Library path simple: choose a BPM range, play linked files, share search links, review tap-check items.
   - Hide source filters, Samsung playlist creation, local file matching, and M3U export behind More tools.
   - Keep record cards compact: BPM, title, artist, category, source, and verification state.

4. Export
   - YouTube Music remains search-link/checklist based for now.
   - Samsung Music remains MediaStore playlist creation when local files can be matched.
   - M3U remains a generic compatible-player export, not the main Samsung path.

5. File BPM
   - Do not overstate accuracy.
   - Prioritize saved verified matches and public references above raw file estimates.
   - Future engine work should be benchmarked against real tracks before UI promises change.

## Next Implementation Steps

1. Finish Library language and layout cleanup.
   - Default: Play and Share links.
   - More tools: Find files, Try Samsung, Open app, M3U file.
   - Tap-check records should be obvious and easy to filter.

2. Add product-level QA coverage.
   - Unit-test category boundaries, trust labels, double-time range matching, and source-filter behavior.
   - Keep UI logic small enough that real-device QA remains practical.

3. Refresh release artifacts.
   - Run compile, unit tests, lint, and assemble.
   - Copy the latest APK into `release/`.
   - Refresh `SHA256SUMS.txt`, `INSTALL.md`, and release notes.

4. Push after every stable checkpoint.
   - Remote: `https://github.com/mohawku78-bit/ALAMMOHAWK.git`
   - Branch: `main`

## Acceptance Criteria

- Fresh clone can build the app with the commands in `HANDOFF.md`.
- `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` pass.
- Measure still prioritizes current media + Tap BPM.
- Library default view is understandable without using advanced export tools.
- File-analysis saves are clearly drafts or estimates unless tap-verified.
- 160, 170, and 180 BPM playlist ranges work, including 70-90 BPM double-time matches.
- Known limitations are documented rather than hidden behind optimistic UI wording.

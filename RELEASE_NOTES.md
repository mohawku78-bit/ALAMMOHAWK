# Bpm Now Debug Build

## Build

- Version: 1.0 debug
- Package: `com.example.integratedbpmmeter`
- App label: `Bpm Now`
- Minimum Android version: Android 10 / SDK 29
- APK: `release/BpmNow-debug.apk`

## Highlights

- Final UI/UX polish for the core product direction: Bpm Now is now framed as a fast Tap BPM confirmation app with workout-range library organization, not an automatic BPM oracle.
- Automatic BPM extraction got a focused accuracy pass: both Kotlin and native tempo engines now consider a low-band kick/bass envelope so loud high-frequency subdivisions are less likely to be mistaken for the main beat.
- Very fast 190-200 BPM candidates are downranked when the half-time pulse has stronger low-frequency support, while 180 BPM is still treated as a valid Running High Pace tempo.
- Added a regression test for a 96 BPM bass pulse with louder 192 BPM treble subdivisions, matching the common “BPM is way too fast” failure mode.
- File Tap now calibrates raw automatic candidates before display, preserving useful half/double alternates while picking the most defensible primary candidate.
- Automatic candidates now show short reason labels such as `Low-band pulse`, `Stable segments`, `Reference family match`, `Possible double-time`, or `Needs tap-check`.
- Direct saved/public reference-family matches are preferred over raw half/double guesses when deciding which candidate to show first.
- Added lightweight drum-focused extraction. The tempo engine now separately scores kick/low drum, snare/clap, and hi-hat transient envelopes instead of relying only on a single full-band onset curve.
- Hi-hat energy is treated mostly as subdivision support, so loud hats are less likely to force a 2x BPM when kick/snare evidence supports the base tempo.
- Added a regression test for a 112 BPM kick/snare backbeat with loud hi-hats to protect the common rock/pop “too fast” failure mode.
- Measure is simplified into the daily-use order: current song card, large album-art Tap BPM area, Reset/Save, compact Reference BPM result, and File Tap entry.
- Library now shows the essential controls first: search, short smart-list chips, 160/170/180/custom BPM range presets, Play, and Share links.
- Advanced Library tools such as source filters, local-file matching, Samsung playlist creation, Samsung Music launch, and M3U export are kept behind `More tools`.
- File Analyze is now presented as File Tap. Automatic BPM output is treated as an estimate/draft, while tap-confirmed BPM remains the trusted saved value.
- Settings is reorganized into Permissions, Tap & Measure, and Advanced sections, with mic/capture described as optional or experimental fallback features.
- The latest build passed Kotlin compile, unit tests, lint, and debug APK assembly, then installed successfully over wireless ADB on Galaxy Tab `SM_T970`.
- Redesigned navigation around three tabs: Measure, Library, and Settings.
- Measure is now centered on Now Playing: current media metadata, manual Tap BPM, direct public BPM lookup, Google search fallback, and pasted web/AI BPM references are the primary workflow.
- File Analyze is kept as a secondary workflow for local files and shared/opened audio.
- File Analyze now automatically checks public BPM references after selected-file metadata is read, while keeping saved verified Library BPM as the preferred source.
- High-confidence public BPM references can be promoted ahead of raw file-analysis candidates when no saved verified BPM exists.
- File Analyze now checks saved Library BPM references for the same title/artist and promotes manually verified Tap/Now Playing/Public reference BPM above raw file-analysis candidates.
- File Analyze results are shown above Preview + Tap so the recommended BPM is visible immediately after analysis; Preview + Tap remains the correction tool below the result.
- File Analyze now warns when the analysis BPM range is fast-only or too narrow, and offers an inline `Reset Analysis` button to restore 60-200 BPM / 90 seconds.
- File Analyze now labels raw file-analysis candidates as estimates, moves `Save Estimate` and `Tap Correct` above long diagnostics, and shows a short tap-check reminder before saving automatic estimates.
- File Analyze no longer shows raw automatic file-analysis confidence as accuracy-like percentages; automatic candidates use `Estimate` or `Check`, while trusted saved/public/tap references keep normal confidence display.
- Library now carries the same trust language forward after saving: raw file-analysis records show `Estimate` and `Needs tap-check` instead of confidence percentages, and the source label reads `File estimate` rather than `Native`.
- Library now includes a `Needs Review` smart list so unverified file-analysis, mic, or capture estimates can be found later and corrected with Tap BPM instead of disappearing into the main saved list.
- Library's BPM playlist panel now keeps the normal user path simple: choose a workout BPM range, play linked local tracks, share YouTube Music search links, and review tap-check items. Samsung playlist creation, local-file matching, app launching, and M3U export are tucked behind `More tools`.
- Hybrid tempo agreement now preserves the native multi-segment agreement score instead of inflating it with candidate confidence.
- Saved-reference matching now retries with a broader title token, fixing punctuation cases such as `She's Electric` where `she electric` did not match the raw SQL title text.
- Now Playing web BPM references can be saved with title, artist, album, and source app package metadata.
- Now Playing automatically checks the current track against the free public BPM lookup path and shows BPM candidates in the app when a match exists.
- Now Playing public BPM candidates can be saved directly with title, artist, album, and source app package metadata.
- Now Playing layout is more compact: the media header shows only song title and artist, Tap controls are reduced to Reset and Save, Reference BPM shows the result without the expanded search/paste workflow, and top-level tabs no longer reserve space for a large title bar.
- Visual polish pass: refreshed the app color system, typography, card surfaces, bottom navigation, and Tap Pad styling so the main measurement screen feels more like a finished music utility than a debug prototype.
- Now Playing song metadata and transport controls live in a compact media card above the Tap Pad, keeping playback controls separate from the low-latency tapping surface.
- Measure now uses an instrument-style BPM panel: album-art background, circular tick gauge, centered BPM readout, internal tap status row, and icon-led Reset/Save controls.
- Measure and File Analyze now keep the screen awake while open, and Library keeps the screen awake while its in-app player is preparing or playing.
- Reference BPM now falls back to matching saved Library records when the free public BPM database has no result, so user-measured tracks can become the app's own reference source.
- Added status-bar-safe spacing for top-level screens after removing the large top app bar.
- Added first-run permission status cards for current media access and microphone listen, including a compact Settings view that shows whether each permission is ready.
- Settings now explains that Mic Listen only works with speaker playback and cannot hear headphone-only audio.
- Added Now Playing media controls for active media sessions: previous, play/pause, and next when the source app exposes transport controls.
- Tap Pad can now use current media album artwork as its background, with a soft blur, dark scrim, and text shadows so the BPM display floats over the cover without fighting the media controls.
- Selected audio files can now be previewed directly in Measure while tapping BPM in the same screen, with Play/Pause, Restart, Reset, Half, Double, Use Tap, and Save Tap controls.
- Alternate candidates and public BPM references are now visually separated so local analysis, selected BPM, and web/database references are easier to understand.
- Saved BPM records now get a smart tempo category automatically: High Pace, Mid Pace, Groove / General, Low / Double-time, or Warm-up / Cool-down.
- 70-90 BPM saves now show double-time compatibility, for example 82 BPM also surfaces as a 164 BPM running cadence option.
- Library now includes smart workout lists for Running 160-180, Jogging 140-159, Cycling 80-100, Double-time 70-90, Warm-up, Recently Saved, and Manually Verified.
- Library now includes a BPM playlist range tool with 160, 170, and 180 preset buckets plus direct min/max BPM input.
- BPM playlist range matching includes double-time compatible tracks, so an 82 BPM track can appear in a 160 BPM-range playlist as 164 BPM.
- The current filtered Library view can be shared as a plain-text Bpm Now playlist for quick manual transfer before YouTube export exists.
- Shared playlist text now includes a YouTube Music search link for each track.
- Library rows now include a YouTube Music shortcut that opens the track search directly in the YouTube Music app when installed, with browser fallback.
- Now Playing saves hidden media URI metadata when the current player exposes it, so Samsung Music/local-player records can later become file-based playlists.
- Library BPM playlist export now includes an M3U8 option for saved records that have a hidden local media URI.
- Existing saved BPM records are cleared on this build's Room v2->v3 migration so newly saved records start with the current source-app and hidden media URI policy.
- Library now has source filters for All sources, Samsung Music, YouTube Music, Local files, and Other apps.
- TXT and M3U8 exports now use the currently visible Library filters, so a Samsung Music + BPM range view can be exported separately from YouTube Music records.
- Library rows show the source app label and an `M3U8-ready` state when a hidden local media URI is available, without exposing the path in the UI.
- Library playlist counts and M3U8 exports now deduplicate records that point to the same local file, so duplicate saved BPM records do not create duplicate playlist entries.
- Added audio-library permission support for Samsung Music M3U8 export: if Samsung Music does not expose a media URI, Library can now run `Find` to match visible Samsung Music records against Android MediaStore and fill hidden local file paths.
- Now Playing saves from Samsung Music also attempt MediaStore local-file matching automatically when audio-library permission has already been granted.
- `Find` now falls back to scanning the whole Android audio library when title-based MediaStore search fails.
- Each Library record now has a manual local-file link action, so a user can attach the exact audio file through the Android file picker when automatic Samsung Music matching is unreliable.
- M3U8 export now saves the playlist file to `Downloads/Bpm Now` without opening a chooser; Samsung Music users should use the dedicated `Samsung` playlist action instead.
- Added Samsung Music playlist export, which writes visible M3U8-ready tracks into Android's system music playlist store so Samsung Music can read them from its Playlists screen when supported.
- Samsung Music export now recreates the target playlist before inserting tracks, preventing duplicate members when the user exports the same BPM list again.
- Added an in-app Samsung Music launcher and compact icon actions for TXT share, local-file matching, M3U8 file save, Samsung playlist creation, and opening Samsung Music playlists.
- Samsung playlist creation now requests the music-library permission directly when needed instead of making the user discover the separate Find action first.
- Samsung playlist creation now also attempts local-file matching for visible Samsung Music records before export, so `Samsung` can be used directly even when some saved media records have not been linked yet.
- Local music matching for Samsung export is now more robust: it handles track-number/artist prefixes in filenames, one-character Korean titles, apostrophe/remaster noise, and gives matching artists priority when multiple local files share the same title.
- Library export actions now use a narrow-screen-friendly two-row grid: `Play`, `Find`, `Samsung`, then `Lists`, `Save M3U`, and `Text`. The generic M3U8 file export is clearly separate from Samsung playlist creation.
- Library now changes `Find` to disabled `Linked` when all visible Samsung Music records already have local file links, and the playlist panel shows the count as `ready` tracks so users do not keep searching unnecessarily.
- M3U8 export now tells the user that Samsung Music does not import shared M3U8 files; real-device testing on `SM_F946N` confirmed Samsung Music is absent from the Android M3U8 open chooser even though other music players appear.
- M3U8 export now saves the file silently to `Downloads/Bpm Now` instead of opening a chooser that can imply Samsung Music should appear; the Samsung-specific path is the dedicated `Samsung` playlist button.
- Library labels the generic export as `Save M3U` and shows a short inline note that Samsung Music may not appear in an M3U share/open flow, so Samsung users should use the dedicated `Samsung` playlist action.
- Library empty filtered views now show `No tracks match this view` directly under the filters, expose a `Clear filters` button, and label filtered counts as `visible` instead of implying the whole library is empty.
- The Samsung/M3U helper copy is shorter: `Samsung creates the playlist. Save M3U stores a file for compatible players.`
- Library now includes an in-app local playlist player for the currently visible BPM-filtered local tracks, with previous, play/pause, next, stop, and progress controls. This gives Samsung Music users a practical fallback when Samsung Music will not import or show external M3U8 playlists.
- The Library player appears as a normal card under the source/list filters, so it remains visible without covering the BPM playlist range controls.
- The Library player now requests Android audio focus before playback, pauses on audio-focus loss, ducks volume when requested, and releases focus on pause/stop so it behaves like a real music utility around other players.
- The BPM playlist actions now put `Play`, `Find`, and `Samsung` above `Lists`, `Save M3U`, and `Text`, making both Samsung-specific paths reachable without horizontal scrolling.
- The Library player queue now follows the current Library filters/search/sort. If the current playing track falls out of the visible BPM list, playback stops and the player switches to a clear queue-changed/no-local-tracks state instead of silently continuing an old list.
- Empty filtered views now disable player Play/Previous/Next controls while keeping Stop available to dismiss the status card.
- Library record rows now use a stacked narrow-screen-friendly layout: BPM/confidence stays on the left, title/artist/category/source metadata stays readable in the main column, and row actions move below the metadata instead of squeezing the song text out of view.
- Library now scrolls as one full screen instead of trapping the saved-track list in a tiny nested list area.
- The bottom navigation has been replaced with a compact custom bar, reducing its visual height while keeping it above the Android system navigation area.
- Saved records can be edited later to override the automatic category and mark or unmark manual verification.
- Manual Tap BPM saves from Measure/File flows are marked as manually verified by default.
- Added a native tempo engine built with Android NDK/CMake, with Kotlin analysis retained as fallback.
- Native tempo now analyzes multiple windows and reports segment agreement, tempo family, and engine warnings.
- BPM candidates are normalized into tempo families so half/double duplicates are less noisy.
- Fast double-time locks are now folded toward a more musical tactus range, so 180-ish detections can recommend the 90-ish family instead of feeling wildly fast.
- Tap BPM with robust averaging, confidence, half/double controls, and save.
- File-linked manual Tap BPM saves the selected file metadata and URI so manually measured tracks appear in Library with the correct song context.
- Low-latency tap pad with haptic feedback and optional short tap sound.
- Now Playing BPM with media session metadata when notification listener access is enabled.
- Automatic Now Playing BPM via experimental internal playback capture, plus Mic Listen fallback for apps that block capture.
- Audio file share/open support: send an `audio/*` file from Samsung Music, Files, or another app to Bpm Now for direct analysis.
- Local audio file analysis with SAF file picker, metadata extraction, automatic analysis after selection, PCM decoding, retry segments, and top 3 BPM candidates.
- File analysis now combines multiple successful file start positions and prefers BPM values that repeat directly across sections, reducing one-section tempo bursts that previously looked too fast.
- Real-device File Analyze verification on `SM_F946N` promoted saved Library BPM for three local files: `She's Electric` 128.2 BPM, `Long Green` 128.2 BPM, and `Dumb Dumb Dumb` 111.5 BPM.
- Public BPM reference lookup using MusicBrainz recording search plus AcousticBrainz `rhythm.bpm` when available.
- Public BPM lookup now retries with broader MusicBrainz queries and treats missing/unstable AcousticBrainz BPM data as "no public BPM" instead of a hard app failure.
- Added Paste Web BPM Answer: paste a Google AI answer, search snippet, or BPM page text and the app extracts BPM/ranges into a usable candidate.
- Web Search fallback button for public BPM pages when the public database has no direct match.
- Pasted web/AI BPM references can be used as the selected candidate or saved with the selected file metadata.
- Room history redesigned as a compact BPM library with search, edit, delete, and BPM/newest/title sorting.
- Saved BPM matching normalizes common title noise such as remaster/version tags before using Library records as current-track references.
- Settings are grouped into Core, Analysis, and Experimental sections; experimental capture controls stay hidden until enabled or active.
- Settings now includes a compact `Best path` guide that explains the preferred flow: play music, tap, compare Reference BPM, then save to Library playlists.
- Settings now has `Reset Analysis`, which restores the analysis range to 60-200 BPM and the segment length to 90 seconds when tuning has drifted into bad ranges.
- Permission copy now clarifies that Mic Listen only works with speaker playback and that automatic capture is optional because many players block it.
- Compact scrollable Now Playing/File/Settings screens so action buttons stay reachable on smaller phones.
- Added adaptive app icon resources and cleaned manifest/lint issues for a more install-ready debug build.
- Added `QA_NOTES.md` with automated verification results and real-device accuracy checklist.

## Install Notes

1. Download `BpmNow-debug.apk` to an Android device.
2. Open the APK and allow installation from the current source if Android asks.
3. For Now Playing BPM, enable notification listener access in Android settings.
4. For file analysis, select audio through the Android file picker.
5. Internal playback capture is experimental and may not work with every music app.
6. If Samsung Music or another app blocks internal capture, use Now Playing > Mic Listen while playing through the speaker.
7. Internal capture and Mic Listen now show explicit read/start failures instead of silently staying idle.
8. If playback capture is blocked, use the Android Share/Open With flow for the audio file; this bypasses app-to-app audio capture restrictions.
9. Public BPM lookup sends track title/artist metadata to public web services when current media lookup runs or the user taps Lookup BPM.
10. Internal playback capture is no longer part of the main measurement flow; it remains experimental in Settings.
11. Smart categories are local-only and editable from Library; YouTube export is intentionally left for a later OAuth/API phase.
12. YouTube Music does not accept the plain text playlist share as a native playlist import target; use the row shortcut/search links for now, or implement OAuth-based YouTube Data API export later.
13. M3U8 export only includes records with a stored local media URI. Some players expose this through media sessions and some do not.
14. This build intentionally clears old saved BPM records during the database migration so the Library starts clean under the new source-app playlist workflow.
15. If Samsung Music records show missing links, open Library, keep the Samsung Music/BPM filters you want, tap `Find`, grant music access, then export after matching completes. If the button says `Linked`, use `Samsung` directly.
16. If `Find` still misses a track, use the row's local-file link button and choose the exact audio file manually.
17. Samsung Music does not appear in the tested Android M3U8 open chooser. Use the `Samsung` playlist button for the Samsung path; the app will request music access from that action if needed.
18. Re-exporting the same Samsung playlist replaces/recreates the app-created playlist instead of appending duplicate tracks.
19. If Samsung Music still hides app-created playlists, use Library `Play` to play the visible BPM-filtered local tracks directly inside Bpm Now.
20. On narrow screens the Library actions are shown as two rows: `Play / Find / Samsung` and `Lists / Save M3U / Text`, so the Samsung paths are visible without horizontal scrolling.
21. M3U8 files are still treated as compatible-player exports. A Samsung Music MIME workaround can make Samsung appear in the chooser, but real-device testing did not prove reliable import/playback, so the dedicated `Samsung` action remains the preferred Samsung Music path.
22. `Lists` opens Samsung Music's Playlists tab directly when the Samsung Music deep link is available; `Samsung` creates or refreshes the app-created playlist first, then opens that Playlists view.
23. `Samsung` now tries to match visible Samsung Music records to local files before creating the playlist, so a separate `Find` tap is no longer required for the common export path.
24. The release folder now keeps stable APK names plus install metadata: `IntegratedBpmMeter-debug.apk`, `BpmNow-debug.apk`, `SHA256SUMS.txt`, `INSTALL.md`, and `RELEASE_NOTES.md`.
25. Added `BpmNow-release-package.zip`, a clean bundle containing only the two APK filenames, checksum file, install guide, and release notes.
26. If file analysis suddenly feels much too fast, use the inline File Analyze warning or Settings `Reset Analysis` to restore 60-200 BPM / 90 seconds.
27. File-analysis BPM is now presented as an estimate; use Tap Correct for the most trustworthy saved value when the groove feels different.
28. File-analysis candidate merging no longer averages half/double tempo families into fake middle BPM values such as 80 + 160 -> 120.
29. Multi-window analysis now favors BPM values repeated across several analysis segments over a single-window burst, reducing fast one-off locks.
30. Kotlin fallback tempo scoring now uses normalized autocorrelation and downranks near-200 BPM subdivision ties, so loud eighth-note patterns are less likely to beat the base tempo.
31. Library rows that need review now show a quick `Verified` action, letting tap-checked estimates leave the Needs Review list without opening the full edit dialog.
32. The Library review action now appears in its own compact strip, keeping row action icons from crowding on narrow screens.
33. Marking a reviewed BPM as verified now asks for confirmation and shows the BPM/title before changing the record.
34. Saving an automatic File Analyze estimate now asks for confirmation and explains that it will enter Needs Review until tap-checked.
35. File analysis now uses cross-section drum-focused consensus: if several parts of the file agree on a BPM, that repeated BPM can outrank a single fast burst from one section.

## Caution

This is a debug APK, not a Play Store signed release. Use it for testing and personal installation only.

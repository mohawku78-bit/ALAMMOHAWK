# Bpm Now Handoff

## Current Deliverables

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK copy: `release/BpmNow-debug.apk`
- Stable release APK copy: `release/IntegratedBpmMeter-debug.apk`
- Release checksums: `release/SHA256SUMS.txt`
- Install guide: `release/INSTALL.md`
- Release notes copy: `release/RELEASE_NOTES.md`
- Clean release bundle: `release/BpmNow-release-package.zip`
- Google Drive desktop copy: `G:\내 드라이브\Integrated BPM Meter\IntegratedBpmMeter-debug.apk`
- Android package id: `com.example.integratedbpmmeter`
- Android app label: `Bpm Now`
- Latest APK SHA-256: `F75A69EFF1618D78F659B8FFF2B42C214D73388907C92F36081BB676D3424A03`
- Latest release ZIP SHA-256: `B64CFB302EAD06017ADA4DDF619D678A272AC227B7D90137010F5F7863E90128`
- GitHub remote: `https://github.com/mohawku78-bit/ALAMMOHAWK.git`
- Git branch: `main`
- Minimum SDK: 29
- Stack: Kotlin, Jetpack Compose, Material 3, MVVM, Room, Coroutines/Flow, Android NDK/CMake

## Implemented Features

- Three-tab navigation for Measure, Library, and Settings.
- Final UI polish pass makes the everyday flow explicit: Measure for current song + Tap BPM, Library for workout BPM ranges, Settings for permissions/advanced controls, and File Tap as a secondary file-listening/tapping workflow.
- Common Compose UI primitives now standardize cards, chips, action buttons, icon buttons, spacing, and compact info rows across the polished screens.
- Measure is ordered as current song card, large album-art Tap BPM pad, Reset/Save, compact Reference BPM result, and File Tap entry.
- Library default controls now prioritize search, short smart-list chips, 160/170/180/custom BPM range presets, Play, and Share links. Source filters, Samsung playlist tools, local-file matching, and M3U export stay behind `More tools`.
- File Analyze is now presented as File Tap. Automatic file-analysis results are estimates/drafts and should be tap-checked before becoming trusted library BPM.
- Settings is grouped into `Permissions`, `Tap & Measure`, and `Advanced`, with mic/capture clearly treated as optional fallback/experimental features.
- Measure is Now Playing first: it reads current media metadata, supports manual Tap BPM, performs direct public BPM lookup, opens a Google BPM search fallback, parses pasted web/AI BPM text, and saves results with media metadata.
- File Analyze is secondary and remains available from Measure or incoming `audio/*` share/open intents.
- Measure has a workbench-style Compose redesign: selected file, analysis status, recommended BPM, confidence, engine diagnostics, save, half/double, and tap correction are grouped by task priority.
- Measure includes selected-file preview playback via Android `MediaPlayer`, plus an in-place Tap BPM pad for manual measurement while listening to the file.
- Tap BPM calculator using nanosecond timestamps, robust interval averaging, confidence, reset, half, double, save, haptic feedback, and optional tap sound.
- Low-latency Compose tap pad using pointer-down timestamps to avoid click-latency feel.
- Room-backed BPM history with search, edit, delete, BPM/newest/title sorting, and compact BPM-first library rows.
- Current media detection with `NotificationListenerService` and `MediaSessionManager`.
- Now Playing screen that links tap BPM results to title, artist, album, package name, and playback state where available.
- Now Playing automatically checks MusicBrainz/AcousticBrainz public BPM data when the detected current track changes, shows BPM candidates in-app, and can save a selected public BPM with the same media metadata.
- Now Playing was compacted for daily use: title/artist-only header, single-line tap stats, Reset/Save controls only, result-only Reference BPM, and no top app bar on main tabs.
- Visual polish pass refreshed the light color scheme, typography, card surfaces, Tap Pad treatment, and bottom navigation to reduce the raw debug-app feel.
- Now Playing metadata and transport controls are separated into a compact media card above the Tap Pad, so controls do not interfere with the low-latency tap target.
- Top-level screens apply status-bar-safe padding because the main tabs no longer use a top app bar.
- Now Playing includes active media-session transport controls: previous, play/pause, and next. These work when the source app exposes controllable `MediaSession` transport controls.
- Tap Pad accepts current media artwork from `MediaMetadata` and renders it as a cropped, softly blurred background with a dark readability overlay and floating BPM text.
- Now Playing Tap Pad uses an instrument-style gauge treatment: circular tick marks, centered BPM value, Tap BPM pill, and an internal tap status/progress row.
- Now Playing Reference BPM chooses public MusicBrainz/AcousticBrainz candidates first, then falls back to matching saved Room records for the same current track.
- Now Playing shows a permission status card when notification listener access is missing, instead of a bare settings button.
- Settings shows compact permission status cards for current media access and microphone listen, refreshes them on resume, and notes that Mic Listen requires speaker playback.
- BPM saves now receive an automatic smart category based on tempo: High Pace, Mid Pace, Groove / General, Low / Double-time, or Warm-up / Cool-down.
- 70-90 BPM records expose a double-time cadence hint such as 82.0 BPM -> 164.0 BPM.
- Library has smart list filters for Running 160-180, Jogging 140-159, Cycling 80-100, Double-time 70-90, Warm-up, Recently Saved, and Manually Verified.
- Library has a BPM playlist range tool: preset 160/170/180 buckets, custom min/max BPM input, double-time matching, and plain-text playlist sharing for the visible filtered list.
- Plain-text playlist sharing includes per-track YouTube Music search links, and each Library row has a YouTube Music search shortcut.
- YouTube Music native playlist creation is not available through Android text sharing; the official route is Google OAuth plus YouTube Data API playlist/search/playlistItems calls.
- Now Playing reads `MediaMetadata.METADATA_KEY_MEDIA_URI` / `MediaDescription.mediaUri` when exposed and stores it in `BpmRecord.fileUri` without showing it in Library rows.
- Library can export visible BPM-filtered records with stored `fileUri` values as a cached `.m3u8` file through FileProvider.
- Room database version is now 3. The v2->v3 migration clears existing BPM records so the Library restarts under the new source-app playlist workflow.
- Library now filters saved records by source app: All sources, Samsung Music, YouTube Music, Local files, and Other apps.
- TXT and M3U8 export names and contents follow the current source-app, smart-list, search, and BPM-range filters.
- Library rows show friendly source labels such as Samsung Music or YouTube Music plus an `M3U8-ready` marker when a hidden local media URI is stored.
- Library local-track counts and M3U8 exports deduplicate saved records that point to the same local file URI.
- Manifest includes audio-library read permission for M3U8 matching: `READ_MEDIA_AUDIO` on Android 13+ and `READ_EXTERNAL_STORAGE` up to Android 12L.
- `LocalAudioResolver` queries Android MediaStore and scores title/artist/album/duration matches so Samsung Music records without media-session URIs can be backfilled with hidden file paths.
- `LocalAudioResolver` now retries with a full audio-library scan when the title/display-name MediaStore query finds no match.
- Library rows include a manual local-file link action backed by `ActivityResultContracts.OpenDocument`; selected audio URIs are persisted when possible and stored as hidden `fileUri` values.
- Library's playlist panel uses a two-row action grid: `Play / Find / Samsung` above `Lists / M3U file / Text`. `Find` requests audio-library permission when needed and updates visible Samsung Music records with matched file paths.
- `M3U file` now saves the generated playlist to `Downloads/Bpm Now` through `MediaStore.Downloads` without opening an Android chooser. This avoids implying Samsung Music should appear for generic M3U files.
- `M3U file` is treated as a generic compatible-player export only. Real-device testing showed Samsung Music does not appear in the Android M3U8 open chooser, so the app now routes Samsung Music users through the dedicated `Samsung` playlist action.
- Library also has `Create Music playlist`, which inserts/updates a playlist in `MediaStore.Audio.Playlists` and writes members through `MediaStore.Audio.Playlists.Members`.
- The visible Library action grid uses `Samsung` for creating/updating the Samsung-readable MediaStore playlist and `Lists` for opening Samsung Music's Playlists tab, so users do not confuse the generic M3U8 file flow with the Samsung playlist flow. `Samsung` now stays enabled for visible records and attempts Samsung Music local-file matching before export.
- Library changes `Find` to disabled `Linked` when the visible Samsung Music records already have local file links, and the playlist panel reports ready-track count after duplicate local URIs are collapsed.
- Library has an in-app local playlist player for the currently visible BPM-filtered local tracks. It uses stored hidden file URIs and provides previous, play/pause, next, stop, elapsed/duration, and progress controls.
- The in-app Library player is the practical fallback when Samsung Music does not import shared M3U8 files or does not show app-created system playlists.
- The in-app Library player requests Android audio focus before playback, pauses on focus loss, ducks on transient duck requests, and releases focus on pause/stop.
- The BPM playlist actions prioritize playback/export above manual min/max fields with a fixed two-row grid: `Play / Find / Samsung` and `Lists / M3U file / Text`.
- The Library player queue syncs with the currently visible Library records. If the user changes filters/search/sort and the current track is no longer visible, playback stops and the player shows `Queue changed; tap Play` or `No local tracks in this view`.
- Empty filtered queues disable Play/Previous/Next while keeping Stop active so the status card can be dismissed.
- Library record rows use a stacked layout so BPM/confidence, title/artist, category/source metadata, and row actions do not compete for the same narrow horizontal space.
- Now Playing Samsung Music saves attempt MediaStore matching automatically when the permission is already granted.
- Library uses a single full-screen `LazyColumn` so filters, playlist controls, and saved tracks scroll together.
- Top-level navigation uses a compact custom bottom bar with a 64dp app nav row plus system navigation inset spacing.
- Library edit dialog supports manual category overrides and manually verified toggling, so the automatic workout grouping is helpful but not forced.
- Room database version is now 3 with migrations for smart categories/manual verification and the source-app workflow reset.
- Now Playing automatic BPM with internal playback capture when allowed by the source app.
- Mic Listen fallback for Samsung Music or other apps that block internal playback capture.
- `ACTION_SEND` and `ACTION_VIEW` `audio/*` support so shared/opened audio files route directly to File Analyze and auto-start analysis.
- Compact scrollable Now Playing, File Analyze, Library, and Settings screens; metadata is grouped so action buttons remain reachable on small devices.
- SAF audio file selection, metadata reading, automatic analysis after selection, PCM decoding, retry-segment analysis, mono conversion, BPM candidate estimation, save, half, double, and tap correction handoff.
- File Analyze checks saved Library BPM references for the same title/artist and promotes trusted manually verified Tap/Now Playing/Public references above raw file-analysis candidates.
- File Analyze now shows status/recommended BPM before Preview + Tap, so results are visible before the correction tool.
- Saved-reference matching retries with a broader title token, fixing punctuation cases such as `She's Electric` where strict SQL `LIKE` did not match the normalized title.
- File-linked manual Tap BPM can add the tapped BPM as the selected candidate or save it directly with the selected file metadata and URI.
- Paste Web BPM Answer parses Google AI/search snippet/web page text for BPM values or ranges and can use/save the parsed value as a public reference.
- Native C++ tempo engine via JNI and CMake, with Kotlin estimator retained as fallback.
- Multi-window native tempo analysis with segment agreement, tempo family display, and engine warnings.
- Tempo family normalization removes obvious half/double duplicate candidates.
- Fast double-time tempo locks are folded toward a musical tactus range before display and saving; this reduces 2x/4x BPM recommendations on dense drum/onset tracks.
- File-analysis candidate merging avoids averaging half/double tempo-family values into fake middle BPM values, and repeated tempos across distinct analysis windows are favored over a one-window burst.
- Kotlin fallback tempo scoring uses normalized autocorrelation and downranks near-200 BPM subdivision ties, reducing cases where loud eighth-note patterns beat the base tempo.
- File Analyze includes a user-triggered public BPM reference lookup through MusicBrainz recording search and AcousticBrainz `rhythm.bpm`.
- Public lookup retries strict and broad MusicBrainz searches, falls back from AcousticBrainz bulk to individual MBID lookup, and exposes a web-search fallback for sources outside the free public API coverage.
- Local reference matching strips common title noise such as remaster/version/live markers before comparing the current media title/artist against saved Library records.
- Google AI/search answers are not scraped automatically; the app keeps the paste parser as a manual fallback because direct Google result extraction is not stable enough for the app.
- Web reference parser tests cover English BPM text, Korean AI-style answers, ranges, and non-BPM text.
- Experimental playback capture using MediaProjection and AudioPlaybackCaptureConfiguration, isolated behind Settings and manual start/stop.
- Settings persistence for tap sound, BPM range, file analysis duration, and experimental capture toggle.
- Adaptive launcher icon, explicit permission lint handling for capture managers, and manifest cleanup for audio file open/share intents.

## Build Commands

Use a JDK 17+ runtime. This machine currently builds with Microsoft JDK 21:

```powershell
$env:JAVA_HOME='C:\Users\svici\.jdks\ms-21.0.10'
.\gradlew.bat --no-daemon :app:compileDebugKotlin
.\gradlew.bat --no-daemon :app:testDebugUnitTest
.\gradlew.bat --no-daemon :app:lintDebug
.\gradlew.bat --no-daemon :app:assembleDebug
```

The first native build may install Android SDK NDK `27.0.12077973` and CMake `3.22.1`.

## Verification Completed

- `:app:compileDebugKotlin` succeeded.
- `:app:testDebugUnitTest` succeeded.
- `:app:lintDebug` succeeded.
- `:app:assembleDebug` succeeded.
- 2026-06-07 final UI polish passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` with Microsoft JDK 21.
- 2026-06-07 latest build installed successfully over wireless ADB on Galaxy Tab `SM_T970`; UI screenshots/dumps verified Measure, Library, Settings, and File Tap after the final polish pass.
- Debug APK was produced at `app/build/outputs/apk/debug/app-debug.apk`.
- APK was copied to Google Drive desktop folder as `IntegratedBpmMeter-debug.apk`.
- 2026-06-06 latest build installed successfully on connected `SM_F946N` via `adb install -r` and launched with `monkey`.
- 2026-06-06 smart category build passed `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`.
- 2026-06-06 source-filter build passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; it installed on `SM_F946N`, launched, migrated the Library to `0 records`, and showed source filters in the Library UI dump.
- 2026-06-06 Samsung Music M3U8 backfill build passed `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; it installed on `SM_F946N` and Library showed the compact `TXT / Find / M3U8` controls.
- 2026-06-06 manual file-link build passed `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; it installed on `SM_F946N`, Library showed the playlist controls, and the filtered view displayed `8 M3U8-ready` records after broader matching.
- 2026-06-06 Samsung playlist creation build passed `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; it installed on `SM_F946N`, Library showed `Create Music playlist`, and tapping it created a system music playlist with 7 tracks.
- 2026-06-06 Samsung Music share-sheet follow-up passed `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; the installed app now exposes compact icon actions, can open Samsung Music directly, and recreates `Bpm Now Playlist` cleanly with 7 non-duplicated MediaStore members.
- 2026-06-06 permission-flow follow-up passed `:app:compileDebugKotlin`, `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; the Samsung playlist action now requests music-library permission directly when needed, installed successfully on `SM_F946N`, and recreated `Bpm Now Playlist` as MediaStore playlist `1000155913` with 7 non-duplicated members.
- 2026-06-06 M3U chooser follow-up passed `:app:compileDebugKotlin`, `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified Samsung Music is absent from the M3U8 chooser, and produced `release/BpmNow-debug.apk` SHA-256 `3DDC7518FE4D28DA1F525107B4F8CBCB216B91ED5CC68DB022E761172239C18E`.
- 2026-06-06 Library player follow-up passed `:app:compileDebugKotlin`, `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified Library `Play` starts the visible local queue, and produced `release/BpmNow-debug.apk` SHA-256 `7DB9273C716F19F8AC1F281D94BA9C761EFE41E0916AFA79EB5B123B7C78F378`.
- UI dump on `SM_F946N` showed the inline Library player card with `She's Electric / Oasis / 128.2 BPM / 1/8 / Playing`, previous, pause, next, stop, progress, elapsed, and duration controls.
- 2026-06-06 audio-focus/action-order follow-up passed `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified the action row order, verified in-app playback still reaches `Playing`, and produced `release/BpmNow-debug.apk` SHA-256 `9584BF964D33FAE4EF03A9233BD17FC3667274BB96EA98448B9DA1038477BEE4`.
- 2026-06-06 queue-sync follow-up passed `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified on a narrow `904x2316` viewport that changing to `Running 160-180` stops an out-of-filter 128.2 BPM track, shows `No local tracks in this view`, disables Play/Previous/Next, and keeps Stop active.
- 2026-06-06 record-row readability follow-up passed `:app:compileDebugKotlin`, `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified on a narrow `904x2316` viewport that the first Library row shows `She's Electric`, `Oasis`, `Groove / General`, `Samsung Music`, and `M3U8-ready`.
- 2026-06-06 Samsung/M3U8 UX follow-up passed `:app:compileDebugKotlin`, `:app:lintDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified Android intent queries do not list Samsung Music for M3U8 open/share targets, verified the narrow Library action row now shows `Play / Find / Samsung` before M3U8/text export, and confirmed tapping `Samsung` focuses `com.sec.android.app.music/.common.activity.MusicMainActivity`.
- 2026-06-06 M3U8 dedupe follow-up passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified the Library panel reports `7 local tracks` for 8 saved records with one duplicate file URI, and verified new MediaStore playlist `Bpm Now M3U8 (4)` id `1000155942` contains 7 unique members.
- 2026-06-06 Samsung Lists follow-up passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified the Library action grid shows `Play / Find / Samsung` and `Lists / M3U8 / Text`, and confirmed tapping `Lists` opens Samsung Music with the `플레이리스트` tab selected.
- 2026-06-06 Samsung auto-match export follow-up passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified the `Samsung` action is enabled in the Library grid, and confirmed tapping it created MediaStore playlist `Bpm Now Playlist` id `1000155950` with 7 members before opening Samsung Music's `플레이리스트` tab.
- 2026-06-06 permission-card follow-up passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified Settings permission cards through UI dump, and refreshed `release/IntegratedBpmMeter-debug.apk`, `release/BpmNow-debug.apk`, `release/SHA256SUMS.txt`, `release/INSTALL.md`, and `release/RELEASE_NOTES.md`.
- 2026-06-06 Samsung linked-state follow-up passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; installed successfully on `SM_F946N`, verified the app DB has 8 Samsung Music records with 7 unique local file links, confirmed `Samsung` created `Bpm Now Playlist` id `1000155955` with 7 MediaStore members, and verified Library shows `7 ready` plus disabled `Linked` instead of an unnecessary `Find` action.
- 2026-06-06 M3U chooser confusion follow-up passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; M3U export now saves silently to `Downloads/Bpm Now`, the Library button reads `M3U file`, and the UI explains that Samsung Music playlists come from the `Samsung` action.
- 2026-06-06 M3U chooser confusion follow-up installed on `SM_F946N`; UI dump verified `M3U file`, the Samsung/M3U explanatory note, and `7 ready`. Tapping `M3U file` kept focus on Bpm Now and showed the saved-to-Downloads status instead of opening an app chooser.
- 2026-06-06 File Analyze saved-reference follow-up passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; installed on `SM_F946N`; verified `She's Electric` -> 128.2 BPM, `Long Green` -> 128.2 BPM, and `Dumb Dumb Dumb` -> 111.5 BPM are promoted as saved Library BPM references above raw file analysis.
- 2026-06-06 File Analyze public-reference follow-up passed `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; installed on `SM_F946N`; UI dump verification was blocked by AOD/keyguard returning `com.android.systemui` only.
- File Analyze now starts public BPM lookup automatically after selected-file metadata is read. Trusted saved Library BPM remains preferred, and high-confidence public BPM can beat raw file analysis only when no saved verified BPM exists.
- File Analyze now detects risky analysis settings, such as fast-only or too-narrow BPM ranges, and shows an inline warning with `Reset Analysis` to restore 60-200 BPM / 90 seconds.
- File Analyze now presents raw file-analysis candidates as estimates rather than verified BPM. Trusted saved/public/tap references keep normal BPM language, while raw candidates use `Auto file estimate`, `Selected Estimate`, `Save Estimate`, and a short tap-check reminder.
- Saving an automatic File Analyze estimate now asks for confirmation and explains that the record will remain in Needs Review until tap-checked.
- Hybrid tempo agreement now preserves native multi-segment agreement instead of replacing low agreement with high candidate confidence.
- Library now labels generic M3U export as `Save M3U` and explains that Samsung Music may not appear in the M3U share/open flow. Use `Samsung` to create/update the Samsung Music/MediaStore playlist, then `Lists` to open Samsung Music's Playlists tab.
- Local Audio matching is now extracted into `media/LocalAudioMatcher.kt` with unit tests for filename prefixes, same-title artist preference, one-character Korean titles, apostrophe/remaster noise, and loose-overlap rejection. This supports Samsung Music local-file matching before playlist export.
- Measure/Tap/File Analyze now keep the screen awake while open, and Library keeps it awake while its in-app player is preparing or playing.
- Latest APK installed successfully on real device `SM_F946N` (`R3CW70KPD4M`). UI dump verified Measure content and Library export controls, including `7 ready`, `Samsung`, `Save M3U`, and the Samsung Music share-sheet warning note.
- Real-device behavior verified after install: `Save M3U` created `Download/Bpm Now/Bpm Now M3U8 (6).m3u8` with 7 tracks without opening a chooser; `Samsung` opened Samsung Music and created MediaStore playlist `Bpm Now Playlist` id `1000155983` with 7 members; Measure reported Bpm Now as `mHoldScreenWindow`.
- Real-device Library BPM preset verification on `SM_F946N` confirmed `160` shows an empty `160-169 BPM / 0 ready` view with exports disabled, while `180` shows `180-189 BPM / 1 ready`; after scrolling, the matching row `Car Jamming / The Clash / 185.9 BPM / Samsung Music / M3U8-ready` is visible.
- Library empty-filter UX now shows `0 visible`, `No tracks match this view`, and a visible `Clear filters` button directly under the filters. Tapping `Clear filters` on `SM_F946N` returned the Library to `8 records` and `Pick a preset or enter min / max / 7 ready`.
- Settings first-run UX now shows a compact `Best path` card and clearer media/mic permission copy. Real-device QA on `SM_F946N` confirmed `Reset Analysis` restored a drifted `200 - 240 BPM / 105 seconds` setup back to `60 - 200 BPM / 90 seconds`.
- File Analyze analysis-range warning QA on `SM_F946N` confirmed default `60 - 200 BPM` shows no warning, forced `186 - 200 BPM` shows `Analysis range may be biased`, and inline `Reset Analysis` removes the warning.
- File Analyze estimate-label QA on `SM_F946N` confirmed `She's Electric` opens through `content://media/external/audio/media/1000000806`, promotes the saved 128.2 BPM reference, labels raw alternates as `Auto file estimate`, and shows `Selected Estimate`, `Save Estimate`, and `Tap Correct` before compact diagnostics when a raw candidate is selected.
- File Analyze final estimate-badge QA on `SM_F946N` confirmed raw alternates now show `Auto file estimate` with `Estimate` instead of accuracy-like `95%`/`100%`, and selecting a raw candidate shows `Selected Estimate`, `Estimate`, `Auto estimate. Tap-check before saving.`, `Save Estimate`, and `Tap Correct`.
- Library saved-record trust labels now match File Analyze: raw file-analysis records show `Estimate` and `Needs tap-check`, experimental mic/capture records show `Check`, and trusted tap/media/public records keep confidence percentages.
- 2026-06-06 Library trust-label follow-up passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; latest APK installed on `SM_F946N`, and UI dump verified existing trusted media records still show percent confidence plus `Verified`.
- Library now includes a `Needs Review` smart list that collects untrusted file-analysis, mic, and capture estimates for later tap-check correction.
- Library rows that need review now show a quick `Mark verified` action in its own compact review strip. It opens a confirmation dialog with the BPM/title before the record leaves Needs Review, so tap-checked estimates can be trusted without crowding the row action icons.
- Library's default BPM playlist panel now emphasizes the daily path: choose a workout BPM range, play linked files, share YouTube Music search links, and tap-check questionable records. Samsung playlist creation, local-file matching, app launch, and M3U export are behind `More tools`.
- Library smart-list and source-filter behavior is now covered by `HistoryLibraryFilterTest`, including double-time workout matching, Needs Review, recent-list capping, and Samsung/YouTube/local/other source separation.
- 2026-06-06 Needs Review follow-up passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; latest APK installed on `SM_F946N`, and UI dump verified `Needs Review` appears after `Manually Verified` in the Library smart-list filter row.
- 2026-06-06 file-analysis merge follow-up passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; latest APK installed on `SM_F946N`, and UI dump verified the Measure screen still shows current media, Tap BPM, Reference BPM, and bottom tabs.
- 2026-06-06 fallback scoring follow-up passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; real-device install was not repeated because `R3CW70KPD4M` stayed in ADB `offline` state after reconnect/server restart attempts.
- 2026-06-07 Library quick-verify follow-up passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; real-device install was not repeated because `R3CW70KPD4M` remained in ADB `offline` state.
- 2026-06-07 Library review-strip follow-up passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; real-device install was not repeated because `R3CW70KPD4M` remained in ADB `offline` state.
- 2026-06-07 verification confirmation follow-up passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; real-device install was not repeated because `R3CW70KPD4M` remained in ADB `offline` state.
- 2026-06-07 File Analyze estimate-save confirmation follow-up passed `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; real-device install was not repeated because `R3CW70KPD4M` remained in ADB `offline` state.
- Latest release APK SHA-256 is `F75A69EFF1618D78F659B8FFF2B42C214D73388907C92F36081BB676D3424A03`.
- Latest clean release bundle SHA-256 is `B64CFB302EAD06017ADA4DDF619D678A272AC227B7D90137010F5F7863E90128`.
- `QA_NOTES.md` records automated checks and real-device checklist.

## Known Limitations

- Google Drive connector in this Codex session does not expose raw binary upload for APK files.
- The local Google Drive desktop account appears different from the Codex Google Drive connector account, so connector search may not find the APK.
- File analysis uses a practical energy/onset autocorrelation estimator; real-device tuning with multiple music files is still needed.
- Public BPM lookup depends on free public database coverage and may return no match even when Google search pages show an answer.
- YouTube/YouTube Music export is not implemented yet; the realistic path is a later Google OAuth + YouTube Data API playlist export with user confirmation for uncertain matches.
- The current YouTube Music shortcut only searches the track. It does not add the track to a YouTube Music playlist automatically.
- Samsung Music/M3U8 export depends on the source player exposing a usable media URI. If a saved media record has no `fileUri`, it is omitted from M3U8 output.
- If Samsung Music exposes only title/artist metadata, the user must tap `Find` in Library and grant audio-library permission so MediaStore matching can fill hidden file paths.
- If MediaStore matching still fails, the user can attach the exact file from the Library row's manual local-file link action.
- Samsung Music does not appear for the strict `.m3u8` MIME chooser. It can appear under broader `audio/*` or `application/itunes` intent types, but real-device selection did not prove reliable import/playback. Generic M3U export now saves only; use the `Samsung` playlist button as the preferred Samsung Music creation path. That button attempts file matching automatically; use `Lists` to open Samsung Music's Playlists tab directly afterward.
- If Samsung Music still hides the app-created playlist, use the in-app Library `Play` button. It is intentionally independent of Samsung Music's playlist import behavior.
- Full audio-focus behavioral QA with another actively playing app is still worth doing manually, but the code path now requests/releases focus and handles loss/duck callbacks.
- Source app filtering uses the media session package name stored at save time. It cannot recover source app or media URI data for old records, which is why this build clears previous history.
- Experimental internal playback capture may fail for apps that block capture; it is intentionally kept out of the main Measure flow.
- Mic Listen cannot hear headphone-only playback.
- The current native engine is a compact onset/tempo detector. Full aubio vendoring or Essentia integration remains a separate future accuracy project, though the current merge/scoring logic now reduces half/double fake-middle picks, single-window bursts, and near-200 BPM subdivision ties.
- Real-track BPM accuracy benchmarking remains pending; the latest pass confirmed build, install, release checksums, and Settings permission-card UI on the real device.

## Next Engineer Prompt

Continue from `C:\Users\svici\OneDrive\Documents\SAND\bpm`.

If continuing from another computer:

```powershell
git clone https://github.com/mohawku78-bit/ALAMMOHAWK.git
cd ALAMMOHAWK
```

First verify:

```powershell
$env:JAVA_HOME='C:\Users\svici\.jdks\ms-21.0.10'
.\gradlew.bat --no-daemon :app:testDebugUnitTest
.\gradlew.bat --no-daemon :app:lintDebug
.\gradlew.bat --no-daemon :app:assembleDebug
```

Then proceed with the next goal in `NEXT_GOAL.md`.

# Bpm Now

Bpm Now is an Android Kotlin / Jetpack Compose app for quickly confirming BPM while listening to music.

The product direction is intentionally practical:

- show the current playing song when Android media access is available
- let the user confirm tempo with a large Tap BPM surface
- save verified BPM records
- organize saved tracks by BPM ranges for running, jogging, cycling, double-time, and warm-up use
- treat file analysis and web/public BPM as reference candidates, not guaranteed answers

## Current Focus

Bpm Now is not designed as an automatic BPM oracle. The main flow is:

1. Play music in Samsung Music, YouTube Music, or another player.
2. Open Bpm Now Measure.
3. Tap along with the beat.
4. Save the verified BPM.
5. Use Library filters to build BPM-based workout lists.

## Build

Requirements:

- Android Studio
- JDK 17 or newer
- Android SDK

From the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK will be generated under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- `local.properties` is intentionally not committed because it contains machine-specific Android SDK paths.
- `release/` is intentionally not committed because it contains generated APK and ZIP artifacts.
- File BPM analysis is kept as a draft/reference feature. Tap BPM and manually verified records should be treated as the most reliable values.

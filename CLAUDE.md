# Sharp Ears — Android piano learning app

Read PLAN.md first, then DESIGN-video-mic-chords.md. Implement one phase per session. Stop when its done-when list passes.

Package: com.bigpapituna.sharpears · Device: Samsung Galaxy Z Fold7 (Android 16) · Input: microphone first, USB-C MIDI optional.

## Commands (PowerShell)
- Unit tests: .\gradlew.bat testDebugUnitTest
- Build + install + launch + screenshot: .\scripts\dev.ps1
- Build + install: .\gradlew.bat installDebug
- Launch: adb shell am start -n com.bigpapituna.sharpears/.MainActivity
- Screenshot: adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png out\screen.png
- Logs: adb logcat -d -s AndroidRuntime:E SharpEars:D
- Jank: adb shell dumpsys gfxinfo com.bigpapituna.sharpears | findstr Janky
- WAV inject (debug): adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es wav <path> --es song <id>
- Lint: .\gradlew.bat lint

## Rules
- Stdlib and platform APIs first. New dependency only if PLAN.md names it.
- engine/ has no Android imports. Test it on the JVM.
- One test per non-trivial logic piece. JUnit only.
- Log every NoteEvent and transport state change with tag SharpEars at debug level. Assert on logs, not pixels, when possible.
- Mark deliberate shortcuts: // ponytail: <ceiling>, <upgrade path>
- Commit once per phase: "P<n>: <summary>". Never commit keys or signing files.

## Key paths
app/src/main/java/com/bigpapituna/sharpears/{engine,audio,ui,data} · assets/songs/index.json · scripts/dev.ps1 · out/screen.png

## Phase status
- P0 bootstrap: done 2026-10-07. Build, unit test, lint (0 errors), emulator screenshot all pass.
- P1 song model + SMF parser: done 2026-10-07. 6 JVM tests; Für Elise (Mutopia, PD) parses to 905 notes, 2:10, both hands.
- P2 keyboard + sampler: done 2026-10-07 except Roy's ear check on the Fold7. 10 JVM tests + 1 multi-touch instrumented test (connectedDebugAndroidTest). Samples: VSCO 2 CE upright, CC0, scripts\samples.ps1.
- P3 falling notes + listen mode: done 2026-10-07 on the emulator. 18 JVM tests; lane positions within 1 px at 3 timestamps (scripts\lanecheck.py); 0.11% janky frames. Open: 30 s frame-time check on the Fold7 (phone was locked). Emulator p95 frame time is 23 ms, over the 16 ms budget; judge on the Fold.
- Note for P4: fitRange caps at 5 octaves, so Für Elise hides 5 of 905 notes (A1..E7 vs C2..C7). Wait mode must auto-satisfy notes outside the keyboard range.

## Environment notes
- JDK: Android Studio's bundled JBR (set in gradle.properties). For gradlew.bat in a fresh shell: $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
- adb is not on PATH: $env:Path="$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
- Emulators: Pixel7_API37 (phone), Fold_API37 (Pixel 9 Pro Fold profile, stand-in for the Galaxy Z Fold7). Start: & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel7_API37
- Real device: Galaxy Z Fold7 SM-F966W, Android 16 (API 36), adb serial RFCY7029SWM, authorized 2026-10-07. Cover screen 1080x2520. Two displays: pass -d <display-id> to screencap when unfolded.
- With the Fold7 and an emulator both attached, set $env:ANDROID_SERIAL (e.g. emulator-5554) before dev.ps1 or gradlew installDebug.
- Player debug launch: adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es song fur-elise --el seek <ms> --ez dump true. It logs laneNote rectangles; then: python scripts\lanecheck.py out\lane.png out\lane.log
- Lint caches its analysis; after changing a non-source file, run: .\gradlew.bat lintAnalyzeDebug --rerun lintDebug

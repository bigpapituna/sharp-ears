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
- P2 keyboard + sampler: done 2026-10-07; Roy confirmed sound on the Fold7. 10 JVM tests + 1 multi-touch instrumented test (connectedDebugAndroidTest). Samples: VSCO 2 CE upright, CC0, scripts\samples.ps1.
- P3 falling notes + listen mode: done 2026-10-07 on the emulator. 18 JVM tests; lane positions within 1 px at 3 timestamps (scripts\lanecheck.py); 0.11% janky frames. Fold7: p95 frame 12 ms, 1.8% janky (emulator p95 23 ms is emulator overhead).
- P4 wait mode, loop, hands: done 2026-10-07. 28 JVM tests. Emulator E2E via adb: wrong key -> WRONG + redFlash, no advance; 14 groups walked; chord A2+A4 needs both; A-B loop wraps (fixed: B at playhead); Left-hand mode waits only for left notes, right notes dimmed and auto-played. Notes outside the keyboard range are auto-played (Transport.playable).
- P5 MIDI input: done 2026-10-07. 6 parser tests (34 JVM total). Verified with Roy's Yamaha digital piano (USB, "Yamaha Corporation Digital Piano") on the Fold7 over Wi-Fi adb: notes parse, active sensing ignored, wait mode advances and flags wrong notes. Open: hot-plug (unplug/replug adapter) check. MIDI notes do not play through the sampler (the piano sounds itself). The Yamaha also exposes USB audio capture, a possible clean input for P6 experiments.
- P6 mic matcher: done 2026-10-07. Verified on Roy's Yamaha via the Fold7 bottom mic with MIDI as judge (session-155837, local only): 33/33 scale notes heard, 0 false strikes, median 113 ms key-to-detection (includes capture latency). Profile build on the Fold: mic thread 6.5-9% of a core, 0 janky frames. Emulator injection: 13/13 groups.
  Design notes: decimated bands (12/24/48 kHz, 2048-sample windows), cached "before" windows, taper 0.25, per-partial fresh rise, semitone/neighbour/sub/odd/sharpness checks, chord-aware probes (group from Transport.listenGroup).
  Fold quirks: capture path is VOICE_RECOGNITION (no UNPROCESSED); AudioRecord.getTimestamp framePosition is offset (put MIDI truth 1.5 s early), so MicInput maps MIDI to audio with its own read clock.
  Mic wrong-note flashes are OFF by default (MicInput.wrongFlash, switch in Microphone setup): Roy's room has talking/TV and loudness jumps cannot be told from wrong notes (57 false flashes in 54 s). Upgrade path: name the wrong note via a semitone-grid search.
  Open: back mic vs bottom mic comparison; labelled fixtures (scale-left etc.) never recorded, only the live session with truth.
- P7 chords: done 2026-10-07. engine/Chords.kt (15 types x inversions x 12 roots, fingering, 4 progressions in C/G/F/D/A with voice leading; 4+ note chords drop the right-hand root), ChordDrill, ui/ChordsScreen.kt (Dictionary with fingering labels + Play, Practice by touch/MIDI/mic with streaks, Progressions -> player in wait mode). Mic: octave doublings inside a chord are credited from the lower note; Transport.chordSlack = 1 with the mic (4+ note chords pass with one note unheard). Emulator: practice by touch 4/4 with a correct miss, by injected audio 3/3; progressions by injected audio 4/4, 4/4, 12/12. Debug extras: --es screen chords --es tab practice|progressions, --es song prog:<i>:<key>.
- Mic chord fix (2026-10-07, from Roy's chord-practice session session-163012, no MIDI): the "two partials must jump" rule rejected real chord notes on his piano (only the fundamental clears once chord-mates' shared overtones are set aside). Now one fresh partial counts if it is the fundamental, a hammer attack (>= 3 dB broadband jump) came within 180 ms, and the note's total power is not falling (that last guard stopped a false C4 in the scale recording). Chord practice listens in the shown octave and the octave below via NoteMatcher.groups (one group per octave; a single group buried upper notes in their lower octave); strikes that began before the chord appeared are ignored. Replay of Roy's session: Bb 18.5 -> 7.8 s, Em 23.2 -> 3.6 s, F#m 13.9 -> 10.8 s. Fold: mic thread 9-11% of a core in chord practice (profile build).
- Build types: debug (all debug tools), profile (release-optimized, debug-signed: .\gradlew.bat assembleProfile, then adb install -r app\build\outputs\apk\profile\app-profile.apk) for CPU/frame measurements. Debug builds run ~2x slower.
- Mic session (Roy, phone on the music stand, MIDI connected, debug build): home -> Microphone setup -> Allow microphone -> for each mic (Bottom, Back) record: Right-hand scale, Left-hand scale, Chords, Für Elise opening, 30 s of silence (tap the label, play, tap Stop). Then: .\scripts\pull-fixtures.ps1 ; .\gradlew.bat testDebugUnitTest --tests *FixtureTest*
- Live mic check: home -> "Mic check: C major scale" (mic on, wait mode, MIDI = judge). Then: adb logcat -d -s SharpEars:D > out\miccheck.log ; python scripts\miccheck.py out\miccheck.log
- Debug injection: --es song fur-elise --es mode wait --es hands right --es inject 'synth:76,75,76@700' (or a WAV path on the device, 48 kHz PCM16 mono).
- Input path: Keyboard (touch), MidiDevices.events and MicInput.events (SharedFlow, emitted on a binder thread, collected on main) -> PlayerScreen.keyStruck -> Transport.noteInput. P6 (mic) feeds the same keyStruck.

## Environment notes
- JDK: Android Studio's bundled JBR (set in gradle.properties). For gradlew.bat in a fresh shell: $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
- adb is not on PATH: $env:Path="$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
- Emulators: Pixel7_API37 (phone), Fold_API37 (Pixel 9 Pro Fold profile, stand-in for the Galaxy Z Fold7). Start: & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel7_API37
- Real device: Galaxy Z Fold7 SM-F966W, Android 16 (API 36), adb serial RFCY7029SWM, authorized 2026-10-07. Cover screen 1080x2520. Two displays: pass -d <display-id> to screencap when unfolded.
- Wi-Fi adb (frees USB-C for the MIDI adapter): with the phone on USB run `adb -s RFCY7029SWM tcpip 5555` then `adb connect 10.0.0.171:5555`; serial becomes 10.0.0.171:5555. Lost on phone reboot; the IP can change.
- With the Fold7 and an emulator both attached, set $env:ANDROID_SERIAL (e.g. emulator-5554) before dev.ps1 or gradlew installDebug.
- Player debug launch: adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es song fur-elise --el seek <ms> --ez dump true. It logs laneNote rectangles; then: python scripts\lanecheck.py out\lane.png out\lane.log
- Tap a control by its label: .\scripts\tap.ps1 "Wait"  (uiautomator dump; ~1 s per call). Key centers come from "keyRect <note> = x,y" log lines.
- Lint caches its analysis; after changing a non-source file, run: .\gradlew.bat lintAnalyzeDebug --rerun lintDebug

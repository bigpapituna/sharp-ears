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

## Debug routes (2.10.1: keyed builds)
Every `--es`/`--ez`/`--ei`/`--ef`/`--el` launch extra the sections below list (`--es screen ...`, `seed`, `simple|layout`,
`today`, `midi`, `say`, `kidseed`, `--ez gate`, the demos' extras) acts only on a debug build and only when
engine/DebugRoutes.kt lets the launch through (security review 2026-10-22, finding 1). MainActivity asks it in onCreate and
onNewIntent (`routesAllowed`) and strips a refused launch's extras before anything reads them (`withoutDebugExtras`; only the
weekly brief notification's `brief_profile`/`brief_period` stay), so TheoryScreen and the demos (EmailDemo, BriefDemo,
PupDebug, SkinDemo, WorldDemo) never see them. DebugRoutesWiringTest fails if a new file reads intent extras.
- Keyless builds (no `DEBUG_ROUTE_KEY` in secrets.properties: every worktree and emulator build agents make): as before,
  `adb -s emulator-5558 shell am start -n com.bigpapituna.sharpears/.MainActivity --es screen settings`.
- Keyed builds (Roy's, and the ones he gives testers): `.\scripts\set-debug-key.ps1` once (a random 24-character key into
  secrets.properties, never shown; it prints "Saved."; `-Remove` takes it out), rebuild, then every route through
  `.\scripts\adb-route.ps1 -Serial <serial> --es screen settings` (it adds `--es dk <key>` from secrets.properties without
  printing it; the serial is required, `-Serial` first or `$env:ANDROID_SERIAL`). Without the key the app opens as a plain
  launch and logs `debug routes: extras ignored (no_key): screen` (`wrong_key` for a wrong one).
- Never, keyed or not: a VIEW intent (a `sharpears://` link, a browser's `intent://`), any action but none or MAIN, a launch
  from the launcher (its category, source bounds or a shortcut id) or a browser (BROWSABLE), a release or profile build.
  Logged as `extras ignored (action)` / `(launcher)`.
- BuildConfig holds `DEBUG_ROUTE_KEY_SHA256` (the key's SHA-256), never the key. To try a keyed build without touching
  secrets.properties: `.\gradlew.bat installDebug -PdebugRouteKey=test123`, then `--es dk test123` (the property is read
  before the file); install again without it to go back to keyless.

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
  Mic wrong-note flashes were OFF by default here (on by default since 2.1, see below; MicInput.wrongFlash, switch in Microphone setup): Roy's room has talking/TV and loudness jumps cannot be told from wrong notes (57 false flashes in 54 s). Upgrade path: name the wrong note via a semitone-grid search.
  Open: back mic vs bottom mic comparison; labelled fixtures (scale-left etc.) never recorded, only the live session with truth.
- P7 chords: done 2026-10-07. engine/Chords.kt (15 types x inversions x 12 roots, fingering, 4 progressions in C/G/F/D/A with voice leading; 4+ note chords drop the right-hand root), ChordDrill, ui/ChordsScreen.kt (Dictionary with fingering labels + Play, Practice by touch/MIDI/mic with streaks, Progressions -> player in wait mode). Mic: octave doublings inside a chord are credited from the lower note; Transport.chordSlack = 1 with the mic (4+ note chords pass with one note unheard). Emulator: practice by touch 4/4 with a correct miss, by injected audio 3/3; progressions by injected audio 4/4, 4/4, 12/12. Debug extras: --es screen chords --es tab practice|progressions, --es song prog:<i>:<key>.
- Mic chord fix (2026-10-07, from Roy's chord-practice session session-163012, no MIDI): the "two partials must jump" rule rejected real chord notes on his piano (only the fundamental clears once chord-mates' shared overtones are set aside). Now one fresh partial counts if it is the fundamental, a hammer attack (>= 3 dB broadband jump) came within 180 ms, and the note's total power is not falling (that last guard stopped a false C4 in the scale recording). Chord practice listens in the shown octave and the octave below via NoteMatcher.groups (one group per octave; a single group buried upper notes in their lower octave); strikes that began before the chord appeared are ignored. Replay of Roy's session: Bb 18.5 -> 7.8 s, Em 23.2 -> 3.6 s, F#m 13.9 -> 10.8 s. Fold: mic thread 9-11% of a core in chord practice (profile build).
- False-green fix (2026-10-07, from session-164847: 14 notes marked green with nothing played): faint wobbling leftovers from the previous chord and room-noise flukes passed every relative check because the fixed floor (-66 dB) sat far below Roy's room (-45..-60 dB). Final gate in NoteMatcher: the lowest partial must jump >= 18 dB (a fresh note out of quiet) or the strike must rise >= 10 dB above the room background (quietest 5% of the last 20 s of hop loudness; a 5 s window measured the music itself and dropped real notes). Replay: 14/14 false greens rejected, all real strikes kept, scale 33/33. Test notesBuried... replaced by faintRingingNoteWobblingInARoomDoesNotCount (210 false strikes without the gate, 0 with).
- UI redesign + icon (2026-10-07): ui/Theme.kt (SharpEarsTheme: dark stage, orange right hand/primary, blue left hand/secondary, green good), adaptive icon res/drawable/ic_logo.xml (concept A, sharp inside an ear; B and C are in the OneDrive piano-app folder). Library is the home screen.
- P8 library + tutorials + import: done 2026-10-07 (emulator; Roy's phone was away). 12 bundled songs in assets/songs (Mutopia PD MIDIs; Twinkle, Ode to Joy, Happy Birthday, Amazing Grace, Canon written by scripts\gen_songs.py; Moonlight is CC BY-SA 2.5). Gymnopedie No. 1 is not on Mutopia: replaced by Chopin Prelude Op. 28 No. 4. Clair de Lune is the full piece (hard), not simplified. index.json: 2 oEmbed-checked tutorial videos per song (checkedOn 2026-10-07) + the Alicia entry (file user/alicia.mid, locked until imported). Import: SAF OpenDocument -> files/user/ (+ files/user/index.json for new songs); a broken file shows "Could not import" (LibraryContentTest truncates fur-elise.mid). Tutorial panel (ui/Tutorial.kt, android-youtube-player core 13.0.0): video follows the song once aligned with "First note" (anchor in prefs per song+video), pauses while wait mode holds, seeks on drift > 0.4 s, muted while the mic listens without headphones, Open in YouTube intent. Emulator: aligned at 7.17 s, song seek -> one video seek 15.48 -> 26.16 s.
- P9 polish: done 2026-10-07. data/Prefs.kt: resume position, played flag, best Perform score per song; key labels (off / C keys / all white keys), app sound; Settings screen with attributions. Survives force-stop ("prefs restored" log line). Every key has a TalkBack node (Keyboard KeySemantics; uiautomator dump lists C2..C5 and all controls). lint: 0 errors. Profile APK 22.9 MB (debug 30.5 MB).
- Fold tabletop: done 2026-10-07. ui/Posture.kt (androidx.window 1.5.1): HALF_OPENED + HORIZONTAL fold -> lane (+ video) above the hinge, bar + keyboard below, keyboard under the lane so columns line up. Verified on Fold_API37 (adb emu posture 2 with the display rotated: hinge at y=1038) and with --ez tabletop true. Android 16 ignores the landscape lock on large screens, so on the inner display the player follows the phone's rotation.
- Chord lessons: done 2026-10-07. engine/Lessons.kt (Leitner boxes 0..5 at 0/1/2/4/7/14 days, due queue, unlock at average box 3, chord labeler per bar, song packs), assets/lessons/*.json (6 packs, scripts\gen_lessons.py), data/LessonStore.kt (mastery prefs, session), Chords > Lessons tab. Every library song gets a "Chords of <song>" pack. Deviation: no inversion level (DESIGN 4.4 level 2). Emulator: 5/5 chords by injected audio in a session.
- P10 Perform mode: done 2026-10-07. engine/PerformScore.kt (good ±150 / ok ±300 / miss, bias, longest streak; mic latency = Strike.delayMs moved back), Transport Mode.PERFORM with a 2 s lead-in, recap dialog, best score saved for runs from the top. Emulator: Twinkle right hand by injected audio 42/42 good (bias -56 ms); with two notes left out 40/42, 95%, streak 22.
- Learn course (2026-10-07): engine/Course.kt holds 27 lessons in 4 levels (Basics, Beginner, Intermediate, Advanced) as data: text plus an exercise in token notation (C4:1 r:1 C4+E4+G4:2) or a library song. Exercises play in the player; wait-mode ones finish at the end, Perform ones at their pass mark (Prefs lesson:<id>). Learn is a bottom-bar tab; the chord SRS tab is now called Daily. Debug: --es screen learn, --es lesson <id>.
- Guidance batch (2026-10-07, emulator only; Roy's phone not reachable). 120 JVM tests, lint 0 errors.
  1. Voice: audio/Voice.kt (platform TextToSpeech, lazy, released in onDestroy; Prefs voice, Settings > Voice). While the mic listens without headphones, lines wait in engine/SpeechQueue (bounded, 30 s expiry) and are spoken when MicInput stops (onStopped); the player's mic waits for Voice.holdsMic. Manifest needs the TTS_SERVICE <queries>. Evidence: "voice: held while the mic listens" then "voice: mic stopped, speaking 1 held line(s)" with an injected mic; voice off logs "voice: (off)". SpeechQueueTest.
  2. Step lessons: engine/Course.kt lessons are 3 to 5 steps (Read, Listen, Play, FromMemory, Perform); song lessons hear and try the opening phrase (written in token notation) and end on "Open the song". ui/LessonScreen.kt embeds PlayerScreen(embedded = Embedded(...)): no orientation lock, one row (play/pause, fixed hand and mode, mic), labels hidden from memory; a step starts itself once the voice finishes; the mic listens only while the step runs. Evidence: fingers-right walked Listen -> Play (injected mic, 9/9) -> FromMemory (touch) -> "lesson fingers-right done"; beat Perform failed at 0% and stayed, passed at 100% with injection. CourseTest. Deviation: the embedded row also has the mic toggle (otherwise lessons could not turn the mic on).
  3. Feedback: engine/Feedback.kt (RunLog in Transport, Report, feedbackLine, spokenNote). Evidence: touch F for F# x3 -> "You missed F sharp three times. Try that bar slowly."; Perform early -> "A little rushed: you were 143 ms early on average." (spoken as milliseconds). FeedbackTest. Limit: the mic matcher cannot name a wrong note (MicEvent.Wrong has no pitch), so only touch and MIDI mistakes are named.
  4. Chord games: engine/ChordGames.kt + ui/ChordGamesScreen.kt, Chords > Play (first tab, now the default). Prompt and chord play with the mic off, then the mic listens. Evidence: Build it 9/10 by touch, "New best" saved (Prefs game:build:0); Hear it answered by injected mic (E minor, octave below); Major or minor right and wrong answers explained. ChordGamesTest.
  5. Metronome and backing: engine/Metronome.kt (+ clickSamples), audio/Clicks.kt (SoundPool, two synthesized WAVs in cache). Metronome pill on every song (Perform gets a one-bar count-in), Backing pill on prog- songs (right hand required, left root played by the app, one-bar count-in). Evidence: click accent every 4 beats at 72 and 100 bpm; no clicks while an injected mic listens. MetronomeTest.
  6. Tempo ramp: engine/TempoRamp.kt; Transport reports each loop pass (lastPass/passes). Speed button: 0.25x, 0.5x, 0.75x, 1x, Ramp. Evidence: Twinkle loop by touch 50 -> 60 -> 70%, held at 70% on passes with a wrong key, "Faster: 60 percent." spoken. TempoRampTest.
  7. Song chords: labelBarsAligned + ChordTrack (LessonStore.songChords, computed with the song packs). Chords pill (library songs with a pack, off by default) shows the bar chord top-left, next chord dimmed. Evidence: Minuet in G labels G, C, G, D7, Cmaj7 as it plays. LessonsTest.
  8. Practice stats: Prefs practice:<yyyy-MM-dd> seconds (60 days), engine/PracticeStats.kt; Songs header "This week: 42 min, 4 days in a row" (omitted at zero); Progress reset clears it. Evidence: 22 s played + seeded days -> 42 min, 4 days; reset removed the keys and the line. PracticeStatsTest.
  9. Two-anchor sync: engine/VideoSync.kt; "Last note" button once synced (Prefs anchor2:<song>:<video>); title "Synced" / "Synced, two points"; a rate outside 0.5x..2x is ignored. Window is at least 360 dp wide. Evidence: Twinkle video, last note at 36.12 s -> rate 1.0367, follower seek target 30.31 s instead of 29.50 s. VideoSyncTest.
  Also: Listen mode no longer mutes the sampler just because the mic toggle is on (the mic never listens in Listen).
- Feature batch (2026-10-08, emulators only: Pixel7_API37 emulator-5554, Fold_API37 emulator-5556; Roy's phone not reachable). 171 JVM tests, lint 0 errors, profile APK 23.8 MB.
  1. Lead-in and count-in: Transport.runFromMs/anchorMs/fresh/preview(). A fresh run (top or after a seek) starts 2 beats before its first group (Wait, Listen), 1 bar with the backing, 2 bars in Perform with clicks on every beat (metronome or not) and a "1 2 3 4" counter at the hit line; count-in keys are IGNORED and never graded; the timeline holds at the run start (shownMs). Before Play the first notes sit 0.75 x LOOKAHEAD above the line with "Press play". Evidence: TransportTest +5; Twinkle wait "lead-in ... from -1875 ms", held at 0 after ~1.9 s; Perform 8 clicks, beats 1..4 twice, counter screenshot.
  2. Feedback light: ui/HitLights.kt drawn by NoteLane in the draw phase (a clock ticks only while a light lives). Correct note: flash up its column (hand colour, white core); wrong: soft orange-red glow along the hit line (WrongGlow). No green keys, no red border. Settings > Falling notes > Feedback light (default on; off = old flashes). Evidence: touch hit/wrong screenshots with --ef lightSlow 12-15; injected mic run logs "light hit" per note; toggle off logs "redFlash" with the red border back (the border was removed later; off now flashes the keys).
  3. Trouble spots: engine/TroubleSpots.kt (+TroubleLog in Transport, PerformScore onMiss). Wait: seconds held past 1.2 s plus 1.5 s per wrong key; Perform: misses. Recap and a new wait-mode "Played through" sheet show "Hardest bars: 3, 5" with "Loop bar N" (A-B loop, wait, 50%, lead-in); Prefs trouble:<id>; library row "Work on bar 3". Evidence: TroubleSpotsTest; Twinkle by touch with 2 wrong keys in bar 3 and a 4 s hold in bar 5 -> "bar 3, bar 5"; Loop bar 3 -> loop 4800..7200 at 0.5x.
  4. Practice today: engine/DailySession.kt planner (up to 6 chords from the Daily queue, next lesson, worst bar looped 2 min or the Continue song in wait mode; "About 10 min" rounded to 5). Panel above a Continue row on Songs; one-line interstitial "Next: chords" (spoken) with Skip and End for today; summary row "Done today: ... N min." Chord time added to practice minutes; a song or loop part counts after 60 s; nothing done writes no summary. Evidence: DailySessionTest; --ez daily true walked chords (1 answered by touch), lesson, bar 11 loop that ended itself at 120 s; song part -> "Done today: Minuet in G. 1 min."
  5. Hear my run: engine/RunRecording.kt (RunRecorder, JSON via engine/MiniJson.kt), data/RunStore.kt files/runs/<id>.json, ui/RunReplay.kt (sampler at the run's speed, song dimmed, played notes thin and bright). Button in the recap and the wait-mode sheet. Evidence: RunRecordingTest; Perform by touch -> "run saved twinkle: 11 notes", replay screenshot. Wait-mode notes are timed at the hold point, so drift shows mainly in Perform runs.
  6. Smart speed: engine/SmartSpeed.kt. Speed button: 0.25x, 0.5x, 0.75x, 1x, Ramp, Auto. Auto = song time over real time of the last Wait or Perform run (holds count, lead-in not; the same ratio as notes per minute played against the song's), +5 points on each day with 85%+ (Perform accuracy, or groups against wrong keys), Prefs auto:<id>. Evidence: SmartSpeedTest; slow wait run "managed 56% ... accuracy 100% -> 61%", button "Auto 61%".
  7. Hands apart: Prefs hands:<id> (hands each song was played through with), handsTip(); one line above the lane on two-hand library songs: right hand first, then left, then "Hands together"; dismissable per song and stage (handsTip:<id>). Evidence: HandsTipTest; Ode to Joy screenshot, "Right hand" set hands=RIGHT.
  8. Rhythm level (6 lessons, after Beginner): Read, Listen, Perform on C4 alone (count-in, 80%), Perform as a tune. Beat lines (Settings > Falling notes, off by default, forced on by Exercise.beatLines). Evidence: CourseTest (one-key eighths 11/11, misses counted); rhythm-eighths step 3 by injected mic 11/11 good, passed.
  9. Keys level (G, F, D, B flat, E minor, D minor): scale (book fingering via Song.fingering), I IV V chords, scale from memory, 4-bar tune Perform. Song.preferFlats from a 0x59 key signature or the lesson; lane "Bb", voice "B flat" (spokenNote/feedbackLine flats). Evidence: NoteNamingTest; key-f scale by injected mic; key-bb screenshot (Bb, finger 4).
  10. Left-hand patterns (4 lessons appended to Intermediate): waltz bass, root-fifth-octave, octaves, broken tenths. Evidence: CourseTest; lh-waltz left hand alone by injected mic, clean.
  11. Technique level (last): five-finger C, G, F, D, A; Hanon 1 (4 bars); contrary-motion C scale. StepKind.RAMP: Perform from 50%, +10 per pass at 90%+, replays itself, done at 100%. Evidence: CourseTest (caught an octave-wrap bug); five-c first pass 100% -> "Faster: 60 percent", replayed at 60%. The full climb to 100% was not run on the emulator.
  12. Pedal and dynamics (MIDI only): MidiParser control changes, MidiDevices.pedal (CC 64). "The sustain pedal" (PedalGrader: a fresh press within 300 ms of each chord, down at its middle, 80%; strip under the keys) and "Soft and loud" (DynamicsGrader: under 50 then over 90, per note). No keyboard: an explanation and "Skip (needs MIDI)". Evidence: PedalDynamicsTest; skip path; pretend keyboard (--es midi) pedal 100% passed, soft and loud 80% passed. Not tried with real hardware.
  13. Ear games (Chords > Play): engine/EarGames.kt (EarGame interface; ChordGame implements it), Intervals (3 levels) and Play it back (C, G, D positions; 3 then 5 notes; a wrong note restarts; 2 failed tries show the notes). Bests game:int:<l>, game:back:<l>. Evidence: EarGamesTest; both played by touch.
  14. Sight-read (a row in Learn, not a game: it needs the lane and player that Learn embeds): engine/SightReading.kt (seeded, I IV V I tones, steps and thirds; level 2 eighths and a bass, in C, G or F), wait mode with names hidden, score seconds plus 2 per wrong note, best per level (sight:<l>). Evidence: SightReadingTest (600 pieces in key, in range, whole bars, fingered); seeded level 1 by touch: 17 s, 1 wrong, new best.
  15. Songs: 6 of the requested tunes (Scarborough Fair, Auld Lang Syne, Silent Night, Jingle Bells, When the Saints, Oh! Susanna) plus Brahms' Lullaby and Frere Jacques in place of Danny Boy, House of the Rising Sun and Shenandoah, whose melodies could not be written note-accurately from memory. gen_songs.py takes ids. All written from memory and checked by bar count and parsing only: Roy, check them by ear. LibraryContentTest: 23 bundled.
  16. Easy versions: Für Elise (easy), Minuet in G (easy), Greensleeves (easy); index.json pairWith both ways, shared videos; rows show "Easy version" or "Full version". Evidence: LibraryContentTest pair check; the link opened minuet-g.
  17. Reminder: Settings > Reminder (switch, TimePicker dialog); POST_NOTIFICATIONS asked on switch-on; reminder/Reminder.kt sets one setAndAllowWhileIdle alarm (no exact-alarm permission), the receiver posts and reschedules, boot and app start reschedule. Evidence: ReminderPlanTest; set 2 minutes ahead -> posted at 00:45:17, shade screenshot, cmd notification list.
  18. Transpose: three-dot "More options" at the end of the bar's second row (-6..+6, "Back to the written key"); Song.transposed() moves everything; chord names relabelled (flats in flat keys); Prefs transpose:<id>. Evidence: TransportTest transposed song; Minuet up 1 shows Ab, Db, Eb and chord "Ab". Fixed a crash on the way (the keyboard kept a stale layout when the range changed).
  19. Export and import progress: Settings > Progress; engine/ProgressBackup.kt (typed values, refuses foreign files), data/ProgressFiles.kt (SAF, replace, recreate). Evidence: ProgressBackupTest; exported 1157 bytes to Downloads and imported it back after the confirm.
  20. Polish pass (both emulators, light and dark, uiautomator label check): key labels kept inside edge keys, mic setup switches labelled, ellipses removed, "Sus and dim", Back from a game or a Daily session, Progress section order.
- TODO: curate videos for the 8 new songs (2026-10-08 batch, two oEmbed-checked tutorials each: Scarborough Fair, Auld Lang Syne, Silent Night, Jingle Bells, When the Saints, Oh! Susanna, Brahms' Lullaby, Frere Jacques). They have "videos": [] in index.json; LibraryContentTest.TO_CURATE allows that until each gets two oEmbed-checked tutorials.
- Personal MIDI files (Alicia, the Clair Obscur medley and its 18 parts) live in OneDrive piano-app/personal-midi, never in the repo; scripts\add-user-song.ps1 installs them (Alicia: -Slot alicia).
- Debug extras (guidance batch): --es lesson <id> [--ei step <n>] [--es inject ...] (inject feeds the first step opened; the mic starts when the step plays, about 0.4 s later, so Perform injections start near ~1600), --es screen chords --es game build|hear|mm [--ei level n] [--es inject ...] (seeded when injecting), --es tab play|daily|dictionary|practice|progressions, --es say "<text>".
- Debug extras (2026-10-08): --ef lightSlow <n> (stretch the hit-line lights for screenshots), --ez daily true (start Practice today), --es screen sight [--ei level 1|2] [--es inject ...] (seed 1 when injecting), --es lesson <id> --es midi "8150:p1,12010:p0,2100:60/40" (a pretend MIDI keyboard into the first step: ms after Play, p1/p0 pedal, key/velocity; commas, adb splits on spaces), --es screen chords --es game int|back [--ei level n].
- Injection timing since the count-in: a Perform run from the top starts 2 bars before its first note (Twinkle at 100 bpm: 4800 ms); Wait and Listen start from the preview, about 1.9 s before. In lessons the mic starts about 0.4 s after Play, so a Perform step's first note is at 2 bars minus 400 ms (rhythm-eighths at 66 bpm: ~6870). For library songs the debug play() starts with the injection, but emulator start-up jank can shift that by seconds; seek near the end (--el seek) for quick recap checks.
- Debug extras added: --es mode perform, --ez tabletop true, --es screen settings, --es tab lessons|dictionary|practice|progressions, inject rests and start time: synth:60,r,62@600~2000.
- Build types: debug (all debug tools), profile (release-optimized, debug-signed: .\gradlew.bat assembleProfile, then adb install -r app\build\outputs\apk\profile\app-profile.apk) for CPU/frame measurements. Debug builds run ~2x slower.
- APKs are arm64-v8a only (2026-10-20, audit-arobust; app/build.gradle.kts abiFilters): the Fold's ABI. The x86_64 emulators (Pixel7_API37, Fold_API37) run them through their ARM translation (ro.product.cpu.abilist x86_64,arm64-v8a); Kotlin runs natively, and the only native code, sherpa-onnx, loads only with the Kokoro model installed. Debug APK 147 -> 116 MB. A Play release goes out as an App Bundle (docs/publishing.md).
- Mic session (Roy, phone on the music stand, MIDI connected, debug build): home -> Microphone setup -> Allow microphone -> for each mic (Bottom, Back) record: Right-hand scale, Left-hand scale, Chords, Für Elise opening, 30 s of silence (tap the label, play, tap Stop). Then: .\scripts\pull-fixtures.ps1 ; .\gradlew.bat testDebugUnitTest --tests *FixtureTest*
- Live mic check: home -> "Mic check: C major scale" (mic on, wait mode, MIDI = judge). Then: adb logcat -d -s SharpEars:D > out\miccheck.log ; python scripts\miccheck.py out\miccheck.log
- Debug injection: --es song fur-elise --es mode wait --es hands right --es inject 'synth:76,75,76@700' (or a WAV path on the device, 48 kHz PCM16 mono).
- Input path: Keyboard (touch), MidiDevices.events and MicInput.events (SharedFlow, emitted on a binder thread, collected on main) -> PlayerScreen.keyStruck -> Transport.noteInput. P6 (mic) feeds the same keyStruck.

## 2.0 revamp (integrated 2026-10-13, branch `integration`; see REVAMP.md)
All six teams' work merged and wired (Theory last). Emulator only (emulator-5556); not on Roy's phone yet. 361 JVM tests, lint 0 errors.
- Tabs: Songs, Learn, Practice, Chords, You (HomeShell, rail when 600 dp or wider). Settings is a pushed screen
  (Screen.Settings) from the gear on You. Screen changes animate (ui/motion/Transitions.kt screenTransitions: curtain into
  and out of the player, forward/back by depth); AnimatedContent keys on the screen class only, HomeShell cross-fades tabs.
- Progress (Team B): data/ProgressStore.kt (prefs file "profile") made in MainActivity, catchUp() once at start, provided
  as ui/profile/LocalProgress. Events are recorded at the call sites in ui/profile/INTEGRATION.md (practice flushes, song
  runs, lesson steps and lessons, chord rounds and games, ear rounds/games/workout as "ear:<kind>" / "ear:workout",
  Practice today, sight-reading, first mic, import, scales/warm-ups/metronome/tuner). ProgressOverlays (level-up, toasts)
  sit on the root but never on the stage: a level-up earned mid-song waits until the player closes, and one earned with a
  Celebration waits until it closes (celebrationsShowing in ui/Celebrate.kt, ProgressOverlays holdLevelUp). Lesson, chord
  game, ear game, workout and theory celebrations show +XP and the level bar (xpBarFor in LocalProgress.kt).
- Onboarding (ui/onboarding): first run only; anyone with XP after catchUp() counts as onboarded. Ends in the suggested lesson.
- Practice tab (Team C, data/PracticeStore.kt "practice"): scales, arpeggios, warm-ups open in the full player, wait mode,
  at the exercise tempo (id "practice-" + title.hashCode()); metronome; tuner (needs the mic).
- Learn: the course path (70 lessons, 10 levels, Team F) with three rows above it: Theory (Team A, TheoryCard, one
  TheoryStore "theory" in MainActivity shared with Screen.Theory/TheoryScreen; lessons record TheoryLesson, units
  TheoryUnit, a lesson's seconds count as practice), Ear training (Team D, Screen.EarTraining, data/EarStore.kt "ear")
  and Sight-read. The theory stage panel is sized by the lesson's scenes (Explainer stageRatio), centred on tall phones.
- Player (Team E): ghost hand over the keys (Prefs.ghostHand, Settings > Falling notes; hidden when labels are), Perform
  grades on the lights, combo counter following the Perform streak, haptics on touch keys (Prefs.haptics, Settings > Sound).
- Home: goal line ("3 of 10 min today") over the weekly line, streak-aware greeting, the pup answers taps and dozes after 30 s.
- Settings > Reset progress also clears EarStore, PracticeStore and TheoryStore; XP and achievements stay. Export/import carries
  progress, mastery, mic, profile, ear, practice, theory.
- Debug routes (debug builds): --es screen practice [--es tab scales|arpeggios|warmups|metronome|tuner],
  --es screen ear [--es game rhythm|melody|pitch|degree|prog --ei level 0-2 | --ez workout true], --es screen profile,
  --es screen onboarding, --es screen settings (now a pushed screen over You), --es screen motion [--es demo celebrate|cheer|pup|keys],
  --es screen theory [--es theory <lesson id> [--ei scene n | --es part quiz [--ei question n] | --es part done|unit]].
- Voice lines: engine/SpokenCatalog.kt also lists the theory scene lines, quiz prompts and explanations (as the voice reads
  them, engine/theory spokenTheory: "4/4" said "4 4", sharps and flats by name), the cheers (CHEER_TITLES) and the ear
  training lines with fixed words (prompts, note, step and progression answers, melody prompts per key and length).
  1789 lines; 844 recorded in Kokoro speaker 3 on 2026-10-13. After changing spoken copy: gradlew :app:testDebugUnitTest
  --tests "*SpokenCatalogTest*" --rerun, then python -I scripts\render_voice.py --check (what a run would record and delete), then
  C:\dev\voice-tools\venv\Scripts\python -I scripts\render_voice.py.
- Jank (emulator-5556, 10 s twinkle wait run with injected mic): 76 to 87% janky, p50 61 ms, but a still Learn screen and the
  motion demo measured the same that afternoon (two emulators on one host, the composer at 65% CPU), so it says nothing
  about the player. Measure on the Fold with the profile build before release.
- Practice redesign (2026-10-14, branch practice-redesign, emulator-5554 only): Scales and Arpeggios one screen (toggle), round key picker with practised counts, one row of type pills, a dark keyboard panel drawn for the screen (ui/practice/ScaleKeyboard.kt: flat keys, real black-key offsets, hand-colour wash, finger numbers on every lit key, left row above right, glow and a gliding finger dot during Play it, Loop), the pup's key line from engine/practice/KeyLines.kt, drag-to-change bpm, stat tiles with a 12-key ring, a nudge bar after the demo. Warm-ups: 5/10/15 pills, level segments, routine panel with animated mini keyboards (RoutinePreview.kt). Metronome: huge drag number in a ring of beat dots, round Tap, trainer and mute under More. Tuner: arc gauge with glowing needle, pup and Allow when the mic is off.
- Ear and chords redesign (2026-10-14, branch fix-ear-chords, emulator-5558 only, from docs/DESIGN-AUDIT.md): guessing games never light the answer while it plays (KeysPlay playSteps lights only melody memory and a key's intro; the pup's headphones ripple pings per sound instead); both hubs are grids of game cards (ui/ear/GameCards.kt: animated picture or mini keyboard, stars per level at 7 of 10, best ring, round Play, a sheet with the how-to and level) with the daily workout as a hero panel; chord games use the ear GameHeader, a green burst and cheer bar per round and neutral miss lines (no red border); the progressions game has beat slots across the top and big answer cards (ui/ear/ProgressionParts.kt, also melody dots); Chords > Dictionary pins the chord in the Practice tab's ScaleKeyboard panel with key and quality strips; Chords > Progressions are chord tiles that light as they play.
- Songs, recap, path, between parts (2026-10-14, branch fix-songs-player, emulator-5554 only): song rows lead with a falling-notes tile drawn from the file's opening (engine/SongArt, parsed once off the main thread in ui/SongTiles.kt), stars, length and a best-score ring; a song run ends on ui/RunRecap.kt (pup by result, accuracy ring, stat chips, +XP and level bar, coaching line from engine/RunWords, Loop bar and Hear my run as secondary); Learn stops show their title and a kind icon (engine/LessonKind); the Practice today interstitial is centred with the pup's quoted line (engine/SessionWords), part chips and a Go ring that still auto-advances. Open: MainActivity's Interstitial call needs parts = s.plan.parts, index = s.index, done = s.done for the chips.
- Open: skipped ear rounds still earn the 5 XP of a round (Team D reports every round); onboarding and the pup's tap lines
  are shown, not spoken, so they are not in the voice catalog.
- Painted hand (2026-10-17, branch painted-hand, emulator-5556 only): the owner's Gemini hand frames (art/hand, README there)
  cut by scripts/cut_hand.py to res/drawable-nodpi/hand_*.webp plus ui/motion/HandAnchors.kt (hand-measured nail centres);
  presses for fingers 2 and 4 are made from the resting frame; the left hand rests on right 3 mirrored. ui/motion/PaintedHand.kt
  (HandPose, drawPaintedHand, handTip, PaintedHand with spring and cross-fade) draws it in the HandShape and FingerNumbers
  scenes and the player's ghost hand (60%, drawn hand as fallback); Posture and Pedal use the soft realistic figure and foot
  (ui/theory/SoftFigure.kt, from art-styles). Practice tab keeps its dot only (the hand muddied the lit keys).

## 2.0.1 engaging lessons (2026-10-14, branch `engaging-lessons`)
Roy: "I don't like areas that have just text." Every Learn reading step is now a short animated explainer, like Theory.
- Model: Step.scenes (engine/theory Scene); read(scene(...), ...) builds a READ step, text = the captions joined (kept for
  the voice catalog and TalkBack). Builders in engine/LessonScenes.kt. 85 read steps in 70 lessons, 2 to 4 scenes each
  (232 scenes): FingerNumbers 52, SongMap 31, Phrase 30, Keys 29, Dynamics 22, Rhythm 17, Chord 13, Lane 10, Pedal 8,
  Articulation 5, Staff 4, Interval 3, Posture 2, HandShape 2, Midi 2, ValueTree 1, Pop 1 (repertoire lessons preview their
  phrase; Technique 2 uses Articulation, Dynamics and Pedal).
- New visuals (engine/theory/Theory.kt, drawn in ui/theory/LessonScenes.kt, pure functions of the clock): Posture, HandShape,
  FingerNumbers (FingerStep presses, spread for stretches; the hand glides, thumb under), Pedal (late = blur), Dynamics,
  Articulation, Phrase (token notation, grand staff + keys, passes per hand, look-ahead), Lane, SongMap (loop, speed), Midi.
- Player: Explainer.kt is shared (rememberScenePlayback, SceneStage, SceneBar, SceneControls, ScenePreview). LessonScreen
  plays a read step's scenes (voice says each caption; Back/Pause/Watch again/Next through the scenes, then the steps).
  Exercise instructions are the pup's bubble.
- Sweep: MIDI-needed step (pup + MidiVisual), Chords progression item (progressionVisual playing), Today panel empty
  line, Chords all-caught-up, sight-read and mic intros, interstitial: all the pup's, not plain text.
- Debug: --es lesson <id> --ei step n --ei scene n; --es screen chords --es pack 06-progressions; --ez emptyToday true.
- Voice: SpokenCatalog adds every scene caption (spokenTheory(say)); 1989 lines. CourseScenesTest checks scenes, ranges
  and captions. emulator-5556 crashed in host GPU mode once on 2026-10-14: start it with -gpu swiftshader_indirect
  -crash-report-mode disabled.

## 2.1 family (FAMILY.md; stage 1 foundation done 2026-10-18, branch family-foundation, emulator-5558 only)
### 2.1.0 status (integrated 2026-10-18, branch `family`, versionCode 7; emulator-5558 Fold only, not on Roy's phone yet)
- Shipped and wired: profiles, picker, parent gate, family wall (latest sticker, crown of the day); a new kid lands in playground-1. Learn per profile (engine/CoursePath): a kid's Playground first, drawn as the pup's world (PlaceScene with lesson stops), big kids pointed at BIG_KID_LESSONS; a grown-up's Basics first and the Playground an "Extra" unit that is never their next lesson (Learn, Practice today, the new-kid open). Grown-up mode is offered once after Beginner (card on the kid's Learn, through the gate); a change of kind reopens the app as them.
- Kid skin everywhere: colour notes with animals (grown-ups: Settings > Falling notes > Colour notes), every white key labelled for kids, big keys fitted to the hand played, bites at Exercise.phraseEnd, stars on every recap, kid words, no numbers on home, Learn, Practice today (lesson + whole song, no chords or loops), recaps or celebrations, no level-up screens; goodbye after a lesson, a song or a Practice today part at the goal, then lessons sleep; paw calendar on You.
- For everyone: wrong keys yellow/red from touch, MIDI and mic (MicInput.wrongFlash on by default, painting resumes for a new player), hint escalation, octave hint, noise sheet; juice on every note (Juice.onStrike with the sampler's sound rule, treat jar crumbs); sticker drops, egg and "one more" on lesson finishes and song recaps; Send to someone; Duet pill in the full player (2+ profiles, library songs, partner credited); Pup's quest under Practice today; Play row (Jam, Pup says, Clap along; first on a kid's home); You: world card and Screen.World (World, Album, Dress up, Records).
- Hooks: LocalWorld and LocalPupOutfit from MainActivity; Modifier.wakesPup on the root; one MicInput.rawFrames fan-out (Jam, Clap along, Playground stages), one ClapCounter (engine/playground), one colour table (engine/skin); PlayerScreen(onWrongKey, anyOfLessons, PlayerStart.duet); LessonScreen(onDone returns the Reward, next, onSongs, onJam); RunRecap(songTitle, run, nextDrop, say); QuietWrongKeys on chord and ear screens; quest events SongFinished (PlayerScreen), LessonDone, ChordReviewed, LoopDone, PupCopied (MainActivity).
- Tuned on synthetic audio only (no room or kid has played them): ClapCounter, PitchHeight (high and low, key hunts), SoftLoud thresholds, the Jam's ChordEar, the kid +3 dB mic offset. Tuned on Roy's recordings: wrong-key detection and the noise watchdog (FixtureTest session-155837 33/33, 0 false strikes, 0 would-be wrong flashes), the overtone-credit fix (724032d).
- Voice: 2315 catalog lines, all recorded (Kokoro speaker 3; 318 kid lines at 0.85). Lines with a name are made on the phone (the emulator falls back to the platform TTS).
- Jank (emulator-5558, debug build, swiftshader; relative only): World tab 60 frames in 10 s, p50 200 ms before the scene bitmap fix, about 280 frames, p50 57 ms after (album 53 ms); player in Listen p50 65 ms; kid Learn map p50 57 ms. Measure on the Fold with the profile build before release.
- Debug routes, one `--es screen` switch: world [--es tab world|album|dress|records], jam|pupsays|clap [--es hint "<line>"], worlddemo --es part ... (a demo world, ui/world/WorldDemo.kt), skin --es view ..., motion, plus learn, you, settings, practice, ear, theory, sight, chords; `--es profile <id>` with any of them; Playground stages through `--es lesson playground-N [--ei step n] [--es inject synth:...|claps:...|dyn:...]`. The old play and playground demo routes (PlayDemo, PlaygroundPreview) are gone; the teams' INTEGRATION.md files still describe them.
- Tests: 672 JVM tests, lint 0 errors (26 warnings, mostly UseKtx in older stores). Emulator walk-through screenshots in app/build/screens/int-*.png (not committed).
- Profiles: data/Profiles.kt ("profiles" file, rules in engine/family/Roster.kt: up to 8, owner "Roy" id "owner" keeps the plain progress files), picker on launch with 2+ profiles, Songs shows the family wall, Settings > Family adds, renames, deletes (asks twice) and switches a kid to grown-up mode. A switch rebuilds the app for the new profile (MainActivity.openApp).
- Every ProgressFiles.NAMES file but "mic" is per profile (ProgressFiles.fileFor: "progress.p3"); backup carries all profiles and the roster. Parent gate (hold the pup 3 s, then a sum) guards Settings and Mic setup for kids, profile management and the report token for everyone; open 10 min. How to use all of it: ui/family/INTEGRATION.md.
- Detector (stage 2, branch family-detector, JVM and Roy's recordings only, no device): MicEvent.Wrong names the key (midi, nearest expected, distance; slip within 2 = yellow) and fires only on a confident piano strike checked at two moments; other unexplained jumps go to NoteMatcher.noise. Would-be wrong flashes: session-155837 57 -> 0 (still 33/33, 0 false strikes), 160604 119 -> 0, the no-truth room sessions 134..176 -> 0..10. So wrong keys show by default now (MicInput.wrongFlash true); the room-noise watchdog (engine/hints/NoiseWatch, ui/hints/NoiseSheet) offers once per session to stop painting them.
- Hints in wait mode (engine/hints/HintPlan, profile hintSeconds 6/12/18 or 4/8/12): key glow, painted hand points, app plays it with the mic deaf ("Your turn."); octave hint (engine/hints/OctaveHint from NoteMatcher.candidates, fires 2.0 s after Roy's A5-for-A4 in the together-4 clip); MicInput.forProfile(kid, micOffsetDb) (+3 dB kids, room margin floor 7.5 dB) and profile=kid|adult on every saved report; MicInput.rawFrames for the clap counter. Wiring left for the integrator: ui/hints/INTEGRATION.md.
- Play corner (stage 2, branch family-play, JVM only; wired in 2.1.0): Jam, Pup says (1 to 3 players, turns gated in engine/play/PupSays.kt), Clap along (60/90/120 bpm, own ClapCounter), Send to someone (run replayed with the piano samples to a WAV plus a Canvas card, via `Share.files`), Pup's quest (3 daily tasks per profile in data/PlayStore.kt, file "play"), Duet (full credit to both). Wiring, events and decisions: ui/play/INTEGRATION.md.
- The note matcher does not transcribe: the Jam listens to C4 to C5 plus every C from C2 to C6 (16 keys, one per group; 37 keys cost about 34% of a desktop core), and engine/play/ChordEar.kt finds a chord's other notes in the raw audio around each heard onset. 502 JVM tests.
- Skin (stage 2, branch family-skin, emulator-5558): Boomwhacker colour notes on the lane and keys (Profile.colourNotes; black keys striped in their neighbours' colours, animals for pre-readers), wrong keys lit yellow (2 semitones or less) or red with a shake via ui/skin/WrongFlash, stars on every recap (no numbers for kids), kid bites and one button in LessonScreen, the goodbye at a kid's daily goal and the paw card. Rules in engine/skin (JVM tests, 480 total); wiring left for the integrator in ui/skin/INTEGRATION.md (PlayerScreen feed, wrongFlash default on, big keys, phraseEnd, Settings switch).
- Debug: --es screen skin --es view lane|wrong|recap|goodbye|asleep|paws|bite [--ez kid --ez reader --es tone --ef slow --ei stars --es lesson]. The emulators share one host: under load swiftshader frames crawl and the system shows "isn't responding"; tap Wait.
- World (stage 2, branch family-world, emulator-5556 only; wired in 2.1.0, see ui/world/INTEGRATION.md): the living pup (ui/Pup.kt: 31 poses, cross-fades, idle acts after 10 s, asleep at 60 s, PupController react/mood, hops with every note via ui/motion/Juice.kt), sticker drops with rarities and the rare floor, the mystery egg, album, dog house stages, scenes with anchors for the Playground path, treat jar, records shelf, dress-up, drop reveal and "one more" (ui/world, rules in engine/world, per-profile data/WorldStore.kt "world"). 490 JVM tests.
- World art and sound are generated: scripts/gen_stickers.py (30 placeholder stickers; Gemini art replaces them under the same names), scripts/gen_jingles.py (res/raw). Debug: --es screen worlddemo --es part album|world|map|screen|card|jar|egg|dress|reveal|shelf|idle|onemore [--ei stage 1-3] [--es theme light|dark].
- Pup's Playground (stage 2 playground team, branch family-playground, emulator-5554 only): engine/playground/Playground.kt is the unit "playground", first in COURSE (now lazy), 12 game lessons in the Scene/Exercise model, places, CONCERT_SONGS, CONCERT_PASS 60. Exercise notation has any-of groups ("C#4|D#4:2", "C6|C#4..C8:2"; OnsetGroup.anyOf, Transport.listenGroups/hintKeys, mic set capped at 12 keys) and Exercise.phraseEnd + bite() for kid bites. Android-free ClapCounter, PitchHeight (onset + YIN, no expected keys) and SoftLoud, all on PianoSynth/synthetic audio in JVM tests (489 tests, 444 before).
- Playground stages (ui/playground: the pup flying with pitch, the dog-house and fence hunt, the drum and claps, the sleeping pup, the concert) hook in through Step.game and PlaygroundStage; LessonScreen wiring, mic notes and matcher findings for the detector team: ui/playground/INTEGRATION.md. Debug: --es lesson playground-N [--ei step n] [--ei scene n] [--es inject synth:...|claps:400,1150|dyn:60/-20,60/-8@1600~1500].

### 2.1.1 fixes (2026-10-18, branch `fixes-2.1.1`, versionCode 8; emulator-5558 Fold only, not on Roy's phone yet)
From Roy's first go with the kids: in Pup's Playground level 2 "sometimes playing the notes doesn't lead to anything",
and the Jam's chord confetti came with no sound and no words.
- Stages hear through the note matcher (ui/playground rememberStage, engine/playground StageEar.kt): StageKeys per
  stage (high and low: C2 E2 G2 C3 E3 G3 + C5 E5 G5 C6 E6 G6; dog houses C#4 D#4 C#5 D#5; fences F#4 G#4 A#4 F#5 G#5
  A#5; Cs C2 to C6; soft and loud A3 to G4 with C#4 D#4), each its own group, `NoteMatcher.strictKeys` on. A key
  outside the set is named by the wrong-key search (a dog house in another octave counts; a white key gets "That one's
  white. Try the black ones here." with the place glowing); the pitch tracker only adds a sure guess. The hunts show
  the two octaves from middle C. The exercise any-of groups name the same keys.
- Strict keys (NoteMatcher, off for the player): an accepted strike must show its two lowest partials (10% of the
  rise), have no fresh lower note whose 4th to 9th partial it is (all of that note's first partials rising, 25%,
  probed only at accept time), take no single-partial slack, and carry the sound (within 12 dB of its window's
  loudest hop); the wrong-key search gets the same checks. PianoSynth, every key at two levels against each stage set
  and the Jam (1056 cases): 104 credited a key that was not struck before, 0 now; Roy's scale through each stage: every C, D#4 and soft-loud key heard,
  0 keys found that were not it, 0 wrong-side highs and lows; FixtureTest strict: 33/33, 0 false strikes.
- I heard you: every sound shows within a frame (StageFx: a ring at the key heard or guessed, sky blue for a sound
  with no key, gold for a find, yellow or red for a wrong piano key against the round's keys; the pup's ears flick;
  "I heard you!" for a sound with no key). The player and lessons make every pup hop for a sound with no key
  (Juice.onHeard).
- No dead ends: RoundCoach (demo, listen, three tries or 7 s quiet stretches, then "Good try, let's keep going" and a
  small star); the drum shows a bar first and asks again by itself (ClapTries); the concert plays the song's start
  first; a Playground lesson's wait mode lets a group go after three tries (PlayerScreen letGo, 9 s stretches: about
  27 s per note with nothing played) and a Perform step after its third failed try (StepRetry). PlaygroundFlowTest walks
  all twelve lessons to the end with a silent kid and with one who plays only wrong keys (1 to 10 min of simulated
  time each).
- Voice: Voice.speakOverMic says call-outs between rounds and over a demo while the mic is deaf (MicInput
  deafWhileSpeaking, 300 ms tail, 8 s cut-off), without stopping the mic; a line that cannot start now is dropped.
  23 new kid lines (2338 recorded).
- Sounds: stage_pop, stage_ding, jam_whoosh (scripts/gen_jingles.py, shaped noise, bands wider than a semitone);
  StageSoundsTest: no strike and no wrong key at 0, -12 and -24 dB with every key expected (strict) and with each
  stage's and the Jam's set.
- Jam: captions for every event (JamWords: "C!", "the red cat key" before reading, "Two notes together!", "Three notes!
  A chord!", "Up the rainbow!") big over the pup for about a second, said to a kid when they pause; two notes pop, a
  chord and a rainbow whoosh; only confirmed notes (matcher strikes, touch, MIDI) make two-together, a chord or a run,
  the chord ear and named keys only burst; strict keys; the pup's ear pulses by "Listening"; "Play any key" for the
  first ten seconds.
- Clips for tuning: a stage with 3+ sounds and no key for 8 s saves its stretch (StageClipWatch), the Jam one 20 s clip
  a session at 10+ sounds (JamClipWatch); both via MicInput.saveStall (files/stalls, 250 MB cap, the Reporter), reports
  carry stage/lesson and profile=kid|adult, never a name.
- Debug: `--ef fxSlow n` (stretch rings, call-outs, captions), `--es screen jam --es midi "6000:60/90,6000:67/90"` (a
  pretend keyboard into the Jam). Screenshots: app/build/screens/fix-*.png (not committed).
- Tests: 702 JVM (672 before), lint 0 errors (25 warnings, all older).
- Still unproven on real audio: strict keys on more than Roy's one scale recording with MIDI truth (no black keys or
  bass in it, no kid, no room with several kids); the deaf windows (a line said over the mic costs the kid about 1 to
  1.5 s of listening, measured on the emulator); the 7 s and 9 s quiet stretches; the noise-onset threshold for "I
  heard you" in a loud room (talking makes the pup's ears twitch); the pitch tracker's 0.9 clarity gate; the drum and
  soft-and-loud thresholds (still synthetic only); the Jam's chords from the mic (on PianoSynth the matcher confirms 1
  of a C-E-G chord, so mic chords will mostly show as keys bursting, not confetti); the sounds' pitchlessness through
  the Fold's speaker and mic. The clips Roy's next session saves are meant for exactly this.

## 2.2 pages: the pond and the concert hall (2026-10-18, branch `pages`, emulator-5554 only; version not bumped)
The kids' path: the garden's twelve, then the pond's twelve, then the concert hall's twelve, then the big kids' road
(Basics). Built alongside the kid home (branch kidhome: the world map, stars on stones, the finale); merge notes in
ui/playground/INTEGRATION.md, "2.2".
- Contract: engine/playground/Pages.kt `PLAYGROUND_PAGES` (garden, pond `POND_ID` "playground-pond" `pond-1..12`,
  hall `HALL_ID` "playground-hall" `hall-1..12`), `PLAYGROUND_LEVELS`, `pageOf`, `isPlaygroundLesson`,
  `isPlaygroundLevel`. COURSE: garden, pond, hall, Basics... (106 lessons). CoursePath: kids walk all three pages then
  Basics; readers get the garden's short list, then the pond and the hall whole; grown-ups see all three as Extra units.
- Content: engine/playground/Pond.kt and Hall.kt, the garden's model (READ steps of one to three scenes, game steps,
  bites). Songs the course has are reused note for note and cut at their first phrase (Twinkle, Row, Old MacDonald,
  London Bridge, Ode to Joy, Lightly Row, Yankee Doodle, the Rhythm unit's bars, fingers-left, five-c, "together"); the
  shared phrases moved to engine/Exercises.kt. engine/playground/Concerts.kt: the shelf of nine songs (a start to warm
  up on, a version with the band at a kid's tempo, CONCERT_PASS), the pond's three, the recital (`Recital`, kept per
  profile in the "play" file, data/RecitalStore.kt), `concertPlan(lessonId, step, recital)`.
- Games: new `ECHO` (copy the pup: frogs on lily pads, spotlights in the hall), `CHORD` (C E G, the pup settles on
  it), `SOFT_LOUD_TUNE` (Ode's phrase usual, soft, loud, soft, graded per phrase), `SET_LIST` (pick three from the
  shelf, warm up on each); grown: `CLAP` (rhythms with ta, ti, ta-a and shh under the paws; pond and hall backdrops),
  `KEY_HUNT` (letters C to G, each round's letter said, badges with the letter or the animal), `CONCERT` (the plan:
  pond picks with frogs and ducks watching, the recital plays the kid's three with the collected animals).
- Hearing: StageRounds phrases (a phrase a round, notes as progress, a let go plays the rest), StageEar(chords),
  StageKeys.FIVE / letter(pc) / C_CHORD (one group), NoteJudge, ChordJudge, TuneLoudness. NoteMatcher strict keys skip
  a lower note of the key's own group (G4 under C4 in a chord): a simultaneous C E G on PianoSynth gave C and E only
  before. Every key at two levels against each new set, the chord rolled 0 to 150 ms, other chords and single keys
  never passing, Roy's scale (session-155837) through the letters and the C position: all as expected.
- Fixed in the garden: the "whole song" steps of Hot Cross Buns and Mary were cut back to the bite for kids.
- Voice: 239 new kid lines (2577 recorded). Lines: PlaygroundLines.PageLines.
- Tests: 730 JVM (702 before): PagesTest, PhraseGamesTest, PlaygroundChecks (the garden's checks on every page),
  PlaygroundFlowTest over all 36 lessons (silent and wrong-keys kids), StageHearingTest and StageSoundsTest on the new
  sets. Lint 0 errors. Time model: 104 to 181 s a lesson (the garden's 107 to 139). Screenshots of every new lesson's
  main stage or scene: app/build/screens/pages-pond-N.png, pages-hall-N.png (not committed).
- Still unproven: everything on a real piano and a kid (the emulator has no piano); the chord on a real piano (one
  group, strict); phrase copy timing (a kid playing a tune faster than the matcher's refractory); the soft and loud
  tune's per-phrase grade (synthetic only, like the garden's nap).
## 2.2 kid home (2026-10-18, branch `kidhome`, emulator-5558 Fold only; version not bumped, the integrator does)
Roy, after 2.1 with the kids: the kid experience was "all over the place" (the grown-up app with a skin). A kid now gets
one screen; grown-ups keep 2.1 unchanged. How it fits together and what to check after merging the pages branch:
ui/kid/INTEGRATION.md.
- One screen for kids (ui/kid/KidHome.kt, chosen in MainActivity's `Screen.Home` by `isKid`): the Playground's pages one
  under the other as their scenes, a stone per lesson on a winding trail, the pup standing on the next stone with "Let's
  go!" (tap him, the bubble or the stone), the egg and the jar in the garden, Pup's quest as a signpost he points at,
  the gate to the big kids' road at the end of the last scene. Three big buttons: Play (a sheet: Jam, Pup says, Clap
  along), Songs (only colour songs: easy, the app's own, white keys only; 19 of 39; big tiles, no stats), My stickers
  (album, dress-up, records, paws as pages). A small whistle top right asks the parent gate, then: who's playing,
  Settings, Microphone setup. Grown-up mode is still offered once after Beginner, at the top of the map.
- Stars on every stone: a lesson's stars from its graded steps (engine/kid/LessonStars over engine/skin/Stars; a step let
  go counts one), the best kept per profile (data/KidStore.kt, file "kid", in ProgressFiles.NAMES); a kid's lesson
  finish shows them over the drop. Stones ahead of the pup sleep ("This one first!"), skipped ones behind him stay open.
- The finale (ui/kid/Finale.kt, Certificate.kt): a page's last lesson finished the first time leads ("Hooray!") to
  night and fireworks, the pup bowing, "<Name> finished the garden!" said on the phone, the page's album page, a
  certificate sent through Share.files, and where the path goes on. The map then rolls to the next page's first stone;
  after the last page the pup says "You're ready for the big kids' road!", the road opens below the gate and Basics'
  first lesson is the next stone (kid skin as before). All from PLAYGROUND_PAGES (engine/kid/KidMap, Finale).
- Pages: CoursePath treats every page's level as the Playground (kids walk them in page order before Basics, grown-ups
  have them as extras); BIG_KID_LESSONS shortens only the pages it names. WorldAnchors.lesson(n)/stops follow the pages,
  twelve stops per scene in three winding rows (engine/world/Trail.kt; the pond's trail is a boardwalk); a lesson drops
  its page's place's sticker (Place.ofPlaygroundLesson is unused now).
- Goodbye on the kid home: once today's goal is reached the pup yawns and sleeps on his stone ("Zzz. New games
  tomorrow!"), Play, Songs and My stickers stay open, a stone opens the asleep screen (a grown-up's one more). A page's
  finale holds the goodbye until after it.
- Voice: 16 new kid lines recorded (2354); lines with a name are made on the phone.
- Debug: `--es screen kidsongs | kidstickers [--es page album|dress|records|paws] | finale [--es page garden]`,
  `--es kidseed <page>:<n>|road|reset`, `--ez sleepy true`, `--ez awake true` (with `--es profile <id>`), and
  `--es screen worlddemo --es part trail` (every scene's twelve stops numbered).
- Tests: 728 JVM (702 before; ProfilesStoreTest's file list gains "kid"), lint 0 errors. Screenshots:
  app/build/screens/kid-*.png (not committed).
- Not proven: no kid has seen it; the pond and the hall exist only as layouts until the pages branch lands; frame time of
  the map on the Fold (emulator only, swiftshader).

### 2.2.0 status (integrated 2026-10-18, branch `release-2.2`, versionCode 9; emulators only, not on Roy's phone yet)
`pages` and `kidhome` merged: a kid's home is the map of the garden, the pond and the concert hall (36 stones, stars,
a finale per page), then the big kids' road; grown-ups keep 2.1 with the three pages as Extra units on Learn.
- Merge checks (ui/kid and ui/playground INTEGRATION.md): every place lookup is the lesson's page (`pageOf`,
  KidPages.dropPlace); `Place.ofPlaygroundLesson` is gone. lessonDone and LessonScreen's "one more" drop and preview the
  page's place (pond-12 dropped pond-10, hall-12 concert-3 on the emulator). Learn shows the pages as Extra, never an
  adult's next lesson. KidMap.next is CoursePath.next: a pre-reader walks all 36, a reader the garden's short list then
  the pond and the hall whole (KidMapTest walks both on the real pages). "kid" and "play" (the recital) are in
  ProgressFiles.NAMES.
- Fixed in the merge: collected stickers sat on the trails' stones (garden bottom row, the pond's boardwalk, the hall's
  carpet); they sit in the gaps now and MapAnchorsTest keeps them clear. Stones ahead of the pup were see-through (planks
  and carpet showed through); solid now. The hall's audience stood in the bottom row's stars; seated lower.
- Redraw: the kid home drew a second full-screen paper over MainActivity's; the concert hall repainted at the 20 Hz
  scene clock with nothing moving (`rememberSceneClock(running = false)`); the next stone's ripple recomposed it every
  frame (read at draw now). Fold emulator, map idle 10 s: p50 200 ms (85 to 100 frames) before, 150 ms (100 to 104)
  after; scrolling p50 150 ms; the adult Learn tab measures the same 150 ms on this host. All GPU (swiftshader issue
  and swap about 100 ms a frame, UI thread under 5 ms): measure on the Fold itself before trusting any of it.
- Voice: 2597 catalog lines, all recorded (583 kid lines at 0.85); 4 new for the pond's and the hall's finales; no
  recording deleted.
- Emulator walk (5558 Fold, 5554 phone; Mia pre-reader p1, Leo reader p2): the map's three scenes and the gate, stones to
  lessons, pond-1/3/9/12 and hall-1/6/11/12 by route, the garden's encore let go after three silent tries (one star),
  every finale rolling on (garden to pond-1, pond to hall-1, hall to the road with the gate open), Play, Songs, My
  stickers, the grown-ups' corner, asleep, dark mode, the adult home, Learn and world. No crash or ANR. Screenshots:
  app/build/screens/r22-*.png (not committed).
- Tests: 759 JVM, lint 0 errors (25 warnings, all older).
- Still open: everything on a real piano with a kid; the map's frame time on the Fold; a finale's "Next stop" names the
  next page even when that page was already walked (only reachable through debug seeds).

### 2.2.1 QA (2026-10-18, branch `qa-2.2.1`, versionCode 10; emulators only, not on Roy's phone yet)
A sweep for small errors of the kind Roy found (the hint hand pressing finger 2 on a Mary note marked 4, 959be29). Each
class of error now has a JVM test that fails if it comes back:
- What a lesson says vs what it shows: scenes and copy named fingers the falling notes, ghost hand and hint hand did not
  use (left-hand "little finger on C" under a 1, bites fingered apart from their line, Practice-tab book fingering only
  in the right hand, trills in the wrong hand, contrary F, progression triads under other fingers than the dictionary).
  Exercise.leftFingers / Song.leftFingering, Exercise.withFingers. FingersAgreeTest, PaintedHandTest, ChordsTest.
- Note spelling: chord tones by pitch class (E major "E, Ab, B"; C minor "D#"), progression pictures (C7 with A#), a
  scale's black keys by one flats switch (D harmonic minor's C# drawn Db), D flat mixolydian "six sharps", F#7 and G#dim7
  arpeggios and the E flat minor warm-up in the wrong accidentals, a dim7's seventh called "a sixth". Chord.spell /
  toneName, Song.spellings, blackKeySpellings. SpellingTest.
- Colour notes: lesson scenes lit keys in the accent while the words said "red cat C", Ode to Joy's card wore A's and B's
  colours, white letters on yellow keys. SceneColoursTest, ColourWordsTest.
- The voice: hints dropped while the mic listened (now speakOverMic), lines missing from the catalog, theory quizzes,
  progression blurbs and a lesson title read as "V7 to I", "Bb" and "maj7" read as written. spokenNumerals.
  SpokenCatalogTest (everyFixedLineAScreenSpeaksIsRecorded, theVoiceReadsNotesAndNumeralsAsWords).
- Copy: kid lines with "wrong", numbers or "1 notes"; bubble lengths (kids 110 characters, grown-ups 150, kid home 40);
  "1 days", "1 times", "every 1 bars", "+1 cents", "1 chords"; "an octave up" for a tenth; two screens with different
  workout minutes; "c position"; ". Best run" for imported songs; "to start" mid-goal; the kid record shelf's counts; a
  three-star line claiming every note right. KidCopyTest, CopyCountsTest, LibraryContentTest (Twinkle's spelling).
- Settings that did not reach their screens: Reset progress kept the goal ring, streak, calendar and sight-reading best;
  App sound off left the Chords tabs and Practice keys sounding; Vibration off still buzzed chord games (and for mic
  notes); Spoken guidance off let the current line finish; Show wrong keys stayed on after the noise offer; a kid's
  reading switch left a stale course path. ProgressStoreTest, SettingsWiringTest.
- Layout: dense phrases drew notes over each other on a phone (PhraseLayoutTest); the finale's "Keep going" scrolled out
  of sight on a phone; the waiting keys pulsed with animations off; the pup scratched through listening steps and sat at
  his piano on clapping steps. ProgressFilesCoverageTest guards backup and profile delete for every per-profile file.
- Voice: 2618 lines recorded. Tests: 797 JVM, lint 0 errors (25 warnings, all older). FixtureTest session-155837 33/33,
  0 false strikes, 0 wrong flashes. Screenshots: app/build/screens/qa-*.png (not committed).
- Not fixed, noted: on rows two and three of a kid map page the pup's head covers the middle star of the walked stone
  above (the pup is taller than the row gap; needs a stars-over-pup layer or a design call); with micAddress unset the
  "Bottom" chip shows selected while start() takes the first built-in mic the phone lists; C# and F# diminished 7
  arpeggios mix sharps and a flat, so the plain names stand in on the notes; Ab/Eb dim and C#/F#/B aug chords show a
  plain key where a double accidental belongs; library songs' DP fingering may change finger on repeated notes; a 2.0
  backup also clears the owner's 2.1+ world, play, skin and kid files; the theory explainers and the tuner's A sound
  with App sound off by design.

## 2.3 part A: the workshop and the fair (2026-10-18, branch `k40a`, emulator-5558 Fold only; merged in 2.3.0 below)
Roy asked for forty more kid lessons: which finger plays, how to sit and hold the hands, basic ways to use the piano,
and games. Part A is the workshop's twelve (sitting, hand shape, finger numbers, hand positions, wrist, arm weight,
touch, pedal) and the fair's four games, plus the place work for all four new places (workshop, treehouse, night sky,
fair). Part B wrote the treehouse's and the night sky's lessons. ui/playground/INTEGRATION.md, "2.3", has the games.
- Path: garden, pond, hall, workshop, treehouse, night sky, fair, then Basics (engine/playground/Pages.kt `PAGES`).
  Workshop: `WORKSHOP_ID` "playground-workshop", `workshop-1..12`, Workshop.kt. Fair: `FAIR_ID` "playground-fair",
  `fair-1..4`, Fair.kt. Readers walk both whole (BIG_KID_LESSONS names only the garden); grown-ups see them as Extra
  units. COURSE is now the pages' levels then Basics (`PLAYGROUND_LEVELS + ...`).
- The lessons (time model 104 to 159 s each):
  - workshop-1 Sit like a pianist: posture scenes (tall, room to move, feet flat), `PICK` the right sitter from a
    sloucher, a too-close one and tucked feet, then one long middle C and three Cs.
  - workshop-2 The bubble hand: hang, hold a bubble, curl, tips; `HAND` with the painted hand and a bubble under it on
    C, then D and E; C D E D C.
  - workshop-3 Finger numbers: both hands numbered; `HAND` twice, the pup calls a finger ("Finger three!"), the kid
    plays the key under it (the mic hears the key; the finger is the kid's).
  - workshop-4 Right hand home: thumb on middle C to G with numbers; `HAND` home keys, listen, play, from memory.
  - workshop-5 Left hand home: little finger on C3 up to the thumb on G3; the same in the left hand.
  - workshop-6 Thumbs meet: the middle C position (both thumbs on C4, left F3 to C4, right C4 to G4), hands taking
    turns out from the middle; listen, play, memory.
  - workshop-7 Wrist like a bridge: arm scenes (level, sagging, poking up, the bridge dashed over the arm); `PICK`
    the level wrist; a slow walk up to G and back.
  - workshop-8 Heavy arm, soft arm: arm scenes (heavy, soft), `SOFT_LOUD` with six rounds (loud, soft, three times;
    `SoftLoud.roundsFor`), then C E G with a heavy arm.
  - workshop-9 Short and long: touch scenes; `PICK` by ear (the pup plays C short or long); a bouncing tune and a
    smooth one, Perform.
  - workshop-10 The pedal: the foot and the ringing chord; `PICK` by ear (pedal down or not); `PEDAL`: the kid plays
    C E G and the pup plays it back with the pedal down. The pedal itself is never graded (the app cannot tell).
  - workshop-11 Two hands take turns: left C D E, right answers C D E; listen, play, memory.
  - workshop-12 The workshop concert: Mary with book fingers (3 2 1 2 3 3 3, 2 2 2, 3 5 5) at 80, the bite, the
    phrase, then `CONCERT` with the band (concertPlan WORKSHOP, Mary only).
  - fair-1 Balloon pop: `BALLOONS` twice, a balloon called by colour (pre-readers) or letter (readers), its key pops it.
  - fair-2 The carousel: `CAROUSEL`, C to G and back three times; every smooth run spins it faster (carouselSpeed).
  - fair-3 The strongman: `STRONGMAN` twice, three swings of middle C; any key counts, the louder the higher the puck
    (StrongmanJudge, against the first swing), the bell at the top. Never a miss.
  - fair-4 The photo booth: `PHOTO`, pick a song from the shelf, play its start three times (a photo each), the takes
    play back, a photo card to share; a closing scene.
- New games: `PICK` (engine/playground/Picks.kt, ui/playground/PickStage.kt; taps, no mic: sitting, wrists, short or
  long, pedal; picture rounds take a tap while the question is still being asked, listening rounds after their sound),
  `HAND` (HandStage.kt: the painted hand on the keys with numbers, bubble or home look; StageEar on its keys), `PEDAL`
  (PedalStage.kt), `BALLOONS`, `CAROUSEL`, `STRONGMAN` (FairStages.kt), `PHOTO` (PhotoStage.kt). Every one uses
  rememberStage / RoundCoach (shown first, a quiet stretch shows it again, three tries let it go).
- Scenes: `ArmVisual` (engine/theory/Theory.kt `ArmMove`, LessonScenes `arm(...)`, ui/theory/SoftFigure `softArm`),
  posture's CLOSE and FEET cues (softFigure `close`, `tucked`). FingersAgreeTest covers every exercise's book fingers
  against the words.
- The world: four new places (engine/world/Stickers.kt `Place.WORKSHOP, TREEHOUSE, NIGHT_SKY, FAIR`, the Playground's
  `Place` likewise), forty stickers (six common, three rare, one shiny each; webps from scripts/gen_stickers.py
  `--place`), four map scenes (ui/world/MorePlaces.kt: a workshop with a piano being built and a tape trail, a treehouse
  with a log trail, a night sky with a glowing trail, a fair with carousel, strongman and a midway), their trail rows
  (engine/world/Trail.kt; a short page is one row, its gate at its end: `WorldAnchors.gate(place, count)`), album
  colours, finale lines for every page ("You finished the workshop!", "Next stop, the fair!"). The new stickers are not
  audience animals (the hall's nine seats stay). A full album now takes about 114 drops (78 to 186 over 60 seeds).
  Stage backdrops: Scenery.kt `placeBackdrop(place, t, floorFrom)` for every place; the echo game uses lanterns in the
  treehouse and the night sky.
- Voice: 221 new lines (2839 recorded, none deleted), rendered at 0.85 after the catalog was complete.
- Tests: 828 JVM (797 before): WorkshopTest, FairTest, PickRoundsTest, and the pages-generic PagesTest, KidMapTest,
  PlaygroundFlowTest (52 lessons, silent and wrong-keys kids), PlaygroundChecks, StageHearingTest and StageSoundsTest on
  the new key sets, DropsTest, MapAnchorsTest, CourseScenesTest, FingersAgreeTest, KidCopyTest. Lint 0 errors (25
  warnings, all older).
- Emulator walk (5558 Fold; Mia pre-reader, Leo reader): the four scenes light and dark with every sticker, the
  workshop and fair map pages with stones and gate, every lesson's scenes and stages, picks by tap (right and not it),
  the hand stage hearing an injected key, the photo booth through by touch (three takes, playback, card), both finales
  (workshop to "Next stop, the fair!", fair to the big kids' road), recorded voice playing. Screenshots:
  app/build/screens/k40a-*.png (not committed).
- Debug: `--es lesson workshop-4 --ei step 1`, `--es kidseed workshop:<n>|fair:<n>`, `--es screen finale --es page
  workshop|fair`, `--es screen worlddemo` (every sticker of the seven places).
- Not proven: everything on a real piano with a kid. Thresholds tuned on PianoSynth only: the strongman's height, the
  carousel's smooth run, the balloons' timing. speakOverMic's deaf window can swallow a note played over the pup's call
  (the coach waits for the line first). `HAND` cannot see fingers by design: it hears keys and trusts the picture. A kid
  who had reached Basics before 2.3 is sent back to workshop-1 by CoursePath (the road opens again once every page is
  walked); their Basics progress is kept.

## 2.3 part B: the treehouse and the night sky (2026-10-18, branch `k40b`, emulator-5554 only; merged in 2.3.0 below)
Roy asked for 40 new kid lessons: which finger to use, basic ways to use the piano and hand placement, and lessons that
keep kids engaged. Part B is 24 of them on two new Playground pages; part A (branch `k40a`) owns the places, the world
and its two pages (Workshop, Fair).
- Treehouse (`TREEHOUSE_ID` "playground-treehouse", treehouse-1..12, engine/playground/Treehouse.kt): fingers that move.
  Finger walk (1 2 3 and back), the left hand's walk (little finger five on C, the thumb's walk down from G), the thumb
  tuck (1 2 3, thumb under to F, three over on the way back), fingers four and five, skips (1 3 5), five fingers in G,
  five fingers in F (finger four on B flat), the same tune in the other hand, copy my fingers (the pup calls "Two,
  three, four!"; the second game by ear), the left hand echo, which finger (tap the number the painted hand used, then
  play the key), and a concert (Hot Cross Buns with both hands; Twinkle with the band singing the tune and the left hand
  on the low notes).
- Night sky (`NIGHT_SKY_ID` "playground-night", night-1..12, NightSky.kt): rhythm and listening. Steady beat (clap,
  then C on every beat at 60, 80 and 100: a star a beat), ta ti-ti ta-a (say, clap, play), rests, the metronome game
  (on the click, every other click), then a tap game and a play-it game each: up or down (then a note higher or lower),
  same or different (then copy the tunes), louder (then loud and quiet), fast or slow (then your usual pace, fast and
  slow), long or short, happy or sad (then the happy chord: the sun comes out), a black-key jam with the band (every
  note a star, the tune drawn across the sky), and Twinkle under the stars with the band.
- Fingers agree everywhere: every exercise writes out the fingers of the hands the child plays, so the falling notes,
  ghost hand, hint hand, scenes, the copy stage's painted hand and badges and the pup's calls all say the same finger
  (TreeSkyFingersTest, and FingersAgreeTest over COURSE). The app hears keys, not fingers: no line says it saw a finger.
- Stages (ui/playground): CopyStage (treehouse: the painted hand plays each phrase, finger badges on the keys, the pup
  calls the fingers; night: stars sing the tune), TapStage (TapQuiz: the pup's example, three tries then he shows the
  answer; pictures with words for readers; which finger uses the painted hand and then the mic for the key),
  BeatStarsStage (drum or metronome, the matcher's own delay taken off a strike), HigherLowerStage, LoudQuietStage,
  TempoStage, SkyChordStage, StarJamStage (band in the black keys' key, the mic deaf 300 ms at each band note),
  TreeSkyConcertStage, the drum game under the stars; backdrops and the painted hand over a stage in TreeSkyStages.kt.
  Rules: TapGames.kt, BeatGames.kt, ListenGames.kt. (Merged, each is a `Game` value: see 2.3.0 status.)
- Voice: TreeSkyLines.fixed() (steps, scenes, the stages' lines) in SpokenCatalog.kidLines; 336 new lines recorded (314 at
  0.85), 2954 in the catalog; render_voice.py deleted nothing.
- Tests: 832 JVM (797 before): TreeSkyTest (shape, copy, bubbles, no digits in kid lines, no finger claims, stage marks,
  games, concerts, beats, jam, any-of), TreeSkyFlowTest (24 lessons, a silent and a wrong-keys kid; merged into
  PlaygroundFlowTest), TreeSkyHearingTest (every set on PianoSynth: positions, higher or lower, the jam, middle C; the
  drum, the clicks and the band never a key), TreeSkyFingersTest, TapGamesTest, TreeSkyGamesTest. Time model 114 to 180
  s a lesson. Lint 0 errors (25 warnings, all older). Screenshots of every lesson's main stage (pre-reader Mia p1,
  reader Leo p2): app/build/screens/k40b-*.png (not committed). Debug: `--es lesson treehouse-N|night-N --ei step n --es
  profile p1 --ez awake true`.
- Not proven: everything with a real piano and a child (emulator only); the beat stars' window (250 ms) and delay
  correction on a real phone; the fast and slow grade when the matcher drops a fast note; the jam's band on a phone
  speaker. On the emulator the stages hear the host's room and speakers (the demo comes back late through the host
  mic), so rounds there get found or let go by themselves; emulator-5554 (Medium_Phone_API_37.0) froze once under the
  host's memory pressure (0.5 GB free) and was restarted.

### 2.3.0 status (integrated 2026-10-19, branch `release-2.3`, versionCode 11; emulators only, not on Roy's phone yet)
`k40a` and `k40b` merged: forty more kid lessons on four more pages, so a kid's path is the garden, the pond, the
concert hall, the workshop, the treehouse, the night sky and the fair (76 Playground lessons, 146 in COURSE), then the
big kids' road. Grown-ups see all seven pages as Extra units on Learn, never as the next lesson ("0 of 70 lessons").
- Merge: COURSE is `PLAYGROUND_LEVELS + the course`; part B's two levels came off the end of COURSE and into Pages.kt as
  `page("treehouse", TREEHOUSE, Place.TREEHOUSE, "The treehouse")` and `page("nightsky", NIGHT_SKY, Place.NIGHT_SKY,
  "The night sky")` (part A's ids, places and titles; part B's notes said "night"). Three voice files both branches
  recorded ("Finger two!", "Finger three!", "Finger four!", both at 0.85): part A's kept.
- One Game registry: part B's stages are `Game` values now (`COPY`, `TAP`, `BEAT`, `HIGHER_LOWER`, `LOUD_QUIET`,
  `TEMPO`, `SKY_CHORD`, `JAM`) drawn by PlaygroundStage next to part A's; `hasPlaygroundStage` is `step.game != null`
  again. What a stage needs besides its exercise is kept by step text in engine/playground/StageGames.kt (as the
  workshop's pick rounds are); `Step.on(StageGame)` sets the Game and keeps the data; part B's `Backdrop` enum is part
  A's `Place`. The night sky's drum steps are plain `CLAP` (ClapStage draws the page's backdrop through `PlaceBackdrop`,
  the twinkling stars). The treehouse's and the night sky's concerts are `CONCERT`: concertPlan plays each step's own
  song (`TREEHOUSE_CONCERT`: Hot Cross Buns with both hands, Twinkle with the left hand under the band; `NIGHT_CONCERT`:
  the shelf's Twinkle, then "Twinkle all the way"), and ConcertStage hands them to TreeSkyConcertStage (their place, the
  step's practice, lanterns or shooting stars). placeBackdrop draws part B's treehouse and sky for those places (part
  A's simpler deck and hill went), so every stage on those pages looks the same.
- Fixed on the walk: the beat stars arced over the moon (the seventh star on the Fold, the seventh and eighth on a
  phone): the beat stage's sky has no moon now (`SkyBackdrop(moon = false)`). The fair's ticket booth stood under the
  photo booth stone, its stars on the roof: lower on the lawn now.
- Switched on by the pages: StepRetry's let go after three tries (Perform steps, concerts), PlayerScreen's `letGo`,
  `ANY_OF_LESSONS` = playground-1, 2, 3, hall-1, night-5, night-11 (the octave hint off there). Checked by PagesTest,
  CoursePathTest, TreeSkyTest and PlaygroundFlowTest (the concerts let go after three tries in the walk).
- Voice: render_voice.py on the merged tree: 3172 catalog lines (1115 kid lines at 0.85), all recorded, 0 new, 0
  deleted.
- Tests: 862 JVM (part A 828, part B 832, from 797; TreeSkyFlowTest folded into PlaygroundFlowTest, which walks all 76
  lessons with a silent kid and a wrong-keys kid, part B's stages modelled; TreeSkyTime folded into PlaygroundChecks:
  part B's lessons 114 to 180 s). FingersAgreeTest now checks the stages' exercises too. FixtureTest session-155837
  33/33, 0 false strikes, 0 wrong flashes; StallReviewTest, StageHearingTest green. Lint 0 errors (25 warnings, all
  older).
- Emulator walk (5558 Fold: Mia pre-reader p1, Leo reader p2; 5554 phone: Mia p1, made with the new-kid flow): the map's
  seven pages and the gate, treehouse-1, 3, 7, 9, 11, 12, night-1, 2, 5, 10, 11, 12, workshop-1, 3, 12 and fair-1, 4
  opened from their stones with their stages (the other night stages by the debug route, dark); workshop-12 finished by
  its steps, its finale ("Next stop, the treehouse!") rolling on to treehouse-1; the treehouse's finale to night-1, the
  night sky's to fair-1, the fair's opening the road (Sit next); dark mode; the adult Learn with the seven Extra units.
  No crash or ANR. Screenshots: app/build/screens/r23-*.png (not committed).
- Not proven: everything on a real piano with a kid; part A's and part B's thresholds are tuned on PianoSynth only.
  workshop-3 and Basics' fingers-right are both titled "Finger numbers" (left as part A wrote it).

## 2.3.1 audit (integrated 2026-10-21, branch `release-2.3.1`, versionCode 12; emulators only, not on Roy's phone yet)
Four audit teams went over 2.3.0 plus the bedtime change (b061572) on their own branches; their reports are
docs/AUDIT-ENGINE.md, AUDIT-ROBUST.md, AUDIT-ADULT.md and AUDIT-KID.md. 142 fixes (engine 28, robustness 37, adult 45,
kid 32), about 131 distinct bugs: eleven were found and fixed by two teams and are kept as one implementation each.
- Engine and content (JVM only): the matcher's late wrong-key report, Practice-tab thumbs on black keys and the chromatic
  fingering, Row Row Row's and the hall's Ode fingers, chord reviews due on their calendar day, one streak on the home,
  a 2.0 backup no longer wiping later files, damaged MIDI files, backwards A-B loops, Locale.ROOT for every number the
  app formats, 340 game and Practice-today lines that had no recording. The suite went from 23 min to about 2 (the
  hearing tests run on all cores, test/engine/playground/Parallel.kt).
- Robustness (emulator): singleTop (one activity), the screen kept on while playing, stages letting go of the mic on
  Home, the open screen saved as a route and reopened after a recreate or process death (a lesson at its step, a song at
  its place, a kid's gated screens as their back screen, the picker asking again), mic hand-over between overlapping
  screens (LocalLeaving), ForegroundClock (time off screen is never practice), a voice with no TTS engine, Kokoro released
  while loading, the import index written whole, the debug build's Compose activities unexported, a cold-start fingering
  memo (onCreate CPU 1567 -> 879 ms traced), backup rules (token, recordings, clips and the model stay home).
- Adult (emulator-5554): the onboarding placement and mic permission, Listen and empty runs earning nothing, Practice
  today's Back and recaps, games of skips earning nothing, mic and notification refusals explained, TalkBack (key names
  "F sharp 4", panes, the week row), imported songs renamed or deleted, the A-B loop's swapped marks, landscape scrolls.
- Kid (emulator-5558): stage time counts toward the goal, the goal's cheer after a lesson, a missed finale played on the
  next open, a kid's song bar (no times, loop, speed, chords, video, Hear my run or combo), the mic on for kids and asked
  for when a kid is made, no achievement banners for kids, sharing behind the gate, the gate shutting with the corner and
  holding three real seconds with animations off, skipped and let-go games one star, the octave hint in colours.
- Merge decisions (in the merge commits): crash reports keep robust's once-per-process hook and roster-name lambda with
  one `StallWatch.scrubNames`; the stalls budget is robust's whole-report `overBudget` ordered by the engine's
  `OLDEST_FIRST` stamps; imported song ids are the engine's ASCII ids with a code for names with no Latin letter;
  robust's route restore replaces the kid audit's lesson-only saver (same behaviour, checked: "screen restored:
  home:LEARN/lesson:1:playground-4" after `am kill`). The kid agent's unfinished let-go star cap was committed first.
- Build: APKs are arm64-v8a only (the note under "Build types" above); the x86_64 emulators run them through ARM translation. Debug
  APK 120.9 MB (120,877,029 bytes; 3514 voice files).
- Voice workflow: after changing spoken copy run SpokenCatalogTest, then `python -I scripts/render_voice.py --check`
  (no model needed; lists the lines a run would record and the files it would delete, says whether the list is older
  than the code, exit 1 when a run is needed); a run refuses a stale list unless --force. Merged tree: 3514 lines (1117
  kid at 0.85), all recorded, nothing to record or delete.
- Tests: 931 JVM, green twice with --rerun (about 2 min each). FixtureTest session-155837 33/33, 0 false strikes, 0
  wrong flashes (strict 33/33); StallReviewTest, StageHearingTest, SpokenCatalogTest green. Lint 0 errors (23 warnings,
  9 hints, all older).
- Emulator walk (5558 Fold, fresh install of the 2.3.1 APK): onboarding as Roy (mic asked and allowed, notifications
  refused), Basics lesson 1, Twinkle in wait mode by touch to its recap, every Settings section with Microphone setup,
  Privacy and Licences, Mia added through Settings > Family (gate), the picker, Mia's map, playground-1 to 3 from their
  stones to their recaps (three stars each), the Play sheet and the Jam, My stickers, the goal's cheer with lessons
  staying open, "Bedtime after the goal: on" then the asleep map and the goodnight, a kid's song bar, back to Roy through
  the corner, backup export (3997 bytes, both profiles), dark mode home for both (a dark-mode recreate reopened
  Settings). No crash or ANR in logcat (only the platform's codec queries). Screenshots: app/build/screens/r231-*.png.
- Still open from the reports' "found, not fixed" lists:
  - Engine: loud broadband noise can pass for a key (needs Roy's room recordings); a transposed song keeps its old
    black-key spellings; the lesson time model's floors; "today" goes stale across midnight while the app stays open;
    "This week" is the last seven days while the goal and paws run Monday to Sunday; the player's 60-day log is pruned by
    date; one recording per text whatever the speed (kid lines heard by grown-ups at 0.85 and back); about 700 recordings
    never played; near-duplicate lines and two lesson titles used twice ("Finger numbers", "Broken chords"); authored
    chord spellings lost ("Db" shown as C sharp); 9 and add9 chords span a ninth in one hand; a last note played late can
    miss when the song's tail is short; bar numbers from the first tempo; hand_l_3 decoded and never drawn; "Hear my run"
    files never read back or deleted with a profile; an unreadable roster resets to the owner and reuses p1; a
    hand-edited backup can carry a wrong type; the fingering solver can move the hand inside a five-key tune.
  - Robustness: debug routes reachable by any installed app on the debug build (#38); a session WAV left without a
    header after a kill (#39); the Jam header squeezed at font size 2.0 (#40); a debug route with `--es profile` opens
    the app twice (#41).
  - Adult: none open; mic detection, MIDI hot-plug, TalkBack speech, frame times, notifications and Google backup need a
    real phone, piano or keyboard.
  - Kid: none recorded (its agent was cut off; the 76-lesson walk table in AUDIT-KID.md was never filled in).
  - Seen on this walk, not fixed: the pup's bubble on the kid map covers the stone to its left (its stars stay visible);
    a clean wait-mode run at a slow touch pace still names a "hardest bar" (holds past 1.2 s count) and Practice today
    then loops it; the onboarding says "Nothing is sent anywhere" (true until a review token is set on a debug build).
- Not proven: everything on a real piano with a child, and frame times on the Fold with the profile build.

## 2.4.0 parent brief (integrated 2026-10-21, branch `release-2.4`, versionCode 13; emulator-5558 Fold only, not on Roy's phone yet)
docs/PARENT-BRIEF-PLAN.md, built in four steps and merged here: step 1 the event log and insights engine (on main,
engine/brief/INTEGRATION.md), `brief2a` the AI writer (engine/brief/ai/INTEGRATION.md), `brief2b` the template writer
(engine/brief/templates/INTEGRATION.md, SAMPLES.md), `brief3` the screens (ui/brief/INTEGRATION.md). The merge combination
compiled and its 1156 tests passed as merged; this step wired the pieces into the app.
- What shipped: a brief for grown-ups about each child (Session, Today, Week; a one-minute read with three facts, two
  or three things to try with buttons, one thing to praise; the full report with the seven-day paw strip, every lesson
  with stars and tries, songs, skills over four weeks, toys, time of day, the room and the hints, and what a grown-up did);
  Refresh, Send (PNG and PDF to the share sheet), Ask (typed or dictated on the phone); "My week" for grown-ups on the You
  tab; the pup's own card for the child after a lesson or a song played to its end, spoken, with Show Mum (a light gate,
  then the session note); the weekly notification at the day and time chosen when the kid was added (Settings > Family
  or the launch picker), whose tap opens that child's week.
- Doors: the kids' whistle corner ("Parent brief", behind the gate it already asked; closing it shuts the gate), Settings >
  Family ("Read the brief"), You > My week, the finish screens, the notification. The brief's dialogs draw the parent gate
  in their own window (it used to open under them).
- The writer chain (data/BriefAi.kt, ui/brief/BriefEnv.kt): per person, `WriterChain(AiWriter(OpenRouterTransport(...)),
  TemplateWriter(CourseTitles.lookup(songs), seed = id hash))`. The AI writes only when the build has a key and the parent
  corner's "AI briefs" is on (default on with a key); without either every call goes straight to the templates. The day
  and the week show the template brief at once and swap in the AI's when it comes ("Written by AI", a line under the brief
  says the AI saw numbers only); a cached AI reply (400 ms) shows without the swap. The session note and the pup's card
  wait 3 s (the AI's own limit, the screen's guard 3.5 s), a day 12 s, a week 12 s plus one retry. Refresh goes past the
  AI's cache. The weekly notification is written by the chain in the alarm's job; its headline is the notification text
  and opening it reads the cache. "Write briefs on mobile data" (off by default): otherwise the AI writes only on an
  unmetered network (data/Network.kt, shared with Reporter).
- The key: `secrets.properties` at the repo root (gitignored) -> `BuildConfig.AI_KEY` at build time; read only by
  data/BriefAi.kt (the transport's keyProvider at call time, and `hasKey`). Never logged: the AI's lines are `ai: ...`
  (times, sizes, kinds, decisions). This PC has no key, so every brief here was written by the templates; logcat over the
  walk had no "sk-or-" or "Bearer". The quota and reply cache (ai-brief.xml) stay out of Android backup and go with a
  deleted profile. `scripts/set-ai-key.ps1` writes the key into secrets.properties without showing it (`-Remove` takes it out).
- Turning AI briefs on (Roy): 1. at openrouter.ai give the key a spending cap (a few dollars), turn off prompt logging,
  check `anthropic/claude-haiku-5.5` has a provider that keeps nothing (the app asks `data_collection: deny`). 2. Run
  `.\scripts\set-ai-key.ps1` (paste the key at the hidden prompt; never in chat), then `.\gradlew.bat installDebug` with
  the phone on Wi-Fi. 3. Settings > Family shows "AI briefs" on. 4. Run the manual check in
  engine/brief/ai/INTEGRATION.md, "Manual check on Roy's phone" (log: `adb logcat -s SharpEars:D | Select-String "ai:"`):
  a kid's lesson finish gives a pup card `ai: reply in ... ms`; Family > the kid > Week says "Written by AI", reopening
  says `ai: WEEK from the cache`, Refresh writes anew; Ask "What should we practise tomorrow?" and "Is she better than her
  brother?" (a kind refusal); airplane mode gives "Written on the phone" and `ai: NETWORK`; mobile data without the switch
  `ai: NOT_ALLOWED`; OpenRouter's Activity shows one request per AI brief. HTTP 401 wrong key, 402 credits or cap, 404
  "data policy" no zero-retention provider (fix the account setting, or build with privateProvidersOnly = false and change
  the privacy paragraph). Rotate the key when the beta ends; before people Roy does not know, the relay (Option A).
- The seven actions (engine/brief/PathEffects.kt, pure, one JVM test each; ui/brief/AppEffects.kt over the child's own
  KidStore and Prefs; MainActivity's BriefNav for screens): Set as next pins a lesson for a day (KidMap, Practice today and
  a grown-up's Learn start read engine/kid/Pace.kt; finishing it clears the pin); Replay opens the lesson; Practise together
  opens the song as a duet with the open grown-up, else the owner, on the left hand (Duet.grownUpFor), a lesson with the
  grown-up sitting in, or the child's songs to pick one ("Pick a song to play with Roy!"); a grown-up's own week opens the
  duet picker; Fresh start forgets a page's stars and done marks after two confirmations (stickers and the finale stay);
  Focus hides the later pages and the road and keeps the pup on the page; Open the next page walks the path past the
  current page as if its finale had played; Teacher mode puts the song on top of the kid's shelf (three at most) with the
  note, first on the kid's Songs ("From your grown-up", tap the note to hear it); the five settings as brief3 built them.
  Undo where the card offers it (60 s or until the brief closes); an action for another child switches the app to them
  first; a lesson opened from the brief after today's goodnight is the grown-up's one more lesson.
- Privacy: Settings > About > Privacy has "The parent brief" (findings only, never a name, a recording or a typed note;
  the zero-retention routing; the checks; when the phone writes instead), and its first and Internet paragraphs name the
  AI brief on a build with a key. The onboarding's "Nothing is sent anywhere" stays only on a build without a key.
  docs/privacy-policy.md and docs/publishing.md have the public-app versions (with the relay), and the Children section
  is flagged for review.
- PlayerScreen split (its own commit, d02d998): the composable's screen-long state moved into `PlayerVars` read through
  `with(v)`, and its parameters into `PlayerArgs` with the body in `PlayerStage` read through `with(args)`. dexdump: the
  body 275 -> 106 registers, Lane 130 -> 74, Bar 122 -> 72, Keys 118 -> 88. Behaviour unchanged (the whole suite with
  FixtureTest and the audits; on the emulator a library song in Listen with its video, Wait and Perform to their recaps,
  adult and kid lesson steps, a Playground stage, the mic check, a kid's duet and a grown-up's duet picker). The largest
  method in the app is now LessonScreen (324 registers): the same split applies there if D8 ever misbehaves.
- Fixed on the walk: an open session read as no practice in every session note and pup card (the provisional end was
  stamped after the findings' clock); "My week" asked "How much should they practise?"; the templates said "the piano
  came out seven times" for visits with no practice; the export's log line named the person; Replay and Practise together
  after goodnight landed on the asleep screen.
- Tests: 1187 JVM (1156 merged, 31 new: BriefServiceTest, PathEffectsTest, KidMapTest's pace, KidStoreTest, DuetTest,
  BriefsTest, AskBriefTest), green. Lint 0 errors (28 warnings, 9 hints: UseKtx style ones, five of them in brief3's files).
- Emulator walk (5558 Fold, the debug APK over 2.3.1's data, Roy owner, Mia p1 pre-reader; Theo and Noor added): My week
  short and full, Refresh, Send (the share sheet with a PNG and a PDF, cancelled), a typed question; Settings > Family's AI
  switches (off, "no AI key"); Theo added in Settings (Friday at 6 pm) and Noor from the launch picker (Tuesday at 6 pm);
  as Mia, Pup's steps and Hot Cross Buns to their finish, the pup's card spoken and shown, Show Mum's light gate and the
  session note ("three minutes this session"); every action card performed through the debug samples door (below) and
  undone where it can be (Set as next moved the pup to stone 8, Fresh start emptied the garden's stars after two
  confirmations, Focus hid the pond and the road, Open the next page, Teacher mode's Ode to Joy with its note on Mia's
  shelf, the duet on Twinkle as "Duet with Roy", the song pick, Replay, Bedtime); the real full report (lessons with
  stars, songs, toys, time of day, hints, "What you did"); dark mode (the brief reopened after the recreate, behind the
  gate); the cover screen (`emu fold`); the weekly notification posted with its headline and tapped (the gate, then Mia's
  week over the picker). No crash or ANR (crash buffer empty). Screenshots: app/build/screens/r24-*.png (not committed).
- Debug (debug builds): `--es brief_profile <id> --es brief_period SESSION|DAY|WEEK` is the notification's door (any
  build); add `--ez brief_samples true` for the real cards and stores over findings that offer every action
  (ui/brief/BriefDemo.kt, sampleActionsEnv). The brief3 demo route (`--es screen brief`) is unchanged.
- Open:
  - Nothing ran against the real model (no key on this PC): wording, the 3 s card on the phone and zero-retention routing
    for Haiku 5.5 wait on Roy's manual check above. Gemini Nano on the Fold7 (plan's open item) not tried.
  - A session's practice comes from its end event: a process killed mid-session (an install, a crash) loses that
    session's practice in the brief (the daily goal's own count keeps it). A periodic checkpoint would fix it.
  - On the cover screen held sideways the brief's header and tabs take half the height (they do not scroll away), and the
    parent gate's pad needs a scroll; portrait is fine.
  - My week's action cards ask the parent gate like a kid's (brief3's rule); for a grown-up's own week the gate could be
    skipped (guardForKids).
  - Pins, focus and pages opened early have no screen of their own to see or clear them (only the card's Undo, then they
    expire: a pin after a day; focus stays until Open the next page).
  - Dictation is code-reviewed only (nothing spoken to the emulator); the share sheet was opened, no app picked.
  - Kid profiles and the AI brief need the Play Families policy and children's privacy rules checked before a public app.

## 2.5.0 wardrobe, trail stickers and the brief by email (integrated 2026-10-21, branch `release-2.5`, versionCode 14; emulator-5558 Fold only, not on Roy's phone yet)
Three branches merged: `wardrobe` (ui/world/INTEGRATION.md 10 to 12, ui/kid/INTEGRATION.md), `mail-engine`
(engine/brief/email/INTEGRATION.md) and `mail-ui` (ui/brief/email/INTEGRATION.md). The merge compiled and its 1291 tests passed
as merged (no conflicts left in ProgressFiles.NAMES, the corner sheet, My stickers or CLAUDE.md); this step wired what the three
left to the integrator, fixed what the walk found and released.
- The wardrobe (Roy: outfits "that kids could earn the more they play", not too hard, not too easy): 60 items in four slots that
  combine (head 23, face 9, body 22, feet and back 7), each with a kid's name and a first-worn line, ten full costumes as sets,
  drawn in code over pup_wear_base (ui/world/Outfit.kt, WearHead/WearFace/WearBody/WearBack.kt), base pose only. Earned from the
  practice ledger (`Ledger` in WorldState, rates at the top of engine/world/Wardrobe.kt): sparkles for minutes (1), days with a
  minute (5), a lesson's first three stars (4), songs to the end (2, five a day), page finales (20); commons along a practice
  track (12, 42, 87, 147, 237, 337, 447, then +120 each; one a day at most), rares on milestones, shinies on big ones. Never
  bought, never lost. Typical kid: item 1 at session 1, 15 items after month 1, 48 by month 6 (WardrobeTest).
- Shown: dress-up by slot with rarity borders; the next outfit as a ring for pre-readers and a bar for readers (no numbers),
  also round My stickers' Dress up tab; the next finish screen reveals a new item ("Keep it on?" Yes / Not now, said by the pup).
  A dressed pup sits in his base pose on the map, the dress-up screen, a kid's song recap after a good run and the first bow of
  a finale (the last two added here; ui/world/INTEGRATION.md 10).
- Trail stickers (Roy: stickers "which they can place around the trail themselves"): a sticker per Playground and Basics stone
  (82, engine/world/LevelStickers.kt, placeholders), earned on the stone's first finish, a shape on a white disc on the stone
  until then; the drops and the egg stay as bonus stickers. The album goes page by page in stone order. Decorate mode
  (ui/kid/Decorate.kt): a tap in My stickers' tray (it wiggles) or a long press on a spot; tap a sticker then a spot, or drag;
  12 to 14 named spots per scene (engine/world/Decor.kt, DecorTest keeps them off stones and stars); placements persist per
  profile (WorldStore) and show on the map, the world tab and the finale. Old worlds keep their items and the pup asks for new
  homes once. 147 new kid lines recorded (3661 voice files).
- The brief by email (docs/PARENT-BRIEF-PLAN.md, design D): each child's brief to a grown-up's address through Brevo, daily
  (30 minutes after the day's last session, 20:30 at the latest), weekly at the weekly slot, both or off, chosen after the
  first email; the first email carries a six-digit code that confirms the address (typed in the app). Quiet 21:30 to 07:00, two
  a day and ten a week at most. Nothing in the email can act: every suggestion says where to tap in the app. Design D as table
  HTML with inline styles (engine/brief/email/EmailHtml.kt, snapshots in app/src/test/resources/email), the full report as a
  PDF. One family alarm (reminder/EmailJob.kt, EmailReceiver: alarm, boot, time zone, clock; request code 70001). Settings per
  child behind the gate (ui/brief/email/EmailSettingsScreen.kt) with a preview (WebView, no network) and "Send me a test email
  now"; doors: the kids' corner > Family > the child > Email the brief, Settings > Family, the brief's own Email button. The
  buttons' deep link `sharpears://brief/<profile id>/<day|week>` opens that child's brief behind the parent gate. Privacy page
  and onboarding say it only on a build with an email key. "Send emails on mobile data" (off by default).
- Keys: `BuildConfig.EMAIL_KEY`, `EMAIL_FROM`, `EMAIL_FROM_NAME`, `EMAIL_IMAGE_BASE` from secrets.properties (gitignored), read
  only by data/BriefEmail.kt; `scripts/set-email-key.ps1` writes them without showing the key (`-Remove`; `-ImageBase <https>
  -PicturesOnly` sets the pictures' address alone and keeps the key). Logs say `email: <KIND> <day> for <profile id> ...`, never
  the key, the address, a name or a body. This PC has no key: every send was refused `NO_KEY` and the screen says "Email is not
  set up on this build." in plain words.
- Wired at integration: every session's end (`Briefs.onSessionEnd`) syncs the open profile's ledger, then plans the email job
  (the app start runs it, the boot receiver too); a kid's lesson finish reads in order: stars, the stone's sticker (its shape
  turning into the sticker, `LevelStickerEarned`), the drop, the rest, something new to wear last (the reveal scrolls itself into
  view), then the pup's card before whichever way on, "One more" included; the email pictures ready to host (below); the
  privacy page names the pictures' host on a build with one (`EmailImages.hostOf`); `world: n level stickers` logged 0 (fixed).
- Hosting the email's pictures (Roy, once): Gmail does not show pictures inside an email, so until they are hosted the alt
  text shows ("The blue pup dancing"). The 27 PNGs are in `hosting/email/` with hosting/README.md: a public GitHub repo with
  Pages (or raw.githubusercontent.com), then `.\scripts\set-email-key.ps1 -ImageBase https://<you>.github.io/<repo>/email
  -PicturesOnly` and `.\gradlew.bat installDebug`. Each picture is then `<base>/<file>.png` exactly (EmailHtmlTest checks it and
  that hosting/email matches app/src/main/assets/email). Empty base: inline pictures, as before. Brevo's image library renames
  files, so it only works if its addresses keep the names.
- Manual checks Roy must run with his keys (phone on Wi-Fi, `adb logcat -s SharpEars:D | Select-String "ai:|email:"`):
  1. AI brief: 2.4.0 above, "Turning AI briefs on", and engine/brief/ai/INTEGRATION.md's manual check.
  2. First email: `.\scripts\set-email-key.ps1` (Brevo key at the hidden prompt, then the verified sender), install; a kid >
     Email the brief, Roy's own address, "Send the first email": within a minute an email with the code in large digits
     (`email: FIRST ... sent`); a wrong code once (one try fewer), then the right one; choose Both.
  3. Test email: "Send me a test email now" ("Sent. Check your inbox.").
  4. Design D in Gmail (app and web) and Samsung Email, light and dark: postcard, In one minute, What I'd do next with its
     where-to-tap lines, the five sections, the slip keys, bars, the gold star, the footer, the PDF; tap a "Do this in the app"
     button with the app installed (Gmail may refuse a non-web link: then the where-to-tap line is the fallback); not clipped.
  5. The rest of ui/brief/email/INTEGRATION.md's list: the daily 30 minutes after play, the weekly at its slot, airplane mode,
     mobile data, a reboot, "Stop these emails"; `Select-String "@"` and `"xkeysib"` over the log show nothing.
- Tests: 1292 JVM (1291 merged, 1 new: EmailHtmlTest.hostedPicturesAreTheBaseThenTheFileName), green. Lint 0 errors (28 warnings,
  9 hints, as before).
- Emulator walk (5558 Fold, the debug APK over 2.4's data; Roy owner, Mia p1 and Noor p3 pre-readers, Theo p2 reader):
  Noor's first 2.5 open (ledger 11 sparkles after `--ei practice 6`), High and low's last stage to its finish: three stars, the
  See-saw's shape turning into the sticker, a tulip drop, the ledger crossing 12 on that finish and the bow tie revealed, "Keep
  it on?" Yes, worn on the map; My stickers: the album in stone order, the tray, the ring "Just started!"; decorate mode by tray
  tap on the garden (roof, flower bed) and on the pond in dark mode, all three kept after a force stop; Two black, three black:
  sunglasses revealed and scrolled into view; a session with two minutes of stage time: the pup's card before "One more", then
  the next lesson; Hot Cross Buns to its end (three stars, rare butterfly) with the dressed pup on the recap; the garden's finale
  bowing in the outfit over the decorated scene; dress-up and the map in dark; Theo's reader bar, his first Black keys sticker,
  the card's Show Mum (the light gate, the session note); the email settings in every state on this keyless build (no address,
  a bad one, the first email refused kindly, code, code gone, unchosen, verified, daily, held, the test email refused), the
  preview's day and week, the corner's one Family row > Noor > Email the brief, the deep link cold (the gate, then Theo's week;
  within the gate's ten minutes it opens straight), `dumpsys alarm` shows EmailReceiver, every session end logs
  `wardrobe: ledger ...` then `email: planned (session ended)` and the alarm's run `not sent: NO_KEY`; the parent brief: Mia's
  week with action cards (Set as next, then Undo), My week for Roy. Logcat over the walk: no app exception, no ANR, crash buffer
  empty, no "sk-or-", "xkeysib" or "Bearer", no address from the app. Screenshots: app/build/screens/r25-*.png (not committed).
- Debug: wardrobe `--es wardrobe all|<ids>`, `--es wear <ids>`, `--es fresh <ids>`, `--es decor <key>@<slot>+...|clear`,
  `--ez homes true`, `--ei practice <minutes>` (added today, for the ledger); email `--es screen email --es child <id> --es seed
  none|waiting|code|gone|unchosen|verified|daily|held [--es view preview --es period day|week]`.
- Open:
  - Nothing reached Brevo or OpenRouter (no keys here): the real emails, Gmail's handling of the deep link and the hosted
    pictures wait on Roy's checks above. The stop line is a `mailto:` handled by hand.
  - A keyless build plans an email look at every session end and refuses it (one log line); harmless, gone with a key.
  - Android delivers BOOT_COMPLETED when a force-stopped app is started again, so the job runs twice on such a start (it is
    locked; nothing doubles).
  - The outfit is drawn on the base pose only; the lesson celebration, the pup's card and Pup says keep their own poses.
  - Decorate mode is kids only; dragging cannot scroll the map; spots on pages not reached stay shut; in decorate mode on the
    Fold the next page's heading shows faintly through the bar.
  - All sticker and outfit art is placeholder (GEMINI-BRIEF B2 and C2); nothing seen by a child yet.
  - The Family list shows "Change" on each child, which also holds Read the brief and Email the brief (the emails say
    "Family > Mia > Email").

## 2.6.0 songs by chords, placement and tester analytics (integrated 2026-10-21, branch `release-2.6`, versionCode 15; emulator-5558 Fold only, not on Roy's phone yet)
Four branches merged: `popc` (the chord charts, docs/CONTENT.md), `popp` (the chord-mode screens and the band,
ui/chords/INTEGRATION.md), `place` (the placement step, ui/family/INTEGRATION-PLACEMENT.md) and `posthog` (tester analytics,
analytics/INTEGRATION.md). The merge compiled and its 1414 tests passed as merged; this step wired what the four left to the
integrator, fixed what the walk found and released.
- Songs by chords (Roy: "give these specific chords for these popular songs so they can learn them"): 107 popular songs as
  chords only (engine/chords/PopSongs*.kt): key, tempo, section order and the chords of each bar, never a melody, a lyric or a
  recording. The legal line, the lyric guard and how to add a song: docs/CONTENT.md, "Popular songs by chords". By the
  numbers (a song counts under each of its styles): greatest hits 24, yacht rock 8, 80s 23, pop 40, rock 18, country 10, soul
  and ballads 35, kids 13, holiday 5, worship 6; difficulty 34 at 1, 40 at 2, 11 at 3, 22 at 4; 6 CHECKED, 78 LIKELY, 23
  SKETCH (every tile and page says "checked", "close enough" or "a sketch"); 31 four-chord songs.
- Where it shows: Chords > Songs (style chips with their counts, "All 107"; Easiest first, Newest, Songs I can play, One new
  chord away; lazy tiles of chords, difficulty bars and the confidence note); the song page (the chart by section, transpose
  -6 to +6, tempo in steps of 4, seven patterns with a feel line, Hear the band lighting the chart bar by bar, Play along: the
  player in Wait with the right hand, the app on the left hand, the Band toggle for drums and bass); "You can play N songs"
  under the Chords tabs (known = Leitner box 3; a tap opens Songs I can play); the Songs tab card for grown-ups; "Songs that
  use these chords" after a Chords > Daily session (the known chords plus the session's; "See all the songs you can play");
  and for kids a "Songs by chords" tile on the kid's Songs (letters for a reader, animals for a pre-reader) that opens the
  Kids-style list and the kid page (colour blocks, no numbers, a tap plays the chord).
- One rule each (wired here): the four-chord badge is the content's `PopSongs.isFourChord` (major key, every chord its I, IV,
  V or vi, colours allowed; popp's stricter copy was deleted), Easiest first is `PopSongs.beginnerOrder` (difficulty,
  four-chord songs, newest, title), the chords a song needs and what known chords play are `PopSongs.chordsOf` and
  `playableWith`, keys move with `PopSongs.transposed`; `PopPlay.toSong` is the one `toSong`. PopSamples.kt is gone.
- Pop runs are kept like library songs (ui/PlayerScreen.kt `popRunEnd`): a Wait or Perform run that played something logs
  the brief's SongRun under `pop-<id>` (stars from the Perform accuracy, or the wrong keys in Wait), keeps a best from the top
  and a first completion (`best:pop-<id>`, `done:pop-<id>`), records the SongRun progress event (XP) and shows the recap;
  Listen keeps nothing. No sticker, quest, trouble bars or auto speed (those belong to library songs). A kid's run to the end
  shows the pup's card first. The brief's three title lookups answer `pop-<id>` with the song's title (My week says "Wagon
  Wheel"), a brief action on a pop run opens its page, and opening a page is `FeatureUsed("popsongs")`.
- Placement (Roy: "ask what their current piano level is, so we don't have to show them all the introductory stuff"):
  grown-ups say Never, A little, Some or A lot (Basics, Beginner, Intermediate, Keys; Learn shows "You skipped these. Open
  any time." over what was passed by); a parent answers for a kid (first time: the garden; has played a bit: the pond; has
  had lessons: the workshop, with the hall's recital as a come-back stone). Skipped lessons are done for the path and never
  for credit: no XP, stars, stickers, finales or brief events. "Let's hear you play" (find a C and C D E F G for A little,
  plus Twinkle's phrase with the band for Some and A lot; never for Never) follows the onboarding's "You're all set" and,
  when a parent tapped "Let them try the check", a kid's first open; it only ever makes the start gentler, shows no mark, and
  counts as a first lesson once (the XP event, three stars, a sticker drop, sparkles at the next ledger sync; no brief
  event). Family > the person > Piano level changes it later and re-plans in place. Added here: the check's id has a title
  for the brief ("Let's hear you play", `Placement.CHECK_TITLE`), and only a check played through moves the level.
- Tester analytics (Roy tests with friends and family; he gets their consent and removes it before any public release):
  "Help Roy test", one phone-wide switch behind the parent gate (the kids' corner and Settings > Family), with an accent dot
  on the kids' whistle while on (on by default since 2.13 where the row shows, a one-time notice to a grown-up; see 2.13); PostHog session replay (all text and pictures masked, no sound, kids recorded masked, as
  "kid") and app events only on a build with POSTHOG_KEY; "Email Roy a daily diary" only on a build with DIARY_TO (hidden on
  this PC); "Something felt off" (one line kept with the screen) while a switch is on. The keyless APK has no PostHog class:
  dexdump over all 19 dex files of the 2.6.0 debug APK finds 0 `Lcom/posthog/` among 22,746 classes; `-Psharpears.posthog=true`
  compiles the SDK in. EmailJob calls `Briefs.init` in both `run` and `plan` (the alarm can wake a process with no screen).
  Details: analytics/INTEGRATION.md.
- Roy's PostHog setup: 1. posthog.com, sign up (free plan), create a project (US or EU cloud), Settings > Project > Project
  API key (phc_...); Settings > Session replay: "Record user sessions" on; Data retention 30 days. 2.
  `.\scripts\set-posthog-key.ps1` (the key at the hidden prompt; `-ApiHost https://eu.i.posthog.com` for EU; it also asks for
  the diary address, Roy's own). The diary needs the Brevo key too (`.\scripts\set-email-key.ps1`). 3. `.\gradlew.bat
  installDebug` to each test phone (never paste the key in chat). 4. On the phone: Help Roy test is on by default (2.13; a
  grown-up sees the notice once and taps OK); a phone where someone turned it off: the kids' whistle (or Settings > Family) >
  the gate > Help Roy test on; the dot shows. 5. Replays: PostHog > Session replay (a few minutes after a session, on Wi-Fi or
  data); events in Activity; filter by `profile_kind`. 6. End of the beta: the switch off on each phone,
  `.\scripts\set-posthog-key.ps1 -Remove`, rebuild, and in PostHog Settings > Project > Danger zone, Delete project (that
  deletes every recording and event).
- Manual checks Roy must run (phone on Wi-Fi, `adb logcat -s SharpEars:D`):
  1. First replay masked: with the PostHog key in and Help Roy test on, play a lesson as a kid and a song as Roy; in PostHog >
     Session replay every text and picture is a grey box, the corner, the brief and the names are masked whole, and there is
     no sound. If anything shows, turn the switch off on every phone before anyone else plays.
  2. Diary email: with DIARY_TO and the Brevo key in, Settings > Family > "Email Roy a daily diary" on, play, keep a
     "Something felt off" note; the diary comes once a day between 20:30 and 21:30 (or the next morning from 07:00), plain
     text, people as "kid p1", never a name; `Select-String "@"` over the log finds nothing from the app.
  3. Band sound balance on the Fold: a pop song's page > Hear the band with "Piano with it" on and off, then Play along with
     Band on, in Wait and in Perform: the drums and bass sit under the piano, on the speaker and on headphones (the band is
     silent while the mic listens, unless headphones are on).
  4. A pop song with the mic: Play along on the Yamaha (Wait, right hand, mic on): each chord heard as one group and the song
     moving on; then a Perform run to the end: the recap's stars and `pop: run pop-<id> mode=PERFORM accuracy=...` in the log.
  5. The earlier ones still stand: 2.4.0 "Turning AI briefs on" and 2.5.0's email checks.
- Fixed at integration: the style chips had no counts; the tile and the page put a middle dot between artist and year (the
  design rules forbid it) and their badge rows did not wrap on a narrow screen; nothing opened chord songs for kids or after a
  chord session; a pop run kept nothing and showed no recap in Wait; "Let's hear you play" said "Lovely playing!" over
  "Lovely playing." when it agreed (the title is now "Thanks for playing!"); a skipped or left check stored the level it was
  opened with (only the debug route could tell); a kid's placement line read "The garden, The pond and The concert hall stay
  open".
- Tests: 1415 JVM (1414 merged; PopFindTest lost its own four-chord tests to the content's rule and gained chip counts and
  the content's order, PopPlayTest gained run ids and the content's transposition, PopTitlesTest 3 new (a pop run and the
  check titled in every lookup, a week of a pop song named and never its id), PlacementTest and TesterRulesTest new
  assertions), green.
- Lint: 0 errors, 28 warnings, 9 hints (as 2.5.0; popp's song page read the window height from the configuration, the one
  new warning, fixed). `:app:compileDebugKotlin -Psharpears.posthog=true` compiles the PostHog door (quote the argument in
  PowerShell 5.1: `"-Psharpears.posthog=true"`, or it splits at the dot). APK: app/build/outputs/apk/debug/app-debug.apk,
  128,275,170 bytes (122.3 MiB), versionCode 15, 2.6.0.
- Emulator walk (5558 Fold, the debug APK over 2.5's data, then `pm clear` for a first run; touch keys, no mic):
  over 2.5's data as Roy, the Songs tab's "Songs by chords" card, Chords > Songs (All 107, the chips with counts, Kids 13,
  each sort), Wagon Wheel (difficulty 1, four chords) transposed up 3 to C with the Whole pattern, Hear the band (`pop:
  hearing wagon-wheel C WHOLE at 148 bpm`, `band: kick hat bass36` cues), Play along in Wait by touch through nine bars with
  the Band toggle on (the band's cues in the player), then the last 14 bars after a seek to the recap (three stars, +50 XP,
  `brief: owner {"e":"song","song":"pop-wagon-wheel",...,"stars":3}`, `pop: run pop-wagon-wheel mode=WAIT ... xp=50`), back
  to the page in C and Whole; as Mia (pre-reader) the kid Songs tile with animals, the kid chord list, Row, Row, Row Your
  Boat as animals; the corner's Testing rows (no diary row), Help Roy test on (`tester: help Roy test on`), the whistle's
  dot, a "Something felt off" note (`tester: note on home:SONGS (33 chars)`), no PostHog file and no device id. Fresh data:
  the onboarding's four levels, A little, touch keys; "You're all set" with "Let's hear you play"; the microphone refused;
  find a C and C D E F G by touch: the first time one task was let go (the start went gentler, Basics; the reward: three
  stars, the red tulip, `kid: placement-check 3 stars`, a StickerDrop with no lesson, no LessonDone), the second played
  through (`began at A_LITTLE (said A_LITTLE, 2 of 2)`, no second reward); Learn opening at Beginner with Basics "Skipped" and
  "You skipped these. Open any time."; Settings > Family's Piano level (the gate first), Lea added from Family (reader) and
  set to "Has had lessons" there (the workshop next, the hall's recital open as the come-back stone), Sam added (reader, "Has
  played a bit"): the pond next, the garden "passed by, open to play", the kid Songs tile with letters, the kid chord list,
  Twinkle's page in letters; with `--es known C,F,G,Am,D,Em`: "You can play 12 songs", Songs I can play, One new chord away
  ("needs G7"); a Major triads session in Chords > Daily to "Session done!" with "Songs that use these chords" (`pop: songs
  for the session's chords: dont-you-forget-about-me, wonderful-tonight, three-little-birds`) and "See all the songs you can
  play" opening Songs I can play; a second Wagon Wheel run, then You > My week: "most often Wagon Wheel ... at three stars";
  dark mode for Chords > Songs, a page, the kid tile, the kid list and page, Learn's skipped band and the check's band task;
  the cover screen (`emu fold`) with Kumbaya's page ("a sketch") in dark and light; Settings > Family's Testing rows on and
  off. Logcat: no app exception, no ANR, crash buffer empty, no "sk-or-", "xkeysib", "phc_" or "Bearer", no "@" from the
  app, no posthog line. Screenshots: app/build/screens/r26-*.png (not committed).
- Debug: `--es screen popsongs [--es chip all|hits|yacht|80s|pop|rock|country|soul|kids|holiday] [--es sort
  easiest|newest|can|one] [--es known C,F,G,Am]` (those chords known, box 3), `--es song <id> [--ei shift n] [--es pattern
  WHOLE|...] [--ei bpm n] [--ez hear true] [--ez kid true [--ez pre true]]`, `--ez kid true` with no song (the kid list),
  `--es view cards`; placement `--es screen placement --es said SOME [--ei task n] [--es inject synth:...]` (the level moves
  only when the check is played through).
- Open:
  - The four-chord badge is the content's rule, so a two-chord (I V) or three-chord (I IV V) song wears "four chords" too
    (The Wheels on the Bus, Ring of Fire). Roy decides: keep it, name it differently, or have the rule ask for all four.
  - Nothing ran against PostHog or Brevo (no keys here): checks 1 and 2. The band and the pop songs were heard on the
    emulator only: checks 3 and 4. 23 charts are sketches; Roy's ear is the check.
  - A pop run drops no sticker and counts for no quest (the brief and XP count it). The kids' chord list is the Kids style
    only (13 songs); holiday and worship songs a child knows are on the grown-ups' list.
  - "You can play" counts a chord known at Leitner box 3 (`PopFind.KNOWN_BOX`); the app's own "mastered" is box 4.
  - Placement is tuned on synthetic audio and touch keys; the band task's 60% is unproven in a real room. With the microphone
    refused the check plays on the screen's keys (walked that way here).
  - The new-kid flow's "Go" is ready before the level question is answered (it appears once "Can they read yet?" is), so a
    parent who taps Go at once gets "first time"; Family > Piano level fixes it.

## 2.7.0 navigation (2026-10-22, branch `ux-today`, versionCode 16; emulator-5558 Fold only, not on Roy's phone yet)
Roy's restructure of the grown-ups' navigation, plus his Simple / Advanced mode. Nothing new was built beyond that; the kid
home (ui/kid) is untouched and ui/kid/INTEGRATION.md did not change (the kid Songs tile still opens the kid chord list).
- Five tabs, in this order: **Today · Songs · Learn · Practice · You** (ui/HomeShell.kt `HomeTab`, labels from
  engine/nav/Nav.kt `NavTab`). Before: Songs (the old home) · Learn · Practice · Chords · You.
- Where everything moved:
  - Today (new first tab, ui/TodayScreen.kt): the header (logo, "Today", the date, the Simple pill), the pup's one greeting
    line, the one-time "Set up mic" banner (closing it is per profile; Settings > Microphone stays the way in), the Today
    card (the plan rows from DailySession's plan in Start order: "N chords due", the next lesson, a song or a two-minute loop;
    one Start walking the plan as "Your plan, part N of M"; Pup's quest as a row of three stars that opens; the goal as one
    small line), Continue (the last song, pop song or lesson, one tile; `Prefs.lastPlayed`, cleared by Reset progress) and
    Explore (Songs, Chords = Learn, Play corner = a sheet with Jam, Pup says, Clap along). The engine side is
    engine/nav/Today.kt (`TodayCard.of`, `ContinueWith`).
  - Off the old home: the header stats went into You's goal card ("This week  N min"), the family wall went to the top of You
    (shown with 2 or more profiles), My week stays on You.
  - Songs (ui/LibraryScreen.kt): the Play corner row at the top ("just for fun"), then Library | Songs by chords. Library
    keeps filters, Start here and imported songs (rename, delete). Songs by chords (was Chords > Songs) is the same list,
    chips and sorts (`popBrowseItems`); the home's pop-songs card is gone.
  - Learn: a Chords section pinned over the path (a sticky header on the page's paper): Learn chords, Games, Progressions.
    The Chords tab is gone. Learn chords (`Screen.LearnChords`) merges Daily, Lesson packs and the Dictionary (now a sheet)
    and keeps "Practise any chord" and the daily chord review line; Games (`Screen.ChordGames`) is the old Chords > Play;
    Progressions (`Screen.Progressions`). The daily chord review is also a row of Today's plan. Theory, Ear training and
    Sight-read keep their cards under the Chords section.
  - Practice: Scales and Warm-ups are the main sub-tabs; Metronome and Tuner share a third, Tools. It opens on the last tool
    used (`PracticeStore.lastTool()`, key `practice.lastTab`, per profile).
  - You: the family wall at the top, the goal card with the week's minutes, the Simple pill beside the gear.
  - Settings, six groups: Family (people, Progress), Playing (Simple mode, keys, falling notes, show wrong keys, sound,
    voice, reminder), Briefs and email, Microphone, About, Testing (last). Every switch kept its key.
- Naming, fixed once (engine/nav/Nav.kt `Names`): "Today" is the home, "Songs" is the Songs tab, "Songs by chords" its
  second half, "Learn chords", "Games" (the chord games), "Progressions", "Play corner" (Jam, Pup says, Clap along), "Tools"
  (metronome and tuner). Lines that said "the Chords tab" now say where it went (Course.kt's two captions, re-rendered:
  4 voice files new, 4 deleted).
- Simple / Advanced (Roy, mid-task): one switch per grown-up (`Prefs.simpleMode`, null = the placement decides: Never and A
  little start Simple, Some and A lot Advanced; a kid is never Simple). It is on Today's header and on You (the Simple pill)
  and in Settings > Playing. Every rule is a pure function in engine/nav/Simple.kt and Simple only filters: Learn chords keeps
  C, F, G, Am, Dm, Em (the dictionary as six pills, no key picker, qualities or inversions; the packs cut to those chords;
  the drill draws from them), Games keeps Build it and Hear it, Progressions keeps the plain-triad ones (I-V-vi-IV and
  I-vi-IV-V: only two exist), Songs by chords keeps four-chord and difficulty-1 songs with Whole and Halves, Practice keeps C,
  G and F major and one warm-up and hides Tools and arpeggios, Learn hides Theory, Ear training and Sight-read (the path
  stays), the player keeps hands, modes, play, the timeline and the mic and puts loop, speed, transpose, metronome, chords
  and duet in one More sheet, Settings shows Family, Simple, Sound, Voice, Reminder and Microphone. Each place ends on a
  quiet "Show more" (ui/SimpleMode.kt `SimpleMore`; "Show all settings" in Settings) that shows the rest once and then
  offers "Switch to Advanced?". Today's plan and the Learn badge count the Simple chord curriculum.
- Tour and What's new (engine/nav/Welcome.kt, ui/WelcomeTour.kt): grown-ups only, per profile. What's new once per
  versionName (at most three lines, `Welcome.WHATS_NEW`, filled for 2.7.0), then a skippable three-card tour with the pup
  that switches tabs as it goes ("Today is your plan", "Songs is everything you can play", "Learn is the road, with chords at
  the top", the third naming Simple and Advanced). A new grown-up sees the tour after placement and no What's new; a kid
  sees neither.
- Debug (debug builds only): `--es screen today`, `songs [--es section chords]`, `learnchords [--es tab
  daily|dictionary|practice]`, `games [--es game <id> --ei level n]`, `progressions`, `practice [--es tab
  scales|arpeggios|warmups|metronome|tuner]`; the old `chords [--es tab ...]` routes still open the new places. `--ez welcome
  true` shows What's new and the tour again; `--es simple on|off|default` sets the profile's switch.
- Tests: 1454 JVM, green (1415 before): NavTest (every Screen route reachable from the five tabs, the tab order and names),
  TodayTest (the plan's composition, Continue), WelcomeTest and WelcomePrefsTest (once per profile and version, kids never,
  Simple kept per profile), LastToolTest, SimpleTest (the default from placement, each place's rule, the copy rules),
  SimpleWiringTest (each screen asks its rule), plus SettingsWiringTest, TesterWiringTest and PracticeStatsTest additions.
- Lint: 0 errors, 28 warnings, 9 hints (as 2.6.0). APK: app/build/outputs/apk/debug/app-debug.apk, versionCode 16, 2.7.0.
- Emulator walk (5558 Fold, as Roy over 2.6's data, Lea and Sam kids; touch keys): What's new then the tour in light and
  dark; Today's Start walking the plan (chords, lesson, song), Continue after Twinkle and after Ring of Fire, the Explore
  tiles and the Play corner sheet; Songs by chords in Songs (the sort kept after Back from a page); Learn's Chords section
  opening Learn chords (dictionary sheet, Practise any chord), Games and Progressions; Practice Tools and a restart reopening
  the Tuner; You with the wall; Settings' groups; every tab in dark and on the cover screen (`emu fold`). Simple: Today and
  You pills, Learn's Show more and Switch to Advanced, Learn chords with six chords and cut packs, Games two, Progressions
  two, Songs by chords 42 songs and a page with Whole and Halves, Practice C/G/F and one warm-up (Show more brings Tools
  back), the player's More sheet, Settings' essentials and Show all settings, dark and cover. Logcat: no app exception, no
  ANR, crash buffer empty. Screenshots: app/build/screens/ux-*.png (not committed).
- Fixed on the walk: BoxWithConstraints under an intrinsic-height Row crashed Learn (the width is read outside it now);
  "Progressions" broke over two lines on the cover (the tiles stack there); Learn opened scrolled past the Chords section
  (now pinned).
- Open:
  - Only two progressions are plain triads, so Simple's Progressions shows two, not three.
  - Simple cuts song packs to the six chords, so "Chords of Moonlight Sonata" can show one chord (Em). Roy decides: keep,
    or drop a song pack that loses chords.
  - Simple's Progressions keeps the five key pills (C, G, F, D, A); D and A use chords outside the six.
  - Continue for a lesson reopens the last lesson opened, done or not.
  - The double open on a debug cold start with extras (seen before 2.7) is unchanged.
## 2.7 the kid arcade (2026-10-22, branch `arcade`, emulator-5554 only; version not bumped, the integrator does)
Roy: "games where they play keys and pop balloons, or play keys that make things grow or build; not only lessons but games
that help them remember what key is where, work their dexterity, and potentially learn songs." Nine games behind a Games
tile on the kid's Play sheet; ui/arcade/INTEGRATION.md has the files, the round's end, the entries and what to check
against the adult navigation branch.
- Rules (engine/arcade, JVM): two shapes of round. Streams (Balloon pop, Feed the pup, Boop the moles): targets on a clock
  that go by on their own; two gone by in a row light the next keys. Calls (Grow the garden, Build the tower, Frog hop,
  Fireworks, Song painter, Rainbow bridge): each line shown by the pup first, then heard note by note; a slip or a quiet
  beat is a stuck beat (lit, then the pup plays it, then let go). Rounds 60 to 90 s; stars from `ArcadeStars` (streams 75%
  and 45% of targets; the tower the clouds; calls 85% and 55% of notes played by the kid plus the lines asked); levels 0 to
  4 (a song a level for the song games) move up on three stars and down under a third played; keys white (the moles add
  black keys at level 3), C3 to C6, at most 12 a round, strict.
- Unlocks: Balloon pop, Grow the garden and Feed the pup at once, then one more every 40 ledger sparkles after the hub's
  first open (`ArcadeUnlocks`). A round counts like a song in the sparkles ledger (no drop); its time is practice; the
  game's sticker (9 new, LevelStickers page "arcade", placeholders from gen_stickers.py) on its first three stars.
- Quest: every other day one open game replaces "Copy the pup three times" (QuestKind.PLAY_GAME). Brief: BriefEvent.GameRound,
  Stats.games, FactKind.GAMES ("Played Frog hop to three stars this week."), ToyKind.ARCADE; Prompts.VERSION 3.
- Voice: 49 new kid lines at 0.85 (3710 in the catalog), none deleted.
- Debug: `--es screen arcade [--es game <id> [--ei level n] [--el seed n] [--es inject synth:...] [--ez bot true] [--ei stars 1|2|3]]`
  (bot: the screen plays the right keys as touches).

## 2.8 arcade keys and the Play corner (2026-10-22, branch `arcade-keys`, docs/PLAN-2.8.md sections 2 and 3; version not bumped)
- Random keys: level 0 of Balloon pop, Feed the pup, Build the tower and Grow the garden (`ArcadePlans.THREE_KEYS`) rolls its
  three keys per round from the white neighbours of the octave (`ArcadeKeys.threeFrom`: C D E, D E F, E F G, F G A, G A B,
  A B C) with the round's seed, so a replay of a seed is the same round. A game's first round for a kid keeps C D E
  (`ArcadePlans.plan(..., first)`, MainActivity's `firstRound` = the game's `GameRecord.rounds == 0`; chosen per game, which
  also keeps the very first arcade round C D E). The garden walks its written C D E steps on the rolled three. From level 1
  the streams' first pass (each key once) is shuffled; the demo still shows the keys low to high. No arcade line or caption
  names keys (the demo plays and lights them, the badges follow), so nothing else changed. Tests: ArcadeRulesTest (50 rolls
  give more than one three, the first round, replay, the shuffle), ArcadeHearingTest (every rolled three is heard).
- Play corner: Roy's "Play corner." that did nothing was not reproduced on the Fold emulator by touch (Simple and Advanced,
  inner and cover screens, normal and large text, animations off, a fold or rotation with the sheet open, long presses):
  Today's tile opened its sheet and each toy every time; the Songs and sheet titles are plain headings; the tour line is text
  in the tour card. Hardened instead: Today's tiles come from `NavGraph.TODAY_TILES` (name to place, `Names.CHORDS` added for
  the literal "Chords"), NavTest checks every tile Today draws is a `Names` constant whose place is in Today's `OPENS` and has
  a tap; the sheet counts any hiding as a close (so the tile can never be left marked open), and the tile logs
  `today: play corner opened, was marked open: ...`, `closed`, `-> <toy>`. If Roy sees it again, logcat or the tester replay
  shows which.
## 2.8 three layouts (2026-10-22, branch `layout-3`, emulator-5558 Fold only; version not bumped, the integrator does)
docs/PLAN-2.8.md section 5. 2.7's one Simple switch became three layouts per grown-up: **Simple** (the first steps, exactly
2.7's Simple), **Advanced** (lessons, songs and practice) and **Full** (everything, 2.7's Advanced).
- Engine: `enum class Layout { SIMPLE, ADVANCED, FULL }` (engine/nav/Layout.kt: titles, card lines, the chooser's question and
  note, wire names); every rule in engine/nav/Simple.kt takes a `Layout`. `Simple.layoutOf(kid, chosen, level)`: a kid is
  always Full (nothing cut, never asked), else the choice, else the level's default (Never and A little Simple, else Full).
  `Simple.asks(kid, chosen)`: a grown-up with no choice. `Simple.cuts(place, layout)` says which places a layout cuts.
- Advanced = Simple plus: Learn's side rows (Theory, Ear training, Sight-read; the path was never cut), Practice in full, every
  song by chords, the song packs whole in Learn chords ("Chords of <song>": the generic packs, the dictionary and the drill keep
  the six chords), Major or minor in Games, Quarters and Broken on a song page, the loop and speed back on the player's bar
  (transpose, metronome, chords and duet stay in More), and Settings' Keys, Falling notes, Show wrong keys and Briefs. Still
  cut in Advanced (each with "Show more"): Learn chords, Games, Progressions (the two pop ones), song patterns, the player bar,
  Settings (Progress and About).
- Prefs: `Prefs.layout: Layout?` (key "layout", wire name) replaces `simpleMode`; read-time migration: an old `simpleMode` true
  reads as Simple, false as Full, absent is null (asked). The first new choice removes `simpleMode`.
- The launch chooser (ui/LayoutChooser.kt): in MainActivity's grown-up Home branch, before HomeShell, while `Simple.asks` is
  true: the pup, "How much do you want on screen?", three cards (a small page with one, two or three lines lit, the name, one
  line), "Change any time on You or in Settings." A tap saves and the tabs open (What's new and the tour after it). It is not a
  Screen of its own (NavGraph unchanged), so it catches a launch, a profile switch and the return from a newcomer's first
  lesson alike. Kids never reach it (their home comes first).
- UI: ui/LayoutMode.kt (was SimpleMode.kt): `LayoutMode`/`LocalLayout` (was SimpleMode/LocalSimple), `LayoutPicker` (three
  segmented buttons: Settings > Playing > Layout, and a place's "Show more" offer), `LayoutPill` (Today's header and You: the
  layout's name, a tap opens a menu of three), `LayoutMore(place, ...)` (was SimpleMore; draws nothing where the layout cuts
  nothing). A change is logged as `Briefs.setting("layout", "<wire>")` and the brief says "The layout was set to Advanced.".
- Copy: the tour's third card and What's new 2.8.0 (one line) name all three.
- Debug: `--es simple simple|advanced|full|unset` (or `--es layout ...`; 2.7's on|off|default still read as simple|full|unset).
- Tests: LayoutTest (was SimpleTest: one row per place part per layout, cuts agree with each rule, copy rules), LayoutWiringTest
  (was SimpleWiringTest), WelcomePrefsTest (the layout per profile, the 2.7 switch carried over), NavTest (the chooser sits
  before the tabs, after the kid home).
## 2.8 one email place (2026-10-22, branch `email-one`, emulator-5558 Fold only; version not bumped, the integrator does)
docs/PLAN-2.8.md section 1. Roy: "the address is for the parent, not the child. One address, confirmed once."
- Settings > Briefs and email is the one place (ui/brief/email/BriefsAndEmail.kt, was the per-child EmailSettingsScreen.kt):
  the family's address with Send code and the six-digit code (the old page's steps, no child header), then one row per kid
  (avatar, name, Preview; Daily switch; Weekly switch with the weekly slot, a tap opens the WeeklySheet, still the weekly
  notification's slot), then Sending (mobile data, Stop all emails) and BriefAiSettings at the bottom. Simple shows it
  (`SettingsPart.BRIEFS` in `Simple.SETTINGS_ESSENTIALS`). Family > child lost "Email the brief"; the parent brief's Email
  button opens the same composable as a full-screen dialog (`EmailDialog(kids)` > `BriefsAndEmailPage`); a row's Preview
  opens `EmailPreviewDialog`, which gained "Send me this email now" (EmailJob.sendTest) once the address is confirmed.
- Gate: as before (the per-child page was Gated). The email part shows after "Open email settings" passes the gate (at once
  when it is open, so inside the brief's dialog); every change guards again; BriefAiSettings stays visible.
- Storage (engine/brief/email/FamilyEmail.kt, data/EmailSettings.kt): `EmailPrefs` is two records in email.xml, the family's
  (`email.family`: address, verified, code, tries, firstSent, the first emails' sends) and each child's (frequency from the two
  switches, chosen, last daily and weekly, held, sends for the caps). `load(first)` joins them into the view EmailSchedule,
  EmailRound and EmailSender read unchanged. The first email (the code) goes for one child only, `FamilyEmail.lead`: the first
  kid whose emails are on, else the first kid. Caps stay per child. Stop removes the address and keeps the switches.
- Migration (`EmailSettings.migrate`, once, when the job or the section first reads): the first kid with a confirmed address
  gives the family its address and confirmation (nothing is confirmed again); with none confirmed, the first code waiting
  moves (it still works); a kid who had an address keeps their switches and history, their emails now go to the family's
  address; once an address moved, a profile with no address is switched off (nobody gets emails they did not get); no
  child's record keeps an address. Nothing moved: nobody is switched off and a new kid starts Weekly on, as before.
- Emails say "Settings > Briefs and email" (the welcome's code line and its switches, the footer); privacy page and onboarding
  say Settings, Briefs and email. Snapshots in app/src/test/resources/email re-pinned (only those lines changed).
- Debug: `--es screen email` now shows the whole page; seeds as before plus `--es seed old` (2.7's per-child address on
  `--es child`, moved as the page opens).
- Tests: 1499 JVM, green. New FamilyEmailTest (split, join, lead, migration), EmailSettingsTest rewritten (family store, switches,
  migration once), EmailRoundTest (three kids, one code), SettingsWiringTest (one form), EmailFlowTest and EmailBriefTest copy.
  Lint 0 errors.
- Emulator (5558 Fold, over Roy, Lea and Sam): a 2.7 email.xml with Sam confirmed (daily) became the family's address on the
  first start (`email: one address for the family now (moved from a child)`), Lea switched off, Sam's Daily on, the Simple
  Settings showing the section; Lea's two switches on (email.xml: BOTH, no address in her record); her Preview with "Send me
  this email now" refused kindly on this keyless build (`email: WEEKLY ... for p1 not sent: NO_KEY`); the gate shut: the
  section's "Open email settings" asks it; Family > Lea without "Email the brief"; the parent brief's Email opening the same
  page. Logcat: no crash, no "@" in the app's lines. Screenshots: app/build/screens/email1-*.png (not committed).
  Another agent was driving 5558 at the same time (installs at 15:43 and 15:53); a walk there may meet the other branch's APK.
## 2.8.1 My songs (2026-10-22, branch `midi-import`, versionCode 20; emulator-5558 Fold only, not on Roy's phone yet)
Roy: pop songs (Michael Jackson first) as easy melodies to play; the app ships nobody's melody, so the learner brings a .mid
and the app makes it a lesson-grade song.
- Songs > Library opens on **My songs** (after Start here): the songs added from files, then the older imports (files/user,
  Alicia). "Add a song from a file" (SAF OpenDocument: audio/midi, audio/x-midi, audio/mid, application/x-midi, then */*);
  the bytes are read once (at most 512 KB, `MySongs.MAX_BYTES`) and the URI is never kept. A file that is no song gets a kind
  line ("That file did not work": not MIDI, no notes, only drums, too big, could not open). Then the title dialog
  (ui/MySongDialog.kt): the title the file suggests (editable), "The tune has N notes.", and when a left hand was found
  Melody only (default) or Both hands. Rows: hands as the subtitle, overflow or long press for Rename and Delete (one
  confirmation). Every layout shows the section and its button (`Simple.songsShows`, `SongsPart`).
- Easy mode, engine/MySongs.kt (pure, MySongsTest): the tune is the (track, channel) line, channel 10 never, that moves
  between pitches most and sits highest (score: moves over the most moves plus mean pitch over the spread, at least 12); a
  track named Melody, Vocal, Voice, Lead (not a choir, pad or bass) wins outright; notes under half a 16th go; starts snap to
  the nearest 16th, one note a 16th (the top; the left hand's bottom), a note starting more than a 16th under a held one
  goes, each note ends where the next begins; the line moves by octaves into G3..C6 (left C2..B3) leaving the fewest
  outside and nearest F4 (left C3), the rest fold in; the left hand is the lowest other line at least 5 semitones under the
  tune; an intro without the tune is cut to the bar the tune starts in; one steady tempo, the file's main one, capped at
  100 bpm (Twinkle, Ode to Joy and Mary's tempo); no sections. Title: the conductor's (or only track's) name when it reads
  as one, else the file name (slugs as words, capitals when it had none, " - " as a comma). Id: the suggested title's
  letters and 6 hex of the bytes' SHA-256 ("fur-elise-1c12c2"), so the same file again replaces itself and keeps its stars.
- Storage (data/SongRepository.kt): files/songs-import/<id>.mid plus the phone-wide preference file "mysongs" (ProgressFiles
  .NAMES and ProfileFiles.SHARED): the list as MiniJson and each song's bytes in base64, so the progress export carries the
  songs and an import writes missing files back (`mine()`). `all()` holds them, so the player, Continue, Today's plan,
  stars, trouble bars, stickers and the brief's titles need nothing new. SmfParser gained `read()` (SmfData: notes with
  track and channel, track names, tempo map) under `parse()`.
- What's new 2.8.1 (one line). Tests: MySongsTest (a made-up two-track file built in the test, Twinkle through whole, the
  ranges and tempo against the built-in songs), MySongsWiringTest, LayoutTest and LayoutWiringTest rows,
  ProgressFilesCoverageTest (songs-import, mysongs), ProfilesStoreTest, WelcomeTest.
- Emulator (5558 Fold, over Roy's data, Simple): fur-elise.mid pushed to Download and added through the picker (413 tune
  notes of 517, 328 left), played in Listen and to the end in Wait by touch (three stars, a sticker, the brief's SongRun
  under the new id), My week names "Fur Elise", rename, delete (file and copy gone), added again with Both hands (the
  stars came back), a JSON file refused, the section in Advanced and Full, dark mode. Screenshots:
  app/build/screens/mysongs-*.png (not committed).
- Open: My songs is the phone's shelf (every grown-up on the phone sees it, like the older imports), not per profile.
  A restore from a backup made before 2.8.1 clears the list (restore replaces every shared file); its files stay as
  orphans. The tile art cache keeps the first art when a song is added again with other hands.

## 2.8.3 MIDI hardening (2026-10-22, branch `midi-hardening`, versionCode 22; JVM only, no UI change)
Security review finding 4 (docs/SECURITY-REVIEW-2026-10-22.md, untracked): a malformed MIDI file could hang or crash the
player and burn CPU at every launch. Every bound below holds whatever the bytes say.
- engine/SmfParser.kt: files over `MAX_BYTES` (512 KB) refused; at most `MAX_TRACKS` (128) tracks read; a time signature
  only when 1..32 beats over a power of two 1..32, else it is left out (4/4 stays); tempos held to `MIN_BPM`..`MAX_BPM`
  (20..300), a zero tempo still ignored; the song ends at `MAX_SONG_MS` (20 minutes: later notes and markers go, held
  notes end there) and keeps its earliest `MAX_NOTES` (20,000) notes in file order; at most `MAX_MARKERS` (256) markers,
  names and markers cut to 128 characters. TempoClock works in Double (the Long product wrapped) with a binary search
  (a linear search per note was quadratic in tempo changes), and has the inverse `tick(ms)` for the length cap.
- engine/MySongs.kt: `MAX_BYTES` is the parser's; the easy song also stops at 20 minutes at its slowed tempo
  (`easyLines`), so a fast file slowed to 100 bpm cannot triple in length; the scan counts the capped tune.
- engine/Metronome.kt: bounded by itself: tempo held to 20..300 (NaN as 120), a time signature music does not have counts
  as 4/4, so a beat is 25 ms to 12 s; one call clicks at most `MAX_CLICKS` (64), a longer span clicks nothing; times past
  `MAX_TIME_MS` click nothing. Fix found by the new test: a frame starting a hair after a beat clicked it twice (the
  start's tolerance was in beats, the end's in ms); both ends now use `from - 1e-9 <= beat < to - 1e-9`.
- engine/Lessons.kt `labelBarsAligned`: at most `MAX_LABEL_BARS` (4,000) bars, none when a bar is under 10 ms or not
  finite; a sweep (each note adds its edge bars once and the bars it holds through as one run) replaced the per-bar filter
  of every note (bars times notes). Same output: LessonsTest checks every bundled song, every progression in every key and
  60 random songs against the old code. A 20-minute 1/32 song of 20,000 notes at 300 bpm labels in a few ms.
- data/SongRepository.kt: the older import (Alicia's unlock) reads at most 512 KB like My songs and refuses more with
  My songs' line; `load` refuses a stored file over 512 KB unread; a backup's base64 copy over 512 KB is not written back.
- MainActivity: the launch pass logs `song pack <id> skipped: ...` per bad song and `song packs: N songs read and
  labelled in T ms`; Today's plan leaves out a Continue song that does not load (it opened unguarded in the session part).
- Tests: MidiFuzzTest (200 seeded inputs: noise, random events, damaged songs, songs with a hostile track; plus cuts of every
  bundled song, through SmfParser, MySongs, the labeler and the metronome: refused the designed way or bounded, each
  under 5 s, in practice 80 ms at most), SmfParserTest (odd time signature, zero and extreme tempos, huge delta, giant note
  count, 40,000 tempo changes, the clock both ways, 128 tracks, 512 KB), MetronomeTest, LessonsTest, MySongsTest (a 300 bpm
  file stops at 20 minutes), LibraryContentTest (512 KB).
- Verify: 1553 JVM tests, all green but PopPrivateTest.privateSongsMustNotShipPublic (red by design on a keyless worktree);
  lint 0 errors, 29 warnings, none in the changed files; assembleDebug versionCode 22, 2.8.3. No emulator walk (no UI change).

## 2.8.4 grown-up QA fixes (2026-10-23, branch `qa-grownup`, versionCode 23; emulator-5558 Fold_QA_GU only)
The grown-up side of docs/QA-2.8.3-grownup.md (untracked): 14 ranked defects and the copy nits. The kid side is branch qa-kid.
- D1 (ui/PlayerScreen.kt `LandscapeLock`): on the Fold's inner screen Android 16 overrides orientation requests, and the
  player's reset to UNSPECIFIED was dropped there (the window manager compared it with the overridden value, already
  UNSPECIFIED), so the stored SENSOR_LANDSCAPE outlived the player and the cover opened sideways after a fold. The release
  sets USER then UNSPECIFIED; players are counted; MainActivity.onConfigurationChanged releases it on every display change
  (smallest width) while no player holds it. Log `orientation: released (...)`.
- D2 (engine/brief/ai): week 4,000 tokens, answer 1,200; `AiRequest.retryTokens`: a reply with finish "length" (empty or cut
  mid-way) is `AiCallException.Kind.CUT_SHORT` and is asked once more with twice the cap (day, week, answer; at most two
  attempts a call, `AiRequest.attempts`; BriefLimits.answerMs 26 s). Then the templates write it, marked "Written on the
  phone". The reply's usage block is logged (`finish: length, N tokens, M thinking`).
- D3 (engine/brief/ExportLayout.kt): `ExportBlock.Text.first` marks a paragraph's first wrapped line; the PNG and PDF draw a
  bullet's dot there only.
- D4 (AiReplies): every whole number in an AI fact must be in the findings' encoding or 0..7 (`AiReplies.SMALL`, day counts);
  a fact that fails gives way to the template writer's fact at its place (`AiWriter.phoneFacts`, wired in data/BriefAi.kt),
  or is dropped. Logged as `fact N: a number not in the findings (57), the phone's fact stands in`.
- D5 (MainActivity, engine/DailySession.kt): a grown-up's Practice today ends on "Done for today!" and the summary only when
  `SessionPlan.through(done, skipped)`: every part played or passed over with the interstitial's Skip (a chord part with
  none answered counts as skipped). Left part way (Back in a part, End for today, Back on the line), Today keeps the plan and
  Start (`daily: ended part way, ...`). Minutes are the practice the session added (`Screen.Daily.practiceAt`), whole as the
  goal line counts them; the player writes its seconds before Back, the chord part before it ends. A kid's session ends as
  before. Under a minute: no minutes in the summary, no Practice chip.
- D6 (engine/nav/Simple.kt): Simple's Songs by chords keeps difficulty 1 or 2 in C, G or F major or A, E or D minor
  (`Simple.easyKey`): 35 of 107.
- D7 (engine/ReminderPlan.skip, reminder/Reminder.kt): no reminder once today's goal is met or today's plan is done.
- D8: rename dialogs and the My songs title dialog start with the caret after the text (`ui/MySongDialog.kt caretAtEnd`).
- D9: the lesson part's arrow and Back go back to that part's line, held (no countdown: `Screen.Daily.held`,
  `Interstitial(countdown = false)`), labelled "Back to your plan"; a finished lesson goes on.
- D10 (engine/MySongs.kt): a file named .mid, .midi or .kar, or starting "MThd", that does not read is `Problem.DAMAGED`
  ("It may be cut short or not a MIDI song."); other files keep the extension line.
- D11: the outfit reveal and the recap's drop scroll themselves into view for kids only; a grown-up's recap stays on its
  stars, accuracy and XP.
- D12 (ui/family/ProfilePicker.kt): with the keyboard up and a name typed, "Who is this for?" and its answers scroll into view.
- D13 (ui/brief/BriefEntries.kt `OneBriefAtATime`): one brief dialog at a time; the newest takes the slot (watched with
  snapshotFlow from after it took it) and an older one closes itself.
- D14: Settings' scroll and "Show all settings" are kept in MainActivity (`openSettings` resets them) and outlive Privacy,
  Licences and Mic setup.
- Nits done (numbers as in the report): 1, 2, 3, 4, 6 (Practice in the centred 720 dp column), 7, 8, 9, 11, 12, 13, 15, 16,
  17, 18, 19, 20, 22, 23. Left for a design decision: 5 (three celebrations after the check), 6's "Scales" tab over the
  Scales | Arpeggios toggle and the pills running off the edge, 7's twelve-arc ring in Simple, 8's Show more above the path,
  10 (song packs' chords come from labelling both hands, with sevenths), 13's title truncation, 14 (the finger number over
  the key label), 21 (the brief's pinned header), 24 (the cover's lane height).
- Tests: 1568 JVM, all green but PopPrivateTest.privateSongsMustNotShipPublic (red by design on a keyless worktree). New:
  OpenRouterTransportTest (cut short, retry, one more try in all), WriterChainTest (cut short falls to the phone, caps),
  AiRepliesTest (numbers), ExportLayoutTest (bullets), DailySessionTest (through, minutes), ReminderPlanTest (skip),
  LayoutTest (Simple's songs), MySongsTest (DAMAGED), QaWiringTest (D1, D8, D11 to D14 from the sources). Lint 0 errors,
  29 warnings (as 2.8.3), none new.
- Emulator (5558 Fold_QA_GU, over the QA's data, touch): a song on the inner screen, Back, fold: the cover in portrait
  (requestedOrientation UNSPECIFIED after Back); Practice today: the lesson's arrow back to its held line, a run left in the
  song part ended part way with no celebration, a run through (chord, Skip, Skip) ended "Done for today!" with "1 chord."
  beside "5 of 10 min today"; My songs rename typed after the title; Settings back from Privacy at About with Show all kept;
  the new-person form with the keyboard up showing "Who is this for?"; My week then the brief door: one brief, one Back.
  Screenshots: app/build/screens/d*.png and nit*.png (not committed).
## 2.8.3 QA, kid side (2026-10-23, branch `qa-kid`; version not bumped, qa-grownup's branch bumps to 2.8.4)
docs/QA-2.8.3-kid.md (untracked): two hours of kid play on a wiped Fold emulator found 6 defects worth fixing, 7 lesser
ones and 12 polish nits. Fixed here, in severity order:
- Show Mum (defect 1): the hold alone still opens the session note (reading is the point), but every action in a brief
  asks the real gate itself: `guardBrief` (ui/brief/BriefPage.kt; a child's brief always unless the gate is open, a grown-up's
  own week while a kid plays) wraps Refresh, Send (the pill and the export's Send), Ask (typed, starters, the microphone),
  the cards, Undo, Email and the weekly time. Sheets step aside while the gate shows (their window covers it). Closing Show
  Mum shuts the gate. BriefWiringTest reads every button of the brief's three files.
- The launch picker (defect 2): with a kid in the family a grown-up's tile and Add someone ask the gate
  (`ParentGateRules.pickerAsks`); a kid's own tile never does. The gate now waits after three wrong sums in a row: 30 s,
  60 s, 120 s, doubling, at most ten minutes ("Try again in N seconds.", counting down, the pad off), forgotten on a pass or
  a day after the last wait (`ParentGateRules.Lockout`, kept in its own prefs file "gate", never in a backup).
- A kid's song recap (defect 3): the recap Dialog hosts its own ParentGateHost, and a gate left asked goes with the recap.
- Playground stages (defect 4): a key touched during the pup's demo counts once the demo has played it
  (`StageRounds.earlyTouchMayCount` / `takesEarly`, `found(early = true)`); anything else in a demo is no try, so an early
  touch can never let a round go. The mic stays deaf through a demo as before.
- Find all the Cs (defect 5): on a touch keyboard every C it has is all there are (`QuestEvent.NotePlayed(touchKeys)`: the
  Jam's C4 and C5); Cs found on a stage and Cs played right in the player (Where C lives' every C, a song) count for kids.
- Pup says (defect 7) opens on `PlayLines.LISTEN_FIRST`, the recorded "Listen first, then copy me."
- Weekly brief (defect 10): no alarm while the phone refuses notifications (`WeeklyPlan.notifies`); the setting reads
  "Off, notifications are off on this phone" with Open notification settings; the alarm comes back when they are allowed.
- Lesser: a recreate on an arcade finish reopens the games (defect 8); finish and cheer-bar buttons ignore taps in their
  first 900 ms (`SettleTaps`, defect 9); a concert retry keeps its song (`LocalStageMemo`, defect 11); arcade practice
  counts only while keys come (`ArcadeTime`, 8 s a key) and a round with no key played earns no sparkles, quest star or
  brief line (defect 12). Nits: the sticker tray only under the album, no hand or mode words on a pre-reader's song bar, an
  arrow on a kid's Next, the outfit bar's repeated words gone, a letter hunt glowing in the key's colour, "Hi, Mia!" clear
  of the map's top fade. Nit 7 (the recap's "Next:" naming the same song) is qa-grownup's fix ("Once more: <song>").
- Not done: defect 6 (the AI's "finish: length") is qa-grownup's; defect 13 (My stickers silent) needs new recordings
  (about 100 wardrobe lines; scripts/render_voice.py with Kokoro); nits 4 and 5 (map layout), 8 and 12 (by design).

## 2.9 retention (2026-10-24, branch `retention`, versionCode 24, 2.9.0; emulator-5558 Fold_QA_GU only)
Roy: "make the app better and more engaging and keep user retention". Three mechanics the app lacked, and the numbers to judge
them. Kid-safe throughout: no guilt, no pressure, no countdown a child can see.
- Rest day (engine/PracticeStats.restDays, streakRun, streakWords): a single missed day between two practice days keeps the
  streak, automatically, at most once in any seven days (a rolling window, given oldest first); yesterday reads as a rest day
  while today is still open. A rest day holds the streak and never adds a day. Streaks.compute spends a rest day before any
  of 2.0's earned freezes (they stay, for a second gap in the week or a gap of two days); StreakResult.restDays and
  restInStreak. Shown plainly: You's streak line "3 days, with one rest day. Kept for today." and a sleeping paw (a paw with a
  crescent moon, ui/Retention.kt drawRestPaw) with "Rest day, your streak stays" under the week's dots; PawWeek's strips (the
  kid paw calendar, the goodbye, the family wall); the brief's seven-day strip (DayStat.rest, from the days its paws fill, and
  the PNG and PDF export); Today's greeting ("Yesterday was a rest day. Ready when you are.", "6 days, with one rest day. Let's
  keep it going.").
- Comeback (engine/Comeback.kt): back after three or more full days without practice, nothing played yet today, once per
  return (PlayStore.comebackGreeted, the return's last-played day). A kid: a card over the map, the pup dancing, "You're back!
  Yay! Let's play one you know." and Play: one stone they know (of the last twelve walked, the most stars, the earlier on a
  tie). A grown-up: "Welcome back" on Today (the greeting says it too, never a word about the streak) with Start: the last
  clean song (three stars, not Listen) from the brief's log at 0.75x in wait mode, in the hands of that run, for three minutes
  (PlayerStart.speed, timeLimitMs), or the last clean lesson again; nothing new. Logged as BriefEvent.Comeback ("comeback",
  the days away) and praised by the brief (PraiseKind.CAME_BACK: "Back at the piano after four days away.").
- Tomorrow's surprise (engine/world/Surprise.kt, World.wrapGift and openGift, WorldState.gift): on a kid's finish screen once the
  day's goal is met (a lesson's celebration, a song's recap, an arcade round, Done for today), a wrapped present, "Something
  for tomorrow" (the pup: "Something for tomorrow! A surprise from me."). One a day, never two waiting, and it opens only on a
  later calendar day: on the kid home it floats over the buttons, "Your surprise is ready!", and a tap opens it (the lid flies
  off, sparkles): a sticker 45 (a drop, DropSource.GIFT), 25 sparkles 40 (Ledger.bonus; the next outfit's ring shows how
  close), something to wear 15 (a practice-track set the kid has not got, "Keep it on?"), rolled from the world's seed and the
  wrap day; an outfit with nothing left becomes sparkles. The owner's reminder says "Your surprise is ready." on such a day,
  even after the owner's goal (ReminderPlan.text and skip, surpriseReady). Grown-ups get no gift: their finish screen's line
  names tomorrow's next thing from the plan (engine/Tomorrow.kt: the Continue song's hands tip, "Tomorrow: the left hand of
  Twinkle", then the next lesson, then the song again), above the drop so it stays in view. One slot for all of it:
  ui/Retention.kt `TomorrowSlot` under `LocalRetention` (MainActivity).
- Measurement: `Tester.retention` (analytics/Tester.kt, names in engine/tester/Retention.kt) sends `comeback_shown`,
  `comeback_played`, `rest_day_used` (once per rest day, when the day after it was played), `surprise_wrapped` and
  `surprise_opened`, each with `profile_kind` kid or adult only; logcat `retention: <event> (<kind>)`. Findings.Stats gain
  `daysBackThisMonth` and `longestGap` (Insights `Habits.of`: days before the first event, and today while unplayed, are no
  gap); the week's template habits section opens on "The piano came out on three of the last seven days, the longest gap three
  days. Played on six days this month." (SectionBank.BACK; it stands in for the days-practised fact). The AI prompt names both
  fields, the rest day and CAME_BACK (Prompts.VERSION 4, snapshots re-pinned). You (grown-ups): "Days played this month: N"
  under the week's dots. analytics/INTEGRATION.md says how to read the events.
- Debug clock (data/AppClock.kt): `--es today 2026-10-28` (debug builds only, also through onNewIntent) moves the app's today
  for the life of the process: the practice log, streaks, Today, the kid home, the gift, the comeback and the brief's events
  and window (Briefs.clock, BriefEnv.now) all read it. The emulator's clock cannot be set (`adb shell date` refuses without
  root on the Play image). File-name stamps and alarms keep the real clock. Walk recipe: force-stop, then `am start ...
  --es profile <id> --es today <day> [--ei practice <min>]` per day; `--es lesson first-song --ei step 3` (two taps on Next)
  finishes a grown-up lesson, `--es lesson playground-11 --ei step 2` a kid's, `--es screen arcade --es game balloons --ez bot
  true` plays a round by itself (press Home before the next day, so the session ends with its practice for the brief).
- Voice: 7 new kid lines (engine/RetentionLines.kt) recorded at 0.85 (3717 files).
- What's new 2.9.0: three lines (rest days, the comeback, tomorrow and the kids' surprise).
- Tests: RestDayTest (one gap kept, the window, two gaps, a gap of two days, today open, the strip, the week line),
  ComebackTest (the three-day rule, once per return, the stone and the piece), SurpriseTest (once a day, never two waiting,
  the next day only, the table's weights, each prize, the outfit fallback, saving), TomorrowTest (the line, the events' props),
  RetentionBriefTest (the event on the wire, the praise, the habits numbers and line, the strip's rest day), RetentionWiringTest
  (every finish screen, the homes, the strips, the reminder, the debug clock), ReminderPlanTest and WelcomeTest additions;
  GoalsTest and PracticeStatsTest moved to the rest-day rule. 1621 JVM tests, all green but
  PopPrivateTest.privateSongsMustNotShipPublic (red by design on a keyless worktree); lint 0 errors, 29 warnings (as 2.8.4).
- Emulator walk (5558, Roy and Mila p1, faked days): Roy back on 27 Oct after the 23rd (Welcome back, Twinkle at 0.75x in
  wait), the 27th and 28th played, the 29th missed, the 30th opened ("Yesterday was a rest day"), then played (You: "3 days,
  with one rest day", the moon paw, "Days played this month: 4"; `rest_day_used`); Mila seeded on the 25th and back on the
  29th (the card, "High and low"), the goal and an arcade round (the gift wrapped), the 30th (ready over the buttons; opened:
  25 sparkles opened the sunglasses), a lesson to the goal (wrapped on its celebration), the 31st (opened: sparkles and the
  party hat); Roy's lesson finish after the goal ("Tomorrow: the left hand of Twinkle, Twinkle, Little Star"); Roy's week of 11
  to 17 Nov (the strip's Monday rest paw, the habits line). Screenshots: app/build/screens/ret-*.png (not committed).
- Decisions: rest days come before the earned freezes and both stay; the window is any seven days, not the calendar week (the
  stricter reading); the kid's stone is the best of the recent twelve; the grown-up's piece replays the clean run's hands; the
  gift's contents are rolled when it opens (so an outfit is one still missing); the reminder (one a phone, the owner's) says
  the surprise when any kid has one ready; event props say `adult` for a grown-up, as the dashboard's profile_kind does.
- Not proven: nothing on a real phone or with a child; the reminder's surprise line by tests only (no alarm fired on the
  walk); dark mode of the new cards was not walked.

## 2.10 living pup (2026-10-24, branch `pup-rig`, versionCode 25, 2.10.0; emulator-5558 Fold_QA_GU only)
Roy: "better animations and mini clips of the dog, like the Duolingo mascot" and "make the app more engaging". The rig
animates the forty stills (res/drawable-nodpi/pup_*.webp), nine of them through SAM 2 layers; five generated clips play
at the big moments (2.12.0, versionCode 28: four of them replaced, branch `pup-clips-2`; see Clips).
- One pup: `ui/pup/Pup.kt` `Pup(pose, mood, modifier, size, controller, ...)` and `ui/pup/PupController.kt`
  `rememberPupController()` draw every pup on screen (ui/Pup.kt keeps SpeechBubble, PupSays, ChunkyButton, Confetti).
  `ui/pup/PupArt.kt` holds `Pose` (moved from ui/Pup.kt, with `eyesOpen`, `mood`, `blinkTwin`), the avatar art
  (`PupAvatarArt`), a shared, thread-safe LruCache of decoded, mipmapped pictures (24 MB) and `rememberPupBitmap` for game
  fields that draw him as a sprite (the rainbow bridge, the dress-up tiles). Playground and arcade stages use `StagePup`
  (ui/playground/PlaygroundKit.kt; `PaintedPup`/`Painted` and ui/play's `PupPicture` are gone). PNG/PDF exports draw
  `Pose.X.res`. PupWiringTest: no `R.drawable.pup_` and no bare pose Image outside ui/pup.
- The table: `engine/pup/PupMotion.kt`, engine-free, every duration, scale, amplitude and spring, and the curves as pure
  functions of time (PupMotionTest: every beat under 1.5 s, the idle loop 2.5 to 4 s, no scale beyond 1.25; the rig also
  clamps whatever stacks up).
- Layers (2.10, `ui/pup/PupRig.kt`, `PupLayers.kt` generated by `scripts/pup_layers.py` from C:\dev\tools\pup-parts, SAM 2
  cuts with a README and a manifest per pose): standing, welcome, dancing, listening, pointing, head_tilt, great_job,
  sleeping and wear_base as body, head, ears, tail and (open eyes only) eyes, each cropped to its alpha box and stored as
  res/drawable-nodpi/pup_<pose>_<layer>.webp, lossy q85 (50 files, 490 KB). Head and tail hang on the body, ears and eyes
  on the head, each turning about its own pivot, in the manifest's draw order (tails and flying ears behind their
  parent). Per pose the cut's limits: the head 12 degrees (listening 4: a paw touches the face; sleeping 3), the ears 15
  (welcome and sleeping 6), the tail 20 (great_job 12, sleeping 5). avatar_0 is cut too but avatars stay still pictures,
  so it is not shipped. A layered pose is decoded off the main thread and drawn from its layers once in (whole until
  then); the other thirty poses keep the whole-image rig.
- Idle: breathing 1.0 to 1.02 (height; width half) over 3.2 s. A blink every 3 to 6 s: layered poses squeeze the eyes
  layer to 0.1 of its height about its middle (150 ms); the rest squint the head (the top 45% of the picture to 0.92
  toward the neck through a clipped second layer, 90 ms); a cross-fade to a closed-eyes twin is wired, no still has one;
  poses drawn with eyes shut never blink. A flick every 8 to 15 s: layered poses turn one ear 10 degrees (or the tail 12)
  about its base and ring back; the rest turn a corner 3 degrees, soft-masked and added (BlendMode.Plus) so there is no
  seam. A pose change: a 150 ms cross-fade (the two pictures added in a layer, so he never thins out) and a 1.04 pop.
- Moods (`engine/pup/PupMood`): beats HAPPY (squash 1.1/0.9 80 ms, stretch 0.92/1.12 rising 220 ms to 0.22 of his size,
  hang 80 ms, fall 180 ms, land on a spring struck at the ground, damping 0.41: bounces of 0.25 and 0.06 of the jump, a
  third under a pixel; 1.07 s), TILT (12 degrees, held 300 ms, 0.7 s, alternating sides: the head layer about the neck
  where the cut allows and no hat sits on it, else the whole pup round a point low in the picture), WAG (3 wobbles of 4
  degrees, 0.45 s; layered: the tail swings 16 degrees and the body wobbles half); held CHEER (a hop a beat landing on
  it, a 5 degree sway, at a bpm folded into 60..150, phase-locked to `beatAt`), POINT (slides up to 0.6 of his size
  toward `pointAt`, leans 3 degrees), LISTEN (leans 4 degrees toward `lean`: the head layer, or the whole pup), SLEEP
  (4.4 s breaths of 3%), IDLE. A beat that comes while a bigger one plays is let go (HAPPY over TILT over WAG). Held
  moods keep the brain's idle acts away (`PupHold.BUSY`). Old reactions play their beat too (a sticker or surprise:
  HAPPY; a wrong key: TILT; the treat: WAG). Eyes that follow the pointing: skipped (the stills have no clean eye
  anchors; the eyes layer is both eyes in one, so they cannot look apart).
- Wired: kid home (idle; the day's hello: the wave clip; a surprise ready: the wave clip, again every 9 s, and no
  pointing at the signpost meanwhile so nothing cuts the wave short; a sticker placed on the map: the jump clip),
  lessons (TILT on a wrong key, WAG on a right one through PlayerScreen's new `onRightKey`, POINT through a listening
  step's demo, HAPPY on a step passed), every stage (StagePup: StageFx's new `hits`/`misses` wag and tilt him, the
  head-tilt pose tilts, pointing slides toward the demo's key through StageState.demoKey and BigKeys' `keyX`, turning
  round toward a key on his right; a finish jumps), the arcade (the same; three stars on the finish: the jump clip),
  Celebrate after a lesson and on "Done for today", kid and grown-up (the celebrate clip once as he lands, held on its
  last frame; the rig's HAPPY then CHEER without it), the song recap (HAPPY when he lands, then CHEER; a kid's three
  stars as the jump clip), the sticker reveal and the surprise opened (the jump clip), Clap along (CHEER at the speed's
  bpm from the first beat, WAG on a clap in time), the kid finale and the concerts' bow (the dance clip; the rig's jump
  then CHEER without it), Pup says (LISTEN on the kid's turn, TILT or HAPPY on the result), the player's kid mode (a 48 dp
  pup in the bar, TransportBar's new `lead` slot: WAG and TILT per key), bedtime (Goodbye: the rig's SLEEP through the
  yawn, then the sleep clip; the map's sleeping pup: SLEEP), grown-up Today (IDLE, no acts, no hops: subtle). Stills
  keep still: avatars, the kid buttons, badges, trophies, the photo booth's photos (`live = false`). Pups that were plain
  pictures before get no idle acts (`idle = false`).
- Clips (`ui/pup/PupClip.kt`, assets/pup). 2.12 swapped four of the five for better cuts. Every file is 318 x 400, alpha,
  12 fps, loop count 0 in the file, ground line row 380, cut by the pipelines in C:\dev\tools\pup-veo (Veo) and its
  `gemini` folder (Gemini and Flow; its README has the windows, the cut-out and the verdicts):
  dance.webp (Veo `pup_veo_dance`, 2.4 s, loops; it beat the Gemini dance), jump.webp (Gemini `pup_gem_jump_1p5x`, one jump
  at 1.5 times, 2.17 s, one-shot: its wrap pops), wave.webp (Gemini `pup_gem_wave`, three waves in 2.5 s, loops; red
  collar and whistle as in pup_welcome), sleep.webp (Gemini `pup_gem_sleep`, one breath, 2.67 s, loops; the twitch
  alternate was not shipped: it lies 14 px high), celebrate.webp (Flow `pup_flow_celebrate`, 43 frames, 3.58 s: sits with a
  paw up, claps, hops, sits with paws at the chest; the 6.25 s action at 1.75 times; one-shot, held). 2,203,700 bytes
  together (2.10's 2,332,238). Not shipped: a bow (Google's video service was overloaded), the Gemini dance, the sleep
  twitch, every AnimatedDrawings and Wan clip. Played by the platform's AnimatedImageDrawable (minSdk 28,
  `ImageDecoder.decodeDrawable`) in a non-clickable ImageView; `PupClips.STAND_H` 306 canvas px map to the pup's box, so a
  head with a 116 px headband is 0.38 of the box, as the stills' heads are (measured: 0.36 to 0.395). One table,
  `PupClips.SPECS`, holds each clip's row (`Spec(scale, oneShot, optional)`): jump, wave and celebrate have full-size heads
  (headbands 115.7, 117 and 118 px), scale 1; dance 114.5 px, 1; the sleep is a lying pup, width-limited to a 99.6 px
  headband, so its view scales 1.15 (114.5 px; 2.10's sleep was 95 px at the same 1.15, about 109). 2.10's jump scaled 1.15
  (drawn 15% small); the Gemini jump is full size, so 1. `PupClip(name)` loops one in a pup's place (`once`, by default
  `PupClips.oneShot(name)`: plays once and holds): dance on the kid finale, sleep on the goodnight screen once the yawn is
  done. The concerts' bravo (ConcertStage, TreeSkyConcertStage) is `PupBow`: the optional bow slot (`BOW`, one-shot, the
  rig's HAPPY without it) when assets/pup/bow.webp is in the app, else the dance looping as in 2.10; to ship a bow, drop
  the file in (318 x 400, 12 fps, the same pipeline), nothing else changes. The kid finale stays on the dance (a bow there
  was not asked; the 2.10 What's new line says "dance at the finale"). A `Pup(clips = true)` plays his HAPPY as the jump
  clip once (decoded ahead): three stars (arcade, a kid's song), the sticker reveal, a sticker placed on the map, the
  surprise opened; nothing loops the jump (PupWiringTest), the debug screen included. The controller's
  `playClip(name, loops, fallback, hold, then)` plays any clip in his place (`prepare(name)` decodes it ahead): the wave on
  the kid home; the celebrate clip on Celebrate, `hold = true` (AnimatedImageDrawable with repeatCount 0 stays on its last
  frame). Each fades over the still (150 ms); a held one stays until the screen goes. The rig plays the fallback beat (and
  then `then`) when the clip is missing, animations are off, or he is dressed up (the clips wear nothing). Open: all the
  clips come from generated video (Veo, Gemini, Flow): add a Licences line before a public build.
- 2.12 check (emulator-5558, screenrecord contact sheets, not committed): the debug route's jump (once, held on its
  stand; its head matches the rig's standing pose at 1), wave and celebrate (once; its last frame, paws at the chest, the
  same a minute later) in light and dark; the Goodbye yawn then the sleep loop in light and night mode (the lying head
  about the yawning one's); the kid home's greeting wave over the pond (Mila undressed with `--es wear none`, then dressed
  again: partyhat and sunglasses). Dark outlines everywhere, no cream fringe, nothing cropped. `--es clip bow` logs
  "missing, the rig's HAPPY". Not on the Fold itself.
- Size added (2.10; 2.12 clips: 2,203,700 bytes, 128,538 fewer): clips 2,332,238 bytes, layers 490,040 bytes: 2,822,278 bytes (2.7 MB) of assets and drawables (both stored
  uncompressed). The debug APK, packaged clean: 133,322,417 (main's last build, 2.9.0) to 133,823,811 bytes, +501,394;
  but main's was a keyed build with PostHog compiled in (2.3 MB more dex, okhttp) and this worktree is keyless: outside
  the dex the APK grew 2,786,607 bytes, about what a keyed 2.10 build adds over 2.9.0.
- Animations off (reducedMotion): no idle motion; a beat is one 150 ms fade of his glow; a pose change is the plain fade;
  no clips (Celebrate holds the rig still). Never blocks input (no pointer handling unless `onTap`; the clip's ImageView
  is not clickable, and a clip never holds up a button or a screen change); everything moving is read in graphicsLayer
  and draw blocks from remembered Animatables and one frame clock per live pup, so a pup never recomposes the screen.
  Smaller than 40 dp he is a picture.
- Debug: `--es screen pup` (ui/pup/PupDebug.kt): every beat, held mood, clip, size and pose on buttons, plus blink and flick.
  For recordings: `--es pose <name> --es mood happy|tilt|wag|cheer|point|listen|sleep --ei every <ms> --ei size <dp>
  --ei bpm n --es clip jump|dance|wave|sleep|celebrate|bow --es act blink|flick --ef slow n` (n times slower, to catch a
  90 ms squint at the emulator's frame rate; the one-shots, jump, celebrate and bow, play once and hold, their chip again
  replays them; bow is the rig's jump until its file ships). Logs: `pup #n: HAPPY`,
  `pup: <pose> holds <mood>`, `pup #n: clip wave x1`, `pup clip <name>: playing x1[, holding its last frame]`.
- What's new 2.10.0: three lines (the pup comes alive; kids see him wag, point and bob; he cheers after each lesson and
  kids see him wave, dance and curl up at bedtime).
- Tests: PupMotionTest (the table, the layered parts), PupWiringTest (no pup drawable outside ui/pup, the rig's rules,
  every mood and clip on its screen, the layers whole and small, the clips: 318 x 400, alpha, loop 0, 12 fps, under
  600 KB each and 2.4 MB together, nothing in assets/pup but SPECS' names, every name in PupClips shipped or marked optional
  (2.12, only the bow), the scales, the one-shots, the jump never looped, the concerts' PupBow; the debug route),
  PupBrainTest (the busy hold), WelcomeTest (2.10.0, three lines).
- Emulator (5558, swiftshader GPU), screenrecord contact sheets (not committed): the kid home's greeting wave (Mila
  undressed for it with `--es wear none`, then dressed again: partyhat and sunglasses; dressed, the map logs the wave and
  wags instead), a lesson (sit, step 3, `--es midi`) with a wrong key then right ones (TILT on the head-tilt pose's head
  layer, WAG and the note's hop), the arcade's three-star jump clip (heads match the rig's crown pose after it), the
  lesson's Celebrate (the celebrate clip once, its last frame held), the garden finale's dance, the goodbye's yawn then
  the sleep clip, an ear and tail flick and the tail wag on the standing pose (`--ef slow 4`), and the lesson's Celebrate
  with animator_duration_scale 0 (no clip, the rig still; the setting deleted again after). Jank, gfxinfo reset then 10 s
  on the kid home and a tap to the play corner (10 s), and 10 s of a lesson step with four keys, the two APKs back to
  back: kid home 2.9.0 91% janky, 50th 77 ms; 2.10 89%, 77 ms. Lesson 2.9.0 79 to 82%, 73 to 77 ms; 2.10 78 to 86%, 73
  to 77 ms. The absolute numbers are the software GPU's (the 5% bar cannot be met on this emulator by either build); the
  frame times did not move.
- Not proven: on the Fold7 itself (frame time with a real GPU, clip decode time), with a child; "Done for today" was not
  walked (same Celebration call, by tests); dark mode of the debug screen only.

## 2.10.1 security fixes (2026-10-24, branch `hardening-2`, versionCode 26; emulator-5558 Fold_QA_GU only)
Three findings of docs/SECURITY-REVIEW-2026-10-22.md (untracked). The parent gate's were fixed in 2.8.4. What's new: no line.
- Finding 1, debug routes (high): see "Debug routes" at the top. engine/DebugRoutes.kt `verdict`/`allowed(buildKey, intentKey,
  action, fromLauncher)`, MainActivity `routesAllowed`, `withoutDebugExtras`, `debugRoutes` (TheoryScreen reads it); every
  former `FLAG_DEBUGGABLE` route check reads it (`routes` in openApp). scripts/set-debug-key.ps1, scripts/adb-route.ps1.
  Decision: BuildConfig keeps the key's SHA-256 (`DEBUG_ROUTE_KEY_SHA256`), not the key, so an unpacked APK (or another app
  reading it on the phone) does not give the key away; the stricter action rule (none or MAIN only) covers shortcuts, whose
  intents always carry an action.
- Finding 6, the first email (medium): it goes before the address is confirmed, so it is the code alone
  (engine/brief/email/CodeEmail.kt, `EmailSender.sendCode`, `EmailHtml.code`, `PlainTextEmail.text(CodeEmail)`): "Hi, it's
  the pup from Sharp Ears. Here is your code.", the code in 42 px digits, "Your first brief comes as soon as you confirm.";
  subject "Your Sharp Ears code is 482913". No name, no findings, no PDF; the job (reminder/EmailJob.kt) reads no findings
  and writes no brief for it. It covers no day (`EmailSchedule.decide` gives today and now), so the day's or week's email
  the schedule sends after the code is typed is the first brief. The brief's welcome, its `first`/`code` parameters and the
  first-email snapshots are gone; `email-code.html` pins the new one. Privacy page and Briefs and email say "only a six-digit
  code". Debug preview: `--es screen email --es view preview --es period first`.
- Finding 5, recordings (medium): Roy keeps `keepRecordings` on by default in the debug build for the beta. The privacy page no
  longer says "then discarded" on such a build; it and Settings > Microphone say `Recordings.KEEP_LINE` ("This test build
  keeps the last four practice recordings on this phone, in the app's own storage, to tune the listening. They never leave
  the phone unless you share them. Turn this off in Settings."), and Settings > Microphone has the switch "Keep the last
  four recordings" in plain view (Microphone setup's switch has the same name). The recordings moved from external app
  storage (readable by apps with the storage permission on Android 9 and 10) to files/recordings/{fixtures,stalls}
  (data/Recordings.kt); the Reporter's thread moves old files once (`recordings: N file(s) moved`, flag `recordingsMoved` in
  mic.xml), keeping names and times; both rule files exclude files/recordings from backup. Share stuck moments still zips
  into cache/share, so the FileProvider paths did not need to grow (file_paths.xml says why); the Reporter reads the new
  folder; pull-fixtures.ps1 and pull-stalls.ps1 copy through run-as and /data/local/tmp.
- Tests: DebugRoutesTest, DebugRoutesWiringTest, RecordingsTest (the move: names, times, a second run, a file already there),
  BackupRulesTest, EmailHtmlTest (the code email: snapshot, nothing about the child, the rules), EmailBriefTest, EmailSenderTest
  (no writer call, no PDF, no name), EmailRoundTest (the code alone, then the day's brief once confirmed), EmailFlowTest,
  EmailScheduleTest, RetentionWiringTest and PupWiringTest (the gate). 1651 JVM tests, all green but
  PopPrivateTest.privateSongsMustNotShipPublic (red by design on a keyless worktree); lint 0 errors, 29 warnings.
- Emulator (5558 Fold_QA_GU): keyless `--es screen settings` opens Settings; a VIEW intent and a LAUNCHER-category launch with
  the same extras log `extras ignored (action)` / `(launcher)` and do not; old external recordings seeded on the device moved
  (`recordings: 4 file(s) moved`, times kept, the external folders gone) and a new mic session landed in
  /data/user/0/com.bigpapituna.sharpears/files/recordings/fixtures; Settings > Microphone's switch off and on wrote
  keepRecordings; the privacy page's microphone paragraphs; the first-email preview shows the code alone. A build with
  `-PdebugRouteKey=test123`: no key and a wrong key ignored (`no_key`, `wrong_key`, the picker shows), also through
  onNewIntent on a running app; `--es dk test123` and scripts/adb-route.ps1 (a scratch copy with a scratch key file) open
  Settings; the APK's dex holds the digest and not the key. Keyless build reinstalled after. Screenshots:
  app/build/screens/sec-*.png (not committed).
- Roy, after merging, on his PC: `.\scripts\set-debug-key.ps1` (prints "Saved."), then `.\gradlew.bat installDebug` (and the
  APK he hands testers from then on is keyed). His routes go through `.\scripts\adb-route.ps1 -Serial <serial> ...`.

## 2.11 step pop (2026-10-24, branch `step-pop`, versionCode 27, 2.11.0; emulator-5558 Fold_QA_GU only)
Roy: "a little animation with a fun quick audio after every lesson page completion, to incentivize people to continue,
similar to Duolingo when you get a right answer during a lesson", then "not one animation but 3 or 4 different step-pop
variants to keep it fresh".
- Where: LessonScreen's `exerciseEnded`, the one place every step's try ends (the embedded player's onFinished and every
  Playground stage's onEnded: letter hunts, ear games, the drum and claps, song parts, picks, the fair). It pops when the step
  passed on its own terms and another step follows (`StepPopRules.outcome` gives POP), at the moment the GOOD cheer bar comes
  up (Duolingo's green bar), so nothing waits on it: Continue goes on as before. No pop on a let-go (three tries, StepRetry),
  nor on a pass made of let-go rounds or notes (half or more: `mostlyLetGo` on Report.letGoRounds/rounds and RunTally.letGos/
  Report.groups, the line LessonStars gives one star at, so a silent kid gets no pop and no climb), nor on the last step
  (Celebrate has it), nor on a listening step or a reading page (nothing judged; the streak is left alone).
- Four pictures (`PopVariant`, engine/lessons/StepPopMotion.kt has every number, ui/StepPop.kt draws them, one Canvas over the
  part of the step the cheer bar leaves in view, under the bar): BURST (a green disc springs in with a white check drawing on,
  a gold ring spreads from 0.6 to 1.2 and fades, seven gold and green stars fly out on a spring), CONFETTI (eighteen paper bits
  in the accent, gold, green and the left hand's blue pop up from the step's bottom edge, rise and fall with gravity, spin a
  little and flip, round the check), RAINBOW (four bands in those colours draw on as an arc from left to right, three sparkles
  trailing the tip, the check popping at the arc's end), PAW (a big terracotta paw print stamps down from 1.6 times its size,
  squashes and bounces on a spring, the check draws inside its pad, a gold ring ripples out). Each gone by 600 ms. Rolled per
  lesson step (`StepPopRules.variant(streak, previous, seedOf(lessonId, step))`): never the same twice in a row; at a streak of
  one or two BURST and PAW are three times as likely as the others, from four RAINBOW and CONFETTI, at three all alike.
- The pup's beat goes with the picture (`pupBeat`): the new `PupMood.POP` (a small HAPPY: squash, a hop of 0.1 of his size,
  a squash ringing out; 520 ms) with BURST and PAW, the new `PupMood.CHEER_WAG` (two bobs swaying one way then the other, the
  tail layer swinging; 520 ms) with CONFETTI and RAINBOW. Priority HAPPY 4, POP and CHEER_WAG 3, TILT 2, WAG 1 (the pop cuts
  the last right key's wag short). The lesson pup's GREAT_JOB mood plays it (`passBeat`); a last step keeps HAPPY.
- The chime (`StepPopRules.chime`, ui/StepPop.kt `playChime`): three piano samples (audio/Sampler.kt, no new assets), the
  triad on a step of the lesson's major scale, rising, 60 ms apart, velocity 72, 80, 88, a 60 ms fade (Sampler.noteVolume)
  and noteOff at 400 ms. In C: C6 E6 G6 for the first clean step, then D F A, E G B, F A C, G B D, A C E (climb =
  min(streak - 1, 5) scale steps) and no higher. The same for all four pictures: the sound is the steady hook. The lesson's
  key (`tonicOf`): the lowest key sounding at the end of its last exercise that does not end on a choice of keys; C when none.
- The streak (`StepStreak`, one per lesson, `rememberStepPops(key = lesson.id)`): one more per clean step (POP, and LAST),
  back to none on a let-go or a graded step skipped with Next; a try that did not pass changes nothing; "Skip (needs MIDI)"
  and Back leave it alone.
- The mic rule (`chimeMode(micListening, headphones, soundOn)`): App sound off, SKIP (the picture only); the mic listening
  with no headphones (MicInput.headphonesOn), PLAY_DEAF: `mic.deafFor(700)` (the chime and 300 ms of room) before the first
  note, so the matcher drops it and it can never count as the learner's note (MicInput's deaf window outlives a stop and a
  start, so a next step's mic opened during it is deaf too); otherwise PLAY. At a step's end the mic usually still listens
  (the player stops it a frame later), so lessons log PLAY_DEAF; the debug screen logs PLAY. Logcat: `step pop: streak N,
  chime PLAY|PLAY_DEAF|SKIP, <variant>`.
- The voice: the step's feedback line and the high five wait for the chime (`StepPops.afterChime`, 400 ms; leaving the step
  says them first, `flush`), so the chime never talks over the pup's praise. Nothing dropped, no new lines.
- No sparkles chip: the wardrobe ledger (engine/world/Wardrobe.kt) awards nothing per step, so none is shown.
- Animations off (reducedMotion): every picture is one 150 ms fade of the check on its disc, drawn from the frame clock (the
  system scale does not cut it short); no stars, bits, arc or paw; the pup plays no beat (IDLE).
- Never blocks: the Canvas (its own layer) handles no pointer and is read in its draw block from one frame clock; the cheer
  bar's Continue still waits its usual 900 ms (SettleTaps) and then goes straight to the next page. Colours: the accent,
  PupGold, Good, LeftHandColor and Ink only (StepPopWiringTest).
- Telemetry: `Tester.stepPop(streak)` sends `step_pop` with `streak` only (engine/tester/StepPopEvent.kt;
  analytics/INTEGRATION.md).
- Debug: `--es screen steppop` (ui/StepPopDebug.kt, through the debug-route gate; reads no extras): Streak 1, 3 and 6 roll
  the picture as a lesson does; Burst, Confetti, Rainbow and Paw show that one at the streak last chosen; the pup beside it;
  no tester event. `--ef fxSlow n` slows the pictures n times for a recording.
- What's new 2.11.0: one line (every step you pass pops, the chime climbs).
- Tests: StepPopRulesTest (the outcomes, half let go, the streak, the climb and every chime in key, rising, a consonant triad,
  on the samples, the mic rule's table, the lesson's key over the whole course, the variant rolls: never twice, deterministic,
  preferred by streak, all four over the course, the pup's pairing, the tester event), StepPopMotionTest (each picture's
  curves a millisecond at a time, gone by 600 ms, the calm fade), PupMotionTest (POP and CHEER_WAG), StepPopWiringTest
  (exerciseEnded fires once, the pairing, the voice waits, the mic is deaf before the first note, no touches, the four
  pictures, design-system colours only, the debug route), PupWiringTest (passBeat), WelcomeTest (2.11.0, one line). 1677 JVM
  tests, all green but PopPrivateTest.privateSongsMustNotShipPublic (red by design on a keyless worktree); lint 0 errors,
  29 warnings (as 2.10.1).
- Decisions: a pop needs a judged step (READ pages and LISTEN steps have nothing to judge, so Roy's "every page" is read as
  every page the learner passes, Duolingo's right answer); a try that did not pass leaves the streak alone (no punishment
  for trying; the high five already rewards it); a graded step skipped with Next resets it, "Skip (needs MIDI)" does not;
  the chime's chords stay in the key (I, ii, iii, IV, V, vi), so three of the six are minor; the voice line waits rather
  than being dropped (a kid's carries their name); the pop fires as the cheer bar comes up, not on Continue, so it can
  never hold up the next page; the lesson's key comes from its last exercise (the model has no key field).
- Emulator (5558 Fold_QA_GU, swiftshader): kid walk (Mila p1, playground-2 "Two black, three black" from step 2, by touch
  on the dog houses and fences): step 2 popped (streak 1, PLAY_DEAF, rainbow; the mic deaf 700 ms; "You did it, Mila!"
  started after the chime), step 3 popped (streak 2, PLAY_DEAF, paw; the line 428 ms after the pop), step 4, the last, went
  to Celebrate with no pop. Grown-up walk (Roy, fingers-right from step 2): the listening step went on with no pop (66 ms to
  the next page), the play step popped by touch (streak 1, PLAY with the mic off, confetti; the line 540 ms after), the
  memory step, the last, went to Celebrate with no pop. The pop logs in the same frame as the step's end and the bar comes
  up with it; Continue to the next page's first log line took 66 to 75 ms (four taps), and Continue still waits SettleTaps'
  900 ms, longer than the pop. The same lesson step replayed rolled the same picture. `dumpsys audio`: the sampler's
  SoundPool (USAGE_GAME, CONTENT_TYPE_MUSIC) logged `event:started` twice within 80 ms of a pop. Animations off
  (animator_duration_scale 0, deleted again after): the check alone, fading; no pup beat logged.
- Recordings: around a step's end the emulator draws 4 to 8 frames a second, so a 600 ms pop shows in 2 to 4 frames at
  full speed; the contact sheet used `--ef fxSlow 2.5` on the debug route. screenrecord starts about 3.5 s late here, so 8 s
  of footage took `--time-limit 12` or more. app/build/screens/steppop-contact.png (8 frames from an 8 s clip, two of each
  picture), steppop-four-8s.mp4, the walks' 8-frame sheets steppop-walk-kid.png (the hunt's own confetti, then PAW) and
  steppop-walk-grownup.png (CONFETTI) with their clips, steppop-reduced-motion.png (not committed).
- For Roy: a lesson step always rolls the same picture (the seed is the lesson and step), so a kid replaying a lesson sees
  the same ones (playground-2's first game is RAINBOW at a streak of one); an attempt or day counter in `seedOf` would vary
  it. Kid stages already end with their own confetti, ding and jump at "You found them all!"; the pop and chime come about
  2 s later, when the run ends. The CONFETTI and RAINBOW beat is the new 520 ms CHEER_WAG and the BURST and PAW beat the
  520 ms POP, not the 1.07 s HAPPY jump (the beat had to stay under 700 ms; `pupBeat` is the one line to change).
- Jank (gfxinfo reset just before the step's last input, read 5 s later: the stage's or player's end, the pop, the bar; the
  two APKs back to back, three runs each, the kid's taps sent as the log says each round listens): the kid hunt 2.10.1 83 to
  88% janky, 50th 81 to 85 ms, 90th 150 ms; 2.11.0 85 to 90%, 50th 85 ms, 90th 150 ms. The grown-up step 2.10.1 78 to 82%,
  50th 65 to 73 ms, 90th 85 to 101 ms; 2.11.0 78 to 84%, 50th 73 ms, 90th 105 to 109 ms. Software GPU numbers; the 90th
  of the grown-up step's end rose about 10 ms with the pop (giving the Canvas its own layer did not change it). Measure on
  the Fold before trusting either.
- Not proven: by ear on the Fold's speaker (the chime's loudness against the voice, the climb, the minor chimes, the fade at
  400 ms), the 700 ms deaf window against a real piano and room, frame time on a real GPU, a child's reaction, dark mode.

## 2.13 cheer pops (2026-10-25, branch `cheer-pop`, versionCode 29, 2.13.0; emulator-5558 Fold_QA_GU only)
Roy: "there are no real animations within the lesson itself that help keep the user engaged ... I would like some things where
they sometimes pop up just to say 'Good job,' 'Continue,' or 'Don't stop. You're doing so well,' ... similar to what Duolingo
does". Now and then, while a step or a song plays, the pup pops in at a corner with a line in a bubble, a small sparkle, and goes.
- The rules (engine/lessons/CheerRules.kt `CheerCoach`, Android-free, deterministic for a seed): in go right notes (a wait group
  done, so a chord counts once; a Perform note hit with its timing; a stage's key found), wrong keys and Perform notes let pass
  (`Transport.troubles.missedAt`), the run's progress and the time; out comes a `Cheer` (trigger, line, entry, `other`, hold).
  Triggers, the rarest first when two meet: HARD_PASSED (a bar, or a stage's round, that went astray earlier this session
  played clean: `CheerMemory.SESSION`, per exercise or song id, for the life of the process, so a lesson's retries and the next
  visit to a song share it), RECOVERY (three right notes straight after a slip, once per slip), RUN (5, 10, 20 and 40 clean notes
  in a row; 20 and 40 always say the number, 5 and 10 roll between the number and a plain line), STEADY (8 notes in a row in the
  good window, Perform only), HALFWAY (half of a run of 16 or more notes, between 50 and 65%, once a step), FOCUS (every five
  minutes of practice, a break over two minutes starts it again; names its minutes up to thirty), NUDGE (ten seconds with nothing
  played while the step runs and nothing busy, once a step), STEP_PASSED (handed to the step pop, below). A trigger that comes
  while a pop is not allowed is let go; the focus pop and the nudge wait for a free moment.
- Rate limits (CheerRulesTest): never in the first 3 s of a step, a try or a fresh run; 15 s between pops for grown-ups, 12 s for
  kids; at most 4 in any minute; never while the voice speaks (any line: the instruction, a hint, a call-out) or the app plays a
  demo or a hint (Listen steps, hint step PLAY, a stage's demo); never in a song's last two bars, nor a step's last quarter (two
  bars at most). Settings > Playing > Encouragement, per profile (Prefs `cheer`): Often (the default for everyone, Roy
  2026-10-25), Less (the waits doubled, 30 s and 24 s, two a minute), Off (no cheer pops; the step pop stays, without its line).
  Every layout shows it (`Simple.SettingsPart.ENCOURAGEMENT` is an essential). "Say it aloud" under it (Prefs `cheerAloud`, null
  until chosen: on for kids, off for grown-ups). The brief logs both ("Encouragement was set to Less.").
- Lines (engine/lessons/CheerLines.kt): two banks, at least 12 per family per audience, plus the runs' numbered lines and the focus
  minutes. KID: the pup's voice, two to six words (the nudge seven, for Roy's "Find the next key, I'm right here."), ending with a
  stop or an exclamation, no digits ("Ten in a row!", KidCopyTest's rule). GROWN-UP: warm and plain, never an exclamation mark, no
  woofs ("Nice and steady.", "10 in a row.", "Take your time."). Never one of the last six shown; never a word about wrong notes
  (a recovery celebrates the save); no step passed line is a cheer bar title. CheerLinesTest.
- The pop (ui/pup/CheerPop.kt `CheerPop`): the pup in a happy pose per family (STREAK, GREAT_JOB, CLAPPING, CROWN, DANCING,
  LEVEL_UP; the nudge waves, WELCOME) with the line in the gold bubble (its tail toward him, two lines at most, a polite live
  region), a seven-star sparkle as he lands with his POP or CHEER_WAG beat, held `CheerCoach.holdMs` (1.6 s, 50 ms a character
  past 18, at most 2.4 s), then out the way he came. Three entries, never the same twice in a row: SLIDE (a spring in from the
  side edge, or down from the top edge every other pop where the place has one corner), POP_UP (springs up from below its spot),
  PEEK (leans in round the edge, tilted, and rights himself). Animations off: the pop fades in and out where it sits, no slide,
  no sparkle, no beat. No pointer handling anywhere; it moves in graphics layers only.
- Where: a lesson (LessonScreen, every step, grown-up and kid, player steps and Playground stages) over the end of its header,
  the title row and the step dots (measured; `Modifier.inRect`), above the pup's instruction, never over it, the player, the keys
  or the notes; the pup 36 to 56 dp to fit the header's height (`CheerPlace.pupDp`), the bubble at most 260 dp. A song played on
  its own (the full player): a top corner of the falling notes, no deeper than 36% of the lane (`CheerPlace.LANE_BAND`: only notes
  still more than 1.6 s from the line, each in view again before it is read; never the hit line), the corner with fewer notes
  passing under it while it shows (`CheerPlace.laneSides`), alternating when even; the chord names keep the left corner, the
  video the right.
- The step pop (2.11) gets the step passed line: `StepPops.fire(..., line = cheerPops.stepPassed())`, `StepPopCue.line`,
  drawn by `StepPopLine` as a bubble over the check (its tail down at it) for the line's hold. Shown, never said (the step's own
  line is spoken after the chime); none with Encouragement off.
- Sound (CheerRules.soundMode, voiceMode, the 2.11 chime's PLAY / PLAY_DEAF / SKIP): a kid's pop is said by the pup (recorded
  lines, Voice), a grown-up's only with "Say it aloud" (and Spoken guidance) on. The mic listening through the speaker during the
  learner's turn: the bubble alone, no word, no sparkle (making the mic deaf then would swallow their note); between notes (a
  stage between its rounds, a rest of 2 s or more in the music): `speakOverMic` and the sparkle with `mic.deafFor(600)`; the mic
  off or headphones on: as it is. The sparkle: two piano samples, the key's fifth and octave high (in C: G6 then C7), velocity 55
  (the chime's 72 to 88), let go at 300 ms; never while the step pop's chime rings (`StepPops.chiming`). A stage's key found pops
  before the stage's own line, so the line keeps the voice.
- Wiring: LessonScreen makes one `CheerPops` per lesson (`rememberCheerPops(..., key = lesson.id)`) and provides it
  (`LocalCheers`) to its embedded player and its stages; `stepBegan` on each step and try; `clear()` when a try ends, a page
  moves or the lesson finishes. PlayerScreen takes the lesson's, or makes its own for a song played on its own; its feed lives in
  PlayerVars (`ui/CheerFeed.kt`, so the body gains no locals): `struck` in keyStruck (right notes), `wrong` in showWrongKey
  (every wrong key shown; not in Perform, where only a note let pass breaks the streak, as the lane's combo and PerformScore
  have it, and a chord's second key reads as wrong), `began` on a fresh run, `passEnded` when an A-B loop wraps (a one-bar
  loop never leaves its bar, so the wrap judges it for the hard part passed), `frame` ten times a
  second. The Playground stages (`rememberStage`): found, missed, and a 250 ms tick (a stage's 7 s re-demo comes before any nudge
  there). Log: `cheer: <trigger> <kid|grown-up> "<line>" voice=PLAY|PLAY_DEAF|SKIP` (`skipped, no room` when no corner fits).
- Telemetry: `Tester.cheerPop(trigger, kid)` sends `cheer_pop` with `trigger` and `profile_kind` only
  (engine/tester/CheerPopEvent.kt; analytics/INTEGRATION.md).
- Debug: `--es screen cheerpop` (ui/pup/CheerPopDebug.kt, through the debug-route gate; reads no extras): a stand-in lesson
  header, instruction and step; one button per trigger (step passed shows the step pop's bubble), Kid or Grown-up, Lesson or Song
  (the lane's two corners alternate), "Learner's turn" (the mic rule), Roll or one entry (Slide, Pop up, Peek). The waits do not
  apply there; no tester event. `--ef fxSlow n` slows the pop n times for a recording.
- Voice: 253 new lines recorded with Kokoro (speaker 3) by scripts/render_voice.py on this PC: 124 kid lines at 0.85 (every
  family but the step passed one, the runs' numbered lines, the focus minutes) and 129 grown-up lines at 1.0 (for "Say it
  aloud"). 3970 in the catalog, nothing left to record or delete (`--check`). The step passed lines are shown, not recorded.
- What's new 2.13.0: one line (the pup pops up with a few kind words when you're on a roll; change how often in Settings).
- Tests: CheerRulesTest (every trigger, the waits, Less, the minute's cap, busy and tail, Off, the step passed line, the last six,
  the seed, the entries and sides, the holds, the sound and voice tables, the sparkle, the places, the setting's defaults, the
  tester event), CheerLinesTest (the counts, the copy rules, the two voices, Roy's examples, the catalog), CheerPopWiringTest (the
  lesson, the player and the stages feed it, where it is drawn, no touches, the mic rule, the log line, the setting in every
  layout, the debug route), StepPopWiringTest (the line handed to the pop), LayoutTest (Encouragement an essential), WelcomeTest
  (2.13.0, one line), KidCopyTest and SpokenCatalogTest (the new lines). 1719 JVM tests, all green but
  PopPrivateTest.privateSongsMustNotShipPublic (red by design on a keyless worktree); lint 0 errors, 29 warnings (as 2.12).
- Decisions: a chord counts once (a wait group done); every wrong key shown counts, from the player's showWrongKey (touch,
  MIDI, the mic's named ones), a mic sound it did not name does not; grown-ups hear pops only with "Say it aloud" (Spoken
  guidance is on by default, so it could not be the switch); Often keeps the four-a-minute cap; the kid nudge may be seven
  words for Roy's own line, "Which key is next?" became "Where's the next key? Let's look!" (kid lines end with a stop or an
  exclamation); runs pop at 5, 10, 20 and 40 only; halfway needs a run of 16 notes or more, so short steps never say it; a
  step's tail is its last quarter (two bars at most), or every pop would fall in a four-bar step's last two bars; the tail is
  the song's own end, never a loop's (Practice today's one-bar loop is not a finish); a hard part passed while a wait holds
  it back stays hard for a later clean pass; a song's
  corner is chosen by the notes the learner plays (the other hand's, dimmed, are not read); the pup's run pose is the
  dancing one (the streak pose's flame carries a 7); `--es midi` now drives a song too (debug).
- Emulator (5558 Fold_QA_GU, screenrecord contact sheets in app/build/screens, not committed): a kid lesson (Mila, key-g step
  2, the mic listening, `--es inject` notes) popped a run of five ("Five in a row! Keep going!"), the nudge after ten quiet
  seconds (Roy's "Find the next key, I'm right here."), a save after a named F5 for F sharp 5 ("You found it again!") and the
  step passed bubble with the burst ("Another one done!"), all `voice=SKIP` (the mic listening through the speaker):
  cheer-walk-kid.png. A grown-up song (Roy, Twinkle, wait mode, right hand, `--es midi`): a run of five, a save, the nudge
  (held for the 15 s wait, before the hint plays at 18 s) and 20 in a row, all in the lane's top left corner (the right hand's
  notes are on the right): cheer-walk-grownup.png. The cover screen, dark mode, the same kid walk: cheer-cover-dark.png. The
  debug route's three entries, both audiences, both places, light and dark: cheer-debug-entries-light.png,
  cheer-debug-grown-light.png, cheer-debug-dark.png. A Playground stage (fair-2, the carousel) popped a run of five. Settings
  in Simple: cheer-settings.png. Perform on the cover screen, landscape (Roy, Twinkle, right hand, `--es mode perform`, MIDI
  on the beat): a save after a note let pass, then eight in the good window ("Nice and even.") in the lane's top band:
  cheer-cover-song-perform.png. The hard part passed: Twinkle with a slip in bar 1, then the song opened again in the same
  process and bar 1 played clean ("The practice is paying off." as bar 2 began). No crash, no ANR. Nothing covered the keys, the hit line, the notes being read or the
  instruction in any frame.
- For Roy, on the Fold: whether 1.6 s is long enough to read a pop mid-song and short enough not to distract; the pup's size
  in a lesson's header (56 dp at most, the header's height) and in a song's corner (48 dp); whether Often (12 s and 15 s) is
  the right rate or Less suits grown-ups better; the three entries' feel at 120 Hz (the emulator draws 4 to 8 frames a
  second here); the sparkle's loudness against the piano (velocity 55, two high notes) and the kid's voice lines over a real
  room (they are the bubble alone while the mic listens without headphones); the lines themselves, kid and grown-up.
- Help Roy test on by default (same release, Roy 2026-10-25): see "Tester analytics" above and analytics/INTEGRATION.md.
  TesterRules.switchOn/defaultTurnsOn/showsNotice; an unset switch where the row shows becomes on once and is saved
  (`tester: default on`, `tester_default_on`), a saved choice always wins (contains), the diary stays off by default; a
  grown-up sees TesterDefaultNotice once (a card with OK, never a kid, never over the stage; a kid's profile leaves it for
  the next grown-up one). The event's mark (`defaultEvent`) is saved with the default, so whichever process applied it (the
  email job's after an update loads first) the next launch sends it once the dashboard records; a build without the dashboard
  keeps the mark. Checked on 5558: tester.xml unset gave on with the notice pending; Mila's home showed nothing; Roy's
  Today showed the card after What's new; OK cleared it (`tester: default on, notice seen`). The notice also shows on keyless
  debug builds (the row shows there; nothing is sent without the key).

## Design system (taste-skill redesign, 2026-10-07)
Skills in .claude/skills (taste-skill, redesign-skill; MIT, from github.com/Leonxlnx/taste-skill). They target the web; their portable rules apply here. Read them before UI work.
- Type: Geist (res/font/geist.ttf) for everything, Geist Mono via `Numeric` for times, scores, counts. Both OFL (licenses/).
- Colour: slate neutrals, ONE accent (terracotta `Accent`). Hand colours and green/red feedback live only on the stage and keyboard. Light and dark follow the system; the player is always dark.
- Shapes: controls are pills, the one raised panel per screen 20 dp, inner squares 12 dp, keys 6 px.
- No cards around list items, no pill badges, no `·`/em-dash/en-dash separators, no symbol glyphs as icons (Material Symbols drawables in res/drawable/ic_*.xml, Apache 2.0).
- Player (Roy, 2026-10-07, after Artie): falling notes carry the note name and a suggested finger (engine/Fingering.kt, DP over hand positions; toggles in Settings), a glow while sounding and a spark at the line; 3D keys (lit-from-above body, front lip, pressed keys sink, black-key shadows); the waiting key shows its finger. Tutorial video is off by default and floats over the lane (drag, Sync, close).
- Stage toggles (Metronome, Backing, Chords) are FilterChip text pills in the transport bar; they appear only where they apply.
- Lessons: the embedded player sits in the one raised dark panel (20 dp) on a system-themed screen; progress dots are the only dots in the app (they carry state). Feedback lines are one sentence, in the accent colour, and the voice says the same words.
- Spoken and shown text share one rule set: no dashes or symbol glyphs; note names spoken as words (F sharp, B flat, C four).
- Feedback (2026-10-08): the player never colours keys red or borders the keyboard; a right note sends a flash up its column, a wrong one a soft orange-red glow along the hit line (WrongGlow, warm, not error red). Keys light only while a note sounds or is held. The chord games keep their own red border.
- Songs tab: "Practice today" is the one raised panel; Continue is a plain row under it. Between session parts: one line, no card, spoken, with Skip.
- The transport bar's second row ends in a three-dot overflow (Transpose); stage toggles stay pills. Material defaults that bring a second colour (the TimePicker's pink AM/PM) are recoloured to the accent.
- Beat lines and the count-in numbers live on the stage only.
- Navigation (2.0): Songs / Learn / Practice / Chords / You bottom bar (rail when the window is 600 dp or wider); Settings opens from the gear on You. Player loop is one button: start, end, clear.

## Privacy and licences (2026-10-07)
This repo is Roy's private build (keeps personal music, debug build). The public Play app will be a separate app: see docs/publishing.md (what to remove or replace, Play checklist, data safety answers) and docs/privacy-policy.md (draft to host). In-app: Settings > About > Privacy (honest about debug recordings) and Licences (full texts in assets/licenses). Debug recordings: Settings > Microphone and Microphone setup > Keep the last four recordings (MicInput.keepRecordings, default on in debug, never in release; the privacy page and Settings say `Recordings.KEEP_LINE`) with Delete recordings in Microphone setup. Since 2.10.1 they live in internal storage, files/recordings/fixtures and files/recordings/stalls (data/Recordings.kt, out of backup); `scripts/pull-fixtures.ps1` and `pull-stalls.ps1` read them through run-as. The video window credits the channel on YouTube.

## Environment notes
- JDK: Android Studio's bundled JBR (set in gradle.properties). For gradlew.bat in a fresh shell: $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
- adb is not on PATH: $env:Path="$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
- Emulators: Pixel7_API37 (phone), Fold_API37 (Pixel 9 Pro Fold profile, stand-in for the Galaxy Z Fold7). Start: & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel7_API37
- Real device: Galaxy Z Fold7 SM-F966W, Android 16 (API 36), adb serial RFCY7029SWM, authorized 2026-10-07. Cover screen 1080x2520. Two displays: pass -d <display-id> to screencap when unfolded.
- Wi-Fi adb (frees USB-C for the MIDI adapter): with the phone on USB run `adb -s RFCY7029SWM tcpip 5555` then `adb connect 10.0.0.171:5555`; serial becomes 10.0.0.171:5555. Lost on phone reboot or when the phone drops off Wi-Fi (it did on 2026-10-07): rejoin Wi-Fi, plug USB once, redo tcpip + connect. The IP can change.
- With the Fold7 and an emulator both attached, set $env:ANDROID_SERIAL (e.g. emulator-5554) before dev.ps1 or gradlew installDebug.
- Player debug launch: adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es song fur-elise --el seek <ms> --ez dump true. It logs laneNote rectangles; then: python scripts\lanecheck.py out\lane.png out\lane.log
- Tap a control by its label: .\scripts\tap.ps1 "Wait"  (uiautomator dump; ~1 s per call). Key centers come from "keyRect <note> = x,y" log lines.
- Lint caches its analysis; after changing a non-source file, run: .\gradlew.bat lintAnalyzeDebug --rerun lintDebug

## Private songs (2.8.0, section 4 of docs/PLAN-2.8.md)
- `secrets.properties` keys (all gitignored, all optional): `AI_KEY`, `AI_MODEL`, `AI_BASE_URL`, `EMAIL_KEY`, `EMAIL_FROM`, `EMAIL_FROM_NAME`, `EMAIL_BASE_URL`, `EMAIL_IMAGE_BASE`, `POSTHOG_KEY`, `POSTHOG_HOST`, `DIARY_TO`, `PRIVATE_BUILD` (default false) and `DEBUG_ROUTE_KEY` (2.10.1, "Debug routes" above; `scripts/set-debug-key.ps1`). `scripts/set-private-build.ps1` writes `PRIVATE_BUILD=true` (`-Off` for false).
- `PopSong.privateOnly` marks a chart that must never ship publicly. `engine/chords/PopSongsTemp.kt` holds six (Michael Jackson, chords only, all SKETCH). They are in `ALL_POP_SONGS` but not in `POP_SONGS`; `ui/chords/PopCatalogue.songs` (the screens' one list) adds them only when `BuildConfig.PRIVATE_BUILD` is true, via `PopFind.visible`. The engine never reads BuildConfig.
- Before any release: delete `PopSongsTemp.kt` and its `TEMP_SONGS` term in `PopSongs.kt`. `PopSongsTest.privateSongsMustNotShipPublic` fails in any build without PRIVATE_BUILD=true while a privateOnly song exists, so a public build fails CI until then.

## 2.14 polish (2026-10-26, branch `polish`; emulator-5558 Fold_QA_GU only; version and What's new left to the merger)
The "polish" part of 2.14, from docs/PERSONA-PLAN-2026-10-26.md and the persona walks (adults, kids, family, more; untracked).
- Owner's name: `Profile.named` (saved as "named"); a fresh owner is `Profile.UNSET_NAME` ("Grown-up") and unnamed. Onboarding's first card asks
  "What should we call you?" (optional, owner only: `askName`/`onName`, `Profiles.nameOwner`). A phone from before 2.14 has an owner "Roy" that
  nobody chose (no flag saved: `Profile.fromMap` reads it as unnamed; any other name, and every non-owner, as named): asked ONCE on the next launch
  (`OwnerName.askDue`, `OwnerNameAsk` dialog over Today before What's new and the tour; `Profiles.ownerNameAsked` is kept in the profiles file;
  a rename in Settings answers it too). Every greeting reads `Profile.greetName` through `engine/family/OwnerName.kt` `Greeting`: "Hi, Dana." or
  "Hi there." (layout chooser), Today ("Good evening, Dana."), the tour's first card, "My week" (card line and the page's first line), RunRecap.
- Copy: step feedback `Feedback.text` writes the note (`writtenNote`: "C3", "F sharp", "B flat"), `spoken` keeps "C three"; one Start on Today
  (`TodayCardView(showStart = welcomeBack == null)`: the comeback card's Start replaces the plan's); My songs titles lower their little words
  (`MySongs.lowerSmallWords`: "Canon in D"; never "A", it may be a key); the arcade's locked games are ONE "More games soon!" tile
  (`ArcadeHub`); Balloon pop starts readers at level 1 (`ArcadeStart.level`; `ArcadeBook.record` now moves on from the level played); the arcade
  finish says what each star needs under it (`ArcadeLines.starMeaning`); the locked "Alicia" row waits for its file (`myShelf`); the chord drill says
  "Play C, E and G, in any order and any octave" (`chordPrompt`); the player's "High five!" bubble is "Nice playing." for grown-ups
  (`CheerLines.highFive`); "Show Mum" is "Show a grown-up"; a zero streak says "A fresh start. Play today and a new streak begins."
- Calmer grown-up finish: `Celebration(calm = CalmFinish(line, items, say, onLeave, below))` and the song recap show ONE reward line
  (`RewardQueue.line`: "A sticker for your trail." / "A rare sticker for your trail." / "Something new for the pup to wear." / "A mystery egg for your
  trail." / "+40 XP, level 3 in 120.") and a small "See rewards (3)" chip (`RewardsChip`) that opens ONE `RewardsSheet` (a reward at a time, Next
  reward / Done): the stone's sticker, stickers, a rare one, something to wear, an egg, the house, a record and the level-up (`RewardItem`, built
  by `RewardQueue.of`; a pending level-up is folded in and consumed in `onLeave`, so no second screen follows). One Next button; no XP bar, no
  OneMore card (the recap's Play again is a plain button for grown-ups). Kids keep the reveals one at a time (`KidRewardStage`, each for
  `dwellMs`, a tap goes on), the big button stays pinned.
- Non-readers (kid, `!reader`): `Celebration(picture = true)` is the pup, the stars, the sticker(s) and ONE arrow button (no title, line, stats,
  "Next:" card or text rows; TalkBack still hears them; the one spoken line is the lesson's); "Done for today" the same; `Goodbye(picture = true)` is the
  sleepy pup and one play button (Back closes it); the wrapped gift, the drop cards and the wardrobe reveal come without captions
  (`captions = reader`).
- Day 1: `World.lessonComplete(starter = true)` on a kid's very first finished lesson (empty wardrobe, no lessons yet) opens `Starter.ITEM` (the
  bandana; its practice set at 22 keeps only the cowboy hat) and sticks the first sticker (the stone's own, else the drop) in `Starter.slot(place)`
  (the first slot of the place's scene); `Reward.starter/placedAt`; the reveal keeps the bandana on without asking (`autoKeep`). Dress up opens on a
  slot with something to wear and shows what is open plus ONE "More soon!" tile (a present) instead of padlocks (`MoreSoonTile`).
- Tap the pup to replay (Roy): `ui/pup/PupReplay.kt` (`rememberPupReplay`, `ReplayPup`, `TapSparkle`, `LocalPupVoice`; `engine/pup/PupTap.kt`).
  On Celebrate, the song recap, the arcade finish, the sticker reveal (`DropRevealOverlay`), the wardrobe reveal, the opened surprise (`GiftOpening`)
  and the kid finale (`PupClip(replay, onTap)`), a tap plays the clip again from its first frame (`PupController.playClip`; the clip cue now cancels and
  rewinds the held or playing drawable) or the rig's HAPPY (then CHEER on a party), a 6-star sparkle for 500 ms, and a kid's recorded line
  ("Woof woof! Amazing!") the first tap only (grown-ups hear nothing); unlimited; the pup alone takes the touch, never the buttons; reduced motion is
  the 150 ms fade (`PupMotion.CALM_MS`). PupReplayWiringTest: every finish screen's pup is a `ReplayPup` with a replay.
- Debug extras are one-shot: `DebugRoutes.oneShot(recreated)`; onCreate drops a launch's extras once the activity is recreated (a font or theme
  change, the process coming back), logging `debug routes: extras already used by this launch (recreate), not applied again`. They used to be read
  again (`--es profile`, `--es today`, the seeds). Checked on 5558: two recreates by font scale kept p2, extras not applied.
- Tests: RewardQueueTest, ArcadeFinishHubTest, PupTapTest, PupReplayWiringTest, PolishWiringTest; ProfilesStoreTest, ProfileTest, RosterTest,
  FeedbackTest, MySongsWiringTest, PupWiringTest updated. Emulator walk (screens in app/build/screens/po-*.png, contact sheets po-contact-*.png, not
  committed): onboarding with "Dana" and without a name (layout chooser, Today, tour), a grown-up's lesson finish with the rewards chip and sheet
  (cushion, watering can, level 2), the kid's first lesson (the bandana, `first sticker stuck at garden:sky`), Dress up on day 1, the arcade hub
  and finish (stars, meanings), the non-reader goodbye, and taps on the pup on Celebrate, the arcade finish and the kid recap (log `pup replay #n`).
- Decisions: the unset owner's stored name is "Grown-up" (never "You": the picker and duets print names); the written note keeps "sharp" and
  "flat" as words (no glyphs), only a C gets its octave digit; a grown-up's finish drops the secondary "All lessons" (Back still goes there);
  the starter is the bandana (free, no new art); the arcade star captions are shown, never spoken (no new recordings); the tap-replay voice line is
  an existing recorded kid cheer.
- Expected conflicts: `levels-ages` (Celebrate and LessonScreen's finish: stars, skip, "Try for 3 stars", `RunWords`; ui/Retention's comeback card
  and `Comeback` copy; TodayScreen's greeting), `parents` (TodayScreen's card; the gate, Settings > Family rename now sets the owner's `named`),
  `depth` (RunRecap's numbers where the timing panel goes: the XP line and `drop()` became `CalmRewards`/`KidRewardStage`).

## 2.14 parents
Branch `parents` (2026-10-26, from 2.13.0 063a6422; version not bumped, What's new untouched: the merger does both).
Priya's family walk (docs/PERSONA-WALK-family.md, not committed): a family card on Today, one minutes ledger, a real
daily time, an honest bedtime, a harder and shorter gate. Emulator Fold_PA (a copy of Fold_QA_GU) on port 5562.
- One minutes ledger (engine/brief/MinutesLedger.kt, data/Briefs.kt): the brief's Session, Today and Week, the paw strip,
  the email, Family today and a kid's daily time all read the event log's sessions; one rounding for every minute a
  person reads (counted down, any time at all is at least 1, as PracticeStats always counted). The open session is
  checkpointed every 30 s (MainActivity, repeatOnLifecycle STARTED) and in onPause into one record (prefs "session", out of
  both backup rule files); the first Briefs.init in a process (MainActivity or the email job's alarm) writes the end a
  killed session never got: on screen to its last checkpoint, practice = the profile's ProgressStore totalSeconds now less
  at its start. The practice counters (PlayerScreen, the Playground stage, the arcade, the chords part) register with
  Briefs.addFlusher, so a checkpoint and an end come after their last seconds are written. A checkpoint after midnight
  ends the session first and starts the next; sessions never merge across midnight (Sessions.of with a zone), so a day's
  minutes match the stores' day. Briefs.screenMsToday gives today's screen time. The 2.4 open item ("a process killed
  mid-session loses that session's practice") is closed. MinutesLedgerStoresTest proves it on the real stores.
- Family today (engine/family/FamilyToday.kt, ui/family/FamilyTodayCard.kt): under the Today card on a grown-up's Today when
  the family has kids, one row per child: avatar, name, "14 min playing of 22 min on screen. 2 lessons, 5 stars.", today's
  paw (played, rest day, nothing yet) with the streak. A tap opens that child's brief on Today without the gate (it is the
  grown-up's own Today; every action inside still asks, guardBrief; ParentBriefDialog(gated = false)).
- The brief's first lines (ui/brief/BriefPage.kt): "Can play Twinkle, Twinkle, Little Star with the right hand and find C on
  the keyboard." (Findings.canDo: lessons and songs at two stars or more in 28 days, ids only, written to the wire only when
  there are some and named for the AI's titles; engine/brief/CanDoWords.kt has a skill per Playground lesson, never more than a
  finish shows, other lessons by title, at most 140 characters) and "4 min playing of 8 min on screen."
- Daily time (engine/skin/TimeCap.kt, KidDoors; ui/family/KidTime.kt): off, 10, 15, 20, 30 or 45 min of screen time per
  child (BedtimeStore.capMin, setting "capMin"), in the grown-ups' corner itself and in Family, behind the gate, with what is
  left today. Once reached: a lesson ends at its next step (onStep) or after the next lesson, a song 4 s into its recap, either
  at the latest 2 min on (GRACE_MS); a toy, the arcade or a chord song at once; anything opened afterwards closes at once.
  The goodbye says "That's all for today, Mia. See you tomorrow!" (named, so made on the phone) with "Lessons, songs and
  games wait for tomorrow." and only a Goodbye button; the pup sleeps on his stone and a stone brings the goodbye back; the Play
  sheet's toys and Games and every Songs tile say "Tomorrow" (a tap: the pup says why). My stickers stays open (looking,
  not playing). The cap counts screen time, so the Jam and the games count.
- Wind down (was "Bedtime after the goal"): "After the goal: wind down" with "Lessons close for the day; Songs and the Jam stay
  open.", and when on, "Close Songs and the Jam too" (BedtimeStore.closeSongs, setting "bedtimeSongs";
  Bedtime.songsOpen). With it, the goodnight offers only the goodbye ("Songs and the Jam are asleep too.") and Songs, the Jam
  and the games say "Tomorrow". The brief, the email, Recs and the action card use the new name; "What you did" names
  capMin and bedtimeSongs.
- The gate (engine/family/ParentGateRules.kt): a two-digit sum with a carry and an answer under 100 ("23 plus 48"), or a times
  table from 4, 6, 7, 8 and 9 ("6 times 7"), rolled; open 2 minutes from the last thing done behind it (was 10 from the pass);
  "Lock now" (ui/family/LockNow.kt) heads the kids' corner, Family, the parent brief, Settings and Microphone setup while it is
  open, and leaves a kid's screen with it; it closes on every switch of player and at a fresh start but not on a recreate
  (closesOnOpen), and after more than 30 s in the background (Profiles.gateAwayAt, survives the process). Each close bumps
  ParentGateController.closings: the corner, its Family and brief, and a kid's Settings and Mic setup close with it; the
  corner's Settings, Mic setup and player rows ask the gate again. The three-wrong lockout is unchanged. Show Mum's hold-only
  light gate is unchanged (it only reads the session note; every action in it asks).
- Priya's other findings: P10 fixed (a three-star headline always names its lesson; a day's or a session's brief steps on each
  day, so "A request to hear it is the best praise" no longer comes back every evening; time and day trends only compare whole
  periods, so a first week is never "grew from about zero"; the can-do line). Skipped: P1 (tester switch default on) and P18
  (the onboarding's AI privacy line), both onboarding copy for `polish`; P2 ("Hi, Roy", polish's onboarding name); the
  Low and Info ones.
- Debug (keyless builds): `--es family "Mia:0,Theo:1"` adds kids (name, 1 when they read); `--ei cap <min>`, `--ez winddown
  true|false`, `--ez closesongs true|false`; `--ei onscreen <min>` adds screen time today; `--ei practice` and `--ez sleepy`
  now go through the ledger too (Briefs.debugSession). Walk a capped day: `--es today <next day> --es profile p1 --ei cap 10
  --ei onscreen 9`, then a minute in a lesson.
- Tests: 1757 JVM (1724 before; ParentsWiringTest, MinutesLedgerTest, MinutesLedgerStoresTest, CanDoTest, FamilyTodayTest,
  TimeCapTest, ParentsWordsTest, ParentGateRulesTest and BedtimeTest rewritten), green but PopPrivateTest on this keyless
  worktree. Lint 0 errors. Screenshots: app/build/screens/pa-*.png (not committed).
- Not proven: a kid on a real phone; the 30 s checkpoint's cost on the Fold (one prefs write); force-stop from the recents
  screen on the Fold (on the emulator a force-stop mid-step kept every second of practice the stores had and lost the 11 s of screen time since the last checkpoint); the grown-up's Settings for a
  kid profile closes after the gate's 2 minutes only on an explicit close, not on silent expiry.

## 2.14 levels and ages
Branch `levels-ages` (2026-10-26, from 2.13.0; version and What's new left to the merger). From the persona walk
(docs/PERSONA-PLAN-2026-10-26.md, untracked): day 1 started everyone at the same place, a 9 to 13 year old got the pond and
nursery songs, stars were easy to skip and hard to chase, and the comeback opened a list.
- Who is who (engine/family/Ages.kt, AgesTest): a kid's `AgeBand` (Little, 4 to 7; Big kid, 8 to 13; Profile.band, a kid saved
  before 2.14 reads as Little) and the `Audience` (little kid, big kid, grown-up) with one rule per question: `mapHome`, `tabs`,
  `kidSkin`, `kidCheers`, `firstUnitId`, `allPopSongs`, `songsOpenOnChords`, `arcadeInTabs`, `showsLevels`, `gift`,
  `streakLine`, `voiceSpeed`, `asksLayout`. `Profile.isKid` stays true for both bands: the parent gate, the brief and its kid
  card, email, bedtime, the mic offset, quests and the tester's profile_kind still ask it. `ui/family isKid()` now means the
  kid skin (Profile.kidSkin); MainActivity asks `mapHome` for the home, the Songs place, the next lesson and the finales.
- Big kids: the tabs in Simple (`Simple.layoutOf(audience, ...)`; their own choice holds, never asked at launch), the main
  course (Basics first; placement No, A few, Lots is Basics, Beginner, Intermediate), Songs opening on every song by chords
  (section null is the person's own first section), the arcade on the Play corner (`PlayRow(onArcade)`, "Arcade"), Pup says
  kept, grown-up lessons, player, finish screens and cheer bank, levels and XP, no woofs on Today. Family > the kid > "Make a
  big kid (8 to 13)" / "Make a little kid (4 to 7)" (`Profile.withBand`); a band change reopens the app.
- Adding a kid (ui/family/ProfilePicker.kt): "How old is Leo?" (Little, 4 to 7 / Big kid, 8 to 13), "Can they read yet?", then
  for a reader "Has Leo had piano lessons?" No / A few / Lots (`Placement.kidQuestion`, `Level.kidLine`/`kidSummary`); Go waits
  for the answer (2.6's open item). A pre-reader is not asked and starts in the garden. Lots keeps "Has had lessons"'s start, the
  workshop with the hall's recital as a come-back stone: the hall itself would have reopened eleven stones for every kid placed
  there. Stones placement passed by are drawn as done (warm sand, an empty paw, no stars).
- Skip the garden (engine/kid/KidLevels.kt GardenSkip): a little reader placed at No with the last three garden stones at three
  stars sees "Too easy?" at the top of the map; Skip the garden sets the level to A few (the pond next, the garden's other
  stones passed by), Not now is for good (KidStore.gardenSkipAsked). No gate: nothing is lost.
- Little readers' pop packs (KidPop): Pop, Greatest hits and Rock after the nursery songs on the kid Songs, each the kid chord
  list of that style (Screen.KidChords.chip); folsom-prison-blues, knockin-on-heavens-door and watermelon-sugar are left out by
  title.
- Day one (engine/nav/DayOne.kt, DayOneTest): never and a little open Today's plan on the lesson (`planSession(lessonFirst)`)
  with no chord review until two course lessons are played; the review's words are plain for them (`ChordWords`: "Play a C
  chord", "C, E and G are lit. Any octave counts.", the keys lit). Some and a lot: the check leaves out Find a C (some: five
  notes and Twinkle; a lot: Twinkle; what it leaves out counts as played), no mic ask in the check and no mic banner unless they
  chose the microphone (`progress.useMic`), no Twinkle as Start here, in the plan or in the library (a song by chords instead,
  "Let's start with a song by chords."), and the level and XP seeded from the answer (engine/progress/PlacementLevel.kt: Newcomer,
  Note Spotter, Melody Maker, Steady Hands; a floor written quietly by ProgressStore.seedFromPlacement once the first-run questions
  are done; grown-ups and big kids).
- I know these chords (engine/chords/KnownChords.kt, ui/chords/KnownChordsSheet.kt): the six first chords then the catalogue's
  most used (16), the songs they play counted as they are picked; Save writes each at the known box (LessonStore.know, never
  lowering a box). Offered on Today to some and a lot with nothing known (until answered), and under an empty Songs I can play or
  One new chord away.
- Stars: the 3-star rule is `Stars.three` (a clean run, or 98 percent or more, nothing let go; 90 with the timing within 80 ms
  still counts); wait-mode notes let go reach it through `LessonStars.ofRun(report, letGos)`. Next over a graded step not passed
  asks "Skip this one? It stays at one star." (Skip it / Keep playing); a lesson with a graded step skipped finishes neutral ("On
  to the next." on screen and nothing said, the standing pup's wag, no clip, confetti or fanfare: ui/Celebrate.kt `FinishStars`, one trailing parameter
  marked "2.14 levels-ages"). "Try for 3 stars" on a lesson's finish under three stars and the kid map's Replay chip on stones
  under three stars open the lesson at its first graded step (`LessonStars.firstGraded`; a new key resets the lesson).
- The comeback (engine/Comeback.kt resume): the lesson left part way at its step (Prefs.openStep, kept as the steps of a lesson
  not finished go by; a replay of a finished one never overwrites it), the
  chart or song played last, else the next lesson; never a list. A little kid's card is the pup's as before and opens the lesson
  left, or a stone they know at its first graded step; a big kid's reads "Welcome back." and "Pick up where you left off: <it>.";
  a grown-up's as 2.9. After a week or more away one line names the streak: "Your 12-day streak ended on the 9th. A new one
  starts today." (grown-ups and big kids).
- Debug: `--ei kidstars 1..3` with `--es kidseed <page>:<n>` seeds every stone at that many stars.
- Tests: AgesTest, DayOneTest, KnownChordsTest, KidLevelsTest, ComebackResumeTest, LevelsAgesWiringTest; StarsTest,
  PlacementTest, PlacementCheckTest and four wiring tests follow the new rules. 1754 JVM tests, all green but
  PopPrivateTest.privateSongsMustNotShipPublic (red by design on a keyless worktree); lint 0 errors, 29 warnings (as 2.13).
- Emulator walk (Fold_LA copy of Fold_QA_GU on 5560, deleted after; touch, no mic): a grown-up at a lot (Twinkle-only check, no
  mic ask, Today on "a song by chords" with Riptide as Start here, no mic banner, the chord pick at 16 songs, Level 10 Steady
  Hands); Leo, a big kid reader with a few (the flow, Beginner's first lesson in the grown-up skin, the skip sheet, the neutral
  finish with Try for 3 stars opening step 3, Songs by chords first with all 107, the Play corner's Arcade, Today); Mila, a little
  reader with lots (the workshop, the hall passed by, a Replay chip opening step 2, the Pop pack); Theo, a little reader at No
  (Skip the garden, then pond-1); the comeback for each with `--es today` (the grown-up's G major at step 3 and the streak line,
  Leo's plain card, Mila's card opening a stone at step 2). Screenshots app/build/screens/la-*.png (not committed).
- Open: the plain chord words were checked by tests only (no beginner reached the chord review on the walk); the neutral finish
  for a little kid by tests only; whether Lots should start at the hall rather than the workshop is Roy's call.

## 2.14 depth
Branch `depth`, 2026-10-26; version not bumped (the merger does); emulator Fold_DP on 5564, a wiped copy of Fold_QA_GU. For strong
players (docs/PERSONA-WALK-more.md, Sam: "thin timing feedback", "98% gives 2 stars", "sight-read has two levels and no staff",
"Level 1, Newcomer"; -adults.md, Marc). Six parts, the rules Android-free in engine/ with JVM tests.
- Perform timing (engine/PerformTiming.kt): `PerformScore.timings()` keeps every graded note (its time, keys, offset in song ms,
  null for a miss; `Recap` itself is unchanged, it is logged and built on every key). `PerformTiming.of(notes, barMs, beats, speed,
  midi)` puts each note in its bar (`TroubleSpots.barIndex`, so "bar 3" is the recap's "Loop bar 3") in real ms (divided by the
  speed), gives each bar a verdict (`OFF_MS` 60, between RunWords' 25 and 80: messy with a miss or early and late together,
  rushed when a quarter of its notes or more came early and none late, dragged the other way, clean otherwise; a bar with nothing
  to play has none) and one plain line, strongest first: one beat early or late in two bars or more ("You rush the first beat of
  bars 3 and 7.", only notes within 0.1 beat of a whole beat name it), nearly every note to one side on a MIDI keyboard (points at
  the offset), rushed or dragged bars, a lean over the whole run, uneven bars, "Your timing was steady all the way through."
  (A run with no note in time says so instead: "No notes came in time, so there is no timing to show yet.")
- The recap (ui/player/TimingPanel.kt, placed by ui/RunRecap.kt in its kid and grown-up branches; branch `polish` owns the recap's
  layout): `RunEnd.timing` (and `flats`), set by `RunEnd.timed(...)` at all three Perform ends in ui/PlayerScreen.kt (library
  song, generated song, pop run). A grown-up sees "Timing", the star rule against the run, a strip on a small stage (one cell a
  bar in Good, PupGold, LeftHandColor or WrongGlow, its number in it, a key), the line in the accent, "Tap a bar to see its notes"
  and a tapped bar's notes ("Beat 2 and", the keys, "120 ms early", "on the beat", "missed"). A kid sees only the strip, a face per
  bar and never a sad one (clean grins, rushed and dragged an "oh", messy a wavy grin), no number, no words, no taps.
- Live tick (ui/HitLights.kt `tick`, pooled like the grade words): a Perform hit puts a short mark in the hit line's band at its
  key's column, left of a faint centre notch when early, right when late, up to half a white key at the good window's edge (amber
  early, blue late, white on the beat), for 650 ms. Never a word. Keys are now timed between frames in Perform
  (`PlayerVars.tickNanos`, up to 250 ms past the last frame): the transport moves a frame at a time, so a key read up to a frame
  early before.
- The song's star rule (engine/PerformScore.kt `songStars`, `SongStars`): 3 = 98% or more played (the recap ring's accuracy, so
  one miss in fifty at most) and at least 9 of 10 notes in the good window; 2 = two misses at most, or at least 3 of 4 in time;
  1 = reached the end. `Stars.of` (the song recap, a kid's words and
  party, the brief's song stars, the duet line) uses it; `Stars.perform` (a lesson step, LessonStars, Bites) is untouched for
  branch `levels-ages`. The recap shows "3 stars: 98% or more, and 9 of 10 notes in time." and "This run: 1 miss, 40 of 42 notes
  in time." (the 2-star rule as well under one star). Decision: the brief said "3 = no misses and 90% in time" and "98% gives 3",
  which cannot both hold for Sam's run (98% accuracy counts good plus OK, so a 98% run has a miss: 40, 1, 1). "No misses" became
  "98% or more" (the number the ring shows; levels-ages' lesson rule also gives 98% its third star) and the timing half stays, so
  a run with every note played but few of them in time never gets three. Sam's exact run is 3 stars; so is one with nothing missed
  and 41 of 42 in time, every note early (bias -132; the old average-timing test gave 2). Both walked.
- MIDI timing offset (engine/MidiOffset.kt, audio/MidiDevices.kt `offsetMs`, ui/MidiTiming.kt): -100 to +100 ms in steps of 5, 0
  by default, kept per phone in the "mic" prefs file (`midiOffsetMs`, carried by a backup). Settings > Microphone > "MIDI keyboard"
  (a Sub, so SettingsWiringTest's `Section("Microphone")` stands): a slider snapped to 5 ms, "Back to 0", and "Tap along to
  measure", a dialog with four count-in clicks and eight to tap (750 ms apart, about ten seconds). The offset is the median of tap
  minus click over taps within 250 ms of a click (taps past 80 ms of the first median left out, at least 5, else "Try again").
  Applied to Perform's scoring only: `keyStruck(midiOffsetMs)` adds it to the latency `Transport.noteInput` gets, for MIDI
  note-ons only; the recording, Wait mode and the lights are untouched. Positive: the keyboard is heard late, its notes move earlier.
- Staff sight-reading (engine/SightStaff.kt, ui/sight/StaffDrill.kt, ui/SightReadScreen.kt): eight levels. The old pieces are 1
  and 2 (best `sight:<n>`, time, lower is better); 3 to 8 are a two-minute drill on a drawn staff (the theory glyphs of
  ui/theory/Draw.kt, engine/theory/Music.kt): treble C to G, the treble staff (C4 to A5), the bass staff (E2 to C4), both staves
  (middle C on either), sharps and flats (two notes in five altered, never E or B sharp, F or C flat), rhythm (a 4/4 bar at 72 bpm
  with clicks and one bar to count in, the next bar dimmed beside it, a cursor, each note graded by PerformScore, a note let pass
  counts as wrong). A note level takes the right key (touch, MIDI or the mic); any other key counts wrong and the note stays. Best
  per level `sightDrill:<n>` (most right, then fewest wrong; cleared by Reset progress). Who has 3 to 8 (`SightLevels`): a
  grown-up in Advanced or Full, or placed at Some or A lot; a kid who reads (CoursePath's big kid) with a parent's "Has had
  lessons". ponytail: levels-ages' 9 to 13 shape should open them too. Learn's row then says "Four new bars, then notes on the
  staff. Eight levels." Eighths have flags, not beams.
- Today's next challenge (engine/nav/NextChallenge.kt, `SessionPart.Challenge`): a grown-up placed at Some or A lot, or whose next
  lesson is past the first three units (Basics, Beginner, Rhythm; "level" read as course unit, decided), gets one item at their
  level, a kind a day in turn (`(epochDay + profile id hash) mod 3`): a library song in Perform at a target tempo (medium songs at
  85% of their tempo at tier 1, hard songs at all of it at tier 2 = A lot or Keys and on; never above the song's own tempo; the
  next song not three-starred each time the kind comes round), a staff drill (level 4 or 6, one up once a best there reaches 20
  right, goal one more than the best and at least 15) or chord changes (I-V-vi-IV or I-vi-IV-V at 80 bpm, ii-V-I or the blues at
  100, keys in turn). `planSession(..., challenge)`: it comes last and stands in for the Continue song played whole (a worst bar on
  a loop stays). Today's row ("Next challenge: 3 stars at 90 bpm", the flag icon), the interstitial, the chips, the quest (a
  challenge song is the song task), and no "Start here" easy song or "Let's start with an easy song." beside it ("Hi, I'm your
  coach. There's a challenge at your level in today's plan."). Practice today walks it: the player in Perform at the target speed
  (closing the recap goes on; `daily: challenge ... met` in the log), or the drill at its level with its goal ("7 of 15 right",
  "Challenge met!", "Go on").
- Levels: names to level 40 (21, 23, 25, 27, 29, 31, 32, 34, 35, 37, 38, 39 added). LevelLadderTest works out a daily
  intermediate's month (about level 34) from the XP table and asks every level to it plus five to have its own name. You reads
  `ProgressStore.level`, and the recap's bar caption takes the store's level (`xpBarFor(before, after, level)`), so the level that
  branch `levels-ages` seeds from placement shows on both. Nothing is seeded here: an A lot player reads "Newcomer" until that
  branch merges.
- Debug: `--es screen sight --ei level 1-8 [--es midi "<ms>:<key>/<vel>,..."]` (a script fixes the seed at 1 and plays from the
  drill's Start; the drill logs its notes, `sight drill: notes ...`, and level 8 its bars with beats); `--es screen settings --es
  midi ...` (from the calibration's Start); `--ez daily true --es midi ...` reaches a challenge part too. A script into a song now
  starts with the transport's first frame, on the frames' clock (`MidiDevices.debugPlay(script, fromUptimeMs)`), so a slow first
  frame no longer puts every scripted note early; a loaded emulator still wanders 30 to 50 ms run to run, so script early or late
  notes at 100 ms or more.
- Tests: PerformTimingTest (11), MidiOffsetTest (4), SightStaffTest (7), NextChallengeTest (5), LevelLadderTest (2),
  DepthWiringTest (6); ProgressionTest (level 41 between ranks) and KidWordsTest (a song's kid words follow the song rule) adjusted.
- Emulator walk (Fold_DP, emulator-5564, fresh data: onboarding "A lot", Full, keys or MIDI; `--es midi` scripts): Today with the
  challenge and the new greeting (dp-01); Practice today to the challenge, the level 6 drill, 20 right and 1 wrong for a goal of
  15, "Done for today" (dp-02 to dp-06); Twinkle in Perform with the first beat of bars 3 and 7 120 ms early: the live ticks
  (dp-07, dp-07b), the panel's "You rush the first beat of bars 3 and 7." and bar 3's notes (dp-08, dp-09, dp-09b); 41 of 42 in
  time with nothing missed at bias -132: 3 stars and the line pointing at the offset (dp-10); Sam's 40, 1, 1 at 98%: 3 stars with
  the rule and the run beside them (dp-11, dp-11b); the kid strip (skin demo, dp-17); levels 3 to 8 (dp-18, dp-18b, dp-19 to dp-22; the rhythm level 115 right
  and 7 wrong over two minutes); the offset row, the calibration's three states and 50 ms saved (dp-12 to dp-16); Learn's row
  (dp-23); You at level 4 (dp-24); a sharp on the bass staff (dp-18-sight-level-7-sharp) and a level 7 challenge, "Challenge
  met!" (dp-04b, dp-05). The other two challenge kinds on a re-made Fold_DP (`--es today 2026-10-27` and `2026-10-28` with
  `--ez daily true`): ii-V-I in F at 100 bpm and Rondo alla Turca at 100 bpm (its own tempo at tier 2), each in Perform to its
  recap with the timing panel, Close going on to "Done for today" (dp-25 to dp-30). A slower target (tier 1, speed under 1)
  rides PlayerStart.speed, the comeback's, and was not walked. Screenshots in app/build/screens/dp-*.png (not committed).
- For Roy, on the Fold with the Yamaha: whether 60 ms is the right line for rushed and dragged; the tick's size at 120 Hz; the
  calibration with real taps (the scripted run found 50 ms for taps scripted about 40 ms late); the drill's keyboard on the cover
  screen; whether a challenge should replace the plan's whole-song part or sit beside it.

### 2.14.0 status (integrated 2026-10-26, branch `release-2.14`, versionCode 30; emulator-5558 Fold_QA_GU only, not on Roy's phone yet)
The four 2.14 branches merged in the order polish, parents, levels-ages, depth (the merge commits name every conflict and
choice). Test baseline: 1863 JVM tests, all green, PopPrivateTest included (a keyed worktree); lint 0 errors, 30 warnings
(the branch sections above give each branch's own count). What's new 2.14.0: three lines (big kids and a start at your level;
the family card, a daily time limit and Lock now; tap the pup, timing bar by bar). WelcomeTest checks them.
- Two 3-star rules, each in its place, never or-ed: a lesson step is `Stars.three`/`perform` (levels-ages: clean or 98% with
  nothing let go, 90% with the timing within 80 ms); a song's Perform run is `songStars` through `Stars.of` (depth: 98% AND
  9 of 10 notes in time), which keeps levels-ages' `letGos` (anything let go: two stars at most). Each place says its rule in
  one frame: the recap's TimingPanel `SongStars.RULE` ("3 stars: 98% or more, and 9 of 10 notes in time.") and "This run: ...";
  a grown-up's or big kid's lesson finish under three stars `Stars.LESSON_RULE` ("3 stars: steps played clean or at 98% or more
  (90% with the timing within 80 ms), and nothing let go.", "80 ms" held by a no-break space) and "This lesson: 1 of 3 steps
  at 3 stars." under its stars (the calm finish's reward slot). A little kid's finish has no numbers.
- Today: `greeting(name, easy, challenge)`: the challenge line, then the song by chords, then the easy song, all with the name;
  one Start (a comeback's); the family card right after the Today card (a grown-up's Today only: it opens a brief without the
  gate and big kids see Today), then I know these chords. Continue: a challenge in the plan wins (no Start here), else the easy
  first song, else the easiest song by chords. `planSession(..., lessonFirst, challenge)` takes both.
- Big kids (`Profile.isKid` true, `kidSkin` false) stay kids for the grown-ups: BigKidGrownUpsTest. Wind down, the goodnight
  after the goal and the goal's well done in LessonScreen ask `anyKid`; a big kid's grown-up finish reads "Done for today" into
  the goodnight. The kid doors (MainActivity `kidDoor`) cover the tabs' screens too: Practice today, Learn chords, Progressions,
  Sight-read, Ear training and Theory are LESSON doors, the chord games a GAMES door; Practice today's lesson ends at its next
  step once the time is up, as a lesson from Learn does; no comeback card (the map's or Today's) offers a lesson once the day
  is closed. `cheer_pop`'s profile kind is the profile's (`CheerPops.profileKid`).
  The brief's next lesson (AppEffects) and the email's map page (BriefEmail) ask `mapHome`. Skin, not safety: the pup tap's woof,
  the non-reader goodbye picture, the day-1 starter and the song recap (RunRecap `kid` and its words) follow `kidSkin`, so a big
  kid reads the grown-up recap with the timing panel. `SightLevels.staffOpen/open(..., bigKid)`: the grown-ups' rule for them.
- Polish with parents and levels-ages: a picture goodbye (little non-reader) whose songs are closed or whose time is up has the
  goodbye as its one button; in a picture finish "Try for 3 stars" is the repeat icon (words for TalkBack); the pop packs, the
  map's Replay chip and Try for 3 stars ask the cap first.
- Emulator smoke (5558, fresh install, keyed build through scripts/adb-route.ps1; Help Roy test turned off right after
  onboarding; screenshots app/build/screens/rel-*.png, not committed): Sam at A lot (level 10 Steady Hands, Today with the
  ii-V-I and staff-drill challenges, the chord pick at 9 songs); Leo (big kid, reads, a few) and Mia (little, not reading) added
  with the sum gate (hold, then "75 plus 17"), Family today on Sam's Today and not on Leo's; Mia's first lesson: the picture
  finish, the bandana, the repeat icon, two pup taps (`pup replay #1`, the woof once); Twinkle in Perform by a MIDI script: the
  timing panel, the rule and run lines, the calm line and its chip and sheet; a grown-up lesson at two stars with the lesson
  rule and Try for 3 stars (opened step 3); Leo's daily time reached in Practice today (the grace closed it before the fix, the
  next step after it) and Start closing at once; Mia capped: the picture goodbye with its one check button, and after a week
  away no comeback card offering a stone over it (it did before the fix); Lock now; comebacks for Sam (the streak line) and Leo
  ("Pick up where you left off: Your first melody.", step 2). No crash.
- Open for Roy: big kids get no next challenge (depth's `NextChallenge.eligible` is grown-ups only) and no What's new or tour
  (`Welcome.next` asks isKid); a capped big kid's tabs still show the plan and the library (each closes at once when opened,
  no "Tomorrow" on them); big kids' arcade hub copy ("Pick a game!") stays kid-style; the lesson rule line's length on the
  cover screen.

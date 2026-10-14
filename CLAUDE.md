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
  2. Feedback light: ui/HitLights.kt drawn by NoteLane in the draw phase (a clock ticks only while a light lives). Correct note: flash up its column (hand colour, white core); wrong: soft orange-red glow along the hit line (WrongGlow). No green keys, no red border. Settings > Falling notes > Feedback light (default on; off = old flashes). Evidence: touch hit/wrong screenshots with --ef lightSlow 12-15; injected mic run logs "light hit" per note; toggle off logs "redFlash" with the red border back.
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
  --tests "*SpokenCatalogTest*" --rerun, then C:\devoice-toolsenv\Scripts\python -I scriptsender_voice.py.
- Jank (emulator-5556, 10 s twinkle wait run with injected mic): 76 to 87% janky, p50 61 ms, but a still Learn screen and the
  motion demo measured the same that afternoon (two emulators on one host, the composer at 65% CPU), so it says nothing
  about the player. Measure on the Fold with the profile build before release.
- Open: skipped ear rounds still earn the 5 XP of a round (Team D reports every round); onboarding and the pup's tap lines
  are shown, not spoken, so they are not in the voice catalog.

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
This repo is Roy's private build (keeps personal music, debug build). The public Play app will be a separate app: see docs/publishing.md (what to remove or replace, Play checklist, data safety answers) and docs/privacy-policy.md (draft to host). In-app: Settings > About > Privacy (honest about debug recordings) and Licences (full texts in assets/licenses). Debug recordings: Microphone setup > Keep practice recordings (MicInput.keepRecordings, default on in debug, never in release) with Delete recordings. The video window credits the channel on YouTube.

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

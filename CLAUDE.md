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
  --tests "*SpokenCatalogTest*" --rerun, then C:\devoice-toolsenv\Scripts\python -I scripts
ender_voice.py.
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

## 2.3 part A: the workshop and the fair (2026-10-18, branch `k40a`, emulator-5558 Fold only; version not bumped)
Roy asked for forty more kid lessons: which finger plays, how to sit and hold the hands, basic ways to use the piano,
and games. Part A is the workshop's twelve (sitting, hand shape, finger numbers, hand positions, wrist, arm weight,
touch, pedal) and the fair's four games, plus the place work for all four new places (workshop, treehouse, night sky,
fair). Part B (branch `k40b`) writes the treehouse's and the night sky's lessons. Merge notes:
ui/playground/INTEGRATION.md, "2.3".
- Path: garden, pond, hall, workshop, [treehouse, night sky from part B], fair, then Basics. engine/playground/Pages.kt
  `PAGES` (part B adds two lines there). Workshop: `WORKSHOP_ID` "playground-workshop", `workshop-1..12`, Workshop.kt.
  Fair: `FAIR_ID` "playground-fair", `fair-1..4`, Fair.kt. Readers walk both whole (BIG_KID_LESSONS names only the
  garden); grown-ups see them as Extra units. COURSE is now the pages' levels then Basics (`PLAYGROUND_LEVELS + ...`).
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

## 2.3 part B: the treehouse and the night sky (2026-10-18, branch `k40b`, emulator-5554 only; version not bumped)
Roy asked for 40 new kid lessons: which finger to use, basic ways to use the piano and hand placement, and lessons that
keep kids engaged. Part B is 24 of them on two new Playground pages; part A (branch `k40a`) owns the places, the world
and its two pages (Workshop, Fair). Merge notes, page entries and the registry edit: ui/playground/INTEGRATION-B.md.
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
  The steps are marked by text in engine/playground/StageGames.kt (the Game enum is part A's file); the only edit to an
  existing UI file is three lines in PlaygroundStage.kt. Rules: TapGames.kt, BeatGames.kt, ListenGames.kt.
- On this branch the two levels sit at the end of COURSE (so `--es lesson` opens them); they become Playground pages at
  the merge (until then StepRetry's let go, PlayerScreen's letGo and ANY_OF_LESSONS do not reach them). CourseTest
  (130 lessons) and AnyOfTest were adjusted for that.
- Voice: TreeSkyLines.fixed() (steps, scenes, the stages' lines) in SpokenCatalog.kidLines; 336 new lines recorded (314 at
  0.85), 2954 in the catalog; render_voice.py deleted nothing.
- Tests: 832 JVM (797 before): TreeSkyTest (shape, copy, bubbles, no digits in kid lines, no finger claims, stage
  marks, games, concerts, beats, jam, any-of), TreeSkyFlowTest (24 lessons, a silent and a wrong-keys kid, as merged),
  TreeSkyHearingTest (every set on PianoSynth: positions, higher or lower, the jam, middle C; the drum, the clicks and
  the band never a key), TreeSkyFingersTest, TapGamesTest, TreeSkyGamesTest. Time model 114 to 180 s a lesson. Lint
  0 errors (25 warnings, all older). Screenshots of every lesson's main stage (pre-reader Mia p1, reader Leo p2):
  app/build/screens/k40b-*.png (not committed). Debug: `--es lesson treehouse-N|night-N --ei step n --es profile p1
  --ez awake true`.
- Not proven: everything with a real piano and a child (emulator only); the beat stars' window (250 ms) and delay
  correction on a real phone; the fast and slow grade when the matcher drops a fast note; the jam's band on a phone
  speaker. On the emulator the stages hear the host's room and speakers (the demo comes back late through the host
  mic), so rounds there get found or let go by themselves; emulator-5554 (Medium_Phone_API_37.0) froze once under the
  host's memory pressure (0.5 GB free) and was restarted.

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

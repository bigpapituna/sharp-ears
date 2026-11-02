# QA sweep: every course, Sharp Ears 2.18.2 (main b0c9bcd3), Fold emulator

Date: 2026-11-02, 01:30 to 07:45 (host clock). Branch `qa-audit`, from main at b0c9bcd3 ("2.18.2: course audio for Claire, Sienna and Rory").
Build: `./gradlew.bat assembleDebug -Psharpears.afterdark=true` in `C:/dev/piano-wt/qa-audit` (keyless: no secrets.properties, so
the debug routes are open). Device: AVD `Fold_QA_GU` on **emulator-5562 only** (5554 and every other emulator were never touched).
Report pass only: the commit changes no app code. The walk used throwaway log lines in the worktree (see "Method"); they were reverted before the commit.

## Totals

| Course | Walked | Result | Bugs |
|---|---|---|---|
| Main Road (10 levels, 70 lessons, 306 steps) | 70 lessons, all 306 steps (85 Read, 62 Listen, 80 Play, 25 From memory, 43 Perform, 7 Ramp, 2 Pedal, 2 Dynamics) | 159 of 159 interactive steps passed with right notes; 62 of 62 Listen demos sounded every note (1,089 of 1,089, worst drift 55 ms); 85 of 85 Read steps showed every scene | B-01 to B-07 |
| Theory (6 units, 27 lessons) | 27 lessons, 112 of 112 scenes, 27 quizzes (155 questions asked, 27 deliberate wrong answers re-asked at the end) | all passed; 19 key-tap and 14 listen questions answered | B-02 (5 lines in the phone's voice) |
| Ear training (5 games x 3 levels) | 15 of 15 game-levels on the emulator, plus 3,750 rounds on the JVM with a perfect, a wrong and a skipping player | 0 findings in the 3,750 rounds; all 15 played through the UI | B-08 |
| Sight-read (levels 1 to 8) | 8 of 8 levels (1 and 2 by mic injection, 3 to 8 by pretend keyboard), plus 600 pieces and 600 drills of seeds on the JVM | all worked, "New best!" end screen seen | B-10 (debug route only) |
| Practice tab | 5 of 5 tabs opened, a scale and an arpeggio demo played; 2,940 scales, arpeggios and warm-ups (every key, hand, octave count, tempo, 96 routines) on the JVM | 0 findings | B-10 (debug route only) |
| Coach voices (Liam, Rory, Claire, Sienna), grown-up profile | 4 voices x 5 sessions; static check of every course file against the 437 grown-up lesson lines; Whisper over 1,744 clips | narration played on every walked line (13 narrated lines per voice, none from the wrong voice folder); every clip decodes and says its line | B-05, B-06 |
| Kid profiles (Mia little, Leo big kid) with a coach voice planted in both | Mia: playground-1 (2 steps), pond-1, playground-2; Leo: find-c, first-melody, beat, sit | 0 coach or narrated lines, kid recordings only | none |

Across the 339 log files of the walk: no crash, no ANR, no `FATAL EXCEPTION`, no warning or error line from SharpEars.

## Bugs by severity

| Severity | Count | IDs |
|---|---|---|
| Blocker | 0 | |
| Major | 2 | B-01, B-02 |
| Minor | 8 | B-03 to B-10 |

## Setup and method

- Fresh `Fold_QA_GU` (`-wipe-data -no-audio`), the afterdark APK installed. Help Roy test was turned off and "screen keys" chosen once (to get past the first-run sheets); the profile was later set to piano level "Some" (roster edit through `run-as`) so Sight levels 3 to 8 open.
- Drivers: `scripts/qa-audit/*.py` (committed with this doc). The course was dumped from `COURSE` by a JVM test (`QaDumpTest`: ids, steps, exercises, every note with start ms and hand, every spoken line and its SpokenCatalog key). Each lesson was launched at step 0 (walks Read and Listen steps by itself; the driver taps Next on the last Read scene) and again at each Play, From memory, Perform and Ramp step with `--es lesson <id> --ei step <n> --es midi "<script>"`. The script plays the step perfectly at its tempo and adds wrong keys (the key above the right one) at the first, middle and last group of the wait-mode steps; Perform and Ramp scripts include the 2-bar count-in (and the 6 passes of a Ramp). After a step ends the driver taps the cheer bar's Continue. The logcat of every launch is kept in `app/build/qa/walk/<lesson>-<step>.log` (not committed). Pedal and Dynamics steps were played by hand-built scripts (pedal events, soft then loud velocities), and their "Skip (needs MIDI)" path was checked (pedal step 3: skipped, lesson done).
- Listen demos: a log line in `audio/Stage.noteOn` ("QA piano noteOn") and in `audio/ClipPlayer` ("QA clip play/started/end", with the prepare time) was added **in the worktree only** and reverted. The voice-fallback finding (B-02) also used a log line in `audio/Voice.kt` and in `SpeakingPolicy.choose`.
- Voice lines: every `voice:` log line was compared with the step's text (Read steps are spoken scene by scene). Latency = step-open log to clip start, and the clip's own prepare time (`prep=`); the log has no line for the audio device's first sample.
- Narration files: static check of `src/afterdark/assets/coach/<voice>/course` against the SpokenCatalog keys of the 437 grown-up lesson lines (own `_w`/`_m` variant first, then the plain file, as `CoachGate.narrationOf` does), then Whisper (small.en, GPU) over 1,744 clips: all decode; expected words were found in the transcript for all but 11 clips, and those are digits and letter names the ASR writes differently ("1451", "CEG", "Thamundi").
- Coach on a profile: `coachVoice`, `coachFor=w`, `coachNotice` written into `shared_prefs/progress.xml` (kids: `progress.k1.xml`, `progress.k2.xml`) with `run-as`; the owner's values were cleared again.
- Mic path: `--es inject "synth:..."` on First melody (Play and Perform) and on Sight levels 1 and 2 (the `--es midi` route does not reach those two, B-10). Play by mic: 14 of 15 groups in time (the 15th note arrived early because the injection used even spacing for a dotted rhythm), latency 21 to 64 ms.
- Not run: the repo's own unit tests (this is a walk, not a build gate).

## Main Road: summary by level

| Level | Lessons | Steps | Steps walked | Interactive steps scripted | Passed | Lessons with a bug |
|---|---|---|---|---|---|---|
| Basics | 6 | 21 | 21 | 9 | 9 | 1 |
| Beginner | 8 | 34 | 34 | 17 | 17 | 2 |
| Rhythm | 6 | 24 | 24 | 12 | 12 | 1 |
| Intermediate | 14 | 55 | 55 | 26 | 26 | 4 |
| Keys | 6 | 30 | 30 | 24 | 24 | 3 |
| Advanced | 5 | 23 | 23 | 12 | 12 | 2 |
| Technique | 7 | 22 | 22 | 8 | 8 | 7 |
| Repertoire 1 | 6 | 36 | 36 | 18 | 18 | 6 |
| Repertoire 2 | 6 | 36 | 36 | 18 | 18 | 6 |
| Technique 2 | 6 | 25 | 25 | 15 | 15 | 5 |
| **Road total** | **70** | **306** | **306** | **159** | **159** | **37** |

Per lesson (steps walked = steps whose log was read; "scripted / passed" counts the Play, From memory, Perform, Ramp, Pedal and Dynamics steps played with right notes; the first-run steps a Never grown-up gets before "sit" were also seen):

| Level | Lesson | Steps | Walked | Scripted / passed | Bugs |
|---|---|---|---|---|---|
| Basics | sit | 3 | 3 | 1 / 1 | - |
| Basics | find-c | 3 | 3 | 1 / 1 | B-07 |
| Basics | fingers-right | 4 | 4 | 2 / 2 | - |
| Basics | fingers-left | 4 | 4 | 2 / 2 | - |
| Basics | beat | 4 | 4 | 2 / 2 | - |
| Basics | white-keys | 3 | 3 | 1 / 1 | - |
| Beginner | first-melody | 5 | 5 | 3 / 3 | B-02 |
| Beginner | lengths | 4 | 4 | 2 / 2 | - |
| Beginner | bass | 4 | 4 | 2 / 2 | - |
| Beginner | together | 5 | 5 | 3 / 3 | - |
| Beginner | black-keys | 4 | 4 | 2 / 2 | B-02 |
| Beginner | rests | 4 | 4 | 2 / 2 | - |
| Beginner | scale | 4 | 4 | 2 / 2 | - |
| Beginner | first-song | 4 | 4 | 1 / 1 | - |
| Rhythm | rhythm-quarters | 4 | 4 | 2 / 2 | - |
| Rhythm | rhythm-halves | 4 | 4 | 2 / 2 | - |
| Rhythm | rhythm-eighths | 4 | 4 | 2 / 2 | B-02 |
| Rhythm | rhythm-dotted | 4 | 4 | 2 / 2 | - |
| Rhythm | rhythm-waltz | 4 | 4 | 2 / 2 | - |
| Rhythm | rhythm-syncopation | 4 | 4 | 2 / 2 | - |
| Intermediate | scale-left | 4 | 4 | 2 / 2 | - |
| Intermediate | scale-both | 4 | 4 | 2 / 2 | - |
| Intermediate | triads | 4 | 4 | 2 / 2 | - |
| Intermediate | inversions | 4 | 4 | 2 / 2 | - |
| Intermediate | broken | 4 | 4 | 2 / 2 | B-02 |
| Intermediate | progression | 5 | 5 | 3 / 3 | B-01 |
| Intermediate | minor | 4 | 4 | 2 / 2 | - |
| Intermediate | expression | 4 | 4 | 1 / 1 | - |
| Intermediate | lh-waltz | 4 | 4 | 2 / 2 | - |
| Intermediate | lh-pop | 4 | 4 | 2 / 2 | B-07 |
| Intermediate | lh-octaves | 4 | 4 | 2 / 2 | - |
| Intermediate | lh-tenths | 4 | 4 | 2 / 2 | - |
| Intermediate | pedal | 3 | 3 | 1 / 1 | B-01 |
| Intermediate | dynamics | 3 | 3 | 1 / 1 | - |
| Keys | key-g | 5 | 5 | 4 / 4 | - |
| Keys | key-f | 5 | 5 | 4 / 4 | - |
| Keys | key-d | 5 | 5 | 4 / 4 | B-07 |
| Keys | key-bb | 5 | 5 | 4 / 4 | - |
| Keys | key-em | 5 | 5 | 4 / 4 | B-07 |
| Keys | key-dm | 5 | 5 | 4 / 4 | B-07 |
| Advanced | scale-two | 5 | 5 | 3 / 3 | - |
| Advanced | sevenths | 4 | 4 | 2 / 2 | - |
| Advanced | arpeggios | 4 | 4 | 2 / 2 | B-07 |
| Advanced | independence | 5 | 5 | 3 / 3 | B-02 |
| Advanced | repertoire | 5 | 5 | 2 / 2 | - |
| Technique | five-c | 3 | 3 | 1 / 1 | B-01 |
| Technique | five-g | 3 | 3 | 1 / 1 | B-01, B-02 |
| Technique | five-f | 3 | 3 | 1 / 1 | B-01 |
| Technique | five-d | 3 | 3 | 1 / 1 | B-01 |
| Technique | five-a | 3 | 3 | 1 / 1 | B-01 |
| Technique | hanon-1 | 3 | 3 | 1 / 1 | B-01 |
| Technique | contrary | 4 | 4 | 2 / 2 | B-01 |
| Repertoire 1 | rep-mary | 6 | 6 | 3 / 3 | B-01 |
| Repertoire 1 | rep-buns | 6 | 6 | 3 / 3 | B-01, B-02, B-07 |
| Repertoire 1 | rep-row | 6 | 6 | 3 / 3 | B-01, B-07 |
| Repertoire 1 | rep-london | 6 | 6 | 3 / 3 | B-01 |
| Repertoire 1 | rep-yankee | 6 | 6 | 3 / 3 | B-01 |
| Repertoire 1 | rep-macdonald | 6 | 6 | 3 / 3 | B-01, B-07 |
| Repertoire 2 | rep-lightly | 6 | 6 | 3 / 3 | B-01 |
| Repertoire 2 | rep-wewish | 6 | 6 | 3 / 3 | B-01, B-07 |
| Repertoire 2 | rep-eine | 6 | 6 | 3 / 3 | B-01 |
| Repertoire 2 | rep-gymno | 6 | 6 | 3 / 3 | B-01 |
| Repertoire 2 | rep-toccata | 6 | 6 | 3 / 3 | B-01, B-07 |
| Repertoire 2 | rep-scarborough | 6 | 6 | 3 / 3 | B-01, B-07 |
| Technique 2 | t2-scales | 4 | 4 | 3 / 3 | B-01, B-02 |
| Technique 2 | t2-broken | 4 | 4 | 2 / 2 | B-01, B-02 |
| Technique 2 | t2-touch | 5 | 5 | 3 / 3 | B-07 |
| Technique 2 | t2-shaping | 4 | 4 | 2 / 2 | - |
| Technique 2 | t2-pedal-progression | 4 | 4 | 2 / 2 | B-01 |
| Technique 2 | t2-sight | 4 | 4 | 3 / 3 | B-07 |

## Theory

27 lessons in 6 units (Keys 4, Reading 5, Rhythm 4, Intervals 4, Scales 5, Chords 5), driven with `--es screen theory --es theory <id>` (Watch) and `--es part quiz`, answers tapped through the accessibility tree. 112 of 112 scenes shown in order and voiced, each scene's line equal to its `say` text (except the 5 lines in the phone's voice, B-02). In every quiz the first question was answered wrongly on purpose, then everything right: the 27 wrong answers were each re-asked exactly once at the end, progress bar and counter right, and the cheer lines ("You got it!", "Spot on!", "Nice work!", "Great job!") never came twice in a row. No lesson stuck in its quiz.

## Ear training

JVM: `QaEarTest` drove every game x level x 25 seeds x 10 rounds with a perfect player (every answer DONE and clean, score 10 of 10), a wrong player (never clean, always explained) and a skipping player (`allSkipped` true): 3,750 rounds, 0 findings. Emulator (`--es screen ear --es game <g> --ei level <n>`): all 15 game-levels played, the right answer read from the `ear: ... round n: (<answer>)` log line, first round of each answered wrongly. Perfect pitch L1 ran its 10 rounds (score 9, streak 9 after the deliberate miss; the XP "Level up!" card appeared). Rhythm tap: one round per level (taps off the beat on purpose; grid, "Not quite" bar and Continue work). One wording bug (B-08).

## Sight-read

Levels 1 and 2 (falling-note pieces, names hidden) by mic injection: 11 of 11 and 13 of 13 groups accepted, 0 wrong, "level 1 done in 16 s, 0 wrong, newBest=true". Levels 3 to 7 (note drill) from a pretend keyboard: 30 right and 2 wrong each (2 deliberate wrong keys); level 7 accepted C#4 for a card shown as D flat. Level 8 (rhythm bars): 14 of 20 notes graded as hits, offsets 23 to 241 ms (my script's count-in guess, not an app fault). Level 3 was played to its 2:00 end: "New best!", "30 right, 2 wrong", Again. JVM: 600 pieces of levels 1 and 2 (16 beats, ends on the tonic, in range) and 600 drills of levels 3 to 8 (one key per card, never the same key twice, bars of 4 beats, in the staff's range): 0 findings.

## Practice tab

Scales (with the Arpeggios chip), Warm-ups and Tools (Metronome, Tuner) open without errors; C major right hand shows fingers 1 2 3 1 2 3 4 5 over C D E F G A B C; the C major arpeggio demo sounded C E G C G E C. JVM `QaPracticeTest`: 2,940 exercises (every scale type x 12 keys x 1 to 2 octaves x 3 hand settings, every arpeggio type, warm-ups in 12 keys at three tempi, 96 routines of 5, 10, 15 and 20 minutes): all parse, stay on the piano, have one finger per note and a title; 0 findings.

## Coach voices (grown-up profile)

| Voice | Course files | Lesson lines with a file | Narrated lines seen in 5 sessions |
|---|---|---|---|
| Liam (male_us) | 2,633 | 436 of 437 | 13, all from `coach/male_us/course` |
| Rory (male_irish) | 2,631 | 436 of 437 | 13 |
| Claire (female_us) | 2,631 | 436 of 437 | 13 |
| Sienna (female_au) | 2,631 | 436 of 437 | 13 |

The 105 lines recorded only as a woman's and a man's take are narrated only when the profile's "for" is set (by design). The audio is the coach's longer wording of each line (it adds a sentence), so a clip is often two to three times the caption; every Read scene waited for it (15 s cap) before moving on. One lesson line has no narration in any voice (B-05); two first-run lines are Liam-only (B-06).

## Kid profiles

Mia (little kid, k1) and Leo (big kid, k2), both with `coachVoice=male_us` planted: 8 launches, 0 `coach:` lines, 0 `narrated` lines, 0 `QA clip play coach/...`; the lines are the pup's recordings (`voice: (recorded)`). Leo, a big kid, walks the grown-up Road (find-c, first-melody, beat, sit) with the pup's voice only.

## Bugs

### B-01 (major) Perform, Ramp and Pedal: every key of a group after the first is flagged WRONG

- Where: Road, every Perform, Ramp or Pedal step whose groups hold more than one note (two hands together, or a chord): 24 lessons, 26 steps. progression step 5; pedal step 3; t2-pedal-progression step 4 (Pedal); five-c, five-g, five-f, five-d, five-a, hanon-1 step 3, contrary step 4 (Ramp); rep-mary, rep-buns, rep-row, rep-london, rep-yankee, rep-macdonald, rep-lightly, rep-wewish, rep-eine, rep-gymno, rep-toccata, rep-scarborough step 5 (Perform, both hands); t2-scales steps 2, 3, 4; t2-broken step 4.
- Repro: `adb -s emulator-5562 shell am start -n com.bigpapituna.sharpears/.MainActivity --es lesson progression --ei step 4 --es midi "6882:48/80,6882:60/80,6882:64/80,6882:67/80,10310:41/80,10310:60/80,10310:65/80,10310:69/80,13739:43/80,13739:59/80,13739:62/80,13739:67/80,17167:48/80,17167:60/80,17167:64/80,17167:67/80"` (four 4-note chords, exactly on time). Same shape with `--es lesson rep-mary --ei step 4` and the Ramp of `five-c --ei step 2`.
- Expected: every key of a chord or two-hand moment counts for its group; 0 wrong keys, no red flash, no pup head tilt.
- Actual: the first key is graded (`input C3 -> CORRECT at 0 ms`); every other key of the same group comes back `input E4 -> WRONG at 0 ms`, with `light wrong`, `wrong key E4` and `pup react HEAD_TILT`. The step still passes (accuracy counts groups), but the run report says `wrongKeys=4 retries=1`; a Ramp logs `ramp: pass clean (wrong=17 accuracy=100)` on each of its 6 passes (85 phantom wrong keys in one Ramp step); a Pedal step logs `wrongKeys=8`; and the spoken feedback blames a perfect player: rep-london says "You missed F twice. Try that bar slowly." (`Report(missed=[(65, 2), (64, 1)] ...)`), rep-wewish "You missed A twice". A real second hand lands a few ms after the first, so every two-hand Perform shows this.
- Evidence: `walk/five-c-2.log` ("input C3 -> CORRECT at 0 ms", "input C4 -> WRONG at 0 ms", "light wrong"), `rep-mary-4.log`, `rep-london-4.log`, `progression-4.log`, `pedal-2.log`, `t2-pedal-progression-3.log`.
- Suspected: `engine/PerformScore.kt` `input()` (a group is graded by its first matching key; the next key finds no ungraded target and returns null) and `engine/Transport.kt` `noteInput()` (Perform branch returns `InputResult.WRONG` when `score.input` is null). A key that belongs to a group already graded within its window should be ignored, not wrong.

### B-02 (major) The recorded voice drops to the phone's TTS, for whole stretches of a session

- Where: any course. Seen in the Road (9 of 70 lessons: first-melody, black-keys, rhythm-eighths, broken, independence, five-g, rep-buns, t2-scales, t2-broken), in Theory (rhythm-beat, ch-triads) and in the coach run (Sienna's "Listen to both hands together."). 13 of 307 launches on the afterdark build (9 of 219 Road launches, 2 of 54 Theory, 1 of 20 coach, 1 of 14 repeats); 0 of 24 launches on a keyless build.
- Repro (intermittent, about 1 launch in 25): `adb -s emulator-5562 shell am start -W -n com.bigpapituna.sharpears/.MainActivity --es lesson black-keys --ei step 0`, force-stop, repeat. The bad launch logs `voice: starting the engine (com.google.android.tts)` and `voice: <text>` without `(recorded)`.
- Expected: every catalog line plays its recording (`voice: (recorded) ...`); all 538 lesson lines have a file in `assets/voice` (checked: 0 missing).
- Actual: black-keys-0 said 5 of 5 lines in the phone's voice, five-g-2 the six "Faster: N percent." lines of the Ramp, first-melody-0 3 of 4. On the instrumented build the log names the cause: `QA recording miss key=3a39eda23616fb86 recorded=596 natural=true plain=false`: **the `recorded` set (`assets.list("voice")`) held 596 names of the 3,975 files in that process**, so every line missing from the short list falls back to Source.SYSTEM; the set is read once (`by lazy`), so the damage lasts until the app is killed. On a keyless build all 24 launches had the full list (small sample). That points at the coach's own `assets.list` on the "voice-lists" thread running at the same moment (`Voice.init` starts both), but it is a guess.
- Also from this: all 7 instruction lines that took over 500 ms to start (1.79 to 2.41 s: rep-buns step 4, t2-scales step 4, rhythm-eighths step 3, t2-broken step 4, broken step 3, independence step 4, five-g step 3) are phone-voice cold starts. Median step-open to clip start for recorded lines: 7 ms.
- Evidence: `walk/black-keys-0.log`, `first-melody-0.log`, `five-g-2.log`, `rep-buns-3.log`; `theory/rhythm-beat-watch.log`, `theory/ch-triads-watch.log`; the QA log line above.
- Suspected: `audio/Voice.kt` (`private val recorded by lazy { runCatching { context.assets.list("voice")... }.getOrDefault(emptySet()) }`, and the "voice-lists" thread in `init`) with `afterdark/CoachHost.kt` (the course folder listing). Cheaper and safer: ask `assets.openFd` for the one key, or check the set's size and list again.

### B-03 (minor) The first spoken feedback line of a launch waits 1.1 to 2.4 s for the phone's TTS engine

- Where: every step end with a run-time feedback line ("Nearly clean: 2 notes needed a second try.", "You missed C four twice. Try that bar slowly.", "A little late: you were 57 milliseconds behind on average."): 105 such lines in the walk, all said by the phone's voice (the Kokoro model is not on the emulator, so on Roy's Fold they may use the model; not testable here).
- Repro: `--es lesson first-melody --ei step 4 --es midi "<perfect play, +40 ms>"`: `voice: starting the engine (com.google.android.tts)` at the step's end, `voice: A little late...` 1.07 s later; fingers-right step 3 (a miss): step ended 01:55:09.989, line spoken 01:55:11.974 (2.0 s).
- Expected: under 500 ms, or the engine warmed in the lesson's first seconds. Actual: `Voice.init` warms only the Kokoro model (`if (enabled && prefs.naturalVoice) model.warmUp()`); the system engine starts on first use.
- Suspected: `audio/Voice.kt` init, `audio/SystemSpeaker.kt` `warmUp()`.

### B-04 (minor) "You missed C three three times": the octave read as a number next to a count

- Where: any miss on a C3 or other low note (bass, broken, fingers-left, lh-octaves, scale-left, rep-buns, rep-lightly, rep-london, rep-macdonald, rep-row, rep-yankee). 13 such lines in the walk, 3 of them "C three three times".
- Repro: `--es lesson bass --ei step 2 with a script that strikes the wrong key before C3 three times`.
- Actual: `voice: You missed C three three times. Try that bar slowly.` and "You missed C three twice." (C4 is "C four"). Expected: leave the octave out of the speech, or say "the C below middle C".
- Suspected: `engine/Feedback.kt` (`spokenNote`, `missedLine`).

### B-05 (minor) One course line has no narration in any coach voice

- Where: Together, step 2 (Listen), "Listen to both hands together." Missing in all four `coach/<voice>/course` folders (key `ac35d6394729a54a`). The line is also a kid line, so the grown-up narration pass (grown-up lines = all lines minus kid lines) skipped it.
- Actual: with a coach on, the lesson narrates its other lines in the coach's voice and this one in the pup's recording (`voice: (recorded) Listen to both hands together.`, seen for Liam, Rory and Claire; Sienna's was in the phone's voice, B-02). Expected: narrated like its neighbours.
- Evidence: `coach/male_us-together-0.log`. Suspected: `scripts/coach_bank.py` (the kid-line subtraction).

### B-06 (minor) Rory, Claire and Sienna lack the two first-run lines Liam has

- Where: Lesson 1 "Sit, then shape your hand" for a Never grown-up: "Listen. This is middle C." and "Your turn. Play middle C." (`FirstRun.FIRST_NOTE_STEPS`). `male_us/course` has both, the other three folders do not (checked by key).
- Actual: the first two steps a new grown-up hears are in the pup's voice with three of the four coaches. Expected: all four narrated. Suspected: the three newer folders were converted from a catalog older than 2.17.

### B-07 (minor) Some voice clips take over half a second to start

- 18 of 614 clip starts logged `prep` over 500 ms, worst 1,027 ms (arpeggios step 4), 971 ms (key-dm step 3), 953 ms (t2-touch step 4); median 83 ms, p95 439 ms. Once a clip also ended 1.4 s after its length (first-melody step 3 by mic: 4,125 ms clip, `QA clip end` 5.5 s after the start). Late, not lost. Mostly the first clip after a screen opens, while the sampler loads.
- Evidence: `walk/arpeggios-3.log`, `key-dm-2.log`, `t2-touch-3.log`, `walk/mic-first-melody-2.log`. Suspected: `audio/ClipPlayer.kt` (MediaPlayer `prepareAsync` per line).

### B-08 (minor) Rhythm tap: "Not quite: 6 of 6 notes on the beat."

- Repro (JVM): `RhythmTapGame(0, 3).grade(pattern.noteTimesMs.map { it + 120 })`: six taps, all 120 ms late.
- Actual: "Not quite: 6 of 6 notes on the beat. You tend to tap about 120 ms late." The round fails (an "ok" note earns 1 of 2 points; 50% is under the 70% pass) while the headline counts every ok note as a hit. Expected: no "6 of 6" beside "Not quite"; count good notes, or say "all 6 a little late".
- Suspected: `engine/ear/RhythmGame.kt` `explain()` (`hits = good + ok`, line 245).

### B-09 (minor) A passed step with misses says "You got it!" over "You missed C4 twice."

- Repro: any wait-mode step played with wrong keys, e.g. beat step 3 (qa-beat-end.png in the run): the bar's title is "You got it!", its line "You missed C4 twice. Try that bar slowly." and a Continue button. Probably intended (the step does pass); flagged because Roy asked about the praise lines.
- Suspected: `ui/LessonScreen.kt` cheer bar, `engine/Feedback.kt`.

### B-10 (minor, debug routes only) Two routes in CLAUDE.md do not do what it says

- `--es screen sight --ei level 1|2 --es midi "<script>"`: the pretend keyboard is never started for the falling-note pieces (`SightReadScreen.kt:109` hands `inject` but not `midiScript` to `PlayerStart`); no `midi: debug script` line is logged. Mic injection works.
- `--es screen practice --es tab arpeggios`: opens the Scales view (the Scales chip is selected); a tap on the Arpeggios chip is needed.

## Repetitive praise

No line twice in a row or within 5 events in any of the 339 logs. The pop lines are rate-limited, so one launch shows 1 to 3 of them. The coach's `start` line repeated across launches: Claire's "Mm, hello again. I was hoping you'd come." in 3 of 5 sessions, Liam's "I've missed hearing you. Let's begin." in 2 of 5 (pool of 10): probably chance, but the pick may be seeded per launch; worth a look in the praise fix. Theory quiz cheers never repeated back to back (155 answers).

## What could not be tested on an emulator

- Real microphone latency, room noise and a real piano's attack (injected synth notes and a pretend MIDI keyboard only; the mic injection's latency=21 to 64 ms is the matcher's, not the hardware's).
- A real USB MIDI keyboard and its sustain pedal (Pedal and Dynamics steps ran on the debug pretend keyboard).
- The Kokoro model's voice for run-time lines (B-03 lines are the phone's TTS here).
- Audio loudness, ducking against the piano, and the coach line over the piano on the Fold's speakers (`-no-audio`; only logs and clip lengths were read; Whisper proves the files say the right words, not that they are audible).
- The Fold's cover display, folding mid-lesson, TalkBack, dark mode and large font scales (an 8-shot layout sheet at font 1.0 showed nothing cut off).
- The kids' 76 Playground lessons (only the first steps of three).
- The Play-store (keyless, asset-pack) build: one 24-launch voice test only.
- Jank, battery, memory pressure (B-02 may depend on it).

## Appendix: re-running the walk

`scripts/qa-audit/` holds the drivers (`lib.py`, `walk.py`, `run_all.py`, `analyze.py`, `summarize.py`, `theory_walk.py`, `ear_walk.py`, `sight_walk2.py`, `coach_walk.py`, `special.py`, `plant.py`, `narr.py`, `whisper_check.py`) and `jvm/` the four JVM harnesses (`QaDumpTest.kt`, `QaEarTest.kt`, `QaPracticeTest.kt`, `QaSightTest.kt`: copy into `app/src/test/java/com/bigpapituna/sharpears/qa/`, run `./gradlew.bat testDebugUnitTest --tests "*Qa*"`; they write `app/build/qa/*`). Order: dump test, install the APK, `python run_all.py` (about 2 h 40 min for the Road), `python summarize.py`. The serial is hard-coded to emulator-5562 in `lib.py`; the scripts expect the QA log lines described above for Listen counts (`analyze.py`), so patch `Stage.noteOn` first.

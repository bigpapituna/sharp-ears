# Sharp Ears 2.0 design audit

Date: 2026-10-14. Build: `main` at 491825c (Version 2.0.0), debug, on emulator-5558 (Fold_API37, Pixel 9 Pro Fold
profile). Inner display 2076x2152 unless a file name says `outer` (cover display, 1080x2424).

Standard: Roy's "I need all aspects of this app to be engaging." Out of scope (being fixed now): lesson Read steps
and the whole Practice tab.

Screenshots are in `out/audit/` (gitignored). File names below are those files without `.png`.

Ranking:
- **P1**: looks bad, or a text page a learner sees often.
- **P2**: fine, but could be alive (motion, pup, illustration).
- **P3**: polish.

Totals: **10 P1, 20 P2, 20 P3**. No crashes (details at the end).

---

## P1, ranked

### 1. Perfect pitch lights the answer while the note plays
Screens: `48-ear-pitch-prompt-light`, `50-ear-pitch-answer-light`; the same pattern shows in `61-ear-degree-dark`.
- **What happens:** while the prompt note sounds, its key glows orange on the keyboard (C4 in the screenshot, and the
  log says the target was C4). The learner can read the answer off the screen, so the game stops being a game.
- **Cause:** `ui/ear/KeysPlay.kt` `playSteps()` puts every sounding note into `glow`, whatever the game.
- **Fix:** in `playSteps`, skip the glow for games where the note is the answer (pitch, degree). Show a listening cue
  instead: a pulse ring around the pup in `GameHeader`, or a sound-wave animation over the keyboard. Melody memory
  level 1 can keep its GIVEN first key.

### 2. Ear training hub is a wall of text and chips
Screens: `40-ear-hub-dark`, `41-ear-hub-2-dark`, `42-ear-hub-2-light`, `60-after-levelup-learn-light`.
- **Works:** the Daily workout card with the pup at the top.
- **Problem:** below it, five games repeat the same block: a heading, a two-line paragraph, three level chips, "Not
  played yet" and a grey Play pill. It is the same chip-wall pattern Roy rejected on Practice > Scales. Nothing
  pictures the game: no icon, no pup, no best score.
- **Fix:** in `ui/ear/EarTrainingScreen.kt`, turn the hub into a two-column grid of game cards. Each card gets:
  - its own pup pose or illustration (headphones for pitch, drum for rhythm);
  - the levels as three tappable stars or dots, filled by best score;
  - a best-score ring;
  - one ChunkyButton.

  Move the how-to paragraph into the game's first round.

### 3. Chords > Play hub is the same text wall
Screens: `62-chords-play-dark`.
- **Problem:** Build it, Hear it, Major or minor and Intervals each get a heading, a paragraph, a chip row, "Not played
  yet" and a Play pill. There is no pup and no picture of a chord.
- **Fix:** use the same game-card grid as item 2 (shared composable), in `ui/ChordsScreen.kt`. Each card shows a mini
  keyboard with the chord lit as its illustration.

### 4. The song recap is a small plain dialog
Screens: `16-player-recap`.
- **Why it matters:** this is the moment after every song run, the most frequent payoff in the app.
- **Problem:** `PlayerScreen.kt` `RecapCard` is a small Material card. A huge "0%" sits beside tiny Good/OK/Missed
  numbers, a 96 dp pup and a grey "Run complete" label. Lessons and theory get a full Celebration with count-up
  stats, rays and an XP bar (`79b-lesson-celebration-settled`, `37-theory-unit-celebration-light`). Songs get far
  less.
- **Fix:** build the recap from `Celebration`'s parts:
  - a pup pose by score;
  - accuracy counting up;
  - stat chips (Good, Missed, Longest streak, Time);
  - +XP and the level bar;
  - confetti only on a new best.

  Keep "Loop bar n" as chips. For a low score, the pup should say a coaching line ("Try it at half speed") rather
  than "No notes hit in time yet."

### 5. Song list rows are bare text
Screens: `08-songs-home-fresh-light`, `09-songs-list-light`, `10-songs-list-end-light`, `92-songs-home-xp-dark`.
- **Problem:** 39 rows of title and composer, with nothing else. There is no cover, no difficulty, no length, no
  best score and no played state, apart from an accent "Work on bar 1". The section headers (Easy 23, Hard 4) are
  the only structure. It reads like a file list.
- **Fix:** in `ui/LibraryScreen.kt` `SongRow`, add:
  - a 48 dp leading tile (a warm colour per difficulty with a note glyph, or the first bars drawn as tiny falling
    notes);
  - one to three stars or the best score on the right;
  - the length in Numeric.

  "Locked" rows get a lock tile. This still follows the no-cards rule: it is a row with a leading image.

### 6. Course path stops are bare numbers
Screens: `23-learn-top-light`, `24-learn-path-1-light`, `25-learn-path-2-light`, `26-learn-path-dark`,
`111-outer-learn`.
- **Problem:** after the START stop, the path is a column of identical grey circles numbered 2, 3, 4 ... 14. Lesson
  names only appear after a tap. The Theory path names every stop (`29-theory-path-light`), so the main course looks
  poorer than the side academy. On the inner display the path is a narrow zig-zag with wide empty margins.
- **Fix:** in `ui/LearnScreen.kt` `StopButton`, put the lesson title under each stop, as `TheoryScreen` does. Give
  each stop an icon for its kind (ear for Listen, hand for Play, metronome for Perform, note for songs). Tint each
  unit's stops with a unit colour. On wide windows, place illustrations or the pup's poses in the side margins.

### 7. Chord games have no pup and no success moment
Screens: `64-chord-game-build-light`, `63-chord-game-build-dark`, `65-chord-game-hear-light`,
`66-chord-game-right-light`, `67-chord-game-solved-light`.
- **Problem:** the ear games put the pup in the header. The chord games have only the text "Build F major", a Score
  and Streak, and a keyboard. Finishing a chord after one wrong note shows the answer "F major: F, A, C." in error
  red. That reads as failure even though the learner just completed the chord. A clean chord gets no flash, burst or
  sound beyond the notes.
- **Fix:** in `ui/ChordGamesScreen.kt`:
  - reuse the ear games' `GameHeader` (the pup with poses);
  - play a green burst across the chord's keys and a pup cheer when a round completes;
  - show the after-the-fact answer in onSurfaceVariant, not error red.

### 8. Progressions ear game is an empty screen with three pills
Screens: `51-ear-prog-prompt-light`, `52-ear-prog-answer-light`, `55-ear-workout-ingame-light`.
- **Problem:** the prompt, then three small Roman-numeral pills ("ii V I, Jazz turnaround"), then one line of
  how-to. The rest of the screen, about three quarters of it, is empty. Nothing moves while four chords play.
- **Fix:** in `ui/ear/KeysPlay.kt`, for CHOICES-only games, add a row of four chord slots that light one by one as
  each chord sounds. Make the answers large cards, with numerals in Nunito and the nickname underneath. After the
  answer, fill the slots with the chord names.

### 9. The "Practice today" interstitial is a nearly empty page
Screens: `94-daily-interstitial-light`, `96-daily-interstitial-2-light`.
- **Problem:**
  - A 120 dp pup and one headline sit left-aligned in the middle of a 2000 px screen.
  - Skip and End for today are small text buttons.
  - There is no Go button. The part opens by itself after the voice, with no countdown, so the learner cannot tell
    what is about to happen or when.
  - The copy "Next: the lesson Sit, then shape your hand" runs the lesson title into the sentence.
- **Fix:** in `ui/Interstitial.kt`:
  - centre the content;
  - show the session as a row of part chips with icons (chords, lesson, loop, song), the done ones ticked;
  - put a countdown ring on a "Go" ChunkyButton;
  - quote the title: `Next lesson: "Sit, then shape your hand"`.

### 10. Chords > Dictionary is a 30-chip wall that pushes the keyboard off a phone screen
Screens: `62-chords-dictionary-dark`, `108-outer-chords-dict`.
- **Problem:** 12 key chips, 15 quality chips and 3 inversion chips sit above the chord. On the cover screen they
  take seven rows, and the keyboard with its finger numbers ends under the nav bar. This is the pattern Roy rejected
  on Scales.
- **Fix:** in `ui/ChordsScreen.kt`, put the chord and keyboard first and pin them. Under them, use:
  - a single horizontally scrolling key strip (or a small circle-of-fifths wheel);
  - a quality strip grouped as Triads, Sevenths and More;
  - inversions as a three-way segmented control.

  The engineer fixing Scales may already be building a shared picker, so coordinate with them.

---

## P2: works, could be alive

1. **Rhythm tap** (`43b-ear-rhythm-listen-light`, `44-ear-rhythm-answer-light`, `45-ear-rhythm-result-light`).
   - Works: a clear bar of rhythm blocks, beat dots, a big count-in number and a warm tap pad.
   - Problem: the blocks are flat dark grey, and the "Wait for your turn" box is a large grey slab. After a miss
     every block gets a red outline.
   - Fix: draw the rhythm with note-head glyphs and beat numbers. Pulse the pad on each beat during the count, and
     plot the learner's taps as dots above the blocks, green on time and orange late or early. Use
     `ui/ear/RhythmPlay.kt`.
2. **Learn header rows** (`23-learn-top-light`, `114-learn-fresh-dark`). Theory, Ear training and Sight-read are three
   flat rows with a 20 dp glyph. Give each a small illustration (staff, headphones pup, score) and a progress ring
   for Ear and Sight-read too (`LearnScreen.kt` `SideRow`).
3. **Practice today panel** on Songs home (`93-songs-home-xp-light`). The three parts are plain grey lines. Prefix
   each with an icon and minutes ("5 chords, 3 min"), and tick parts done today.
4. **Theory quiz answers** (`34-theory-quiz-light`). Four full-width text pills with a single letter left-aligned.
   For note-name questions, use a grid of large letter tiles, or let the learner tap the key on a small keyboard. Play
   the note when an answer is revealed (`ui/theory/Quiz.kt`).
5. **Theory explainer stage** (`33-theory-explainer-staff-light`, `39-theory-explainer-dark`, `110-outer-theory`).
   The panel is tall, but the staff drawing fills only a strip of it, leaving large empty bands above and below.
   Scale the drawing to the panel, or shrink the panel to the scene (`Explainer` `stageRatio`).
6. **Ghost hand** (`12-player-wait`, `13-player-wait-playing`, `70-lesson-play-light`).
   - Works: it reads as a hand and shows the next finger.
   - Problems: it covers the C3 and C4 key labels. In `12-player-wait` the left thumb is drawn tilted across a black
     key. At a stage edge it is cut off (see P2-9).
   - Fix: lower its alpha where it meets the key labels, or lift the labels above it, and clamp the thumb angle
     (`ui/motion/GhostHand.kt`).
7. **"New song. Start with the right hand alone?" banner** (`13-player-wait-playing`, `15-player-perform-running`,
   `105-outer-player`). It stays up during playback and Perform and takes a whole row of the stage. Hide it once Play
   is pressed (`PlayerScreen.kt`).
8. **Tutorial video offline** (`22-player-video-offline`). The panel opens on a black box with no message. Show the
   pup with "The video needs an internet connection" and a Close button (`ui/Tutorial.kt`).
9. **Lesson stage left edge** (`70-lesson-play-light`, `71-lesson-perform-light`, `106-outer-lesson-play`). The range
   starts exactly at C4. The C4 falling note is half cut by the panel's 20 dp corner, and the ghost hand's thumb is
   clipped. Pad the range by one white key on each side (`LessonScreen.kt`, range for the embedded player).
10. **Player on the cover screen** (`105-outer-player`). The player rotates to landscape, which leaves about 180 px
    of lane. Für Elise's opening notes stack into an unreadable cluster ("4 E D# 5 E"). Use a shorter note-label
    mode or fewer octaves when the lane is short.
11. **Chords > Progressions** (`62-chords-progressions-dark`). Four text rows with "Practice" links. Show each
    progression as a row of coloured chord tiles (C, G, Am, F) that light in sequence on tap.
12. **Chords > Daily packs** (`62-chords-daily-dark`). The packs are text rows, and locked packs only say "Opens
    after ...". Give each pack a mini keyboard icon and a progress bar, and show a lock for locked ones.
13. **Chords > Practice and the daily chords part** (`62-chords-practice-dark`, `95-daily-chords-part-light`). A big
    chord name, a keyboard and empty space. Add the pup header from the ear games, and a flash when the chord lands.
14. **Ear game headers** (`46`, `48`, `61`, `107`). The pup is 56 dp and changes pose, but does nothing else. Bounce
    him on a right answer and make the header progress bar glow on a streak. The `Pose` changes are already there to
    build on.
15. **Melody memory prompt** (`46-ear-melody-prompt-light`). While the melody plays, only one key is lit at a time
    and nothing else moves. Add a row of dots, one per note, that fill as it plays and refill as the learner plays it
    back.
16. **Workout start and between screens** (`53-ear-workout-start-light`, `56-ear-workout-between-light`). The three
    games are a grey list. Use three game tiles with icons, the current one raised and done ones ticked with their
    score.
17. **Sight-read** (`102-sightread-light`, `103-sightread-dark`).
    - Works: the dark stage, Level 1 and Level 2, and New piece.
    - Problem: before Play there is one lonely note block and "Press play". There is no pup and no sense of the four
      bars ahead.
    - Fix: show the four bars as a mini staff preview above the stage, and add the pup with "Ready when you are"
      (`SightReadScreen.kt`).
18. **Lesson cheer bar** (`74-lesson-perform-result-light`). The bottom sheet covers the keyboard, the stage's best
    part. Shorten the sheet, or let the stage dim instead of hiding behind it.
19. **Mic setup** (`89-mic-setup-dark`).
    - Problem: "bottom mic, voice recognition" is a debug string. The level reads "-78 dB" and the sensitivity
      "-66 dB", which are jargon for a learner.
    - Fix: show the level as a live bar with "quiet room" and "playing" marks, and the slider as "Hears soft notes"
      to "Ignores room noise".
    - Works: the listening pup and "Heard C4 0 times".
20. **Theory card** on Learn (`23-learn-top-light`). The progress ring is an empty grey circle at 0. Put the unit's
    keyboard icon inside the ring.

---

## P3: polish

1. "START" bubble on both paths (`23-learn-top-light`, `29-theory-path-light`) is all caps, which breaks the
   sentence-case rule. Use "Start".
2. You tab: "Last 12 weeks ... 1 days" (`82-you-xp-3-light`, `81b-you-30days-light`). The plural is wrong.
3. You tab heatmap tiles (`82-you-xp-3-light`): each is about 60 dp on the inner display, so the grid looks blocky.
   Cap the tile size and centre the grid.
4. Level-up overlay XP (`58-levelup-overlay-light`, `59-after-levelup-light`). It counts 156 to 160 XP while "123 XP
   to level 3" stays fixed (the sums disagree mid-count). The You tab then says 190 XP, 93 to level 3. Count both
   numbers together.
5. "ms" in learner copy. The lesson cheer bar says "you were 200 ms early on average"
   (`74-lesson-perform-result-light`, `engine/Feedback.kt`), and the recap says "Timing: n ms late". Say "a little
   early" or "a hair late" instead.
6. Rhythm tap result repeats itself: the title "Not quite" is followed by "Not quite: 0 of 6 notes ..."
   (`45-ear-rhythm-result-light`).
7. Workout with every round skipped: "Good effort!" and +95 XP for 0/20 (`57-ear-workout-end-light`). The pup also
   says "Done, 0 of 8" (`56`). This is the known open item. A pup line like "Skipped this time. Tomorrow?" would be
   more honest.
8. The ear hub's workout card, after the workout, lists different games ("Scale degrees, Progressions, Perfect
   pitch") from the ones just played (`60-after-levelup-learn-light`). Label it "Tomorrow: ...".
9. The theory quiz count changes from 1/5 to 1/6 after a wrong answer (`34`, `35`), with no word why. Add "One more
   to try again".
10. Theory lesson done (debug route) shows "3/5 Score" next to "65% New best" (`36-theory-lesson-done-light`). Check
    whether the two can disagree with real data.
11. The achievement toast covers the screen title ("Chords", `67-chord-game-solved-light`). Its icons switch between
    a glyph and a pup image (`100`, `100b`). Use the pup art for every toast.
12. Scale degrees on the cover screen: "Skip" wraps alone onto its own row, and "sol" wraps alone under do to fa
    (`107-outer-ear-degree`).
13. Songs home greeting: "Hi, I'm your coach. Let's start with an easy song." still shows at 5 of 10 minutes with
    a streak (`92`, `93`). It should move on once the learner has played.
14. Perform count-in: the pup sits hard against the right edge of the stage (`14-player-perform-countin`). Inset
    him by 16 dp.
15. Flag dialog (`19-player-flag-dialog`) is a stock AlertDialog with small radio text. That is fine for a debug
    tool. Restyle it only if it ships.
16. Settings (`85`, `86`, `88`) is long and text-heavy, which is acceptable for settings. The Reset progress
    paragraph is dense.
17. Privacy and Licences (`90`, `91`) are plain text pages. They are rarely seen, so they are fine. Each Licences
    entry has a separate "Show licence text" link; making the whole row tappable would halve the length.
18. A lesson opened from a cold debug launch showed a fully black frame about 3 s in (`68-lesson-listen-light`).
    This is probably the curtain transition while the sampler loads. Check it on the Fold.
19. Onboarding (`01` to `07`) is warm and well made. The system notification prompt lands on top of step 4 when
    Finish is tapped (`06`). Ask for it when the reminder switch is turned on instead.
20. Interstitial and recap copy: "Next: chords" and "Run complete" are terse next to the pup's other lines. Give them
    the pup's voice.

---

## What works well

- The Celebration screens are the app at its best: lesson and new best (`79b`), theory unit (`37`, `38`), Practice
  today done with confetti (`100`) and level-up (`58`). The pup art, rays, count-up chips and the XP bar all land.
- Onboarding (`01` to `07`): pup speech bubbles, the daily-goal picker with a label per choice, and "You're all set".
- Theory explainers (`32`, `33`, `110`): voiced, animated, the pup pointing, with Back, Pause and Replay.
- The You tab (`80`, `109`, `113`): the level ring around the pup, the goal ring, the streak flame, the week dots and
  the achievement grid of pup badges (`84`).
- The player stage: 3D keys, coloured hands, finger numbers on notes, the count-in numbers with the pup, and the hit
  glow (`12` to `15`).
- Dark mode is consistent everywhere checked. There are no wrong-colour surfaces. The accent swaps to the lighter
  terracotta on dark, and the paw-print backdrop stays subtle.

## Crashes and errors

- **No crashes:** `logcat -s AndroidRuntime:E` was empty for the whole session, and there was no FATAL or ANR.
- **Non-fatal error, twice:** `SharpEars: mic: thread failed: java.io.IOException: write failed: EBADF`. It happened
  when a lesson Perform step ended and when Practice today moved to the next part. The recording file appears to be
  closed while the mic thread is still writing (`audio/MicInput.kt`). Nothing broke on screen, but the end of that
  session's recording may be lost.

## What I could not check

- **Lesson Perform pass and the real lesson Celebration.** Injected synth audio scored 22%, and the `--es midi`
  script scored 0%: its events log as "truth" but the matcher in the Perform step ignores them. The cheer bar and
  Celebration were checked through `--es screen motion` instead (`78b`, `79b`).
- **The player recap for a good run and its "Hear my run" button.** I only reached the 0% recap (`16`).
- **Tabletop posture.** `emu posture 2` on this emulator folds along a vertical hinge (book posture), so the player
  did not split (`112-tabletop-player`). The `--ez tabletop true` debug flag was not tried.
- **The metronome tool and the tuner from outside the Practice tab.** The player's Metronome is a toggle chip, and I
  found no non-Practice entry to the tuner.
- **Every inner-display screen in both themes.** Ear games, theory quiz and onboarding are light only. Chords tabs,
  settings, mic setup, privacy and licences are dark only. Both themes were checked on Songs home, Learn, the theory
  path and celebration, the ear hub, scale degrees, the chord game, sight-read and the You tab.
- **Real microphone input and haptics** (emulator).

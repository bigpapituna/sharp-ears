# Sharp Ears 2.1: the family update

Roy's brief (2026-10-18): profiles for the whole family, a kid skin, Pup's Playground, and the loop that makes a
game sticky (a living pup, random rewards, a world that grows) for every profile. Plan: docs/KIDS-PLAN.md (read it
first, all of it). Art: art/mascot/pup-*.jpg cut to res/drawable-nodpi/pup_*.webp (22 poses, see
art/GEMINI-BRIEF.md for what each shows). Quality over speed: Roy said so explicitly. Tests, emulator proof, no
shortcuts in copy or motion.

Delivered by one foundation agent, then five parallel teams in worktrees, then an integration pass. Same rules
as REVAMP.md: each team owns its files exclusively, new preferences go in the team's own store, engine code has
no Android imports and JVM tests, an INTEGRATION.md at the team's package root says what to wire, compile and
tests pass before every commit, commit on the branch, never push.

## Stage 1: Foundation (Opus, high) on branch family-foundation

Owns everything in this stage; nobody else runs. Delivers:

- `engine/family/Profile.kt`: `Profile(id, name, avatar, kind = ADULT|KID, reader, dailyGoalMin, hintSeconds,
  colourNotes, micOffsetDb, createdAt)`, Android-free, with the rules the plan gives (kid defaults: goal 5 or 8,
  hints 4/8/12; adult: 10, 6/12/18).
- `data/Profiles.kt`: the list, the current profile, create/rename/delete/switch, persisted in its own
  SharedPreferences file. Max eight. Deleting asks twice in the UI; the store just deletes.
- Per-profile stores: every file in `ProgressFiles.NAMES` except `mic` becomes `<name>.<profileId>`; the first
  profile keeps the unsuffixed files so Roy's existing progress is his profile. Backup/export/import walk all
  profiles. ProgressStore, ear, practice, theory, mastery, progress stores take the suffix from Profiles.
- `LocalProfile` composition local, and `KidsMode` helpers (`isKid`, `reader`, `hintSeconds`) read from it.
- `ui/family/ProfilePicker.kt`: shown on launch when there is more than one profile; pup avatars (tint the
  sweatband of `pup_wear_base` in eight colours; real avatar art comes later), names, "add someone" which is the
  new-kid flow: name, avatar, "Can they read yet?", go. Adults get the existing onboarding after.
- `ui/family/ParentGate.kt`: hold the pup three seconds, then a sum ("7 plus 5") typed on a number pad. Wraps
  Settings, Microphone setup, profile management, the report token, and "switch to grown-up mode".
- `ui/family/FamilyWall.kt`: a home card with every profile's avatar, this week's paw prints (days practised),
  latest sticker (placeholder slot the World team fills via `FamilyWallEntry.stickerRes`).
- Wiring in MainActivity/HomeShell: picker on launch, the wall on the home screen, the gate where the plan says.
- Tests: profile rules, store round trip per profile, backup includes all profiles, suffix migration keeps
  existing files, parent gate sum logic.

## Stage 2: five teams (parallel, branches family-<team>, worktrees C:\dev\piano-wt\<team>)

| Team | Model, effort | Emulator | Owns (exclusive) |
|---|---|---|---|
| playground | Opus, xhigh | 5554 | engine/Course.kt, engine/Exercises.kt, engine/Transport.kt, engine/LessonScenes.kt, ui/theory/LessonScenes.kt, engine/playground/*, ui/playground/* |
| skin | Opus, high | 5558 | ui/NoteLane.kt, ui/Keyboard.kt, ui/LessonScreen.kt, ui/RunRecap.kt, engine/RunWords.kt, engine/SessionWords.kt, ui/skin/*, engine/skin/* |
| detector | Opus, high | none (JVM, real clips) | engine/NoteMatcher.kt, engine/StallWatch.kt, engine/Struggle.kt, engine/hints/*, ui/hints/*, ui/PlayerScreen.kt, audio/*, data/Reporter.kt |
| play | Opus, high | none (JVM) | ui/play/*, engine/play/*, engine/DailySession.kt, engine/RunRecording.kt, engine/ear/MelodyMemoryGame.kt, data/StuckShare.kt (becomes Share) |
| world | Opus, xhigh | 5556 | ui/Pup.kt, ui/Celebrate.kt, ui/HitLights.kt, ui/motion/*, ui/world/*, engine/world/*, data/WorldStore.kt, res/raw/* (jingles), scripts/gen_stickers.py, res/drawable-nodpi/sticker_* |

Shared files no team edits in stage 2 (the integrator wires them): MainActivity.kt, HomeShell.kt, LearnScreen.kt,
Theme.kt, data/Prefs.kt, data/Profiles.kt, ui/family/*, engine/family/*. Exception: one debug route in
MainActivity's `first` block per team, smallest diff.

What each team delivers (details and copy in docs/KIDS-PLAN.md):

- **playground**: the twelve Playground lessons as a new first unit (kid profiles see it first; adults can open
  it), every READ step as scenes in the existing Scene model, the range exercise ("any key above C4", "any key in
  this black-key group"; an any-of group in Exercise + Transport), the clap counter for lesson 7 (onset only,
  tested with synthetic claps and speech-like noise), soft/loud grading from riseDb for lesson 11 labelled a
  toy, phrase cut points (`Exercise.phraseEnd`) on the reused lessons for the kid bites, and the lower pass mark
  for the concert. Lesson-specific screens (the pup flying with pitch, the black-key hunt) in ui/playground.
  Tests: every lesson parses and plays, scenes render, range exercise and clap counter pass and fail on
  PianoSynth audio. Screenshots of every lesson on 5554.
- **skin**: Boomwhacker colour notes (C red, D orange, E yellow, F green, G teal, A blue, B purple) on the lane
  and keyboard as a toggle (on for kids, off by default for adults, `Profile.colourNotes`), animal names for
  pre-readers, bites in LessonScreen (one scene then "more?", exercise cut at phraseEnd), stars on every recap
  with the accuracy number hidden for kids, kid RunWords, bigger keys for kids, the session goodbye and the paw
  calendar, no red flashes for kids (head tilt instead: expose `onWrong` for the World team's pup). Tests: colour
  mapping, star thresholds, kid words, bite cutting. Screenshots on 5558.
- **detector and skin, together (Roy, 2026-10-18 evening)**: a wrong key lights up on the keyboard and the lane,
  ON BY DEFAULT for every profile (the old default, flashes off because of room noise, flips; the toggle stays).
  Yellow when the wrong key is within two semitones of an expected note (a slip to a neighbour), red when it is
  further. The detector team makes `MicEvent.Wrong` carry the nearest expected midi and the semitone distance,
  and only raises it on a confident strike (fresh attack, clear rise) so talking and TV in the room do not paint
  keys; measure the false-wrong rate on the session recordings (FixtureTest prints "would-be wrong flashes";
  session-155837 must stay low) and tune before flipping the default. The skin team draws it: the key and its
  falling-note column glow yellow or red for about 600 ms with a short shake, kids get the same colours but the
  pup's head tilt instead of any wording like "wrong". Test: distance classification, and a fixture replay
  asserting the wrong count on 155837 does not rise above the current number.
- **detector, room-noise watchdog (Roy, same evening)**: when the mic keeps producing wrong-key events that do
  not look like a piano (no partial structure, no clean attack, or many in a burst while the keys the song
  expects stay silent), count them per minute; past a threshold (start at 6 in a minute, tune on the
  recordings) the pup offers once per session: "Noisy room? I can stop painting wrong keys for now" with two
  ChunkyButtons: "Keep showing" and "Stop for this session" (and, when it happens in listen or wait mode with no
  playing at all for a while, "Pause listening"). Turning it off is per session, never changes the setting.
  Pure Android-free counter in engine/hints with tests; the sheet in ui/hints.
- **detector**: hint escalation (glow, painted hand points, app plays it) with per-profile timings; the octave
  hint from the 2026-10-17 clips (when the strongest rejected candidate is an octave from an expected note for
  two seconds: pup line + arrow overlay in ui/hints); per-profile mic sensitivity offset; a raw-frame tap on
  MicInput for the clap counter (`MicInput.rawFrames: (FloatArray) -> Unit`); kid/adult tag on stuck reports
  (never the name); StallReviewTest and the real stall clips stay green; session-155837 stays 33/33 with 0 false.
  Tests: escalation timing, octave hint on the real clip, offset applied.
- **play**: Jam (every note: colour burst, pup bounce scaled to pitch, chords confetti, glissando rainbow; no
  score), Pup says (Simon, one and two player alternating, turn gating done in the game: events outside your turn
  are ignored and the pup says whose turn), Clap along at three speeds (uses the clap counter from
  engine/playground when merged; until then its own copy in engine/play with the same interface), Share my concert
  (recording + card image through the existing FileProvider), Pup's quest (three daily tasks from DailySession,
  kid and adult variants), Duet (two profiles, one hand each, both credited; a model in engine/play, the recap
  wiring is for the integrator). Tests: Simon logic, turn gating, quest generation, duet credit.
- **world**: the living pup in ui/Pup.kt using the 22 poses (idle after ten seconds: sneeze, tail chase,
  butterfly, scratch, sleep; reactions: surprised, thinking, shh, peek, head tilt, treat; cross-fades and
  springs, reduced motion respected), juice on every note (HitLights, key flash, soft tick), sticker drops with
  three rarities and the floor (never more than three lessons without a rare), the mystery egg (every third
  lesson, hatches on the next), the album with gaps per place, dog house in three stages, garden and concert
  audience built from what was collected, the treat jar (notes fill it, tipping it feeds the pup), the records
  shelf (bronze/silver/gold per song), dress the pup (code-drawn accessories now, art later), the "one more"
  button component shown during the celebration with a preview of the drop, jingles per rarity and star (short,
  generated or synthesized, no copyrighted samples), placeholder stickers (scripts/gen_stickers.py: flat shapes
  with the white border, 30 of them). Tests: drop distribution and floor, egg cycle, album gaps, house stages,
  records thresholds, jar maths. Screenshots on 5556 of the album, world, jar, egg, dress-up.

## Stage 3: integration (Opus, xhigh)

Merge the five branches into `family`, then wire: picker and onboarding for kids, Learn path order per profile
kind with the Playground on the world map, family wall on home, grown-up-mode prompt after Beginner, duet and
take-turns in the player and recap, hints in the player, Jam/Pup says/Clap along/quest entry points, the world
and album in You, parent corner. Render the voice catalog (scripts/render_voice.py, speed 0.85 for kid lines;
about ten minutes). All unit tests, lint, emulator walk-through with screenshots of every new screen on the Fold
emulator (light and dark, kid and adult profile), fix what breaks, update CLAUDE.md and the docs, bump to
versionCode 7 / 2.1.0, release, and install on Roy's Fold over wireless debugging if it is reachable.

## Design rules (unchanged from REVAMP.md)

Warm palette, one accent, the pup's gold only around him; Nunito headlines, Geist body, Geist Mono numbers;
ChunkyButton for the main action; speech bubbles for the pup; reduced motion respected everywhere; no em dashes
in copy; sentence case; nothing text-only, the pup narrates and scenes animate; no timers, energy limits,
lose-your-streak or nags anywhere.

# Pup's Playground (2.1, stage 2 playground team): what the integrator and the other teams need

The twelve Playground lessons are the first unit of the course, written as games for small children. Engine pieces
(Android-free, JVM-tested) live in `engine/playground`; the stages the embedded player cannot show live here in
`ui/playground`. Plan: docs/KIDS-PLAN.md section 4.

## The unit

- `engine/playground/Playground.kt`: `PLAYGROUND`, a `CourseLevel` with id `"playground"` (`PLAYGROUND_ID`), title
  "Pup's Playground", lessons `playground-1` .. `playground-12`. It is **first in `COURSE`**. The type is
  `CourseLevel` (Course.kt has no `CourseUnit`; the family foundation's INTEGRATION.md calls it a unit).
- `Profile.firstUnitId` is `"playground"` for kids. Open a new kid on `PLAYGROUND.lessons.first()`
  (`Screen.Lesson(PLAYGROUND.lessons.first(), Screen.Home(HomeTab.LEARN))`); MainActivity opens `Screen.Home()` today.
- **Grown-ups**: the Playground being first in `COURSE` also puts it first in `ALL` (LearnScreen), so today
  `ALL.firstOrNull { !done }` (Learn's next lesson, the onboarding's suggestion, Practice today's lesson part) picks
  `playground-1` for Roy. Order the path by `firstUnitId`: for a grown-up, the next lesson should skip the playground
  unit (or show it after Technique 2 as an optional row); `ProgressStore` counts its lessons and the unit in
  `lessonsTotal` and `unitsComplete` (fine, or filter by kind if achievements should not need it).
- Places for the map and album pages: `PLAYGROUND_PLACES` (lesson id to `Place.GARDEN | POND | CONCERT_HALL`, four
  each, in order). The plan's short path for big kids: `BIG_KID_LESSONS` (2, 3, 6, 7, 12, then Basics).
- Lesson lengths are checked by a time model in PlaygroundTest (about 105 to 140 s of a child's time each by that
  model; in the room, with tries, the cheer bar and slower speech, two to three minutes).

## The games: Step.game and the stage registry

`Step.game: Game?` (new, default null) marks the steps that draw their own stage. Each still has an ordinary kind and
exercise, so the plain embedded player can run it until this is wired (it just looks plain).

| Lesson | Step | Game | What it shows |
|---|---|---|---|
| 1 High and low | 2, 3, 4 | `HIGH_LOW` | the pup flies with pitch over a dusk garden; star up high or cushion below; half the keyboard glows |
| 2 Two black, three black | 2, 3, 4 | `KEY_HUNT` | big keyboard; dog houses get a house, fences a picket fence, as found; the pup points |
| 3 Where C lives | 2 | `KEY_HUNT` | the same, Cs get a badge |
| 7 The steady drum | 2, 3 | `CLAP` | the pup drums (count-in, then the beats); claps fill paw prints; a tap pad stands in |
| 11 Soft and loud | 2 | `SOFT_LOUD` | the pup naps under the moon, jumps awake on loud; a meter shows the guess |
| 12 Pup's concert | 2, 3 | `CONCERT` | pick one of three songs, curtains, the embedded player in Perform, fireworks at the pass mark |

**Wire it in LessonScreen** (the skin team's file), in the exercise branch, where the embedded `PlayerScreen` is:

```kotlin
import com.bigpapituna.sharpears.ui.playground.PlaygroundStage
import com.bigpapituna.sharpears.ui.playground.hasPlaygroundStage

// after the pup's PupSays(step.text) line, inside the Box(Modifier.weight(1f)...):
key(index, run) {
    if (hasPlaygroundStage(step)) PlaygroundStage(step, lesson.id, deps,
        inject = inject?.takeIf { index == startStep && run == 0 },
        onEnded = { exerciseEnded(step, it) },
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).fillMaxHeight())
    else SharpEarsTheme(dark = true) { PlayerScreen(...) }   // as today
}
```

`PlaygroundStage` draws the one raised dark panel itself (rounded, `Stage` colour), runs its own microphone, input and
sound, and calls `onEnded` once with a `Report`, exactly as `PlayerScreen.onFinished` does, so `exerciseEnded`,
`Step.passed`, the cheer bar and "Try again" (`run++`) all work unchanged. `ui/playground/PlaygroundPreview.kt`
(`PlaygroundLessonPreview`) is this wiring in a cut-down LessonScreen: copy from it.

What each game reports: `HIGH_LOW`, `KEY_HUNT`, `SOFT_LOUD` are wait-mode runs of the step's exercise
(`transport.report()`: groups, wrong keys; a key played before the song reaches the next round skips ahead to it, since
the time between rounds is only pacing); `CLAP` reports `groups` = beats with a clap on them and `extraScore` = that
as a share of the beats (a PLAY step, so it always passes: "no fails" on a toy); `CONCERT` reports the Perform run of
the chosen song (`recap`), graded against `CONCERT_PASS`.

## Engine additions other teams use

- **Any-of groups** (engine/Course.kt, Song.kt, Transport.kt): exercise notation `"C#4|D#4:2"` (either key) and
  `"C6|C#4..C8:2"` (any key in the range; the first listed is the demonstration key). `OnsetGroup.anyOf` marks them:
  one key completes the group in wait mode and Perform, the keys count even off the keyboard on screen, only the first
  ever sounds by itself. Chords and every existing song and lesson are unchanged (tested). `Song.transposed` keeps them.
  Transport adds `listenGroups` (each alternative alone), `nextGroup`, `hintKeys` (the demonstration key only, for an
  any-of group) and caps the mic set of a big range at `MIC_ANY_OF_LIMIT` = 12 spread keys (`micKeys`).
- **Phrase ends**: `Exercise.phraseEnd` (beats) and `Exercise.bite()` (cuts both hands there, fingers too). Set on
  Hot Cross Buns and Mary (bar 2), Twinkle and Ode to Joy (bar 2), "fingers-right" (up to G, beat 5), "beat"
  (C-G, bar 2), "scale" (the way up, bar 2), and the Playground's Up to G. **Skin team**: in kid mode play
  `step.exercise.bite()` instead of the exercise (it returns the same exercise when there is no phrase end).
- **Concert pass mark**: `CONCERT_PASS = 60` (Perform accuracy) on lesson 12's songs and lesson 7's "C on every boom".
  It stands for two stars; if the skin team's two-star rule is another number, set `CONCERT_PASS` to it.
- **ClapCounter** (engine/playground/ClapCounter.kt): `process(chunk: FloatArray, count): Int` (claps confirmed in the
  chunk, about 110 ms after each clap), `listener`, `total`, `reset()`, `trace` (debug reasons). 48 kHz raw PCM, any
  chunk size, one thread. **Play team**: Clap along can use it as is (it ignores the drum from `drumSamples()`, the
  metronome click, piano and talking; tested). `ClapSynth.render` makes test claps.
- **PitchHeight** (engine/playground/PitchHeight.kt): which note was struck, from raw PCM with no expected keys (an
  attack, then YIN): `process(chunk, count): List<Heard>` (`Heard.midi` fractional, `.key`), within a semitone on
  PianoSynth C1..A7, cheap (work only on a strike). `pupHeight(midi)` maps a note to 0..1. **Play team**: the Jam's
  "pup bounce scaled to pitch" can use it.
- **SoftLoud** (engine/playground/SoftLoud.kt): `SoftLoud.grade(riseDb, anchorDb?, offsetDb)` and `SoftLoudGame`.

## The microphone (rewritten in 2.1.1)

- Who hears what: `HIGH_LOW`, `KEY_HUNT` and `SOFT_LOUD` run through `rememberStage` (PlaygroundKit.kt): the note
  matcher listens for the stage's keys (`engine/playground/StageEar.kt` StageKeys, each its own group, at most 12)
  with `NoteMatcher.strictKeys` on, its wrong-key search names keys outside the set, and `PitchHeight` (on
  `MicInput.rawFrames`) only adds a guess when it is sure (clarity 0.9; for high and low only 2.5 semitones or more
  from middle C). A `StageEar` fuses them into one sound per moment; `HighLowJudge`/`HuntJudge` (or the soft-loud
  game) say what the round makes of it. `CLAP` reads the raw audio through `ClapCounter`; `CONCERT` runs the
  embedded player.
- The mic starts once the voice has read the step's instruction and then stays on: call-outs and demos are said over
  it (`Voice.speakOverMic`, the mic deaf while the line plays and 300 ms after), the phone's demo keys are played
  with `MicInput.deafFor`. The stages' own sounds (`WorldSounds.stagePop`, `ding`) have no pitch.
- Every round: a demo first (`RoundCoach`: "Let me show you" and "Your turn!" the first time, the keys ringing gold),
  then listening; a miss or a 7 s quiet stretch is a try, three let the round go with a small star. The plain player
  does the same for Playground lessons (`PlayerScreen(letGo = true)`, 9 s quiet stretches, no demo: the hints show
  the key) and LessonScreen lets a Perform step go after its third failed try (`StepRetry`).
- **Octave hint**: off for any-of groups, as before. **Hint escalation** plays `transport.hintKeys`.
- The matcher findings that sent the hunts to the pitch tracker in 2.1 (C3 credited for a G4, C6 for a D4, A#5 for a
  C#4, A1 as C#4 and more) are what strict keys fix: on PianoSynth, all 88 keys at two levels against every stage set
  and the Jam's keys, 104 cases credited a key that was not struck before and none now, and Roy's scale (session-155837) stays 33/33 with no false strikes.
- Stuck moments: a stage with sounds and no key recognised for 8 s keeps a clip (`StageClipWatch`,
  `MicInput.saveStall`, report `stage=` / `lesson=` / `profile=kid|adult`).
- Debug: `--ef fxSlow n` stretches the rings and call-outs n times for screenshots.

## Voice, art and copy

- Every scene caption and step line is in `COURSE`, so `SpokenCatalog` already lists them (render them at speed 0.85
  for kid lines). The stages' short call-outs ("Up I go!", "Find a fence!") are shown, not spoken: the microphone is
  listening while they show, and the voice waits for it.
- Painted poses not in `Pose` yet (flying, standing, sleeping, goodnight, surprised, drumming, clapping, pointing,
  head tilt, dancing, crown, great job, listening) are drawn with `Painted`/`PaintedPup` in `PlaygroundKit.kt`, which
  give them the pup's glow, shadow and a hop. **World team**: when `Pose` has them all, `PaintedPup` can call `Pup`.
- Lesson 4 says the steps have names (C, D, E); the scene keys are drawn by ui/theory/Scenes.kt in the accent colour.
  For colour notes on the scenes too, keysScene would take the skin's colour for each lit key (integrator).
- Copy rules held by tests: second person, sentence case, no dashes, no percentages, nothing called wrong or a
  mistake, one to three scenes per read, short sentences.

## Debug

`--es screen playground [--es lesson playground-N] [--ei step n] [--ei scene n] [--es inject ...] [--es profile id]`
opens `PlaygroundLessonPreview` over the app (MainActivity's one debug line). Injections: MicInput's own
(`synth:84,36@1500~2000`, a WAV path), plus `claps:400,1150,1900` and `dyn:60/-20,60/-30,60/-8@1600~1500`
(note/gain dB, step ms, first ms), rendered to a WAV in the cache. Log lines start with `playground:` (tag SharpEars).
Since 2.1.1 the preview route is gone: `--es lesson <id> [--ei step n] [--es profile id]` opens LessonScreen itself
(MainActivity), for every page (`--es lesson pond-3 --ei step 1 --es profile p1`).

## 2.2: the pond and the concert hall (branch `pages`)

Two more pages of twelve lessons after the garden, then the big kids' road (Basics). Same model, same stage kit, same
rules (demo first, three tries and it goes by, every sound answered, strict keys, at most 12 keys a stage).

### The contract (engine/playground/Pages.kt)

- `PLAYGROUND_PAGES` = garden (`PLAYGROUND_ID` "playground", `playground-1..12`), pond (`POND_ID` "playground-pond",
  `Place.POND`, "The pond", `pond-1..12`), concert hall (`HALL_ID` "playground-hall", `Place.CONCERT_HALL`, "The concert
  hall", `hall-1..12`). Also `PLAYGROUND_LEVELS`, `pageOf(lessonId)`, `isPlaygroundLesson(id)`, `isPlaygroundLevel(levelId)`.
- `COURSE` = garden, pond, hall, Basics, ... `PLAYGROUND_PLACES` has 36 entries. The last Playground lesson is `hall-12`
  (`PLAYGROUND_PAGES.last().lastLessonId`).
- `CoursePath`: a kid walks the garden, the pond, the hall, then Basics (`after(hall-12)` is "sit"); a big kid (reader)
  still gets the garden's short list (`BIG_KID_LESSONS`), then walks the pond and the hall whole (they are written for
  readers too: letters, songs with words); a grown-up has all three pages as extra units after Technique 2, never
  suggested (`isExtra` is `isPlaygroundLevel`).
- `StepRetry.isPlayground` is `isPlaygroundLesson` (three tries let a Perform step go on every page);
  `ANY_OF_LESSONS` = playground-1, 2, 3 and hall-1 (the octave hint stays off there).

### The lessons and their games

| Lesson | Game | What it shows |
|---|---|---|
| pond-1 Twinkle | player | the first phrase (the bite), then the A line |
| pond-2 Row, Row, Row Your Boat | player | rep-row's phrase cut after "row your boat", then "gently down the stream" |
| pond-3 Copy the frogs | `ECHO` x2 | frogs on lily pads sing four notes, each in its colour; you play them back; then two you know |
| pond-4 Drum on a lily pad | `CLAP` at 60 and 90, Perform | the pup drums on a lily pad |
| pond-5 Ta and ti-ti | `CLAP` rhythm, Perform | ta and ti words under the paw prints (the Rhythm unit's eighths bar) |
| pond-6 Old MacDonald | player | rep-macdonald's phrase, then E, I, E, I, O |
| pond-7 Left paw walks | player | the garden's steps an octave down in the left hand, then fingers-left |
| pond-8 Both paws | player | low C held under C D E, then the "together" Twinkle cut to two bars |
| pond-9 Your first chord | `CHORD` | C, E and G glow with their badges; all three together and the pup settles on them |
| pond-10 London Bridge | player | rep-london's phrase, then the whole line |
| pond-11 Twinkle all the way | player, Perform | A B A at 70 with the band |
| pond-12 Pond concert | `CONCERT` x2 | pick Twinkle, Row or Old MacDonald; frogs and ducks watch and cheer |
| hall-1 Letters on the keys | `KEY_HUNT` x3 | every D, then E and F, then the one called; badges with the letter or the animal |
| hall-2, 3, 4 Ode to Joy, Lightly Row, Yankee Doodle | player | first phrase, then the whole line |
| hall-5 Both hands, five fingers | player | Technique's five-c at 60, up and home once |
| hall-6 Soft song, loud song | `SOFT_LOUD_TUNE` | Ode's phrase the usual way, softly, loudly, softly; graded per phrase, the pup naps or wakes |
| hall-7 With the band | player, Perform | Ode with the band at 80, two stars |
| hall-8 Ta-a and the rest | `CLAP` rhythm, Perform x2 | ta-a and shh (the Rhythm unit's halves bar), then as a tune |
| hall-9 Five notes to copy | `ECHO` x2 | spotlights on the stage, five notes, two rounds, then two you know |
| hall-10 Hands together | player | Ode's phrase over C3, then G3 |
| hall-11 Pick your three | `SET_LIST` | the shelf of nine songs; pick three, warm up on each one's start (wait mode) |
| hall-12 The recital | `CONCERT` x3 | the three picked songs, one a step, the collected animals in the audience |

New in the registry (`PlaygroundStage`): `ECHO` (EchoStage.kt), `CHORD` (ChordStage.kt), `SOFT_LOUD_TUNE`
(TuneStage.kt), `SET_LIST` (SetListStage.kt). Grown: `CLAP` reads a rhythm (a clap counts on the nearest note, words under
the paws; pond and hall backdrops), `KEY_HUNT` hunts letters (on the 2.2 pages each round's letter is said first,
`PageLines.find`), `CONCERT` follows `concertPlan(lessonId, step, recital)`. Backdrops and the audience are in Scenery.kt
(the audience uses the world's `StickerImage` and `Scenery.audience(owned)` from `LocalWorld`, read only). The recital's
three songs are kept per profile in the "play" file under "recital" (`data/RecitalStore.kt`); with none picked,
`Recital.DEFAULT` (Twinkle, Old MacDonald, Ode to Joy).

### How they hear (engine)

- `StageRounds(phrases = true)`: a round is a phrase (the notes between two rests); the pup shows it whole, each note
  heard is progress, three tries let the rest of it go. A found chord plays all its keys in.
- `StageKeys.FIVE` (C4 to G4) for the copy games and the tune, `StageKeys.letter(pc)` (octaves 2 to 6) for the letter
  hunts, `StageKeys.C_CHORD` as ONE matcher group for the chord, with `StageEar(chords = true)` so each key of it comes
  through. `NoteMatcher.strictKeys` no longer rejects a key for a lower note of its own group (G4's 2nd partial is C4's
  3rd): without that, a C E G struck within 5 ms gave only C and E on PianoSynth. Single-key groups (every stage before,
  the Jam) are unchanged.
- Judges: `NoteJudge` (the next note, any octave), `ChordJudge` (all three within 1.2 s; a key outside the chord on its
  own is a try), `TuneLoudness` (each phrase's middle rise graded with `SoftLoud.grade` against the first phrase),
  `HuntJudge` (letters: `MissWhy.NOT_LETTER`, `PageLines.where`).
- Tests: StageHearingTest (every key at two levels against the C position; the chord rolled 0, 40 and 150 ms at two
  levels found, no single key and no other chord ever found; the letter hunts in the hunts' loop; Roy's scale through all
  of it), StageSoundsTest (the new sets, the chord as one group), PlaygroundFlowTest (all 36 lessons, a silent kid and a
  wrong-keys kid), PagesTest (contract, copy, songs reused note for note, games, concerts, time model), PhraseGamesTest.

### Octave hint: any-of ranges used by the stages

The garden's ranges (high and low, dog houses, fences, Cs) and, 2.2, the letter hunts' `D4|D2|D3|D5|D6`,
`E4|E2|E3|E5|E6`, `F4|F2|F3|F5|F6`, `G4|G2|G3|G5|G6` and `C4|C2|C3|C5|C6` (hall-1). The copy games, the tune and the
chord use plain notes (no any-of); `ANY_OF_LESSONS` lists hall-1.

### What the integrator checks after merging with the kid-home branch

Done in 2.2.0 (branch `release-2.2`, CLAUDE.md "2.2.0 status"): all six hold; `Place.ofPlaygroundLesson` was removed and
every lookup goes through the lesson's page (KidPages.dropPlace over `pageOf`). The list stays as the record of what
was checked.

1. The map's anchors per page: a stone for every id of `PLAYGROUND_PAGES[1].lessonIds` (pond) and `[2]` (hall), in
   order, with their stars. The world's `Place.ofPlaygroundLesson(n)` still splits the garden's twelve into three places
   (4 each), which is wrong now: each page is its own place (`pageOf(id).place`: GARDEN, POND, CONCERT_HALL; the world's
   `Place` is GARDEN, POND, CONCERT).
2. The finale after `hall-12` (`PLAYGROUND_PAGES.last().lastLessonId`) sends the kid to Basics ("sit").
3. MainActivity's `lessonDone` and LessonScreen's "one more" preview map a lesson to the world's place by its
   "playground-N" number: pond and hall lessons get null. Map them by `pageOf(id)?.place` instead.
4. Learn for grown-ups: the three pages are "Extra" units after Technique 2; LearnScreen's mapped kid view checks
   `level.id == PLAYGROUND_ID` for the drawn world and splits the garden over `Place.entries`: with three pages it should
   draw one scene per page.
5. Voice: this branch records 239 new lines (2577 in the catalog). After the merge, run SpokenCatalogTest and
   scripts/render_voice.py on the merged tree (it deletes recordings that are not in the list, so never on one branch).
6. A kid who finished the garden before 2.2 should land on pond-1 through `CoursePath.next`, not on Basics.

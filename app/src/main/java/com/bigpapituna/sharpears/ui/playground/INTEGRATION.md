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

## The microphone: wiring and notes for the detector team

- The stages read raw audio through `MicInput.tap` (it exists today and is what the tuner uses) and set it back to
  null when they stop. When the detector team's `MicInput.rawFrames` lands, switch `rememberListening` in
  `PlaygroundKit.kt` to it (one line): `mic.rawFrames = raw` instead of `mic.tap = raw`.
- Who hears what: `HIGH_LOW` and `KEY_HUNT` listen through `PitchHeight` only (the matcher's expected set stays empty);
  `CLAP` through `ClapCounter` only; `SOFT_LOUD` through the note matcher, expecting middle C (it needs `riseDb`);
  `CONCERT` through the embedded player as usual. All of them wait for `voice.holdsMic`, like the player.
- **PlayerScreen** (detector team): where any-of steps run in the plain player, set
  `mic.matcher.groups = transport.listenGroups` instead of `mic.matcher.group = transport.listenGroup` (line ~542).
  As one group, the alternatives' shared overtones are set aside and octave inference credits the wrong ones.
- **Octave hint**: ignore it for any-of groups (`transport.nextGroup?.anyOf == true`); they span octaves on purpose.
  Lessons with any-of ranges: playground-1 (steps 2 to 4, half the piano each side of middle C), playground-2
  (steps 2 to 4, the dog houses and fences in octaves 3 to 5), playground-3 (step 2, C2 to C6).
- **Hint escalation**: "the app plays it" should play `transport.hintKeys` (one key for an any-of group).
- Findings on the matcher, from RangeAudioTest's PianoSynth runs (not changed here, NoteMatcher is the detector
  team's): asked about several keys at once (each its own group) it credits C3 when G4 is struck (G4 is C3's third
  partial: the "higher octave" check covers the second partial only), C6 for a D4, and A#5 for a C#4. That is why the
  hunts listen through the pitch tracker. The same may hit single expected notes (expecting C3, a G4 counts): worth a
  test in NoteMatcherTest. Its cost grows with every expected key: 36 keys took half a desktop core on the JVM.

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

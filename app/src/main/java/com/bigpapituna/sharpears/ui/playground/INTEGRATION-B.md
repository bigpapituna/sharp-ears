# 2.3 part B: the treehouse and the night sky (branch `k40b`)

Twenty-four new kid lessons on two Playground pages: the treehouse (fingers that move) and the night sky (rhythm and
listening). Part A (branch `k40a`) owns the places and the world: `Place.TREEHOUSE` and `Place.NIGHT_SKY`, their
scenes, trails, stickers, kid map, finale lines, and the Workshop and Fair pages. This branch adds no `Place` and no
`Game` value and touches none of part A's files, so the two pages are wired up at the merge, here.

## The two levels

| Constant | Value | Level | File | Lessons |
|---|---|---|---|---|
| `TREEHOUSE_ID` | `"playground-treehouse"` | `TREEHOUSE` ("Pup's Treehouse") | engine/playground/Treehouse.kt | `treehouse-1` .. `treehouse-12` |
| `NIGHT_SKY_ID` | `"playground-night"` | `NIGHT_SKY` ("Pup's Night Sky") | engine/playground/NightSky.kt | `night-1` .. `night-12` |

Treehouse: 1 Finger walk, 2 Left hand walk, 3 Thumb tuck, 4 Pinky push, 5 Skips, 6 Five fingers in G, 7 Five fingers
in F, 8 Same tune, other hand, 9 Copy my fingers, 10 Left hand echo, 11 Which finger?, 12 Treehouse concert.
Night sky: 1 Steady beat, 2 Ta, ti-ti, ta-a, 3 Shh, a rest, 4 The metronome game, 5 Up or down, 6 Same or different,
7 Loud or quiet, 8 Fast or slow, 9 Long or short, 10 Happy or sad, 11 Black key tunes, 12 Night sky concert.

## What to do at the merge

1. **Pages** (engine/playground/Pages.kt): insert these two entries after the workshop page, in this order:

   ```kotlin
   PlaygroundPage("treehouse", TREEHOUSE_ID, Place.TREEHOUSE, "The treehouse", TREEHOUSE.lessons.map { it.id }),
   PlaygroundPage("night", NIGHT_SKY_ID, Place.NIGHT_SKY, "The night sky", NIGHT_SKY.lessons.map { it.id }),
   ```

   and add `TREEHOUSE, NIGHT_SKY` to `PLAYGROUND_LEVELS` at the same place. (`PLAYGROUND_LEVELS` is a list of its own,
   not derived from the pages: both need the two levels.)
2. **COURSE** (engine/Course.kt): this branch appends `TREEHOUSE, NIGHT_SKY` at the very end of `COURSE` (after
   Technique 2) only so `--es lesson <id>` opens them. Move them to their pages' place among the Playground levels
   (after the workshop's level), and drop the comment.
3. **Tests that name the course's shape**, edited here for the end-of-COURSE placement: `CourseTest.everyLessonIsUsable`
   (130 lessons, the two ids at the end: put them in page order, and add part A's) and `AnyOfTest.aChordStillNeedsEveryNote`
   (excludes the two ids from the "no any-of outside the Playground" check: once they are Playground levels the
   exclusion can go). Part A's own count changes (PagesTest, CoursePathTest, PlaygroundFlowTest's `36`, KidMapTest)
   will need these two pages too.
4. Nothing else is needed for these to switch on once the pages list them: `isPlaygroundLesson` (and so
   `StepRetry`'s let go after three tries for Perform steps and the concerts, PlayerScreen's `letGo` for wait mode,
   CourseScenesTest's one-to-three scenes, CourseTest's two-to-seven steps), `ANY_OF_LESSONS`, `CoursePath`, the kid
   map's stones and the finale (part A), the page's sticker drops (part A). On this branch they are off for these 24
   lessons: a Perform step here offers "Try again" without the third-try let go, the plain player waits without it.
   TreeSkyFlowTest walks every lesson as it will run merged.

## The stages (ui/playground) and the registry edit

The new stages are drawn for steps marked in `engine/playground/StageGames.kt` (`StageGames.of(step)`, by step text,
unique in the course; `StageGame` says which stage and its data). The `Game` enum is part A's file, so it is not
extended; the registry looks the marks up first. **The only edit to an existing UI file** is PlaygroundStage.kt
(three lines): `hasPlaygroundStage` is also true for a marked step, and `PlaygroundStage` calls
`TreeSkyStage(game, ...)` (TreeSkyStages.kt) for one before its `when (step.game)`. If the integrator prefers `Game`
values, each `StageGame` maps to one stage below; nothing else reads the marks.

| StageGame | Stage file | Used in |
|---|---|---|
| `Copy(TREEHOUSE)` / `Copy(TREEHOUSE, byEar = true)` | CopyStage.kt | treehouse 1 to 10 (a copy game each; 9 and 10 two, the second by ear) |
| `Copy(NIGHT)` | CopyStage.kt (stars sing the tune) | night-6 |
| `Tap(FINGER, ...)` | TapStage.kt (painted hand, five numbers, then the key on the mic) | treehouse-11 (right hand, then left) |
| `Tap(UP_DOWN / SAME_DIFFERENT / LOUDER / FAST_SLOW / LONG_SHORT / HAPPY_SAD)` | TapStage.kt (no mic) | night 5 to 10 |
| `Beat(DRUM)` / `Beat(CLICK)` | BeatStarsStage.kt | night-1 (three speeds), night-4 (three) |
| `NightClap` (the step keeps `Game.CLAP`) | ClapStage under SkyBackdrop (TreeSkyStages.kt) | night-1, 2, 3 |
| `HigherLower` | HigherLowerStage.kt | night-5 |
| `LoudQuiet` | LoudQuietStage.kt | night-7 |
| `Tempo` | TempoStage.kt | night-8 |
| `SkyChord` | SkyChordStage.kt | night-10 |
| `Jam` | StarJamStage.kt | night-11 (two) |
| `Concert(TREEHOUSE / NIGHT)` | TreeSkyConcertStage.kt | treehouse-12, night-12 (two each) |

Every marked step still has an ordinary kind and exercise, so a screen without the stage plays it in the player.
Only the clapping steps carry a `Game` (`Game.CLAP`, so LessonScreen's pup claps above them); all others have
`game == null`, so PlaygroundFlowTest (merged) walks them as plain wait or Perform steps and PlaygroundChecks'
time model counts them as such (for these two pages use TreeSkyTime, test sources, which times each stage as it runs:
114 to 180 s a lesson). The concerts do not use `concertPlan` (it would give the garden's songs): each concert
step plays its own exercise.

**ClapStage and the new places**: `NightClap` draws the night sky and lays ClapStage over it. ClapStage draws its own
backdrop only for `Place.POND` and `Place.CONCERT_HALL` (`pageOf(lessonId)?.place`); when part A adds the new places to
its `when`, keep `Place.NIGHT_SKY` drawing nothing there (or the night sky), or the stars are covered.

**Backdrops**: the treehouse (leaves, trunk, lanterns, rope ladder, planks) and the night sky (`night()` from
SoftLoudStage.kt plus constellations and a shooting star) are drawn in TreeSkyStages.kt (`TreeBackdrop`,
`SkyBackdrop`), not taken from the world's scenes. Swap them for part A's scene art if it should match the map.

## Any-of lessons (the octave-hint skip list)

`night-5` (higher or lower: any key above or below the pup's note) and `night-11` (any black key). `ANY_OF_LESSONS`
finds them by itself once `PLAYGROUND_LEVELS` holds the two levels (TreeSkyTest.anyOfLessonsAreListed checks the pair).

## Lines and voice

- engine/playground/TreeSkyLines.kt: the stages' lines (finger calls, tap questions and answers, beat, higher or lower,
  loud and quiet, fast and slow, the chord, the jam, the concerts). `TreeSkyLines.fixed()` is every fixed line of the
  two pages (their steps and scenes as LessonScreen says them, and those lines), registered with one line in
  `SpokenCatalog.kidLines()`, so they are recorded at the kids' speed (0.85). After the merge `PLAYGROUND_LEVELS` lists
  the steps again; `distinct()` keeps one of each.
- Recorded here with scripts/render_voice.py: 336 new lines (314 at the kids' speed), 2954 in the catalog; it deleted
  nothing (the catalog here is the 2.2.1 catalog plus these lines). After merging with part A, run SpokenCatalogTest and
  render_voice.py once on the merged tree.

## Debug

`--es lesson treehouse-N|night-N [--ei step n] --es profile p1 --ez awake true` (any lesson, any step, as a kid; the
stages log `playground:` lines). Screenshots of every lesson's main stage: app/build/screens/k40b-*.png (not committed).

## Not proven

Like the other pages, nothing here has met a real piano or a child. The beat stars take the matcher's own delay off a
strike (`MicEvent.Strike.delayMs`) and allow a quarter second either way; the fast and slow game grades the median gap
between notes against your first phrase (a note the matcher misses makes a phrase look slower); the jam's band is
kept out of the microphone by a 300 ms deaf moment at each of its notes (tested on PianoSynth only).

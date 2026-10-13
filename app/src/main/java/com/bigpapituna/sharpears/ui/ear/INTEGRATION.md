# Ear training: what the integration pass wires

Team D (branch `revamp/ear`). Everything here is new files; no shared file was touched.

## Files

| File | What |
|---|---|
| `engine/ear/EarTraining.kt` | `EarKind` (5 games), `EarTrainer` interface, `TrainerBase`, `Step`, `Cue`, `Choice`, key and solfege helpers, `EarEvent` |
| `engine/ear/RhythmGame.kt` | `Meter`, `RhythmPattern`, pattern generator, `gradeTaps` (pure), `RhythmTapGame` |
| `engine/ear/MelodyMemoryGame.kt` | `MelodyMemoryGame` |
| `engine/ear/PitchGames.kt` | `PerfectPitchGame`, `ScaleDegreeGame` |
| `engine/ear/ProgressionGame.kt` | `PROGRESSION_CATALOG` (10 progressions), `ProgressionGame` |
| `engine/ear/Workout.kt` | `Workout.plan`, `levelFor`, `streak`, `EarProgress` |
| `data/EarStore.kt` | SharedPreferences file `ear` |
| `ui/ear/EarTrainingScreen.kt` | the entry composable, the hub, one game and its celebration |
| `ui/ear/KeysPlay.kt`, `RhythmPlay.kt`, `WorkoutRun.kt`, `EarCommon.kt` | game screens, the workout |
| tests | `app/src/test/java/com/bigpapituna/sharpears/ear/` |

All engine code is pure Kotlin and JVM tested. Levels are 0..2 in code and "Level 1 to 3" on screen.

## The entry point

```kotlin
@Composable
fun EarTrainingScreen(
    sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, micDefault: Boolean,
    onBack: () -> Unit,
    onReport: (EarEvent) -> Unit = {},
    sound: () -> Boolean = { true },     // pass { prefs.sound }; false = the app stays silent (no clicks, no notes)
    start: EarStart? = null,             // EarStart(kind, level) or EarStart(workout = true): open straight into a game
)
```

It fills the space it is given (hub, game, workout, celebration), handles the system Back itself
(game to hub, hub to `onBack`), makes its own `EarStore` and `Clicks`, and needs no new permission (the mic
is only used if the player turns "Use mic" on, and the RECORD_AUDIO request is made from the game screen).
It does not need a `Prefs` instance. Pass the same `sampler`, `midi`, `mic`, `voice` the Chords tab gets.

## Where it lives

Learn tab, as a row next to the course and Theory (and Sight reading):

```
Learn
  Course path
  Theory academy      -> TheoryScreen(...)
  Ear training        -> EarTrainingScreen(...)    <- this
  Sight reading
```

Subtitle for the row: "Rhythm, melody, pitch, scale degrees, progressions. Daily workout inside." Show the
workout state on the row if you like: `EarStore(context).workoutDoneToday()` / `workoutStreak()`.
The row opens the screen full size in place of the Learn list (like Sight reading does); `onBack` returns to Learn.
The mic is stopped whenever a game is not waiting for an answer and when the app leaves the foreground (`onScreen()`).

## Events to report (`onReport`)

All are `EarEvent`, defined in `engine/ear/EarTraining.kt`.

| Event | When | Fields | Suggested use |
|---|---|---|---|
| `EarEvent.Round(kind, level, clean)` | every finished round, in a game or the workout | `clean` = it earned its point | a small XP tick, and a streak keeper (only `clean` ones count towards "perfect round" goals) |
| `EarEvent.GameComplete(kind, level, score, rounds, bestStreak, newBest, seconds)` | a hub game ended (10 rounds), NOT for the workout's games | `seconds` = time played | XP for the game, practice minutes (`seconds`), achievements ("10 of 10", "new best", first game of each kind: `EarStore.plays(kind)`) |
| `EarEvent.Workout(games, score, rounds, bestStreak, seconds)` | the daily workout's final celebration | | the bigger XP award, daily goal, streak (a workout counts as practice that day), practice minutes (`seconds`) |

Bests and day streak live in `EarStore` (file `ear`): `best(kind, level)`, `plays(kind)`, `workoutDays()`,
`workoutStreak()`, `workoutsDone()`, `lastWorkout()` ("13/20"). `EarStore.reset()` forgets everything; call it from
"Reset progress" in Settings (`Prefs.resetProgress()` does not know this file).

## Debug route (optional)

No route was added to `MainActivity` (shared file). To test on an emulator, the smallest diff is one line in the `first`
block that shows `EarTrainingScreen(..., start = EarStart(EarKind.byId(extras.screen-arg), level))`, for example
`--es screen ear --es game rhythm --ei level 1`, or `EarStart(workout = true)` for the workout.

## Behaviour worth knowing

- Sound: with the mic on and no headphones the app's own sounds stay off while listening, as in Chords > Play; the app plays
  the prompt first (mic off), then listens. Touch and MIDI work throughout the answer phase.
- Rhythm tap does not use the mic. Taps come from the pad (the whole lower half) and from MIDI key presses. A fixed 40 ms
  output latency is subtracted before grading (`OUTPUT_LATENCY_MS` in `RhythmPlay.kt`).
- The daily workout is three different games, about five minutes, chosen by the games played least recently, each at the first
  level whose best is below 7 of 10, with 4 to 8 rounds. It never changes a game's best score.
- The voice speaks prompts and results when Settings > Voice is on; everything is also on screen.

## Known gaps

- Melodic versus harmonic intervals (optional in the brief) is not built.
- Not run on a device or emulator (other teams own them); logic is covered by JVM tests, the Compose screens by compilation only.
  First thing to check on a device: rhythm tap feel (latency constant), the keys lighting in step with the sampler, and the mic
  listening for perfect pitch in any octave.
- Rhythm output latency is one guess for all phones; a tap-along calibration would measure it.
- Perfect pitch by mic accepts the note in any octave of C3 to C6.

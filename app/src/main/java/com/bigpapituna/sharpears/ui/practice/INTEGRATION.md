# Practice tools: what to wire

Team C (branch `revamp/practice`). Everything is new files; no shared file was touched.

## Files

- `engine/practice/Scales.kt`: scale types, `Scale`, `Arpeggio`, textbook fingering, `toExercise`.
- `engine/practice/Warmups.kt`: `Warmup`, five-finger, Hanon style, contrary motion, hand independence, repeated notes, trills, `buildRoutine`.
- `engine/practice/MetronomeTool.kt`: `TickSchedule`, `TapTempo`, `TimeSig`, `Subdivision`, `Trainer`, `RandomMute`.
- `engine/practice/Pitch.kt`: `PitchDetector` (YIN plus a spectral refinement), `PitchTracker`, `PitchSmoother`.
- `data/PracticeStore.kt`: SharedPreferences file `practice`.
- `ui/practice/PracticeScreen.kt`, `MetronomeScreen.kt`, `TunerScreen.kt`.

## Entry composable

```kotlin
PracticeScreen(
    sampler: Sampler,
    mic: MicInput,
    store: PracticeStore,                       // PracticeStore(context), one instance, remembered in MainActivity
    onOpenExercise: (Exercise, String) -> Unit,
    modifier: Modifier = Modifier,
    startTab: PracticeTab = PracticeTab.SCALES, // SCALES, ARPEGGIOS, WARMUPS, METRONOME, TUNER
    onEvent: (PracticeEvent) -> Unit = {},
)
```

It draws its own "Practice" heading and tab row, like `ChordsScreen`, and fills the space it is given. Put it in the Practice tab of the bottom bar.

## onOpenExercise contract

`onOpenExercise(exercise, title)` is called when the learner taps "Practice it" (scales, arpeggios) or "Practice" / "Open" (warm-ups).

- `exercise` is an `Exercise` in token notation with `right`, `left`, `bpm`, `practice` (RIGHT, LEFT or BOTH), `flats`, and `fingers` (the right-hand fingering, one number per right-hand note).
- Open it exactly like a Learn lesson step: `exercise.toSong(id, title)` and the player in wait mode (Perform mode also works; the scale is a plain stream of notes). Use an id such as `"practice-" + title.hashCode()`.
- The player's tempo control should start at `exercise.bpm`. Warm-ups carry a tempo range in the UI only, so no extra data is needed.
- Left-hand-only exercises: `Song.fingering` is applied to right-hand notes only (engine/Fingering.kt), so a left-hand scale shows the player's own computed fingers, not the textbook ones. The screen shows the textbook left-hand numbers under the keyboard. To carry them into the player, `Fingering.of` would need a `leftFingering` field on `Song` (shared file, not done here).

## Progress events

`onEvent(PracticeEvent(kind, id, title, bpm))` fires when an item is opened, before `onOpenExercise`:

- `kind = SCALE`, id `scale:<TYPE>:<root>`: a scale was started. Record "scale practised".
- `kind = ARPEGGIO`, id `arp:<TYPE>:<root>`: same for arpeggios.
- `kind = WARMUP`, id = the warm-up id: a warm-up was started. Record "warm-up done" (a routine is a list of these).

The store already counts them per key and type (times and best tempo): `PracticeStore.stat("scale:MAJOR:0")`, `allStats()`, `distinctKeysPractised()`. Events are sent on open, not on finish, because the hub cannot see how the player ended. If the integration pass wants "finished" instead, call `store.recordScale(type, root, bpm)` / `recordWarmup(id, bpm)` from the player's run-finished callback and pass `onEvent = {}`; remove the `store.record...` calls in `PracticeScreen.kt` (three places, each next to an `onEvent`).

Practice minutes: the player already counts song time; the metronome and tuner add none.

Suggested XP: 10 for a scale or arpeggio, 15 for a warm-up, bonus when `bpm` beats `stat.bestBpm`. Achievements the store can drive: "12 keys" (`distinctKeysPractised() >= 12`), "Hanon 1", "Metronome master" (not tracked).

## Permissions and lifecycle

- The tuner asks for `RECORD_AUDIO` itself (it shows an "Allow microphone" button). It starts the mic only if nothing else is using it, sets `mic.tap`, and on leaving the screen or the app going to the background clears the tap and stops the mic it started.
- The metronome stops when the activity leaves the foreground and releases its own `SoundPool` on dispose. It does not use `audio/Clicks.kt` because it needs four loudnesses; it reuses `clickSamples` and `Wav.write`.
- Keep the screen composed (not destroyed) while the Practice tab is hidden only if you want the metronome to keep running; it does not need that.

## Debug route (one line in MainActivity's `first` block)

`--es screen practice --es tab scales|arpeggios|warmups|metronome|tuner`, mapped to `PracticeScreen(startTab = ...)`. Not added here to avoid a conflict in a shared file.

## Known gaps

- Hanon: number 1 is the book's pattern. Numbers 2 to 5 are written in Hanon's style (five-finger shapes shifting one step), not copied from the printed score, and are named "Hanon style N". Compare with the score before calling them Hanon.
- Arpeggio fingerings follow the thumb-on-a-white-key rule and match the usual ones for white-key roots; the black-key roots (D flat, F sharp, E flat minor and so on) are playable but not checked against a published table.
- The 24 scale tables were written from the standard fingering charts; the left hand of E flat minor and B flat minor and the G sharp minor left hand are the ones to check against Roy's own book.
- Nothing was run on a device. The screens compile and the engine has 39 JVM tests; the gauge, the beat dots and the keyboard panel have not been looked at.

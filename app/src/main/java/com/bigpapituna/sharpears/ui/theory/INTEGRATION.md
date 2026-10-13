# Theory Academy: integration notes (Team A, branch revamp/theory)

Six units, 27 lessons. Each lesson is an animated explainer (3 to 5 scenes drawn on a Canvas, captions in the
pup's bubble and spoken through `Voice`, notes and clicks played as they light up) followed by a 3 to 6 question
quiz. Everything is local: no assets, no network, no new dependencies.

## Files

| Path | What |
|---|---|
| `engine/theory/Music.kt` | Spelled pitches, staff positions, ledger lines, key signatures, scales, intervals, circle of fifths, triads (pure Kotlin) |
| `engine/theory/Theory.kt` | Model: `TheoryUnit`, `TheoryLesson`, `Scene`, the `Visual` types with their timing and sound cues, quiz `Question`s |
| `engine/theory/TheoryContent.kt` | All copy and content (`THEORY`, `THEORY_LESSONS`, `theoryLesson(id)`) |
| `engine/theory/Progress.kt` | `TheoryProgress` (next lesson, units done, unit score), `lessonAfter`, `percent` |
| `data/TheoryStore.kt` | Progress in its own SharedPreferences file `theory` (`done:<id>`, `best:<id>`, `secs:<id>`), `rememberTheoryStore()` |
| `ui/theory/TheoryScreen.kt` | `TheoryScreen` (path, explainer, quiz, celebrations) and `TheoryCard` (the Learn tab summary) |
| `ui/theory/Explainer.kt`, `Scenes.kt`, `Draw.kt`, `Quiz.kt` | Scene player, renderers, drawing kit (glyphs, compact piano), quiz |
| `app/src/test/.../theory/` | `MusicTheoryTest`, `TheoryContentTest` |

## Wiring

1. **Route.** Add a screen to `MainActivity` (the debug route already does this, keep it or fold it in):

   ```kotlin
   data class Theory(val back: Screen) : Screen
   is Screen.Theory -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
       TheoryScreen(sampler, voice, onBack = { voice.stop(); screen = s.back }, soundOn = prefs.sound,
           onLessonDone = { id, pct -> /* XP */ }, onUnitComplete = { unit, pct, secs -> /* XP, achievement */ })
   }
   ```

   `TheoryScreen` handles its own Back (path, explainer, quiz, celebration) and calls `onBack` only from the path.
   It creates its own `Clicks` for the rhythm scenes and releases them when it leaves.

2. **Learn tab.** Show `TheoryCard(store, onOpen = { screen = Screen.Theory(s) })` near the top of Learn, next to
   the Sight-read row (same shape: 16 dp rounded row on `surfaceContainerHigh`). It shows a ring of six segments
   (filled per unit done), "Next: <lesson>" and "N of 6 units". Use one shared store so the card updates when a quiz
   ends: `val theory = rememberTheoryStore()` at the activity level and pass it to both `TheoryCard` and
   `TheoryScreen(store = theory)`. The store bumps `version` on every change.

3. **Progress events** (for Team B's XP, goals and achievements):
   - `onLessonDone(lessonId, percent)`: every finished quiz, first-try score 0..100. Missed questions are asked
     again at the end, so every finished lesson ends on right answers; the score counts first tries only.
   - `onUnitComplete(unitId, percent, seconds)`: once, the first time all of a unit's lessons are done. `percent` is
     the average best quiz score of the unit, `seconds` the time spent in it. Unit ids: `keys`, `read`, `rhythm`,
     `intervals`, `scales`, `chords`.
   - Time spent can also be read from `TheoryStore.seconds(lessonId)` / `unitSeconds(unit)` if practice minutes
     should include theory.

4. **Progress backup.** If `ProgressBackup` should cover theory, export and import the `theory` preferences file
   (keys above); `TheoryStore.reset()` clears it for Settings > Progress reset.

## Debug (debug builds only)

```
adb shell am start -S -n com.bigpapituna.sharpears/.MainActivity --es screen theory
   [--es theory <lesson id> [--ei scene n]]                 # open a lesson's explainer at a scene
   [--es theory <id> --es part quiz [--ei question n]]      # its quiz from question n
   [--es theory <id> --es part done|unit]                   # lesson or unit celebration (nothing saved)
.\scripts\theory-shots.ps1 sc-major:1:3,read-staff:0:2:quiz # screenshots into out\theory-*.png
```

Logs (tag `SharpEars`): `theory: <id> scene i/n <Visual>`, `autoplay to scene`, `question`, `answered`,
`<id> quiz c/t (p%) in Ns`, `unit <id> complete`.

## Notes for the integration pass

- Reduced motion (`reducedMotion()`): scenes are drawn at their end state; their notes still play in time.
- The explainers always play notes (they are the lesson); `soundOn` only gates the celebration fanfare.
- The voice reads captions with `interrupt = true`; the explainer and quiz stop the voice when they leave.
- Not checked yet: dark theme and the Fold's inner display (colours come from theme tokens; layouts cap at 720 dp).

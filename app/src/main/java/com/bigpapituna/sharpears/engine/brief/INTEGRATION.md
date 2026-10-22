# Parent brief, step 1: the event log and the insights engine

Branch `brief-engine` (docs/PARENT-BRIEF-PLAN.md, build outline step 1). What steps 2 (writers) and 3 (screens, actions,
notification, export) call, where every event comes from, and what is left for the integrator. 1009 JVM tests (931 before,
78 new), all green; the emit calls spot-checked on the Fold emulator (emulator-5558). No screen looks different: the edits
are one-line emit calls, plus one crash workaround in PlayerScreen (below).

## Files

| File | What |
|---|---|
| `engine/brief/Events.kt` | `BriefEvent`, the sealed event family; one MiniJson line each, tolerant decode (`Unknown` for a newer app's events) |
| `engine/brief/RunTally.kt` | one run's wrong keys (near, far, top pairs), hint depth per stuck wait, octave hints, groups let go |
| `engine/brief/RunEvents.kt` | `StepRecorder` (one lesson step's tries into one `LessonStep`), `RunEvents.song` (a SongRun with its wrong keys) |
| `engine/brief/Findings.kt` | **the contract**: `Findings`, its kinds, `encode`/`decode`, `redacted()` |
| `engine/brief/CONTRACT.md` | every kind explained with examples: read this before writing words or screens |
| `engine/brief/Insights.kt` | `Insights.compute(...)`, the rules, sessions, windows |
| `data/EventLog.kt` | one profile's log file: append, read, cap, delete, backup helpers |
| `data/Briefs.kt` | the one door: `Briefs.log { at -> ... }`, the session clock, reading, actions, backup, deletion |

## What steps 2 and 3 call

```kotlin
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.engine.brief.*

// Off the main thread (Briefs.read waits for the log's pending writes on its own thread):
val now = System.currentTimeMillis()
val events = Briefs.read(profile.id) +                       // everything kept: up to 90 days or 20,000 events
    listOfNotNull(Briefs.sessionSoFar(profile.id))           // a session note while the session is still open
val findings = Insights.compute(events, profile, Period.WEEK /* SESSION | DAY */, now,
    previousPeriods = 4,                                     // the baseline for trends (sessions, days or weeks)
    current = CurrentSettings(goalMinutes = progress.goalMinutes, bedtimeOn = bedtimeStore.bedtime, wrongKeysShown = mic.wrongFlash))

findings.redacted().encode()      // the only thing the AI writer may send (ids, numbers, enums; no names, no free text)
Findings.decode(text)             // a stored brief back (unknown kinds are dropped)

// A grown-up took an action from a card (behind the gate), so the next brief lists it in findings.actions:
Briefs.action(ActionId.SET_NEXT.name, mapOf("lesson" to "pond-5"))
```

- `Insights.compute` is pure and deterministic; it never touches Android. `zone` defaults to the phone's.
- Lesson titles: `COURSE.flatMap { it.lessons }.first { it.id == id }.title`; song titles from `SongRepository`; page titles
  from `PLAYGROUND_PAGES`. The child's name is `Profile.name`, inserted by the writer on the phone.
- The AI writer's guard ("a reply naming a lesson not in the findings is dropped"): `findings.lessonIds()` and
  `findings.songIds()` are the allowed sets.
- SETTING params: `setting` is a `SettingId.wire` (reader, bedtime, colourNotes, micEasier, quietRoom), `value` the new
  value (micEasier: the new `Profile.micOffsetDb` in dB). quietRoom is defined as `MicInput.wrongFlash = false` (stop
  painting wrong keys on this phone), the in-app answer to a noisy room.
- Weekly notification and "My week": the same call with `Period.WEEK`; for grown-ups `profileKind` is ADULT and the kid-only
  flags and settings never appear (InsightsTest.theAdultVariantHasTheSameShapeWithoutTheKidsFlags).

## Where each event is emitted

All through `Briefs` (a no-op before `Briefs.init`, so JVM tests and previews log nothing). Logcat: `brief: <profileId>
<line>` at debug level (tag SharpEars), never a name.

| Event | Where | Notes |
|---|---|---|
| SessionStarted / SessionEnded | `MainActivity.onStart` / `onStop`, and `openApp` on a switch of player | duration on the ForegroundClock; `practiceSec` = `ProgressStore.practiceSecondsTotal` difference; the picker logs nothing |
| LessonStep | `ui/LessonScreen.kt`: `complete()` (passed or let go), the Next button (skipped, or a read step), Skip (needs MIDI), and on leaving a step after a try or during one (Back or closing the lesson: `left`) | one `StepRecorder` per step; tries, hints and wrong keys summed over its tries; the player hands over its tally through `Embedded.onTally` (and an unfinished try's as it goes); stages report no hints |
| LessonDone | `LessonScreen.complete()` on the last step | `first` from `Prefs.lessonDone` before `onDone` |
| SongRun | `ui/PlayerScreen.kt` `finished()`, library songs only, a run that played something (not Listen, not empty) | the run RunRecap shows; a duet partner gets the same SongRun in their log |
| WrongKey | with each LessonStep and SongRun, every (expected, played) pair, most frequent first, up to 24 (the top five before the email brief) | touch, MIDI, and mic wrong keys unless the room watchdog stopped painting for the session |
| RunDetail (on LessonStep and SongRun) | `engine/Transport.kt` feeds the run's `RunTally` the notes itself: a wait group as it comes due (`tick`), each key as heard (`noteInput`, the mic's latency taken off), the group done; each Perform group played (`PerformScore.onHit`, its offset) or missed (`onMiss`). The Playground's let-go (`RunTally.letGo`, PlayerScreen) marks the rest of the group never played | skipped notes, hesitation (mean, per key slowest, per hand), each hand's clean notes, the Perform spread, hint counts past 32. No PlayerScreen change was needed. The drum stage (`ui/playground/ClapStage.kt`) reports its beats as `rounds` |
| OctaveHint | with each LessonStep and SongRun that had one | |
| NoisyRoom | `PlayerScreen.checkNoise` ("player"), `ui/playground/PlaygroundKit.kt` stage clip watch ("stage:<GAME>") | |
| ToyOpened | `MainActivity` `Screen.Play` (`TimedOnScreen`); 2.7 `Screen.Arcade` and `Screen.ArcadeRound` as ARCADE | on leaving the toy |
| GameRound (2.7) | `MainActivity` `Screen.ArcadeRound`'s round end (ui/arcade/INTEGRATION.md) | each arcade round played to its end |
| EarRound | `MainActivity.earEvent` (ear training: rhythm, melody, pitch, degree, prog), `ui/ChordGamesScreen.kt` ("chord:build" ...) | one per round answered |
| QuestStar | `data/PlayStore.recordQuest` | for the store's own profile |
| StickerDrop | `data/WorldStore.lessonComplete` / `songComplete` | each drop, egg included |
| GoalReached | `data/ProgressStore` when the day's goal is first met (the kid's goal cheer comes from the same moment) | |
| BedtimeShown | `ui/skin/BedtimeStore.goodnightOn` set | |
| SettingChanged | `Profiles.update` / `setKind` (reader, colourNotes, micOffsetDb, hintSeconds, kind), `BedtimeStore.bedtime`, `ProgressStore.goalMinutes`, Settings switches, Microphone setup (wrongFlash, sensitivityDb) | never the name |
| ParentAction | `Briefs.action(...)`: step 3's action cards | |
| FinalePlayed | `data/KidStore.finalePlayed` | |

## The log, backup and deletion

- `files/events/<profileId>.log`, one line per event, written on one background thread ("brief-log"); pruned on open and
  when it grows a tenth past its cap to the last 90 days or 20,000 events, whichever is smaller (EventLogTest).
- Backup (`data/ProgressFiles.kt`): the progress file gains `"events": {"<profileId>": [lines]}`; version stays 1, so an
  older app imports it and ignores the logs. Import (`EventLog.restore`): a backup with the roster replaces every log (a
  profile without lines gets none); one from before the brief (no "events") clears the logs, since its progress is older
  than any of them; a 2.0 owner-only backup leaves them. `ProgressFilesCoverageTest` checks export, import and deletion.
- Deleting a profile deletes its log (`Profiles.delete` -> `Briefs.forget`).
- Android's own backup (backup_rules.xml, data_extraction_rules.xml) does not exclude `files/events`, so the logs go with
  the progress preferences (at most about 2 MB a profile at the cap).

## Decisions (made without asking, as briefed)

- Wrong keys are aggregated per run (every pair up to 24, plus near and far totals on the run), never one event per key.
- Mic wrong keys count whether or not they are painted, except after the room-noise watchdog stopped painting for the
  session (then they may be the room).
- A session is a stretch on screen as one person; Insights merges stretches under five minutes apart, so a rotation, a
  phone call or a look at another app does not split it. A session with no end (a crash) ends at its last event.
- `practiceSec` (what the goal counts) is the headline minutes; time on screen is beside it.
- Reading readiness counts the Playground lessons whose words mention letters (hall-1 to hall-4 today), over the last
  14 days: 2.5 stars or more on average, four samples, three days, a week apart at least.
- The noisy-room candidates: quietRoom when the watchdog fired; micEasier only when stages heard sounds and no key in a
  room the watchdog never minded (an easier mic in a noisy room hears more noise), up to +9 dB.
- `LONG_DAYS` suggests bedtime (a parental control here: the goodnight after the goal) for kids at three times the goal
  and 30 minutes or more on two days of a week.
- Candidates take one from every flag before any flag's second, five at most, so one stuck lesson cannot crowd out the
  rest.
- `BriefEvent.LessonStep.left` (not in the brief's sketch) records a step left without passing after a try, so a child
  who gives up after two failed tries is seen as struggling.

## PlayerScreen and D8 (read before editing ui/PlayerScreen.kt)

> 2.4.0: split. Its screen-long state lives in `PlayerVars` (read through `with(v)`), its parameters in `PlayerArgs` and
> its body in `PlayerStage` (read through `with(args)`): 275 -> 106 dex registers. Still open a lesson step and a library
> song on an emulator after editing it; new state that lives as long as the screen goes into PlayerVars.

On the Fold emulator, after this branch's edits, every lesson's player crashed as it opened: a NullPointerException on
`SongMeta.getId()` at `meta?.let { prefs.transpose(it.id) }`. The Kotlin bytecode was right; D8 (AGP 9.4.1) had allocated
the null check of `meta` to another register (holding the activity) in PlayerScreen's very large composable (about 280
dex registers). The 2.3.1 build compiles the same line correctly only by luck of allocation: any edit to the file can move
it. Fixed here by reading `meta` only through four small functions at the end of the file (`savedTranspose`,
`savedAutoPercent`, `tutorialOf`, `handsTipOf`), and by keeping the run's tally in `Transport.tally` and handing it to the
lesson through `Embedded.onTally` rather than adding a local and a parameter. Checked: the dex has no SongMeta call left
in the composable, and a kid's lesson player, a pond stage, a progression, the mic check, Twinkle (with its video) and Hot
Cross Buns by injected audio all open and run. Other nullable parameters (`embedded`, `chordTrack`) are still null-checked
inside the composable; the lasting fix is to split PlayerScreen (integrator's or a later step's call). After any edit to
it, open a lesson step and a library song on an emulator before trusting the build.

## Spot checks on the Fold emulator (emulator-5558, debug APK)

`brief:` lines in logcat and files/events/<id>.log: sessions start and end with time on screen and practice (`"practice":6`);
a read step and a lesson step left mid-run (`"tries":1,"hints":[3],"wrong":10,"near":5,"far":5,"left":true` with its five
wrong-key pairs); a library song played by injected audio (`"song"` with `"near":1` and the D#4-for-D4 pair, its sticker);
the Jam's ToyOpened (8.6 s). Not checked on a device: settings, quest stars, goals, finales, ear rounds, bedtime (JVM and
code review only).

## Not wired (for the integrator)

- Theory lessons, sight-reading, the Practice tab's scales and warm-ups and the generated songs (progressions, the mic
  check) log no runs: they are not lessons or library songs. Their practice time still counts in each session's
  `practiceSec`.
- Clap along keeps no score by design ("no score and no end"), so clap accuracy comes from the Playground's drum steps
  (`LessonStep.score` with `game` CLAP).
- Chord practice and the Daily chords part log nothing beyond practice time.
- Pup says logs only its ToyOpened (its per-player bests stay in the world store).
- Playground stages hand over no tally: a stage left mid-round is not logged as a try (its let-go rounds and score are,
  when it ends).
- CLAUDE.md has no section for this step (steps 2 and 3 run in parallel; the integrator writes the status).

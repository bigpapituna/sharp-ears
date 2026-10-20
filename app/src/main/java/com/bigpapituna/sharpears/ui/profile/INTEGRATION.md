# Progress and You: integration notes

Team B (branch `revamp/progress`). Everything here is new files; no shared file was edited. Line numbers below are
from the base commit `64ee887` and will have moved.

## What was built

| Piece | File |
|---|---|
| XP, levels, titles | `engine/progress/Progression.kt` |
| 54 achievements, `Snapshot` | `engine/progress/Achievements.kt` |
| Daily and weekly goals, streak with freezes, heat steps | `engine/progress/Goals.kt` |
| `ProgressEvent` (what to report) | `engine/progress/ProgressEvent.kt` |
| Starting level suggestion | `engine/progress/Onboarding.kt` |
| Store (XP, achievements, goals, answers, own 400 day practice log) | `data/ProgressStore.kt`, prefs file `profile` |
| The You tab | `ui/profile/ProfileScreen.kt` (+ `ProfileCharts.kt`) |
| Level-up moment | `ui/profile/LevelUpOverlay.kt` |
| Achievement banner and both overlays in one call | `ui/profile/AchievementToast.kt` (`ProgressOverlays`) |
| Reporting from any screen | `ui/profile/LocalProgress.kt` |
| First run | `ui/onboarding/OnboardingScreen.kt` |
| Icons for the tab and the lock | `res/drawable/ic_profile_person.xml`, `ic_profile_lock.xml` |

## 1. Create the store (MainActivity, next to `val prefs = Prefs(this)`)

```kotlin
val progress = ProgressStore(this, prefs, songIds = { repo.all().map { it.id } })
LaunchedEffect(Unit) { progress.catchUp() }          // once ever: credits practice, lessons and songs from before
```

`catchUp()` gives existing progress its XP quietly (no level-up screen, no toasts). It is a no-op after the first call.

Provide it to every screen, so call sites need no new parameters:

```kotlin
CompositionLocalProvider(LocalProgress provides progress) { /* the whole app */ }
```

Backup: add `"profile"` to `ProgressFiles.NAMES` so Export and Import progress carry XP, achievements and goals.
`Prefs.resetProgress()` does not touch the profile file, which is right: resetting song positions should not take away
XP or achievements. (The store's prefs file is `profile`, not `progress`, because Prefs already owns `progress`.)

## 2. The You tab

```kotlin
// HomeShell.kt
enum class HomeTab(...) { SONGS, LEARN, PRACTICE, CHORDS, YOU("You", R.drawable.ic_profile_person) }

// MainActivity, in the when (s.tab)
HomeTab.YOU -> ProfileScreen(progress, prefs, songs, onSettings = { /* open SettingsScreen as its own screen with back = s */ })
```

Settings is reached from the gear on this screen, so it stops being a tab (it needs a back button as a pushed screen). `ProfileScreen` reads `store.version` and `prefs.version`, so it
updates itself as soon as an event is recorded.

## 3. Where to call `ProgressStore.record`

Call it **after** the Prefs write it goes with (`markCompleted`, `saveScore`, `markLessonDone`), because the snapshot
also reads Prefs. All calls are cheap and main thread. `val progress = LocalProgress.current` in a screen, or the store
directly in MainActivity.

| Where | When | Call |
|---|---|---|
| `PlayerScreen.flushPractice()` (after `prefs.addPractice(...)`, ~line 249) | every 15 s and on exit | `progress?.record(ProgressEvent.Practice(LocalDateTime.now(), secs))` |
| `MainActivity` the `onDispose { prefs.addPractice(...) }` (~line 313) | a Practice today part ends | the same `Practice` event with the seconds just added |
| `PlayerScreen` run finished (~lines 449 to 458): the branch with `run != null` | a Perform run ended | `SongRun(meta.id, r.accuracy, newBest = best, firstCompletion = !wasCompleted)`; read `wasCompleted` from `prefs.progress(meta.id).completed` before `markCompleted` |
| same function, the `else` branch (`prefs.markCompleted`) | a Wait run reached the end (Listen is the app playing: no SongRun, not marked played) | `SongRun(meta.id, null, newBest = false, firstCompletion = !wasCompleted)` |
| `LessonScreen.complete()` (~line 103) | every step passed | `LessonStepPassed` in both branches (the `else { index++ }` and the `if (last)` one) |
| `MainActivity` `onDone = { prefs.markLessonDone(l.id) }` (~lines 296 and 327) | the last step passed | `LessonComplete(l.id)` right after `markLessonDone` |
| `ChordGamesScreen.strike()`, `ChordGame.Outcome.DONE` (~line 179) | a round is answered | `ChordRound(root = game.target.root)` (for Ear games without a root, `ChordRound()`) |
| `ChordGamesScreen` `LaunchedEffect(finished)` (~line 165), after `saveGameBest` | the ten rounds are over | `GameComplete(kind.id, level, game.score, game.rounds)` |
| `MainActivity.finishDaily()` (~line 250), after `saveDailySummary` | Practice today finished with something done | `DailySession(done.size)` |
| `SightReadScreen` where `saveSightBest` is called (~line 86) | a sight-reading run ended | `SightRead(level)` |
| `PlayerScreen` mic toggle (`prefs.micDefault = true`, ~lines 547 and 552) | first time the mic goes on | `FeatureUsed("mic")` |
| `MainActivity` MIDI import handler | a file is imported | `FeatureUsed("import")` |
| Theory (Team A) lesson end and unit end | | `TheoryLesson(lessonId)`, `TheoryUnit(unitId)` (XP and achievements count the first time only) |
| Ear training (Team D) each answered round | | `EarRound(correct)` |
| Practice tools (Team C) when opened | | `FeatureUsed("tuner")`, `"metronome"`, `"scales"`, `"warmups"`, `"hands"`; any string counts toward the Explorer achievement |

Only lesson completion and theory pay once per id; practice, rounds and runs add up every time they are reported, so
report each thing once.

What each event is worth: practice minute 10, lesson step 15, lesson complete 100 (once), song run 50 (+50 new best),
chord round 5, game complete 40, theory lesson 60 (once), theory unit 200 (once), daily goal met 75 (once a day, paid
automatically by the Practice event that crosses the goal), ear round 5. Two additions beyond the brief: sight-reading
run 20 and Practice today session 25 (constants in `Xp`, change freely).

`record` returns `RecordResult(xpGained, levelUp, unlocked)` if a screen wants to react itself (for example the
lesson complete screen could say "+100 XP"). The overlays do not need it.

## 4. Overlays (MainActivity root)

Put one line as the **last child** of the root, above the screen switch, outside the player's forced-dark theme
so it reads in the app's theme:

```kotlin
Box(Modifier.fillMaxSize()) {
    /* HomeShell / Player / Lesson ... */
    ProgressOverlays(progress)
}
```

`ProgressOverlays` shows `LevelUpOverlay` (full screen, Back or Keep going dismisses) when the store has a pending
level-up, and `AchievementToast` banners one at a time (3 s each, tap to dismiss), held back while a level-up is up.
Both are driven by `progress.levelUp` and `progress.toasts`; they are also usable on their own.
A level-up can be earned by the Practice event while a song is playing. The store keeps it pending until
`consumeLevelUp()`, so if a full-screen moment mid-song is unwelcome, simply do not compose `ProgressOverlays` while the
player is on screen; the overlay then appears when the player closes. Toasts from a Practice event can also fire during
a song (first note, 100 minutes); they are small and tap to dismiss.

## 5. Onboarding gate

```kotlin
val first: Screen = when {
    !progress.onboardingDone && !debugRoute -> Screen.Onboarding
    ...
}
// ...
Screen.Onboarding -> OnboardingScreen(progress, prefs, onReminderChanged = { Reminder.schedule(this, prefs) }) { lessonId ->
    screen = lessonId?.let { id -> ALL.firstOrNull { it.second.id == id }?.let { Screen.Lesson(it.second, Screen.Home(HomeTab.LEARN)) } }
        ?: Screen.Home()
}
```

`onboardingDone` is set when the last step is finished and when Skip is pressed, so it never shows twice. The screen
sets `store.experience`, `goalMinutes`, `useMic`, and on Prefs `reminderOn`, `reminderMinutes`, `micDefault` (public
setters only). Android 13+ notification permission is asked at the last step when the reminder is on. The suggested
first lesson comes from `Experience.startLessonId` (never: `sit`, a little: `first-melody`, some years:
`scale-left`; a test checks they exist in `COURSE`). For existing users (Roy), treat `progress.xp > 0` after
`catchUp()` as already onboarded: `if (progress.xp > 0) progress.onboardingDone = true` before the gate.

## 6. The home greeting

`progress.goalLine()` returns "3 of 10 min today" or "Goal met, 12 min today". `progress.streak()` gives the
streak with freezes (`.streak`, `.freezes`, `.atRisk`). Suggestions for `LibraryScreen.greeting()`:

- Not met yet and `streak.atRisk`: "Your {n} day streak is waiting. {goalLine}."
- Goal met: "That's today's goal. Rest those hands."
- Header subtitle (replacing or beside the weekly minutes): `progress.goalLine()`.
- Pup pose: `STREAK` when `streak.streak >= 3`, `GREAT_JOB` when the goal is met (the You tab already uses this).

## 7. Notes and known edges

- The streak is computed from the practice log (this store's own plus `Prefs.practice()`), not stored, so a restored
  backup or a changed goal cannot make it drift. Freezes: one earned every 7th day in a row, two held at most, a missed
  day spends one; today is never counted as missed.
- Prefs forgets practice after 60 days; the store keeps its own log for 400 days, which the 12 week heatmap needs
  (84 days). If `record(Practice)` is never wired, the screens still work from Prefs for the last 60 days.
- Practice XP is counted from the store's own lifetime seconds, so Prefs seconds are never paid twice; the daily goal
  compares the larger of the two sources for the day, so wiring both is safe.
- Night owl (after 22:00) and Early bird (before 07:00) use the time on the Practice event.
- All text avoids em and en dashes; a test checks the achievement copy.

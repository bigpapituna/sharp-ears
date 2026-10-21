# Parent brief, step 3: the screens, actions, notification, export and kid card

> **Integrated in 2.4.0** (branch `release-2.4`, CLAUDE.md "2.4.0 parent brief"): the real writers replace FakeWriter
> (now the demo's only), AppEffects connects all seven actions (engine/brief/PathEffects.kt, MainActivity's BriefNav), and
> every entry below is wired. `BriefEnv.service` became `serviceFor(profile)`; the pup's card on the finish screens is
> `rememberKidCardStep` / `KidCardStepHost` (shown after the celebration's button) rather than KidBriefCardHost.

Branch `brief3` (docs/PARENT-BRIEF-PLAN.md, build outline step 3). Built against the `BriefWriter` interface only: the real
writers (brief2a's AI writer, brief2b's template writer) are not on this branch, so every screen runs on a stand-in
(`FakeWriter.kt`, marked for replacement). 1083 JVM tests (1009 before, 74 new), all green; lint 0 errors; every screen
seen on the Fold emulator (emulator-5558), light and dark, inner screen and phone width.

## What is here

| File | What |
|---|---|
| `engine/brief/BriefActions.kt` | The seven actions: `Effects` (injected), `BriefActions.perform` -> `Outcome`, `Performed` (undo, commit once), `ActionCopy` (every card and sheet word), `ActionRules` (no kid-only actions on "My week") |
| `engine/brief/AskBrief.kt` | `AskHistory` (last 8 exchanges), `AskRules` (clean, name swapped out before the writer, starter questions), `DictationText` |
| `engine/brief/WeeklySchedule.kt` | `WeeklySlot`, `WeeklyPlan.next` (DST-safe), labels, `BriefNotice` (notification words, quiet-week rule) |
| `engine/brief/ExportLayout.kt` | The export document (`ExportDoc`), word wrap, tall card and Letter PDF layout maths, file names |
| `engine/brief/KidCardWords.kt` | The child's card: cleaned lines, the spoken text, when a card is due |
| `engine/brief/BriefFormat.kt`, `BriefSamples.kt` | Report wording and chart maths (`Spark`); made-up findings for previews and tests |
| `data/BriefStore.kt` | Per profile file `brief` (in `ProgressFiles.NAMES`): the weekly slot, the card's last session, the last notification |
| `data/BriefLoader.kt` | `Findings` for a person from the event log (reads files: off the main thread) |
| `reminder/BriefReminder.kt` | One alarm per person, `BriefReceiver`, the notification |
| `ui/brief/*` | `ParentBriefScreen`, `BriefPage`, `RecommendationCard` and `ActionSheet`, `AskBox` and `Dictation`, `BriefExport` (PNG and PDF), `WeeklyTime`, `KidBriefCard` and `ShowMumDialog`, `BriefEntries` (dialogs and the My week card), `BriefEnv`, `AppEffects`, `BriefDemo` |

Already wired (additive edits, check them on merge): `AndroidManifest.xml` (the `BriefReceiver` and the speech-service
`<queries>`), `MainActivity` (the debug route, `BriefReminder.scheduleAll(this)` after `Reminder.schedule`),
`ProgressFiles.NAMES` gets `"brief"` (ProfilesStoreTest updated), `ParentGate.HoldThePup` is `internal`, `ConcertFiles.typeOf`
knows `.pdf`, Settings > Family has "Read the brief" on every kid (behind the gate) and `NewProfileFlow` takes an optional
`onWeekly` (FamilySettings passes it).

## What the integrator wires

1. **The writers.** `ui/brief/BriefEnv.kt`: `BriefWriters.chain = { titles -> FakeWriter(titles) }`. Replace it with the real
   chain (AI first) and set `BriefWriters.fallback = { titles -> templateWriter }`. `BriefService` gives the first writer 3 s
   for a session note and the child's card, 20 s for a day, a week and an answer (`BriefLimits`), then asks the fallback. If
   `WriterChain` already does its own timing, set the limits to a large value; nothing else changes. `FakeWriter` is only
   used by `BriefDemo` and the tests after that. A writer may return null from `write`: the screen says "I could not write
   this one just now" and keeps Refresh.
2. **The effects.** `AppEffects` (ui/brief/AppEffects.kt) does the five SETTING actions for real (reader, colourNotes,
   micEasier through `Profiles.update`, bedtime through `BedtimeStore`, quietRoom as the phone's `MicInput.wrongFlash` off),
   each with an Undo that puts the old value back. The other methods throw `NotConnected` and the card says "This one is not
   connected in this version yet", so nothing pretends. Fill them in (all take effect for the child in `who`):
   - `setNext(lessonId, untilMs)`: keep `lessonId` (null: the path's next) and `untilMs` in a per profile store; make
     `KidMap.next` / `CoursePath.next` return the pinned lesson while `now < untilMs` and it is not done; clear the pin when
     the lesson is finished. Undo removes the pin.
   - `replay(lessonId)`: navigate to `Screen.Lesson(lesson, back)` (needs a callback from the screen that hosts the brief).
   - `practiseTogether(lessonId, songId)`: the Duet picker with the grown-up on the left hand (`Duet.of(currentId, partnerId,
     Hand.LEFT)` where the grown-up is the open profile) on that lesson or song; none lets them pick.
   - `freshStart(pageId)`: for the page's lessons (`PlaygroundPage.lessonIds`) remove `KidStore` stars (`debugForget` shows the
     keys) and `Prefs` lesson done, keep stickers and finales; undo restores the snapshot.
   - `focus(pageId)` / `unlockNext(pageId)`: a per profile "pace" (focus page, pages opened early) read by `KidMap.build`;
     undo restores the old pace.
   - `assignSong(songId, note, afterSongId)`: a shelf entry with the note (stays on the phone; the brief's `redacted()` never
     sends it); undo removes it. `songChoices(afterSongId)` lists the songs to offer.
   Build one `Effects` per profile in `BriefEnv.effectsFor` (the live env passes `AppEffects(app, profiles, profile)`). Where an
   action needs navigation, give `AppEffects` the callbacks from wherever the brief opens.
3. **The notification's door.** The alarm's `PendingIntent` opens `MainActivity` with extras `BriefReminder.EXTRA_PROFILE`
   (`brief_profile`) and `EXTRA_PERIOD` (`brief_period`, `WEEK`). In `onNewIntent` and `onCreate`, when they are present (all
   builds, not only debug), open the Family brief for that child: show `ParentBriefDialog(env, kids, onClose, initial =
   profileId, period = Period.valueOf(period))` (it asks the parent gate first). The receiver is registered; boot, time zone
   and clock changes reschedule every person (`BriefReminder.scheduleAll`); a changed slot calls `BriefReminder.schedule`
   (the Family sheet does through `BriefEnv.weekly`). Notifications need `POST_NOTIFICATIONS`: the weekly sheet and the new-kid
   step ask for it when the switch is on; denied, the brief simply waits in the app.
4. **The parent corner entry.** `KidHome`'s `GrownUpsSheet` (the whistle): add a row "Parent brief" that opens
   `ParentBriefDialog(rememberBriefEnv(), kids, onClose)`. Settings > Family already has it per child.
5. **The You tab entry ("My week").** `ProfileScreen` for a grown-up: put `MyWeekCard(rememberBriefEnv(), LocalProfile.current)` under
   today's panel (the same slot as `world`/`paws`; it is a card that opens a full-screen dialog, so no `Screen` is needed). No
   gate, no avatar tabs, no kid-only actions (`ActionRules`); the card never calls a writer until it is opened.
6. **The finish-screen card.** On a kid's lesson or song finish, after the celebration's button (not over it: the
   celebration is full already), show once per session:
   ```kotlin
   var mum by remember { mutableStateOf(false) }
   KidBriefCardHost(env, profile, say = { voice.speak(it, interrupt = true) }, onShowMum = { mum = true })
   if (mum) ShowMumDialog(env, profile, onClose = { mum = false })
   ```
   `KidBriefCardHost` loads the SESSION findings (`Briefs.sessionSoFar` is included), asks `BriefService.kidCard` (3 s, then the
   fallback), shows nothing for a quiet session (`KidCardWords.due`: 2 minutes practised, once per session, remembered in
   `BriefStore.cardSession`) and speaks the lines when they appear. Spoken text goes through the existing voice path
   (`Voice.speak`; a line with the child's name is made on the phone like every named line). The lines are already cleaned
   (no dashes or symbols, at most three, 110 characters); add the card's lines to no voice catalog (names are never recorded).
   "Show Mum" uses a light gate (hold the pup, no sum); to tighten, swap `LightGate` for `ParentGate` in `ShowMumDialog`.
   Actions inside the note still ask the full parent gate when it has closed.
7. **The privacy page.** Add a paragraph (Settings > About > Privacy): the parent brief is written from facts about each
   child's playing (numbers, lesson names, flags), never audio and never a name; with the AI writer on, those facts and a
   typed or dictated question go to the model, names swapped out on the phone first; the phone's templates write it offline;
   dictation uses Android's on-device speech recognition only (the microphone button is hidden when the phone has none); the
   brief's export goes only where the grown-up sends it.
8. The new-kid flow from the **launch picker** (`ProfilePicker.onAdd`) does not pass `onWeekly`, so a kid added there keeps the
   default (Sunday at 6 pm, on) and can change it in the parent corner. Pass it the same way FamilySettings does if you want
   the question there too.

## Decisions (made without asking)

- Actions record to the child's own log (`Briefs.logFor(child)`, not `Briefs.action`, which logs for whoever is playing), once
  the Undo window (60 s, or when the screen closes) has passed; an undone action is never recorded. Non-undoable ones (replay,
  duet) are recorded at once.
- The first name never goes with a question: `AskRules.forWriter` swaps it for "my child" (a grown-up: "the learner").
- Dictation is on-device only (Android 13+ `isOnDeviceRecognitionAvailable`; before that the standard recogniser with
  `EXTRA_PREFER_OFFLINE`); no mic button when none. Text lands in the field, nothing is kept.
- The weekly notification stays quiet on a week with no playing (no nagging) and hides the name on the lock screen.
- Export: the PNG card is the app's warm light palette whatever the phone's theme (like the concert card), capped at 7,200 px
  (the PDF keeps every page); a one-minute read is shrunk onto one PDF page, a full report paginates (headings never alone).
- Charts: a seven-day paw strip, small trend lines (a gap for a week with too little data), plain bars. No pie charts and no red:
  a line that moves the wrong way is the accent, not an alarm.
- "Written by AI" / "Written on the phone" and the time are plain text under the range (the design rules ban pill badges).
- Adults get PRACTISE_TOGETHER and SET_NEXT like the contract says; reader, bedtime, FRESH_START, PACE_* and TEACHER_ASSIGN are
  hidden for them even if a writer passes them.

## Debug route

`adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es screen brief [extras]` (debug builds; adb splits `--es`
values at spaces, so `ask` takes underscores for spaces):

| Extra | Does |
|---|---|
| `--es view parent` (default) / `myweek` / `kidcard` / `mum` / `weekly` / `notify --es who p1` / `live` | the parent brief over three made-up children; My week; the child's card (Show Mum opens the note); Show Mum's gate and note; the new-kid weekly step; posts the real weekly notification for a real profile; the real env over this phone's real kids |
| `--es period session\|day\|week`, `--es child mia\|theo\|noor` | the tab, the child |
| `--es theme light\|dark`, `--es writer ai\|phone` | the look; the note on the brief |
| `--ez full true` | the full report open |
| `--ei card 0..2` | open that recommendation's sheet (day tab: 0 Teacher mode, 1 Pace, 2 Fresh start) |
| `--ez export true`, `--ez weeklySheet true`, `--es ask Why_is_it_hard` | the Send sheet, the weekly time sheet, a question asked at once |
| `--ez gate true`, `--ez empty true` | start with the gate shut; a child with nothing to read |

## Not proven

- The AI and template writers are not here: every screen is proven on the stand-in. Wording quality, the 3 s fallback and
  the answer box with real answers need the integrator's pass.
- Dictation: the emulator reports on-device recognition, but nothing was spoken to it; the recogniser path (partial results,
  errors, permission) is code-reviewed and JVM-tested for its text rules only. Try it on the Fold.
- The notification was posted from real findings on the emulator and its tap goes to `MainActivity` (see item 3 for opening the
  week). The alarm is registered (`dumpsys alarm`: Sunday 6 pm local); firing it by clock was not waited for.
- The share sheet was opened with a PNG and a PDF (about 200 KB each for a one-minute read, a one-page PDF); no app was picked.

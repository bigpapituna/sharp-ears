# Placement (2.6): what the integrator checks

Branch `place`. Roy: "when creating a profile, ask what their current piano level is, so we don't have to show them all the
introductory stuff if necessary." Adults still see the full home at once (no hidden first-run).

## What it is

- `engine/family/Placement.kt`: `Level` (NEVER, A_LITTLE, SOME, A_LOT), `Placement.startFor(level, kind, reader)` giving a
  `PlacementPlan` (start unit and lesson, lessons to mark "done, skipped", the come-back stone, what the home shows first),
  and `Placement.reconcile` (what applying a plan changes in someone's marks).
- `engine/family/PlacementCheck.kt`: "Let's hear you play": the tasks per level (none for NEVER, two for A_LITTLE, three for
  SOME and A_LOT), the exercises, and `suggest(selfReport, score)` (the gentler of the two; the self-report when they agree or
  the check was skipped; it never raises a level).
- `ui/family/PlacementCheckScreen.kt`: the check (`Screen.Placement` in MainActivity).
- `Profile.level` (persisted in the roster JSON as `"level"`; an old profile reads as NEVER, so nothing changes for it; a
  change of kind resets it to NEVER).
- `Prefs`: `lessonDone` (the path: played or skipped), `lessonPlayed` (credit), `lessonSkipped`, `skippedLessons`,
  `applyPlacement(plan)`, `placementCheckPending`. A skipped lesson is two keys, `lesson:<id>` and `skip:<id>`.
  `markLessonDone` clears the skip flag (a real finish); `forgetLessonDone` removes both.

## The plan per level

| Level | Grown-up | Kid (a parent answers) |
|---|---|---|
| NEVER | Basics (nothing skipped) | the garden (nothing skipped) |
| A_LITTLE | Beginner; Basics skipped | the pond; the garden skipped |
| SOME | Intermediate; Basics, Beginner, Rhythm skipped | the workshop; the garden, pond and hall skipped, except the hall's recital (hall-12), which stays as a "come back" stone |
| A_LOT | Keys; Basics to Intermediate skipped; Learn opens at Keys; the home opens on Songs | not offered (counts as SOME) |

Skipped lessons are done for the path (`CoursePath.next`, `KidMap.next`, Practice today, the quest) and never for credit.

## Check against the brief (skipped lessons)

- A skipped lesson emits no `LessonDone` (or any) event: the marks are written by `Prefs.applyPlacement`, which logs only to
  logcat (`placement: ...`). Nothing in engine/brief reads `Prefs.lessonDone`. The brief's lesson findings, READY_FOR_ROAD,
  stars and "new lessons" all come from events, so a skipped page is invisible to them. A page the brief calls "walked" needs
  its finale event, which never plays for a skipped page.
- Wired to `lessonPlayed` (not `lessonDone`): `ProgressStore.snapshot` and `catchUp` (lessons done, units complete,
  achievements), `world.earnLevels` (stone stickers), the wardrobe ledger's walked pages, `Finale.owed`, `LessonScreen`'s
  `firstFinish` (the first real play of a skipped lesson is a first finish: XP, sticker, `LessonDone(firstFinish = true)`),
  and `AppEffects.done` (Fresh start and its Undo do not touch skip marks).
- `KidMap.build(..., skipped = prefs::lessonSkipped)` shows skipped stones OPEN with no stars (`Stone.skipped`), so a skipped
  page is never "walked" and its finale is never owed. `BriefEmail.extras` passes it, with the placed `CoursePath`.
- `Profiles.update` logs `level` as a `SettingChanged` event like the other profile settings. The email brief's `settingLine`
  ignores unknown keys, so it says nothing about it; the AI/template writers see it only as a setting change. If the brief
  should ever say it ("started at the pond"), that is a new `settingLine` case.
- "Let's hear you play" is recorded as a first lesson without a brief event: the id `placement-check` is not a course lesson,
  and `CourseTitles.lookup` has no title for it. If the brief should show it, add an event and a title; nothing does now.

## Check against the pop-chords work (popc, popp)

None expected. This branch touches nothing in engine/chords or ui/chords. Shared files edited here: `MainActivity.kt` (a
`Screen.Placement`, the profile collector, the onboarding and picker callbacks, `LearnScreen(skipped = ...)`,
`KidMap.build(skipped = ...)`), `Prefs.kt`, `Profile.kt`, `ProfilePicker.kt`, `FamilySettings.kt`, `OnboardingScreen.kt`,
`LearnScreen.kt`, `KidMap.kt`, `LessonScreen.kt` (`LessonDrop` made internal, `firstFinish`), `ProgressStore.kt`. A merge
conflict in MainActivity is the only likely thing; the hunks are small and self-contained.

## Behaviour to know

- NEVER never sees the check. A_LITTLE gets two tasks (find a C; C D E F G), SOME and A_LOT three (plus Twinkle's first
  phrase with the band at 70 bpm). "Skip" and Back are always in view; quitting halfway keeps the self-report silently, with no
  reward. No score, pass, fail or "wrong" is ever shown. A task let go after three tries is gone by.
- It counts as the first lesson (only when played through, once per profile): `ProgressEvent.LessonComplete("placement-check")`,
  three stars in the kid store, and `world.lessonComplete` (a sticker, sparkles toward an outfit). The stars are for taking
  part, not a grade.
- A grown-up's check follows the onboarding's questions (the mic choice comes first), from the "You're all set" screen. A kid's
  runs on their first open when a parent tapped "Let them try the check" (`Prefs.placementCheckPending`).
- A change of level (onboarding, Family > child > Piano level) is read by MainActivity's profile collector: it re-plans the
  open profile's marks in place (no reopen, no lost screen). A lower level opens the lessons that were only passed by; a lesson
  played, its stars, stickers and finales are never touched. A profile that is not open is re-planned when it opens.
- The check uses the Playground stages (`KeyHuntStage`, `CopyStage`) and the embedded player, with a scratch preferences file
  (`progress.placement-check`, deleted when it closes) so it counts as nobody's practice. `LocalProgress` is null inside it.

## Not done / open

- Voice: the check says only lines that already exist (the stages' "Let me show you", "Your turn!", "Good try, let's keep
  going" and the copy stage's finger call). The pup's closing line is text only.
- Tuned on synthetic audio only (injection on the emulator); the band task's 60% is the concerts' mark, unproven on a real room.
- `Experience` (the older three-answer question) is kept for profiles that answered it; a profile with a level uses the plan's start.

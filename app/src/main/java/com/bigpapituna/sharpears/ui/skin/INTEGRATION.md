# Skin (2.1, stage 2): what the integrator wires

The kid skin and the wrong-key light. Owned files: ui/NoteLane.kt, ui/Keyboard.kt, ui/LessonScreen.kt, ui/RunRecap.kt,
engine/RunWords.kt, engine/SessionWords.kt, ui/skin/*, engine/skin/*. Rules (pure, JVM-tested) live in engine/skin;
drawing in ui/skin. Everything below that is already live needs nothing; the numbered items need a line elsewhere.

## Already live (reads LocalProfile, no wiring)

- **Colour notes** on NoteLane and Keyboard: both default `colours = colourNotesOn()`, which is `Profile.colourNotes`
  (kids on, grown-ups off) unless a screen provides `LocalColourNotes`. Letters for readers, code-drawn animals for
  pre-readers (`reader()`); black keys are stripes of their two white neighbours (decision in engine/skin/NoteColours).
  On the keyboard the colour is a pad near the front of each key; a note-name label sits on the pad (an animal for a
  pre-reader), so kids want key labels on (item 4).
- **Stars on every recap** (RunRecap): one to three stars (engine/skin/Stars: Perform 60% = two, 90% with mean timing
  under 80 ms = three; Wait runs: no wrong keys = three, up to three = two; never zero). Grown-ups keep the ring, chips,
  XP and Loop bar under them. Kids: no number anywhere, RunWords' kid headline and line with their name, the party on
  three stars, never a sad pose.
- **Bites in LessonScreen** for kids: a reading step plays its first scene, then a "More?" pill; exercises cut at the
  phrase end (item 6); one run passes (Perform needs two stars; a ramp is one Perform run at 75%); kid feedback words;
  one main button (no Back, no scene controls; the top-left back arrow stays); the finish has no stats or XP numbers.
- **Goodbye inside lessons**: when a kid's lesson ends with today's goal reached (ProgressStore.secondsToday against
  goalMinutes, shown once a day), the celebration's button is "Goodnight, pup" and the goodbye opens (pup_goodnight
  yawning, the goodnight line with the name, this week's paws with today's stamped). A lesson opened after the goodbye
  shows the pup asleep instead of the lesson; "Grown-ups: one more lesson" goes through `gate.guard` and allows one
  for today.

## 1. Wrong key shown (PlayerScreen, detector team's file)

```kotlin
val wrongs = rememberWrongFlash(slow = start.lightSlow) { w -> /* optional: the pup's head tilt, see 7 */ }
NoteLane(..., wrong = wrongs)
Keyboard(..., wrong = wrongs)
// touch and MIDI, in keyStruck's InputResult.WRONG branch:
WrongKey.of(midi, transport.expected.map { it.midi })?.let(wrongs::show)
// mic, MicEvent.Wrong (detector adds the nearest expected note and the distance in semitones):
is MicEvent.Wrong -> if (transport.waiting && mic.wrongFlash /* && not turned off for this session */)
    wrongs.show(WrongKey(struckMidi, abs(e.distance)))   // struckMidi = e.nearest + e.distance if only those are given
```
Yellow at a distance of two semitones or less, red further; about 600 ms with a short shake, steady with animations
off. Kids see the same colours and no words. Keep `lights.wrong(...)` for the combo rule if wanted, but drop the old
`wrongFlash` red border (`wrongVisible`) once this is in. Log line: `wrong key D4 distance=2 NEAR`.

**Default flips**: `MicInput.wrongFlash` (prefs "mic", key "wrongFlash") must default to **true** (FAMILY.md; the switch
in Microphone setup stays, the watchdog's per-session off stays the detector's).

## 2. Big keys for kids (PlayerScreen)

```kotlin
val keySize = KeyboardSize.forKid(isKid())
val range = remember(song, keySize) { keySize.rangeOf(song.notes) }   // REGULAR is exactly fitRange
Keys(Modifier.fillMaxWidth().weight(if (isKid()) keySize.heightShare else <today's 0.3 / 0.45>))
Keyboard(..., keySize = keySize)
```
BIG shows just the octave the notes need (C4 to C5 is 8 white keys, about twice as wide) and grows labels and pads;
the lane uses the same range, so columns stay aligned. Transport's `playable = range` still auto-plays anything outside.

## 3. Kid copy in the player bar

The embedded bar's "Streak N" (Perform) is a number: hide it for kids (`!isKid()`). LessonScreen already gives the bar
kid labels ("Your turn", "Play along"), no tempo or pass mark.

## 4. Settings and defaults (Prefs and Settings are shared files)

- Grown-ups: a "Colour notes" switch (Settings > Falling notes) writing the profile:
  `profiles.update(p.id) { it.copy(colourNotes = on) }`. The preference is `Profile.colourNotes`; nothing new in Prefs.
- Kids: default key labels to `KeyLabels.ALL` for kid profiles, so every white key's pad carries its letter or animal.
- Optional: provide `LocalColourNotes provides false` where colours are a crutch you do not want (sight-reading).

## 5. Goodbye outside lessons, and the paw card

- After a song run or a Practice today part ends (never mid-run), for a kid:
  `if (Bedtime.sayGoodnight(true, progress.secondsToday(), progress.goalMinutes, store.goodnightOn, today))`
  show `Goodbye(name, PawWeek.of(progress.practiceLog(), today), onSongs = { Songs tab }, onClose = ..., onJam = ...,
  say = { voice.speak(it, interrupt = true) })` and set `store.goodnightOn = today` (`rememberBedtimeStore()`).
  Songs and the Jam never close (`Bedtime.SONGS_OPEN`); lessons are closed by LessonScreen itself.
- MainActivity's two `LessonScreen(...)` calls: pass `onSongs = { screen = Screen.Home(HomeTab.SONGS) }` and, once the
  play team's Jam exists, `onJam = { ... }` (null hides the Jam button).
- The You tab: `PawCalendarCard(kid = isKid())` (reads LocalProgress; kids get a line with no number).
- Add `"skin"` to `ProgressFiles.NAMES`: BedtimeStore keeps the goodnight and "one more" dates per profile in it.

## 6. Phrase ends (playground team)

`Bites.phraseEndBeats(ex)` (engine/skin/Bites.kt) answers null until `Exercise.phraseEnd` exists; then return it there
in beats (`Bites.barsToBeats(bars, ex)` if it counts bars). That is the one line to change; LessonScreen takes it as the
default `phraseEnd` parameter. Null plays the whole exercise. `Bites.cut` cuts both hands and the book fingering.

## 7. The World team's pup

`WrongFlash.count` (state, bumps per wrong key) and `last` (the WrongKey), or the `onWrong` lambda of
`rememberWrongFlash`, are the hook for the head tilt: `LaunchedEffect(wrongs.count.intValue) { tilt() }`.

## 8. Words and voice

- Interstitial (Practice today): for kids use `SessionWords.chip(p, kid)`, `quoted(p, kid)`, `about(p, kid)` (no counts,
  no bar numbers, no minutes) and speak `SessionWords.kidNext(p)`.
- Home goal line for kids: `SessionWords.kidGoal(progress.secondsToday(), progress.goalMinutes)` instead of minutes.
- Voice catalog: add `SkinLines.fixed()` to `SpokenCatalog.all()` and render them at speed 0.85. Lines with a name in
  them (recaps, step lines, goodnight) are generated on the phone; each has a nameless twin in the catalog.
- The pup can name a key: `NoteColours.spokenName(midi, reader())` ("the red cat key", "the C sharp key").

## Debug route (debug builds)

`--es screen skin --es view lane|wrong|recap|goodbye|asleep|paws|bite` with `--ez kid true|false`, `--ez reader
true|false`, `--es tone near|far|both` and `--ef slow 12` (wrong), `--ei stars 1..3` (recap), `--es lesson <id> --ei
step n --ei bars 2` (bite: the real LessonScreen as a kid, phrase end at two bars), `--el pos <ms>` (lane). The route
is one line in MainActivity's `first` block; it replaces the whole content, so remove or keep it as you like.

## Logs (tag SharpEars)

`wrong key <note> distance=<n> NEAR|FAR`, `lesson <id> step n/m <KIND> kid [bite <beats> beats]`, `lesson <id> step n
(bite) scene 1/1`, `lesson <id> step n more`, `bedtime: goodnight after <id>`, `bedtime: <id> asleep`,
`bedtime: a grown-up allowed one more lesson`, `skin demo: <view> kid=<b> reader=<b>`.

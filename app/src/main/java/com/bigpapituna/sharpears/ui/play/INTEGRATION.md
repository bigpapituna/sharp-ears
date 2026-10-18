# Play corner (2.1, stage 2, team play): what the integrator wires

Jam, Pup says, Clap along, Share my concert, Pup's quest and Duet. Everything here is built and tested on the JVM;
nothing is wired into the app yet except a debug route. Engine code is in engine/play (Android-free, tests in
app/src/test/.../engine/play); screens are in ui/play, each with @Preview composables (light, dark, and a still or
empty state) so they can be checked in Android Studio without a device.

Debug: `adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es screen play [--es demo jam|pupsays|clap|duet|quest]`
opens `PlayDemo` over the app (a menu without `demo`). It is the only edit outside this team's files: three lines next
to the motion demo in MainActivity (the `first` block maps to MainActivity's private `Screen` types, and a new
Screen would have been a bigger diff). Remove it or keep it, as you like.

## Entry points

All screens are full-screen composables with their own exit pill (and no score, no timer). Give each a `Screen`
in MainActivity (depth 2, system theme, not the stage) and a Back handler that calls the same `onExit`.

| What | Call | Where it goes |
|---|---|---|
| Jam | `JamScreen(mic, midi, frames, onExit, hint = null)` | A "Play" row on Songs or Learn (kids: high on the Songs tab; it is the app for a four-year-old). The quest card's "Find all the Cs" opens it with `hint = "Can you find all the Cs?"`. |
| Pup says | `PupSaysScreen(mic, midi, sampler, voice, sound = { prefs.sound }, onExit, onFinished = { results -> })` | The same "Play" row, and the Playground's "Copy the pup" lesson (#6) can link to it. `onFinished` gets every player's `PupSaysResult(profileId, best)` with `best` = their best of the day, for the World team (stickers, crown). |
| Clap along | `ClapAlongScreen(mic, frames, clicks, voice, sound = { prefs.sound }, onExit)` | The "Play" row; the Playground's "The steady drum" (#7) can link to it. `clicks` is an `audio/Clicks` (make one, `release()` on dispose, as PlayDemo does). |
| Quest card | `QuestCard(plan, onTask = { task -> })` | Songs tab, under "Practice today" (`plan` is the same `SessionPlan` the panel shows, from `planSession(...)` in MainActivity ~line 373; kids' quests do not need it, pass it anyway). `task.opens` says where a tap should go: `"songs"`, `"pupsays"`, `"jam"`, `"chords"`, `"learn"`. |
| Send to someone | `ShareConcertButton(run, songTitle, stars, stickerRes = null)` | On the song recap (ui/RunRecap.kt, skin team) after a Wait or Perform run: `run` = the run just saved (`RunStore(context).load(songId)` or the RunRecording PlayerScreen holds), `stars` = the recap's star count, `stickerRes` = the sticker the World team dropped for this run (null leaves the dashed slot). |
| Duet picker | `DuetPicker(onStart = { duet -> }, onCancel)` | From a song's start options (next to the hand chooser) when there are two or more profiles. See Duet below. |

`mic` is MainActivity's `MicInput`, `midi` its `MidiDevices`, `sampler` its `Sampler`, `voice` the profile's `Voice`.

## Raw frames and the clap counter

- Jam (the chord ear) and Clap along (the clap counter) read raw mic audio through `FrameTap`
  (ui/play/PlayCommon.kt): `fun interface FrameTap { fun register(sink: ((FloatArray) -> Unit)?) }`. Pass
  `FrameTap { mic.rawFrames = it }` once the detector team's `MicInput.rawFrames: ((FloatArray) -> Unit)?` is merged.
  Until then `mic.frameTap()` bridges the existing tuner tap (`MicInput.tap`), which works today (PlayDemo uses it).
  Frames come on the audio thread; both consumers are thread-safe. `frames = null` is fine: the Jam then counts
  chords only when the matcher or MIDI hears three keys, and Clap along offers tapping only.
- `engine/play/ClapCounter.kt` has exactly the playground team's interface, `class ClapCounter(sampleRate = 48_000)`
  with `fun process(chunk: FloatArray): Int` (claps confirmed in that chunk). Keep one of the two. If you keep the
  playground's, change the import in ClapAlongScreen.kt and its latency: the screen subtracts
  `ClapCounter.CONFIRM_MS` (80 ms, the time from a clap's start to its confirmation) plus 20 ms of buffering from the
  moment a count arrives. Mine is tested in ClapTest: a burst counts once in any chunk size, 300 ms refractory, a
  steady 120 bpm beat counts every clap, a hiss, a voiced vowel, piano notes and quiet taps count nothing.

## The store

`data/PlayStore.kt`, one SharedPreferences file per profile: `ProgressFiles.fileFor("play", profileId)` ("play" for
the owner, "play.p3" for others). **Add `"play"` to `ProgressFiles.NAMES`** so backup, import and profile deletion
cover it. It holds today's quest (`"quest"`, JSON) and the day's best in Pup says (`"pupSaysDay"`, "2026-10-18:7").
The rules live in engine/play/PlayBook.kt (tested with an in-memory storage).

## Quest events to send

`PlayStore(context, profile.id).recordQuest(event, kid = profile.isKid)` returns the tasks that event completed (a
star each; say something or let the World team pop a star). The Play screens send their own (Pup says: `PupCopied`
per pattern copied, to each player's own store; Jam: `NotePlayed` for every C). The integrator sends:

| Event | Where | Call |
|---|---|---|
| `QuestEvent.SongFinished(songId)` | PlayerScreen, the run-end function (~lines 457 to 480), both branches, library songs (`meta != null`) | after the `SongRun` record |
| `QuestEvent.LessonDone(lessonId)` | MainActivity `onDone = { prefs.markLessonDone(l.id); ... }` (~lines 453 and 489) | after `LessonComplete` |
| `QuestEvent.ChordReviewed(itemId)` | MainActivity, the Daily chords part's `onEnd(answered)` | `part.itemIds.take(answered).forEach { ... }` |
| `QuestEvent.LoopDone(songId, bar)` | MainActivity, a Daily `SessionPart.Loop` part ending (its two minutes ran) | `LoopDone(part.songId, part.bar)` |
| `QuestEvent.PupCopied(length)` | Ear training, melody memory: `EarEvent.Round(kind = MELODY, clean = true)` (MainActivity ~line 409) | `PupCopied(0)` (the length does not matter) |
| `QuestEvent.NotePlayed(midi)` | optional: lessons and songs hearing Cs (find-c already completes "Find all the Cs" through `LessonDone("find-c")`) | only for `midi % 12 == 0`, to keep writes rare |

A grown-up's quest comes from the day's plan, so it exists once `QuestCard` (or `PlayStore.quest(kid, plan)`) has
run that day; events before that are dropped. A kid's quest is made on its first event. A quest that is not
finished just ends with the day: no streak, nothing lost.

## Duet: what to call and where

Model: engine/play/Duet.kt. `Duet(leftId, rightId)`; both players get the whole run (the same stars and XP each).

1. **Pick.** `DuetPicker(onStart = { duet -> ... })`. The current profile is one of the two; `duet.partnerOf(current.id)`
   is the other. Carry the `Duet` into the player (a new `duet: Duet? = null` field on `PlayerStart`).
2. **Play.** Exactly both-hands practice: `PlayerStart(practice = Practice.BOTH, ...)`. The hand chooser is hidden or
   locked while a duet runs. Nothing else changes in the player.
3. **Credit the partner** in PlayerScreen's run-end function (~lines 457 to 480), right after the current profile's
   own `SongRun` record, for `val partnerId = duet.partnerOf(profile.id)!!`:
   ```kotlin
   val partnerPrefs = Prefs(context, partnerId)
   val partnerProgress = ProgressStore(context, partnerPrefs)
   val before = partnerPrefs.progress(meta.id)
   if (r != null && transport.scoreFromMs <= 0.0 && r.accuracy > 0) partnerPrefs.saveScore(meta.id, r.accuracy) else partnerPrefs.markCompleted(meta.id)
   partnerProgress.record(Duet.partnerRun(meta.id, r?.accuracy, before.bestAccuracy.takeIf { it >= 0 }, before.completed))
   PlayStore(context, partnerId).recordQuest(QuestEvent.SongFinished(meta.id), partner.isKid)
   ```
   (`r` is the Perform run when there is one; for a Wait run pass `null` accuracy, as the player does.) The partner's
   practice minutes are not added: the practice log belongs to the phone's open profile.
4. **Recap.** The recap's stars are the run's; show `Duet.recapLine(leftName, rightName, stars)` under them, both
   avatars, and `duet.credit(stars, xpGained)` if the recap lists per-person XP. Stickers (World team) drop for both.

## Decisions to know

- **Jam listening range.** The note matcher confirms expected notes; it does not transcribe. The Jam listens to
  middle C to C5 plus every C from C2 to C6 (16 keys), each key its own group. Measured on the JVM: 16 keys cost about
  10% of a desktop core, C3 to C6 (37 keys) about 34%, so the range is what a phone can afford. Single notes come out
  exact on PianoSynth; free-play chords do not (the matcher hears one note of C E G), so the **chord ear**
  (engine/play/ChordEar.kt) checks the raw audio around each heard note's onset and adds the chord's other keys
  (3 of 3 on six test chords, 1 on every single note of a legato line). A high note's overtone heard as a lower key
  (C6 also "played" F4) is dropped (`Jam.micEcho`). Measure the mic thread on the Fold before release.
- **Share my concert sends the run replayed, not the mic.** files/runs holds the notes as played (JSON), not audio,
  and release builds never record the mic. `ConcertMix` replays the notes with the app's own piano samples (decoded
  from assets/samples with MediaCodec) into a 48 kHz WAV, the same thing "Hear my run" plays. The card is a 1080 x 1350
  PNG drawn with Android's Canvas from `ConcertCardLayout`. Both go to cache/share, the only folder the FileProvider
  serves (res/xml/file_paths.xml is unchanged), named "Mia plays Ode to Joy.wav/.png".
- **Share helper.** `Share.files(context, files, title, subject = title)` in data/StuckShare.kt shares any files in
  cache/share (one: ACTION_SEND, more: ACTION_SEND_MULTIPLE, a MIME type that fits them all); `Share.fresh(context)`
  gives the emptied folder. `StuckShare.zip/share` use it, unchanged for Microphone setup. Rename the file to
  Share.kt at will.
- **Pup says.** Melody memory's walk moved into `MelodyWalk` (same melodies for every seed, tested). Everyone copies
  the same growing song at their own length; one try per turn, then the turn passes; a lone player (or the last one
  in) goes on without hand-offs. Kids (any kid among the players) get C to G, grown-ups the C major octave. The mic
  listens to the pattern's keys while it is the player's moment only; the app's own notes are played with the mic off.
- **Clap along.** Tapping is the default and the drum sounds on every beat. Clapping turns the mic on, and the pup
  drums silently unless headphones are on, because his drum through the speaker would count as claps. A clap within a
  quarter beat (at most 150 ms) is in time; the row of dots and the pup's cheers at 4, 8 and 16 in a row are the only
  feedback, no score.
- **Duet credit is full, not split.** Both get the run's stars and XP; the partner's best and first completion are
  judged against their own progress (`Duet.partnerRun`).
- **Crown of the day.** `PupSaysGame.crown(results)` gives everyone with the top best (ties share it). For the day
  across games, read each profile's `PlayStore(context, id).pupSaysBest()` and pass them as `PupSaysResult`s.
- Boomwhacker colours live in engine/play/NoteColours.kt (the skin team has the same table; keep one).
- Copy: sentence case, no dashes, nothing that shames: misses are "Oops! Two more tries.", early and late claps are
  mentioned to grown-ups only, kids' quest rows show dots instead of "1 of 3".

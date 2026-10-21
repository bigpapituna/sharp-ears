# The brief's contract: Findings

`engine/brief/Findings.kt` is what the insights engine (`Insights.compute`) hands to every writer (the AI writer, the
template writer, the child's writer) and every screen of the parent brief (docs/PARENT-BRIEF-PLAN.md). Steps 2 and 3 build
against this page and that file only; neither needs to read the event log.

## Rules every consumer can rely on

- **Ids, numbers and enums only.** No prose and no names. A writer inserts the child's name and the lessons' and songs'
  titles on the phone (lesson ids are `CourseLesson.id` in `COURSE`, song ids are the library's `SongMeta.id`, page ids
  are `PlaygroundPage.id`: garden, pond, hall, workshop, treehouse, nightsky, fair).
- **Only what happened.** Every lesson and song id in a finding is in the person's events; every page is one a finale
  played on, or the page of a lesson in the events (InsightsTest's property test checks it over random histories).
- **Own past only.** Trends compare the person with their own earlier periods; there is no field that could compare two
  children.
- **Ordered by importance.** The one-minute read takes `facts.take(3)`, `candidates.take(2..3)` and `praise.first()`.
  The full report uses `stats`.
- **Stable names.** The JSON (`Findings.encode()`) writes enum names; they never change. `Findings.decode` drops entries
  whose kind it does not know, so an older screen survives a newer engine.
- **What may leave the phone:** `findings.redacted().encode()`. It drops every action or setting param that is not one of
  `lesson, song, page, setting, value, after`, every value that is not id-shaped (`[A-Za-z0-9._:-]{1,64}`: a teacher's
  note never goes), and nothing in Findings is a name to begin with.

## Shape

```kotlin
Findings(
    profileKind: ProfileKind,        // KID or ADULT ("My week" for grown-ups uses the same structure)
    period: Period,                  // SESSION | DAY | WEEK
    range: TimeRange(fromMs, toMs),  // epoch ms; toMs is "now" for DAY and WEEK, the session's end for SESSION
    facts, trends, flags, candidates, praise,
    stats: Stats,
    reader: Boolean,                 // false only for a kid who does not read yet (animals, not letters)
    goalMinutes: Int?,               // the daily goal
    actions: List<ActionTaken>,      // grown-ups' actions in the range, "recorded in the next brief"
    settings: List<SettingChange>,   // settings changed in the range (key and value as text)
)
```

Fact, Flag and Praise share one shape: `kind`, `value: Long`, `of: Long?` (what the value is out of), `lessons`,
`songs`, and for Flag and Praise a `page`. What value and of mean is fixed per kind (below). Trend is
`(kind, now, before, direction UP|DOWN|STEADY, good: Boolean?, periods)`. Candidate is
`(recommendation, action, params, because: FlagKind?)`.

## Periods

| Period | Range | Compared with (`previousPeriods`, default 4) |
|---|---|---|
| SESSION | the last session at or before now; stretches on screen less than 5 minutes apart are one session | the sessions before it |
| DAY | today from midnight (phone time zone) to now | the days before |
| WEEK | the seven days ending today, to now | the seven-day blocks before |

Earlier periods that ended before the person's first event never count (a new child has no "usual"). For a session note
while the session is still open, add `Briefs.sessionSoFar(profileId)` to the events first.

## Facts

| Kind | value | of | ids | Example (a writer's wording, not the engine's) |
|---|---|---|---|---|
| PRACTICE_MINUTES | minutes practised (what the goal counts) | minutes on screen | | "42 minutes at the piano this week." |
| DAYS_PRACTISED (WEEK) | days with practice | 7 | | "Five days out of seven." |
| SESSIONS (DAY, WEEK) | sessions, when more than one | | | "Three short sessions." |
| GOAL_DAYS | days the goal was reached | 7 (WEEK) | | "The daily goal four times." |
| MOSTLY_MORNING / AFTERNOON / EVENING / NIGHT (WEEK) | minutes in that part (60 percent or more) | all minutes | | "Evenings are piano time." |
| NEW_LESSONS | lessons finished for the first time | | lessons, latest first | "Two new lessons: Copy the frogs and Drum on a lily pad." |
| LESSONS_REPLAYED | finishes of lessons done before | | lessons | "Played Twinkle twice more." |
| SONG_RUNS | songs played to the end | | songs, most played first | "Ode to Joy, three times." |
| NEAR_MISSES | wrong keys within two semitones | all wrong keys | | "Most slips were the key next door." |
| TIMING | mean ms off the beat (Perform) | runs | | "About 40 ms from the beat." |
| HANDS | left hand's hit rate | right hand's | | "Left hand 61, right hand 96." |
| EAR_ROUNDS | ear and chord game rounds right | rounds | | "5 of 6 by ear." |
| CLAP | mean percent of drum beats with a clap | drum steps | | "Clapped on most beats." |
| TOY_MINUTES | minutes in Jam, Pup says, Clap along | minutes on screen | | "14 minutes of jamming." |
| STICKERS | stickers dropped | new to the album | | "Three stickers, all new." |
| QUEST_STARS | Pup's quest stars | | | |
| HINTS | stuck moments where the app played the key | stuck moments with any hint | | |
| SKIPPED_NOTES | notes that came due and were never played (let go, missed in Perform), three or more | notes that came due | | "Four of 412 notes went by unplayed." |
| HESITATION | mean ms from a note coming due to its key being played (wait mode), ten notes or more | notes timed | | "About 1.1 seconds to find each key." |
| TEMPO_SPREAD | mean spread (standard deviation) of Perform runs' offsets from the beat, ms | Perform runs | | "The beat held to within about 40 ms." |

Wording numbers is the writer's call (kids' copy has no numbers; the parent's brief has them, never as the headline).

## Trends

Moving needs both 15 percent of the baseline and the kind's smallest step. `good` is null when STEADY.

| Kind | Unit | Smallest step | Up is good? | Needs |
|---|---|---|---|---|
| MINUTES | practice minutes | 5 | yes | |
| DAYS (WEEK) | days | 1 | yes | |
| SESSIONS (DAY, WEEK) | sessions | 1 | yes | |
| STARS | mean lesson stars | 0.25 | yes | 2 finishes |
| ACCURACY | Perform percent | 5 | yes | 2 runs |
| TIMING_MS | ms off the beat | 15 | no (down is good) | 2 runs |
| NEAR_SHARE | percent of wrong keys next door | 10 | yes (misses getting closer) | 5 wrong keys |
| WRONG_PER_RUN | wrong keys per graded run | 1 | no | 3 runs |
| LEFT_HAND / RIGHT_HAND | hit rate percent | 8 | yes | 2 runs for that hand |
| EAR | percent right | 10 | yes | 5 rounds |
| CLAP | percent of beats | 10 | yes | 2 drum steps |
| PLAY_HINTS | percent of stuck moments needing the app to play | 10 | no | 4 stuck moments |
| HESITATION_MS | mean ms to find a key (wait mode) | 150 | no (down is good) | 10 notes |
| TEMPO_SPREAD | spread of Perform offsets from the beat, ms | 10 | no (down is good: steadier) | 2 Perform runs |

Example: `Trend(DAYS, now 5.0, before 2.0, UP, good = true, periods = 2)`: "Five days this week, up from about two."

## Flags, why they are normal, and their candidates

The plan's tone rule: every "hard" comes with why it is normal and a next step. The second column is the explanation a
writer should pair with the flag; the last column is what the engine suggests (from the menu below).

| Flag | When | Why it is normal (for the writer) | Candidates |
|---|---|---|---|
| STUCK_LESSON (lessons: one; value: most tries; of: steps let go) | a step let go after three tries or tried three or more times and not finished at two stars or more after it; or a lesson finished only at one star twice or more | new skills take a few days to settle; the app lets a step go on purpose so nobody is stuck | SET_NEXT {lesson} (UNSTICK: fresh tomorrow), PRACTISE_TOGETHER {lesson} |
| STUCK_SONG (songs) | three or more runs, none at two stars | songs come in pieces | PRACTISE_TOGETHER {song} |
| NOISY_ROOM (value: events; of: sessions) | three or more room events in a week or events in two sessions; two in a session or day | the microphone hears the whole room | SETTING quietRoom when the noise watchdog fired; SETTING micEasier {value: new offset dB} when only a stage heard sounds without a key (likely soft playing) and the offset is under 9 dB |
| HINT_DEPENDENCE (value: third-step hints; of: hinted moments; lessons/songs where most) | four or more times the app played the key, 30 percent of stuck moments or more | hints are how the app teaches | PRACTISE_TOGETHER {lesson or song} |
| PAGE_STRUGGLE (kid; page; lessons) | three lessons of one page stuck or at one star | a page can be a big step | FRESH_START {page} (FOCUS) |
| READY_FOR_LETTERS (kid, pre-reader; lessons; value: mean stars x10; of: samples) | letter lessons (hall-1 to hall-4) at 2.5 stars or more, 4 samples, on 3 days spanning a week, within two weeks, nothing let go | they have been reading the letters already | SETTING reader {value: true} |
| READY_FOR_ROAD (kid; page and/or lesson) | the last page (the fair) finished, its finale or fair-4, within two weeks, no road lesson since | | SET_NEXT {} (NEXT_STEP: the road's first lesson) |
| CRUISING (lessons, page of the latest) | three or more first finishes averaging 2.67 stars or more, no let go, no third-step hint | | kids on a page before the last: PACE_UNLOCK {page} |
| REPLAYING_NOT_MOVING_ON (value: replays; lessons) | three or more replays and no first finish | replaying favourites is how kids build confidence | SET_NEXT {} (MOVE_ON: the path's next lesson) |
| LOW_STARS (lessons) | first finish at one star, not stuck | first runs are often rough | REPLAY_FOR_STARS {lesson} |
| LEFT_HAND_BEHIND (value: left; of: right) | left hit rate 15 points or more under the right, two runs each | the left hand is nearly always the second hand | PRACTISE_TOGETHER {song, else lesson} |
| FAR_MISSES (value: far; of: all wrong) | 10 or more far misses, 60 percent or more, colour notes off | finding keys comes before playing them | SETTING colourNotes {value: true} |
| MASTERED_SONG (songs) | three or more runs at three stars | | kids: TEACHER_ASSIGN {after: song} (NEW_MUSIC) |
| SCATTERED (value: levels; lessons: latest; page) | lessons from three or more levels or pages, mean stars under two | | kids: PACE_FOCUS {page} |
| TOY_HEAVY (value: sessions; of: sessions) | 60 percent of a session's time in toys (3 minutes or longer), in two sessions (one for SESSION) | play is music too | SET_NEXT {} (ROUTINE: a lesson first next time) |
| LONG_DAYS (kid; value: days; of: the minutes line) | days on screen at three times the goal and 30 minutes or more: two in a week, one in a day or session; bedtime off | enthusiasm; a soft stop helps some families | SETTING bedtime {value: true} |
| PRACTICE_DIPPED (WEEK; value: days; of: usual days) | two or more days under the usual, or half the usual minutes (usual from two earlier weeks or more) | weeks vary | PRACTISE_TOGETHER {} (ROUTINE) |
| HESITATES_ON (value: the key, a MIDI note number, 60 is middle C; of: its typical wait in ms; lessons/songs where most) | one key whose slowest find was 2 s or more in three runs or more, typically twice the person's usual wait or more (ten notes timed); the most often slow key | a key the hand still looks for is a key it is learning, and the pause shrinks a little with every find | SETTING colourNotes {value: true} when colour notes are off; PRACTISE_TOGETHER {lesson, else song} (TOGETHER) |
| LEFT_HAND_LAGS (value: left hand's mean wait ms; of: the right's; lessons/songs, slowest left first) | both hands timed on eight notes or more, the left 500 ms or more slower and 1.5 times the right or more; not when LEFT_HAND_BEHIND is flagged | the left hand is nearly always the second hand, and it learns where the keys are a little later | PRACTISE_TOGETHER {song, else lesson} (TOGETHER) |

Grown-ups (profileKind ADULT) never get READY_FOR_LETTERS, READY_FOR_ROAD, PAGE_STRUGGLE or LONG_DAYS, and never the
candidates SETTING reader or bedtime, FRESH_START, PACE_FOCUS, PACE_UNLOCK or TEACHER_ASSIGN. Everything else has the
same shape.

## The action menu (ActionId) and params

| Action | Params | What the screen does (step 3) |
|---|---|---|
| SET_NEXT | `lesson` (optional; none = the path's next lesson) | pins the lesson as the next stone for a day |
| REPLAY_FOR_STARS | `lesson` | opens it again |
| PRACTISE_TOGETHER | `song` or `lesson` (optional) | Duet with the grown-up on the left hand (a lesson: the grown-up sits in; none: the grown-up picks) |
| FRESH_START | `page` | restarts the page, two confirmations |
| PACE_FOCUS | `page` | focus mode: later pages hidden for now |
| PACE_UNLOCK | `page` (the current one) | opens the next page early |
| SETTING | `setting` = reader, bedtime, colourNotes, micEasier, quietRoom; `value` | one tap: Profile.reader, BedtimeStore.bedtime, Profile.colourNotes, Profile.micOffsetDb = value (dB), MicInput.wrongFlash off |
| TEACHER_ASSIGN | `after` (a song mastered; the parent picks the new one and may write a note) | a song on the shelf with a note |

Each is behind the parent gate and reversible; when one is taken, call `Briefs.action(actionId.name, params)` so it shows
in the next brief's `actions`. RecommendationKind (UNSTICK, MOVE_ON, POLISH, TOGETHER, ROUTINE, SETTINGS, NEXT_STEP, FOCUS,
NEW_MUSIC) is the heading a writer may use.

## Praise (in this order of specialness)

Sorted by `PRAISE_ORDER` (Findings.kt): the enum's order with the kinds added later in their place, so the enum still only
grows at the end.

| Kind | value | ids | Example |
|---|---|---|---|
| FINALE | | page | "Finished the pond, fireworks and all." |
| FIRST_CHORD | | lesson | "First chord ever: C, E and G together." |
| STUCK_WITH_IT | lessons | lessons | "Copy the frogs took four tries and ended on three stars." |
| SHINY_STICKER | count | | |
| GOAL_STREAK | days in a row (3 or more) | | |
| THREE_STARS | lessons | lessons, latest first | |
| TIMING_IMPROVED | ms now | | |
| STEADIER_TEMPO | spread ms now (trend TEMPO_SPREAD down) | | "A steadier beat than before." |
| ACCURACY_IMPROVED | percent now | | |
| MISSES_CLOSER | percent next door now | | |
| FINDS_KEYS_FASTER | mean wait ms now (trend HESITATION_MS down) | | "Keys are coming quicker." |
| EAR_IMPROVED | percent now | | |
| LEFT_HAND_IMPROVED | percent now | | |
| CONSISTENT | days (4 or more in the last seven, or 3 and more than usual) | | |
| CLEAN_SONG | songs | songs | |
| RARE_STICKER | count | | |
| NEW_LESSONS | lessons | lessons | |
| QUEST_DAY | days with all three quest stars | | |

## Stats (the full report)

- `days`: seven `DayStat(date "2026-10-21", minutes, practiceMinutes, sessions, goal)`, oldest first, ending on the
  range's last day.
- `lessons`: every lesson played in the range, in the order first played: `LessonStat(lessonId, stars (best finish,
  null when not finished), tries, letGo (steps), finishes, minutes)`.
- `songs`: `SongStat(songId, runs, bestStars, bestAccuracy)`, most played first.
- `skills`: one `SkillSeries(kind, points)` per SkillKind (STARS, ACCURACY, TIMING_MS, NEAR_SHARE, LEFT_HAND, RIGHT_HAND,
  EAR, CLAP, PLAY_HINTS), four weekly points oldest first ending with the range, null for a week with too little to
  measure (the same minimums as the trends).
- `toys`: `ToyStat(toy jam|pupSays|clapAlong, opens, minutes)`; `timeOfDay`: `PartStat(part, minutes, sessions)` for
  morning (5 to 12), afternoon (12 to 17), evening (17 to 21), night.
- `noisyRoom`: room events; `hints`: `HintStats(glow, point, play, octave)`: stuck moments ending at each hint step, and
  octave hints.
- `totals`: minutes, practiceMinutes, sessions, daysPractised, lessonsFinished, newLessons, songRuns, stickers,
  rareStickers, shinyStickers, questStars, goalDays, earRounds, earCorrect.

Added for the email brief (2026-10-21), from each run's note-by-note detail (`BriefEvent.LessonStep.detail` and
`SongRun.detail`, engine/brief/RunTally.kt) and the wrong-key pairs. Keys are MIDI note numbers (60 is middle C). All have
defaults, so findings stored before them read back with nothing in them.

- `keySlips`: the slip map, `KeySlips(midi, near, far)` for every key wanted that drew a wrong key, low to high (near: the
  key struck was two semitones or closer). `topSlip`: the most common `SlipPair(expected, played, count)`.
- `notesDue`, `skippedNotes`: notes that came due in lesson steps and songs, and those never played (let go by the
  Playground, missed in Perform, or left out of a chord of four or more by the microphone's slack).
- `hesitation`: `HesitationStat(meanMs, notes, worstKey, worstMs)`, the wait from a note coming due to its key being played
  (wait mode only; one wait counts at most 20 s), and the key with the longest typical wait (the mean of its slowest find
  in each run, keys seen in two runs or more preferred). Null when nothing was waited for.
- `tempoSpreadMs` over `tempoRuns`: tempo steadiness, the mean standard deviation of Perform runs' offsets from the beat
  (weighted by the notes graded; a run needs four graded notes). Lower is steadier; the run's own lean early or late is
  timing (TIMING), not steadiness.
- `left`, `right`: `HandStat(notes, clean, hesitationMs)` per hand: notes that came due, the percent played with no wrong
  key charged to them (not let go, not missed; null under eight notes), the hand's mean wait. Null for a hand with none.
  In Perform a note is clean when its group was played in time (wrong keys cost nothing there, as in the score).
- `hintsBefore`: the period before's `HintStats` (last week for a WEEK, yesterday for a DAY, the session before for a
  SESSION), null when nothing was played in it. A run with more than 32 stuck waits counts them all (`RunDetail.hintSteps`).
- `clap`: `ClapStat(onBeat, beats, steps)` from the drum steps (game CLAP: a step's notes are the beats with a clap, its
  rounds the beats), null with none. Clap along keeps no score by design.
- `ear`: `EarStat(game, rounds, correct)` per ear or chord game, most played first.
- `weeks`: practice minutes in each of the four weeks ending with the range, oldest first.
- `sessionMinutes`, `sessionMinutesBefore`: the mean session's minutes on screen in the range and in the periods before.
- `mix`: `PlayMix(lessons, songs, toys)`, minutes on screen in lesson steps, songs (Listen included) and the toys.

## A whole example (WEEK, a kid who does not read yet)

```json
{"profileKind":"KID","period":"WEEK","range":{"from":1792472400000,"to":1793059200000},"reader":false,"goalMinutes":5,
 "facts":[{"kind":"PRACTICE_MINUTES","value":42,"of":50,"lessons":[],"songs":[]},
          {"kind":"DAYS_PRACTISED","value":5,"of":7,"lessons":[],"songs":[]},
          {"kind":"NEW_LESSONS","value":2,"of":null,"lessons":["pond-4","pond-3"],"songs":[]}],
 "trends":[{"kind":"DAYS","now":5.0,"before":2.0,"direction":"UP","good":true,"periods":2}],
 "flags":[{"kind":"STUCK_LESSON","value":3,"of":1,"lessons":["pond-5"],"songs":[],"page":null}],
 "candidates":[{"recommendation":"UNSTICK","action":"SET_NEXT","params":{"lesson":"pond-5"},"because":"STUCK_LESSON"},
               {"recommendation":"TOGETHER","action":"PRACTISE_TOGETHER","params":{"lesson":"pond-5"},"because":"STUCK_LESSON"}],
 "praise":[{"kind":"STUCK_WITH_IT","value":1,"of":null,"lessons":["pond-3"],"songs":[],"page":null},
           {"kind":"CONSISTENT","value":5,"of":null,"lessons":[],"songs":[],"page":null}],
 "actions":[],"settings":[],"stats":{...}}
```

One template reading of it: "Mia played on five days this week, up from about two (42 minutes). She finished Copy the
frogs and Drum on a lily pad. Ta and ti-ti is still tricky: the pup let one step go after three tries, which is how the
app keeps things moving while a new rhythm settles. Set it as tomorrow's first stone, or sit with her for it. Praise:
she stuck with Copy the frogs until it gave her three stars."

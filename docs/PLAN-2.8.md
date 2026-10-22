# 2.8.0 plan: one email place, Play corner fix, random game keys, MJ placeholders, three layouts

Roy, 2026-10-22. Grounded in the code as of 2.7.2. Nothing removed; everything hidden sits behind the layout mode.

## 1. One email setting (not per child under Family)

Today: Settings > "Briefs and email" is a paragraph pointing at Family > child > "Email the brief" (`ui/SettingsScreen.kt:213`,
`ui/family/FamilySettings.kt:93`). The address, code and cadence live per child in `EmailSettingsScreen`.

Change: the address is for the parent, not the child. One address, confirmed once.

- `data/EmailStore`: move `address`, `confirmed` to a family-level store (`email.family`); keep per-child `daily/weekly/slot`
  only. Migration: first confirmed child address becomes the family address.
- Settings > "Briefs and email" becomes the one place: address field + code (the existing `EmailSettingsScreen` body, minus
  the child header), then one row per child: `[avatar] Mia  Daily ✓  Weekly Sun 6 pm  [Preview]`.
- Family > child: "Email the brief" button removed; "Read the brief" keeps its top-bar "Email" button but it deep-links to
  Settings > Briefs and email (no second form).
- `BRIEFS` joins `SETTINGS_ESSENTIALS` so Simple shows it.

```
Settings
└ Briefs and email
   Your email address  [________]  Send code
   Mia     daily ✓   weekly Sun 6:00 pm   Preview
   Noah    daily ✗   weekly off           Preview
   Written by: AI (online) / phone
```

## 2. "Play corner." does nothing

In code the Today tile opens `PlayCornerSheet` (`ui/TodayScreen.kt:136,141`); the trailing stop means Roy saw it elsewhere
(tour text or the kid side). Plan: reproduce on the Fold emulator in Simple and Advanced, grown-up and kid; fix the dead path;
add a NavTest asserting every `Names.*` tile has an `OPENS` entry. Suspect: the Simple filter hides the toys the sheet lists,
leaving an empty sheet.

## 3. Games: a random key each session

`engine/arcade/ArcadePlans.kt:50-58` ladders `CDE → FIVE → OCTAVE`. Level 0 stays three keys (the scaffold), but which three
is rolled per round:

```kotlin
// ArcadeKeys
fun threeFrom(pool: List<Int>, rnd: Random): List<Int> {
    val start = rnd.nextInt(pool.size - 2)        // three neighbours, e.g. D E F or G A B
    return pool.subList(start, start + 3)
}
// ArcadePlans.balloonKeys(level, rnd): 0 -> ArcadeKeys.threeFrom(ArcadeKeys.OCTAVE, rnd); ...
```

Levels 1+ shuffle the order the cues draw from (`stream(...)` already takes `rnd`). The first ever round stays C D E (the
pup's demo names them); the seed is the round's, so a replay repeats. Test: 50 rolls cover more than one triple.

## 4. Michael Jackson placeholders (temporary, personal use)

Chords, key, tempo, sections only (`engine/chords/PopSong.kt` contract; never melody or lyrics). Add `PopSongsTemp.kt` with
`confidence = SKETCH` and a new flag:

```kotlin
data class PopSong(..., val privateOnly: Boolean = false)   // never in a public build
```

`PopFind` drops `privateOnly` songs unless `BuildConfig.PRIVATE_BUILD` (true from secrets.properties on Roy's PC, false by
default). Six to start: Billie Jean, Beat It, Man in the Mirror, Human Nature, Rock With You, The Way You Make Me Feel. One
file to delete before release; a test fails if `privateOnly` songs exist and `PRIVATE_BUILD` is false.

## 5. Three layouts, chosen at launch

Today: `Prefs.simpleMode: Boolean?` (on/off/unset), `Simple.isOn(isKid, pref, level)`, filters in `engine/nav/Simple.kt`.

```kotlin
enum class Layout { SIMPLE, ADVANCED, FULL }
// Prefs.layout: Layout?  (migrates simpleMode: true -> SIMPLE, false -> FULL, null -> ask)
```

- `Simple.kt` rules take `Layout` instead of `Boolean`: SIMPLE = today's Simple; ADVANCED = Simple plus Learn's full path,
  Practice, Songs packs, Settings keys/falling notes/wrong keys; FULL = everything (today's Advanced).
- Launch: when `layout == null` and a grown-up profile is active, a one-screen chooser before Today: three cards
  (Simple: "the first steps", Advanced: "lessons, songs and practice", Full: "everything") with one line each and a note
  "Change any time on You or in Settings". Kids never see it.
- "Switch to Advanced" prompts become a three-way picker; the tour and What's new mention three modes.
- Tests: `SimpleTest` becomes `LayoutTest`, one row per screen part per layout.

```
Welcome back, Roy. How much do you want on screen?
[ Simple ]  [ Advanced ]  [ Full ]
```

## Order and who

1. Layouts (engine rules + chooser): Opus xhigh, worktree `layout-3`.
2. Email consolidation: Opus xhigh, `email-one`.
3. Arcade random keys + Play corner fix: Opus xhigh, `arcade-keys`.
4. MJ placeholders + private flag: Sonnet high, `songs-temp` (chords only; no lyric recall).
5. Merge, tests, lint, Fold emulator pass, 2.8.0 (versionCode 19), install on the Fold: Fable.

About one evening of agent time.

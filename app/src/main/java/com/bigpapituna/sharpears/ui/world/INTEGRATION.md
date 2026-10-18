# World team (2.1, stage 2): what to wire

Branch `family-world`. The loop that makes the app sticky, for every profile: a living pup, juice on every note,
sticker drops with rarities, the mystery egg, the album, the world that grows (dog house, garden, pond, concert
audience), the treat jar, the records shelf, dress-up, "one more", jingles. Nothing shows up in the app until it is
wired below; the pup's new life (idle acts, cross-fades, note bounces) is already on wherever a `Pup` is drawn.

Files (all owned by this team): `ui/Pup.kt`, `ui/Celebrate.kt`, `ui/HitLights.kt`, `ui/motion/Juice.kt`,
`ui/world/*`, `engine/world/*`, `data/WorldStore.kt`, `res/raw/*` (from `scripts/gen_jingles.py`),
`res/drawable-nodpi/sticker_*` (from `scripts/gen_stickers.py`). Tests: `engine/world/DropsTest`, `WorldTest`,
`PupBrainTest`, `data/WorldStoreTest`, `ui/world/WorldCopyTest`, `ui/HitLightsTest` (46 new, 490 in all).

Debug (the one line in MainActivity's `first` block):
`adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es screen world --es part <part>` with part
`album | world [--es place garden|pond|concert] | map | screen | card | jar | egg | dress | reveal [--es rarity common|rare|shiny --ez eggdrop true --ez double true] | shelf | idle | onemore`,
options `--ei stickers n`, `--ei stage 1|2|3`, `--ei notes n`, `--ez egg false`, `--es wear tophat,sunglasses,bowtie`,
`--es theme light|dark`. It runs in a demo world (file `world.worlddemo`), never a real profile's.

## 1. The store, per profile (MainActivity.openApp, ProgressFiles)

```kotlin
// ProgressFiles.NAMES: add "world", so backup, import and profile deletion cover it.
val NAMES = listOf("progress", "mastery", "mic", "profile", "ear", "practice", "theory", "world")

// openApp, next to the other stores:
val world = WorldStore(this, profile.id)
world.mergeRecords(repo.all().associate { it.id to prefs.progress(it.id).bestAccuracy }.filterValues { it >= 0 })  // the shelf starts with old bests
Juice.world = world                        // every note played drops a crumb in this profile's treat jar
WorldSounds.init(this); WorldSounds.on = prefs.sound   // and again wherever Settings changes Sound
```

Provide it with the others (the outfit too, so every `Pup` in his base pose wears it):

```kotlin
CompositionLocalProvider(..., LocalWorld provides world, LocalPupOutfit provides world.state.outfit) { ... }
```

`world.state` is Compose state, so `LocalPupOutfit provides world.state.outfit` follows dress-up live. One file per
profile: `ProgressFiles.fileFor("world", id)` ("world" for the owner, "world.p3" otherwise), one JSON string under
`state` plus `saysAt:<date>` longs. Settings > Reset progress leaves the world alone (stickers are like achievements).

## 2. The root: touches wake the pups

On the root Surface (or Box) in `setContent`: `Modifier.wakesPup()` (ui/motion/Juice.kt). It watches every touch
without taking it, so the pups' idle clock restarts on any input. Without it, only notes and taps on a pup count.

## 3. Juice on every note (PlayerScreen; the detector team owns it, the integrator wires)

In `keyStruck(midi, latencyMs, velocity)`, once per note, whatever the result:

```kotlin
Juice.onStrike(midi, riseDb, sound = soundAllowed())
```

- `riseDb`: the mic's `MicEvent.Strike.riseDb` (pass it into keyStruck from the Strike branch), `Juice.riseOf(velocity)`
  for MIDI, `Juice.DEFAULT_RISE` for touch.
- `sound = soundAllowed()`: the tick follows the sampler's rule, so it never sounds while the mic listens without
  headphones (it would feed the matcher).
- An ungraded note (Listen mode, IGNORED, the Jam) gets a soft column glow: `lights.touch(midi, handColor(handOf(midi)))`.
  Right notes already flash (`lights.hit`), wrong ones glow (`lights.wrong`).
- Every pup on screen hops to the pitch (`BounceSize.SMALL` by default; `Pup(..., bounce = BounceSize.BIG)` in the
  Jam, `BounceSize.NONE` to keep one still), and the note counts as input.
- `world.flush()` when the player closes (jar notes are saved every 5; a full jar at once).

## 4. Lessons and songs: the rewards

**A course lesson finished** (MainActivity, both `onDone = { prefs.markLessonDone(l.id); progress.record(...) }`):

```kotlin
val place = KidPages.dropPlace(l.id)              // 2.2: the lesson's page's place (garden, pond, concert hall); null elsewhere
val reward = world.lessonComplete(l.id, place)    // a sticker; the egg hatches or is laid
```

Show it in LessonScreen's finish `Celebration` (skin team's file) through the new slots:

```kotlin
Celebration(..., reward = { DropReveal(reward, cardWidth = 120.dp) },
    oneMore = nextLesson?.let { n -> { OneMoreButton(world.preview(KidPages.dropPlace(n.id)), onClick = onNext!!, title = n.title) } })
```

`DropReveal` flips each card in (lesson sticker first, then the egg's, hatching), plays the rarity's jingle and
makes a pup it is given (`pup = controller`) surprised for rare and shiny. `world.preview()` must be read after
`lessonComplete` (it previews the lesson after this one, the same roll that lesson will make). With `oneMore` the
Celebration's own primary becomes a quiet text button under it. For a reward with more (an egg laid, the dog house
grown, something new to wear), add `RewardExtras(reward)` under the reveal, or show the whole moment over the screen
with `DropRevealOverlay(reward, onDone)` instead.

**A song played to its end** (PlayerScreen, where `SongRun` is recorded; Perform passes its accuracy, Wait/Listen null):

```kotlin
val reward = world.songComplete(meta.id, r?.accuracy)     // a sticker every finish; the shelf keeps the best
```

RunRecap (skin team) gets the same two pieces: `DropReveal(reward)` under the stats and
`OneMoreButton(world.previewSong(), onClick = onAgain, title = meta.title, label = "Play it again")`.
`reward.record` (a `RecordUp`) says when the song's medal went up: `RewardExtras(reward, songTitle = { title })` shows it.

Not wired by design: chord rounds, ear games, theory and sight-reading drop nothing (the floor and the album pace are
tuned for lessons and songs).

## 5. Where the world lives (the You tab, a new screen)

- You tab (`ProfileScreen`, team B's file): one more `item` near the top: `WorldCard(world, onOpen = { tab -> screen = Screen.World(s, tab) })`.
  For a kid profile put it first (KIDS-PLAN 3.4: the album is the kid's You tab).
- `Screen.World(back, tab)` in MainActivity: `WorldScreen(world, songs, onBack = { screen = s.back }, start = s.tab, onSong = ::openSong)`
  (tabs: World with the jar and egg, Album, Dress up, Records). It handles Back.
- Pieces on their own, for any layout: `AlbumView(world.state.album)`, `PlaceScene(place, state)`, `WorldMap(state)`,
  `TreatJarPanel(world)`, `DressUp(world)`, `RecordsShelf(world.state.records, songs, onSong = ...)`, `EggView(look)`,
  `DogHouse(stage)`, `StickerImage(sticker)`, `MedalDisc(medal)`.

## 6. The Playground on the world (Learn path, playground team and integrator)

`WorldMap(world.state, stop = { n -> /* the Playground lesson n button */ })` draws the garden, pond and concert hall
one under the other, each with a trail through its four stops; `stop(n)` is centred on lesson n's anchor (1..12, left
to right in each place). `PlaceScene(place, state, stop = ...)` for one place. Anchors as shares of a scene
(`SCENE_RATIO` wide to tall): `WorldAnchors.lesson(n)`, `stops(place)`, `HOUSE`, `JAR`, `EGG`, `STAGE`,
`item(sticker)`, `seat(i)`; `Modifier.centerAt(x, y)` places anything on one inside a scene. Without `stop` each stop
shows a numbered marker. The jar and the egg sit in the garden (`onJar`, `onEgg` make them tappable).

## 7. Family wall and the crown of the day (foundation's files, integrator)

```kotlin
familyWallEntries(this@MainActivity, roster, latestSticker = { id -> WorldStore.latestSticker(this@MainActivity, id) })
val crowned = WorldStore.crownOfTheDay(this@MainActivity, roster.profiles.map { it.id })   // profile id or null
```

The crown is the longest Pup says pattern today among profiles (a tie goes to whoever got there first). Suggested: a
small crown on that avatar in FamilyWall, and `pup.react(PupReaction.CROWN)` when the player takes it.

## 8. Pup says results (play team)

`PupSaysResult(profileId, best, day = LocalDate.now(), atMillis = now)` is defined in engine/world/Crown.kt. At the end
of every Pup says game, for each player:

```kotlin
val result = PupSaysResult(player.id, best)
if (player.id == world.profileId) world.pupSays(result) else WorldStore(context, player.id).pupSays(result)
```

The store keeps each day's best (14 days) and when it was reached; results for another profile are ignored by a store.

## 9. Reactions and moods for other teams

```kotlin
val pup = rememberPupController()                 // idle = false keeps the pose (no acts, no sleep)
Pup(Pose.WEAR_BASE, controller = pup)             // or PupSays(pose, line, controller = pup)
pup.react(PupReaction.HEAD_TILT)                  // kids: a wrong key (skin team's onWrong), instead of any red
pup.react(PupReaction.PEEK)                       // a hint is coming (detector team)
pup.react(PupReaction.GOODNIGHT)                  // the session goodbye (skin team); he sleeps until the next input
pup.react(PupReaction.CLAP | DANCE | FLY | TREAT | SURPRISED | CROWN | THINKING | SHH)
pup.mood = PupMood.THINKING                       // held while an ear game waits for an answer
pup.mood = PupMood.SHH                            // held during a listening step; PupMood.NONE to let go
```

A reaction shows for its time (1.3 to 3.2 s) and counts as input; a mood holds and keeps idle acts and sleep away.
Without a controller every `Pup` makes its own: idle acts after 10 s without input (sneeze, tail chase, butterfly,
scratch, every 15 s, never the same twice running), asleep at 60 s with z's, awake on the next touch or note. Use
`Pup(..., idle = false)` where the pose carries meaning that must not change (none found so far).

Star chimes on the recap (skin team): `WorldSounds.star(i)` for star i (0, 1, 2; each a step brighter).

## 10. Dress-up shows where he sits in his base pose

The outfit is drawn only over `Pose.WEAR_BASE`. Suggested homes for the base pose so kids see their pup dressed: the
You hero when nothing else applies (instead of WELCOME) and the Songs greeting's resting pose. Items open at 1, 3, 6,
10, 15 and 22 different stickers (bow tie, sunglasses, party hat, scarf, headphones, top hat), one per head, eyes and neck.

## Logs (tag SharpEars)

`world: lesson <id> -> pond-3 shiny lesson new, egg laid, house 2, unlocked partyhat, album 9/30, lessons 6`,
`world: song <id> 88% -> ..., record bronze to silver`, `world: treat jar full`, `world: jar tipped, treats 3`,
`world: outfit tophat, bowtie`, `world: pup says best 6 on <date>`, `world sound: jingle rare|star 2|munch|...`,
`pup #3: idle sneeze|asleep|awake|react SURPRISED|mood THINKING`.

## Decisions to know

- Rarity belongs to the sticker (six common, three rare, one shiny per place); a roll picks the rarity (70/25/5),
  then a sticker of it. The floor counts every drop (lessons and songs): never more than three in a row without a
  rare or shiny. With the floor the long-run shares are about 60.5/32.9/6.6 (tested over 10k rolls).
- Gaps are preferred 4 times in 5, so doubles turn up now and then; drops without a place fill the garden first, then
  spill over. A whole album takes about 53 drops on average (31 to 123 over 60 seeds, DropsTest), the three shinies
  being the long pole; with a sticker per lesson and per song that is a few weeks of daily play. Doubles show as "x3".
  If it fills too fast in practice: lower `Drops.PREFER_NEW` or add a second page (KIDS-PLAN 4).
- An egg is laid on every third finished lesson (replays count) and hatches on the next one, one rarity up. Songs drop
  a sticker every time they are played to the end (no losers), never eggs.
- Drops are deterministic from a per-profile seed and the history, so "one more" can show the next sticker's shape;
  a song played in between changes what the next lesson drops (the preview is read when shown).
- The jar holds 200 notes and only tips when full; a full jar stays full.
- The scenes tick at 20 fps (clouds, smoke, ripples), not every frame; still with animations off. Cross-fades are an
  instant swap with animations off; idle acts still change pose (a swap, not motion).
- Placeholder art: stickers from scripts/gen_stickers.py and code-drawn accessories, dog house, jar and egg. The
  Gemini stickers drop in under the same names (`sticker_<place>_<n>.webp`, 512 px square, transparent, white border).
- Sounds: 44.1 kHz WAVs (about 450 KB together). The tick sounds only when the integrator passes `soundAllowed()`.

## Frame cost (emulator-5556, swiftshader, three emulators on one host: relative numbers only)

Each scene draws its still parts once into a bitmap (per size, theme, stage, egg) and redraws only the sky, clouds,
smoke, flag and ripples at 20 fps; shiny stickers sweep for 1.4 s then rest 2.6 s (an offscreen layer only while
sweeping); the egg rocks for 0.8 s every 3.2 s. Measured p50 frame times over 8 s: Learn tab (before any of this)
26 ms; concert hall 26 ms; garden at stage 3 with egg and a shiny 48 ms (93 ms before the caching); album with 22
stickers 44 ms. Measure on the Fold with the profile build before release (CLAUDE.md), as for the player.

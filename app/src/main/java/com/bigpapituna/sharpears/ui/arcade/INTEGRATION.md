# The kid arcade (2.7): what is here and what to check after a merge

Branch `arcade`. Roy: "I want kids to have games where they play keys and pop balloons, or play keys that make things grow
or build; not only lessons but games that help them remember what key is where, work their dexterity, and potentially
learn songs." Nine games, a hub, one entry tile on the kid's Play sheet, a quest task, stickers, sparkles and a brief event.

## Files

| File | What |
|---|---|
| `engine/arcade/Arcade.kt` | `ArcadeGame` (the nine, in unlock order), `Target`, `Side`, `Cue`, `RoundResult`, `ArcadeStars` (the star rule), `ArcadeLevels` (next level), `ArcadeKeys` (C3 to C6, at most 12), `ArcadeRound`, `RoundPlan`. |
| `engine/arcade/StreamRound.kt` | Balloons, treats, moles: targets on a clock, a key hits the oldest live one (pairs need both keys within 500 ms), two gone by light the next ones. |
| `engine/arcade/CallRound.kt` | Garden, tower, frogs, fireworks, painter, bridge: lines shown first, heard note by note; a slip or a quiet beat is a stuck beat (1 lights the key, 2 the pup plays it, 3 lets it go). |
| `engine/arcade/ArcadePlans.kt` | Every game's rounds by level (keys, speed, pairs, walks, crossings, planks, songs). |
| `engine/arcade/ArcadeSongs.kt` | Hot Cross Buns, Mary, Twinkle, Ode to Joy from engine/Exercises.kt, as bites and phrases. |
| `engine/arcade/ArcadeBook.kt` | The per-kid state (bests, levels, rounds, stickers, painted songs, unlock base), `ArcadeUnlocks`, `ArcadeQuest`. |
| `engine/arcade/ArcadeLines.kt` | Every line said (`spoken()`, in SpokenCatalog.kidLines) or shown (`all()`, the copy test). |
| `data/ArcadeStore.kt` | The book in the profile's "play" file (key `arcade`), so backup, import and profile deletion already cover it. |
| `ui/arcade/ArcadeHub.kt` | The hub: a tile a game with its best stars; locked ones are presents, the next with a ring. `GamesPicture` for the Play sheet tile. |
| `ui/arcade/ArcadeGameScreen.kt` | A round: intro, demo, the frame loop, the ear (`rememberStageEar`, strict keys), touch and MIDI, sounds, practice time, the finish. |
| `ui/arcade/ArcadeKit.kt` | `ArcadePlay` (the round on screen), `keyBadge` (colour with animal or letter), `RoundClock` (a sun, no numbers), scenery. |
| `ui/arcade/StreamFields.kt`, `CallFields.kt`, `SongFields.kt` | The nine playfields. `GameSpec.kt` says which one a game draws. |
| `ui/arcade/ArcadeFinish.kt` | Stars landing, the pup, his words, the sticker flipping in, Again and Games. |

Touched outside: `MainActivity.kt` (two screens, routes, the debug route, the round's end), `ui/kid/KidHome.kt` (`onGames`),
`ui/kid/KidPlaySheet.kt` (the Games tile), `ui/play/QuestCard.kt` (`opens` for the new kind, one line),
`engine/play/Quest.kt` + `PlayBook.kt` + `data/PlayStore.kt` (the quest hook), `engine/world/World.kt` + `data/WorldStore.kt`
(`gameRound`), `engine/world/LevelStickers.kt` (the arcade page) + `ui/world/LevelStickerRes.kt` + `ui/world/Album.kt`,
`engine/brief/*` (the event, the stats, the fact and its phrasings, the prompt), `engine/SpokenCatalog.kt`,
`ui/playground/FairStages.kt` (`balloon` and `burst` made internal, reused), `scripts/gen_stickers.py`.

## Entries

- The kid's Play sheet (`KidPlaySheet(onGames = ...)`): a wide Games tile under Jam, Pup says and Clap along, wired through
  `KidHome(onGames = ...)` to `Screen.Arcade(home)`. The `Toy` enum (ui/play/PlayRow.kt) is untouched: the arcade is not a Toy.
- Pup's quest: on an even day one of the open games replaces "Copy the pup three times" (`ArcadeQuest.pick`); its task's
  `opens` is "games" and its `ref` the game's id; KidHome's `onQuestTask` opens that game's round over the hub.
- Grown-ups have no door (the adult Play row is the other agent's). `Screen.Arcade` works for any profile if one is added.

## What a round's end does (MainActivity, `Screen.ArcadeRound`'s `onRoundEnd`)

1. `ArcadeStore.record`: best, next level (up on three stars, down when the kid played under a third), rounds, the
   sticker flag on the first three stars, a painted song from two stars.
2. `WorldStore.gameRound`: the sparkles ledger counts the round like a song played to its end (two sparkles, inside the
   five songs a day), no sticker drop; the game's sticker (`LevelStickers.arcade(id)`, page "arcade") into the tray on its
   first three stars. Items the ledger opens wait for the next lesson or song finish (the arcade shows no wardrobe reveal).
3. `BriefEvent.GameRound(game, stars, keys, level, ms)` ("game" on the wire). Insights turns them into `Stats.games` and the
   `FactKind.GAMES` fact ("Played Frog hop to three stars this week."); the kid card and the headline have lines for it.
4. `QuestEvent.GameRound(id)`.
Also: `ToyOpened(ARCADE)` for the hub's and each round's time on screen, and the round's time counts as practice (as a
stage's does), so the goal, the paws and the ledger's minutes see it.

## The unlock order

Balloon pop, Grow the garden, Feed the pup open at once; then Build the tower, Boop the moles, Frog hop, Fireworks, Song
painter, Rainbow bridge, one for every 40 sparkles the ledger gains after the hub's first open (`ArcadeUnlocks.STEP`, about
two sessions of a typical kid). The hub syncs the ledger as it opens and the pup says "A new game is ready!" once.

## Check against the adult navigation change (the parallel branch)

- MainActivity: the `Screen` sealed interface gained `Arcade` and `ArcadeRound`; `depthOf`, `routeOf`, `screenAt` and
  `keepsScreenOn` have their branches. If the other branch moves screens or the `when (s)` that draws them, carry the two
  branches over (they sit just before `Screen.MicSetup`'s), plus the `arcadeStore` and `questGame()` lines after
  `KidStore(...)` and the debug route after `route == "finale"`.
- `KidHome(..., onGames = ...)` is passed in the kid home's call; KidHome and KidPlaySheet are kid-only files.
- `ui/play/QuestCard.kt`'s `opens` has a `PLAY_GAME` branch; if the other branch rewrote the quest card, keep the branch
  (the `when` is exhaustive, so the build says so).
- `ToyKind` gained `ARCADE`: any new exhaustive `when` over it needs a branch.
- PromptSnapshotTest's snapshots were refreshed and `Prompts.VERSION` is 3 (the findings carry `games`).

## Debug routes (debug builds; add `--es profile p1 --ez awake true`)

- `--es screen arcade`: the hub.
- `--es screen arcade --es game balloons|garden|feed|tower|moles|frogs|fireworks|painter|bridge [--ei level n] [--el seed n]`
  `[--es inject synth:60,62,64@700~3000]`: a round (injection: the Playground's, PianoSynth or a WAV).
- `--ez bot true` with a game: the screen plays the right keys itself as touches (a stream's at 40% of its way, a call's
  0.7 s into its beat), so a round can be watched, shot and finished on an emulator with no piano.
- `--ei stars 1|2|3` with a game: straight to the finish with such a round (it is recorded: the first three stars earn the
  sticker, as a real round would).

## Logs (tag SharpEars)

`arcade: <game> level n starts, keys [...]`, `arcade: touched|heard|named|midi <key> -> Hit|Slip|Half|...`,
`arcade: <game> level n ended: h of o, s slips, l lines, n stars, ms`, `arcade: <profile> <game> round n: n stars, best b,
level a to b[, sticker][, painted]`, `arcade: <profile> opened <game> (n open)`, `world: arcade <game> round, ...`,
`arcade: practice +n s (<game>)`.

## Not proven

Everything on a real piano with a child: the stream speeds, the tower's beat, two keys at once on a real piano (PianoSynth
only: ArcadeHearingTest), and whether the emulator's host mic leaves the rounds alone. Art is placeholder (stickers by
gen_stickers.py, scenes drawn in code).

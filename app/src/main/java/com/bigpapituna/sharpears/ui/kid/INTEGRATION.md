# The kid home (2.2): what is here and what to check after a merge

Branch `kidhome`. Roy's verdict on 2.1 with the kids: the kid experience was "all over the place" (the grown-up app with a
skin: five tabs, the family wall, the Play row, Practice today, the quest card, Chords, Practice, You). A kid now gets one
screen, the pup's world. Grown-ups keep the 2.1 app exactly (tabs, wall, Play row, Practice today, everything).

## Files

| File | What |
|---|---|
| `ui/kid/KidHome.kt` | The kid's home: the map, the pup on the next stone ("Let's go!"), the signpost, the three big buttons (Play, Songs, My stickers), the whistle (grown-ups' corner, behind the parent gate: who's playing, Settings, Microphone setup), the quest sheet, grown-up mode offered once after Beginner. |
| `ui/kid/KidMap.kt` | A page's scene with its stones (`PageScene`, `StoneView`), the gate, the big kids' road and the road ahead behind a barrier. |
| `ui/kid/Signpost.kt` | Pup's quest as a wooden signpost with three stars. |
| `ui/kid/KidPlaySheet.kt` | Play: Jam, Pup says, Clap along as three big tiles. |
| `ui/kid/KidSongs.kt` | Songs: only the colour songs, big tiles with the tune's opening as coloured balls, no stats. |
| `ui/kid/KidStickers.kt` | My stickers: the album, dress-up, records, this week's paws, as pages. |
| `ui/kid/Finale.kt` | A page's finale: night, fireworks, the pup bowing, the name said, the album page, the certificate, where the path goes on. |
| `ui/kid/Certificate.kt` | The certificate (Android Canvas, 1350 x 1080 PNG) and "Send to someone" through `Share.files`. |
| `engine/kid/*` | Pure rules, JVM-tested: `KidMap` (next stone, stone states, the road), `Finale` (trigger, what comes after), `LessonStars`, `KidSongs`, `KidPages` (page to scene, drop place), `KidLines`, `CertificateWords`. |
| `engine/world/Trail.kt` | Twelve stops per scene in three winding rows, the trail's line, the gate's spot. `WorldAnchors` (ui/world/Scenes.kt) reads it. |
| `data/KidStore.kt` | Per profile file `"kid"` (in `ProgressFiles.NAMES`): best stars per lesson, finales played, the road line said. |

Also changed: `engine/CoursePath.kt` (every page's level is the Playground), `ui/world/Scenes.kt` (`WorldAnchors` per page,
`PlaceScene(stops, stop(i), overlay)`, the pond's boardwalk, no numbered markers when `stop` is null), `ui/LearnScreen.kt`
(one scene per page on the mapped path, which only a kid reached and a kid no longer sees), `ui/LessonScreen.kt` (`onDone`
gets the lesson's stars; `finishLabel`, `holdGoodnight`; a kid's finish shows the stars over the drop), `MainActivity.kt`,
`engine/SpokenCatalog.kt` (`KidLines.fixed()` in the kid lines), `data/Prefs.kt` (`forgetLessonDone`, debug seed only),
`ui/world/WorldDemo.kt` (`--es part trail`).

## What the pages agent's pond and concert hall need from here

Nothing, if each new page is a `PlaygroundPage` in `PLAYGROUND_PAGES` (path order) whose `levelId` names a `CourseLevel`
in `COURSE` (anywhere in the list: `CoursePath` orders the Playground by the pages, not by `COURSE`) and whose `place` is
`POND` or `CONCERT_HALL`. The map draws a scene per page and a stone per lesson (twelve fill a scene; fewer leave the rest
of the trail empty; more squeeze in rows), drops stickers from the page's place, plays the finale on `lastLessonId`, and
walks on to the next page's first stone. `Finale.after` and the finale's second line ("Next stop, the pond!") follow the
list.

Three things to know:

- **Big kids** (readers): `BIG_KID_LESSONS` applies to a page only if it names lessons on it. Today it names five garden
  lessons, so a big kid plays those, then walks the pond and the hall whole. Add pond or hall picks to the list to
  shorten those pages for big kids too.
- **A new `Place` value** (a fourth place) needs a branch in `KidPages.worldPlace` and art in `ui/world/Scenes.kt`; the
  `when` is exhaustive, so the build says so.
- **Drops follow pages now**: a garden lesson drops garden stickers (2.1 dropped pond stickers from lessons 5 to 8 and
  concert stickers from 9 to 12 by number through `Place.ofPlaygroundLesson`, removed in 2.2.0). Twelve garden lessons
  plus the eggs usually fill most of the garden page by its finale, which is what the finale shows.
- **Stickers keep off the stones** (2.5): the kid sticks stickers in each scene's named spots (engine/world/Decor.kt),
  which sit clear of the stones, their stars and the gate; DecorTest fails if a moved spot or trail row would cover one.
  Decorate mode (ui/kid/Decorate.kt): a tap on a sticker in My stickers' tray, or a long press on a spot, starts it;
  the tray replaces the three buttons; tap a sticker then a spot (or the reverse), or hold and drag; Done ends it.

## A kid's lesson finish, in order (2.5.0)

The stars; the stone's own sticker, its shape turning into the sticker with the pup's line (`LevelStickerEarned`, first finish
only); the drop flipping in (and the egg); once it has landed the rest (egg laid, dog house, record) and last something new to
wear (`WardrobeReveal`, which scrolls itself into view, "Keep it on?"); then the pup's card when one is due this session,
whichever button the kid takes, "One more" included (its Continue then says "One more"). Songs keep their recap's order (the
drop, the extras, the reveal) and show the card on leaving the player.

## After merging `pages` and `kidhome`: check

Done in 2.2.0 (branch `release-2.2`): every step below, with the four new finale lines recorded (2597 in the catalog);
see CLAUDE.md "2.2.0 status".

1. A trial merge of `pages` (at f2bb6bf) into `kidhome` (done in this worktree and aborted, nothing committed) conflicts
   in three files only: `engine/CoursePath.kt` (take kidhome's: it is page-driven and gives the same order and the same
   big-kid rule as the pages branch's `isPlaygroundLevel` version, and its extra constructor parameter has a default, so
   the pages branch's CoursePathTest runs unchanged), `CLAUDE.md` and `docs/KIDS-PLAN.md` (keep both sections).
   `engine/SpokenCatalog.kt` merges by itself.
2. `.\gradlew.bat :app:testDebugUnitTest`: with kidhome's CoursePath the merged tree compiled and ran 756 tests, all
   green after `KidMapTest.theRealPagesLeadOnToBasics` stopped assuming one page. It and `MapAnchorsTest.theRealPages...`
   walk the real pages: the garden's finale leads to the pond, the pond's to the hall, the hall's to Basics.
3. Render the new kid lines: `.\gradlew.bat :app:testDebugUnitTest --tests "*SpokenCatalogTest*"`, then
   `C:\dev\voice-tools\venv\Scripts\python -I scripts\render_voice.py` (the pond's and the hall's lessons, "You finished
   the pond!", "Next stop, the concert hall!").
4. On the emulator as a kid (`--es profile <id>`):
   - `--es screen worlddemo --es part trail`: the three scenes with their twelve stops numbered.
   - `--es kidseed garden:11 --ez awake true --es lesson playground-12 --ei step 3`, Finish, "Hooray!": the garden's
     finale, then the map rolls on to the pond's first stone.
   - `--es kidseed pond:12` and the same for the pond's last lesson; `--es kidseed road` for the road after the last page.
   - A pond or hall lesson with a `Step.game` stage finishes with stars on its stone (the stage's `Report` goes through
     `LessonStars.ofRun`: a recap by Perform rules, `extraScore` on the same lines, else wrong keys).
5. Bump the version and write the release notes (not done here).

## Debug routes (debug builds; combine with `--es profile <id>`)

- `--es screen kidsongs | kidstickers [--es page album|dress|records|paws] | finale [--es page garden]`
- `--es kidseed <page>:<n>` marks the page's first n lessons done with stars (3, 2, 3, 1, ...) and forgets its finale;
  `road` walks every page and plays every finale; `reset` forgets the Playground's lessons, stars and finales.
- `--ez sleepy true` reaches today's goal and says goodnight (the map shows the pup asleep); `--ez awake true` lets a
  grown-up's one more lesson through today (the map awake whatever was played).

## Logs (tag SharpEars)

`kid home: next <id> (item n)[, rolled on from <page>]`, `kid: <id> n stars, best m`, `kid: <id> ends <page>, its finale is
next`, `kid: finale of <page> played`, `finale: <page> for <name>, n stars, album a/b, then <after>`, `certificate: packed
<file>`, `kid songs: n of m play by colour: ...`, `kid stickers: page <PAGE>`, `kid home: grown-ups switched to <id>`,
`kid debug: ...`.

## Decisions

- A lesson's stars: each graded step's passing run by the skin's `Stars` rules (Perform accuracy and timing, a stage's own
  score on the same lines, a waiting run by its wrong keys), a step let go after three tries as one star, the lesson the
  mean of its steps with a half rounded up, three for a lesson with nothing graded. The stone keeps the best. Kept for
  grown-ups too (never shown to them).
- The finale plays the first time a page's last lesson is finished (not on replays), marked played as it opens. After it
  the page counts as walked: its gaps stay open to play, but the pup moves on. After the last page the road opens and
  Basics' first unfinished lesson is the next stone, with the kid skin as before.
- Stones ahead of the pup are asleep: a tap makes the pup say "This one first!". Stones behind him that were skipped (a
  big kid's other games, gaps behind a finale) are open to play.
- The colour songs: easy, the app's own, every note a white key (19 of the library's 39 today), simplest first.
- The quest is the signpost; a tap opens the three tasks (the play team's `QuestCardContent`). The family wall, Practice
  today, Theory, Ear training and Sight-read are not on the kid home; grown-ups have them.
- Today's goodbye still shows once (after a song, a part, a lesson, or a finale); after it the pup sleeps on his stone and
  a stone opens the lesson's asleep screen, where a grown-up can allow one more through the gate.

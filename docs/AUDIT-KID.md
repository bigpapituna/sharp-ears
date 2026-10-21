# Kid-experience audit (branch `audit-akid`, 2026-10-20)

The kid's side of Sharp Ears walked as a kid would, on emulator-5558 (Fold, inner and cover screen), with Mia (p1,
pre-reader) and Leo (p2, reader) made through the real new-kid flow, and the owner (Roy). Every finding is fixed with the
smallest diff, one commit each; pure rules carry a JVM test. Screenshots: app/build/screens/akid-*.png (not committed).

Severity: **High** a kid is stuck, unheard, loses progress or reaches something unsafe; **Medium** a promise of the
design is broken (adult UI or numbers leaking, a reward or cheer missing); **Low** cosmetic.

## Findings

| # | Where | What | Sev | Fix | Test |
|---|---|---|---|---|---|
| 1 | Playground stages (PlaygroundStage) | The stages wrote no practice time; only the embedded player did. A kid who played the fair's four games or most of the night sky got no paw and never reached the goal. | High | Each stage counts its time on screen (every 15 s and at the end), as the player does (`StageTime`). 35bd788 | StageTimeTest |
| 2 | LessonScreen finish | With bedtime off (the default) the goal's once-a-day cheer was said only after a song, a Practice today part or a finale: a kid on the map never heard it. | Medium | The lesson's finish says it after the pup's lesson line; a page's last lesson leaves it to the finale. 389c9bd | UI (walked) |
| 3 | LessonScreen finish | A kid's finish offered "All lessons"; the back arrow read "Back to lessons" to TalkBack. A kid's home is the map. | Medium | "Back to the map". 6521bd5 | UI |
| 4 | Kid home, finale | A page's finale was queued in memory: with the app closed or killed between the last lesson and the map, it was lost for good (no fireworks, album page or certificate). | High | The kid home plays a finale owed when it opens (`Finale.owed`). 831ced5 | KidMapTest |
| 5 | Kid map, rows two and three | The pup's head covered the middle star of the walked stone above (noted in 2.2.1 QA). | Low | Stars drawn on a layer over the pup, his bubble and the signpost. 433215b | UI (screens) |
| 6 | Kid map | The outfit from My stickers was never shown on the kid's own map (the pup only stood, pointed or slept). | Medium | Dressed, the pup waits on his stone in his outfit. 1675fe8 | UI (screens) |
| 7 | Grown-ups' corner | "Who's playing?" was a plain row: with five or more players the last ones were squeezed to nothing and could not be picked. | Medium | The row scrolls. 04e7c3d | UI |
| 8 | Certificate, photo card, concert card | "Send to someone" opened the share sheet straight away from a kid's profile. | High | Through the parent gate while a kid plays (`guardForKids`). b38f58f | UI |
| 9 | Player in lessons, concerts, Songs | Kids skip the onboarding that sets `micDefault`, so every player step opened with the mic off: a kid at the piano was not heard. | High | A kid starts with the mic on until someone switches it off. 798f366 | UI (walked) |
| 10 | Kid's colour song | Opened the grown-ups' player: times, Loop, speed, Chords, Metronome, the debug flag, Transpose and "New song. Start with the right hand alone?". | High | A kid's bar (hands, modes, Play, position, Duet, mic, video); the song starts on the tune. 53a9a05 | UI (screens) |
| 11 | Bedtime on, asleep screen | A grown-up's "one more lesson" opened every lesson for the rest of the day. | Medium | Finishing a lesson spends the grant (`Bedtime.oneMoreSpent`). 4712200 | BedtimeTest |
| 12 | Every kid screen but the player | "Achievement unlocked" banners slid in over lessons, the map, toys and stickers: "First note" in the first game, "Goal met", "One week streak", "100 minutes", "You reached level 10". Only the level-up was held for kids. | High | Achievements unlock unseen for kids. 9031231 | Emulator: screen recording, "Goal met" over Zoe's key hunt before; none in Max's first game after |
| 13 | Falling notes (songs, lesson steps, concerts, set list) | The lane's combo pill ("x5", "x12", shattering) counted a live streak for kids. | Medium | No combo number on a kid's lane. bc656b1 | Emulator: Hot Cross Buns, 12 right notes, no pill |
| 14 | Kid's song bar | The tutorial video button stayed: "Open in YouTube" (no gate), YouTube's links and related videos, Sync and Last note. | High | No video on a kid's bar. eadd7a8 | UI |
| 15 | Kid's song recap | "Hear my run" opened the grown-ups' replay (clock, "your notes are the thin bright ones"). | Low | Not for kids. ffe8b3f | UI |
| 16 | Pup says | The phone's Back skipped Done's work: the game was not stopped, the day's best and the crown not saved. | Medium | Back leaves as Done does. 8988a78 | Emulator log "pupsays: over" on Back |
| 17 | Lesson steps (Next) | A graded step skipped with Next did not count, so a lesson skipped through ("nothing to grade") earned three stars and its drop: concerts, the recital, the sitting picks, the photo booth. | Medium | A skipped graded step is one star (`LessonStars.ofSkip`). 2ce8fe7 | LessonStarsTest.skippingEveryGameIsOneStarNotThree; emulator "kid: workshop-1 1 stars" |
| 18 | Grown-ups' corner, parent gate | A pass kept the gate open ten minutes after the grown-up handed the phone back: the whistle then opened Settings, Microphone setup and "Who's playing?" (the grown-ups' profiles) with no gate. | High | The gate shuts when the corner closes and whenever the kid's map comes back. e7c6422 | Emulator log "gate: closed ..." then "gate: asked" |
| 19 | Finale | The finale's album page kept ten slot buttons that did nothing (TalkBack stops). | Low | A slot is a button only where the album opens it. 4d96e11 | uiautomator |
| 20 | Kid map | The pup on the next stone was a nameless button; the next stone said "tap to play" while lessons slept; the whistle was 44 dp. | Low | "The pup" ("asleep"), "next, the pup is asleep", 48 dp. 2a1aa03 | uiautomator |
| 21 | Drum stage, finale (animations off) | With animations off a clap's burst was drawn fully faded (a clap showed nothing); the finale changed the pup's pose every 1.9 s while its fireworks held still. | Medium | Still rays for 0.6 s; one bow. 50fcec3 | Code (the 0.6 s burst was too quick to catch on this emulator) |
| 22 | Parent gate (animations off) | The three-second hold was a Compose tween: with the system's animations off it ended at once, so a 0.4 s press passed it. | High | The ring runs in real time whatever the scale. fd40eb2 | Emulator: 0.4 s press stays, 3.6 s passes (animations off) |
| 23 | New kid, every stage | Nothing in a kid's path asked for the microphone: on a phone that had not allowed it every stage and step silently heard nothing. | High | Making a kid's profile asks the grown-up once, if needed. c387ea7 | Emulator, mic revoked: system dialog on Go, "microphone for a new kid allowed" |
| 24 | Player octave hint | A kid heard "That's the right note, but one octave up. Try lower." | Medium | "Nearly! That one is too high. Find the same colour lower down." (recorded, kid speed). 1acc35f | OctaveHintTest.aKidHearsColoursNotOctaves |
| 25 | Noisy-room sheet | The kid's question had the grown-ups' answers ("Keep showing", "Stop for this session", "Pause listening", which turns the mic off). | Low | "No, keep them" / "Yes, stop them". 231e344 | UI |
| 26 | Any lesson or finale | Process death, "Don't keep activities" or a dark-mode switch sent a kid back to the map; the lesson restarted from its first step. | Medium | The open lesson (and step) or finale is saved with the activity. e1899a2 | Emulator: step 2, Home, am kill, launcher: "screen restored: lesson:playground-3:1" |
| 27 | Grown-up mode offer (kid map) | "Not yet" was not gated: a kid's tap put the one-time offer away before a grown-up saw it. | Low | Gated like "Grown-up mode". 256ddec | UI |
| 28 | Lesson finish, bedtime on | After a grown-up's "one more" lesson was spent, the finish still offered a "one more" button into the next, sleeping, lesson's asleep screen. | Low | "Back to the map" and no way on. 313e872 | BedtimeTest.aGrownUpsOneMoreIsOneLesson (the rule); UI |
| 29 | Garden's drum scene (rhythm visual) | "4 over 4" and "80 bpm" were drawn in the corners of a kid's drum picture. | Medium | Not drawn for a kid; the beat dots and boxes do the counting. 7c4c961 | UI (screens) |
| 30 | Night sky, Long or short (touch picture) | The picture's labels said "Legato, smooth" and "Staccato, short" to a kid. | Low | "Long" and "Short", as the workshop's picture says. fea4e96 | ArticulationLabelsTest |
| 31 | Pond, "Both paws" staff scene | A "4 over 4" was drawn on each staff of a kid's two-hand picture, as the drum scene's was (#29). | Low | Not drawn for a kid. 3676aff | UI |
| 32 | Playground stages, stars | A stage whose rounds were all let go after three tries (nothing played, or a mic that heard nothing) gave three stars: a let-go round adds no wrong key. | Medium | The stage's report carries its let-go and total rounds; half or more let go is one star, some let go two (`LessonStars.ofRun`). Committed in the integration (release-2.3.1) from the agent's uncommitted work. | LessonStarsTest.roundsLetGoCapTheStars |

## Close-out (integration, 2026-10-21)

The audit's agent was cut off before its summary. Rows 30 and 31 were committed without their rows; row 32 was left
uncommitted in the akid worktree (it compiled and its test passed) and was committed during the 2.3.1 integration. The
lesson table below was never filled in: a blank cell means not recorded, not failed. The agent left no "found, not
fixed" list; the only kid item known open is the one CLAUDE.md's 2.3.1 section carries (everything on a real piano with a
child).

Findings: 32 (High 10, Medium 13, Low 9), all fixed.

## The 76 Playground lessons

Walked = opened from its stone (or the step route where noted) and taken to the end; P = pre-reader (Mia), R = reader
(Leo). "Steps" lists what was looked at.

| Lesson | P | R | Notes |
|---|---|---|---|
| playground-1 (4 steps) | | | |
| playground-2 (4 steps) | | | |
| playground-3 (5 steps) | | | |
| playground-4 (5 steps) | | | |
| playground-5 (5 steps) | | | |
| playground-6 (7 steps) | | | |
| playground-7 (4 steps) | | | |
| playground-8 (5 steps) | | | |
| playground-9 (4 steps) | | | |
| playground-10 (5 steps) | | | |
| playground-11 (3 steps) | | | |
| playground-12 (4 steps) | | | |
| pond-1 (5 steps) | | | |
| pond-2 (5 steps) | | | |
| pond-3 (3 steps) | | | |
| pond-4 (4 steps) | | | |
| pond-5 (4 steps) | | | |
| pond-6 (5 steps) | | | |
| pond-7 (5 steps) | | | |
| pond-8 (5 steps) | | | |
| pond-9 (4 steps) | | | |
| pond-10 (5 steps) | | | |
| pond-11 (4 steps) | | | |
| pond-12 (4 steps) | | | |
| hall-1 (4 steps) | | | |
| hall-2 (5 steps) | | | |
| hall-3 (5 steps) | | | |
| hall-4 (5 steps) | | | |
| hall-5 (5 steps) | | | |
| hall-6 (3 steps) | | | |
| hall-7 (4 steps) | | | |
| hall-8 (5 steps) | | | |
| hall-9 (3 steps) | | | |
| hall-10 (5 steps) | | | |
| hall-11 (3 steps) | | | |
| hall-12 (5 steps) | | | |
| workshop-1 (5 steps) | | | |
| workshop-2 (4 steps) | | | |
| workshop-3 (3 steps) | | | |
| workshop-4 (5 steps) | | | |
| workshop-5 (5 steps) | | | |
| workshop-6 (4 steps) | | | |
| workshop-7 (4 steps) | | | |
| workshop-8 (4 steps) | | | |
| workshop-9 (5 steps) | | | |
| workshop-10 (4 steps) | | | |
| workshop-11 (4 steps) | | | |
| workshop-12 (5 steps) | | | |
| treehouse-1 (5 steps) | | | |
| treehouse-2 (5 steps) | | | |
| treehouse-3 (5 steps) | | | |
| treehouse-4 (5 steps) | | | |
| treehouse-5 (5 steps) | | | |
| treehouse-6 (5 steps) | | | |
| treehouse-7 (5 steps) | | | |
| treehouse-8 (5 steps) | | | |
| treehouse-9 (4 steps) | | | |
| treehouse-10 (4 steps) | | | |
| treehouse-11 (4 steps) | | | |
| treehouse-12 (4 steps) | | | |
| night-1 (5 steps) | | | |
| night-2 (5 steps) | | | |
| night-3 (5 steps) | | | |
| night-4 (5 steps) | | | |
| night-5 (3 steps) | | | |
| night-6 (3 steps) | | | |
| night-7 (3 steps) | | | |
| night-8 (3 steps) | | | |
| night-9 (4 steps) | | | |
| night-10 (3 steps) | | | |
| night-11 (4 steps) | | | |
| night-12 (4 steps) | | | |
| fair-1 (3 steps) | | | |
| fair-2 (2 steps) | | | |
| fair-3 (3 steps) | | | |
| fair-4 (3 steps) | | | |

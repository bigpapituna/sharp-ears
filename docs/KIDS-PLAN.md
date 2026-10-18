# Family plan (2.1): profiles, a kid skin, Pup's Playground, and the things that make kids come back

## Status (2026-10-18, 2.1.0 integrated on branch `family`)

Shipped (emulator-tested on the Fold emulator, JVM-tested; not yet on Roy's phone or in front of a child):

- Profiles (up to eight), the picker, the new-kid flow, per-profile files and backup, the parent gate, the family wall
  with paw prints, latest sticker and the crown of the day, grown-up mode offered once after Beginner.
- The kid skin: colour notes with animals, letters for readers, bites, stars without numbers, kid words, big keys,
  slower speech (0.85) with the name said on recaps, hint escalation at 4, 8 and 12 s, the goodnight at the daily goal
  and the paw calendar, the pup's head tilt on a wrong key.
- Pup's Playground: all twelve lessons with their stages (flying pup, key hunts, drum, nap, concert), first on a kid's
  path and drawn on the pup's world; an extra unit for grown-ups.
- For everyone: juice on every note, the living pup, sticker drops with the rare floor, the mystery egg, the album,
  the dog house, garden, pond and concert audience, the treat jar, dress-up, the records shelf, "one more", jingles,
  wrong keys shown yellow or red, the room-noise offer, the octave hint, Jam, Pup says for one to three players, Clap
  along, Send to someone, Pup's quest and Duet.
- Voice: every fixed line recorded (318 kid lines at 0.85); lines with a name are made on the phone.

2.2 (2026-10-18, branch `kidhome`): a kid's home is one screen, the pup's world (no tabs, wall, Practice today or quest
card): the Playground's pages as scenes with a stone per lesson and its best stars, the pup on the next stone, the quest
as a signpost, Play, Songs (colour songs only) and My stickers at the bottom, a whistle for the grown-ups' corner behind
the gate. Each page ends in a finale (fireworks, the album page, a certificate to send) and the last one opens the big
kids' road into Basics. Grown-ups keep 2.1. See CLAUDE.md, 2.2 kid home, and ui/kid/INTEGRATION.md.

Not yet:

- Tested with real kids in a real room. The clap counter, the high-and-low and key-hunt pitch tracker, soft and loud,
  the Jam's chord ear and the kid mic offset are tuned on synthetic audio only. (2.1.1: the stages and the Jam now hear
  through the note matcher with strict keys, checked on Roy's scale recording; the pitch tracker is only a fallback
  guess; every round is shown first and lets go after three tries; stuck stage moments and one Jam stretch a session
  are saved as clips for tuning. See CLAUDE.md, 2.1.1 fixes.)
- Real art: stickers, accessories, the dog house, jar and egg are placeholders (scripts/gen_stickers.py, code-drawn);
  Gemini art drops in under the same names.
- The per-kid report in the parent corner (minutes, lessons, where they got stuck) and take-turns in the ear games
  (Pup says has turns; the ear games do not).
- Frame timing on the Fold itself with the profile build (emulator numbers only so far).
- (Built in 2.2, branch `pages`: the pond and the concert hall, twelve lessons each after the garden, then Basics.
  Roy asked for the kids' path to be comprehensive; section 9 had held them back until the first page got used. See
  CLAUDE.md, 2.2 pages.)
- A second Playground page, by design (section 9). 2.2: the pond and the concert hall pages are being written on the
  `pages` branch; the kid home draws whatever PLAYGROUND_PAGES holds.

Written 2026-10-18. Status at writing: proposal, nothing built. First testers: Roy's nieces and nephews, several kids on one
phone and one piano, in a room with talking, on visits (so short sessions, new kids set up in seconds).

## 1. The question: a separate kids' section, or universal lessons?

The music is universal. Middle C, a steady beat, Hot Cross Buns and the two-black-key landmark are the same for a
six-year-old and a forty-year-old, and the current course (Basics, Beginner, Rhythm, Intermediate, Keys, Advanced,
Technique) already starts at "Sit, then shape your hand" and "Find C anywhere", which is where a child starts too.

What is not universal is the delivery:

| | Adult (today) | Child 4 to 9 |
|---|---|---|
| Step length | 3 to 6 minutes, several scenes of prose | 60 to 90 seconds, one idea, then a reward |
| Reading | note names, finger numbers, "F sharp", percentages | pre-readers: colours and animals; readers: letters only |
| Scoring | accuracy %, "New best", streaks | stars and stickers, never a number that can shame |
| Attention | a 10 minute session goal | 5 to 8 minutes, then the pup says goodnight |
| Being stuck | 5 s, then a hint | a hint, then the pup points, then the app plays it |
| Why continue | progress, mastery | a story, a collection, the pup being silly, a sibling to beat |

So: **do not fork the course.** Build (1) profiles, (2) a Kids mode that changes how every lesson is presented and
paced, and (3) a short "Pup's Playground" path that sits in front of Basics and is written as games. A child finishes
the Playground and walks straight into the regular Basics unit with the kid skin still on. Graduating to the "adult
section" is just turning the skin off, which a parent can do at any time and the app suggests once Beginner is done.

One engine, one matcher, one lesson model (Step, Scene, Exercise), one set of songs.

## 1b. For everyone, not only kids (added 2026-10-18)

Roy's call: the things that make a game sticky are not a kids' feature. They are on for every profile; the kid
skin is only the presentation layer on top (section 3). So, for adults and kids alike:

- **Juice on every note.** Every detected note makes the pup react (a bounce scaled to pitch, an ear flick), the key
  flashes its colour, a soft tick sounds. Lessons, songs, practice: nothing the player does is silent. The Jam
  screen is the same thing with no goal.
- **A living pup.** Idle moments after ten seconds of nothing (sneeze, tail chase, butterfly, scratch, sleep),
  reactions (surprised, thinking, shh, peek) at the right moments, and the pup eating when the treat jar is tipped.
  Never sad, never nagging: "ready to play", not "you abandoned me".
- **Variable rewards.** Stickers drop from lessons and song runs with three rarities (common, rare, shiny). The
  drop is random within a floor (never more than three lessons without a rare). A mystery egg appears every third
  lesson and hatches on the next one. Collections show their gaps ("7 of 10 in the pond"). Adults get the same
  album; the stickers are not babyish (instruments, animals, places).
- **A world that grows.** The dog house upgrades in three stages, the garden fills with what was collected, the
  concert audience is the animals unlocked so far. The lesson path lives inside this place. Effort becomes
  something you can see and show.
- **"One more".** The next lesson's button appears during the celebration, with a preview of what it drops.
- **Sound.** A jingle per rarity, the pup's bark on a clean run, a soft chime per star. Audio lands faster than
  pixels.
- **Showing off.** The "songs I can play" shelf with bronze, silver and gold records per song; Share my concert
  (a recording plus a card) for anyone, not only Grandma.
- **Family wall, duet, take turns, crown of the day**: for every profile.
- **Octave hint and hint escalation**: for everyone, with adult timings (6, 12, 18 s) and kid timings (4, 8, 12 s).
- **Wrong key shown, by default.** The key you hit lights yellow when it is within two semitones of the right one
  and red when it is further away, for every profile (toggle stays in Settings). Only on confident strikes, so
  room talk does not paint keys.
  If the room still fools it (a burst of "wrong keys" that do not look like a piano), the pup offers once per
  session to stop painting wrong keys, or to pause listening; the setting itself is untouched.
- **Stars** are shown for everyone; adults also see the accuracy number. Colour notes are a toggle adults can turn
  on (many adult beginners use colour books too).

What stays kid-only: animal key names, bites (one scene per step), hiding numbers, the 5 to 8 minute goodbye, the
parent gate, slower speech, the pup saying the name on every recap (adults get it on milestones only).

Left out for everyone: timers, energy limits, lose-your-streak, nags. The parents are the gatekeepers, and Roy is
an adult user who would uninstall over those.

## 2. Profiles: the family on one phone

Progress lives in SharedPreferences files named in `ProgressFiles.NAMES` (progress, mastery, mic, profile, ear,
practice, theory). A profile is a suffix on those names (`progress.p3`), so every store, the backup and the export
keep working. Shared across profiles: mic calibration, the voice model, imported songs, the report token.

- **Picker at launch** (skipped when there is one profile): a row of pup avatars with names. Up to eight.
- **New kid in 20 seconds:** name, avatar, "Can they read yet?", go. The first Playground lesson opens straight away.
  Adults get the existing onboarding (played before? daily goal).
- **Per profile:** course position, XP, stickers, daily goal, reminder time, kid/adult, reader, hint speed.
- **Family wall:** a card on the home screen with everyone's avatar, this week's paw prints and latest sticker.
  Siblings see each other's progress; that is most of the motivation.
- **Parent corner:** settings, microphone setup, the token, adding and removing profiles, switching a kid to grown-up
  mode, and the per-kid report (minutes, lessons, where they got stuck). Behind a hold-the-pup-for-three-seconds gate
  with a sum ("7 plus 5") for the readers who learn the hold.
- **Duet:** pick a partner; one profile plays the left hand, the other the right, both get the stars. Zero engine cost,
  it is both-hands mode with two names on the recap. Works for two adults learning together as well.
- **Take turns:** in Copy the pup and the ear games, two or three profiles alternate on the same phone. Each turn
  is credited to its player. No losers: everyone who finishes gets the sticker, the best run gets a crown that day.

## 3. The kid skin

A profile flag (`kids`, `reader`) and the pieces it switches:

1. **Colour notes.** Boomwhacker / Chroma-Notes colours, which many kids' piano books already use: C red, D orange,
   E yellow, F green, G teal, A blue, B purple. Falling notes, keyboard keys and the pup's scenes all use them. Readers
   get the letter on the colour. For Little kids each key also has an animal: C cat, D dog, E elephant, F frog,
   G goat, A ant, B bear; the pup says "the red cat key".
2. **Bites.** Each Step is cut to one Scene and one short exercise. The existing lessons are not rewritten: a lesson
   in kids mode shows the same steps, but READ steps stop after the first scene and offer "more?" and PLAY exercises
   are cut at the first phrase marker (bar 4 or 8). One clean run passes.
3. **Stars, not percent.** The recap shows one to three stars (heard it, heard it in time, heard it twice). RunWords
   gets a kid vocabulary ("You did it!", "The pup is dancing"). No numbers, no "New best".
4. **Stickers.** Every lesson drops a sticker into an album (pup poses, instruments, animals), one page per Playground
   place. The album is the kid's "You" tab. A session ends on the sticker, not on XP. XP still accrues underneath so
   grown-up mode later keeps the progress.
5. **Says the child's name.** The on-phone voice (sherpa-onnx, already used for generated lines) says "Great job, Mia"
   and "Goodnight, Theo". Pre-rendered lines cannot do this; generated ones can. Small, and kids notice it first.
6. **Hint escalation.** Stuck 4 s: the key glows. 8 s: the painted hand points at it. 12 s: the app plays it and
   says "your turn". Adults keep the single 5 s hint. Both times are a knob per profile.
7. **Octave hint.** From the 2026-10-17 stuck moments: when the matcher keeps rejecting a strong candidate an octave
   from an expected note, the pup says "that's the right note, but one octave up: try lower" and the lane shows an
   arrow. Kids will play the wrong octave constantly; adults do too.
8. **Big and slow.** Larger on-screen keys, one button per screen, the pup speaks slower (0.85) with shorter lines,
   the wait-mode window for "in time" is wider.
9. **Session goodbye.** After the daily goal (5 minutes Little, 8 Big kid) the pup yawns and says goodnight. Songs and
   the Jam stay open; no new lesson starts. Paw-print calendar for the week; a missed day is a blank paw, never a
   lost streak.
10. **Dress the pup.** Stickers unlock hats, sunglasses and a bow tie drawn over the pup. Code-drawn at first (a few
    shapes), real art when Gemini quota returns. Kids dress a mascot for longer than they practise; that is fine.
11. **Gentle wrongs.** Wrong-note flashes stay off (Roy's room has talking); in kids mode a wrong key makes the pup
    tilt its head, nothing red.

## 4. Pup's Playground (new content)

Twelve mini-lessons, two to four minutes each, in the Step model so the Learn path renders them, arranged as a map of
the pup's world (garden, pond, concert hall) on the existing lesson path. Mic only.

| # | Lesson | Game | Engine need |
|---|---|---|---|
| 1 | High and low | The pup climbs when a high key sounds, sinks on a low one. "Make the pup fly" | range exercise: any key above/below C4 |
| 2 | Two black, three black | Find the two-black "dog house" and the three-black "fence". Play any key of the group the pup points at | range exercise over the group |
| 3 | Where C lives | C is just left of the dog house. Play every C on the piano (reuse find-c, kid scenes) | exists |
| 4 | Pup's steps C D E | Step up and down with the pup. Colours and animals first appear | exists |
| 5 | Hot Cross Buns | Three colours, one song (reuse rep-buns, first phrase) | exists |
| 6 | Copy the pup | Call and response, two then three notes, by ear (MelodyMemoryGame, kid wrapper, take turns) | exists |
| 7 | The steady drum | Clap with the pup's drum (clap detection, no pitch), then play C on every beat | onset-only counter |
| 8 | Mary had a little lamb | Reuse rep-mary with colours and the phrase cut | exists |
| 9 | Left paw | Low C with the pinky (C3), four times, then under the melody | exists |
| 10 | Up to G | C D E F G, the whole hand. "Five fingers, five colours" | exists |
| 11 | Soft and loud | The pup sleeps on soft notes, jumps on loud ones. Graded from the mic rise level, labelled a toy | riseDb thresholds |
| 12 | Pup's concert | Perform one of the three songs with the band; two stars to pass. Fireworks, the album page completes | exists, lower pass |

Big kids play 2, 3, 6, 7 and 12, then Basics. A second page (Twinkle, Row Your Boat, Old MacDonald, hands together)
comes only if the first one gets used.

## 5. Beyond lessons: things kids do when they do not want a lesson

- **Jam.** A screen where every key you play does something: the colour bursts, the pup bounces to the pitch, a chord
  throws confetti, a glissando makes a rainbow. No goal, no score. For four-year-olds this is the app. Cheap: it is the
  mic events driving the existing celebration pieces.
- **Pup says.** Simon on the piano: the app plays a pattern, the kid copies, it grows by one. Two-player alternating.
  (MelodyMemoryGame with a kid face and turn-taking.)
- **Clap along.** The drum game from lesson 7 as its own toy: tap or clap with the beat at three speeds.
- **Share my concert.** After a performance, "send to Grandma": the run's recording (RunRecording already exists) as a
  WAV plus a card image with the stars and the sticker, through the share sheet (FileProvider is already set up).
- **Pup's quest.** Three tiny tasks a day for kids (play two songs, copy the pup three times, find all the Cs), from
  the existing DailySession, each with its own star.

## 6. Robustness for a room full of kids

- **Softer hands.** Kids strike lighter. A per-profile mic sensitivity offset (default +3 dB easier for kids), and
  the new stuck moments from the nieces and nephews will show whether the thresholds need more. The stuck reporter
  already runs per device; reports carry the profile kind (kid/adult), not the name.
- **Noise.** Talking and TV are already handled (flashes off, room margin). Clap detection must ignore speech:
  a short broadband onset, no pitch, at least 300 ms apart.
- **Two kids, one piano.** Take-turns games pause listening between turns and say whose turn it is, so a sibling
  hitting keys early does not score.
- **Nothing to break.** Parent corner gated; no destructive button outside it; profile delete asks twice.
- **Fold.** Big keys and the tabletop posture on the inner screen for the Jam and the Playground.
- **Tests.** Every Playground lesson parses, plays in the player and passes with PianoSynth; range exercise and clap
  counter with synthetic audio; store round trip per profile; backup includes all profiles; stars and kid RunWords;
  hint escalation timing; octave hint on the real 2026-10-17 stall clip. Emulator screenshots of every Playground
  lesson and the Jam before release.

## 7. Voice and art

- About 200 new fixed lines (Playground scenes, kid RunWords, goodbyes, hints), rendered on the PC with
  scripts/render_voice.py, same voice, speed 0.85, about seven minutes. Names and anything with a name are generated
  on the phone.
- Pup poses needed: sleeping, drumming, flying, head tilt, holding a sticker, dressed. About 30 stickers. Roy makes
  these with Gemini from the mascot sheet when quota allows; until then: the ten existing poses, code-drawn accessories
  and coloured-shape stickers. Nothing in the plan waits on art.

## 8. Build plan

Branch `family`, worktrees under C:\dev\piano-wt\. Sequential for the foundation, then five teams in parallel, then
one integrator. Each team leaves an INTEGRATION.md in its package, as in 2.0.

| Step | Team | Model, effort | Scope |
|---|---|---|---|
| 1 | Foundation | Opus, high | Profiles (suffix, picker, new-kid flow, per-profile settings), `KidsMode` state, parent gate, family wall data. Merged before anything else starts. |
| 2a | Playground | Opus, high | The twelve lessons + scenes, the map on the lesson path, range exercise, clap counter, soft/loud grade, phrase cut points on reused lessons. |
| 2b | Skin | Sonnet, medium | Colour and animal notes on NoteLane, Keyboard and scenes (colour as a toggle for adults); bites; stars on every recap, numbers hidden for kids; kid RunWords; big keys; session goodbye and paw calendar. |
| 2f | World and rewards | Opus, high | For all profiles: juice on every note, the living pup (idle and reaction poses from art/mascot), sticker drops with rarities and the floor, mystery egg, album with gaps, dog house stages, garden and audience, treat jar, "one more" button, jingles, the records shelf, dress the pup. |
| 2c | Detector and hints | Opus, high | Hint escalation, octave hint (engine + pup line + lane arrow), per-profile sensitivity offset, take-turns listening pauses, kid tag on stuck reports. |
| 2d | Play | Sonnet, medium | Jam, Pup says (two player), Clap along, Share my concert, Pup's quest, Duet. |
| 2e | Voice and stickers | Sonnet, medium | Catalog lines, name generation hooks, render run, placeholder stickers and accessories, pup pose hooks. |
| 3 | Integration | Opus, high | Picker and onboarding wiring, Learn path order per profile kind, family wall on home, grown-up-mode prompt after Beginner, Duet and take-turns in the player, screenshots of everything on the Fold emulator, merge, 2.1.0 release. |

Rough size: about 6,500 lines, 100 to 120 new tests, one release (2.1.0, versionCode 7). Two days of agent time;
one evening of Roy's for art prompts and a first test with the kids.

## 9. Not in this plan

- Finger-number grading (the mic cannot see fingers; the pup asks for them, that is all).
- Real dynamics (needs MIDI; the mic level version is a toy and says so).
- Per-profile voices (25 minutes of rendering per voice, and the pup should sound like one pup).
- A separate kids app or store listing; this is the private build.
- Cloud sync between phones; backup/export per profile covers moving a kid to another device.
- A third age band or a second Playground page until the first one gets used.

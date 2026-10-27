# Store listing draft (Sharp Ears 2.15)

A draft for the public app's Google Play listing. It is not shown in the app. Everything below describes something the app
does today (2.15, branch `show-off`); nothing is promised for later. Written plainly: no superlatives, no ratings, no
"free" or price (none is set), no artist or brand names (docs/publishing.md, "Trademarks").

Read with docs/publishing.md (the checklist for the separate public app) and docs/privacy-policy.md (the draft policy).

## 1. App name

**Sharp Ears: Learn Piano** (23 characters; Play allows 30). The private app is called Sharp Ears; the public one may take
another id (`com.bigpapituna.sharpears.play`) and name, so this one is a suggestion.

## 2. Short description (80 characters at most)

> Piano lessons, songs by chords and timing feedback. Mic, MIDI or on-screen keys.

(80 characters. A shorter option: "Learn piano with lessons, songs by chords and feedback on your timing." is 70.)

## 3. Full description (under 4,000 characters)

> Sharp Ears teaches piano with short daily practice, a step-by-step course and songs you can play along to. Play on the
> on-screen keyboard, a USB or Bluetooth MIDI keyboard, or a piano the phone listens to through its microphone.
>
> TODAY, IN ONE TAP
> Today holds a plan for the day: a few chords to review, your next lesson and a song. Press Start and the app walks you
> through it. Back after a few days away, and Today starts you with three easy minutes. Miss a single day in a week and your
> streak stays.
>
> PLAY SONGS BY CHORDS
> More than a hundred songs are written out as chords: the key, tempo, sections and the chord for each bar. A drum and bass
> band plays along while you play the chords. The charts hold no melodies or lyrics, and some are marked as first guesses.
> You can sort them by the ones you can already play.
>
> SEE YOUR TIMING
> After a song in Perform mode, a strip shows every bar as clean, rushed, dragged or messy. Tap a bar to see which notes
> were early or late, in milliseconds. A line in plain words names what to fix. With a MIDI keyboard you can measure and set
> its timing offset.
>
> LOOP THE HARD BARS
> The bars you missed most are listed after each song, each with a Loop button that repeats just that bar at half speed.
> Speed buttons include a ramp that rises as you get it right, and an automatic speed that follows your last run.
>
> A COURSE THAT STARTS WHERE YOU ARE
> Tell the app how much you have played and the course starts at a fitting level, from the first key to keys, rhythm, left-hand
> patterns and technique. Lessons have a few short steps: read, listen, play, play from memory.
>
> MORE TO PRACTISE
> - Chords: a dictionary with fingering, drills, games and progressions in several keys.
> - Sight-reading: pieces, then two-minute drills on a treble, bass or both staves.
> - Ear training, a music theory course, scales, arpeggios and warm-ups.
> - A metronome, a tuner, key transposing and tutorial videos you can sync to a song.
> - My songs: bring in a MIDI file and the app makes an easy version of its tune.
> - A daily reminder you set, and progress you can export to a file.
>
> FOR KIDS
> Grown-ups can add a profile for a child. Children from 4 to 7 get a picture-led map with a friendly dog, short lessons with
> spoken help, stickers and games. Children from 8 to 13 get their own tabs with pop songs first and a grown-up-style player.
> Both bands can play on a MIDI keyboard or the microphone.
>
> FOR PARENTS
> - Family today on your Today screen: each child's minutes, lessons and stars.
> - A daily time limit for each child (10 to 45 minutes). When it is reached, lessons, songs and games close until tomorrow.
> - The parent brief: what a child played, what they can do now and what to try next. It is made on the phone from the
>   app's own records and can be emailed to you, daily or weekly.
> - A gate (a maths question) in front of the grown-ups' corner, and Lock now to close it at once.
>
> PRIVACY
> No account, no ads. Names, progress and settings stay on the phone. The microphone listens only while a lesson, a song, a
> game, the tuner or Microphone setup is open with the mic on; the app works out the notes on the phone as you play, and the
> sound is gone straight away. None of it is kept.
>
> GOOD TO KNOW
> The microphone works best in a quiet room with a piano; a MIDI keyboard is more exact. Sound from a television or
> conversation can be mistaken for notes. Tutorial videos play from YouTube and need a connection. The app is a practice
> aid and does not replace a teacher.

Counted with the text above (quote markers removed): see section 8. Keep it under 4,000 after any edit.

Facts behind each claim, so nothing here drifts from the app:

| Claim | Where it lives |
|---|---|
| Today plan, one Start, three easy minutes, rest day | TodayScreen, engine/DailySession.kt, engine/Comeback.kt, engine/PracticeStats.kt (2.9) |
| More than a hundred songs by chords, no melodies or lyrics, some "a sketch" | engine/chords/PopSongs*.kt (107 public charts), engine/chords/PopSong.kt, confidence labels |
| Drum and bass band, sort by chords you know | engine/chords/PopPlay.kt, PopFind (Can play, One new chord away) |
| Timing strip, tap a bar, plain line, MIDI offset | engine/PerformTiming.kt, ui/player/TimingPanel.kt, ui/MidiTiming.kt (2.14) |
| Loop the hard bars at half speed, ramp, Auto | engine/TroubleSpots.kt, PlayerScreen.loopBar, TempoRamp, SmartSpeed (2.8 guidance batch) |
| Course by level, steps | engine/Course.kt, engine/family/Placement.kt, engine/nav/DayOne.kt (2.14) |
| Chords, sight-reading, ear training, theory, scales, tools | ui/ChordsScreen, ui/SightReadScreen, ui/ear, ui/theory, ui/practice |
| Transpose, tutorial videos synced, My songs, reminder, export | CLAUDE.md "Guidance batch", "Feature batch 2026-10-08", 2.8.1 |
| Kids 4 to 7 and 8 to 13 | engine/family/Ages.kt (2.14), ui/kid, ui/play |
| Family today, daily time, parent brief, email, gate, Lock now | 2.14 parents, 2.4 and 2.5 sections |

## 4. Feature bullets

For kids (shown to grown-ups choosing for a child; the app is for adults and teens, see section 6):
- A map with a friendly dog guides each step; lessons are a few short steps with spoken help.
- Stickers, dress-up items and a trail to collect; games for notes, chords and rhythm.
- Children 4 to 7 get a picture-led home; children 8 to 13 get the tabs, pop songs first, and levels.
- Play on a MIDI keyboard, the on-screen keys or a piano the phone hears.

For grown-ups:
- One Start for the day's chords, lesson and song.
- More than 100 songs by chords with a drum and bass band.
- Timing after a song, bar by bar, and one tap to loop the bars you missed.
- A course placed at your level, sight-reading on the staff, ear training, theory and technique.
- A streak that keeps itself through one missed day in a week.

For parents:
- Family today: each child's minutes, lessons and stars on your Today screen.
- A daily time limit per child that closes lessons, songs and games until tomorrow.
- A parent brief for each child, written on the phone, with an email option.
- A maths gate for the grown-ups' corner, and Lock now.

## 5. Screenshots: 8 captions

Take them on the Fold's cover screen or a phone in portrait (1080 x 1920 or similar), light or dark, all in one theme.
Use a fresh profile with a name that is not a real person's.

| # | Screen | Caption |
|---|---|---|
| 1 | Today: the plan card (chords, lesson, song) with Start, and the streak | One Start for your day |
| 2 | Songs by chords: the chord player with the blocks and the band bar | Play songs by chords with a band |
| 3 | A Perform recap: the bar strip, its legend and the plain line | See your timing, bar by bar |
| 4 | The same recap lower down: Hardest bars and the Loop bar buttons | Loop the bars you missed |
| 5 | Learn: the course path with the next lesson pulsing | A course that starts where you are |
| 6 | Sight-read: a staff drill with the keyboard | Read notes on the staff |
| 7 | Family today on a grown-up's Today, with a child's brief open | See each child's day |
| 8 | The kid map (garden or pond) with the dog | Short lessons and stickers for kids |

Do not use a screenshot of a private chart (see section 7), the tester switches or the debug builds' extras.

## 6. Privacy summary

Reconciled at the 2.15.0 merge with the in-app privacy page (Settings, About, Privacy), whose first three sections are built
by `MicTrust.privacy(facts)` in engine/MicTrust.kt. The quotes below are its exact strings for the PUBLIC build's facts: no
Help Roy test, no diary, no kept recordings and no review clips (section 7 removes them), with the AI brief and the brief by
email present. If the page's words change, change these with them.

Short form, for the listing (each sentence taken from the page):

> Sharp Ears has no account and no ads. The microphone hears your piano so the app can check the notes you play. It listens
> only while a lesson, a song, a game, the tuner or Microphone setup is open with the mic on and the app is on screen. It
> works out the notes on this phone as you play, and the sound is gone straight away. None of it is kept. Everything else
> stays in the app's own storage on this phone. The parent brief's findings, with no names and no sound, go to an AI service
> that writes the brief. The brief by email, once a grown-up sets it up, goes through an email service to the address they
> give. Tutorial videos play in YouTube's own player, which connects to Google. A privacy policy link goes here: [URL].

The page itself, as the public build shows it (the lead, then three sections):

> Sharp Ears has no account and no ads. In short:
>
> **What the app listens to.** The microphone hears your piano so the app can check the notes you play. It listens only while
> a lesson, a song, a game, the tuner or Microphone setup is open with the mic on and the app is on screen. Android 12 and newer
> show a green microphone dot while it listens. It works out the notes on this phone as you play, and the sound is gone
> straight away. None of it is kept. None of it is sent anywhere. Kids' profiles use the same rule. Without the mic, play on
> the screen's keys or a MIDI keyboard.
>
> **What stays on this phone.** Everything else stays in the app's own storage on this phone: the names of the people who
> play, and each one's progress, stars, minutes, stickers, settings and parent brief. A child's profile holds a first name, an
> age group and their playing. No birthday, no photo, no voice. The parent gate guards a child's brief and settings. Deleting a
> person in Settings, Family removes their progress and their brief. Uninstalling the app removes everything. If the phone's
> backup to your Google account is on, it can carry progress to a new phone, never recordings.
>
> **What leaves this phone.** Only these, on this build: The parent brief's findings, with no names and no sound, go to an AI
> service that writes the brief. The brief by email, once a grown-up sets it up, goes through an email service to the address
> they give. Tutorial videos play in YouTube's own player, which connects to Google.

A public build without the AI key or the email key drops that sentence (and the short form's); with neither, "What leaves this
phone" reads: "Nothing about you or your playing leaves this phone. Tutorial videos play in YouTube's own player, which
connects to Google." The microphone card ("How the microphone works") says the same about the sound: "It works out the notes on
this phone as you play, and the sound is gone straight away. None of it is kept. None of it is sent anywhere."

Points to settle against the privacy page and docs/privacy-policy.md before release:
- The AI-written brief and the emailed brief send data off the phone when a build has the keys; the listing and the Play data
  safety form must say so for the public build (docs/publishing.md, "Data safety form answers").
- Kid profiles: the policy says the app is not directed at children under 13, but 2.1 onward has kid profiles. Decide the
  target audience and Play's Families policy before writing "for kids" copy into the public listing. Section 4's kids bullets
  are for that decision; leave them out if the listing stays adults and teens.
- Tutorial videos use YouTube's embedded player; docs/publishing.md section 1 asks for a review against YouTube's terms first, and "Open in YouTube" only is the fallback (then drop the videos line from section 3).

## 7. Remove before public release

These exist in the private build and must not reach the public listing or the public app (see docs/publishing.md):
- **Help Roy test** (Settings, behind the gate; PostHog screen recordings, app events and error reports, and the Brevo diary)
  and everything about it. The public build has no PostHog code (the keyless `noposthog` door) and no such section in the policy.
- **Recordings kept**: debug and private builds can keep practice recordings, with a switch in Microphone setup; a release
  build never records. Do not mention it, and check that no fixtures folder is created.
- The six `privateOnly` chart sketches (engine/chords/PopSongsTemp.kt), which `PopPrivateTest.privateSongsMustNotShipPublic`
  guards. The "more than a hundred songs" claim counts the 107 public charts only.
- The Alicia slot and the personal MIDI files; the CC BY-SA Moonlight file unless its licence is met (publishing.md section 1).
- Debug extras and the open debug routes of a keyless build.

## 8. Counts

The checks used for this draft: short description 80 characters; the full description (section 3, quote markers removed)
under 4,000; name 23. Recount after editing:

```
python -c "import re,sys;t=open('docs/STORE-LISTING.md',encoding='utf-8').read();b=t.split('## 3. Full description')[1].split('Counted with')[0];b=re.sub(r'^> ?','',b,flags=re.M);print(len(b.strip()))"
```

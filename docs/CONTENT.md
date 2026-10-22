# Content: songs and course (Sharp Ears 2.0, team F)

Everything here is written for Sharp Ears. No MIDI files were downloaded; every tune was written by hand in the
generator's token notation (`scripts/gen_songs.py`) from well-known public-domain melodies. Arrangements are ours.

## How songs are made

`python scripts/gen_songs.py [ids...]` writes `app/src/main/assets/songs/<id>.mid` (track 1 right hand, track 2 left
hand). `python scripts/gen_songs.py --index` also adds or refreshes the batch entries in `index.json`.

- Token notation: `<note><octave>:<beats>`, `r` is a rest, `+` stacks a chord (`C4+E4+G4:2`). Beats are quarter notes.
- Left hands of the 2026-10-13 batch are built from chord symbols by `lh()` (patterns: `root`, `half`, `pah`, `waltz`,
  `oompah4`, `lilt`, `arp8`), so a bass line is a list of chords per bar, not a hand-typed string.
- The generator checks that both hands have the same length, that the song is whole bars (plus a pickup), and that no
  note crosses a bar line.
- Sections: the loader (`SongRepository`) has no sections field, and `data/` is not ours to change. `SmfParser` already
  turns MIDI marker events into `Song.sections`, so each new song carries its sections as markers in the right-hand
  track. The same list (name and bar number) is in `index.json` under `sections`; the loader ignores it, the tests
  compare the two. The transport bar draws the section marks and the player names the current section.
- Tutorial videos: none for the new songs (`"videos": []`). `LibraryContentTest.TO_CURATE` allows that until each song
  gets two oEmbed-checked tutorials, like the 2026-10-08 batch.
- Licence for all of them: Public Domain (traditional, or a composer who died before 1926). Attribution text is
  "Arrangement written for Sharp Ears (melody and bass); the tune is in the public domain" for traditional tunes and
  "Simplified arrangement written for Sharp Ears; the music is in the public domain (X died in Y)" for composed ones.

## The 15 new songs

| id | title | composer | level | key, time, tempo | sections (bar) |
|---|---|---|---|---|---|
| mary-had-a-little-lamb | Mary Had a Little Lamb | Traditional | easy | C, 4/4, 100 | First verse 1, Second verse 9 |
| hot-cross-buns | Hot Cross Buns | Traditional | easy | C, 4/4, 96 | First time 1, Second time 5 |
| row-row-row-your-boat | Row, Row, Row Your Boat | Traditional | easy | C, 6/8, 90 | Row the boat 1, Merrily 5, Again 9, Merrily again 13 |
| london-bridge | London Bridge | Traditional | easy | C, 2/4, 108 | First time 1, Second time 9 |
| yankee-doodle | Yankee Doodle | Traditional | easy | C, 4/4, 116 | Verse 1, Verse again 5, Chorus 9, Chorus again 13 |
| old-macdonald | Old MacDonald Had a Farm | Traditional | easy | C, 4/4, 108 | Old MacDonald 1, And on his farm 5, Last time 9 |
| haydn-surprise | Surprise Symphony (theme) | Joseph Haydn | easy | C, 2/4, 84 | Theme 1, Second time 5, The surprise 9, Ending 13 |
| au-clair-de-la-lune | Au clair de la lune | Traditional (French) | easy | C, 4/4, 84 | First time 1, Second time 5 |
| lightly-row | Lightly Row | Traditional (German) | medium | C, 4/4, 100 | First phrase 1, Second phrase 5, Middle 9, Last phrase 13 |
| we-wish-you-a-merry-christmas | We Wish You a Merry Christmas | Traditional (English) | medium | G, 3/4, 112 | First time 1, Second time 9 |
| eine-kleine-nachtmusik | Eine kleine Nachtmusik (opening) | W. A. Mozart | medium | G, 4/4, 112 | Opening 1, Answer 5 |
| gymnopedie-1 | Gymnopedie No. 1 (opening) | Erik Satie | medium | D, 3/4, 66 | Introduction 1, Melody 5 |
| bach-toccata-opening | Toccata in D minor (opening) | J. S. Bach | medium | D minor, 4/4, 60 | First statement 1, Echo 3, Chords 5 |
| hall-of-the-mountain-king | In the Hall of the Mountain King (theme) | Edvard Grieg | hard | B minor, 4/4, 104 | Creeping in 1, Higher 5 |
| rondo-alla-turca | Rondo alla Turca (theme) | W. A. Mozart | hard | A minor, 2/4, 100 | Theme 1, Theme again 5, Last note 9 |

Spread: 8 easy, 5 medium, 2 hard. Title says "(opening)" or "(theme)" where only that part is written.

### What was simplified, and how sure the tune is

Pitches and rhythm are from memory with no source to check against, so every song deserves one listen against a
recording before it gets tutorial videos. Confidence, honestly:

- Firm: Mary Had a Little Lamb, Hot Cross Buns, Row Row Row (6/8 counted in quarters, three beats a bar), London Bridge,
  Yankee Doodle (verse and chorus, checked against the words syllable by syllable), Old MacDonald (the farm verse; the
  animal-sound lines are left out).
- Mostly firm: Haydn (the C E G / F D B theme three times, then a closing chord; the real second strain is not
  written), Au clair de la lune (the first verse twice; the middle "Prete-moi ta plume" phrase is not written),
  We Wish You a Merry Christmas (bars 6 to 8 are the least sure), Lightly Row (the middle B phrase is the least sure).
- Simplified: Eine kleine Nachtmusik (bars 1 to 4 are Mozart's opening; bars 5 to 6 repeat it; bars 7 to 8 are a closing
  cadence written for Sharp Ears), Gymnopedie No. 1 (the left hand is Satie's two seventh chords; the melody is only
  its skeleton, one note a bar), Mountain King (the creeping theme twice, the second time an octave higher; rhythm
  approximate), Rondo alla Turca (the first four bars twice, then a last note), Toccata (the famous opening gesture and
  its echo, then a diminished seventh chord and a D minor chord; real free-time fermatas are written as long notes).

### Suggested songs that were not written

Not reliable enough from memory, so left out rather than risk teaching a wrong tune: Kumbaya, Lavender's Blue, Danny
Boy, Shenandoah, Home on the Range, Aura Lee, Jesu Joy of Man's Desiring, Vivaldi Spring, Swan Lake, Morning Mood,
Hungarian Dance No. 5, Habanera, William Tell finale, Can-Can, Blue Danube, Largo from the New World, Schubert Ave
Maria, Chopin Nocturne Op. 9 No. 2. Best done from a checked source (a public-domain score on IMSLP, or Roy's ear).

## The course

`engine/Course.kt` is the course; the Learn screen builds from `COURSE`, so new levels appear without any UI change.
70 lessons in 10 levels. Each lesson is 3 to 6 steps; the first step is always reading. Step text has no dashes, and
nothing the voice cannot say (no hash, slash or ampersand).

| level | lessons |
|---|---|
| Basics (6) | sit, find-c, fingers-right, fingers-left, beat, white-keys |
| Beginner (8) | first-melody, lengths, bass, together, black-keys, rests, scale, first-song |
| Rhythm (6) | rhythm-quarters, -halves, -eighths, -dotted, -waltz, -syncopation |
| Intermediate (14) | scale-left, scale-both, triads, inversions, broken, progression, minor, expression, lh-waltz, lh-pop, lh-octaves, lh-tenths, pedal, dynamics |
| Keys (6) | key-g, key-f, key-d, key-bb, key-em, key-dm |
| Advanced (5) | scale-two, sevenths, arpeggios, independence, repertoire |
| Technique (7) | five-c, five-g, five-f, five-d, five-a, hanon-1, contrary |
| **Repertoire 1 (6)** | rep-mary, rep-buns, rep-row, rep-london, rep-yankee, rep-macdonald |
| **Repertoire 2 (6)** | rep-lightly, rep-wewish, rep-eine, rep-gymno, rep-toccata, rep-scarborough |
| **Technique 2 (6)** | t2-scales, t2-broken, t2-touch, t2-shaping, t2-pedal-progression, t2-sight |

### Repertoire lessons (new)

Every lesson has the same six steps: a short idea to read, the opening four bars heard (Listen), the right hand alone
(Play, the app plays the left), the left hand alone (Play, the app plays the tune), both hands in time (Perform, 80
percent), then a reading step that opens the whole song in the library (`songId`). The phrases are written in
exercise notation in `Course.kt` (`MARY_X`, `BUNS_X`, and so on) and mirror the first bars of the generated songs.
Repertoire 1 uses the easy songs; Repertoire 2 uses the medium ones plus the existing Scarborough Fair.
Not in a lesson, but in the library: Haydn Surprise, Au clair de la lune, Mountain King and Rondo alla Turca.

### Technique 2 (new)

- t2-scales: C, G and F major, hands together an octave apart, three Perform steps with the book fingering (the
  exercise's `fingers` field drives the numbers on screen; F major is flat-named with finger 4 on B flat).
- t2-broken: broken chords on C, F and G (root, third, fifth, third), listen, right hand, both hands in time.
- t2-touch: legato against staccato, shown as full-length notes against notes with rests, then a mixed Perform.
- t2-shaping: a phrase soft then loud; the last step is a Dynamics step and needs a MIDI keyboard (skippable).
- t2-pedal-progression: C, G, A minor, F with the pedal changed on each chord; the last step is a Pedal step
  (MIDI keyboard, skippable).
- t2-sight: five sight-reading habits, then one play-through and two Perform lines (one in G with an F sharp).

## Popular songs by chords

The legal line: a popular song in Sharp Ears is its chords only. Key, tempo, time, section order, the chords of each
bar, the artist and the title, nothing else. No melody, no lyric and no recording of any song, in any build: not in
the content, the tests, the comments or the notes. A chord progression, a key, a tempo and a song's form are facts
anyone may write down; the melody, the words and the recordings belong to their owners. The player shows chord
blocks, the band plays a drum and bass bed, and the learner plays the chords.

The lyric guard is structural, so nothing about a song's words is ever stored, not even a hash:
- a section is named only from a fixed set: Intro, Verse, Pre-chorus, Chorus, Post-chorus, Bridge, Interlude, Solo,
  Breakdown, Outro, Tag, Refrain, with an optional number;
- a note is one line about the chords: no quotation marks, never the words sing, sings, lyric or words, and the
  song's title at most once.
`PopSongsTest` enforces both.

Where it lives: `engine/chords/PopSong.kt` (the contract), `PopSongs.kt` (the chart parser and the helpers:
`fourChordSongs`, `playableWith`, `byStyle`, `sortedForBeginners`, `transposed`, `difficultyByRule`), and one file
of charts per batch: `PopSongsHits.kt`, `PopSongsYacht.kt`, `PopSongsEighties.kt`, `PopSongsPop.kt`,
`PopSongsCountry.kt`, `PopSongsFamily.kt`, `PopSongsWorship.kt`. Turning a chart into a playable `Song` is the
player's job (`PopPlay.toSong`, engine/chords/PopPlay.kt, the only one). Since 2.6.0 the screens use these helpers as the
one rule for the four-chord badge, "Easiest first" (`beginnerOrder`), "can play" and keys; ui/chords/INTEGRATION.md.

### What is there (107 songs)

A song counts under each of its styles, so the style rows add up to more than 107.

| style | songs | CHECKED | LIKELY | SKETCH |
|---|---|---|---|---|
| Greatest hits | 24 | 0 | 19 | 5 |
| Yacht rock | 8 | 0 | 4 | 4 |
| 80s rock | 23 | 0 | 19 | 4 |
| Pop | 40 | 0 | 34 | 6 |
| Rock | 18 | 0 | 15 | 3 |
| Country | 10 | 0 | 8 | 2 |
| Soul | 12 | 0 | 10 | 2 |
| Ballad | 26 | 0 | 19 | 7 |
| Kids | 13 | 5 | 5 | 3 |
| Holiday | 5 | 1 | 2 | 2 |
| Worship | 6 | 1 | 3 | 2 |
| all songs | 107 | 6 | 78 | 23 |

By difficulty: 34 at 1, 40 at 2, 11 at 3, 22 at 4 (74 at 1 or 2). Four-chord songs (a major key, only I, IV, V and vi,
slash bass ignored): 31.

Confidence: CHECKED only when every section is sure (so far the short traditional tunes), LIKELY when verse and
chorus are sure and the rest may be off by a bar or two, SKETCH when the structure is simplified or a section is
doubtful. Keys and tempos of the songs in doubt were checked against a key and tempo database (songbpm.com), never
against a page of chords and words.

Left out, because the key or the chords would not settle: No Woman, No Cry (the live key), Dancing in the Dark,
Let's Stay Together, Baby Come Back, Footloose, Fast Car (Luke Combs), Last Night, Something in the Orange, Baby
Shark, As It Was, Believer, Diamonds, Die with a Smile, Espresso, Good as Hell, Lose Control; and, earlier, Sailing,
What a Fool Believes, Summer Breeze, Lowdown, Lido Shuffle, Africa, Man! I Feel Like a Woman!, Whiskey Glasses,
Beautiful Crazy, Levitating, bad guy, Beautiful Things, Firework, Treat You Better, Stay, Rolling in the Deep.

### How to add a song

1. Pick the batch file for its main style and add a `pop(...)`: id (kebab case, unique), title, artist, year,
   styles, key (`"Eb"`, `"F#m"`), tempo (within 5 bpm of the record, in the pulse the chart counts: a slow
   half time feel counts half),
   pattern, difficulty, confidence, note, the section order, and the chart.
2. The chart is one line per section: `Chorus: F Am7 | Dm7 F/C | Bb F/A | Gm7 Bb/C`. Bars split by `|`; chords in a
   bar share it evenly unless they give beats (`F:1 Bb:1 C:2`); `(C G | Am F) x4` writes bars out again. Chords are a
   root, a `ChordType` suffix (m, 7, maj7, m7, sus2, sus4, 6, m6, add9, 9, dim, dim7, m7b5, aug) and an optional slash
   bass. The order is `Intro, Verse x2, Chorus`; `Chorus#up` tells two charts of one section apart (the tag is not
   shown). Compound time counts its dotted quarters: 12/8 as four beats, 6/8 as two. An unknown chord, a bar whose
   beats do not add up, a section in the order with no chart or a chart never played fails the build's tests.
3. Difficulty is not a guess: `PopSongs.difficultyByRule` (one to start, one for five chords, one for eight, one for
   three colour chords, one for changes faster than 0.9 a second). Run `PopSongsTest`; its message gives the number.
4. Every chord outside the key needs a reason in the note, with one of the words borrowed, secondary, passing,
   modal, chromatic or blues.
5. Write the note about the chords and the feel only. Never write, quote or paraphrase a line of the song anywhere.
6. Leave a song out rather than guess its key or its chords.

## Tests

- `CourseTest`: 70 unique lesson ids, 3 to 6 steps, every exercise parses and every note gets a finger, no forbidden
  characters in step text, Repertoire lessons have the six-step shape and name a song that is in the library, Technique 2
  has the right step kinds, fingering counts and flat naming, every `songId` exists in `index.json`.
- `PopSongsTest` (21 tests): the chart language, every popular song parsing with full bars, unique ids, the lyric guard,
  the difficulty rule, the style, difficulty and four-chord minimums, chords in the key or explained, and the helpers.
- `LibraryContentTest`: 38 bundled songs parse with both hands; each new song has more than 20 notes, a title,
  composer, licence and attribution, a spread of difficulties, at least ten seconds of music, and sections that match
  between `index.json` and the MIDI markers.

## For the integration pass

- Nothing to wire: the Learn path, song library and player pick up the new levels, songs and section marks by
  themselves.
- Videos for the 15 songs are still to be curated (see `LibraryContentTest.NEW_SONGS`); when a song gets two videos,
  take it out of `TO_CURATE`.
- If another team adds a song to `index.json` or lessons to `Course.kt`, the counts in the two tests change (38 songs,
  70 lessons).

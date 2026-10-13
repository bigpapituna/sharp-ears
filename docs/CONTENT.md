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

## Tests

- `CourseTest`: 70 unique lesson ids, 3 to 6 steps, every exercise parses and every note gets a finger, no forbidden
  characters in step text, Repertoire lessons have the six-step shape and name a song that is in the library, Technique 2
  has the right step kinds, fingering counts and flat naming, every `songId` exists in `index.json`.
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

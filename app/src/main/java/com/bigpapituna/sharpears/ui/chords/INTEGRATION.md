# Songs by chords: integration notes

Branch `popp` (screens and play). Written against `engine/chords/PopSong.kt` only. popc writes the content
(`POP_SONGS`, `engine/chords/PopSongs.kt`), place writes the placement step. Chords, keys, tempos and section names only:
no melody, lyric or lyric fragment anywhere, notes included.

## What was built

Engine (Android-free, JVM tests in `app/src/test/.../engine/chords`):

- `engine/chords/PopPlay.kt` (one self-contained `object PopPlay` and the `BandBed` types)
  - `toSong(song, pattern, key, bpm): Song`: chord blocks. Right hand voice-led chords (the progressions' `voiceLead`),
    left hand the root or the slash chord's bass, seven rhythms, sections carried as `Song` sections, key and tempo moved,
    fingers per right-hand note (dictionary fingers for triads, the solver's for the rest). Song id
    `pop-<id>-<pattern>-<key>-<bpm>`: the `pop-` prefix is what shows the player's Backing toggle (as `prog-` does).
  - `bandBed(song, pattern, key, bpm): BandBed`: generic drums (kick, snare, hat) and a bass an octave under the left hand,
    from the chords and the pattern. `BandBed.cuesIn(fromMs, toMs)` is what a player asks each frame.
  - `chordTrack(...)` (the lane's Chords label, one chord per beat so two chords in a bar both show), `run(...)` (song, bed
    and chord track together), `bars(...)`, `name(chord, flats)` (sharps in sharp keys: G#m, not Abm), `keyName`, `flats`,
    `shift`, `nearest`, `feel(pattern)` (the one-line feel), `patternName`, and the run ids (`runId`, `runIdOf`, `ofRun`,
    `titleOfRun`, 2.6.0).
- `engine/chords/PopFind.kt`: style chips with their counts (`chipCounts`, "Pop 40"), the four sorts, `known(mastery)`,
  `missing`, `canPlay`, `oneAway`, `playableCount`, `using(songs, chords)` (songs that use a finished pack's chords),
  `withChord`; the rules underneath are the content's (PopSongs.kt).
- `engine/chords/BandSounds.kt`: the three drum sounds as samples made in code (no files), same bytes every run.
- `audio/BandPlayer.kt`: drums through a SoundPool (cached wavs like the metronome's), bass on the piano sampler.

UI (`ui/chords`):

- `PopSongsScreen.kt`: `PopBrowse` (chips, sort pills, tiles, the kid list), `PopSongTile`, `DifficultyBars`,
  `ConfidenceNote`, `FourChordBadge`, `PlayableLine` ("You can play N songs"), `PopSongsCard` (Songs tab),
  `SongsForChordsCard` ("Songs that use these chords").
- `PopSongPage.kt`: the song page (chart by section, transpose stepper, tempo stepper, seven-pattern picker with the feel line,
  Hear the band with the chart lighting bar by bar and beat by beat, Play along, the note line, the chord tap that opens the
  dictionary sheet) and the kid variant (colour blocks by root, letters for readers, animals for pre-readers, no numbers, a tap
  plays the chord).
- `BandRider.kt`: what the player holds for the band, so the player's own body gains one call.
- `PopCatalogue.kt`: the one place that says which songs there are (`POP_SONGS`). `PopCardsDemo.kt`: debug.

## Integrated in 2.6.0 (what the integrator did)

1. **The real list.** `PopCatalogue.songs` is `POP_SONGS` (107 charts); `PopSamples.kt` is gone.
2. **One rule each.** `PopPlay.toSong` is the only `toSong` (popc had none). The four-chord badge is the content's
   `PopSongs.isFourChord` (PopFind's own stricter rule was deleted: a three-chord I IV V song, a IVmaj7 or a V7 now wears
   it, a minor key never). Easiest first is `PopSongs.beginnerOrder` (difficulty, four-chord songs, newest, title);
   `PopFind.chordsOf` is `PopSongs.chordsOf`; "can play" and "one away" are `PopSongs.playableWith`; transposing (the
   chart and the page's chord chips) is `PopSongs.transposed` (`PopPlay.moved` was deleted).
3. **Ids.** `Screen.PopSong(songId)` and the `popsong:` route part look the song up in `PopCatalogue.songs`. A run is kept
   under `PopPlay.runId(song)` = `pop-<id>` (`PopPlay.runIdOf(played song id)`), so a saved best or a brief's SongRun
   finds its song again.
4. **Placement.** place shows no pop song; nothing to wire.

## Entry points (all wired in MainActivity on this branch)

- Chords tab > **Songs** tab (`ChordsTab.SONGS`, shown when the list is not empty): `ChordsScreen(popSongs, popStart, onOpenPopSong)`.
- Songs tab card for grown-ups: `LibraryScreen(chordSongs = ...)` shows `PopSongsCard`; it opens Chords > Songs.
- Song page: `Screen.PopSong(songId, back, shift, pattern, bpm)`. Back from the player returns to it with the same key,
  pattern and tempo.
- Player: `Screen.Player(song, null, start, 0.0, back, band, chords)`; `PlayerScreen(..., band = ...)`. With a band the
  Backing toggle is labelled **Band** and plays drums and bass (and the app plays the left hand); `chordTrack` feeds the
  Chords pill. The player starts in Wait with the right hand only; Listen and Perform are the player's own.
- "You can play N songs": `PlayableLine` under the Chords tab row (all tabs but Songs; a tap opens Songs on "Songs I can
  play"), and in the Songs tab itself.
  "Known" = a chord in the Leitner store at box 3 or more (played cleanly three times running), `PopFind.KNOWN_BOX`.
  The app's own "mastered" is box 4 (a week of reviews); that would show the count only after a week, so 3 is used.
  Change the constant if Roy wants it stricter.
- Kids (2.6.0): a "Songs by chords" tile on the kid's Songs (`KidSongsScreen(onChordSongs = ...)`, after the grown-up's
  shelf, hidden while a duet is being picked) opens `Screen.KidChords`, the kid list (`KidChordSongs`: `PopBrowse(kid =
  true)`, the Kids style only, easiest first); a song opens the kid page. Readers see letters on the tile and the page,
  pre-readers the animals. The kid page shows the pup's line, never a song's note (a note may count in words), and never a
  year, a tempo or a number. Debug: `--es screen popsongs --ez kid true` (no song) opens the kid list.

## Events (wired in 2.6.0)

- **After a chord session** (Chords > Daily: a pack's Practise or today's chords, not inside Practice today): the
  "Session done!" celebration carries `SongsForChordsCard` over the profile's known chords plus the session's chords
  (still new to the Leitner boxes); a song opens its page (the session ends), "See all the songs you can play" opens Chords
  > Songs sorted to "Songs I can play". Logged `pop: songs for the session's chords: <ids>|none`. No card when no song fits.
- **Opening a song page**: `ProgressEvent.FeatureUsed("popsongs")`.
- **A run to its end** (PlayerScreen's `popRunEnd`, for a played song whose id `PopPlay.runIdOf` reads): Wait or Perform,
  something played, logs the brief's `SongRun` under `pop-<id>` (stars from the Perform accuracy, or the wrong keys in Wait,
  as `Stars.of` gives a library song), saves a best from the top (`best:pop-<id>`), marks it played (`done:pop-<id>`),
  records `ProgressEvent.SongRun` (XP, first completion) and shows the recap with its XP. Listen or nothing played: no
  recap, nothing kept. No trouble bars, auto speed, sticker drop or quest (they belong to library songs). A kid's run to
  the end shows the pup's card first, as a library song does. Log: `pop: run pop-<id> mode=WAIT ... stars=n ...`.
- **Brief titles**: `PopPlay.titleOfRun` answers `pop-<id>` in the template writer's `CourseTitles.lookup`, the AI's
  `Titles.course` (`song(id)`; not in `all()`) and `AppTitles`. A brief action on a pop run (Practise together) opens the
  song's page for that person (no duet there).
- **Tester**: the screen name of a page is `popsong:<id>` (no key, pattern or tempo); the kid list is `kidchords`.

## Decisions

- The band is drums and bass only, generic; the "Band" toggle reuses the Backing toggle and the metronome's click
  count-in (one bar), so a pop song needs no new button. The band is silent while the mic listens, like every sound the
  app makes in Wait and Perform (headphones lift it).
- Hear the band plays the bed and, by default, a soft right-hand piano (pill "Piano with it" turns it off), so the chords
  are heard against the groove.
- "Four chords" is the content's rule (2.6.0, `PopSongs.isFourChord`): a major key and every chord its I, IV, V or vi, slash
  bass notes ignored, colours allowed (sus, add9, 6, Imaj7, IVmaj7, V7, vi7); a minor-key song never wears it.
- Patterns: WHOLE a chord a chord-span; HALVES on beats 1 and 3; QUARTERS every beat (bass 1 and 3); BROKEN one held chord
  over a left-hand root-fifth-third-fifth; BALLAD_ARPEGGIO the right hand rolling the chord up and back in eighths over
  a held bass; SHUFFLE long-short on every beat (swung thirds); EIGHTHS_ROCK chords in eighths, bass every beat. A chord that
  starts off a pattern's grid is struck where it starts.
- The song page lets a grown-up change tempo in steps of 4 bpm (the player has its own speed). Transpose is -6 to +6.
- No difficulty number is shown anywhere: one to four small bars, with a spoken word ("easy", "gentle", "tricky", "hard") for TalkBack.

## Debug routes (debug builds; add `--es profile owner` on a phone with a picker)

    --es screen popsongs [--es chip all|hits|yacht|80s|pop|rock|country|soul|kids|holiday] [--es sort easiest|newest|can|one]
    --es screen popsongs --es song <id> [--ei shift n] [--es pattern HALVES] [--ei bpm n] [--ez hear true] [--ez kid true [--ez pre true]]
    --es screen popsongs --es view cards

## Tests

`PopPlayTest` (rhythm per pattern, sections, transposition, slash chords, 3/4, fingers, band, drum sounds, chord track) and
`PopFindTest` (sorts, chips, "can play" and "one away", four-chord rule, `using`).

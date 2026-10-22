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
    `shift`, `nearest`, `moved`, `feel(pattern)` (the one-line feel), `patternName`.
- `engine/chords/PopFind.kt`: style chips, the four sorts, `known(mastery)`, `missing`, `canPlay`, `oneAway`, `fourChord`,
  `playableCount`, `using(songs, chords)` (songs that use a finished pack's chords), `withChord`.
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
- `PopCatalogue.kt`: the one place that says which songs there are. `PopSamples.kt`: six sample songs. `PopCardsDemo.kt`: debug.

## What the integrator must swap

1. **Samples to the real list.** `PopCatalogue.songs` returns `POP_SAMPLES`. Make it return `POP_SONGS`, then delete
   `ui/chords/PopSamples.kt`. Nothing else names the samples.
2. **One `toSong`.** popc builds a `toSong` in PopSongs.kt too. The screens only call `PopPlay.toSong`, `PopPlay.bandBed`,
   `PopPlay.chordTrack` and `PopPlay.run`, all in `PopPlay`. Keep PopPlay's and delete popc's, or point `PopPlay.toSong` at
   popc's and keep the band and the chord track. If popc's helpers use names like `PopFind`/`PopPlay` there will be a
   clash: rename theirs, these are the screens' names.
3. **Songs on a song page by id.** `Screen.PopSong(songId)` looks the song up in `PopCatalogue.songs` (and the saved-route
   `popsong:` part restores it), so ids must stay stable (the contract says they do).
4. **Maybe place's step.** Whatever place builds (a placement step for a new player) can open a song with
   `screen = Screen.PopSong(id, back)`, or show `PopBrowse` inline.

## Entry points (all wired in MainActivity on this branch)

- Chords tab > **Songs** tab (`ChordsTab.SONGS`, shown when the list is not empty): `ChordsScreen(popSongs, popStart, onOpenPopSong)`.
- Songs tab card for grown-ups: `LibraryScreen(chordSongs = ...)` shows `PopSongsCard`; it opens Chords > Songs.
- Song page: `Screen.PopSong(songId, back, shift, pattern, bpm)`. Back from the player returns to it with the same key,
  pattern and tempo.
- Player: `Screen.Player(song, null, start, 0.0, back, band, chords)`; `PlayerScreen(..., band = ...)`. With a band the
  Backing toggle is labelled **Band** and plays drums and bass (and the app plays the left hand); `chordTrack` feeds the
  Chords pill. The player starts in Wait with the right hand only; Listen and Perform are the player's own.
- "You can play N songs": `PlayableLine` under the Chords tab row (all tabs but Songs), and in the Songs tab itself.
  "Known" = a chord in the Leitner store at box 3 or more (played cleanly three times running), `PopFind.KNOWN_BOX`.
  The app's own "mastered" is box 4 (a week of reviews); that would show the count only after a week, so 3 is used.
  Change the constant if Roy wants it stricter.
- Kids: `PopBrowse(kid = true)` and `PopSongPage` read `LocalProfile.isKid` (the page also takes `kidOverride`). **No kid
  entry is wired**: the kid home's Songs button opens `KidSongsScreen`. A tile there that opens `Screen.PopSong` (Kids-style
  songs only) is the way in; decide with the kid home's owner. The kid page shows the pup's line, never a song's note
  (a note may count in words), and never a year, a tempo or a number.

## Events the integrator must wire

- **After a chord pack is finished** (`LessonStore.answer` at the end of a session over one pack, or the pack row's
  Practise): show `SongsForChordsCard(PopCatalogue.songs, known, onOpen = { screen = Screen.PopSong(it.id, back) }, onMore = { Chords > Songs with PopStart(sort = CAN_PLAY) })`
  with `known = PopFind.known(lessons.mastery())` (the finished pack's chords are already in it once recorded; or pass the
  pack's chords unioned in). It draws nothing when no song fits, so it can be put in the session-done Celebration or the
  Daily tab without a check. Seen in `--es screen popsongs --es view cards`.
- **Progress events**: none are recorded yet. Natural ones: `ProgressEvent.FeatureUsed("popsongs")` on opening a page,
  and a run to the end through `onFinished` in `PlayerScreen` (the Player's `ranToEnd` is already tracked in MainActivity).
  A pop run has no `SongMeta`, so no stars or best score are kept (same as the progressions).
- **Brief log**: not wired (no `SongMeta`).

## Decisions

- The band is drums and bass only, generic; the "Band" toggle reuses the Backing toggle and the metronome's click
  count-in (one bar), so a pop song needs no new button. The band is silent while the mic listens, like every sound the
  app makes in Wait and Perform (headphones lift it).
- Hear the band plays the bed and, by default, a soft right-hand piano (pill "Piano with it" turns it off), so the chords
  are heard against the groove.
- "Four chords" is exactly I, IV, V and vi in a major key, slash bass notes ignored; a seventh on one of them, a fifth chord,
  a three-chord song and any minor-key song do not wear it.
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

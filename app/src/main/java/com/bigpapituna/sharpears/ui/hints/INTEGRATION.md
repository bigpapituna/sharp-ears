# Detector and hints (2.1, stage 2): what the integrator wires

Branch `family-detector`. Owned files: engine/NoteMatcher.kt, engine/StallWatch.kt, engine/Struggle.kt, engine/hints/*,
ui/hints/*, ui/PlayerScreen.kt, audio/*, data/Reporter.kt. No shared file was edited (no MainActivity route).

## Wrong keys (with the skin team)

`MicEvent.Wrong(sample, midi, nearest, distance, riseDb)`: `midi` was played, `nearest` is the expected note closest
to it, `distance` semitones away (1..12), `slip` = within 2 (yellow), else red. It fires only on a confident piano
strike: a loudness jump no expected note explains, in which a key within an octave of the expected notes passes every
check a right note passes, at two moments 20 to 55 ms apart (a voice moves, a string does not), with no single-partial
slack and no lower note under it. Everything else is noise (below). `engine/hints/WrongKeys.kt` has `nearest`,
`distance`, `isSlip` and `WrongKey(midi, nearest, distance, id)` with `slip`, for touch and MIDI wrong keys too.

Would-be wrong flashes per recording (FixtureTest; before = every unexplained jump was a wrong note):

| recording | before | after | note |
|---|---|---|---|
| session-155837 (scale, MIDI truth, talking in the room) | 57 | 0 | 33/33 heard, 0 false strikes; asserted <= 2 |
| session-160604 (TV, nothing played) | 119 | 0 | |
| session-131834 | 134 | 6 | no truth: harness expects C3/C4/E4/G4/C5, so real keys count as wrong |
| session-154949 | 147 | 9 | no truth |
| session-163012 (chord practice) | 176 | 10 | no truth |
| session-164847 (false-green session) | 148 | 6 | no truth |
| session-155803 / 155830 / 155832 / 155834 | 72 / 4 / 2 / 6 | 0 / 0 / 0 / 0 | |
| session-163129 / 210815 / 211208 / 211214 / 211349 | 24 / 37 / 19 / 1 / 8 | 0 / 2 / 0 / 0 / 1 | |

**Default flip**: `MicInput.wrongFlash` now defaults to true (the switch in Microphone setup still turns it off; a stored
false stays false). `MicInput.paintsWrongKeys` = the setting and not stopped for this session by the watchdog.

**What the integrator wires**: PlayerScreen takes `onWrongKey: (WrongKey) -> Unit`. It is called for every wrong key
shown, from the mic (only while painting is on), touch and MIDI. Hand it to the skin team's wrong flash on the
keyboard and the lane (the key and its column glow yellow when `slip`, red otherwise, about 600 ms with a short
shake) and to the world team's pup head tilt for kids. Today the player still shows the old hit-line WrongGlow
(`HitLights.wrong`) for each one; the skin team decides whether that stays. The simplest wiring is a state in
PlayerScreen, e.g. `var lastWrong by remember { mutableStateOf<WrongKey?>(null) }` set in `showWrongKey()`, passed to
`Keyboard(wrongFlash = ...)` and `NoteLane(wrongFlash = ...)` once those parameters exist.

## Room-noise watchdog

`MicInput.noise: SharedFlow<Long>` (matcher samples) gets every jump with no piano note in it. `engine/hints/
NoiseWatch.kt` counts that noise while the expected keys stay silent (no right note for 4 s); 6 in a minute with at
least 2 painted wrong keys in the same minute trips it, once per app session (`MicInput.noiseWatch` lives as long as
MicInput). Wrong keys alone never trip it (a learner, not a room). PlayerScreen shows `ui/hints/NoiseSheet` over the
lane: the pup says "Noisy room? I can stop painting wrong keys for now", ChunkyButtons "Keep showing" and "Stop for
this session" (`MicInput.wrongPaintOff = true`, never stored), and "Pause listening" when nothing was played for 20 s
in wait mode (mic off for this song; `prefs.micDefault` untouched). Tuned on Roy's recordings: never on 155837 or
160604; trips on 163012 (10 s in) and 164847 (58 s in).

## Hint escalation

`engine/hints/HintPlan(profile.hintSeconds)`: GLOW at a, POINT at b, PLAY at c (grown-ups 6/12/18, kids 4/8/12), once
each per wait. PlayerScreen runs it in wait mode when labels are shown (not from memory or sight-reading: a hint would
give the notes away). GLOW: `ui/hints/KeyGlow` over the keyboard on the waiting keys in hand colours. POINT: the
painted hand (`ui/hints/PointingHand`, PaintedHand's frames, index finger tapping the key; the ghost hand hides
meanwhile). PLAY: the sampler plays the waiting notes (the mic is deaf for 1.7 s via `MicInput.deafFor`, so it does not
hear the phone) and the pup says "Your turn." The old single hint was the 5 s stuck moment; stuck reports still go out
at 5 s (StallWatch threshold unchanged).

**Skin team hook**: if the skin's Keyboard grows a highlight parameter, pass the waiting keys there when
`hintStep >= HintStep.GLOW` and drop the `KeyGlow` overlay in PlayerScreen's `Keys()`.

## Octave hint

`NoteMatcher.candidates: ((Candidate) -> Unit)?` reports every expected note turned down above the sensitivity floor
with `Reject` as a value (`HIGHER_OCTAVE`: only its octave above rose; `LOWER_OCTAVE`: the octave below did; others).
`engine/hints/OctaveHint` fires once per wait when the strongest thing heard for 2 s was an expected note an octave off
(those reasons, or a wrong key exactly 12 away). MicInput feeds it on the audio thread; PlayerScreen resets it per wait
(`mic.octaveReset`) and polls `mic.octaveCheck()`. The pup says "That's the right note, but one octave up. Try lower."
(or "down ... Try higher.") and `ui/hints/OctaveArrow` draws the arrow over the keyboard from the played key to the
right one. Roy's clip stall-20261017-210524-lesson-together-4: fires 2.0 s after his A5, about A4, "octave up".

**Any-of lessons**: PlayerScreen takes `anyOfLessons: Set<String>` (lesson ids). The integrator passes the playground
team's list (e.g. `Playground.ANY_OF_LESSONS`) from LessonScreen's `PlayerScreen(...)` call; songs are never skipped.

## Per-profile mic sensitivity and report tag

`MicInput.forProfile(kid, micOffsetDb)` sets `NoteMatcher.offsetDb` (the strike floor `sensitivityDb` and the room
margin drop by it; the room margin never below 7.5 dB, Roy's false greens sat up to 7 dB over the room) and the report
tag. PlayerScreen calls it from `LocalProfile`; **call it in MainActivity.openApp() too** (other mic screens: chords,
ear games, tuner, playground): `mic.forProfile(profile.isKid, profile.micOffsetDb)`. The stored sensitivity setting
(MicSetupScreen's slider) is untouched. Every report MicInput saves (stuck moments, flags, answer keys) gets
`profile=kid|adult` under its "# sharp ears" header (`StallWatch.tagProfile`); never the name. Stall reports also
print `mic_offset_db=`.

## Raw frames (playground and play teams: the clap counter)

`MicInput.rawFrames: ((FloatArray) -> Unit)?`, called on the audio thread with each chunk as read (512 mono floats at
48 kHz, -1..1), before note matching, mic and debug injection alike. The array is reused: copy what you keep, return
fast. Set it while the clap screen listens and null it after (one consumer at a time, like `tap` for the tuner).

## Other screens

Chord games, ear games and chord practice listen only for right notes. They can save the wrong-key search with
`mic.matcher.config = mic.matcher.config.copy(nameWrongKeys = false)` while open (not stored). Cost with it on: a
cheap first look at three partials per key, full checks for the top three; FixtureTest replays ran 5 to 20% slower than
the old matcher (most where noise is constant).

## Logs (tag SharpEars)

`wrong key X near Y, d away, yellow|red`, `input mic -> WRONG X ... (painted=...)`, `noise watch: offer|keep showing
wrong keys|wrong keys off for this session|listening paused`, `hints: GLOW|POINT|PLAY on [...] after n s`,
`hints: the app plays [...]`, `octave hint: X played an octave up|down`, `mic: deaf for n ms`,
`mic: profile kid|adult, offset n dB`.

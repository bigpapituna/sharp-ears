# Piano Song & Chords Learning App — Android Implementation Roadmap

Planning document only. No application code lives here. Written 2026-10-07 for implementation by Claude Code running **Opus 5.5**.

Companion document: `DESIGN-video-mic-chords.md` (YouTube import, mic-only input on the Galaxy Z Fold7, evolving chord teaching, Opus 5.5 effort levels).

Reference app: **Artie** by ArtMaster (artmaster.com/artie). The Instagram reel at https://www.instagram.com/reels/DeDkJiYpLOd/ shows it in use ("IRL Piano Tiles"). A frame from that reel is saved at `reference/artie-reel-frame.jpg` for internal reference only. Do not ship it.

---

## 0. How to use this document

- **One phase per Claude Code session.** Phases are in §7.2. Each has Goal, Files, Done-when, Verify. Start a fresh session per phase so context stays clean.
- **Done-when is the contract.** A phase is finished only when every done-when line is checked, by the commands listed, not by reading the code.
- **Placeholders** look like `[[NAME]]`. §8 lists them with the phase that first needs each one. Fill them before that phase starts.
- **Rules for the implementing agent** (also goes into `CLAUDE.md`, Appendix A):
  1. Stdlib and platform APIs first. Add a dependency only when the plan names it or a few lines cannot do the job.
  2. The `engine` package has zero Android imports so it runs under plain JVM unit tests.
  3. Every non-trivial piece of logic ships with one test that fails if the logic breaks. No test frameworks beyond JUnit.
  4. Build only the current phase. Do not scaffold for later phases.
  5. Verify with the screenshot loop (§6.2) and unit tests before declaring done. Commit once per phase.
  6. Mark deliberate shortcuts with a `ponytail:` comment naming the ceiling and the upgrade path.

---

## 1. What the reference app does

**Seen in the reel.** Phone in landscape on the music stand of a digital piano. Top strip: a few bars of notation with the current bar highlighted. Middle: a dark lane where colored note blocks (one color per hand) fall toward a hit line. Bottom: a full-width piano keyboard with the keys to play lit up. The player plays the real piano. The phone hears it through the microphone and the song advances.

**Published feature set** (artmaster.com/artie and their review article, read 2026-10-07):

| Artie feature | This app v1 | Notes |
|---|---|---|
| Microphone input, song reacts to the real piano | Yes | Core feature. §5.4 |
| MIDI input (USB / Bluetooth) | Yes | Cheap with `android.media.midi`, and the reliable fallback for testing |
| Falling notes, keyboard at the bottom | Yes | Compose Canvas. §5.6 |
| Wait Mode: music pauses until the correct note is played | Yes | Core feature. §5.5 |
| Tempo slow-down | Yes | 0.25x to 1.0x |
| Left / right / both hands | Yes | Non-practiced hand is auto-played or hidden |
| Loop a section | Yes | A–B loop on the timeline |
| Scrub through the song | Yes | Seek bar plus drag on the lane |
| Session recap: rhythm, accuracy, score | v1.1 | Perform mode, P10 |
| Song library, 3 difficulty levels per song | Partial | Twelve public-domain songs bundled, one arrangement each, plus imported songs such as Alicia (§8) |
| MIDI / MusicXML import | MIDI in v1.1 (P11), MusicXML backlog | |
| Notation strip above the lane | Backlog | Rendering notation is a project of its own |
| Step-by-step lessons, spoken guidance, AI Q&A | Backlog | Would need a hosted LLM; not in scope |
| Chords learning | Yes, and beyond Artie | Dictionary, practice, progressions. §5.8 |
| YouTube tutorials per song | Yes, not in Artie | §6.3 |
| iOS + Android, $34.99/month | Android only, personal use | |

---

## 2. High-level architecture

Single Android app module. Kotlin, Jetpack Compose, Material 3. Minimum SDK 26, target the latest stable SDK. No NDK, no DI framework, no database in v1.

```
┌──────────────────────────────────────────────────────────────────┐
│ UI (Compose)   Library · Player · Chords · Tutorials · Settings   │
│                Calibration (mic)                                  │
├──────────────────────────────────────────────────────────────────┤
│ ViewModels     one per screen; hold engine state across rotation │
├───────────────────┬──────────────────────────┬───────────────────┤
│ INPUT BUS         │ ENGINE (pure Kotlin)     │ OUTPUT            │
│ TouchKeyboard     │ Song model               │ Sampler           │
│ MidiInput         │ SmfParser (.mid files)   │   (SoundPool)     │
│ MicMatcher        │ Transport + WaitMode     │ Metronome / UI fx │
│ WavInjector (dbg) │ Chords + Progressions    │ YouTube embed     │
│                   │ Scoring (v1.1)           │                   │
│  all emit NoteOn(midi, velocity) / NoteOff(midi)                 │
├───────────────────┴──────────────────────────┴───────────────────┤
│ DATA   assets/songs/*.mid + index.json · assets/samples/*.ogg     │
│        SharedPreferences (progress, settings)                     │
└──────────────────────────────────────────────────────────────────┘
```

**Principles**

- Every input source produces the same two events. The engine never knows whether a note came from a finger, a cable, or a microphone.
- The renderer is a pure function of `(song, positionMs, transportState, visibleKeyRange)`.
- Songs are stored as Standard MIDI Files plus one `index.json` of metadata. One parser serves bundled songs and user imports.
- The engine package compiles without Android, so the risky logic (wait mode, note matching, chord detection) is tested in milliseconds on the JVM.

**Package layout** (one Gradle module `app`)

```
app/src/main/java/com.bigpapituna.sharpears/
  engine/     Song.kt  SmfParser.kt  Transport.kt  Chords.kt  NoteMatcher.kt  Midi.kt(parsing only)
  audio/      MicInput.kt  Sampler.kt  MidiDevices.kt  WavInjector.kt
  ui/         LibraryScreen  PlayerScreen (NoteLane, Keyboard, TransportBar)  ChordsScreen
              TutorialTab  SettingsScreen  CalibrationScreen  theme/
  data/       SongRepository.kt (assets + index.json)  Prefs.kt
  MainActivity.kt  App.kt (navigation)
app/src/main/assets/songs/      *.mid, index.json
app/src/main/assets/samples/    piano samples (OGG)
app/src/test/                   JVM unit tests + fixtures (.mid, .wav)
scripts/                        dev.ps1 (build/install/launch/screenshot), samples.ps1 (ffmpeg)
CLAUDE.md  PLAN.md  reference/
```

---

## 3. Feature list

### v1 (MVP, mirrors the reel)

| ID | Feature | Detail |
|---|---|---|
| F1 | Song library | List of bundled songs with hand info, difficulty tag, progress badge. Tap to open the player. |
| F2 | Falling-notes player | Dark lane, note blocks colored by hand, hit line, on-screen keyboard that lights the keys to play. Landscape-first. |
| F3 | Listen mode | Song auto-plays through the sampler at the chosen speed. Notes light the keys as they hit. |
| F4 | Wait mode | Playback freezes at each note group until the player plays every note in it. Early hits within 200 ms count. Wrong notes flash red and do not advance. |
| F5 | Scrub and loop | Seek bar, drag the lane to scrub, A–B loop markers, jump to section. |
| F6 | Speed and hands | Speed 0.25x–1.0x. Hand filter: both, left, right. The other hand is hidden or auto-played. |
| F7 | Inputs | On-screen multi-touch keyboard, USB/Bluetooth MIDI, microphone matcher. Selectable in Settings, auto-detect MIDI when a device appears. |
| F8 | Calibration | Live input meter, sensitivity sliders, "play middle C" check. |
| F9 | Chords | Dictionary (root × type, inversions, fingering), chord practice (play the shown chord with any input), 3+ preset progressions that run through wait mode. |
| F10 | Tutorials tab | Curated YouTube videos per song in an embedded player, plus "Open in YouTube". |
| F11 | Persistence | Per-song progress (best accuracy, last position, completed), settings, calibration values. |
| F12 | Accessibility and polish | Content descriptions on keys and controls, 48 dp touch targets, dark theme, app icon, key-name labels toggle. |
| F13 | MIDI import | Pick a `.mid` from device storage (Storage Access Framework). It joins the library with a middle-C hand split by default. Needed for Alicia and any creator MIDI. |

### v1.1

- Perform mode with session recap (accuracy, timing, streak), saved as best score.
- Chord-name quiz.
- Accompaniment: app plays the other hand while the mic listens. Only when headphones or MIDI are in use (speaker output would feed back into the mic).
- Runtime YouTube search (needs `[[YOUTUBE_API_KEY]]`).

### Backlog, explicitly not planned

Notation strip; polyphonic ML transcription (Basic Pitch); guided lessons and AI assistant; MusicXML import; licensed song catalog; cloud sync; iOS.

---

## 4. Required libraries and APIs

Do not pin versions in this document. Resolve the latest stable versions in P0 and record them in `gradle/libs.versions.toml`.

| Need | Choice | Kind | Why this, and what was rejected |
|---|---|---|---|
| Language, UI | Kotlin, Jetpack Compose (BOM), Material 3, Activity Compose, Lifecycle ViewModel Compose, Navigation Compose | AndroidX | Standard stack. Navigation Compose is one dependency and avoids hand-rolled back-stack bugs. |
| Async | kotlinx.coroutines, StateFlow | comes with Compose | |
| JSON (`index.json`) | `org.json` | Android stdlib | Small file; no serialization plugin needed. |
| Persistence | `SharedPreferences` | Android stdlib | ~50 keys. Room and DataStore rejected as overkill. |
| MIDI devices | `android.media.midi` (`MidiManager`, USB host + BLE MIDI) | Android stdlib, API 23+ | Native. BLE needs `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` runtime permissions on API 31+. |
| MIDI file parsing | In-repo minimal SMF parser, ~200 lines | own code | Format 0/1, variable-length quantities, running status, note on/off (velocity 0 = off), tempo map (meta 0x51), time signature (0x58), track names (0x03). Rejected: android-midi-lib (unmaintained), JFugue (huge). |
| Audio capture | `AudioRecord` | Android stdlib | Source `UNPROCESSED` when supported, else `VOICE_RECOGNITION`, else `MIC`, to keep automatic gain control and noise suppression off the signal. |
| Note detection | In-repo RMS onset detector + Goertzel filters on expected notes, ~150 lines | own code | The engine always knows which notes are expected, so detection is "was C4 just struck?", not full transcription. Upgrade path: Spotify Basic Pitch (Apache-2.0) via LiteRT (formerly TensorFlow Lite) or ONNX Runtime for true polyphonic transcription. |
| Audio output | `SoundPool` with one OGG sample per key | Android stdlib | 20–40 ms latency, fine for a learning app. Upgrade path: Oboe (C++/NDK) if latency is a complaint. |
| Piano samples | Default chosen (§8): VSCO 2 Community Edition (CC0) if it includes a piano, else Salamander Grand Piano (CC-BY 3.0, attribution required). Convert with ffmpeg to 1.5 s OGG at ~48 kbps, ~3 MB for 88 keys. | asset | Verify the license file at implementation time. |
| YouTube playback | `com.pierfrancescosoffritti.androidyoutubeplayer:core` (wraps the official IFrame Player API; no API key) | Maven Central, open source | Fallback: `WebView` with the IFrame embed (~60 lines, fullscreen and lifecycle handled by hand). Always also offer an "Open in YouTube" `Intent`. Follow the YouTube API Services Terms (official embed only, no ad blocking, player not smaller than 200×200). |
| YouTube search (v1.1 only) | YouTube Data API v3 `search.list` | public REST, API key | 100 quota units per search, 10,000 units/day free. Only if curated IDs are not enough. |
| Tests | JUnit 4, kotlinx-coroutines-test, Compose UI test (`ui-test-junit4`), `adb` | | Robolectric rejected; the emulator plus adb covers UI checks. |
| Tooling | Android Studio (bundles JDK 17+, SDK, emulator), Gradle wrapper, version catalog, ffmpeg (samples, test WAVs), Python 3 + `mido` (fixture MIDI only, optional) | | |

Permissions: `RECORD_AUDIO` (runtime), `INTERNET` (YouTube), `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` (runtime, BLE MIDI only), `<uses-feature android.hardware.usb.host required=false>`, `<uses-feature android.software.midi required=false>`.

No proprietary APIs. No Claude API in the app. Claude Code is a build-time tool only (§6.4).

---

## 5. Data flow

### 5.1 Data model (engine, pure Kotlin)

- `Note(midi: Int, startMs: Long, durationMs: Long, hand: Hand, velocity: Int)`
- `OnsetGroup(startMs, notes: List<Note>)`: notes whose start times fall within 30 ms of each other (a chord or a two-hand strike). Wait mode operates on groups, not notes.
- `Section(name, startMs, endMs)`: from MIDI markers (meta 0x06) or bars, used for "jump to section" and loop presets.
- `Song(id, title, bpm, timeSignature, notes, groups, sections, durationMs)`
- `SongMeta` (from `index.json`): `id, file, title, composer, difficulty, handTracks {right: trackIndex, left: trackIndex} | split: "middleC", youtube: [ {id, title, channel, checkedOn} ], license, attribution`
- `TransportState(positionMs, speed, mode: LISTEN | WAIT | PERFORM, hands, loop: (startMs, endMs)?, waiting: Boolean, expected: OnsetGroup?, satisfied: Set<Int>)`
- `Progress(songId → bestAccuracy, lastPositionMs, completed)`, `Settings(inputSource, speed, hands, showLabels, keyboardRange, onsetThresholdDb, scoreThreshold, soundOn)`

### 5.2 Song content pipeline

1. Bundled `.mid` files in `assets/songs/` plus `index.json`.
2. On open, `SmfParser` turns ticks into milliseconds using the tempo map, merges all tracks, and assigns hands: by `handTracks` from `index.json`, else by track index (track 1 right, track 2 left, the common piano convention), else by channel, else split at middle C (midi 60).
3. Notes are grouped into `OnsetGroup`s and sections are derived. The result is cached in the ViewModel for the open song.
4. Sources for public-domain MIDI: Mutopia Project (mutopiaproject.org), the Classical Piano MIDI Page (piano-midi.de, check its attribution terms), or write simple tunes by hand with a 20-line `mido` script. The v1 list is in §8.

### 5.3 Input bus

| Source | Emits | Notes |
|---|---|---|
| Touch keyboard | NoteOn + NoteOff | Multi-touch via Compose `pointerInput`; each pointer id maps to one key; slides between keys re-trigger. |
| MIDI | NoteOn + NoteOff | Parse 3-byte channel messages: `0x9n` with velocity > 0 is on, `0x8n` or `0x9n` with velocity 0 is off. Device picker lists `MidiManager` devices; auto-connect to the first one. |
| Microphone | NoteOn only | No reliable note-off from a mic. The engine treats mic notes as momentary. |
| WAV injector (debug builds) | NoteOn only | Same pipeline as the mic, fed from a WAV file in `assets/debug/` or a path passed via `adb shell am start --es wav <path>`. Makes wait mode testable end to end on the emulator. |

All sources publish to one `SharedFlow<NoteEvent>` on the main thread. The sampler listens to the same flow for touch and MIDI (so the on-screen keyboard makes sound), and is muted while the mic is the active source (§5.7).

### 5.4 Microphone note detection

The problem is narrower than transcription. In wait mode the engine knows the expected group (1–4 notes). The matcher answers "did a strike just happen, and does it contain the expected notes?"

Pipeline, running on a dedicated audio thread at `THREAD_PRIORITY_URGENT_AUDIO`:

1. `AudioRecord`, mono, 16-bit PCM, 44.1 kHz (fallback 48 kHz). Read in hops of 512 samples (11.6 ms) into a 4096-sample ring buffer (93 ms window).
2. **Onset detection.** Compute the window's RMS in dB. An onset fires when RMS rises at least `onsetThresholdDb` (default 6 dB) above the trailing 200 ms median within 40 ms, then enters an 80 ms refractory period.
3. **Classification at onset.** For each expected note, run Goertzel filters at harmonics 1–4 of its fundamental, weighted 1.0 / 0.7 / 0.5 / 0.3, on the frames 20–80 ms after the onset, and take the best frame. Score = weighted harmonic energy divided by total frame energy, so loudness cancels out.
4. **Decoy check.** Also score the expected note shifted by ±1 semitone. Accept only if the real score is at least `scoreThreshold` (default 0.15) and at least twice the decoy score. A broadband thump raises all bins equally; a real note raises its own harmonics far more.
5. **Chord window.** Notes of one group may arrive across several onsets within 250 ms. Each accepted note marks itself satisfied; the group completes when all notes of the practiced hand are satisfied.
6. **Wrong-note signal.** An onset with no accepted expected note is reported as "wrong note" (the matcher cannot say which note). The UI flashes red; the transport does not move.
7. **Debounce.** The same note within 120 ms is ignored.

Cost: at most 4 expected notes × (4 harmonics + 2 decoys × 4 harmonics) = 48 Goertzel filters over 4096 samples per 11.6 ms hop. Negligible on any phone.

Calibration knobs (exposed on the Calibration screen with a live meter): `onsetThresholdDb` (3–12), `scoreThreshold` (0.05–0.5), input source choice. Hardware varies; keep the knobs.

Known ceilings (mark with `ponytail:` comments): octave confusion when the expected note's harmonics coincide with a played note an octave below; no detection of which wrong note was played; noisy rooms raise false onsets; very fast repeated notes under 120 ms merge. Upgrade path: Basic Pitch model via LiteRT for full polyphonic transcription, swapped in behind the same `NoteEvent` interface.

Latency budget: window 93 ms + classification 20–80 ms after onset, so about 60–120 ms from key strike to NoteOn. Fine for wait mode, acceptable for Perform mode with a ±150 ms timing window.

### 5.5 Transport and wait mode

The transport is a pure state machine ticked once per frame with `dtMs`.

- **Listen mode.** `position += dt × speed`. When position crosses a note's start, emit a sampler note-on; crossing its end emits note-off. Keys light while a note is sounding.
- **Wait mode.** Advance as in Listen until position reaches the next unsatisfied group's start. Freeze there (`waiting = true`, `expected = group`). Each incoming NoteOn that is in `expected` (filtered to the practiced hand) is added to `satisfied`. When all required notes are satisfied: unfreeze, play the group's non-practiced-hand notes through the sampler if sound is allowed, advance to the next group. A correct NoteOn arriving up to 200 ms before the group's time counts as satisfied (prevents stutter at tempo). A NoteOn not in `expected` is a wrong note: UI feedback only.
- **Hand filter.** Only notes of the practiced hand are required. The other hand's notes are auto-satisfied and either hidden or drawn dimmed, and auto-played when the sampler is allowed to sound.
- **Seek.** `seek(ms)` sets position, clears `satisfied`, sets the next group to the first group with `startMs >= position`, and stops any sounding notes.
- **Loop.** When position reaches `loop.endMs`, `seek(loop.startMs)`.
- **Perform mode (v1.1).** Transport never waits. Each group is scored by the first matching NoteOn: within ±150 ms is "good", within ±300 ms is "ok", else "miss". Recap shows accuracy (good + ok over total), timing bias, longest streak.

Unit tests cover: gating, early hit, chord group completion, hand filter, seek reset, loop wrap, speed scaling, wrong note has no effect.

### 5.6 Rendering the lane and keyboard

- Compose `Canvas` inside the player screen. A `LaunchedEffect` loop calls `withFrameNanos`, ticks the transport with the real frame delta, and updates a `positionMs` state. The Canvas redraws on that state.
- **Keyboard geometry.** Visible range auto-fits the song: lowest to highest note, rounded outward to octave boundaries, minimum 2 octaves, maximum 5. White key width = lane width / white key count. Black keys are 60% of the width and height, offset to the right of their white neighbor. Range is also settable in Settings. No pan or zoom in v1.
- **Note blocks.** Lookahead 2.5 s at speed 1.0 fills the lane height, so `pxPerMs = laneHeight / 2500`. A note's rectangle has `height = durationMs × pxPerMs` (minimum 8 dp) and `bottom = hitLineY − (startMs − positionMs) × pxPerMs`. Draw only notes within `[position − 500 ms, position + lookahead]`, found by binary search on start time. Right hand orange, left hand blue (as in the reel), dimmed when filtered out, key label inside the block when labels are on.
- **Keyboard highlights.** Keys of notes currently crossing the hit line light in the hand color. In wait mode the expected keys pulse until satisfied; a satisfied key flashes green; a wrong note flashes the whole keyboard border red for 150 ms.
- **Transport bar.** Play/pause, mode toggle (Listen / Wait), speed chips, hand chips, A–B loop buttons, seek bar with section ticks, input-source icon with a live level dot in mic mode.
- **Scrub.** Vertical drag on the lane moves `position` by `dragPx / pxPerMs` with momentum off. The seek bar does the same proportionally.
- Orientation: the player requests `sensorLandscape` on entry and releases it on exit. Other screens follow the device.

### 5.7 Audio output rules

- Sampler voices: one `SoundPool` with 88 loaded OGG samples, max 16 streams, `USAGE_GAME` attributes for the low-latency path. Note-off sets the stream volume to 0 (no release envelope in v1).
- **Mic active ⇒ app piano output muted.** The real piano makes the sound. Speaker output would be picked up by the mic and trigger false onsets. The metronome and accompaniment hand are allowed only when `AudioManager` reports a wired or Bluetooth headset.
- Entering Listen mode stops the mic. Entering Wait mode with the mic selected restarts it.
- Touch and MIDI input always sound through the sampler when `soundOn` is true.

### 5.8 Chords module

- **Chord table.** Interval formulas: major, minor, diminished, augmented, sus2, sus4, 6, m6, 7, maj7, m7, dim7, m7b5, add9, 9. Inversions by rotating the formula. Default root-position fingering per triad and seventh (static table). Twelve roots × table = the dictionary.
- **Dictionary screen.** Pick root and type. The keyboard component highlights the chord, a Play button sounds it through the sampler, an Inversion chip cycles inversions, fingering numbers appear on the keys.
- **Practice.** The app shows a chord; the player plays it with the active input. Detection reuses the matcher with the chord notes as the expected group. For mic and touch, matching is by pitch class (any octave) so a beginner's octave choice does not fail them; for MIDI, exact notes. Success advances to the next chord; a progress counter and streak are shown.
- **Progressions.** Presets such as I–V–vi–IV and ii–V–I in C, G, F, D, A, generated as a `Song` whose groups are chord blocks at one chord per bar. They run through the normal player in wait mode. This is the only place progressions are implemented.
- **Quiz (v1.1).** Show a highlighted chord, offer four names.

### 5.9 Persistence

`SharedPreferences` keyed by song id: best accuracy, last position, completed flag. Settings and calibration values as plain keys. The library screen reads them once on resume. No migrations needed in v1.

---

## 6. Integration

### 6.1 Claude Code (Opus 5.5) workflow

1. **Project location.** Create the code project at `C:\dev\piano-app` (chosen), a path with no spaces and outside OneDrive. Gradle inside a synced folder is slow and occasionally locks files. Copy `PLAN.md`, `CLAUDE.md`, and `reference/` into it. If the project must stay under OneDrive, exclude `build/` and `.gradle/` from sync.
2. **Git.** `git init` in P0, commit at the end of every phase with the phase id in the message. A GitHub remote is optional.
3. **Session setup.** Open the project folder as the working directory in the Claude Code desktop app. Select the Opus 5.5 model. Use high effort for P3, P4, P6 (engine and audio), medium for content phases (P8, P9).
4. **One phase per session.** Prompt pattern in Appendix B. The agent reads `PLAN.md` and `CLAUDE.md`, implements the phase, runs the verify commands, commits, stops.
5. **Plan mode first** for P3 and P6: ask for a one-page design before code, approve it, then implement.
6. **Human checkpoints.** Phases that need ears or hardware (P2 sound quality, P5 MIDI device, P6 real-piano test) end with a short manual checklist for Roy. Everything else is machine-verifiable.
7. **Optional skills** in later polish: `ui-ux-pro-max` for the player's visual pass, `ponytail` to keep the implementation minimal.

### 6.2 Agent verification loop

All commands go in `CLAUDE.md` and in `scripts/dev.ps1`. PowerShell on Windows.

```bash
.\gradlew.bat testDebugUnitTest
```
```bash
.\gradlew.bat installDebug
```
```bash
adb shell am start -n com.bigpapituna.sharpears/.MainActivity
```
```bash
adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png out\screen.png
```
```bash
adb shell input tap 540 1600
```
```bash
adb logcat -d -s AndroidRuntime:E SharpEars:D
```
```bash
adb shell dumpsys gfxinfo com.bigpapituna.sharpears | findstr Janky
```
```bash
adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es wav /sdcard/fixtures/c-e-g.wav --es song test-3-notes
```

The agent reads `out\screen.png` with its image reader after every UI change. Key coordinates for `input tap` are computed from the keyboard geometry in §5.6 and logged by the app in debug builds (`SharpEars: keyRect C4 = …`), so taps are deterministic. The app logs every NoteEvent and transport state change at debug level; the agent asserts on logcat, not on pixels, wherever it can.

### 6.3 YouTube tutorials

**Build-time curation (Claude Code task, P8).** For each song in `index.json`, web-search `"<title>" piano tutorial easy` and pick 1–2 videos. Criteria: title names the song and "tutorial"; falling-notes or slow "easy" versions preferred since they match the app; established channels or over 100k views; not age-restricted; embeddable, checked with the public oEmbed endpoint `https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=<id>&format=json`, which errors for videos that cannot be embedded. Record `{id, title, channel, checkedOn}` in `index.json`. The agent cannot watch video; Roy spot-checks the list once.

**In-app playback.** Tutorials tab in the player: embedded player via the `android-youtube-player` library (IFrame API, no key), plus an "Open in YouTube" button that fires an `ACTION_VIEW` intent. If the library misbehaves, replace with a `WebView` embed; keep the intent button regardless. Pause the embedded video when the lane starts playing.

**Runtime search (v1.1, optional).** YouTube Data API v3 `search.list` with `[[YOUTUBE_API_KEY]]`, cached per song for 30 days. Not needed if curation covers the library.

**Terms.** Use only the official embed. Do not download, strip audio, or hide the player's branding and ads.

**Video sync, share-sheet import, and lesson packs** are designed in `DESIGN-video-mic-chords.md` (engine-driven video follower, Type A/B import pipeline, evolving chord curriculum, Fold 7 specifics).

### 6.4 No Claude API in the app

The app has no LLM dependency at runtime. Artie's "AI assistant" and spoken guidance are backlog. If that changes, load the `claude-api` skill in that session; it is not part of this plan.

---

## 7. Build and test plan

### 7.1 Environment (Windows, once, by Roy)

1. Install Android Studio (bundles a JDK, SDK Manager, emulator). In SDK Manager install: latest platform, build-tools, platform-tools, emulator, and an x86_64 Google APIs system image for the latest stable API.
2. Create an AVD: Pixel 7 class, latest API, 4 GB RAM, hardware GPU.
3. Set `ANDROID_HOME` to the SDK path and add `platform-tools` to `PATH`. Point `JAVA_HOME` at Android Studio's bundled JDK or set `org.gradle.java.home` in `gradle.properties`.
4. On `[[TEST_PHONE]]`: enable Developer options and USB debugging; `adb devices` must list it. Mic tests run on this phone, not the emulator.
5. Install ffmpeg (`winget install ffmpeg`). Optional: Python 3 with `pip install mido` for generating fixture MIDI files.
6. Verify: `adb devices`, `.\gradlew.bat --version`, emulator boots to home screen.

### 7.2 Phases

Each phase: Goal · Files · Done when · Verify. "Verify" means the commands in §6.2 unless stated.

**P0 — Bootstrap**
- Goal: empty Compose app builds, installs, and the screenshot loop works.
- Files: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/` skeleton with one placeholder screen, `CLAUDE.md` (Appendix A), `scripts/dev.ps1`, `.gitignore`, first commit.
- Done when: `scripts/dev.ps1` produces `out\screen.png` showing the placeholder home screen on the emulator; `testDebugUnitTest` passes with one trivial test; `.\gradlew.bat lint` reports no errors.

**P1 — Song model and SMF parser**
- Goal: load a `.mid` into `Song` with correct millisecond timing and hand assignment.
- Files: `engine/Song.kt`, `engine/SmfParser.kt`, `data/SongRepository.kt`, test fixtures: a generated scale with one tempo change, plus the first bundled song from the §8 list (Für Elise) with its `index.json` entry.
- Done when: parser tests assert note count, first and last note timing across the tempo change, running-status handling, and hand assignment for both the `handTracks` and the middle-C strategies; opening the bundled song logs its note count and duration.

**P2 — Keyboard and sampler**
- Goal: an on-screen keyboard that plays piano samples under multi-touch.
- Files: `ui/Keyboard.kt`, `audio/Sampler.kt`, `assets/samples/` produced by `scripts/samples.ps1` from the sample set chosen in §8, the input bus.
- Done when: adb taps at computed key rects log the right NoteOn/NoteOff midi numbers; a screenshot shows the pressed key highlighted; two simultaneous touches produce two sounding notes (logged); Roy confirms the sound is acceptable on the phone. Attribution text for the sample set is in Settings → About.

**P3 — Falling notes and Listen mode** (plan mode first)
- Goal: the player screen auto-plays a song with notes falling onto the keyboard in sync with sound.
- Files: `engine/Transport.kt` (Listen only), `ui/PlayerScreen.kt`, `ui/NoteLane.kt`, `ui/TransportBar.kt`.
- Done when: transport unit tests pass (speed scaling, note-on/off emission, seek); screenshots at three timestamps show the expected notes at the expected y positions within 10 px, computed from the model; `gfxinfo` janky frames under 5% over a 30 s playthrough on the emulator; seek bar, lane drag, and speed chips work via adb input and logcat state lines; the player is landscape.

**P4 — Wait mode, loop, hands**
- Goal: the song only advances when the correct keys are played.
- Files: `engine/Transport.kt` (Wait), input bus wiring, UI feedback states.
- Done when: unit tests in §5.5 pass; adb-driven end-to-end on the emulator with touch input: a wrong key leaves position unchanged and logs a red flash; the right key advances to the next group; a two-note group needs both; A–B loop wraps; left-hand-only mode hides right-hand notes and auto-plays them.

**P5 — MIDI input**
- Goal: a USB or Bluetooth keyboard drives wait mode.
- Files: `audio/MidiDevices.kt`, `engine/Midi.kt` (byte parsing), device picker in Settings.
- Done when: byte-parser unit tests pass (note on, note off, velocity 0, running status, other messages ignored); on `[[MIDI_KEYBOARD]]` a played note advances wait mode and hot-plugging reconnects. If no MIDI hardware exists yet, the unit tests alone close this phase and the device test moves to §8.

**P6 — Microphone matcher, calibration, WAV injector** (plan mode first)
- Goal: a real piano drives wait mode through the phone's mic.
- Files: `audio/MicInput.kt`, `engine/NoteMatcher.kt`, `audio/WavInjector.kt`, `ui/CalibrationScreen.kt`, test WAVs (synthesized in-test from decaying harmonic sums, plus `[[FIXTURE_WAVS]]` recorded on the real piano).
- Done when: matcher unit tests pass for single notes C4/E4/G4 with decoy C#4 rejected, a two-note chord within the chord window, a repeated note, and silence with room noise producing no onsets; the WAV injector drives a 3-note test song through wait mode to completion on the emulator (screenshot shows the completion state, logcat shows three satisfied groups); calibration screen shows a live meter and persists thresholds; on the real piano Roy plays a slow C major scale with each hand and the app detects at least 90% of notes with at most one false onset per 30 s of silence.

**P7 — Chords**
- Goal: dictionary, practice, progressions.
- Files: `engine/Chords.kt`, `ui/ChordsScreen.kt`, progression generator.
- Done when: chord-table unit tests pass for every type and inversion across 12 roots; dictionary highlights and plays any chord; practice succeeds with touch input via adb and with the WAV injector; three progressions run through the player in wait mode.

**P8 — Library, metadata, tutorials**
- Goal: a real song library with tutorial videos.
- Files: `assets/songs/` with every bundled song in the §8 list, `index.json` complete including the Alicia entry that points at the user folder, `ui/LibraryScreen.kt`, `ui/TutorialTab.kt`, `ui/ImportMidi.kt` (SAF picker that copies the file into `files/user/`; merged here from P11).
- Done when: every bundled song parses and plays; Alicia, imported from the Fold's Downloads folder through the picker, parses with a middle-C hand split and plays with its tutorial video attached; a malformed import shows an error instead of crashing (unit test with a truncated fixture); each bundled song has at least one curated, embeddable video id with a checked date; the tutorial tab plays an embedded video on the emulator (network required) and "Open in YouTube" fires the intent; the library shows difficulty and progress badges.

**P9 — Persistence, settings, polish, accessibility**
- Done when: progress and settings survive a force-stop and relaunch (`adb shell am force-stop` then relaunch, logcat shows restored values); TalkBack reads key names and all controls (content descriptions present, verified with `adb shell uiautomator dump`); touch targets at least 48 dp; dark theme; app icon; key labels toggle; `lint` clean; APK under 30 MB.

**P10 — Perform mode and recap** (v1.1)
- Done when: scoring unit tests pass; a playthrough with the WAV injector produces the expected accuracy and streak; the best score is saved and shown in the library.

**P11 — merged into P8.** MIDI import is needed in v1 so Alicia can be loaded.

### 7.3 Test matrix

| Layer | What | How | Runs on |
|---|---|---|---|
| Engine | parser, transport, chords, matcher, scoring, MIDI bytes | JUnit, synthetic fixtures | JVM, every phase |
| UI | screens render, keyboard taps, wait mode E2E, chord practice | adb input + screenshots + logcat assertions; a few Compose UI tests for the keyboard | emulator |
| Audio in | mic matcher with real recordings | fixture WAVs through the injector | emulator; real phone for the final check |
| Audio out | sample playback, polyphony, latency feel | logcat for correctness; ears for quality | phone |
| MIDI | device connect, hot-plug | manual | phone + `[[MIDI_KEYBOARD]]` |
| Performance | frame time, mic thread CPU, cold start | `gfxinfo`, `top`, `am start -W` | emulator and phone |
| Accessibility | descriptions, targets | `uiautomator dump`, TalkBack walk | phone |

### 7.4 Budgets

- Frame time p95 under 16 ms in the player on the emulator and on `[[TEST_PHONE]]`.
- Mic pipeline under 10% of one core.
- Strike-to-NoteOn under 120 ms (mic), under 30 ms (MIDI).
- Cold start under 2 s. APK under 30 MB.

### 7.5 Release (only if `[[DISTRIBUTION]]` is Play Store)

Signing config, privacy policy covering the microphone, Data safety form, target SDK current, adaptive icon, screenshots. Sideload needs none of this.

---

## 8. Placeholders and inputs needed

| Placeholder | Needed by | Default if unanswered |
|---|---|---|
| App name and package id | P0 | Chosen 2026-10-07: **Sharp Ears**, `com.bigpapituna.sharpears` |
| Project path | P0 | Chosen: `C:\dev\piano-app` |
| `[[TEST_PHONE]]` model and Android version | P2 | Samsung Galaxy Z Fold7, Android 16 / One UI 8 (confirmed 2026-10-07; see DESIGN-video-mic-chords.md §2) |
| `[[MIDI_KEYBOARD]]` model; USB "TO HOST" port or Bluetooth MIDI; OTG cable available? | P5 | Roy has a USB-C to MIDI adapter. Optional input only; the mic stays primary. Used for calibration (DESIGN-video-mic-chords.md §2.5) |
| Sample set | P2 | Chosen: the default. VSCO 2 Community Edition (CC0) if it includes a piano, else Salamander Grand Piano (CC-BY 3.0, attribution shown in Settings → About). Verify the license file in P2. |
| v1 song list | P1, P8 | Chosen 2026-10-07. Bundled, all public domain: Für Elise, Ode to Joy, Canon in D, Minuet in G (Petzold), Prelude in C (Bach), Gymnopédie No. 1, Clair de Lune (simplified), Moonlight Sonata mvt 1, Greensleeves, Amazing Grace, Twinkle Twinkle, Happy Birthday. First imported song: Alicia (Clair Obscur: Expedition 33), tutorial video `seSSbMxVDr0`, MIDI from the creator's Patreon, loaded through the import picker, never bundled. |
| Licensing stance for non-PD songs | P8 | Chosen: none bundled; personal imports through the picker, Alicia first |
| `[[FIXTURE_WAVS]]`: Roy records C4, E4, G4, C3, C5 single notes and a C major triad on the real piano with the phone, 2 s each | P6 | synthetic fixtures only (weaker test) |
| `[[YOUTUBE_API_KEY]]` | v1.1 only | not used |
| `[[DISTRIBUTION]]`: sideload or Play Store | P9 | sideload |
| Orientation preference | P3 | player landscape, rest free |
| Visual style | P3, P9 | dark lane, orange right hand, blue left hand, as in the reel |
| Min SDK | P0 | 26 |

---

## 9. Risks and known ceilings

| Risk | Mitigation | Upgrade path |
|---|---|---|
| Mic detection accuracy on real pianos and rooms | Expected-note matching instead of transcription; decoy check; calibration knobs; MIDI as the reliable alternative | Basic Pitch via LiteRT behind the same interface |
| Speaker output feeding back into the mic | Hard rule: mic active ⇒ sampler muted unless headphones | Echo cancellation never needed if the rule holds |
| Emulator cannot test the mic | WAV injector runs the same pipeline | |
| SoundPool latency and 16-voice cap | Acceptable for learning; cap at 16 streams | Oboe |
| Public-domain MIDI quality varies | Curate; fix hand tracks in `index.json` | Hand-arranged simplifications |
| YouTube videos removed or embedding disabled | oEmbed check at curation; "Open in YouTube" always works; `checkedOn` date for re-curation | Runtime search |
| Gradle under OneDrive | Project outside OneDrive | |
| Scope creep toward Artie's lessons and AI tutor | Backlog, explicitly out of scope | Separate plan if ever wanted |

---

## Appendix A — CLAUDE.md starter (copy into the project)

```
# Sharp Ears — Android piano learning app

Read PLAN.md first. Implement one phase per session. Stop when its done-when list passes.

## Commands (PowerShell)
- Unit tests: .\gradlew.bat testDebugUnitTest
- Build + install: .\gradlew.bat installDebug
- Launch: adb shell am start -n com.bigpapituna.sharpears/.MainActivity
- Screenshot: adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png out\screen.png
- Logs: adb logcat -d -s AndroidRuntime:E SharpEars:D
- Jank: adb shell dumpsys gfxinfo com.bigpapituna.sharpears | findstr Janky
- WAV inject (debug): adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es wav <path> --es song <id>
- Lint: .\gradlew.bat lint

## Rules
- Stdlib and platform APIs first. New dependency only if PLAN.md names it.
- engine/ has no Android imports. Test it on the JVM.
- One test per non-trivial logic piece. JUnit only.
- Log every NoteEvent and transport state change with tag SharpEars at debug level. Assert on logs, not pixels, when possible.
- Mark deliberate shortcuts: // ponytail: <ceiling>, <upgrade path>
- Commit once per phase: "P<n>: <summary>". Never commit keys or signing files.

## Key paths
engine/ audio/ ui/ data/ · assets/songs/index.json · scripts/dev.ps1 · out/screen.png
```

## Appendix B — Session prompts for Opus 5.5

First session:

> Read PLAN.md and CLAUDE.md. Implement phase P0 exactly as written. Verify with the commands in CLAUDE.md. When every done-when item passes, commit as "P0: bootstrap" and stop. Do not start P1.

Design-heavy phases (P3, P6):

> Read PLAN.md §5.4–§5.6 and CLAUDE.md. Enter plan mode and propose the design for phase P6 in one page: files, data flow, test list. Wait for approval, then implement, verify, commit as "P6: mic matcher", and stop.

Curation task (P8):

> For every song in app/src/main/assets/songs/index.json with an empty youtube list, follow PLAN.md §6.3: search, apply the criteria, check embeddability with the oEmbed endpoint, and write 1–2 entries with id, title, channel, checkedOn. Show me the table before committing.

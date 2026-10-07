# Design Addendum — YouTube Import, Mic-Only Input on the Galaxy Z Fold7, Evolving Chord Teaching

Companion to `PLAN.md`. Written 2026-10-07. Target device: Samsung Galaxy Z Fold7 (Android 16, One UI 8, 8.0-inch 2184×1968 main display, 6.5-inch cover display). Input: microphone first. Roy's USB-C to MIDI adapter is optional and is used mainly as ground truth for calibrating the mic.

Example tutorial: https://www.youtube.com/watch?v=seSSbMxVDr0 — "Alicia - Clair Obscur: Expedition 33 | Piano Tutorial" by Jon Pumper, 3:06, falling-notes style, no speech, embeddable, MIDI offered on the creator's Patreon.

Snippets are illustrative, Kotlin unless marked, and short on purpose. The implementing agent writes the real code.

---

## 0. Decisions

1. **Media is never downloaded.** Import means metadata, timing anchors, and note data from legitimate sources. Playback stays in the official embedded player. Frame analysis or audio transcription of the video would need the file, and YouTube's terms forbid downloading it; automating the download does not change that.
2. **Two kinds of tutorial video.** Type A, falling-notes (the example): no speech, note data must come from a MIDI file. Type B, spoken lesson: the transcript becomes a lesson pack. Import handles both.
3. **Mic is the required input.** Every flow works with the mic alone. The USB-C MIDI adapter adds precise input and a calibration mode, never a dependency.
4. **Lesson content is data.** Chord teaching lives in JSON lesson packs plus a mastery model, so it grows by adding files, not code.

---

## 1. Automatic YouTube import

### 1.1 What can be automated, and from where

| Need | Source | Automatic? |
|---|---|---|
| Title, channel, thumbnail, embeddability | oEmbed endpoint, no key: `https://www.youtube.com/oembed?url=<watch url>&format=json` | Yes |
| Length, description, links | Watch page read by Claude Code in its browser | Yes (build time) |
| Video type (A or B) | Title keywords plus transcript presence | Yes, heuristic |
| Note data for Type A | Creator's MIDI (this example: Patreon), MuseScore community files, public-domain MIDI, or Roy playing it in through the MIDI adapter | Partly. The file itself is a human download or a recording |
| Steps for Type B | Transcript via the watch page's "Show transcript" panel, converted by Claude Code | Yes (build time) |
| Video-to-song alignment | One or two taps in the app, or audio auto-alignment in v1.1 (§3.3) | Semi, then yes |

### 1.2 Capturing links on the phone: share sheet → inbox

Roy shares a video from the YouTube app to this app. The app stores the id in an inbox. The next Claude Code session drains the inbox. Twenty lines, no network.

```xml
<!-- AndroidManifest.xml, inside MainActivity -->
<intent-filter>
  <action android:name="android.intent.action.SEND" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="text/plain" />
</intent-filter>
```

```kotlin
// MainActivity: onCreate and onNewIntent
val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
val videoId = text?.let { Regex("""(?:v=|youtu\.be/|shorts/)([\w-]{11})""").find(it)?.groupValues?.get(1) }
if (videoId != null) Inbox.add(filesDir, videoId)   // appends {id, sharedOn} to files/inbox.json
```

Claude Code reads it from a debug build with `adb shell run-as com.bigpapituna.sharpears cat files/inbox.json`. The library shows inbox items as "Waiting for import" so nothing is forgotten.

### 1.3 The Claude Code import pipeline (per video)

1. **Metadata.** Call oEmbed. Record title, channel, embeddable flag. Non-embeddable videos keep only an "Open in YouTube" button.
   ```powershell
   Invoke-RestMethod "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=seSSbMxVDr0&format=json" | Select-Object title, author_name
   ```
2. **Page read.** Open the watch page in the browser. Note length and description links. For the example, the description says "MIDI file on Patreon".
3. **Classify.** Type A if the title has "tutorial", "synthesia", "easy piano", or a song name, and the transcript panel is absent or near-empty. Type B if the transcript has sentences. When unsure, ask Roy with one line.
4. **Type A: note data.** In order of preference: the creator's own MIDI (exact match to the video, rate 1.0); a MuseScore or similar transcription Roy downloads for personal use; a public-domain MIDI for classical pieces; Roy records the arrangement himself through the MIDI adapter (§2.5 covers the recorder, it is the same code path); for public-domain tunes only, a simplified arrangement Claude Code writes as MIDI with a short `mido` script. Place the file in `app/src/main/assets/songs/` if its license allows bundling, else in the app's `files/user/` folder via the import picker (P11). Then write the `index.json` entry (§1.4) with empty anchors.
5. **Type B: lesson pack.** Click "Show transcript", read the timestamped text, and write a lesson pack (§4.1): one step per chord, voicing, progression, or hand pattern the teacher demonstrates, with the timestamp where it is shown. Fill midi notes from music knowledge, mark anything inferred, and have Roy spot-check against the video once.
6. **Validate.** A JVM unit test loads every `index.json` entry and lesson pack and fails on unknown fields, bad midi numbers, or steps out of time order.
7. **Ship.** Commit, rebuild, install. Optional: an "Import pack" file picker in Settings accepts a JSON or MIDI file so content can be added without a rebuild.

### 1.4 `index.json` additions

```json
{
  "id": "alicia-expedition33",
  "title": "Alicia (Clair Obscur: Expedition 33)",
  "composer": "Lorien Testard",
  "file": "user/alicia.mid",
  "source": "creator MIDI (Jon Pumper, Patreon)",
  "license": "personal use, not bundled",
  "handTracks": { "right": 0, "left": 1 },
  "videos": [
    {
      "id": "seSSbMxVDr0", "kind": "falling-notes", "channel": "Jon Pumper",
      "lengthSec": 186, "checkedOn": "2026-10-07",
      "anchors": [ { "songMs": 0, "videoSec": null } ]
    }
  ]
}
```

`anchors` stay null until Roy aligns the video in the app (§3.1). One anchor is enough when the MIDI is the creator's own, because the video runs at the MIDI's tempo. Two anchors cover a MIDI from another source.

### 1.5 What stays manual, honestly

Downloading a creator's MIDI from Patreon or MuseScore; the one or two alignment taps; a thirty-second spot-check of a Type B pack. Everything else is a Claude Code session or a share-sheet tap.

---

## 2. Microphone-only on the Galaxy Z Fold7

### 2.1 Capture configuration

Samsung's voice-processing paths (automatic gain, noise suppression) smear piano transients. Use the unprocessed source when the device reports it, else the voice-recognition source, which also bypasses those effects. Open at 48 kHz, the native rate on Samsung, and pick the built-in mic explicitly. The Fold has several mics; expose the choice in Calibration instead of guessing.

```kotlin
fun openMic(am: AudioManager, preferredAddress: String?): AudioRecord {
    val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED
                 else MediaRecorder.AudioSource.VOICE_RECOGNITION
    val rate = 48_000
    val fmt = AudioFormat.Builder().setSampleRate(rate)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
    val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = AudioRecord.Builder().setAudioSource(source).setAudioFormat(fmt)
        .setBufferSizeInBytes(maxOf(minBuf, 4096 * 2 * 2)).build()
    am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        .filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        .firstOrNull { preferredAddress == null || (Build.VERSION.SDK_INT >= 33 && it.address == preferredAddress) }
        ?.let { rec.preferredDevice = it }          // addresses look like "bottom", "top", "back"
    return rec
}
// ponytail: fixed 48 kHz; fall back to 44.1 kHz only if the builder throws
```

Matcher parameters from PLAN.md §5.4 scale with the rate: a 4096-sample window is 85 ms at 48 kHz, the hop 10.7 ms. Nothing else changes.

### 2.2 Fold postures and layout

The natural way to use this app on a Fold is **tabletop**: the phone half-open on the music stand, bottom half flat, top half upright. The hinge line splits the screen, so the lane and the video go on top, the keyboard and controls go on the bottom. Jetpack WindowManager reports the posture and the hinge bounds. This is the one new AndroidX dependency this addendum adds (`androidx.window:window`).

```kotlin
lifecycleScope.launch {
    WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        val tabletop = fold != null && fold.state == FoldingFeature.State.HALF_OPENED &&
                       fold.orientation == FoldingFeature.Orientation.HORIZONTAL
        posture.value = if (tabletop) Posture.TABLETOP else Posture.FLAT
        hingeTopPx.value = fold?.bounds?.top          // split line for the tabletop layout
    }
}
```

| Posture | Layout |
|---|---|
| Tabletop (half-open, hinge horizontal) | Top pane: video (when a video is attached) beside the note lane. Bottom pane: keyboard full width, transport bar above it. Split at the hinge. |
| Open flat (8-inch, near-square) | Video left, lane right, keyboard across the bottom. |
| Cover screen (6.5-inch, tall) | Landscape player as in PLAN.md; video as a thumbnail that expands on tap. |

Testing without the device: the Android emulator ships foldable profiles ("7.6 Fold-in with outer display"); `adb emu fold` and `adb emu unfold` switch screens, and the hinge angle is set under Extended controls → Virtual sensors. The agent can screenshot all three layouts.

### 2.3 Speaker and mic in the same room

The embedded video plays through the Fold's speakers. While it plays, the mic would hear it. Rules:

- Listening windows open only while the video is paused, unless headphones are connected.
- With headphones (wired, USB-C, or Bluetooth) the video may keep playing while the mic listens. Bluetooth adds 150–250 ms of output latency, so timing always references the player's reported position, never the audio.

```kotlin
fun headphonesOn(am: AudioManager) = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
    it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
    (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
}
```

### 2.4 Android 16 notes

- `RECORD_AUDIO` is a runtime permission; request it on first entry to Calibration or Wait mode, with a one-line explanation. The system shows the green mic indicator while listening; that is expected.
- The mic runs only while the player screen is in the foreground. No foreground service, no background capture.
- Target SDK 36. No NDK, so the 16 KB page-size requirement does not apply.
- One UI's "Flex mode panel" may offer to move controls to the bottom half; the app's own tabletop layout already does that, so opt out by handling the posture itself.

### 2.5 The USB-C MIDI adapter as ground truth

PLAN.md P5 already handles MIDI input through `android.media.midi`. With the adapter plugged into the Fold's USB-C port and the piano's MIDI out, the adapter becomes a measuring instrument:

- **Calibration session.** Roy plays a slow scale and a few chords. The app records MIDI note-ons (truth) and mic detections side by side, then reports hit rate, false positives, and the detection-latency median. It suggests `onsetThresholdDb` and `scoreThreshold` by trying a small grid of values against the recorded buffers offline, which takes under a second.
- **Fixture capture.** The same session saves the mic audio and the MIDI events as test fixtures, so the matcher's unit tests use real Fold-7 recordings instead of synthetic tones.
- **Record your own arrangement.** The MIDI stream saved as a `.mid` becomes a library song. This is how Roy can add a song he can already play, or build a simplified version of a copyrighted piece for his own use.

```kotlin
// Calibration scoring: a MIDI note-on is "hit" if the mic reported the same pitch within 250 ms after it
val hits = midi.count { m -> mic.any { it.midi == m.midi && it.t - m.t in 0..250 } }
val falsePositives = mic.count { d -> midi.none { it.midi == d.midi && d.t - it.t in 0..250 } }
val latency = midi.mapNotNull { m -> mic.firstOrNull { it.midi == m.midi && it.t - m.t in 0..250 }?.let { it.t - m.t } }.sorted()
```

None of this is required to use the app. It makes the mic path trustworthy faster.

---

## 3. Syncing the mic-driven engine with the video

### 3.1 Type A (falling-notes video): the engine is the clock, the video follows

The song engine from PLAN.md §5.5 already pauses in Wait mode until the right note is heard. The video is a follower of transport state:

- Map song time to video time with a linear function through the anchors: `videoSec = a.videoSec + (songMs − a.songMs) / 1000 × rate`, where `rate` is 1.0 with one anchor or derived from two.
- When the transport plays, seek the player to the mapped time if it drifted more than 0.3 s, then play. When the transport waits for a note or is paused, pause the player.
- Scrubbing the lane seeks the video. Loops seek it back.

```kotlin
class VideoFollower(private val player: YouTubePlayer, private val tracker: YouTubePlayerTracker,
                    private val anchors: List<Anchor>) {
    private val a = anchors.first()
    private val rate = anchors.getOrNull(1)?.let { b -> (b.videoSec - a.videoSec) / ((b.songMs - a.songMs) / 1000.0) } ?: 1.0
    fun videoSecFor(songMs: Long) = a.videoSec + (songMs - a.songMs) / 1000.0 * rate
    fun onTransport(s: TransportState) {
        if (s.waiting || !s.playing) { player.pause(); return }
        val want = videoSecFor(s.positionMs).toFloat()
        if (abs(tracker.currentSecond - want) > 0.3f) player.seekTo(want)
        player.play()
    }
}
// ponytail: linear map; a tutorial with tempo changes needs per-section anchors
```

**Aligning in the app.** "Align" button → the video plays from the start → Roy taps "That's the first note" when the first note sounds → the app records `(firstGroup.startMs, tracker.currentSecond)`. A second tap at the last note is optional. Ten seconds of work per video.

**Pausing a YouTube video repeatedly is normal player use.** The IFrame API exposes play, pause, and seek for exactly this. Keep the embedded player visible at or above 200×200 and leave its branding alone.

### 3.2 Type B (spoken lesson): the video leads, the mic verifies

Each lesson step has a timestamp `t`. The runner plays the video from one step to the next, pauses it, shows the target chord on the keyboard, opens a listening window, records the result, and continues.

```kotlin
suspend fun runLesson(pack: LessonPack, video: VideoControl, matcher: NoteMatcher, mastery: MasteryStore) {
    pack.steps.forEachIndexed { i, step ->
        val end = pack.steps.getOrNull(i + 1)?.t ?: pack.lengthSec
        video.playUntil(step.t, end)                       // seek, play, suspend until the tracker passes `end`
        video.pause()                                      // speakers silent, the mic may listen
        step.expect?.let { target ->
            keyboard.highlight(target)
            val result = matcher.listenFor(target, timeoutMs = step.timeoutMs)   // which expected notes were heard
            mastery.record(step.itemId, result.allHeard)
            feedback.show(result)                          // "Heard C and G, missing the E"
        }
    }
}
```

The teacher's own playing is in the video segment that just ended, so by the time the mic opens, the room is quiet. With headphones on, the runner can leave the video's audio running during the window.

### 3.3 Automatic alignment (v1.1)

Replace the alignment tap with Android's `AudioPlaybackCapture` (API 29+), which records this app's own audio output, including the embedded player, after a one-time system consent dialog. Run the onset detector on that capture, cross-correlate the onset sequence with the MIDI's onset times, and take the lag with the highest correlation as the offset. Ceiling: needs the app's own playback (fine, the embed is ours), and tempo-changing videos still want section anchors. Build only if the tap turns out to be annoying.

---

## 4. The evolving chord-teaching system

### 4.1 Content model: lesson packs

A pack is a JSON file in `assets/lessons/` (or imported). Items are the atoms the mastery model tracks. Any item may point at a video moment.

```json
{
  "id": "neo-soul-basics-01",
  "title": "Neo-soul chords, part 1",
  "level": 5,
  "requires": ["sevenths-01"],
  "video": { "id": "<videoId>", "channel": "<channel>", "lengthSec": 540, "checkedOn": "2026-10-07" },
  "items": [
    { "id": "Cmaj9-rootless", "type": "chord", "name": "Cmaj9 (rootless)", "pitchClasses": [4, 7, 11, 2],
      "example": [64, 67, 71, 74], "hand": "right", "t": 72.0, "tip": "Leave the root to the left hand." },
    { "id": "ii-V-I-C", "type": "progression", "chords": ["Dm9", "G13", "Cmaj9"], "key": "C", "bpm": 70, "t": 140.0 },
    { "id": "lh-root-fifth-octave", "type": "pattern", "notes": [[48, 0], [55, 500], [60, 1000]], "loops": 4, "t": 200.0 }
  ]
}
```

Item types and how the mic verifies them:

| Type | What the learner does | Verification |
|---|---|---|
| chord | Plays the chord, any octave, rolled or together | All expected pitch classes heard within 400 ms; per-note feedback on misses |
| progression | Plays each chord in turn at a slow metronome | The chords module's progression player: a generated mini-song in Wait mode |
| pattern | Plays a left-hand figure for N loops | A generated mini-song in Wait mode, exact notes |
| listen | Watches a video segment | No verification, marks the item seen |

### 4.2 Mastery model

Leitner boxes per item, due dates by box, a daily queue, and unlocks by prerequisite. Forty lines, pure Kotlin, unit-tested.

```kotlin
data class Mastery(val box: Int = 0, val dueAt: Long = 0)            // boxes 0..5
fun Mastery.after(correct: Boolean, now: Long): Mastery {
    val b = if (correct) minOf(box + 1, 5) else maxOf(box - 1, 0)
    val days = listOf(0, 1, 2, 4, 7, 14)[b]
    return Mastery(b, now + days * 86_400_000L)
}
fun dueItems(all: Map<String, Mastery>, now: Long) = all.filterValues { it.dueAt <= now }.keys
// a pack unlocks when every pack it requires averages box >= 3
```

The Chords home screen shows "Due today", "New", and "Mastered" counts and a Practice button that walks the due queue with the mic. Progress persists in `SharedPreferences` keyed by item id.

### 4.3 Three content sources that grow on their own schedule

1. **Authored packs.** Claude Code writes packs from a one-line request ("a 12-bar blues pack in C and G"). Music knowledge is in the model; the schema keeps it honest; the validator test keeps it loadable.
2. **Song-derived packs.** Every song in the library gets an automatic "Chords of <song>" pack: a chord labeler runs over each bar of the MIDI and emits the distinct chords in order of appearance. Adding a song adds its chord pack with zero authoring. This is the hook that keeps chord teaching in step with future song tutorials.
3. **Video-derived packs.** Type B imports (§1.3 step 5), with items linked to the teacher's timestamps.

```kotlin
val templates = mapOf("" to setOf(0, 4, 7), "m" to setOf(0, 3, 7), "7" to setOf(0, 4, 7, 10),
                      "maj7" to setOf(0, 4, 7, 11), "m7" to setOf(0, 3, 7, 10), "dim" to setOf(0, 3, 6))
fun labelBar(notes: List<Note>): String {
    val w = IntArray(12); notes.forEach { w[it.midi % 12] += it.durationMs.toInt() }
    return (0..11).flatMap { r -> templates.map { (suffix, iv) -> Triple(NAMES[r] + suffix, r, iv) } }
        .maxBy { (_, r, iv) -> iv.sumOf { w[(r + it) % 12] } - (0..11).filter { (it - r + 12) % 12 !in iv }.sumOf { w[it] } / 2 }
        .first
}
// ponytail: duration-weighted template match per bar, no key context; upgrade: smooth across bars
```

### 4.4 Curriculum ladder (data, not code)

| Level | Pack theme | Source |
|---|---|---|
| 1 | Major and minor triads, root position, both hands | authored |
| 2 | Inversions and smooth voice leading | authored |
| 3 | Seventh chords: maj7, m7, dom7 | authored |
| 4 | Progressions in five keys: I–V–vi–IV, ii–V–I, 12-bar blues | authored |
| 5 | Style voicings and left-hand patterns from video lessons | video-derived |
| 6 | Chords of the songs in your library | song-derived |

Levels are a suggestion encoded in `requires`. New packs slot in anywhere.

### 4.5 How it evolves alongside song tutorials

- A new song (bundled, imported, or recorded through the adapter) immediately yields its chord pack, and the song's player gets a "Practice the chords first" button that runs it.
- A pack item can reference a song and bar range (`"applyIn": {"songId": "alicia-expedition33", "bars": [1, 8]}`), so a voicing learned from a video is practiced inside a real piece.
- Mastery data tells the library which songs are "chord-ready": every chord in the song's pack is at box 3 or higher.

---

## 5. Opus 5.5 effort recommendation

Effort controls how deeply the model reasons per step and how thoroughly it verifies before moving on. The API default is medium, so set it explicitly per session in the Claude Code desktop app. Giving the full spec up front (these two documents) does more than any effort setting; raise effort where failure modes are silent.

| Work | Effort | Why |
|---|---|---|
| P0 bootstrap, scripts, sample conversion, share-sheet inbox | medium | Mechanical, fully specified, cheap to retry |
| SMF parser, keyboard and sampler, chord table, lesson pack loader, mastery model | medium | Well-defined with unit tests that catch mistakes immediately |
| Note lane rendering, wait-mode transport, Type A video follower, Type B runner | high | Timing and state machines; bugs are silent until a human plays; worth the extra reasoning per step |
| Mic matcher on the Fold 7, calibration against MIDI, fold-posture layouts | xhigh | DSP choices, threading, device quirks, and the hardest acceptance test in the project |
| Lesson-pack authoring, transcript conversion, YouTube curation | medium | Content work constrained by a schema and a validator |
| A stubborn mic false-positive or sync bug after an xhigh attempt | max, one session | Only when the level below has failed with the evidence in hand |

Cost shape: higher effort mostly means more output tokens per task, roughly two to three times between medium and xhigh. It is cheaper than a second session to undo a subtle audio bug. Keep plan mode on for the xhigh phases so the design is approved before tokens go into code.

---

## 6. Implementation steps (added to PLAN.md's phases)

| Step | Builds on | Done when |
|---|---|---|
| S1 Fold-7 capture config and mic picker | P6 | Calibration screen lists the built-in mics by address; the chosen one persists; unprocessed source is used when reported, logged at start |
| S2 Fold postures | P3 | Screenshots of tabletop, flat, and cover layouts from the foldable emulator profile; keyboard sits below the hinge in tabletop |
| S3 Share-sheet inbox | P0 | Sharing a YouTube link from the emulator's browser adds an entry; `run-as` shows it; the library lists it as waiting |
| S4 Calibration against MIDI | P5, P6 | A session with the adapter produces hit rate, false positives, latency median, and suggested thresholds; fixtures are saved |
| S5 Type A video follower and alignment tap | P8 | With the example video and its MIDI, Wait mode pauses and resumes the video within 0.3 s of the mapped time; scrub seeks it |
| S6 Lesson packs, mastery, Chords home | P7 | Level 1–4 packs load and validate; due queue works across simulated days in unit tests; practice runs with the WAV injector |
| S7 Type B runner | S5, S6 | One imported spoken lesson plays segment by segment and verifies chords at pauses on the emulator with injected audio |
| S8 Song-derived chord packs | P1, S6 | Every library song has a chord pack; labeler unit tests on a known progression |
| S9 Auto-alignment (v1.1) | S5 | Offset found within 100 ms of the tapped anchor on three videos |

---

## 7. Open items

- Roy obtains the example's MIDI from the creator's Patreon and places it in the app's user folder, or records his own through the adapter.
- Which headphones Roy uses at the piano, if any. It decides whether the video can keep playing during listening windows.
- App name and package id: chosen, **Sharp Ears**, `com.bigpapituna.sharpears`.
- First authored packs: confirm the level 1–4 themes in §4.4 or replace them.

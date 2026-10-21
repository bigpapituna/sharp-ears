# Robustness audit (branch audit-arobust, 2026-10-20)

Crashes, ANRs, jank, memory, threads, data, APK, security and static checks on the 2.3.0 debug build. Emulator
Pixel7_API37 (emulator-5556, swiftshader, debug build) unless a row says otherwise; never Roy's phone. Emulator frame
times are relative only: measure the Fold with the profile build before a release.

Severity: **Critical** loses data or crashes on a common path; **High** breaks a core use (a lesson, the mic, the
voice) or leaks private data; **Medium** a crash or leak on an uncommon path, or visible jank; **Low** cost without a
visible symptom, or debug-only.

## Findings

| # | Where | What | Sev | Repro | Fix | Test |
|---|---|---|---|---|---|---|
| 1 | AndroidManifest, MainActivity.onNewIntent | The launcher icon after a non-launcher start, the reminder's notification and `am start` each stacked another MainActivity (monkey: 15 in one task, 17 alive), each with its own mic, voice, MIDI and sampler | High | monkey seed 42, `dumpsys activity activities` | `launchMode="singleTop"`; a debug route in the new intent opens as a fresh launch (af80549) | emulator: one activity after monkey |
| 2 | ui/playground/PlaygroundKit.kt rememberStageEar, rememberListening | Every Playground stage kept the mic capturing after Home (the debug session WAV kept growing, 30% CPU) | High | open a stage, press Home, `top` | stop on `onScreen()` false like the other mic screens (b16769f) | emulator |
| 3 | MainActivity KeepScreenOnWhile | Nothing set keepScreenOn: a phone's 30 s timeout switched the screen off under a child playing, which stopped the mic and the lesson | High | lesson step, hands off for 30 s | screen kept on while a lesson, song, game or the mic is up (5f69ca0) | emulator |
| 4 | data/Reporter.kt catchCrashes | A crash handler chained on every onCreate, each holding a Reporter with the activity as context: every destroyed activity stayed reachable, a crash was saved once per handler | Medium | rotate or recreate n times, crash: n reports | once per process, app context, one daemon reports thread (734b8a8) | emulator |
| 5 | audio/Voice.kt | With no working TextToSpeech engine the waiting lines spoke forever: holdsMic never cleared, lesson steps never started, mic steps never listened | High | natural voice off, Google TTS disabled, fingers-right step 2 stays on Play | drop the lines when the engine fails to start (a38d222) | emulator |
| 6 | audio/MicInput.kt capture loop | A loop that ended without stop() (startRecording refused, read error, missing injection file) left its thread set: every later start() returned true without listening | High | inject a missing file, then any mic screen | the loop clears itself; a refused AudioRecord is released (0afd759) | emulator |
| 7 | audio/MicInput.kt recordings, saveStall | The debug WAV writer, the .notes file and saveStall threw on a full or missing disk: a crash from a screen's dispose or mid-lesson; a failed append killed the mic thread | Medium | fixtures and stalls replaced by files | log and drop the recording only (fe9d686) | emulator |
| 8 | audio/NaturalVoice.kt release | release() freed the Kokoro model only once its lazy load had finished: a profile switch or recreate during warmUp leaked the native model; a line after release threw RejectedExecutionException | Medium | switch profile right after launch | free a loading model at release, drop lines after release (4c79808) | emulator |
| 9 | audio/NaturalVoice.kt constructor | Listed assets/voice (3173 files) on the main thread before the first frame, about 130 ms of startup CPU | Medium | method trace of a cold start | list on the voice worker (2352fe7) | trace |
| 10 | AndroidManifest backup | Auto Backup on with no rules: the GitHub token (reports.xml), debug recordings and stuck-moment clips could go to the Google account and a new phone; the Kokoro model alone exceeds the 25 MB quota, which stops the whole backup | High | `bmgr backupnow` | backup and data extraction rules for Android 9 to 11 and 12+; privacy page updated (0234141) | `bmgr` on the emulator |
| 11 | ui/PlayerScreen.kt | The playhead was read in PlayerScreen's own scope: the 1300-line player recomposed at frame rate. Twinkle Listen: UI thread per frame p50 18.7 -> 4.3 ms, p90 66.7 -> 12.3 ms, frames in 10 s 171 -> 411 | High | method trace, Listen | lane dump only with `--ez dump`; the bar reads the playhead to 100 ms in its own scope (cdd5923) | trace |
| 12 | ui/playground/PlaygroundKit.kt piano | Every key built its own gradient Brush (and native shader) each frame | Low | trace a stage | one brush per key colour per frame (25979e8) | trace |
| 13 | ui/practice/PracticeKit.kt, ui/ear/WorkoutRun.kt | Lint UseOfNonLambdaOffsetOverload: animated offsets read in composition, recomposing per frame | Low | lint | lambda `offset {}` (540e54f) | lint |
| 14 | MainActivity.openApp | Debug routes naming an unknown lesson or song threw (NoSuchElementException, IndexOutOfBounds); with singleTop that crashed the running app | Low | `--es lesson nope` | log and open home (cf8f79e) | emulator, 12 bad routes |
| 15 | MainActivity screen state | The open screen lived in a plain `remember`: any recreate (a process the system stopped in the background, Don't keep activities, a dark mode, locale or font size change) reopened at home, Songs tab: a lesson lost its step, a song its screen, a kid's game its place | High | Learn > Find C anywhere > step 3, Home, `am kill`, relaunch: Songs tab | the screen is saved as a route in the instance state ("home:LEARN/lesson:2:find-c") and reopened, back stack included; songs reopen in wait mode at their saved position; a kid's gated screens reopen as their back screen (a recreate closes the gate) | emulator: lesson step 3, Twinkle at 0:09, kid in Settings -> kid home |
| 16 | audio/MicInput.kt saveStall, the 250 MB cap | The cap deleted "oldest first by name", but names start with their kind (answer-, crash-, flag-, stall-, summary-): every answer-key clip and every crash report, sent or not, went before the oldest stuck moment; a sent marker deleted alone sent its report again | Medium | stalls folder at the budget, a crash report saved, then a stuck moment: the unsent crash report is deleted | `StallWatch.overBudget`: whole reports (clip, report, marker), oldest written first, never the newest | StallWatchTest.overBudgetDeletesWholeReportsOldestWrittenFirst |
| 17 | audio/MicInput.kt autoRecord, stalls() | Debug sessions are named session-HHmmss (no date) and the last four were kept by name: after midnight the newest session was the first deleted while last night's stayed; Microphone setup's "Newest" stuck moment was the last by name (any stall- after every flag-) | Low | four sessions in the evening, one the next morning, a sixth session: the morning one is gone | order by modification time | emulator (debug-only files) |
| 18 | data/SongRepository.kt import | An imported song's id kept a-z and 0-9 only: every file named in Arabic, Cyrillic, Devanagari, Chinese or Japanese became "user-song", so each such import overwrote the last one's file and index entry and took over its saved position, best score and trouble bars | High | import سلام.mid, then مرحبا.mid: one song left, with the first one's progress | `userSongId` keeps letters, marks and digits of every script | LibraryContentTest.importedSongsInAnyScriptGetTheirOwnIds |
| 19 | MainActivity (Practice today chords), ui/theory/TheoryScreen.kt, ui/ear/EarTrainingScreen.kt, ui/ear/WorkoutRun.kt | Screens that time themselves for practice minutes used the wall clock or elapsedRealtime from open to finish, background included: chords started at 23:50, the phone locked, the part left at 07:00 credited 7 h 10 min to the new day (goal met, XP, a streak day); a wall clock set back gave negative seconds | Medium | Practice today chords, Home for an hour, back, End: an hour credited | `ForegroundClock`, a monotonic clock that stands still between onStop and onStart | ForegroundClockTest.timeOffScreenDoesNotCount |
| 20 | ui/Lifecycle.kt onScreen, MainActivity and HomeShell transitions, the mic effects of PlayerScreen, MicSetupScreen, ChordGamesScreen, ChordsScreen, ear/KeysPlay | Screens overlap while AnimatedContent runs (the player's curtain 720 ms, home tabs 260 ms). The next screen's mic.start() found the old thread and did nothing; then the old screen's onDispose stopped the mic (and cleared its expected notes). Microphone setup's "Try it: C major scale" opened with the mic icon on and heard nothing for the whole run; Back left setup deaf; a chord game to the tuner the same | High | Settings > Microphone setup > Try it: log "mic: stopped" after "player open", no "mic: start" | a leaving screen is off screen for `onScreen()` (`LocalLeaving`), so it lets go of the mic before the next one starts; each effect stops only the mic it started | emulator: setup -> mic check -> setup, start and stop in order, each listening |
| 21 | audio/MicInput.kt stop | stop() posts the clearing of the mic's name; with a start in the same turn (now every mic-to-mic screen change) the post cleared the new mic's name: setup read "Starting" and reports said mic=null while it ran. Same change as audit-aadult ac92fdb, so the branches agree | Low | setup -> mic check -> Back: "Starting" under the mic chips | clear only when no thread runs | emulator: "bottom mic, voice recognition" after Back |
| 22 | data/SongRepository.kt imported, writeUserIndex | files/user/index.json was rewritten in place, and read with no guard on every launch (openApp merges records over repo.all()): an import cut short by a full disk or a killed process left a truncated index, and the app crashed in onCreate on every launch until Clear storage wiped all progress | High | a truncated index.json: FATAL JSONException in MainActivity.onCreate, every launch | written beside and renamed over (whole or not at all); an unreadable index reads as no imports, logged | emulator: truncated index -> app opens, "index unreadable, ignored"; then importing Привет.mid rewrote it (user-привет) |
| 23 | data/Reporter.kt catchCrashes | A crash report carries the app's last 200 log lines, which hold spoken lines and share file names with the player's name ("voice: Well done, Mia!", "Mia plays ...png"), and goes to GitHub with sending on; reports promise "profile=kid or adult, never the name" | Medium | a kid's lesson, then a crash: the report's recent log names the kid | every name on the roster (whole words, any case, any script) becomes "<name>" in the crash report; read at crash time from the app context | StallWatchTest.crashLogsLoseThePlayersNames; emulator `am crash` report written |
| 24 | ui/AboutScreens.kt PrivacyScreen | On the private build the page opened with "It does not send anything about you or your playing anywhere" and "uses the internet only to show tutorial videos", above its own paragraph on review reports going to GitHub; the mic list left out the Playground games, the Jam, Pup says, Clap along and Microphone setup; "delete the clips" did not say sent copies stay in the repository | Low | Settings > Privacy | the opening and Internet lines name the review reports on builds that can send them; the mic list is complete; sent copies stay | emulator screenshot (arobust-privacy.png) |
| 25 | ui/SettingsScreen.kt, ui/onboarding/OnboardingScreen.kt reminder label | The reminder time was formatted with java.time's localized SHORT style, which follows the locale and ignores the phone's 12 or 24-hour switch the picker honours: en-US with 24-hour on showed "6:30 PM" beside an 18:30 picker; de-DE with 12-hour showed 18:30 | Low | `settings put system time_12_24 24`, Settings > Reminder | android.text.format.DateFormat.getTimeFormat, as the phone shows times | emulator: 24 h "18:30", 12 h "6:30 PM" |
| 26 | AndroidManifest ReminderReceiver, reminder/Reminder.kt | The reminder alarm is an instant set from the local time; only BOOT_COMPLETED rescheduled it, so after a time-zone change (travel) or a clock change it rang once at the old hour (18:30 Berlin rang at 12:30 in New York) | Low | `cmd alarm set-timezone`: no reschedule | TIME_SET and TIMEZONE_CHANGED (both allowed for manifest receivers) reschedule without posting | emulator: "reminder:" logged on each time-zone change with the app in the background |
| 27 | ui/LessonScreen.kt finish time | The lesson's "took" time on the celebration was wall-clock from open to finish: background time counted, and a clock set back (network time correction) gave a negative time ("-1:-05") into the count-up | Low | open a lesson, set the clock back a minute, finish | ForegroundClock, as the practice timers | ForegroundClockTest (the clock); compile |
| 28 | app/build.gradle.kts abiFilters | Every APK carried lib/x86_64 (26 MB of sherpa-onnx) for the emulators, on top of arm64 for the Fold: 147 MB per install over Wi-Fi adb | Low | `unzip -v app-debug.apk` | arm64-v8a only; the x86_64 emulators run it through their ARM translation (checked: primaryCpuAbi arm64-v8a, the player opens and plays). Notes in CLAUDE.md, release path in docs/publishing.md | emulator-5556 install and run; APK 147.4 -> 116.1 MB |
| 29 | engine/Course.kt withFingers (startup) | Before the first frame, openApp builds COURSE (CoursePath), which builds every Playground page and works out fingering (a DP over hand positions) 64 times for 17 distinct exercises: the biggest item in cold start, 68% of onCreate's traced CPU | Medium | `am start -W --start-profiler` with sampling, CPU time per method | the fingered exercise is kept per exercise (bounded memo in an object, safe for any static-init order) | FingeringTest.anExerciseIsFingeredOnceAndAlike; JVM cold COURSE 872 -> 208 ms; traced onCreate CPU 1567 -> 879 ms, withFingers 576 -> 125 ms |
| 30 | audio/Clicks.kt | "Synthesized once into the cache", but every Clicks (each lesson opened, each song's metronome) wrote both click WAVs again on the main thread: StrictMode's largest app-side disk write in a tour (18 writes, 20 reads) | Low | StrictMode (debug, penaltyLog) during a screen tour | written once, under a name carrying the sound's hash, rewritten only if not whole | emulator: Perform count-in clicks, click-accent-5966416d.wav 3404 bytes reused |
| 31 | MainActivity.onCreate, onSaveInstanceState | A recreate skipped "Who's playing today?" whenever there was saved state: the picker left in the background (Don't keep activities, or the process stopped by the system) came back as the app opened for the last player, so a kid picking the phone up landed in the grown-up's progress | Medium | 2 profiles, picker, Home with Don't keep activities on, relaunch: Roy's home | the instance state says the picker was still asking; it asks again (8edc5b5) | emulator: picker restored; Mia picked opens as p1 |
| 32 | ui/TransportBar.kt | At font size 2.0 the two fixed-height rows overflowed: the play button and the More menu went off the right edge (a sliver of Play left) and the song slider shrank to nothing | Medium | `settings put system font_scale 2.0`, any song | the bar's text stops growing at 1.15x (where the slider keeps its room); the rest of the player follows the phone (18337eb) | emulator: Play, flag, More visible at 2.0; Play starts (arobust-fs2-player-after.png) |
| 33 | ui/PlayerScreen.kt frame loop | The song is stepped by the time between frames, and frames stop off screen: Home or a locked screen mid-song, and the first frame back played on by the whole time away (40 s away: 0:06 jumped to 0:46, the notes between never sounded, a Perform run would score them missed) and credited it as practice (+46 s for 6 s played). The practice-minutes class of #19 in the player | Medium | Für Elise Listen, Play, Home 40 s, back | the step leaves out the time away (`ForegroundClock.awayMs`) (c6c3712) | ForegroundClockTest.awayGrowsOnlyOffScreen; emulator: resumes where it was, practice +15 s after 15 s on screen |
| 34 | ui/RunReplay.kt | Hear my run steps the same way: back from Home it started every note of the time away at once | Low | Play my run, Home 20 s, back | as #33 (0a63ff4) | emulator: 2 s played, Home 20 s, the replay ended 5 s after the return, not at once |
| 35 | audio/MicInput.kt saveStall | `clip_starts_s=` (a key=value line for the review) followed the locale: "2,00" on a German phone | Low | de-DE, a stuck moment | Locale.ROOT for that line only (1015d99) | compile |
| 36 | res/drawable/ic_profile_person.xml | Lint UnusedResources | Low | lint | deleted (f2214fe) | lint |
| 37 | debug build manifest | The debug build (the one on the phone) merged ui-tooling's PreviewActivity (runs the composable its intent names) and ui-test-manifest's ComponentActivity in exported: any app on the phone could start them in this app's process, which holds the mic permission | Low | merged debug manifest: both `exported="true"` | app/src/debug/AndroidManifest.xml sets both exported=false; androidTest still reaches them (cfd4bd4) | emulator: Permission Denial for both; androidTest assembles |
| 38 | MainActivity debug routes | On the debug build any installed app can send MainActivity's debug extras: `--es kidseed reset` forgets a kid's Playground, `--ez sleepy` adds practice minutes, `--es profile` switches player, `--es screen settings` opens a kid's Settings without the gate. The report token is still behind the gate (Mic setup's Set up and Remove always call `gate.guard`) | Low | an app sending an explicit intent with the extras | not fixed: telling adb from another app needs the caller's identity, which Android hands out only on opt-in; the Play build is a release build without routes (docs/publishing.md). Keep untrusted apps off the phone that runs the debug build | review |
| 39 | audio/MicInput.kt debug session WAV | A process killed mid-recording (RECORD_AUDIO revoked: Android kills the app; or a crash) leaves session-*.wav with a zeroed header: the PCM is intact, players and `Wav.read` refuse it | Low | Jam recording, `pm revoke ... RECORD_AUDIO` | not fixed (debug recordings only; the header is written on close) | emulator: 44 zero bytes, then PCM |
| 40 | ui/play/JamScreen.kt header | At font size 2.0 the "Play any key" hint squeezes the mic label to one letter per line ("Li st en in g") for the hint's ten seconds | Low | font 2.0, the Jam | not fixed (layout call for the designer; nothing hidden) | arobust-fs2-esscreenjam.png |
| 41 | MainActivity.onNewIntent | A debug route with `--es profile` opens the app twice (the profile flow's collector and the route each call openApp: two Voice engines, the first released at once) | Low | `am start ... --es profile p1` while open | not fixed (debug only, no leak: the first is released) | log: "open as p1" twice |

## Close-out (third agent, 2026-10-21)

Rows 1 to 30 are the first two agents', 31 to 41 the third's. Every fix is its own commit; `git log b061572..HEAD` lists
39 with this log. Emulator-5556 (Pixel7_API37, debug build, swiftshader) only; never Roy's phone.

### Counts by severity, all three agents

| Severity | Found | Fixed | Open |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 11 | 11 | 0 |
| Medium | 11 | 11 | 0 |
| Low | 19 | 15 | 4 (#38 to #41) |
| All | 41 | 37 | 4 |

### Crash hunt

- Monkey, 3000 events at 150 ms: seeds 42, 7 and 99 (mostly as Mia, the kid: kid home, stickers, songs, gate, a lesson,
  Twinkle) and an extra seed 2024 as Roy (games, chords, sight reading, a lesson): no crash, no ANR, crash buffer empty,
  one MainActivity after each. App-process errors were platform noise only (codec queries for the Vorbis samples).
- Don't keep activities (switched on in Developer options; `settings put` alone does not reach the activity manager):
  a lesson at step 3, the player, the Jam (the mic stopped and started once) and the picker (#31, fixed) all come back.
- Profile switch in a lesson's mic step: the mic not left running, the voice engine released, kid home opens.
- RECORD_AUDIO revoked while the Jam records: Android kills the process (by design); the relaunch asks again; "Don't
  allow" shows "The microphone is off ... The keys here still work". The debug WAV is left headerless (#39).
- Airplane mode with a pending crash report and a fake test token: nothing sent, no attempt, no crash; the fake token
  never reached logcat or a report and was removed before the network came back.
- de-DE (per-app locale): 13 screens, no crash (#35 fixed). Font scale 2.0: 11 screens, no crash (#32 fixed, #40 open).
- `am send-trim-memory` RUNNING_MODERATE, LOW, CRITICAL during Für Elise, then BACKGROUND and COMPLETE at Home: no crash,
  the song resumes. That walk found #33.

### Jank and startup (emulator, debug build; relative only)

Another agent's Fold emulator kept the host at 93 to 99% CPU for most of this session, so frame times swing 3x between
runs. The first pass, before that load, is the clean one; the later three passes give the range. gfxinfo over 10 s
after 14 s to settle; framestats split each frame into UI thread (vsync to sync) and render thread plus GPU.

| Screen | Clean pass: frames, p50 / p90 / p99 | Loaded passes: p50 / p90 / p99 | UI thread p50, loaded |
|---|---|---|---|
| Player, Für Elise Listen | 446, 40 / 69 / 200 ms | 150-200 / 150-250 / 250-500 ms | 77 to 137 ms |
| Kid map (Mia's home) | 203, 61 / 200 / 300 ms | 150-200 / 200-400 / 300+ ms | 81 to 115 ms |
| World (You > World) | 111, 150 / 250 / 300 ms | 150-250 / 350-400 / 600+ ms | 55 to 143 ms |
| A stage (playground-1 step 2) | 204, 81 / 200 / 350 ms | 73-200 / 300-350 / 450+ ms | 34 to 136 ms |
| The Jam | 167, 85 / 250 / 650 ms | 150 / 250-350 / 600+ ms | 92 to 110 ms |

Render thread and UI thread grew together with the host load; no screen showed a UI-thread cause of its own, so no jank
fix was made (no before/after). The player's p50 of 40 ms is what #11's fix left; the rest is swiftshader.

Cold start (`am start -W`, five runs): to the picker 7.3 to 10.6 s (median 8.2 s); straight to Roy's home with
`--es profile owner` 13.8 to 15.9 s ("Displayed"). A sampling trace of the second says why the emulator is this slow:
the freshly installed debug APK runs `compilation-filter=run-from-apk` (interpreted until background dexopt), and of
the main thread's 11.9 s of CPU under the profiler, onCreate is 2.7 s: COURSE 1.0 s, CoursePath 1.35 s, the first
MaterialTheme composition 1.9 s. Moving COURSE off the main thread is a refactor, not a fix; measure on the Fold with
the profile build before deciding.

### Memory

PSS after a cold start at Learn 113.7 MB; after one round of 13 screens (chords, practice, You, world, the player, a
lesson, ear, theory, Mia's map, the Jam, a stage, stickers, Learn) 148.1 MB; after a second round 151.5 MB (Java heap
13.3 / 16.1 / 17.6 MB, native 12.8 / 44.1 / 45.4 MB). One activity, 8 views, 6 app contexts after both rounds: no
growth worth chasing. Four recreates (dark mode on and off, twice) on the Jam: threads 50 then 49; per recreate the mic
stopped and started once, MIDI once, the sampler's 23 samples loaded once.

### Security

- The token: never logged (only "token set" / "removed"), absent from crash reports and stalls, masked in the dialog
  (PasswordVisualTransformation), kept out of backups (#10). Setting or removing it is behind the parent gate for
  everyone; no debug route reaches it (#38 lists what debug routes do reach).
- FileProvider: one path, cache/share/. Exported: MainActivity (launcher) and profileinstaller's receiver (DUMP
  permission); the debug build's Compose activities are no longer exported (#37).
- Network: the app's own code connects only to api.github.com, only with a token set, sending on and an unmetered
  network. YouTube's embedded player loads only when the user opens a tutorial video (off by default). The privacy page
  says both (#24).

### Static

- Kotlin: 19 compiler warnings in app code, none real: 11 unneeded `!!` or `?.`, 3 `while (...) Unit` frame loops
  flagged "expression is unused", 2 always-true/false conditions on paths already guarded, one `when` with an `else`.
- Lint: 0 errors, 23 warnings (19 UseKtx, 1 ApplySharedPref where commit is meant (backup restore before a restart),
  1 ModifierParameter, 1 NewerVersionAvailable, 1 ChromeOsAbiSupport from the arm64-only choice), 9 hints
  (AutoboxingStateCreation, none on a per-frame state). UnusedResources fixed (#36).
- Tests: 869 JVM tests, 0 failures (131 suites).

### APK

Debug APK 147.4 MB before #28 (x86_64 and arm64-v8a), 117.4 MB at the end of the audit (117,380,303 bytes; arm64-v8a
only: assets 43.3, lib 24.2, dex 37.7, res 5.7 MB compressed).

### Not reproduced or not covered

- The "isn't responding" dialog in an earlier screenshot (arobust-anr1.png) did not come back in four monkey runs or
  the walks: the same host load as above is the likely cause.
- Not covered: anything on the Fold or a real piano, a profile build, frame times without a loaded host, a backup
  restore, MIDI hardware.

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

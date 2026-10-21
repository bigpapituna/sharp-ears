# Adult experience audit (branch audit-aadult, 2026-10-20)

The grown-up's app as the owner profile ("owner", Roy), on emulator-5554 (Medium_Phone_API_37.0, phone), light and
dark, portrait and landscape where the screen allows. One commit per fix; a JVM test wherever the logic is pure.
Screenshots: app/build/screens/aadult-*.png (not committed).

Severity: High = wrong progress, lost data or a broken flow; Medium = misleading, an accessibility gap or a visible
fault on a main path; Low = copy, polish, a corner.

## Findings

| # | Where | What | Severity | Fix | Test |
|---|---|---|---|---|---|
| 1 | Onboarding > Learn, Practice today, quest | "Some years" / "A little" said "We'll start you in Intermediate" (or Beginner), then every suggestion pointed at Basics' first lesson | High | CoursePath takes the placement and suggests from there; earlier lessons come round once the rest is done (10e5b5e) | CoursePathTest.theOnboardingPlacementIsWhereSuggestionsStart; aadult-f1-learn-after.png |
| 2 | Keyboard (every key), TalkBack | The 48 dp minimum widened each narrow key's node over its neighbours: touching C4 read D4 | Medium | Key nodes use a zero minimum; uiautomator bounds match the drawn keys (2b6ce0d) | uiautomator dump |
| 3 | Player, Listen | Letting the app play a song to the end marked it played and paid 50 XP and a song for achievements every time | High | A Listen run only sends the song back to the top (2098713) | aadult-f3-listen-end.png |
| 4 | Practice today, chord part | Back threw away the chords answered and left the session open: Chords > Daily then opened mid-queue | High | Back ends the session and counts the answered chords, like Skip the rest (e44c032) | emulator walk |
| 5 | Practice today, song part | A song part played to its end skipped its recap: stars, XP and the dropped sticker never seen | Medium | The recap shows first; closing it, or Back, goes on (25759c0) | aadult-f4-daily-recap*.png |
| 6 | Celebration, level-up | TalkBack read and could activate the home screen under them | Medium | Each is a pane (paneTitle), which also announces it (10fc145) | uiautomator dump |
| 7 | Player, light phone theme | Status bar icons dark grey on the black stage | Low | Light icons while the player shows, again after each configuration change (63cc1ca) | aadult-f10-player-statusbar-after.png |
| 8 | Chords, a chord session | Correct and Streak read 0 or 1 and reset on every chord | Low | Hidden inside a session ("2 of 5" already shows) (6584045) | emulator walk |
| 9 | Song recap | The pup fell asleep with z's over "Clean run!" a minute later | Low | Recap pup held like the lesson celebration's (idle = false) (a57f29e) | aadult-f15-recap-after-65s.png |
| 10 | Song recap, phone landscape | The sticker reveal landed below the scroll space: only the top of the card showed | Medium | The column scrolls the sticker into view once the numbers have counted (ae91e49) | aadult-f16-recap-*.png |
| 11 | Player, Wait at the song's end | Play with nothing left counted as finishing: "Clean run!", 3 stars, 50 XP, a sticker, the quest, and it wiped the hardest bars | High | Report.empty; such a run is handled like Listen (f2171db) | FeedbackTest.aRunThatReachedNothingIsEmpty; aadult-f-empty-run-after.png |
| 12 | Player, A-B loop | An end marked before the start set no loop while the bar showed "0:13-0:07" | Medium | The marks swap; the same spot twice waits for an end (ab9022f) | aadult-f8-loop-*.png |
| 13 | Microphone setup, privacy lines | "Nothing is recorded or sent anywhere" while debug builds keep recordings; "never leave the phone" over Send them for review | Medium | Both say what this build does (5795b5b) | aadult-f12-micsetup-*.png |
| 14 | Microphone setup, mic chips | "Bottom" showed selected while the app listened with the first built-in mic | Low | The chip selected is the mic start() takes (d787517) | aadult-f13-micsetup-*.png |
| 15 | Microphone setup, sliders | Read as "slider" and a percent | Low | Each says which it is (1ce9d7c) | uiautomator dump |
| 16 | MicInput restart | stop()'s posted clear wiped the new mic's name: "Starting" for good and reports with mic=null | Medium | The posted clear only runs when no start followed (ac92fdb) | emulator walk |
| 17 | Practice today interstitial | A chord part ended early showed as skipped in the chips | Low | SessionPart.doneIn matches a chord part by kind (e9576c4) | DailySessionTest.aChordPartEndedEarlyIsStillDone |
| 18 | Settings > Reset progress | "Done for today: Twinkle. 11 min." stayed over "0 of 10 min today" | Low | The daily summary is among the keys cleared (5a62b49) | ProgressStoreTest.resetClearsTodaysPracticeTodaySummary |
| 19 | Practice today, song part | The song part did not become the Continue song: Songs still offered "Start here" | Low | The part sets the last song as Songs does (a862947) | emulator walk |
| 20 | Settings > Import a 2.0 backup | Restoring a backup with no roster wiped the owner's stickers, world, quests and kid map | High | Only the files the backup holds are replaced; the confirm says what stays (8c2dd59) | ProfileFilesTest.aBackupFrom20KeepsTheOwnersNewerFiles |
| 21 | Settings > Reset progress copy | Said only chord lessons, XP and achievements stay; lessons and stickers stay too | Low | Copy corrected in the row and the dialog (410dde8) | aadult-reset-dialog.png |
| 22 | About > Privacy, What is stored | Left out the family's names and pups and the pup's world | Medium | Listed (e6f1f97) | emulator walk |
| 23 | Full-screen dialogs (Someone new, duet picker) | White status bar icons on a light page | Low | DialogBarsFollowTheme (e5c8d80) | aadult-dialogbars-after.png |
| 24 | You, the week row | TalkBack read seven letters with no sign of which days were done | Low | One node a day: "Tuesday, practised" (0a9b344) | uiautomator dump |
| 25 | Learn > Theory path | Tag said START in capitals | Low | "Start" (ef15c61) | emulator walk |
| 26 | Songs > Your songs (imported) | An imported song could not be renamed or deleted | Medium | Row menu: Rename, Delete with a confirm; the title changes in the user index only (2a0244b) | aadult-import-*.png |
| 27 | Player, hands tip | "New song. Start with the right hand alone?" stayed over a run with R already picked | Low | The row waits until its hand is not the one played (a6a4d82) | emulator walk |
| 28 | Chord games, ear games, the ear workout | Skip through all ten rounds still finished the game: 40 XP, "Good effort!", a best of 0 that took it off "New", the seconds as practice, a workout streak day | Medium | Games count Skips (allSkipped); such a game goes back to the list and earns nothing (6e31ba0) | ChordGamesTest/EarGamesTest.aGameOfOnlySkipsEarnsNothing; logcat "every round skipped, nothing earned", XP 785 before and after |
| 29 | Lesson finish, Next lesson | A lesson replayed for review led to the one after it on the path (already done) while Learn, Practice today and the quest pointed further on; a placed grown-up at the course's end got no Next | Medium | CoursePath.after(lesson, done) passes over done lessons and wraps to next() (8d7e96c) | CoursePathTest.nextLessonAfterAReplayIsWhereLearnPoints (wiring is one line, not walked) |
| 30 | Chords copy | Verb "practice" on Chords ("5 chords to practice", buttons) where the app writes "practise" | Low | Spelled "practise" (7b085dc) | copy |
| 31 | Learn > Sight-reading habits | The last scene sent the reader to "the sight-reading drill in Practice"; it is on Learn | Low | Caption and its recorded line redone (2115af1) | SpokenCatalogTest, render_voice.py (2 new, 2 deleted) |
| 32 | Settings > Daily reminder | Notifications refused twice: the switch did nothing and said nothing; turned off in the phone later, the switch stayed on with no reminder | Medium | A line says notifications are off and opens the phone's page for them; re-checked on return (5367e10) | aadult-reminder-blocked.png, aadult-reminder-refused.png |
| 33 | Android backup (privacy) | No backup rules: Google backup and phone moves took the GitHub report token and the room recordings; the 155 MB voice model put the app over the 25 MB limit, so progress was never backed up | High | data_extraction_rules.xml and backup_rules.xml exclude reports.xml, kokoro/, stalls/, fixtures/; Privacy says what the phone's backup may carry (48c95e3) | bmgr backupnow on the local transport: 45 KB, the four paths excluded (BackupXmlParserLogging) |
| 34 | Songs, Continue row | An imported song played through showed a blank line under its title | Low | "Imported", as the panel and rows say (2ef557c) | by reading |
| 35 | Player, USB MIDI | Keys held as the keyboard was unplugged stayed lit (no note-offs) | Low | Held keys cleared when the keyboard goes (6804e64) | by reading (no hardware) |
| 36 | Player > Duet picker (landscape phone) | After picking a partner, the hands and Play together were below the fold with no sign of more | Medium | A pick scrolls them into view (c100e3b) | aadult-duet-step2.png, aadult-duet-picked-after.png |
| 37 | Player bar during a duet | Both, L and R looked active but ignored taps | Low | Disabled while the duet runs (34b74ac) | aadult-zoom-duetbar.png, uiautomator enabled=false |
| 38 | Learn, phone held sideways | Opened with the next lesson's stop and its Start tag below the fold | Low | When the stop is not fully shown it goes a third of the way down; portrait unchanged (c18e5bb) | aadult-learn-top.png, aadult-learn-land-after.png, aadult-learn-port-after.png |
| 39 | Learn, a grown-up's Playground games | Cards said "Jump ahead" for games that are never next | Low | "Try it" (9e07626) | aadult-learn-extra-card-after.png |
| 40 | Sight-read result | "New best!" over "New best: 0:20, no wrong notes." | Low | The line is the time and the wrong notes (1fcfc1b) | aadult-sight2-done.png |
| 41 | Microphone setup, tuner, player mic button | Mic refused twice: Allow microphone and the mic button did nothing, so the app's main input could not be turned on and nothing said why | High | After a refusal they say the mic is off; Mic setup and the tuner open the app's page in the phone's settings and check again on return (5e29840) | aadult-mic-refused-after.png, aadult-player-mic-refused.png, aadult-tuner-refused.png |
| 42 | Chord games, chord practice, ear games: Use mic | The same silent refusal | Low | Their feedback line says the mic is off and where (8cdd4a8) | aadult-chordgame-mic-refused.png |
| 43 | Every keyboard, hint hand, tuner, dictionary Play (TalkBack) | Key names were the written ones: TalkBack read "F#4" as "F number 4" (a quiz asking for F sharp had no key called that) | Medium | spokenKey: "F sharp 4"; the dictionary's Play says "Play C sharp minor" (e72349a) | FeedbackTest.notesAreSpokenAsWords; uiautomator desc='F sharp 4' |
| 44 | Onboarding > How will you play? Microphone | Only saved the choice: the permission was never asked, so the first lesson heard nothing from the piano and said nothing | High | Finish asks for the microphone after the reminder's question (1ec15cb) | aadult-onb-mic-choice.png, aadult-onb-mic-ask.png; logcat "onboarding: microphone granted=true" |
| 45 | Songs header | "Goal met, 16 min today" over "This week: 17 min" with only today practised (the week rounded, the goal line counts down) | Low | The week counts down too, at least 1 minute (17093e9) | PracticeStatsTest.weekAndStreak |

## Summary (closing pass, 2026-10-21)

Three agents worked the branch: two found and fixed findings 1 to 45, the third (this pass) re-verified the finished tree
and walked what the table did not yet say was covered. The closing pass found nothing new to fix.

**Findings by severity (45, one commit each, 45 commits after b061572):** High 8 (#1, 3, 4, 11, 20, 33, 41, 44), Medium 14, Low 23.
Every High and Medium has a fix; a JVM test
wherever the logic is pure (CoursePathTest, FeedbackTest, DailySessionTest, ProgressStoreTest, ProfileFilesTest,
ChordGamesTest, EarGamesTest, PracticeStatsTest), the rest by emulator walk, uiautomator dump or logcat as the table says.

**Checked in the closing pass, no defect found**
- Settings: every switch on Settings (Colour notes, Note names, Finger numbers, Feedback light, Beat lines, Ghost hand,
  App sound, Vibration, Spoken guidance, Daily reminder, the key-label segments) was flipped, the app force-stopped and
  relaunched: every state came back as set (aadult-set*.xml dumps, not committed).
- Backup round trip with three profiles (Roy, Sam, Mia): Export, Reset progress, Import, Export again. The two files
  are identical, 33 preference files each (every profile's progress, mastery, ear, practice, theory, world, play, skin,
  kid, profile, plus the roster and mic).
- All 38 bundled songs opened through the player route in Wait mode with no crash (AndroidRuntime log empty). The one
  index entry that is not bundled, "Alicia", is the locked import stub; opening it by debug route, with no file, throws
  in `SongRepository.load`, but the Songs screen never offers it until a file is imported (`available`).
- Lint: 0 errors, 25 warnings (UseKtx, AutoboxingStateCreation and similar, none about accessibility or text).
- Contrast, computed from the theme: body text on its surface is 4.6 to 10.6 to 1 in light and dark. Borders
  (`outline`, `outlineVariant`) are 1.7 to 1.9 to 1, under the 3 to 1 non-text guideline; every bordered control also has
  a text label and a selected fill, so nothing relies on the border alone. Left as designed.
- Reduced motion: `reducedMotion()` (animator scale 0) is read by every file that runs an infinite or entry animation.
- A uiautomator scan of Songs, Learn, Practice (all five tabs), Chords (all tabs), You, Theory and the ear games found
  no tappable item without a label; items under 48 dp were only rows scrolled partly off screen and the keys' own nodes.
- Onboarding, Home, Learn, Settings and Family code paths were re-read against the table: Skip, the three experience
  answers, mic choice and the notification refusal all land on a defined state.

**Needs a real piano, MIDI keyboard or phone (not verifiable on the emulator)**
- Microphone note detection, the octave hint and the noisy-room offer on a real acoustic or digital piano.
- USB MIDI input, hot-plug and sustain pedal (#35 was fixed by reading).
- TalkBack speech itself (descriptions checked only through uiautomator).
- Frame rate and jank on the Fold: the emulator on a loaded host runs 200 to 300 ms frames and raised an ANR dialog on
  Practice > Scales once during the closing pass (cold start, swiftshader, three other emulators running); the earlier
  profile runs on the Fold are in CLAUDE.md. Not treated as a defect.
- Real notification delivery at the reminder time, Google backup and restore on a phone move (#33 was checked with
  `bmgr backupnow` on the local transport only).
- The voice model download and the Kokoro voice on a phone with the model installed.

**Tests:** `testDebugUnitTest` 871 tests, 0 failures, 0 skipped; `lintDebug` 0 errors. Version not bumped (2.3.0).

**Screenshots** (app/build/screens/, not committed, 200+ files named aadult-*.png). By area:
onboarding aadult-onb-*; Home aadult-home-*, aadult-dark-home*, aadult-rm-home-*; Practice today aadult-daily-*,
aadult-f4-daily-recap*; Songs and import aadult-songs-list, aadult-import-*; player aadult-player-*, aadult-hint-*,
aadult-wrong-*, aadult-transpose-*, aadult-f8-loop-*, aadult-f16-recap-*, aadult-duet-*; Learn aadult-learn-*,
aadult-lesson-*, aadult-theory-*, aadult-sight-*, aadult-ear-*; Practice tools aadult-practice-*; Chords aadult-chords-*,
aadult-game-*, aadult-chordgame-*; You aadult-you-*; Settings aadult-settings-*, aadult-reminder-*, aadult-mic*-,
aadult-micsetup-*, aadult-token-*, aadult-family-*, aadult-export-*, aadult-reset-dialog, aadult-privacy-after;
dark and landscape aadult-dark-*, aadult-land-*; findings before and after aadult-f<n>-*.

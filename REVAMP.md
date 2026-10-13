# Sharp Ears 2.0: the revamp

Roy's brief (2026-10-13): the biggest update by far. Robust, feature-rich, teaches people, animated and engaging.
Delivered by six parallel teams, each in its own git worktree and branch, then an integration pass. The app is
private (Roy's phone only); personal music stays out of git; the GitHub repo is private.

## Vision

A complete piano teacher in one app: a course that goes from first touch to intermediate repertoire, a theory
academy with animated explainers, ear and rhythm training, practice tools (scales, warm-ups, metronome, tuner),
a growing song library, and a coach (the Golden Coach Pup) who tracks progress, celebrates wins and keeps a
learner coming back through goals, streaks and achievements. Everything on the phone, nothing in the cloud.

## Teams (wave 1, parallel)

| Team | Branch | Owns (exclusive) | Delivers |
|---|---|---|---|
| A Theory Academy (Opus) | revamp/theory | engine/theory/*, ui/theory/* | Animated explainers (staff, intervals, scales, keys, circle of fifths, rhythm, chords), quizzes, 6 units |
| B Progress & You (Sonnet) | revamp/progress | engine/progress/*, data/ProgressStore.kt, ui/profile/*, ui/onboarding/* | XP, levels, achievements, goals, streak freeze, stats charts, calendar heatmap, the You tab, first-run onboarding |
| C Practice tools (Sonnet) | revamp/practice | engine/practice/*, ui/practice/* | Scales and arpeggios in every key with fingering, warm-ups (Hanon-style), hand independence drills, metronome tool, tuner |
| D Ear & rhythm (Sonnet) | revamp/ear | engine/ear/*, ui/ear/* | Rhythm tap, melody memory, perfect pitch, scale degrees, progression ID, a hub with levels |
| E Motion & delight (Opus) | revamp/motion | ui/Keyboard.kt, ui/NoteLane.kt, ui/HitLights.kt, ui/Pup.kt, ui/Celebrate.kt, ui/motion/* | Key animations, hit particles, combo counter, ghost hand showing fingering, pup idle life, transitions, level-up, haptics |
| F Content (Sonnet) | revamp/content | scripts/gen_songs.py, assets/songs/*, engine/Course.kt, assets/lessons | 15+ new public-domain songs with sections, course expansion (Repertoire, Technique, Reading units) |

Shared files no team edits (the integration pass wires everything): MainActivity.kt, HomeShell.kt, data/Prefs.kt,
ui/LearnScreen.kt, ui/Theme.kt, audio/*, engine/Transport.kt, engine/NoteMatcher.kt. Exception: a team may add
ONE debug route (`--es screen <name>`) to MainActivity's `first` block so it can test on an emulator; keep it to
the smallest possible diff.

Each team: new preferences in its own `data/<Feature>Store.kt` (its own SharedPreferences file), engine code with
no Android imports and JVM tests, an `INTEGRATION.md` at its package root saying what to wire (tab, entry points,
events to record). Compile (`:app:compileDebugKotlin`) and test (`:app:testDebugUnitTest`) must pass before each
commit. Commit on the branch; never push.

## Wave 2: integration and QA (Opus)

Merge the six branches into main, then: five tabs (Songs, Learn, Practice, Chords, You; Settings reached from
You), Learn hosts the course path + Theory + Ear training, every XP event wired (practice time, lesson, song run,
game, theory unit), onboarding on first run, ghost hand and particles in the player, transitions between screens,
level-up celebrations. Then: all unit tests, lint, emulator walk-through with screenshots of every new screen in
light and dark, fix what breaks, update CLAUDE.md and docs. Install on Roy's phone only when he says so.

## Design rules (unchanged)

Warm palette, one accent, the pup's gold only around him; Nunito headlines, Geist body, Geist Mono numbers; pills
for actions (ChunkyButton for the main one), 20 dp surfaces, no cards-for-everything; speech bubbles for the pup;
reduced motion respected everywhere (ui/Theme.kt reducedMotion()); no em dashes in copy; sentence case.

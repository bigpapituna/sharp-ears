# Tester events: the taxonomy (2.15)

Everything here goes to Roy's PostHog project (680038, us.posthog.com) only while **Help Roy test** is on, and only from a
build with the PostHog key. A keyless build has no PostHog class at all and sends nothing. The code is the source of truth:
`engine/tester/AppEvents.kt` (`CATALOG`, the builders), `engine/tester/TesterRules.kt` (`TesterTally`, made from the brief's
own log), `analytics/Events.kt` (the ones sent from a screen) and `analytics/Errors.kt` (error reports). `AppEventsTest`
fails when an event or a property is missing from this page, when a property key holds a denied word, or when the code
sends an event the catalog does not list.

## The privacy rule

- A property is an id of the app's own (a lesson, a bundled song, a chord chart, an arcade game), a number, a bucket, a
  boolean or one of a few fixed words. Never a name, an email address, a title, typed text, a file or a path: no property
  key may hold the words `name`, `email`, `title`, `text`, `file` or `path` (`AppEvents.denied`, checked by a test).
- A song of the person's own (imported, or added to My songs) goes out as `my_song`. Its id is made from its title or file
  name, so it never leaves the phone. Screen names say `song:user-song` for the same reason (2.15 also fixed this for My
  songs, which had slipped through).
- No sound, ever. Session replay masks every text and picture (see `analytics/INTEGRATION.md`).

## Super properties (with every event)

| Property | Values | Set by |
|---|---|---|
| `profile_kind` | `kid` (a little kid, 4 to 7), `big_kid` (8 to 13), `adult` | `Tester.profile`, at each profile open |
| `layout` | `simple`, `advanced`, `full` | `Tester.look`, from MainActivity's layout |
| `plain_look` | `true`, `false` | `Tester.look(layout, plainLook)`: only once the plain-mode branch passes it; absent before |
| `app_version` | the version name, `2.14.0` | `Tester.marks` |
| `is_emulator` | `true` on an emulator, `false` on a phone (never missing) | `Tester.marks` (`BuildMarks.isEmulator`) |
| `build_flavor` | `debug` (keyless, agents), `tester` (a keyed build: Roy's phone and the testers'), `profile`, `release` | `Tester.marks` |
| `coach_voice` | `true` (the coach voice plays for the profile open), `false` | `Tester.extra`, from the 2.16 extra's door at each profile open, setting change and moment; only on a build with AFTERDARK=true (absent otherwise). Never a line, its words or its id |
| `coach_voice_id` | the voice's folder (`male_us`, `male_irish`, `female_us`, later `female_au`), or `off` | `Tester.extra`, the same door and times; only on a build with AFTERDARK=true |
| `coach_for` | `w` (the coach talks to a woman), `m` (to a man), `name` (2.17: "Just use my name", the lines for anyone), `unset` | `Tester.extra`, the same door and times; only on a build with AFTERDARK=true |
| `input_mode` | `mic`, `midi`, `touch` (2.17: how the person open plays now: a MIDI keyboard plugged in, else the mic when it is on and allowed, else the screen's keys) | `Tester.input`, at each profile open and when the first playing step's question is answered |

`Errors.beforeSend` also stamps `is_emulator`, `build_flavor` and `app_version` on any event that left before they were
registered. PostHog adds its own `$is_emulator`, `$app_version` and the rest; ours are the ones the setup page filters on.
2.15 changed `profile_kind`: a big kid used to go out as `kid`. Events that carry their own `profile_kind` (the retention
events, `cheer_pop`) now say `big_kid` for a big kid too (`AppEvents.refineKind` in `Tester.capture`).

## Events

### Sessions

| Event | Properties | When |
|---|---|---|
| `app_open` | `cold` (true at a launch, false back from the background) | the app comes on screen |
| `session_length` | `minutes_bucket` | a session ends (the app leaves the screen, or another person takes over) |
| `practice_session` | `minutes_bucket` (practice time), `parts` (kinds of play: lesson, song, chart, arcade, ear, toy) | a session that had practice ends |
| `$screen` | `route` | each screen (PostHog's own name; the title is the route's last part) |

Buckets: minutes are `0`, `1-4`, `5-9`, `10-19`, `20-29`, `30-59`, `60+`; accuracy is `0-49`, `50-69`, `70-84`, `85-94`,
`95-100`.

### Learning

| Event | Properties | When |
|---|---|---|
| `lesson_started` | `lesson` | a lesson's screen first shows |
| `lesson_step_passed` | `lesson`, `step` (its index), `kind` (read, listen, play, from_memory, perform, ramp, pedal, dynamics), `stars` (when it had some), `tries`, `let_go` | a step is left forward: passed, or let go after three tries (never one skipped with Next or left part way) |
| `lesson_finished` | `lesson`, `stars`, `tries`, `let_go` (the count), `seconds` | the last step ends |
| `stage_stuck` | `lesson`, `step`, `kind`, `tries`, `let_go` | a step took three or more tries, or was let go |
| `hint_step` | `lesson`, `step`, `kind`, `tries`, `let_go`, `hints`, `deepest` | a step needed hints |
| `song_played` | `song` (a bundled id, or `my_song`), `mode` (listen, wait, perform), `stars`, `accuracy_bucket` (Perform), `duet` | a song played to its end (listen: the app played it through) |
| `chord_chart_played` | `chart` (the song's id), `mode` (listen for Hear the band; wait or perform for Play along), `stars` | a chord chart heard or played along to the end |
| `arcade_game_played` | `game`, `level`, `stars` | an arcade round ends |
| `sight_drill_done` | `level`, `score` (notes right for `staff`, points for `piece`, lower is better), `kind` | a sight-reading run ends |
| `daily_plan_started` | `parts` | Practice today starts |
| `daily_plan_finished` | `parts` (done), `of`, `minutes_bucket`, `complete` | Practice today ends (done for today, or left part way) |
| `toy_opened` | `toy`, `seconds` | a toy of the play corner closes |

`song_run` (2.6 to 2.14) became `song_played` and `chord_chart_played` in 2.15. Its raw `accuracy` became
`accuracy_bucket`.

### Setting up

| Event | Properties | When |
|---|---|---|
| `onboarding_step` | `step` (welcome, experience, goal; 2.16 and before also habit), `result` (completed, skipped), and the card's answer (2.17): `who` (me, child; the welcome card, only when "Who is this for?" was asked), `level` (never, a_little, some, a_lot; the experience card), `goal` (minutes a day; the goal card) | an onboarding card is left with Continue, Finish or Skip |
| `first_note` | `input` (mic, midi, touch), `seconds_since_first_open` (since this profile first opened on this phone; profiles from before 2.17 never send it) | the profile's first right note in a player (a lesson step, a song, the placement check), once per profile (2.17) |
| `input_chosen` | `answer` (mic: "Use my piano", touch: "Use screen keys") | a grown-up answers "Got a piano or keyboard here?" on their first playing step (2.17; not asked with MIDI plugged in, nor of kids) |
| `notification_ask` | `result` (granted, denied, not_now) | the owner answers "Want a nudge tomorrow at ...?" under their first finished lesson: Yes with notifications allowed (granted), Yes and Android said no (denied), Not now (2.17; the onboarding no longer asks) |
| `placement_result` | `level` (never, a_little, some, a_lot) | a piano level is set (the onboarding's answer, the placement check, Family > Piano level) |
| `layout_chosen` | `layout` | a layout is chosen (at launch or in Settings) |
| `big_kid_made` | none | a kid profile is made as a big kid |
| `tour_card_seen` | `which` (the tour: plan, band, timing, loop, streak; For grown-ups: family, limit, gate) | a card of the tour or of For grown-ups shows (the Plain look skips streak) |
| `mic_permission` | `result` (granted, denied) | someone answers the microphone question, on any screen |
| `mic_not_heard` | `cause` (noise, silence) | the could-not-hear hint shows in a player, a lesson step or a Playground stage (once per hint; the brief's `notHeard` through TesterTally) |

### Grown-ups and family

| Event | Properties | When |
|---|---|---|
| `family_card_opened` | none | a child's row on Family today is tapped |
| `brief_opened` | `period`, `kind` (the child's: kid or adult) | a brief opens |
| `brief_email_set_up` | `set_up` (yes, no) | the family's email address is confirmed with its code (yes), or the emails are stopped (no) |
| `time_cap_set` | `minutes` (0 is off) | a kid's daily time is set |
| `time_cap_reached` | `minutes` | the daily time's goodbye shows (once a day) |
| `gate_opened` | none | the parent gate's sum is answered right |
| `gate_failed` | none | a wrong answer at the gate |
| `email_sent_result` | `result` | an email send ends |
| `ai_brief_result` | `result` | an AI brief call ends |

### Retention and the pops (2.9 to 2.13)

| Event | Properties |
|---|---|
| `comeback_shown`, `comeback_played`, `rest_day_used`, `surprise_wrapped`, `surprise_opened` | `profile_kind` |
| `step_pop` | `streak` |
| `cheer_pop` | `trigger`, `profile_kind` |

### Testing and errors

| Event | Properties | When |
|---|---|---|
| `tester_default_on` | none | the default turned Help Roy test on (once) |
| `profile_kind` | `kind` | the person playing changed |
| `felt_off` | `screen` | "Something felt off" was kept |
| `error_shown` | `where`, `screen` | an error was put on screen |
| `$exception` | `where`, `handled`, `is_error_test`, plus at most `kind`, `status`, `problem`, `code`, `reason`, `importance`, `clip` | an error report (below) |

## Error reports (`$exception`)

PostHog's error tracking, through the SDK (posthog-android 3.72.5):

- **Uncaught exceptions**: `Errors.installCrashHandler` puts the app's own handler in front of the SDK's: the crash goes
  out as fatal (`where` = `uncaught`, `handled` = false), written to the SDK's queue at once and sent at the next start. The
  SDK's own autocapture is on too (`errorTrackingConfig.autoCapture`), but PostHog's remote config turns it off while the
  project's "Exception autocapture" is off (it was off in project 680038 on 2026-10-27); when both act, the second copy has
  the same place, class and message, and the limit drops it, so a crash goes out once.
- **Native crashes** (the speech library): read from Android's tombstones at the next start (`captureNativeCrashes`).
- **ANRs**: the SDK has none. At the next start `Errors.reportPastExits` reads the ANRs Android kept (Android 11 and up,
  `ApplicationExitInfo`) that were not reported yet, each as `ApplicationNotResponding` with the main thread's stack from
  Android's trace, `where` = `anr`.
- **Handled exceptions**, through `Errors.report(where, throwable, extra)` at the app's risk points:

| `where` | What failed | `extra` |
|---|---|---|
| `voice_engine` | the phone's speech engine did not start | `code` |
| `voice_utterance` | the speech engine failed a line | `code` |
| `natural_voice` | the natural voice (Kokoro) failed to say a line | none |
| `natural_voice_load` | the Kokoro model did not load | none |
| `ai_transport` | an AI brief call failed (OpenRouter, the model Anthropic's): rate limited, refused, server, timeout, network, bad reply | `kind`, `status` |
| `email_send` | an email send failed (Brevo) | `kind`, `status` |
| `midi_refused` | a file picked for My songs was refused | `problem` (not_midi, damaged, no_notes, drums_only, too_big, unreadable) |
| `midi_pick` | reading a picked file failed | none |
| `song_import` | the older song import failed | none |
| `pup_clip` | a pup clip did not decode | `clip` |
| `anr` | an earlier run did not respond | `reason`, `importance` |
| `uncaught` | a crash (`handled` = false, `$exception_level` fatal) | none |
| `error_test` | the debug route's test (below) | none |

Calls not made on purpose (a metered network, no key, a bad URL) are not reported.

What a report may carry: the exception's class and stack frames (the app's own code), and its message scrubbed by
`ErrorScrub.message`: no email address, key or bearer token, long opaque token, link, path, file name, nothing quoted, no
run of digits like a phone number, none of the family's names, one line, at most 200 characters. The same scrub runs in
`Errors.beforeSend` on every `$exception`, the SDK's own included.

How often: one report per place, exception class and message in ten minutes, and at most 30 a day in all. The count is kept
in tester.xml (never backed up), so a crash loop cannot reset it. A report beyond the limit logs `errors: <where> not sent
(limited)`.

Log lines (tag SharpEars): `errors: reported <where> (<Class>)` when a report goes (`errors: reported uncaught
(<Class>)` for a crash), `errors: reported native_crash (...)` or `errors: reported uncaught (<Class>, fatal=true,
is_error_test=..., is_emulator=...)` when one the SDK caught itself goes, `errors: <where> not sent (<why>)` otherwise.
Never the message.

The test: `--es screen errortest` (a debug build, through engine/DebugRoutes.kt; keyed builds through
`scripts/adb-route.ps1`) sends one handled `ErrorTestException` at once, then throws an uncaught one four seconds later, which
closes the app; that one is sent at the next start. Both carry `is_error_test` = true so Roy can find and delete them. A second
run within ten minutes sends only the crash (the handled one is limited).

## Feature flags

Read in one place (`engine/tester/Flags.kt`, through `AppFlags` in `analytics/Events.kt`), only while the dashboard records;
anything missing or odd gives the safe local default, which is how the app behaves without PostHog. A value is kept for the
rest of a profile's visit once it came. `$feature_flag_called` goes out when a flag is read, so experiments see who was shown
what.

| Flag | Type | Default | Read by |
|---|---|---|---|
| `plain_look_offer` | boolean (or variants `control` and `test`) | true: the offer shows | the plain-mode branch's one-time Plain look offer (not on main yet: `AppFlags.plainLookOffer`) |
| `cheer_frequency_default` | multivariate: `often`, `less`, `off` | `often` | `Prefs.cheerFrequency` for a profile that never chose Encouragement |

# Tester analytics: integration notes (branch `posthog`)

Roy tests with friends and family and wants to see where testers get stuck. He asks the testers (and a child's parent)
himself, turns testing on on the phone he hands over, and removes the feature before any public release. CLAUDE.md,
"Tester analytics", has his setup steps.

## What it is

- **"Help Roy test"**: one phone-wide switch behind the parent gate (the kids' grown-ups corner, which the gate already opened,
  and Settings > Family, where each change asks the gate). Since 2.13 (Roy, 2026-10-25) it is on by default where its row
  shows (a build with the key, or a debug build, which sends nothing without the key): an unset switch becomes on once, is
  saved (`tester: default on`, the event `tester_default_on`), and a grown-up gets a one-time notice card with OK (never a
  kid: it waits for a grown-up's profile; never over the stage). A choice saved on the phone, on or off, always wins. One
  line under it:
  "Sends screen recordings and app events to Roy's test dashboard. No sound." While it is on, the kids' whistle carries a
  small accent dot. Kid profiles are recorded too (Roy's update, 2026-10-21: no per-child tick).
- **PostHog** (com.posthog:posthog-android 3.72.5, session replay) records only on a build with `POSTHOG_KEY` and only while the
  switch is on. Off at start: nothing is set up. Turned off later: `stopSessionReplay()` and `optOut()`, nothing flushed.
- **"Email Roy a daily diary"**: its own switch, only on a build with `DIARY_TO`, off by default. Plain text, once a day
  (20:30 to 21:30, or the next morning from 07:00 for what is left; never in the quiet hours, never twice a day, never with
  nothing new), to `DIARY_TO` only, through the brief's Brevo transport (needs EMAIL_KEY and EMAIL_FROM too).
- **"Something felt off"** (while either is on): one line, kept with the screen it was on and the one before, in the diary;
  PostHog gets a `felt_off` event with the screen only (never the text).

## The build flag

`app/build.gradle.kts`: `withPostHog = POSTHOG_KEY is set` (or `-Psharpears.posthog=true`, to compile the SDK in without a key
and check that code builds). With it: the dependency and `src/posthog/java/.../analytics/TesterSdk.kt`. Without it:
`src/noposthog/java/.../analytics/TesterSdk.kt`, a stub with the same members, and no PostHog class in the APK (the keyless
debug APK's dex has no `com/posthog` class; checked with dexdump). The SDK's AAR declares no provider, service or receiver
(only INTERNET and ACCESS_NETWORK_STATE, which the app has), so nothing of it can start on its own even when it is in.
`TesterWiringTest` keeps the gradle lines, the door's config and "no `import com.posthog` outside the door".

## Files

- `engine/tester/TesterRules.kt` (pure): when the SDK may start, which rows show, screen names from routes, imported song ids
  as `user-song`, `lesson_started`, ai/email log lines as results, safe lines (no address, no family name), and
  `TesterTally` (brief events -> `stage_stuck`, `hint_step`, `lesson_finished` with tries and let-gos, `song_run`, `toy_opened`).
- `engine/tester/Diary.kt` (pure): the tester log's line kinds (`t_screen`, `t_note`, `t_error`, `t_result`, `t_log`, `t_kind`,
  written as `BriefEvent.Unknown` through the brief's own `EventLog`), `DiarySchedule`, the diary's text.
- `analytics/Tester.kt`: the switches and device id (tester.xml), the tester log (files/tester/diary.log), the hooks.
- `analytics/TesterDiary.kt`: the daily email, run by reminder/EmailJob.kt (`run` sends, `plan` only looks).
- `analytics/TesterSettings.kt`: the rows and the note dialog.
- `src/posthog|noposthog/.../TesterSdk.kt`: the door, and `Modifier.testerMask()` (PostHog's `postHogMask()`, or nothing).

## Hooks in existing code

- MainActivity: `Tester.init` and `Briefs.tap = Tester::briefEvent` in onCreate; `Tester.profile(isKid, id)` in openApp;
  `Tester.screen(route)` beside the saved route; `away()` / `back()` in onStop / onStart; the session end copies the
  log's refusal lines (`collectLog`) on a thread while a switch is on; the song import error.
- data/Briefs.kt: `tap`, called after each logged event.
- data/BriefAi.kt, data/BriefEmail.kt: their `log` also hands the line to `Tester.logLine` (ai_brief_result,
  email_sent_result). BriefEmail gained `transport(context)`, shared by the brief's sender and the diary.
- reminder/EmailJob.kt: the diary's look in `run` and `plan`; both now call `Briefs.init(app)` first, because the alarm can
  wake a process with no activity, where `Briefs.read` returned nothing (that also hid every child's play from the brief
  emails' schedule in such a process).
- Errors on screen (`error_shown`, the place only): song import, progress export and import, the brief's Send, the
  certificate's and the concert's share, the recordings' share.
- `brief_opened` (period, kid or adult) in ParentBriefScreen.
- Masked as a whole in replays (`testerMask`): the grown-ups corner sheet, the brief screens, the email settings, the
  names in Settings > Family, the launch picker's names, the family wall, the certificate. Everything else: all text and
  images masked by the SDK config.
- Privacy page ("Testing", and the first and Internet paragraphs) and docs/privacy-policy.md only on a build with a key or a
  diary address; the onboarding line says it while a switch is on.
- Backup: tester.xml and files/tester excluded (both rule files, BackupRulesTest).

## Events (PostHog)

`$screen` per screen (title: route name, `route` property), `lesson_started`, `lesson_finished`, `stage_stuck`, `hint_step`,
`song_run`, `toy_opened`, `profile_kind` (also a super property: kid or adult), `brief_opened`, `email_sent_result`,
`ai_brief_result`, `error_shown`, `felt_off`, plus the SDK's own application lifecycle events. Properties are ids, the app's
own words and numbers; a sanitizer drops any value shaped like an email address.

2.9, retention (`Tester.retention`, names in engine/tester/Retention.kt): `comeback_shown`, `comeback_played`, `rest_day_used`,
`surprise_wrapped`, `surprise_opened`, each with `profile_kind` (kid or adult) only. To judge them: comeback_played over
comeback_shown, the share of returns that play; rest_day_used per active profile per week; surprise_opened over surprise_wrapped
(a kid who came back the next day or later), and the days-back numbers the weekly brief now carries.

2.11, the step pop (`Tester.stepPop`, engine/tester/StepPopEvent.kt): `step_pop` each time a lesson step pops (passed
cleanly with another step to come), with `streak` (the run of clean steps in that lesson, 1 and up) only. To judge it: the
spread of streak lengths, and lesson_finished over lesson_started before and after 2.11.

2.13, the cheer pops (`Tester.cheerPop`, engine/tester/CheerPopEvent.kt): `cheer_pop` each time one shows (and with each step
pop's bubble), with `trigger` (run, recovery, halfway, hard_passed, steady, focus, nudge, step_passed) and `profile_kind` (kid or
adult) only: no line, no name, no lesson. To judge it: pops per minute played by family and audience, and lesson_finished over
lesson_started with Encouragement on against off (the brief logs the setting as `cheer`).

## Not done / for the integrator

- Nothing ran against PostHog or Brevo (no keys on this PC). Roy's check is in CLAUDE.md.
- `hint_step` comes at the stage's end (one event with the hint count and deepest step), not at each hint as it shows.
- The log copy reads the app's own logcat (`logcat -d -t <time> -s SharpEars:D AndroidRuntime:E`): best effort, logcat
  keeps only the last few hundred KB.
- PostHog's own preference file is named after the key (`posthog-android-<key>.xml`), so the backup rules cannot list it;
  it holds the SDK's ids and flags only, and a restored phone starts with the switch off (tester.xml is not restored).
- The dot is on the kids' whistle only; a grown-up's home has no corner.

## 2.15: error tracking and the event taxonomy (branch `error-tracking`)

- The full table of events, properties, flags and error reports is `EVENTS.md` (beside this file); Roy's PostHog steps
  are `docs/POSTHOG-SETUP.md`.
- Error tracking: the door turns on the SDK's exception autocapture and native crash capture (crashes also go through the
  app's own handler, `Errors.installCrashHandler`, since the project's autocapture setting can switch the SDK's off), adds a `beforeSend`
  (`Errors.beforeSend`: scrubs every exception message, stamps `is_emulator`, `build_flavor`, `app_version`, marks
  `is_error_test`, limits the SDK's own reports) and offers `captureException`. `Errors.report(where, throwable, extra)` is
  the one helper for handled exceptions (rate limited and scrubbed by `engine/tester/ErrorRules.kt`); `Errors.reportPastExits`
  reports ANRs of earlier runs. All of it only while Help Roy test is on, on a build with the key.
- `profile_kind` has three values now: `kid`, `big_kid`, `adult` (`Tester.profile` reads the roster for the band; events
  that carry their own kind are refined in `Tester.capture`).
- New super properties: `layout` (`Tester.look`, MainActivity), `plain_look` (`Tester.look(layout, plainLook)`, for the
  plain-mode branch to pass), `is_emulator`, `build_flavor`, `app_version`.
- `song_run` became `song_played` (`my_song` for a song of the person's own, `accuracy_bucket`) and `chord_chart_played`.
  `TesterRules.safeId` now also hides My songs ids (made from the title).
- Flags: `AppFlags` (in `Events.kt`) over `engine/tester/Flags.kt`. `Prefs.cheerFrequency` reads `cheer_frequency_default`
  when unset. `sendFeatureFlagEvent` is on now, so experiments get their exposure events.
- `-Psharpears.posthog=false` builds the keyless door on a PC that has the key.

For the merger:
- plain-mode: call `Tester.look(layout.wire, plainLook = <the Plain look in force>)` where MainActivity calls
  `Tester.look(layout.wire)` (one line, after the layout's `remember`), and read `AppFlags.plainLookOffer` before showing
  the one-time Plain look offer.
- mic-trust: its `mic_not_heard` is in `AppEvents.CATALOG` already; keep its `TesterRules.MIC_NOT_HEARD` and its
  `TesterTally` line (the `when` there now also has 2.15's branches after `LessonDone`).

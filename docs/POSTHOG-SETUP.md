# PostHog setup for Sharp Ears (2.15)

For Roy, or an agent driving Chrome for him. Project 680038 on us.posthog.com. The app side is done (CLAUDE.md "2.15
posthog"); the events and their properties are in `app/src/main/java/com/bigpapituna/sharpears/analytics/EVENTS.md`. Nothing
here was clicked by the agent that wrote it: these are the steps to click together.

Everything below only fills with data from phones where Help Roy test is on, running a build with the PostHog key.

## 1. Error tracking in the project

The app reports crashes, ANRs and its handled errors itself, so nothing here is required for them to arrive.

1. Open **Error tracking** in the left menu: the issues list. Reports group into issues by exception and stack; the 2.15
   emulator test made one issue for `ErrorTestException` (see step 5).
2. Optional: Settings (the gear, bottom left) > Project > **Error tracking** > **Exception autocapture** (it may be called
   "Autocapture exceptions"). It was off in this project on 2026-10-27. On, the SDK's own crash handler acts as well; the app
   keeps it to one copy per crash, so either way is fine. Leave it as it is unless PostHog's docs ask for it.

Proguard or R8 mapping upload is not needed: the debug builds Roy hands out are not minified.

## 2. Filter out test traffic (internal and test users)

Every event carries `is_emulator` (true or false), `build_flavor` (`debug`, `tester`, `profile`, `release`) and
`app_version`; error test reports carry `is_error_test` = true.

1. Settings > Project > **Product analytics** > **Filter out internal and test users**.
2. Add these filters (each an event property):
   - `is_emulator` **is not** `true`
   - `build_flavor` **is not** `debug`
   - `is_error_test` **is not** `true`
3. Turn on **Enable this filter on all new insights** (it may read "Filter out internal and test users by default").
4. Save. In any insight the toggle **Filter out internal and test users** now applies these; leave it on.

Use "is not" rather than "equals false": events from before 2.15 have no `is_emulator` and should still count.

Roy's own phone sends `build_flavor` = `tester` like the testers' phones. To leave his own play out too, add a fourth filter on
his phone's id once he knows it: Activity > open one of his events > copy the `distinct_id` > add `distinct_id` **is not**
that id.

## 3. The dashboard

Product analytics > Dashboards > **New dashboard** > Blank > name it **Sharp Ears testers**. Make each insight below with
**New insight**, then **Save to dashboard** > Sharp Ears testers. Keep **Filter out internal and test users** on in each.

### 3.1 Retention by profile kind

Do the kids come back?

1. New insight > **Retention**.
2. **Cohortizing event** (first time / "Start event"): `app_open`. **Retaining event** ("Return event"): `app_open`.
3. Period: **Week** (and a second copy with **Day**, for the first weeks of testing). Date range: last 8 weeks.
4. **Breakdown** by event property `profile_kind` (kid, big_kid, adult). If the breakdown is not offered for retention on
   this project, save three copies instead, each with a filter `profile_kind` **equals** `kid`, `big_kid` or `adult`.
5. Name it "Retention by profile kind". Save to the dashboard.

Read with care: PostHog counts a phone (the app's random device id), not a person. A phone where a kid and a grown-up both
play counts under each kind it was used as.

### 3.2 Funnel: onboarding, first lesson, day 2 return

1. New insight > **Funnels**.
2. Step 1: `onboarding_step`, with filters `step` **equals** `habit` and `result` **equals** `completed` (the last card,
   Finish). Optional: a second insight with step 1 any `onboarding_step` where `result` = `skipped`, to see who skips.
3. Step 2: `lesson_step_passed` (the first lesson step passed).
4. Step 3: `app_open` with filter `cold` **equals** `true` (a later launch).
5. **Conversion window**: 2 days. Step order: Sequential. Date range: last 30 days.
6. Name it "Onboarding to first lesson to return". Save to the dashboard.

A funnel cannot ask for "the next calendar day". For the exact day 2 return, add a retention insight: Retention, cohortizing
event `onboarding_step` (`step` = `habit`, `result` = `completed`), retaining event `app_open`, period **Day**; the **Day 1**
column is the share back the next day. Name it "Day 2 return after onboarding" and save it beside the funnel.

### 3.3 Lesson step drop-off

Where in a lesson do people stop?

1. New insight > **Funnels**.
2. Step 1: `lesson_started`. Steps 2 to 5: `lesson_step_passed` with filter `step` **equals** `0`, then `1`, `2`, `3`.
   Last step: `lesson_finished`.
3. Add a filter on every step: `lesson` **equals** the lesson to look at (start with `find-c`, a first lesson; the lesson ids
   are in `engine/Course.kt`). Or, for all lessons at once, **Breakdown** by event property `lesson`.
4. Conversion window: 1 hour. Name it "Lesson step drop-off". Save to the dashboard.

Beside it, a **Trends** insight: event `stage_stuck`, **Total count**, breakdown `lesson`, chart **Table**: the steps that
take three tries or more. And `hint_step` the same way, for the hints.

### 3.4 Errors by where

1. New insight > **Trends**.
2. Event: `$exception` (it may show as "Exception"). Math: **Total count**. Interval: day.
3. **Breakdown** by event property `where` (voice_engine, ai_transport, email_send, midi_refused, uncaught, anr ...).
4. Filter: `is_error_test` **is not** `true` (the test filter does this too).
5. Chart: stacked bars. Name it "Errors by where". Save to the dashboard.

A second one is worth it: the same with **Unique users** (how many phones hit each error). The details of each report (the
stack, the scrubbed message, the app version) are on Error tracking > the issue.

### 3.5 Weekly active by layout

1. New insight > **Trends**.
2. Event: `app_open`. Math: **Weekly active users** (or **Unique users** with interval **Week**).
3. **Breakdown** by event property `layout` (simple, advanced, full).
4. Date range: last 8 weeks. Name it "Weekly active by layout". Save to the dashboard.

Once the plain-mode branch is in, a copy broken down by `plain_look` shows who keeps the Plain look on.

### 3.6 Useful extras

- Practice: Trends, `practice_session`, Total count, breakdown `minutes_bucket`; and `daily_plan_finished` broken down by
  `complete`.
- Songs: Trends, `song_played`, breakdown `song`, chart Table; `chord_chart_played` by `chart`.
- The parent gate: Trends, `gate_failed` against `gate_opened` (a gate that fails often is too hard).
- The microphone: Trends, `mic_permission` by `result`; with the mic-trust branch, `mic_not_heard` by `cause`.

## 4. Experiments with the flags

Two flags, read by the app with a safe default when PostHog is not there (EVENTS.md, "Feature flags"):

- `cheer_frequency_default`: where Encouragement starts for a profile that never chose. Feature flags > **New feature
  flag** > key `cheer_frequency_default` > **Multiple variants**: `often` and `less` (50/50; `off` is allowed too). Or make
  it an **Experiment** (Experiments > New experiment, feature flag key `cheer_frequency_default`, variants `often` as control
  and `less` as test) with the goal metric `lesson_finished` (and secondary `app_open` retention). Only profiles that never
  touched the setting take the flag's value.
- `plain_look_offer`: whether the plain-mode branch's one-time "Prefer a plainer look?" card shows. A boolean flag (or an
  experiment with variants `control` = no offer and `test` = offer). It does nothing until plain-mode is merged and reads
  `AppFlags.plainLookOffer`.

`$feature_flag_called` events go out when the app reads a flag, so the experiment knows who saw which variant.

## 5. Removing test events

Every event from an emulator says `is_emulator` = true and the error test's say `is_error_test` = true, so the filters in
step 2 already keep them out of every insight. To clear them from Error tracking, open the issue for
`ErrorTestException` and resolve or suppress it. If the plan offers event deletion (Settings > Project > Data management or
a deletion request), delete events where `is_error_test` = true, and the emulator's events by its `distinct_id`.

The 2.15 emulator test (2026-10-27, UTC) sent, all from `distinct_id` `0a8925c2-82ea-4169-9cf4-5c7ae4b8f24a` with
`is_emulator` = true: 15:14:58 to 15:15:01 Application Installed, `tester_default_on`, `app_open`, `profile_kind`,
Application Opened, `$screen`; 15:15:25 `$exception` (`where` error_test); 15:19:52 to 15:19:56 `app_open`, `profile_kind`,
Application Opened, `$screen` and `$exception` (`where` uncaught, a fatal crash on purpose); 15:20:17 to 15:20:20 `app_open`,
`profile_kind`, Application Opened, `$screen`; and three short masked session replays of those minutes.

## 6. End of the beta

As before (CLAUDE.md "Roy's PostHog setup", step 6): the switch off on each phone, `.\scripts\set-posthog-key.ps1 -Remove`,
rebuild, and in PostHog Settings > Project > Danger zone, delete the project (every recording, event and error report).

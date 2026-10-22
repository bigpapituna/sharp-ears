# Parent brief: plan

Agreed with Roy on 2026-10-21 in a brainstorm. Status: built and integrated in 2.4.0 (2026-10-21, branch `release-2.4`,
versionCode 13); the brief by email (design D, Brevo, daily and weekly, a code to confirm the address) integrated in 2.5.0
(2026-10-21, branch `release-2.5`, versionCode 14; emulator only). Every brief is written by the templates and no email has
been sent until Roy puts his keys in, hosts the email's pictures (hosting/README.md) and runs the manual checks (CLAUDE.md,
"2.4.0 parent brief" and "2.5.0"). The relay (Option A) and Gemini Nano are still open. 2.6.0 (2026-10-21, branch
`release-2.6`, versionCode 15): a pop song's run is a SongRun under `pop-<id>` with the song's title in every brief, the
placement check's first-lesson reward logs only its sticker drop, no lesson (its id has a title all the same), skipped
lessons log nothing, and
a piano-level change is a `level` setting event the writers do not word yet.

## What it is

A report for the grown-ups about what each child is doing in the app, written warmly, with facts, an
explanation, recommendations, and buttons that act on them. The child gets their own short, positive version from
the pup. Roy and other grown-up learners get the same thing about themselves ("My week").

## Decisions

- **Readers:** the parent, in the parent corner of the shared phone (behind the gate). Later, with sign-in, on the
  parent's own phone. "Show Mum" on the child's finish screen opens the session note for a parent standing there.
- **Cadence, all three:** a session note (right after a session), a daily view, and a weekly brief with a
  notification. The weekly day and time are chosen at setup and changeable in the parent corner.
- **Depth, both:** a one-minute read by default (headline, three facts, two or three recommendations, one thing to
  praise); "See the full report" expands to sections with statistics (seven-day strip, every lesson with stars and
  tries, skills over the last four weeks as small trend lines, the toy mix, time of day, noisy-room events, hints
  used).
- **Family view:** avatars across the top as tabs, one child at a time. Never a comparison between children.
- **Tone:** warm, kind, optimistic, coach-like, factual. Compare a child only with their own past weeks. Every
  "hard" is paired with why it is normal and what to do next. Numbers are present, never the headline.
- **No off-app advice** in version one (no teachers, benches, books).
- **The child's brief:** first person from the pup, positives and one "next time let's try", spoken aloud and shown
  as a card at the end of a session. The child never sees the parent's version.
- **Actions, all seven in version one**, each behind the gate, reversible, and recorded in the next brief:
  Set as next (pins a lesson as the next stone for a day), Replay for stars, Practise together (Duet with the parent
  on the left hand), Fresh start / restart a page (two confirmations), Pace (focus mode hides later pages; or unlock
  the next page early), settings suggestions as one tap (reader mode, bedtime, colour notes, mic a little easier),
  Teacher mode (assign a song to the child's shelf with a note).
- **Refresh** rewrites the current brief. **Ask the brief:** the parent types or dictates a question (Android's
  on-device speech recognition; the voice stays on the phone) and gets an answer from the same findings.
- **Export:** any brief as an image or PDF through the share sheet.

## How the words are written

One insights engine, three writers, chosen automatically:

1. **AI (online):** the structured findings go to a model and come back in a fixed structure the app renders.
   Version one: OpenRouter, model `anthropic/claude-haiku-5.5`, the key baked into the private build at build time
   from a gitignored file on Roy's PC (never in git, never in chat, never in logs), protected by a spending cap set
   on the key at OpenRouter ("Option B"). Before sharing beyond people Roy knows: move the key to a relay Roy owns
   (a Cloudflare Worker with per-install quotas, "Option A"); the app only changes where it points. Phones with
   Gemini Nano built in may use it first, later.
2. **Templates (offline, slow, failed, over quota):** the built-in writer produces the same structure from the same
   findings, instantly, with a small "written on the phone" note.
3. **The child's writer:** the pup's version, same findings, different brief.

Session notes and the child's card use the AI with a three-second limit, then templates. Daily and weekly use the
AI with a longer limit.

**Guardrails in the prompt and on the way back:** the model sees only the findings (numbers, lesson names, flags,
trends), never audio and never the child's name (inserted locally); it must only use the findings as facts, never
compare with other children, give no off-app advice, pair every "hard" with why it is normal and a next step, and
pick recommendations only from the action menu by id. If a reply names a lesson that is not in the findings, that
line is dropped and the template line used.

## What has to be built underneath

- **Event log**, append-only, per profile, capped, local: lesson, step, tries, let-go, hints, near/far wrong keys,
  hand, timing, duration, timestamp, toy opened, song played with stars, noisy-room event, settings changes, actions
  taken by a parent. Most signals exist today but are scattered across stores; the brief needs them in one place
  with history. Added to ProgressFiles.NAMES for backup and deletion.
- **Insights engine** (Android-free, tested): facts, trends against the child's own past weeks, flags (stuck here,
  noisy room, ready for letters, ready for the road, replaying for stars instead of moving on), candidate
  recommendations with action ids.
- **Writers:** the AI client (OpenRouter, structured output, timeouts, quota, the key from BuildConfig), the
  template bank (large, varied, tested for the copy rules), the child's writer.
- **Screens:** parent corner > Family > one child (session, today, week; short and full), "Show Mum" on the finish
  screen, the child's card, the action cards, the question box with dictation, export, the weekly notification and
  its settings; "My week" on the grown-up You tab.
- **Privacy page:** a paragraph on what the AI brief sends (findings only) and the offline fallback.

## Build outline (after 2.3.1)

1. Event log and insights engine: Opus, xhigh.
2. Writers: the prompt and the AI client: Opus, xhigh; the template bank: Sonnet, high.
3. Screens, actions, notification, export, the child's card: Sonnet, high.
4. Integration, privacy copy, emulator pass, release: Opus, xhigh.

About two days. The build-time key: `scripts/set-ai-key.ps1` reads `secrets.properties` (gitignored) and the Gradle
build exposes it as BuildConfig.AI_KEY; the release APK on the private page carries it; rotate the key when the
beta ends.

## Open

- Confirm whether Gemini Nano responds on the Fold7 (a ten-minute check during the build); it would make the brief
  free and offline on supported phones.
- The relay (Option A) before the public version.

# The parent brief by email, step 1: the engine

> **2.8 (branch `email-one`): one address for the family.** `EmailPrefs` is now stored as two records joined on load
> (`FamilyEmail.join`): the family's address, confirmation and code under `email.family`, and each child's Daily and Weekly
> switches, last sent, held reason and sends (for the caps) under the profile id. `EmailSettings.of(context, id).load(first)`
> gives the same view the schedule and the sender always read; `EmailSettings.family(context)` starts, verifies and stops the
> address; the first email (the code) goes for one child only (`FamilyEmail.lead`); `EmailSettings.migrate` moves 2.7's
> per-child addresses once (the first confirmed child's address becomes the family's, nothing is confirmed again; children
> who had no address are switched off). The per-child API below is 2.5's.

Branch `mail-engine` (Roy, 2026-10-21: design D, docs/email-design-D.py). Parents get each child's brief by email without
opening the app: a DAILY email each day the child played, once their playing is done; a WEEKLY email on the grown-up's day
and time; after the first email the grown-up picks DAILY, WEEKLY, BOTH or OFF. The grown-up enters the address in the app
and confirms it with a code the first email carries. Sent through Brevo (free plan, 300 a day) from the verified sender
"The pup at Sharp Ears". The email is read on the grown-up's own phone, where the app is usually not installed, so nothing in
it can act: every suggestion says "Do this in the app" with a plain line of where.

This step is the engine: Android-free and JVM-tested, except `data/EmailSettings.kt` (a thin SharedPreferences store, also
tested on the JVM). No screen changed, nothing is scheduled yet, nothing was sent anywhere: no test reaches Brevo, and this
PC has no key. 1241 JVM tests (1187 before, 54 new), all green.

## Files

| File | What |
|---|---|
| `EmailSchedule.kt` | `EmailFrequency`, `EmailKind`, `PlaySpan`, `EmailPlan`, `EmailDue` (Send or Wait), `EmailCaps`, `EmailSchedule.decide` and `spans` |
| `EmailBrief.kt` | the model of one email (`EmailBrief` and its parts), `EmailBriefs.build`, `EmailExtras`, `PathProgress` |
| `EmailWords.kt` | the email's own words: subjects, preheader, `whyFor`, the line to say at home, the welcome, the footer |
| `EmailSections.kt` | the five tap-to-open detail sections |
| `PlainTextEmail.kt` | `EmailRenderer` (what step 2 implements), the text part, a plain HTML fallback |
| `EmailVerification.kt` | `EmailPrefs` (one child's settings), `EmailVerification` (start, verify) |
| `BrevoTransport.kt` | `EmailMessage`, `EmailTransport`, `EmailSendException`, `EmailScrub`, `BrevoTransport` |
| `EmailSender.kt` | refuse, write, attach, build, render, send, record |
| `data/EmailSettings.kt` | each child's `EmailPrefs` in `email.xml`, a key per child |
| tests `engine/brief/email/*`, `data/EmailSettingsTest` | schedule (both clock changes), model (400 random emails), verification, transport (local fake server), sender |

The extra data Roy asked for is in the event log and the findings (engine/brief/CONTRACT.md, "Added for the email brief"):
each run's slips by key (every pair, up to 24), notes never played, hesitation (mean, per key, per hand), each hand's clean
notes, a Perform run's tempo spread, hint counts, claps on the drum's beats; Stats for each; facts SKIPPED_NOTES, HESITATION,
TEMPO_SPREAD; trends HESITATION_MS, TEMPO_SPREAD; flags HESITATES_ON and LEFT_HAND_LAGS; praise STEADIER_TEMPO and
FINDS_KEYS_FASTER. The in-app brief, the templates and the AI prompt (Prompts.VERSION 2) know them too.

## What step 2 implements against `EmailBrief`

### The HTML renderer

```kotlin
val designed = EmailRenderer { email -> html(email) to PlainTextEmail.text(email) }   // the text part: always PlainTextEmail's
```

Draw design D from the model, top to bottom; every string is ready to show (sentence case, numbers in, no dashes, names
filled in, titles plain). Escape every value (a name or an imported song's title can hold `<` or `&`).

| Model | Design D |
|---|---|
| `preheader` | the hidden first line (`display:none`) a mail app shows after the subject |
| `welcome` (first email only) | a card above the postcard: `title`, `paragraphs`, the `code` big and spaced, `codeLine`, then `howOften` (`title`, `where`, the four `choices` as a list, `note`) |
| `postcard` | the dashed card: the pup (`avatar` is Profile.avatar, the sweatband colour index; `avatarColour` its name for alt text; `pose`), the tilted `stamp` ("WEEK 43", "WED 21 OCT"), `range`, `headline` big, `chips` as pills, `paws` (seven, oldest first: `practised` a gold paw, `goal` a ring, `today` the last), `pawCaption` small |
| `oneMinute` | "In one minute": each `Part` a paragraph with its bold `label`; `sayThis` in the speech bubble beside the pup (`pose` STICKER) |
| `next` (null: leave the card out) | "What I'd do next": `intro` small, then each `NextItem` numbered: bold `text`, a small "Why:" `why`; when it has a `button`, a button-looking block that does not link anywhere, saying `button` ("Do this in the app") with `where` under it. An item without a `button` is something to keep doing (nothing to tap) |
| `details` | "The details, if you want them": `intro`, then each `DetailSection` as `<details>` (`open` on the first) with `title` as the summary |
| `Block.Text` | a paragraph; bold `label` first when there is one; `small` for a caption or footnote |
| `Block.Rows` | the lessons table: `title` (with `subtitle` small under it), `value` right-aligned, `stars` as three stars (null: none filled, "not finished yet" says it), `highlight` the hard one (design D's tinted row) |
| `Block.Keys` | the key strip: one cell per key from a C to a C (`black` keys narrower, or drawn over the white ones), coloured by `heat` 0 to 3; `near` and `far` say which colour (design D: yellow for near, orange for far); `caption` under it |
| `Block.Tiles` | the stat tiles: big `value`, small `label` |
| `Block.Bars` | the four-week bars (weeks only), heights from `share`, `caption` above, `values` under |
| `Block.Meter` | one bar split by `percent` (the mix); its words follow as a `Text` |
| `signOff` | the gold star card: the pup (`pose` CROWN), `title`, `text`, `signature` |
| `footer` | small lines under everything |

Mail apps: Gmail and Outlook ignore `<details>` and show every section open, which is fine (the PDF is attached for the rest).
Use inline styles and table layout; no web fonts are guaranteed (fall back to Arial). The pup pictures cannot be `data:`
URIs (Gmail drops them) and the email API has no inline attachments: host the PNGs (art/pup_*.png) at an HTTPS address Roy
controls and use plain `<img src>`, or leave the pictures out in version one. Keep the HTML under 100 KB (Gmail clips
longer messages).

### The screens

- **Email, per child** ("grown-ups corner > Family > Mia > Email": the words the email uses, in `EmailWords.where`,
  `EmailWords.welcome` and `EmailWords.footer`; make that path real, for example a "Family" row in the kids' corner
  (`KidHome.GrownUpsSheet`) opening Settings > Family, with an "Email" button next to "Read the brief" on each kid; or change
  those three words in one place). Behind the parent gate.
  - No address: a field and "Send the code" -> `EmailSettings.of(context, id).start(address)`; `BAD_ADDRESS` says "That does
    not look like an email address". On `SENDING`, run the email job at once (below): the first email goes now.
  - Pending (`prefs.pending`): "We sent a code to the address. Type it here." Six digits ->
    `verify(code)`: `OK` shows the frequency choice; `WRONG` "Not that one, N tries left" (`EmailVerification().triesLeft`);
    `LOCKED` / `EXPIRED` "Ask for a new code" (start again); "Send a new code" starts again (the caps: two emails a day).
  - Verified: the address (shown here only, never logged), the frequency as four choices (Daily, Weekly, Both, Off) ->
    `choose(f)`; the weekly day and time are the weekly notification's (`BriefStore.weekly`, the existing WeeklyTime sheet);
    "Stop these emails" -> `stop()`; a line "Last sent ..." from `lastDaily` / `lastWeekly`.
  - `chosen` false after verifying: ask for the frequency right away (until then it is WEEKLY).
- **Privacy page** and the onboarding line: below.

## `EmailSettings`

```kotlin
val s = EmailSettings.of(context, profile.id)       // per CHILD; one phone-wide file email.xml, a key per child
s.load(): EmailPrefs                                // address, verified, code, codeExpiresAt, wrongTries, firstSent, frequency, chosen, lastDaily, lastWeekly, sends
s.start(address): EmailVerification.Started        // SENDING (a fresh code, the first email pending) or BAD_ADDRESS
s.verify(code): EmailVerification.Verified         // OK, WRONG, LOCKED (third wrong try: code gone), EXPIRED (24 h), NO_CODE, ALREADY
s.choose(EmailFrequency.BOTH)                      // DAILY, WEEKLY, BOTH, OFF
s.sent(kind, day, at)                              // EmailSender's Outcome.Sent carries the recorded prefs: s.save(outcome.prefs)
s.stop()                                           // the address and code gone (sends kept for the caps)
EmailSettings.forget(context, id)                  // already called by Profiles.delete
prefs.plan(weekly): EmailPlan                       // for EmailSchedule.decide
```

- `email.xml` is in `ProgressFiles.NAMES` and `ProfileFiles.SHARED`: the grown-up's own progress export carries it and an
  import puts it back; Android's cloud backup and device transfer leave it out (res/xml/backup_rules.xml,
  data_extraction_rules.xml, BackupRulesTest).
- `EmailPrefs.toString()` hides the address and the code. Never log `prefs.address`.

## Scheduling the sends (the integrator)

Use the existing alarm pattern (reminder/BriefReminder.kt: one `setAndAllowWhileIdle` alarm, a receiver, `goAsync` and a
thread), not WorkManager (no new dependency). One alarm for the whole family (request code e.g. 3000), set to the earliest
`Wait.atMs` over every child with an address. Run the job:

- when the alarm fires; at app start, boot, a time-zone or clock change (BriefReceiver's actions);
- when a session ends (`MainActivity.onStop` and a switch of player: set the alarm 31 minutes ahead, the daily's quiet half
  hour plus a minute);
- right after `start(address)` (the first email), `verify`, `choose` and a weekly slot change.

```kotlin
// Off the main thread. For each kid with an address (EmailSettings.of(app, id).load().address != null):
val store = EmailSettings.of(app, profile.id)
var prefs = store.load()
val weekly = BriefStore(app, profile.id).weeklyOrDefault(profile.isKid)
val now = System.currentTimeMillis()
val open = Briefs.sessionSoFar(profile.id, at = now)
val spans = EmailSchedule.spans(Briefs.read(profile.id) + listOfNotNull(open), now, openNow = open != null)
var wake: Long? = null
for (round in 1..3) {                                              // at most two go a day; three rounds is plenty
    val due = EmailSchedule.decide(now, ZoneId.systemDefault(), prefs.plan(weekly), spans)
    if (due is EmailDue.Wait) { wake = due.atMs; break }
    due as EmailDue.Send
    val findings = BriefLoader.findings(app, profile, due.period, now = due.findingsAt)
    val out = sender.send(due, prefs, findings, BriefAi.writers(app, profile).chain, child(profile, weekly))
    if (out is EmailSender.Outcome.Sent) { store.save(out.prefs); prefs = out.prefs; continue }
    // Refused (no key, metered, capped...): wait for the next trigger. Failed (offline, Brevo down): try again later.
    wake = if (out is EmailSender.Outcome.Failed) now + 15 * 60_000L else null
    break
}
// Set the family alarm at the earliest wake over the children (at least a minute ahead); cancel it when none.
```

Notes for the job:
- `findings` at `due.findingsAt`: a day already over (an email held overnight by the quiet hours) is read to its last
  moment, a weekly at its slot. `BriefLoader.findings` takes `now`, so this needs no change.
- The weekly email and the weekly notification share the slot; they can both go (the notification on the phone, the email to
  the grown-up). The writer chain caches by period, so the email's weekly brief and the notification's are the same words,
  written once (the AI's 40 a week per child counts each fresh write).
- `EmailSender.Child(profile.id, profile.name, profile.avatar, titles, weekly, extras, summary)`: titles as the brief's
  (`BriefTitles` over `CourseTitles.lookup { songs[it] }`), `summary = { id -> COURSE.flatMap { it.lessons }.firstOrNull { it.id == id }?.summary }`,
  `extras = EmailExtras(path = PathProgress(page, done, total, next) from KidStore, bedtimeOn = BedtimeStore(app, id).bedtime)`.
  `phoneName` stays null unless Roy wants "from Roy's phone" in the footer.
- The PDF: `pdf = { findings, brief -> ExportDoc.of(brief, findings, profile.name, titles, full = true, now)` drawn with
  `BriefExport(app).pdf(blocks, tempFile)`, read as bytes, the file deleted. It uses an Android Canvas, so it lives in
  ui/brief or data/; null (or a throw) sends without the attachment and the footer stops promising one.
- Call it all off the main thread: a weekly brief written by the AI can take 24 s.

## BuildConfig wiring (a `data/BriefEmail.kt`, like `data/BriefAi.kt`)

The fields exist (app/build.gradle.kts, from secrets.properties): `EMAIL_KEY`, `EMAIL_FROM`, `EMAIL_FROM_NAME` (default
"The pup at Sharp Ears"), `EMAIL_BASE_URL` (default https://api.brevo.com/v3). Only this object should read `EMAIL_KEY`.

```kotlin
object BriefEmail {
    val hasKey: Boolean get() = BuildConfig.EMAIL_KEY.isNotBlank() && BuildConfig.EMAIL_FROM.isNotBlank()
    fun sender(context: Context): EmailSender {
        val app = context.applicationContext
        val allowed = { Network.unmetered(app) || BriefAi.mobileData(app) }          // or its own "Send emails on mobile data"
        return EmailSender(
            transport = BrevoTransport(BuildConfig.EMAIL_BASE_URL, keyProvider = { BuildConfig.EMAIL_KEY },  // read at call time
                senderName = BuildConfig.EMAIL_FROM_NAME, senderEmail = BuildConfig.EMAIL_FROM, allowed = allowed, log = { Log.d(TAG, it) }),
            hasKey = { hasKey }, networkAllowed = allowed,
            pdf = { f, b -> /* the full report's PDF bytes, see above */ null },
            render = designedRenderer,                                               // step 2's; PlainTextEmail.renderer until then
            log = { Log.d(TAG, it) },
        )
    }
}
```

- `scripts/set-ai-key.ps1` writes `AI_KEY`; give it an email twin (or a `-Name EMAIL_KEY` switch) so the Brevo key never
  passes through chat. `EMAIL_FROM` is the sender address verified in Brevo (Senders, domains and dedicated IPs).
- Logs (tag SharpEars, "email: ..."): the kind, the day, the profile id, sizes, the writer, a failure's kind and its
  scrubbed message. Never the key, the address, the child's name, the subject or the body; `EmailScrub` and the tests
  check the transport, and `EmailSender` builds its lines from ids only.

## Privacy page (draft paragraph)

For Settings > About > Privacy (`ui/AboutScreens.kt`), after the AI brief's paragraph. The onboarding line ("Everything
stays on this phone...") needs ", except the brief by email if you turn it on" on a build with an email key.

> **The brief by email.** If you give an email address for a child (grown-ups corner, Family, the child, Email), the app
> sends that child's brief to it: the brief's words and numbers (minutes, days, stars, which lessons and songs, slips, how
> long keys took to find, what the app suggests) and the full report as a PDF. It goes from this phone to Brevo
> (brevo.com), the email service that delivers it, and from there to your address. Nothing else is sent: no recording or
> sound, no note you typed, nothing about the other children. The child's first name is in the email, because it is
> written for you. The first email carries a code; until it is typed in the app, nothing else is sent. You choose daily,
> weekly, both or off, nothing goes between 9:30 pm and 7 am, and never more than two a day. The address stays on this
> phone, in the app's own storage; it is not in Android's backup, and it is deleted with the child's profile or when you
> stop the emails. Brevo keeps delivery records under its own privacy policy.

For the public app (docs/privacy-policy.md, the data safety form): "personal info: email address, for app functionality,
shared with a service provider (Brevo) to deliver the email"; and the brief's contents as "app activity".

## Manual check on Roy's phone (once the keys are in)

1. At brevo.com: verify the sender (Senders, domains and dedicated IPs; the address goes in `EMAIL_FROM`), make an API key
   (SMTP and API, API keys), and turn on two-factor sign-in for the account. The free plan sends 300 a day.
2. Put `EMAIL_KEY=xkeysib-...` and `EMAIL_FROM=...` in `secrets.properties` at the repo root (never in chat, never
   committed), build and install as usual, the phone on Wi-Fi.
3. Watch the log: `adb logcat -s SharpEars:D | Select-String "email:"`.
4. As a grown-up: the child's Email page, Roy's own address, "Send the code". Within a minute: an email "Mia's piano
   brief: your code is NNNNNN" with the welcome, the code, the four choices and the day's brief (or the last day played).
   The log: `email: FIRST ... sent`. Type a wrong code once (one try fewer), then the right one; choose Both.
5. Let Mia play a lesson; leave the app. About 30 minutes later (the alarm): the daily email. The log: `email: DAILY ...
   sent`. Open it on another phone where the app is not installed: the postcard, In one minute, What I'd do next with "Do
   this in the app" and where, the five sections (Gmail shows them open), the gold star, the PDF attached and opening.
6. Play again after 20:30 the same day: no second daily (one a day). Play after 21:00: nothing until 07:00, then the
   day's email covers that day.
7. Set the weekly slot a few minutes ahead (the WeeklyTime sheet): the weekly email at that minute (and the notification).
8. Airplane mode at a due time: `email: ... failed: NETWORK`, then it goes on the next try. Mobile data only, no switch:
   `not sent: METERED`. A build without the key: `not sent: NO_KEY`, and the screen says the build cannot send email.
9. At brevo.com > Transactional > Logs: one entry per email, delivered. 401 in the log: the key is wrong; 400 "sender":
   `EMAIL_FROM` is not a verified sender; 402: no credits; 429: over the rate.
10. `logcat | Select-String "@"` over the whole walk shows no address and `Select-String "xkeysib"` nothing.

## Decisions (made without asking, as briefed)

- **The first email is the verification email.** It carries the code, so it goes as soon as the address is entered, with
  that day's findings (or the last day played within two weeks; the welcome alone if never). Roy's "right after the address
  is verified" read as "right after it is entered for verifying": the code that verifies it travels in that email.
  It ignores the quiet hours (the grown-up is waiting for the code) but not the caps.
- **Until the grown-up chooses, a verified address gets the WEEKLY email** rather than nothing; `chosen` tells the screen to
  ask.
- **A day's email** is due 30 real minutes after the last session of the day ended (a new session restarts the half hour),
  or at 20:30 if a session that started before 20:30 is still open or ends later; play that starts after 20:30 is sent 30
  minutes after it ends. Quiet hours (21:30 to 07:00) move it to 07:00; a day held overnight goes the next morning until
  noon, then it is dropped (the week covers it). One per child per day.
- **A weekly email** missed at its slot (the phone off) still goes for 48 hours; it covers the seven days ending at the slot.
- **Caps** (two a day, ten a week, Monday to Sunday) count every email, the first included, so a wrong address typed over
  and over cannot flood anyone. Both the schedule and the sender check them.
- **Retries:** a 5xx once; never a 4xx or a timeout (Brevo may have taken it, and a second try could send it twice); a 2xx is
  always sent, whatever its body.
- **Storage:** one phone-wide file with a key per child, so Android's backup can exclude it by exact name (per-profile files
  "email.p3.xml" cannot be excluded: the rules take no wildcards).
- **Hesitation** is measured in wait mode only (in Perform the song does not wait; the offset is timing), from the group
  coming due on the run's own clock (pauses do not count), the mic's detection delay taken off, one wait at most 20 s.
- **Hand balance:** a note is clean when it was played with no wrong key charged to it (the nearest note wanted) and not
  let go; in Perform, when its group was played in time (wrong keys cost nothing there, as in the score).
- **Tempo steadiness** is the standard deviation of a Perform run's offsets from the beat (four graded notes or more), so a
  run that is steadily a little late is steady; the lean is TIMING.
- **Skipped notes** count notes let go by the Playground, missed in Perform, or left out of a chord of four or more by the
  mic's slack; notes never reached (a run left early) do not count.
- **No PlayerScreen change:** the transport feeds the tally the notes itself; the Playground's let-go already calls
  `RunTally.letGo`. The drum stage reports its beats as `rounds` (its stars are unchanged: no round is let go).
- **Wrong-key pairs** are still one WrongKey line each, now every pair up to 24 a run (was five).
- **STEADIER_TEMPO** is a praise (with a TEMPO_SPREAD trend), not a flag: it is good news.
- **`whyFor`** reasons are written from CONTRACT.md's "why it is normal", one per flag (two where the action changes it),
  and one per action for a candidate that answers no flag.
- **No pronouns** about the child in the email's own words (the name, or none), as in the brief.
- **Subjects** keep the brief's headline when it fits in 78 characters and has no dash; otherwise "Mia's week at the
  piano, from the pup" and the like. The first email's subject carries the code, so it can be read from the notification.

## Not done here

- The HTML renderer, the screens, the job and its alarm, `BriefEmail`, the PDF provider, the privacy and onboarding copy,
  the key script's email twin (step 2 and the integrator).
- Nothing ran on a device and nothing reached Brevo. The real words, the 30-minute trigger on the phone, the rendering in
  Gmail and Outlook and Brevo's sender verification wait on Roy's manual check above.
- The extra run data is logged from this version on: older logs have no detail, so the keys, hesitation and hands lines
  fill in as new runs come.

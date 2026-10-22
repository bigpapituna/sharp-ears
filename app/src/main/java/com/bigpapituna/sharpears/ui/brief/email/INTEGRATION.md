# The parent brief by email, step 2: the email, the screens, the job

> **2.8 (branch `email-one`): one email place.** The address is the parent's, not the child's: it is entered and confirmed
> once in **Settings > Briefs and email** (`BriefsAndEmail.kt`, which replaced the per-child `EmailSettingsScreen.kt`), with
> one row per child under it (Daily and Weekly switches, the weekly slot, Preview), then who writes the brief. Family's
> "Email the brief" is gone; the parent brief's Email button opens the same composable as a dialog (`EmailDialog(kids)`);
> a row's Preview opens `EmailPreviewDialog`, which now has "Send me this email now" once the address is confirmed. The
> emails point at "Settings > Briefs and email". Storage: engine/brief/email/FamilyEmail.kt and data/EmailSettings.kt
> (`email.family` beside the children's keys; 2.7's addresses move over once). The sections below describe 2.5's per-child
> pages and are kept for the history.

Branch `mail-ui` (Roy, 2026-10-21; step 1 is engine/brief/email/INTEGRATION.md). Design D as real email HTML, the settings
screens behind the parent gate, the send job on one alarm, and the wiring objects. Nothing was sent anywhere: this PC has no
Brevo key (`BuildConfig.EMAIL_KEY` empty), no test reaches Brevo, and the emulator's sends are refused with a kind message.
Roy's phone was never targeted. Version not bumped.

## What is wired

| File | What |
|---|---|
| `engine/brief/email/EmailHtml.kt` | `EmailHtml.render(email, options)`: design D as table-based, inline-styled HTML; `renderer(options)` for `EmailSender` (HTML plus `PlainTextEmail`'s text and a stop line); `deepLink`, `stopLink`, `withoutDarkMode` |
| `engine/brief/email/EmailImages.kt` | where the pup pictures come from: `hosted(baseUrl)` (https), `inline(read)` (`data:` PNGs), `NONE` |
| `engine/brief/email/EmailLinks.kt` | reads `sharpears://brief/<profile id>/<day or week>` back |
| `engine/brief/email/EmailRound.kt` | the job's decisions, pure: send what is due per child (3 rounds, the caps), retry/wake rules, the family's one alarm (`alarmAt`), the 07:00 and 20:30 checkpoints |
| `engine/brief/email/EmailFlow.kt` | the settings screen's logic and words: steps, resend wait, code replies, the four choices, last-sent lines, every refusal in kind words |
| `engine/brief/email/EmailVerification.kt` | `EmailPrefs` gained `lastDailyAt`, `lastWeeklyAt`, `held` (and `counted(at)` for a test email); old files decode with defaults |
| `engine/brief/email/EmailBrief.kt`, `EmailSender.kt` | `EmailBrief.profileId` (for the deep link), passed through from `EmailSender.Child` |
| `data/BriefEmail.kt` | like `BriefAi`: `hasKey`, `sender(context, profile)` (Brevo from BuildConfig, designed renderer, full-report PDF as the attachment), `child(...)`, `images`, the phone-wide "mobile data" switch, `preview(...)` |
| `reminder/EmailJob.kt` | `EmailJob.run / plan / afterSession / sendTest`, `EmailReceiver` (alarm, boot, time zone, clock) |
| `data/Briefs.kt` | `Briefs.onSessionEnd` hook, set by MainActivity to `EmailJob.afterSession` |
| `ui/brief/email/EmailSettingsScreen.kt` | the page per child; `EmailPreview.kt` (`EmailDialog`, `EmailPreviewScreen`); `EmailDemo.kt` (debug route) |
| `ui/family/FamilyDialog.kt`, `FamilySettings.kt`, `KidHome.kt`, `ParentBriefScreen.kt` | the entries (below) |
| `AndroidManifest.xml` | `sharpears://brief` intent filter on MainActivity; `EmailReceiver` (BOOT_COMPLETED, TIMEZONE_CHANGED, TIME_SET) |
| `app/build.gradle.kts` | `EMAIL_IMAGE_BASE` (blank by default) next to the existing EMAIL_* fields |
| `scripts/cut_email_pups.py`, `app/src/main/assets/email/*.png` | the 27 small PNGs (200 px wide, 96 colours, 13 to 15 KB each) |
| `scripts/set-email-key.ps1` | writes `EMAIL_KEY`, `EMAIL_FROM`, `EMAIL_FROM_NAME` (and optionally `EMAIL_IMAGE_BASE`) into `secrets.properties` without showing the key; `-Remove` takes them out |
| `ui/AboutScreens.kt`, `ui/onboarding/OnboardingScreen.kt` | the privacy paragraph ("The brief by email", only on a build with a key) and the onboarding line |

### The path the email tells parents to follow is real

"Grown-ups corner > Family > Mia > Email":

- the kids' grown-ups corner (whistle, behind the gate) has a **Family** row (`GrownUpCorner.onFamily`) opening `FamilyDialog`:
  Settings' Family section on its own; tap **Change** on a kid and there are **Read the brief** and **Email the brief**;
- Settings > Family has the same buttons;
- the parent brief's own screen has an **Email** button for the child on show.

All three open `EmailDialog` (settings, with a preview a tap away) behind `Gated`.

### The deep link

`sharpears://brief/<profile id>/<day|week>` opens that child's brief behind the parent gate (the same code as the weekly
notification: `MainActivity.briefFrom`). Checked on the emulator: the link on a cold app, the gate, then Mia's week.
A link without a child opens the app only; a child that is not on the phone is dropped with a log line.

## The renderer

- 600 px column, every style inline, a table for every layout; the one `<style>` draws the `<details>` toggles and the dark
  mode (`prefers-color-scheme`), nothing else. Nunito first via a Google Fonts `<link>`, then Trebuchet MS and Arial.
- Palette is the mockup's, with the button, stamp and stat text a shade deeper (#B4531F, blue #3F6FB8) so white on them
  passes 4.5:1.
- Postcard (stamp, range, headline, chips, the seven-cell paw strip with a ring for a goal day), In one minute with the pup
  and the line to say, What I'd do next (a "Do this in the app" button per action: a bulletproof table button linking to the
  deep link, the where-to-tap line under it), five `<details>` (the first open, a note that Gmail shows them open), the
  slip heatmap (one cell per key C to C, black keys narrower and shorter, yellow near and orange far, darker for more, the
  count under it), tiles, the four-week bars (proportional heights), the mix as one split bar, the gold star, the footer.
- The first email puts the welcome above the postcard with the code in 42 px digits.
- Every string is escaped; the only links are https (fonts, hosted pictures), `sharpears://` and one `mailto:`.
- The footer ends with "Send a note to stop the emails for Mia": `mailto:EMAIL_FROM?subject=Stop the emails for Mia`. **This is
  manual for now**: it lands in the sender mailbox and Roy switches the child off (or the grown-up uses the app).

### Size, pictures and the snapshots

| Email | hosted pictures | inline pictures (what a build with no `EMAIL_IMAGE_BASE` sends) |
|---|---|---|
| day | 23.7 KB | 80 KB |
| week | 36.0 KB | 93 KB |
| first email, a day played | 27.4 KB | 84 KB |
| first email, never played | 14.3 KB | 53 KB |

A test fails above 400 KB (and above 60 KB hosted); an email inlines only the three pictures it uses (the child's pup in
their sweatband colour, the sticker pup, the crown), never all 27. Snapshots (word for word): `app/src/test/resources/email/email-day.html`,
`email-week.html`, `email-first.html`, `email-first-never-played.html`. When a design change is meant, run
`.\gradlew.bat :app:testDebugUnitTest --tests "*EmailHtmlTest*"`, open the new files in `app/build/email-snapshots/`, copy them over the
snapshots. `app/build/email-samples/designed/email-*.html` (inline pictures) are what Roy opens in a browser.

## The job

**Alarm, not WorkManager**: the repo's reminders (`Reminder`, `BriefReminder`) are `setAndAllowWhileIdle` alarms with a
receiver and `goAsync`; WorkManager is not a dependency and CLAUDE.md says platform APIs first. One alarm for the family
(request code 70001, clear of the per-child weekly codes 1000 to 66535).

Runs: the alarm; app start; boot, time zone and clock change (`EmailReceiver`); every session end (`Briefs.onSessionEnd`:
**plans only**, it sets the alarm for the half hour after and sends nothing as the app leaves the screen, so the receiver, which
has a proper lifetime, does the sending); 07:00 and 20:30 (always a candidate wake while any child is active); after the grown-up
enters an address, a code, a choice, a weekly slot, the mobile-data switch or stops (the screen calls `EmailJob.run` on the IO
dispatcher). Sending never touches the main thread; one run at a time (a lock), each send recorded on the freshest settings, so a
frequency changed mid-send is kept. Before sending it parks a fallback alarm 15 minutes ahead, so a process killed mid-send retries.

Retries: a failure (offline, Brevo down) in 15 minutes; a metered network in 30 minutes; a brief not written in 15 minutes; no
key, off, unverified: not at all (a change wakes it). A refusal is saved as `EmailPrefs.held` and the screen says it.

Only kid profiles are looked at. A kid switched to grown-up mode keeps their settings but sends nothing.

## The screens (emulator-5558 Fold, `app/build/screens/mail2-*.png`, not committed)

`--es screen email --es seed none|waiting|code|gone|unchosen|verified|daily|held [--es view preview --es period day|week --ez samples true --es theme light|dark --es child <id>]`
(debug builds; the seeded address is parent@example.com).

| Screenshot | State |
|---|---|
| `01-no-address` | the field, "Send the first email", the no-key note |
| `02-bad-address` | "That does not look like an email address." |
| `03-first-refused-nokey` | the real refusal path: "Email is not set up on this build." |
| `04-code-entry` | "Check your inbox, then enter the code", six digits, "Send a new code, in 7 minutes" |
| `05-code-gone` | "Ask for a new code" |
| `06-verified-choose` | verified, "Choose how often" before a choice |
| `07-verified` | the four big choices, the weekly row, last sent lines |
| `08-held-wifi*` | "Waiting for Wi-Fi", and the test email, the mobile-data switch |
| `09-test-refused` | "Send me a test email now" on this keyless build: "Email is not set up on this build." |
| `10-preview-week`, `11-preview-day` | the preview (WebView, no script, no network, links swallowed) |
| `12-deeplink-gate`, `12c-deeplink-brief` | the deep link: the gate, then Mia's week |
| `13c-corner`, `14-family`, `14b-family-mia` | the Family row, Family, "Email the brief" |

## Manual check on Roy's phone with the real keys

1. Brevo: verify the sender address; make an API key; two-factor sign-in on (engine INTEGRATION.md, "Manual check").
2. `.\scripts\set-email-key.ps1` (the key at the hidden prompt, then the sender address and name); optionally
   `-ImageBase https://.../email` after uploading `app/src/main/assets/email/*.png` there. Then `.\gradlew.bat installDebug`, phone on Wi-Fi.
   Watch: `adb logcat -s SharpEars:D | Select-String "email:"`.
3. Mia > Email the brief: **Roy's own address**, "Send the first email". Within a minute: the first email with the code in large
   digits. Screen says "Check your inbox, then enter the code." Type one wrong code (one try fewer), then the right one: the four choices.
   Choose **Both**. "Send me a test email now": a second email, "Sent. Check your inbox."
4. **Open the test email in Gmail (the app and web) and in Samsung Email**, in light and dark, and check:
   - the postcard: stamp, chips, seven paws, the headline; the pup (see "Pictures" below);
   - In one minute with the pup's line; What I'd do next: each button and its where-to-tap line;
   - the five sections (Gmail shows them open, Samsung Email may fold them); the slip keys, the bars, the split bar;
   - the gold star, the footer, the stop line; the PDF attached and opening;
   - **tap a "Do this in the app" button** with the app installed: it should open Mia's brief behind the gate. Gmail may refuse
     a non-web link; if so the where-to-tap line is the fallback, and the answer decides whether to host an https landing page that redirects.
   - the message is not "clipped" by Gmail (the 102 KB rule): inline pictures put the week at about 93 KB, close to it.
5. Play a lesson, leave the app: about 30 minutes later the daily (`email: DAILY ... sent`). Play after 20:30: nothing more that
   day; after 21:30 nothing until 07:00. Change the weekly slot a few minutes ahead: the weekly email arrives then.
6. Airplane mode at a due time: `email: ... failed: NETWORK`, then it goes on the next try. Mobile data only, switch off:
   "Waiting for Wi-Fi."; switch on: it goes. Reboot the phone: the alarm is set again (`email: job (boot_completed)`).
7. "Stop these emails": the address is gone; `logcat | Select-String "@"` over the walk shows no address and `Select-String "xkeysib"` nothing.

## Left, and what to know

- **Pictures.** Gmail (and some other apps) do not show `data:` pictures: with no `EMAIL_IMAGE_BASE` the alt text shows instead
  ("The blue pup dancing"). Hosting the pictures at an https address and setting `EMAIL_IMAGE_BASE` fixes it and brings the week
  to 36 KB. A hosted picture is fetched when the email is opened (the host sees that), and so is the Google font: the privacy
  paragraph says so and, on a build with a base, names the host (`EmailImages.hostOf`).
  2.5.0 (integration): the 27 files are ready to upload in `hosting/email/` at the repo root, with `hosting/README.md` (GitHub
  Pages recommended; `.\scripts\set-email-key.ps1 -ImageBase https://.../email -PicturesOnly` sets the base without the key).
  `EmailHtmlTest.hostedPicturesAreTheBaseThenTheFileName` checks every `src` is `<base>/<file>.png` exactly and that
  hosting/email matches the app's assets byte for byte.
- Nothing ran against Brevo or on a real mail client: the HTML is checked in headless Chrome (light and dark, full width) and by the
  tests, not in Gmail, Outlook or Samsung Email. Outlook desktop ignores rounded corners and shows plain rectangles.
- The unsubscribe is a `mailto:`, handled by hand. A list-unsubscribe header would need the transport to send headers (Brevo
  supports it); not done.
- The in-app preview shows the phone's own words and never asks the AI. The test email goes through the real chain (the AI's
  quota counts a fresh write, the cache serves a repeat).
- The export's PDF footer says "Nothing leaves it unless you send it": true to the app, now odd in an email's attachment. Engine copy, left.
- A first email with no key waits and the session-end plan wakes the receiver once per session until a key exists (a log line each
  time, nothing sent). Harmless; a "no key: never plan" shortcut would need the job to know `hasKey`.
- The "Weekly brief" row reuses the notification's sheet, so its "Send a notification" switch shows there; the email's weekly slot ignores that switch.
- Dark mode in the preview follows the app's theme; real mail apps decide for themselves.

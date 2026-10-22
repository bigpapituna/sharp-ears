# Privacy policy (draft for the public app)

> Draft for the Google Play version. Fill in the bracketed items, have it read over, and host it at a public URL
> (for example a page on your own website or a GitHub Pages site). The Play listing and the app's Settings > Privacy
> must both link to it. The private build's in-app Privacy page differs in one place: it can keep practice recordings.

**[App name]**, by **[your name or company]**
Last updated: **[date]**
Contact: **[email address]**

## Summary

[App name] does not collect, store on any server, sell or share any personal information. It has no account, no ads,
no analytics and no tracking. Everything it keeps stays on your device. The one exception is the parent brief, when an
AI writes it: then the brief's numbers (never a name, a recording or a typed note) go to the AI service that writes the
words, as described below, and nothing of it is kept there.

## Microphone

The app asks for microphone access so it can hear which piano notes you play. The microphone is used only while you
have it switched on in a song, lesson, chord or ear training game, or the tuner, and the app is on screen. The sound is analysed on your device
to detect notes and is then discarded. It is never recorded, saved or sent anywhere.
The private build can keep recordings, and sharing them or switching sending on is only ever started by you, from Microphone setup.

You can refuse or withdraw microphone access at any time in your device settings. The app still works with on-screen
keys or a MIDI keyboard.

## Information stored on your device

The app stores, in its private storage on your device only:

- your settings (for example key labels and sound),
- your progress: song positions, scores, completed lessons, chord practice schedule and practice minutes,
- your XP, level, achievements, daily goal and practice streak, and your answers to the first-run questions,
- ear training scores and the daily workout days, and what you practised with the scales, warm-ups and metronome,
- MIDI files you choose to import.

All of it is kept in the app's own settings files on your device. This information is not transmitted to us or to
anyone else. Uninstalling the app deletes it. You can reset progress in Settings (XP and achievements are kept until
you uninstall), and export it to a file you choose. If your device backs up apps to your Google account, Android's own
backup may include these settings files (never microphone recordings), so a new phone can restore them.

## MIDI keyboards

If you connect a MIDI keyboard, the notes it sends are processed on your device and are not stored or transmitted.

## Spoken guidance

Spoken guidance uses a voice that runs entirely on your device: lines recorded into the app, and an on-device voice
model (Kokoro) for lines that change. Nothing is sent anywhere. With the natural voice turned off in Settings, the app
uses your device's own text-to-speech engine instead; whether that engine works offline is up to your device.

## The parent brief (AI-written summaries)

The parent brief is a summary for grown-ups of each person's playing in the app: minutes, days, stars, tries, which
lessons and songs, and moments such as a noisy room or a step that was hard. It is made on your device from the app's
own records. It never uses a recording or any sound, and it compares a child only with their own earlier weeks. Grown-ups
read it behind the parent gate; a child sees only a short, positive card from the app's mascot.

On Wi-Fi (or mobile data, if you allow it), the brief for grown-ups, the child's card and the answers to your questions
can be written by an AI model. The app sends **[the relay: your own server at relay URL]**, which passes it to
OpenRouter (openrouter.ai) and on to the model's maker (Anthropic, for Claude Haiku), only the brief's findings:
minutes, days, stars, tries, which lessons, songs and pages, and notes such as "stuck on a lesson" or "noisy room". It
never sends a name (the app writes the name in afterwards, on your device), a recording or any sound, a note a grown-up
typed, or a song or lesson whose title or file name holds someone's name. A question you type or dictate is sent as text
with names taken out; dictation uses your device's own speech recognition, so your voice stays on the device. The app
asks OpenRouter to use only providers that do not keep or train on what is sent. Like any website, the relay and
OpenRouter see the request come from your internet connection.

What comes back is checked on your device, and anything that names something not in the findings is left out. Offline,
on mobile data you did not allow, when the model is slow, after 40 AI briefs in a week for one person, when a reply fails
a check, or with AI briefs switched off in Settings, Family, the brief is written on your device instead, and says so. The
last brief written for each person is kept on your device so opening it again sends nothing.

## Testing (private test builds only)

> Only in a private test build made with a PostHog key or a diary address (CLAUDE.md, "Tester analytics"). The public app
> is built without them, has no PostHog code at all, and this section is removed from its policy.

In a test build, Roy can turn on **Help Roy test**, behind the parent gate, on a phone he hands to a tester he has asked
(and, for a child, their parent). While it is on, the app records what happens on its screens and sends it, with app
events such as a lesson started or a stage that was hard, to Roy's test dashboard at PostHog (posthog.com). Every text and
picture on screen is hidden in the recording. No sound is ever recorded. Kid profiles are recorded too; the recording says
only kid or grown-up, never a name. The device goes by a random id made on it. PostHog keeps recordings for 30 days. With
**Email Roy a daily diary** on, a plain email goes to Roy once a day through Brevo: which screens, lessons, tries and notes,
with no names or addresses. Both are off until Roy turns them on, stay on that device (they are not in Android's backup),
and are removed before any public release.


Some songs offer tutorial videos from YouTube, shown in YouTube's official embedded player. When you open a video,
the player connects to YouTube and Google, which may collect information under their own policies:

- YouTube Terms of Service: https://www.youtube.com/t/terms
- Google Privacy Policy: https://policies.google.com/privacy

The app does not receive any information from YouTube about you. If you never open a video and AI briefs are off, the
app makes no network connections.

## Children

The app is intended for adults and teenagers learning the piano and is not directed at children under 13.
It does not knowingly collect information from anyone.

> **[To review before the public release:** since 2.1 the app has kid profiles, and since 2.4 the parent brief can send
> a child's practice numbers (no name) to an AI service. Both need Google Play's Families policy and the children's
> privacy rules where the app is offered checked, and this section rewritten to match.**]**

## Changes

If this policy changes, the new version will be posted at this address with a new "last updated" date.

## Contact

Questions about this policy: **[email address]**

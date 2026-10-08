# Publishing checklist (for the separate public app)

The app in this repo is Roy's private build: it may hold copyrighted music for personal use, it is a debug build that
can keep practice recordings, and it ships the CC BY-SA Moonlight file. **Do not publish this build.** The public app
will be a separate app (its own applicationId, for example `com.bigpapituna.sharpears.play`, and its own name if
wanted), built from this code with the changes below. Not legal advice: get the YouTube and music items read over.

## 1. Content to change in the public app

| Item | Private app (now) | Public app |
|---|---|---|
| Alicia slot in `assets/songs/index.json` | locked entry pointing at a personal file | remove the entry and its video |
| Clair Obscur medley and parts | on the phone only, never in the repo | nothing to do; never add them |
| Moonlight Sonata (Mutopia, CC BY-SA 2.5) | bundled with attribution | replace with a public-domain MIDI, or keep and meet share-alike (attribution in app plus the licence, offer the file under the same licence) |
| Other bundled MIDIs | Mutopia public domain, and five tunes written for the app | keep; attribution already on the Licences page |
| Tutorial videos | official YouTube embed, the app pauses, seeks and mutes it | review against the YouTube API Services Terms and Developer Policies (embedding is allowed; check that muting the player while the mic listens and auto-pausing are acceptable, keep YouTube branding visible, do not overlay the player). If in doubt, ship with "Open in YouTube" only and drop the embed |
| Practice recordings | debug builds only, switch in Microphone setup | release builds never record (already true); verify with a release build that no `fixtures` folder is created |
| Debug extras (`--es song`, `--es inject`, ...) | debug builds only | already ignored in release builds; keep it that way |
| Sheet music and arrangements from other people | not bundled | never bundle; personal imports only through the file picker |

## 2. Google Play requirements

- [ ] **Developer account**: one-time registration fee, identity verification. Personal accounts must run a closed test
      with at least 12 testers for 14 days before applying for production.
- [ ] **Privacy policy** hosted at a public URL (`docs/privacy-policy.md` is the draft), linked from the listing and from
      Settings > Privacy in the app.
- [ ] **Data safety form**: see the answers below.
- [ ] **Permissions declaration**: microphone, used for a core feature (hearing piano notes). The in-app explanation
      before the permission prompt already exists (Microphone setup).
- [ ] **Target API level**: Play's current minimum (the app targets 37, which is ahead of it).
- [ ] **Release build**: R8 on (`isMinifyEnabled = true` with keep rules checked for the YouTube player and
      SoundPool paths), then test the release build end to end.
- [ ] **Signing**: create an upload key, store it outside the repo (never commit it), enrol in Play App Signing.
- [ ] **Content rating questionnaire**: no violence, no user content, no purchases; expect a rating suitable for all ages.
- [ ] **Target audience**: adults and teens (not "children"), so Play's Families policy and COPPA do not apply.
      Keep the listing free of child-directed wording and imagery.
- [ ] **Store listing**: name, short and full description, icon (512 px), feature graphic, phone and tablet screenshots,
      category Education or Music & Audio, contact email.
- [ ] **Ads declaration**: no ads.
- [ ] **App access**: no login needed; say so.

### Data safety form answers

| Question | Answer |
|---|---|
| Does the app collect or share user data? | No |
| Is data encrypted in transit? | Not applicable: the app sends no data. (The YouTube embed is Google's own player.) |
| Can users request deletion? | Not applicable: nothing is collected; uninstalling removes on-device data |
| Audio (microphone) | Not collected: processed on the device, not stored or transmitted |
| Files (imported MIDI) | Not collected: stay on the device |
| App activity (progress, scores) | Not collected: stay on the device |

Note on the YouTube player: Google's guidance is that data collected by an SDK or embedded service on the app's behalf
counts. The embedded player is loaded only when the user opens a video; describe it in the privacy policy (done) and
check the current Play guidance on embedded web content when filling in the form.

## 3. Laws and good practice

- **Copyright**: the items in section 1.
- **Open-source licences**: the Licences page (Settings > About) shows every component with its full licence text
  (Apache 2.0, MIT for android-youtube-player, SIL OFL for Geist). Keep it current when adding a dependency.
- **Privacy laws** (PIPEDA in Canada, GDPR in the EU, CCPA in California): satisfied by collecting nothing; the privacy
  policy says so plainly.
- **Accessibility** (AODA in Ontario, the European Accessibility Act for EU sales): screen-reader labels, 48 dp touch
  targets and readable contrast are in place; recheck after UI changes.
- **Consumer protection**: only matters if the app is ever paid or has subscriptions (Google Play billing, clear
  pricing, Quebec and EU cancellation rules).
- **Trademarks**: avoid game, film or brand names in the public app's listing and bundled content.

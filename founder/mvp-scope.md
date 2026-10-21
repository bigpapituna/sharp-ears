<!-- /founder:mvp-scope · 2026-10-21 · input: a public Play Store version of Sharp Ears; features wanted: everything shipped in the private build (see facts), plus account-free use, a free tier, and a paid family plan; the private build's imported copyrighted songs must stay out. -->

# MVP scope: Sharp Ears public Play Store version

Assumption: the public app is a separate build with its own applicationId (docs/publishing.md), launching in Canada first.
Assumption: kids are a target audience. This overrides the plan in docs/publishing.md to declare "adults and teens" so that Families rules and COPPA do not apply. A kids' mode with a pup world for 4 to 10 year olds is child-directed, so I treat both as applying. Roy should confirm with a lawyer.
Assumption: no analytics, no accounts and no server stay true, since the privacy draft promises them.

## 1. Feature triage

| Feature | Category | Reasoning |
|---|---|---|
| Mic listening on a real piano | Must | The product is pointless without it. |
| Pup's Playground, kid world map, 76 kids' lessons on 7 pages | Must | The reason a child opens the app; the whole difference. |
| Family profiles (up to 8) | Must | The paid gate and the one-phone household. |
| Parent gate | Must | Guards settings and purchases from a child; Families bars manipulative purchase tactics aimed at children. |
| Account-free use | Must | No server to run, and nothing to disclose. |
| Privacy policy, Data safety form, mic explainer | Must | Store review blocks launch without them. |
| 39 public-domain songs | Must | Songs are what a child plays after the lessons. Check each licence; Moonlight is CC BY-SA, per docs/publishing.md. |
| Free tier and paid family plan (Play Billing) | Should | Beta can run free. Add billing in weeks 3 and 4. |
| 70-lesson adult course | Should | Already built; serves the second persona. Do not market it first. |
| Wrong-key colouring and hint escalation | Should | Keeps a stuck child from quitting. |
| On-device voice and 3,500 recorded lines | Should | Core to the pup; check the GPL espeak-ng data note in docs/publishing.md. |
| Stickers, rewards, living mascot | Should | The return loop. Real art can replace the placeholder art after launch. |
| Ear and rhythm games | Should | Already in the pages. |
| Imported songs (file picker) | Won't | The private build's copyrighted music must stay out; cut the feature, not only the files. |
| YouTube tutorial embed | Won't | docs/publishing.md says if in doubt ship "Open in YouTube" only; a child-facing embed adds review risk. |
| Crash and stuck-moment upload, owner reports | Won't | Stuck-moment clips can hold a child's voice. In the public app nothing is sent anywhere. |
| "Send to someone" certificate | Won't | The FTC says sharing features can turn into collection or disclosure ([FTC FAQ](https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions)). |
| Duet and chord tools | Won't | Adult-leaning; more surface to review and explain. |
| USB-C MIDI | Won't | The mic-only story is the point. |
| Cloud accounts, sync, iOS | Won't | Needs servers or a second platform. |
| Per-child parent report | Won't | Not built yet (docs/KIDS-PLAN.md). |

That is 7 Must, 6 Should, 8 Won't.

## 2. MVP definition

"A parent can install Sharp Ears, set up a child's profile, put the phone on the piano and see the child finish a first pup game and earn a sticker in under 5 minutes, with no account."

Features that make it possible: mic listening, the kid world map, profiles, the parent gate, the mic explainer and the privacy documents.

## 3. User flow

```
Step 1: Parent opens the Play listing and installs (no account)
Step 2: Parent reads the mic explainer and taps Allow (40 seconds)
Step 3: Parent names a child, picks their age and passes the parent gate
Step 4: Child plays the first pup game on the real piano (2 minutes)
Step 5: Sticker, goodnight, family wall; the parent is shown the family plan after the first page, behind the gate
```

## 4. Technical scope

**Build versus buy.** Auth: none. Payments: Google Play Billing used directly. I advise against RevenueCat for v1: it adds an SDK and a vendor that sees purchases, and Families says SDKs in apps for children must be approved for child-directed services ([Google](https://support.google.com/googleplay/android-developer/answer/9893335)). For reference, RevenueCat is free up to $2,500 a month tracked and then 1% ([pricing](https://www.revenuecat.com/pricing)). Analytics: none; use Play Console's built-in statistics. Crash data: Android vitals in Play Console.

**Stack.** Unchanged: Kotlin and Jetpack Compose, minimum Android 9 (facts). Public variant of the build: new applicationId, remove the Alicia slot, imports, embed and reports.

**Build time (Estimate, one developer working with AI agents):**

| Work | Days |
|---|---|
| Public build variant and removals | 1 |
| Free-tier gating and entitlement | 2 |
| Play Billing subscription | 3 |
| Mic explainer and parent consent flow | 1 |
| Privacy policy rewrite, Data safety, content rating | 1 |
| Release build, R8, end-to-end test | 2 |
| Listing assets and screenshots | 2 |
| **Total** | **12 days** (1+2+3+1+1+2+2) |

**Hosting.** The app needs none. The privacy policy needs a public URL. GitHub Pages is free, with a 1 GB size limit and a 100 GB a month soft bandwidth limit, but its terms bar sites that are mainly commercial transactions or SaaS ([GitHub docs](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits)). A plain policy page fits; a sales landing page may not. Other hosts: Not checked.

**Store and legal requirements.**
- Play Console registration: one-time $25 ([Google](https://support.google.com/googleplay/android-developer/answer/6112435)).
- A personal account created after 13 Nov 2023 needs a closed test of at least 12 testers for 14 days before production ([Google](https://support.google.com/googleplay/android-developer/answer/14151465)). Third-party guides say organization accounts are exempt, and Google's blog says they need a D-U-N-S number that can take up to 30 days ([Android Developers Blog](https://android-developers.googleblog.com/2023/07/boosting-trust-and-transparency-in-google-play.html)). Whether open testing needs the gate: Not found.
- Families policy: apps with children in the target audience must follow it, and it lists microphone data as sensitive ([Google](https://support.google.com/googleplay/android-developer/answer/9893335)). The privacy policy and Data safety answers must match the app. The draft in docs/privacy-policy.md says "not directed at children under 13"; rewrite it.
- COPPA (US): applies to apps directed to children under 13 that collect personal information; recordings of a child's voice count ([FTC FAQ](https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions)). The rule was amended in 2025 ([Hunton](https://www.hunton.com/privacy-and-cybersecurity-law-blog/ftc-publishes-final-coppa-rule-amendments)). An app that sends nothing off the phone is in the strongest position.
- Canada: the federal privacy commissioner treats children under 13 as generally unable to consent ([law firm summary](https://www.mltaikins.com/insights/key-privacy-considerations-for-minors-navigating-a-rapidly-evolving-landscape/)) and is drafting a Children's Privacy Code ([OPC](https://www.priv.gc.ca/en/about-the-opc/what-we-do/consultations/completed-consultations/consultation-children-code/)). Quebec requires a parent's consent under 14 ([CFIB summary](https://www.cfib-fcei.ca/en/site/qc-law-25)). On-device only is the simplest answer to all three.
- Microphone data counts as "collected" only if it leaves the device; processing on the device alone is not collection, per a [third-party restatement](https://appmetrica.yandex.com/docs/en/data-security/google-data-safety) of Google's rules. Confirm in Play Console.

## 5. What you are not building, and why

| Feature | Why it feels important | Why it can wait | Revisit when |
|---|---|---|---|
| iOS version | Artie, Simply and flowkey are on iOS | A second platform doubles testing | 100 paying subscribers |
| Cloud accounts and sync | Parents change phones | Needs servers and a privacy rewrite | 50 support requests about lost progress |
| Parent report per child | Parents want proof of practice | Not built; needs careful data handling | 5 parents ask for it unprompted |
| Pop songs and imports | Children want songs they know | Licensing and copyright | A licence offer or 1,000 installs |
| Video tutorials | Competitors have them | Review risk and drift | After Families approval |

## 6. Launch criteria

**Must work.**
- Install, mic permission and first game finish with no account.
- The microphone is only on while a game or lesson is on screen.
- The parent gate blocks purchases and settings from a child.
- No network call during a normal session. Verify in a release build.
- Release build with R8 on has no `fixtures` folder and no recording.
- Privacy policy URL is public and matches the Data safety form.
- Content rating and target audience answers are submitted.

**Can be broken or ugly.** Placeholder sticker art. Some pup poses repeat. Billing not live in the beta.

**Test with the first 10 users.** Whether the child starts a session on day 3 without a parent suggesting it.

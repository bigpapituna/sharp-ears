<!-- /founder:metrics-dashboard · 2026-10-21 · input: stage: pre-launch; current numbers: none beyond one family. -->

# Metrics dashboard: Sharp Ears

Assumption: "none beyond one family" means no counts exist. founder/facts.md records no numbers, so every current value below is blank.
Assumption: the public app keeps its promise of no analytics, so metrics come from Play Console, short parent texts and observed sessions, not from an in-app tracker (docs/privacy-policy.md).
Assumption: no raise is planned (facts), so investor metrics are skipped.

## 1. Stage assessment

**Pre-launch, validation.** One family, no store listing, no revenue. The focus is proof that parents allow the mic and children return. Tailor everything to a 15-minute weekly review and a spreadsheet.

## 2. The five metrics

**1. Opted-in closed testers, held continuously.**
- Definition: testers opted in to the closed test for the last 14 days without a gap, as Play Console counts them (Google requires 12 for new personal accounts; [Google](https://support.google.com/googleplay/android-developer/answer/14151465)).
- Current value: 0 outside the family (facts: testers are Roy and his nieces and nephews).
- Target: day 30 = 12, day 60 = 20, day 90 = 40.
- Decision it informs: when to apply for production access.
- If below: recruit through Ottawa piano teachers and pay a CAD 10 card for 14 days completed.

**2. Waitlist signups from Canadian parents.**
- Definition: email addresses collected on the landing page with a consent line, from parents of a 4 to 10 year old.
- Current value: 0.
- Target: 40, 80, 150.
- Decision: whether there is demand before spending on listing assets.
- If below: repost the printable sticker chart in r/CanadianParents and to teachers.

**3. Week-2 child return rate.**
- Definition: share of beta children whose parent answers "yes" to "Did your child start it without you suggesting it?" in the week 2 text.
- Current value: not measured. Calculate: yes answers / children texted.
- Target: 60% in each period (6 of 10), from a minimum of 10 children.
- Decision: whether the pup's world is working.
- If below: watch two recorded sessions and fix the point where the child stops, before recruiting more.

**4. Parent mic grant rate in the first session.**
- Definition: parents who tap Allow on the microphone prompt in the first session / parents observed.
- Current value: not measured.
- Target: 6 of 8 observed in the first 30 days; hold at 75% after.
- Decision: whether the mic explainer works.
- If below: rewrite the explainer and ask the parent what they thought it did.

**5. Crash-free users.**
- Definition: share of users with no crash in 28 days, from Android vitals in Play Console.
- Current value: not measured; needs enough users to show.
- Target: 98% by day 30; hold at 99% by day 90. These are Roy's targets, not a benchmark.
- Decision: whether the build is safe to widen.
- If below: fix the top crash before any posts.

## 3. Metrics to ignore

| Metric | Why it feels important | Why it misleads | Track instead |
|---|---|---|---|
| Total installs | It is the number Play shows first | Day 30 retention for education apps on Android is 2.2% per AppsFlyer via [Braze, 2026](https://www.braze.com/resources/articles/mobile-app-retention), so most installs vanish | Week-2 return |
| Reddit upvotes and followers | They show attention | They do not predict a child's second session | Waitlist signups |
| Page views | Easy to count | Visitors are not parents with a piano | Waitlist rate |
| Play star rating with under 20 ratings | A single number | Family and friends skew it | Written parent quotes |
| Subscription revenue with no cohort | It feels like proof | One buyer is not demand | Paying families after a trial |

## 4. Tracking setup

| Need | Tool | Cost | Setup time |
|---|---|---|---|
| Installs, crashes, testers, subscriptions | Play Console | $25 once ([Google](https://support.google.com/googleplay/android-developer/answer/6112435)) | 1 day |
| Parent answers and waitlist | Google Forms with Sheets | Free with a Google account ([review](https://www.jetadmin.io/blog/google-forms-review/)) | 1 hour |
| User feedback | A text message and a call | Free | 10 minutes |
| Dashboard | One Google Sheet | Free | 1 hour |

Do not add a third-party analytics SDK to the public app. The privacy policy promises none, and Google's Families policy requires SDKs in children's apps to be approved for child-directed services ([Google](https://support.google.com/googleplay/android-developer/answer/9893335)). Whether Firebase Analytics is approved for child-directed use: Not found.

**Events to track (reported, not logged by an SDK).**

| Event | When it fires | What it tells you | Source |
|---|---|---|---|
| Install | Play listing install | Reach | Play Console |
| Tester opted in | Tester joins the closed test | Progress to 12 | Play Console |
| Mic allowed or denied | Parent taps the prompt | Trust | Observed |
| Profile created | Parent names a child | Setup friction | Observed |
| First game finished | Child completes game 1 | Activation | Observed |
| Day 3 start | Child opens the app on day 3 | Early habit | Parent text |
| Week 2 start without a prompt | Child starts a session alone | Return | Parent text |
| Sticker earned | Child gets a reward | Reward loop | Observed |
| Parent gate passed | Adult enters the gate | Purchase intent | Observed |
| Trial started | Parent starts a trial | Interest | Play Console |
| Subscription active at day 30 | Parent still subscribed | Value | Play Console |
| Crash | App stops | Quality | Play Console |
| Waitlist signup | Form submitted | Demand | Forms |

## 5. Weekly review template

```
Week of: ___
Opted-in testers: ___ (last week: ___)
Waitlist emails: ___ (target: ___)
Children returning in week 2: ___ of ___ (target: 60%)
Parents granting the mic: ___ of ___ (target: 75%)
Crash-free users: ___ % (target: 98%)

What worked: ___
What didn't: ___
One thing to try this week: ___
```

## 6. Investor-ready metrics

Skipped. No raise is planned (facts).

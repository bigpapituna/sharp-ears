<!-- /founder:product-brief · 2026-10-21 · input: Sharp Ears: an Android app that teaches piano by listening to your real piano through the phone mic, with a kids' mode and a family of profiles; for parents of young kids and adult beginners who own a piano or keyboard. -->

# Product brief: Sharp Ears (public Play Store version)

Assumption: this brief covers the public build, a separate app from the private build (docs/publishing.md).
Assumption: the primary audience comes from the persona hypotheses in persona-gen.md, which are not yet confirmed by interviews.
Assumption: the 90-day targets are Roy's starting points to test, not benchmarks.

## 1. Problem statement

A child who owns, or can reach, a piano gets a few lessons or an app and then stops, because practice at home is lonely, silent and needs a parent sitting beside them. Parents feel it most: they become the practice police. Today they use a weekly teacher, a subscription app such as [Simply Piano](https://www.hellosimply.com/family/piano-for-kids) (family plan, up to 5 profiles) or YouTube. Rockstar Music's June 2026 roster shows 73 of 183 Canadian instructors teach children aged 5 to 12 ([source](https://rockstarmusic.ca/research/state-of-music-lessons-canada-2026), one school, not a survey). Nothing in founder/facts.md shows how many families fail at practice. That is the first thing to find out.

## 2. Target audience

Primary: a parent in Canada with a piano or keyboard at home and a child aged 4 to 10 who plays on the same phone every day. Secondary: an adult beginner learning alone. Tertiary: a grandparent hosting grandchildren. Persona-gen.md has the detail.

## 3. Value proposition

Sharp Ears helps parents of young children get daily piano practice going by listening to the family's real piano through the phone mic and giving each child a pup's world to come back to, unlike apps such as [Artie](https://artmaster.com/artie) whose page describes lessons and tutoring but no kids' features, or Simply Piano, which adapts to a child's age but has no story world on its page.

## 4. Core features (MVP)

1. **Mic listening on a real piano.** No cable, acoustic or digital (facts). Without it the app is a video player.
2. **Pup's Playground and the kid world map.** 76 kids' game lessons across 7 pages (facts). It is the reason a child opens the app.
3. **Family profiles, up to 8 on one phone.** Each child has progress, stickers and a pup. One phone is the shared device in the target home.
4. **Parent gate and a plain mic explainer.** Parents decide on the microphone; Google's [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335) lists microphone data as sensitive.
5. **Adult course, 70 lessons, and 39 public-domain songs** (facts). The grown-up path for the second persona.
6. **Hints and wrong-key colouring.** Keeps a stuck child from quitting; hint escalation is shipped (facts).
7. **Free tier and a family plan.** Needed to launch publicly; pricing is in pricing-strategy.md.

## 5. Success metrics (first 90 days of the beta)

| KPI | Target | Type |
|---|---|---|
| Opted-in closed testers, held for 14 days | 12 or more (Google's gate) | Leading |
| Children who start a session in week 2 without a parent prompt, from a parent text | 6 of 10 | Leading |
| Parents who grant the mic in the first session | 6 of 8 | Leading |
| Waitlist email signups from Canadian parents | 100 | Leading |
| Paying family subscribers | 5 | Lagging |

## 6. Risks and assumptions

1. **Parents will let a phone listen to a child's practice.** Test: 8 parent sessions, CAD 160 in gift cards, 10 days.
2. **Kids return on their own.** Test: 10 children, two parent texts, CAD 0 to 100, 14 days.
3. **Mic-only detection is good enough in a real, noisy room.** docs/KIDS-PLAN.md says the app is not yet tested with real kids in a real room. Test: record 5 home sessions with parent consent and watch where kids get stuck, CAD 100 in gift cards, 10 days.

## 7. Go-to-market snapshot

Channels and CAC. CAC is an Estimate: Android CPI of $1.22 ([Segwise, early 2025](https://segwise.ai/blog/cpi-ipm-roas-benchmarks-optimizing-ad-spend/)) to $3.70 ([Mapendo](https://mapendo.co/blog/cost-per-install-2025-the-ultimate-report-to-grow-your-app-worldwide)) divided by 2.1% install-to-paid ([RevenueCat](https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026)) is $58 to $176 per payer in USD.
1. Play Store open testing in Canada: Estimate: $0 per install.
2. Piano teachers and studios in Ottawa: $0 plus time; Not found as a published benchmark.
3. Reddit communities such as [r/pianolearning](https://gummysearch.com/r/pianolearning) and [r/CanadianParents](https://gummysearch.com/r/CanadianParents/): $0 plus time, subject to each sub's rules.

Growth tactic specific to the product: the "send to someone" certificate and the family wall, which put a grandparent or aunt in front of the app. Check COPPA before enabling it for children: the FTC says sharing features can turn into collection or disclosure ([FTC FAQ](https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions)).

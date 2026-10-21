<!-- /founder:validate-idea · 2026-10-21 · input: Sharp Ears: an Android app that teaches piano by listening to your real piano through the phone mic, with a kids' mode and a family of profiles; for parents of young kids and adult beginners who own a piano or keyboard. -->

# Validate idea: Sharp Ears

Assumption: the target is the public Play Store version (not the private build), launched in Canada first.
Assumption: founder fit is scored from founder/facts.md, since the input says nothing about Roy.
Assumption: prices below are "$" as shown on each vendor page; where a page does not name the currency I treat it as USD.

## 1. The 30-second assessment

The problem is real but soft: parents want kids to practise, and practice is the part that fails. The idea is not new. Several well-funded or long-running apps already listen to a piano. What is different is the shape: kids first, a story world, up to 8 profiles on one phone, Android, no cable. The product is built. The open question is whether anyone beyond one family wants it, and nothing in founder/facts.md answers that.

## 2. The five fatal questions

**Q1: Customer and willingness to pay.** A parent in Canada, roughly 30 to 45, with a piano or keyboard at home and a child aged 4 to 10, who has tried or would try a teacher and finds weekly practice a fight. Anchors: family plans cost $209 to $230 a year (Simply Piano $209.90 per [ArtMaster, 13 Jul 2026](https://www.artmaster.com/articles/what-piano-apps-actually-cost-in-2026); flowkey Family $224.99 per [flowkey help, 4 Aug 2026](https://help.flowkey.com/articles/4466337)). Estimate: 2.1% of installs on freemium apps become paying within 35 days ([RevenueCat, 2026](https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026)), so about 1 in 48 installers. "Maybe" is a no until a parent pays or pre-commits. Today the count is zero.

**Q2: Why hasn't someone built this?** They have, in parts.
- [Simply Piano](https://www.hellosimply.com/family/piano-for-kids): up to 5 profiles, adapts to a child's age, ad-free. Raised $50M at a $1B valuation in 2021 ([Calcalist](https://www.calcalistech.com/ctech/articles/0,7340,L-3910571,00.html)).
- [Artie](https://artmaster.com/artie): listens through the mic or MIDI, falling notes, wait mode, $34.99 a month. Its page mentions no kids features. ArtMaster closed a round of more than 800,000 euros in March 2025 ([ain.ua](https://en.ain.ua/2025/03/13/czech-startup-artmaster-secures-eur800k)).
- [Skoove](https://www.skoove.com/blog/best-piano-apps/), [Yousician](https://support.yousician.com/hc/en-us/articles/115005189525-Premium-membership-options-in-Yousician) and flowkey also listen.
What is different now: mic listening is table stakes, and one person with AI coding agents can ship 76 kids' lessons. Cheap building also means anyone can copy the kids' angle.

**Q3: Distribution.** First 100 users: Roy's network, then Canadian parents reached through teachers and parent communities. There is no built-in loop yet. The nearest candidates are sending a certificate to a grandparent and a sibling duet, both shipped per docs/KIDS-PLAN.md. Every other user is manual effort. Red flag: the only scalable channel I can see is the Play Store listing.

**Q4: Big business or a feature?** Simply Piano already has family profiles, so the kids' angle can be added by an incumbent. Ceiling: Estimate: $1M ARR needs 1,000,000 / 89.99 = 11,112 subscribers at a CAD 89.99 plan (price proposed in pricing-strategy.md, not decided), which at 2.1% conversion is 11,112 / 0.021 = about 529,000 installs. Simply Piano is estimated at 88 million downloads ([MixRank](https://mixrank.com/playstore/apps/com.joytunes.simplypiano), third-party, undated). For $1M ARR, Sharp Ears needs about 0.6% of that.

**Q5: Can the founder build it?** Yes, and mostly has. Hardest challenges are not code. First, the Play gate: a personal developer account created after 13 Nov 2023 needs a closed test with at least 12 testers opted in for 14 days ([Google](https://support.google.com/googleplay/android-developer/answer/14151465)). Second, kids' app policy: if children are a target audience, Google's [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335) applies, and it lists microphone data as sensitive. docs/publishing.md currently plans to declare "adults and teens" so the policy does not apply, which contradicts the kids' mode. Third, docs/KIDS-PLAN.md says the app has not been tested with real kids in a real room. Not a first-hire problem, but a lawyer read of the privacy items is advisable.

## 3. Idea scorecard

| Dimension | Score | Notes |
|---|---|---|
| Problem severity | 3 | Real, discretionary. Piano is the most-taught instrument in one Canadian roster: 105 of 183 instructors ([Rockstar Music, June 2026](https://rockstarmusic.ca/research/state-of-music-lessons-canada-2026), one school, not a survey). |
| Market size | 4 | Proven by incumbents' scale; Canada-first is small but real. |
| Willingness to pay | 3 | Competitors charge $90 to $230 a year; no Sharp Ears buyer yet. |
| Competition gap | 3 | Kids plus 8 profiles plus Android is a gap, but Simply Piano is adjacent and copyable. |
| Distribution | 2 | No audience, $0 to $200 a month, Android only, no loop proven. |
| Timing | 3 | Mic apps are mainstream; kids' privacy rules are tightening (below), which raises cost. |
| Founder fit | 3 | Built it, has a piano and child testers; no teaching credentials or audience stated. |
| **Total** | **21/35** | Promising but has gaps. Weakest: distribution. |

Timing note: the FTC amended COPPA in 2025 ([Hunton](https://www.hunton.com/privacy-and-cybersecurity-law-blog/ftc-publishes-final-coppa-rule-amendments)), and Canada's privacy commissioner is drafting a Children's Privacy Code ([OPC](https://www.priv.gc.ca/en/about-the-opc/what-we-do/consultations/completed-consultations/consultation-children-code/)).

## 4. Validation experiments (each under 2 weeks and $200)

Use a public-safe build, not the private one, which holds copyrighted music (docs/publishing.md).

1. **Parents accept the mic.** Show the app to 8 Canadian parents on their own phones with a child present. Success: 6 of 8 grant the microphone in the first session and 5 of 8 allow a second session the next day. Cost: CAD 20 gift cards x 8 = CAD 160.
2. **Kids come back unprompted.** 10 children on a piano they already own, 14 days. Ask the parent one text on day 7 and 14: "Did your child start it without you suggesting it?" Success: 6 of 10 say yes in week 2. Cost: CAD 0 to 100.
3. **Waitlist from strangers.** A one-page site and one post each in [r/CanadianParents](https://gummysearch.com/r/CanadianParents/) and a teacher group, after reading their rules. Success: 25 email signups from Canadian parents of 4 to 10 year olds in 14 days. Cost: under CAD 30.

## 5. The honest verdict

**Build it, narrowly.** Ship the public build family-first and do not market it to adult beginners, where Skoove, Simply Piano and flowkey are strongest. The single most important thing this week: fix the audience declaration (treat children as a target audience and plan for the Families policy) before anything else, then recruit 12 outside families for the closed test. The 14-day test is also experiment 2.

Sharp Ears becomes a stronger bet if experiment 1 passes and a teacher recommends it. If fewer than 4 of 10 children return in week 2, the pup world is not doing its job; fix that before spending on any channel.

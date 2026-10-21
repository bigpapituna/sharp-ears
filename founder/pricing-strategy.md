<!-- /founder:pricing-strategy · 2026-10-21 · input: who pays: parents and adult learners; no pricing idea yet; Artie charges $34.99 a month; the app has no servers to pay for. -->

# Pricing strategy: Sharp Ears

Assumption: Roy has not decided a price (facts). Every price below is a proposal.
Assumption: prices are set in CAD in Play Console for Canada. Competitor prices are USD, so compare after converting at the day's rate; I show parity comparisons, which flatter Sharp Ears.
Assumption: the beta runs free and billing ships in weeks 3 and 4 (mvp-scope.md).

## 1. Pricing model analysis

| Model | Fit | Pros | Cons |
|---|---|---|---|
| Flat subscription | 4 | Market standard; every competitor sells it | Parents resent paying when a child pauses |
| Usage-based | 1 | None here | Punishes practice |
| Per-seat | 2 | Profiles map to seats | Parents compare it to a family bundle |
| Freemium | 4 | Parents trust before paying; Skoove, Piano Marvel and Artie have free tiers | About 2.1% of installs pay within 35 days ([RevenueCat, 2026](https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026)) |
| Credits | 1 | None | Confusing around children |
| One-time purchase | 3 | No servers, so no ongoing cost; parents like it | No recurring revenue; Roy supports it forever |

**Recommendation:** freemium with a flat annual-first subscription. Add a capped one-time founding offer (below) because the app has no server costs.

## 2. Tier design

**Free.** 2 profiles. The first Playground page (12 lessons) and 10 of 39 songs. The first unit of the adult course. Upgrade trigger: finishing page 1 and wanting page 2, or adding a third child.

**Solo.** 1 profile, all 76 kids' lessons or the 70-lesson adult course, all 39 songs. CAD 7.99 a month or CAD 59.99 a year (37% off: 59.99 / (7.99 x 12) = 0.626). Upgrade trigger: a second person in the home wants a profile.

**Family.** Up to 8 profiles, everything in Solo for all of them. CAD 11.99 a month or CAD 89.99 a year (37% off: 89.99 / (11.99 x 12) = 0.625). Both paid plans start with a 14-day trial (pay only after a habit forms; RevenueCat reports a median trial-to-paid rate of 42.5% for trials of 17 to 32 days and 25.5% for trials under 4 days, same report; the page's metric labels conflict, so treat it as directional). Not a business tier: there is no sales-led plan, since schools would need privacy reviews.

## 3. Competitive pricing context

All prices checked 2026-10-21; USD unless the page says otherwise.

| App | Price and what is included | Sharp Ears vs it |
|---|---|---|
| [Artie](https://artmaster.com/artie) | $34.99 a month; $149.99 a year with a 7-day trial; Family $229.99 a year ([ArtMaster, 13 Jul 2026](https://www.artmaster.com/articles/what-piano-apps-actually-cost-in-2026)) | Below. At parity, Family CAD 89.99 is 61% below $229.99 |
| [Skoove](https://help.skoove.com/en/articles/5417607-what-is-skoove-premium-and-how-much-does-it-cost) | $29.99 a month, $149.99 a year (11 Feb 2026) | Below Solo CAD 59.99 |
| [flowkey](https://help.flowkey.com/articles/4466337) | Premium $149.99 a year, Family $224.99 (4 Aug 2026) | Below |
| [Simply Piano](https://www.artmaster.com/articles/what-piano-apps-actually-cost-in-2026) | $169.90 a year, Family $209.90 (13 Jul 2026) | Below |
| [Yousician](https://support.yousician.com/hc/en-us/articles/115005189525-Premium-membership-options-in-Yousician) | Premium $89.99 a year (one instrument), Family $209.99 | Solo below; Family far below |

Roy's $34.99 anchor is Artie's monthly price, the highest individual monthly price I found. Annual prices cluster at $90 to $170 for one person and $209 to $230 for a family. Sharp Ears is cheaper because it has no video lessons or current pop songs (competitor-matrix.md). Pricing 30% to 60% under competitors (at parity) is a launch choice; I did not find evidence on whether parents see a low price as a quality warning. Test it.

## 4. Unit economics

Cost per user per month, with the arithmetic:
- Servers: $0. The app sends nothing (facts).
- Google's fee: 15% on subscriptions in all markets other than Australia, the EEA, Japan, the UK and the US (10% plus a 5% billing fee there). The page does not name Canada, so Canada appears to fall under 15% ([Google](https://support.google.com/googleplay/android-developer/answer/112622)). Confirm in Play Console.
- Registration: $25 once ([Google](https://support.google.com/googleplay/android-developer/answer/6112435)).
- Sales tax on Canadian subscriptions: Not found.
- Support: Estimate: 5 minutes per subscriber a year x CAD [Roy's hourly value] is small but not zero.
- Roy's tooling: CAD [monthly AI coding subscription cost].

Net per year after Google's 15%: Family annual 89.99 x 0.85 = CAD 76.49. Solo annual 59.99 x 0.85 = CAD 50.99. Family monthly 11.99 x 0.85 = CAD 10.19. Gross margin before Roy's time: 85% on every tier.

Break-even: Estimate: to cover a CAD 200 a month budget from Family annual plans, 200 / (76.49 / 12) = 31.4, so 32 subscribers. One subscriber covers the $25 registration (25 / 76.49 = 0.33). Targets blended ARPU: Estimate: 70% Family and 30% Solo at the annual prices gives 0.7 x 89.99 + 0.3 x 59.99 = CAD 80.99 a year, or CAD 68.84 net.

## 5. Pricing psychology

- **Anchoring.** Show the monthly Family price (CAD 11.99) next to the annual (CAD 89.99, which is CAD 7.50 a month). At parity the annual Family plan costs 2.6 months of Artie's monthly price (89.99 / 34.99 = 2.57).
- **Decoy.** Solo annual at CAD 59.99 makes Family at CAD 89.99 (CAD 30 more for up to 7 extra profiles) the obvious pick.
- **Annual framing.** Say "CAD 7.50 a month, billed yearly" and drop the discount percentage.
- **Children.** Show every price and every trial offer only behind the parent gate, with no countdowns. Google's Families policy prohibits manipulative tactics to encourage purchases ([Google](https://support.google.com/googleplay/android-developer/answer/9893335)).

## 6. Launch versus scale pricing

- **Launch (first 90 days):** Family CAD 69.99 a year, Solo CAD 49.99 a year. The price buys reviews and proof; the Play rating matters more than early revenue.
- **Founding offer:** a one-time Family lifetime pass at CAD 199.99, limited to the first 100 buyers, closing 30 days after launch. That is 199.99 / 89.99 = 2.2 years of the Family annual price. Rationale: no servers means a lifetime pass has near-zero running cost, and it funds the tooling bill. Risk: Roy supports it indefinitely.
- **Grandfathering:** early subscribers keep the launch price for as long as they stay subscribed. Check Google's price-change rules before promising this; Not checked.
- **Price increase triggers:** move to the scale prices when two things are true: 4.0 or higher on Play with 50 or more ratings, and 25 or more paying families.

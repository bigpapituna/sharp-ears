<!-- /founder:user-interviews · 2026-10-21 · input: assumptions we are least sure about: that parents will let a phone listen to a child's practice; that kids keep coming back to the pup's world without a parent pushing; that mic-only detection is good enough on a real piano in a noisy room; that families want multiple profiles on one phone. -->

# Customer interviews: Sharp Ears

Assumption: you interview parents, not children. Watching a child play is a separate step that needs the parent's written consent.
Assumption: the primary persona is Marie-Eve in persona-gen.md (a parent in Ottawa with a child aged 4 to 10 and a piano at home).
Assumption: interviews run by phone or video, 25 minutes, in English; offer French if the parent prefers.

## 1. Interview objectives

You listed four assumptions. Test the first three as objectives. The fourth is covered by two questions in the problem section.

| Hypothesis | Confirmed looks like | Rejected looks like |
|---|---|---|
| H1. A parent will let a phone listen to a child's practice. | They describe, unprompted, a time they let a device listen or record (voice assistant, school app) and say what made them comfortable. | They name the microphone as a reason they said no to an app, and nothing you say changes it. |
| H2. A child returns to a story world without a parent pushing. | The child has asked for an app or game on their own in the last two weeks, and the parent can name the day. | The child only plays when told, or the parent cannot recall a time. |
| H3. Mic-only detection is good enough in a noisy room. | The piano sits in a room with TV or siblings and the parent says that is when practice happens. This is a test of the room, not of Sharp Ears. | The piano is in a silent room and nobody practises when it is loud, so the risk never arises. |

Note: docs/KIDS-PLAN.md says the app is not yet tested with real kids in a real room. Interviews tell you if the room is noisy; only a session tells you whether it works.

## 2. Screening criteria

**Must have.** Lives in Canada. Has a child aged 4 to 10. Has a piano or keyboard at home. Has used a phone or tablet app with the child in the last month.

**Nice to have.** The child has had at least one piano lesson. Two or more children share the instrument. Android phone.

**Disqualify.** Piano teachers (they will sell you their method), friends and relatives, anyone who knows Roy, and parents who say they do not allow screens at all (you are not building for them).

**Where to find them.** [r/CanadianParents](https://gummysearch.com/r/CanadianParents/) (about 4,000 members per GummySearch, a tracker that closed in November 2025, so count is stale), parents of students at an Ottawa piano teacher's studio, your own school or neighbourhood parent groups. Read each community's rules first. Reddit moderators may require approval for posts like this; I could not read r/pianolearning's rules.

**Incentive.** CAD 20 gift card for a 25-minute call. Eight interviews = CAD 160.

## 3. Interview script

**Opening (2 minutes).** Say: "Thanks for doing this. I'm building something for families with a piano, and I'm still working out whether it's any good, so I'd really like to hear what's not working in your house. Nothing you say will hurt my feelings. The most useful thing you can do is be blunt. I won't try to sell you anything. May I record this so I can listen back and not type? Only I will hear it and I'll delete it after I've taken notes. Is that all right?"

**Warm-up (3 minutes).**
1. "Tell me about your family and who plays the piano at your place." Prompt: "Who uses it most?"
2. "Where does it sit in the house? What's happening in that room during the day?" Prompt: "Is the TV nearby?"

**Problem exploration (10 minutes).**
3. "Tell me about the last time your child sat down at the piano. What happened?" Prompt: "Who started it? How long did it last?"
4. "What happened the time before that?" Prompt: "What was different?"
5. "Tell me about the last time you tried something to help them practise. What was it?" Prompt: "Did you pay for it? What happened after a month?"
6. "Tell me about the last app your child asked to open on their own." Prompt: "What did they ask? Who suggested it first?" (tests H2)
7. "Think of the last app you let listen through the microphone, or one you said no to. What happened?" Prompt: "What made the difference?" (tests H1)
8. "When there is more than one child, who uses the phone, and how do they share it?" Prompt: "Do they get their own logins anywhere?" (profiles)
9. "Last time you wanted to see whether your child played the right notes, how did you check?" Prompt: "What did you do when you weren't sure?"

**Solution exploration (5 minutes).**
10. "If you could change one thing about how your child practises, what would it be?" Listen for features they raise unprompted.
11. "What would be a dealbreaker for an app like this?" Listen for privacy, price, ads.
12. "Who in your home would decide whether to pay for it?"

**Reaction (5 minutes).** Say exactly: "It's an app that listens to your real piano through the phone and gives each child a puppy to practise with. Everything stays on the phone." Then ask:
13. "What's your first reaction? What would you want to know?"
14. "Where would the phone sit while your child plays?"
15. "What is the first thing that would make you say no?"
16. "What do you pay for apps today, and what for?" (tests price)

Real interest: they ask when they can try it, offer a child's name, or ask to see it. Politeness: "sounds great" with no detail.

**Closing (2 minutes).** "Do you know one or two other parents with a piano I could talk to? Would you be willing to try an early version for two weeks and tell me honestly what happened, starting this week?" Thank them and say what happens next: "I'll send a CAD 20 gift card by email today."

## 4. Analysis framework

After 5 to 8 interviews, fill this in.

| # | Piano room noisy? | Mic worry (Y/N) | Child asked for an app on their own (Y/N) | Shares one phone (Y/N) | Price they named | Asked to try (Y/N) |
|---|---|---|---|---|---|---|

**Signal versus noise.** Count a pattern only if 3 or more interviewees raise it unprompted. One loud parent is not a trend.

**Decision criteria.**
- Build on (ship the family beta): 5 of 8 have a shared phone, and 4 of 8 asked to try it.
- Pivot to an adult-first or teacher-first angle: fewer than 3 of 8 say a child ever asks for an app on their own.
- Stop (rethink the mic): 4 of 8 name the microphone as a reason they would say no. Check Google's [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335) and what the in-app explainer says before you decide.

## 5. Red flags cheat sheet

Polite, not honest:
- "That sounds cool" with no detail.
- "I'd definitely use that."
- "You should add [feature]."
- They cannot describe the last time their child played.

Real interest:
- They describe the problem in specific, emotional terms without prompting.
- They have already spent money on lessons or an app.
- They ask when they can use it.
- They offer to introduce another parent.

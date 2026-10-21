# Parent brief, step 2b: the template writer

The built-in writer (docs/PARENT-BRIEF-PLAN.md, "Templates (offline, slow, failed, over quota)"): it writes the brief on the
phone with no network, instantly, and never fails. Android-free (`engine/brief/templates/`), JVM-tested. SAMPLES.md has
four full examples of what it writes; read them first.

## What the integrator needs

```kotlin
import com.bigpapituna.sharpears.engine.brief.templates.CourseTitles
import com.bigpapituna.sharpears.engine.brief.templates.TemplateWriter

val titles = CourseTitles.lookup { songId -> songRepository.all().firstOrNull { it.id == songId }?.title }
val templates = TemplateWriter(titles = titles, seed = profile.id.hashCode().toLong())   // clock defaults to System.currentTimeMillis
```

| Constructor parameter | What |
|---|---|
| `titles: (String) -> String` | Turns a lesson id, a library song id or a Playground page id into its title; blank for one it does not know (a line that wants a title then reads without it). `CourseTitles.lookup(songTitle)` covers lessons (`COURSE`) and pages (`PLAYGROUND_PAGES`) itself, so only the song lookup is yours (`SongRepository.all()`, an imported song included). The lookup runs on the calling thread and is cheap. |
| `seed: Long` | Any stable number per family or profile (the profile id's hash is fine). It starts each phrasing list at a different place, so two families do not read alike; the same seed, findings and week always give the same words. Change it and the whole wording moves. Default 0. |
| `clock: () -> Long` | Stamps `writtenAt`. Default `System::currentTimeMillis`; tests pass a fixed one. |

## Where it goes in the chain

The template writer is the last writer in the `WriterChain`, after the AI writer, because it never returns null:

```kotlin
WriterChain(listOf(aiWriter, templates))      // AI first (three-second limit for notes and the card), templates last
```

- `write(findings, name): Brief` always returns a Brief (`writtenBy = TEMPLATE`, which is what the screen's small "written on
  the phone" note keys on). `kidCard(findings, name): KidCard` always returns one, `answer(findings, name, question): String`
  always returns text. Nothing is thrown: if anything inside ever failed, a plain fallback Brief, card or sentence comes back.
- Run it on whatever thread is convenient: it is pure, takes microseconds, and holds no state between calls.
- The name is passed in and used only in the headline and the praise (at most twice in the one-minute read), and in the
  child's card. A grown-up's brief (`ProfileKind.ADULT`) never uses the name: it says "you".
- Hand it the same `Findings` the screens keep. It names only lessons, songs and pages the findings mention.

## What the screens can rely on

- `Brief.facts` has one to four sentences; `notes` up to four (hard flags first, then the good-news ones), each with its
  "why that is normal"; `recommendations` up to three, each tied to one `Candidate` of the findings (the action button comes
  from `Recommendation.candidate`), none longer than 160 characters; `praise` always has a sentence.
- `sections` is empty for a SESSION. For a DAY or WEEK it holds, in this order and only when there is something to say:
  "Habits", "Skills", "Stuck and unstuck", "What you did" (actions and settings changed in the range, so a parent's action
  shows in the next brief as the plan promises). Section bodies are plain paragraphs.
- The headline is at most 110 characters, one sentence, no digits, no dashes.
- `KidCard.lines` has two or three lines of at most 70 characters, first person from the pup, no digits or symbols, so
  the voice can speak them as they are; `nextTry` is always present ("Next time, let's try ..."). Hand the lines to the
  speech queue; nothing in them needs the catalog of recorded lines (they are made on the phone).
- Titles are in typographic double quotes in the parent's text and bare in the child's card.

## Asking the brief

`answer` recognises seven intents by keywords: how long they played, what they did, what was hard, what to do next, what
to praise, ready for letters, ready for the road. Anything else gets "I can only answer about what the app saw this week."
(or "today", "this session") and the headline. It answers from the findings only, in one to three sentences. A model-backed
`answer` goes first in the chain and this one catches what it cannot answer.

## Tests

`engine/brief/templates` tests (in `src/test`): `TemplateWriterTest` (the rules over thousands of random findings),
`TemplateBankTest` (bank sizes, every token filled), `TemplateCatalogTest` (prints the four samples; also writes
`app/build/template-samples.md`). `TemplateGen` is the random-findings generator; reuse it for the chain's tests.

## How the wording varies

A phrasing is picked per slot (a kind in a period, say "fact DAYS_PRACTISED") from the lines that apply to these findings.
The pick is the seed's hash of the slot plus the week number, so the same week reads the same on Refresh and each new
week moves every list on by one: the next week's brief never repeats a phrasing of the last. Lines that name a lesson are
preferred over lines that do not. The bank is data (`Facts.kt`, `Trends.kt`, `Flags.kt`, `Praise.kt`, `Recs.kt`, `Kid.kt`,
`Sections.kt`, `Headlines.kt`, `Answers.kt`): add phrasings to a list and the tests check the rest.

# Parent brief, step 2a: the AI writer

Branch `brief2a` (docs/PARENT-BRIEF-PLAN.md, build outline step 2, "the prompt and the AI client"). The AI writer, its
prompts, its HTTP client, its quota and cache, the writer chain, and what the integrator wires. Everything here is
Android-free and JVM-tested except `OpenRouterTransport`, which uses java.net only (also JVM-tested, against a local fake
server). No test ever reaches OpenRouter, and this PC has no key.

## Files

| File | What |
|---|---|
| `AiWriter.kt` | `AiWriter : BriefWriter, Refreshable`, `AiLimits`, the `Refreshable` interface |
| `Prompts.kt` | the three system prompts (brief, the pup's card, the answer), the user messages, the length limits, the worked examples |
| `AiReplies.kt` | the checks on the way back: parse, tidy, drop what breaks a rule, fill the name in |
| `Titles.kt` | `Titles` (the lookup), `TitleSet` (the titles for one findings), `IdAliases` (ids that hold a name), `Names` |
| `AiTransport.kt` | `AiTransport`, `AiRequest`, `AiCallException` (kinds, billed or not), `Secrets.scrub` |
| `OpenRouterTransport.kt` | the HTTPS client |
| `Budget.kt` | `Budget` (40 calls per profile per week, the cache), `BudgetStore`, `MemoryBudgetStore` |
| `WriterChain.kt` | `WriterChain`: the writers in order, the first that writes wins |
| test `engine/brief/ai/*` | 52 tests: `AiWriterContractTest`, `PromptSnapshotTest` (pins in `src/test/resources/brief-ai/`), `PromptsTest`, `AiRepliesTest`, `OpenRouterTransportTest`, `BudgetTest`, `WriterChainTest` |

## Building the chain

Make the transport and the budget once per process; make an `AiWriter` and the chain per profile (they are cheap). Call
the chain off the main thread: it blocks for up to the limit.

```kotlin
import com.bigpapituna.sharpears.BuildConfig
import com.bigpapituna.sharpears.engine.brief.ai.*

// Once (Application or a small object in data/):
val aiStore = PrefsBudgetStore(context.getSharedPreferences("ai-brief", Context.MODE_PRIVATE))
val budget = Budget(aiStore)                                   // 40 calls per profile per week, Monday to Sunday, phone's zone
val transport = OpenRouterTransport(
    baseUrl = BuildConfig.AI_BASE_URL,                         // https://openrouter.ai/api/v1
    keyProvider = { BuildConfig.AI_KEY },                      // read at call time; empty on any build without secrets.properties
    model = BuildConfig.AI_MODEL,                              // anthropic/claude-haiku-5.5
    allowed = { Network.unmetered(context) || settings.aiOnMobileData },
    log = { Log.d(TAG, it) },
)

// Per profile, when a brief, a card or an answer is wanted:
val songs = SongRepository(context).all().associate { it.id to it.title }   // read once here, not inside the lambda
val chain = WriterChain(listOf(
    AiWriter(transport, BuildConfig.AI_MODEL, System::currentTimeMillis, AiLimits(), budget, profile.id,
        titles = Titles.course { songs },
        otherNames = { roster.profiles.map { it.name } },     // every name on the phone: titles and ids holding one are hidden
        log = { Log.d(TAG, it) }),
    TemplateWriter(/* brief2b */),                             // never returns null, so the chain always writes
), log = { Log.d(TAG, it) })

val brief = chain.write(findings, profile.name)                // Brief, writtenBy AI or TEMPLATE
val again = chain.refresh(findings, profile.name)              // Refresh: past the cache, counts as a call
val card = chain.kidCard(sessionFindings, profile.name)        // KidCard (the AI writes it for kids only)
val answer = chain.ask(findings, profile.name, question)       // WriterChain.Answer(text, writtenBy)
```

The `BudgetStore` over SharedPreferences (six lines, in data/, since engine/ has no Android):

```kotlin
class PrefsBudgetStore(private val p: SharedPreferences) : BudgetStore {
    override fun get(key: String): String? = p.getString(key, null)
    override fun put(key: String, value: String?) { p.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
}
```

- **Deleting a profile:** call `budget.forgetProfile(id)` in `Profiles.delete`, next to `Briefs.forget(app, id)`.
- **Backup:** `ai-brief.xml` is a cache and a weekly count; add `<exclude domain="sharedpref" path="ai-brief.xml" />` to
  backup_rules.xml and data_extraction_rules.xml (nothing breaks if it travels; it is just not worth carrying). It is not a
  progress file, so ProgressFiles.NAMES does not need it; the progress reset may clear it.
- **The unmetered lambda:** `Reporter.unmetered()` is private. Move it to a shared `data/Network.kt`
  (`fun unmetered(context: Context): Boolean`, the same two capability checks) and use it in both places. "Unless the user
  allowed": a switch in Settings > Privacy, "Write briefs on mobile data", off by default. Version one may ship without the
  switch (`allowed = { Network.unmetered(context) }`); the session note then falls to the templates on mobile data.
- **Waiting:** a session note and the pup's card wait at most 3 s, today's brief and an answer 12 s, the weekly brief 24 s
  (12 s, then one retry after a server error or a timeout). For the day and the week, show the template brief at once
  (`TemplateWriter` directly) and swap in the chain's result when it comes back with `writtenBy == AI`; for the session
  note and the card, a short wait with the pup is fine.
- **The weekly notification:** compute the weekly findings and call `chain.write` in the background job that posts it; the
  headline can be the notification's text. Opening the brief from the notification reads the cache (same findings), unless
  something was played in between.
- **Who wrote it:** `Brief.writtenBy`, `KidCard.writtenBy` and `WriterChain.Answer.writtenBy`. The screens show a small
  "written on the phone" under a TEMPLATE brief (the plan's words); an AI brief can say nothing, or "written by AI".

## What the writer does

- **Sends** (`AiRequest`, OpenAI chat shape): the system prompt from `Prompts`, and one user message holding what to write,
  whether the child reads, the titles of every lesson, song and page the findings mention (looked up on the phone), the
  candidates by index with their titles, and `findings.redacted().encode()`. `Prompts` redacts inside the user-message
  builders, so no caller can send raw findings.
- **Never sends a name.** The model writes `{name}`; the app fills it in after the checks. A title holding the person's
  name goes as `{name}` ("Mia's lullaby" is "{name}'s lullaby"); a title holding anyone else's name on the roster is not
  sent; an id holding a roster name (an imported song's id is its file name, `user-mia-s-lullaby`) goes as `hidden-1`;
  a question has the person's name replaced by `{name}` and anyone else's by "someone else", and is cut to 400 characters.
- **Asks for** strict JSON: `{"headline", "facts": [], "notes": [], "recommendations": [{"text", "candidateIndex"}],
  "praise", "sections": [{"title", "body"}]}` for a brief, `{"lines": [], "nextTry"}` for the pup's card, plain text for an
  answer. JSON mode (`response_format: json_object`) is asked for; the parser takes the outermost object anyway, so a code
  fence or a sentence around it does no harm.
- **Checks** every piece (`AiReplies`): the em and en dashes become commas (or "to" between numbers), markdown and bullets
  go, `{ Name }` spellings become `{name}`. A piece is dropped when it names a lesson, song or page title not in the
  findings, compares with other children or learners or ages, gives advice outside the app (teachers, books, benches,
  other apps, health; "Teacher mode" is the app's own), holds another placeholder or a link, is over its length, or (a
  recommendation) points at a candidate index that does not exist or is used twice. The headline must hold no digit
  outside a title. The pup's lines must also be positive (no hard, wrong, missed, stuck...), hold no symbol and no number
  over ten, and say nothing about letters to a child who does not read yet; the next time line must start with "Next time".
  **Null** when the headline, every fact or the praise is gone (brief), with fewer than two lines (card), or when the
  answer breaks a rule (an answer is one piece). The log says what was dropped and why, never the text.
- **Returns null without a call** for empty findings (nothing happened: the template says so kindly), for a grown-up's
  pup card, over the week's quota (the cache still serves), and for a blank question.

### The prompts' guardrails (each sentence is asserted in PromptsTest)

The brief, in this order: only the findings as facts; compare only with their own earlier sessions, days or weeks; never
mention or compare with other children or learners, nor what is usual for an age; no advice outside the app (teachers,
books, videos, other apps, equipment, furniture, health or sleep); every hard moment with why it is normal (the flag's
reason from CONTRACT.md, all listed in the prompt) and a next step; recommendations only from the numbered candidates, by
index; numbers in the facts, never in the headline; warm, kind, optimistic, factual, like a good coach, never labelling or
guessing at moods, tiredness or home life; sentence case and plain words; no em or en dash; titles only as listed; never a
name, `{name}` instead, "they" not "he" or "she" (grown-ups: "you"); no emoji, markdown, bullets or quoted titles. Then the
shape with its limits: headline one sentence of at most 110 characters with no numbers; facts two to four sentences;
notes at most three; recommendations two or three of at most 160 characters; praise one sentence; sections only for a
day or a week. The grown-up's variant drops the pup, reader mode, bedtime and every kid-only flag and action.

The pup's card: first person as the pup; only the findings; only positives; no comparison; two or three lines of at most
twelve words; one line starting "Next time, let's try" from the first candidate with a lesson or song; read aloud, so
numbers as words, none over ten, no symbols; words a young child knows; no letters or note names for a pre-reader; titles
as listed; `{name}` at most once; no dashes; never settings, minutes, goals or rules; flags are for the grown-ups.

The answer: two to four sentences from the findings only; if they cannot answer, say so kindly and say what they do show;
a comparison or off-app question gets a kind "the app only looks at {name}'s own practice"; an action only if it is a
candidate; then the brief's rules; plain text only.

Each prompt carries two worked examples built from CONTRACT.md's example (the kid's week, stuck on Ta and ti-ti) and its
kinds (a reader's session with the left hand behind and a noisy room; a grown-up's week with timing much better and far
misses; a grown-up's session with the left hand behind). PromptsTest runs every example output through the same checks a
real reply meets, against the real catalog of titles, and checks the example titles are still the app's.

### Changing a prompt

PromptSnapshotTest fails on any change. Read the new text in `app/build/prompt-snapshots/`, copy it over
`app/src/test/resources/brief-ai/`, and bump `Prompts.VERSION` in the same commit (the cache key includes it, so briefs
written under the old prompt are written again).

## The key

- It comes from `secrets.properties` at the repo root (gitignored) through app/build.gradle.kts into `BuildConfig.AI_KEY`;
  without the file it is empty and every call ends `NO_KEY` before any connection (the templates write).
- `OpenRouterTransport` reads it through `keyProvider` at call time, puts it in the `Authorization: Bearer` header only, and
  keeps it in no field. Nothing else in the app should read `BuildConfig.AI_KEY`.
- Errors are `AiCallException` with a scrubbed message (`Secrets.scrub`: the key, any six-character piece of it, any
  `Bearer ...`, anything shaped like `sk-...`, any opaque token of 32 characters or more) and no cause. A provider's error
  text is kept, scrubbed and cut to 160 characters, because it is what tells Roy what went wrong ("No endpoints found",
  "Insufficient credits"). OpenRouterTransportTest has a fake server echo the Authorization header back in 400, 401, 403,
  429, 500 and 200-with-error bodies and checks no eight characters of the key reach the message, the stack trace or a log.
- Logs (`log` lambdas, tag SharpEars at debug): one line per attempt and per decision, such as `ai: reply in 1830 ms
  (attempt 1, 912 chars)`, `ai: WEEK from the cache`, `ai: RATE_LIMITED: HTTP 429`, `ai: WEEK dropped 1: fact 2: names a
  title not in the findings`. Never the key, the name, a prompt or a reply. Crash reports (Reporter) carry recent logcat;
  these lines are safe in them.
- It is in the APK ("Option B"): anyone holding the APK can dig it out. The guard is the spending cap on the key at
  OpenRouter. Before the app goes beyond people Roy knows, move to a relay he owns ("Option A"): only `baseUrl` and
  `keyProvider` change. Rotate the key when the beta ends.
- Redirects are not followed and plain HTTP is refused (loopback only, for the tests), so the key only ever goes to the
  HTTPS host in `AI_BASE_URL`.

## Privacy page (draft paragraph)

For Settings > About > Privacy (`ui/AboutScreens.kt`), a new heading after "Internet", and the first paragraph and the
Internet paragraph adjusted to mention it. The onboarding line "Everything stays on this phone. Nothing is sent anywhere."
(`ui/onboarding/OnboardingScreen.kt`) is no longer true on a build with a key: "Everything stays on this phone, except the
brief's findings when the AI writes it (Settings, Privacy)."

> **The AI brief.** On a build with an AI key, and on Wi-Fi (or mobile data, if you allow it), the brief for grown-ups, the
> pup's card and the answers to your questions can be written by an AI model. The app sends OpenRouter (openrouter.ai),
> which passes it to the model's maker (Anthropic, for Claude Haiku), only the brief's findings: minutes, days, stars,
> tries, which lessons, songs and pages, and notes such as "stuck on a lesson" or "noisy room". It never sends a name (the
> app writes the name in afterwards, on this phone), a recording or any sound, a note a grown-up typed, or a song or lesson
> whose title or file name holds someone's name. A question you type or dictate is sent as text with names taken out;
> dictation uses the phone's own speech recognition, so your voice stays on the phone. The app asks OpenRouter to use only
> providers that do not keep or train on what is sent. Like any website, OpenRouter sees the request come from your
> internet connection. What comes back is checked on the phone, and anything that names something not in the findings is
> left out. Offline, on mobile data you did not allow, when the model is slow, after 40 AI briefs in a week for one
> person, or when a reply fails a check, the brief is written on the phone instead, and says so. The last brief written
> for each person is kept on the phone so opening it again costs nothing.

For the public app (docs/privacy-policy.md, docs/publishing.md's data safety answers): the same paragraph, with the relay
(Option A) named instead of the key in the app, and "data shared with a third party: app activity, for app functionality,
not stored by the provider" in the data safety form.

## Manual check on Roy's phone (once the key is in)

1. At openrouter.ai: give the key a spending cap (a few dollars), turn off input/output logging in the account's privacy
   settings, and check `anthropic/claude-haiku-5.5` lists at least one provider that does not keep data (the transport
   asks for `provider.data_collection: deny`).
2. Put `AI_KEY=sk-or-...` in `secrets.properties` at the repo root (never in chat, never committed). Build and install as
   usual; the phone on Wi-Fi.
3. Watch the log: `adb logcat -s SharpEars:D | findstr "ai:"` (PowerShell: `| Select-String "ai:"`).
4. As a kid profile, finish a lesson. The pup's card on the finish screen should read like the pup (positive, short, a
   "Next time, let's try" line) and the log shows `ai: reply in ... ms (attempt 1, ...)`. A card written on the phone
   instead shows `ai: no reply within 3000 ms` or a failure kind.
5. Parent corner > Family > the kid > Week. Written by AI: warm sentences in the kid's name, two or three recommendations
   with buttons, no "written on the phone" note. Back out and open it again: the log says `ai: WEEK from the cache` and
   the words are the same. Tap Refresh: a new `reply in` line, new words, a new time.
6. Ask the brief "What should we practise tomorrow?": an answer in a few seconds naming only lessons from the week. Ask
   "Is she better than her brother?": a kind "the app only looks at her own practice" and a fact about her own week.
7. Turn on airplane mode and open Today: the brief comes at once with "written on the phone"; the log says
   `ai: NETWORK: ...`. Wi-Fi off and mobile data on (no allow switch): `ai: NOT_ALLOWED: not sent: the network is metered`.
8. At openrouter.ai > Activity: one request per AI brief, card or answer, none for the cached re-open, costs in fractions
   of a cent.
9. If the log shows `CLIENT: HTTP 401` the key is wrong; `HTTP 402` the credits or the cap; `HTTP 404 ... data policy` no
   provider meets the no-keeping request (fix the account setting, or build with `privateProvidersOnly = false` and
   change the privacy paragraph); `RATE_LIMITED` the cap or OpenRouter's rate limit. Each falls back to the templates.

## Decisions (made without asking, as briefed)

- **JSON mode, not a JSON schema.** `response_format: json_object` is the widest-supported form on OpenRouter (it is
  ignored where a provider lacks it); the shape is in the prompt with worked examples, and the reply is checked field by
  field anyway. A schema can be added later without changing the checks.
- **What a "call" is for the quota.** One per AiWriter call that reached the provider and may have cost: a 2xx (usable or
  not) or a timeout. A 4xx, 429, 5xx, offline, metered or no-key call is given back, so (d) leaves the quota untouched.
  The weekly brief's one retry counts with its call, not on its own.
- **Deadlines on the writer's side too.** The transport's timeouts bound the connection and each read; the writer also waits
  on its own thread for at most the limit (twice for a week that may retry), so a reply that drips in slowly cannot hold
  the screen.
- **The cache** keeps one reply per profile and slot (SESSION, DAY, WEEK, KID, ANSWER), keyed by a SHA-256 of the prompt
  version, the model, the prompts and the findings without their "now" (a day's or a week's range ends at the moment it
  was computed, so with it the cache would never hit). It stores the model's text with `{name}` and checks it again when
  read back, so a renamed child gets the new name.
- **Required parts.** The plan only said the headline; I also require at least one fact and the praise, because a brief
  without them is not the plan's one-minute read, and the templates write a whole one instead. Dropped lines are not
  topped up from the template writer (the plan's "template line used"): mixing two voices in one brief read badly in
  every sketch; WriterChain could add it later.
- **Titles in prose** are matched case-sensitively, as the app writes them, and only for titles of two words or more,
  after the allowed titles are blanked out: many titles are everyday words ("Skips", "Rests") or phrases ("soft and loud",
  "hands together") that a sentence may use in passing. A title said lowercase mid-sentence is not caught; a sentence
  starting with another lesson's title is.
- **The pup's card is for kids.** A grown-up's kidCard returns null without a call; the template writer decides whether
  grown-ups see a card at all.
- **SCATTERED** has no "why it is normal" in CONTRACT.md; the prompt says "trying lots of things is curiosity, and staying
  in one place lets the stars build" (grown-ups: "staying with one level lets it settle").
- **Privacy routing** (`provider.data_collection: deny`) is on by default; see step 9 above if it leaves no provider.
- **HTTP-Referer** is `android-app://com.bigpapituna.sharpears` (the app has no website); X-Title is "Sharp Ears".
- **No prompt caching.** The system prompts are about 4,000 tokens each; at 40 calls a week per child the saving is not worth
  the extra request shape.

## Not done here

- The template writer (brief2b) and the screens (brief3); `PrefsBudgetStore`, `Network.unmetered`, the mobile-data switch,
  the privacy and onboarding copy, the backup exclusion and the profile-deletion hook above (the integrator's).
- Nothing was run against the real API (no key on this PC, and tests must not). The prompts' quality with the real model,
  the latency of a 3 s session note on the phone, and whether `data_collection: deny` leaves a provider for Haiku 5.5 are
  checked by the steps above.
- Gemini Nano on the Fold7 (the plan's open question) would be another `BriefWriter` placed before `AiWriter` in the chain.

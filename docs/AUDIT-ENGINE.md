# Engine and content audit (2026-10-20, branch `audit-aengine`)

The engine and content team's part of the full audit: correctness that can be proven on the JVM (engine/, the data
model, the content and the consistency between them). No emulator, no device: every finding below has a fix and a JVM
test that fails without it, unless the row says otherwise. Severity: high (wrong for many people, or data or privacy
lost), medium (wrong in a real flow), low (an edge, a corrupt file, a debug tool).

## Findings

| # | Where | What | Severity | Fix | Test |
|---|---|---|---|---|---|
| 1 | engine/AnswerKey.kt | The MIDI answer key (mic vs keyboard reports) paired a key press with earlier key presses instead of mic strikes: a mic strike just before the key was reported as a miss and a false note, and a key struck twice that the mic never heard was never reported | medium (the reports that tune the matcher) | add the event to its list before pairing it | AnswerKeyTest.theMicJustBeforeTheKeyIsAMatch, aKeyRepeatedTheMicNeverHeardIsMissedTwice |

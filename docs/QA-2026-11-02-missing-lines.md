# Missing coach narration (QA 2026-11-02, B-05 and B-06): lines to render

No audio was generated in the fix round. These are the exact files Roy's local TTS pipeline should render. Until they exist, the app
plays the pup's recording for these lines (`voice: (recorded) ...`), which is the designed fallback.

## Where the files go

- Folder: `app/src/afterdark/assets/coach/<voice>/course/`
- File name: `<key>.ogg` (these lines are for anyone, `for: "any"`, so no `_w` / `_m` variants are needed)
- The key is `SpokenCatalog.key(orig)` (the text the app speaks, below as "App line"); the app looks up `<key>_w.ogg` / `<key>_m.ogg`
  first when the profile's "for" is set, then `<key>.ogg` (`CoachGate.narrationOf`).
- Encoding, as `scripts/coach_bank.py` does it: Ogg Opus, mono, 48 kHz, 24 kb/s, metadata dropped, two-pass linear loudnorm to
  `I=-23:TP=-2:LRA=11` (Liam, `male_us`: `I=-20:TP=-2:LRA=11`), true peak at or below -1.5 dBTP after the encode.
- Through the bank instead: add a row `{"key": ..., "orig": ..., "coach": ..., "for": "any", "file": "<key>.mp3"}` to
  `tts-bakeoff/elevenlabs/course_<voice>/index.json` (or the local voice's equivalent), drop the rendered `<key>.mp3` beside it,
  and run `python scripts/coach_bank.py` (run the unit tests first so `app/build/voice-lines.txt` exists; see the note at the
  end about the B-05 key).

Voice folders: `male_us` (Liam), `male_irish` (Rory), `female_us` (Claire), `female_au` (Sienna).

## B-05: "Listen to both hands together." (all four voices)

Lesson "Together", step 2 (Listen). Missing in every voice: the line is also a kid line, and the narration pass took grown-up
lines as "all lines minus kid lines", so it was never written.

| Voice | Folder | File | App line (`orig`) | Coach wording (`coach`, delivery tag in brackets, not spoken) |
|---|---|---|---|---|
| Liam | `coach/male_us/course/` | `ac35d6394729a54a.ogg` | Listen to both hands together. | [low voice] Listen to both hands together... hear how they fit, for me. |
| Rory | `coach/male_irish/course/` | `ac35d6394729a54a.ogg` | Listen to both hands together. | [low voice] Listen to both hands together... hear how they fit, for me. |
| Claire | `coach/female_us/course/` | `ac35d6394729a54a.ogg` | Listen to both hands together. | [low voice] Listen to both hands together... hear how they fit, for me. |
| Sienna | `coach/female_au/course/` | `ac35d6394729a54a.ogg` | Listen to both hands together. | [low voice] Listen to both hands together... hear how they fit, for me. |

The coach wording is new (no voice has one): it follows the course's pattern (the line, then a short added phrase). Change it
freely; only the key and file name must stay.

## B-06: the two first-run lines Liam has (Rory, Claire, Sienna)

Lesson 1 "Sit, then shape your hand" for a grown-up who has never played (`FirstRun.FIRST_NOTE_STEPS`). Liam's were rendered in
2.17 (`tts-bakeoff/elevenlabs/course_male_us_2.17/index.json`); the three newer voices were converted from the older catalog.
The coach wording below is Liam's, word for word.

| Voice | Folder | File | App line (`orig`) | Coach wording (`coach`) |
|---|---|---|---|---|
| Rory | `coach/male_irish/course/` | `0210b88256decf13.ogg` | Listen. This is middle C. | [low voice] Listen... this is middle C. Let it sink in for me. |
| Rory | `coach/male_irish/course/` | `9795d6d562c0ae4b.ogg` | Your turn. Play middle C. | [seductive] Your turn... play middle C for me. I want to hear you. |
| Claire | `coach/female_us/course/` | `0210b88256decf13.ogg` | Listen. This is middle C. | [low voice] Listen... this is middle C. Let it sink in for me. |
| Claire | `coach/female_us/course/` | `9795d6d562c0ae4b.ogg` | Your turn. Play middle C. | [seductive] Your turn... play middle C for me. I want to hear you. |
| Sienna | `coach/female_au/course/` | `0210b88256decf13.ogg` | Listen. This is middle C. | [low voice] Listen... this is middle C. Let it sink in for me. |
| Sienna | `coach/female_au/course/` | `9795d6d562c0ae4b.ogg` | Your turn. Play middle C. | [seductive] Your turn... play middle C for me. I want to hear you. |

## Checks after rendering

- `ls app/src/afterdark/assets/coach/*/course/ | grep -c -e ac35d6394729a54a -e 0210b88256decf13 -e 9795d6d562c0ae4b` gives 4 for
  B-05 and 3 x 2 for B-06 (Liam already has the two B-06 files).
- With a coach on: `--es lesson together --ei step 0` logs `voice: (narrated coach/<voice>/course/ac35d6394729a54a.ogg) Listen to
  both hands together.`; a Never grown-up's first lesson logs `narrated coach/<voice>/course/0210b88256decf13.ogg` and
  `.../9795d6d562c0ae4b.ogg`.
- Note for `coach_bank.py`: the grown-up list it builds is "catalog lines at speed 1.0"; a line that is both a kid line and a grown-up
  line appears once, at the kid speed, so check the "with no recording" list it prints for `ac35d6394729a54a` and keep it.

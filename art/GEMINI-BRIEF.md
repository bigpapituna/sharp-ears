# Gemini brief: art for the family update (2.1)

Everything the plan in docs/KIDS-PLAN.md needs from Gemini, as copy-paste prompts. Nothing in the build waits on
these; they replace placeholders when they land.

## How to work with Gemini

- **Attach art/mascot/sheet.jpg to every pup prompt** and say "the same character as the attached image". That is
  the only way to keep the pup on model across separate generations. Pose 8 on the sheet drifted (a shiba with no
  sweatband), so always restate the character line below.
- **One image per prompt.** Sheets come out at about 200 px per figure, too small. Ask for single, square, full-body
  images and save at the largest size offered (PNG if it offers it, otherwise JPG is fine).
- If a result drifts, reply: "Same character as the attached image: golden retriever puppy, red and white striped
  sweatband, blue cord with a silver whistle. Keep everything else, change only the pose." Regenerating in the same
  chat keeps the look better than starting a new chat.
- Save files with the names given here, into the folders given here. Do not crop or edit; scripts do the cutting.
- Skip anything Gemini refuses or butchers; the placeholders stay for that one item.

## The character line (paste at the start of every pup prompt)

> Golden Coach Pup, the same character as the attached image: a cartoon golden retriever puppy, honey-gold fur with a
> cream chest and muzzle, big round dark brown eyes, small black nose, pink tongue, a red and white striped sweatband
> on its head, and a blue cord around its neck with a silver coach's whistle. Flat vector cartoon style, thick dark
> brown outlines, soft cel shading, pastel highlights. Full body, centred, the whole figure inside the frame with a
> margin, plain pure white background, no text, no numbers, no sparkles or props unless named, square 1:1 image.

## A. Pup poses, folder art/mascot/, file pup-<name>.png

One prompt each: the character line, then the pose sentence.

| File | Pose sentence |
|---|---|
| pup-head-tilt | Sitting, head tilted to one side, one ear up, a gentle puzzled smile, eyes open. (A kid pressed the wrong key; nothing sad.) |
| pup-drumming | Standing on its hind legs behind a small cartoon drum, a drumstick in each front paw mid-hit, mouth open happily. |
| pup-flying | Leaping straight up with all four legs spread, ears flapping, eyes wide and delighted, seen from the side. |
| pup-sticker | Sitting, holding up a blank white star-shaped sticker in both front paws, proud smile. (The app draws on the sticker.) |
| pup-goodnight | Sitting, mid-yawn, eyes squeezed shut, one paw raised in a wave goodbye. |
| pup-dancing | Standing on its hind legs, dancing: one paw up, one hip out, tongue out, eyes closed in joy. |
| pup-clapping | Sitting up on its hind legs, front paws together mid-clap, big grin. |
| pup-listening | Sitting, eyes closed, calm smile, one paw cupped behind an ear. No headphones. (Replaces the off-model sheet pose.) |
| pup-pointing-down | Standing, pointing down and to the left with one front paw, encouraging look. (For "try the lower key".) |
| pup-crown | Sitting proudly, a small gold cartoon crown on its head in front of the sweatband. (Best run of the day.) |

## B. Dress the pup, folder art/mascot/, file pup-wear-<name>.png

Same base pose for all six so the app can swap them: "Sitting facing the camera, the exact sitting pose of figure 1 in
the attached image, happy open-mouth smile, one paw raised in a wave." Then add one item:

| File | Item |
|---|---|
| pup-wear-base | nothing extra (this is the clean base; the sheet's figure 1 at full size) |
| pup-wear-sunglasses | wearing round black sunglasses |
| pup-wear-tophat | wearing a small black top hat tilted on top of the sweatband |
| pup-wear-bowtie | wearing a red bow tie on the blue cord above the whistle |
| pup-wear-headphones | wearing big pastel over-ear headphones over the sweatband |
| pup-wear-scarf | wearing a long striped rainbow scarf |
| pup-wear-partyhat | wearing a pointed party hat with polka dots |

## C. Stickers, folder art/stickers/, file <place>-<n>.png (30, one per prompt)

Style line for all of them:

> A single sticker for a children's piano app: flat vector cartoon, thick dark brown outline, soft cel shading,
> pastel colours, with a thick white sticker border around the whole shape. Centred on a plain pure white
> background, no text, square 1:1 image.

Then the subject. Three places, ten each:

**garden** (Playground lessons 1 to 4): 1 a red tulip, 2 a smiling sun, 3 a blue bird on a branch, 4 a ladybird,
5 a watering can, 6 a snail with a spiral shell, 7 a dog house with a red roof, 8 a wooden fence with three posts,
9 a butterfly with yellow wings, 10 a rainbow over a cloud.

**pond** (lessons 5 to 8): 1 a green frog on a lily pad, 2 a yellow duckling, 3 a goldfish, 4 a dragonfly,
5 a paper boat, 6 a turtle, 7 a water lily flower, 8 a fishing rod with a bobber, 9 a little wooden bridge,
10 a rain cloud with three drops.

**concert** (lessons 9 to 12): 1 a gold star, 2 a trophy, 3 a red velvet curtain, 4 a microphone, 5 a small drum,
6 a grand piano seen from the side, 7 a spotlight, 8 a bouquet of flowers, 9 a ticket stub, 10 fireworks.

## D. The player and the foot (carried over from 2.0.2), folder art/figure/

Same soft-realistic style as the hand frames in art/hand (attach art/hand/hand-right-3.jpg as the style reference):

> Soft realistic illustration, the same painted style as the attached hand image: smooth skin, soft blurred
> shadows, a brown knit sweater, no outlines, plain pure white background, no text, square 1:1 image.

| File | Subject |
|---|---|
| figure-upright.jpg | A person seen exactly from the left side, sitting upright on a piano bench at an upright piano, forearms level with the keys, wrists straight, feet flat on the floor. Head to feet in frame; the piano cut off at the right edge. |
| figure-slouch.jpg | The same person, same camera, slouching: back rounded, shoulders up, wrists dropped below the keys, chin forward. |
| figure-tooclose.jpg | The same person, same camera, sitting too close to the piano with elbows bent sharply behind the body. |
| foot-up.jpg | A foot in a grey sock seen exactly from the left side, heel on a wooden floor, toes resting lightly on a brass piano sustain pedal that is not pressed, the shin rising out of the top of the frame. |
| foot-down.jpg | The same foot and camera, the pedal pressed fully down, the ball of the foot pushing it. |

## E. Playground map backgrounds, folder art/map/, file <place>.png (nice to have)

> A wide, calm background illustration for a children's app, flat vector cartoon, pastel colours, soft cel shading,
> very low detail so buttons can sit on top of it, no characters, no text, 16:9 image.

garden: a sunny garden with a lawn, a dog house, a fence and a few flowers along the bottom. pond: a pond with lily
pads, reeds and a little bridge. concert: a concert hall stage with a red curtain, a spotlight and an empty piano.

## Order if quota runs out again

A (the ten poses) first, then B, then C garden, then D, then the rest of C, then E.

## Received 2026-10-18 (batch A)

Nine of ten poses plus a clean sitting base, saved as art/mascot/pup-*.jpg: listening, crown, pointing (sideways,
not down), flying, goodnight, dancing, wear-base, sticker, drumming, head-tilt. Missing: clapping.

## A2. Alive and reacting (next batch, same character line)

Idle moments (the pup does these on its own when the child does nothing for ten seconds) and reactions:

| File | Pose sentence |
|---|---|
| pup-clapping | sitting up on its hind legs, front paws together mid-clap, big grin |
| pup-pointing-down | standing, pointing straight down with one front paw, looking at the viewer, encouraging smile |
| pup-sneeze | sitting, mid-sneeze, eyes shut, nose scrunched, ears flying up |
| pup-tail-chase | curled round chasing its own tail, tongue out, seen from the side |
| pup-scratch | sitting, scratching behind one ear with a hind leg, eyes half closed |
| pup-butterfly | sitting, looking up at a small blue butterfly just above its nose, eyes wide |
| pup-surprised | sitting, eyes huge, mouth a round O, ears straight up, both front paws raised (a rare sticker just dropped) |
| pup-thinking | sitting, one paw on its chin, eyes looking up and to the side, mouth small |
| pup-shh | sitting, one paw held in front of its mouth, eyes closed, calm (a listening moment) |
| pup-peek | peeking in from the left edge of the frame, only the head and one paw visible, mischievous smile |
| pup-treat | sitting, holding a cartoon bone-shaped biscuit in both paws, about to bite it, eyes shining |
| pup-sleeping | curled up asleep on the floor, no pillow, a small "z" shape not needed, peaceful |

## A3. Profile avatars (eight)

> The same character's head and shoulders only, facing the camera, happy, centred, plain white background, square.
> Make eight versions that differ only in the colour of the striped sweatband: red, blue, green, purple, yellow,
> pink, orange, teal.

Files: art/mascot/avatar-<colour>.png.

## A4. World pieces, folder art/world/ (flat vector, same outline and shading style as the pup, white background)

dog-house-1 (small plain wooden dog house), dog-house-2 (the same with a red roof and a flower box),
dog-house-3 (the same with a chimney, a flag and a porch light), treat-jar-empty, treat-jar-half, treat-jar-full
(a glass jar of bone biscuits), egg-closed (a pastel spotted egg), egg-cracking (the same egg with a crack and light
inside), egg-hatched (the shell halves open, empty), stage-empty (a small wooden stage with a red curtain),
audience (five small cartoon animals sitting in a row watching: a bird, a frog, a duckling, a turtle, a ladybird).

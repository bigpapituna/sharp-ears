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

## B2. The 2.5 wardrobe, folder art/mascot/, file pup-wear-<id>.png

Fifty-four more things to wear (engine/world/Wardrobe.kt; the six of section B stay). Same base pose and same
character line as section B: "Sitting facing the camera, the exact sitting pose of figure 1 in the attached image,
happy open-mouth smile." Then add the one item below. Keep the red and white sweatband under every hat (the headbands
replace it). One image per item, square, plain white background. The app draws these in code until they land; the
painted ones replace the code-drawn item one for one (ui/world/Outfit.kt and Wear*.kt, `Wear.box` gives each item's
place on the base image).

| File | Name | Slot | Rarity | Earned by | Item |
|---|---|---|---|---|---|
| pup-wear-boots | Puddle Jumper | back | common | practice track 7 | wearing shiny red rain boots on the front paws |
| pup-wear-beanie | Snow Day Pup | head | common | practice track 8 | wearing a blue knitted bobble beanie with a yellow pompom, covering the sweatband |
| pup-wear-freckles | Freckle Face | face | common | practice track 9 | wearing a sprinkle of light brown freckles on both cheeks |
| pup-wear-band-pink | Pink Power | head | common | practice track 10 | wearing a pink sweatband with a white stripe in place of the red one |
| pup-wear-hoodie | Snuggle Pup | body | common | practice track 11 | wearing a soft teal hoodie with the hood down, drawstrings and a front pocket |
| pup-wear-starglasses | Star Eyes | face | common | practice track 12 | wearing pink star-shaped party glasses |
| pup-wear-backpack | Adventure Pup | back | common | practice track 13 | wearing a little orange backpack, straps over both shoulders |
| pup-wear-band-blue | Blue Sky | head | common | practice track 14 | wearing a sky blue sweatband with a white stripe in place of the red one |
| pup-wear-chefhat | Chef Pupcake | head | common | practice track 15 | wearing a tall puffy white chef's hat |
| pup-wear-raincoat | Splashy Pup | body | common | practice track 16 | wearing a yellow raincoat with dark toggles, the hood down |
| pup-wear-skates | Zoom Zoom | back | common | practice track 17 | wearing white roller skates with pink laces and mint wheels on the front paws |
| pup-wear-bee | Buzzy Bee | body | common | practice track 18 | wearing a yellow and black striped bee costume with tiny see-through wings |
| pup-wear-antennae | Buzzy Bee | head | common | practice track 18 | wearing a headband with two black bee antennae with yellow tips |
| pup-wear-band-green | Leafy Green | head | common | practice track 19 | wearing a leaf green sweatband with a white stripe in place of the red one |
| pup-wear-tutu | Twirly Pup | body | common | practice track 20 | wearing a fluffy pink tutu round the waist |
| pup-wear-slippers | Twirly Pup | back | common | practice track 20 | wearing pink ballet slippers with ribbons crossing up the front legs |
| pup-wear-heartglasses | Lovely Eyes | face | common | practice track 21 | wearing red heart-shaped glasses |
| pup-wear-cowboyhat | Rodeo Pup | head | common | practice track 22 | wearing a tan cowboy hat with a dark band and a gold stud |
| pup-wear-bandana | Rodeo Pup | body | common | practice track 22 | wearing a red bandana with white dots tied round the neck |
| pup-wear-band-yellow | Sunny Side | head | common | practice track 23 | wearing a sunny yellow sweatband with a white stripe in place of the red one |
| pup-wear-monocle | Professor Pup | face | common | practice track 24 | wearing a gold monocle on one eye with a little chain |
| pup-wear-snowman | Frosty Pup | body | common | practice track 25 | wearing a round white snowman suit with three coal buttons and a red scarf |
| pup-wear-carrotnose | Frosty Pup | face | common | practice track 25 | wearing an orange carrot nose over its own nose, like a snowman |
| pup-wear-band-purple | Purple Magic | head | common | practice track 26 | wearing a lilac purple sweatband with a white stripe in place of the red one |
| pup-wear-pumpkin | Pumpkin Pup | body | common | practice track 27 | wearing a round orange pumpkin suit with a leafy green collar |
| pup-wear-dino | Dino Pup | body | common | practice track 28 | wearing a green dinosaur onesie with a cream belly, orange spikes and a tail |
| pup-wear-dinocap | Dino Pup | head | common | practice track 28 | wearing a green dinosaur hood with orange spikes along the top |
| pup-wear-cape-blue | Midnight Cape | body | common | practice track 29 | wearing a blue cape fastened with two gold clasps at the shoulders |
| pup-wear-whiskers | Kitty Pup | face | common | practice track 30 | wearing drawn-on cat whiskers and a little nose dot pattern |
| pup-wear-flowercrown | Flower Fairy | head | rare | finale of the garden | wearing a crown of small pink, yellow, blue and white flowers with leaves |
| pup-wear-mermaid | Merpup | body | rare | finale of the pond | wearing a teal scaly mermaid tail over the legs with a fin, and two lilac seashells on the chest |
| pup-wear-ballerina | Prima Pupperina | body | rare | finale of the concert hall | wearing a pink ballerina dress with a fitted top, a wide layered tutu and a bow at the waist |
| pup-wear-guitar | Rock Star Pup | back | rare | finale of the workshop | wearing a tiny orange acoustic guitar slung on the back, its red strap across the chest |
| pup-wear-wings | Fairy Pup | back | rare | finale of the treehouse | wearing pastel pink and blue fairy wings with sparkles |
| pup-wear-astronaut | Space Pup | body | rare | finale of the night sky | wearing a white astronaut suit with a chest control panel and a star patch |
| pup-wear-spacehelmet | Space Pup | head | rare | finale of the night sky | wearing a clear round space helmet over the whole head on a silver collar |
| pup-wear-rocketpack | Space Pup | back | rare | finale of the night sky | wearing a silver rocket pack with red tips and little flames |
| pup-wear-lion | Brave Lion | body | rare | finale of the fair | wearing a big fluffy golden lion's mane round the head |
| pup-wear-herosuit | Super Pup | body | rare | 7 days in a row | wearing a blue superhero suit with a yellow star badge, a yellow belt and a short red cape |
| pup-wear-heromask | Super Pup | face | rare | 7 days in a row | wearing a red superhero eye mask with ties fluttering behind |
| pup-wear-cape-red | Super Cape | body | rare | 14 days in a row | wearing a red superhero cape fastened with two gold clasps |
| pup-wear-knight | Sir Pup | body | rare | 10 three-star lessons | wearing silver knight armour with round shoulder plates and a red shield with a gold cross on the chest |
| pup-wear-knighthelmet | Sir Pup | head | rare | 10 three-star lessons | wearing a silver knight's helmet with a red plume, the face open |
| pup-wear-princess | Princess Pup | body | rare | 25 three-star lessons | wearing a long lilac princess dress with puffed sleeves and a pink sash with a gold rosette |
| pup-wear-piratehat | Captain Pup | head | rare | 20 songs to the end | wearing a black pirate tricorn hat with gold trim and a friendly white skull |
| pup-wear-eyepatch | Captain Pup | face | rare | 20 songs to the end | wearing a black pirate eye patch |
| pup-wear-doctor | Doctor Pup | body | rare | 40 days practised | wearing a white doctor's coat with a pocket and a pen, a stethoscope round the neck |
| pup-wear-headmirror | Doctor Pup | head | rare | 40 days practised | wearing a doctor's head band with a round silver mirror on the forehead |
| pup-wear-firefighter | Fire Chief Pup | body | rare | 60 songs to the end | wearing a tan firefighter coat with silver reflective stripes and buckles |
| pup-wear-firehelmet | Fire Chief Pup | head | rare | 60 songs to the end | wearing a red firefighter helmet with a gold badge on the front shield |
| pup-wear-halo | Little Angel | head | shiny | 3 pages | wearing a glowing golden halo floating above the head, with sparkles |
| pup-wear-crown | King of the Road | head | shiny | the big kids' road | wearing a gold crown with five points and red, blue and green gems |
| pup-wear-wizardhat | Star Wizard | head | shiny | 30 days practised | wearing a tall purple wizard hat with yellow stars, a moon and a gold band |
| pup-wear-rainbowdress | Rainbow Star | body | shiny | 50 three-star lessons | wearing a dress with a white top and a skirt in rainbow stripes, with sparkles |

For a shiny item add: "a soft golden glow and a few small sparkles around the item".

## C2. The stones' own stickers, folder art/stickers/, file lv-<lesson id>.png (82, one per prompt)

One per Playground lesson and per Basics lesson on the big kids' road (engine/world/LevelStickers.kt), earned the
first time its stone is finished and stuck on the map by the kid. Same style line as section C. The placeholders are
res/drawable-nodpi/sticker_lv_<lesson id, - as _>.webp from scripts/gen_stickers.py --levels; save the painted ones
as art/stickers/lv-<lesson id>.png and they replace them one for one. No letters or numbers on any of them. Each
page's concert medal is a gold medal with a star, on a ribbon in the page's colour.

**garden**: playground-1 a see-saw, one end up and one down; playground-2 piano keys: two black, then three black; playground-3 a red cat, C's animal; playground-4 three paw prints walking up the steps; playground-5 a hot cross bun; playground-6 a parrot that copies; playground-7 two crossed drumsticks; playground-8 a fluffy little lamb; playground-9 a cosy mitten for the left paw; playground-10 a goat, G's animal; playground-11 a soft feather; playground-12 the garden's concert medal.

**pond**: pond-1 a twinkling star with a face; pond-2 a rowing boat with oars; pond-3 a frog singing; pond-4 a drum on a lily pad; pond-5 music notes, a ta and a ti-ti; pond-6 a farm cow; pond-7 stepping stones over the water; pond-8 a high five: two paws together; pond-9 a triple scoop: three notes at once; pond-10 London Bridge; pond-11 a wishing wand; pond-12 the pond's concert medal.

**hall**: hall-1 three letter blocks (no letters: colours for C, D, E); hall-2 a happy note; hall-3 a little sailboat; hall-4 a cap with a feather in it; hall-5 a hand print; hall-6 a little mouse, quiet; hall-7 a trumpet; hall-8 a soft pillow for the rest; hall-9 a xylophone; hall-10 two hearts together; hall-11 a song book; hall-12 a rose for the recital.

**workshop**: workshop-1 a piano bench; workshop-2 soap bubbles; workshop-3 a finger puppet; workshop-4 a little house; workshop-5 a mailbox with a letter; workshop-6 a thumbs up; workshop-7 a toy car on a bridge; workshop-8 an elephant, heavy and gentle; workshop-9 a bouncy ball; workshop-10 a sneaker on a pedal; workshop-11 a tin can phone; workshop-12 the workshop's concert medal.

**treehouse**: treehouse-1 a caterpillar inching along; treehouse-2 a marching ant; treehouse-3 a hedgehog, tucked and round; treehouse-4 little weights; treehouse-5 hopscotch squares; treehouse-6 a bunch of grapes; treehouse-7 a fox; treehouse-8 a hand mirror; treehouse-9 a monkey; treehouse-10 an echo bat; treehouse-11 a magnifying glass; treehouse-12 the treehouse's concert medal.

**nightsky**: night-1 a ticking alarm clock; night-2 two maracas; night-3 a sleepy bear; night-4 a metronome; night-5 a rocket going up; night-6 odd socks; night-7 a thunder cloud; night-8 a racing rabbit; night-9 a long snake; night-10 an umbrella, sunny or rainy; night-11 a black cat; night-12 the night sky's concert medal.

**fair**: fair-1 a bunch of balloons; fair-2 a pinwheel; fair-3 the strongman's bell; fair-4 a camera.

**road (Basics)**: sit a cushion; find-c a treasure map; fingers-right a glove; fingers-left two mittens on a string; beat a conductor's baton; white-keys an alphabet train: an engine and two cars.

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

# Team E (Motion and delight): what to wire

Branch `revamp/motion`. Everything below works with today's call sites unchanged: every new parameter has a
default, and nothing new appears until it is wired. Every effect is skipped or reduced when `reducedMotion()` is
true (the system's "Remove animations").

Files owned: `ui/Keyboard.kt`, `ui/NoteLane.kt`, `ui/HitLights.kt`, `ui/Pup.kt`, `ui/Celebrate.kt`,
`ui/motion/*` (`GhostHand.kt`, `Transitions.kt`, `CountUp.kt`, `MotionDemo.kt`). Tests: `HitLightsTest`,
`ui/motion/CountUpTest`, `ui/motion/GhostHandTest`.

Debug: `adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es screen motion --es demo celebrate|cheer|pup|keys`
shows each effect on its own (`MotionDemo.kt`, the one debug route this team added to MainActivity).

## What already works without wiring

- Keys sink 3 dp and darken while lit, then spring back with a small bounce on release; an idle sheen sweeps the
  keyboard every 7 to 11 s (no frames between sweeps).
- The hit line: 6 to 10 sparks in the hand colour rise from the column of every right note (64-slot pool), a combo
  counter ("x12") shows at the lane's top right from 5 right notes in a row, pulses on each new one and shatters
  on a wrong key. HitLights counts the combo itself from `hit()` and `wrong()`.
- The pup blinks every few seconds and looks around every 6 to 10 s, in every pose, everywhere.
- Celebration: dust puff on landing, stats count up from zero, the flourish no longer delays the entrance.
- CheerBar: the pup pops over the bar edge with a spring overshoot; the title types in (18 ms a letter).

## PlayerScreen (one-line changes; verified on emulator-5556 then reverted, the full diff is below)

1. Ghost hand over the keyboard. In `Keys()`, wrap the Keyboard in a Box and draw the hand on top:

   ```kotlin
   val ghost = remember(song, fingers, practice, mode) { ghostNotes(song, fingers) { mode == Mode.LISTEN || transport.required(it) } }
   Box(modifier) {
       Keyboard(..., modifier = Modifier.fillMaxSize().then(/* the wrongVisible border */), sheen = !playing)
       GhostHand(ghost, { position }, range.first, range.last, Modifier.matchParentSize(), visible = prefs.ghostHand)
   }
   ```

   Fingers come from `engine/Fingering.of(song)` (the `fingers` map PlayerScreen already has; lessons with a
   book fingering go through `Song.fingering`). Pass `{ position }` as a lambda: the hand reads it every frame
   in its own loop without recomposing, and its loop sleeps while the playhead stands still. Suggested setting:
   `Prefs.ghostHand` (Settings > Falling notes > Ghost hand), on by default for the Basics and Beginner levels.
   The hand covers the key labels a little at 60%, so lessons that hide labels lose nothing.
2. `sheen = !playing` on the Keyboard (keeps the glint off while notes fall).
3. Perform grades ("Perfect" / "Good" floating up from the line). In `keyStruck`:

   ```kotlin
   val good0 = transport.score?.recap()?.good ?: 0
   val result = transport.noteInput(midi, latencyMs)
   val grade = if (transport.mode == Mode.PERFORM) (if ((transport.score?.recap()?.good ?: 0) > good0) HitLights.PERFECT else HitLights.GOOD) else null
   ...
   lights.hit(midi, handColor(h), grade)
   ```

   (Cleaner, if the integration pass may touch engine/: have `Transport.noteInput` keep `lastGrade: Grade?`.)
4. Perform streak: only misses break it, so in `wrongFeedback()` use
   `lights.wrong(breaksCombo = transport.mode != Mode.PERFORM)` and in the frame loop, next to the
   `listenFor` check: `if (mode == Mode.PERFORM) transport.score?.let { lights.streak(it.streakNow) }`.
   The counter then matches the bar's "Streak N" and shatters on a miss.
5. `seek()`: add `lights.resetCombo()` so a new run starts at zero without a shatter.
6. Haptics (touch input only; a buzz is meaningless with the phone on the music stand): `val haptics = rememberHaptics()`,
   then in the touch `onNoteOn`, after `keyStruck`: right note -> `haptics.tick()`, wrong -> `haptics.miss()`;
   `haptics.success()` when a run finishes with a recap. Gate with `haptics.enabled = prefs.haptics`.

Do not pass `flash` to the player's Keyboard: the player's rule is "no green keys, no red border" (feedback lives
on the hit line). `flash` is for the chord screens and games, which colour keys on purpose.

Measured on emulator-5556 (debug build, twinkle wait mode with injected mic notes, 10 s window,
`dumpsys gfxinfo ... | findstr Janky`), with other teams building on the same host:

| Build | Janky frames | p50 |
|---|---|---|
| Before (main) | 2.13%, 3.99%, 0.77% | 21 to 22 ms |
| After, PlayerScreen untouched (keys, lane, lights) | 0.99%, 1.03%, 1.00% | 20 ms |
| After, all wired incl. ghost hand (interleaved with ghost off) | on 1.83, 3.77, 1.03, 9.51% / off 2.36, 1.08, 1.34, 2.35% | on 20 to 23 / off 20 to 28 ms |

The first ghost-hand version drew each hand through an offscreen layer and cost about 5 ms p50; it now draws
straight onto the keys. The 9.5% run is a single outlier while the host was loaded. Check on the Fold with the
profile build before release.

## Pup

- Home (LibraryScreen greeting): `PupSays(pose, line, ..., onTap = { /* optional: a random encouraging line */ }, sleepyAfterMs = 30_000)`.
  The timer restarts on a pose change or a tap; for "idle on the home screen" the integration can also key the
  call on the last interaction if it wants touches elsewhere to wake him.
- `Pup(pose, onTap = {})` anywhere he should wiggle when tapped. Without `onTap` he takes no touches, so he
  never steals a tap from a clickable row around him.

## Celebration and CheerBar

```kotlin
Celebration(title, line, stats, primary, onPrimary, ..., newBest = true, xpGained = 120, xpBar = XpBar(from = 0.7f, to = 1.25f, caption = "Level 4"))
```

- `newBest`: a slow sunburst and stars behind the pup (use it for a Perform best, a game best, a sight-reading best).
- `xpGained`: "+120 XP" rolls up under the stats with a bar. `xpBar` is level progress as fractions of a level
  (Team B defines XP): a `to` above 1 fills the bar, flashes and wraps, a level up. Without `xpBar` the bar just
  fills alongside the number.
- Level-up celebration: `Celebration("Level 5!", ..., pose = Pose.LEVEL_UP, xpGained = n, xpBar = XpBar(f, 1f + g, "Level 5"))`.
- Stat values count up when they hold a number: "3:12" counts as a time, otherwise the first number counts and the
  rest stays ("95%", "8/10", "1.5 s"). Screen readers get the final value.
- The PlayerScreen recap dialog (RecapCard + Confetti on a new best) is not a Celebration; if the integration pass
  moves Perform results to a Celebration, pass `newBest = r.newBest`.
- CheerBar: nothing to wire.

## Screen transitions (MainActivity, HomeShell)

`ui/motion/Transitions.kt`:

- `ScreenMotion.forward(still)`, `back(still)`, `curtain(still)` (into or out of the player: the old screen dims
  away, a beat of dark stage, the player fades up from 104%), `tab(still)`.
- `screenTransitions(still, isStage, depth)` builds one `transitionSpec` for a whole stack.

MainActivity: wrap the `when (val s = screen)` in

```kotlin
val still = reducedMotion()
AnimatedContent(screen, label = "screen", contentKey = { it::class to (it as? Screen.Home)?.tab },
    transitionSpec = screenTransitions(still,
        isStage = { it is Screen.Player || (it is Screen.Daily && !it.between) },
        depth = { when (it) { is Screen.Home -> 0; is Screen.Player, is Screen.Lesson, is Screen.SightRead, is Screen.Daily -> 2; else -> 1 } })) { s ->
    when (s) { ... }   // use s, never the outer `screen`, inside
}
```

Notes: during a transition both screens are composed for ~0.5 s, so two BackHandlers and two LaunchedEffects can
overlap briefly; PlayerScreen's orientation lock runs on enter and its onDispose after the exit, which is fine.
Keep `contentKey` coarse (class plus tab) so state changes inside a screen (e.g. Daily's index, Player's seek)
do not animate as screen changes. HomeShell: `AnimatedContent(tab, transitionSpec = { ScreenMotion.tab(still) })`
around the tab body.

Also in `Transitions.kt`:

- `Modifier.pressScale()` for tappable rows and tiles (scales to 0.97 while pressed, springs back; only watches the
  touch). Put it before `.clickable`: `Modifier.pressScale().clickable { ... }`. Good homes: library rows, Learn
  path nodes, Practice and You tiles.
- `Shimmer(modifier, shape)`: a loading placeholder (the library while index.json and imports load, the You
  charts while stats compute).
- `Haptics` / `rememberHaptics()`: `tick()` (rate-limited to one per 40 ms), `miss()` (double tap), `success()`.
  They follow the phone's touch-feedback setting; `enabled` is the app switch.

## Known gaps

- The ghost hand is a top-down hint, not an anatomical hand: fingers sit one white key apart, so wide stretches
  (octaves, chords past a fifth) show the hand centred between its notes with fingers off the keys.
- Notes without a finger (chords wider than five notes past the fifth) dip no finger; the hand still moves.
- Pup "sleepy" uses its own timer (pose change or tap resets it), not global touches.
- Haptics are not wired anywhere yet (needs the PlayerScreen and Prefs changes above).

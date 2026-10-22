package com.bigpapituna.sharpears.ui.arcade

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.arcade.ArcadeGame
import com.bigpapituna.sharpears.engine.arcade.ArcadeLines
import com.bigpapituna.sharpears.engine.arcade.ArcadePlans
import com.bigpapituna.sharpears.engine.arcade.ArcadeRound
import com.bigpapituna.sharpears.engine.arcade.ArcadeSongs
import com.bigpapituna.sharpears.engine.arcade.ArcadeStars
import com.bigpapituna.sharpears.engine.arcade.CallPlan
import com.bigpapituna.sharpears.engine.arcade.CallRound
import com.bigpapituna.sharpears.engine.arcade.Cue
import com.bigpapituna.sharpears.engine.arcade.RoundEnd
import com.bigpapituna.sharpears.engine.arcade.RoundResult
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.HeardBy
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.StageTime
import com.bigpapituna.sharpears.engine.world.LevelSticker
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.Stage
import com.bigpapituna.sharpears.ui.onScreen
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.play.ExitPill
import com.bigpapituna.sharpears.ui.playground.BigKeys
import com.bigpapituna.sharpears.ui.playground.Painted
import com.bigpapituna.sharpears.ui.playground.PaintedPup
import com.bigpapituna.sharpears.ui.playground.StageLine
import com.bigpapituna.sharpears.ui.playground.rememberFleeting
import com.bigpapituna.sharpears.ui.playground.rememberStageEar
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * One round of an arcade game on screen (ui/arcade): the pup says what to do, shows the keys (the mic deaf), then the
 * round runs on the frame clock while the Playground's ear listens for its keys (strict, at most twelve). Every key is
 * answered: the stage's ripple on the keys, the pup's face, and the game's own picture. The on-screen keys, a MIDI
 * keyboard and the debug injection (PianoSynth) count the same as the piano. At the end: stars, the game's sticker the
 * first time it earns three, and Again or the hub.
 */

private enum class Phase { INTRO, PLAY, FINISH }

/**
 * A round of [game] at [level] ([seed] draws it). [onRoundEnd] is called once with the result and returns what it did
 * (the book's [RoundEnd]); [sticker] is the game's sticker, shown when this round earned it. [debugResult] (debug builds)
 * skips straight to the finish with that result.
 */
@Composable
fun ArcadeGameScreen(
    game: ArcadeGame, level: Int, seed: Long, deps: LessonDeps, inject: String?, sticker: LevelSticker?,
    onRoundEnd: (RoundResult) -> RoundEnd, onAgain: () -> Unit, onGames: () -> Unit, onExit: () -> Unit,
    debugResult: RoundResult? = null,
) {
    val reader = LocalProfile.current.let { !it.isKid || it.reader }
    val play = remember(game, level, seed) { ArcadePlay(ArcadePlans.round(game, level, seed), reader) }
    val round = play.round
    var phase by remember(play) { mutableStateOf(if (debugResult != null) Phase.FINISH else Phase.INTRO) }
    val scope = rememberCoroutineScope()
    val voice = deps.voice
    val mic = deps.mic
    fun now() = SystemClock.uptimeMillis()
    val endNow by rememberUpdatedState(onRoundEnd)
    // The caller makes new deps as the sampler gets ready: read the latest (a cold start opens a round before it is).
    val depsNow = rememberUpdatedState(deps)

    fun say(text: String) { play.show(text); if (phase == Phase.PLAY) voice.speakOverMic(text) else voice.speak(text, interrupt = true) }

    /** The pup plays [keys] for the kid ([gapMs] apart), the mic deaf meanwhile; [light] rings each gold as it sounds. */
    fun pupPlays(keys: List<Int>, gapMs: Long, leadMs: Long = 0, light: Boolean = true) = scope.launch {
        delay(leadMs)
        mic.deafFor(keys.size * gapMs + 900)
        play.demoKeys = keys
        for ((i, k) in keys.withIndex()) {
            if (deps.prefs.sound) deps.sampler.noteOn(k, 80)
            if (light) play.fx.ripple(k, PupGold, fill = true)
            play.demoIndex = i
            delay(gapMs)
            deps.sampler.noteOff(k)
        }
        delay(300)
        play.demoKeys = emptyList(); play.demoIndex = null
    }

    fun handle(cues: List<Cue>) {
        val t = now()
        for (c in cues) {
            when (c) {
                is Cue.Demo -> {
                    play.react(Painted.POINTING, c.leadMs + c.keys.size * c.gapMs + 600, t)
                    if (c.first) { play.show(PlaygroundLines.LET_ME_SHOW); voice.speakOverMic(PlaygroundLines.LET_ME_SHOW) } else play.show(ArcadeLines.WATCH)
                    pupPlays(c.keys, c.gapMs, c.leadMs)
                }
                is Cue.Listen -> {
                    play.react(Painted.LISTENING, 0, t)
                    if (c.first) { play.show(PlaygroundLines.YOUR_TURN); voice.speakOverMic(PlaygroundLines.YOUR_TURN) }
                    else if (round is CallRound) play.show(ArcadeLines.GO)
                }
                is Cue.Spawned -> if (c.helped) { play.lit = play.lit + c.target.keys; play.show(ArcadeLines.HELP) }
                is Cue.Hit -> {
                    play.played[c.target.id] = true
                    play.lit = play.lit - c.target.keys.toSet()
                    c.target.keys.forEach { play.fx.found(it) }
                    play.add(Fx(FxKind.HIT, c.target, c.key, t, c.target.phrase))
                    play.react(if (c.streak >= 5 && c.streak % 5 == 0) Painted.DANCING else Painted.CLAPPING, 650, t)
                    if (c.streak >= 5 && c.streak % 5 == 0) play.show(ArcadeLines.STREAK[(c.streak / 5 - 1) % ArcadeLines.STREAK.size])
                    if (hitSound(game)) mic.deafFor(220)
                }
                is Cue.Half -> { play.fx.found(c.key); play.add(Fx(FxKind.HALF, c.target, c.key, t)) }
                is Cue.Slip -> {
                    play.fx.wrong(c.key, c.near)
                    play.add(Fx(FxKind.SLIP, null, c.key, t))
                    play.react(Painted.HEAD_TILT, 900, t)
                    if (round is CallRound) play.show(ArcadeLines.SLIP)
                }
                is Cue.Expired -> {
                    play.played[c.target.id] = false
                    play.lit = play.lit - c.target.keys.toSet()
                    play.add(Fx(FxKind.GONE, c.target, c.target.key, t))
                    play.react(Painted.SURPRISED, 600, t)
                }
                is Cue.Nudge -> {
                    play.lit = play.lit + c.target.key
                    if (c.beat >= 2) pupPlays(listOf(c.target.key), 500)
                }
                is Cue.LetGo -> {
                    play.played[c.target.id] = false
                    play.lit = play.lit - c.target.key
                    play.add(Fx(FxKind.LET_GO, c.target, c.target.key, t, c.target.phrase))
                    play.react(Painted.POINTING, 900, t)
                    play.show(PlaygroundLines.LET_GO)
                    pupPlays(listOf(c.target.key), 450)
                }
                is Cue.LineDone -> {
                    play.add(Fx(FxKind.LINE, null, -1, t, c.phrase))
                    play.react(Painted.DANCING, 1_100, t)
                    if (lineSound(game)) mic.deafFor(400)
                    val call = round as? CallRound
                    call?.let { afterLine(it.plan, c.phrase, it.linesDone) }?.let(::say)
                    if (game == ArcadeGame.TOWER && call != null) {
                        val blocks = call.hits
                        if (blocks == ArcadeStars.clouds(level)) play.show(ArcadeLines.CLOUDS)
                        else if (blocks == ArcadeStars.clouds(level) + 6) play.show(ArcadeLines.HIGHER)
                    }
                }
                is Cue.Rest -> { say(ArcadeLines.FINISH_REST); pupPlays(c.keys, 260, leadMs = 900) }
                is Cue.Finished -> {
                    Log.d(TAG, "arcade: ${game.id} level $level ended: ${c.result.hits} of ${c.result.offered}, ${c.result.slips} slips, " +
                        "${c.result.lines} lines, ${c.result.stars} stars, ${c.result.durationMs} ms")
                    play.result = c.result
                    play.end = endNow(c.result)
                    phase = Phase.FINISH
                }
            }
        }
        if (cues.isNotEmpty()) play.version++
    }

    fun key(midi: Int, how: String) {
        val t = now()
        if (!round.listening) return
        val cues = round.key(midi, t)
        if (cues.isNotEmpty()) Log.d(TAG, "arcade: $how ${noteName(midi)} -> ${cues.joinToString { it::class.simpleName ?: "" }}")
        handle(cues)
    }

    // The round: the intro said (and the song named), then the demo, then the frame clock till it ends.
    LaunchedEffect(play) {
        if (debugResult != null) { play.result = debugResult; play.end = endNow(debugResult); return@LaunchedEffect }
        snapshotFlow { depsNow.value.ready }.first { it }
        val intro = ArcadeLines.intro(game, reader, level)
        play.show(intro); voice.speak(intro, interrupt = true)
        if (game == ArcadeGame.FIREWORKS || game == ArcadeGame.PAINTER) { delay(300); voice.speak(ArcadeLines.song(ArcadeSongs.of(level))) }
        withTimeoutOrNull(9_000) { delay(400); snapshotFlow { voice.speaking.value }.first { !it } }
        delay(300)
        Log.d(TAG, "arcade: ${game.id} level $level starts, keys ${round.keys.sorted().map(::noteName)}")
        phase = Phase.PLAY
        handle(round.start(now()))
        while (!round.over) {
            withFrameNanos { }
            val t = now()
            play.now = t
            if (play.poseUntil in 1..t) { play.poseUntil = 0; play.pose = Painted.LISTENING }
            handle(round.tick(t))
        }
    }
    // The finish: the ding, then the pup's words (the mic is off by now).
    LaunchedEffect(play, phase) {
        if (phase != Phase.FINISH) return@LaunchedEffect
        snapshotFlow { play.end }.first { it != null }
        val end = play.end!!
        WorldSounds.ding()
        delay(500)
        voice.speak(ArcadeLines.finish(end.stars), interrupt = true)
        if (end.firstThree) { voice.speak(ArcadeLines.STICKER); sticker?.let { voice.speak(it.line) } }
        else if (end.paintedNew) voice.speak(ArcadeLines.PAINTED)
        else if (end.levelAfter > end.levelBefore) voice.speak(ArcadeLines.HARDER)
    }

    // The ear: the round's keys, strict; a chord's keys each their own sound when two at once are wanted.
    val listening = rememberStageEar(mic, voice, phase == Phase.PLAY && !round.over, inject, round.keys, chords = round.pairs) { s ->
        play.fx.heard(s)
        if (s.final && (s.by == HeardBy.MATCHER || s.by == HeardBy.NAMED)) s.midi?.let { key(it, if (s.by == HeardBy.MATCHER) "heard" else "named") }
    }
    LaunchedEffect(deps.midi) { deps.midi.events.collect { e -> if (e is NoteEvent.On && phase == Phase.PLAY) key(e.midi, "midi") } }
    if (phase == Phase.PLAY) ArcadePractice(deps, game)
    DisposableEffect(play) { onDispose { (round.keys + play.demoKeys).forEach { deps.sampler.noteOff(it) } } }

    fun touch(m: Int) {
        if (deps.prefs.sound && (!listening || mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(500); deps.sampler.noteOff(m) } }
        if (phase == Phase.PLAY) key(m, "touched")
    }

    SharpEarsTheme(dark = true) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Stage).safeDrawingPadding()) {
            val roomH = maxHeight
            val spec = remember(game) { GameSpec.of(game) }
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides com.bigpapituna.sharpears.ui.Ink) { ExitPill(onExit) }
                    Spacer(Modifier.width(12.dp))
                    RoundClock({ if (phase == Phase.INTRO) 0f else round.progress(play.now) }, Modifier.weight(1f).height(40.dp))
                }
                val line = rememberFleeting(play.lineSeq, play.line, ms = 2_400) ?: if (play.demoKeys.isNotEmpty()) ArcadeLines.WATCH else null
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!spec.pupInField) {
                        PaintedPup(play.pose, size = (roomH * 0.1f).coerceIn(52.dp, 84.dp), poke = play.fx.poke)
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) { StageLine(line) }
                }
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))) {
                    spec.field(play, Modifier.fillMaxSize(), ::touch)
                }
                if (!spec.ownKeys) {
                    val (low, high) = remember(round) { keyRange(round.keys) }
                    Spacer(Modifier.height(8.dp))
                    ArcadeKeys(play, low, high, level, Modifier.fillMaxWidth().height((roomH * 0.2f).coerceIn(92.dp, 160.dp)), ::touch)
                }
            }
            if (phase == Phase.FINISH) play.result?.let { r ->
                ArcadeFinish(game, r, play.end, sticker?.takeIf { play.end?.firstThree == true }, onAgain = onAgain, onGames = onGames)
            }
        }
    }
}

/** The words after line [line] of [plan] (the [done]th line finished): what comes next in a song, else the game's cheer. */
internal fun afterLine(plan: CallPlan, line: Int, done: Int): String? {
    val next = plan.lines.getOrNull(line + 1) ?: return null
    if (plan.song) {
        val song = ArcadeSongs.of(plan.level)
        val whole = plan.lines.indices.filter { plan.lines[it].keys == song.notes && !plan.lines[it].demo }
        return when {
            whole.size > 1 && line + 1 == whole.last() -> ArcadeLines.ENCORE
            line + 1 == whole.firstOrNull() -> ArcadeLines.WHOLE_SONG
            plan.game == ArcadeGame.FIREWORKS && line + 1 == song.bites().size && next.demo -> ArcadeLines.JOINED
            plan.game == ArcadeGame.PAINTER && line + 1 == song.phrases().size -> ArcadeLines.BY_YOURSELF
            plan.game == ArcadeGame.FIREWORKS -> ArcadeLines.lineDone(plan.game, done)
            else -> null
        }
    }
    return ArcadeLines.lineDone(plan.game, done)
}

/** The keyboard shown for [keys]: their white keys and enough around them for eight at least, white at both ends. */
internal fun keyRange(keys: Set<Int>): Pair<Int, Int> {
    var low = keys.min(); var high = keys.max()
    while (isBlack(low)) low--
    while (isBlack(high)) high++
    var add = true
    while ((low..high).count { !isBlack(it) } < 8) {
        if (add) { high++; while (isBlack(high)) high++ } else { low--; while (isBlack(low)) low-- }
        add = !add
    }
    return low to high
}

/**
 * The keys at the bottom: the piano as a map. Up to level two the round's keys wear their colours with the animal (a
 * pre-reader) or the letter (a reader); from level three only the Cs are marked and the kid finds the rest. Keys lit for
 * help, and the demo's key, shine in their colour at any level.
 */
@Composable
internal fun ArcadeKeys(play: ArcadePlay, low: Int, high: Int, level: Int, modifier: Modifier, onKey: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val keys = play.round.keys
    val classes = keys.map { it % 12 }.toSet()
    val sounding = play.demoIndex?.let { play.demoKeys.getOrNull(it) }
    BigKeys(low, high, "Piano keys: play the game on your piano, or tap them here.", modifier,
        fill = { m ->
            when {
                m == sounding || m in play.lit -> keyColour(m)
                level < 3 && m % 12 in classes && !isBlack(m) -> keyColour(m).copy(alpha = 0.42f)
                else -> null
            }
        }, fx = play.fx, onKey = onKey,
        overlay = { g -> labels(g, if (level < 3) classes else setOf(0), play.reader, measurer) })
}

/** Small badges at the bottom of the white keys whose letter is in [classes]. */
private fun DrawScope.labels(g: PianoGeometry, classes: Set<Int>, reader: Boolean, measurer: androidx.compose.ui.text.TextMeasurer) {
    val r = minOf(g.ww * 0.3f, g.height * 0.13f)
    for (m in g.whites) if (m % 12 in classes) {
        val rect = g.rect(m)
        keyBadge(m, Offset(rect.center.x, rect.bottom - r * 1.5f), r, reader, measurer)
    }
}

/**
 * The non-tonal sound a hit makes, true when it made one (the mic is then deaf for it). Call games play their notes
 * quietly till a line ends, as the stages' phrases do: the next note may come at once.
 */
private fun hitSound(game: ArcadeGame): Boolean {
    when (game) {
        ArcadeGame.BALLOONS -> WorldSounds.pop(0.9f + 0.3f * Math.random().toFloat())
        ArcadeGame.FEED -> WorldSounds.munch()
        ArcadeGame.MOLES -> WorldSounds.stagePop()
        ArcadeGame.TOWER -> WorldSounds.tick(0.8f)
        else -> return false
    }
    return true
}

/** The sound of a line done (a pop, a whoosh), true when it made one. The tower's every block is a line: it ticks already. */
private fun lineSound(game: ArcadeGame): Boolean {
    when (game) {
        ArcadeGame.TOWER -> return false
        ArcadeGame.FIREWORKS, ArcadeGame.PAINTER, ArcadeGame.FROGS -> WorldSounds.whoosh()
        else -> WorldSounds.pop(1.3f)
    }
    return true
}

/**
 * While the round plays (and the app is on screen) its time is practice, as a Playground stage's is: written every
 * [StageTime.FLUSH_MS] and when the round ends or the screen goes.
 */
@Composable
private fun ArcadePractice(deps: LessonDeps, game: ArcadeGame) {
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    val from = remember { longArrayOf(0L) }
    fun flush() {
        val secs = ((System.currentTimeMillis() - from[0]) / 1000).toInt()
        if (secs <= 0) return
        from[0] += secs * 1000L
        deps.prefs.addPractice(java.time.LocalDate.now(), secs)
        progress?.record(com.bigpapituna.sharpears.engine.progress.ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))
        Log.d(TAG, "arcade: practice +$secs s (${game.id})")
    }
    if (onScreen()) {
        DisposableEffect(Unit) { from[0] = System.currentTimeMillis(); onDispose { flush() } }
        LaunchedEffect(Unit) { while (true) { delay(StageTime.FLUSH_MS); flush() } }
    }
}

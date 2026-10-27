package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.Lit
import com.bigpapituna.sharpears.engine.newGame
import com.bigpapituna.sharpears.engine.GameKind
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.chordTitle
import com.bigpapituna.sharpears.engine.noteName
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.bigpapituna.sharpears.ui.ear.CardGrid
import com.bigpapituna.sharpears.ui.ear.GameHeader
import com.bigpapituna.sharpears.ui.ear.lowerFirst
import com.bigpapituna.sharpears.ui.ear.missTitle
import com.bigpapituna.sharpears.ui.ear.GameCard
import com.bigpapituna.sharpears.ui.ear.GameSheet
import com.bigpapituna.sharpears.ui.ear.MiniKeys
import com.bigpapituna.sharpears.ui.ear.STAR_AT
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bigpapituna.sharpears.ui.pup.Pose

private const val LOW = 48   // C3
private const val HIGH = 84  // C6

/** Debug start for the games: which game and level to open straight away, and the mic injection. */
data class GameStart(val game: GameKind? = null, val level: Int = 0, val inject: String? = null)

/** Games (Chords > Play before 2.7): the game picker with best scores, or the game being played. Simple shows Build it and Hear it, Advanced adds Major or minor. */
@Composable
fun ChordGamesScreen(sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, prefs: Prefs, micDefault: Boolean, start: GameStart) {
    var playing by remember { mutableStateOf(start.game?.let { it to start.level }) }
    val levels = remember { mutableStateMapOf<GameKind, Int>() }
    var session by remember { mutableIntStateOf(0) }
    val now = playing
    if (now != null) {
        androidx.activity.compose.BackHandler { voice.stop(); playing = null }   // Back ends the game, back to the list
        androidx.compose.runtime.key(now, session) {
            GameScreen(now.first, now.second, sampler, midi, mic, voice, prefs, micDefault, start.inject.takeIf { start.game == now.first },
                onExit = { voice.stop(); playing = null }, onAgain = { voice.stop(); session++ })
        }
        return
    }
    val v = prefs.version.intValue
    var sheet by remember { mutableStateOf<GameKind?>(null) }
    // The layouts (2.8, engine/nav/Simple.kt): Simple has Build it and Hear it, Advanced adds Major or minor, until "Show more".
    val layout = LocalLayout.current.layout
    var more by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val games = com.bigpapituna.sharpears.engine.nav.Simple.games(GameKind.entries, com.bigpapituna.sharpears.engine.nav.Simple.shown(layout, more))
    fun bests(g: GameKind) = g.levels.indices.map { if (v >= 0) prefs.gameBest(g.id, it) else -1 }
    // Each game opens on its first level not yet mastered, until another is picked in its sheet.
    fun levelOf(g: GameKind) = levels[g] ?: bests(g).indexOfFirst { it < STAR_AT }.let { if (it < 0) g.levels.lastIndex else it }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PupSays(Pose.GET_READY, "Pick a game. Each one is ten quick rounds on the keys.", size = 64.dp)
            CardGrid(games.size) { i, m ->
                val g = games[i]
                val level = levelOf(g)
                val b = bests(g)
                GameCard(g.title, level, b.map { it >= STAR_AT }, b[level], onOpen = { sheet = g }, onPlay = { playing = g to level },
                    modifier = m, artOnPanel = true) { ChordArt(g) }
            }
        }
        LayoutMore(com.bigpapituna.sharpears.engine.nav.Simple.Place.GAMES, more, { more = true })
    }
    sheet?.let { g ->
        GameSheet(g.title, g::howToFor, g.levels, bests(g), levelOf(g), onPlay = { l -> levels[g] = l; sheet = null; playing = g to l },
            onDismiss = { sheet = null }, artOnPanel = true) { ChordArt(g) }
    }
}

/** Each chord game's picture: a mini keyboard showing the game's idea. */
@Composable
private fun ChordArt(g: GameKind) = when (g) {
    // A chord building up from its root: C, then E, then G.
    GameKind.BUILD -> MiniKeys { t -> mapOf(60 to 1f, 64 to ((t - 0.25f) * 6).coerceIn(0f, 1f), 67 to ((t - 0.55f) * 6).coerceIn(0f, 1f)) }
    // A chord ringing: all three keys pulse together.
    GameKind.HEAR -> MiniKeys(periodMs = 1600) { t -> val a = 0.45f + 0.55f * kotlin.math.sin(t * Math.PI).toFloat(); mapOf(60 to a, 64 to a, 67 to a) }
    // The third flips between E (major) and E flat (minor).
    GameKind.MAJOR_MINOR -> MiniKeys(periodMs = 2600) { t -> mapOf(60 to 1f, 67 to 1f, (if (t < 0.5f) 64 else 63) to 1f) }
    // Two notes a fifth apart, joined by an arc.
    GameKind.INTERVALS -> MiniKeys(arc = 60 to 67) { t -> mapOf(60 to 1f, 67 to ((t - 0.35f) * 5).coerceIn(0f, 1f)) }
    // A short tune: one key after another.
    GameKind.PLAYBACK -> MiniKeys(periodMs = 3000) { t -> val notes = listOf(60, 62, 64, 65, 67, 64); mapOf(notes[(t * notes.size).toInt().coerceAtMost(notes.lastIndex)] to 1f) }
}

private enum class Phase { PROMPT, ANSWER, REVIEW }

/** How a finished round went, for the cheer bar. */
private class RoundEnd(val text: String, val clean: Boolean)

/**
 * One game of ten rounds, dressed like the ear games: the pup's header (listening while the app plays), keys
 * that burst green across the chord when a round lands, a soft warm flash on a wrong key, and the cheer bar
 * with the pup after each round. The answer shown after a slip reads in the neutral colour: the chord was
 * still found. Each round: the voice says the prompt and the sampler plays the chord (mic off, so it cannot
 * hear the phone), then the mic listens (if on) for the answer; touch and MIDI work throughout the answer.
 */
@Composable
private fun GameScreen(
    kind: GameKind, level: Int, sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, prefs: Prefs, micDefault: Boolean,
    inject: String?, onExit: () -> Unit, onAgain: () -> Unit,
) {
    val game = remember { newGame(kind, level, seed = if (inject != null) 1 else System.nanoTime()) }
    var version by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.PROMPT) }
    var replay by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf<Pair<String, Boolean>?>(null) }   // text, good
    var review by remember { mutableStateOf<RoundEnd?>(null) }
    var missed by remember { mutableStateOf(false) }
    var pings by remember { mutableIntStateOf(0) }
    var newBest by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(micDefault && mic.hasPermission() || inject != null) }
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val flash = remember { mutableStateMapOf<Int, Color>() }               // judged keys: a ripple each
    val scope = rememberCoroutineScope()
    // Settings > Vibration on touch keys; a buzz only ever answers a key touched on the screen.
    val haptics = com.bigpapituna.sharpears.ui.motion.rememberHaptics().also { it.enabled = prefs.haptics }
    // 2.15: through the microphone card; its Not now stays on the keys, quietly.
    val askMic = rememberMicAsk(mic) { if (it) micOn = true else if (!mic.micNotNow) line = MIC_REFUSED_LINE to false }
    val round = remember(version) { game.round }
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    val xpAtStart = remember { progress?.xp ?: 0 }
    val finished = remember(version) { game.finished }

    fun sound() = prefs.sound && (!(micOn && phase == Phase.ANSWER) || mic.headphonesOn())
    suspend fun voiceDone() { delay(250); while (voice.speaking.value) delay(50) }
    suspend fun playChord() {
        val groups = game.toPlay()
        if (groups.isEmpty() || !prefs.sound) return
        Log.d(TAG, "games: play ${game.describe()} " + groups.joinToString(" ") { g -> g.joinToString("+") { noteName(it) } })
        if (groups.size == 1) {                                             // a chord rings
            groups[0].forEach { sampler.noteOn(it, 85) }
            pings++
            delay(1600)
            groups[0].forEach(sampler::noteOff)
        } else groups.forEach { g ->                                        // a melody: one note after another
            g.forEach { sampler.noteOn(it, 85) }
            pings++
            delay(620)
            g.forEach(sampler::noteOff)
            delay(80)
        }
        delay(200)
    }
    fun flashKeys(keys: Collection<Int>, color: Color, ms: Long) {
        keys.forEach { flash[it] = color }
        scope.launch { delay(ms); keys.forEach { if (flash[it] == color) flash.remove(it) } }
    }

    // Each round (and each Replay): prompt and chord with the mic off, then listen.
    LaunchedEffect(round, replay, finished) {
        if (finished) return@LaunchedEffect
        phase = Phase.PROMPT
        if (replay == 0 || kind == GameKind.BUILD) {
            val p = game.prompt()
            Log.d(TAG, "games: ${kind.id} level $level round ${round + 1}: ${p.text} (${game.describe()})")
            voice.speak(p.spoken)
            voiceDone()
        }
        playChord()
        phase = Phase.ANSWER
    }
    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        // Every round skipped: nothing was played. It paid 40 XP, a "Good effort!" and a best of 0; now back to the games.
        if (game.allSkipped) { Log.d(TAG, "games: ${kind.id} level $level every round skipped, nothing earned"); onExit(); return@LaunchedEffect }
        newBest = prefs.saveGameBest(kind.id, level, game.score)
        progress?.record(com.bigpapituna.sharpears.engine.progress.ProgressEvent.GameComplete(kind.id, level, game.score, game.rounds))
        Log.d(TAG, "games: ${kind.id} level $level finished score=${game.score} bestStreak=${game.bestStreak} newBest=$newBest")
        voice.speak("${game.score} out of ${game.rounds}." + if (newBest) " A new best." else "")
    }

    fun strike(m: Int, src: String) {
        if (phase != Phase.ANSWER) return
        val a = game.input(m, System.currentTimeMillis())
        Log.d(TAG, "games: heard ${noteName(m)} src=$src -> ${a.outcome} clean=${a.clean} score=${game.score} streak=${game.streak}")
        when (a.outcome) {
            ChordGame.Outcome.WRONG -> {
                missed = true
                flashKeys(listOf(m), WrongGlow, 450)
                if (src == "touch") haptics.miss()
                a.line?.let { line = it.text to false; voice.speak(it.spoken, deferrable = false) }
            }
            ChordGame.Outcome.FOUND -> { flashKeys(listOf(m), Good, 450); a.line?.let { line = it.text to true } }
            ChordGame.Outcome.DONE -> {
                // The chord's root (pitch class) counts toward the every-key achievements; the melodic games have none.
                progress?.record(com.bigpapituna.sharpears.engine.progress.ProgressEvent.ChordRound((game as? ChordGame)?.target?.root))
                com.bigpapituna.sharpears.data.Briefs.log { com.bigpapituna.sharpears.engine.brief.BriefEvent.EarRound(it, "chord:" + kind.id, a.clean, level) }
                // The success moment: a green burst across every key of the answer (a warm one when it took a slip).
                flashKeys(game.lit().keys + m, if (a.clean) Good else RightHandColor, 900)
                if (a.clean && src == "touch") haptics.success()
                line = null
                review = RoundEnd(a.line!!.text, a.clean)
                voice.speak(a.line.spoken)
                phase = Phase.REVIEW                                   // the mic stops, so the voice can speak
            }
            ChordGame.Outcome.IGNORED -> {}
        }
        version++
    }
    fun goOn() {
        voice.stop()
        review = null; line = null; missed = false; replay = 0
        game.next(); version++
    }

    LaunchedEffect(midi) {
        midi.events.collect { e -> if (e is NoteEvent.On) { touched[e.midi] = RightHandColor; strike(e.midi, "midi") } else touched.remove(e.midi) }
    }
    val visible = onScreen()
    val listen = micOn && visible && phase == Phase.ANSWER && !voice.holdsMic
    var chordStart by remember { mutableLongStateOf(0L) }
    val listening by androidx.compose.runtime.rememberUpdatedState(listen)
    DisposableEffect(listen) {
        if (listen) {
            if (inject != null) mic.startInjection(inject) else if (!mic.start()) micOn = false
            chordStart = 0L
        }
        onDispose { if (listen) mic.stop() }      // only the mic this effect started: never the next screen's
    }
    LaunchedEffect(version, listen) {
        mic.matcher.expected = if (listen) game.micNotes() else emptySet()
        mic.matcher.groups = game.micGroups()
    }
    // Strikes that began before this round's listening started (the previous answer ringing on) do not count.
    LaunchedEffect(round, replay, listen) { chordStart = mic.matcher.samplesProcessed }
    LaunchedEffect(mic) {
        mic.events.collect { e ->
            if (!listening || e !is MicEvent.Strike) return@collect
            val onset = e.sample - ((e.delayMs ?: 0.0) * MicInput.RATE / 1000).toLong()
            if (onset < chordStart) { Log.d(TAG, "games: ignored ${noteName(e.midi)} (struck before this round)"); return@collect }
            strike(e.midi, "mic")
        }
    }

    if (finished) {
        if (game.allSkipped) return
        Celebration(
            when { newBest -> "New best!"; game.score * 2 >= game.rounds -> "Nice playing!"; else -> "Good effort!" },
            "${kind.title}, ${kind.levels[level].lowerFirst()}.",
            listOf(Stat("${game.score}/${game.rounds}", "Score"), Stat("${game.bestStreak}", "Best streak")),
            primary = "Play again", onPrimary = onAgain, secondary = "Games", onSecondary = onExit,
            pose = when { newBest -> Pose.LEVEL_UP; game.score * 2 >= game.rounds -> Pose.GREAT_JOB; else -> Pose.MISTAKE },
            sampler = sampler.takeIf { sound() && game.score * 2 >= game.rounds },
            newBest = newBest,
            xpGained = progress?.let { (it.xp - xpAtStart).takeIf { g -> g > 0 } },
            xpBar = progress?.let { com.bigpapituna.sharpears.ui.profile.xpBarFor(xpAtStart, it.xp) },
        )
        return
    }
    val rev = review
    val plays = game.toPlay().isNotEmpty()
    val pose = when {
        rev != null -> if (rev.clean) Pose.GREAT_JOB else Pose.LEARNING
        phase == Phase.PROMPT && plays -> Pose.LISTENING
        missed -> Pose.MISTAKE
        game.streak >= 3 -> Pose.STREAK
        else -> Pose.GET_READY
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GameHeader(kind.title, "Level ${level + 1}: ${kind.levels[level].lowerFirst()}", null, round, game.rounds, rev != null,
                game.score, game.streak, pose, remember(version) { game.prompt().text }, onExit,
                listening = phase == Phase.PROMPT && plays, pings = pings)
            // Hints and what was wrong, in the neutral colour; a found note in green.
            Text(line?.first ?: if (phase == Phase.PROMPT && plays) "Listen" else "", color = when {
                line == null -> MaterialTheme.colorScheme.primary
                line?.second == false -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.tertiary
            }, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2)
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(micOn, {
                    if (micOn) micOn = false else if (mic.hasPermission()) micOn = true else askMic.ask()
                }, label = { Text(if (micOn) "Listening" else "Use mic") },
                    leadingIcon = { Icon(painterResource(if (micOn) R.drawable.ic_mic else R.drawable.ic_mic_off), contentDescription = null, modifier = Modifier.size(18.dp)) })
                if (plays) OutlinedButton(onClick = { if (phase == Phase.ANSWER) replay++ }, enabled = phase == Phase.ANSWER) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp)); Text("Hear again")
                }
                TextButton(onClick = { voice.stop(); line = null; missed = false; game.skip(); version++ }, enabled = phase != Phase.REVIEW) { Text("Skip") }
            }
            // The how-to shows in the first round only; the hub's sheet has it too.
            if (round == 0) Text(kind.howToFor(level), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val lit = remember(version) {
                game.lit().mapValues { (_, l) -> when (l) { Lit.SHOWN -> RightHandColor.copy(alpha = 0.5f); Lit.GIVEN -> RightHandColor; Lit.FOUND -> Good } }
            }.let { l -> if (rev?.clean == true) l.mapValues { Good } else l } + touched     // a clean round: the whole chord green
            Keyboard(
                LOW, HIGH, lit = lit,
                onNoteOn = { m, vel -> touched[m] = RightHandColor; if (sound()) sampler.noteOn(m, vel); strike(m, "touch") },
                onNoteOff = { m -> touched.remove(m); sampler.noteOff(m) },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                flash = flash,
            )
            Spacer(Modifier.height(if (rev != null) 240.dp else 12.dp))
        }
        if (rev != null) CheerBar(
            if (rev.clean) Cheer.GOOD else Cheer.MISS,
            when {
                rev.clean -> cheerTitle(round + game.score)
                kind == GameKind.BUILD -> "You built it"
                else -> missTitle(round + game.score)
            },
            rev.text + if (!rev.clean && kind == GameKind.BUILD) " Clean next time for the point." else "",
            if (round + 1 >= game.rounds) "See results" else "Continue",
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { goOn() }
    }
}

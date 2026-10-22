package com.bigpapituna.sharpears.ui.play

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.PlayStore
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.play.PupSaysGame
import com.bigpapituna.sharpears.engine.play.PupSaysGame.Answer
import com.bigpapituna.sharpears.engine.play.PupSaysGame.Phase
import com.bigpapituna.sharpears.engine.play.PupSaysPlayer
import com.bigpapituna.sharpears.engine.play.PupSaysResult
import com.bigpapituna.sharpears.engine.play.QuestEvent
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.family.LocalProfiles
import com.bigpapituna.sharpears.ui.family.PupAvatar
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Pup says: the pup plays a pattern on the speaker (mic off), the player copies it on the piano (mic on, the pattern's
 * keys only) or on the keys here, and it grows. Two or three people take turns: the game itself decides whose moment
 * it is (engine/play/PupSays.kt), so a sibling playing early scores nothing and the pup says whose turn it is.
 * Results: everyone's best goes to their own play store (the crown of the day), each copied pattern to their quest.
 */

/**
 * Pup says. Starts with "who's playing" when there is anyone else on the phone. [onFinished] gets everyone's result
 * once the game is over, with each one's best of the day (for the World team's crown and stickers).
 */
@Composable
fun PupSaysScreen(
    mic: MicInput, midi: MidiDevices?, sampler: Sampler?, voice: Voice?, sound: () -> Boolean,
    onExit: () -> Unit, onFinished: (List<PupSaysResult>) -> Unit = {},
) {
    val me = LocalProfile.current
    val others = LocalProfiles.current?.all.orEmpty().filter { it.id != me.id }
    var players by remember { mutableStateOf<List<Profile>?>(if (others.isEmpty()) listOf(me) else null) }
    var round by remember { mutableIntStateOf(0) }
    val chosen = players
    if (chosen == null) {
        val picked = remember { mutableStateListOf<String>() }
        WhoPlays(me, others, picked, onToggle = { if (it in picked) picked.remove(it) else if (picked.size < 2) picked.add(it) },
            onStart = { players = listOf(me) + picked.mapNotNull { id -> others.firstOrNull { it.id == id } } }, onExit = onExit)
    } else androidx.compose.runtime.key(round) {
        PupSaysGameScreen(chosen, mic, midi, sampler, voice, sound, onExit, onFinished, onAgain = { round++ })
    }
}

/** Who takes turns: the player now, and up to two more from the phone's profiles. */
@Composable
internal fun WhoPlays(me: Profile, others: List<Profile>, picked: List<String>, onToggle: (String) -> Unit, onStart: () -> Unit, onExit: () -> Unit) {
    // One readable column, centred on a wide screen (the Fold's inner screen), with the exit pill at the top left.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 76.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Pup says", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            PupSays(Pose.WELCOME, if (picked.isEmpty()) "I play a tune, you copy it. Just you, or take turns?"
                else "Taking turns: ${(listOf(me.name) + others.filter { it.id in picked }.map { it.name }).joinToString(", ")}.")
            PeoplePicker(others, picked, max = PupSaysGame.MAX_PLAYERS - 1, onToggle = onToggle)
            ChunkyButton(onStart, Modifier.fillMaxWidth()) { Text(if (picked.isEmpty()) "Just me" else "Take turns") }
        }
        ExitPill(onExit, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }
}

/** What the screen shows for one moment of the game. */
internal class PupSaysView(
    val seats: List<SeatView>, val turn: Int, val phase: Phase, val line: String, val patternSize: Int, val copied: Int,
    val sounding: Int, val keysLow: Int, val keysHigh: Int, val lit: Map<Int, Color>, val crown: Set<String>,
)

internal class SeatView(val profile: Profile, val best: Int, val triesLeft: Int, val out: Boolean)

@Composable
private fun PupSaysGameScreen(
    people: List<Profile>, mic: MicInput, midi: MidiDevices?, sampler: Sampler?, voice: Voice?, sound: () -> Boolean,
    onExit: () -> Unit, onFinished: (List<PupSaysResult>) -> Unit, onAgain: () -> Unit,
) {
    val kid = people.any { it.isKid }
    val game = remember { PupSaysGame(people.map { PupSaysPlayer(it.id, it.name) }, kid) }
    val context = LocalContext.current
    val stores = remember { people.associate { it.id to PlayStore(context, it.id) } }
    var version by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf(if (game.multi) game.whoseTurnLine() + " Tap ready at the keys." else "Listen, then copy me.") }
    var sounding by remember { mutableIntStateOf(-1) }
    var results by remember { mutableStateOf<List<PupSaysResult>?>(null) }
    val glow = remember { mutableStateMapOf<Int, Color>() }
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var phase by remember { mutableStateOf(game.phase) }
    /** Every move of the game: recompose, and keep [phase] (what the pup's playing is keyed on) in step. */
    fun changed() { version++; phase = game.phase }

    fun say(text: String) { line = text; voice?.speak(text) }
    /** Waits for the pup's line, at most six seconds: a voice that never settles must not hold the game (2.8.2). */
    suspend fun voiceDone() { delay(200); val until = System.currentTimeMillis() + 6_000; while (voice?.speaking?.value == true && System.currentTimeMillis() < until) delay(50) }

    fun finish() {
        val day = people.map { p -> stores.getValue(p.id).savePupSays(game.seats.first { it.player.id == p.id }.best) }
        results = day
        Log.d(TAG, "pupsays: over, " + game.results().joinToString { "${it.profileId} best ${it.best}" } + "; today " + day.joinToString { "${it.profileId} ${it.best}" })
        onFinished(day)
    }

    fun answer(a: Answer, m: Int, src: String) {
        Log.d(TAG, "pupsays: ${noteName(m)} ($src) -> $a")
        when (a) {
            // Said aloud only at the hand-off; while the pup plays, his words stay in the bubble.
            is Answer.Ignored -> a.line?.let { if (game.phase == Phase.HANDOFF) say(it) else line = it }
            is Answer.Found -> {}
            is Answer.Passed -> {
                val p = people[game.turn]
                stores.getValue(p.id).recordQuest(QuestEvent.PupCopied(a.length), p.isKid)
                say(a.line)
            }
            is Answer.Missed -> say(a.line)
        }
        changed()
        if (game.phase == Phase.RESULT) scope.launch {
            afterResult(game, ::voiceDone) { changed() }
            when {
                game.over -> finish()
                game.phase == Phase.HANDOFF -> say(game.whoseTurnLine() + " Tap ready at the keys.")
            }
        }
    }

    fun input(m: Int, src: String) { if (!game.over) answer(game.input(m), m, src) }

    // The pup plays the pattern: keys light in their colour while each note sounds; the mic stays off meanwhile.
    // Keyed on the phase alone, so keys pressed meanwhile (which recompose) do not start it over.
    LaunchedEffect(phase) {
        if (phase != Phase.DEMO) return@LaunchedEffect
        voiceDone()
        delay(350)
        val hold = if (kid) 560L else 460L
        for ((i, n) in game.pattern.withIndex()) {
            sounding = i
            glow[n] = noteColour(n)
            try {
                if (sound()) sampler?.noteOn(n, 80)
                delay(hold)
            } finally { sampler?.noteOff(n); glow.remove(n) }
            delay(90)
        }
        sounding = -1
        game.demoDone(); changed()
        // Short, because the mic waits for the voice: notes played while he speaks are only heard on the keys here.
        say(if (game.multi) "${game.current.player.name}, copy me!" else "Your turn!")
    }

    LaunchedEffect(Unit) { voice?.speak(line) }                // the opening line, for those who do not read yet
    val listening = game.phase == Phase.LISTEN
    rememberMicStrikes(mic, listen = listening && voice?.holdsMic != true, expected = game.micNotes(), groups = game.micGroups(), label = "pupsays") { e -> input(e.midi, "mic") }
    if (midi != null) LaunchedEffect(midi) { midi.events.collect { e -> if (e is NoteEvent.On) input(e.midi, "midi") } }

    val res = results
    // Read through [version]: every move of the game recomposes the view.
    val view = version.let { _ -> PupSaysView(
        seats = game.seats.mapIndexed { i, s -> SeatView(people[i], s.best, s.triesLeft, s.out) },
        turn = game.turn, phase = game.phase, line = line, patternSize = game.pattern.size, copied = game.copied, sounding = sounding,
        keysLow = 60, keysHigh = if (kid) 67 else 72, lit = glow + touched,
        crown = res?.let { PupSaysGame.crown(it) } ?: emptySet(),
    ) }
    // The system Back leaves as Done does: the day's best and the crown are kept (MainActivity's Back alone dropped them).
    androidx.activity.compose.BackHandler { if (!game.over) { game.stop(); finish() }; onExit() }
    PupSaysContent(view, onExit = { if (!game.over) { game.stop(); finish() }; onExit() },
        onReady = { game.ready(); changed() }, onAgain = onAgain,
        onKeyDown = { m ->
            touched[m] = noteColour(m)
            // Through the speaker only when the mic is not listening (or with headphones), so the mic does not hear it twice.
            if (sound() && (!listening || mic.headphonesOn())) sampler?.noteOn(m, 90)
            input(m, "touch")
        },
        onKeyUp = { m -> touched.remove(m); sampler?.noteOff(m) })
}

/** After a result: a moment to hear the pup's line, then the next turn. */
private suspend fun afterResult(game: PupSaysGame, voiceDone: suspend () -> Unit, changed: () -> Unit) {
    delay(900)
    voiceDone()
    game.next()
    changed()
}

@Composable
internal fun PupSaysContent(v: PupSaysView, onExit: () -> Unit, onReady: () -> Unit, onAgain: () -> Unit, onKeyDown: (Int) -> Unit, onKeyUp: (Int) -> Unit) {
    val still = reducedMotion()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExitPill(onExit)
            Spacer(Modifier.weight(1f))
            v.seats.forEachIndexed { i, s -> SeatBadge(s, current = i == v.turn && v.phase != Phase.OVER, crowned = s.profile.id in v.crown) }
        }
        val pupRes = when (v.phase) {
            Phase.HANDOFF -> R.drawable.pup_pointing
            Phase.DEMO -> R.drawable.pup_practicing
            Phase.LISTEN -> R.drawable.pup_listening
            Phase.RESULT -> if (v.line.startsWith("Oops") || v.line.contains("last try")) R.drawable.pup_head_tilt else R.drawable.pup_great_job
            Phase.OVER -> if (v.crown.isNotEmpty()) R.drawable.pup_crown else R.drawable.pup_great_job
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PupPicture(pupRes, 104.dp)
            SpeechBubble(v.line, Modifier.weight(1f, fill = false))
        }
        if (v.phase != Phase.OVER) PatternDots(v.patternSize, v.copied, v.sounding, v.phase == Phase.DEMO, still)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (v.phase) {
                Phase.HANDOFF -> {
                    val s = v.seats[v.turn]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PupAvatar(s.profile.avatar, size = 112.dp, selected = true)
                        ChunkyButton(onReady) { Text("${s.profile.name}, I'm ready") }
                    }
                }
                Phase.OVER -> Results(v, onAgain)
                else -> {}
            }
        }
        if (v.phase != Phase.OVER) Keyboard(v.keysLow, v.keysHigh, lit = v.lit, onNoteOn = { m, _ -> onKeyDown(m) }, onNoteOff = onKeyUp,
            sheen = false, modifier = Modifier.fillMaxWidth().height(190.dp))
    }
}

/** One player at the top: avatar, three dots for the tries left, and their best. The one playing now is ringed. */
@Composable
private fun SeatBadge(s: SeatView, current: Boolean, crowned: Boolean) {
    val c = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics {
        contentDescription = "${s.profile.name}: best ${s.best}, ${s.triesLeft} ${if (s.triesLeft == 1) "try" else "tries"} left" + if (current) ", playing now" else ""
    }) {
        Box(Modifier.clip(CircleShape).then(if (current) Modifier.border(3.dp, c.primary, CircleShape) else Modifier).padding(3.dp)) {
            PupAvatar(s.profile.avatar, size = 40.dp)
            if (crowned) PupPicture(R.drawable.pup_crown, 22.dp, Modifier.align(Alignment.TopEnd))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 3.dp)) {
            repeat(PupSaysGame.MISSES) { i -> Box(Modifier.size(6.dp).background(if (i < s.triesLeft) c.primary else c.outlineVariant, CircleShape)) }
        }
    }
}

/** A dot per note of the pattern: lit while the pup plays it, filled as the player copies it. */
@Composable
private fun PatternDots(size: Int, copied: Int, sounding: Int, demo: Boolean, still: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "$size notes, $copied copied" },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        repeat(size) { i ->
            val on = if (demo) i == sounding else i < copied
            val grow by animateFloatAsState(if (on && !still) 1.25f else 1f, tween(if (still) 0 else 140), label = "dot")
            Box(Modifier.size(16.dp).scale(grow).background(if (on) c.primary else c.surfaceContainerHighest, CircleShape))
        }
    }
}

/** The end: everyone's best of this game, the crown on the best, and play again. */
@Composable
private fun Results(v: PupSaysView, onAgain: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        v.seats.sortedByDescending { it.best }.forEach { s ->
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp)
                .width(260.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box { PupAvatar(s.profile.avatar, size = 48.dp); if (s.profile.id in v.crown) PupPicture(R.drawable.pup_crown, 26.dp, Modifier.align(Alignment.TopEnd)) }
                Text(s.profile.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (s.best == 0) "good try" else "${s.best} notes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        ChunkyButton(onAgain) { Text("Play again") }
    }
}

// ---- previews ----

private val mia = Profile.kid("Mia", 2, reader = false, id = "p1")
private val theo = Profile.kid("Theo", 5, reader = true, id = "p2")

private fun previewView(phase: Phase, line: String, crown: Set<String> = emptySet()) = PupSaysView(
    seats = listOf(SeatView(mia, 4, 2, false), SeatView(theo, 5, 3, false)), turn = 1, phase = phase, line = line,
    patternSize = 5, copied = 2, sounding = 3, keysLow = 60, keysHigh = 67, lit = mapOf(64 to noteColour(64)), crown = crown,
)

@Preview(name = "Pup says, Theo's turn", widthDp = 400, heightDp = 820)
@Composable
private fun PupSaysHandoffPreview() = SharpEarsTheme(dark = false) {
    Surface { PupSaysContent(previewView(Phase.HANDOFF, "Theo, your turn. Tap ready at the keys."), {}, {}, {}, {}, {}) }
}

@Preview(name = "Pup says, copying, dark", widthDp = 400, heightDp = 820)
@Composable
private fun PupSaysListenPreview() = SharpEarsTheme(dark = true) {
    Surface { PupSaysContent(previewView(Phase.LISTEN, "Theo, copy me!"), {}, {}, {}, {}, {}) }
}

@Preview(name = "Pup says, over with the crown", widthDp = 400, heightDp = 820)
@Composable
private fun PupSaysOverPreview() = SharpEarsTheme(dark = false) {
    Surface { PupSaysContent(previewView(Phase.OVER, "Theo, that was your last try. Your best: 5 notes!", crown = setOf("p2")), {}, {}, {}, {}, {}) }
}

@Preview(name = "Pup says, who plays", widthDp = 400, heightDp = 700)
@Composable
private fun WhoPlaysPreview() = SharpEarsTheme(dark = false) {
    Surface { WhoPlays(Profile.owner(), listOf(mia, theo), listOf("p1"), {}, {}, {}) }
}

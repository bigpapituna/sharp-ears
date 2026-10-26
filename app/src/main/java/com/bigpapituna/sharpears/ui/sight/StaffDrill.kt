package com.bigpapituna.sharpears.ui.sight

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.DrillScore
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.PerformScore
import com.bigpapituna.sharpears.engine.SightLevel
import com.bigpapituna.sharpears.engine.SightStaff
import com.bigpapituna.sharpears.engine.StaffCard
import com.bigpapituna.sharpears.engine.theory.Clef
import com.bigpapituna.sharpears.engine.theory.ledgerLines
import com.bigpapituna.sharpears.engine.theory.staffPosition
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Good
import com.bigpapituna.sharpears.ui.GeistMono
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.Stage
import com.bigpapituna.sharpears.ui.WrongGlow
import com.bigpapituna.sharpears.ui.onScreen
import com.bigpapituna.sharpears.ui.theory.drawClef
import com.bigpapituna.sharpears.ui.theory.drawFlat
import com.bigpapituna.sharpears.ui.theory.drawHead
import com.bigpapituna.sharpears.ui.theory.drawRest
import com.bigpapituna.sharpears.ui.theory.drawSharp
import com.bigpapituna.sharpears.ui.theory.drawStem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Random
import kotlin.math.floor

/**
 * The staff drill (2.14, levels 3 to 8 of Sight-read, engine/SightStaff.kt): two minutes of notes on a real staff, played on
 * the screen's keys, a MIDI keyboard or the piano through the mic. Levels 3 to 7 show a note at a time: the right key scores
 * and brings the next note, a wrong one counts and the note stays. Level 8 shows a bar at a time with the next one beside
 * it, the clicks counting one bar in and going on under it; each note is graded as Perform grades it, and a note let pass
 * counts as wrong. Note names never show. [seed] fixes the notes (a debug run, logged); [onDone] gets the score once the two
 * minutes are up.
 */
@Composable
fun StaffDrill(level: Int, deps: LessonDeps, seed: Long, onDone: (DrillScore) -> Unit, modifier: Modifier = Modifier,
               /** Debug: a pretend keyboard played from the drill's start (`--es midi`, MidiDevices.debugPlay). */
               midiScript: String? = null, goal: Int? = null) {
    val lv = SightLevel.of(level)
    val rhythm = lv == SightLevel.RHYTHM
    val context = LocalContext.current
    var running by remember(level, seed) { mutableStateOf(false) }
    var right by remember(level, seed) { mutableIntStateOf(0) }
    var wrong by remember(level, seed) { mutableIntStateOf(0) }
    var leftMs by remember(level, seed) { mutableLongStateOf(SightStaff.DRILL_MS) }
    val rnd = remember(level, seed) { Random(seed) }
    // Level 8's whole run of bars is drawn at the start (the timeline is graded as one); the others draw a card at a time.
    val cards = remember(level, seed) { if (rhythm) SightStaff.cards(level, seed, SightStaff.RHYTHM_BARS) else emptyList() }
    val starts = remember(level, seed) { SightStaff.rhythmStarts(cards) }
    var card by remember(level, seed) { mutableStateOf(if (rhythm) cards.first() else SightStaff.card(level, rnd)) }
    var bar by remember(level, seed) { mutableIntStateOf(-1) }               // level 8: the bar being played (-1 the count-in)
    var beatNow by remember(level, seed) { mutableStateOf(-1f) }             // level 8: where the cursor is, in beats of the bar
    val results = remember(level, seed) { mutableStateMapOf<Long, Color>() } // level 8: each note's colour once graded
    val flash = remember { mutableStateMapOf<Int, Color>() }
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val score = remember(level, seed) {
        if (!rhythm) null else PerformScore(SightStaff.rhythmGroups(cards), { true },
            onHit = { start, off -> results[start] = Good; right++; Log.d(TAG, "sight drill: hit at $start ${off.toInt()} ms") },
            onMiss = { start -> results[start] = WrongGlow; wrong++; Log.d(TAG, "sight drill: missed at $start") })
    }
    var startedAt by remember(level, seed) { mutableLongStateOf(0L) }
    val clicks = remember { runCatching { com.bigpapituna.sharpears.audio.Clicks(context) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { clicks?.release() } }
    val micOn = deps.prefs.micDefault && deps.mic.hasPermission()

    fun songMs() = android.os.SystemClock.uptimeMillis() - startedAt - SightStaff.rhythmBarMs.toLong()
    fun lightKey(midi: Int, c: Color) { flash[midi] = c }
    fun answer(midi: Int, latencyMs: Double = 0.0, src: String) {
        if (!running) return
        if (rhythm) {
            val at = songMs() - latencyMs
            val g = score?.input(midi, at)
            Log.d(TAG, "sight drill: ${com.bigpapituna.sharpears.engine.noteName(midi)} at ${at.toLong()} -> $g ($src)")
            if (g == null && at > -com.bigpapituna.sharpears.engine.OK_MS) { wrong++; lightKey(midi, WrongGlow) } else if (g != null) lightKey(midi, Good)
            return
        }
        val want = card.keys.first()
        if (midi == want) {
            right++; lightKey(midi, Good)
            card = SightStaff.card(level, rnd, card)
            Log.d(TAG, "sight drill: right ${com.bigpapituna.sharpears.engine.noteName(midi)} ($src), next ${card.code}, $right right")
        } else {
            wrong++; lightKey(midi, WrongGlow)
            Log.d(TAG, "sight drill: wrong ${com.bigpapituna.sharpears.engine.noteName(midi)} for ${com.bigpapituna.sharpears.engine.noteName(want)} ($src)")
        }
    }
    val answerNow by rememberUpdatedState(::answer)

    fun start() {
        right = 0; wrong = 0; results.clear(); bar = -1; beatNow = -1f
        startedAt = android.os.SystemClock.uptimeMillis()
        running = true
        Log.d(TAG, "sight drill: level $level seed $seed starts" + if (rhythm) ", bars " + cards.joinToString(" | ") { it.timedCode } else ", first ${card.code}")
        if (!rhythm) Log.d(TAG, "sight drill: notes " + SightStaff.cards(level, seed, 60).joinToString(" ") { it.code })
        midiScript?.let { deps.midi.debugPlay(it) }
    }

    // The clock: the two minutes, and for level 8 the clicks, the bar and the cursor.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var lastBeat = -99
        while (isActive && running) {
            val now = android.os.SystemClock.uptimeMillis()
            if (rhythm) {
                val t = songMs().toDouble()
                val beat = floor(t / SightStaff.rhythmBeatMs).toInt()
                if (beat != lastBeat && beat >= -SightStaff.BEATS) { lastBeat = beat; clicks?.play(Math.floorMod(beat, SightStaff.BEATS) == 0) }
                bar = if (t < 0) -1 else (t / SightStaff.rhythmBarMs).toInt()
                beatNow = ((t - maxOf(bar, 0) * SightStaff.rhythmBarMs) / SightStaff.rhythmBeatMs).toFloat()
                if (bar in cards.indices) card = cards[bar]
                score?.advance(t)
                leftMs = (SightStaff.DRILL_MS - t.toLong().coerceAtLeast(0)).coerceAtLeast(0)
                if (t >= SightStaff.DRILL_MS) break
            } else {
                leftMs = (SightStaff.DRILL_MS - (now - startedAt)).coerceAtLeast(0)
                if (leftMs <= 0) break
            }
            delay(16)
        }
        if (!running) return@LaunchedEffect
        running = false
        val s = DrillScore(right, wrong)
        Log.d(TAG, "sight drill: level $level done, ${s.label}")
        onDone(s)
    }
    // Lights fade.
    LaunchedEffect(flash.size, flash.values.toList()) { if (flash.isNotEmpty()) { delay(260); flash.clear() } }

    // MIDI and the mic, as the touch keys.
    LaunchedEffect(deps.midi) {
        deps.midi.events.collect { e -> if (e is NoteEvent.On) { touched[e.midi] = RightHandColor; answerNow(e.midi, 0.0, "midi") } else touched.remove(e.midi) }
    }
    val visible = onScreen()
    val listen = micOn && running && visible && !deps.voice.holdsMic
    val listening by rememberUpdatedState(listen)
    DisposableEffect(listen) {
        val ok = listen && deps.mic.start()
        onDispose { if (ok) deps.mic.stop() }
    }
    LaunchedEffect(listen, card) {
        deps.mic.matcher.expected = if (!listen) emptySet() else if (rhythm) (cards.getOrNull(bar)?.keys.orEmpty() + cards.getOrNull(bar + 1)?.keys.orEmpty()).toSet() else card.keys.toSet()
        deps.mic.matcher.groups = emptyList()
    }
    LaunchedEffect(deps.mic) {
        deps.mic.events.collect { e ->
            if (!listening) return@collect
            when (e) {
                is MicEvent.Strike -> answerNow(e.midi, e.delayMs ?: 0.0, "mic")
                is MicEvent.Wrong -> if (!rhythm) answerNow(e.midi, 0.0, "mic")
                else -> {}
            }
        }
    }

    val keys = when (lv) {
        SightLevel.TREBLE_C, SightLevel.RHYTHM -> 60..72
        SightLevel.TREBLE -> 60..84
        SightLevel.BASS -> 36..60
        else -> 36..84
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The clock and the tally.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%d:%02d".format(java.util.Locale.ROOT, leftMs / 60_000, (leftMs / 1000) % 60), style = Numeric.merge(MaterialTheme.typography.titleLarge),
                modifier = Modifier.semantics { contentDescription = "${leftMs / 1000} seconds left" })
            Spacer(Modifier.weight(1f))
            // A challenge counts toward its goal: "7 of 15 right".
            Text((if (goal != null) "$right of $goal right" else "$right right") + if (wrong > 0) ", $wrong wrong" else "", style = Numeric.merge(MaterialTheme.typography.titleMedium),
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        // The staff on its stage.
        Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Stage).padding(12.dp), contentAlignment = Alignment.Center) {
            if (rhythm) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val now = if (bar < 0) 0 else bar
                StaffView(cards[now.coerceAtMost(cards.lastIndex)], Modifier.weight(1f).height(170.dp), cursor = if (running && bar >= 0) beatNow else null,
                    colours = { i -> starts.getOrNull(now)?.getOrNull(i)?.let { results[it] } },
                    countIn = if (running && bar < 0) beatNow + SightStaff.BEATS else null)
                cards.getOrNull(now + 1)?.let { StaffView(it, Modifier.weight(1f).height(170.dp), dim = true) }
            } else StaffView(card, Modifier.widthIn(max = 420.dp).fillMaxWidth().height(if (card.grand) 230.dp else 170.dp))
        }
        Text(lv.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!running) ChunkyButton(onClick = ::start) { Text("Start, 2 minutes") }
        Spacer(Modifier.weight(1f, fill = false))
        // The keys, names hidden.
        Keyboard(keys.first, keys.last, lit = touched + flash, onNoteOn = { m, _ ->
            if (deps.prefs.sound && (!listen || deps.mic.headphonesOn())) deps.sampler.noteOn(m, 80)
            answer(m, src = "touch")
        }, onNoteOff = { m -> deps.sampler.noteOff(m) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).height(150.dp),
            flash = flash, sheen = false, colours = false, reader = true)
    }
}

/**
 * A card on a staff: a treble or bass staff, or both joined, with its clef, its note (or bar) in the middle, ledger lines,
 * sharps and flats. Level 8 adds the 4/4 sign, a [cursor] (beats into the bar) and each graded note in its [colours].
 * [dim]: the next bar, waiting. [countIn]: the count-in's beat (0 to 4) as dots over the staff.
 */
@Composable
fun StaffView(card: StaffCard, modifier: Modifier, cursor: Float? = null, dim: Boolean = false, colours: (Int) -> Color? = { null }, countIn: Float? = null) {
    val measurer = rememberTextMeasurer()
    val desc = if (card.notes.size == 1) "A note on the ${if (card.clef == Clef.BASS) "bass" else "treble"} staff" else "A bar of ${card.notes.size} notes"
    Canvas(modifier.semantics { contentDescription = desc }) {
        val ink = if (dim) InkDim.copy(alpha = 0.6f) else Ink
        val staves = if (card.grand) listOf(Clef.TREBLE, Clef.BASS) else listOf(card.clef)
        // A space between lines: five lines and room for two ledger lines each side, per staff.
        val s = minOf(size.height / (if (card.grand) 18f else 11f), size.width / 16f)
        val trebleTop = (size.height - s * (if (card.grand) 14f else 4f)) / 2f
        fun top(clef: Clef) = if (card.grand && clef == Clef.BASS) trebleTop + 10f * s else trebleTop
        fun yOf(position: Int, clef: Clef) = top(clef) + 4f * s - position * s / 2f
        val left = s * 0.8f
        staves.forEach { clef ->
            for (i in 0..4) drawLine(ink.copy(alpha = 0.8f * ink.alpha), Offset(left, top(clef) + i * s), Offset(size.width - s * 0.5f, top(clef) + i * s), strokeWidth = s * 0.08f)
            drawClef(clef == Clef.TREBLE, left + s * 0.3f, top(clef) + (if (clef == Clef.TREBLE) 0f else 0f), s, ink, 1f)
        }
        if (card.grand) drawLine(ink, Offset(left, top(Clef.TREBLE)), Offset(left, top(Clef.BASS) + 4 * s), strokeWidth = s * 0.12f)
        var x0 = left + s * 3.2f
        val rhythm = card.level == SightLevel.RHYTHM.n
        if (rhythm) {
            // 4/4, then the bar's notes spread by their beats.
            val style = TextStyle(color = ink, fontSize = (s * 2f / density / fontScale).sp, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold)
            listOf(0f, 2f).forEach { dy ->
                val m = measurer.measure("4", style)
                drawText(m, topLeft = Offset(x0, top(card.clef) + dy * s + s - m.size.height / 2f))
            }
            x0 += s * 2.2f
        }
        val w = size.width - s * 1.2f - x0
        val beats = SightStaff.beatsOf(card)
        // A bar's beats across the staff after the 4/4, a little in from each end so the barline has room.
        fun xAt(beat: Float) = x0 + w * ((beat + 0.3f) / 4.3f)
        countIn?.let { c ->
            for (i in 0 until 4) drawCircle(if (i < c) PupGold else InkDim.copy(alpha = 0.4f), s * 0.35f, Offset(x0 + w * (i + 0.5f) / 4f, top(card.clef) - s * 1.6f))
        }
        var played = 0
        card.notes.forEachIndexed { i, n ->
            val x = if (!rhythm) x0 + w / 2f else xAt(beats[i].toFloat())
            val clef = n.staff ?: card.clef
            val colour = if (n.rest) ink else colours(played++) ?: ink
            if (n.rest) { drawRest(n.value, x, top(clef) + 2f * s, s, ink); return@forEachIndexed }
            val pitch = n.pitches.first()
            val pos = staffPosition(pitch, clef)
            val y = yOf(pos, clef)
            ledgerLines(pos).forEach { lp -> drawLine(ink, Offset(x - s * 1.0f, yOf(lp, clef)), Offset(x + s * 1.0f, yOf(lp, clef)), strokeWidth = s * 0.1f) }
            if (pitch.accidental == 1) drawSharp(Offset(x - s * 1.7f, y), s, colour)
            if (pitch.accidental == -1) drawFlat(Offset(x - s * 1.6f, y), s, colour)
            drawHead(Offset(x, y), s, n.value, colour)
            drawStem(Offset(x, y), s, n.value, up = pos < 4, color = colour)
        }
        if (rhythm) drawLine(ink, Offset(size.width - s * 0.5f, top(card.clef)), Offset(size.width - s * 0.5f, top(card.clef) + 4 * s), strokeWidth = s * 0.1f)
        cursor?.let { c ->
            val x = xAt(c.coerceIn(0f, 4f))
            drawLine(PupGold.copy(alpha = 0.8f), Offset(x, top(card.clef) - s), Offset(x, top(card.clef) + 5f * s), strokeWidth = s * 0.18f)
        }
    }
}

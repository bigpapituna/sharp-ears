package com.bigpapituna.sharpears.ui.skin

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.TroubleSpot
import com.bigpapituna.sharpears.engine.family.PawWeek
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.skin.KeyboardSize
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.engine.skin.WrongKey
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.LaneBackground
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.LessonScreen
import com.bigpapituna.sharpears.ui.NoteLane
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.RunEnd
import com.bigpapituna.sharpears.ui.RunRecap
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.XpBar
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.paper
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Debug only (`--es screen skin --es view <v>`): the skin team's pieces on their own, for emulator screenshots.
 * Views: lane (colour notes on the lane and keys), wrong (`--es tone near|far`), recap (`--ei stars 1..3`), goodbye,
 * paws, bite (`--es lesson <id> --ei step n`: the real LessonScreen). `--ez kid true|false` (default true) and
 * `--ez reader true|false` (default true) pick who is playing; `--el pos <ms>` freezes the lane; `--ef slow n` stretches the wrong-key flash n times for a screenshot.
 */
@Composable
fun SkinDemo(intent: Intent, sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, prefs: Prefs, ready: () -> Boolean) {
    val kid = intent.getBooleanExtra("kid", true)
    val reader = intent.getBooleanExtra("reader", true)
    val who = if (kid) Profile.kid("Mia", 1, reader, id = "demo-kid") else Profile.adult("Roy", 0, id = "demo-adult").copy(colourNotes = intent.getBooleanExtra("colours", true))
    val view = intent.getStringExtra("view") ?: "lane"
    Log.d(TAG, "skin demo: $view kid=$kid reader=$reader")
    CompositionLocalProvider(LocalProfile provides who) {
        SharpEarsTheme(dark = if (view == "lane" || view == "wrong") true else androidx.compose.foundation.isSystemInDarkTheme()) {
            when (view) {
                "lane", "wrong" -> LaneDemo(intent, kid, if (view == "wrong") intent.getStringExtra("tone") ?: "both" else null)
                "recap" -> RecapDemo(intent.getIntExtra("stars", 3), sampler.takeIf { prefs.sound })
                "goodbye" -> Goodbye(who.name, demoWeek(), onSongs = {}, onClose = {}, onJam = {}, say = { voice.speak(it) })
                "asleep" -> Goodbye(who.name, demoWeek(), onSongs = {}, onClose = {}, onJam = {}, onOneMore = {}, asleep = true)
                "paws" -> PawsDemo()
                "bite" -> androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val lesson = COURSE.flatMap { it.lessons }.firstOrNull { it.id == intent.getStringExtra("lesson") } ?: COURSE.first().lessons.first()
                    LessonScreen(lesson, intent.getIntExtra("step", 0), LessonDeps(sampler, midi, mic, voice, prefs, ready()), null,
                        onStep = {}, onSong = null, onDone = {}, onNext = null, onBack = {},
                        // The phrase-end hook as the playground team will fill it: the first two bars here.
                        phraseEnd = { ex -> intent.getIntExtra("bars", 2).takeIf { it > 0 }?.let { b -> b.toDouble() * ex.beats } })
                } }
                else -> Text("Unknown skin view $view")
            }
        }
    }
}

/** Every pitch class over the first notes, both hands, so the lane shows colours, stripes and the hand edges. */
private val DEMO = Exercise(right = "C4:0.5 D4:0.5 E4:0.5 F4:0.5 G4:0.5 A4:0.5 B4:0.5 C5:1",
    left = "r:0.25 C#4:0.5 D#4:0.5 r:0.5 F#4:0.5 G#4:0.5 A#4:0.5", bpm = 75.0)

@Composable
private fun LaneDemo(intent: Intent, kid: Boolean, tone: String?) {
    val song = remember { DEMO.toSong("skin-demo", "Colours") }
    val keySize = KeyboardSize.forKid(kid)
    val range = remember(keySize) { keySize.rangeOf(song.notes) }
    val pos = intent.getLongExtra("pos", 150L).toDouble()
    val slow = intent.getFloatExtra("slow", 1f)
    val flash = rememberWrongFlash(slow)
    // Wrong keys against an expected C4: D4 is a slip (yellow), A4 is far (red). Repeated so a screenshot catches one.
    if (tone != null) LaunchedEffect(tone) {
        while (true) {
            if (tone != "far") { flash.show(WrongKey.of(62, listOf(60))!!); delay(((if (tone == "near") 650 else 900) * slow).toLong()) }
            if (tone != "near") { flash.show(WrongKey.of(69, listOf(60, 64))!!); delay(((if (tone == "far") 650 else 900) * slow).toLong()) }
        }
    }
    val labels = remember(range) { range.filter { !NoteColours.isBlack(it) }.associateWith { if (it % 12 == 0) "C${it / 12 - 1}" else NoteColours.letter(it) } }
    Column(Modifier.fillMaxSize().background(LaneBackground).safeDrawingPadding()) {
        NoteLane(song, pos, range.first, range.last, onScrubStart = {}, onScrub = {}, onScrubEnd = {},
            modifier = Modifier.fillMaxWidth().weight(1f), wrong = flash)
        Keyboard(range.first, range.last, lit = if (tone == null) mapOf(60 to RightHandColor) else emptyMap(), onNoteOn = { _, _ -> }, onNoteOff = {},
            modifier = Modifier.fillMaxWidth().weight(keySize.heightShare), labels = labels, keySize = keySize, wrong = flash, sheen = false)
    }
}

@Composable
private fun RecapDemo(stars: Int, sampler: Sampler?) {
    val r = when (stars) { 3 -> Recap(19, 1, 0, 22, 20); 2 -> Recap(12, 3, 5, 60, 9); else -> Recap(5, 2, 13, 140, 3) }
    val e = RunEnd(r, newBest = stars == 3, spots = if (stars < 3) listOf(TroubleSpot(3, 4800.0, 7200.0, 4.0)) else emptyList(),
        canReplay = true, timeMs = 48_000, xpGained = 15, xpBar = XpBar(0.42f, 0.58f, "Level 3"))
    RunRecap(e, 1.0, sampler, onAgain = {}, onLoop = {}, onClose = {}, onReplay = {})
}

private fun demoWeek(): List<com.bigpapituna.sharpears.engine.family.PawDay> {
    val today = LocalDate.now()
    val monday = today.with(java.time.DayOfWeek.MONDAY)
    val played = listOf(0L, 1L, 3L).map { monday.plusDays(it) }.filter { !it.isAfter(today) } + today
    return PawWeek.of(played.associateWith { 300 }, today)
}

@Composable
private fun PawsDemo() {
    Box(Modifier.fillMaxSize().paper().safeDrawingPadding()) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("You", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("A kid's card", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PawCalendarCard(kid = true, week = demoWeek())
            Text("A grown-up's card", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PawCalendarCard(kid = false, week = demoWeek())
        }
    }
}

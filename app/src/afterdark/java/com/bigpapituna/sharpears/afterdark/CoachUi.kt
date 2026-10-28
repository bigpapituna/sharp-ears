package com.bigpapituna.sharpears.afterdark

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.family.PlainLook
import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import com.bigpapituna.sharpears.ui.LocalPlain
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.pup.BubbleTail
import com.bigpapituna.sharpears.ui.pup.CheerBubble
import kotlinx.coroutines.delay

/** The words of Settings > Playing > Coach voice, its one-time notice and its question (2.16). */
internal object CoachCopy {
    const val TITLE = "Coach voice"
    const val LINE = "A low, flirty grown-up voice cheers you on while you play, its words in the pup's bubble. For adults only. " +
        "It follows Encouragement: as often, and quiet when that is Off."
    const val OFF = "Off"
    const val PLAIN = "Off in Plain look."
    const val QUIET = "Encouragement is Off, so the coach stays quiet."
    const val NOTICE_TITLE = "For adults only"
    const val NOTICE = "The coach voice is flirty and suggestive: a low voice saying things like \"Good girl\" or \"Good boy\" and " +
        "\"Just like that\". It is never explicit, but it is meant for grown-ups. Keep it off on a profile a child uses, and " +
        "wear headphones around others."
    const val NOTICE_YES = "Turn it on"
    const val NOTICE_NO = "Not now"
    const val ASK = "How should your coach talk to you?"
    const val AS_WOMAN = "As a woman"
    const val AS_MAN = "As a man"
    /** 2.17 (audit M4): neither: the lines for anyone. */
    const val BY_NAME = "Just use my name"
    /** 2.17 (audit M3): the mic hears again after a line. */
    const val LISTENING = "Listening again"
}

/**
 * Settings > Playing > Coach voice, under Encouragement (2.16): Off or one of the build's voices (CoachHost.voices: whatever
 * folders it has, a new one a new choice), then how the coach talks to the player (as a woman or a man), for a grown-up's
 * profile only ([CoachGate.offered]: never a kid's or a big kid's, whatever is stored). Off by default. Turning it on from Off
 * asks the parent gate every time (LocalParentGate.guard), the first time also the notice that it is flirty and for adults only,
 * and how to talk to them when that was never answered; changing the voice while it is on, the woman or man answer, or Off need
 * none of that. In the Plain look it reads "Off in Plain look." (the choice kept). The values are the profile's own
 * (Prefs.extraString, CoachGate.KEY and FOR_KEY), never written to the brief's settings log (it would reach the AI brief and the
 * email).
 */
@Composable
internal fun CoachSettings(prefs: Prefs, sub: @Composable (String) -> Unit) {
    val me = LocalProfile.current
    if (!CoachGate.offered(me)) return
    val voices = remember { CoachHost.voices() }
    if (voices.isEmpty()) return
    val gate = LocalParentGate.current
    val plain = LocalPlain.current.on
    val v = prefs.version.intValue
    val chosen = if (v >= 0) CoachGate.voiceOf(prefs.extraString(CoachGate.KEY), voices.map { it.id }) else null
    val forStored = if (v >= 0) prefs.extraString(CoachGate.FOR_KEY)?.takeIf { CoachGate.answered(it) } else null
    // Turning on: the voice waiting for the notice and the question, and which of the two shows.
    var turningOn by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    fun set(voice: String?) {
        prefs.setExtraString(CoachGate.KEY, voice ?: CoachGate.OFF)
        if (voice == null || voice != chosen) CoachHost.hush()
        CoachHost.on()
        Log.d(TAG, "coach: voice ${voice ?: "off"} for ${me.id}")
    }
    fun setFor(w: String) { prefs.setExtraString(CoachGate.FOR_KEY, w); CoachHost.on(); Log.d(TAG, "coach: talks to ${me.id} as $w") }
    /** After the gate: the notice once, the question once, then on. */
    fun proceed(voice: String) {
        when {
            prefs.extraFlag(CoachGate.NOTICE_KEY) != true -> { turningOn = voice; notice = true; Log.d(TAG, "coach: the notice") }
            !CoachGate.answered(prefs.extraString(CoachGate.FOR_KEY)) -> { turningOn = voice; notice = false; Log.d(TAG, "coach: asks how to talk") }
            else -> { turningOn = null; set(voice) }
        }
    }
    sub(CoachCopy.TITLE)
    if (plain) { Text(CoachCopy.PLAIN, style = MaterialTheme.typography.bodyMedium, color = dim); return }
    Text(CoachCopy.LINE, style = MaterialTheme.typography.bodySmall, color = dim)
    Column(Modifier.selectableGroup().padding(top = 4.dp)) {
        ChoiceRow(CoachCopy.OFF, chosen == null) { if (chosen != null) set(null) }
        for (voice in voices) ChoiceRow(voice.name, chosen == voice.id) {
            when {
                chosen == voice.id -> {}
                chosen != null -> set(voice.id)                        // on already: another voice, no gate
                else -> gate.guard { proceed(voice.id) }
            }
        }
    }
    if (chosen != null) {
        Text(CoachCopy.ASK, style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(top = 8.dp))
        // 2.17: three answers as radio rows (a third segment did not fit the cover screen with its words).
        Column(Modifier.selectableGroup().padding(top = 4.dp)) {
            ChoiceRow(CoachCopy.AS_WOMAN, forStored == "w") { setFor("w") }
            ChoiceRow(CoachCopy.AS_MAN, forStored == "m") { setFor("m") }
            ChoiceRow(CoachCopy.BY_NAME, forStored == CoachGate.NAME) { setFor(CoachGate.NAME) }
        }
        if (PlainLook.cheerFrequency(false, prefs.cheerFrequency) == CheerFrequency.OFF)
            Text(CoachCopy.QUIET, style = MaterialTheme.typography.bodySmall, color = dim)
    }
    val waiting = turningOn
    if (waiting != null && notice) AlertDialog(onDismissRequest = { turningOn = null; notice = false },
        title = { Text(CoachCopy.NOTICE_TITLE) },
        text = { Text(CoachCopy.NOTICE) },
        confirmButton = { TextButton(onClick = { notice = false; prefs.setExtraFlag(CoachGate.NOTICE_KEY, true); proceed(waiting) }) { Text(CoachCopy.NOTICE_YES) } },
        dismissButton = { TextButton(onClick = { turningOn = null; notice = false; Log.d(TAG, "coach: the notice, not now") }) { Text(CoachCopy.NOTICE_NO) } })
    else if (waiting != null) AlertDialog(onDismissRequest = { turningOn = null },
        title = { Text(CoachCopy.ASK) },
        text = {
            Column {
                for ((w, label) in listOf("w" to CoachCopy.AS_WOMAN, "m" to CoachCopy.AS_MAN, CoachGate.NAME to CoachCopy.BY_NAME))
                    TextButton(onClick = { setFor(w); turningOn = null; set(waiting) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        },
        confirmButton = {})
}

/** One choice of a list: the whole row selects it, TalkBack reads it as a radio button. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * The words of a line that has no bubble of its own (a lesson's first playing step, its finish and stars, a wrong key, a hint,
 * the comeback card, Done for today): the pup's gold bubble at the top of the screen for the line's length, fading in and out.
 * Drawn over every screen by MainActivity and over the song recap's window; never for a kid's or big kid's profile (nothing
 * plays for one either). It takes no touches.
 */
@Composable
internal fun CoachOverlay() {
    if (!CoachGate.offered(LocalProfile.current) || LocalPlain.current.on) return
    val c = CoachHost.caption.value
    var shown by remember { mutableStateOf<CoachHost.Caption?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(c?.seq) {
        if (c == null) { visible = false; return@LaunchedEffect }
        shown = c
        visible = true
        delay(c.ms)
        visible = false
    }
    // 2.17 (audit M3): "Listening again", small and quiet, as the mic hears again after a line.
    val cue = CoachHost.listening.value
    var cueShown by remember { mutableStateOf(false) }
    LaunchedEffect(cue) { if (cue == 0) return@LaunchedEffect; cueShown = true; delay(CoachSound.CUE_MS); cueShown = false }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 8.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        val words = shown?.text
        if (words != null) AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
            CheerBubble(words, BubbleTail.BOTTOM, Modifier.widthIn(max = 340.dp), MaterialTheme.typography.titleSmall)
        }
        AnimatedVisibility(cueShown && !visible, enter = fadeIn(), exit = fadeOut()) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)) {
                Text(CoachCopy.LISTENING, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
    }
}

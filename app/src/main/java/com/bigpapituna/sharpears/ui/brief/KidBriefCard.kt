package com.bigpapituna.sharpears.ui.brief

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.BriefStore
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.KidCardWords
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.DialogBarsFollowTheme
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.family.HoldThePup
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.family.LocalParentGate

/**
 * The child's card (docs/PARENT-BRIEF-PLAN.md): the pup's own short version of a session, first person, kind, with one
 * "next time let's try". Spoken as it appears ([say] is the app's voice: `voice.speak(it, interrupt = true)`; a line with
 * the child's name is made on the phone like every other named line), and tapping the pup says it again. "Show Mum" is the
 * door to the grown-up's session note ([onShowMum] -> [ShowMumDialog]). The child never sees the parent's version.
 */
@Composable
fun KidBriefCard(card: KidCard, say: (String) -> Unit, onShowMum: () -> Unit, modifier: Modifier = Modifier, speakOnShow: Boolean = true) {
    val lines = remember(card) { KidCardWords.lines(card) }
    val next = remember(card) { KidCardWords.nextTry(card) }
    val spoken = remember(card) { KidCardWords.spoken(card) }
    LaunchedEffect(card) { if (speakOnShow && spoken.isNotEmpty()) say(spoken) }
    val c = MaterialTheme.colorScheme
    Column(modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(PupGold.copy(alpha = 0.16f))
        .border(1.dp, PupGold.copy(alpha = 0.5f), RoundedCornerShape(28.dp)).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PupSays(Pose.GREAT_JOB, lines.firstOrNull().orEmpty(), size = 112.dp, onTap = { say(spoken) }, style = MaterialTheme.typography.titleLarge)
        lines.drop(1).forEach { SpeechBubble(it, Modifier.padding(start = 96.dp), MaterialTheme.typography.titleMedium) }
        if (next != null) Text(next, style = MaterialTheme.typography.titleMedium, color = c.onSurface, textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface.copy(alpha = 0.7f)).padding(horizontal = 16.dp, vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            PillButton(KidCardWords.SHOW_MUM, onClick = onShowMum)
            PillButton("Hear it again", onClick = { say(spoken) })
        }
    }
}

/**
 * Loads the child's card for the session that just ended and shows it once per session: nothing when the session was quiet
 * (KidCardWords.due), or the writers have nothing. Place it on the kid's finish screen.
 */
@Composable
fun KidBriefCardHost(env: BriefEnv, profile: Profile, say: (String) -> Unit, onShowMum: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val card by produceState<KidCard?>(null, profile.id) {
        val store = BriefStore(context, profile.id)
        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { env.loadFindings(profile, Period.SESSION, env.now()) }.getOrNull() }
        if (f != null && KidCardWords.due(f.stats.totals.practiceMinutes * 60, f.range.fromMs.takeIf { !f.isEmpty }, store.cardSession.takeIf { it != 0L })) {
            value = env.serviceFor(profile).kidCard(f, profile.name)
            if (value != null) store.cardSession = f.range.fromMs
        }
    }
    card?.let { KidBriefCard(it, say, onShowMum, modifier) }
}

/**
 * "Show Mum": a full screen over the child's finish screen. First a light gate (hold the pup, no sum), enough to READ the
 * session note for that child alone, which is the point of it: the grown-up standing there sees it at once. Every action
 * inside the note (Send, Ask, Refresh, the cards) asks the full parent gate itself (BriefPage's guardBrief; QA 2.8.3 found
 * the hold alone reaching the share sheet and the AI). Closing it shuts the gate: the phone goes back to the child.
 */
@Composable
fun ShowMumDialog(env: BriefEnv, profile: Profile, onClose: () -> Unit, startPassed: Boolean = false) {
    val gate = LocalParentGate.current
    val close = { if (profile.isKid) gate.close(); onClose() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogBarsFollowTheme()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var passed by rememberSaveable { mutableStateOf(startPassed) }
            if (!passed) LightGate(onPass = { passed = true }, onCancel = close)
            else ParentBriefScreen(env, listOf(profile), onBack = close, initialPeriod = Period.SESSION, periods = listOf(Period.SESSION),
                showChildren = false, title = "${profile.name}'s session note")
            // An action in the note asks the full parent gate: drawn in this window, over the note.
            com.bigpapituna.sharpears.ui.family.ParentGateHost()
        }
    }
}

/** The parent gate without the sum: hold the pup for three seconds. */
@Composable
fun LightGate(onPass: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Not now, go back") }
        }
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Grown-ups only", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            HoldThePup(onPass)
        }
    }
}

/**
 * The child's card as a step after a finish screen (2.4): [rememberKidCardStep] reads the session and asks the writers while
 * the celebration plays ([ready] true once the lesson or song is finished), [KidCardStep.then] wraps the finish button's way
 * on so the card shows first when one is due, and [KidCardStepHost] draws it full screen with Show Mum and a Continue that
 * goes where the button was going. Once per session (KidCardWords.due, BriefStore.cardSession), never for a grown-up, never
 * for a quiet session; when the writers are slow the button simply goes on.
 */
@androidx.compose.runtime.Stable
class KidCardStep internal constructor(private val store: BriefStore?) {
    internal var card by mutableStateOf<KidCard?>(null)
    internal var session = 0L
    internal var next by mutableStateOf<(() -> Unit)?>(null)
    internal var label = ""

    /** [action], with the card first when one is ready for this session; the card's button then says [label]. */
    fun then(label: String, action: () -> Unit): () -> Unit = {
        if (card != null && next == null && store != null && store.cardSession != session) {
            store.cardSession = session
            this.label = label
            next = action
        } else action()
    }
}

@Composable
fun rememberKidCardStep(profile: Profile, ready: Boolean): KidCardStep {
    val context = LocalContext.current
    val env = if (profile.isKid) rememberBriefEnv() else null
    val step = remember(profile.id) { KidCardStep(if (profile.isKid) BriefStore(context.applicationContext, profile.id) else null) }
    LaunchedEffect(ready, profile.id) {
        if (!ready || env == null || step.card != null) return@LaunchedEffect
        val store = BriefStore(context.applicationContext, profile.id)
        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { env.loadFindings(profile, Period.SESSION, env.now()) }.getOrNull() }
            ?: return@LaunchedEffect
        val start = f.range.fromMs.takeIf { !f.isEmpty }
        if (!KidCardWords.due(f.stats.totals.practiceMinutes * 60, start, store.cardSession.takeIf { it != 0L })) {
            android.util.Log.d(com.bigpapituna.sharpears.TAG, "brief: no card for ${profile.id} (a quiet session or shown already)")
            return@LaunchedEffect
        }
        step.session = start ?: return@LaunchedEffect
        step.card = env.serviceFor(profile).kidCard(f, profile.name)
        android.util.Log.d(com.bigpapituna.sharpears.TAG, "brief: card for ${profile.id} ready (${step.card?.writtenBy})")
    }
    return step
}

/** Draws [step]'s card over the finish screen while it is up; Continue goes on, Show Mum opens the grown-up's note. */
@Composable
fun KidCardStepHost(step: KidCardStep, profile: Profile, say: (String) -> Unit) {
    val go = step.next ?: return
    val card = step.card ?: return
    // 2.15, a big kid's Plain look: the pup's card is the pup talking ("You played with me"), so it is not drawn; the button
    // goes on at once. The grown-ups still read the session in the child's brief.
    if (com.bigpapituna.sharpears.ui.LocalPlain.current.on) { LaunchedEffect(go) { step.next = null; go() }; return }
    val env = rememberBriefEnv()
    var mum by remember { mutableStateOf(false) }
    fun done() { step.next = null; go() }
    BackHandler { done() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Centred on the screen, scrolling when it does not fit (a phone held sideways in the player).
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                KidBriefCard(card, say, onShowMum = { mum = true })
                com.bigpapituna.sharpears.ui.ChunkyButton(onClick = ::done, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) { Text(step.label) }
            }
        }
    }
    if (mum) ShowMumDialog(env, profile, onClose = { mum = false })
}

package com.bigpapituna.sharpears.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.family.Placement
import com.bigpapituna.sharpears.engine.family.PlacementCheck
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.family.Roster
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.reducedMotion

/**
 * "Who's playing today?": shown on launch when the phone has two or more profiles. A pup avatar per person with the name
 * under it, and "Add someone" while there is room. [onPick] gets the id; [onAdd] a new profile from [NewProfileFlow]
 * (not stored yet: the caller creates it, kids go straight in, grown-ups to the onboarding).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePicker(roster: Roster, onPick: (String) -> Unit, onAdd: (Profile) -> Unit,
                  /** A new kid's weekly brief time, chosen at the end of the flow (the caller stores it once the profile exists). */
                  onWeekly: ((com.bigpapituna.sharpears.engine.brief.WeeklySlot) -> Unit)? = null,
                  /** A new kid whose parent asked for "Let's hear you play" (2.6); the caller keeps it for the kid's first open. */
                  onCheck: ((Boolean) -> Unit)? = null) {
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) { NewProfileFlow(onDone = onAdd, onCancel = { adding = false }, onWeekly = onWeekly, onCheck = onCheck); return }
    // Centred on the screen, scrolling when eight people do not fit.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing)) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            PupSays(Pose.WELCOME, "Who's playing today?", size = 112.dp, style = MaterialTheme.typography.headlineSmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                roster.profiles.forEach { p ->
                    Tile(p.name, onClick = { onPick(p.id) }, label = "${p.name}, ${avatarLabel(p.avatar)}") { PupAvatar(p.avatar, size = 104.dp) }
                }
                if (!roster.isFull) Tile("Add someone", onClick = { adding = true }, label = "Add someone") { AddCircle(104.dp) }
            }
        }
    }
    }
}

@Composable
private fun Tile(name: String, onClick: () -> Unit, label: String, face: @Composable () -> Unit) =
    Column(Modifier.width(140.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = label }
        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        face()
        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }

/** The empty tile: a dashed circle with a plus. */
@Composable
private fun AddCircle(size: androidx.compose.ui.unit.Dp) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 3.dp.toPx()
            drawCircle(c.outline, radius = this.size.minDimension / 2 - w, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.primary, modifier = Modifier.size(40.dp))
    }
}

/**
 * Someone new, in one screen and about twenty seconds: a name, a pup colour, "Who is this for?" (a kid or a grown-up)
 * and, for a kid, "Can they read yet?" and how much piano they have played (2.6; a parent answers, and may let the pup listen to them
 * play a little). Go hands back a [Profile] with the kind's defaults and that level (not stored yet).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProfileFlow(onDone: (Profile) -> Unit, onCancel: () -> Unit,
                   /** A kid's weekly brief: asked at the end ("When should we send you Mia's weekly brief?"); the caller stores it once the profile exists. Null: not asked. */
                   onWeekly: ((com.bigpapituna.sharpears.engine.brief.WeeklySlot) -> Unit)? = null,
                   /** A kid whose parent asked for the short listen ("Let them try"): told just before the profile is handed back; the caller keeps it for the kid's first open. Null: not offered. */
                   onCheck: ((Boolean) -> Unit)? = null) {
    BackHandler(onBack = onCancel)
    val still = reducedMotion()
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableIntStateOf(1) }
    var kind by rememberSaveable { mutableStateOf<ProfileKind?>(null) }
    var reader by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var levelName by rememberSaveable { mutableStateOf(Level.NEVER.name) }
    var tryCheck by rememberSaveable { mutableStateOf(false) }
    val level = Level.parse(levelName)
    val focus = LocalFocusManager.current
    // Kids never see the grown-ups' questions, and their games listen through the microphone from the first one: a phone
    // that has not allowed it yet asks the grown-up making a kid's profile, once (before, the stages heard nothing and
    // nobody was asked). Allowed or not, the profile is made.
    val context = androidx.compose.ui.platform.LocalContext.current
    var made by remember { mutableStateOf<Profile?>(null) }
    var weeklyFor by remember { mutableStateOf<Profile?>(null) }
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { allowed ->
        android.util.Log.d(com.bigpapituna.sharpears.TAG, "profiles: microphone for a new kid ${if (allowed) "allowed" else "not allowed"}")
        made?.let(onDone)
    }
    fun go(p: Profile) {
        if (p.isKid && context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            made = p; askMic.launch(android.Manifest.permission.RECORD_AUDIO)
        } else onDone(p)
    }
    // The last question for a kid: when the grown-up wants their weekly brief (parent brief, ui/brief/WeeklyTime.kt).
    weeklyFor?.let { p ->
        com.bigpapituna.sharpears.ui.brief.WeeklyTimeStep(p.name, onChosen = { slot -> onWeekly?.invoke(slot); weeklyFor = null; go(p) })
        return
    }
    val clean = Profile.cleanName(name)
    val ready = clean.isNotEmpty() && kind != null && (kind == ProfileKind.ADULT || reader != null)
    val line = when {
        clean.isEmpty() -> "Hi! What's your name?"
        kind == null -> "Hi, $clean! Who is this for?"
        kind == ProfileKind.KID && reader == null -> "Can $clean read yet?"
        kind == ProfileKind.KID -> "Let's play, $clean!"
        else -> "Nice to meet you, $clean."
    }
    Column(Modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
                Text("Someone new", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PupAvatar(avatar, size = 96.dp)
                com.bigpapituna.sharpears.ui.SpeechBubble(line, Modifier.weight(1f, fill = false))
            }
            OutlinedTextField(name, { name = it.take(Profile.MAX_NAME) }, label = { Text("Name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
            Question("Pick a pup")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(Profile.AVATARS) { i ->
                    Box(Modifier.size(64.dp).clip(CircleShape)
                        .selectable(avatar == i, role = Role.RadioButton) { avatar = i }
                        .semantics { contentDescription = avatarLabel(i) }) {
                        PupAvatar(i, size = 64.dp, selected = avatar == i)
                    }
                }
            }
            Question("Who is this for?")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigPill("A kid", kind == ProfileKind.KID, Modifier.weight(1f)) { kind = ProfileKind.KID }
                BigPill("A grown-up", kind == ProfileKind.ADULT, Modifier.weight(1f)) { kind = ProfileKind.ADULT; reader = null }
            }
            AnimatedVisibility(kind == ProfileKind.KID, enter = if (still) fadeIn() else fadeIn() + expandVertically(),
                exit = if (still) fadeOut() else fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Question("Can they read yet?")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigPill("Not yet", reader == false, Modifier.weight(1f)) { reader = false }
                        BigPill("Yes", reader == true, Modifier.weight(1f)) { reader = true }
                    }
                    Text(when (reader) {
                        false -> "Colours and animals on the keys, five minutes a day."
                        true -> "Colours with letters on the keys, eight minutes a day."
                        null -> "Before they read, the keys get colours and animals."
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Placement (2.6): a parent says how much piano the kid has played, so the pages before it can be passed by.
                    if (reader != null) {
                        Question(if (clean.isEmpty()) "Have they played piano before?" else "Has $clean played piano before?")
                        Level.kid.forEach { l ->
                            BigPill(l.kidLine!!, level == l, Modifier.fillMaxWidth()) { levelName = l.name; if (l == Level.NEVER) tryCheck = false }
                        }
                        Text(Placement.planFor(level, ProfileKind.KID, reader == true).line.let { if (level == Level.NEVER) "They start at the very beginning, in the garden." else "$it Anything skipped stays open." },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Off unless asked: a kid is not tested. The pup shows two or three tiny things and listens; nothing is marked.
                        if (level != Level.NEVER && onCheck != null) {
                            BigPill(if (tryCheck) "Let them try: on" else "Let them try the check", tryCheck, Modifier.fillMaxWidth()) { tryCheck = !tryCheck }
                            Text("The pup plays two or three tiny things and listens. Nothing is marked, and it only ever makes the start gentler.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        ChunkyButton(onClick = {
            val k = kind ?: return@ChunkyButton
            val p = if (k == ProfileKind.KID) Profile.kid(clean, avatar, reader == true).copy(level = level.forKid) else Profile.adult(clean, avatar)
            if (k == ProfileKind.KID) onCheck?.invoke(tryCheck && PlacementCheck.offered(level))
            if (k == ProfileKind.KID && onWeekly != null) weeklyFor = p else go(p)
        }, enabled = ready, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Go")
        }
    }
}

@Composable
private fun Question(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 4.dp).semantics { heading() })

/** A big answer pill: outlined, filled with the accent tint when chosen. */
@Composable
private fun BigPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(modifier.heightIn(min = 64.dp).clip(CircleShape)
        .background(if (selected) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outline), CircleShape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = if (selected) c.onPrimaryContainer else c.onSurface, textAlign = TextAlign.Center)
    }
}

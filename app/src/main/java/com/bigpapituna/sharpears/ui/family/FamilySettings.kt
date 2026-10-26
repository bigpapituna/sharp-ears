package com.bigpapituna.sharpears.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.analytics.testerMask
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.family.Placement
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind

/** What a profile is, in a few words: "Kid, reads", "Kid, not reading yet", "Grown-up", "Grown-up, this phone's owner". */
fun kindLabel(p: Profile) = when {
    // 2.14: a big kid says so (the tabs); a little kid reads as before.
    p.bigKid && p.reader -> "Big kid, reads"
    p.bigKid -> "Big kid, not reading yet"
    p.isKid && p.reader -> "Kid, reads"
    p.isKid -> "Kid, not reading yet"
    p.isOwner -> "Grown-up, this phone's owner"
    else -> "Grown-up"
}

/**
 * The Family section of Settings: everyone on the phone, with rename, reader, grown-up or kid mode and delete, plus
 * "Add someone". Every change goes through the parent gate ([ParentGateController.guard]), also for grown-ups: it is
 * about kids reaching settings on a shared phone. Delete asks twice. Adding here does not switch who is playing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilySettings(profiles: Profiles, gate: ParentGateController = LocalParentGate.current,
                   /** The AI brief switches and Testing at the end (the kids' corner's Family); Settings draws them in groups of their own (2.7). */
                   extras: Boolean = true) {
    val roster by profiles.rosterFlow.collectAsState()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var levelFor by remember { mutableStateOf<Profile?>(null) }              // Family > this person > Piano level (2.6)
    var deleting by remember { mutableStateOf<Pair<Profile, Int>?>(null) }      // the profile and which ask (1, then 2)
    var adding by rememberSaveable { mutableStateOf(false) }
    var briefFor by rememberSaveable { mutableStateOf<String?>(null) }       // the parent brief, opened on this child
    var newSlot by remember { mutableStateOf<com.bigpapituna.sharpears.engine.brief.WeeklySlot?>(null) }   // chosen at the end of the new-kid flow
    var newCheck by remember { mutableStateOf(false) }                      // a new kid whose parent asked for "Let's hear you play"
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Text("Everyone who plays on this phone. Each has their own progress, goal and stickers; the microphone setup is shared.",
        style = MaterialTheme.typography.bodyMedium, color = dim)
    roster.profiles.forEach { p ->
        val expanded = open == p.id
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = if (expanded) "Hide" else "Change") {
            open = if (expanded) null else p.id
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PupAvatar(p.avatar, size = 44.dp)
            Column(Modifier.weight(1f).testerMask()) {           // names are hidden in tester replays
                Text(p.name + if (p.id == roster.currentId) " (playing now)" else "", style = MaterialTheme.typography.titleSmall)
                Text(kindLabel(p) + levelSuffix(p), style = MaterialTheme.typography.bodySmall, color = dim)
            }
            Text(if (expanded) "Hide" else "Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) FlowRow(Modifier.padding(start = 50.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // The brief by email is not here since 2.8: one address for the family, in Settings > Briefs and email (ui/brief/email).
            if (p.isKid) TextButton(onClick = { gate.guard { briefFor = p.id } }) { Text("Read the brief") }
            TextButton(onClick = { gate.guard { renaming = p } }) { Text("Rename") }
            TextButton(onClick = { gate.guard { levelFor = p } }) { Text("Piano level") }
            if (p.isKid) {
                TextButton(onClick = { gate.guard { profiles.update(p.id) { it.copy(reader = !it.reader) } } }) {
                    Text(if (p.reader) "Not reading yet" else "Reads now")
                }
                // 2.14, the age band (engine/family/Ages.kt): the pup's world for 4 to 7, the tabs for 8 to 13. The app reopens as them.
                TextButton(onClick = { gate.guard { profiles.update(p.id) { it.withBand(if (it.bigKid) com.bigpapituna.sharpears.engine.family.AgeBand.LITTLE else com.bigpapituna.sharpears.engine.family.AgeBand.BIG) } } }) {
                    Text(if (p.bigKid) "Make a little kid (4 to 7)" else "Make a big kid (8 to 13)")
                }
                TextButton(onClick = { gate.guard { profiles.setKind(p.id, ProfileKind.ADULT) } }) { Text("Switch to grown-up mode") }
            } else if (!p.isOwner) {
                TextButton(onClick = { gate.guard { profiles.setKind(p.id, ProfileKind.KID) } }) { Text("Switch to kid mode") }
            }
            if (!p.isOwner) TextButton(onClick = { gate.guard { deleting = p to 1 } }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
        // 2.14: the daily time and "After the goal: wind down" (was the plain "Bedtime after the goal" button), each change
        // behind the gate, both off by default: kids otherwise play as long as they like.
        if (expanded && p.isKid) KidTimeControls(p, Modifier.padding(start = 58.dp, end = 4.dp, bottom = 12.dp))
    }
    if (!profiles.isFull) TextButton(onClick = { gate.guard { adding = true } }) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp)); Text("Add someone")
    } else Text("Eight people is the most one phone holds.", style = MaterialTheme.typography.bodySmall, color = dim)
    // Who writes the parent brief: the AI or the phone, and whether on mobile data (behind the gate, like the rest of Family).
    if (extras) com.bigpapituna.sharpears.ui.brief.BriefAiSettings(gate)
    // Testing (analytics/Tester.kt): "Help Roy test" and the diary, each change behind the gate.
    if (extras) com.bigpapituna.sharpears.analytics.TesterSettings(Modifier.padding(top = 16.dp), guard = { gate.guard(it) })

    renaming?.let { p ->
        // The caret starts after the old name (QA D8: typing went in front of it, "MilaMia").
        var field by remember(p.id) { mutableStateOf(com.bigpapituna.sharpears.ui.caretAtEnd(p.name)) }
        val name = field.text
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename ${p.name}") },
            text = { OutlinedTextField(field, { field = it.copy(text = it.text.take(Profile.MAX_NAME)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = Profile.cleanName(name).isNotEmpty(), onClick = { profiles.rename(p.id, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
    // Piano level (2.6): where the course starts for this person. Raising it passes more lessons by (marked skipped, no stars, still
    // open); lowering it opens the ones that were only passed by. Lessons played and stars are never taken away.
    levelFor?.let { p ->
        var picked by remember(p.id) { mutableStateOf(if (p.isKid) p.level.forKid else p.level) }
        AlertDialog(onDismissRequest = { levelFor = null }, title = { Text("Piano level for ${p.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 2.14: a kid's answers are No, A few and Lots, to the question the new-kid flow asks.
                    if (p.isKid) Text(Placement.kidQuestion(p.name), style = MaterialTheme.typography.bodyMedium, color = dim)
                    Level.options(p.kind).forEach { l ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(picked == l, role = Role.RadioButton) { picked = l },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.RadioButton(picked == l, onClick = null)
                            Column {
                                Text(if (p.isKid) l.kidLine!! else l.label, style = MaterialTheme.typography.titleSmall)
                                if (!p.isKid) Text(l.adultLine, style = MaterialTheme.typography.bodySmall, color = dim)
                            }
                        }
                    }
                    Text(Placement.planFor(picked, p.kind, p.reader, p.band).line.let { if (picked == Level.NEVER) "The course starts at its very beginning." else it } +
                        " Lessons already played, and their stars, are never taken away.", style = MaterialTheme.typography.bodySmall, color = dim,
                        modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { profiles.update(p.id) { it.copy(level = picked) }; levelFor = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { levelFor = null }) { Text("Cancel") } })
    }
    deleting?.let { (p, ask) ->
        if (ask == 1) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${p.name}?") },
            text = { Text("${p.name}'s progress, practice time and stickers are removed from this phone.") },
            confirmButton = { TextButton(onClick = { deleting = p to 2 }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } })
        else AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Are you sure?") },
            text = { Text("This cannot be undone. Export progress first if you might want ${p.name}'s back.") },
            confirmButton = { TextButton(onClick = { profiles.delete(p.id); deleting = null; open = null }) {
                Text("Delete ${p.name} for good", color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } })
    }
    if (adding) Dialog(onDismissRequest = { adding = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        com.bigpapituna.sharpears.ui.DialogBarsFollowTheme()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NewProfileFlow(onDone = { made ->
                profiles.create(made, switch = false)?.takeIf { it.isKid }?.let { kid ->
                    // The weekly brief: the day and time chosen at the end of the flow (Sunday at 6 pm unless changed).
                    com.bigpapituna.sharpears.data.BriefStore(appContext, kid.id).weekly = newSlot ?: com.bigpapituna.sharpears.engine.brief.WeeklySlot.DEFAULT
                    com.bigpapituna.sharpears.reminder.BriefReminder.schedule(appContext, kid)
                    if (newCheck) com.bigpapituna.sharpears.data.Prefs(appContext, kid.id).placementCheckPending = true
                }
                newSlot = null; newCheck = false; adding = false
            }, onCancel = { adding = false }, onWeekly = { newSlot = it }, onCheck = { newCheck = it })
        }
    }
    // The parent brief (ui/brief): one child at a time, behind the gate (the dialog asks again if it has closed).
    briefFor?.let { id ->
        val env = com.bigpapituna.sharpears.ui.brief.rememberBriefEnv()
        com.bigpapituna.sharpears.ui.brief.ParentBriefDialog(env, roster.profiles.filter { it.isKid }, onClose = { briefFor = null }, initial = id)
    }
}

/** ", a few lessons" / ", played some" after a person's kind, once they said how much they have played (nothing for a first time). */
private fun levelSuffix(p: Profile): String =
    if (p.level == Level.NEVER) "" else ", " + if (p.isKid) p.level.forKid.kidSummary!! else "played " + p.level.label.lowercase()

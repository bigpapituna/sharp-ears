package com.bigpapituna.sharpears.ui.brief.email

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.analytics.testerMask
import com.bigpapituna.sharpears.data.BriefEmail
import com.bigpapituna.sharpears.data.BriefStore
import com.bigpapituna.sharpears.data.EmailSettings
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.brief.email.EmailFlow
import com.bigpapituna.sharpears.engine.brief.email.EmailPrefs
import com.bigpapituna.sharpears.engine.brief.email.EmailSender
import com.bigpapituna.sharpears.engine.brief.email.EmailVerification
import com.bigpapituna.sharpears.engine.brief.email.FamilyEmail
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.reminder.BriefReminder
import com.bigpapituna.sharpears.reminder.EmailJob
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.brief.BriefAiSettings
import com.bigpapituna.sharpears.ui.brief.PillButton
import com.bigpapituna.sharpears.ui.brief.WeeklySheet
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.ParentGateController
import com.bigpapituna.sharpears.ui.family.PupAvatar
import com.bigpapituna.sharpears.ui.paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Settings > Briefs and email (2.8, docs/PLAN-2.8.md section 1): the one place for the brief by email. The family's one
 * address, the parent's, with its code (engine/brief/email/EmailFlow.kt follows its steps); then one row per child: the Daily
 * and Weekly switches, the weekly day and time (a tap changes it; it is the weekly notification's too) and Preview; then who
 * writes the brief ([BriefAiSettings]). Settings draws it under its own heading; the parent brief's Email button opens the
 * same composable in [EmailDialog]. There is no other email form.
 *
 * Behind the parent gate, as the per-child page was: the email part shows once the gate is passed, and every change asks again
 * when its ten minutes are up. The address is shown only here and never logged. Nothing is sent from the main thread: the work
 * runs on the IO dispatcher through [EmailJob], the same code the schedule uses.
 */
@Composable
fun BriefsAndEmail(kids: List<Profile>, modifier: Modifier = Modifier, gate: ParentGateController = LocalParentGate.current, zone: ZoneId = ZoneId.systemDefault()) {
    var unlocked by remember { mutableStateOf(gate.isOpen) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (unlocked) EmailPart(kids, gate, zone)
        else {
            Text("Each child's brief in your inbox, daily or weekly. Grown-ups only.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            PillButton("Open email settings", onClick = { gate.guard { unlocked = true } })
        }
        BriefAiSettings(gate)
    }
}

/** [BriefsAndEmail] as a page of its own, with a back arrow: [EmailDialog] and the debug route. */
@Composable
fun BriefsAndEmailPage(kids: List<Profile>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
            Text("Briefs and email", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            BriefsAndEmail(kids)
        }
    }
}

private enum class StopAsk { STOP, CHANGE }

@Composable
private fun EmailPart(kids: List<Profile>, gate: ParentGateController, zone: ZoneId) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    // 2.7's per-child addresses move to the family's (once) before anything here is read.
    val family = remember { EmailSettings.migrate(context); EmailSettings.family(context) }
    val ids = kids.map { it.id }
    fun views() = kids.associate { it.id to EmailSettings.of(context, it.id).load() }

    var address by remember { mutableStateOf(family.load()) }       // the family's record
    var children by remember(ids) { mutableStateOf(views()) }
    var slots by remember(ids) { mutableStateOf(kids.associate { it.id to BriefStore(context, it.id).weeklyOrDefault(it.isKid) }) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var typed by rememberSaveable { mutableStateOf("") }
    var addressTouched by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var weeklyFor by remember { mutableStateOf<Profile?>(null) }
    var previewFor by rememberSaveable { mutableStateOf<String?>(null) }
    var stopAsk by remember { mutableStateOf<StopAsk?>(null) }
    var mobile by remember { mutableStateOf(BriefEmail.mobileData(context)) }

    fun reload() { address = family.load(); children = views(); now = System.currentTimeMillis() }
    // The page follows the settings: a send in the background (the schedule, the alarm) shows up within a few seconds.
    LaunchedEffect(ids) { while (true) { delay(4_000); reload() } }

    val stage = EmailFlow.stage(address)
    val lead = FamilyEmail.lead(kids.map { it.id to (children[it.id] ?: EmailPrefs()) })

    /** Runs the job off the main thread and says what happened to the first email, in words. [ok] is what to say when it went. */
    fun runJob(reason: String, working: String, ok: String?) {
        busy = working; message = null
        scope.launch {
            val report = withContext(Dispatchers.IO) { runCatching { EmailJob.run(context, reason) }.getOrNull() }
            val r = report?.lead?.let { report.results[it] }
            busy = null; reload()
            message = when {
                r == null -> "I could not do that just now. Try again in a moment."
                r.sent.isNotEmpty() -> ok ?: EmailFlow.outcomeWords(r.last!!)
                r.last != null -> EmailFlow.outcomeWords(r.last)
                else -> r.prefs.held?.let { EmailFlow.heldWords(r.prefs) } ?: ok
            }
        }
    }

    fun sendCode(to: String) {
        if (EmailFlow.addressProblem(to) != null || to.isBlank()) { addressTouched = true; return }
        gate.guard {
            scope.launch {
                val started = withContext(Dispatchers.IO) { family.start(to) }
                if (started == EmailVerification.Started.BAD_ADDRESS) { addressTouched = true; return@launch }
                code = ""; reload()
                Log.d(TAG, "email: the family's first email asked for")
                runJob("first email", "Sending the code", "Check your inbox, then enter the code.")
            }
        }
    }

    fun confirm() {
        val digits = code.filter { it.isDigit() }
        if (digits.length != 6) return
        gate.guard {
            scope.launch {
                val v = withContext(Dispatchers.IO) { family.verify(digits) }
                reload(); code = ""
                message = EmailFlow.codeWords(v, EmailVerification().triesLeft(address))
                if (v == EmailVerification.Verified.OK) EmailJob.runAsync(context, "address confirmed")
            }
        }
    }

    fun setSwitch(kid: Profile, daily: Boolean, on: Boolean) = gate.guard {
        scope.launch {
            withContext(Dispatchers.IO) { EmailSettings.of(context, kid.id).let { if (daily) it.setDaily(on) else it.setWeekly(on) } }
            reload()
            EmailJob.runAsync(context, "frequency chosen")
        }
    }

    // Hidden as a whole in tester replays: the address and the children's names are on it.
    Column(Modifier.fillMaxWidth().testerMask(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!BriefEmail.hasKey) Note(EmailFlow.refusalWords(EmailSender.Refusal.NO_KEY) + " You can fill this in, but nothing will be sent.")

        Sub("Your address")
        when (stage) {
            EmailFlow.Stage.NO_ADDRESS -> if (kids.isEmpty()) {
                Text("Add a child in Family first. Each email is one child's brief.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            } else {
                Text("One address for every child's brief: yours. The first email carries only a six-digit code, so we know the address is yours before any brief goes.",
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                val problem = if (addressTouched) EmailFlow.addressProblem(typed) ?: "Type your email address.".takeIf { typed.isBlank() } else null
                OutlinedTextField(typed, { typed = it.take(254); addressTouched = false }, label = { Text("Your email address") }, singleLine = true,
                    isError = problem != null, supportingText = { problem?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendCode(typed) }))
                ChunkyButton(enabled = busy == null, modifier = Modifier.fillMaxWidth(), onClick = { sendCode(typed) }) { Text("Send code") }
                Text("Nothing is sent until you press this. Then it is only ever to this address.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }

            EmailFlow.Stage.FIRST_WAITING -> {
                val held = lead?.let { children[it] }?.let(EmailFlow::heldWords)
                Text("The code has not gone yet. It is for ${address.address}. ${held ?: "It goes as soon as the phone can send it."}", style = MaterialTheme.typography.bodyLarge)
                ChunkyButton(enabled = busy == null, modifier = Modifier.fillMaxWidth(),
                    onClick = { gate.guard { runJob("first email retried", "Sending the code", "Check your inbox, then enter the code.") } }) { Text("Try again now") }
                TextButton(onClick = { stopAsk = StopAsk.CHANGE }) { Text("Use a different address") }
            }

            EmailFlow.Stage.CODE -> {
                Text("Check your inbox, then enter the code. We sent it to ${address.address}. It can take a minute, and it may land in spam. It works for 24 hours.",
                    style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = Numeric.copy(fontSize = 30.sp, letterSpacing = 8.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { confirm() }))
                ChunkyButton(enabled = code.length == 6 && busy == null, modifier = Modifier.fillMaxWidth(), onClick = ::confirm) { Text("Confirm") }
                val wait = EmailFlow.resendIn(address, now)
                TextButton(enabled = wait == 0L && busy == null, onClick = { sendCode(address.address.orEmpty()) },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(if (wait == 0L) "Send a new code" else "Send a new code, ${EmailFlow.waitWords(wait)}")
                }
                TextButton(onClick = { stopAsk = StopAsk.CHANGE }) { Text("Use a different address") }
            }

            EmailFlow.Stage.CODE_GONE -> {
                Text("That code cannot be used any more. We can send a new one to ${address.address}.", style = MaterialTheme.typography.bodyLarge)
                val wait = EmailFlow.resendIn(address, now)
                ChunkyButton(enabled = wait == 0L && busy == null, modifier = Modifier.fillMaxWidth(), onClick = { sendCode(address.address.orEmpty()) }) {
                    Text(if (wait == 0L) "Send a new code" else "Send a new code, ${EmailFlow.waitWords(wait)}")
                }
                TextButton(onClick = { stopAsk = StopAsk.CHANGE }) { Text("Use a different address") }
            }

            EmailFlow.Stage.VERIFIED -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(address.address.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Text("Confirmed. Every child's emails go here.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                TextButton(onClick = { stopAsk = StopAsk.CHANGE }) { Text("Change") }
            }
        }
        busy?.let { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(it, style = MaterialTheme.typography.bodyMedium)
        } }
        message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

        if (kids.isNotEmpty()) {
            Sub("Each child")
            if (stage != EmailFlow.Stage.VERIFIED) Text(EmailFlow.ROWS_WAIT, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            kids.forEach { kid ->
                KidRow(kid, children[kid.id] ?: EmailPrefs(), slots[kid.id] ?: WeeklySlot.DEFAULT, confirmed = stage == EmailFlow.Stage.VERIFIED, zone = zone,
                    onDaily = { setSwitch(kid, daily = true, on = it) }, onWeekly = { setSwitch(kid, daily = false, on = it) },
                    onSlot = { gate.guard { weeklyFor = kid } }, onPreview = { gate.guard { previewFor = kid.id } })
            }
            Text("The weekly day and time are each child's weekly notification's too.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }

        if (address.address != null) {
            Sub("Sending")
            SwitchRow("Send emails on mobile data", "Off: emails wait for Wi-Fi. A brief with its PDF is a few hundred kilobytes.", mobile) { v ->
                gate.guard { BriefEmail.setMobileData(context, v); mobile = v; EmailJob.runAsync(context, "mobile data switch") }
            }
            TextButton(onClick = { stopAsk = StopAsk.STOP }) { Text("Stop all emails", color = c.error) }
        }
        Text("What goes in an email: one child's brief, its words and numbers (minutes, days, stars, lessons, slips, what the app suggests), and the full " +
            "report as a PDF. Never a recording, a note you typed, or anything about the other children. It is sent through Brevo, the email service. " +
            "More in Settings, About, Privacy.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }

    weeklyFor?.let { kid ->
        WeeklySheet(kid.name, slots[kid.id] ?: WeeklySlot.DEFAULT, zone = zone,
            onSave = { s ->
                BriefStore(context, kid.id).weekly = s; slots = slots + (kid.id to s)
                BriefReminder.schedule(context, kid); EmailJob.runAsync(context, "weekly slot")
            },
            onDismiss = { weeklyFor = null })
    }

    previewFor?.let { id -> kids.firstOrNull { it.id == id }?.let { kid -> EmailPreviewDialog(kid, onClose = { previewFor = null }) } ?: run { previewFor = null } }

    stopAsk?.let { ask ->
        val stopping = ask == StopAsk.STOP
        AlertDialog(onDismissRequest = { stopAsk = null },
            title = { Text(if (stopping) "Stop all emails?" else "Use a different address?") },
            text = { Text(if (stopping) "No child's brief will come by email any more, and your address is removed from this phone. The briefs stay in the app."
                else "Your address and any code are removed from this phone first. Then you can type another. Each child's switches stay as they are.") },
            confirmButton = { TextButton(onClick = {
                stopAsk = null
                gate.guard {
                    scope.launch { withContext(Dispatchers.IO) { family.stop() }; typed = ""; code = ""; message = null; reload(); EmailJob.runAsync(context, "stopped") }
                }
            }) { Text(if (stopping) "Stop the emails" else "Remove it", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { stopAsk = null }) { Text("Keep it") } })
    }
}

/**
 * One child: the avatar and name with Preview, the Daily switch, the Weekly switch with its day and time (a tap changes them),
 * and once the address is confirmed, what went last and why something is waiting (the no-key note is said once, above).
 */
@Composable
private fun KidRow(kid: Profile, prefs: EmailPrefs, slot: WeeklySlot, confirmed: Boolean, zone: ZoneId,
                   onDaily: (Boolean) -> Unit, onWeekly: (Boolean) -> Unit, onSlot: () -> Unit, onPreview: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PupAvatar(kid.avatar, size = 44.dp)
            Text(kid.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            PillButton("Preview", onClick = onPreview, modifier = Modifier.semantics { contentDescription = "Preview ${kid.name}'s email" })
        }
        Column(Modifier.padding(start = 58.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(prefs.frequency.daily, role = Role.Switch, onValueChange = onDaily),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Daily", style = MaterialTheme.typography.titleSmall)
                    Text(EmailFlow.dailyLine(kid.name), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                Switch(prefs.frequency.daily, onCheckedChange = null)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = "Change when the weekly email comes", onClick = onSlot)) {
                    Text("Weekly", style = MaterialTheme.typography.titleSmall)
                    Text(EmailFlow.weeklyLine(slot), style = MaterialTheme.typography.bodySmall, color = c.primary)
                }
                Switch(prefs.frequency.weekly, onCheckedChange = onWeekly, modifier = Modifier.semantics { contentDescription = "Weekly email for ${kid.name}" })
            }
            if (confirmed) {
                val lines = EmailFlow.lastLines(prefs, zone) + listOfNotNull(EmailFlow.heldWords(prefs).takeIf { prefs.held != EmailSender.Refusal.NO_KEY.name })
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
            }
        }
    }
}

/** A part of the section, styled as Settings' own parts (Keys, Sound, Progress). */
@Composable
private fun Sub(text: String) = Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp).semantics { heading() })

@Composable
private fun Note(text: String) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHigh).padding(14.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}

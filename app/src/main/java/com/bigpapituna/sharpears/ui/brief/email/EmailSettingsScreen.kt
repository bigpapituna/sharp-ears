package com.bigpapituna.sharpears.ui.brief.email

import android.util.Log
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.BriefEmail
import com.bigpapituna.sharpears.data.BriefStore
import com.bigpapituna.sharpears.data.EmailSettings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.brief.email.EmailFlow
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailSender
import com.bigpapituna.sharpears.engine.brief.email.EmailVerification
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.reminder.BriefReminder
import com.bigpapituna.sharpears.reminder.EmailJob
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.brief.PillButton
import com.bigpapituna.sharpears.ui.brief.WeeklyRow
import com.bigpapituna.sharpears.ui.brief.WeeklySheet
import com.bigpapituna.sharpears.ui.family.PupAvatar
import com.bigpapituna.sharpears.ui.paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * "Email the brief" for one child (grown-ups corner > Family > Mia > Email), behind the parent gate (EmailDialog wraps it).
 * One page that follows the address's journey (engine/brief/email/EmailFlow.kt): no address (the field, "Send the first
 * email"), the code ("Check your inbox, then enter the code": six digits, three tries, a new code after ten minutes),
 * then verified (how often: four big choices; the weekly day and time; a test email; what was last sent). Every refusal is
 * in kind words ("Email is not set up on this build", "Waiting for Wi-Fi", "That's enough for today").
 *
 * The address is shown only here. Nothing is sent from this screen's thread: the work runs on the IO dispatcher through
 * [EmailJob], the same code the schedule uses.
 */
@Composable
fun EmailSettingsScreen(child: Profile, onBack: () -> Unit, onPreview: () -> Unit, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault()) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember(child.id) { EmailSettings.of(context, child.id) }
    val c = MaterialTheme.colorScheme

    var prefs by remember { mutableStateOf(store.load()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var address by rememberSaveable(child.id) { mutableStateOf("") }
    var addressTouched by remember { mutableStateOf(false) }
    var code by rememberSaveable(child.id) { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var slot by remember { mutableStateOf(BriefStore(context, child.id).weeklyOrDefault(child.isKid)) }
    var weeklyOpen by remember { mutableStateOf(false) }
    var stopAsk by remember { mutableStateOf<StopAsk?>(null) }
    var mobile by remember { mutableStateOf(BriefEmail.mobileData(context)) }

    // The page follows the settings: a send in the background (the schedule, the alarm) shows up within a few seconds.
    LaunchedEffect(child.id) {
        while (true) { delay(4_000); now = System.currentTimeMillis(); prefs = store.load() }
    }

    val stage = EmailFlow.stage(prefs)
    fun reload() { prefs = store.load(); now = System.currentTimeMillis() }

    /** Runs the job off the main thread and says what happened to this child's email, in words. [ok] is what to say when it went. */
    fun runJob(reason: String, working: String, ok: String?) {
        busy = working; message = null
        scope.launch {
            val report = withContext(Dispatchers.IO) { runCatching { EmailJob.run(context, reason) }.getOrNull() }
            val r = report?.results?.get(child.id)
            busy = null; reload()
            message = when {
                r == null -> "I could not do that just now. Try again in a moment."
                r.sent.isNotEmpty() -> ok ?: EmailFlow.outcomeWords(r.last!!)
                r.last != null -> EmailFlow.outcomeWords(r.last)
                else -> store.load().held?.let { EmailFlow.heldWords(store.load()) } ?: ok
            }
        }
    }

    fun sendFirst(to: String) {
        if (EmailFlow.addressProblem(to) != null || to.isBlank()) { addressTouched = true; return }
        scope.launch {
            val started = withContext(Dispatchers.IO) { store.start(to) }
            if (started == EmailVerification.Started.BAD_ADDRESS) { addressTouched = true; return@launch }
            code = ""; reload()
            Log.d(TAG, "email: ${child.id} first email asked for")
            runJob("first email", "Sending the first email", "Check your inbox, then enter the code.")
        }
    }

    fun confirm() {
        val typed = code.filter { it.isDigit() }
        if (typed.length != 6) return
        scope.launch {
            val v = withContext(Dispatchers.IO) { store.verify(typed) }
            reload(); code = ""
            message = EmailFlow.codeWords(v, EmailVerification().triesLeft(prefs))
            if (v == EmailVerification.Verified.OK) EmailJob.runAsync(context, "address confirmed")
        }
    }

    fun choose(f: EmailFrequency) {
        scope.launch {
            withContext(Dispatchers.IO) { store.choose(f) }
            reload(); message = null
            EmailJob.runAsync(context, "frequency chosen")
        }
    }

    fun test() {
        val period = if (prefs.frequency == EmailFrequency.WEEKLY) Period.WEEK else Period.DAY
        busy = "Sending a test email"; message = null
        scope.launch {
            val out = withContext(Dispatchers.IO) { runCatching { EmailJob.sendTest(context, child, period) }.getOrNull() }
            busy = null; reload()
            message = if (out == null) "I could not do that just now. Try again in a moment." else EmailFlow.outcomeWords(out)
        }
    }

    Column(modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
            Text("Email the brief", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PupAvatar(child.avatar, size = 52.dp)
                Column {
                    Text("For ${child.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Grown-ups only", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
            if (!BriefEmail.hasKey) Note(EmailFlow.refusalWords(EmailSender.Refusal.NO_KEY) + " You can fill this in, but nothing will be sent.")

            when (stage) {
                EmailFlow.Stage.NO_ADDRESS -> {
                    Text("Get ${child.name}'s brief in your inbox without opening the app: what happened, what to try next, and one thing to praise. " +
                        "The first email carries a code, so we know the address is yours.", style = MaterialTheme.typography.bodyLarge)
                    val problem = if (addressTouched) EmailFlow.addressProblem(address) ?: "Type your email address.".takeIf { address.isBlank() } else null
                    OutlinedTextField(address, { address = it.take(254); addressTouched = false }, label = { Text("Your email address") }, singleLine = true,
                        isError = problem != null, supportingText = { problem?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendFirst(address) }))
                    ChunkyButton(enabled = busy == null, modifier = Modifier.fillMaxWidth(), onClick = { sendFirst(address) }) { Text("Send the first email") }
                    Text("Nothing is sent until you press this. Then it is only ever to this address.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }

                EmailFlow.Stage.FIRST_WAITING -> {
                    Heading("The first email has not gone yet")
                    Text("It is for ${prefs.address}. ${EmailFlow.heldWords(prefs) ?: "It goes as soon as the phone can send it."}", style = MaterialTheme.typography.bodyLarge)
                    ChunkyButton(enabled = busy == null, modifier = Modifier.fillMaxWidth(), onClick = { runJob("first email retried", "Sending the first email", "Check your inbox, then enter the code.") }) { Text("Try again now") }
                    TextButton(onClick = { stopAsk = StopAsk.CHANGE }) { Text("Use a different address") }
                }

                EmailFlow.Stage.CODE -> {
                    Heading("Check your inbox, then enter the code")
                    Text("We sent a code to ${prefs.address}. It can take a minute, and it may land in spam. It works for 24 hours.", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = Numeric.copy(fontSize = 30.sp, letterSpacing = 8.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { confirm() }))
                    ChunkyButton(enabled = code.length == 6 && busy == null, modifier = Modifier.fillMaxWidth(), onClick = ::confirm) { Text("Confirm") }
                    val wait = EmailFlow.resendIn(prefs, now)
                    TextButton(enabled = wait == 0L && busy == null, onClick = { sendFirst(prefs.address.orEmpty()) }) {
                        Text(if (wait == 0L) "Send a new code" else "Send a new code, ${EmailFlow.waitWords(wait)}")
                    }
                    TextButton(onClick = { stopAsk = StopAsk.CHANGE }) { Text("Use a different address") }
                }

                EmailFlow.Stage.CODE_GONE -> {
                    Heading("Ask for a new code")
                    Text("That code cannot be used any more. We can send a new one to ${prefs.address}.", style = MaterialTheme.typography.bodyLarge)
                    val wait = EmailFlow.resendIn(prefs, now)
                    ChunkyButton(enabled = wait == 0L && busy == null, modifier = Modifier.fillMaxWidth(), onClick = { sendFirst(prefs.address.orEmpty()) }) {
                        Text(if (wait == 0L) "Send a new code" else "Send a new code, ${EmailFlow.waitWords(wait)}")
                    }
                    TextButton(onClick = { stopAsk = StopAsk.CHANGE }) { Text("Use a different address") }
                }

                EmailFlow.Stage.VERIFIED -> Verified(child, prefs, slot, now, zone, busy == null,
                    onChoose = ::choose, onWeekly = { weeklyOpen = true }, onTest = ::test, onPreview = onPreview, onChange = { stopAsk = StopAsk.CHANGE })
            }

            busy?.let { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(it, style = MaterialTheme.typography.bodyMedium)
            } }
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (message == null && stage != EmailFlow.Stage.FIRST_WAITING) EmailFlow.heldWords(prefs)?.takeIf { stage == EmailFlow.Stage.VERIFIED }?.let { Note(it) }

            if (prefs.address != null) {
                Heading("Sending")
                SwitchRow("Send emails on mobile data", "Off: emails wait for Wi-Fi. A brief with its PDF is a few hundred kilobytes.", mobile) { v ->
                    BriefEmail.setMobileData(context, v); mobile = v
                    EmailJob.runAsync(context, "mobile data switch")
                }
                TextButton(onClick = { stopAsk = StopAsk.STOP }) { Text("Stop these emails", color = c.error) }
            }
            Text("What goes in the email: the brief's words and numbers (minutes, days, stars, lessons, slips, what the app suggests) and the full report as a PDF. " +
                "Never a recording, a note you typed, or anything about the other children. It is sent through Brevo, the email service. More in Settings, About, Privacy.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
    }

    if (weeklyOpen) WeeklySheet(child.name.takeIf { child.isKid }.orEmpty(), slot, zone = zone,
        onSave = { s -> BriefStore(context, child.id).weekly = s; slot = s; BriefReminder.schedule(context, child); EmailJob.runAsync(context, "weekly slot") },
        onDismiss = { weeklyOpen = false })

    stopAsk?.let { ask ->
        val stopping = ask == StopAsk.STOP
        AlertDialog(onDismissRequest = { stopAsk = null },
            title = { Text(if (stopping) "Stop these emails?" else "Use a different address?") },
            text = { Text(if (stopping) "${child.name}'s brief will stop coming by email, and the address is removed from this phone. The brief stays in the app."
                else "The address and any code are removed from this phone first. Then you can type another.") },
            confirmButton = { TextButton(onClick = {
                stopAsk = null
                scope.launch { withContext(Dispatchers.IO) { store.stop() }; address = ""; code = ""; message = null; reload(); EmailJob.runAsync(context, "stopped") }
            }) { Text(if (stopping) "Stop the emails" else "Remove it", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { stopAsk = null }) { Text("Keep it") } })
    }
}

private enum class StopAsk { STOP, CHANGE }

@Composable
private fun Verified(
    child: Profile, prefs: com.bigpapituna.sharpears.engine.brief.email.EmailPrefs, slot: WeeklySlot, now: Long, zone: ZoneId, enabled: Boolean,
    onChoose: (EmailFrequency) -> Unit, onWeekly: () -> Unit, onTest: () -> Unit, onPreview: () -> Unit, onChange: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${child.name}'s brief goes to", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                Text(prefs.address.orEmpty(), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
        Heading(if (prefs.chosen) "How often" else "Choose how often")
        if (!prefs.chosen) Text("The address is confirmed. Until you choose, ${child.name}'s week comes once a week.", style = MaterialTheme.typography.bodyMedium, color = c.primary)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmailFlow.choices(child.name, slot).forEach { ch ->
                val chosen = prefs.chosen && prefs.frequency == ch.frequency
                Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (chosen) c.primaryContainer else c.surfaceContainerHigh)
                    .border(if (chosen) 2.dp else 1.dp, if (chosen) c.primary else c.outlineVariant, RoundedCornerShape(20.dp))
                    .selectable(chosen, enabled = enabled, role = Role.RadioButton) { onChoose(ch.frequency) }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ch.label, style = MaterialTheme.typography.titleMedium, color = if (chosen) c.onPrimaryContainer else c.onSurface)
                    Text(ch.line, style = MaterialTheme.typography.bodyMedium, color = if (chosen) c.onPrimaryContainer else c.onSurfaceVariant)
                }
            }
        }
        if (prefs.frequency.weekly) {
            WeeklyRow(slot, onChange = onWeekly)
            Text("The weekly day and time are shared with the weekly notification.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        val last = EmailFlow.lastLines(prefs, zone)
        if (last.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Last sent", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            last.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Heading("See it, try it")
        PillButton("See what it looks like", onClick = onPreview, modifier = Modifier.fillMaxWidth())
        PillButton("Send me a test email now", onClick = onTest, enabled = enabled, modifier = Modifier.fillMaxWidth())
        Text(if (prefs.frequency == EmailFrequency.WEEKLY) EmailFlow.TEST_NOTE.replace("today's brief", "this week's brief") else EmailFlow.TEST_NOTE,
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp).semantics { heading() })

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

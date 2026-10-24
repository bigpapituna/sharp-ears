package com.bigpapituna.sharpears.ui.brief

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.brief.WeeklyPlan
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.reminder.Reminder
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.paper
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * The weekly brief's day and time, chosen by the grown-up: at the end of the new-kid flow ("When should we send you Mia's
 * weekly brief?", Sunday at 6 pm unless changed) and later in the parent corner. A notification needs the phone's
 * permission; asked when it is switched on, never in a way that blocks (the brief is always there to open in the app).
 */

private val WEEK = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/**
 * Whether the phone lets the app notify, read again each time the app comes back (from the phone's settings, say). When it
 * turns on, every weekly alarm is set (BriefReminder.schedule sets none without it, QA 2.8.3).
 */
@Composable
fun rememberCanNotify(): Boolean {
    val context = LocalContext.current
    val shown = com.bigpapituna.sharpears.ui.onScreen()
    val can = remember(shown) { Reminder.canNotify(context) }
    var was by remember { mutableStateOf(can) }
    androidx.compose.runtime.LaunchedEffect(can) {
        if (can && !was) com.bigpapituna.sharpears.reminder.BriefReminder.scheduleAll(context.applicationContext)
        was = can
    }
    return can
}

/** The phone's notification settings for this app. */
fun openNotificationSettings(context: android.content.Context) {
    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
}

/** "Off, notifications are off on this phone." and the way to the phone's notification settings for the app. */
@Composable
fun NotificationsOffNote(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Text(WeeklyPlan.NOTIFICATIONS_OFF + ".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { openNotificationSettings(context) }) { Text("Open notification settings") }
    }
}

/** The day chips, the time stepper and the on/off switch; reports every change. */
@Composable
fun WeeklyPicker(slot: WeeklySlot, onChange: (WeeklySlot) -> Unit, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault(), now: () -> Long = System::currentTimeMillis) {
    val c = MaterialTheme.colorScheme
    val canNotify = rememberCanNotify()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Send a notification", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(slot.on, { onChange(slot.copy(on = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary),
                modifier = Modifier.semantics { contentDescription = "Send a notification" })
        }
        Text("Day", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEK.forEach { d ->
                val chosen = d == slot.day
                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape).background(if (chosen) c.primary else c.surfaceContainerHigh)
                    .selectable(chosen, role = Role.RadioButton) { onChange(slot.copy(day = d)) }
                    .semantics { contentDescription = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }, contentAlignment = Alignment.Center) {
                    Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        color = if (chosen) c.onPrimary else c.onSurface)
                }
            }
        }
        Text("Time", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            StepButton("Earlier", R.drawable.ic_remove) { onChange(slot.copy(minutes = WeeklyPlan.step(slot.minutes, -1))) }
            Text(WeeklyPlan.timeLabel(slot.minutes), style = Numeric.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium), textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 140.dp).padding(horizontal = 8.dp).semantics { contentDescription = "Time, " + WeeklyPlan.timeLabel(slot.minutes) })
            StepButton("Later", R.drawable.ic_add) { onChange(slot.copy(minutes = WeeklyPlan.step(slot.minutes, 1))) }
        }
        if (slot.on && !canNotify) NotificationsOffNote()
        else Text(if (slot.on) "Next one: " + nextLabel(slot, zone, now()) + "." else "No notification. The brief is still here whenever you open it.",
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

/** "Sunday 25 Oct at 6 pm". */
fun nextLabel(slot: WeeklySlot, zone: ZoneId, now: Long): String {
    val at = WeeklyPlan.next(Instant.ofEpochMilli(now), zone, slot).atZone(zone)
    return at.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)) + " at " + WeeklyPlan.timeLabel(slot.minutes)
}

@Composable
private fun StepButton(label: String, icon: Int, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(52.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = c.onSurface)
    }
}

/** Asks for the notification permission when a slot is switched on and the phone has not allowed it; calls [then] either way. */
@Composable
fun rememberNotificationAsk(then: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { then() }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !Reminder.canNotify(context)) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) else then()
    }
}

/**
 * A row for the Week tab and the parent corner: "Weekly brief", when it comes, and Change. On a phone that does not let the
 * app notify it says "Off, notifications are off on this phone" with the way to the phone's settings under it.
 */
@Composable
fun WeeklyRow(slot: WeeklySlot, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val canNotify = rememberCanNotify()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = "Change when the weekly brief comes", onClick = onChange).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Weekly brief", style = MaterialTheme.typography.titleSmall)
                Text(WeeklyPlan.stateLabel(slot, canNotify), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
            Text("Change", style = MaterialTheme.typography.labelLarge, color = c.primary)
        }
        if (slot.on && !canNotify) {
            val context = LocalContext.current
            TextButton(onClick = { openNotificationSettings(context) }) { Text("Open notification settings") }
        }
    }
}

/** The sheet the row opens. Saving writes the slot, sets the alarm (through [onSave]) and asks for the notification permission when it is on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklySheet(name: String, slot: WeeklySlot, onSave: (WeeklySlot) -> Unit, onDismiss: () -> Unit, zone: ZoneId = ZoneId.systemDefault(), now: () -> Long = System::currentTimeMillis) {
    var draft by remember { mutableStateOf(slot) }
    val ask = rememberNotificationAsk { onSave(draft); onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("When should the weekly brief come?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(if (name.isNotBlank()) "A notification opens $name's week." else "A notification opens your week.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            WeeklyPicker(draft, { draft = it }, zone = zone, now = now)
            ChunkyButton(onClick = { if (draft.on) ask() else { onSave(draft); onDismiss() } }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Not now") }
        }
    }
}

/**
 * The end of the new-kid flow: "When should we send you Mia's weekly brief?" with Sunday at 6 pm ready. [onChosen] gets the
 * slot (the caller stores it once the profile exists); "Not now" keeps the default.
 */
@Composable
fun WeeklyTimeStep(name: String, onChosen: (WeeklySlot) -> Unit, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault(), now: () -> Long = System::currentTimeMillis) {
    var slot by remember { mutableStateOf(WeeklySlot.DEFAULT) }
    val ask = rememberNotificationAsk { onChosen(slot) }
    BackHandler { onChosen(WeeklySlot.DEFAULT) }
    Column(modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PupSays(Pose.WELCOME, "When should we send you ${name}'s weekly brief?", size = 104.dp, style = MaterialTheme.typography.titleLarge)
            Text("A short read on how the week went, with one or two things to try. You choose the day and time, and you can change it later in the parent corner.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WeeklyPicker(slot, { slot = it }, zone = zone, now = now)
        }
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ChunkyButton(onClick = { if (slot.on) ask() else onChosen(slot) }, modifier = Modifier.fillMaxWidth()) { Text("Save and go") }
            TextButton(onClick = { onChosen(WeeklySlot.DEFAULT) }) { Text("Not now, use Sunday at 6 pm") }
        }
    }
}

package com.bigpapituna.sharpears.ui.brief

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Share
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefFormat
import com.bigpapituna.sharpears.engine.brief.ExportDoc
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.family.PupAvatar
import com.bigpapituna.sharpears.ui.family.avatarLabel
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The parent brief (docs/PARENT-BRIEF-PLAN.md): the children as avatar tabs across the top, one at a time (never side by
 * side, never compared), then Session, Today and Week for that child, each as the one-minute read with the full report
 * behind a button. [children] with one grown-up and [showChildren] false is "My week" (ui/brief/MyWeek.kt).
 *
 * Behind the parent gate: the caller wraps it in Gated (ParentBriefDialog does); every action asks the gate again when it
 * has closed.
 */
@Composable
fun ParentBriefScreen(
    env: BriefEnv, children: List<Profile>, onBack: () -> Unit, modifier: Modifier = Modifier,
    initialChild: String? = null, initialPeriod: Period = Period.WEEK, periods: List<Period> = Period.entries,
    title: String = "Parent brief", showChildren: Boolean = true, openExport: Boolean = false, openAsk: String = "",
    /** Demo and test hooks: the full report open, a recommendation's sheet open, the weekly sheet open. */
    expanded: Boolean = false, openCard: Int? = null, openWeekly: Boolean = false,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val controller = remember(env) { BriefController(env, scope) }
    DisposableEffect(controller) { onDispose { controller.commitAll() } }
    var childId by rememberSaveable { mutableStateOf(initialChild ?: children.firstOrNull()?.id) }
    var periodName by rememberSaveable { mutableStateOf(initialPeriod.name) }
    val period = Period.valueOf(periodName).takeIf { it in periods } ?: periods.first()
    val child = children.firstOrNull { it.id == childId } ?: children.firstOrNull()
    var exporting by remember { mutableStateOf<Pair<Brief, Findings>?>(null) }
    var weeklyOpen by remember { mutableStateOf(openWeekly) }

    Column(modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        if (child == null) {
            PupSays(Pose.WELCOME, "Add a child in Family to see how their week goes.", Modifier.padding(24.dp), size = 96.dp)
            return@Column
        }
        if (showChildren) ChildTabs(children, child.id, onPick = { childId = it })
        if (periods.size > 1) PeriodTabs(periods, period, onPick = { periodName = it.name })
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState(), enabled = true).padding(horizontal = 20.dp).padding(top = 6.dp)) {
            val key = child.id + "/" + period.name
            Crossfade(key, animationSpec = tween(if (reducedMotion()) 0 else 180), label = "brief") { k ->
                val c = children.firstOrNull { k.startsWith(it.id + "/") } ?: child
                val p = Period.valueOf(k.substringAfter('/'))
                val page = controller.page(c, p)
                var slot by remember(c.id) { mutableStateOf(env.weekly.get(c)) }
                BriefPage(page, controller, onSend = { b, f -> exporting = b to f },
                    footer = if (p != Period.WEEK) null else { {
                        WeeklyRow(slot, onChange = { weeklyOpen = true })
                        if (weeklyOpen) WeeklySheet(c.name.takeIf { c.kind == ProfileKind.KID }.orEmpty(), slot, zone = env.zone, now = env.now,
                            onSave = { s -> slot = s; env.weekly.set(c, s) }, onDismiss = { weeklyOpen = false })
                    } })
                LaunchedEffect(page.phase) {
                    if (page.phase != Phase.READY) return@LaunchedEffect
                    if (expanded) page.expanded = true
                    if (openCard != null && page.cards.isEmpty()) controller.openSheet(page, openCard)
                    if (openExport && exporting == null) exporting = page.brief!! to page.findings!!
                }
                LaunchedEffect(page.phase, openAsk) { if (openAsk.isNotEmpty() && page.phase == Phase.READY && page.asks.exchanges.isEmpty()) controller.ask(page, openAsk) }
            }
        }
    }
    exporting?.let { (brief, findings) ->
        val c = children.firstOrNull { it.id == childId } ?: child ?: return@let
        ExportSheet(env, brief, findings, c, onDismiss = { exporting = null })
    }
}

@Composable
private fun ChildTabs(children: List<Profile>, selectedId: String, onPick: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        children.forEach { p ->
            val chosen = p.id == selectedId
            Column(Modifier.widthIn(min = 76.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Tab) { onPick(p.id) }.padding(horizontal = 8.dp, vertical = 6.dp)
                .semantics { selected = chosen; contentDescription = "${p.name}, ${avatarLabel(p.avatar)}" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PupAvatar(p.avatar, size = 60.dp, selected = chosen)
                Text(p.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium), maxLines = 1,
                    color = if (chosen) c.onSurface else c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PeriodTabs(periods: List<Period>, selected: Period, onPick: (Period) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(CircleShape).background(c.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        periods.forEach { p ->
            val chosen = p == selected
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape).background(if (chosen) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(role = Role.Tab) { onPick(p) }.semantics { this.selected = chosen }, contentAlignment = Alignment.Center) {
                Text(BriefFormat.periodLabel(p), style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (chosen) c.onPrimaryContainer else c.onSurfaceVariant)
            }
        }
    }
}

/**
 * Send: the brief as a picture and a PDF, previewed here, then the system share sheet (Share.files) when the grown-up
 * presses Send. Short is the one-minute read; Full is the whole report (the PDF has every page, the picture is capped).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(env: BriefEnv, brief: Brief, findings: Findings, who: Profile, onDismiss: () -> Unit, startFull: Boolean = false) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var full by rememberSaveable { mutableStateOf(startFull) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val doc = remember(brief, findings, full) { ExportDoc.of(brief, findings, who.name, env.titles, full, env.now(), env.zone) }
    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, doc) {
        value = null
        value = withContext(Dispatchers.Default) { runCatching { BriefExport(context).card(doc) }.getOrNull()?.asImageBitmap() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surfaceContainerLow) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Send this brief", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("One minute read", selected = !full, onClick = { full = false })
                PillButton("Full report", selected = full, onClick = { full = true })
            }
            Box(Modifier.fillMaxWidth().heightIn(max = 340.dp, min = 200.dp).clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
                .verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                val img = preview
                if (img == null) Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
                else Image(img, contentDescription = "A preview of the picture that will be sent", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            }
            Text("A picture and a PDF go to the share sheet. Nothing is sent until you pick an app there.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            if (failed) Text("I could not make the files just now. Try again in a moment.", style = MaterialTheme.typography.bodyMedium, color = c.primary)
            ChunkyButton(enabled = !sending, modifier = Modifier.fillMaxWidth(), onClick = {
                sending = true; failed = false
                scope.launch {
                    val files = withContext(Dispatchers.IO) { runCatching {
                        BriefExport.pack(context, doc, who.name, findings.period, java.time.Instant.ofEpochMilli(env.now()).atZone(env.zone).toLocalDate())
                    }.onFailure { Log.d(TAG, "brief: export failed: $it") }.getOrNull() }
                    sending = false
                    if (files == null) failed = true
                    else { Share.files(context, files, "Send this brief", ExportDoc.title(who.name, findings.period, who.kind == ProfileKind.KID)); onDismiss() }
                }
            }) { Text(if (sending) "Making the files" else "Send") }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Not now") }
        }
    }
}

package com.bigpapituna.sharpears.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.nav.Welcome
import com.bigpapituna.sharpears.ui.motion.ScreenMotion
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.tour.TourPicture

/**
 * What's new (2.7): a sheet with the pup and up to three lines for [version] (engine/nav/Welcome.kt), shown once per version
 * to a grown-up who was here before it. Closing it any way counts as seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(version: String, lines: List<String>, onDone: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pup(Pose.GREAT_JOB, size = 64.dp, idle = false)
                    Column {
                        Text("What's new", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text("Sharp Ears $version", style = Numeric.merge(MaterialTheme.typography.labelMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                        Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                ChunkyButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Got it") }
            }
        }
    }
}

/**
 * The tour (2.7, rewritten in 2.15): the five things that matter most ("One Start for your day", "Play songs by chords with a
 * band", "See your timing, bar by bar", "Loop the bars you missed", "A streak that forgives"), a small picture of the real
 * screen on each (ui/tour/TourArt.kt) and one sentence. It stays over whichever tab the grown-up is on. Skip or the last
 * card's button ends it ([onDone] with whether it was skipped); it never shows again for this profile. In the Plain look the
 * streak card is left out (four cards).
 */
@Composable
fun TourDialog(onDone: (skipped: Boolean) -> Unit, /** 2.14: the hello on the first card reads the grown-up's name ("Hi, Dana."), or "Hi there." without one. */ name: String? = null) {
    // 2.15.0 merge: in the Plain look (no streak counter) the streak card is skipped (Welcome.tour).
    val plain = LocalPlain.current.on
    TourCards(remember(plain) { Welcome.tour(plain) }, label = "tour", hello = com.bigpapituna.sharpears.engine.family.Greeting.hi(name), onDone = onDone)
}

/**
 * "For grown-ups" (2.15): three cards the first time a grown-up is on their own screen after adding a kid: Family today with the
 * brief, the daily time that closes the app, and the gate with Lock now ([Welcome.parentsTour]; [emailed] is whether this build
 * can email a brief). Skippable; never again on this phone.
 */
@Composable
fun ParentsTourDialog(emailed: Boolean, onDone: (skipped: Boolean) -> Unit) {
    TourCards(remember(emailed) { Welcome.parentsTour(emailed) }, label = "parents tour", hello = "For grown-ups", onDone = onDone)
}

/** The cards of a tour in one dialog: the picture, a title, one line, the dots, Skip and Next. [hello] sits over the first card. */
@Composable
private fun TourCards(cards: List<Welcome.Card>, label: String, hello: String?, onDone: (skipped: Boolean) -> Unit) {
    var i by rememberSaveable { mutableIntStateOf(0) }
    // 2.15 (posthog + show off): tour_card_seen names the card by its picture (plan, band, timing, loop, streak; the
    // parents' tour family, limit, gate), the tab it once opened being gone.
    androidx.compose.runtime.LaunchedEffect(i) { com.bigpapituna.sharpears.analytics.Events.tourCardSeen(cards[i].art.name) }
    val still = reducedMotion()
    Dialog(onDismissRequest = { onDone(true) }) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedContent(i, transitionSpec = { ScreenMotion.tab(still) }, label = label) { n ->
                    val c = cards[n]
                    Column(Modifier.fillMaxWidth().heightIn(min = 300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (n == 0 && hello != null) Text(hello, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        TourPicture(c.art)
                        Text(c.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                        Text(c.line, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Where the tour stands: the progress dots carry state, as in lessons.
                Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Card ${i + 1} of ${cards.size}" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cards.indices.forEach { k ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (k == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (i < cards.lastIndex) TextButton(onClick = { onDone(true) }) { Text("Skip") }
                    Spacer(Modifier.weight(1f))
                    ChunkyButton(onClick = { if (i < cards.lastIndex) i++ else onDone(false) }) {
                        Text(if (i < cards.lastIndex) "Next" else "Let's go")
                    }
                }
            }
        }
    }
}

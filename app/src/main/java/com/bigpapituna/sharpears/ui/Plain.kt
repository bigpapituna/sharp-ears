package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.family.PlainLook

/*
 * 2.15, the Plain look on screen (engine/family/PlainLook.kt has the rules and the words). ONE switch, [LocalPlain], provided
 * by MainActivity for the open profile (PlainLook.on: a little kid's is always off); every pup, cheer pop, step pop, streak
 * and reward entry point reads it itself (PlainWiringTest), so the eighty-odd places that draw the pup need no change.
 */

/** The Plain look for the open profile: [on] (never on a little kid's profile) and its "Step sound" ([stepSound]). */
@Immutable
data class PlainMode(val on: Boolean = false, val stepSound: Boolean = false)

/** Provided by MainActivity (non-static: it flips live from Settings). Previews, tests and the picker get it off. */
val LocalPlain = compositionLocalOf { PlainMode() }

/** True while the Plain look is on for the profile playing. */
@Composable @ReadOnlyComposable
fun plainLook(): Boolean = LocalPlain.current.on

/**
 * Where a screen needs something to look at in the pup's place (a finish, the goodbye, the level ring on You): the app's
 * own mark, the two notes and the keys (the launcher's monochrome icon), small on a quiet disc. Decorative.
 */
@Composable
fun PlainMark(size: Dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.size(size).clip(CircleShape).background(c.surfaceContainerHigh).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_logo_mono), contentDescription = null, tint = c.primary, modifier = Modifier.size(size * 0.82f))
    }
}

/** What the pup would have said, in the Plain look: the words alone on a quiet surface, no pup and no tail pointing at him. */
@Composable
fun PlainWords(line: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    val c = MaterialTheme.colorScheme
    Text(line, style = style, color = c.onSurface,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp))
}

/**
 * The one-time offer (2.15): "Prefer a plainer look? Turn off the pup, cheers and streaks." with Try Plain look and No thanks.
 * Small and outlined, inline where it shows (Today under the card, a lesson's finish): it never blocks anything.
 */
@Composable
fun PlainOfferCard(onTry: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp))
        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(PlainLook.OFFER_LINE, style = MaterialTheme.typography.bodyMedium, color = c.onSurface)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onNo) { Text(PlainLook.OFFER_NO, color = c.onSurfaceVariant) }
            Spacer(Modifier.size(4.dp))
            TextButton(onClick = onTry) { Text(PlainLook.OFFER_YES) }
        }
    }
}

/**
 * The days played in the Plain look (the paw strip's place): a dot for each day, filled when there was any practice, outlined
 * otherwise; no paws and no rest-day moon (a rest day still keeps the streak, silently). [days] are (initial, played, today).
 */
@Composable
fun PlainDays(days: List<Triple<String, Boolean, Boolean>>, size: Dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val played = days.count { it.second }
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "Days played this week: $played of ${days.size}" },
        horizontalArrangement = Arrangement.spacedBy(size * 0.5f)) {
        days.forEach { (initial, did, today) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(size).clip(CircleShape)
                    .then(if (did) Modifier.background(c.primary) else Modifier.border(1.5.dp, if (today) c.primary else c.outline, CircleShape)))
                Text(initial, style = MaterialTheme.typography.labelSmall, color = if (today) c.primary else c.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The one-time offer where it may show ([moment]: Today on opening the app, a lesson's finish): decided once as it first
 * shows (PlainLook.offerDue: a grown-up or big kid, Plain off, never offered on this profile, and on opening the app after
 * seven days played or more, and the dashboard's plain_look_offer flag not saying no), marked offered at once (once per
 * profile, answered or not), gone after either answer. Try
 * Plain look turns the switch on, logged as a settings event like any other.
 */
@Composable
fun PlainOffer(prefs: com.bigpapituna.sharpears.data.Prefs, audience: com.bigpapituna.sharpears.engine.family.Audience, moment: PlainLook.Moment,
               modifier: Modifier = Modifier, daysPlayed: () -> Int = { 0 }) {
    val due = androidx.compose.runtime.remember {
        // 2.15 merge: the dashboard's plain_look_offer flag (default yes) asked last; no means not shown and not marked offered.
        PlainLook.offerDue(audience, prefs.plainLook, prefs.plainOffered, moment, daysPlayed(),
            flag = { com.bigpapituna.sharpears.analytics.AppFlags.plainLookOffer })
    }
    var answered by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (!due || answered) return
    androidx.compose.runtime.LaunchedEffect(Unit) {
        prefs.plainOffered = true
        android.util.Log.d(com.bigpapituna.sharpears.TAG, "plain: offer shown (${moment.name.lowercase()})")
    }
    PlainOfferCard(
        onTry = {
            answered = true
            prefs.plainLook = true
            com.bigpapituna.sharpears.data.Briefs.setting("plainLook", true)
            android.util.Log.d(com.bigpapituna.sharpears.TAG, "plain: look on (the offer)")
        },
        onNo = { answered = true; android.util.Log.d(com.bigpapituna.sharpears.TAG, "plain: offer declined") },
        modifier)
}

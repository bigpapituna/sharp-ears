package com.bigpapituna.sharpears.ui

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.engine.MicCardCopy
import com.bigpapituna.sharpears.engine.MicFacts
import com.bigpapituna.sharpears.engine.MicTrust

/*
 * 2.15 mic trust: the microphone card (engine/MicTrust.kt writes it) before the microphone is first asked for, and the one
 * way every screen asks ([rememberMicAsk]). MicTrustWiringTest fails on a RECORD_AUDIO request anywhere else (the parent
 * brief's dictation is speech, not notes, and keeps its own).
 */

/** The activity's microphone (MainActivity provides it next to LocalProfile), for screens that only need to ask for it. */
val LocalMic = androidx.compose.runtime.staticCompositionLocalOf<MicInput?> { null }

/** What this build and this phone do with sound and data, for the card and the privacy page. */
fun micFacts(mic: MicInput): MicFacts = MicFacts(
    canKeepRecordings = mic.canRecord,
    keepsRecordings = mic.keepRecordings,
    sendsReviewClips = mic.canRecord && mic.reporter.enabled,
    testerDashboard = com.bigpapituna.sharpears.analytics.Tester.hasKey,
    testerDiary = com.bigpapituna.sharpears.analytics.Tester.diaryBuilt,
    aiBrief = com.bigpapituna.sharpears.data.BriefAi.hasKey,
    emailBrief = com.bigpapituna.sharpears.data.BriefEmail.hasKey,
)

/**
 * The card, over everything in its own window, in the system's light or dark. [asking]: before the permission prompt, with
 * Allow and Not now; otherwise ("How the microphone works" from Microphone setup or the privacy page) one Close.
 */
@Composable
fun MicCardDialog(copy: MicCardCopy, asking: Boolean, onAllow: () -> Unit, onNotNow: () -> Unit) {
    Dialog(onDismissRequest = onNotNow, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SharpEarsTheme(dark = isSystemInDarkTheme()) {
            DialogBarsFollowTheme()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxSize().padding(horizontal = 20.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(copy.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                            Text(copy.lead, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            copy.sections.forEach { s ->
                                Text(s.heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
                                Text(s.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (asking) {
                                ChunkyButton(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text(copy.allow) }
                                OutlinedButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(copy.notNow) }
                            } else ChunkyButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) { Text(copy.close) }
                        }
                    }
                }
            }
        }
    }
}

/** "How the microphone works": the card to read, nothing asked. */
@Composable
fun MicCardInfo(mic: MicInput, onClose: () -> Unit) {
    val copy = remember(mic) { MicTrust.card(micFacts(mic)) }
    MicCardDialog(copy, asking = false, onAllow = onClose, onNotNow = onClose)
}

/**
 * How a screen asks for the microphone (2.15). [ask] grants at once when allowed; otherwise the card comes first, the first
 * time, and again whenever a mic button is tapped after "Not now" (never for an automatic ask: "Not now" is not nagged).
 * For a kid the card is a grown-up's: the parent gate first, then the card ([child] names the kid; the picker making a kid
 * passes [gated] false, its grown-up is already there). Allow on the card leads to Android's prompt; Not now (or the gate
 * left) answers false and the screen stays on its touch keys.
 */
@Stable
class MicAsk internal constructor(private val askNow: (Boolean, String?) -> Unit) {
    /**
     * [auto]: the screen asks by itself on opening (no tap): after "Not now" it quietly answers false. [child]: the kid the
     * card is about, when the screen knows better than the profile open (the picker making one).
     */
    fun ask(auto: Boolean = false, child: String? = null) = askNow(auto, child)
}

@Composable
fun rememberMicAsk(mic: MicInput? = LocalMic.current, gated: Boolean? = null, onResult: (granted: Boolean) -> Unit): MicAsk {
    val gate = com.bigpapituna.sharpears.ui.family.LocalParentGate.current
    val profileNow = com.bigpapituna.sharpears.ui.family.LocalProfile.current
    val profile by rememberUpdatedState(profileNow)
    val result by rememberUpdatedState(onResult)
    val kid = profileNow.isKid
    val behindGate = gated ?: kid
    var showing by remember { mutableStateOf(false) }
    var forChild by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.d(TAG, "mic card: microphone ${if (granted) "allowed" else "not allowed"}")
        result(granted)
    }
    val ask = remember(mic, behindGate) {
        MicAsk { auto, child ->
            forChild = child ?: profile.name.takeIf { profile.isKid }
            when {
                mic == null -> launcher.launch(Manifest.permission.RECORD_AUDIO)     // no app around it (a preview): Android asks
                mic.hasPermission() -> result(true)
                auto && mic.micNotNow -> { Log.d(TAG, "mic card: not asked (not now)"); result(false) }
                // Answered before: a grown-up's screen asks Android again by itself; a kid's never puts Android's prompt in
                // front of the kid (a mic button tapped later still brings the gate and the card).
                auto && mic.micCardSeen -> if (behindGate) { Log.d(TAG, "mic card: not asked (a kid, answered before)"); result(false) }
                    else launcher.launch(Manifest.permission.RECORD_AUDIO)
                behindGate -> gate.guard({ showing = true; Log.d(TAG, "mic card: shown to a grown-up") }) {
                    Log.d(TAG, "mic card: gate left, touch keys"); mic.micNotNow = true; result(false)
                }
                else -> { showing = true; Log.d(TAG, "mic card: shown") }
            }
        }
    }
    if (showing && mic != null) {
        val copy = remember(forChild) { MicTrust.card(micFacts(mic), forChild) }
        MicCardDialog(copy, asking = true,
            onAllow = {
                showing = false; mic.micCardSeen = true; mic.micNotNow = false
                Log.d(TAG, "mic card: allow")
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onNotNow = {
                showing = false; mic.micCardSeen = true; mic.micNotNow = true
                Log.d(TAG, "mic card: not now, touch keys")
                result(false)
            })
    }
    return ask
}

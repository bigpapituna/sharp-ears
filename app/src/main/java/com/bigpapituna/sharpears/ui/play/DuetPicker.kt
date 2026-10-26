package com.bigpapituna.sharpears.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.play.Duet
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.family.LocalProfiles
import com.bigpapituna.sharpears.ui.family.PupAvatar
import com.bigpapituna.sharpears.ui.family.avatarLabel
import androidx.compose.foundation.Image

/**
 * One person to pick: their pup avatar and name, a tap toggles them. Used by the duet picker and by Pup says when
 * choosing who takes turns.
 */
@Composable
internal fun PersonChip(p: Profile, chosen: Boolean, enabled: Boolean = true, onTap: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(20.dp))
            .background(if (chosen) c.primaryContainer else c.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onTap)
            .semantics { selected = chosen; contentDescription = "${p.name}, ${avatarLabel(p.avatar)}" }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PupAvatar(p.avatar, size = 64.dp, selected = chosen)
        Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, textAlign = TextAlign.Center,
            color = if (enabled || chosen) c.onSurface else c.onSurfaceVariant)
    }
}

/** Everyone else on the phone, as chips; up to [max] can be chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeoplePicker(people: List<Profile>, chosen: List<String>, max: Int, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        people.forEach { p ->
            val on = p.id in chosen
            PersonChip(p, on, enabled = on || chosen.size < max) { onToggle(p.id) }
        }
    }
}

/**
 * Duet: pick who plays with you, then who takes which hand. The profile playing now is always one of the two.
 * [onStart] gets the pairing; the player then runs the song with both hands (Practice.BOTH) and the recap names both
 * (see ui/play/INTEGRATION.md). With nobody else on the phone, the pup says how to add someone.
 */
@Composable
fun DuetPicker(onStart: (Duet) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val me = LocalProfile.current
    val others = LocalProfiles.current?.all.orEmpty().filter { it.id != me.id }
    var partner by remember { mutableStateOf(others.singleOrNull()?.id) }
    var meLeft by remember { mutableStateOf(false) }
    DuetPickerContent(me, others, partner, meLeft, onPick = { partner = if (partner == it) null else it }, onSwap = { meLeft = !meLeft },
        onStart = { partner?.let { onStart(Duet.of(me.id, it, if (meLeft) com.bigpapituna.sharpears.engine.Hand.LEFT else com.bigpapituna.sharpears.engine.Hand.RIGHT)) } },
        onCancel = onCancel, modifier = modifier)
}

@Composable
internal fun DuetPickerContent(
    me: Profile, others: List<Profile>, partnerId: String?, meLeft: Boolean,
    onPick: (String) -> Unit, onSwap: () -> Unit, onStart: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier,
) {
    val partner = others.firstOrNull { it.id == partnerId }
    // The player is sideways on a phone, so the hands and "Play together" land below the fold: a pick brings them up.
    val startInView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val firstPick = remember { partnerId }
    androidx.compose.runtime.LaunchedEffect(partnerId) { if (partnerId != null && partnerId != firstPick) startInView.bringIntoView() }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExitPill(onCancel, label = "Back")
        Text("Play a duet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (others.isEmpty()) {
            PupSays(Pose.LEARNING, "A duet needs two players. Add someone in Settings, under Family, then come back.")
            return@Column
        }
        PupSays(Pose.WELCOME, when {
            partner == null -> if (me.kidSkin) "Who's playing with you?" else "Who are you playing with?"
            else -> "One of you plays the left hand, the other the right. You both get the stars."
        })
        PeoplePicker(others, listOfNotNull(partnerId), max = 1, onToggle = onPick)
        if (partner != null) {
            val left = if (meLeft) me else partner
            val right = if (meLeft) partner else me
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HandCard("Left hand", left, R.drawable.hand_l_rest, Modifier.weight(1f))
                HandCard("Right hand", right, R.drawable.hand_r_1, Modifier.weight(1f))
            }
            OutlinedButton(onClick = onSwap, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(painterResource(R.drawable.ic_repeat), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Swap hands")
            }
            ChunkyButton(onStart, Modifier.fillMaxWidth().bringIntoViewRequester(startInView)) { Text("Play together") }
        }
    }
}

/** Who plays one hand: the hand drawing, the hand's name and the player's avatar and name. */
@Composable
private fun HandCard(hand: String, p: Profile, handRes: Int, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) { Image(painterResource(handRes), contentDescription = null, modifier = Modifier.size(56.dp)) }
        Text(hand, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PupAvatar(p.avatar, size = 56.dp)
        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

// ---- previews ----

private val previewPeople = listOf(
    Profile.kid("Mia", 2, reader = false, id = "p1"), Profile.kid("Theo", 5, reader = true, id = "p2"), Profile.adult("Nadia", 4, id = "p3"),
)

@Preview(name = "Duet, picking hands", widthDp = 400, heightDp = 820)
@Composable
private fun DuetPickerPreview() = SharpEarsTheme(dark = false) {
    Surface { DuetPickerContent(Profile.owner(), previewPeople, "p1", meLeft = false, {}, {}, {}, {}) }
}

@Preview(name = "Duet, nobody else yet, dark", widthDp = 400, heightDp = 600)
@Composable
private fun DuetPickerAlonePreview() = SharpEarsTheme(dark = true) {
    Surface { DuetPickerContent(Profile.owner(), emptyList(), null, meLeft = false, {}, {}, {}, {}) }
}

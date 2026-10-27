package com.bigpapituna.sharpears.ui.world

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.progress.Progression
import com.bigpapituna.sharpears.engine.world.LevelSticker
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.engine.world.Reward
import com.bigpapituna.sharpears.engine.world.RewardItem
import com.bigpapituna.sharpears.engine.world.RewardQueue
import com.bigpapituna.sharpears.engine.world.Starter
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.profile.LocalProgress
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay

/*
 * The finish screens' rewards as a queue (2.14, engine/world/RewardQueue.kt). A grown-up sees one line and a small chip that
 * opens ONE sheet ([RewardsSheet]), a reward at a time; a kid keeps the reveals, one at a time, in the lesson's own screen
 * ([KidRewardStage]), with the big button always in view. Both draw each reward with [RewardItemView].
 */

/** The small chip on a grown-up's finish: "See rewards (3)". It only opens the sheet; nothing waits on it. */
@Composable
fun RewardsChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (com.bigpapituna.sharpears.ui.LocalPlain.current.on) return       // 2.15: no "See rewards" chip in the Plain look
    val c = MaterialTheme.colorScheme
    Box(modifier.heightIn(min = 40.dp).clip(CircleShape).background(c.surfaceContainerHigh)
        .clickable(role = Role.Button, onClickLabel = "Open the rewards", onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.primary)
    }
}

/**
 * The one rewards sheet of a grown-up's finish: [items] one at a time (the sticker, a rare one, something to wear, an egg, the
 * new level), "Next reward" until the last, then "Done". Closing it any way (a swipe, Back, Done) is the same: [onClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsSheet(items: List<RewardItem>, onClose: () -> Unit, say: ((String) -> Unit)? = null) {
    // 2.15: no reveals in the Plain look (stickers, wearables, eggs and sparkles still land, unseen).
    if (items.isEmpty() || com.bigpapituna.sharpears.ui.LocalPlain.current.on) { LaunchedEffect(Unit) { onClose() }; return }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var i by rememberSaveable { mutableIntStateOf(0) }
    val at = i.coerceIn(0, items.lastIndex)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(RewardQueue.heading(at, items.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() })
                // The reward fills the room above the button and scrolls if a big text size needs more: the button stays in reach.
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.runtime.key(at) { RewardItemView(items[at], reader = true, say = say) }
                }
                ChunkyButton(onClick = { if (at < items.lastIndex) i = at + 1 else onClose() }, modifier = Modifier.fillMaxWidth()) {
                    Text(RewardQueue.button(at, items.size))
                }
            }
        }
    }
}

/**
 * One reward drawn: a sticker card flipping in (the drop reveal), the stone's sticker turning into the sticker, something to
 * wear (the pup puts it on), an egg, the dog house, a record or a new level. [reader] false (a child who cannot read yet):
 * the pictures alone, no sentences, and what he wears he keeps without being asked. [say] is the pup's voice, for his lines.
 */
@Composable
fun RewardItemView(item: RewardItem, reader: Boolean, say: ((String) -> Unit)?, cardWidth: Dp = 150.dp, songTitle: (String) -> String? = { null }) {
    when (item) {
        is RewardItem.Sticker -> DropReveal(Reward(drops = listOf(item.drop)), cardWidth = cardWidth, captions = reader)
        is RewardItem.Trail -> TrailSticker(item.sticker, reader, placed = item.placedAt != null, say = say.takeIf { reader })
        is RewardItem.Wear -> WardrobeReveal(item.items, say = say.takeIf { reader }, captions = reader, autoKeep = !reader || item.starter)
        RewardItem.EggLaid -> Picture(reader, "A mystery egg appeared. It hatches when you finish your next lesson.") { EggView(EggLook.CLOSED, Modifier.size(84.dp, 108.dp)) }
        is RewardItem.House -> Picture(reader, if (item.stage >= 3) "The dog house got a chimney, a flag and a porch light." else "The dog house got a red roof and a flower box.") {
            DogHouse(item.stage, Modifier.size(120.dp))
        }
        is RewardItem.Record -> Picture(reader, "${item.up.after.label} record for ${songTitle(item.up.songId) ?: "this song"}.") { MedalDisc(item.up.after, Modifier.size(96.dp)) }
        is RewardItem.Level -> LevelCard(item)
    }
}

/** A picture, with its sentence under it for a reader. */
@Composable
private fun Picture(reader: Boolean, line: String, picture: @Composable () -> Unit) {
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = line }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        picture()
        if (reader) Text(line, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

/** A new level, for a grown-up's sheet: the pup, "Level 3: Note Spotter" and how far the next one is. */
@Composable
private fun LevelCard(item: RewardItem.Level) {
    val xp = LocalProgress.current?.xp
    val c = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Level up. Level ${item.up.to}, ${Progression.title(item.up.to)}" }) {
        Pup(Pose.LEVEL_UP, size = 120.dp, idle = false)
        Text("Level up", style = MaterialTheme.typography.labelLarge, color = c.primary)
        Text("Level ${item.up.to}: ${Progression.title(item.up.to)}", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (xp != null) Text(Progression.xpToNext(xp).let { if (it > 0) "$it XP to level ${item.up.to + 1}." else "You reached the top level." },
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

/**
 * The stone's own sticker, earned on its first finish: its shape turns into the sticker with a pop (LevelStickerEarned's
 * animation, big, without the row around it). A reader gets the words ("Cushion! A sticker for your trail."); a child who
 * cannot read yet gets the sticker, and on their first lesson the pup has already stuck it on the map ([placed]).
 */
@Composable
fun TrailSticker(sticker: LevelSticker, reader: Boolean, placed: Boolean, say: ((String) -> Unit)? = null) {
    val still = reducedMotion()
    val turn = remember(sticker.key) { Animatable(if (still) 1f else 0f) }
    val sounds = rememberWorldSounds()
    LaunchedEffect(sticker.key) {
        if (!still) { delay(450); turn.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        sounds.jingle(Rarity.COMMON)
        say?.invoke(sticker.line)
        android.util.Log.d(TAG, "world: stone sticker ${sticker.key} shown on the finish" + if (placed) ", stuck on the map for them" else "")
    }
    val c = MaterialTheme.colorScheme
    val line = "${sticker.name}! A sticker for your trail." + if (placed) " ${Starter.PLACED_LINE}" else " Stick it on the map."
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = line }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            val t = turn.value
            if (t < 1f) StickerSilhouette(sticker, c.onSurface.copy(alpha = 0.38f), Modifier.size(120.dp).graphicsLayer { alpha = 1f - t })
            if (t > 0f) StickerImage(sticker, Modifier.size(120.dp).graphicsLayer {
                val k = if (t < 0.7f) 0.6f + 0.6f * (t / 0.7f) else 1.2f - 0.2f * ((t - 0.7f) / 0.3f)
                scaleX = k; scaleY = k; alpha = t.coerceIn(0f, 1f)
            })
        }
        if (reader) Text(line, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}


/**
 * A kid's rewards, one at a time (2.14): [items] shown in turn in the same place, each for a while (the sticker flips in, the
 * trail sticker turns, he puts on what he won), a tap goes to the next at once, and the last one stays. Small dots tell how many
 * there are. Nothing here holds up the big button: the caller keeps it in view.
 */
@Composable
fun KidRewardStage(items: List<RewardItem>, reader: Boolean, say: ((String) -> Unit)?, modifier: Modifier = Modifier, songTitle: (String) -> String? = { null }) {
    // 2.15: a little kid's (the Plain look is never on there); guarded all the same: no reveals in the Plain look.
    if (items.isEmpty() || com.bigpapituna.sharpears.ui.LocalPlain.current.on) return
    var i by remember(items) { mutableIntStateOf(0) }
    val at = i.coerceIn(0, items.lastIndex)
    val still = reducedMotion()
    // Each reward gets its time (RewardQueue-sized by what it shows), then the next comes in its place.
    LaunchedEffect(items, at) {
        if (at >= items.lastIndex) return@LaunchedEffect
        delay(dwellMs(items[at], still))
        i = at + 1
    }
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Next") { if (at < items.lastIndex) i = at + 1 },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.runtime.key(at) { RewardItemView(items[at], reader, say, cardWidth = 132.dp, songTitle = songTitle) }
        if (items.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = "Reward ${at + 1} of ${items.size}" }) {
            items.indices.forEach { k ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (k == at) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

/** How long a reward stays before the next comes in (a tap goes sooner): long enough to see it land, longer for the ones that move most. */
internal fun dwellMs(item: RewardItem, still: Boolean): Long = (when (item) {
    is RewardItem.Sticker -> if (item.drop.rarity.special) 4200L else 3300L
    is RewardItem.Trail -> 3000L
    is RewardItem.Wear -> 4200L
    RewardItem.EggLaid -> 2600L
    is RewardItem.House -> 2600L
    is RewardItem.Record -> 2600L
    is RewardItem.Level -> 3000L
}).let { if (still) it * 3 / 4 else it }

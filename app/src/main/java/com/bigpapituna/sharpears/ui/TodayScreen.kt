package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.SongArt
import com.bigpapituna.sharpears.engine.nav.Names
import com.bigpapituna.sharpears.engine.nav.TodayCard
import com.bigpapituna.sharpears.engine.nav.TodayRow
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.play.Toy

/** Today's goal and streak for the card and the pup (ProgressStore): "3 of 10 min today", met or not, days in a row. */
data class GoalState(val line: String, val met: Boolean, val streak: Int, val atRisk: Boolean)

/** What Today's Continue tile shows (engine/nav ContinueWith, resolved by the caller). */
sealed interface ContinueTile {
    /** A library song; [fresh] is "Start here", an easy first song before anything was played. */
    data class Song(val entry: LibraryEntry, val fresh: Boolean) : ContinueTile
    data class PopSong(val song: com.bigpapituna.sharpears.engine.chords.PopSong) : ContinueTile
    /** A course lesson and the name of its unit. */
    data class Lesson(val lesson: CourseLesson, val unit: String?, val done: Boolean) : ContinueTile
}

class TodayActions(
    /** Start walks today's plan (Practice today), or runs it again once done. */
    val onStart: () -> Unit,
    val onContinue: () -> Unit,
    val onMic: () -> Unit, val onMicClose: () -> Unit,
    /** Explore: the Songs tab, the chords at the top of Learn, and a toy of the Play corner. */
    val onSongs: () -> Unit, val onChords: () -> Unit, val onToy: (Toy) -> Unit,
)

/** What the pup says when he is tapped on Today: one at random, never the same twice in a row. */
private val PUP_TAPS = listOf(
    "Woof! You've got this.", "Every minute at the keys counts.", "Slow and steady makes it stick.", "Mistakes mean you're learning.",
    "Ready when you are.", "Your fingers remember more than you think.", "One more song? I'll listen.", "Small steps, big music.",
)

/**
 * The Today tab (2.7), a grown-up's home, with one main action: the pup's greeting in a line, the microphone banner until the
 * mic is set up, the Today card (today's plan as a short list, its one Start, Pup's quest as a row of stars, the goal in
 * small print), then two quiet rows: Continue (the song or lesson played last) and Explore (Songs, Chords, Play corner).
 * The week's numbers and the family wall are on You.
 */
@Composable
fun TodayScreen(card: TodayCard, goal: GoalState?, cont: ContinueTile?, micBanner: Boolean, a: TodayActions,
                /** Pup's quest as stars (ui/play QuestStars), inside the card. */
                quest: (@Composable () -> Unit)? = null,
                /** Debug (--ez emptyToday true): the card with nothing due, for screenshots. */
                emptyPlan: Boolean = false) {
    val shown = if (emptyPlan && card.state == TodayCard.State.PLAN) card.copy(state = TodayCard.State.NOTHING_DUE, rows = emptyList()) else card
    var tapLine by remember { mutableStateOf<String?>(null) }
    var playCorner by remember { mutableStateOf(false) }
    val dressed = com.bigpapituna.sharpears.ui.world.LocalPupOutfit.current != com.bigpapituna.sharpears.engine.world.Outfit.NONE
    val pose = when {
        shown.state == TodayCard.State.DONE -> Pose.RESTING
        goal?.met == true -> Pose.GREAT_JOB
        (goal?.streak ?: 0) >= 3 -> Pose.STREAK
        dressed -> Pose.WEAR_BASE                    // in his outfit (2.1): the base pose is the one he wears it in
        else -> Pose.WELCOME
    }
    val fresh = (cont as? ContinueTile.Song)?.fresh == true || cont == null
    val arts = rememberSongArts(remember(cont) { listOfNotNull((cont as? ContinueTile.Song)?.entry?.meta) })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Header()
            // The pup in one line: what he says depends on the day so far; a tap gets a word of encouragement, and left alone
            // for half a minute he dozes off.
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pup(pose, size = 48.dp, onTap = { tapLine = (PUP_TAPS - tapLine).random() }, sleepyAfterMs = 30_000)
                Text(tapLine ?: remember(shown.state, goal) { greeting(shown.state, goal, fresh) }, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f))
            }
            if (micBanner) MicBanner(a.onMic, a.onMicClose)
            TodayCardView(shown, goal, a, quest)
            when (cont) {
                null -> {}
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QuietLabel(if (fresh) "Start here" else "Continue")
                    ContinueRow(cont, arts, a.onContinue)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietLabel("Explore")
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExploreTile(Names.SONGS, R.drawable.ic_library_music, Modifier.weight(1f), a.onSongs)
                    ExploreTile("Chords", R.drawable.ic_piano, Modifier.weight(1f), a.onChords)
                    ExploreTile(Names.PLAY_CORNER, R.drawable.ic_play_arrow, Modifier.weight(1f)) { playCorner = true }
                }
            }
        }
    }
    if (playCorner) PlayCornerSheet(onToy = { t -> playCorner = false; a.onToy(t) }, onDismiss = { playCorner = false })
}

/** The logo on its own dark tile (as on the launcher, so the white sharp shows in light mode too), and the tab's name. */
@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Stage), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Names.TODAY, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.getDefault())) },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The layout (2.8, engine/nav/Layout.kt), one tap from the home.
        LayoutPill()
    }
}

/** A quiet section label: the rows under it are secondary to the card. */
@Composable
private fun QuietLabel(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp).semantics { heading() })

/**
 * Until the microphone is set up: one line and the way to it, outlined (the card is the one raised surface). It goes for good
 * once the mic is allowed, or with its close; Settings > Microphone keeps the setup.
 */
@Composable
private fun MicBanner(onMic: () -> Unit, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp))
        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = c.primary, modifier = Modifier.size(22.dp))
        Text("Set up the microphone so the app hears your piano.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onMic) { Text("Set up mic") }
        IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Not now", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    }
}

/** The Today card, the screen's one raised panel: the plan and Start, the quest's stars, the goal line. */
@Composable
private fun TodayCardView(card: TodayCard, goal: GoalState?, a: TodayActions, quest: (@Composable () -> Unit)?) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.surfaceContainerHigh)
        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your plan", style = MaterialTheme.typography.labelLarge, color = c.primary, modifier = Modifier.weight(1f).semantics { heading() })
            if (card.state == TodayCard.State.PLAN) Text(card.about, style = Numeric.merge(MaterialTheme.typography.labelMedium), color = c.onSurfaceVariant)
        }
        when (card.state) {
            TodayCard.State.DONE -> {
                Text("Done for today", style = MaterialTheme.typography.headlineSmall)
                Text(card.summary.orEmpty().removePrefix("Done today: ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                TextButton(onClick = a.onStart, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Practice again") }
            }
            TodayCard.State.NOTHING_DUE -> {
                Text("Nothing is due today", style = MaterialTheme.typography.headlineSmall)
                Text("Pick any song you like, or a game in Learn.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                ChunkyButton(onClick = a.onSongs, modifier = Modifier.fillMaxWidth()) { Text("Open Songs") }
            }
            TodayCard.State.PLAN -> {
                card.rows.forEach { PlanRow(it) }
                ChunkyButton(onClick = a.onStart, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Start")
                }
            }
        }
        quest?.invoke()
        goal?.let { Text(it.line.replaceFirstChar { ch -> ch.uppercase() }, style = Numeric.merge(MaterialTheme.typography.bodySmall),
            color = if (it.met) c.primary else c.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp)) }
    }
}

/** One part of the plan: what kind it is as a small icon, its name, and a quiet line. */
@Composable
private fun PlanRow(r: TodayRow) {
    val c = MaterialTheme.colorScheme
    val icon = when (r.kind) { TodayRow.Kind.CHORDS -> R.drawable.ic_piano; TodayRow.Kind.LESSON -> R.drawable.ic_school; TodayRow.Kind.SONG -> R.drawable.ic_music_note }
    Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Continue as one plain tile: its picture, what it is and where it stopped, and a round play button. */
@Composable
private fun ContinueRow(t: ContinueTile, arts: Map<String, SongArt>, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val (title, line, resume) = when (t) {
        is ContinueTile.Song -> {
            val p = t.entry.progress
            val resume = !t.fresh && p.lastPositionMs > 0 && !p.completed
            Triple(t.entry.meta.title, when {
                t.fresh -> "An easy first song, melody and bass."
                resume -> "Paused at ${formatTime(p.lastPositionMs.toDouble())}"
                p.bestAccuracy >= 0 -> "Best run ${p.bestAccuracy}%"
                else -> t.entry.meta.composer.ifEmpty { "Imported" }    // an imported song has none: the line was blank
            }, resume)
        }
        is ContinueTile.PopSong -> Triple(t.song.title, "${Names.SONGS_BY_CHORDS}, ${t.song.artist}", false)
        is ContinueTile.Lesson -> Triple(t.lesson.title, listOfNotNull("Lesson", t.unit).joinToString(", ") + if (t.done) ". Played before." else "", false)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).pressScale().clip(RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClickLabel = if (resume) "Resume $title" else "Open $title", onClick = onOpen).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        when (t) {
            is ContinueTile.Song -> SongTile(arts[t.entry.meta.id], locked = !t.entry.available, size = 56.dp)
            is ContinueTile.PopSong -> com.bigpapituna.sharpears.ui.chords.KeyBadge(t.song.key,
                com.bigpapituna.sharpears.engine.chords.PopPlay.keyName(t.song.key, t.song.minor), Modifier.size(56.dp))
            is ContinueTile.Lesson -> Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_school), contentDescription = null, tint = c.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(26.dp))
        }
    }
}

/** A small Explore tile: an icon and a name, the whole tile a button. */
@Composable
private fun ExploreTile(title: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxHeight().heightIn(min = 76.dp).pressScale().clip(RoundedCornerShape(16.dp))
        .border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp)
        .semantics(mergeDescendants = true) { contentDescription = title },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Icon(painterResource(icon), contentDescription = null, tint = c.primary, modifier = Modifier.size(26.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Explore's Play corner: the Jam, Pup says and Clap along in a sheet (the same row as at the top of Songs). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayCornerSheet(onToy: (Toy) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Names.PLAY_CORNER, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text("  just for fun", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.bigpapituna.sharpears.ui.play.PlayRow(onToy, titled = false)
            }
        }
    }
}

/** What the pup says on Today: today done, a first visit, the goal or the streak, or the time of day. */
internal fun greeting(state: TodayCard.State, goal: GoalState?, fresh: Boolean, hour: Int = java.time.LocalTime.now().hour): String = when {
    state == TodayCard.State.DONE -> "That's today done. Rest those hands, see you tomorrow."
    fresh -> "Hi, I'm your coach. Let's start with an easy song."
    goal?.met == true -> "That's today's goal. Rest those hands."
    goal != null && goal.atRisk -> "Your ${goal.streak} day streak is waiting. ${goal.line.replaceFirstChar { it.uppercase() }}."
    goal != null && goal.streak >= 2 -> "${goal.streak} days in a row. Let's keep it going."
    hour < 12 -> "Good morning. A few minutes at the keys?"
    hour < 18 -> "Good afternoon. Ready when you are."
    else -> "Good evening. A short session before the day ends?"
}

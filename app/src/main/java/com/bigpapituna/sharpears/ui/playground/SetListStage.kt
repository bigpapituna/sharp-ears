package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.RecitalStore
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.Recital
import com.bigpapituna.sharpears.engine.playground.SHELF
import com.bigpapituna.sharpears.engine.playground.ShelfSong
import com.bigpapituna.sharpears.engine.playground.shelfSong
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Embedded
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.family.LocalProfile
import kotlinx.coroutines.delay

/**
 * 2.2, "Pick your three" (the concert hall's lesson 11): the shelf of every song the kid learned on the Playground's
 * pages, as cards with pictures; they tap three (a star marks each, the pup counts down what is left), then warm up on
 * each one's first phrase in the embedded player (wait mode, right hand, the band on the left, three tries per note
 * and it goes by). The three are saved for the recital ([RecitalStore]) as soon as they are picked. Reports the notes
 * played over the three warm ups, a Play step that always passes.
 */
@Composable
internal fun SetListStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profile = LocalProfile.current
    val store = remember(profile.id) { RecitalStore(context, profile.id) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(store.load().filter { shelfSong(it) != null }.distinct().take(Recital.SIZE)) } }
    var playing by remember { mutableIntStateOf(-1) }            // -1 picking, then the song warming up
    var between by remember { mutableStateOf(false) }
    var groups by remember { mutableIntStateOf(0) }
    val ended by rememberUpdatedState(onEnded)
    val voice = deps.voice

    if (playing < 0) {
        ShelfPicker(picked, modifier.fillMaxSize(),
            onToggle = { id ->
                if (id in picked) picked.remove(id) else if (picked.size < Recital.SIZE) picked += id
                voice.speak(PageLines.PICKS_LEFT.getOrElse(Recital.SIZE - picked.size) { PageLines.PICK_THREE }, interrupt = true)
            },
            onReady = {
                store.save(picked.toList())
                Log.d(TAG, "playground: set list ${picked.joinToString()}")
                playing = 0
            })
        return
    }
    val songs = picked.mapNotNull(::shelfSong)
    if (playing >= songs.size) return
    LaunchedEffect(between) {
        if (!between) return@LaunchedEffect
        delay(1_600)
        between = false
        playing++
        if (playing >= songs.size) ended(Report(emptyList(), 0, 0, null, groups = groups))
    }
    val song = songs[playing]
    Column(modifier.fillMaxSize()) {
        // The set list along the top: the song warming up big, the others small.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            songs.forEachIndexed { i, s ->
                val now = i == playing
                Canvas(Modifier.size(if (now) 44.dp else 30.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF241F1B))
                    .border(2.dp, if (now) PupGold else if (i < playing) Ink.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(10.dp)).padding(4.dp)) { songPicture(s.picture) }
            }
            Spacer(Modifier.width(4.dp))
            Text(song.title, color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(playing) {
                PlayerScreen(song.start.toSong("$lessonId-warmup-${song.id}", song.title), null, deps.sampler, deps.midi, deps.mic, voice, deps.prefs, deps.ready,
                    0.0, false,
                    PlayerStart(Mode.WAIT, Practice.RIGHT, mic = deps.prefs.micDefault && deps.mic.hasPermission(), inject = inject?.takeIf { playing == 0 }),
                    onBack = {},
                    onFinished = { r ->
                        groups += r.groups
                        Log.d(TAG, "playground: warm up ${song.title} done (${r.groups} notes)")
                        voice.speak(if (playing + 1 < songs.size) PageLines.NEXT_SONG else PageLines.WARMED_UP)
                        between = true
                    },
                    embedded = Embedded("Warm up"),
                    modifier = Modifier.fillMaxSize(),
                    letGo = true)
            }
            if (between) Box(Modifier.fillMaxSize().background(Color(0xCC110F0D)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PaintedPup(Painted.DANCING, size = 120.dp)
                    StageLine(if (playing + 1 < songs.size) PageLines.NEXT_SONG else PageLines.WARMED_UP)
                }
            }
        }
    }
}

/** The shelf: every song as a card; tap three. A star on each picked; the pup says how many are left; then "Warm up!". */
@Composable
private fun ShelfPicker(picked: List<String>, modifier: Modifier, onToggle: (String) -> Unit, onReady: () -> Unit) {
    BoxWithConstraints(modifier.padding(12.dp)) {
        val columns = if (maxWidth > 560.dp) 5 else 3
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(Painted.CROWN, size = 64.dp)
                Spacer(Modifier.width(8.dp))
                StageLine(if (picked.size < Recital.SIZE) PageLines.PICK_THREE else PageLines.PICKS_LEFT[0])
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SHELF, key = { it.id }) { s -> ShelfCard(s, s.id in picked) { onToggle(s.id) } }
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                ChunkyButton(onClick = onReady, enabled = picked.size == Recital.SIZE) { Text("Warm up!") }
            }
        }
    }
}

@Composable
private fun ShelfCard(song: ShelfSong, on: Boolean, onTap: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color(0xFF3A2E22) else Color(0xFF241F1B))
        .border(2.dp, if (on) PupGold else Color.Transparent, RoundedCornerShape(14.dp))
        .clickable(role = Role.Checkbox, onClick = onTap).semantics { contentDescription = song.title; selected = on }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f)) {
            Canvas(Modifier.fillMaxSize()) { songPicture(song.picture) }
            if (on) Canvas(Modifier.size(22.dp).align(Alignment.TopEnd).background(Color(0xFF241F1B), CircleShape)) {
                drawPath(star(Offset(size.width / 2, size.height / 2), size.width * 0.45f), PupGold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(song.title, color = if (on) Ink else InkDim, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
    }
}

package com.bigpapituna.sharpears.ui.playground

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Share
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.play.ConcertFiles
import com.bigpapituna.sharpears.engine.playground.FairLines
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.PHOTOS
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.SHELF
import com.bigpapituna.sharpears.engine.playground.ShelfSong
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.photoExercise
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup

/** The pup's pose in each photo of the strip. */
private val POSES = listOf(Pose.GREAT_JOB, Pose.DANCING, Pose.CROWN)

/**
 * 2.3, the fair's photo booth ([com.bigpapituna.sharpears.engine.playground.Game.PHOTO]): the kid picks a song from the
 * shelf (every song the Playground taught), then plays its start once for each of three photos ("Smile!", "Say cheese!",
 * "Big finish!"), heard note by note like the copy games. Each photo flashes and lands on the strip with the pup in a new
 * pose. Then the pup plays the kid's last take back at the kid's own pace, and the photo card is ready: the strip, the
 * kid's name, the song and the stars, to send through the share sheet (nothing leaves the phone unless a grown-up picks
 * an app). "Keep going" ends the step.
 */
@Composable
internal fun PhotoStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    var song by remember { mutableStateOf<ShelfSong?>(null) }
    var result by remember { mutableStateOf<Report?>(null) }
    var stars by remember { mutableIntStateOf(3) }
    val takes = remember { mutableStateListOf<Pair<Int, Long>>() }
    val s = song
    when {
        s == null -> SongShelf(modifier.fillMaxSize(), onPick = { song = it }, onShown = { deps.voice.speak(FairLines.PICK_SONG) })
        result == null -> PhotoTakes(s, lessonId, deps, inject, modifier, takes, onDone = { r, st -> stars = st; result = r })
        else -> PhotoCard(s, stars, takes.toList(), deps, modifier, onDone = { onEnded(result!!) })
    }
}

/** The shelf of songs as big cards with their pictures: tap one. */
@Composable
private fun SongShelf(modifier: Modifier, onPick: (ShelfSong) -> Unit, onShown: () -> Unit) {
    LaunchedEffect(Unit) { onShown() }
    BoxWithConstraints(modifier) {
        Canvas(Modifier.fillMaxSize()) { fairLights(0.3f) }
        val columns = if (maxWidth > 560.dp) 5 else 3
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pup(Pose.CROWN, size = 64.dp, idle = false)
                Spacer(Modifier.width(8.dp))
                StageLine(FairLines.PICK_SONG)
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SHELF, key = { it.id }) { song ->
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE6241F1B)).clickable(role = Role.Button) { onPick(song) }
                        .semantics { contentDescription = "Play ${song.title}" }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.fillMaxWidth(0.8f).aspectRatio(1f)) { songPicture(song.picture) }
                        Spacer(Modifier.height(4.dp))
                        Text(song.title, color = Ink, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center,
                            maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

/** The three takes: the song's start a round, heard note by note; each one finished flashes and fills a photo on the strip. */
@Composable
private fun PhotoTakes(song: ShelfSong, lessonId: String, deps: LessonDeps, inject: String?, modifier: Modifier, takes: MutableList<Pair<Int, Long>>,
                       onDone: (Report, Int) -> Unit) {
    val ex = remember(song) { photoExercise(song) }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val flash = remember { Animatable(0f) }
    val longest = remember(ex) { longestPhrase(ex) }
    val holder = remember { arrayOfNulls<StageRun>(1) }
    val done by rememberUpdatedState(onDone)
    val st = rememberStage(ex, "$lessonId-photo-${song.id}", lessonId, "photo", deps, inject,
        onEnded = { r -> val run = holder[0]; done(r, (PHOTOS - (run?.letGo?.size ?: 0)).coerceIn(1, 3)) },
        keys = { StageKeys.of(ex) },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()) },
        foundLine = { run, _ -> if (run.rounds.doneInRound == 0 || run.finished) FairLines.SNAPPED else null },
        missLine = { _, _ -> PageLines.NEXT_NOTE },
        prompt = { run -> FairLines.SNAP[run.done.coerceIn(0, FairLines.SNAP.lastIndex)] },
        onFound = { k ->
            holder[0]?.let { r ->
                // A new take starts a new list: the last take is the one played back.
                if (r.rounds.doneInRound == 0) takes.clear()
                takes += k to SystemClock.uptimeMillis()
                if (r.rounds.doneInRound == r.rounds.roundGroups.size - 1 && !still) scope.launch { flash.snapTo(1f); flash.animateTo(0f, tween(500)) }
            }
        },
        coach = { phraseCoach(longest, 420, call = true) },
        phrases = true, demoGapMs = 420,
    )
    holder[0] = st.run
    val run = st.run
    val v = run.version
    val shot = remember(v) { run.done }
    val demo = st.demoKeys
    val line = stageLine(st, when {
        demo.isNotEmpty() -> "Listen first!"
        run.phase == RoundCoach.Phase.LISTEN -> FairLines.SNAP[run.done.coerceIn(0, FairLines.SNAP.lastIndex)]
        else -> null
    })
    val songKeys = remember(ex) { StageKeys.of(ex) }
    val colours = com.bigpapituna.sharpears.ui.skin.colourNotesOn()
    val low = remember(ex) { com.bigpapituna.sharpears.engine.theory.whiteAtOrBelow(StageKeys.of(ex).min() - 2) }
    val high = remember(ex) { com.bigpapituna.sharpears.engine.theory.whiteAtOrAbove(StageKeys.of(ex).max() + 2) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Canvas(Modifier.fillMaxSize()) { fairLights(0.3f) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(song.title, color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.background(Color(0xE62A221C), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    StageLine(line)
                    Spacer(Modifier.height(6.dp))
                    StagePup(if (demo.isNotEmpty()) Pose.POINTING else Pose.GREAT_JOB, size = (roomH * 0.22f).coerceIn(64.dp, 120.dp), fx = st.fx, point = st.demoKey)
                }
                // The strip: a frame per photo, filled as each take is done.
                Column(Modifier.fillMaxHeight().width((roomH * 0.2f).coerceIn(70.dp, 120.dp)).background(Color(0xFFFBF5EE), RoundedCornerShape(10.dp)).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(PHOTOS) { i ->
                        Box(Modifier.weight(1f).fillMaxWidth().background(if (i < shot) Color(0xFF7DB8FF) else Color(0xFF3A322C), RoundedCornerShape(6.dp))
                            .border(if (i == shot && run.phase == RoundCoach.Phase.LISTEN) 3.dp else 0.dp, PupGold, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center) {
                            if (i < shot) Pup(POSES[i], size = androidx.compose.ui.unit.Dp.Unspecified, live = false, ground = false,
                                modifier = Modifier.fillMaxSize().padding(4.dp).semantics { contentDescription = "Photo ${i + 1}" })
                        }
                    }
                }
            }
            BigKeys(low, high, "Piano keys for ${song.title}", Modifier.fillMaxWidth().height((roomH * 0.26f).coerceIn(96.dp, 180.dp)).padding(top = 8.dp),
                fill = { m -> if (m == st.demoIndex?.let { demo.getOrNull(it) }) PupGold
                    else if (colours && m in songKeys) com.bigpapituna.sharpears.ui.skin.noteColour(m).copy(alpha = 0.45f) else null },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                    st.tap(m)
                })
        }
        if (flash.value > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
    }
}

/**
 * The end of the booth: the pup plays the kid's last take back (each gap kept between a third and a second), the photo
 * card develops, "Send to someone" packs it as a PNG for the share sheet, and "Keep going" goes on with the lesson.
 */
@Composable
private fun PhotoCard(song: ShelfSong, stars: Int, take: List<Pair<Int, Long>>, deps: LessonDeps, modifier: Modifier, onDone: () -> Unit) {
    val context = LocalContext.current
    val name = LocalProfile.current.name
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val develop = remember { Animatable(0f) }
    // A kid hands the phone to a grown-up to send it: the share sheet reaches every app and contact (parent gate).
    val gate = com.bigpapituna.sharpears.ui.family.LocalParentGate.current
    LaunchedEffect(Unit) {
        image = withContext(Dispatchers.Default) { drawPhotoCard(context, name, song.title, stars, PHOTO_W / 2, PHOTO_H / 2) }
        deps.voice.speak(FairLines.PLAYBACK, interrupt = true)
        delay(1_600)
        // The kid's own take, at their own pace.
        val gaps = take.zipWithNext { a, b -> (b.second - a.second).coerceIn(330L, 1_000L) } + 600L
        Log.d(TAG, "playground: photo booth plays back ${take.size} notes")
        for ((i, n) in take.withIndex()) {
            if (deps.prefs.sound) deps.sampler.noteOn(n.first, 84)
            delay(gaps[i] * 9 / 10); deps.sampler.noteOff(n.first); delay(gaps[i] / 10)
        }
        develop.animateTo(1f, tween(900))
        deps.voice.speak(FairLines.CARD)
    }
    Column(modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StageLine(if (develop.value < 1f) FairLines.PLAYBACK else FairLines.CARD)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            image?.let {
                Image(it.asImageBitmap(), contentDescription = "Photo card: $name plays ${song.title}", modifier = Modifier.fillMaxHeight()
                    .aspectRatio(PHOTO_W / PHOTO_H.toFloat()).clip(RoundedCornerShape(12.dp)).graphicsLayer { alpha = 0.25f + 0.75f * develop.value })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = !busy && develop.value >= 1f, onClick = { gate.guardForKids {
                busy = true
                scope.launch {
                    val file = runCatching { withContext(Dispatchers.IO) { packPhotoCard(context, name, song.title, stars) } }
                    busy = false
                    file.onSuccess { Share.files(context, listOf(it), ConcertFiles.title(name, song.title)) }.onFailure { Log.d(TAG, "photo card: share failed: $it") }
                }
            } }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Send to someone", color = Ink)
            }
            ChunkyButton(onClick = onDone) { Text("Keep going") }
        }
    }
}

/** The photo card's size: portrait, like a strip from a booth. */
const val PHOTO_W = 1080
const val PHOTO_H = 1440

/**
 * The photo card (Android Canvas, so it can go out as a PNG): warm paper, a photo strip of three frames with the pup in
 * three poses on bright backdrops, "Photo booth" over it, and under it the kid's [name], the [songTitle] and the [stars].
 */
fun drawPhotoCard(context: Context, name: String, songTitle: String, stars: Int, width: Int = PHOTO_W, height: Int = PHOTO_H): Bitmap {
    val w = width.toFloat(); val h = height.toFloat()
    val bmp = androidx.core.graphics.createBitmap(width, height)
    val c = AndroidCanvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    c.drawColor(0xFFFBF5EE.toInt())
    val nunito = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull()
    // Bulbs along the top, like the booth's sign.
    for (k in 0 until 9) { p.color = listOf(0xFFE0605A, 0xFFF2B84B, 0xFF7DB8FF)[k % 3].toInt(); c.drawCircle(w * (0.1f + k * 0.1f), h * 0.035f, w * 0.014f, p) }
    p.typeface = nunito; p.fontVariationSettings = "'wght' 800"; p.textAlign = Paint.Align.CENTER
    p.color = 0xFFE38E5A.toInt(); p.textSize = h * 0.045f
    c.drawText("Photo booth", w / 2, h * 0.095f, p)
    // The strip: three frames.
    val stripL = w * 0.2f; val stripR = w * 0.8f
    p.color = 0xFF2B2420.toInt(); c.drawRoundRect(RectF(stripL, h * 0.12f, stripR, h * 0.78f), w * 0.02f, w * 0.02f, p)
    val backs = listOf(0xFF7DB8FF, 0xFFF6A654, 0xFF7CC173)
    for (i in 0 until 3) {
        val top = h * (0.135f + i * 0.215f); val box = RectF(stripL + w * 0.03f, top, stripR - w * 0.03f, top + h * 0.2f)
        p.color = backs[i].toInt(); c.drawRoundRect(box, w * 0.012f, w * 0.012f, p)
        BitmapFactory.decodeResource(context.resources, POSES[i].res)?.let { img ->
            val s = minOf(box.width() / img.width, box.height() / img.height) * 0.92f
            val iw = img.width * s; val ih = img.height * s
            c.drawBitmap(img, null, RectF(box.centerX() - iw / 2, box.bottom - ih, box.centerX() + iw / 2, box.bottom), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            img.recycle()
        }
    }
    // The words: who played what, the stars, the app.
    p.color = 0xFF2B2420.toInt(); p.textSize = h * 0.05f
    val who = name.trim().ifEmpty { "A piano player" }
    while (p.measureText(who) > w * 0.9f && p.textSize > h * 0.025f) p.textSize *= 0.94f
    c.drawText(who, w / 2, h * 0.845f, p)
    p.fontVariationSettings = "'wght' 600"; p.color = 0xFF8A7D72.toInt(); p.textSize = h * 0.032f
    while (p.measureText(songTitle) > w * 0.9f && p.textSize > h * 0.018f) p.textSize *= 0.94f
    c.drawText(songTitle, w / 2, h * 0.89f, p)
    for (i in 0 until 3) {
        val x = w / 2 + (i - 1) * h * 0.06f; val y = h * 0.94f
        val path = android.graphics.Path()
        for (k in 0 until 10) {
            val a = Math.PI * k / 5 - Math.PI / 2; val r = if (k % 2 == 0) h * 0.024f else h * 0.011f
            val px = x + (r * Math.cos(a)).toFloat(); val py = y + (r * Math.sin(a)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.color = if (i < stars.coerceIn(1, 3)) 0xFFF2B84B.toInt() else 0xFFE6DACC.toInt(); c.drawPath(path, p)
    }
    p.textSize = h * 0.022f; p.color = 0xFF8A7D72.toInt()
    c.drawText("Sharp Ears", w / 2, h * 0.985f, p)
    return bmp
}

/** Writes the photo card into a fresh cache/share as "<Name> plays <song>.png" (call off the main thread). */
fun packPhotoCard(context: Context, name: String, songTitle: String, stars: Int): File {
    val dir = Share.fresh(context)
    val bmp = drawPhotoCard(context, name, songTitle, stars)
    val file = File(dir, ConcertFiles.stem(ConcertFiles.title(name, songTitle)) + ".png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    Log.d(TAG, "photo card: packed ${file.name} (${file.length() / 1024} KB)")
    return file
}

package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.SongArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Library tile art for every song, read once per process: each file is parsed off the main thread, in list order,
 * and its tile appears as soon as it is ready. Scrolling never waits for it.
 */
private val songArts = mutableStateMapOf<String, SongArt>()

/** The art (opening notes and length) of [metas] that can be read, filling in as files are parsed. */
@Composable
fun rememberSongArts(metas: List<SongMeta>): Map<String, SongArt> {
    val context = LocalContext.current
    LaunchedEffect(metas.map { it.id }) {
        val repo = withContext(Dispatchers.IO) { SongRepository(context.applicationContext) }
        val t0 = System.currentTimeMillis()
        var n = 0
        for (m in metas) {
            if (m.id in songArts) continue
            val art = withContext(Dispatchers.Default) {
                if (!repo.available(m)) null else runCatching { SongArt.of(repo.load(m)) }.getOrNull()
            } ?: continue
            songArts[m.id] = art
            n++
        }
        if (n > 0) Log.d(TAG, "library art: $n songs read in ${System.currentTimeMillis() - t0} ms")
    }
    return songArts
}

/**
 * A song's tile: its opening as falling notes in the hand colours over a little keyboard, on the stage's dark
 * ground (in both themes, like the player). Locked songs show a lock. The notes fade in once the file is read.
 */
@Composable
fun SongTile(art: SongArt?, locked: Boolean, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val c = MaterialTheme.colorScheme
    if (locked) {
        Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_profile_lock), contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(size * 0.42f))
        }
        return
    }
    val still = reducedMotion()
    val shown by animateFloatAsState(if (art != null) 1f else 0f, if (still) tween(0) else tween(420), label = "art")
    Canvas(modifier.size(size).clip(RoundedCornerShape(12.dp)).clearAndSetSemantics { }) {
        val h = this.size.height
        val wd = this.size.width
        drawRect(Brush.verticalGradient(listOf(StageRaised, Stage)))
        val keysTop = h * 0.80f
        val lane = keysTop - h * 0.08f
        // A soft glow where the notes land.
        val glowTop = keysTop - lane * 0.35f
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, RightHandColor.copy(alpha = 0.18f)), startY = glowTop, endY = keysTop),
            topLeft = Offset(0f, glowTop), size = Size(wd, keysTop - glowTop))
        art?.notes?.forEach { n ->
            val bottom = keysTop - n.y * lane
            val top = (bottom - n.h * lane).coerceAtLeast(0f)
            val x = n.x * wd
            val w = (n.w * wd - 1.5f).coerceAtLeast(2f)
            val tall = (bottom - top - 1f).coerceAtLeast(2f)
            drawRoundRect(if (n.right) RightHandColor else LeftHandColor, Offset(x, top), Size(w, tall),
                CornerRadius(minOf(w, tall) * 0.35f), alpha = shown)
        }
        // The keyboard: seven white keys and five black ones.
        val kw = wd / 7f
        for (i in 0 until 7) drawRect(Ink.copy(alpha = 0.88f), Offset(i * kw + 0.75f, keysTop + 1f), Size(kw - 1.5f, h - keysTop))
        for (i in listOf(0, 1, 3, 4, 5)) drawRect(Stage, Offset((i + 1) * kw - kw * 0.28f, keysTop + 1f), Size(kw * 0.56f, (h - keysTop) * 0.55f))
    }
}

/** Difficulty as [filled] of three small stars in the accent. */
@Composable
fun DifficultyStars(filled: Int, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    if (filled <= 0) return
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(size * 3 + 4.dp, size).semantics { contentDescription = "Difficulty $filled of 3" }) {
        val r = this.size.height / 2f
        val star = Path().apply {
            for (i in 0 until 10) {
                val k = if (i % 2 == 0) 1f else 0.45f
                val a = (-PI / 2 + i * PI / 5).toFloat()
                val px = cos(a) * k * r
                val py = sin(a) * k * r + r * 0.08f
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        for (i in 0 until 3) translate(r + i * (this.size.height + 2.dp.toPx()), r) { drawPath(star, if (i < filled) on else off) }
    }
}

/**
 * Where a song stands, at the end of its row: the best Perform score in a ring filled to it, or a tick once
 * played through. Nothing for untouched songs.
 */
@Composable
fun BestBadge(best: Int?, played: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    when {
        best != null -> Box(modifier.size(44.dp).semantics(mergeDescendants = true) { contentDescription = "Best run $best percent" },
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp)) {
                val w = 3.5.dp.toPx()
                val s = Size(this.size.width - w, this.size.height - w)
                drawArc(c.surfaceContainerHighest, 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
                drawArc(c.primary, -90f, 360f * best.coerceIn(0, 100) / 100f, false, Offset(w / 2, w / 2), s, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text("$best", style = Numeric.merge(MaterialTheme.typography.labelMedium).copy(fontSize = 13.sp), color = c.onSurface, textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { })
        }
        played -> Box(modifier.size(30.dp).background(c.primary, CircleShape).semantics { contentDescription = "Played through" }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Difficulty stars and the length on one line under a song's title. */
@Composable
fun SongFacts(difficulty: String, durationMs: Long?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DifficultyStars(SongArt.stars(difficulty))
        durationMs?.let {
            val len = SongArt.length(it)
            Text(len, style = Numeric.merge(MaterialTheme.typography.labelMedium), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = "Length $len" })
        }
    }
}

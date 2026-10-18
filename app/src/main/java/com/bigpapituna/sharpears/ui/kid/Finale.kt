package com.bigpapituna.sharpears.ui.kid

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.kid.Finale
import com.bigpapituna.sharpears.engine.kid.KidLines
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.engine.world.AlbumPage
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.AlbumPageView
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The pup's bow: proud in his crown, a little dance, then holding up the page's sticker, round and round. */
private val BOW = listOf(Pose.CROWN, Pose.DANCING, Pose.STICKER)

/**
 * The finale (2.2): a page of the Playground is done (its last lesson finished, the first time). The whole screen goes
 * to night with fireworks; the pup bows (crown, dance, sticker); "<Name> finished the garden!" is said with the kid's
 * name (the on-phone voice, [say]) and shown big; the page's album page shows complete or with its gaps; the
 * certificate (ui/kid/Certificate.kt) can go to someone; and the pup says where the path goes on ([after]: the next
 * page, or the big kids' road). "Keep going" ([onDone]) goes back to the map, which rolls on to the next stone.
 * Always dark, like the stage. With animations off the fireworks hold still and everything is in place at once.
 */
@Composable
fun FinaleScreen(page: PlaygroundPage, name: String, album: AlbumPage, stars: Int, after: Finale.After, say: (String) -> Unit, onDone: () -> Unit,
                 sound: Boolean = true) = SharpEarsTheme(dark = true) {
    BackHandler(onBack = onDone)
    val still = reducedMotion()
    val headline = remember(name, page) { KidLines.finished(name, page) }
    val onward = remember(after) { KidLines.onward(after) }
    val rise = remember { Animatable(if (still) 1f else 0f) }
    var pose by remember { mutableStateOf(BOW.first()) }
    val sayNow by rememberUpdatedState(say)
    LaunchedEffect(Unit) {
        Log.d(TAG, "finale: ${page.id} for $name, $stars stars, album ${album.collected}/${album.size}, then $after")
        if (sound) WorldSounds.jingle(Rarity.SHINY)
        sayNow(headline)
        if (!still) rise.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        // The name line is made on the phone, the next one is recorded: a breath between them so they never overlap.
        delay(if (still) 2600 else 1700)
        sayNow(onward)
    }
    LaunchedEffect(Unit) { var i = 0; while (true) { delay(1900); i++; pose = BOW[i % BOW.size] } }
    // Night: light status bar icons while the finale shows, the phone's own after.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        var ctx = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val bars = (ctx as? android.app.Activity)?.window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val before = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose { if (bars != null && before != null) bars.isAppearanceLightStatusBars = before }
    }
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF141A3A), Color(0xFF2B2150), Color(0xFF3B2340))))) {
        Fireworks(Modifier.fillMaxSize(), still)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > maxHeight * 0.95f && maxWidth >= 640.dp
            val pupSize: Dp = if (wide) minOf(maxHeight * 0.3f, 220.dp) else minOf(maxWidth * 0.5f, 200.dp)
            val head: @Composable ColumnScope.() -> Unit = {
                Box(Modifier.size(pupSize), contentAlignment = Alignment.BottomCenter) {
                    Pup(pose, size = pupSize, idle = false, modifier = Modifier.graphicsLayer {
                        val k = 0.6f + 0.4f * rise.value; scaleX = k; scaleY = k; alpha = rise.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    })
                }
                Text(headline, style = MaterialTheme.typography.displaySmall, color = PupGold, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }.graphicsLayer { alpha = rise.value })
                Text(onward, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = rise.value })
            }
            val prize: @Composable ColumnScope.() -> Unit = {
                AlbumPageView(album, Modifier.widthIn(max = 560.dp))
                CertificateCard(name, page, stars, Modifier.widthIn(max = 560.dp))
            }
            val go: @Composable () -> Unit = {
                ChunkyButton(onClick = onDone, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) { Text("Keep going") }
            }
            if (wide) Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.42f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    head(); go()
                }
                Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()).graphicsLayer { alpha = rise.value; translationY = (1 - rise.value) * 80f },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), content = prize)
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                head()
                Column(Modifier.graphicsLayer { alpha = rise.value; translationY = (1 - rise.value) * 80f }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = prize)
                go()
            }
        }
    }
    }
}

/** The fireworks' colours: the colour notes' seven, the pup's gold and white. */
private val SPARKS: List<Color> = NoteColours.WHITE.map { Color(it.argb) } + PupGold + Color.White

/** One firework: when it goes up (ms), where it bursts (shares of the screen) and its colour. */
private class Shell(val at: Long, val x: Float, val y: Float, val colour: Color, val accent: Color, val seed: Int)

/**
 * Fireworks over the whole screen: a rocket climbs, bursts into a ring of sparks that fall and fade, and another goes up
 * every half second (a little slower after the first few seconds). Everything is a function of the time since the
 * screen opened, so nothing is kept per spark. Still with animations off: a few bursts held open.
 */
@Composable
private fun Fireworks(modifier: Modifier, still: Boolean) {
    val shells = remember {
        val r = java.util.Random(7)
        var t = 0L
        List(60) { i ->
            val c = SPARKS[r.nextInt(SPARKS.size)]
            Shell(t, 0.12f + r.nextFloat() * 0.76f, 0.10f + r.nextFloat() * 0.32f, c, SPARKS[r.nextInt(SPARKS.size)], i).also {
                t += if (i < 10) 420L + r.nextInt(200) else 900L + r.nextInt(500)
            }
        }
    }
    var now by remember { mutableFloatStateOf(if (still) 1500f else 0f) }
    if (!still) LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (isActive) withFrameMillis { now = (it - start).toFloat() }
    }
    Canvas(modifier) {
        val t = now
        if (still) listOf(0, 3, 5).forEach { drawShell(shells[it], 950f) }
        else {
            // Round and round for as long as the finale stays open.
            val loop = shells.last().at + RISE + LIFE
            val at = t % loop
            shells.forEach { s -> val age = at - s.at; if (age in 0f..RISE + LIFE) drawShell(s, age) }
        }
    }
}

private const val RISE = 450f
private const val LIFE = 1700f

private fun DrawScope.drawShell(s: Shell, age: Float) {
    val w = size.width; val h = size.height
    val burst = Offset(w * s.x, h * s.y)
    if (age < RISE) {
        // The rocket climbing from the bottom, a short bright streak.
        val k = age / RISE
        val y = h + (burst.y - h) * (1 - (1 - k) * (1 - k))
        drawLine(s.colour.copy(alpha = 0.9f), Offset(burst.x, y), Offset(burst.x, y + h * 0.04f), w * 0.004f)
        drawCircle(Color.White, w * 0.004f, Offset(burst.x, y))
        return
    }
    val t = (age - RISE) / 1000f                     // seconds since the burst
    val fade = (1f - (age - RISE) / LIFE).coerceIn(0f, 1f)
    val reach = minOf(w, h) * 0.26f
    val n = 40
    for (i in 0 until n) {
        val a = 2 * PI * i / n + s.seed
        val speed = reach * (0.75f + 0.25f * ((i * 7 + s.seed) % 5) / 4f)
        val drift = 1 - kotlin.math.exp(-3.2f * t)
        val p = Offset(burst.x + (cos(a) * speed * drift).toFloat(), burst.y + (sin(a) * speed * drift).toFloat() + h * 0.05f * t * t)
        val c = if (i % 3 == 0) s.accent else s.colour
        drawCircle(c.copy(alpha = fade), minOf(w, h) * (0.006f + 0.003f * fade), p)
        drawCircle(c.copy(alpha = fade * 0.25f), minOf(w, h) * 0.016f * fade, p)
    }
    if (t < 0.18f) drawCircle(Color.White.copy(alpha = 1f - t / 0.18f), reach * 0.18f, burst)
}

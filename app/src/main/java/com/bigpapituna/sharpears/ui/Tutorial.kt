package com.bigpapituna.sharpears.ui

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.bigpapituna.sharpears.R
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.Video
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * A tutorial video that follows the song (DESIGN 3.1, Type A): the engine is the clock. Once aligned (the
 * video second where the song's first note sounds), the video plays while the song moves and pauses while
 * wait mode holds; a drift over 0.4 s seeks it. Unaligned, it is an ordinary embedded player.
 * Official IFrame embed only: no download, branding and ads untouched (PLAN 6.3).
 */
class TutorialState(val songId: String, val videos: List<Video>, private val prefs: Prefs, private val firstNoteMs: Long) {
    var video by mutableStateOf(videos.first())
    var player by mutableStateOf<YouTubePlayer?>(null)
    val tracker = YouTubePlayerTracker()
    var anchor by mutableStateOf(prefs.anchor(songId, videos.first().id))

    fun pick(v: Video) { video = v; player = null; anchor = prefs.anchor(songId, v.id) }

    /** "First note" tap: the video is at the song's first note right now. */
    fun align() {
        val sec = tracker.currentSecond
        anchor = sec
        prefs.saveAnchor(songId, video.id, sec)
        Log.d(TAG, "video ${video.id} aligned: first note at %.2f s".format(sec))
    }

    fun videoSecFor(songMs: Double): Float? = anchor?.let { (it + (songMs - firstNoteMs) / 1000.0).toFloat().coerceAtLeast(0f) }
}

// The nearest YouTube rate at or below the song's speed (ramp speeds like 0.6 fall between); drift seeks cover the rest.
private fun rateFor(speed: Double) = when {
    speed < 0.5 -> PlayerConstants.PlaybackRate.RATE_0_25
    speed < 0.75 -> PlayerConstants.PlaybackRate.RATE_0_5
    speed < 1.0 -> PlayerConstants.PlaybackRate.RATE_0_75
    else -> PlayerConstants.PlaybackRate.RATE_1
}

/** The embedded player. [moving] is true while the song plays and is not held by wait mode. */
@Composable
fun TutorialVideo(t: TutorialState, moving: Boolean, speed: Double, muted: Boolean, songMs: () -> Double, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) { key(t.video.id) {
        AndroidView(
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    lifecycle.addObserver(this)
                    addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            youTubePlayer.addListener(t.tracker)
                            youTubePlayer.cueVideo(t.video.id, t.videoSecFor(songMs()) ?: 0f)
                            t.player = youTubePlayer
                            Log.d(TAG, "video ready ${t.video.id}")
                        }
                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            Log.d(TAG, "video error ${t.video.id}: $error")
                        }
                    })
                }
            },
            onRelease = { v -> lifecycle.removeObserver(v); v.release(); t.player = null },
            // The view keeps 16:9 itself and would spill over its neighbours: size it from the height and clip.
            modifier = Modifier.aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
        )
    } }
    // While the mic listens through the phone speaker's room, the video's piano would sound like the player's.
    LaunchedEffect(t.player, muted) { t.player?.let { if (muted) it.mute() else it.unMute() } }
    LaunchedEffect(t.player, speed) { t.player?.setPlaybackRate(rateFor(speed)) }
    LaunchedEffect(t.player, t.anchor, moving) {
        val p = t.player ?: return@LaunchedEffect
        if (t.anchor == null) return@LaunchedEffect
        if (!moving) { p.pause(); return@LaunchedEffect }
        p.play()
        while (true) {
            val want = t.videoSecFor(songMs()) ?: return@LaunchedEffect
            if (abs(t.tracker.currentSecond - want) > 0.4f) {
                Log.d(TAG, "video seek %.2f -> %.2f s".format(t.tracker.currentSecond, want))
                p.seekTo(want)
                delay(1500)          // let it buffer before judging drift again
            } else delay(400)
        }
    }
}

/**
 * The tutorial as a small floating window over the falling notes: drag it by its title bar, close it with
 * the X. A one-tap "Sync" (the First note alignment) and "Open in YouTube" live in the title bar.
 */
@Composable
fun TutorialPip(t: TutorialState, moving: Boolean, speed: Double, muted: Boolean, songMs: () -> Double,
                onClose: () -> Unit, maxHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    // Fit the window into the space it floats over: title bar plus a 16:9 video no taller than what is left.
    val bar = if (maxHeight < 260.dp) 36.dp else 44.dp
    val videoH = (maxHeight - bar).coerceIn(60.dp, 200.dp)
    val width = videoH * 16f / 9f
    val context = LocalContext.current
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Column(
        modifier
            .offset { androidx.compose.ui.unit.IntOffset(offset.x.toInt(), offset.y.toInt()) }
            .shadow(12.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(StageRaised),
    ) {
        Row(
            Modifier.width(width).height(bar)
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); offset += drag } }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (t.videos.size > 1) "Video ${t.videos.indexOf(t.video) + 1}/${t.videos.size}" else "Tutorial",
                style = MaterialTheme.typography.labelLarge, color = InkDim, maxLines = 1,
                modifier = Modifier.weight(1f).then(if (t.videos.size > 1) Modifier.clickable { t.pick(t.videos[(t.videos.indexOf(t.video) + 1) % t.videos.size]) } else Modifier))
            TextButton(onClick = t::align, enabled = t.player != null, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (t.anchor == null) "Sync" else "Re-sync", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${t.video.id}".toUri()))
                Log.d(TAG, "open in YouTube ${t.video.id}")
            }, modifier = Modifier.size(40.dp)) { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "Open in YouTube", tint = InkDim, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Close video", tint = Ink, modifier = Modifier.size(20.dp))
            }
        }
        TutorialVideo(t, moving, speed, muted, songMs, Modifier.width(width).height(videoH))
        if (t.anchor == null && maxHeight >= 300.dp) Text("Play the video and tap Sync when its first note sounds.",
            style = MaterialTheme.typography.bodySmall, color = InkDim, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}
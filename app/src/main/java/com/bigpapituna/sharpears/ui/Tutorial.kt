package com.bigpapituna.sharpears.ui

import android.content.Intent
import android.util.Log
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

private fun rateFor(speed: Double) = when (speed) {
    0.25 -> PlayerConstants.PlaybackRate.RATE_0_25
    0.5 -> PlayerConstants.PlaybackRate.RATE_0_5
    0.75 -> PlayerConstants.PlaybackRate.RATE_0_75
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
 * Video choice, alignment, and the always-available "Open in YouTube". [compact] (the cover screen) keeps it to
 * one short row so the video keeps most of the height.
 */
@Composable
fun TutorialControls(t: TutorialState, modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    fun open() {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${t.video.id}".toUri()))
        Log.d(TAG, "open in YouTube ${t.video.id}")
    }
    if (compact) {
        FlowRow(modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (t.videos.size > 1) TextButton(onClick = { t.pick(t.videos[(t.videos.indexOf(t.video) + 1) % t.videos.size]) }) {
                Text("Video ${t.videos.indexOf(t.video) + 1}/${t.videos.size}")
            }
            TextButton(onClick = t::align, enabled = t.player != null) { Text(if (t.anchor == null) "First note" else "Re-sync") }
            TextButton(onClick = ::open) { Text("YouTube") }
        }
        return
    }
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${t.video.channel}: ${t.video.title}", style = MaterialTheme.typography.labelMedium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (t.videos.size > 1) t.videos.forEachIndexed { i, v ->
                FilterChip(v == t.video, { t.pick(v) }, label = { Text("Video ${i + 1}") })
            }
            FilledTonalButton(onClick = t::align, enabled = t.player != null) { Text("First note") }
            OutlinedButton(onClick = ::open) { Text("Open in YouTube") }
        }
        Text(
            t.anchor?.let { "Synced: the video follows the song (first note at %.1f s). Tap First note again to re-align.".format(it) }
                ?: "To sync: play the video and tap First note when its first note sounds.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Video above its controls, for layouts that keep them together. */
@Composable
fun TutorialPanel(t: TutorialState, moving: Boolean, speed: Double, muted: Boolean, songMs: () -> Double,
                  modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier) {
        TutorialVideo(t, moving, speed, muted, songMs, Modifier.weight(1f).fillMaxWidth())
        TutorialControls(t, compact = compact)
    }
}
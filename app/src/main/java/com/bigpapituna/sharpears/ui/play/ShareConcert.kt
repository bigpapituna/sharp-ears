package com.bigpapituna.sharpears.ui.play

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Share
import com.bigpapituna.sharpears.engine.RunRecording
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.play.ConcertCardLayout
import com.bigpapituna.sharpears.engine.play.ConcertFiles
import com.bigpapituna.sharpears.engine.play.ConcertMix
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.family.LocalProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import java.time.LocalDate
import com.bigpapituna.sharpears.ui.pup.Pose

/*
 * Share my concert: after a performance, "Send to someone" packs the run as a WAV (the notes as played, replayed with
 * the app's piano samples) and a card (the pup, the song, the stars, the sticker) into cache/share and opens the share
 * sheet through the FileProvider (data/StuckShare.kt's Share). Nothing leaves the phone unless the player picks an app.
 */

/** The card's colours: the app's warm light palette, so the card looks the same whatever the phone's theme. */
private object CardInk {
    const val PAPER = 0xFFFBF5EE.toInt()
    const val EDGE = 0xFFE38E5A.toInt()                       // the accent
    const val GLOW = 0x55F2B84B                               // the pup's gold, soft
    const val INK = 0xFF2B2420.toInt()
    const val INK_DIM = 0xFF8A7D72.toInt()
    const val STAR = 0xFFF2B84B.toInt()
    const val STAR_EMPTY = 0xFFE6DACC.toInt()
}

/**
 * Draws the card: [title] ("Mia plays Ode to Joy" reads best: see [ConcertFiles.title]), [stars] of three filled,
 * [stickerRes] (a drawable, or null for the empty slot the World team fills), [footer] at the bottom.
 */
fun drawConcertCard(context: Context, title: String, stars: Int, stickerRes: Int?, footer: String,
                    width: Int = ConcertCardLayout.WIDTH, height: Int = ConcertCardLayout.HEIGHT): Bitmap {
    val l = ConcertCardLayout.of(title, stars, width, height)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    c.drawColor(CardInk.PAPER)
    // A thin accent frame, inset like a print.
    p.style = Paint.Style.STROKE; p.strokeWidth = width * 0.008f; p.color = CardInk.EDGE
    val inset = width * 0.03f
    c.drawRoundRect(RectF(inset, inset, width - inset, height - inset), l.corner, l.corner, p)

    // The pup in his gold glow.
    p.style = Paint.Style.FILL; p.color = CardInk.GLOW
    c.drawCircle(l.pup.centerX, l.pup.centerY, l.pup.width * 0.5f, p)
    drawRes(context, Pose.GREAT_JOB.res, RectF(l.pup.left, l.pup.top, l.pup.right, l.pup.bottom), c)

    // The title, centred, one or two lines.
    val nunito = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull()
    p.typeface = nunito; p.fontVariationSettings = "'wght' 800"
    p.textSize = l.titleSize; p.color = CardInk.INK; p.textAlign = Paint.Align.CENTER
    val lineH = l.titleSize * 1.15f
    val top = l.title.centerY - lineH * l.titleLines.size / 2
    l.titleLines.forEachIndexed { i, s -> c.drawText(s, l.title.centerX, top + lineH * i + l.titleSize * 0.9f, p) }

    // Stars: gold for the ones earned, a pale outline for the rest.
    l.stars.forEachIndexed { i, b ->
        val path = Path().apply {
            ConcertCardLayout.starPoints(b).forEachIndexed { k, (x, y) -> if (k == 0) moveTo(x, y) else lineTo(x, y) }
            close()
        }
        p.typeface = null; p.fontVariationSettings = null
        if (i < l.filled) { p.style = Paint.Style.FILL; p.color = CardInk.STAR; c.drawPath(path, p) }
        else { p.style = Paint.Style.STROKE; p.strokeWidth = width * 0.008f; p.strokeJoin = Paint.Join.ROUND; p.color = CardInk.STAR_EMPTY; c.drawPath(path, p) }
    }

    // The sticker, or its slot: a dashed circle.
    val s = l.sticker
    if (stickerRes != null) drawRes(context, stickerRes, RectF(s.left, s.top, s.right, s.bottom), c)
    else {
        p.style = Paint.Style.STROKE; p.strokeWidth = width * 0.006f; p.color = CardInk.STAR_EMPTY
        p.pathEffect = DashPathEffect(floatArrayOf(width * 0.018f, width * 0.012f), 0f)
        c.drawCircle(s.centerX, s.centerY, s.width * 0.42f, p)
        p.pathEffect = null
    }

    // The footer: the app and the day.
    val geist = runCatching { ResourcesCompat.getFont(context, R.font.geist) }.getOrNull()
    p.style = Paint.Style.FILL; p.typeface = geist; p.fontVariationSettings = "'wght' 500"
    p.textSize = l.footerSize; p.color = CardInk.INK_DIM; p.textAlign = Paint.Align.CENTER
    c.drawText(footer, l.footer.centerX, l.footer.bottom - l.footerSize * 0.3f, p)
    return bmp
}

/** Draws a drawable-nodpi image into [box], keeping its shape, centred. */
private fun drawRes(context: Context, res: Int, box: RectF, c: Canvas) {
    val img = BitmapFactory.decodeResource(context.resources, res) ?: return
    val scale = minOf(box.width() / img.width, box.height() / img.height)
    val w = img.width * scale; val h = img.height * scale
    val dst = RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2)
    c.drawBitmap(img, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    img.recycle()
}

/**
 * Writes the concert into a fresh cache/share: "<title>.wav" (when there is a run with notes) and "<title>.png".
 * Call off the main thread.
 */
fun packConcert(context: Context, run: RunRecording?, title: String, stars: Int, stickerRes: Int?, day: LocalDate = LocalDate.now()): List<File> {
    val dir = Share.fresh(context)
    val (wavName, pngName) = ConcertFiles.names(title)
    val out = ArrayList<File>(2)
    if (run != null && !run.isEmpty) {
        val keys = context.assets.list("samples").orEmpty().mapNotNull { it.removePrefix("p").removeSuffix(".ogg").toIntOrNull() }
        val cache = HashMap<Int, FloatArray?>()
        val audio = ConcertMix.render(run, keys, MIX_RATE) { k -> cache.getOrPut(k) { decodeSample(context, "samples/p$k.ogg") } }
        if (audio.isNotEmpty()) out += File(dir, wavName).also { Wav.write(it, audio, MIX_RATE) }
    }
    val card = drawConcertCard(context, title, stars, stickerRes, ConcertFiles.footer(day))
    out += File(dir, pngName).also { f -> f.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    card.recycle()
    Log.d(TAG, "concert: packed ${out.joinToString { "${it.name} (${it.length() / 1024} KB)" }}")
    return out
}

private const val MIX_RATE = 48_000

/** Decodes one of the piano samples (OGG in assets) to mono floats at [MIX_RATE]; null when the phone cannot. */
private fun decodeSample(context: Context, path: String): FloatArray? = runCatching {
    val ex = MediaExtractor()
    context.assets.openFd(path).use { fd -> ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
    val format = ex.getTrackFormat(0)
    ex.selectTrack(0)
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    codec.configure(format, null, null, 0); codec.start()
    var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var pcm = FloatArray(rate * 4)
    var n = 0
    val info = MediaCodec.BufferInfo()
    var inDone = false; var outDone = false
    try {
        while (!outDone) {
            if (!inDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                    else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> codec.outputFormat.let {
                    channels = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT); rate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                o >= 0 -> {
                    val b = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                    b.position(info.offset); b.limit(info.offset + info.size)
                    val s = b.asShortBuffer()
                    while (s.remaining() >= channels) {
                        var v = 0f
                        repeat(channels) { v += s.get() / 32768f }
                        if (n == pcm.size) pcm = pcm.copyOf(pcm.size * 2)
                        pcm[n++] = v / channels
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
        }
    } finally { codec.stop(); codec.release(); ex.release() }
    val mono = pcm.copyOf(n)
    if (rate == MIX_RATE) mono else FloatArray((mono.size.toLong() * MIX_RATE / rate).toInt()) { i ->
        val pos = i.toDouble() * rate / MIX_RATE
        val k = pos.toInt().coerceAtMost(mono.size - 2)
        (mono[k] + (mono[k + 1] - mono[k]) * (pos - k)).toFloat()
    }
}.onFailure { Log.d(TAG, "concert: could not decode $path: $it") }.getOrNull()

/**
 * The "Send to someone" button for a recap: packs [run] (may be null: then only the card goes) and the card, then
 * opens the share sheet. [songTitle] and [stars] are the run's; [stickerRes] the sticker it dropped, when there is one.
 */
@Composable
fun ShareConcertButton(run: RunRecording?, songTitle: String, stars: Int, modifier: Modifier = Modifier, stickerRes: Int? = null) {
    val context = LocalContext.current
    val name = LocalProfile.current.name
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // A kid hands the phone to a grown-up to send it: the share sheet reaches every app and contact (parent gate).
    val gate = com.bigpapituna.sharpears.ui.family.LocalParentGate.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(enabled = !busy, onClick = { gate.guardForKids {
            busy = true; error = null
            scope.launch {
                val title = ConcertFiles.title(name, songTitle)
                val files = runCatching { withContext(Dispatchers.IO) { packConcert(context, run, title, stars, stickerRes) } }
                busy = false
                files.onSuccess { Share.files(context, it, title) }
                    .onFailure { Log.d(TAG, "concert: share failed: $it"); com.bigpapituna.sharpears.analytics.Tester.error("concert share"); error = "Could not get it ready. Try again?" }
            }
        } }) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Getting it ready" else "Send to someone")
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ---- previews ----

@Preview(name = "Concert card", widthDp = 360, heightDp = 450)
@Composable
private fun ConcertCardPreview() {
    val context = LocalContext.current
    val card = remember { drawConcertCard(context, "Mia plays Twinkle Twinkle Little Star", 2, null, "Sharp Ears, October 18, 2026", 720, 900) }
    Image(card.asImageBitmap(), contentDescription = "The concert card")
}

@Preview(name = "Send to someone")
@Composable
private fun ShareConcertButtonPreview() = SharpEarsTheme(dark = false) {
    Surface { Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ShareConcertButton(run = null, songTitle = "Ode to Joy", stars = 3)
    } }
}

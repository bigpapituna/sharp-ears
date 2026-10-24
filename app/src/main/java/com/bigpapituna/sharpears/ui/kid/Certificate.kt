package com.bigpapituna.sharpears.ui.kid

import com.bigpapituna.sharpears.analytics.testerMask
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Share
import com.bigpapituna.sharpears.engine.kid.CertificateWords
import com.bigpapituna.sharpears.engine.play.Box as CardBox
import com.bigpapituna.sharpears.engine.play.ConcertCardLayout
import com.bigpapituna.sharpears.engine.play.ConcertFiles
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.kid.KidPages
import com.bigpapituna.sharpears.ui.world.colour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import com.bigpapituna.sharpears.ui.pup.Pose

/** The certificate's colours: warm paper whatever the phone's theme, so it prints and sends the same. */
private object CertInk {
    const val PAPER = 0xFFFBF5EE.toInt()
    const val PAPER_EDGE = 0xFFF1E4D0.toInt()
    const val ACCENT = 0xFFE38E5A.toInt()
    const val GOLD = 0xFFF2B84B.toInt()
    const val GOLD_DEEP = 0xFFC98A1E.toInt()
    const val GLOW = 0x66F2B84B
    const val INK = 0xFF2B2420.toInt()
    const val INK_DIM = 0xFF8A7D72.toInt()
    const val STAR_EMPTY = 0xFFE6DACC.toInt()
}

/** The certificate's size: 5:4, landscape like a certificate on a wall, whole in any chat. */
const val CERT_W = 1350
const val CERT_H = 1080

/**
 * Draws the certificate (2.2, the finale): warm paper in a double frame with stars at the corners, the pup in his crown
 * in a gold glow on the left over a rosette in the page's colour, and on the right "Well done!", the kid's [name] big,
 * "finished the garden", the page's [stars] and the [date]. Android's Canvas, so it can go out as a PNG.
 */
fun drawCertificate(context: Context, name: String, page: PlaygroundPage, stars: Int, date: LocalDate,
                    width: Int = CERT_W, height: Int = CERT_H): Bitmap {
    val words = CertificateWords.of(name, page, date)
    val w = width.toFloat(); val h = height.toFloat()
    val bmp = androidx.core.graphics.createBitmap(width, height)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // Paper with a soft warm edge.
    c.drawColor(CertInk.PAPER)
    p.shader = RadialGradient(w / 2, h / 2, w * 0.75f, intArrayOf(CertInk.PAPER, CertInk.PAPER_EDGE), floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w, h, p); p.shader = null
    // The double frame and a star in each corner.
    val m = w * 0.035f
    p.style = Paint.Style.STROKE; p.color = CertInk.ACCENT; p.strokeWidth = w * 0.009f
    c.drawRoundRect(RectF(m, m, w - m, h - m), w * 0.03f, w * 0.03f, p)
    p.color = CertInk.GOLD; p.strokeWidth = w * 0.004f
    val m2 = m + w * 0.018f
    c.drawRoundRect(RectF(m2, m2, w - m2, h - m2), w * 0.02f, w * 0.02f, p)
    val cs = w * 0.045f
    listOf(m2 to m2, w - m2 to m2, m2 to h - m2, w - m2 to h - m2).forEach { (x, y) -> star(c, p, x, y, cs, CertInk.GOLD, CertInk.GOLD_DEEP) }

    // Left: the pup in his crown in a gold glow, a rosette in the page's colour under him.
    val pupBox = RectF(w * 0.07f, h * 0.14f, w * 0.40f, h * 0.70f)
    p.style = Paint.Style.FILL; p.color = CertInk.GLOW
    c.drawCircle(pupBox.centerX(), pupBox.centerY() + h * 0.02f, pupBox.width() * 0.5f, p)
    drawRes(context, Pose.CROWN.res, pupBox, c)
    val pageColour = page.place.let(KidPages::worldPlace).colour().toArgb()
    rosette(c, p, pupBox.centerX(), h * 0.80f, w * 0.075f, pageColour)

    // Right: the words.
    val nunito = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull()
    val geist = runCatching { ResourcesCompat.getFont(context, R.font.geist) }.getOrNull()
    val left = w * 0.46f; val right = w - w * 0.08f; val mid = (left + right) / 2
    p.textAlign = Paint.Align.CENTER; p.style = Paint.Style.FILL
    p.typeface = nunito; p.fontVariationSettings = "'wght' 800"
    p.color = CertInk.ACCENT; p.textSize = h * 0.075f
    c.drawText(words.heading, mid, h * 0.24f, p)
    // The name, as big as it fits.
    p.color = CertInk.INK
    p.textSize = h * 0.16f
    while (p.measureText(words.name) > right - left && p.textSize > h * 0.06f) p.textSize *= 0.94f
    c.drawText(words.name, mid, h * 0.43f, p)
    p.typeface = geist; p.fontVariationSettings = "'wght' 500"
    p.color = CertInk.INK; p.textSize = h * 0.058f
    c.drawText(words.what, mid, h * 0.545f, p)
    // The page's stars.
    val ss = h * 0.11f; val gap = h * 0.03f
    val row = 3 * ss + 2 * gap
    for (i in 0 until 3) {
        val x = mid - row / 2 + i * (ss + gap) + ss / 2
        val y = h * 0.67f - if (i == 1) ss * 0.12f else 0f
        if (i < stars.coerceIn(1, 3)) star(c, p, x, y, ss, CertInk.GOLD, CertInk.GOLD_DEEP)
        else star(c, p, x, y, ss, CertInk.PAPER, CertInk.STAR_EMPTY)
    }
    p.style = Paint.Style.FILL; p.color = CertInk.INK_DIM; p.textSize = h * 0.042f
    c.drawText(words.date, mid, h * 0.82f, p)
    p.textSize = h * 0.032f; p.fontVariationSettings = "'wght' 600"
    c.drawText(words.footer, mid, h - m2 - h * 0.035f, p)
    return bmp
}

/** A five-point star centred on ([x], [y]), [size] across, filled and outlined. */
private fun star(c: Canvas, p: Paint, x: Float, y: Float, size: Float, fill: Int, edge: Int) {
    val pts = ConcertCardLayout.starPoints(CardBox(x - size / 2, y - size / 2, x + size / 2, y + size / 2))
    val path = Path().apply { pts.forEachIndexed { k, (px, py) -> if (k == 0) moveTo(px, py) else lineTo(px, py) }; close() }
    p.style = Paint.Style.FILL; p.color = fill; c.drawPath(path, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.06f; p.strokeJoin = Paint.Join.ROUND; p.color = edge; c.drawPath(path, p)
}

/** A prize rosette: two ribbon tails and a pleated disc in [colour] with a gold middle. */
private fun rosette(c: Canvas, p: Paint, x: Float, y: Float, r: Float, colour: Int) {
    p.style = Paint.Style.FILL; p.color = colour
    listOf(-1f, 1f).forEach { s ->
        val tail = Path().apply {
            moveTo(x + s * r * 0.25f, y); lineTo(x + s * r * 0.85f, y + r * 1.7f); lineTo(x + s * r * 0.55f, y + r * 1.45f)
            lineTo(x + s * r * 0.25f, y + r * 1.75f); lineTo(x - s * r * 0.15f, y); close()
        }
        c.drawPath(tail, p)
    }
    val pleats = Path()
    for (i in 0 until 24) {
        val a = Math.PI * 2 * i / 24
        val rr = if (i % 2 == 0) r else r * 0.86f
        val px = x + (rr * Math.cos(a)).toFloat(); val py = y + (rr * Math.sin(a)).toFloat()
        if (i == 0) pleats.moveTo(px, py) else pleats.lineTo(px, py)
    }
    pleats.close()
    c.drawPath(pleats, p)
    p.color = CertInk.GOLD; c.drawCircle(x, y, r * 0.6f, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.06f; p.color = CertInk.GOLD_DEEP; c.drawCircle(x, y, r * 0.6f, p)
    star(c, p, x, y, r * 0.75f, CertInk.PAPER, CertInk.GOLD_DEEP)
}

/** Draws a drawable-nodpi image into [box], keeping its shape, centred. */
private fun drawRes(context: Context, res: Int, box: RectF, c: Canvas) {
    val img = BitmapFactory.decodeResource(context.resources, res) ?: return
    val scale = minOf(box.width() / img.width, box.height() / img.height)
    val iw = img.width * scale; val ih = img.height * scale
    c.drawBitmap(img, null, RectF(box.centerX() - iw / 2, box.bottom - ih, box.centerX() + iw / 2, box.bottom), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    img.recycle()
}

/** Writes the certificate into a fresh cache/share as "<Name> finished the garden.png" (call off the main thread). */
fun packCertificate(context: Context, name: String, page: PlaygroundPage, stars: Int, date: LocalDate = LocalDate.now()): File {
    val dir = Share.fresh(context)
    val bmp = drawCertificate(context, name, page, stars, date)
    val file = File(dir, ConcertFiles.stem(CertificateWords.of(name, page, date).title) + ".png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    Log.d(TAG, "certificate: packed ${file.name} (${file.length() / 1024} KB)")
    return file
}

/**
 * The certificate on screen, as it will be sent (the same bitmap), with "Send to someone" under it: the PNG through the
 * share sheet ([Share.files]); nothing leaves the phone unless a grown-up picks an app.
 */
@Composable
fun CertificateCard(name: String, page: PlaygroundPage, stars: Int, modifier: Modifier = Modifier, date: LocalDate = LocalDate.now()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val words = remember(name, page, date) { CertificateWords.of(name, page, date) }
    // A kid hands the phone to a grown-up to send it: the share sheet reaches every app and contact (parent gate).
    val gate = com.bigpapituna.sharpears.ui.family.LocalParentGate.current
    LaunchedEffect(name, page, stars) { image = withContext(Dispatchers.Default) { drawCertificate(context, name, page, stars, date, CERT_W / 2, CERT_H / 2) } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The child's name is drawn into the picture: hidden in tester replays.
        Box(Modifier.fillMaxWidth().aspectRatio(CERT_W / CERT_H.toFloat()).shadow(10.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .testerMask().semantics { contentDescription = "${words.heading} ${words.name} ${words.what}, ${words.date}" }) {
            image?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(CERT_W / CERT_H.toFloat())) }
        }
        OutlinedButton(enabled = !busy, onClick = { gate.guardForKids {
            busy = true; error = null
            scope.launch {
                val file = runCatching { withContext(Dispatchers.IO) { packCertificate(context, name, page, stars, date) } }
                busy = false
                file.onSuccess { Share.files(context, listOf(it), words.title) }
                    .onFailure { Log.d(TAG, "certificate: share failed: $it"); com.bigpapituna.sharpears.analytics.Tester.error("certificate share"); error = "Could not get it ready. Try again?" }
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

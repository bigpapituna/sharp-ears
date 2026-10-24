package com.bigpapituna.sharpears.ui.pup

import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * A pose cut into layers (2.10, scripts/pup_layers.py from the SAM 2 cuts in C:\dev\tools\pup-parts): body, head, the ears,
 * the tail and, where they are open, the eyes. [w] x [h] is the pose's canvas; [headMax], [earMax], [tailMax] are how far
 * this cut lets each part turn (degrees) before a gap or a slid face shows.
 */
internal class PupRig(val w: Int, val h: Int, val headMax: Float, val earMax: Float, val tailMax: Float, val layers: List<PupLayer>) {
    val hasEyes = layers.any { it.name == EYES }
}

/** One layer: its picture, where its top left sits on the canvas ([x], [y]), its pivot ([px], [py]) and its parent. */
internal class PupLayer(val name: String, val res: Int, val x: Float, val y: Float, val px: Float, val py: Float, val parent: String)

internal const val HEAD = "head"
internal const val TAIL = "tail"
internal const val EYES = "eyes"
internal const val LEFT_EAR = "left_ear"
internal const val RIGHT_EAR = "right_ear"

/** A rig with its pictures decoded (through [PupArt]'s cache), ready to draw. */
internal class RigArt(val rig: PupRig, val bitmaps: List<ImageBitmap>)

/** The layered rig of [pose], decoded; null for the thirty poses drawn whole. Slow: call it off the main thread. */
internal fun loadRig(res: Resources, pose: Pose): RigArt? {
    val rig = PUP_LAYERS[pose] ?: return null
    return RigArt(rig, rig.layers.map { PupArt.bitmap(res, it.res) })
}

/**
 * How the parts are turned this frame (degrees, clockwise): the head round the neck (the ears and eyes go with it), each
 * ear round its base, the tail round its root; [eyes] squeezes the eyes' height about their middle (1 open .. 0 shut).
 * Each is held to what the cut allows.
 */
internal class RigPose(var head: Float = 0f, var leftEar: Float = 0f, var rightEar: Float = 0f, var tail: Float = 0f, var eyes: Float = 1f)

/** The pose drawn from its layers into [box] (the canvas fitted in the pup's box), turned as [p] says. */
internal fun DrawScope.drawRig(art: RigArt, box: Rect, p: RigPose, colorFilter: ColorFilter?) {
    val rig = art.rig
    val k = box.width / rig.w
    val head = rig.layers.firstOrNull { it.name == HEAD }
    val headDeg = p.head.coerceIn(-rig.headMax, rig.headMax)
    translate(box.left, box.top) {
        scale(k, k, Offset.Zero) {
            for ((i, l) in rig.layers.withIndex()) {
                val img = art.bitmaps[i]
                fun DrawScope.picture() = drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(l.x.toInt(), l.y.toInt()),
                    IntSize(img.width, img.height), colorFilter = colorFilter)
                fun DrawScope.own() = when (l.name) {
                    LEFT_EAR -> rotate(p.leftEar.coerceIn(-rig.earMax, rig.earMax), Offset(l.px, l.py)) { picture() }
                    RIGHT_EAR -> rotate(p.rightEar.coerceIn(-rig.earMax, rig.earMax), Offset(l.px, l.py)) { picture() }
                    TAIL -> rotate(p.tail.coerceIn(-rig.tailMax, rig.tailMax), Offset(l.px, l.py)) { picture() }
                    EYES -> scale(1f, p.eyes.coerceIn(0.05f, 1f), Offset(l.px, l.py)) { picture() }
                    HEAD -> rotate(headDeg, Offset(l.px, l.py)) { picture() }
                    else -> picture()
                }
                if (l.parent == HEAD && head != null && headDeg != 0f) rotate(headDeg, Offset(head.px, head.py)) { own() } else own()
            }
        }
    }
}

package com.bigpapituna.sharpears.ui.pup

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.engine.world.IdleAct
import com.bigpapituna.sharpears.engine.world.PupHold
import com.bigpapituna.sharpears.engine.world.PupReaction

/*
 * The pup's pictures (res/drawable-nodpi/pup_*.webp). Every pup on screen is drawn by ui/pup: [Pup] for the living one,
 * [PupAvatarArt] for a profile's face, [rememberPupBitmap] for a game field that draws him as a sprite. No other file names a
 * pup drawable (PupWiringTest).
 */

/**
 * The Golden Coach Pup's poses: the ten from the first sheet (art/mascot/sheet.jpg, cut by scripts/cut_mascot.py) and the
 * painted ones (art/mascot/pup-*.jpg, cut by scripts/cut_pup.py; what each shows is in art/GEMINI-BRIEF.md). [WEAR_BASE] is
 * the clean sitting pose the outfit is drawn over. [eyesOpen]: false where the picture already has them shut (a happy
 * squint, asleep), so he never blinks there.
 */
enum class Pose(val res: Int, val eyesOpen: Boolean = true) {
    WELCOME(R.drawable.pup_welcome), PRACTICING(R.drawable.pup_practicing), GREAT_JOB(R.drawable.pup_great_job, eyesOpen = false),
    MISTAKE(R.drawable.pup_mistake), LEARNING(R.drawable.pup_learning), LEVEL_UP(R.drawable.pup_level_up, eyesOpen = false),
    STREAK(R.drawable.pup_streak), LISTENING(R.drawable.pup_listening, eyesOpen = false), GET_READY(R.drawable.pup_get_ready),
    RESTING(R.drawable.pup_resting, eyesOpen = false),
    // Painted, one image each.
    BUTTERFLY(R.drawable.pup_butterfly), CLAPPING(R.drawable.pup_clapping, eyesOpen = false), CROWN(R.drawable.pup_crown),
    DANCING(R.drawable.pup_dancing, eyesOpen = false), DRUMMING(R.drawable.pup_drumming), FLYING(R.drawable.pup_flying),
    GOODNIGHT(R.drawable.pup_goodnight, eyesOpen = false), HEAD_TILT(R.drawable.pup_head_tilt), PEEK(R.drawable.pup_peek),
    POINTING(R.drawable.pup_pointing), SCRATCH(R.drawable.pup_scratch, eyesOpen = false), SHH(R.drawable.pup_shh, eyesOpen = false),
    SLEEPING(R.drawable.pup_sleeping, eyesOpen = false), SNEEZE(R.drawable.pup_sneeze, eyesOpen = false), STANDING(R.drawable.pup_standing),
    STICKER(R.drawable.pup_sticker), SURPRISED(R.drawable.pup_surprised), TAIL_CHASE(R.drawable.pup_tail_chase),
    THINKING(R.drawable.pup_thinking), TREAT(R.drawable.pup_treat), WEAR_BASE(R.drawable.pup_wear_base),
}

/**
 * The closed-eyes picture of the same pose, for a blink by cross-fade (PupMotion.BLINK_FADE_MS). None of the forty stills
 * has one yet (no pair shares a body with only the eyes shut), so every pose with open eyes blinks by the squint instead.
 * ponytail: null for all, add `pup_<pose>_blink.webp` art and name it here.
 */
val Pose.blinkTwin: Pose? get() = null

/**
 * The mood a pose carries when the screen names none: the dancing, clapping and drumming pictures cheer on the beat, the
 * sleepy ones breathe slowly, the listening one leans in. The rest idle.
 */
val Pose.mood: PupMood get() = when (this) {
    Pose.DANCING, Pose.CLAPPING, Pose.DRUMMING -> PupMood.CHEER
    Pose.SLEEPING, Pose.RESTING, Pose.GOODNIGHT -> PupMood.SLEEP
    Pose.LISTENING -> PupMood.LISTEN
    else -> PupMood.IDLE
}

/** The pose for a reaction. */
val PupReaction.pose: Pose get() = when (this) {
    PupReaction.SURPRISED -> Pose.SURPRISED
    PupReaction.THINKING -> Pose.THINKING
    PupReaction.SHH -> Pose.SHH
    PupReaction.PEEK -> Pose.PEEK
    PupReaction.HEAD_TILT -> Pose.HEAD_TILT
    PupReaction.TREAT -> Pose.TREAT
    PupReaction.CLAP -> Pose.CLAPPING
    PupReaction.DANCE -> Pose.DANCING
    PupReaction.FLY -> Pose.FLYING
    PupReaction.CROWN -> Pose.CROWN
    PupReaction.GOODNIGHT -> Pose.GOODNIGHT
}

/**
 * The beat a reaction plays with its pose (2.10): a sticker (a clap for a common one, surprise for a rare one), a dance, the
 * crown jump for joy; a wrong key tilts; a treat wags.
 */
val PupReaction.beat: PupMood? get() = when (this) {
    PupReaction.SURPRISED, PupReaction.CLAP, PupReaction.DANCE, PupReaction.FLY, PupReaction.CROWN -> PupMood.HAPPY
    PupReaction.HEAD_TILT -> PupMood.TILT
    PupReaction.TREAT -> PupMood.WAG
    else -> null
}

/** The pose for a held state of the brain (null: the screen's own pose). */
val PupHold.pose: Pose? get() = when (this) {
    PupHold.NONE -> null
    PupHold.THINKING -> Pose.THINKING
    PupHold.SHH -> Pose.SHH
    PupHold.LISTENING -> Pose.LISTENING
    PupHold.PEEK -> Pose.PEEK
    PupHold.SLEEPING -> Pose.SLEEPING
    PupHold.BUSY -> null
}

/** The pose for an idle act. */
val IdleAct.pose: Pose get() = when (this) {
    IdleAct.SNEEZE -> Pose.SNEEZE
    IdleAct.TAIL_CHASE -> Pose.TAIL_CHASE
    IdleAct.BUTTERFLY -> Pose.BUTTERFLY
    IdleAct.SCRATCH -> Pose.SCRATCH
}

/** The eight avatar faces (the sitting pup with the sweatband in each avatar's colour), by avatar number. */
internal val AVATAR_ART = intArrayOf(
    R.drawable.pup_avatar_0, R.drawable.pup_avatar_1, R.drawable.pup_avatar_2, R.drawable.pup_avatar_3,
    R.drawable.pup_avatar_4, R.drawable.pup_avatar_5, R.drawable.pup_avatar_6, R.drawable.pup_avatar_7,
)

/**
 * The decoded pictures, shared by every pup in the process: a pose decoded once is drawn by all the pups that show it, and a
 * pose swapped back and forth (the Jam, the arcade) is not decoded again. About 1.7 MB a picture, about 2 MB a layered pose's
 * parts; 24 MB keeps the ones in use. Thread-safe: the layers are decoded off the main thread.
 * Mipmapped, so a 576 px picture drawn at 48 dp stays smooth while he breathes.
 */
internal object PupArt {
    private val cache = object : LruCache<Int, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: ImageBitmap) = value.width * value.height * 4
    }

    fun bitmap(res: Resources, id: Int): ImageBitmap = cache.get(id) ?: run {
        val b = BitmapFactory.decodeResource(res, id)
        b.setHasMipMap(true)
        b.prepareToDraw()
        b.asImageBitmap().also { cache.put(id, it) }
    }

    /** Where a picture of [w] x [h] sits in a box of [box]: fitted whole, feet on the bottom edge, centred. */
    fun fit(w: Int, h: Int, box: Size): Rect {
        val k = minOf(box.width / w, box.height / h)
        val dw = w * k; val dh = h * k
        return Rect(Offset((box.width - dw) / 2f, box.height - dh), Size(dw, dh))
    }
}

/** The picture of [pose] for a game field that draws the pup itself (a sprite crossing a bridge, a dress-up tile). */
@Composable
fun rememberPupBitmap(pose: Pose): ImageBitmap {
    val res = LocalResources.current
    return remember(pose) { PupArt.bitmap(res, pose.res) }
}

/** A profile's face (ui/family/Avatar.kt draws the disc round it): the avatar's picture, fitted, feet down. Still. */
@Composable
fun PupAvatarArt(avatar: Int, modifier: Modifier = Modifier) {
    val res = LocalResources.current
    val art = remember(avatar) { PupArt.bitmap(res, AVATAR_ART[avatar.coerceIn(0, AVATAR_ART.size - 1)]) }
    Canvas(modifier) {
        val r = PupArt.fit(art.width, art.height, size)
        drawImage(art, IntOffset.Zero, IntSize(art.width, art.height), IntOffset(r.left.toInt(), r.top.toInt()), IntSize(r.width.toInt(), r.height.toInt()))
    }
}

package com.bigpapituna.sharpears.ui.family

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.pup.PupAvatarArt

/** The sweatband colours, by avatar index (scripts/tint_avatars.py paints the pups with the same ones). */
val AVATAR_COLOURS = listOf(
    Color(0xFFD2443F), Color(0xFF3E7BDB), Color(0xFF3EA35A), Color(0xFF8B5CD6),
    Color(0xFFF2C12E), Color(0xFFE8609E), Color(0xFFF08A2E), Color(0xFF1FA69A),
)

fun avatarColour(avatar: Int): Color = AVATAR_COLOURS[avatar.coerceIn(0, Profile.AVATARS - 1)]

/** "the blue pup", for TalkBack. */
fun avatarLabel(avatar: Int): String = "the ${Profile.AVATAR_NAMES[avatar.coerceIn(0, Profile.AVATARS - 1)]} pup"

/**
 * A profile's face: the pup in his sitting pose with the sweatband in the avatar's colour, on a soft disc of the same
 * colour. [selected] draws a ring around it. Decorative; the name beside it says who it is.
 * ponytail: tinted copies of pup_wear_base (ui/pup/PupArt.kt AVATAR_ART); real avatar art replaces res/drawable-nodpi/pup_avatar_<n>.webp later.
 */
@Composable
fun PupAvatar(avatar: Int, modifier: Modifier = Modifier, size: Dp = 72.dp, selected: Boolean = false) {
    val colour = avatarColour(avatar)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier.size(size)
            .then(if (selected) Modifier.border(3.dp, colour, CircleShape).padding(5.dp) else Modifier)
            .clip(CircleShape)
            .background(colour.copy(alpha = if (dark) 0.28f else 0.20f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        PupAvatarArt(avatar.coerceIn(0, Profile.AVATARS - 1), Modifier.fillMaxSize().padding(top = size * 0.08f))
    }
}

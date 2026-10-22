package com.bigpapituna.sharpears.ui.arcade

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bigpapituna.sharpears.engine.arcade.ArcadeGame

/**
 * How a game sits in the round's screen: its playfield, whether the pup lives inside it ([pupInField]: Feed the pup, the
 * one he eats in) and whether its field is the keyboard itself ([ownKeys]: the moles peek out of the keys).
 */
internal class GameSpec(val pupInField: Boolean = false, val ownKeys: Boolean = false,
                        val field: @Composable (ArcadePlay, Modifier, (Int) -> Unit) -> Unit) {
    companion object {
        fun of(game: ArcadeGame): GameSpec = when (game) {
            ArcadeGame.BALLOONS -> GameSpec { p, m, _ -> BalloonField(p, m) }
            ArcadeGame.FEED -> GameSpec(pupInField = true) { p, m, _ -> FeedField(p, m) }
            ArcadeGame.MOLES -> GameSpec(ownKeys = true) { p, m, key -> MoleField(p, m, key) }
            ArcadeGame.GARDEN -> GameSpec { p, m, _ -> GardenField(p, m) }
            ArcadeGame.TOWER -> GameSpec { p, m, _ -> TowerField(p, m) }
            ArcadeGame.FROGS -> GameSpec { p, m, _ -> FrogField(p, m) }
            ArcadeGame.FIREWORKS -> GameSpec { p, m, _ -> FireworksField(p, m) }
            ArcadeGame.PAINTER -> GameSpec { p, m, _ -> PainterField(p, m) }
            ArcadeGame.BRIDGE -> GameSpec { p, m, _ -> BridgeField(p, m) }
        }
    }
}

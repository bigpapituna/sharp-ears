package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.bigpapituna.sharpears.TAG

/**
 * Tabletop posture (DESIGN 2.2): the Fold half open with the hinge horizontal. [hingeTopPx] and [hingeBottomPx]
 * are window coordinates of the hinge; the player puts the lane above it and the keyboard below.
 */
data class Tabletop(val hingeTopPx: Int, val hingeBottomPx: Int)

/** Null when the phone is flat, folded shut, or not a foldable. [force] (debug) fakes a hinge at mid-window. */
@Composable
fun rememberTabletop(force: Boolean = false): Tabletop? {
    val activity = LocalActivity.current ?: return null
    var tabletop by remember { mutableStateOf<Tabletop?>(null) }
    LaunchedEffect(activity, force) {
        if (force) {
            val h = activity.window.decorView.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
            tabletop = Tabletop(h / 2 - 12, h / 2 + 12)
            Log.d(TAG, "posture tabletop (forced) hinge=${h / 2}")
            return@LaunchedEffect
        }
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            val now = fold?.takeIf { it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL }
                ?.let { Tabletop(it.bounds.top, it.bounds.bottom) }
            Log.d(TAG, "posture ${if (now != null) "tabletop hinge=${now.hingeTopPx}..${now.hingeBottomPx}" else "flat"} (fold=${fold?.state} ${fold?.orientation} ${fold?.bounds})")
            tabletop = now
        }
    }
    return tabletop
}

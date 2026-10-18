package com.bigpapituna.sharpears.engine.world

/** A point in a place's scene, as shares of the scene's width ([x]) and height ([y]). */
data class ScenePoint(val x: Float, val y: Float)

/**
 * The trail through a place's scene (2.2, the kid home): one stop per lesson of the Playground page drawn there, twelve
 * of them, in three rows of four that wind like a snake: left to right along the top row, back right to left, then left
 * to right again and out at the right, where the last scene keeps the gate to the big kids' road. The rows sit where
 * each scene leaves room: the garden's lawn under the dog house, across the pond on a boardwalk, the concert hall's
 * stage and floor. Pure; ui/world/Scenes.kt draws the trail through [path] and puts the stones on [stops].
 */
object Trail {
    /** Stops in a scene: a Playground page's twelve lessons. */
    const val STOPS = 12
    const val PER_ROW = 4

    private val COLUMNS = floatArrayOf(0.12f, 0.35f, 0.58f, 0.81f)
    /** A small lift and dip along each row, so the stones sit like stones and not on a ruler. */
    private val WIGGLE = floatArrayOf(0.006f, -0.008f, 0.009f, -0.005f)

    /** The rows' heights in each scene (three rows hold the twelve stops). */
    private fun rows(place: Place): FloatArray = when (place) {
        Place.GARDEN -> floatArrayOf(0.615f, 0.755f, 0.895f)
        Place.POND -> floatArrayOf(0.53f, 0.69f, 0.85f)
        Place.CONCERT -> floatArrayOf(0.48f, 0.615f, 0.75f)
    }

    /** The row heights for [count] stops: three rows, or more squeezed between the same top and bottom for a longer page. */
    private fun rowsFor(place: Place, count: Int): List<Float> {
        val base = rows(place)
        val n = maxOf(base.size, (count + PER_ROW - 1) / PER_ROW)
        return if (n == base.size) base.toList() else List(n) { i -> base.first() + (base.last() - base.first()) * i / (n - 1) }
    }

    /** Row [row] runs left to right (even rows) or right to left (odd rows). */
    private fun rightward(row: Int) = row % 2 == 0

    /**
     * The first [count] stops of [place]'s trail, in walking order. Twelve for a full page; fewer for a page still being
     * written (the rest of the trail waits empty); a longer page squeezes in more rows rather than leaving the scene.
     */
    fun stops(place: Place, count: Int = STOPS): List<ScenePoint> {
        if (count <= 0) return emptyList()
        val ys = rowsFor(place, count)
        return List(count) { i ->
            val row = i / PER_ROW
            val k = i % PER_ROW
            val col = if (rightward(row)) k else PER_ROW - 1 - k
            ScenePoint(COLUMNS[col], ys[row] + WIGGLE[i % WIGGLE.size])
        }
    }

    /**
     * The whole line the trail is drawn through: in from the left edge level with the first row, through every stop
     * with a wide turn past each row's end, and out over the edge the last row runs to.
     */
    fun path(place: Place, count: Int = STOPS): List<ScenePoint> {
        val stops = stops(place, maxOf(count, 1))
        val ys = rowsFor(place, maxOf(count, 1))
        val out = ArrayList<ScenePoint>(stops.size + ys.size + 2)
        out += ScenePoint(-0.06f, ys.first() + 0.03f)
        stops.forEachIndexed { i, s ->
            out += s
            val row = i / PER_ROW
            val endOfRow = i % PER_ROW == PER_ROW - 1 && i < stops.lastIndex
            if (endOfRow) out += ScenePoint(if (rightward(row)) 0.95f else 0.05f, (ys[row] + ys[row + 1]) / 2f)
        }
        val lastRow = (stops.size - 1) / PER_ROW
        out += ScenePoint(if (rightward(lastRow)) 1.06f else -0.06f, ys[lastRow] - 0.04f)
        return out
    }

    /** Where the gate to the big kids' road stands in the last page's scene: on the trail, where it leaves the scene. */
    fun gate(place: Place, count: Int = STOPS): ScenePoint {
        val ys = rowsFor(place, maxOf(count, 1))
        val lastRow = (maxOf(count, 1) - 1) / PER_ROW
        return ScenePoint(if (rightward(lastRow)) 0.93f else 0.07f, ys[lastRow] - 0.02f)
    }
}

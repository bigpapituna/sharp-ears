package com.bigpapituna.sharpears.engine.world

/**
 * A named spot in a place's scene where a kid can stick a sticker (2.5): its [id] (saved), the [place], what a kid
 * would call it ([name]: "on the dog house roof"), its centre as shares of the scene's width ([x]) and height ([y]),
 * and the sticker's size there as a share of the width ([size]).
 */
data class SceneSlot(val id: String, val place: Place, val name: String, val x: Float, val y: Float, val size: Float)

/** A box in a scene, in shares of its width and height. */
data class SceneBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(o: SceneBox): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/**
 * Where stickers go on the map (2.5, Roy: "which they can place around the trail themselves as they learn, rather
 * than being put on randomly"): every scene has 12 to 14 named [SceneSlot]s, in the sky, on the grass, in the water,
 * on the walls, all clear of the trail's stones, the stars under them and the gate ([blocked], DecorTest at a phone's
 * and the Fold's width). The kid map's decorate mode puts a sticker from the tray into a slot, moves it to another or
 * back to the tray ([Placements]). The pup and his bubble stand over the stickers (drawn after them), so a sticker
 * never hides him.
 */
object Decor {
    private fun s(place: Place, id: String, name: String, x: Float, y: Float, size: Float) = SceneSlot("${place.id}:$id", place, name, x, y, size)

    val SLOTS: List<SceneSlot> = listOf(
        s(Place.GARDEN, "sky", "high in the sky", 0.7f, 0.1f, 0.15f),
        s(Place.GARDEN, "cloud", "on a little cloud", 0.42f, 0.09f, 0.14f),
        s(Place.GARDEN, "sky-left", "in the sky over the dog house", 0.12f, 0.12f, 0.14f),
        s(Place.GARDEN, "tree", "in the tree", 0.87f, 0.23f, 0.13f),
        s(Place.GARDEN, "roof", "on the dog house roof", 0.29f, 0.255f, 0.1f),
        s(Place.GARDEN, "hill", "on the hill", 0.58f, 0.3f, 0.11f),
        s(Place.GARDEN, "fence", "beside the dog house", 0.065f, 0.43f, 0.11f),
        s(Place.GARDEN, "edge", "at the edge of the lawn", 0.95f, 0.555f, 0.09f),
        s(Place.GARDEN, "bed", "in the flower bed", 0.235f, 0.945f, 0.075f),
        s(Place.GARDEN, "grass", "on the grass", 0.465f, 0.945f, 0.075f),
        s(Place.GARDEN, "path", "by the path", 0.695f, 0.945f, 0.075f),
        s(Place.GARDEN, "corner", "in the corner", 0.94f, 0.94f, 0.08f),

        s(Place.POND, "sky", "in the sky", 0.2f, 0.11f, 0.15f),
        s(Place.POND, "sky-right", "high over the pond", 0.78f, 0.12f, 0.15f),
        s(Place.POND, "sun", "next to the clouds", 0.5f, 0.08f, 0.12f),
        s(Place.POND, "trees", "in the trees", 0.1f, 0.33f, 0.11f),
        s(Place.POND, "bridge", "on the far shore", 0.5f, 0.32f, 0.13f),
        s(Place.POND, "trees-right", "in the trees by the water", 0.9f, 0.32f, 0.11f),
        s(Place.POND, "water", "in the pond water", 0.235f, 0.46f, 0.08f),
        s(Place.POND, "water-mid", "on the water", 0.465f, 0.46f, 0.08f),
        s(Place.POND, "water-right", "by the lily pad", 0.695f, 0.46f, 0.08f),
        s(Place.POND, "reeds", "in the reeds", 0.95f, 0.51f, 0.08f),
        s(Place.POND, "shore", "on the shore", 0.235f, 0.945f, 0.075f),
        s(Place.POND, "grass", "on the grass", 0.465f, 0.945f, 0.075f),
        s(Place.POND, "bank", "on the bank", 0.695f, 0.945f, 0.075f),

        s(Place.CONCERT, "top", "on the curtain top", 0.5f, 0.075f, 0.11f),
        s(Place.CONCERT, "lamp-left", "by the left lamp", 0.2f, 0.11f, 0.12f),
        s(Place.CONCERT, "lamp-right", "by the right lamp", 0.8f, 0.11f, 0.12f),
        s(Place.CONCERT, "curtain-left", "on the left curtain", 0.065f, 0.32f, 0.1f),
        s(Place.CONCERT, "curtain-right", "on the right curtain", 0.935f, 0.27f, 0.1f),
        s(Place.CONCERT, "wall-left", "on the back wall", 0.32f, 0.21f, 0.1f),
        s(Place.CONCERT, "wall-right", "on the wall by the spotlight", 0.68f, 0.21f, 0.1f),
        s(Place.CONCERT, "stage-left", "by the stage", 0.235f, 0.35f, 0.09f),
        s(Place.CONCERT, "stage-right", "by the band", 0.765f, 0.35f, 0.09f),
        s(Place.CONCERT, "wings", "in the wings", 0.95f, 0.43f, 0.08f),
        s(Place.CONCERT, "aisle", "in the aisle", 0.95f, 0.56f, 0.08f),
        s(Place.CONCERT, "spot", "in the spotlight", 0.5f, 0.2f, 0.08f),

        s(Place.WORKSHOP, "pegboard", "on the pegboard", 0.385f, 0.13f, 0.11f),
        s(Place.WORKSHOP, "shelf", "on the top shelf", 0.79f, 0.08f, 0.12f),
        s(Place.WORKSHOP, "wall", "on the wall", 0.555f, 0.13f, 0.1f),
        s(Place.WORKSHOP, "window", "by the window", 0.13f, 0.12f, 0.11f),
        s(Place.WORKSHOP, "bench", "on the workbench", 0.455f, 0.27f, 0.1f),
        s(Place.WORKSHOP, "bench-left", "on the bench by the saw", 0.2f, 0.35f, 0.095f),
        s(Place.WORKSHOP, "stool", "on the stool", 0.06f, 0.36f, 0.08f),
        s(Place.WORKSHOP, "piano", "on the new piano", 0.78f, 0.3f, 0.11f),
        s(Place.WORKSHOP, "floor", "on the floorboards", 0.235f, 0.945f, 0.075f),
        s(Place.WORKSHOP, "floor-mid", "on the floor", 0.465f, 0.945f, 0.075f),
        s(Place.WORKSHOP, "floor-right", "by the toolbox", 0.695f, 0.945f, 0.075f),
        s(Place.WORKSHOP, "corner", "in the corner", 0.94f, 0.935f, 0.08f),

        s(Place.TREEHOUSE, "leaves", "high in the leaves", 0.07f, 0.09f, 0.11f),
        s(Place.TREEHOUSE, "branch", "on a branch", 0.22f, 0.18f, 0.11f),
        s(Place.TREEHOUSE, "sky", "in the sky", 0.87f, 0.08f, 0.13f),
        s(Place.TREEHOUSE, "window", "by the treehouse window", 0.3f, 0.3f, 0.1f),
        s(Place.TREEHOUSE, "platform", "on the platform", 0.505f, 0.4f, 0.1f),
        s(Place.TREEHOUSE, "bird", "on the far branch", 0.865f, 0.24f, 0.09f),
        s(Place.TREEHOUSE, "trunk", "by the big trunk", 0.65f, 0.27f, 0.1f),
        s(Place.TREEHOUSE, "bush", "in the bush", 0.92f, 0.45f, 0.09f),
        s(Place.TREEHOUSE, "roots", "among the roots", 0.235f, 0.945f, 0.075f),
        s(Place.TREEHOUSE, "ground", "on the ground", 0.465f, 0.945f, 0.075f),
        s(Place.TREEHOUSE, "mushroom", "by the mushrooms", 0.695f, 0.945f, 0.075f),
        s(Place.TREEHOUSE, "corner", "in the corner", 0.94f, 0.94f, 0.08f),

        s(Place.NIGHT_SKY, "moon", "by the moon", 0.8f, 0.13f, 0.14f),
        s(Place.NIGHT_SKY, "stars", "among the stars", 0.33f, 0.1f, 0.13f),
        s(Place.NIGHT_SKY, "high", "high in the night sky", 0.57f, 0.2f, 0.13f),
        s(Place.NIGHT_SKY, "corner", "in the corner of the sky", 0.11f, 0.2f, 0.11f),
        s(Place.NIGHT_SKY, "cloud", "on a sleepy cloud", 0.36f, 0.33f, 0.11f),
        s(Place.NIGHT_SKY, "east", "over the far hill", 0.92f, 0.32f, 0.085f),
        s(Place.NIGHT_SKY, "hill-left", "on the hill", 0.1f, 0.43f, 0.09f),
        s(Place.NIGHT_SKY, "hilltop", "on the hilltop", 0.62f, 0.41f, 0.1f),
        s(Place.NIGHT_SKY, "hill-right", "by the telescope", 0.81f, 0.43f, 0.09f),
        s(Place.NIGHT_SKY, "grass", "in the grass", 0.235f, 0.945f, 0.075f),
        s(Place.NIGHT_SKY, "flowers", "by the night flowers", 0.465f, 0.945f, 0.075f),
        s(Place.NIGHT_SKY, "glow", "in the glow", 0.695f, 0.945f, 0.075f),

        s(Place.FAIR, "sky", "up with the balloons", 0.1f, 0.11f, 0.12f),
        s(Place.FAIR, "wheel", "by the big wheel", 0.68f, 0.09f, 0.13f),
        s(Place.FAIR, "flags", "on the bunting", 0.4f, 0.1f, 0.1f),
        s(Place.FAIR, "tent", "by the tent", 0.165f, 0.5f, 0.09f),
        s(Place.FAIR, "booth", "by the booth", 0.47f, 0.54f, 0.09f),
        s(Place.FAIR, "stall", "at the stall", 0.72f, 0.54f, 0.09f),
        s(Place.FAIR, "lawn-1", "on the fair's lawn", 0.12f, 0.83f, 0.1f),
        s(Place.FAIR, "lawn-2", "by the snack cart", 0.35f, 0.83f, 0.1f),
        s(Place.FAIR, "lawn-3", "on the midway", 0.58f, 0.83f, 0.1f),
        s(Place.FAIR, "lawn-4", "by the gate path", 0.8f, 0.86f, 0.09f),
        s(Place.FAIR, "front-1", "near the front", 0.235f, 0.945f, 0.075f),
        s(Place.FAIR, "front-2", "by the lights", 0.465f, 0.945f, 0.075f),
        s(Place.FAIR, "front-3", "in the corner", 0.695f, 0.945f, 0.075f),
    )

    private val byId = SLOTS.associateBy { it.id }

    fun slot(id: String?): SceneSlot? = id?.let(byId::get)
    fun of(place: Place): List<SceneSlot> = SLOTS.filter { it.place == place }

    /** The slot's sticker box at a scene [ratio] (height over width; the scene is 0.95 as tall as wide). */
    fun box(s: SceneSlot, ratio: Float = SCENE_HEIGHT): SceneBox {
        val hx = s.size / 2f
        val hy = s.size / ratio / 2f
        return SceneBox(s.x - hx, s.y - hy, s.x + hx, s.y + hy)
    }

    /** A scene's height as a share of its width (ui/world SCENE_RATIO is its inverse). */
    const val SCENE_HEIGHT = 0.95f

    // ---- the map's things, in dp, as ui/kid/KidMap.kt sizes them (MapSizes reads these) ----

    /** A stone's diameter on a scene [w] dp wide. */
    fun stoneDp(w: Float): Float = (w * 0.085f).coerceIn(36f, 60f)
    /** The next stone is bigger. */
    fun nextDp(w: Float): Float = stoneDp(w) * 1.25f
    fun pupDp(w: Float): Float = (w * 0.105f).coerceIn(54f, 96f)
    fun gateDp(w: Float): Float = (w * 0.12f).coerceIn(46f, 88f)

    /**
     * What a slot must keep clear of on a scene [w] dp wide: every stone of the page's trail (the next stone's size,
     * the biggest a stone gets), the three stars hanging under it, and on the last page the gate. Shares of the scene.
     */
    fun blocked(place: Place, stones: Int, w: Float, gate: Boolean): List<SceneBox> {
        val h = w * SCENE_HEIGHT
        val d = nextDp(w)
        val star = stoneDp(w) * 0.36f
        val out = ArrayList<SceneBox>()
        Trail.stops(place, stones).forEach { p ->
            val cx = p.x * w; val cy = p.y * h
            out += SceneBox((cx - d * 0.55f) / w, (cy - d / 2f) / h, (cx + d * 0.55f) / w, (cy + d / 2f) / h)
            // The stars: three in a row a little wider than the stone, from just under its middle.
            val sw = star * 3.3f
            out += SceneBox((cx - sw / 2f) / w, (cy + star - star * 0.15f) / h, (cx + sw / 2f) / w, (cy + 2.05f * star) / h)
        }
        if (gate) {
            val g = Trail.gate(place, stones)
            val gw = gateDp(w)
            val bottom = g.y * h + gw * 0.5f
            out += SceneBox((g.x * w - gw / 2f) / w, (bottom - gw * 1.15f) / h, (g.x * w + gw / 2f) / w, bottom / h)
        }
        return out
    }
}

/**
 * Which sticker sits where, for one person: slot id to sticker key ([Decal.key]). Pure; WorldState keeps it.
 * Unplaced stickers are in the tray ([tray]).
 */
data class Placements(val at: Map<String, String> = emptyMap()) {
    /** The slot [key] sits in, or null when it is in the tray. */
    fun slotOf(key: String): String? = at.entries.firstOrNull { it.value == key }?.key

    /**
     * [key] put in [slotId]: from the tray or from another slot (a move). A sticker already there swaps places with it
     * (to the slot [key] came from), or goes back to the tray when [key] came from the tray.
     */
    fun put(key: String, slotId: String): Placements {
        if (Decor.slot(slotId) == null) return this
        val from = slotOf(key)
        val there = at[slotId]
        if (there == key) return this
        val m = at.toMutableMap()
        from?.let { m.remove(it) }
        m[slotId] = key
        if (there != null && from != null) m[from] = there
        return Placements(m)
    }

    /** [key] back in the tray. */
    fun remove(key: String): Placements = slotOf(key)?.let { Placements(at - it) } ?: this

    /** What sits in the slots of [place], slot to decal. */
    fun on(place: Place): List<Pair<SceneSlot, Decal>> = at.mapNotNull { (slot, key) ->
        val s = Decor.slot(slot)?.takeIf { it.place == place } ?: return@mapNotNull null
        Decal.byKey(key)?.let { s to it }
    }

    /** Only stickers the person owns, in slots that exist (an import, a newer version's slot). */
    fun valid(owned: Set<String>): Placements = Placements(at.filter { (slot, key) -> Decor.slot(slot) != null && key in owned && Decal.byKey(key) != null })

    companion object {
        /** The tray: [earned] (oldest first) not in a slot, newest first. */
        fun tray(earned: List<String>, p: Placements): List<String> {
            val placed = p.at.values.toSet()
            return earned.filter { it !in placed }.distinct().reversed()
        }
    }
}

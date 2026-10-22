package com.bigpapituna.sharpears.ui.kid

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.kid.KidLines
import com.bigpapituna.sharpears.engine.kid.KidMapModel
import com.bigpapituna.sharpears.engine.kid.Stone
import com.bigpapituna.sharpears.engine.kid.StoneState
import com.bigpapituna.sharpears.engine.play.Quest
import com.bigpapituna.sharpears.engine.play.QuestTask
import com.bigpapituna.sharpears.engine.world.WorldState
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.analytics.testerMask
import com.bigpapituna.sharpears.ui.family.PupAvatar
import com.bigpapituna.sharpears.ui.motion.BounceSize
import com.bigpapituna.sharpears.ui.play.QuestCardContent
import com.bigpapituna.sharpears.ui.play.Toy
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.rememberPupController
import kotlinx.coroutines.delay

/** What the grown-ups' corner offers once the parent gate is passed. */
class GrownUpCorner(val profiles: List<Profile>, val currentId: String, val onSettings: () -> Unit, val onMicSetup: () -> Unit,
                    val onSwitch: (String) -> Unit,
                    /** The parent brief (2.4): each child's session, day and week (null: no row). */
                    val onBrief: (() -> Unit)? = null,
                    /** Family (2.4.1): everyone on the phone, each with rename and "Read the brief" (null: no row). */
                    val onFamily: (() -> Unit)? = null)

/** Said once a day per kid when the home first shows (a pre-reader hears what the bubble says). */
private val greeted = HashSet<String>()

/**
 * The kid's home (2.2): ONE screen, the pup's world. The map scrolls through the Playground's pages (ui/kid/KidMap.kt),
 * the pup stands on the next stone with "Let's go!" (tap him, the bubble or the stone), the egg and the treat jar sit
 * in the garden as always, Pup's quest is the signpost he points at, and three big buttons stay at the bottom: Play
 * (Jam, Pup says, Clap along), Songs and My stickers. No tabs, no numbers, no cards. A tiny whistle top right is the
 * grown-ups' corner, behind the parent gate ([onGrownUps] asks it). While lessons sleep after today's goodbye
 * ([asleep]) the pup sleeps on his stone; Play, Songs and the stickers stay open.
 *
 * [from] is a page whose finale just played: the map opens at its end and rolls on to the next stone. [roadLine]
 * makes the pup say the big kids' road line once ([onRoadSaid] remembers it).
 */
@Composable
fun KidHome(
    model: KidMapModel, world: WorldState, name: String, avatar: Int, asleep: Boolean, quest: Quest,
    onStone: (Stone) -> Unit, onToy: (Toy) -> Unit, onSongs: () -> Unit, onStickers: () -> Unit, onQuestTask: (QuestTask) -> Unit,
    onGrownUps: () -> Unit, say: (String) -> Unit,
    modifier: Modifier = Modifier, from: String? = null, roadLine: Boolean = false, onRoadSaid: () -> Unit = {},
    onJar: (() -> Unit)? = null, onEgg: (() -> Unit)? = null,
    grownUps: GrownUpCorner? = null, onGrownUpsClosed: () -> Unit = {},
    /** A kid who finished Beginner: the offer of grown-up mode (2.1, through the gate), at the top of the map; null: none. */
    grownUpOffer: (@Composable () -> Unit)? = null,
    /** 2.5: sticking stickers on the map (decorate mode); null: the stickers are only shown. */
    decor: DecorActions? = null,
    /** A sticker picked in My stickers' tray: decorate mode opens holding it ([onDecorateStarted] clears it). */
    decorateWith: String? = null, onDecorateStarted: () -> Unit = {},
    /** Once after 2.5's update: the stickers the app had stuck are in the tray, and the pup says so. */
    homesLine: Boolean = false, onHomesSaid: () -> Unit = {},
    /** 2.7: the Play sheet's Games tile opens the arcade (ui/arcade); null: no tile. */
    onGames: (() -> Unit)? = null,
) {
    var playOpen by remember { mutableStateOf(false) }
    var questOpen by remember { mutableStateOf(false) }
    val decorate = remember { DecorateState() }
    // The pup's line: "Let's go!", the road line once, "This one first!" for a moment after a tap on a stone ahead.
    var nudge by remember { mutableStateOf<String?>(null) }
    var road by remember { mutableStateOf(roadLine) }
    var homes by remember { mutableStateOf(homesLine && !roadLine) }
    val line = when {
        nudge != null -> nudge!!
        asleep -> KidLines.ASLEEP_SHORT
        road -> KidLines.ROAD
        homes -> KidLines.HOMES
        else -> KidLines.LETS_GO
    }
    val sayNow by rememberUpdatedState(say)
    LaunchedEffect(homes) {
        if (!homes) return@LaunchedEffect
        delay(900); sayNow(KidLines.HOMES)
        delay(7000); homes = false; onHomesSaid()
    }
    LaunchedEffect(decorateWith) {
        if (decorateWith == null) return@LaunchedEffect
        decorate.start(held = decorateWith); onDecorateStarted(); sayNow(KidLines.PICK_SPOT)
    }
    if (decorate.active) BackHandler { decorate.stop() }
    val tray = remember(world.tray) { world.tray.mapNotNull(com.bigpapituna.sharpears.engine.world.Decal::byKey) }
    // Pages the kid has reached: any stone on them walked, open or next (or the road open: all of them).
    val reached = model.pages.indices.filter { i -> model.roadOpen || model.pages[i].stones.any { it.state != StoneState.LATER } }.toSet()
    val decorSay: (String) -> Unit = { l -> nudge = l; sayNow(l) }
    val decorActions = decor?.let { a -> DecorActions(a.place, a.unplace, decorSay) }
    LaunchedEffect(road) {
        if (!road) return@LaunchedEffect
        sayNow(KidLines.ROAD)
        delay(6500); road = false; onRoadSaid()
    }
    LaunchedEffect(nudge) { if (nudge != null) { delay(2600); nudge = null } }
    LaunchedEffect(Unit) {
        val key = name + java.time.LocalDate.now()
        if (!roadLine && !homes && greeted.add(key)) { delay(900); sayNow(if (asleep) KidLines.ASLEEP else line) }
    }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val still = reducedMotion()
    val bottomBar = 104.dp
    // The page under the map is the app's own (MainActivity's root draws the paper): a second full-screen paper here
    // painted the grain and the paws twice on every frame of the map.
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            val sceneW = minOf(maxWidth - 32.dp, 720.dp)
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomBar
            // Where the next stone is in the list: its item and how far down that item it sits.
            fun spotOf(lessonId: String?): Pair<Int, Dp>? {
                if (lessonId == null) return null
                model.pages.forEachIndexed { i, p ->
                    nextSpot(p, lessonId, sceneW)?.let { (_, y) -> return (1 + i) to (FLAG + y) }
                }
                val r = model.road.flatMap { it.stones }.indexOfFirst { it.lessonId == lessonId }
                return if (r >= 0) (1 + model.pages.size) to roadSpot(r, sceneW).second else null
            }
            LaunchedEffect(model.next, from) {
                val target = spotOf(model.next) ?: return@LaunchedEffect
                fun offset(at: Dp) = with(density) { (at - (viewport - bottom) * 0.5f).roundToPx() }.coerceAtLeast(0)
                val start = from?.let { f -> model.pages.firstOrNull { it.page.id == f }?.stones?.lastOrNull()?.lessonId }?.let(::spotOf)
                if (start != null && !still) {
                    list.scrollToItem(start.first, offset(start.second))
                    delay(700)
                    list.animateScrollToItem(target.first, offset(target.second))
                } else list.scrollToItem(target.first, offset(target.second))
                Log.d(TAG, "kid home: next ${model.next} (item ${target.first})" + (from?.let { ", rolled on from $it" } ?: ""))
            }
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = top + 8.dp, bottom = bottom + 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item(key = "hello") { Hello(name, avatar) }
                grownUpOffer?.let { offer -> item(key = "grown-up") { Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)) { offer() } } }
                model.pages.forEachIndexed { i, page ->
                    item(key = "page-${page.page.id}") {
                        PageScene(page, world, onStone = { s -> if (decorate.active) Unit else if (s.state == StoneState.LATER && !asleep) nudge = KidLines.THIS_ONE_FIRST.also(say) else onStone(s) },
                            onJar = onJar, onEgg = onEgg, asleep = asleep,
                            spots = decorActions?.let { a -> { sl, dc -> SpotView(sl, dc, decorate, a, reachable = i in reached) } }) { sizes ->
                            val w = maxWidth
                            nextSpot(page, model.next, w)?.let { (x, y) ->
                                // The trail runs rightward along the first row, back along the second, and so on.
                                val at = page.stones.indexOfFirst { it.lessonId == model.next }
                                PupSpot(x, y, sizes, line, asleep, quest.stars, pointable = !asleep && nudge == null && !road && !homes && !decorate.active,
                                    forward = (at / com.bigpapituna.sharpears.engine.world.Trail.PER_ROW) % 2 == 0, gate = i == model.pages.lastIndex, hop = decorate.landed,
                                    onGo = { model.stone(model.next!!)?.let(onStone) }, onSignpost = { say(KidLines.signpost(quest.stars)); questOpen = true })
                            }
                            if (i == model.pages.lastIndex) {
                                val g = com.bigpapituna.sharpears.ui.world.WorldAnchors.gate(page.place, page.stones.size)
                                Gate(model.roadOpen, sizes.gate, Modifier.pinAt(w * g.x, sceneHeight(w) * g.y + sizes.gate * 0.5f, ay = 1f))
                            }
                        }
                    }
                }
                if (!model.roadOpen) item(key = "road-ahead") { RoadAhead() }
                if (model.roadOpen && model.road.isNotEmpty()) item(key = "road") {
                    BigKidsRoad(model.road, asleep = asleep, world = world, onStone = { s -> if (decorate.active) Unit else if (s.state == StoneState.LATER && !asleep) nudge = KidLines.THIS_ONE_FIRST.also(say) else onStone(s) }) { sizes ->
                        val i = model.road.flatMap { it.stones }.indexOfFirst { it.lessonId == model.next }
                        if (i >= 0) {
                            val (x, y) = roadSpot(i, maxWidth)
                            PupSpot(x, y, sizes, line, asleep, quest.stars, pointable = !asleep && nudge == null && !road && !decorate.active, forward = roadForward(i, maxWidth), hop = decorate.landed,
                                onGo = { model.stone(model.next!!)?.let(onStone) }, onSignpost = { say(KidLines.signpost(quest.stars)); questOpen = true })
                        }
                    }
                }
            }
        }
        // A soft fade under the status bar, so the scrolling map does not run into the clock.
        val bar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bg = MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxWidth().height(bar + 20.dp).background(Brush.verticalGradient(0f to bg, bar / (bar + 20.dp) to bg.copy(alpha = 0.94f), 1f to bg.copy(alpha = 0f))))
        Whistle(onClick = onGrownUps, dot = com.bigpapituna.sharpears.analytics.Tester.switchOn.value, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 6.dp, end = 10.dp))
        if (decorate.active && decorActions != null) DecorTray(tray, decorate, decorActions, onDone = { decorate.stop() }, modifier = Modifier.align(Alignment.BottomCenter))
        else KidButtons(onPlay = { playOpen = true }, onSongs = onSongs, onStickers = onStickers, height = bottomBar,
            modifier = Modifier.align(Alignment.BottomCenter))
        DragGhost(decorate, tray, placed = { k -> com.bigpapituna.sharpears.engine.world.Decal.byKey(k) }, origin = origin)
    }
    if (playOpen) KidPlaySheet(onToy = { t -> playOpen = false; onToy(t) }, onDismiss = { playOpen = false },
        onGames = onGames?.let { open -> { playOpen = false; open() } })
    if (questOpen) QuestSheet(quest, onTask = { t -> questOpen = false; onQuestTask(t) }, onDismiss = { questOpen = false })
    grownUps?.let { GrownUpsSheet(it, onDismiss = onGrownUpsClosed) }
}

/** The page flag's height and the gap under it: how far into a page's item its scene starts. */
private val FLAG = 48.dp

/** "Hi, Mia!" with their pup, at the top of the map. */
@Composable
private fun Hello(name: String, avatar: Int) {
    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp).padding(end = 56.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PupAvatar(avatar, size = 52.dp)
        Text("Hi, $name!", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
}

/**
 * The pup on the next stone: standing on it like a token on a board, his "Let's go!" bubble beside him the way the
 * trail goes on ([forward]: rightward from here; over the stones still to come, never the walked ones and their stars),
 * the signpost (Pup's quest) behind him. Every so often he turns and points at the signpost and the bubble says how
 * many stars are still to find; then back. Asleep, he curls up on the stone.
 */
@Composable
internal fun BoxWithConstraintsScope.PupSpot(x: Dp, y: Dp, sizes: MapSizes, line: String, asleep: Boolean, found: Int, pointable: Boolean,
                                             forward: Boolean, onGo: () -> Unit, onSignpost: () -> Unit, gate: Boolean = false, hop: Int = 0) {
    val w = maxWidth
    val p = sizes.pup
    val reach = sizes.next * 0.55f
    // The bubble goes the way the trail does, unless the scene ends too soon on that side.
    val right = if ((if (forward) w - x else x) - reach >= 150.dp) forward else !forward
    var pointing by remember { mutableStateOf(false) }
    // At bedtime he yawns on his stone first, then curls up to sleep.
    var yawned by remember { mutableStateOf(false) }
    LaunchedEffect(asleep) { yawned = false; if (asleep) { delay(2800); yawned = true } }
    LaunchedEffect(pointable, found) {
        pointing = false
        if (!pointable || found >= 3) return@LaunchedEffect
        while (true) { delay(7000); pointing = true; delay(3400); pointing = false }
    }
    val shown = if (pointing) KidLines.signpostShort(found) else line
    // Dressed up (My stickers): he waits on his stone in his outfit, which only the sitting base pose wears (the map was
    // the one home where a kid never saw the pup they dressed; the grown-ups' home shows him the same way).
    val dressed = com.bigpapituna.sharpears.ui.world.LocalPupOutfit.current != com.bigpapituna.sharpears.engine.world.Outfit.NONE
    val pose = when { asleep && yawned -> Pose.SLEEPING; asleep -> Pose.GOODNIGHT; pointing -> Pose.POINTING; dressed -> Pose.WEAR_BASE; else -> Pose.GET_READY }
    // GET_READY points to the right of the screen, POINTING to the left: mirrored when the bubble is on the left.
    val mirror = !asleep && !right
    val pup = rememberPupController(idle = !pointing && !asleep)
    // A sticker stuck on the map (decorate mode): he dances.
    LaunchedEffect(hop) { if (hop > 0) pup.react(com.bigpapituna.sharpears.engine.world.PupReaction.DANCE) }
    // A little behind the stone's middle, so the stone shows in front of his feet.
    val px = if (right) x - sizes.stone * 0.18f else x + sizes.stone * 0.18f
    Box(Modifier.pinAt(px, y + sizes.stone * 0.22f, ay = 1f).size(p)
        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Play the next game", onClick = onGo)
        .semantics { contentDescription = if (asleep) "The pup, asleep" else "The pup" }   // TalkBack read a nameless button
        .graphicsLayer { if (mirror) scaleX = -1f }) {
        Pup(pose, size = p, controller = pup, idle = !pointing && !asleep, bounce = BounceSize.SMALL)
    }
    // The signpost stands behind him; where the scene ends there (or the gate stands), low in front, under his bubble.
    val behind = if (right) x - p * 0.68f else x + p * 0.68f
    val fits = behind in p * 0.32f..(if (gate) w * 0.86f else w - p * 0.32f)
    if (fits) Signpost(found, onSignpost, Modifier.pinAt(behind, y + sizes.stone * 0.3f, ay = 1f), width = p * 0.56f)
    val room = (if (right) w - x else x) - reach - 6.dp
    KidBubble(shown, tailLeft = right, style = sizes.bubble, onClick = onGo,
        // Level with the stone: over the stones still to come in its row, clear of the row above and its stars.
        modifier = Modifier.pinAt(if (right) x + reach else x - reach, y - sizes.stone * 0.15f, ax = if (right) 0f else 1f, ay = 0.5f)
            .widthIn(max = minOf(room, 360.dp)))
    if (!fits) Signpost(found, onSignpost, Modifier.pinAt(if (right) x + reach + p * 0.35f else x - reach - p * 0.35f, y + sizes.stone, ay = 1f), width = p * 0.5f)
}

/** The pup's speech bubble on the map: big words on gold, its tail toward him; tapping it starts the game. */
@Composable
private fun KidBubble(text: String, tailLeft: Boolean, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shape = remember(tailLeft, density) {
        val tail = with(density) { 10.dp.toPx() }; val r = with(density) { 20.dp.toPx() }
        GenericShape { s, _ ->
            if (tailLeft) {
                addRoundRect(RoundRect(tail, 0f, s.width, s.height, CornerRadius(r)))
                val y = s.height * 0.62f
                moveTo(tail + 1, y - tail); lineTo(0f, y + tail * 0.5f); lineTo(tail + 1, y + tail * 0.4f); close()
            } else {
                addRoundRect(RoundRect(0f, 0f, s.width - tail, s.height, CornerRadius(r)))
                val y = s.height * 0.62f
                moveTo(s.width - tail - 1, y - tail); lineTo(s.width, y + tail * 0.5f); lineTo(s.width - tail - 1, y + tail * 0.4f); close()
            }
        }
    }
    Text(text, style = style, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (tailLeft) 22.dp else 14.dp, end = if (tailLeft) 14.dp else 22.dp, top = 10.dp, bottom = 11.dp))
}

/**
 * The grown-ups' corner: the pup's silver whistle, small and quiet in the top corner (kids leave it alone; it asks the
 * parent gate). [dot]: a small accent dot while "Help Roy test" records this phone (analytics/Tester.kt).
 */
@Composable
private fun Whistle(onClick: () -> Unit, modifier: Modifier = Modifier, dot: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Box(modifier.size(48.dp).clip(CircleShape).background(c.surfaceContainerHigh.copy(alpha = 0.85f))   // 48 dp: the touch minimum
        .clickable(role = Role.Button, onClickLabel = "Grown-ups", onClick = onClick).semantics { contentDescription = "Grown-ups" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26.dp)) {
            val w = size.width; val line = w * 0.07f
            val ink = c.onSurfaceVariant
            val metal = Color(0xFFC9CED6)
            // The cord ring, the round chamber and the mouthpiece, like the one round the pup's neck.
            drawCircle(ink, w * 0.11f, Offset(w * 0.24f, w * 0.26f), style = Stroke(line))
            val body = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.40f, w * 0.60f), w * 0.30f))
                addRoundRect(RoundRect(w * 0.40f, w * 0.30f, w * 0.98f, w * 0.52f, CornerRadius(w * 0.06f)))
            }
            drawPath(body, metal)
            drawPath(body, ink, style = Stroke(line))
            drawCircle(ink, w * 0.08f, Offset(w * 0.40f, w * 0.60f))
            drawLine(ink, Offset(w * 0.30f, w * 0.34f), Offset(w * 0.36f, w * 0.42f), line)
        }
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(9.dp).clip(CircleShape).background(c.primary)
            .semantics { contentDescription = "Help Roy test is on" })
    }
}

/** The three big buttons at the bottom: Play, Songs, My stickers, each the pup doing it. */
@Composable
private fun KidButtons(onPlay: () -> Unit, onSongs: () -> Unit, onStickers: () -> Unit, height: Dp, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth().background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.22f to bg.copy(alpha = 0.92f), 1f to bg))
        .navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().height(height).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidButton("Play", Pose.DANCING, onPlay, Modifier.weight(1f))
            KidButton("Songs", Pose.PRACTICING, onSongs, Modifier.weight(1f))
            KidButton("My stickers", Pose.STICKER, onStickers, Modifier.weight(1f))
        }
    }
}

/** A big chunky button: the pup's picture and one word, on a lip it sinks into when pressed. */
@Composable
internal fun KidButton(label: String, pose: Pose, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lip = 5.dp
    val face = c.surfaceContainerHigh
    val edge = lerp(c.surfaceContainerHighest, Color.Black, 0.22f)
    BoxWithConstraints(modifier.fillMaxSize().clickable(source, indication = null, role = Role.Button, onClick = onClick).semantics { contentDescription = label }) {
        Box(Modifier.fillMaxSize().padding(top = lip).background(edge, RoundedCornerShape(22.dp)))
        val face0 = Modifier.fillMaxSize().offset { IntOffset(0, if (pressed) lip.roundToPx() else 0) }.padding(bottom = lip)
            .background(face, RoundedCornerShape(22.dp))
        // Side by side where there is room (the unfolded Fold); the pup over the word on a phone.
        if (maxWidth >= 190.dp) Row(face0.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Image(painterResource(pose.res), contentDescription = null, modifier = Modifier.size(52.dp))
            Text(label, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp), maxLines = 1,
                color = c.onSurface)
        } else Column(face0.padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Image(painterResource(pose.res), contentDescription = null, modifier = Modifier.size(40.dp))
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp), maxLines = 1,
                color = c.onSurface)
        }
    }
}

/** Pup's quest, opened from the signpost: the three tasks with their stars, each a way to go and do it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestSheet(quest: Quest, onTask: (QuestTask) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QuestCardContent(quest, kid = true, onTask = onTask)
        }
    }
}

/**
 * The grown-ups' corner, behind the parent gate: Settings, Microphone setup and who is playing (tap someone and the app
 * opens as them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrownUpsSheet(corner: GrownUpCorner, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow) {
        // The whole corner is hidden in tester replays (names, the gate's world); scrolls on a short screen.
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).navigationBarsPadding()
            .verticalScroll(androidx.compose.foundation.rememberScrollState()).testerMask()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("For grown-ups", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text("Who's playing?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            // Up to eight people: the row scrolls (on the cover screen a fifth one was squeezed to nothing).
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                corner.profiles.forEach { p ->
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = "Play as ${p.name}") {
                        Log.d(TAG, "kid home: grown-ups switched to ${p.id}"); corner.onSwitch(p.id)
                    }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PupAvatar(p.avatar, size = 56.dp, selected = p.id == corner.currentId)
                        Text(p.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
            corner.onBrief?.let { CornerRow("Parent brief", R.drawable.ic_book, it) }
            corner.onFamily?.let { CornerRow("Family", R.drawable.ic_person, it) }
            CornerRow("Settings", R.drawable.ic_settings, corner.onSettings)
            CornerRow("Microphone setup", R.drawable.ic_mic, corner.onMicSetup)
            // Testing (analytics/Tester.kt): "Help Roy test", the diary, "Something felt off"; the gate was passed to get here.
            com.bigpapituna.sharpears.analytics.TesterSettings(Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CornerRow(title: String, icon: Int, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHigh)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

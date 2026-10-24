package com.bigpapituna.sharpears.ui.world

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.engine.world.Drop
import com.bigpapituna.sharpears.engine.world.DropHistory
import com.bigpapituna.sharpears.engine.world.DropSource
import com.bigpapituna.sharpears.engine.world.Medal
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.PupReaction
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.engine.world.RecordUp
import com.bigpapituna.sharpears.engine.world.Reward
import com.bigpapituna.sharpears.engine.world.Stickers
import com.bigpapituna.sharpears.engine.world.WearItem
import com.bigpapituna.sharpears.engine.world.WorldState
import com.bigpapituna.sharpears.ui.Celebration
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.Stat
import com.bigpapituna.sharpears.ui.motion.Juice
import com.bigpapituna.sharpears.ui.motion.wakesPup
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.pup.rememberPupController

/**
 * A demo world with [stickers] stickers (catalogue order, so the garden fills first; two doubles), the egg waiting,
 * [notes] in the jar and a few records. For the debug route and screenshots.
 */
fun demoWorld(stickers: Int, notes: Int = 120, egg: Boolean = true, songIds: List<String> = emptyList(), outfit: Outfit = Outfit.NONE): WorldState {
    val order = Stickers.ALL.take(stickers.coerceIn(0, Stickers.ALL.size))
    val owned = order.associate { it.id to 1 }.toMutableMap()
    order.getOrNull(0)?.let { owned[it.id] = 3 }
    order.getOrNull(3)?.let { owned[it.id] = 2 }
    val records = songIds.zip(listOf(97, 88, 72, 64, 99, 81)).toMap()
    return WorldState(seed = 7L, history = DropHistory(owned, sinceRare = 1, rolls = stickers + 3), lessons = 5, egg = egg, jar = notes,
        treats = 2, records = records, outfit = outfit, latest = order.lastOrNull()?.id)
}

/** A reward for the reveal demo: one drop of [rarity], plus an egg's when [egg]; new unless [double]. */
fun demoReward(rarity: Rarity, egg: Boolean, double: Boolean = false, unlock: List<WearItem>? = null, level: Boolean = false): Reward {
    val first = Stickers.ALL.first { it.rarity == rarity && it.place == Place.POND }
    val drops = buildList {
        add(Drop(first, DropSource.LESSON, isNew = !double))
        if (egg) add(Drop(Stickers.ALL.first { it.rarity == Rarity.SHINY && it.place == Place.CONCERT }, DropSource.EGG, isNew = true))
    }
    return Reward(drops, eggLaid = false, eggHatched = egg, houseBefore = 1, houseAfter = if (egg) 2 else 1,
        unlocked = unlock ?: if (egg) listOf(WearItem.SUNGLASSES) else emptyList(), record = if (egg) RecordUp("twinkle", Medal.SILVER, Medal.GOLD) else null,
        level = if (level) com.bigpapituna.sharpears.engine.world.LevelStickers.byLesson("pond-3") else null)
}

/**
 * Debug only: `--es screen world --es part <part>` shows one piece of the world team's work on its own, in a demo
 * world (a "worlddemo" profile's file, never a real one). Parts: album, world (--es place garden|pond|concert, or all),
 * map, jar, egg, dress, reveal (--es rarity common|rare|shiny, --ez egg true), shelf, idle, onemore. Options:
 * --ei stickers n (default 12), --ei notes n, --ei stage 1|2|3 (sets the sticker count for that dog house stage),
 * --es wear tophat,sunglasses, --es theme light|dark.
 */
@Composable
fun WorldDemo(intent: Intent, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val dark = when (intent.getStringExtra("theme")) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val part = intent.getStringExtra("part") ?: "world"
    val stage = intent.getIntExtra("stage", 0)
    val stickers = when (stage) { 1 -> 3; 2 -> 9; 3 -> 24; else -> intent.getIntExtra("stickers", 12) }
    val songIds = remember { runCatching { SongRepository(context).all().map { it.id }.take(6) }.getOrDefault(emptyList()) }
    val songs = remember { runCatching { SongRepository(context).all() }.getOrDefault(emptyList()) }
    val wear = intent.getStringExtra("wear")?.split(",")?.mapNotNull { WearItem.byId(it.trim()) }?.toSet().orEmpty()
    val world = remember {
        WorldStore(context, "worlddemo").also {
            it.debugSet(demoWorld(stickers, intent.getIntExtra("notes", 120), egg = intent.getBooleanExtra("egg", true), songIds = songIds, outfit = Outfit(wear)))
        }
    }
    WorldSounds.init(context)
    Juice.world = world
    SharpEarsTheme(dark = dark) {
        CompositionLocalProvider(LocalPupOutfit provides world.state.outfit, LocalWorld provides world) {
            Surface(Modifier.fillMaxSize().paper().wakesPup(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    when (part) {
                        "reveal" -> DropRevealOverlay(demoReward(
                            Rarity.entries.firstOrNull { it.name.equals(intent.getStringExtra("rarity"), true) } ?: Rarity.COMMON,
                            egg = intent.getBooleanExtra("eggdrop", false), double = intent.getBooleanExtra("double", false),
                            unlock = intent.getStringExtra("unlock")?.split(",")?.mapNotNull { WearItem.byId(it.trim()) },
                            level = intent.getBooleanExtra("level", false)), onDone = onClose)
                        "onemore" -> {
                            // As the app will: the lesson's reward applied first, then the preview of the lesson after it.
                            val reward = remember { world.lessonComplete("demo") }
                            Celebration("Lesson done!", "Steps up and down with the pup.", listOf(Stat("3", "Steps"), Stat("2:10", "Time")),
                                primary = "Back to the path", onPrimary = onClose, pose = Pose.LEVEL_UP, xpGained = 100,
                                reward = { DropReveal(reward, cardWidth = 120.dp) },
                                oneMore = { OneMoreButton(world.preview(), onClick = onClose, title = "Hot Cross Buns") })
                        }
                        // The whole world screen scrolls by itself, so it is not put in the demo's scrolling column.
                        "screen" -> WorldScreen(world, songs, onBack = onClose)
                        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (part) {
                                "album" -> AlbumView(world.state)
                                "world" -> {
                                    val place = Place.byId(intent.getStringExtra("place"))
                                    if (place != null) PlaceScene(place, world.state) else WorldMap(world.state)
                                    if (place == null || place == Place.GARDEN) Text(houseLine(world.state.unique), style = MaterialTheme.typography.bodyMedium)
                                }
                                "map" -> WorldMap(world.state)
                                // 2.2: the twelve stops of every scene's trail, numbered, and the last scene's gate (the kid map's layout).
                                "trail" -> WorldMap(world.state, stop = { _, i ->
                                    Box(Modifier.size(34.dp).background(androidx.compose.ui.graphics.Color(0xFFFFF7E8), androidx.compose.foundation.shape.CircleShape)
                                        .border(2.dp, WorldInk, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = WorldInk)
                                    }
                                })
                                "jar" -> TreatJarPanel(world)
                                "egg" -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                                    EggView(EggLook.CLOSED, Modifier.size(90.dp, 116.dp))
                                    EggView(EggLook.CRACKING, Modifier.size(90.dp, 116.dp), crack = 0.8f)
                                    EggView(EggLook.HATCHED, Modifier.size(90.dp, 116.dp), open = 0.7f)
                                }
                                "dress" -> DressUp(world)
                                // 2.5: every item on its own pup (--es slot head|face|body|back), for checking the drawing.
                                "wardrobe" -> {
                                    val slot = com.bigpapituna.sharpears.engine.world.WearSlot.entries.firstOrNull { it.name.equals(intent.getStringExtra("slot"), true) }
                                    val px = intent.getIntExtra("size", 150).dp
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        WearItem.entries.filter { slot == null || it.slot == slot }.forEach { item ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Pup(Pose.WEAR_BASE, size = px, idle = false, outfit = Outfit(setOf(item)), bounce = com.bigpapituna.sharpears.ui.motion.BounceSize.NONE)
                                                Text(item.label, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                "shelf" -> RecordsShelf(world.state.records, songs)
                                "idle" -> IdleDemo()
                                "card" -> WorldCard(world, onOpen = {})
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A pup left alone (idle acts at 10 s, asleep at a minute) and buttons for every reaction. */
@Composable
private fun IdleDemo() {
    val pup = rememberPupController()
    var n by remember { mutableIntStateOf(0) }
    Text("Leave him for ten seconds, then a minute.", style = MaterialTheme.typography.titleMedium)
    Box(Modifier.padding(top = 30.dp)) { Pup(Pose.WEAR_BASE, size = 220.dp, controller = pup, onTap = { n++ }) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PupReaction.entries.forEach { r -> OutlinedButton(onClick = { pup.react(r) }) { Text(r.name.lowercase().replace('_', ' ')) } }
        OutlinedButton(onClick = { Juice.onStrike(48 + (n++ % 4) * 12) }) { Text("note") }
    }
}

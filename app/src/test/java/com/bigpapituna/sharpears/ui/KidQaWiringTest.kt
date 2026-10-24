package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The kid side's QA 2.8.3 fixes that live in screen wiring, read from the sources (the rules have their own JVM tests). */
class KidQaWiringTest {
    private val main = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(main, path).readText().replace("\r\n", "\n")

    /** Defect 8: a recreate on an arcade round's finish reopened a fresh, harder round instead of the stars. */
    @Test fun aFinishedArcadeRoundReopensAsTheGames() {
        val activity = src("MainActivity.kt")
        assertTrue(activity.contains("arcadeEnded = s.seed; shownRoute = routeOf(s.back)"))
        assertTrue(activity.contains("shownRoute = if (shown is Screen.ArcadeRound && shown.seed == arcadeEnded) routeOf(shown.back) else routeOf(shown)"))
        // Defect 12: a round with no key played earns no sparkles, quest star or brief line.
        assertTrue(activity.contains("if (r.played) {"))
        assertTrue(src("ui/arcade/ArcadeGameScreen.kt").contains("ArcadeTime.coveredMs(from[0], now, keys)"))
    }

    /** Defect 9: the cheer bar's and the finish's buttons come up where the keys were; a tap in their first moment is ignored. */
    @Test fun finishButtonsIgnoreTapsAsTheyAppear() {
        val celebrate = src("ui/Celebrate.kt")
        assertTrue(celebrate.contains("ChunkyButton(onClick = settledTap(title, line, action = onButton)"))
        assertTrue(celebrate.contains("ChunkyButton(onClick = primaryTap"))
        assertTrue(celebrate.contains("TextButton(onClick = secondaryTap"))
    }

    /** Defect 11: a retry of a concert keeps the song picked the try before. */
    @Test fun aConcertRetryKeepsItsSong() {
        assertTrue(src("ui/LessonScreen.kt").contains("LocalStageMemo provides stageMemo"))
        val concert = src("ui/playground/ConcertStage.kt")
        assertTrue(concert.contains("memo?.get(memoKey) as? ConcertSong"))
        assertTrue(concert.contains("chosen = it; memo?.set(memoKey, it)"))
    }

    /** Defect 4: a touch during a stage's demo reaches the engine's early rule; the mic does not. */
    @Test fun touchesDuringADemoGoThroughTheEarlyRule() {
        val kit = src("ui/playground/PlaygroundKit.kt")
        assertTrue(kit.contains("st.onFeed = { s -> heard(s); answer(s, touch = true) }"))
        assertTrue(kit.contains("val early = touch && run.rounds.earlyTouchMayCount(st.demoShown)"))
        assertTrue(kit.contains("run.found(a.key, now(), early)"))
    }

    /** Polish: the sticker tray only under the album; no mode words for a kid who cannot read yet. */
    @Test fun polishNits() {
        assertTrue(src("ui/kid/KidStickers.kt").contains("pager.currentPage == StickerPage.ALBUM.ordinal) StickerTray("))
        assertTrue(src("ui/TransportBar.kt").contains("if (!(s.kid && !com.bigpapituna.sharpears.ui.family.reader())) {"))
        assertTrue(src("ui/RunRecap.kt").contains("lead = \"Again\""))
    }
}

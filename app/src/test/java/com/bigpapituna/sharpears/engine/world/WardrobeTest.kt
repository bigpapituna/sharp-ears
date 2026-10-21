package com.bigpapituna.sharpears.engine.world

import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** 2.5: the wardrobe's catalogue, the practice ledger and the earning curve. */
class WardrobeTest {
    private val monday = LocalDate.of(2026, 1, 5)

    @Test fun theCatalogueHasEnoughToWearInFourSlotsAndFullCostumes() {
        val items = WearItem.entries
        assertTrue("${items.size} items", items.size >= 36)
        assertEquals(WearSlot.entries.toSet(), items.map { it.slot }.toSet())
        assertTrue("every slot has several", WearSlot.entries.all { s -> items.count { it.slot == s } >= 7 })
        assertTrue("eight costumes or more in the body slot", items.count { it.slot == WearSlot.BODY } >= 8 + 2)
        assertEquals("ids are unique", items.size, items.map { it.id }.toSet().size)
        // The 2.1 six are still the first six on the practice track.
        assertEquals(listOf(WearItem.BOW_TIE, WearItem.SUNGLASSES, WearItem.PARTY_HAT, WearItem.SCARF, WearItem.HEADPHONES, WearItem.TOP_HAT),
            (1..6).map { n -> items.single { (it.earn as? Earn.Practice)?.n == n } })
        // Every rarity is used: rare on milestones, shiny on big ones, common along the track.
        for (i in items) when (i.earn) {
            is Earn.Practice -> assertEquals(i.id, Rarity.COMMON, i.rarity)
            is Earn.Pages, Earn.Road -> assertEquals(i.id, Rarity.SHINY, i.rarity)
            else -> assertTrue(i.id, i.rarity.special)
        }
        // A costume's pieces share their earn and differ in slot, so the whole costume goes on at once.
        Wardrobe.UNITS.forEach { set -> assertEquals(set.map { it.id }.toString(), set.size, set.map { it.slot }.toSet().size) }
        assertTrue(Wardrobe.UNITS.count { it.size > 1 } >= 8)
        // The practice track has no gaps, and each step needs more sparkles than the last.
        val track = items.mapNotNull { (it.earn as? Earn.Practice)?.n }.toSet()
        assertEquals((1..Wardrobe.practiceCount()).toSet(), track)
        assertTrue(Wardrobe.PRACTICE_AT.size >= Wardrobe.practiceCount())
        assertTrue(Wardrobe.PRACTICE_AT.zipWithNext().all { (a, b) -> b > a })
        // Finale items name real pages; the road item asks for all of them.
        assertEquals(PLAYGROUND_PAGES.map { it.id }.toSet(), Wardrobe.ROAD_PAGES)
        items.mapNotNull { it.earn as? Earn.Finale }.forEach { f -> assertTrue(f.pageId, PLAYGROUND_PAGES.any { it.id == f.pageId }) }
    }

    @Test fun copyKeepsTheKidRules() {
        val banned = Regex("[0-9—–·#&/%]| - |\\b(wrong|mistakes?|fail\\w*|buy|lost|lose)\\b", RegexOption.IGNORE_CASE)
        val lines = WearItem.entries.flatMap { listOf(it.line, it.nickname, it.label, Wardrobe.how(it)) } +
            listOf(Wardrobe.revealLine(listOf(WearItem.BEE)), Wardrobe.revealLine(listOf(WearItem.BEE, WearItem.ANTENNAE))) +
            (0..10).map { NextOutfit(listOf(WearItem.BOOTS), it / 10f).words }
        for (l in lines) {
            assertFalse("\"$l\"", banned.containsMatchIn(l))
            assertTrue("\"$l\"", l.first().isUpperCase())
        }
        WearItem.entries.forEach { assertTrue(it.line, it.line.last() in ".!?" && it.line.length <= 40) }
    }

    @Test fun theLedgerCountsEachSecondOnceAndKeepsWhatAPrunedLogGave() {
        val d1 = monday; val d2 = monday.plusDays(1); val d4 = monday.plusDays(3)
        var l = Ledger().sync(mapOf(d1 to 600), emptySet(), emptySet())
        assertEquals(600L, l.seconds); assertEquals(1, l.days); assertEquals(1, l.streak)
        // The same log again adds nothing; a day that grew adds only what it grew by.
        assertEquals(l, l.sync(mapOf(d1 to 600), emptySet(), emptySet()))
        l = l.sync(mapOf(d1 to 900, d2 to 30), emptySet(), emptySet())
        assertEquals(930L, l.seconds); assertEquals("half a minute is not a day", 1, l.days)
        l = l.sync(mapOf(d2 to 120), emptySet(), emptySet())
        assertEquals(2, l.days); assertEquals(2, l.streak)
        // A gap breaks the run; the best is kept.
        l = l.sync(mapOf(d4 to 300), setOf("playground-1"), setOf("garden"))
        assertEquals(1, l.streak); assertEquals(2, l.best); assertEquals(3, l.days)
        // The log pruned (60 days): nothing is taken back, and stars or a page forgotten stay counted.
        val pruned = l.sync(emptyMap(), emptySet(), emptySet())
        assertEquals(l.sparkles, pruned.sparkles)
        assertEquals(setOf("playground-1"), pruned.threeStars)
        assertEquals(0, l.streakOn(d4.plusDays(2)))
        assertEquals(1, l.streakOn(d4.plusDays(1)))
        // Sparkles: 22 minutes, 3 days, a three-star lesson, a page.
        assertEquals((900 + 120 + 300) / 60 + 3 * Sparkles.PER_DAY + Sparkles.PER_THREE_STARS + Sparkles.PER_FINALE, l.sparkles)
        // Songs: every one counts toward the milestones, five a day toward sparkles.
        var s = Ledger()
        repeat(8) { s = s.song(d1) }
        s = s.song(d2)
        assertEquals(9, s.songs); assertEquals(6, s.songSparkles)
    }

    @Test fun onePracticeItemADayMilestonesAtOnceAndNothingIsEverLost() {
        val l = Ledger(seconds = 60L * 400, days = 40, finales = setOf("garden"), threeStars = (1..10).map { "x$it" }.toSet())
        val due = Wardrobe.due(l, emptySet(), monday, practiceDay = null)
        assertEquals("one practice item, then the milestones", WearItem.BOW_TIE, due.first())
        assertEquals(1, due.count { it.earn is Earn.Practice })
        assertTrue(WearItem.FLOWER_CROWN in due && WearItem.KNIGHT in due && WearItem.KNIGHT_HELMET in due && WearItem.DOCTOR in due)
        assertTrue("nothing more today", Wardrobe.due(l, due.toSet(), monday, practiceDay = monday).isEmpty())
        assertEquals(WearItem.SUNGLASSES, Wardrobe.due(l, due.toSet(), monday.plusDays(1), practiceDay = monday).single())
        // An import that lowers the ledger closes nothing: open items stay open.
        val s = World.syncLedger(WorldState(seed = 1L, wardrobe = due.toSet()), emptyMap(), emptySet(), emptySet(), monday)
        assertEquals(due.toSet(), s.wardrobe)
        // The ring: how far into the next practice item's gap.
        val half = Ledger(seconds = 60L * (Wardrobe.PRACTICE_AT[0] + (Wardrobe.PRACTICE_AT[1] - Wardrobe.PRACTICE_AT[0]) / 2))
        val next = Wardrobe.next(half, setOf(WearItem.BOW_TIE))!!
        assertEquals(listOf(WearItem.SUNGLASSES), next.items)
        assertEquals(0.5f, next.share, 0.05f)
        assertEquals("On the way!", next.words)
        assertNull(Wardrobe.next(Ledger(), WearItem.entries.toSet()))
    }

    @Test fun anUnlockWaitsForTheNextFinishScreen() {
        var s = WorldState(seed = 3L)
        s = World.syncLedger(s, mapOf(monday to 15 * 60), emptySet(), emptySet(), monday)
        assertEquals(listOf(WearItem.BOW_TIE), s.fresh)
        val o = World.lessonComplete(s, Place.GARDEN, "playground-1")
        assertEquals(listOf(WearItem.BOW_TIE), o.reward.unlocked)
        assertTrue(o.state.fresh.isEmpty())
        assertTrue(WearItem.BOW_TIE in o.state.wardrobe)
        assertTrue("the next finish has nothing new", World.lessonComplete(o.state, Place.GARDEN, "playground-2").reward.unlocked.isEmpty())
        // A song counts toward the ledger by itself.
        assertEquals(1, World.songComplete(o.state, "twinkle", null, monday).state.ledger.songs)
    }

    /**
     * The typical kid Roy described: five ten-minute sessions a week (Monday to Friday), two lessons and a song each
     * session, half the lessons on three stars, walking the Playground's pages in order. Prints the unlock timeline.
     */
    @Test fun aTypicalKidEarnsByPractisingNotTooFastNotTooSlow() {
        val lessons = PLAYGROUND_PAGES.flatMap { it.lessonIds }
        var s = WorldState(seed = 11L)
        val practice = HashMap<LocalDate, Int>()
        val stars = HashSet<String>()
        val walked = HashSet<String>()
        var at = 0
        var session = 0
        val opened = ArrayList<Triple<Int, LocalDate, List<WearItem>>>()
        var firstFinale: Int? = null
        val out = StringBuilder("session  day         minutes  sparkles  opened\n")
        var day = monday
        while (session < 130) {
            if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) { day = day.plusDays(1); continue }
            session++
            practice[day] = 600
            val before = s.wardrobe
            repeat(2) {
                if (at < lessons.size) {
                    val id = lessons[at]
                    if (at % 2 == 0) stars += id
                    PLAYGROUND_PAGES.firstOrNull { it.lastLessonId == id }?.let { walked += it.id; if (firstFinale == null) firstFinale = session }
                    s = World.syncLedger(s, practice, stars, walked, day)
                    s = World.lessonComplete(s, null, id).state
                    at++
                }
            }
            s = World.songComplete(World.syncLedger(s, practice, stars, walked, day), "twinkle", null, day).state
            val new = WearItem.entries.filter { it in s.wardrobe && it !in before }
            if (new.isNotEmpty()) {
                opened += Triple(session, day, new)
                out.append("%7d  %s  %7d  %8d  %s\n".format(java.util.Locale.ROOT, session, day, s.ledger.minutes, s.ledger.sparkles,
                    new.joinToString { "${it.label} (${it.rarity.label.lowercase()})" }))
            }
            day = day.plusDays(1)
        }
        val months = (1..6).map { m -> opened.filter { it.second.isBefore(monday.plusMonths(m.toLong())) }.sumOf { it.third.size } }
        out.append("items open by the end of months 1 to 6: $months of ${WearItem.entries.size}\n")
        println(out)
        java.io.File("build/wardrobe-timeline.txt").writeText(out.toString())

        fun sessionOf(item: WearItem) = opened.firstOrNull { item in it.third }?.first
        fun practiceN(n: Int) = WearItem.entries.first { (it.earn as? Earn.Practice)?.n == n }
        assertTrue("item 1 by session 2", sessionOf(practiceN(1))!! <= 2)
        for (n in 2..4) assertTrue("item $n within two weeks", sessionOf(practiceN(n))!! <= 10)
        // The first rare comes on the first finale, not before.
        val firstRare = opened.first { o -> o.third.any { it.rarity.special } }
        assertNotNull(firstFinale)
        assertEquals(firstFinale, firstRare.first)
        assertTrue(WearItem.FLOWER_CROWN in firstRare.third)
        // Never more than one practice item a session; milestones may come with it.
        opened.forEach { o -> assertTrue("session ${o.first}: ${o.third}", o.third.filter { it.earn is Earn.Practice }.map { it.earn }.toSet().size <= 1) }
        // A month in: a handful from practice, more than the six of 2.1 with the finales; the wardrobe lasts months.
        val practiceMonth1 = opened.filter { it.second.isBefore(monday.plusMonths(1)) }.flatMap { it.third }.filter { it.earn is Earn.Practice }.map { it.earn }.toSet().size
        assertTrue("practice items in month 1: $practiceMonth1", practiceMonth1 in 5..8)
        assertTrue("month 3 still has more to earn: $months", months[2] < WearItem.entries.size)
        assertTrue("months two and three keep giving: $months", months[1] > months[0] && months[2] > months[1])
    }
}

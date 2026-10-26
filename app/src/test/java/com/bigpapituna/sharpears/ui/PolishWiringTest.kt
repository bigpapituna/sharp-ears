package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.engine.MySongs
import com.bigpapituna.sharpears.engine.nav.TodayCard
import com.bigpapituna.sharpears.engine.DebugRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.14 polish (the persona walks of 2.13.0): the copy fixes, the calmer grown-up finish, the picture finish for children who
 * cannot read, the day-1 outfit and the one-shot debug extras, read from the sources and the pure rules.
 */
class PolishWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(root, path).readText()

    // ---- Copy ----

    @Test fun todayGreetsByNameOrSaysHiThere() {
        fun g(state: TodayCard.State = TodayCard.State.PLAN, fresh: Boolean = false, hour: Int = 19, back: Boolean = false, name: String? = null) =
            greeting(state, null, fresh, hour, back, name)
        assertEquals("Good evening, Dana. A short session before the day ends?", g(name = "Dana"))
        assertEquals("Good evening. A short session before the day ends?", g())
        assertEquals("Good morning, Dana. A few minutes at the keys?", g(hour = 8, name = "Dana"))
        assertEquals("Good afternoon, Dana. Ready when you are.", g(hour = 14, name = "Dana"))
        assertEquals("Hi, Dana. I'm your coach. Let's start with an easy song.", g(fresh = true, name = "Dana"))
        assertEquals("Hi there. I'm your coach. Let's start with an easy song.", g(fresh = true))
        assertEquals("Welcome back, Dana. Good to see you.", g(back = true, name = "Dana"))
        assertEquals("Welcome back. Good to see you.", g(back = true))
        // "Hi, Roy" is gone: no greeting names anyone but the person who gave a name.
        assertFalse("Roy" in g(fresh = true) || "Roy" in g(back = true))
    }

    @Test fun todayHasOneStartWhileTheComebackCardShows() {
        val today = src("ui/TodayScreen.kt")
        assertTrue("TodayCardView(shown, goal, a, quest, showStart = welcomeBack == null)" in today)
        assertTrue("if (showStart) ChunkyButton(onClick = a.onStart" in today)
        // The card's own Start is the one that stays.
        assertTrue("Text(\"Start\")" in src("ui/Retention.kt"))
    }

    @Test fun theLayoutChooserTheTourAndMyWeekReadTheName() {
        assertTrue("current.greetName" in src("MainActivity.kt").substringAfter("ui.LayoutChooser(").substringBefore("returning"))
        assertTrue("Greeting.layout(name, returning)" in src("ui/LayoutChooser.kt"))
        assertTrue("name = current.greetName" in src("MainActivity.kt").substringAfter("ui.TourDialog(").substringBefore("onDone"))
        assertTrue("Greeting.hi(name)" in src("ui/WelcomeTour.kt"))
        assertTrue("Greeting.myWeekCard(me.greetName)" in src("ui/brief/BriefEntries.kt"))
        assertTrue("Greeting.myWeek(page.profile.greetName)" in src("ui/brief/BriefPage.kt"))
        assertTrue("who.greetName" in src("ui/RunRecap.kt") && "who.name" !in src("ui/RunRecap.kt"))
        // No greeting reads the owner's stored name any more.
        assertFalse("LayoutChooser(current.name" in src("MainActivity.kt"))
    }

    @Test fun onboardingAsksTheNameOnTheFirstCardForTheOwnerOnly() {
        val ob = src("ui/onboarding/OnboardingScreen.kt")
        assertTrue("askName: Boolean = askReminder" in ob && "if (askName) onName(name)" in ob)
        assertTrue("OwnerName.QUESTION" in ob && "WelcomeStep(if (askName) name else null)" in ob)
        assertTrue("askName = profile.isOwner, onName = { n -> profiles.nameOwner(n) }" in src("MainActivity.kt"))
        // A phone from before asks once, before the What's new and tour sheets.
        val main = src("MainActivity.kt")
        assertTrue("profiles.ownerNameDue(progress.onboardingDone)" in main && "OwnerNameAsk {" in main)
        assertTrue(main.indexOf("OwnerNameAsk {") < main.indexOf("engine.nav.Welcome.next("))
    }

    @Test fun myCasingKeepsTheFilesButNotItsLittleWords() {
        assertEquals("Canon in D", MySongs.titleFrom(null, "canon-in-d.mid"))
        assertEquals("Canon in D", MySongs.titleFrom("Canon In D", "x.mid"))
        assertEquals("Songs of the Sea", MySongs.titleFrom(null, "songs_of_the_sea.mid"))
        assertEquals("The Sound of Silence", MySongs.titleFrom(null, "the-sound-of-silence.mid"))
        assertEquals("Prelude in A", MySongs.titleFrom(null, "prelude-in-a.mid"))
        assertEquals("Sonata in A Major", MySongs.titleFrom("Sonata In A Major", "x.mid"))                 // a key letter keeps its capital
        assertEquals("Some Artist, The Tune", MySongs.titleFrom(null, "Some_Artist_-_The_Tune.mid"))      // after a comma it starts a phrase
        assertEquals("Billie Jean", MySongs.titleFrom(null, "billie-jean.mid"))
        assertEquals("Test Tune", MySongs.titleFrom("Test Tune", "other.mid"))
        assertEquals("a lower case file's own words", "my own song of mine", MySongs.lowerSmallWords("my own song of mine"))
        assertEquals("Hey Jude", MySongs.lowerSmallWords("Hey Jude"))
        assertEquals("Take on Me", MySongs.lowerSmallWords("Take On Me"))
    }

    @Test fun theSongsTabHidesTheLockedPersonalRowUntilItsFileExists() {
        val meta = { id: String, file: String -> com.bigpapituna.sharpears.data.SongMeta(id, file, id, "", "easy", com.bigpapituna.sharpears.engine.HandRule.Auto, "", "") }
        val alicia = LibraryEntry(meta("alicia", "user/alicia.mid"), available = false, progress = com.bigpapituna.sharpears.data.SongProgress())
        val mine = LibraryEntry(meta("canon", "songs-import/canon.mid"), available = true, progress = com.bigpapituna.sharpears.data.SongProgress())
        val imported = LibraryEntry(meta("old", "user/old.mid"), available = true, progress = com.bigpapituna.sharpears.data.SongProgress())
        assertEquals(listOf("canon", "old"), myShelf(listOf(alicia, mine, imported)).map { it.meta.id })
        assertEquals("shown once the file exists", listOf("alicia"), myShelf(listOf(alicia.copy(available = true))).map { it.meta.id })
    }

    @Test fun otherQuotedStringsAreFixed() {
        assertEquals("Show a grown-up", com.bigpapituna.sharpears.engine.brief.KidCardWords.SHOW_MUM)
        assertEquals("Play C, E and G, in any order and any octave. Notes heard turn green.", com.bigpapituna.sharpears.engine.chordPrompt(listOf("C", "E", "G"), mic = true))
        assertEquals("Play A, C sharp and E, in any order and any octave. Notes you play turn green.",
            com.bigpapituna.sharpears.engine.chordPrompt(listOf("A", "C sharp", "E"), mic = false))
        assertFalse("Play it between" in src("ui/ChordsScreen.kt"))
        assertEquals("High five!", com.bigpapituna.sharpears.engine.lessons.CheerLines.highFive(kid = true))
        assertEquals("Nice playing.", com.bigpapituna.sharpears.engine.lessons.CheerLines.highFive(kid = false))
        assertTrue("CheerLines.highFive(kid)" in src("ui/PlayerScreen.kt"))
        assertFalse("Practise today to start one." in src("ui/profile/ProfileScreen.kt"))
    }

    // ---- The grown-up's calm finish ----

    @Test fun aGrownUpsFinishIsOneLineOneChipOneButton() {
        val lesson = src("ui/LessonScreen.kt")
        val grown = lesson.substringAfter("a grown-up's calm finish").substringBefore("if (kid) com.bigpapituna.sharpears.ui.brief.KidCardStepHost")
        assertTrue("RewardQueue.of(reward, progress?.levelUp?.value)" in grown && "RewardQueue.line(items" in grown)
        assertTrue("calm = calm" in grown)
        assertFalse("no XP bar, no stacked reveals, no card about the next sticker", "xpGained" in grown || "reward = dropSlot" in grown || "oneMore = oneMore" in grown)
        val cel = src("ui/Celebrate.kt")
        assertTrue("if (xpGained != null && calm == null) XpRow" in cel)
        assertTrue("calm == null && !picture) TextButton(onClick = secondaryTap" in cel)
        assertTrue("RewardsChip(it, onClick = { open.value = true })" in cel && "RewardsSheet(calm.items" in cel)
        // The level-up joins the queue and is consumed when the finish goes, so no second screen follows it.
        assertTrue("onLeave = { progress?.consumeLevelUp() }" in grown && "calmNow.value?.onLeave?.invoke()" in cel)
        // The song recap: the same line and chip, no card about the next sticker, one Play again.
        val recap = src("ui/RunRecap.kt")
        assertTrue("CalmRewards(calm," in recap && "val peek = nextDrop.takeIf { !short && kid }" in recap)
        assertFalse("XpLine(e.xpGained" in recap)
        // One sheet, one reward at a time, never stacked.
        val views = src("ui/world/RewardViews.kt")
        assertTrue("ModalBottomSheet" in views && "RewardItemView(items[at]" in views && "RewardQueue.button(at, items.size)" in views)
        assertEquals("one sheet", 1, Regex("""ModalBottomSheet\(""").findAll(views).count())
    }

    @Test fun kidsKeepTheRevealsOneAtATimeWithTheBigButtonAlwaysInView() {
        val views = src("ui/world/RewardViews.kt")
        assertTrue("fun KidRewardStage(" in views && "delay(dwellMs(items[at], still))" in views && "androidx.compose.runtime.key(at) { RewardItemView(" in views)
        val lesson = src("ui/LessonScreen.kt")
        assertTrue("KidRewardStage(items, reader, say)" in lesson)
        assertFalse("the old stack of reveals is gone", "RewardExtras(reward, say = say, level = false)" in lesson)
        assertTrue("KidRewardStage(" in src("ui/RunRecap.kt"))
        // The button is pinned under the scrolling middle (Celebrate's compact layout), as before.
        assertTrue("the middle scrolls above the buttons" in src("ui/Celebrate.kt"))
    }

    // ---- Children who cannot read ----

    @Test fun aChildWhoCannotReadGetsPicturesAndOneArrow() {
        val lesson = src("ui/LessonScreen.kt")
        assertTrue("val pictures = kid && !who.reader" in lesson && "picture = pictures" in lesson)
        assertTrue("val oneMore: (@Composable () -> Unit)? = if (kid && !pictures &&" in lesson)
        val cel = src("ui/Celebrate.kt")
        assertTrue("if (picture) {" in cel && "ic_arrow_forward" in cel && "contentDescription = primary" in cel)
        assertTrue("if (stats.isNotEmpty() && !picture)" in cel)
        // No sentences: the sticker, the gift, the wearable and the pictures come without captions.
        val views = src("ui/world/RewardViews.kt")
        assertTrue("captions = reader" in views && "if (reader) Text(line" in views)
        assertTrue("captions = com.bigpapituna.sharpears.ui.family.profile().reader" in src("ui/Retention.kt") || "captions = reader" in src("ui/Retention.kt") ||
            "WrappedGift(modifier, captions = com.bigpapituna.sharpears.ui.family.profile().reader)" in src("ui/Retention.kt"))
        // "Done for today" and the goodbye too.
        assertTrue("picture = !current.reader" in src("MainActivity.kt") && "picture = current.kidSkin && !current.reader" in src("MainActivity.kt"))
        val bye = src("ui/skin/Goodbye.kt")
        assertTrue("picture: Boolean = false" in bye && "if (picture) androidx.activity.compose.BackHandler(onBack = onClose)" in bye)
        assertEquals("both goodbyes in the lesson", 2, Regex("""picture = kid && !who\.reader""").findAll(lesson).count())
    }

    // ---- Day 1 outfit ----

    @Test fun dayOneHasSomethingToWearAndNoWallOfPadlocks() {
        val dress = src("ui/world/DressUp.kt")
        assertTrue("MoreSoonTile(" in dress && "listOf(null)" in dress)
        assertTrue("WearSlot.entries.firstOrNull { sl -> s.unlocked.any { it.slot == sl } } ?: WearSlot.HEAD" in dress)
        // Locked items are no longer tiles of their own: the grid is what is open, then the one tile.
        assertTrue("all.filter { it in s.wardrobe } + (if (all.any { it !in s.wardrobe }) listOf(null) else emptyList())" in dress)
        assertTrue("starter = profile.kidSkin" in src("MainActivity.kt"))
        assertTrue("starter: Boolean = false" in src("data/WorldStore.kt"))
    }

    // ---- Debug extras are one-shot ----

    @Test fun debugExtrasActOnceNotAgainOnARecreate() {
        assertTrue(DebugRoutes.oneShot(recreated = false))
        assertFalse(DebugRoutes.oneShot(recreated = true))
        val main = src("MainActivity.kt")
        val create = main.substringAfter("override fun onCreate(").substringBefore("sampler = Sampler(this)")
        assertTrue("debugRoutes = routesAllowed(intent) && com.bigpapituna.sharpears.engine.DebugRoutes.oneShot(recreated = savedInstanceState != null)" in create)
        assertTrue("if (!debugRoutes) intent = withoutDebugExtras(intent)" in create)
        // The profile switch and the clock read what is left of the intent, which a recreate has stripped.
        assertTrue("intent.getStringExtra(\"profile\")?.takeIf { debugRoutes }" in main)
    }
}

package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The deep link the email's buttons carry, read back by the app. */
class EmailLinksTest {
    @Test fun theLinksTheEmailWritesAreTheOnesTheAppReads() {
        for (id in listOf("p1", "owner", "kid-7", "p 1/\u00e9", "<b>\"p\"", "a+b")) for (f in listOf(EmailFixtures.dayFindings(), EmailFixtures.weekFindings())) {
            val e = EmailFixtures.email(f, profileId = id)
            val want = if (e.period == EmailPeriod.DAY) Period.DAY else Period.WEEK
            assertEquals("$id ${e.period}", EmailLinks.Target(id, want), EmailLinks.parse(EmailHtml.deepLink(e)))
        }
    }

    @Test fun otherLinksAndOddOnesAreHandledQuietly() {
        assertEquals(EmailLinks.Target("p1", Period.WEEK), EmailLinks.parse("sharpears://brief/p1"))
        assertEquals(EmailLinks.Target("p1", Period.WEEK), EmailLinks.parse("sharpears://brief/p1/nonsense"))
        assertEquals(EmailLinks.Target("p1", Period.DAY), EmailLinks.parse("SharpEars://brief/p1/DAY?x=1#y"))
        assertEquals(EmailLinks.Target("p1", Period.SESSION), EmailLinks.parse("sharpears://brief/p1/session"))
        assertNull("no child named", EmailLinks.parse("sharpears://brief"))
        assertNull(EmailLinks.parse("sharpears://brief/"))
        assertNull(EmailLinks.parse("sharpears://briefcase/p1/week"))
        assertNull(EmailLinks.parse("https://example.org/brief/p1/week"))
        assertNull(EmailLinks.parse("sharpears://brief/%ZZ/week"))
        assertNull(EmailLinks.parse(null))
    }
}

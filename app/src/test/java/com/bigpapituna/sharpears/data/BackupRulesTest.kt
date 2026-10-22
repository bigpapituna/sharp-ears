package com.bigpapituna.sharpears.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit (docs/AUDIT-ENGINE.md): Android's backup took every preference file and the external files, so the report
 * token (data/Reporter.kt, "never in the code, a log or a report") went to Google Drive and to a new phone, with up to
 * 250 MB of practice clips (over the 25 MB limit, which stops the whole backup). Both rule files keep them home.
 */
class BackupRulesTest {
    private val main = File("src/main")

    @Test fun theReportTokenAndTheClipsStayOnThePhone() {
        val manifest = File(main, "AndroidManifest.xml").readText()
        assertTrue(Regex("android:fullBackupContent=\"@xml/backup_rules\"").containsMatchIn(manifest))
        assertTrue(Regex("android:dataExtractionRules=\"@xml/data_extraction_rules\"").containsMatchIn(manifest))
        // The names the rules exclude are the ones the code writes.
        val reporter = File(main, "java/com/bigpapituna/sharpears/data/Reporter.kt").readText()
        assertTrue(reporter.contains("getSharedPreferences(\"reports\""))
        assertTrue(reporter.contains("getExternalFilesDir(null), \"stalls\""))
        val full = File(main, "res/xml/backup_rules.xml").readText()
        val extraction = File(main, "res/xml/data_extraction_rules.xml").readText()
        for ((name, text) in listOf("backup_rules" to full) + Regex("(?s)<(cloud-backup|device-transfer)>(.*?)</\\1>").findAll(extraction)
            .map { it.groupValues[1] to it.groupValues[2] }.toList()) {
            assertTrue(name, text.contains("<exclude domain=\"sharedpref\" path=\"reports.xml\" />"))
            assertTrue(name, text.contains("<exclude domain=\"external\" path=\"stalls\" />"))
            // The email brief's settings hold the grown-ups' addresses (data/EmailSettings.kt): never in Android's backup.
            assertTrue(name, text.contains("<exclude domain=\"sharedpref\" path=\"${EmailSettings.FILE}.xml\" />"))
            // Tester analytics (analytics/Tester.kt): the switches, the device id and the tester log belong to this phone.
            assertTrue(name, text.contains("<exclude domain=\"sharedpref\" path=\"${com.bigpapituna.sharpears.analytics.Tester.FILE}.xml\" />"))
            assertTrue(name, text.contains("<exclude domain=\"file\" path=\"${com.bigpapituna.sharpears.analytics.Tester.DIR}\" />"))
        }
        assertEquals(2, Regex("<(cloud-backup|device-transfer)>").findAll(extraction).count())
    }
}

package com.bigpapituna.sharpears.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.bigpapituna.sharpears.TAG
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Sends what the app saved for review (stuck moments, "that wasn't right" flags, crashes, weekly summaries; all in
 * files/stalls) to Roy's private GitHub repo: each clip goes to the "reports" branch and each report becomes an
 * issue, labelled by kind, where the repo's review workflow and the daily check-in pick it up.
 *
 * Off until a token is set (in Microphone setup, or scripts/set-report-token.ps1 from the PC; either way it lives only
 * in this app's private storage, never in the code, a log or a report) and the switch in Microphone setup is on. Only on Wi-Fi or another unmetered network.
 */
class Reporter(context: Context) {
    // The app's context: MicInput is made per Activity, and the crash hook outlives it.
    private val context: Context = context.applicationContext
    private val prefs = context.getSharedPreferences("reports", Context.MODE_PRIVATE)
    private val worker get() = WORKER
    private val dir get() = File(context.getExternalFilesDir(null), "stalls")

    val hasToken get() = !prefs.getString("token", null).isNullOrBlank()
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true) && hasToken
        set(v) { prefs.edit().putBoolean("enabled", v).apply() }
    private val repo get() = prefs.getString("repo", null) ?: DEFAULT_REPO

    /** Stores a token typed in Microphone setup and switches sending on. The value itself is never logged. */
    fun setToken(token: String) {
        val e = prefs.edit().putString("token", token.trim()).putBoolean("enabled", true)
        if (prefs.getString("repo", null) == null) e.putString("repo", DEFAULT_REPO)
        e.apply()
        Log.d(TAG, "reports: token set in the app")
    }

    /** Forgets the token; sending stays off until a new one is set. */
    fun clearToken() { prefs.edit().remove("token").apply(); Log.d(TAG, "reports: token removed") }

    /** Reports saved but not yet sent. */
    fun pending(): Int = reports().count { !sentMarker(it).exists() }

    /** Sends everything waiting, in the background, when allowed. Safe to call often. */
    fun flush() = worker.execute {
        if (!enabled || !unmetered()) return@execute
        for (txt in reports()) {
            if (sentMarker(txt).exists()) continue
            try { send(txt); sentMarker(txt).writeText(java.time.LocalDateTime.now().toString()) }
            catch (e: Exception) { Log.d(TAG, "reports: ${txt.name} not sent (${e.message}); will retry"); return@execute }
        }
    }

    private fun reports() = dir.listFiles { f -> f.extension == "txt" }?.sortedBy { it.name }.orEmpty()
    private fun sentMarker(txt: File) = File(txt.path.removeSuffix(".txt") + ".sent")

    private fun send(txt: File) {
        val name = txt.nameWithoutExtension
        val text = txt.readText()
        val wav = File(dir, "$name.wav").takeIf { it.exists() }
        if (wav != null) putFile("reports/$name.wav", wav.readBytes(), "clip $name")
        putFile("reports/$name.txt", text.toByteArray(), "report $name")
        val title = Regex("(?m)^title=(.*)$").find(text)?.groupValues?.get(1) ?: name
        val body = buildString {
            append("```\n").append(text.lines().filterNot { it.startsWith("title=") }.joinToString("\n").trim()).append("\n```\n\n")
            if (wav != null) append("clip: `reports/$name.wav` ([listen](https://github.com/$repo/blob/reports/reports/$name.wav))\n")
            append("report: `reports/$name.txt`\n")
        }
        api("POST", "/repos/$repo/issues", JSONObject().put("title", title).put("body", body).put("labels", JSONArray().put(kindOf(name))))
        Log.d(TAG, "reports: sent $name as an issue")
    }

    /** Labels by file prefix; the repo's workflow reviews the ones with audio. */
    private fun kindOf(name: String) = when {
        name.startsWith("stall-") -> "stuck-moment"
        name.startsWith("flag-") -> "flagged"
        name.startsWith("answer-") -> "answer-key"
        name.startsWith("crash-") -> "crash"
        else -> "summary"
    }

    private fun putFile(path: String, bytes: ByteArray, message: String) {
        val body = JSONObject().put("message", message).put("branch", "reports")
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
        // Re-sending after a failed issue: the file is already there, so pass its sha to overwrite.
        runCatching { api("GET", "/repos/$repo/contents/$path?ref=reports", null) }.getOrNull()
            ?.let { JSONObject(it).optString("sha").takeIf(String::isNotEmpty) }?.let { body.put("sha", it) }
        api("PUT", "/repos/$repo/contents/$path", body)
    }

    private fun api(method: String, path: String, body: JSONObject?): String {
        val c = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000; c.readTimeout = 60_000
        c.setRequestProperty("Authorization", "Bearer " + prefs.getString("token", ""))
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code >= 300) throw java.io.IOException("$method $path: HTTP $code ${text.take(200)}")
        return text
    }

    private fun unmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Writes a report with no audio (crash, summary) into the same queue. */
    fun saveText(name: String, text: String) { dir.mkdirs(); File(dir, "$name.txt").writeText(text) }

    /** Catches crashes: each is saved with its stack trace and the app's recent log, and goes out on the next start. */
    fun catchCrashes(version: String, names: () -> Collection<String> = { rosterNames() }) {
        // Once per process: every MainActivity made (a recreate after dark mode or a language change, an import) asks again, and each
        // handler chained on the last kept its activity alive and saved the same crash once more.
        synchronized(Reporter::class.java) { if (crashHandlerSet) return; crashHandlerSet = true }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val log = runCatching {
                    ProcessBuilder("logcat", "-d", "-t", "200", "--pid=${android.os.Process.myPid()}").start().inputStream.bufferedReader().readText()
                }.getOrDefault("(log unavailable)")
                val who = runCatching { names() }.getOrDefault(emptyList())     // the players' names never leave in a report
                saveText("crash-" + stamp(), com.bigpapituna.sharpears.engine.StallWatch.scrubNames(
                    "title=Crash: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(80)}\n" +
                    "# sharp ears crash\nversion=$version thread=${t.name} device=${android.os.Build.MODEL} android=${android.os.Build.VERSION.RELEASE}\n\n" +
                    e.stackTraceToString() + "\nrecent log:\n" + log.lines().takeLast(200).joinToString("\n"), who))
            }
            previous?.uncaughtException(t, e)
        }
    }

    /**
     * Once a week: practice minutes per day, and the stuck moments, flags and crashes of those days per song.
     * The first run only starts the clock.
     */
    fun weeklySummary(practice: Map<java.time.LocalDate, Int>, today: java.time.LocalDate = java.time.LocalDate.now()) {
        val last = prefs.getString("lastSummary", null)?.let(java.time.LocalDate::parse)
        if (last == null) { prefs.edit().putString("lastSummary", today.toString()).apply(); return }
        if (last.plusDays(7) > today) return
        val days = (1..7).map { today.minusDays(it.toLong()) }.reversed()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
        val names = dir.listFiles { f -> f.extension == "txt" }.orEmpty().map { it.nameWithoutExtension }
        fun inWeek(n: String) = n.split("-").getOrNull(1)?.let { d -> days.any { it.format(fmt) == d } } == true
        fun perSong(prefix: String) = names.filter { it.startsWith(prefix) && inWeek(it) }
            .groupingBy { it.split("-").drop(3).joinToString("-") }.eachCount().entries.sortedByDescending { it.value }
        val text = buildString {
            val total = days.sumOf { practice[it] ?: 0 } / 60
            append("title=Weekly summary: ${days.first()} to ${days.last()} ($total min practised)\n")
            append("# sharp ears weekly summary\npractice minutes per day:\n")
            days.forEach { append("  $it  ${(practice[it] ?: 0) / 60}\n") }
            for ((label, prefix) in listOf("stuck moments" to "stall-", "flags" to "flag-", "mic vs keyboard mismatches" to "answer-")) {
                val per = perSong(prefix)
                append("$label: ${per.sumOf { it.value }}\n")
                per.forEach { (song, n) -> append("  $song  $n\n") }
            }
            append("crashes: ${names.count { it.startsWith("crash-") && inWeek(it) }}\n")
        }
        saveText("summary-" + stamp(), text)
        prefs.edit().putString("lastSummary", today.toString()).apply()
    }

    private fun stamp() = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    /** The roster's names, read at crash time: the log holds the voice's lines and saved file names, which name the kids. */
    private fun rosterNames(): List<String> = runCatching {
        com.bigpapituna.sharpears.engine.family.Roster.decode(context.getSharedPreferences(com.bigpapituna.sharpears.engine.family.ProfileFiles.ROSTER,
            Context.MODE_PRIVATE).getString(Profiles.KEY_ROSTER, null)).profiles.map { it.name }
    }.getOrDefault(emptyList())

    private companion object {
        const val DEFAULT_REPO = "bigpapituna/sharp-ears"
        /** One sending thread for the process (a Reporter is made with every MicInput, so with every activity). */
        val WORKER: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "reports").apply { isDaemon = true } }
        var crashHandlerSet = false
    }
}

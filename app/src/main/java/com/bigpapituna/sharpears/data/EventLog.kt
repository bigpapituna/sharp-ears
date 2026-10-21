package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/**
 * One person's brief event log (docs/PARENT-BRIEF-PLAN.md, "Event log"): append-only, one line per event
 * (engine/brief/Events.kt), in the app's files folder at files/events/<profileId>.log. Writes are buffered and go to
 * disk on one background thread ([io]), never the caller's; [read] and [lines] wait for what was appended before them,
 * so call them off the main thread. Capped: on open (and when it grows a tenth past the cap) the log keeps the last
 * [keepMs] (90 days) and at most [maxEvents] (20,000) events, whichever is smaller, and drops lines that are not events
 * (a line cut short when the app was killed mid-write).
 *
 * Deleted with its profile (Profiles.delete through Briefs.forget) and carried by the progress backup ("events" in
 * engine/ProgressBackup.kt, data/ProgressFiles.kt).
 */
class EventLog(
    val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: Executor = IO,
    val maxEvents: Int = MAX_EVENTS,
    val keepMs: Long = KEEP_MS,
) {
    private val lock = Any()
    private val pending = ArrayList<String>()
    private var scheduled = false
    /** Lines on disk since the last prune (the io thread's own count; -1 before the first). */
    private var onDisk = -1
    @Volatile private var closed = false

    init { submit { prune() } }

    /** Adds [event] to the log; returns at once, the line is written in the background. Ignored once closed or deleted. */
    fun append(event: BriefEvent) {
        val line = event.encode()
        synchronized(lock) {
            if (closed) return
            pending += line
            if (scheduled) return
            scheduled = true
        }
        submit { drain() }
    }

    /** Waits until every event appended so far is on disk. */
    fun flush() = onIo { drain() }

    /** The events from [since] (inclusive) to [until] (exclusive), oldest first, as written; waits for pending writes. */
    fun read(since: Long = Long.MIN_VALUE, until: Long = Long.MAX_VALUE): List<BriefEvent> =
        lines().mapNotNull(BriefEvent::decode).filter { it.at >= since && it.at < until }

    /** The raw lines, oldest first (the backup carries them as they are, so a newer app's events survive). */
    fun lines(): List<String> = onIo { drain(); readLines() }

    /** Replaces the whole log with [lines] (a backup restored), then applies the cap. */
    fun replace(lines: List<String>) = onIo {
        synchronized(lock) { pending.clear() }
        writeAll(lines.filter { it.isNotBlank() && '\n' !in it })
        prune()
    }

    /** Deletes the file; nothing more is written to this log. */
    fun delete() = onIo {
        synchronized(lock) { pending.clear(); closed = true }
        file.delete()
        onDisk = 0
    }

    /** Writes what is pending and stops taking events. */
    fun close() { flush(); synchronized(lock) { closed = true } }

    // ---- the io thread ----

    private fun drain() {
        val batch = synchronized(lock) { scheduled = false; ArrayList(pending).also { pending.clear() } }
        if (batch.isEmpty()) return
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { w -> for (l in batch) { w.write(l); w.write("\n") } }
        if (onDisk >= 0) onDisk += batch.size
        if (onDisk > maxEvents + maxEvents / 10) prune()
    }

    private fun readLines(): List<String> = if (!file.exists()) emptyList() else file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }

    /** Keeps the newest [maxEvents] events of the last [keepMs]; rewrites the file only when something goes. */
    private fun prune() {
        if (!file.exists()) { onDisk = 0; return }
        val all = readLines()
        val from = clock() - keepMs
        val kept = all.filter { l -> BriefEvent.decode(l)?.let { it.at >= from } == true }.takeLast(maxEvents)
        if (kept.size != all.size) writeAll(kept)
        onDisk = kept.size
    }

    /** The whole file at once: written beside it, then moved over it, so a kill mid-write leaves the old log. */
    private fun writeAll(lines: List<String>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w -> for (l in lines) { w.write(l); w.write("\n") } }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        onDisk = lines.size
    }

    private fun submit(block: () -> Unit) = io.execute { onIoThread.set(true); try { block() } finally { onIoThread.set(false) } }

    /** Runs [block] on the io thread after everything queued before it, and waits for its result. */
    private fun <T> onIo(block: () -> T): T {
        if (onIoThread.get() == true) return block()
        val task = FutureTask { onIoThread.set(true); try { block() } finally { onIoThread.set(false) } }
        io.execute(task)
        return task.get()
    }

    companion object {
        /** The folder under the app's files folder. */
        const val DIR = "events"
        const val MAX_EVENTS = 20_000
        const val KEEP_MS = 90L * 24 * 60 * 60 * 1000

        /** One thread for every log: lines reach disk in the order they were appended. */
        val IO: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "brief-log").apply { isDaemon = true } }

        private val onIoThread = ThreadLocal<Boolean>()
        private val ID = Regex("[A-Za-z0-9_-]{1,32}")

        /** [profileId]'s log file under the app's [filesDir] (an id that could not be a profile's is refused). */
        fun fileFor(filesDir: File, profileId: String): File {
            require(ID.matches(profileId)) { "not a profile id: $profileId" }
            return File(File(filesDir, DIR), "$profileId.log")
        }

        /** The profiles that have a log under [filesDir]. */
        fun profilesWithLogs(filesDir: File): List<String> =
            File(filesDir, DIR).listFiles()?.map { it.name }?.filter { it.endsWith(".log") }?.map { it.removeSuffix(".log") }
                ?.filter(ID::matches)?.sorted().orEmpty()

        /**
         * What a backup carries: each of [profileIds]' log lines (empty logs left out). Read straight from the files; the
         * caller flushes open logs first (Briefs.exportLogs).
         */
        fun export(filesDir: File, profileIds: List<String>): Map<String, List<String>> = profileIds.filter(ID::matches).associateWith { id ->
            fileFor(filesDir, id).takeIf { it.exists() }?.readLines(Charsets.UTF_8)?.filter { it.isNotBlank() }.orEmpty()
        }.filterValues { it.isNotEmpty() }

        /**
         * Puts a backup's logs in place. A backup that replaces every profile ([allProfiles], it carries the roster)
         * replaces every log: each of [profileIds] (the roster after the import) gets its lines from [logs] or none, and
         * logs of anyone else go. A backup from before the brief ([logs] null) clears them all: its progress is older than
         * any event, so a brief from them would describe progress that is gone. A 2.0 backup (the owner's files only, no
         * roster) leaves the logs as they are, as it leaves the other profiles.
         */
        fun restore(filesDir: File, logs: Map<String, List<String>>?, profileIds: List<String>, allProfiles: Boolean) {
            if (!allProfiles) return
            val keep = profileIds.filter(ID::matches).toSet()
            for (id in profilesWithLogs(filesDir)) if (id !in keep) fileFor(filesDir, id).delete()
            for (id in keep) {
                val lines = logs?.get(id).orEmpty().filter { it.isNotBlank() && '\n' !in it && BriefEvent.decode(it) != null }
                val f = fileFor(filesDir, id)
                if (lines.isEmpty()) { f.delete(); continue }
                f.parentFile?.mkdirs()
                f.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            }
        }
    }
}

package com.bigpapituna.sharpears.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bigpapituna.sharpears.engine.play.ConcertFiles
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hands files to the system share sheet through the app's FileProvider (authority "<package>.files"). The provider
 * may only serve cache/share (res/xml/file_paths.xml), so everything shared is written there first: [fresh] gives
 * the folder emptied of the previous share. Only ever started by the user.
 */
object Share {
    /** cache/share, emptied: one share at a time, the previous one's files are no longer needed. */
    fun fresh(context: Context): File = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.deleteRecursively() } }

    /**
     * Opens the share sheet for [files] (all inside cache/share), readable by the chosen app only. [title] heads the
     * sheet; [subject] goes along as an email's subject or a chat's caption where the app uses it.
     */
    fun files(context: Context, files: List<File>, title: String, subject: String = title) {
        require(files.isNotEmpty()) { "nothing to share" }
        val uris = files.map { FileProvider.getUriForFile(context, context.packageName + ".files", it) }
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        send.setType(ConcertFiles.mimeType(files.map { it.name })).putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The clip carries the read grant to every file, and lets the sheet preview the first.
        send.clipData = ClipData.newRawUri(files[0].name, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

/**
 * Share stuck moments (Microphone setup): packs the stalls folder into one zip in cache/share and hands it to the
 * system share sheet through [Share], for when the reports cannot go to GitHub. Only ever started by the user.
 */
object StuckShare {
    /** Zips [files] into cache/share/stuck-moments-<yyyyMMdd-HHmm>.zip; call off the main thread. */
    fun zip(context: Context, files: List<File>, progress: (done: Int) -> Unit): File {
        val dir = Share.fresh(context)
        val out = File(dir, "stuck-moments-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            files.forEachIndexed { i, f ->
                zip.putNextEntry(ZipEntry(f.name).apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                progress(i + 1)
            }
        }
        return out
    }

    /** Opens the share sheet for [zip], with its name as the subject. */
    fun share(context: Context, zip: File) = Share.files(context, listOf(zip), "Share stuck moments", zip.nameWithoutExtension)
}

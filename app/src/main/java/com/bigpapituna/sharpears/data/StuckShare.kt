package com.bigpapituna.sharpears.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Share stuck moments (Microphone setup): packs the stalls folder into one zip in cache/share and hands it to the
 * system share sheet, for when the reports cannot go to GitHub. Only ever started by the user.
 */
object StuckShare {
    /** Zips [files] into cache/share/stuck-moments-<yyyyMMdd-HHmm>.zip; call off the main thread. */
    fun zip(context: Context, files: List<File>, progress: (done: Int) -> Unit): File {
        // One zip at a time: the previous share's zip is no longer needed.
        val dir = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
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

    /** Opens the share sheet for [zip], readable by the chosen app only. */
    fun share(context: Context, zip: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", zip)
        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, zip.nameWithoutExtension)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(zip.name, uri)
        context.startActivity(Intent.createChooser(send, "Share stuck moments").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

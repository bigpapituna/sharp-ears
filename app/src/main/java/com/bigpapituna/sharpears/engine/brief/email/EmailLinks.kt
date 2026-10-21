package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.Period
import java.net.URLDecoder

/**
 * The app's door for the email's buttons: `sharpears://brief/<profile id>/<day or week>` ([EmailHtml.deepLink] writes them).
 * MainActivity reads the link it is opened with through [parse] and shows that child's brief, behind the parent gate. A link
 * that names no child opens the app and nothing else.
 */
object EmailLinks {
    /** A child and the period of the brief a link opens. */
    data class Target(val profileId: String, val period: Period)

    /** [uri] as a [Target], or null when it is not one of ours or names no child. A missing or unknown period is the week. */
    fun parse(uri: String?): Target? {
        val rest = uri?.trim()?.takeIf { it.startsWith("sharpears://brief", ignoreCase = true) }?.substring("sharpears://brief".length) ?: return null
        if (rest.isNotEmpty() && !rest.startsWith("/")) return null
        val segments = rest.substringBefore('?').substringBefore('#').split('/').filter { it.isNotEmpty() }
            .map { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return null }
        val id = segments.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val period = Period.entries.firstOrNull { it.name.equals(segments.getOrNull(1), ignoreCase = true) } ?: Period.WEEK
        return Target(id, period)
    }
}

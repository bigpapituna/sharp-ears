package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import java.time.LocalDate

/**
 * What the finale's certificate says (2.2): "Well done!", the kid's name, "finished the garden", the day, and the app
 * at the bottom; [title] ("Mia finished the garden") heads the share sheet and names the file. Pure; ui/kid/Certificate.kt
 * draws it.
 */
data class CertificateWords(val heading: String, val name: String, val what: String, val date: String, val footer: String, val title: String) {
    companion object {
        const val HEADING = "Well done!"
        const val FOOTER = "Sharp Ears"

        fun of(name: String?, page: PlaygroundPage, date: LocalDate): CertificateWords {
            val who = name?.trim()?.takeIf { it.isNotEmpty() }
            val what = "finished ${KidPages.inSentence(page)}"
            return CertificateWords(HEADING, who ?: "A piano player", what, day(date), FOOTER, "${who ?: "I"} $what")
        }

        /** "October 18, 2026". */
        fun day(date: LocalDate): String = date.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + date.dayOfMonth + ", " + date.year
    }
}

package com.bigpapituna.sharpears.engine.brief.email

import java.util.Base64

/**
 * Where the email's pup pictures come from (scripts/cut_email_pups.py cuts them into app/src/main/assets/email as
 * `pup-<pose>.png`, and `pup-<pose>-<n>.png` for the three poses that wear the child's sweatband colour).
 *
 * - [hosted]: plain https `<img src>` at an address Roy controls (EMAIL_IMAGE_BASE). Every mail app shows them and the HTML
 *   stays small. The right way once there is a host.
 * - [inline]: `data:` pictures inside the HTML. Needs no host, but Gmail and some other mail apps do not show them (the
 *   alt text stands in), and each picture adds about 20 KB to a message Gmail clips at 102 KB. Only the three pictures an
 *   email uses are inlined.
 * - [NONE]: no pictures (the alt text is dropped too): for tests and for a build with neither.
 */
fun interface EmailImages {
    /** The `src` for [pose] on the pup with sweatband [avatar], or null to leave the picture out. */
    fun src(pose: PupPose, avatar: Int): String?

    companion object {
        val NONE = EmailImages { _, _ -> null }

        /** The poses that wear the sweatband colour of the child's avatar. */
        private val TINTED = setOf(PupPose.DANCING, PupPose.CLAPPING, PupPose.THINKING)

        /** "pup-dancing-3.png", "pup-crown.png". */
        fun fileName(pose: PupPose, avatar: Int): String =
            "pup-" + pose.name.lowercase() + (if (pose in TINTED) "-" + avatar.coerceIn(0, 7) else "") + ".png"

        /** Every file the pictures can be: what a host must serve. */
        fun allFiles(): List<String> = PupPose.entries.flatMap { p -> if (p in TINTED) (0..7).map { fileName(p, it) } else listOf(fileName(p, 0)) }

        fun hosted(baseUrl: String): EmailImages {
            val base = baseUrl.trim().trimEnd('/')
            require(base.startsWith("https://")) { "pictures are hosted over https" }
            return EmailImages { pose, avatar -> "$base/" + fileName(pose, avatar) }
        }

        /** [read] gives a file's bytes by name (assets/email/<name>), or null when it is missing. */
        fun inline(read: (String) -> ByteArray?): EmailImages = EmailImages { pose, avatar ->
            read(fileName(pose, avatar))?.takeIf { it.isNotEmpty() }?.let { "data:image/png;base64," + Base64.getEncoder().encodeToString(it) }
        }
    }
}

package com.bigpapituna.sharpears.engine.brief.email

/**
 * The first email (2.10.1, security review 2026-10-22, finding 6). It goes before the address is confirmed, so after a typo it
 * reaches a stranger: it carries the six-digit [code] and nothing about the family. A line from the pup, the code, and that
 * the first brief comes once the code is typed; no child's name, no brief, no numbers, no picture, no attachment. Once the
 * code is in, the next brief the schedule sends is the first one, like any other ([EmailBriefs.build]).
 *
 * [EmailHtml.code] draws it in design D's colours, [PlainTextEmail.text] writes its text part. Android-free and tested.
 */
data class CodeEmail(val code: String) {
    /** The code is in the subject too, so it can be read from the notification. */
    val subject: String get() = "Your Sharp Ears code is $code"
    /** The line a mail app shows after the subject. */
    val preheader: String get() = CONFIRM

    /** Every piece of text in the email, in order (the rules' tests walk it). */
    fun texts(): List<String> = listOf(subject, preheader, GREETING, CODE_LABEL, code, CONFIRM)

    companion object {
        /** The pup's one line. */
        const val GREETING = "Hi, it's the pup from Sharp Ears. Here is your code."
        const val CODE_LABEL = "Your code"
        const val CONFIRM = "Your first brief comes as soon as you confirm."
    }
}

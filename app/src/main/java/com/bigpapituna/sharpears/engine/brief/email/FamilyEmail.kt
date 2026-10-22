package com.bigpapituna.sharpears.engine.brief.email

/**
 * One email address for the family (Roy, 2026-10-22, docs/PLAN-2.8.md section 1: "the address is for the parent, not the
 * child"). It is entered and confirmed once, in Settings > Briefs and email, and every child's emails go to it. Each child
 * keeps only their own Daily and Weekly switches (the weekly slot is BriefStore's, as before), the last emails sent, the
 * held reason and the sends the caps count.
 *
 * Both records are [EmailPrefs]: [addressPart] is what the family's keeps, [ownPart] what a child's keeps, and [join] makes
 * the one view the schedule, the round and the sender read (they did not change). Pure, so the split, the first email's
 * child ([lead]) and the move from 2.7's per-child addresses ([migrate]) are tested on the JVM.
 */
object FamilyEmail {
    /** The family's part of [p]: the address, its confirmation and code, whether the first email went, the first emails' sends. */
    fun addressPart(p: EmailPrefs): EmailPrefs = EmailPrefs(address = p.address, verified = p.verified, code = p.code,
        codeExpiresAt = p.codeExpiresAt, wrongTries = p.wrongTries, firstSent = p.firstSent, sends = p.sends)

    /** A child's own part of [p]: everything but the address (the switches, the last sent, held, the sends for the caps). */
    fun ownPart(p: EmailPrefs): EmailPrefs = p.copy(address = null, verified = false, code = null, codeExpiresAt = 0, wrongTries = 0, firstSent = false)

    /**
     * One child's view: [own] with the [family]'s address. The first email (it carries the code) is about one child only,
     * [first] (see [lead]); for every other child it counts as gone, so the family gets one code, not one per child.
     */
    fun join(family: EmailPrefs, own: EmailPrefs, first: Boolean): EmailPrefs = ownPart(own).copy(
        address = family.address, verified = family.verified, code = family.code, codeExpiresAt = family.codeExpiresAt,
        wrongTries = family.wrongTries, firstSent = family.firstSent || !first)

    /** The child the first email is about: the first child (in the family's order) whose emails are on, else the first child. */
    fun lead(children: List<Pair<String, EmailPrefs>>): String? =
        (children.firstOrNull { it.second.frequency != EmailFrequency.OFF } ?: children.firstOrNull())?.first

    /** What [migrate] made: the family's record, and the children's records to write. */
    data class Moved(val family: EmailPrefs, val children: Map<String, EmailPrefs>)

    /**
     * From 2.7's per-child addresses to one. [old] is every child in the family's order with what their record held (null:
     * none). The first child with a confirmed address gives the family its address and confirmation, so nothing is confirmed
     * again; with none confirmed, the first child with a code waiting gives its address and code (the code still works).
     * A child who had an address keeps their switches and their emails' history (their emails now go to the family's
     * address); once an address moved, a child who had none is switched off, so nobody gets emails they did not get before.
     * No child's record keeps an address.
     */
    fun migrate(old: List<Pair<String, EmailPrefs?>>): Moved {
        val from = old.firstOrNull { (_, p) -> p != null && p.verified && p.address != null }?.second
            ?: old.firstOrNull { (_, p) -> p != null && p.pending }?.second
        val moved = from != null
        val children = LinkedHashMap<String, EmailPrefs>()
        for ((id, p) in old) when {
            p != null && p.address != null -> children[id] = ownPart(p)
            p != null -> children[id] = if (moved) ownPart(p).copy(frequency = EmailFrequency.OFF) else ownPart(p)
            moved -> children[id] = EmailPrefs(frequency = EmailFrequency.OFF)
        }
        return Moved(from?.let(::addressPart) ?: EmailPrefs(), children)
    }
}

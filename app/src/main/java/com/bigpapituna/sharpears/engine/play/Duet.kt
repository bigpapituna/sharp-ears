package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.progress.ProgressEvent

/**
 * Duet: two people at one piano, one plays the left hand and the other the right. In the player it is plain
 * both-hands practice (Practice.BOTH) with two names on the recap; this is the pairing and the credit. Both get
 * the run: the same stars and the same XP each, never half (a duet is twice the music, not half the credit).
 */
data class Duet(val leftId: String, val rightId: String) {
    init { require(leftId != rightId) { "a duet takes two different people" } }

    val ids get() = listOf(leftId, rightId)

    /** The other hand's player. */
    fun partnerOf(id: String): String? = when (id) { leftId -> rightId; rightId -> leftId; else -> null }

    fun handOf(id: String): Hand? = when (id) { leftId -> Hand.LEFT; rightId -> Hand.RIGHT; else -> null }

    /** Hands swapped (the picker's swap button). */
    fun swapped() = Duet(rightId, leftId)

    /**
     * What each of the two gets for a run that earned [stars] (0..3) and [xp] (what the store paid the one whose
     * profile is open): both the same.
     */
    fun credit(stars: Int, xp: Int): List<DuetCredit> =
        listOf(DuetCredit(leftId, Hand.LEFT, stars.coerceIn(0, 3), xp.coerceAtLeast(0)), DuetCredit(rightId, Hand.RIGHT, stars.coerceIn(0, 3), xp.coerceAtLeast(0)))

    companion object {
        /** The duet with the profile [currentId] on [hand] and [partnerId] on the other. */
        fun of(currentId: String, partnerId: String, hand: Hand = Hand.RIGHT) =
            if (hand == Hand.LEFT) Duet(currentId, partnerId) else Duet(partnerId, currentId)

        /**
         * The partner's own song run event, judged against the partner's progress: a new best only if [accuracy]
         * beats [partnerBest] (their saved best for the song, null when none), a first completion if they had never
         * completed it. Record it in the partner's ProgressStore (see ui/play/INTEGRATION.md).
         */
        fun partnerRun(songId: String, accuracy: Int?, partnerBest: Int?, partnerCompleted: Boolean): ProgressEvent.SongRun =
            ProgressEvent.SongRun(songId, accuracy, newBest = accuracy != null && accuracy > (partnerBest ?: -1), firstCompletion = !partnerCompleted)

        /**
         * The grown-up who sits down with [child] for the parent brief's "Practise together" (2.4): the grown-up the app is
         * open as ([openId], when that is a grown-up other than the child), else the phone's owner, else any other grown-up;
         * null when there is none (then the duet picker asks).
         */
        fun grownUpFor(child: com.bigpapituna.sharpears.engine.family.Profile, openId: String?,
                       roster: List<com.bigpapituna.sharpears.engine.family.Profile>): String? {
            val grownUps = roster.filter { !it.isKid && it.id != child.id }
            return (grownUps.firstOrNull { it.id == openId } ?: grownUps.firstOrNull { it.isOwner } ?: grownUps.firstOrNull())?.id
        }

        /** The recap's line: "Mia played the left hand and Theo the right. Two stars each!" */
        fun recapLine(leftName: String, rightName: String, stars: Int): String {
            val s = when (stars.coerceIn(0, 3)) {
                0 -> "Good playing, both of you."
                1 -> "One star each!"
                2 -> "Two stars each!"
                else -> "Three stars each!"
            }
            return "$leftName played the left hand and $rightName the right. $s"
        }
    }
}

/** One player's share of a duet run: their hand, and the stars and XP they get (the full amount, see [Duet.credit]). */
data class DuetCredit(val profileId: String, val hand: Hand, val stars: Int, val xp: Int)

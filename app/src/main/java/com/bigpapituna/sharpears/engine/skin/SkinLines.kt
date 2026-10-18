package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.RunWords
import com.bigpapituna.sharpears.engine.SessionWords

/**
 * Every fixed line the kid skin can say, for the voice catalog (engine/SpokenCatalog, scripts/render_voice.py at speed
 * 0.85 for kid lines). Lines with a kid's name in them are not here: the on-phone voice generates those (it can say
 * names), and each has a nameless twin listed here for when no name is known.
 */
object SkinLines {
    fun fixed(): List<String> = buildList {
        RunWords.KID_TITLES.flatten().forEach(::add)
        RunWords.KID_LINES.flatten().forEach { add(RunWords.named(it, null)) }
        for (passed in listOf(true, false)) for (last in listOf(true, false)) add(RunWords.kidStepLine(passed, last, null))
        add(RunWords.KID_LESSON_TITLE); add(RunWords.kidLessonLine(null))
        SessionWords.GOODNIGHTS.forEach { add(RunWords.named(it, null)) }
        add(SessionWords.STILL_OPEN); add(SessionWords.lessonsAsleep(null))
        for (midi in 60..71) for (reader in listOf(true, false)) {
            add(NoteColours.spokenName(midi, reader))
            if (reader && NoteColours.isBlack(midi)) add(NoteColours.spokenName(midi, reader, flats = true))
        }
    }.distinct()
}

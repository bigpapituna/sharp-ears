package com.bigpapituna.sharpears.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The app's today (2.9): the phone's date, moved by [offsetDays] on a debug build only, so a walk can fake the days the
 * retention features count (rest days, comebacks, Tomorrow's surprise) on an emulator whose clock cannot be set without
 * root. `--es today 2026-10-28` (MainActivity, debug builds) sets it for the life of the process; the brief's events are
 * stamped on the same moved clock (Briefs.clock). Release and normal debug launches leave it at zero: plain LocalDate.now().
 * The engine never reads it: screens and stores pass the day in.
 */
object AppClock {
    @Volatile var offsetDays: Long = 0L
        private set

    fun today(): LocalDate = LocalDate.now().plusDays(offsetDays)
    fun now(): LocalDateTime = LocalDateTime.now().plusDays(offsetDays)
    fun millis(): Long = System.currentTimeMillis() + offsetDays * DAY_MS

    /** Debug: today reads as [day] from now on (until the process ends). */
    fun pretend(day: LocalDate) { offsetDays = ChronoUnit.DAYS.between(LocalDate.now(), day) }

    private const val DAY_MS = 86_400_000L
}

package com.bigpapituna.sharpears.ui.profile

import androidx.compose.runtime.staticCompositionLocalOf
import com.bigpapituna.sharpears.data.ProgressStore

/**
 * The app's ProgressStore for any screen to report to without a new parameter:
 * `LocalProgress.current?.record(ProgressEvent.LessonStepPassed)`. The integration provides it once at the root
 * (`CompositionLocalProvider(LocalProgress provides progress) { ... }`); it is null in previews and tests.
 */
val LocalProgress = staticCompositionLocalOf<ProgressStore?> { null }

/**
 * The level bar for a Celebration (integration): from where [before] XP stood in its level to where [after] stands,
 * counted in levels, so a level gained fills the bar, flashes and wraps.
 */
fun xpBarFor(before: Int, after: Int,
             /** The level to name (2.14): the store's own ([ProgressStore.level]) where the caller has it, so a seeded level reads the same here as on You. */
             level: Int = com.bigpapituna.sharpears.engine.progress.Progression.levelFor(after)): com.bigpapituna.sharpears.ui.XpBar {
    val p = com.bigpapituna.sharpears.engine.progress.Progression
    val gained = p.levelFor(after) - p.levelFor(before)
    return com.bigpapituna.sharpears.ui.XpBar(p.fractionToNext(before), gained + p.fractionToNext(after), "Level $level")
}

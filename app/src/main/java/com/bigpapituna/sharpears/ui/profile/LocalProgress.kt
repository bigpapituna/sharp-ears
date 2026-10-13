package com.bigpapituna.sharpears.ui.profile

import androidx.compose.runtime.staticCompositionLocalOf
import com.bigpapituna.sharpears.data.ProgressStore

/**
 * The app's ProgressStore for any screen to report to without a new parameter:
 * `LocalProgress.current?.record(ProgressEvent.LessonStepPassed)`. The integration provides it once at the root
 * (`CompositionLocalProvider(LocalProgress provides progress) { ... }`); it is null in previews and tests.
 */
val LocalProgress = staticCompositionLocalOf<ProgressStore?> { null }

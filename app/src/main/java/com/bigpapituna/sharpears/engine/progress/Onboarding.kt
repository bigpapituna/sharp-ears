package com.bigpapituna.sharpears.engine.progress

/** "Have you played before?" and where in the course each answer starts. */
enum class Experience(val label: String, val detail: String, val startLessonId: String, val startUnit: String) {
    NEVER("Never", "I have never touched a piano.", "sit", "basics"),
    A_LITTLE("A little", "I know where some notes are.", "first-melody", "beginner"),
    SOME_YEARS("Some years", "I have played, and want to get better.", "scale-left", "intermediate");

    companion object {
        fun fromOrdinal(n: Int) = entries.getOrNull(n)
    }
}

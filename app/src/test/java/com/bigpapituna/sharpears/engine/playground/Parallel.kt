package com.bigpapituna.sharpears.engine.playground

/**
 * [cases] worked out on every core at once, the results in the cases' order. The hearing tests run a matcher per key,
 * level and set; one after another they took 20 minutes of the suite (audit, 2026-10-20). Each case must stand alone:
 * its own audio, matcher, pitch tracker and ear. The checks then read the results in order, as before.
 */
internal fun <T, R> parallelMap(cases: List<T>, f: (T) -> R): List<R> =
    cases.parallelStream().map { f(it) }.collect(java.util.stream.Collectors.toList())

package com.bigpapituna.sharpears

import org.junit.Assert.assertEquals
import org.junit.Test

// ponytail: P0 only proves the JVM test task runs; P1 adds real engine tests
class SmokeTest {
    @Test fun jvmTestsRun() = assertEquals(4, 2 + 2)
}

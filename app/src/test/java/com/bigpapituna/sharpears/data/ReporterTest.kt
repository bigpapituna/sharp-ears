package com.bigpapituna.sharpears.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy

/** A context with in-memory preferences that never change (enough to make a Reporter). */
private class QuietContext : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, _, args -> args?.getOrNull(1) } as SharedPreferences
}

class ReporterTest {
    /**
     * Audit: MainActivity makes a MicInput, so a Reporter, and calls catchCrashes on every start; each call chained one
     * more handler and kept its Activity. One crash then ran logcat and saved a report once per handler, each sent as
     * its own issue. The hook goes in once per process.
     */
    @Test fun theCrashHookGoesInOncePerProcess() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Reporter(QuietContext()).catchCrashes("t")
            val hook = Thread.getDefaultUncaughtExceptionHandler()
            assertNotSame(before, hook)
            repeat(3) { Reporter(QuietContext()).catchCrashes("t") }
            assertSame(hook, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }
}

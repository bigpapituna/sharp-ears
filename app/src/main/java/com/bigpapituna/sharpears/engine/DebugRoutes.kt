package com.bigpapituna.sharpears.engine

import java.security.MessageDigest

/**
 * Who may open the debug routes (security review 2026-10-22, finding 1). The debug build's launch extras (`--es screen`,
 * `--es seed`, `--es today`, `--es midi`, `--ez gate` and the rest, read by MainActivity and the demos it opens) act only
 * when [allowed] says so; MainActivity drops every other launch's extras before anything reads them.
 *
 * - Never from a link, a browser, another app's own action or the launcher: only a launch with no action or MAIN (what
 *   `adb shell am start -n` sends) that the launcher did not start (its icon or a shortcut: [fromLauncher]).
 * - A keyless build ([buildKey] empty: the worktree builds agents put on emulators) takes the routes as before.
 * - A keyed build (secrets.properties DEBUG_ROUTE_KEY, scripts/set-debug-key.ps1: the builds Roy hands testers) takes them
 *   only with `--es dk <key>` (scripts/adb-route.ps1 adds it). The build holds the key's SHA-256 ([digest]), not the key,
 *   so the APK does not give the key away to whoever unpacks it.
 *
 * Android-free: the activity passes the intent's action, its `dk` extra and whether the launcher sent it.
 */
object DebugRoutes {
    /** The extra that carries the key: `--es dk <key>`. */
    const val KEY_EXTRA = "dk"
    const val ACTION_MAIN = "android.intent.action.MAIN"

    enum class Verdict { ALLOWED, ACTION, LAUNCHER, NO_KEY, WRONG_KEY }

    /** Why a launch's extras may or may not act. [buildKey] is the build's key digest ("" on a keyless build). */
    fun verdict(buildKey: String, intentKey: String?, action: String?, fromLauncher: Boolean = false): Verdict = when {
        action != null && action != ACTION_MAIN -> Verdict.ACTION      // VIEW (a link, a browser), a shortcut's or another app's action
        fromLauncher -> Verdict.LAUNCHER
        buildKey.isBlank() -> Verdict.ALLOWED
        intentKey.isNullOrBlank() -> Verdict.NO_KEY
        MessageDigest.isEqual(digest(intentKey).toByteArray(), buildKey.trim().lowercase().toByteArray()) -> Verdict.ALLOWED
        else -> Verdict.WRONG_KEY
    }

    /**
     * Whether a launch's debug extras may act on this create: the first one only (2.14). After a recreate ([savedInstanceState]
     * was handed back: a theme or font change, the process coming back) the activity's intent still holds them, and acting again
     * switched the profile, moved the clock and re-seeded the stores (persona walk: "profiles: switched to owner" after a lesson).
     * A route that arrives later comes in through onNewIntent and acts there, once.
     */
    fun oneShot(recreated: Boolean): Boolean = !recreated

    fun allowed(buildKey: String, intentKey: String?, action: String?, fromLauncher: Boolean = false): Boolean =
        verdict(buildKey, intentKey, action, fromLauncher) == Verdict.ALLOWED

    /** The key's SHA-256 in lower-case hex, as app/build.gradle.kts writes it into BuildConfig.DEBUG_ROUTE_KEY_SHA256. */
    fun digest(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.trim().toByteArray(Charsets.UTF_8)).joinToString("") { Integer.toHexString((it.toInt() and 0xFF) or 0x100).substring(1) }
}

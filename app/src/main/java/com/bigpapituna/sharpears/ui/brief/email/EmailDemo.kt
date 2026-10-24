package com.bigpapituna.sharpears.ui.brief.email

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.bigpapituna.sharpears.data.EmailSettings
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailPrefs
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.ParentGateController

/*
 * The debug route `--es screen email` (debug builds only, MainActivity opens it like the other demos, and on a keyed build
 * only with `--es dk <key>`: engine/DebugRoutes.kt): Settings > Briefs and email (the family's address and every kid's
 * row) over settings seeded to the state asked for, so every step can be seen. A build with an email key could send from
 * it (the security review's finding 1), which is why the route needs the key on the builds testers get.
 *
 *   --es child <profile id>                 the kid whose row is seeded and whose preview opens (default: the first kid)
 *   --es seed none|waiting|code|gone|unchosen|verified|daily|held|old   the step (default: what the phone holds)
 *                                           old: 2.7's per-child address on that kid, moved to the family's as the page opens
 *   --es view settings|preview              default settings
 *   --es period day|week|first              the preview's tab (default week); first: the first email, the code alone
 *   --ez samples true                       the preview over made-up findings
 *   --es theme light|dark
 * The seeded address is parent@example.com, a made-up one.
 */
@Composable
fun EmailDemo(intent: Intent, fallback: Profile, onExit: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val dark = when (intent.getStringExtra("theme")) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val kids = remember { Profiles(context).all.filter { it.isKid } }
    val child = remember { kids.firstOrNull { it.id == intent.getStringExtra("child") } ?: kids.firstOrNull() ?: fallback }
    remember(child.id) { seed(context, child.id, intent.getStringExtra("seed")); child.id }      // seeded once, before the page reads it
    val gate = remember { ParentGateController(null).also { it.passed() } }
    var preview by remember { mutableStateOf(intent.getStringExtra("view") == "preview") }
    SharpEarsTheme(dark) {
        CompositionLocalProvider(LocalParentGate provides gate) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (preview) EmailPreviewScreen(child, onBack = { preview = false }, samples = intent.getBooleanExtra("samples", false),
                    startDay = intent.getStringExtra("period") == "day", first = intent.getStringExtra("period") == "first")
                else BriefsAndEmailPage(kids.ifEmpty { listOf(child) }, onBack = onExit)
            }
        }
    }
}

private fun seed(context: Context, id: String, step: String?) {
    val address = "parent@example.com"
    if (step == "old") {
        // 2.7's shape: the address on the kid, no family record. The page's first read moves it (EmailSettings.migrate).
        context.getSharedPreferences(EmailSettings.FILE, Context.MODE_PRIVATE).edit {
            remove(EmailSettings.FAMILY)
            putString(id, EmailPrefs(address, verified = true, firstSent = true, frequency = EmailFrequency.DAILY, chosen = true).encode())
        }
        return
    }
    EmailSettings.migrate(context)
    val family = EmailSettings.family(context)
    val store = EmailSettings.of(context, id)
    val now = System.currentTimeMillis()
    val confirmed = EmailPrefs(address, verified = true, firstSent = true)
    val both = EmailPrefs(frequency = EmailFrequency.BOTH, chosen = true, lastDailyAt = now - 20 * 3_600_000L, lastWeeklyAt = now - 3 * 86_400_000L,
        sends = listOf(now - 20 * 3_600_000L))
    when (step) {
        "none" -> family.seed(EmailPrefs())
        "waiting" -> family.seed(EmailPrefs(address, code = "482913", codeExpiresAt = now + 86_400_000L))
        "code" -> family.seed(EmailPrefs(address, code = "482913", codeExpiresAt = now + 86_400_000L, firstSent = true, sends = listOf(now - 3 * 60_000L)))
        "gone" -> family.seed(EmailPrefs(address, firstSent = true, sends = listOf(now - 30 * 60_000L)))
        "unchosen" -> { family.seed(confirmed); store.save(EmailPrefs()) }
        "verified" -> { family.seed(confirmed); store.save(both) }
        "daily" -> { family.seed(confirmed); store.save(both.copy(frequency = EmailFrequency.DAILY)) }
        "held" -> { family.seed(confirmed); store.save(both.copy(held = "METERED")) }
    }
}

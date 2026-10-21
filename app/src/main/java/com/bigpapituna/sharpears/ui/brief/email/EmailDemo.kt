package com.bigpapituna.sharpears.ui.brief.email

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
import com.bigpapituna.sharpears.data.EmailSettings
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailPrefs
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.ParentGateController

/*
 * The debug route `--es screen email` (debug builds only, MainActivity opens it like the other demos): the email settings of
 * a real child on this phone, over settings seeded to the state asked for, so every step can be seen. Nothing is sent: a
 * debug build has no email key, so a send is refused and the screen says so in kind words.
 *
 *   --es child <profile id>                 default: the first kid
 *   --es seed none|waiting|code|gone|unchosen|verified|daily|held   the step (default: what the phone holds)
 *   --es view settings|preview              default settings
 *   --es period day|week                    the preview's tab (default week)
 *   --ez samples true                       the preview over made-up findings
 *   --es theme light|dark
 * The seeded address is parent@example.com, a made-up one.
 */
@Composable
fun EmailDemo(intent: Intent, fallback: Profile, onExit: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val dark = when (intent.getStringExtra("theme")) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val child = remember {
        val kids = Profiles(context).all.filter { it.isKid }
        kids.firstOrNull { it.id == intent.getStringExtra("child") } ?: kids.firstOrNull() ?: fallback
    }
    remember(child.id) { seed(context, child.id, intent.getStringExtra("seed")) }
    val gate = remember { ParentGateController(null).also { it.passed() } }
    var preview by remember { mutableStateOf(intent.getStringExtra("view") == "preview") }
    SharpEarsTheme(dark) {
        CompositionLocalProvider(LocalParentGate provides gate) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (preview) EmailPreviewScreen(child, onBack = { preview = false }, samples = intent.getBooleanExtra("samples", false), startDay = intent.getStringExtra("period") == "day")
                else EmailSettingsScreen(child, onBack = onExit, onPreview = { preview = true })
            }
        }
    }
}

private fun seed(context: android.content.Context, id: String, step: String?) {
    val store = EmailSettings.of(context, id)
    val now = System.currentTimeMillis()
    val address = "parent@example.com"
    val verified = EmailPrefs(address, verified = true, firstSent = true, frequency = EmailFrequency.BOTH, chosen = true,
        lastDailyAt = now - 20 * 3_600_000L, lastWeeklyAt = now - 3 * 86_400_000L, sends = listOf(now - 20 * 3_600_000L))
    when (step) {
        "none" -> store.forget()
        "waiting" -> store.save(EmailPrefs(address, code = "482913", codeExpiresAt = now + 86_400_000L))
        "code" -> store.save(EmailPrefs(address, code = "482913", codeExpiresAt = now + 86_400_000L, firstSent = true, sends = listOf(now - 3 * 60_000L)))
        "gone" -> store.save(EmailPrefs(address, firstSent = true, sends = listOf(now - 30 * 60_000L)))
        "unchosen" -> store.save(verified.copy(chosen = false, frequency = EmailFrequency.WEEKLY, lastDailyAt = 0, lastWeeklyAt = 0, sends = emptyList()))
        "verified" -> store.save(verified)
        "daily" -> store.save(verified.copy(frequency = EmailFrequency.DAILY))
        "held" -> store.save(verified.copy(held = "METERED"))
    }
}

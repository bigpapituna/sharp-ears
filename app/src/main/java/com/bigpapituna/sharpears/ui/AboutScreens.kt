package com.bigpapituna.sharpears.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.SongMeta

/** Back arrow, heading, and a readable column (about 65 characters wide) for the text pages. */
@Composable
private fun TextPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            content()
            Column(Modifier.padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp).semantics { heading() })

@Composable
private fun Para(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

@Composable
private fun Link(label: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) { Text(label) }
}

/**
 * What the app does with the microphone, files and the network. Written for this private build: it says plainly
 * that debug builds can keep practice recordings (switchable in Microphone setup).
 */
@Composable
fun PrivacyScreen(canRecord: Boolean, onMicSetup: () -> Unit, onBack: () -> Unit,
                  /** This build carries an AI key, so the parent brief can be written by an AI model (data/BriefAi.kt). */
                  aiBrief: Boolean = com.bigpapituna.sharpears.data.BriefAi.hasKey,
                  /** This build can send the brief by email (a key and a sender in data/BriefEmail.kt). */
                  emailBrief: Boolean = com.bigpapituna.sharpears.data.BriefEmail.hasKey,
                  /** Where the email's pictures are hosted (EMAIL_IMAGE_BASE's host), or null when they travel inside the email. */
                  pictureHost: String? = com.bigpapituna.sharpears.data.BriefEmail.pictureHost) = TextPage("Privacy", onBack) {
    val except = listOfNotNull("the parent brief's numbers when the AI writes it (below)".takeIf { aiBrief },
        "the brief by email, if a grown-up turns it on (below)".takeIf { emailBrief },
        "the review reports below, which you switch on".takeIf { canRecord })
    Para("Sharp Ears has no account, no ads and no analytics. " + if (except.isNotEmpty()) "Apart from ${except.joinToString(" and ")}, " +
        "it sends nothing about you or your playing anywhere." else "It does not send anything about you or your playing anywhere.")
    Heading("Microphone")
    Para("The microphone is used only to hear which piano notes you play. It listens only while a song, a lesson or Playground game, " +
        "a chord or ear training game, the Jam, Pup says, Clap along, the tuner or Microphone setup is open with the mic on, and the app " +
        "is on screen. The sound is analysed on this phone and then discarded.")
    if (canRecord) {
        Para("This is a private development build. With Keep practice recordings switched on, it saves your last 4 mic sessions " +
            "on this phone so note detection can be tuned from your playing, plus short clips when wait mode gets stuck, when you flag " +
            "something, or when the mic and a USB keyboard disagree. Anything said in the room ends up in those files. " +
            "They stay on the phone unless Send them for review is on: then, on Wi-Fi, those clips, crash reports and a weekly summary " +
            "go to your own private GitHub repository, where they stay. Switch either off, or delete the clips on this phone, in Microphone setup. " +
            "Sharing them, or switching sending on, is only ever started by you, from Microphone setup.")
        TextButton(onClick = onMicSetup) { Text("Microphone setup") }
    }
    Heading("What is stored")
    Para("The names and pups of everyone who plays on this phone, and for each of them: settings, song progress, best scores, practice " +
        "minutes, lesson and chord progress, XP, level, achievements and goals, ear training scores, what was practised with the scales " +
        "and warm-ups, stickers and the pup's world. These and any MIDI files you import are kept in the app's " +
        "private storage on this phone. Uninstalling the app removes them. If the phone's own backup to your Google account is on, it " +
        "can carry them to a new phone; it never takes the mic recordings or the report token. Settings has a reset for progress " +
        "(lessons, stickers, XP and achievements stay).")
    Heading("Internet")
    val uses = listOfNotNull("to show tutorial videos", "to have the parent brief written by AI".takeIf { aiBrief },
        "to send the brief by email, when a grown-up turns that on".takeIf { emailBrief },
        "to send review reports, when that is on".takeIf { canRecord })
    Para("The app itself uses the internet only " + uses.dropLast(1).joinToString(", ").let { if (it.isEmpty()) uses.last() else "$it and ${uses.last()}" } +
        ". When you open a video, YouTube's embedded player loads " +
        "from YouTube and connects to Google, and YouTube's terms and Google's privacy policy apply to it. " +
        "Open in YouTube hands the video to the YouTube app or your browser.")
    Link("YouTube Terms of Service", "https://www.youtube.com/t/terms")
    Link("Google Privacy Policy", "https://policies.google.com/privacy")
    Heading("The parent brief")
    Para("The parent brief is made from each person's own playing on this phone: minutes, days, stars, tries, which lessons and songs, " +
        "and moments such as a noisy room or a step that was hard. It never uses a recording or any sound, and it compares a child only " +
        "with their own earlier weeks. Grown-ups read it behind the parent gate; a kid sees only the pup's short, kind card. A brief you " +
        "send goes only where you send it.")
    if (aiBrief) {
        Para("On this build an AI model can write the words. On Wi-Fi, or on mobile data if you allow it in Settings, Family, the app sends " +
            "OpenRouter (openrouter.ai), which passes it to the model's maker (Anthropic, for Claude Haiku), only the brief's findings: " +
            "minutes, days, stars, tries, which lessons, songs and pages, and notes such as \"stuck on a lesson\" or \"noisy room\". It never " +
            "sends a name (the app writes the name in afterwards, on this phone), a recording or any sound, a note a grown-up typed, or a song " +
            "or lesson whose title or file name holds someone's name. A question you type or dictate is sent as text with names taken out. " +
            "The app asks OpenRouter to use only providers that do not keep or train on what is sent. Like any website, OpenRouter sees the " +
            "request come from your internet connection.")
        Para("What comes back is checked on the phone, and anything that names something not in the findings is left out. Offline, on mobile " +
            "data you did not allow, when the model is slow, after 40 AI briefs in a week for one person, when a reply fails a check, or with " +
            "AI briefs switched off in Settings, Family, the brief is written on the phone instead, and says so. The last brief written for " +
            "each person is kept on the phone, so opening it again sends nothing.")
    } else Para("On this build every brief, card and answer is written on the phone, and nothing about it leaves the phone.")
    Para("Dictating a question uses the phone's own speech recognition, so your voice stays on the phone; the microphone button is hidden " +
        "when the phone has none.")
    if (emailBrief) {
        Heading("The brief by email")
        Para("If you give an email address for a child (grown-ups corner, Family, the child, Email), the app sends that child's brief to it: " +
            "the brief's words and numbers (minutes, days, stars, which lessons and songs, slips, how long keys took to find, what the app " +
            "suggests) and the full report as a PDF. It goes from this phone to Brevo (brevo.com), the email service that delivers it, and " +
            "from there to your address. Nothing else is sent: no recording or sound, no note you typed, nothing about the other children. " +
            "The child's first name is in the email, because it is written for you. The first email carries a code; until it is typed in " +
            "the app, nothing else is sent. You choose daily, weekly, both or off, nothing goes between 9:30 pm and 7 am, and never more than " +
            "two a day. The address stays on this phone, in the app's own storage; it is not in Android's backup, and it is deleted with the " +
            "child's profile or when you stop the emails. Brevo keeps delivery records under its own privacy policy. When you open the email, " +
            "your mail app may fetch its font from Google" + (pictureHost?.let { " and the pup's pictures from $it (the same pictures for every family)" } ?: "") +
            ", as it does for any email.")
    }
    Heading("Speech and MIDI")
    Para("Spoken guidance uses a voice that runs entirely on the phone: lines recorded into the app, and the Kokoro voice model for the rest. " +
        "With the natural voice off, it uses the phone's own text-to-speech engine instead. A USB MIDI keyboard's notes are read on the phone and go nowhere else.")
}

private data class Component(val name: String, val by: String, val licence: String, val file: String?)

private val COMPONENTS = listOf(
    Component("Jetpack Compose, Material 3, AndroidX Activity and Window", "The Android Open Source Project", "Apache License 2.0", "apache-2.0.txt"),
    Component("Kotlin standard library and coroutines", "JetBrains", "Apache License 2.0", "apache-2.0.txt"),
    Component("android-youtube-player", "Pierfrancesco Soffritti", "MIT License", "mit-android-youtube-player.txt"),
    Component("Geist and Geist Mono typefaces", "The Geist Project Authors", "SIL Open Font License 1.1", "ofl-1.1.txt"),
    Component("Nunito typeface", "The Nunito Project Authors", "SIL Open Font License 1.1", "ofl-1.1.txt"),
    Component("Material Symbols icons", "Google", "Apache License 2.0", "apache-2.0.txt"),
    Component("Kokoro-82M voice model and its recorded lines", "hexgrad", "Apache License 2.0", "apache-2.0.txt"),
    Component("sherpa-onnx speech runtime, with ONNX Runtime and eSpeak NG data", "Xiaomi Corporation (k2-fsa), Microsoft, the eSpeak NG authors",
        "Apache License 2.0; MIT License; GPL 3.0 (pronunciation data)", "apache-2.0.txt"),
    Component("VSCO 2 Community Edition upright piano samples", "Simon Dalzell (Ivy Audio) and Versilian Studios", "CC0 1.0 (public domain)", null),
)

/** Every third-party component and song source, with its licence; tap a licence to read its full text. */
@Composable
fun LicencesScreen(songs: List<SongMeta>, onBack: () -> Unit) = TextPage("Licences", onBack) {
    val context = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    Para("Sharp Ears is built with these components. Tap a licence to read it in full.")
    COMPONENTS.forEach { c ->
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleSmall)
            Text("${c.by}. ${c.licence}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.file != null) {
                val key = c.name
                Row(Modifier.heightIn(min = 48.dp).clickable { open = if (open == key) null else key }, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (open == key) "Hide licence text" else "Show licence text", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                if (open == key) {
                    val text = remember(c.file) { context.assets.open("licenses/${c.file}").bufferedReader().use { it.readText() } }
                    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Heading("Tutorial videos")
    Para("Videos are streamed by YouTube in its official embedded player and belong to their channels. They are not stored by this app.")
    Link("YouTube Terms of Service", "https://www.youtube.com/t/terms")
    Heading("Songs")
    songs.forEach { s ->
        Text("${s.title} (${s.composer.ifEmpty { "unknown" }}): ${s.license}. ${s.attribution}.".replace("..", "."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Para("Songs marked personal use are your own copies on this phone and are not part of the app.")
}

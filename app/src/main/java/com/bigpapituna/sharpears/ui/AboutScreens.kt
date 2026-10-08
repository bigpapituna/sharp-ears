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
fun PrivacyScreen(canRecord: Boolean, onMicSetup: () -> Unit, onBack: () -> Unit) = TextPage("Privacy", onBack) {
    Para("Sharp Ears has no account, no ads and no analytics. It does not send anything about you or your playing anywhere.")
    Heading("Microphone")
    Para("The microphone is used only to hear which piano notes you play. It listens only while a song, lesson or chord game " +
        "has the mic switched on and the app is on screen. The sound is analysed on this phone and then discarded.")
    if (canRecord) {
        Para("This is a private development build. With Keep practice recordings switched on, it saves your last 4 mic sessions " +
            "on this phone so note detection can be tuned from your playing. Anything said in the room ends up in those files. " +
            "They stay on the phone; switch them off or delete them in Microphone setup.")
        TextButton(onClick = onMicSetup) { Text("Microphone setup") }
    }
    Heading("What is stored")
    Para("Your settings, song progress, best scores, practice minutes, lesson and chord progress, and any MIDI files you import " +
        "are kept in the app's private storage on this phone. Uninstalling the app removes them. Settings has a reset for song progress.")
    Heading("Internet")
    Para("The app itself uses the internet only to show tutorial videos. When you open one, YouTube's embedded player loads " +
        "from YouTube and connects to Google, and YouTube's terms and Google's privacy policy apply to it. " +
        "Open in YouTube hands the video to the YouTube app or your browser.")
    Link("YouTube Terms of Service", "https://www.youtube.com/t/terms")
    Link("Google Privacy Policy", "https://policies.google.com/privacy")
    Heading("Speech and MIDI")
    Para("Spoken guidance uses the phone's own text-to-speech engine. A USB MIDI keyboard's notes are read on the phone and go nowhere else.")
}

private data class Component(val name: String, val by: String, val licence: String, val file: String?)

private val COMPONENTS = listOf(
    Component("Jetpack Compose, Material 3, AndroidX Activity and Window", "The Android Open Source Project", "Apache License 2.0", "apache-2.0.txt"),
    Component("Kotlin standard library and coroutines", "JetBrains", "Apache License 2.0", "apache-2.0.txt"),
    Component("android-youtube-player", "Pierfrancesco Soffritti", "MIT License", "mit-android-youtube-player.txt"),
    Component("Geist and Geist Mono typefaces", "The Geist Project Authors", "SIL Open Font License 1.1", "ofl-1.1.txt"),
    Component("Material Symbols icons", "Google", "Apache License 2.0", "apache-2.0.txt"),
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

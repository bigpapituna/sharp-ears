package com.bigpapituna.sharpears.ui.brief.email

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.BriefAi
import com.bigpapituna.sharpears.data.BriefEmail
import com.bigpapituna.sharpears.data.BriefLoader
import com.bigpapituna.sharpears.data.BriefStore
import com.bigpapituna.sharpears.data.EmailSettings
import com.bigpapituna.sharpears.engine.brief.BriefSamples
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.DialogBarsFollowTheme
import com.bigpapituna.sharpears.ui.brief.PillButton
import com.bigpapituna.sharpears.ui.family.Gated
import com.bigpapituna.sharpears.ui.family.ParentGateHost
import com.bigpapituna.sharpears.ui.paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The email settings of one child as a full screen dialog behind the parent gate: the page ([EmailSettingsScreen]) and its
 * preview. [startPreview] and [samples] are for the debug route (the preview over made-up findings).
 */
@Composable
fun EmailDialog(child: Profile, onClose: () -> Unit, startPreview: Boolean = false, samples: Boolean = false, startDay: Boolean = false) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogBarsFollowTheme()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Gated(onCancel = onClose) {
                var preview by rememberSaveable { mutableStateOf(startPreview) }
                if (preview) EmailPreviewScreen(child, onBack = { preview = false }, samples = samples, startDay = startDay)
                else EmailSettingsScreen(child, onBack = onClose, onPreview = { preview = true })
            }
            ParentGateHost()
        }
    }
}

/**
 * What the parent's email looks like now: the child's brief for today or this week drawn by the same renderer the sender uses
 * (the phone's own words: the AI is not asked, so a preview costs nothing), in a WebView that runs no script, follows no link
 * and loads nothing from the network. The buttons inside do nothing here; in the email they open the app. Nothing is sent.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmailPreviewScreen(child: Profile, onBack: () -> Unit, modifier: Modifier = Modifier, samples: Boolean = false, startDay: Boolean = false) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current.applicationContext
    var weekly by rememberSaveable { mutableStateOf(!startDay) }
    val c = MaterialTheme.colorScheme
    val html by produceState<String?>(null, child.id, weekly) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val period = if (weekly) Period.WEEK else Period.DAY
                val findings = if (samples) BriefSamples.kid(period, now) else BriefLoader.findings(context, child, period, now)
                val brief = BriefAi.writers(context, child).phone.write(findings, child.name) ?: return@runCatching null
                val saved = EmailSettings.of(context, child.id).load()
                val frequency = if (saved.chosen) saved.frequency else EmailFrequency.WEEKLY
                val email = BriefEmail.preview(context, child, findings, brief, BriefStore(context, child.id).weeklyOrDefault(child.isKid), frequency)
                BriefEmail.previewHtml(context, email)
            }.getOrNull()
        }
    }
    Column(modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
            Text("What it looks like", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Daily", onClick = { weekly = false }, selected = !weekly)
            PillButton("Weekly", onClick = { weekly = true }, selected = weekly)
        }
        Text("${child.name}'s ${if (weekly) "week" else "day"} as the email draws it. Nothing is sent, and the buttons do nothing here.",
            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val page = html
            if (page == null) CircularProgressIndicator(strokeWidth = 3.dp)
            else AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    setBackgroundColor(0)
                    // Every link is swallowed: the preview opens nothing (the buttons are the app's own link in the real email).
                    webViewClient = object : WebViewClient() { override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true }
                    contentDescription = "The email, as it will look"
                }
            }, update = { web: WebView ->
                if (web.tag != page) { web.tag = page; web.loadDataWithBaseURL(null, page, "text/html", "utf-8", null) }
            })
        }
    }
}

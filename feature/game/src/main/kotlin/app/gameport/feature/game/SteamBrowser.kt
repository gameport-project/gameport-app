package app.gameport.feature.game

import android.content.pm.ApplicationInfo
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassIconButton
import app.gameport.core.steam.session.WebCookies

/** A page opens on the web pages of Steam only: a link that leaves them is not followed. */
internal fun isSteamPage(host: String?): Boolean {
    val name = host?.lowercase() ?: return false
    return name == "steamcommunity.com" || name.endsWith(".steamcommunity.com") || name == "steampowered.com" || name.endsWith(".steampowered.com")
}

private val COOKIE_SITES = listOf("https://steamcommunity.com", "https://store.steampowered.com")

/**
 * A page of Steam inside GamePort, with the account already signed in when there is a session (no new sign-in, see [SteamWebSession]): the Workshop of a game, the page of a game in the
 * shop. At the top, the back button leaves the screen, the title says what it is, the chevrons go to the previous and next page of the site (greyed when there is none), and [action]
 * sits at the right when the screen has one of its own. [hint] is a line under the header.
 */
@Composable
internal fun SteamBrowser(
    title: String,
    subtitle: String,
    hint: String?,
    startUrl: String,
    state: WorkshopState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    var web by remember { mutableStateOf<WebView?>(null) }
    // The pages of the site have their own previous and next buttons: the back button of the screen always leaves it.
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                // Previous and next page, greyed when there is none: chevrons, so they are not mistaken for the arrow that leaves the screen.
                GlassIconButton(onClick = { web?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.workshop_previous_page))
                }
                GlassIconButton(onClick = { web?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.workshop_next_page))
                }
                action?.invoke()
            }
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The bottom margin matches the top one.
            Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 24.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                when (val shown = state) {
                    WorkshopState.Loading -> CircularProgressIndicator()
                    WorkshopState.Unavailable -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.workshop_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GlassButton(onClick = onRetry) { Text(stringResource(R.string.workshop_retry)) }
                    }
                    is WorkshopState.Ready -> SteamWebPage(shown.cookies, startUrl, onCreated = { web = it }, onHistory = { back, forward -> canGoBack = back; canGoForward = forward })
                }
            }
        }
    }
}

/** The web view: the cookies of the account, when there are any, are put in before the first page is asked for, and it stays on the pages of Steam. */
@Composable
private fun SteamWebPage(cookies: WebCookies?, startUrl: String, onCreated: (WebView) -> Unit, onHistory: (back: Boolean, forward: Boolean) -> Unit) {
    val sessionId = remember { java.util.UUID.randomUUID().toString().replace("-", "").take(24) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // A debug build can be looked at from the Mac (chrome://inspect), to see what the page does.
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                val manager = CookieManager.getInstance()
                manager.setAcceptCookie(true)
                manager.setAcceptThirdPartyCookies(this, true)
                if (cookies != null) {
                    COOKIE_SITES.forEach { site ->
                        manager.setCookie(site, "steamLoginSecure=${cookies.steamLoginSecure}; Path=/; Secure; HttpOnly")
                        manager.setCookie(site, "sessionid=$sessionId; Path=/; Secure")
                    }
                    manager.flush()
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !isSteamPage(request.url.host)

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = onHistory(view.canGoBack(), view.canGoForward())
                }
                onCreated(this)
                loadUrl(startUrl)
            }
        },
        onRelease = { it.destroy() },
    )
}

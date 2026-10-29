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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.BackButton
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassIconButton
import app.gameport.core.steam.session.WebCookies

/** The page opens on the web pages of Steam only: a link that leaves them is not followed. */
internal fun isSteamPage(host: String?): Boolean {
    val name = host?.lowercase() ?: return false
    return name == "steamcommunity.com" || name.endsWith(".steamcommunity.com") || name == "steampowered.com" || name.endsWith(".steampowered.com")
}

internal fun workshopUrl(appId: Int) = "https://steamcommunity.com/app/$appId/workshop/"

private val COOKIE_SITES = listOf("https://steamcommunity.com", "https://store.steampowered.com")

/**
 * The Workshop of a game: Steam's own page, with the account already signed in (no new sign-in, see [WorkshopViewModel]). The button at the top gets back to the game without leaving
 * GamePort: the game is started again, or brought to the front when it is still running.
 */
@Composable
fun WorkshopScreen(onBack: () -> Unit, viewModel: WorkshopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name by viewModel.gameName.collectAsStateWithLifecycle()
    val running by viewModel.gameRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    // The pages of the Workshop have their own previous and next buttons: the back button of the screen always goes back to the game's page.
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.workshop_title), style = MaterialTheme.typography.headlineMedium)
                    Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Previous and next page of the Workshop, greyed when there is none: chevrons, so they are not mistaken for the arrow that leaves the screen.
                GlassIconButton(onClick = { web?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.workshop_previous_page))
                }
                GlassIconButton(onClick = { web?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.workshop_next_page))
                }
                val toGame = { viewModel.launchIntent()?.let { runCatching { context.startActivity(it) } }; Unit }
                // A game still there is gone back to (glass button); one that is not is launched (the filled button, as on the game's page).
                if (running) {
                    GlassButton(onClick = toGame) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.workshop_back_to_game))
                    }
                } else {
                    Button(onClick = toGame) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.workshop_launch_game))
                    }
                }
            }
            Text(stringResource(R.string.workshop_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The bottom margin matches the top one.
            Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 24.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                when (val shown = state) {
                    WorkshopState.Loading -> CircularProgressIndicator()
                    WorkshopState.Unavailable -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.workshop_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GlassButton(onClick = viewModel::load) { Text(stringResource(R.string.workshop_retry)) }
                    }
                    is WorkshopState.Ready -> SteamWebPage(shown.cookies, workshopUrl(viewModel.appId), onCreated = { web = it }, onHistory = { back, forward -> canGoBack = back; canGoForward = forward })
                }
            }
        }
    }
}

/** The web view: the cookies of the account are put in before the first page is asked for, and it stays on the pages of Steam. */
@Composable
private fun SteamWebPage(cookies: WebCookies, startUrl: String, onCreated: (WebView) -> Unit, onHistory: (back: Boolean, forward: Boolean) -> Unit) {
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
                COOKIE_SITES.forEach { site ->
                    manager.setCookie(site, "steamLoginSecure=${cookies.steamLoginSecure}; Path=/; Secure; HttpOnly")
                    manager.setCookie(site, "sessionid=$sessionId; Path=/; Secure")
                }
                manager.flush()
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

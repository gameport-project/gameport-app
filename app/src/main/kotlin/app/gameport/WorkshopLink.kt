package app.gameport

import android.content.Intent

/**
 * A game that shows a link to its Workshop (the "browse" button of a game) asks the system to open the page. GamePort offers to take it,
 * so the Workshop opens in the application, with the account already signed in.
 */
internal fun workshopAppId(intent: Intent?): Int? {
    val uri = intent?.data ?: return null
    if (intent.action != Intent.ACTION_VIEW) return null
    return workshopAppId(uri.scheme, uri.host, uri.pathSegments)
}

internal fun workshopAppId(scheme: String?, host: String?, segments: List<String>): Int? {
    if (scheme != "https" && scheme != "http") return null
    val name = host?.lowercase()
    if (name != "steamcommunity.com" && name != "www.steamcommunity.com") return null
    if (segments.size < 3 || segments[0] != "app" || segments[2] != "workshop") return null
    return segments[1].toIntOrNull()?.takeIf { it > 0 }
}

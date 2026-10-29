package app.gameport.core.steam

import android.util.Log
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.WebCookies
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in account for the web pages of Steam (the Workshop in the application): the cookies are made from the session already open, with no new sign-in. */
@Singleton
class SteamWebSession @Inject constructor(
    private val sessions: SteamSessionHolder,
) {
    /** The cookies, or null when there is no session (signed out, offline) or Steam refused to make them. */
    suspend fun cookies(): WebCookies? = runCatching { sessions.current.value?.webCookies() }
        .onFailure { Log.w(TAG, "the cookies of the web pages could not be made: ${it.javaClass.simpleName}") }
        .getOrNull()

    private companion object {
        const val TAG = "GPWorkshop"
    }
}

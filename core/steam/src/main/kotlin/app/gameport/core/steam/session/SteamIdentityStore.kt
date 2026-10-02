package app.gameport.core.steam.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who was last signed in, kept so GamePort can show the library and start games without reaching Steam. */
data class CachedIdentity(val steamId: Long, val displayName: String) {
    /** The account id the library cache is keyed by: the lower 32 bits of the SteamID. */
    val accountId: Long get() = steamId and 0xFFFFFFFFL
}

/** The last signed-in identity, and whether the player chose offline mode. Both are cleared on sign-out. */
@Singleton
class SteamIdentityStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("gameport_steam_identity", Context.MODE_PRIVATE)
    private val _offlineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE, false))

    /** True when the player asked GamePort not to contact Steam. */
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    fun setOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE, enabled).apply()
        _offlineMode.value = enabled
    }

    fun identity(): CachedIdentity? {
        val steamId = prefs.getLong(KEY_STEAM_ID, 0L).takeIf { it != 0L } ?: return null
        return CachedIdentity(steamId, prefs.getString(KEY_NAME, "").orEmpty())
    }

    fun save(identity: CachedIdentity) {
        prefs.edit().putLong(KEY_STEAM_ID, identity.steamId).putString(KEY_NAME, identity.displayName).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _offlineMode.value = false
    }

    private companion object {
        const val KEY_OFFLINE = "offline_mode"
        const val KEY_STEAM_ID = "steam_id"
        const val KEY_NAME = "display_name"
    }
}
